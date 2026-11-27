#!/usr/bin/env node
/**
 * CalcSuits Phase 4.1 — Isolated POC Runner: Direct ONNX Runtime Inference for
 * `onnx-community/TexTeller-ONNX` (Apache-2.0, ViT 448x448 grayscale + TrOCR decoder).
 *
 * Bypasses `@huggingface/transformers` `pipeline("image-to-text")` (which fails due to
 * missing `preprocessor_config.json`) and implements the exact upstream `OleehyO/TexTeller`
 * image preprocessing (`trim_white_border`, grayscale, aspect-preserving resize to max 448,
 * normalize mean=0.9545467 std=0.15394445, zero-pad to 448x448) + direct `onnxruntime-node`
 * encoder/decoder greedy decoding + `AutoTokenizer` decoding.
 */

import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import { createRequire } from "node:module";
import { performance } from "node:perf_hooks";
import { pathToFileURL } from "node:url";
import { normalizeWithAst, isSemanticallyEquivalent, evaluateGranularMetrics } from "./math_ir_ast.mjs";
import { buildDetailedSummary, classifyFailureOrigin } from "./poc_metrics.mjs";

const require = createRequire("/tmp/poc_ocr_env/package.json");
const ort = require("onnxruntime-node");
const { PNG } = require("pngjs");
const { AutoTokenizer, env } = require("@huggingface/transformers");

const VARIANT = process.argv[2] || "q4f16";
const MANIFEST_PATH = process.argv[3] || "/tmp/poc_ocr_dataset/manifest.json";
const OUT_FILE = process.argv[4] || `poc/ocr/results/texteller_${VARIANT}_base.json`;
const MAX_ITEMS = process.argv[5] ? Number(process.argv[5]) : 999;

const MODEL_ID = "onnx-community/TexTeller-ONNX";
const MODEL_REVISION = "9727784d91d7f8437dc7140941c4335284ce075e";
const EXPECTED_Q4F16_SHA256 = {
  "encoder_model_q4f16.onnx": "c42190515ffcd4a728a1abee4dc8d4636b62f782fd823294d2861434a7f7d0eb",
  "decoder_model_q4f16.onnx": "edebc4c90596d13313382eecfeef89f0041efe7bfa4e89391d5b5dbabab25100",
  "tokenizer.json": "ec4ca954798a092faf6fefcfa47fb5f85d76cdf6ab170b624ae1a683d53dae14",
};
const PINNED_VARIANT_SHA256 = {
  q4f16: EXPECTED_Q4F16_SHA256,
};
const MODEL_DIR = "/tmp/poc_ocr_env/texteller_onnx";
const HF_CACHE = "/tmp/poc_ocr_env/hf_cache";

const IMAGE_MEAN = 0.9545467;
const IMAGE_STD = 0.15394445;
const FIXED_IMG_SIZE = 448;
const BOS_TOKEN_ID = 0; // <s>
const EOS_TOKEN_ID = 2; // </s>
const DECODER_START_TOKEN_ID = 2; // per config.json decoder.decoder_start_token_id (RoBERTa/TrOCR)
const MAX_NEW_TOKENS = 36;

const VARIANT_FILES = {
  q4: ["encoder_model_q4.onnx", "decoder_model_q4.onnx"],
  q4f16: ["encoder_model_q4f16.onnx", "decoder_model_q4f16.onnx"],
  int8: ["encoder_model_int8.onnx", "decoder_model_int8.onnx"],
};

export function verifyFileSha256(filePath, expectedSha256, label = filePath) {
  if (!expectedSha256) {
    throw new Error(`Missing expected SHA-256 for ${label}; refusing unverified artifact.`);
  }
  if (!fs.existsSync(filePath)) {
    throw new Error(`Expected artifact not found for SHA-256 verification: ${filePath}`);
  }
  const actual = crypto.createHash("sha256").update(fs.readFileSync(filePath)).digest("hex");
  if (actual !== expectedSha256) {
    throw new Error(
      `SHA-256 mismatch for ${label} (${filePath}): expected ${expectedSha256}, got ${actual}`
    );
  }
  return actual;
}

