/**
 * CalcSuits Phase 4.1 — Isolated POC: Intermediate Mathematical Representation (IR)
 * and Normalization Layer (OCR raw / LaTeX -> MathIr -> CalcSuits Solver Expression).
 *
 * Strictly isolated from production app code; does NOT modify MathExpressionEvaluator.
 */

export function normalizeLatexOrOcrToSolver(rawInput, mode = "auto") {
  if (!rawInput || typeof rawInput !== "string") {
    return { normalized: "", kind: "EMPTY", warnings: ["empty_input"] };
  }

  const warnings = [];
  let s = rawInput.trim();

  // If the engine wrapped the primary math expression in $$...$$ or $...$, extract that block
  // (neural LaTeX OCR models occasionally emit trailing list artifacts like "\n\n1." outside $$...$$).
  const displayBlock = s.match(/\$\$([\s\S]*?)\$\$/);
  if (displayBlock) {
    s = displayBlock[1].trim();
  } else {
    const inlineBlock = s.match(/\$([^$]+)\$/);
    if (inlineBlock && s.startsWith("$")) {
      s = inlineBlock[1].trim();
    } else {
      s = s.replace(/^\$\$|\$\$$/g, "").replace(/^\$|\$$/g, "");
      s = s.replace(/^\\\[|\\\]$/g, "").replace(/^\\\(|\\\)$/g, "");
    }
  }

  // Normalize line breaks: stacked lines in linear OCR cannot reliably become fractions automatically
  if (/\r?\n/.test(s)) {
    const lines = s.split(/\r?\n/).map((l) => l.trim()).filter(Boolean);
    if (lines.length > 1) {
      warnings.push("multiline_ocr_fragment");
    }
    s = lines.join(" ");
  }

  // Unwrap LaTeX text/formatting macros
  s = s.replace(/\\(?:mathrm|text|textbf|mathit|operatorname|rm|bf)\s*\{([^}]*)\}/g, " $1 ");
  s = s.replace(/\\(?:left|right|big|Big|bigg|Bigg)(?![a-zA-Z])/g, "");
  s = s.replace(/\\ |\\,|\\;|\\:|\\!|\\quad(?![a-zA-Z])|\\qquad(?![a-zA-Z])/g, " ");

  // Normalize LaTeX operators & symbols (control sequences end before any non-letter, including digits e.g. \times2)
  s = s.replace(/\\times(?![a-zA-Z])|\\cdot(?![a-zA-Z])|\\ast(?![a-zA-Z])/g, " * ");
  s = s.replace(/\\div(?![a-zA-Z])/g, " / ");
  s = s.replace(/\\pm(?![a-zA-Z])/g, "±");
  s = s.replace(/\\%/g, "%");
  s = s.replace(/\\pi(?![a-zA-Z])/g, "pi");

  // Normalize Unicode math operators & superscripts
  s = s.replace(/[×✕✖·∙]/g, "*");
  s = s.replace(/[÷∶]/g, "/");
  s = s.replace(/[−–—]/g, "-");
  s = s.replace(/π/g, "pi");

  // Unicode superscripts -> ^n
  const supMap = {
    "⁰": "0", "¹": "1", "²": "2", "³": "3", "⁴": "4",
    "⁵": "5", "⁶": "6", "⁷": "7", "⁸": "8", "⁹": "9",
  };
  s = s.replace(/[⁰¹²³⁴⁵⁶⁷⁸⁹]+/g, (match) => {
    const digits = [...match].map((c) => supMap[c]).join("");
    return digits.length === 1 ? `^${digits}` : `^(${digits})`;
  });

  // Convert LaTeX fractions \frac{num}{den} (supports nested braces iteratively)
  s = convertLatexFractions(s);

  // Convert LaTeX nth roots \sqrt[n]{x} and square roots \sqrt{x}
  s = convertLatexRoots(s);

  // Convert Unicode square root √144 or √(x+1) -> sqrt(...)
  s = s.replace(/√\s*\(([^)]+)\)/g, "sqrt($1)");
  s = s.replace(/√\s*([0-9]+(?:\.[0-9]+)?|[a-zA-Z])/g, "sqrt($1)");

  // Convert LaTeX exponents ^{...} -> ^... or ^(...)
  s = s.replace(/\^\s*\{([^{}]+)\}/g, (_, exp) => {
    const t = exp.trim();
    return /^[0-9]+$|^[a-zA-Z]$/.test(t) ? `^${t}` : `^(${t})`;
  });

  // Remove remaining harmless LaTeX braces
  s = s.replace(/[{}]/g, "");

  // Normalize decimal commas only when between digits (e.g. 12,75 -> 12.75)
  s = s.replace(/(\d),(\d)/g, "$1.$2");

  // Context-aware disambiguation for linear OCR: "35 x 2" (spaces around x between numbers) -> "35 * 2"
  if (mode !== "latex_strict") {
    s = s.replace(/(\d)\s+[xX]\s+(\d)/g, "$1 * $2");
  }

  // Collapse whitespace and standardize around binary operators for canonical comparison
  s = s.replace(/\s+/g, " ").trim();

  // Classify solver compatibility
  const pctMatch = /^(-?\d+(?:\.\d+)?)\s*%\s*(?:de|of)\s*(-?\d+(?:\.\d+)?)$/i.test(s);
  const hasEquals = s.includes("=");
  const hasVarX = /[a-zA-Z]/.test(
    s.replace(/\b(sin|cos|tan|asin|acos|atan|sinh|cosh|tanh|log|ln|sqrt|abs|exp|fact|pi|mod|de|of)\b/gi, "")
  );

  let kind = "ARITHMETIC_EXPRESSION";
  if (pctMatch) {
    kind = "PERCENTAGE";
  } else if (hasEquals && /\bx\b/i.test(s) || (hasEquals && /\d[xX]|[xX]\^/.test(s))) {
    kind = "EQUATION_IN_X";
  } else if (hasEquals) {
    kind = "UNSUPPORTED_EQUATION";
  } else if (hasVarX) {
    kind = "ALGEBRAIC_FUNCTION_OR_EXPRESSION";
  }

  return { normalized: s, kind, warnings };
}

