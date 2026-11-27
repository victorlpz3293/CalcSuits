#!/usr/bin/env node
/**
 * CalcSuits Phase 4.1 — Isolated POC Unit Tests for MathIrNode AST, Precedence-Aware
 * Serializer, and Live Contrast against CalcSuits's real MathExpressionEvaluator.
 */

import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import fs from "node:fs";
import path from "node:path";
import { normalizeWithAst, isSemanticallyEquivalent } from "./math_ir_ast.mjs";

const astCases = [
  // 1. Precedence: -x^2 vs (-x)^2 (CRITICAL: MathExpressionEvaluator gives u- prec 4 > ^ prec 3!)
  { name: "unary minus over power -x^2", raw: "-x^2", mode: "latex_strict", expected: "-(x^2)", evalX: 2, expectedVal: -4 },
  { name: "unary minus over numeric power -2^2", raw: "-2^2", mode: "latex_strict", expected: "-(2^2)", evalX: null, expectedVal: -4 },
  { name: "grouped negative base (-x)^2", raw: "(-x)^2", mode: "latex_strict", expected: "(-x)^2", evalX: 2, expectedVal: 4 },
  { name: "grouped negative numeric (-2)^2", raw: "(-2)^2", mode: "latex_strict", expected: "(-2)^2", evalX: null, expectedVal: 4 },

  // 2. Right-associative power: 2^3^2 vs (2^3)^2
  { name: "right-associative 2^3^2", raw: "2^3^2", mode: "latex_strict", expected: "2^3^2", evalX: null, expectedVal: 512 },
  { name: "left-grouped (2^3)^2", raw: "(2^3)^2", mode: "latex_strict", expected: "(2^3)^2", evalX: null, expectedVal: 64 },
  { name: "latex nested power 2^{3^{2}}", raw: "2^{3^{2}}", mode: "latex_strict", expected: "2^3^2", evalX: null, expectedVal: 512 },

  // 3. Implicit multiplication
  { name: "implicit 5x", raw: "5x", mode: "latex_strict", expected: "5 * x", evalX: 3, expectedVal: 15 },
  { name: "implicit 2(3+4)", raw: "2(3+4)", mode: "latex_strict", expected: "2 * (3 + 4)", evalX: null, expectedVal: 14 },
  { name: "implicit (x+1)(x-1)", raw: "(x+1)(x-1)", mode: "latex_strict", expected: "(x + 1) * (x - 1)", evalX: 4, expectedVal: 15 },

  // 4. Root scope & nth roots
  { name: "sqrt scope \\sqrt{144} + 5", raw: String.raw`\sqrt{144} + 5`, mode: "latex_strict", expected: "sqrt(144) + 5", evalX: null, expectedVal: 17 },
  { name: "sqrt scope \\sqrt{144 + 5}", raw: String.raw`\sqrt{144 + 5}`, mode: "latex_strict", expected: "sqrt(144 + 5)", evalX: null, expectedVal: Math.sqrt(149) },
  { name: "unicode sqrt √144 + 5", raw: "√144 + 5", mode: "auto", expected: "sqrt(144) + 5", evalX: null, expectedVal: 17 },
  { name: "nth root \\sqrt[3]{27}", raw: String.raw`\sqrt[3]{27}`, mode: "latex_strict", expected: "27^(1 / 3)", evalX: null, expectedVal: 3 },

  // 5. Fractions & grouping
  { name: "fraction \\frac{12.75}{0.5}", raw: String.raw`\frac{12.75}{0.5}`, mode: "latex_strict", expected: "12.75 / 0.5", evalX: null, expectedVal: 25.5 },
  { name: "fraction \\frac{3x + 2}{5}", raw: String.raw`\frac{3x + 2}{5}`, mode: "latex_strict", expected: "(3 * x + 2) / 5", evalX: 6, expectedVal: 4 },
  { name: "fraction with compound denom \\frac{1}{2+x}", raw: String.raw`\frac{1}{2+x}`, mode: "latex_strict", expected: "1 / (2 + x)", evalX: 3, expectedVal: 0.2 },

  // 6. Real LaTeX OCR artifacts fixed from Texify POC
  { name: "Texify \\mathrm{~x^{2}+5x+6~}", raw: String.raw`$$\mathrm{~x^{2}+5x+6~}$$`, mode: "latex_strict", expected: "x^2 + 5 * x + 6", evalX: 2, expectedVal: 20 },
  { name: "Texify \\mathbf{x}^{2}", raw: String.raw`$$\mathbf{x}^{2}\,+\,5\mathbf{x}\,+\,6$$`, mode: "latex_strict", expected: "x^2 + 5 * x + 6", evalX: 2, expectedVal: 20 },
  { name: "Texify **X${}^{2}$ + 5X + 6**", raw: "**X${}^{2}$ + 5X + 6**", mode: "latex_strict", expected: "x^2 + 5 * x + 6", evalX: 2, expectedVal: 20 },
  { name: "Texify \\times2 boundary", raw: String.raw`$$125+35\times2$$`, mode: "latex_strict", expected: "125 + 35 * 2", evalX: null, expectedVal: 195 },
  { name: "Texify duplicate lines", raw: "(3x + 2) / 5\n\n(3x + 2) / 5", mode: "latex_strict", expected: "(3 * x + 2) / 5", evalX: 6, expectedVal: 4 },

  // 7. Equations & percentages
  { name: "linear equation 2x + 5 = 15", raw: "**2x + 5 = 15**", mode: "latex_strict", expected: "2 * x + 5 = 15", evalX: null, expectedVal: null },
  { name: "percentage 15\\% \\text{ de } 250", raw: String.raw`15\% \text{ de } 250`, mode: "latex_strict", expected: "15% de 250", evalX: null, expectedVal: null },
];