export function verifyTexTellerCache(
  variant = "q4f16",
  modelDir = MODEL_DIR,
  hfCacheDir = HF_CACHE,
  expectedHashes = PINNED_VARIANT_SHA256[variant]
) {
  const files = VARIANT_FILES[variant];
  if (!files) throw new Error(`Unsupported TexTeller variant: ${variant}`);
  if (!expectedHashes) {
    throw new Error(`No pinned SHA-256 manifest declared for TexTeller variant "${variant}"; refusing unverified execution.`);
  }
  const verified = {};
  for (const fname of files) {
    verified[fname] = verifyFileSha256(path.join(modelDir, fname), expectedHashes[fname], fname);
  }
  const tokPath = path.join(hfCacheDir, MODEL_ID, "tokenizer.json");
  verified["tokenizer.json"] = verifyFileSha256(tokPath, expectedHashes["tokenizer.json"], "tokenizer.json");
  return verified;
}

async function ensureOnnxFiles(variant) {
  const files = VARIANT_FILES[variant];
  if (!files) throw new Error(`Unsupported TexTeller variant: ${variant}`);
  const expectedHashes = PINNED_VARIANT_SHA256[variant];
  if (!expectedHashes) {
    throw new Error(`No pinned SHA-256 manifest declared for TexTeller variant "${variant}"; refusing unverified execution.`);
  }
  fs.mkdirSync(MODEL_DIR, { recursive: true });
  const sizes = {};
  for (const fname of files) {
    const dst = path.join(MODEL_DIR, fname);
    if (!fs.existsSync(dst)) {
      const url = `https://huggingface.co/${MODEL_ID}/resolve/${MODEL_REVISION}/onnx/${fname}`;
      const resp = await fetch(url, { redirect: "follow" });
      if (!resp.ok) throw new Error(`Failed downloading ${fname}: HTTP ${resp.status}`);
      const buf = Buffer.from(await resp.arrayBuffer());
      fs.writeFileSync(dst, buf);
    }
    // Always verify SHA-256 for both freshly downloaded and cached ONNX files
    verifyFileSha256(dst, expectedHashes[fname], fname);
    sizes[fname] = fs.statSync(dst).size;
  }
  return { encoderPath: path.join(MODEL_DIR, files[0]), decoderPath: path.join(MODEL_DIR, files[1]), sizes, expectedHashes };
}

function loadAndTrimGray(filePath) {
  const png = PNG.sync.read(fs.readFileSync(filePath));
  const { width, height, data } = png;
  // Estimate background from corners (exact TexTeller trim_white_border logic)
  const cornerIdx = [0, width - 1, (height - 1) * width, (height - 1) * width + (width - 1)];
  let bgR = 0, bgG = 0, bgB = 0;
  for (const idx of cornerIdx) {
    bgR += data[idx * 4];
    bgG += data[idx * 4 + 1];
    bgB += data[idx * 4 + 2];
  }
  bgR = Math.round(bgR / 4);
  bgG = Math.round(bgG / 4);
  bgB = Math.round(bgB / 4);

  let xMin = width, xMax = -1, yMin = height, yMax = -1;
  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
      const p = (y * width + x) * 4;
      const dr = Math.abs(data[p] - bgR);
      const dg = Math.abs(data[p + 1] - bgG);
      const db = Math.abs(data[p + 2] - bgB);
      const diffGray = 0.299 * dr + 0.587 * dg + 0.114 * db;
      if (diffGray > 15) {
        if (x < xMin) xMin = x;
        if (x > xMax) xMax = x;
        if (y < yMin) yMin = y;
        if (y > yMax) yMax = y;
      }
    }
  }
  if (xMax < xMin || yMax < yMin) {
    xMin = 0; yMin = 0; xMax = width - 1; yMax = height - 1;
  }
  const cw = xMax - xMin + 1;
  const ch = yMax - yMin + 1;
  const gray = new Uint8Array(cw * ch);
  for (let y = 0; y < ch; y++) {
    for (let x = 0; x < cw; x++) {
      const p = ((yMin + y) * width + (xMin + x)) * 4;
      gray[y * cw + x] = Math.round(0.299 * data[p] + 0.587 * data[p + 1] + 0.114 * data[p + 2]);
    }
  }
  return { width: cw, height: ch, gray };
}

