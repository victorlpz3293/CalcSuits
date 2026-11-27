#!/usr/bin/env node
/**
 * CalcSuits Phase 4.1 — Isolated POC Runner: Tesseract 5 LSTM (via tesseract.js WASM)
 * Evaluates controlled dataset images (base or preprocessed), normalizes via the
 * MathIrNode AST pipeline, and records granular metrics, latency percentiles, and
 * engine-vs-normalizer error attribution.
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
const { createWorker } = require("tesseract.js");

const MANIFEST_PATH = process.argv[2] || "/tmp/poc_ocr_dataset/manifest.json";
const OUT_FILE = process.argv[3] || "poc/ocr/results/tesseract_base.json";
const CACHE_DIR = "/tmp/poc_ocr_env/tessdata_cache";

const TESSDATA_LANG_PATH = "https://tessdata.projectnaptha.com/4.0.0_fast";
const EXPECTED_ENG_SHA256 = "5dc5d8d640a212c9d6184921ba103b186f50e0fed9ee716c53e6b312b400d747";

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

export function verifyTesseractCache(cacheDir = CACHE_DIR, expectedSha256 = EXPECTED_ENG_SHA256) {
  const trainedDataPath = path.join(cacheDir, "eng.traineddata");
  return verifyFileSha256(trainedDataPath, expectedSha256, "eng.traineddata");
}

async function main() {
  fs.mkdirSync(CACHE_DIR, { recursive: true });
  const manifest = JSON.parse(fs.readFileSync(MANIFEST_PATH, "utf-8"));

  // Never load an unverified cached traineddata file
  const cachedTrainedData = path.join(CACHE_DIR, "eng.traineddata");
  if (fs.existsSync(cachedTrainedData)) {
    verifyTesseractCache(CACHE_DIR, EXPECTED_ENG_SHA256);
  }

  const tInit0 = performance.now();
  const worker = await createWorker("eng", 1, {
    langPath: TESSDATA_LANG_PATH,
    cachePath: CACHE_DIR,
    logger: () => {},
  });
  const initMs = Math.round(performance.now() - tInit0);

  // Verify downloaded/cached artifact immediately before any inference
  try {
    verifyTesseractCache(CACHE_DIR, EXPECTED_ENG_SHA256);
  } catch (err) {
    await worker.terminate();
    throw err;
  }

  let trainedDataBytes = 0;
  for (const f of fs.readdirSync(CACHE_DIR)) {
    const stat = fs.statSync(path.join(CACHE_DIR, f));
    if (stat.isFile()) trainedDataBytes += stat.size;
  }

  const results = [];
  let peakRssMb = 0;

  for (const item of manifest) {
    const t0 = performance.now();
    const ret = await worker.recognize(item.file);
    const elapsedMs = Math.round(performance.now() - t0);
    const rssMb = Math.round(process.memoryUsage().rss / (1024 * 1024));
    if (rssMb > peakRssMb) peakRssMb = rssMb;

    const rawText = (ret.data.text || "").trim();
    const astRes = normalizeWithAst(rawText, "linear_ocr");
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
      confidence: ret.data.confidence,
      ast_ok: astRes.ok,
      ast_error: astRes.error,
      normalized: astRes.normalized,
      semantic_exact: semanticOk,
      error_origin: classifyFailureOrigin(semanticOk, rawText, astRes),
      granular,
      elapsed_ms: elapsedMs,
    });
  }

  await worker.terminate();

  const summary = buildDetailedSummary("Tesseract 5 LSTM (eng, tessdata_fast via tesseract.js WASM)", results, {
    license_code: "Apache-2.0",
    init_ms: initMs,
    traineddata_bytes: trainedDataBytes,
    traineddata_mb: Number((trainedDataBytes / 1048576).toFixed(2)),
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