let passed = 0;
for (const tc of astCases) {
  const res = normalizeWithAst(tc.raw, tc.mode);
  assert.equal(res.ok, true, `AST parse failed for [${tc.name}] (${tc.raw}): ${res.error}`);
  assert.equal(res.normalized, tc.expected, `Serialization mismatch for [${tc.name}]: got "${res.normalized}", expected "${tc.expected}"`);
  passed++;
}

// Verify all 7 mandatory expressions pass semantic check
const mandatory7 = [
  ["expr_01_arith_mul", String.raw`125 + 35 \times 2`],
  ["expr_02_div_dec", String.raw`\frac{12.75}{0.5}`],
  ["expr_03_poly_sq", String.raw`x^{2} + 5x + 6`],
  ["expr_04_sqrt", String.raw`\sqrt{144} + 5`],
  ["expr_05_grouped_frac", String.raw`\frac{3x + 2}{5}`],
  ["expr_06_linear_eq", "2x + 5 = 15"],
  ["expr_07_percent_es", String.raw`15\% \text{ de } 250`],
];
for (const [id, raw] of mandatory7) {
  const res = normalizeWithAst(raw, "latex_strict");
  assert.equal(isSemanticallyEquivalent(id, res), true, `Mandatory expression ${id} failed semantic check: ${res.normalized}`);
  passed++;
}

console.log(`AST_POC_TESTS_PASSED: ${passed}/${passed}`);

// Live JVM contrast against CalcSuits's REAL compiled MathExpressionEvaluator (read-only classpath!)
const kotlinClassesDir = path.resolve("app/build/tmp/kotlin-classes/debug");
if (fs.existsSync(kotlinClassesDir)) {
  const gradleCache = process.env.GRADLE_USER_HOME || path.join(process.env.HOME, ".gradle");
  const kotlinStdlib = execFileSync("find", [gradleCache, "-name", "kotlin-stdlib-2.*.jar"]).toString().trim().split("\n")[0];
  if (kotlinStdlib) {
    const tmpJavaDir = "/tmp/poc_ocr_env/jvm_verify";
    fs.mkdirSync(tmpJavaDir, { recursive: true });
    const evalPairs = astCases.filter((c) => c.expectedVal !== null);
    const lines = evalPairs.map((c) => {
      const nameEsc = c.name.replace(/\\/g, "\\\\").replace(/"/g, '\\"');
      const exprEsc = c.expected.replace(/\\/g, "\\\\").replace(/"/g, '\\"');
      const call = c.evalX === null
        ? `eval.evaluate("${exprEsc}").doubleValue()`
        : `eval.evaluate("${exprEsc}", ${c.evalX}.0).doubleValue()`;
      return `check("${nameEsc}", ${call}, ${c.expectedVal});`;
    });
    // Also prove the raw precedence hazard in MathExpressionEvaluator directly:
    lines.push(`check("RAW_HAZARD_-2^2_IN_EVALUATOR", eval.evaluate("-2^2").doubleValue(), 4.0);`);
    lines.push(`check("SERIALIZED_SAFE_-(2^2)_IN_EVALUATOR", eval.evaluate("-(2^2)").doubleValue(), -4.0);`);

    const javaSrc = `
import com.calcsuits.domain.usecase.MathExpressionEvaluator;
import java.math.MathContext;
import java.math.RoundingMode;
public class VerifyMathEval {
  static int ok = 0;
  static void check(String name, double actual, double expected) {
    if (Math.abs(actual - expected) > 1e-6) {
      throw new AssertionError("FAIL " + name + ": actual=" + actual + " expected=" + expected);
    }
    ok++;
  }
  public static void main(String[] args) {
    MathExpressionEvaluator eval = new MathExpressionEvaluator(new MathContext(14, RoundingMode.HALF_UP), false);
    ${lines.join("\n    ")}
    System.out.println("REAL_JVM_MATHEVAL_VERIFIED: " + ok + "/" + ok);
  }
}
`;
    const javaFile = path.join(tmpJavaDir, "VerifyMathEval.java");
    fs.writeFileSync(javaFile, javaSrc, "utf-8");
    const cp = `${kotlinClassesDir}:${kotlinStdlib}:${tmpJavaDir}`;
    execFileSync("javac", ["-cp", cp, javaFile], { stdio: "inherit" });
    const out = execFileSync("java", ["-cp", cp, "VerifyMathEval"]).toString().trim();
    console.log(out);
  }
}
