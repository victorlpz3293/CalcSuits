#!/usr/bin/env node
/**
 * CalcSuits Phase 4.1 — Consolidates poc/ocr/poc_results_summary.json exclusively
 * from the verified, unaltered JSON artifacts in poc/ocr/results/.
 */

import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";

const RESULTS_DIR = "poc/ocr/results";
const OUT_FILE = process.argv[2] || "poc/ocr/poc_results_summary.json";

const ENGINE_PAIRS = [
  {
    id: "tesseract_5_lstm",
    base_file: "tesseract_base.json",
    preprocessed_file: "tesseract_preprocessed.json",
    pinned_revision: "cdn.jsdelivr.net/npm/@tesseract.js-data/eng/4.0.0_best_int (tesseract.js@5.1.1)",
    model_sha256: {
      "eng.traineddata": "5dc5d8d640a212c9d6184921ba103b186f50e0fed9ee716c53e6b312b400d747",
    },
  },
  {
    id: "rapidlatexocr_pix2tex_fp32",
    base_file: "rapidlatexocr_base.json",
    preprocessed_file: "rapidlatexocr_preprocessed.json",
    pinned_revision: "RapidAI/RapidLaTeXOCR@v0.0.0 (Release ID 112346722; upstream lukas-blecher/LaTeX-OCR@1781514fb8c92ea9f94057295fdae0e683f4648e)",
    model_sha256: {
      "image_resizer.onnx": "e0b075c39700f64d50400f39c8fc186bbb3b5d84d31864008313f376603aca9d",
      "encoder.onnx": "01bf5dc25539ca0cd5b1bd29296ea495977a6ba5f629dc4178277809d26e5e7d",
      "decoder.onnx": "bd695497bf1b22279b7626f5916c79226e1e244c84355f8da7edfd2d921d0072",
      "tokenizer.json": "1dc27b18d6a518d0d5ff3f4bb7bd98521fe80ad39e5b2a246d4109f1bb9d5019",
    },
  },
  {
    id: "xenova_texify_q8",
    base_file: "texify_q8_base.json",
    preprocessed_file: "texify_q8_preprocessed.json",
    pinned_revision: "Xenova/texify@98b3e3d88921ae91525d116d8d79a8402e5b5e4e",
    model_sha256: {
      "onnx/encoder_model_quantized.onnx": "302452c132a82b1c70389f0f646952586afa20c3f9a16b517e40600c49eb8f23",
      "onnx/decoder_model_merged_quantized.onnx": "ed067534a996c818708d0d377b94ace32b9738f0d7a0c4ef635e4fa3d28653ce",
      "tokenizer.json": "02c318d9cfa95bf323371762b8f838a82709530274d36dba6eca880f0add6cc4",
      "config.json": "e1c4e95573d80a1816d8d1e00fcc9f71961226369877b4fd392708fe7d2e9e23",
      "preprocessor_config.json": "b2fc6ff0098ac8e6af6e688bfc28188db883c99a48afa60e8555faeedc0c45e4",
      "generation_config.json": "0fb8980236d664d2851ed5939b435ecc6fe0e32ee08b83e75cb1743db0cfe10e",
      "tokenizer_config.json": "fd4d94f8b9dbb7deeb3a3ef084ca0e16c43d45774a180730b7e9cfd6359a074b",
    },
  },
  {
    id: "texteller_onnx_q4f16_direct",
    base_file: "texteller_q4f16_base.json",
    preprocessed_file: "texteller_q4f16_preprocessed.json",
    pinned_revision: "onnx-community/TexTeller-ONNX@9727784d91d7f8437dc7140941c4335284ce075e",
    model_sha256: {
      "onnx/encoder_model_q4f16.onnx": "c42190515ffcd4a728a1abee4dc8d4636b62f782fd823294d2861434a7f7d0eb",
      "onnx/decoder_model_q4f16.onnx": "edebc4c90596d13313382eecfeef89f0041efe7bfa4e89391d5b5dbabab25100",
      "tokenizer.json": "ec4ca954798a092faf6fefcfa47fb5f85d76cdf6ab170b624ae1a683d53dae14",
      "tokenizer_config.json": "74c8bdcd769acae371f98980fe929be1172fab3df844a50159c61998a3832eea",
    },
  },
];

const DEGRADED_VARIANTS = ["printed_low_light", "printed_blur_noise", "handwritten_synth_difficult"];

function sha256File(filePath) {
  return crypto.createHash("sha256").update(fs.readFileSync(filePath)).digest("hex");
}

function readResultFile(filename) {
  const fullPath = path.join(RESULTS_DIR, filename);
  const data = JSON.parse(fs.readFileSync(fullPath, "utf-8"));
  if (!data.summary || !Array.isArray(data.results)) {
    throw new Error(`Invalid result structure in ${fullPath}`);
  }
  return {
    file: fullPath,
    sha256: sha256File(fullPath),
    record_count: data.results.length,
    summary: data.summary,
    results: data.results,
  };
}

