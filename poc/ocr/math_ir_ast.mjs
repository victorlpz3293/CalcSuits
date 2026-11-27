/**
 * CalcSuits Phase 4.1 — Isolated POC: Real AST-based Mathematical Intermediate
 * Representation (MathIrNode), Recursive-Descent LaTeX/OCR Parser, and
 * Precedence-Aware Serializer compatible with CalcSuits's MathExpressionEvaluator.
 *
 * CRITICAL PRECEDENCE NOTE (verified in MathExpressionEvaluator.kt lines 40-49):
 *   precedence = { "+":1, "-":1, "*":2, "/":2, "%":2, "^":3, "u-":4 }
 *   Because unary minus ("u-") has HIGHER precedence (4) than exponentiation ("^", 3),
 *   "-x^2" in MathExpressionEvaluator evaluates as "(-x)^2".
 *   Therefore, UnaryOp("-", Power(base, exp)) MUST be serialized as "-(base^exp)"
 *   to preserve standard mathematical semantics (-4 for x=2), whereas
 *   Power(UnaryOp("-", x), 2) is serialized as "(-x)^2" (+4 for x=2).
 */

// ============================================================================
// 1. MathIrNode AST Model
// ============================================================================
export const MathIr = {
  number: (raw) => ({ type: "NumberLiteral", raw }),
  variable: (name) => ({ type: "Variable", name }),
  constant: (symbol) => ({ type: "Constant", symbol }),
  unary: (op, operand) => ({ type: "UnaryOp", op, operand }),
  binary: (op, left, right) => ({ type: "BinaryOp", op, left, right }),
  power: (base, exponent) => ({ type: "Power", base, exponent }),
  fraction: (numerator, denominator) => ({ type: "Fraction", numerator, denominator }),
  root: (radicand, index = null) => ({ type: "Root", radicand, index }),
  group: (inner) => ({ type: "Group", inner }),
  func: (name, argument) => ({ type: "FunctionCall", name, argument }),
  percentOf: (percent, base, keyword = "de") => ({ type: "PercentageOf", percent, base, keyword }),
  equation: (lhs, rhs) => ({ type: "Equation", lhs, rhs }),
};

const KNOWN_FUNCTIONS = new Set([
  "sin", "cos", "tan", "asin", "acos", "atan",
  "sinh", "cosh", "tanh", "log", "ln", "sqrt", "abs", "exp", "fact",
]);