function resizeAspectMax(img, maxSize) {
  // Mirrors torchvision Resize(size=447, max_size=448): longest side becomes 448
  const scale = Math.min((maxSize - 1) / Math.min(img.width, img.height), maxSize / Math.max(img.width, img.height));
  const newW = Math.max(1, Math.min(maxSize, Math.round(img.width * scale)));
  const newH = Math.max(1, Math.min(maxSize, Math.round(img.height * scale)));
  const out = new Float32Array(newW * newH);
  const scaleX = img.width / newW;
  const scaleY = img.height / newH;
  for (let y = 0; y < newH; y++) {
    const sy = Math.min(img.height - 1, (y + 0.5) * scaleY - 0.5);
    const y0 = Math.max(0, Math.floor(sy));
    const y1 = Math.min(img.height - 1, y0 + 1);
    const wy = Math.max(0, sy - y0);
    for (let x = 0; x < newW; x++) {
      const sx = Math.min(img.width - 1, (x + 0.5) * scaleX - 0.5);
      const x0 = Math.max(0, Math.floor(sx));
      const x1 = Math.min(img.width - 1, x0 + 1);
      const wx = Math.max(0, sx - x0);
      const v00 = img.gray[y0 * img.width + x0];
      const v10 = img.gray[y0 * img.width + x1];
      const v01 = img.gray[y1 * img.width + x0];
      const v11 = img.gray[y1 * img.width + x1];
      const v = (v00 * (1 - wx) + v10 * wx) * (1 - wy) + (v01 * (1 - wx) + v11 * wx) * wy;
      out[y * newW + x] = (v / 255.0 - IMAGE_MEAN) / IMAGE_STD;
    }
  }
  return { width: newW, height: newH, data: out };
}

function toTexTellerTensor(filePath) {
  const trimmed = loadAndTrimGray(filePath);
  const resized = resizeAspectMax(trimmed, FIXED_IMG_SIZE);
  // Zero-pad right and bottom AFTER normalization (matches TexTeller padding(images, 448))
  const padded = new Float32Array(FIXED_IMG_SIZE * FIXED_IMG_SIZE); // filled with 0.0
  for (let y = 0; y < resized.height; y++) {
    for (let x = 0; x < resized.width; x++) {
      padded[y * FIXED_IMG_SIZE + x] = resized.data[y * resized.width + x];
    }
  }
  return new ort.Tensor("float32", padded, [1, 1, FIXED_IMG_SIZE, FIXED_IMG_SIZE]);
}

async function runGreedyDecode(encoderSess, decoderSess, pixelTensor, startTokenId) {
  const encInName = encoderSess.inputNames[0];
  const encOut = await encoderSess.run({ [encInName]: pixelTensor });
  const hiddenStates = encOut[encoderSess.outputNames[0]];

  const decInputs = decoderSess.inputNames;
  const seq = [startTokenId];
  for (let step = 0; step < MAX_NEW_TOKENS; step++) {
    const feeds = {};
    for (const name of decInputs) {
      if (name === "input_ids") {
        feeds[name] = new ort.Tensor("int64", BigInt64Array.from(seq.map((v) => BigInt(v))), [1, seq.length]);
      } else if (name === "encoder_hidden_states") {
        feeds[name] = hiddenStates;
      } else if (name === "encoder_attention_mask") {
        feeds[name] = new ort.Tensor("int64", BigInt64Array.from(new Array(hiddenStates.dims[1]).fill(1n)), [1, hiddenStates.dims[1]]);
      } else if (name === "attention_mask") {
        feeds[name] = new ort.Tensor("int64", BigInt64Array.from(new Array(seq.length).fill(1n)), [1, seq.length]);
      }
    }
    const decOut = await decoderSess.run(feeds);
    const logitsTensor = decOut[decoderSess.outputNames[0]];
    const vocabSize = logitsTensor.dims[2];
    const offset = (seq.length - 1) * vocabSize;
    let bestId = 0;
    let bestVal = -Infinity;
    for (let v = 0; v < vocabSize; v++) {
      const val = logitsTensor.data[offset + v];
      if (val > bestVal) {
        bestVal = val;
        bestId = v;
      }
    }
    seq.push(bestId);
    if (bestId === EOS_TOKEN_ID) break;
    // Guard against degenerate 5-in-a-row identical token loops (still counts as hallucination failure)
    if (seq.length >= 6 && seq.slice(-5).every((t) => t === bestId)) break;
  }
  return seq;
}