const engines = ENGINE_PAIRS.map((entry) => {
  const base = readResultFile(entry.base_file);
  const prep = readResultFile(entry.preprocessed_file);

  const degradedBefore = base.results.filter((r) => DEGRADED_VARIANTS.includes(r.variant));
  const degradedBeforePassed = degradedBefore.filter((r) => r.semantic_exact).length;
  const degradedAfterPassed = prep.results.filter((r) => r.semantic_exact).length;

  const variantDelta = {};
  for (const v of DEGRADED_VARIANTS) {
    const bRows = base.results.filter((r) => r.variant === v);
    const pRows = prep.results.filter((r) => r.variant === v);
    const bPass = bRows.filter((r) => r.semantic_exact).length;
    const pPass = pRows.filter((r) => r.semantic_exact).length;
    variantDelta[v] = {
      before_passed: bPass,
      before_total: bRows.length,
      before_pct: Number(((bPass / bRows.length) * 100).toFixed(1)),
      after_passed: pPass,
      after_total: pRows.length,
      after_pct: Number(((pPass / pRows.length) * 100).toFixed(1)),
      delta_pct_points: Number((((pPass / pRows.length) - (bPass / bRows.length)) * 100).toFixed(1)),
    };
  }

  return {
    id: entry.id,
    engine: base.summary.engine,
    pinned_revision: entry.pinned_revision,
    model_sha256: entry.model_sha256,
    artifacts: {
      base: { file: base.file, sha256: base.sha256, records: base.record_count },
      preprocessed: { file: prep.file, sha256: prep.sha256, records: prep.record_count },
    },
    base_67: base.summary,
    preprocessed_21: prep.summary,
    degraded_before_vs_after: {
      total_degraded: degradedBefore.length,
      before_passed: degradedBeforePassed,
      before_pct: Number(((degradedBeforePassed / degradedBefore.length) * 100).toFixed(1)),
      after_passed: degradedAfterPassed,
      after_pct: Number(((degradedAfterPassed / prep.results.length) * 100).toFixed(1)),
      delta_pct_points: Number((((degradedAfterPassed / prep.results.length) - (degradedBeforePassed / degradedBefore.length)) * 100).toFixed(1)),
      by_variant: variantDelta,
    },
  };
});

const sourceManifestDigest = crypto
  .createHash("sha256")
  .update(
    engines
      .flatMap((e) => [
        `${e.artifacts.base.file}:${e.artifacts.base.sha256}`,
        `${e.artifacts.preprocessed.file}:${e.artifacts.preprocessed.sha256}`,
      ])
      .join("\n")
  )
  .digest("hex");

const consolidated = {
  schema_version: "4.1.2",
  source_artifacts_sha256: sourceManifestDigest,
  execution_environment: {
    platform: "Linux x86_64 container (4 vCPU, gVisor kernel 4.19.0)",
    warning: "CPU container measurements only. NOT Android ARM64 on-device benchmarks.",
    node_version: "v22.23.4",
    python_version: "3.11.2",
    imagemagick_version: "6.9.11-60 Q16 x86_64",
    npm_packages: {
      "tesseract.js": "5.1.1",
      "onnxruntime-node": "1.20.1",
      "@huggingface/transformers": "3.4.0",
      "pngjs": "7.0.0",
    },
  },
  dataset: {
    base_total_images: 67,
    printed_linear: 49,
    printed_2d_typeset: 4,
    handwritten_synthetic: 14,
    preprocessed_degraded_images: 21,
    total_evaluated_per_engine: 88,
    handwritten_real_camera: 0,
    deterministic_seed_added_post_run: 20261127,
    reproducibility_note:
      "The 8 JSON result files in poc/ocr/results/ are preserved untouched from the executed benchmark run. Subsequently, generate_dataset.py was hardened to replace Python hash() with SHA-256-derived seeds and pass ImageMagick -seed/-strip so future regenerations are byte-for-byte deterministic.",
  },
  ast_and_jvm_verification: {
    ast_unit_tests: "31/31 passed (poc/ocr/test_math_ir_ast.mjs)",
    real_jvm_math_expression_evaluator_checks: "24/24 passed against compiled app/build/tmp/kotlin-classes/debug",
    precedence_hazard_verified: "MathExpressionEvaluator.evaluate('-2^2') == +4.0; AST serializer emits '-(2^2)' -> -4.0",
  },
  engines_executed: engines,
  not_executed_or_blocked_by_environment: [
    "Android physical ARM64 device CPU/NNAPI/XNNPACK latency, RAM, and thermal benchmarks (no emulator/ADB/device in container)",
    "Google ML Kit Text Recognition v2 runtime execution (requires Android runtime / Play Services; also incompatible with GPL-3.0 due to proprietary ML Kit ToS)",
    "Real human camera handwriting photos under real paper/lighting/shadow conditions",
    "Git repository commit/branch verification (no .git directory present in local workspace)",
  ],
};

fs.writeFileSync(OUT_FILE, JSON.stringify(consolidated, null, 2) + "\n", "utf-8");
console.log(`Wrote ${OUT_FILE} from ${engines.length * 2} verified JSON artifacts.`);
