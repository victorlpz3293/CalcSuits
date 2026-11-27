#!/usr/bin/env node
/**
 * CalcSuits Phase 4.1 — Isolated POC Runner: RapidAI/RapidLaTeXOCR (Pix2Tex ONNX)
 * Executes REAL direct ONNX Runtime CPU inference (`onnxruntime-node`) using the
 * official ONNX exports of `lukas-blecher/LaTeX-OCR` (pix2tex) published by
 * `RapidAI/RapidLaTeXOCR` (release v0.0.0: image_resizer.onnx, encoder.onnx,
 * decoder.onnx, tokenizer.json).
 *
 * Strictly isolated from Android production code; downloads models to /tmp/poc_ocr_env/.
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

const MANIFEST_PATH = process.argv[2] || "/tmp/poc_ocr_dataset/manifest.json";
const OUT_FILE = process.argv[3] || "poc/ocr/results/rapidlatexocr_results.json";
const MODEL_DIR = "/tmp/poc_ocr_env/rapidlatexocr_models";
const RELEASE_TAG = "v0.0.0"; // GitHub Release ID 112346722 (2023-07-15), exported from lukas-blecher/LaTeX-OCR@1781514fb8c92ea9f94057295fdae0e683f4648e
const BASE_URL = `https://github.com/RapidAI/RapidLaTeXOCR/releases/download/${RELEASE_TAG}`;
const MODEL_FILES = ["image_resizer.onnx", "encoder.onnx", "decoder.onnx", "tokenizer.json"];
const EXPECTED_SHA256 = {
  "image_resizer.onnx": "e0b075c39700f64d50400f39c8fc186bbb3b5d84d31864008313f376603aca9d",
  "encoder.onnx": "01bf5dc25539ca0cd5b1bd29296ea495977a6ba5f629dc4178277809d26e5e7d",
  "decoder.onnx": "bd695497bf1b22279b7626f5916c79226e1e244c84355f8da7edfd2d921d0072",
  "tokenizer.json": "1dc27b18d6a518d0d5ff3f4bb7bd98521fe80ad39e5b2a246d4109f1bb9d5019",
};

const MAX_W = 672;
const MAX_H = 192;
const MIN_W = 32;
const MIN_H = 32;
const BOS_TOKEN = 1;
const EOS_TOKEN = 2;
const MAX_SEQ_LEN = 128;

const SHA256_HEX_RE = /^[a-f0-9]{64}$/;

export function isValidSha256Hex(value) {
  return typeof value === "string" && SHA256_HEX_RE.test(value.toLowerCase());
}

export function computeFileSha256(filePath) {
  const hash = crypto.createHash("sha256");
  const fd = fs.openSync(filePath, "r");
  const buf = Buffer.allocUnsafe(4 * 1024 * 1024);
  try {
    let bytesRead = 0;
    while ((bytesRead = fs.readSync(fd, buf, 0, buf.length, null)) > 0) {
      hash.update(buf.subarray(0, bytesRead));
    }
  } finally {
    fs.closeSync(fd);
  }
  return hash.digest("hex");
}

export function verifyFileSha256(filePath, expectedSha256, label = filePath) {
  if (!isValidSha256Hex(expectedSha256)) {
    throw new Error(`Missing or invalid expected SHA-256 for ${label}; refusing unverified artifact.`);
  }
  if (!fs.existsSync(filePath)) {
    throw new Error(`Expected artifact not found for SHA-256 verification: ${filePath}`);
  }
  if (!fs.statSync(filePath).isFile()) {
    throw new Error(`Expected regular file for SHA-256 verification: ${filePath}`);
  }
  const expectedLower = expectedSha256.toLowerCase();
  const actual = computeFileSha256(filePath);
  if (actual !== expectedLower) {
    throw new Error(
      `SHA-256 mismatch for ${label} (${filePath}): expected ${expectedLower}, got ${actual}`
    );
  }
  return actual;
}

export function verifyRapidLatexOcrCache(modelDir = MODEL_DIR, expectedHashes = EXPECTED_SHA256) {
  if (!expectedHashes || Object.keys(expectedHashes).length === 0) {
    throw new Error(`Empty or missing expectedHashes manifest for RapidLaTeXOCR.`);
  }
  const verified = {};
  for (const name of MODEL_FILES) {
    const dst = path.join(modelDir, name);
    verified[name] = verifyFileSha256(dst, expectedHashes[name], name);
  }
  return verified;
}

export async function downloadAndVerifyArtifact(url, targetPath, expectedSha256, label, fetchImpl = fetch) {
  if (!isValidSha256Hex(expectedSha256)) {
    throw new Error(`Missing or invalid expected SHA-256 for ${label}; refusing download.`);
  }
  fs.mkdirSync(path.dirname(targetPath), { recursive: true });
  const tmpPath = `${targetPath}.tmp.${process.pid}.${Date.now()}`;
  try {
    const resp = await fetchImpl(url, { redirect: "follow" });
    if (!resp.ok) throw new Error(`Failed downloading ${label}: HTTP ${resp.status}`);
    const hash = crypto.createHash("sha256");
    const outFd = fs.openSync(tmpPath, "w");
    try {
      if (resp.body && typeof resp.body.getReader === "function") {
        const reader = resp.body.getReader();
        while (true) {
          const { done, value } = await reader.read();
          if (done) break;
          const chunk = Buffer.from(value.buffer, value.byteOffset, value.byteLength);
          hash.update(chunk);
          fs.writeSync(outFd, chunk);
        }
      } else {
        const buf = Buffer.from(await resp.arrayBuffer());
        hash.update(buf);
        fs.writeSync(outFd, buf);
      }
    } finally {
      fs.closeSync(outFd);
    }
    const actual = hash.digest("hex");
    const expectedLower = expectedSha256.toLowerCase();
    if (actual !== expectedLower) {
      throw new Error(
        `SHA-256 mismatch for downloaded ${label} (${url}): expected ${expectedLower}, got ${actual}`
      );
    }
    fs.renameSync(tmpPath, targetPath);
    return actual;
  } catch (err) {
    fs.rmSync(tmpPath, { force: true });
    throw err;
  }
}

export async function ensureModels({ modelDir = MODEL_DIR, fetchImpl = fetch } = {}) {
  fs.mkdirSync(modelDir, { recursive: true });
  const sizes = {};
  const downloadedFiles = [];
  for (const name of MODEL_FILES) {
    const expected = EXPECTED_SHA256[name];
    if (!isValidSha256Hex(expected)) {
      throw new Error(`No pinned SHA-256 declared for ${name}; aborting.`);
    }
    const dst = path.join(modelDir, name);
    if (fs.existsSync(dst)) {
      verifyFileSha256(dst, expected, name);
    } else {
      await downloadAndVerifyArtifact(`${BASE_URL}/${name}`, dst, expected, name, fetchImpl);
      downloadedFiles.push(name);
    }
    sizes[name] = fs.statSync(dst).size;
  }
  verifyRapidLatexOcrCache(modelDir, EXPECTED_SHA256);
  return { sizes, downloadedFiles };
}

export async function loadVerifiedRapidLatexOcr({
  modelDir = MODEL_DIR,
  fetchImpl = fetch,
  sessionFactory = (p, opts) => ort.InferenceSession.create(p, opts),
} = {}) {
  const { sizes, downloadedFiles } = await ensureModels({ modelDir, fetchImpl });
  const idToToken = loadBpeVocab(path.join(modelDir, "tokenizer.json"));
  const tLoad0 = performance.now();
  const resizerSess = await sessionFactory(path.join(modelDir, "image_resizer.onnx"), { executionProviders: ["cpu"] });
  const encoderSess = await sessionFactory(path.join(modelDir, "encoder.onnx"), { executionProviders: ["cpu"] });
  const decoderSess = await sessionFactory(path.join(modelDir, "decoder.onnx"), { executionProviders: ["cpu"] });
  const loadMs = Math.round(performance.now() - tLoad0);
  verifyRapidLatexOcrCache(modelDir, EXPECTED_SHA256);
  return { resizerSess, encoderSess, decoderSess, idToToken, modelSizes: sizes, downloadedFiles, loadMs };
}

function loadPngGray(filePath) {
  const png = PNG.sync.read(fs.readFileSync(filePath));
  const { width, height, data } = png;
  const gray = new Uint8Array(width * height);
  for (let i = 0; i < width * height; i++) {
    const r = data[i * 4];
    const g = data[i * 4 + 1];
    const b = data[i * 4 + 2];
    gray[i] = Math.round(0.299 * r + 0.587 * g + 0.114 * b);
  }
  return { width, height, gray };
}

function resizeBilinearGray(img, newW, newH) {
  const out = new Uint8Array(newW * newH);
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
      out[y * newW + x] = Math.max(0, Math.min(255, Math.round(v)));
    }
  }
  return { width: newW, height: newH, gray: out };
}

function padCropDiv32(img, divable = 32) {
  const { width, height } = img;
  let minV = 255, maxV = 0;
  for (let i = 0; i < img.gray.length; i++) {
    if (img.gray[i] < minV) minV = img.gray[i];
    if (img.gray[i] > maxV) maxV = img.gray[i];
  }
  const span = Math.max(1, maxV - minV);
  const norm = new Uint8Array(img.gray.length);
  let sum = 0;
  for (let i = 0; i < img.gray.length; i++) {
    const v = Math.round(((img.gray[i] - minV) / span) * 255);
    norm[i] = v;
    sum += v;
  }
  const mean = sum / norm.length;
  const threshold = 128;
  const darkBg = mean <= threshold;
  let xMin = width, xMax = -1, yMin = height, yMax = -1;
  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
      let v = norm[y * width + x];
      const isInk = darkBg ? v > threshold : v < threshold;
      if (darkBg) norm[y * width + x] = 255 - v;
      if (isInk) {
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
  const pw = Math.max(divable, Math.ceil(cw / divable) * divable);
  const ph = Math.max(divable, Math.ceil(ch / divable) * divable);
  const padded = new Uint8Array(pw * ph).fill(255);
  for (let y = 0; y < ch; y++) {
    for (let x = 0; x < cw; x++) {
      padded[y * pw + x] = norm[(yMin + y) * width + (xMin + x)];
    }
  }
  return { width: pw, height: ph, gray: padded };
}

function minmaxSize(img) {
  let cur = img;
  const rW = cur.width / MAX_W;
  const rH = cur.height / MAX_H;
  const maxR = Math.max(rW, rH);
  if (maxR > 1) {
    const nw = Math.max(1, Math.floor(cur.width / maxR));
    const nh = Math.max(1, Math.floor(cur.height / maxR));
    cur = resizeBilinearGray(cur, nw, nh);
  }
  const pw = Math.max(cur.width, MIN_W);
  const ph = Math.max(cur.height, MIN_H);
  if (pw !== cur.width || ph !== cur.height) {
    const padded = new Uint8Array(pw * ph).fill(255);
    for (let y = 0; y < cur.height; y++) {
      for (let x = 0; x < cur.width; x++) {
        padded[y * pw + x] = cur.gray[y * cur.width + x];
      }
    }
    cur = { width: pw, height: ph, gray: padded };
  }
  return cur;
}

function toNormalizedTensor(img) {
  const mean = 0.7931 * 255.0;
  const invStd = 1.0 / (0.1738 * 255.0);
  const f32 = new Float32Array(img.width * img.height);
  for (let i = 0; i < img.gray.length; i++) {
    f32[i] = (img.gray[i] - mean) * invStd;
  }
  return new ort.Tensor("float32", f32, [1, 1, img.height, img.width]);
}

function loadBpeVocab(tokenizerPath) {
  const tok = JSON.parse(fs.readFileSync(tokenizerPath, "utf-8"));
  const vocab = tok.model?.vocab || {};
  const idToToken = new Map();
  for (const [k, v] of Object.entries(vocab)) {
    idToToken.set(Number(v), k);
  }
  if (Array.isArray(tok.added_tokens)) {
    for (const at of tok.added_tokens) {
      idToToken.set(Number(at.id), at.content);
    }
  }
  return idToToken;
}

function decodeTokens(tokenIds, idToToken) {
  const pieces = [];
  for (const id of tokenIds) {
    if (id === BOS_TOKEN || id === EOS_TOKEN || id === 0) continue;
    const t = idToToken.get(id);
    if (t) pieces.push(t);
  }
  let s = pieces.join("").replace(/Ġ/g, " ").replace(/\[EOS\]|\[BOS\]|\[PAD\]/g, "").trim();
  // Mirror RapidLaTeXOCR post_process whitespace cleanup
  const letter = "[a-zA-Z]";
  const noletter = "[\\W_^\\d]";
  let prev;
  do {
    prev = s;
    s = s.replace(new RegExp(`(?!\\\\ )(${noletter})\\s+?(${noletter})`, "g"), "$1$2");
    s = s.replace(new RegExp(`(?!\\\\ )(${noletter})\\s+?(${letter})`, "g"), "$1$2");
    s = s.replace(new RegExp(`(${letter})\\s+?(${noletter})`, "g"), "$1$2");
  } while (s !== prev);
  return s.trim();
}

export async function recognizePngRapidLatexOcr(filePath, { resizerSess, encoderSess, decoderSess, idToToken }) {
  const resizerIn = resizerSess.inputNames[0];
  const encIn = encoderSess.inputNames[0];
  const decInNames = decoderSess.inputNames;

  const rawGray = loadPngGray(filePath);
  const baseImg = minmaxSize(padCropDiv32(rawGray));
  let r = 1.0;
  let w = baseImg.width;
  let h = baseImg.height;
  let finalTensor = null;

  // Official RapidLaTeXOCR loop_image_resizer (mirrors rapid_latex_ocr/main.py exactly)
  for (let iter = 0; iter < 10; iter++) {
    h = Math.max(1, Math.floor(h * r));
    const resized = resizeBilinearGray(baseImg, Math.max(1, Math.round(w)), h);
    const padded = padCropDiv32(minmaxSize(resized));
    finalTensor = toNormalizedTensor(padded);
    const resOut = await resizerSess.run({ [resizerIn]: finalTensor });
    const logits = resOut[resizerSess.outputNames[0]].data;
    let bestIdx = 0;
    for (let i = 1; i < logits.length; i++) {
      if (logits[i] > logits[bestIdx]) bestIdx = i;
    }
    w = (bestIdx + 1) * 32;
    if (w === padded.width) break;
    r = w / padded.width;
  }

  // Encoder
  const encOut = await encoderSess.run({ [encIn]: finalTensor });
  const context = encOut[encoderSess.outputNames[0]];

  // Autoregressive greedy decoder (matching temperature=0.00001 in LaTeXOCRInput)
  const seq = [BOS_TOKEN];
  for (let step = 0; step < MAX_SEQ_LEN; step++) {
    const xData = BigInt64Array.from(seq.map((v) => BigInt(v)));
    const maskData = new Uint8Array(seq.length).fill(1);
    const feeds = {
      [decInNames[0]]: new ort.Tensor("int64", xData, [1, seq.length]),
      [decInNames[1]]: new ort.Tensor("bool", maskData, [1, seq.length]),
      [decInNames[2]]: context,
    };
    const decOut = await decoderSess.run(feeds);
    const outTensor = decOut[decoderSess.outputNames[0]];
    const vocabSize = outTensor.dims[2];
    const offset = (seq.length - 1) * vocabSize;
    let bestId = 0;
    let bestVal = -Infinity;
    for (let v = 0; v < vocabSize; v++) {
      const val = outTensor.data[offset + v];
      if (val > bestVal) {
        bestVal = val;
        bestId = v;
      }
    }
    seq.push(bestId);
    if (bestId === EOS_TOKEN) break;
  }

  return decodeTokens(seq.slice(1), idToToken);
}

async function main() {
  const loaded = await loadVerifiedRapidLatexOcr({ modelDir: MODEL_DIR });
  const { modelSizes, loadMs } = loaded;

  const manifest = JSON.parse(fs.readFileSync(MANIFEST_PATH, "utf-8"));
  const results = [];
  let peakRssMb = 0;

  for (const item of manifest) {
    const t0 = performance.now();
    const rawLatex = await recognizePngRapidLatexOcr(item.file, loaded);
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

  const totalModelBytes = Object.values(modelSizes).reduce((a, b) => a + b, 0);
  const summary = buildDetailedSummary("RapidAI/RapidLaTeXOCR (Pix2Tex ONNX FP32 via onnxruntime-node CPU)", results, {
    source_repo: "https://github.com/RapidAI/RapidLaTeXOCR (v0.0.0 export of lukas-blecher/LaTeX-OCR)",
    license_code: "MIT",
    load_ms: loadMs,
    model_files_mb: Object.fromEntries(Object.entries(modelSizes).map(([k, v]) => [k, Number((v / 1048576).toFixed(2))])),
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