async function main() {
  fs.mkdirSync(MODEL_DIR, { recursive: true });
  fs.mkdirSync(HF_CACHE, { recursive: true });
  env.cacheDir = HF_CACHE;
  env.allowLocalModels = true;
  env.allowRemoteModels = true;

  const { encoderPath, decoderPath, sizes, expectedHashes } = await ensureOnnxFiles(VARIANT);
  const cachedTokenizerPath = path.join(HF_CACHE, MODEL_ID, "tokenizer.json");
  if (fs.existsSync(cachedTokenizerPath)) {
    verifyFileSha256(cachedTokenizerPath, expectedHashes["tokenizer.json"], "tokenizer.json");
  }

  const tLoad0 = performance.now();
  const tokenizer = await AutoTokenizer.from_pretrained(MODEL_ID, { revision: MODEL_REVISION });
  verifyFileSha256(cachedTokenizerPath, expectedHashes["tokenizer.json"], "tokenizer.json");
  const encoderSess = await ort.InferenceSession.create(encoderPath, { executionProviders: ["cpu"] });
  const decoderSess = await ort.InferenceSession.create(decoderPath, { executionProviders: ["cpu"] });
  const loadMs = Math.round(performance.now() - tLoad0);

  const manifest = JSON.parse(fs.readFileSync(MANIFEST_PATH, "utf-8")).slice(0, MAX_ITEMS);

  // Upstream OleehyO/TexTeller uses GenerationConfig(bos_token_id=0, eos_token_id=2, pad_token_id=1).
  // Starting from decoder_start_token_id=2 (</s>) degrades decoding; BOS=0 (<s>) produces exact LaTeX.
  const chosenStartToken = BOS_TOKEN_ID;

  const results = [];
  let peakRssMb = 0;

  for (const item of manifest) {
    const t0 = performance.now();
    const tensor = toTexTellerTensor(item.file);
    const seq = await runGreedyDecode(encoderSess, decoderSess, tensor, chosenStartToken);
    const rawLatex = tokenizer.decode(seq, { skip_special_tokens: true }).trim();
    const elapsedMs = Math.round(performance.now() - t0);
    const rssMb = Math.round(process.memoryUsage().rss / (1024 * 1024));
    if (rssMb > peakRssMb) peakRssMb = rssMb;

    const astRes = normalizeWithAst(rawLatex, "latex_strict");
    const semanticOk = isSemanticallyEquivalent(item.expr_id, astRes);
    const granular = evaluateGranularMetrics(item.expr_id, rawLatex, astRes);

    results.push({
      filename: item.filename,
      expr_id: item.expr_id,
      category: item.category,
      variant: item.variant,
      preprocessed: Boolean(item.preprocessed),
      ground_truth: item.ground_truth_display,
      expected_canonical: item.expected_canonical,
      raw_ocr: rawLatex,
      ast_ok: astRes.ok,
      ast_error: astRes.error,
      normalized: astRes.normalized,
      semantic_exact: semanticOk,
      error_origin: classifyFailureOrigin(semanticOk, rawLatex, astRes),
      granular,
      elapsed_ms: elapsedMs,
    });
  }

  const totalModelBytes = Object.values(sizes).reduce((a, b) => a + b, 0);
  const summary = buildDetailedSummary(`onnx-community/TexTeller-ONNX (${VARIANT}, Direct onnxruntime-node CPU)`, results, {
    source_repo: "https://huggingface.co/onnx-community/TexTeller-ONNX (upstream https://github.com/OleehyO/TexTeller)",
    license_code_and_model: "Apache-2.0",
    direct_onnx_inputs: { encoder: encoderSess.inputNames, decoder: decoderSess.inputNames },
    chosen_start_token_id: chosenStartToken,
    load_ms: loadMs,
    model_files_mb: Object.fromEntries(Object.entries(sizes).map(([k, v]) => [k, Number((v / 1048576).toFixed(2))])),
    model_disk_mb: Number((totalModelBytes / 1048576).toFixed(2)),
    peak_rss_mb: peakRssMb,
  });

  fs.mkdirSync(path.dirname(OUT_FILE), { recursive: true });
  fs.writeFileSync(OUT_FILE, JSON.stringify({ summary, results }, null, 2), "utf-8");
  console.log(JSON.stringify(summary, null, 2));
}

if (process.argv[1] && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href) {
  main().catch((err) => {
    console.error(err);
    process.exit(1);
  });
}
