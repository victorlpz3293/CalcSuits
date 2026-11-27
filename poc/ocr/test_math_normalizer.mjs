#!/usr/bin/env node
/**
 * CalcSuits Phase 4.1 — Isolated POC Unit Tests for the Intermediate Mathematical
 * Representation (IR) & Normalization Layer.
 */

import assert from "node:assert/strict";
import { normalizeLatexOrOcrToSolver, isSemanticallyEquivalent } from "./math_normalizer.mjs";

const cases = [
  // 7 mandatory expressions (LaTeX input from neural Math OCR)
  {
    id: "expr_01_arith_mul",
    raw: String.raw`125 + 35 \times 2`,
    mode: "latex_strict",
    expectedNorm: "125 + 35 * 2",
    expectedKind: "ARITHMETIC_EXPRESSION",
  },
  {
    id: "expr_02_div_dec",
    raw: String.raw`\frac{12.75}{0.5}`,
    mode: "latex_strict",
    expectedNorm: "12.75 / 0.5",
    expectedKind: "ARITHMETIC_EXPRESSION",
  },
  {
    id: "expr_03_poly_sq",
    raw: String.raw`x^{2} + 5x + 6`,
    mode: "latex_strict",
    expectedNorm: "x^2 + 5x + 6",
    expectedKind: "ALGEBRAIC_FUNCTION_OR_EXPRESSION",
  },
  {
    id: "expr_04_sqrt",
    raw: String.raw`\sqrt{144} + 5`,
    mode: "latex_strict",
    expectedNorm: "sqrt(144) + 5",
    expectedKind: "ARITHMETIC_EXPRESSION",
  },
  {
    id: "expr_05_grouped_frac",
    raw: String.raw`\frac{3x + 2}{5}`,
    mode: "latex_strict",
    expectedNorm: "(3x + 2) / 5",
    expectedKind: "ALGEBRAIC_FUNCTION_OR_EXPRESSION",
  },
  {
    id: "expr_06_linear_eq",
    raw: String.raw`2x + 5 = 15`,
    mode: "latex_strict",
    expectedNorm: "2x + 5 = 15",
    expectedKind: "EQUATION_IN_X",
  },
  {
    id: "expr_07_percent_es",
    raw: String.raw`15\% \text{ de } 250`,
    mode: "latex_strict",
    expectedNorm: "15% de 250",
    expectedKind: "PERCENTAGE",
  },
  // Additional structural & OCR noise edge cases
  {
    id: "expr_01_arith_mul",
    raw: "125 + 35 x 2",
    mode: "linear_ocr",
    expectedNorm: "125 + 35 * 2",
    expectedKind: "ARITHMETIC_EXPRESSION",
  },
  {
    id: "expr_03_poly_sq",
    raw: "x² + 5x + 6",
    mode: "auto",
    expectedNorm: "x^2 + 5x + 6",
    expectedKind: "ALGEBRAIC_FUNCTION_OR_EXPRESSION",
  },
  {
    id: "expr_04_sqrt",
    raw: "√144 + 5",
    mode: "auto",
    expectedNorm: "sqrt(144) + 5",
    expectedKind: "ARITHMETIC_EXPRESSION",
  },
];

let passed = 0;
for (const c of cases) {
  const res = normalizeLatexOrOcrToSolver(c.raw, c.mode);
  assert.equal(res.normalized, c.expectedNorm, `Norm mismatch for ${c.raw}: got ${res.normalized}`);
  assert.equal(res.kind, c.expectedKind, `Kind mismatch for ${c.raw}: got ${res.kind}`);
  assert.equal(isSemanticallyEquivalent(c.id, res.normalized), true, `Semantic mismatch for ${c.id}`);
  passed++;
}

// Verify nth-root conversion \sqrt[3]{27} -> (27)^(1/(3))
const cubeRoot = normalizeLatexOrOcrToSolver(String.raw`\sqrt[3]{27}`, "latex_strict");
assert.equal(cubeRoot.normalized, "(27)^(1/(3))");
passed++;

// Verify decimal comma normalization
const decComma = normalizeLatexOrOcrToSolver("12,75 ÷ 0,5", "auto");
assert.equal(decComma.normalized, "12.75 / 0.5");
passed++;

console.log(`NORMALIZER_POC_TESTS_PASSED: ${passed}/${passed}`);
