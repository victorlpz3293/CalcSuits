#!/usr/bin/env node
/**
 * CalcSuits Phase 4.1 — Automated Offline Security & Integrity Test Suite
 *
 * Verifies ALL 4 OCR engines:
 *   1. TexTeller-ONNX (q4f16) — 4 pinned files (encoder, decoder, tokenizer.json, tokenizer_config.json)
 *   2. Xenova/texify (q8)     — 7 pinned files (encoder, decoder, tokenizer.json, config.json,
 *                               preprocessor_config.json, generation_config.json, tokenizer_config.json)
 *   3. RapidLaTeXOCR (FP32)   — 4 pinned files (image_resizer, encoder, decoder, tokenizer.json)
 *   4. Tesseract 5 LSTM       — 1 pinned file (eng.traineddata)
 *
 * Tests:
 *   - Pre-load SHA-256 verification & rejection of corrupted/missing/undeclared/shadow files
 *     strictly BEFORE model/tokenizer/worker instantiation (loaderCalls === 0).
 *   - Real 100% OFFLINE execution (globalThis.fetch + http.request + https.request hard-blocked)
 *     for TexTeller-ONNX (q4f16) and Tesseract 5 LSTM on real formula PNGs.
 */

import assert from "node:assert/strict";
import crypto from "node:crypto";
import fs from "node:fs";
import http from "node:http";
import https from "node:https";
import os from "node:os";
import path from "node:path";
import zlib from "node:zlib";
import { createRequire } from "node:module";

import {
  resolvePinnedModelConfig as resolveTexifyConfig,
  getHfRevisionCacheDir as getTexifyRevDir,
  getHfCacheFilePath as getTexifyCachePath,
  assertNoUnrevisionedShadowFiles as assertNoTexifyShadow,
  loadVerifiedTexifyPipeline,
  verifyModelCache as verifyTexifyCache,
} from "./run_onnx_math_poc.mjs";

import {
  resolvePinnedTexTellerConfig,
  getHfCacheFilePath as getTexTellerHfPath,
  assertNoUnrevisionedShadowTokenizer as assertNoTexTellerShadow,
  loadVerifiedTexTeller,
} from "./run_texteller_direct_poc.mjs";

import {
  loadVerifiedRapidLatexOcr,
  verifyRapidLatexOcrCache,
} from "./run_rapidlatexocr_poc.mjs";

import {
  ensureVerifiedTesseractModel,
  loadVerifiedTesseractWorker,
  verifyTesseractCache,
} from "./run_tesseract_poc.mjs";

import { normalizeWithAst } from "./math_ir_ast.mjs";

const require = createRequire("/tmp/poc_ocr_env/package.json");
const ort = require("onnxruntime-node");
const { PNG } = require("pngjs");
const { env: hfEnv } = require("@huggingface/transformers");

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

function makeTempDir(prefix) {
  return fs.mkdtempSync(path.join(os.tmpdir(), prefix));
}

function sha256Buf(buf) {
  return crypto.createHash("sha256").update(buf).digest("hex");
}

async function withNetworkHardBlocked(fn) {
  const origFetch = globalThis.fetch;
  const origHttpReq = http.request;
  const origHttpsReq = https.request;
  const origHttpGet = http.get;
  const origHttpsGet = https.get;
  let blockedAttempts = 0;

  const block = (api, target) => {
    blockedAttempts++;
    throw new Error(`OFFLINE_NETWORK_BLOCKED (${api}: ${String(target)})`);
  };

  globalThis.fetch = async (url) => block("fetch", url);
  http.request = (opts) => block("http.request", typeof opts === "string" ? opts : opts?.href || opts?.host);
  https.request = (opts) => block("https.request", typeof opts === "string" ? opts : opts?.href || opts?.host);
  http.get = (opts) => block("http.get", typeof opts === "string" ? opts : opts?.href || opts?.host);
  https.get = (opts) => block("https.get", typeof opts === "string" ? opts : opts?.href || opts?.host);

  try {
    const result = await fn();
    assert.equal(blockedAttempts, 0, `Expected 0 outbound network attempts, saw ${blockedAttempts}`);
    return result;
  } finally {
    globalThis.fetch = origFetch;
    http.request = origHttpReq;
    https.request = origHttpsReq;
    http.get = origHttpGet;
    https.get = origHttpsGet;
  }
}