// ============================================================================
// 2. Stage 1: Surface Cleanup (Markdown, LaTeX formatting macros, OCR noise)
// ============================================================================
export function cleanRawOcrSurface(rawInput, mode = "auto") {
  if (!rawInput || typeof rawInput !== "string") {
    return { cleaned: "", warnings: ["empty_input"] };
  }
  const warnings = [];
  let s = rawInput.trim();

  // Deduplicate repeated identical lines (common neural seq2seq artifact on small crops)
  const rawLines = s.split(/\r?\n/).map((l) => l.trim()).filter(Boolean);
  if (rawLines.length >= 2 && rawLines[0] === rawLines[1]) {
    warnings.push("deduplicated_repeated_lines");
    s = rawLines[0];
  }

  // Extract $$...$$ or $...$ math block when model adds surrounding Markdown/list artifacts
  const displayBlock = s.match(/\$\$([\s\S]*?)\$\$/);
  if (displayBlock) {
    s = displayBlock[1].trim();
  } else {
    const inlineBlock = s.match(/^\$([^$]+)\$/);
    if (inlineBlock) {
      s = inlineBlock[1].trim();
    } else {
      // Strip markdown headings/bold wrappers (e.g. "**2x + 5 = 15**", "## 125 + 35")
      if (/^#{1,6}\s+/.test(s) || /^\*\*.*\*\*$/.test(s)) {
        warnings.push("stripped_markdown_wrapper");
        s = s.replace(/^#{1,6}\s+/, "").replace(/^\*\*|\*\*$/g, "").trim();
      }
      s = s.replace(/\$\$/g, "").replace(/\$/g, "");
      s = s.replace(/^\\\[|\\\]$/g, "").replace(/^\\\(|\\\)$/g, "");
    }
  }

  // Detect multiline fragments (e.g., linear OCR splitting numerator/denominator)
  if (/\r?\n/.test(s)) {
    const lines = s.split(/\r?\n/).map((l) => l.trim()).filter(Boolean);
    if (lines.length > 1) warnings.push("multiline_ocr_fragment");
    s = lines.join(" ");
  }

  // Iteratively unwrap LaTeX presentation macros (handles nested braces like \mathrm{~x^{2}+5x+6~})
  s = unwrapLatexStyleMacros(s);

  // Strip LaTeX sizing & spacing macros + non-breaking space ~
  s = s.replace(/\\(?:left|right|big|Big|bigg|Bigg|displaystyle|textstyle)(?![a-zA-Z])/g, "");
  s = s.replace(/\\ |\\,|\\;|\\:|\\!|\\quad(?![a-zA-Z])|\\qquad(?![a-zA-Z])|~/g, " ");

  // Normalize LaTeX operators & symbols (control sequences end before any non-letter, incl. digits)
  s = s.replace(/\\times(?![a-zA-Z])|\\cdot(?![a-zA-Z])|\\ast(?![a-zA-Z])/g, " * ");
  s = s.replace(/\\div(?![a-zA-Z])/g, " / ");
  s = s.replace(/\\%/g, "%");
  s = s.replace(/\\pi(?![a-zA-Z])/g, " pi ");

  // Normalize LaTeX function macros (\sin -> sin, \ln -> ln, etc.)
  for (const fn of KNOWN_FUNCTIONS) {
    if (fn === "sqrt") continue;
    s = s.replace(new RegExp(`\\\\${fn}(?![a-zA-Z])`, "g"), ` ${fn} `);
  }

  // Normalize Unicode operators, minus, brackets, constants, and superscripts
  s = s.replace(/[×✕✖·∙]/g, "*");
  s = s.replace(/[÷∶]/g, "/");
  s = s.replace(/[−–—]/g, "-");
  s = s.replace(/π/g, " pi ");
  s = s.replace(/\[/g, (m, offset, str) => {
    // Preserve optional root index in \sqrt[n]{...}
    return str.slice(Math.max(0, offset - 5), offset) === "\\sqrt" ? "[" : "(";
  });
  s = s.replace(/\]/g, (m, offset, str) => {
    // Keep closing ] if matching \sqrt[...]{
    const before = str.slice(0, offset);
    const lastSqrt = before.lastIndexOf("\\sqrt[");
    const lastClose = before.lastIndexOf("]");
    return lastSqrt > lastClose ? "]" : ")";
  });

  // Normalize Unicode superscripts -> ^{digits}
  const supMap = {
    "⁰": "0", "¹": "1", "²": "2", "³": "3", "⁴": "4",
    "⁵": "5", "⁶": "6", "⁷": "7", "⁸": "8", "⁹": "9",
  };
  s = s.replace(/[⁰¹²³⁴⁵⁶⁷⁸⁹]+/g, (match) => {
    const digits = [...match].map((c) => supMap[c]).join("");
    return `^{${digits}}`;
  });

  // Normalize decimal commas between digits (e.g. 12,75 -> 12.75)
  s = s.replace(/(\d),(\d)/g, "$1.$2");

  // Context-aware disambiguation for linear OCR: "35 x 2" -> "35 * 2"
  if (mode !== "latex_strict") {
    s = s.replace(/(\d)\s+[xX]\s+(\d)/g, "$1 * $2");
  }

  // Normalize empty-base LaTeX superscript artifacts like "X{}^{2}" -> "X^{2}"
  s = s.replace(/\{\}\s*\^/g, "^");

  // Collapse whitespace
  s = s.replace(/\s+/g, " ").trim();
  return { cleaned: s, warnings };
}

function unwrapLatexStyleMacros(input) {
  const macros = ["mathrm", "mathbf", "mathit", "mathsf", "mathtt", "text", "textbf", "textit", "operatorname", "rm", "bf", "it"];
  let s = input;
  let changed = true;
  let guard = 0;
  while (changed && guard++ < 30) {
    changed = false;
    for (const m of macros) {
      const re = new RegExp(`\\\\${m}\\s*\\{`);
      const match = re.exec(s);
      if (match) {
        const braceStart = match.index + match[0].length - 1;
        const grp = extractBalancedGroup(s, braceStart, "{", "}");
        if (grp) {
          s = s.slice(0, match.index) + " " + grp.content + " " + s.slice(grp.endIdx + 1);
          changed = true;
          break;
        }
      }
    }
  }
  return s;
}

function extractBalancedGroup(str, startIdx, openCh, closeCh) {
  if (str[startIdx] !== openCh) return null;
  let depth = 0;
  for (let i = startIdx; i < str.length; i++) {
    if (str[i] === openCh) depth++;
    else if (str[i] === closeCh) {
      depth--;
      if (depth === 0) return { content: str.slice(startIdx + 1, i), endIdx: i };
    }
  }
  return null;
}

// ============================================================================
// 3. Stage 2: Lexer & Recursive-Descent Parser -> MathIrNode AST
// ============================================================================
function tokenizeCleaned(input) {
  const tokens = [];
  let i = 0;
  while (i < input.length) {
    const ch = input[i];
    if (/\s/.test(ch)) {
      i++;
      continue;
    }
    if (ch === "\\") {
      let j = i + 1;
      while (j < input.length && /[a-zA-Z]/.test(input[j])) j++;
      const cmd = input.slice(i + 1, j);
      if (cmd === "frac" || cmd === "dfrac" || cmd === "tfrac") {
        tokens.push({ type: "FRAC", value: "\\frac" });
      } else if (cmd === "sqrt") {
        tokens.push({ type: "SQRT", value: "\\sqrt" });
      } else if (cmd.length > 0) {
        tokens.push({ type: "UNKNOWN_MACRO", value: "\\" + cmd });
      }
      i = j;
      continue;
    }
    if (ch === "√") {
      tokens.push({ type: "UNICODE_SQRT", value: "√" });
      i++;
      continue;
    }
    if (/[0-9.]/.test(ch)) {
      let j = i;
      let dots = 0;
      while (j < input.length && /[0-9.]/.test(input[j])) {
        if (input[j] === ".") dots++;
        j++;
      }
      const num = input.slice(i, j);
      if (dots > 1 || num === ".") {
        tokens.push({ type: "INVALID", value: num });
      } else {
        tokens.push({ type: "NUMBER", value: num });
      }
      i = j;
      continue;
    }
    if (/[a-zA-Z]/.test(ch)) {
      let j = i;
      while (j < input.length && /[a-zA-Z]/.test(input[j])) j++;
      const word = input.slice(i, j);
      const low = word.toLowerCase();
      if (low === "pi" || low === "e") {
        tokens.push({ type: "CONST", value: low });
      } else if (low === "mod") {
        tokens.push({ type: "OP", value: "%" });
      } else if (low === "de" || low === "of") {
        tokens.push({ type: "PCT_KW", value: low });
      } else if (KNOWN_FUNCTIONS.has(low)) {
        tokens.push({ type: "FUNC", value: low });
      } else if (word.length === 1) {
        tokens.push({ type: "VAR", value: low });
      } else {
        // Multi-letter word (could be unknown identifier or OCR noise)
        tokens.push({ type: "IDENT", value: word });
      }
      i = j;
      continue;
    }
    if ("+-*/%^=!".includes(ch)) {
      tokens.push({ type: ch === "=" ? "EQ" : ch === "!" ? "BANG" : "OP", value: ch });
      i++;
      continue;
    }
    if ("(){}[]".includes(ch)) {
      const map = { "(": "LPAREN", ")": "RPAREN", "{": "LBRACE", "}": "RBRACE", "[": "LBRACK", "]": "RBRACK" };
      tokens.push({ type: map[ch], value: ch });
      i++;
      continue;
    }
    tokens.push({ type: "INVALID", value: ch });
    i++;
  }
  return tokens;
}

class MathIrParser {
  constructor(tokens) {
    this.tokens = tokens;
    this.pos = 0;
  }
  peek(offset = 0) {
    return this.tokens[this.pos + offset] || null;
  }
  consume() {
    return this.tokens[this.pos++] || null;
  }
  expect(type) {
    const t = this.peek();
    if (!t || t.type !== type) {
      throw new Error(`Expected ${type} but found ${t ? `${t.type}(${t.value})` : "EOF"}`);
    }
    return this.consume();
  }

  parseTopLevel() {
    if (this.tokens.length === 0) throw new Error("Empty expression");
    // Check percentage pattern: <num> % (de|of) <expr>
    const saved = this.pos;
    try {
      const maybePct = this.tryParsePercentageOf();
      if (maybePct && !this.peek()) return maybePct;
    } catch {
      // fallback to general expression
    }
    this.pos = saved;

    const lhs = this.parseAdditive();
    if (this.peek()?.type === "EQ") {
      this.consume();
      const rhs = this.parseAdditive();
      if (this.peek()) {
        throw new Error(`Unexpected token after equation: ${this.peek().value}`);
      }
      return MathIr.equation(lhs, rhs);
    }
    if (this.peek()) {
      throw new Error(`Unexpected trailing token: ${this.peek().value}`);
    }
    return lhs;
  }

  tryParsePercentageOf() {
    const pctExpr = this.parseUnary();
    if (this.peek()?.type === "OP" && this.peek().value === "%") {
      this.consume();
      if (this.peek()?.type === "PCT_KW") {
        const kw = this.consume().value;
        const baseExpr = this.parseAdditive();
        return MathIr.percentOf(pctExpr, baseExpr, kw);
      }
    }
    return null;
  }

  parseAdditive() {
    let left = this.parseMultiplicative();
    while (this.peek()?.type === "OP" && (this.peek().value === "+" || this.peek().value === "-")) {
      const op = this.consume().value;
      const right = this.parseMultiplicative();
      left = MathIr.binary(op, left, right);
    }
    return left;
  }

  canStartImplicitFactor(tok) {
    if (!tok) return false;
    return (
      tok.type === "VAR" ||
      tok.type === "CONST" ||
      tok.type === "FUNC" ||
      tok.type === "LPAREN" ||
      tok.type === "FRAC" ||
      tok.type === "SQRT" ||
      tok.type === "UNICODE_SQRT"
    );
  }

  parseMultiplicative() {
    let left = this.parseUnary();
    while (true) {
      const tok = this.peek();
      if (tok?.type === "OP" && (tok.value === "*" || tok.value === "/" || tok.value === "%")) {
        const op = this.consume().value;
        const right = this.parseUnary();
        left = MathIr.binary(op, left, right);
      } else if (this.canStartImplicitFactor(tok)) {
        // Standard mathematical implicit multiplication: 5x, 2(3+4), (x+1)(x-1), 2sqrt(9)
        const right = this.parsePower();
        left = MathIr.binary("*", left, right);
      } else {
        break;
      }
    }
    return left;
  }

  // Standard mathematical precedence: unary minus binds LOOSER than power (-x^2 == -(x^2))
  parseUnary() {
    const tok = this.peek();
    if (tok?.type === "OP" && tok.value === "-") {
      this.consume();
      return MathIr.unary("-", this.parseUnary());
    }
    if (tok?.type === "OP" && tok.value === "+") {
      this.consume();
      return this.parseUnary();
    }
    return this.parsePower();
  }

  // Exponentiation is right-associative: 2^3^2 == 2^(3^2)
  parsePower() {
    const base = this.parsePostfix();
    if (this.peek()?.type === "OP" && this.peek().value === "^") {
      this.consume();
      const exponent = this.parseUnary();
      return MathIr.power(base, exponent);
    }
    return base;
  }

  parsePostfix() {
    let node = this.parsePrimary();
    while (this.peek()?.type === "BANG") {
      this.consume();
      node = MathIr.func("fact", node);
    }
    return node;
  }

  parseLatexArg() {
    if (this.peek()?.type === "LBRACE") {
      this.consume();
      const inner = this.parseAdditive();
      this.expect("RBRACE");
      return inner;
    }
    return this.parsePrimary();
  }

  parsePrimary() {
    const tok = this.peek();
    if (!tok) throw new Error("Unexpected end of expression");

    if (tok.type === "NUMBER") {
      this.consume();
      return MathIr.number(tok.value);
    }
    if (tok.type === "VAR") {
      this.consume();
      return MathIr.variable(tok.value);
    }
    if (tok.type === "CONST") {
      this.consume();
      return MathIr.constant(tok.value);
    }
    if (tok.type === "FRAC") {
      this.consume();
      const num = this.parseLatexArg();
      const den = this.parseLatexArg();
      return MathIr.fraction(num, den);
    }
    if (tok.type === "SQRT") {
      this.consume();
      let index = null;
      if (this.peek()?.type === "LBRACK") {
        this.consume();
        index = this.parseAdditive();
        this.expect("RBRACK");
      }
      const radicand = this.parseLatexArg();
      return MathIr.root(radicand, index);
    }
    if (tok.type === "UNICODE_SQRT") {
      this.consume();
      if (this.peek()?.type === "LPAREN") {
        this.consume();
        const rad = this.parseAdditive();
        this.expect("RPAREN");
        return MathIr.root(rad, null);
      }
      const rad = this.parsePower();
      return MathIr.root(rad, null);
    }
    if (tok.type === "FUNC") {
      const fn = this.consume().value;
      if (this.peek()?.type === "LPAREN") {
        this.consume();
        const arg = this.parseAdditive();
        this.expect("RPAREN");
        return fn === "sqrt" ? MathIr.root(arg, null) : MathIr.func(fn, arg);
      }
      const arg = this.parsePower();
      return fn === "sqrt" ? MathIr.root(arg, null) : MathIr.func(fn, arg);
    }
    if (tok.type === "LPAREN") {
      this.consume();
      const inner = this.parseAdditive();
      this.expect("RPAREN");
      return MathIr.group(inner);
    }
    if (tok.type === "LBRACE") {
      this.consume();
      const inner = this.parseAdditive();
      this.expect("RBRACE");
      return inner;
    }
    throw new Error(`Unsupported or invalid token: ${tok.type}(${tok.value})`);
  }
}

export function parseToMathIr(rawInput, mode = "auto") {
  const { cleaned, warnings } = cleanRawOcrSurface(rawInput, mode);
  const tokens = tokenizeCleaned(cleaned);
  try {
    const parser = new MathIrParser(tokens);
    const ast = parser.parseTopLevel();
    return { ok: true, cleaned, ast, warnings, error: null };
  } catch (err) {
    return { ok: false, cleaned, ast: null, warnings, error: err.message };
  }
}

// ============================================================================
// 4. Stage 3: Precedence-Aware Serializer -> CalcSuits MathExpressionEvaluator
// ============================================================================
function nodePrecedence(node) {
  switch (node.type) {
    case "Equation":
    case "PercentageOf":
      return 0;
    case "BinaryOp":
      return node.op === "+" || node.op === "-" ? 1 : 2;
    case "Fraction":
      return 2;
    case "UnaryOp":
      return 3; // mathematical unary precedence before power
    case "Power":
      return 4;
    default:
      return 5; // atom / group / func / root
  }
}

function isSimpleAtom(node) {
  return node.type === "NumberLiteral" || node.type === "Variable" || node.type === "Constant";
}

export function serializeIrToCalcSuits(node) {
  switch (node.type) {
    case "NumberLiteral":
      return node.raw;
    case "Variable":
      return node.name;
    case "Constant":
      return node.symbol;
    case "Group":
      return `(${serializeIrToCalcSuits(node.inner)})`;
    case "UnaryOp": {
      const inner = node.operand;
      // CRITICAL: In MathExpressionEvaluator.kt, unary minus has precedence 4 > "^" (3),
      // so "-x^2" would evaluate as "(-x)^2"! Wrapping Power in parentheses emits "-(x^2)".
      if (node.op === "-" && (inner.type === "Power" || inner.type === "BinaryOp" || inner.type === "Fraction")) {
        return `-(${serializeIrToCalcSuits(inner)})`;
      }
      return `${node.op}${serializeIrToCalcSuits(inner)}`;
    }
    case "BinaryOp": {
      const p = nodePrecedence(node);
      const leftStr =
        nodePrecedence(node.left) < p
          ? `(${serializeIrToCalcSuits(node.left)})`
          : serializeIrToCalcSuits(node.left);
      // For non-associative "-" and "/", also parenthesize equal-precedence right child
      const needRightParen =
        nodePrecedence(node.right) < p ||
        ((node.op === "-" || node.op === "/") && nodePrecedence(node.right) === p);
      const rightStr = needRightParen
        ? `(${serializeIrToCalcSuits(node.right)})`
        : serializeIrToCalcSuits(node.right);
      return `${leftStr} ${node.op} ${rightStr}`;
    }
    case "Fraction": {
      const numStr = isSimpleAtom(node.numerator) || node.numerator.type === "Group"
        ? serializeIrToCalcSuits(node.numerator)
        : `(${serializeIrToCalcSuits(node.numerator)})`;
      const denStr = isSimpleAtom(node.denominator) || node.denominator.type === "Group"
        ? serializeIrToCalcSuits(node.denominator)
        : `(${serializeIrToCalcSuits(node.denominator)})`;
      return `${numStr} / ${denStr}`;
    }
    case "Power": {
      // Base needs parentheses if it is UnaryOp, BinaryOp, Fraction, or Power (left-grouped (2^3)^2)
      const baseNeedsParen =
        node.base.type === "UnaryOp" ||
        node.base.type === "BinaryOp" ||
        node.base.type === "Fraction" ||
        node.base.type === "Power";
      const baseStr = baseNeedsParen
        ? `(${serializeIrToCalcSuits(node.base)})`
        : serializeIrToCalcSuits(node.base);
      // Right-associative: Power in exponent (2^3^2) does NOT need extra parens; BinaryOp/Fraction/Unary does
      const expNeedsParen =
        node.exponent.type === "BinaryOp" ||
        node.exponent.type === "Fraction" ||
        node.exponent.type === "UnaryOp";
      const expStr = expNeedsParen
        ? `(${serializeIrToCalcSuits(node.exponent)})`
        : serializeIrToCalcSuits(node.exponent);
      return `${baseStr}^${expStr}`;
    }
    case "Root": {
      const radStr = serializeIrToCalcSuits(node.radicand);
      if (!node.index) {
        return `sqrt(${radStr})`;
      }
      const idxStr = serializeIrToCalcSuits(node.index);
      const baseWrapped = isSimpleAtom(node.radicand) ? radStr : `(${radStr})`;
      const idxWrapped = isSimpleAtom(node.index) ? idxStr : `(${idxStr})`;
      return `${baseWrapped}^(1 / ${idxWrapped})`;
    }
    case "FunctionCall": {
      if (node.name === "fact") {
        const argStr = isSimpleAtom(node.argument)
          ? serializeIrToCalcSuits(node.argument)
          : `(${serializeIrToCalcSuits(node.argument)})`;
        return `${argStr}!`;
      }
      return `${node.name}(${serializeIrToCalcSuits(node.argument)})`;
    }
    case "PercentageOf": {
      return `${serializeIrToCalcSuits(node.percent)}% ${node.keyword} ${serializeIrToCalcSuits(node.base)}`;
    }
    case "Equation": {
      return `${serializeIrToCalcSuits(node.lhs)} = ${serializeIrToCalcSuits(node.rhs)}`;
    }
    default:
      throw new Error(`Unknown IR node type: ${node.type}`);
  }
}

export function collectVariables(node, acc = new Set()) {
  if (!node || typeof node !== "object") return acc;
  if (node.type === "Variable") acc.add(node.name);
  for (const val of Object.values(node)) {
    if (val && typeof val === "object" && val.type) collectVariables(val, acc);
  }
  return acc;
}

export function normalizeWithAst(rawInput, mode = "auto") {
  const parsed = parseToMathIr(rawInput, mode);
  if (!parsed.ok) {
    return {
      ok: false,
      cleaned: parsed.cleaned,
      normalized: parsed.cleaned,
      ast: null,
      kind: "PARSE_ERROR",
      variables: [],
      warnings: parsed.warnings,
      error: parsed.error,
    };
  }
  const normalized = serializeIrToCalcSuits(parsed.ast);
  const vars = [...collectVariables(parsed.ast)];
  let kind = "NUMERIC_EVALUABLE";
  if (parsed.ast.type === "PercentageOf") {
    kind = "PERCENTAGE_OF";
  } else if (parsed.ast.type === "Equation") {
    kind = vars.length === 1 && vars[0] === "x" ? "EQUATION_IN_X" : "UNSUPPORTED_EQUATION";
  } else if (vars.length === 1 && vars[0] === "x") {
    kind = "GRAPHABLE_FUNCTION_OF_X";
  } else if (vars.length > 0) {
    kind = "UNSUPPORTED_VARIABLES";
  }
  return {
    ok: true,
    cleaned: parsed.cleaned,
    normalized,
    ast: parsed.ast,
    kind,
    variables: vars,
    warnings: parsed.warnings,
    error: null,
  };
}

// ============================================================================
// 5. Strict Mathematical Semantic & Granular Structural Evaluators (for POC)
// ============================================================================
export function isSemanticallyEquivalent(exprId, astResult) {
  if (!astResult.ok) return false;
  const compact = astResult.normalized.replace(/\s+/g, "").toLowerCase();
  const spaceNorm = astResult.normalized.replace(/\s+/g, " ").trim().toLowerCase();
  switch (exprId) {
    case "expr_01_arith_mul":
      return compact === "125+35*2";
    case "expr_02_div_dec":
      return compact === "12.75/0.5" || compact === "(12.75)/(0.5)";
    case "expr_03_poly_sq":
      return compact === "x^2+5*x+6" || compact === "x^2+5x+6";
    case "expr_04_sqrt":
      return compact === "sqrt(144)+5";
    case "expr_05_grouped_frac":
      return compact === "(3*x+2)/5" || compact === "(3x+2)/5" || compact === "((3*x+2))/5";
    case "expr_06_linear_eq":
      return compact === "2*x+5=15" || compact === "2x+5=15";
    case "expr_07_percent_es":
      return spaceNorm === "15% de 250";
    default:
      return false;
  }
}

/**
 * Granular structural checks required by Phase 4.1 Section 5:
 * symbols, exponents, fractions, roots, parentheses/grouping.
 */
export function evaluateGranularMetrics(exprId, rawOcr, astResult) {
  const norm = (astResult.normalized || "").replace(/\s+/g, "").toLowerCase();
  const metrics = {
    symbols_ok: null,
    exponent_ok: null,
    fraction_ok: null,
    root_ok: null,
    grouping_ok: null,
  };

  if (exprId === "expr_01_arith_mul") {
    metrics.symbols_ok = norm.includes("+") && norm.includes("*");
  } else if (exprId === "expr_02_div_dec") {
    metrics.symbols_ok = norm.includes("/") && norm.includes(".");
    metrics.fraction_ok = astResult.ok && (norm === "12.75/0.5" || norm === "(12.75)/(0.5)");
  } else if (exprId === "expr_03_poly_sq") {
    metrics.symbols_ok = (norm.match(/\+/g) || []).length >= 2;
    metrics.exponent_ok = astResult.ok && /x\^2/.test(norm);
  } else if (exprId === "expr_04_sqrt") {
    metrics.symbols_ok = norm.includes("+");
    metrics.root_ok = astResult.ok && norm === "sqrt(144)+5"; // strict scope: NOT sqrt(144+5)
  } else if (exprId === "expr_05_grouped_frac") {
    metrics.symbols_ok = norm.includes("+") && norm.includes("/");
    metrics.fraction_ok = astResult.ok && norm.includes("/5");
    metrics.grouping_ok = astResult.ok && /\(3\*?x\+2\)\/5/.test(norm);
  } else if (exprId === "expr_06_linear_eq") {
    metrics.symbols_ok = norm.includes("+") && norm.includes("=");
  } else if (exprId === "expr_07_percent_es") {
    metrics.symbols_ok = norm.includes("%");
  }

  return metrics;
}
