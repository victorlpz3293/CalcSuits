#!/usr/bin/env node
/**
 * CalcSuits Phase 4.1 — Automated OCR Dataset, Metrics & Adversarial Validation Suite
 *
 * Verifies:
 *   1. Deterministic dataset generation across distinct PYTHONHASHSEED values (byte-identical PNGs).
 *   2. Multi-resolution & multi-condition image coverage (explicitly asserting 0 real camera handwritten photos).
 *   3. Consistency & completeness of all 8 verified result JSONs + poc_results_summary.json
 *      (including granular metrics: symbols, exponents, fractions, roots, grouping).
 *   4. Deliberate negative / incorrect-recognition tests on blank, noise, and non-math text PNGs
 *      through offline TexTeller-ONNX + MathIrNode AST pipeline (verifying clean failure handling).
 */

import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import crypto from "node:crypto";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { createRequire } from "node:module";

import { loadVerifiedTexTeller } from "./run_texteller_direct_poc.mjs";
import { normalizeWithAst, isSemanticallyEquivalent } from "./math_ir_ast.mjs";
import { classifyFailureOrigin } from "./poc_metrics.mjs";

const require = createRequire("/tmp/poc_ocr_env/package.json");
const ort = require("onnxruntime-node");
const { PNG } = require("pngjs");

let passed = 0;
let failed = 0;

async function runTest(name, fn) {
  try {
    await fn();
    passed++;
    console.log(`PASS: ${name}`);
  } catch (err) {
    failed++;
    console.error(`FAIL: ${name}\n  ${err.stack || err.message}`);
  }
}

function sha256File(p) {
  return crypto.createHash("sha256").update(fs.readFileSync(p)).digest("hex");
}

function preprocessForTexTeller(pngPath) {
  const png = PNG.sync.read(fs.readFileSync(pngPath));
  const { width, height, data } = png;
  const gray = new Uint8Array(width * height);
  let xMin = width, yMin = height, xMax = -1, yMax = -1;
  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
      const idx = (y * width + x) * 4;
      const g = Math.round(0.299 * data[idx] + 0.587 * data[idx + 1] + 0.114 * data[idx + 2]);
      gray[y * width + x] = g;
      if (g < 250) {
        if (x < xMin) xMin = x;
        if (x > xMax) xMax = x;
        if (y < yMin) yMin = y;
        if (y > yMax) yMax = y;
      }
    }
  }
  if (xMax < xMin) { xMin = 0; yMin = 0; xMax = width - 1; yMax = height - 1; }
  const cw = xMax - xMin + 1;
  const ch = yMax - yMin + 1;
  const scale = Math.max(cw, ch) > 448 ? 448 / Math.max(cw, ch) : 1;
  const nw = Math.max(1, Math.round(cw * scale));
  const nh = Math.max(1, Math.round(ch * scale));
  const out = new Float32Array(448 * 448);
  const MEAN = 0.9545467;
  const STD = 0.15394445;
  for (let y = 0; y < nh; y++) {
    const sy = Math.min(ch - 1, Math.floor((y + 0.5) * (ch / nh)));
    for (let x = 0; x < nw; x++) {
      const sx = Math.min(cw - 1, Math.floor((x + 0.5) * (cw / nw)));
      const g = gray[(yMin + sy) * width + (xMin + sx)];
      out[y * 448 + x] = (g / 255.0 - MEAN) / STD;
    }
  }
  return new ort.Tensor("float32", out, [1, 1, 448, 448]);
}

async function runTexTellerSingle(loaded, pngPath) {
  const { encoderSess, decoderSess, tokenizer } = loaded;
  const pixelTensor = preprocessForTexTeller(pngPath);
  const encOut = await encoderSess.run({ [encoderSess.inputNames[0]]: pixelTensor });
  const hidden = encOut[encoderSess.outputNames[0]];
  const seq = [0];
  let repeatCount = 0;
  for (let step = 0; step < 36; step++) {
    const idsTensor = new ort.Tensor("int64", BigInt64Array.from(seq.map(BigInt)), [1, seq.length]);
    const decOut = await decoderSess.run({
      [decoderSess.inputNames[0]]: idsTensor,
      [decoderSess.inputNames[1]]: hidden,
    });
    const logits = decOut[decoderSess.outputNames[0]];
    const vocab = logits.dims[2];
    const off = (seq.length - 1) * vocab;
    let bestId = 0, bestVal = -Infinity;
    for (let v = 0; v < vocab; v++) {
      if (logits.data[off + v] > bestVal) { bestVal = logits.data[off + v]; bestId = v; }
    }
    if (seq[seq.length - 1] === bestId) repeatCount++; else repeatCount = 0;
    seq.push(bestId);
    if (bestId === 2 || repeatCount >= 5) break;
  }
  return tokenizer.decode(seq.slice(1), { skip_special_tokens: true }).trim();
}