function extractBraceGroup(str, startIdx) {
  if (str[startIdx] !== "{") return null;
  let depth = 0;
  for (let i = startIdx; i < str.length; i++) {
    if (str[i] === "{") depth++;
    else if (str[i] === "}") {
      depth--;
      if (depth === 0) {
        return { content: str.slice(startIdx + 1, i), endIdx: i };
      }
    }
  }
  return null;
}

function convertLatexFractions(input) {
  let s = input;
  let guard = 0;
  while (s.includes("\\frac") && guard++ < 20) {
    const idx = s.indexOf("\\frac");
    let pos = idx + 5;
    while (pos < s.length && s[pos] === " ") pos++;
    const num = extractBraceGroup(s, pos);
    if (!num) break;
    pos = num.endIdx + 1;
    while (pos < s.length && s[pos] === " ") pos++;
    const den = extractBraceGroup(s, pos);
    if (!den) break;
    const nTrim = num.content.trim();
    const dTrim = den.content.trim();
    const nFmt = /[+\-*/ ]/.test(nTrim) ? `(${nTrim})` : nTrim;
    const dFmt = /[+\-*/ ]/.test(dTrim) ? `(${dTrim})` : dTrim;
    s = s.slice(0, idx) + `${nFmt} / ${dFmt}` + s.slice(den.endIdx + 1);
  }
  return s;
}

function convertLatexRoots(input) {
  let s = input;
  let guard = 0;
  while (s.includes("\\sqrt") && guard++ < 20) {
    const idx = s.indexOf("\\sqrt");
    let pos = idx + 5;
    while (pos < s.length && s[pos] === " ") pos++;
    let degree = null;
    if (s[pos] === "[") {
      const closeBracket = s.indexOf("]", pos + 1);
      if (closeBracket !== -1) {
        degree = s.slice(pos + 1, closeBracket).trim();
        pos = closeBracket + 1;
        while (pos < s.length && s[pos] === " ") pos++;
      }
    }
    const rad = extractBraceGroup(s, pos);
    if (!rad) break;
    const replacement = degree
      ? `(${rad.content.trim()})^(1/(${degree}))`
      : `sqrt(${rad.content.trim()})`;
    s = s.slice(0, idx) + replacement + s.slice(rad.endIdx + 1);
  }
  return s;
}

/**
 * Strict mathematical semantic equivalence check (NOT visual similarity).
 * Returns true ONLY if the normalized expression preserves exact mathematical meaning.
 */
export function isSemanticallyEquivalent(exprId, normalized) {
  const compact = normalized.replace(/\s+/g, "").toLowerCase();
  const spaceNorm = normalized.replace(/\s+/g, " ").trim().toLowerCase();

  switch (exprId) {
    case "expr_01_arith_mul":
      return compact === "125+35*2";
    case "expr_02_div_dec":
      return compact === "12.75/0.5" || compact === "(12.75)/(0.5)";
    case "expr_03_poly_sq":
      return compact === "x^2+5x+6" || compact === "x^2+5*x+6" || compact === "x^(2)+5x+6";
    case "expr_04_sqrt":
      return compact === "sqrt(144)+5";
    case "expr_05_grouped_frac":
      return compact === "(3x+2)/5" || compact === "(3*x+2)/5" || compact === "(3x+2)/(5)";
    case "expr_06_linear_eq":
      return compact === "2x+5=15" || compact === "2*x+5=15";
    case "expr_07_percent_es":
      return spaceNorm === "15% de 250" || spaceNorm === "15 % de 250";
    default:
      return false;
  }
}
