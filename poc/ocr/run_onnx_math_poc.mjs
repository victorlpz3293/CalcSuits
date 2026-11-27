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
  "config.json": "e1c4e95573d80a1816d8d1e00fcc9f71961226369877b4fd392708fe7d2e9e23",
  "preprocessor_config.json": "b2fc6ff0098ac8e6af6e688bfc28188db883c99a48afa60e8555faeedc0c45e4",
  "generation_config.json": "0fb8980236d664d2851ed5939b435ecc6fe0e32ee08b83e75cb1743db0cfe10e",
  "tokenizer_config.json": "fd4d94f8b9dbb7deeb3a3ef084ca0e16c43d45774a180730b7e9cfd6359a074b",
};
const PINNED_MODEL_HASHES = {
  "Xenova/texify:q8": EXPECTED_TEXIFY_Q8_SHA256,
};

const HF_CACHE = "/tmp/poc_ocr_env/hf_cache";
const HF_REMOTE_HOST = "https://huggingface.co/";
const HF_REMOTE_PATH_TEMPLATE = "{model}/resolve/{revision}/";
const SHA256_HEX_RE = /^[a-f0-9]{64}$/;

export function isValidSha256Hex(value) {
  return typeof value === "string" && SHA256_HEX_RE.test(value.toLowerCase());
}

export function resolvePinnedModelConfig(modelId = MODEL_ID, dtype = DTYPE) {
  if (process.env.HF_MODEL_REVISION) {
    throw new Error(
      `HF_MODEL_REVISION override is disallowed; pinned revisions cannot be bypassed silently.`
    );
  }
  if (process.env.HF_ENDPOINT) {
    throw new Error(
      `HF_ENDPOINT override is disallowed; remote host is pinned to ${HF_REMOTE_HOST}.`
    );
  }
  const revision = PINNED_REVISIONS[modelId];
  if (!revision || revision === "main") {
    throw new Error(`Model "${modelId}" has no pinned non-main revision; refusing silent fallback to "main".`);
  }
  const expectedHashes = PINNED_MODEL_HASHES[`${modelId}:${dtype}`];
  if (!expectedHashes || Object.keys(expectedHashes).length === 0) {
    throw new Error(
      `Model "${modelId}" with dtype "${dtype}" has no pinned SHA-256 manifest; refusing unverified load.`
    );
  }
  for (const [relPath, sha] of Object.entries(expectedHashes)) {
    if (!isValidSha256Hex(sha)) {
      throw new Error(
        `Invalid or missing pinned SHA-256 for "${relPath}" (${modelId}:${dtype}); refusing unverified load.`
      );
    }
  }
  return { revision, expectedHashes };
}

/**
 * Resolves the exact filesystem cache directory used by @huggingface/transformers@3.4.0
 * FileCache (getModelFile -> fsCacheKey = pathJoin(repo_id, revision, filename) when revision !== 'main').
 */
export function getHfRevisionCacheDir(cacheDir, modelId, revision) {
  if (!revision || revision === "main") {
    throw new Error(`Refusing unpinned or "main" revision ("${revision}") for model "${modelId}".`);
  }
  return path.join(cacheDir, modelId, revision);
}

