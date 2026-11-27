#!/usr/bin/env node
/**
 * CalcSuits Phase 4.1 — Isolated POC Runner: Specialized Neural Math OCR (Transformers.js ONNX Runtime CPU)
 * Used for ONNX models with full Transformers.js image processor support (e.g. Xenova/texify q8).
 * Normalizes via MathIrNode AST pipeline and persists granular metrics and latency percentiles.
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
const { env, pipeline } = require("@huggingface/transformers");

const MODEL_ID = process.argv[2] || "Xenova/texify";
const DTYPE = process.argv[3] || "q8";
const MANIFEST_PATH = process.argv[4] || "/tmp/poc_ocr_dataset/manifest.json";
const OUT_FILE = process.argv[5] || "poc/ocr/results/texify_q8_base.json";
const MAX_ITEMS = process.argv[6] ? Number(process.argv[6]) : 999;

const PINNED_REVISIONS = {
  "Xenova/texify": "98b3e3d88921ae91525d116d8d79a8402e5b5e4e",
};
const EXPECTED_TEXIFY_Q8_SHA256 = {
  "onnx/encoder_model_quantized.onnx": "302452c132a82b1c70389f0f646952586afa20c3f9a16b517e40600c49eb8f23",
  "onnx/decoder_model_merged_quantized.onnx": "ed067534a996c818708d0d377b94ace32b9738f0d7a0c4ef635e4fa3d28653ce",
  "tokenizer.json": "02c318d9cfa95bf323371762b8f838a82709530274d36dba6eca880f0add6cc4",
};
const PINNED_MODEL_HASHES = {
  "Xenova/texify:q8": EXPECTED_TEXIFY_Q8_SHA256,
};

const HF_CACHE = "/tmp/poc_ocr_env/hf_cache";

export function resolvePinnedModelConfig(modelId = MODEL_ID, dtype = DTYPE) {
  if (process.env.HF_MODEL_REVISION) {
    throw new Error(
      `HF_MODEL_REVISION override is disallowed; pinned revisions cannot be bypassed silently.`
    );
  }
  const revision = PINNED_REVISIONS[modelId];
  if (!revision) {
    throw new Error(`Model "${modelId}" has no pinned revision; refusing silent fallback to "main".`);
  }
  const expectedHashes = PINNED_MODEL_HASHES[`${modelId}:${dtype}`];
  if (!expectedHashes) {
    throw new Error(
      `Model "${modelId}" with dtype "${dtype}" has no pinned SHA-256 manifest; refusing unverified load.`
    );
  }
  return { revision, expectedHashes };
}

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

export function verifyModelCache(
  modelDir = path.join(HF_CACHE, "Xenova/texify"),
  expectedHashes = EXPECTED_TEXIFY_Q8_SHA256,
  { allowMissing = false } = {}
) {
  const verified = {};
  for (const [relPath, expectedSha] of Object.entries(expectedHashes)) {
    const fullPath = path.join(modelDir, relPath);
    if (allowMissing && !fs.existsSync(fullPath)) continue;
    verified[relPath] = verifyFileSha256(fullPath, expectedSha, relPath);
  }
  return verified;
}

function dirSizeBytes(dir) {
  let total = 0;
  if (!fs.existsSync(dir)) return 0;
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, entry.name);
    if (entry.isDirectory()) total += dirSizeBytes(p);
    else if (entry.isFile()) total += fs.statSync(p).size;
  }
  return total;
}

async function main() {
  const { revision, expectedHashes } = resolvePinnedModelConfig(MODEL_ID, DTYPE);
  fs.mkdirSync(HF_CACHE, { recursive: true });
  env.cacheDir = HF_CACHE;
  env.allowLocalModels = true;
  env.allowRemoteModels = true;

  const modelDir = path.join(HF_CACHE, MODEL_ID);
  // Reject any corrupted/tampered cached files BEFORE Transformers.js loads them
  verifyModelCache(modelDir, expectedHashes, { allowMissing: true });

  const manifest = JSON.parse(fs.readFileSync(MANIFEST_PATH, "utf-8"));

  const tLoad0 = performance.now();
  const pipe = await pipeline("image-to-text", MODEL_ID, {
    dtype: DTYPE,
    revision,
  });
  const loadMs = Math.round(performance.now() - tLoad0);

  // Strictly verify every required file exists and matches its pinned SHA-256 before inference
  verifyModelCache(modelDir, expectedHashes, { allowMissing: false });

  const modelBytes = dirSizeBytes(modelDir);

  const subset = manifest.slice(0, MAX_ITEMS);
  const results = [];
  let peakRssMb = 0;

  for (const item of subset) {
    const t0 = performance.now();
    const out = await pipe(item.file, { max_new_tokens: 64 });
    const elapsedMs = Math.round(performance.now() - t0);
    const rssMb = Math.round(process.memoryUsage().rss / (1024 * 1024));
    if (rssMb > peakRssMb) peakRssMb = rssMb;

    const rawText = (Array.isArray(out) ? out[0]?.generated_text : out?.generated_text) || "";
    const astRes = normalizeWithAst(rawText, "latex_strict");
    const semanticOk = isSemanticallyEquivalent(item.expr_id, astRes);
    const granular = evaluateGranularMetrics(item.expr_id, rawText, astRes);

    results.push({
      filename: item.filename,
      expr_id: item.expr_id,
      category: item.category,
      variant: item.variant,
      preprocessed: Boolean(item.preprocessed),
      ground_truth: item.ground_truth_display,
      expected_canonical: item.expected_canonical,
      raw_ocr: rawText,
      ast_ok: astRes.ok,
      ast_error: astRes.error,
      normalized: astRes.normalized,
      semantic_exact: semanticOk,
      error_origin: classifyFailureOrigin(semanticOk, rawText, astRes),
      granular,
      elapsed_ms: elapsedMs,
    });
  }

  const summary = buildDetailedSummary(`${MODEL_ID} (${DTYPE}, ONNX Runtime CPU via Transformers.js)`, results, {
    license_model: MODEL_ID === "Xenova/texify" ? "CC-BY-SA-4.0 (weights) / GPL-3.0 (upstream VikParuchuri/texify code)" : "See upstream repo",
    model_revision: revision,
    load_ms: loadMs,
    model_disk_mb: Number((modelBytes / 1048576).toFixed(2)),
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