async function main() {
  // 1. Determinism test across PYTHONHASHSEED=1 vs PYTHONHASHSEED=42
  await runTest("Dataset generation determinism: PYTHONHASHSEED=1 vs 42 produces 67/67 byte-identical PNGs", async () => {
    const dirA = fs.mkdtempSync(path.join(os.tmpdir(), "poc_det_a_"));
    const dirB = fs.mkdtempSync(path.join(os.tmpdir(), "poc_det_b_"));
    try {
      execFileSync("python3", ["poc/ocr/generate_dataset.py", dirA], {
        env: { ...process.env, PYTHONHASHSEED: "1" },
        stdio: "pipe",
      });
      execFileSync("python3", ["poc/ocr/generate_dataset.py", dirB], {
        env: { ...process.env, PYTHONHASHSEED: "42" },
        stdio: "pipe",
      });

      const manA = JSON.parse(fs.readFileSync(path.join(dirA, "manifest.json"), "utf8"));
      const manB = JSON.parse(fs.readFileSync(path.join(dirB, "manifest.json"), "utf8"));
      assert.equal(manA.length, 67);
      assert.equal(manB.length, 67);

      let identical = 0;
      for (let i = 0; i < manA.length; i++) {
        assert.equal(manA[i].filename, manB[i].filename);
        const hA = sha256File(path.join(dirA, manA[i].filename));
        const hB = sha256File(path.join(dirB, manB[i].filename));
        assert.equal(hA, hB, `Non-deterministic PNG for ${manA[i].filename}`);
        identical++;
      }
      assert.equal(identical, 67);
    } finally {
      fs.rmSync(dirA, { recursive: true, force: true });
      fs.rmSync(dirB, { recursive: true, force: true });
    }
  });

  // 2. Multi-resolution & condition coverage + explicit separation of synthetic vs real handwriting
  await runTest("Dataset coverage: multiple resolutions, lighting/tilt/noise conditions, and explicit 0 real handwritten camera photos", async () => {
    const manifestPath = "/tmp/poc_ocr_dataset/manifest.json";
    assert.ok(fs.existsSync(manifestPath), "Run generate_dataset.py into /tmp/poc_ocr_dataset first");
    const manifest = JSON.parse(fs.readFileSync(manifestPath, "utf8"));
    assert.equal(manifest.length, 67);

    const resolutions = new Set();
    const variants = new Set();
    const categories = new Map();

    for (const item of manifest) {
      const png = PNG.sync.read(fs.readFileSync(item.file));
      resolutions.add(`${png.width}x${png.height}`);
      variants.add(item.variant);
      categories.set(item.category, (categories.get(item.category) || 0) + 1);
    }

    assert.ok(resolutions.size >= 5, `Expected >=5 distinct resolutions, got: ${[...resolutions].join(", ")}`);
    for (const reqVar of [
      "printed_clean_sans",
      "printed_serif",
      "printed_large",
      "printed_mono_small",
      "printed_low_light",
      "printed_tilted",
      "printed_blur_noise",
      "handwritten_synth_clear",
      "handwritten_synth_difficult",
      "printed_2d_typeset",
    ]) {
      assert.ok(variants.has(reqVar), `Missing required variant: ${reqVar}`);
    }
    assert.equal(categories.get("printed"), 49);
    assert.equal(categories.get("printed_2d"), 4);
    assert.equal(categories.get("handwritten_synthetic"), 14);
    assert.equal(categories.get("handwritten_real_camera") || 0, 0);
    console.log(`  Verified resolutions (${resolutions.size}): ${[...resolutions].sort().join(", ")}`);
  });

  // 3. Consolidated summary & all 8 real JSON result artifacts integrity check
  await runTest("Summary & 8 result JSON artifacts: exact counts, SHA-256 digest match, and granular metrics completeness", async () => {
    const summary = JSON.parse(fs.readFileSync("poc/ocr/poc_results_summary.json", "utf8"));
    assert.equal(summary.dataset.handwritten_real_camera, 0);
    assert.equal(summary.engines_executed.length, 4);

    const digestLines = [];
    for (const eng of summary.engines_executed) {
      const baseSha = sha256File(eng.artifacts.base.file);
      const prepSha = sha256File(eng.artifacts.preprocessed.file);
      assert.equal(baseSha, eng.artifacts.base.sha256, `Stale base SHA-256 for ${eng.id}`);
      assert.equal(prepSha, eng.artifacts.preprocessed.sha256, `Stale preprocessed SHA-256 for ${eng.id}`);
      assert.equal(eng.artifacts.base.records, 67);
      assert.equal(eng.artifacts.preprocessed.records, 21);

      const g = eng.base_67.granular_metrics;
      for (const k of ["symbols", "exponents", "fractions", "roots", "grouping"]) {
        assert.ok(g[k] && typeof g[k].accuracy_pct === "number", `Missing granular metric ${k} for ${eng.id}`);
      }
      digestLines.push(`${eng.artifacts.base.file}:${baseSha}`);
      digestLines.push(`${eng.artifacts.preprocessed.file}:${prepSha}`);
    }
    const recomputedDigest = crypto.createHash("sha256").update(digestLines.join("\n")).digest("hex");
    assert.equal(recomputedDigest, summary.source_artifacts_sha256, "source_artifacts_sha256 mismatch");
  });

  // 4. Negative / incorrect-recognition tests with real offline TexTeller-ONNX + MathIrNode AST
  await runTest("Adversarial / incorrect-recognition cases: blank, random noise, and non-math text handled cleanly offline", async () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "poc_adv_"));
    try {
      // (a) Blank white image
      const blankPath = path.join(tmp, "blank_white.png");
      execFileSync("convert", ["-size", "480x140", "xc:white", "-strip", blankPath]);

      // (b) High-frequency deterministic monochrome noise
      const noisePath = path.join(tmp, "garbage_noise.png");
      execFileSync("convert", ["-seed", "999", "-size", "480x140", "xc:gray50", "+noise", "Impulse", "-colorspace", "Gray", "-strip", noisePath]);

      // (c) Non-math prose ("HELLO WORLD")
      const nonMathPath = path.join(tmp, "non_math_text.png");
      execFileSync("convert", [
        "-size", "480x140", "xc:white",
        "-font", path.resolve("app/src/main/res/font/space_grotesk.ttf"),
        "-fill", "black", "-gravity", "Center", "-pointsize", "48",
        "-annotate", "0", "HELLO WORLD", "-strip", nonMathPath,
      ]);

      const loaded = await loadVerifiedTexTeller({
        variant: "q4f16",
        modelDir: "/tmp/poc_ocr_env/texteller_onnx",
        hfCacheDir: "/tmp/poc_ocr_env/hf_cache",
      });

      for (const [label, imgPath] of [
        ["blank_white", blankPath],
        ["garbage_noise", noisePath],
        ["non_math_text", nonMathPath],
      ]) {
        const raw = await runTexTellerSingle(loaded, imgPath);
        const ast = normalizeWithAst(raw, "latex_strict");
        // Must NEVER match a valid target math expression (e.g., expr_06_linear_eq)
        const falsePositive = isSemanticallyEquivalent("expr_06_linear_eq", ast);
        assert.equal(falsePositive, false, `${label} produced false positive match! raw=${raw}`);
        const origin = classifyFailureOrigin(falsePositive, raw, ast);
        assert.notEqual(origin, "PASS", `${label} must not be classified as PASS`);
        console.log(`  Adversarial [${label}]: raw="${raw.slice(0, 60)}" ast_ok=${ast.ok} ast_error=${ast.error} origin=${origin}`);
      }
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });

  console.log(`\nOCR Validation Summary: ${passed}/${passed + failed} PASSED`);
  if (failed > 0) process.exit(1);
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