export function getHfCacheFilePath(cacheDir, modelId, revision, relPath) {
  const normalizedRel = String(relPath || "").replace(/^\/+/, "");
  if (!normalizedRel) {
    throw new Error(`Empty relative artifact path for model "${modelId}".`);
  }
  return path.join(getHfRevisionCacheDir(cacheDir, modelId, revision), normalizedRel);
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

export function assertNoUnrevisionedShadowFiles(cacheDir, modelId, expectedHashes) {
  const localModelPath = env.localModelPath || "";
  for (const relPath of Object.keys(expectedHashes)) {
    const normalizedRel = relPath.replace(/^\/+/, "");
    // In @huggingface/transformers@3.4.0 getModelFile():
    // tryCache(cache, pathJoin(env.localModelPath, modelId, filename), pathJoin(modelId, revision, filename))
    // checks cachePath BEFORE proposedCacheKey. Reject any shadow file there.
    const shadowCachePath = path.join(cacheDir, localModelPath, modelId, normalizedRel);
    if (fs.existsSync(shadowCachePath)) {
      throw new Error(
        `Unauthorized unrevisioned shadow cache file detected at "${shadowCachePath}"; refusing load.`
      );
    }
  }
}

export function verifyModelCache(
  modelRevisionDir = getHfRevisionCacheDir(HF_CACHE, "Xenova/texify", PINNED_REVISIONS["Xenova/texify"]),
  expectedHashes = EXPECTED_TEXIFY_Q8_SHA256,
  { allowMissing = false } = {}
) {
  if (!expectedHashes || Object.keys(expectedHashes).length === 0) {
    throw new Error(`Empty or missing expectedHashes manifest; refusing unverified cache.`);
  }
  const verified = {};
  for (const [relPath, expectedSha] of Object.entries(expectedHashes)) {
    if (!isValidSha256Hex(expectedSha)) {
      throw new Error(`Missing or invalid expected SHA-256 for ${relPath}; refusing unverified cache.`);
    }
    const fullPath = path.join(modelRevisionDir, relPath);
    if (allowMissing && !fs.existsSync(fullPath)) continue;
    verified[relPath] = verifyFileSha256(fullPath, expectedSha, relPath);
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
    if (!resp.ok) {
      throw new Error(`Failed downloading ${label} from ${url}: HTTP ${resp.status}`);
    }
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

export function configureVerifiedTransformersEnv(cacheDir = HF_CACHE) {
  fs.mkdirSync(cacheDir, { recursive: true });
  env.useBrowserCache = false;
  env.useCustomCache = false;
  env.useFSCache = true;
  env.cacheDir = cacheDir;
  env.allowLocalModels = false;
  env.allowRemoteModels = true;
  env.remoteHost = HF_REMOTE_HOST;
  env.remotePathTemplate = HF_REMOTE_PATH_TEMPLATE;
}

export async function ensureVerifiedHfArtifacts({
  modelId = MODEL_ID,
  revision,
  expectedHashes,
  cacheDir = HF_CACHE,
  fetchImpl = fetch,
}) {
  if (!revision || revision === "main") {
    throw new Error(`Refusing unpinned or "main" revision ("${revision}") for model "${modelId}".`);
  }
  if (!expectedHashes || Object.keys(expectedHashes).length === 0) {
    throw new Error(`Missing expectedHashes manifest for "${modelId}".`);
  }
  configureVerifiedTransformersEnv(cacheDir);
  assertNoUnrevisionedShadowFiles(cacheDir, modelId, expectedHashes);

  const verifiedHashes = {};
  const downloadedFiles = [];

  for (const [relPath, expectedSha] of Object.entries(expectedHashes)) {
    if (!isValidSha256Hex(expectedSha)) {
      throw new Error(`Missing or invalid expected SHA-256 for ${relPath}; refusing load.`);
    }
    const targetPath = getHfCacheFilePath(cacheDir, modelId, revision, relPath);
    if (fs.existsSync(targetPath)) {
      // Strictly verify cached file BEFORE Transformers.js can open or load it
      verifiedHashes[relPath] = verifyFileSha256(targetPath, expectedSha, relPath);
    } else {
      const normalizedRel = relPath.replace(/^\/+/, "");
      const url = `${HF_REMOTE_HOST}${modelId}/resolve/${encodeURIComponent(revision)}/${normalizedRel}`;
      verifiedHashes[relPath] = await downloadAndVerifyArtifact(
        url,
        targetPath,
        expectedSha,
        relPath,
        fetchImpl
      );
      downloadedFiles.push(relPath);
    }
  }

  const revisionCacheDir = getHfRevisionCacheDir(cacheDir, modelId, revision);
  verifyModelCache(revisionCacheDir, expectedHashes, { allowMissing: false });
  return { revisionCacheDir, verifiedHashes, downloadedFiles };
}

export async function loadVerifiedTexifyPipeline({
  modelId = MODEL_ID,
  dtype = DTYPE,
  cacheDir = HF_CACHE,
  fetchImpl = fetch,
  pipelineLoader = pipeline,
} = {}) {
  const { revision, expectedHashes } = resolvePinnedModelConfig(modelId, dtype);
  const { revisionCacheDir, verifiedHashes, downloadedFiles } = await ensureVerifiedHfArtifacts({
    modelId,
    revision,
    expectedHashes,
    cacheDir,
    fetchImpl,
  });

  const requestedFiles = new Set();
  const progressCallback = (evt) => {
    if (!evt || evt.status !== "initiate") return;
    if (evt.name && evt.name !== modelId) {
      throw new Error(`Unexpected repository "${evt.name}" requested during load of "${modelId}".`);
    }
    const normFile = String(evt.file || "").replace(/^\/+/, "");
    if (!normFile) return;
    requestedFiles.add(normFile);
    if (!Object.prototype.hasOwnProperty.call(expectedHashes, normFile)) {
      throw new Error(
        `Transformers.js requested undeclared artifact "${normFile}" without a pinned SHA-256; refusing unverified load.`
      );
    }
    verifyFileSha256(
      getHfCacheFilePath(cacheDir, modelId, revision, normFile),
      expectedHashes[normFile],
      normFile
    );
  };

  const tLoad0 = performance.now();
  const pipe = await pipelineLoader("image-to-text", modelId, {
    dtype,
    revision,
    cache_dir: cacheDir,
    progress_callback: progressCallback,
  });
  const loadMs = Math.round(performance.now() - tLoad0);

  // Defense-in-depth post-load verification of exact revisioned FileCache entries
  verifyModelCache(revisionCacheDir, expectedHashes, { allowMissing: false });

  return {
    pipe,
    revision,
    revisionCacheDir,
    verifiedHashes,
    downloadedFiles,
    requestedFiles: [...requestedFiles],
    loadMs,
  };
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
  const { pipe, revision, revisionCacheDir, loadMs } = await loadVerifiedTexifyPipeline({
    modelId: MODEL_ID,
    dtype: DTYPE,
    cacheDir: HF_CACHE,
  });

  const manifest = JSON.parse(fs.readFileSync(MANIFEST_PATH, "utf-8"));
  const modelBytes = dirSizeBytes(revisionCacheDir);

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
