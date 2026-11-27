#!/usr/bin/env node
/**
 * CalcSuits Phase 4.1 — Isolated POC Runner: Specialized Neural Math OCR (Transformers.js ONNX Runtime CPU)
 * Used for ONNX models with full Transformers.js image processor support (e.g. Xenova/texify q8).
 * Normalizes via MathIrNode AST pipeline and persists granular metrics and latency percentiles.
 */

import fs from "node:fs";
import path from "node:path";
import { createRequire } from "node:module";
import { performance } from "node:perf_hooks";
import { normalizeWithAst, isSemanticallyEquivalent, evaluateGranularMetrics } from "./math_ir_ast.mjs";
import { buildDetailedSummary, classifyFailureOrigin } from "./poc_metrics.mjs";

const require = createRequire("/tmp/poc_ocr_env/package.json");
const { env, pipeline } = require("@huggingface/transformers");

const MODEL_ID = process.argv[2] || "Xenova/texify";
const DTYPE = process.argv[3] || "q8";
const MANIFEST_PATH = process.argv[4] || "/tmp/poc_ocr_dataset/manifest.json";
const OUT_FILE = process.argv[5] || "poc/ocr/results/texify_q8_base.json";
const MAX_ITEMS = process.argv[6] ? Number(process.argv[6]) : 999;

const HF_CACHE = "/tmp/poc_ocr_env/hf_cache";
fs.mkdirSync(HF_CACHE, { recursive: true });
env.cacheDir = HF_CACHE;
env.allowLocalModels = true;
env.allowRemoteModels = true;

const manifest = JSON.parse(fs.readFileSync(MANIFEST_PATH, "utf-8"));

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
  const tLoad0 = performance.now();
  const pipe = await pipeline("image-to-text", MODEL_ID, {
    dtype: DTYPE,
  });
  const loadMs = Math.round(performance.now() - tLoad0);

  const modelDir = path.join(HF_CACHE, MODEL_ID);
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
    load_ms: loadMs,
    model_disk_mb: Number((modelBytes / 1048576).toFixed(2)),
    peak_rss_mb: peakRssMb,
  });

  fs.mkdirSync(path.dirname(OUT_FILE), { recursive: true });
  fs.writeFileSync(OUT_FILE, JSON.stringify({ summary, results }, null, 2), "utf-8");
  console.log(JSON.stringify(summary, null, 2));
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