// Helper: preprocess PNG identically to TexTeller (448x448 grayscale) for offline inference proof
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

async function main() {
  // 1. Texify: 7 pinned files (including all 4 auxiliary JSONs), corrupted auxiliary rejection, shadow rejection
  await runTest("Texify (q8): verifies all 7 pinned files and rejects corrupted auxiliary config.json BEFORE pipeline()", async () => {
    const cfg = resolveTexifyConfig("Xenova/texify", "q8");
    const modelId = "Xenova/texify";
    assert.equal(Object.keys(cfg.expectedHashes).length, 7, "Texify must pin all 7 consumed files");
    for (const reqFile of [
      "onnx/encoder_model_quantized.onnx",
      "onnx/decoder_model_merged_quantized.onnx",
      "tokenizer.json",
      "config.json",
      "preprocessor_config.json",
      "generation_config.json",
      "tokenizer_config.json",
    ]) {
      assert.ok(cfg.expectedHashes[reqFile], `Missing pinned hash for ${reqFile}`);
    }

    const tmp = makeTempDir("poc_texify_sec_");
    try {
      // (a) Corrupt first required artifact -> loadVerifiedTexifyPipeline rejects BEFORE pipelineFactory
      const corruptEnc = getTexifyCachePath(tmp, modelId, cfg.revision, "onnx/encoder_model_quantized.onnx");
      fs.mkdirSync(path.dirname(corruptEnc), { recursive: true });
      fs.writeFileSync(corruptEnc, "tampered-encoder");

      let loaderCalls = 0;
      await assert.rejects(
        () =>
          loadVerifiedTexifyPipeline({
            cacheDir: tmp,
            modelId,
            dtype: "q8",
            fetchImpl: async () => {
              throw new Error("Should not fetch corrupted local file");
            },
            pipelineFactory: async () => {
              loaderCalls++;
              return {};
            },
          }),
        /SHA-256 mismatch/i
      );
      assert.equal(loaderCalls, 0, "pipelineFactory must NOT execute on corrupted encoder");

      // (b) Corrupt auxiliary config.json with synthetic manifest where all other 6 files are valid
      const tmpAux = makeTempDir("poc_texify_aux_");
      try {
        const synHashes = {};
        for (const rel of Object.keys(cfg.expectedHashes)) {
          const p = getTexifyCachePath(tmpAux, modelId, cfg.revision, rel);
          fs.mkdirSync(path.dirname(p), { recursive: true });
          const payload = Buffer.from(`valid-content-${rel}`);
          fs.writeFileSync(p, payload);
          synHashes[rel] = sha256Buf(payload);
        }
        // Flip 1 byte in auxiliary config.json
        const auxCfgPath = getTexifyCachePath(tmpAux, modelId, cfg.revision, "config.json");
        fs.writeFileSync(auxCfgPath, Buffer.from("CORRUPTED-config.json"));
        const revDir = getTexifyRevDir(tmpAux, modelId, cfg.revision);
        assert.throws(
          () => verifyTexifyCache(revDir, synHashes),
          /SHA-256 mismatch.*config\.json/i
        );

        // (c) Shadow unrevisioned file check (exact Transformers.js localModelPath shadow)
        const shadowPath = path.join(tmpAux, hfEnv.localModelPath || "", modelId, "config.json");
        fs.mkdirSync(path.dirname(shadowPath), { recursive: true });
        fs.writeFileSync(shadowPath, "{}");
        assert.throws(() => assertNoTexifyShadow(tmpAux, modelId, synHashes), /shadow/i);
      } finally {
        fs.rmSync(tmpAux, { recursive: true, force: true });
      }
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });

  // 2. TexTeller: 4 pinned files (including tokenizer_config.json), corrupted & shadow rejection BEFORE ONNX/Tokenizer load
  await runTest("TexTeller-ONNX (q4f16): rejects corrupted tokenizer_config.json & shadow files BEFORE ONNX/Tokenizer load", async () => {
    const cfg = resolvePinnedTexTellerConfig("q4f16");
    const modelId = "onnx-community/TexTeller-ONNX";
    assert.equal(cfg.files.length, 2);
    assert.equal(Object.keys(cfg.expectedHashes).length, 4);
    assert.ok(cfg.expectedHashes["tokenizer_config.json"], "tokenizer_config.json must be pinned");

    const tmp = makeTempDir("poc_texteller_sec_");
    try {
      const modelDir = path.join(tmp, "onnx");
      const hfDir = path.join(tmp, "hf");
      const tokCfgPath = getTexTellerHfPath(hfDir, modelId, cfg.revision, "tokenizer_config.json");
      fs.mkdirSync(path.dirname(tokCfgPath), { recursive: true });
      fs.writeFileSync(tokCfgPath, '{"tampered": 1}');

      fs.mkdirSync(modelDir, { recursive: true });
      fs.writeFileSync(path.join(modelDir, "encoder_model_q4f16.onnx"), "bad");

      let sessionCalls = 0;
      let tokCalls = 0;
      await assert.rejects(
        () =>
          loadVerifiedTexTeller({
            variant: "q4f16",
            modelDir,
            hfCacheDir: hfDir,
            fetchImpl: async () => {
              throw new Error("Should reject corrupted local file before fetch");
            },
            sessionFactory: async () => {
              sessionCalls++;
              return {};
            },
            tokenizerFactory: async () => {
              tokCalls++;
              return {};
            },
          }),
        /SHA-256 mismatch/i
      );
      assert.equal(sessionCalls, 0, "ONNX session must NOT open on corrupted cache");
      assert.equal(tokCalls, 0, "AutoTokenizer must NOT open on corrupted cache");

      // Unrevisioned shadow tokenizer check (exact Transformers.js localModelPath shadow)
      const shadowTok = path.join(hfDir, hfEnv.localModelPath || "", modelId, "tokenizer.json");
      fs.mkdirSync(path.dirname(shadowTok), { recursive: true });
      fs.writeFileSync(shadowTok, "{}");
      assert.throws(() => assertNoTexTellerShadow(hfDir), /shadow/i);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });

  // 3. RapidLaTeXOCR: pre-load SHA-256 rejection of corrupted/missing file BEFORE sessionFactory
  await runTest("RapidLaTeXOCR: rejects corrupted encoder.onnx & missing files BEFORE ONNX session creation", async () => {
    const tmp = makeTempDir("poc_rapid_sec_");
    try {
      fs.writeFileSync(path.join(tmp, "image_resizer.onnx"), "corrupt-bytes");
      let sessionCalls = 0;
      await assert.rejects(
        () =>
          loadVerifiedRapidLatexOcr({
            modelDir: tmp,
            fetchImpl: async () => {
              throw new Error("Should not fetch corrupted existing file");
            },
            sessionFactory: async () => {
              sessionCalls++;
              return {};
            },
          }),
        /SHA-256 mismatch/i
      );
      assert.equal(sessionCalls, 0, "ONNX sessions must NOT open on corrupted RapidLaTeXOCR artifact");
      assert.throws(() => verifyRapidLatexOcrCache(tmp), /SHA-256 mismatch|not found/i);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });

  // 4. Tesseract 5: pre-load SHA-256 verification, corrupted rejection BEFORE createWorker, valid synthetic download flow
  await runTest("Tesseract 5: rejects corrupted eng.traineddata BEFORE createWorker() and verifies gunzipped download hash", async () => {
    const tmp = makeTempDir("poc_tess_sec_");
    try {
      const dummyRaw = Buffer.from("synthetic-traineddata-payload-v1");
      const dummySha = sha256Buf(dummyRaw);
      const dummyGz = zlib.gzipSync(dummyRaw);

      // Clean cache download with valid hash passes
      let workerCalls = 0;
      const loaded = await loadVerifiedTesseractWorker({
        cacheDir: tmp,
        expectedSha256: dummySha,
        fetchImpl: async () => ({ ok: true, status: 200, arrayBuffer: async () => dummyGz }),
        workerFactory: async (_lang, _oem, opts) => {
          workerCalls++;
          assert.equal(opts.cacheMethod, "readOnly");
          assert.equal(opts.gzip, false);
          assert.equal(opts.langPath, tmp);
          return { terminate: async () => {} };
        },
      });
      assert.equal(workerCalls, 1);
      assert.equal(loaded.downloaded, true);

      // Corrupt cached eng.traineddata -> rejected BEFORE workerFactory
      fs.writeFileSync(path.join(tmp, "eng.traineddata"), Buffer.from("tampered-payload"));
      let corruptWorkerCalls = 0;
      await assert.rejects(
        () =>
          loadVerifiedTesseractWorker({
            cacheDir: tmp,
            expectedSha256: dummySha,
            fetchImpl: async () => {
              throw new Error("Must not fetch when corrupt file exists");
            },
            workerFactory: async () => {
              corruptWorkerCalls++;
              return { terminate: async () => {} };
            },
          }),
        /SHA-256 mismatch/i
      );
      assert.equal(corruptWorkerCalls, 0, "createWorker must NOT execute on corrupted eng.traineddata");
      assert.throws(() => verifyTesseractCache(tmp, dummySha), /SHA-256 mismatch/i);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });

  // 5. REAL 100% OFFLINE INFERENCE TEST: TexTeller-ONNX (q4f16) with fetch/http/https HARD BLOCKED
  await runTest("TexTeller-ONNX (q4f16): REAL 100% OFFLINE load + OCR inference with network hard-blocked (0 outbound calls)", async () => {
    const sampleImg = "/tmp/poc_ocr_dataset/expr_06_linear_eq__printed_clean_sans.png";
    assert.ok(fs.existsSync(sampleImg), `Missing sample image: ${sampleImg}`);

    await withNetworkHardBlocked(async () => {
      const { encoderSess, decoderSess, tokenizer, downloadedFiles } = await loadVerifiedTexTeller({
        variant: "q4f16",
        modelDir: "/tmp/poc_ocr_env/texteller_onnx",
        hfCacheDir: "/tmp/poc_ocr_env/hf_cache",
      });
      assert.equal(downloadedFiles.length, 0, "Zero downloads allowed in offline mode");

      const pixelTensor = preprocessForTexTeller(sampleImg);
      const encOut = await encoderSess.run({ [encoderSess.inputNames[0]]: pixelTensor });
      const hidden = encOut[encoderSess.outputNames[0]];

      const seq = [0];
      for (let step = 0; step < 30; step++) {
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
        seq.push(bestId);
        if (bestId === 2) break;
      }

      const rawLatex = tokenizer.decode(seq.slice(1), { skip_special_tokens: true }).trim();
      const ast = normalizeWithAst(rawLatex, "latex_strict");
      assert.equal(ast.ok, true, `Expected valid AST from offline TexTeller, got: ${rawLatex} (${ast.error})`);
      assert.equal(ast.normalized, "2 * x + 5 = 15", `Unexpected offline OCR output: ${ast.normalized} (raw: ${rawLatex})`);
    });
  });

  // 6. REAL 100% OFFLINE INFERENCE TEST: Tesseract 5 with fetch/http/https HARD BLOCKED
  await runTest("Tesseract 5: REAL 100% OFFLINE worker load + OCR inference with network hard-blocked (0 outbound calls)", async () => {
    const sampleImg = "/tmp/poc_ocr_dataset/expr_01_arith_mul__printed_clean_sans.png";
    assert.ok(fs.existsSync(sampleImg), `Missing sample image: ${sampleImg}`);

    // Prime verified cache once (before offline phase)
    await ensureVerifiedTesseractModel({ cacheDir: "/tmp/poc_ocr_env/tessdata_cache" });

    await withNetworkHardBlocked(async () => {
      const { worker, downloaded } = await loadVerifiedTesseractWorker({
        cacheDir: "/tmp/poc_ocr_env/tessdata_cache",
      });
      try {
        assert.equal(downloaded, false, "Zero downloads allowed in offline mode");
        const ret = await worker.recognize(sampleImg);
        const text = (ret.data.text || "").trim();
        assert.ok(text.length > 0, "Offline Tesseract returned empty text");
        assert.ok(text.includes("125") && text.includes("35"), `Expected 125 and 35 in offline Tesseract output, got: ${text}`);
      } finally {
        await worker.terminate();
      }
    });
  });

  console.log(`\nOffline Integrity Summary: ${passed}/${passed + failed} PASSED`);
  if (failed > 0) process.exit(1);
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
