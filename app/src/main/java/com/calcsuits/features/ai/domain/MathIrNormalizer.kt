package com.calcsuits.features.ai.domain

/**
 * AST-based Mathematical Intermediate Representation (`MathIrNode`), Recursive-Descent LaTeX/OCR
 * Parser, and Precedence-Aware Serializer compatible with CalcSuits's `MathExpressionEvaluator`.
 *
 * CRITICAL PRECEDENCE NOTE (verified in MathExpressionEvaluator.kt):
 *   precedence = { "+":1, "-":1, "*":2, "/":2, "%":2, "^":3, "u-":4 }
 *   Because unary minus ("u-") has HIGHER precedence (4) than exponentiation ("^", 3),
 *   "-x^2" in MathExpressionEvaluator evaluates as "(-x)^2".
 *   Therefore, UnaryOp("-", Power(base, exp)) MUST be serialized as "-(base^exp)"
 *   to preserve standard mathematical semantics (-4 for x=2), whereas
 *   Power(UnaryOp("-", x), 2) is serialized as "(-x)^2" (+4 for x=2).
 */
sealed interface MathIrNode {
    data class NumberLiteral(val raw: String) : MathIrNode
    data class Variable(val name: String) : MathIrNode
    data class Constant(val symbol: String) : MathIrNode
    data class UnaryOp(val op: String, val operand: MathIrNode) : MathIrNode
    data class BinaryOp(val op: String, val left: MathIrNode, val right: MathIrNode) : MathIrNode
    data class Power(val base: MathIrNode, val exponent: MathIrNode) : MathIrNode
    data class Fraction(val numerator: MathIrNode, val denominator: MathIrNode) : MathIrNode
    data class Root(val radicand: MathIrNode, val index: MathIrNode? = null) : MathIrNode
    data class Group(val inner: MathIrNode) : MathIrNode
    data class FunctionCall(val name: String, val argument: MathIrNode) : MathIrNode
    data class PercentageOf(val percent: MathIrNode, val base: MathIrNode, val keyword: String = "de") : MathIrNode
    data class Equation(val lhs: MathIrNode, val rhs: MathIrNode) : MathIrNode
}

enum class MathExpressionKind {
    NUMERIC_EVALUABLE,
    PERCENTAGE_OF,
    EQUATION_IN_X,
    GRAPHABLE_FUNCTION_OF_X,
    UNSUPPORTED_EQUATION,
    UNSUPPORTED_VARIABLES,
    PARSE_ERROR,
}

data class MathIrNormalizationResult(
    val ok: Boolean,
    val cleaned: String,
    val normalized: String,
    val ast: MathIrNode?,
    val kind: MathExpressionKind,
    val variables: List<String>,
    val warnings: List<String>,
    val error: String?,
)

object MathIrNormalizer {
    private val KNOWN_FUNCTIONS = setOf(
        "sin", "cos", "tan", "asin", "acos", "atan",
        "sinh", "cosh", "tanh", "log", "ln", "sqrt", "abs", "exp", "fact",
    )

    private val STYLE_MACROS = listOf(
        "mathrm", "mathbf", "mathit", "mathsf", "mathtt",
        "text", "textbf", "textit", "operatorname", "mbox", "rm", "bf", "it",
    )

    private val SUPERSCRIPT_MAP = mapOf(
        '⁰' to '0', '¹' to '1', '²' to '2', '³' to '3', '⁴' to '4',
        '⁵' to '5', '⁶' to '6', '⁷' to '7', '⁸' to '8', '⁹' to '9',
    )

    private enum class TokenType {
        NUMBER, VAR, CONST, FUNC, PCT_KW, IDENT, FRAC, SQRT, UNICODE_SQRT,
        UNKNOWN_MACRO, OP, EQ, BANG, LPAREN, RPAREN, LBRACE, RBRACE, LBRACK, RBRACK, INVALID,
    }

    private data class Token(val type: TokenType, val value: String)

    data class SurfaceCleanResult(val cleaned: String, val warnings: List<String>)

    fun cleanRawOcrSurface(rawInput: String?, latexStrict: Boolean = false): SurfaceCleanResult {
        if (rawInput.isNullOrBlank()) {
            return SurfaceCleanResult("", listOf("empty_input"))
        }
        val warnings = mutableListOf<String>()
        var s = rawInput.trim()

        // Deduplicate repeated identical lines
        val rawLines = s.split(Regex("\\r?\\n")).map { it.trim() }.filter { it.isNotEmpty() }
        if (rawLines.size >= 2 && rawLines[0] == rawLines[1]) {
            warnings.add("deduplicated_repeated_lines")
            s = rawLines[0]
        }

        // Extract $$...$$ or $...$ math block
        val displayBlock = Regex("""\$\$([\s\S]*?)\$\$""").find(s)
        if (displayBlock != null) {
            s = displayBlock.groupValues[1].trim()
        } else {
            val inlineBlock = Regex("""^\$([^$]+)\$""").find(s)
            if (inlineBlock != null) {
                s = inlineBlock.groupValues[1].trim()
            } else {
                if (Regex("""^#{1,6}\s+""").containsMatchIn(s) || Regex("""^\*\*.*\*\*$""").matches(s)) {
                    warnings.add("stripped_markdown_wrapper")
                    s = s.replace(Regex("""^#{1,6}\s+"""), "")
                        .replace(Regex("""^\*\*|\*\*$"""), "")
                        .trim()
                }
                s = s.replace("$$", "").replace("$", "")
                s = s.replace(Regex("""^\\\[|\\\]$"""), "")
                    .replace(Regex("""^\\\(|\\\)$"""), "")
                    .trim()
            }
        }

        // Collapse multiline fragments
        if (Regex("""\r?\n""").containsMatchIn(s)) {
            val lines = s.split(Regex("""\r?\n""")).map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.size > 1) warnings.add("multiline_ocr_fragment")
            s = lines.joinToString(" ")
        }

        // Strip trailing sentence punctuation often emitted from textbook lines (e.g. "= t," or "= 0.")
        s = s.replace(Regex("""[,;.]\s*$"""), "").trim()

        // Iteratively unwrap LaTeX presentation macros
        s = unwrapLatexStyleMacros(s)

        // Strip LaTeX sizing & spacing macros + non-breaking space ~
        s = s.replace(Regex("""\\(?:left|right|big|Big|bigg|Bigg|displaystyle|textstyle)(?![a-zA-Z])"""), "")
        s = s.replace(Regex("""\\ |\\,|\\;|\\:|\\!|\\quad(?![a-zA-Z])|\\qquad(?![a-zA-Z])|~"""), " ")

        // Normalize ASCII-caret OCR artifacts emitted on dark-mode screens: "x^{\wedge}2" -> "x^2"
        s = s.replace(Regex("""\^\s*\{\s*\\wedge\s*\}|\\wedge(?![a-zA-Z])"""), "^")

        // Normalize LaTeX operators & symbols
        s = s.replace(Regex("""\\times(?![a-zA-Z])|\\cdot(?![a-zA-Z])|\\ast(?![a-zA-Z])"""), " * ")
        s = s.replace(Regex("""\\div(?![a-zA-Z])"""), " / ")
        s = s.replace("\\%", "%")
        s = s.replace(Regex("""\\pi(?![a-zA-Z])"""), " pi ")

        // Normalize LaTeX function macros (\sin -> sin, etc.)
        for (fn in KNOWN_FUNCTIONS) {
            if (fn == "sqrt") continue
            s = s.replace(Regex("""\\$fn(?![a-zA-Z])"""), " $fn ")
        }

        // Normalize Unicode operators, minus, brackets, constants, and superscripts
        s = s.replace(Regex("""[×✕✖·∙]"""), "*")
        s = s.replace(Regex("""[÷∶]"""), "/")
        s = s.replace(Regex("""[−–—]"""), "-")
        s = s.replace("π", " pi ")

        // Convert square brackets to parentheses except optional root index in \sqrt[n]{...}
        s = normalizeSquareBrackets(s)

        // Normalize Unicode superscripts -> ^{digits}
        s = Regex("""[⁰¹²³⁴⁵⁶⁷⁸⁹]+""").replace(s) { m ->
            val digits = m.value.map { SUPERSCRIPT_MAP[it] ?: it }.joinToString("")
            "^{$digits}"
        }

        // Normalize decimal commas between digits (12,75 -> 12.75)
        s = s.replace(Regex("""(\d),(\d)"""), "$1.$2")

        // Disambiguate "35 x 2" -> "35 * 2"
        if (!latexStrict) {
            s = s.replace(Regex("""(\d)\s+[xX]\s+(\d)"""), "$1 * $2")
        }

        // Normalize empty-base LaTeX superscript artifacts like "X{}^{2}" -> "X^{2}"
        s = s.replace(Regex("""\{\}\s*\^"""), "^")

        // Collapse whitespace
        s = s.replace(Regex("""\s+"""), " ").trim()
        return SurfaceCleanResult(s, warnings)
    }

    private fun normalizeSquareBrackets(input: String): String {
        val sb = StringBuilder(input.length)
        var inSqrtIndex = false
        for (i in input.indices) {
            val ch = input[i]
            when (ch) {
                '[' -> {
                    val isSqrtPrefix = i >= 5 && input.substring(i - 5, i) == "\\sqrt"
                    if (isSqrtPrefix) {
                        inSqrtIndex = true
                        sb.append('[')
                    } else {
                        sb.append('(')
                    }
                }
                ']' -> {
                    if (inSqrtIndex) {
                        inSqrtIndex = false
                        sb.append(']')
                    } else {
                        sb.append(')')
                    }
                }
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun unwrapLatexStyleMacros(input: String): String {
        var s = input
        var changed = true
        var guard = 0
        while (changed && guard++ < 30) {
            changed = false
            for (macro in STYLE_MACROS) {
                val match = Regex("""\\$macro\s*\{""").find(s)
                if (match != null) {
                    val braceStart = match.range.last
                    val grp = extractBalancedGroup(s, braceStart, '{', '}')
                    if (grp != null) {
                        s = s.substring(0, match.range.first) + " " + grp.first + " " + s.substring(grp.second + 1)
                        changed = true
                        break
                    }
                }
            }
        }
        return s
    }

    private fun extractBalancedGroup(str: String, startIdx: Int, openCh: Char, closeCh: Char): Pair<String, Int>? {
        if (startIdx >= str.length || str[startIdx] != openCh) return null
        var depth = 0
        for (i in startIdx until str.length) {
            if (str[i] == openCh) depth++
            else if (str[i] == closeCh) {
                depth--
                if (depth == 0) {
                    return str.substring(startIdx + 1, i) to i
                }
            }
        }
        return null
    }

    private fun tokenizeCleaned(input: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var i = 0
        while (i < input.length) {
            val ch = input[i]
            if (ch.isWhitespace()) {
                i++
                continue
            }
            if (ch == '\\') {
                var j = i + 1
                while (j < input.length && input[j].isLetter()) j++
                val cmd = input.substring(i + 1, j)
                when {
                    cmd == "frac" || cmd == "dfrac" || cmd == "tfrac" ->
                        tokens.add(Token(TokenType.FRAC, "\\frac"))
                    cmd == "sqrt" ->
                        tokens.add(Token(TokenType.SQRT, "\\sqrt"))
                    cmd.isNotEmpty() ->
                        tokens.add(Token(TokenType.UNKNOWN_MACRO, "\\$cmd"))
                }
                i = j
                continue
            }
            if (ch == '√') {
                tokens.add(Token(TokenType.UNICODE_SQRT, "√"))
                i++
                continue
            }
            if (ch.isDigit() || ch == '.') {
                var j = i
                var dots = 0
                while (j < input.length && (input[j].isDigit() || input[j] == '.')) {
                    if (input[j] == '.') dots++
                    j++
                }
                val num = input.substring(i, j)
                if (dots > 1 || num == ".") {
                    tokens.add(Token(TokenType.INVALID, num))
                } else {
                    tokens.add(Token(TokenType.NUMBER, num))
                }
                i = j
                continue
            }
            if (ch.isLetter()) {
                var j = i
                while (j < input.length && input[j].isLetter()) j++
                val word = input.substring(i, j)
                val low = word.lowercase()
                when {
                    low == "pi" || low == "e" -> tokens.add(Token(TokenType.CONST, low))
                    low == "mod" -> tokens.add(Token(TokenType.OP, "%"))
                    low == "de" || low == "of" -> tokens.add(Token(TokenType.PCT_KW, low))
                    low in KNOWN_FUNCTIONS -> tokens.add(Token(TokenType.FUNC, low))
                    word.length == 1 -> tokens.add(Token(TokenType.VAR, low))
                    else -> tokens.add(Token(TokenType.IDENT, word))
                }
                i = j
                continue
            }
            if (ch in "+-*/%^=!") {
                val type = when (ch) {
                    '=' -> TokenType.EQ
                    '!' -> TokenType.BANG
                    else -> TokenType.OP
                }
                tokens.add(Token(type, ch.toString()))
                i++
                continue
            }
            when (ch) {
                '(' -> tokens.add(Token(TokenType.LPAREN, "("))
                ')' -> tokens.add(Token(TokenType.RPAREN, ")"))
                '{' -> tokens.add(Token(TokenType.LBRACE, "{"))
                '}' -> tokens.add(Token(TokenType.RBRACE, "}"))
                '[' -> tokens.add(Token(TokenType.LBRACK, "["))
                ']' -> tokens.add(Token(TokenType.RBRACK, "]"))
                else -> tokens.add(Token(TokenType.INVALID, ch.toString()))
            }
            i++
        }
        return tokens
    }

    private class Parser(private val tokens: List<Token>) {
        private var pos = 0

        private fun peek(offset: Int = 0): Token? = tokens.getOrNull(pos + offset)
        private fun consume(): Token? = if (pos < tokens.size) tokens[pos++] else null
        private fun expect(type: TokenType): Token {
            val t = peek()
            if (t == null || t.type != type) {
                throw IllegalArgumentException(
                    "Expected $type but found ${if (t != null) "${t.type}(${t.value})" else "EOF"}"
                )
            }
            return consume()!!
        }

        fun parseTopLevel(): MathIrNode {
            if (tokens.isEmpty()) throw IllegalArgumentException("Empty expression")
            val saved = pos
            runCatching {
                val maybePct = tryParsePercentageOf()
                if (maybePct != null && peek() == null) return maybePct
            }
            pos = saved

            val lhs = parseAdditive()
            if (peek()?.type == TokenType.EQ) {
                consume()
                val rhs = parseAdditive()
                if (peek() != null) {
                    throw IllegalArgumentException("Unexpected token after equation: ${peek()!!.value}")
                }
                return MathIrNode.Equation(lhs, rhs)
            }
            if (peek() != null) {
                throw IllegalArgumentException("Unexpected trailing token: ${peek()!!.value}")
            }
            return lhs
        }

        private fun tryParsePercentageOf(): MathIrNode? {
            val pctExpr = parseUnary()
            val opTok = peek()
            if (opTok?.type == TokenType.OP && opTok.value == "%") {
                consume()
                val kwTok = peek()
                if (kwTok?.type == TokenType.PCT_KW) {
                    val kw = consume()!!.value
                    val baseExpr = parseAdditive()
                    return MathIrNode.PercentageOf(pctExpr, baseExpr, kw)
                }
            }
            return null
        }

        private fun parseAdditive(): MathIrNode {
            var left = parseMultiplicative()
            while (peek()?.type == TokenType.OP && (peek()!!.value == "+" || peek()!!.value == "-")) {
                val op = consume()!!.value
                val right = parseMultiplicative()
                left = MathIrNode.BinaryOp(op, left, right)
            }
            return left
        }

        private fun canStartImplicitFactor(tok: Token?): Boolean {
            if (tok == null) return false
            return tok.type == TokenType.VAR ||
                tok.type == TokenType.CONST ||
                tok.type == TokenType.FUNC ||
                tok.type == TokenType.LPAREN ||
                tok.type == TokenType.FRAC ||
                tok.type == TokenType.SQRT ||
                tok.type == TokenType.UNICODE_SQRT
        }

        private fun parseMultiplicative(): MathIrNode {
            var left = parseUnary()
            while (true) {
                val tok = peek()
                if (tok?.type == TokenType.OP && (tok.value == "*" || tok.value == "/" || tok.value == "%")) {
                    val op = consume()!!.value
                    val right = parseUnary()
                    left = MathIrNode.BinaryOp(op, left, right)
                } else if (canStartImplicitFactor(tok)) {
                    val right = parsePower()
                    left = MathIrNode.BinaryOp("*", left, right)
                } else {
                    break
                }
            }
            return left
        }

        private fun parseUnary(): MathIrNode {
            val tok = peek()
            if (tok?.type == TokenType.OP && tok.value == "-") {
                consume()
                return MathIrNode.UnaryOp("-", parseUnary())
            }
            if (tok?.type == TokenType.OP && tok.value == "+") {
                consume()
                return parseUnary()
            }
            return parsePower()
        }

        private fun parsePower(): MathIrNode {
            val base = parsePostfix()
            if (peek()?.type == TokenType.OP && peek()!!.value == "^") {
                consume()
                val exponent = parseUnary()
                return MathIrNode.Power(base, exponent)
            }
            return base
        }

        private fun parsePostfix(): MathIrNode {
            var node = parsePrimary()
            while (peek()?.type == TokenType.BANG) {
                consume()
                node = MathIrNode.FunctionCall("fact", node)
            }
            return node
        }

        private fun parseLatexArg(): MathIrNode {
            if (peek()?.type == TokenType.LBRACE) {
                consume()
                val inner = parseAdditive()
                expect(TokenType.RBRACE)
                return inner
            }
            return parsePrimary()
        }

        private fun parsePrimary(): MathIrNode {
            val tok = peek() ?: throw IllegalArgumentException("Unexpected end of expression")
            return when (tok.type) {
                TokenType.NUMBER -> {
                    consume()
                    MathIrNode.NumberLiteral(tok.value)
                }
                TokenType.VAR -> {
                    consume()
                    MathIrNode.Variable(tok.value)
                }
                TokenType.CONST -> {
                    consume()
                    MathIrNode.Constant(tok.value)
                }
                TokenType.FRAC -> {
                    consume()
                    val num = parseLatexArg()
                    val den = parseLatexArg()
                    MathIrNode.Fraction(num, den)
                }
                TokenType.SQRT -> {
                    consume()
                    var index: MathIrNode? = null
                    if (peek()?.type == TokenType.LBRACK) {
                        consume()
                        index = parseAdditive()
                        expect(TokenType.RBRACK)
                    }
                    val radicand = parseLatexArg()
                    MathIrNode.Root(radicand, index)
                }
                TokenType.UNICODE_SQRT -> {
                    consume()
                    if (peek()?.type == TokenType.LPAREN) {
                        consume()
                        val rad = parseAdditive()
                        expect(TokenType.RPAREN)
                        MathIrNode.Root(rad, null)
                    } else {
                        val rad = parsePower()
                        MathIrNode.Root(rad, null)
                    }
                }
                TokenType.FUNC -> {
                    val fn = consume()!!.value
                    val arg = if (peek()?.type == TokenType.LPAREN) {
                        consume()
                        val a = parseAdditive()
                        expect(TokenType.RPAREN)
                        a
                    } else {
                        parsePower()
                    }
                    if (fn == "sqrt") MathIrNode.Root(arg, null) else MathIrNode.FunctionCall(fn, arg)
                }
                TokenType.LPAREN -> {
                    consume()
                    val inner = parseAdditive()
                    expect(TokenType.RPAREN)
                    MathIrNode.Group(inner)
                }
                TokenType.LBRACE -> {
                    consume()
                    val inner = parseAdditive()
                    expect(TokenType.RBRACE)
                    inner
                }
                else -> throw IllegalArgumentException("Unsupported or invalid token: ${tok.type}(${tok.value})")
            }
        }
    }

    private fun nodePrecedence(node: MathIrNode): Int = when (node) {
        is MathIrNode.Equation, is MathIrNode.PercentageOf -> 0
        is MathIrNode.BinaryOp -> if (node.op == "+" || node.op == "-") 1 else 2
        is MathIrNode.Fraction -> 2
        is MathIrNode.UnaryOp -> 3
        is MathIrNode.Power -> 4
        else -> 5
    }

    private fun isSimpleAtom(node: MathIrNode): Boolean =
        node is MathIrNode.NumberLiteral || node is MathIrNode.Variable || node is MathIrNode.Constant

    fun serializeToCalcSuits(node: MathIrNode): String = when (node) {
        is MathIrNode.NumberLiteral -> node.raw
        is MathIrNode.Variable -> node.name
        is MathIrNode.Constant -> node.symbol
        is MathIrNode.Group -> "(${serializeToCalcSuits(node.inner)})"
        is MathIrNode.UnaryOp -> {
            val inner = node.operand
            if (node.op == "-" && (inner is MathIrNode.Power || inner is MathIrNode.BinaryOp || inner is MathIrNode.Fraction)) {
                "-(${serializeToCalcSuits(inner)})"
            } else {
                "${node.op}${serializeToCalcSuits(inner)}"
            }
        }
        is MathIrNode.BinaryOp -> {
            val p = nodePrecedence(node)
            val leftStr = if (nodePrecedence(node.left) < p) {
                "(${serializeToCalcSuits(node.left)})"
            } else {
                serializeToCalcSuits(node.left)
            }
            val needRightParen = nodePrecedence(node.right) < p ||
                ((node.op == "-" || node.op == "/") && nodePrecedence(node.right) == p)
            val rightStr = if (needRightParen) {
                "(${serializeToCalcSuits(node.right)})"
            } else {
                serializeToCalcSuits(node.right)
            }
            "$leftStr ${node.op} $rightStr"
        }
        is MathIrNode.Fraction -> {
            val numStr = if (isSimpleAtom(node.numerator) || node.numerator is MathIrNode.Group) {
                serializeToCalcSuits(node.numerator)
            } else {
                "(${serializeToCalcSuits(node.numerator)})"
            }
            val denStr = if (isSimpleAtom(node.denominator) || node.denominator is MathIrNode.Group) {
                serializeToCalcSuits(node.denominator)
            } else {
                "(${serializeToCalcSuits(node.denominator)})"
            }
            "$numStr / $denStr"
        }
        is MathIrNode.Power -> {
            val baseNeedsParen = node.base is MathIrNode.UnaryOp ||
                node.base is MathIrNode.BinaryOp ||
                node.base is MathIrNode.Fraction ||
                node.base is MathIrNode.Power
            val baseStr = if (baseNeedsParen) {
                "(${serializeToCalcSuits(node.base)})"
            } else {
                serializeToCalcSuits(node.base)
            }
            val expNeedsParen = node.exponent is MathIrNode.BinaryOp ||
                node.exponent is MathIrNode.Fraction ||
                node.exponent is MathIrNode.UnaryOp ||
                node.exponent is MathIrNode.Power
            val expStr = if (expNeedsParen) {
                "(${serializeToCalcSuits(node.exponent)})"
            } else {
                serializeToCalcSuits(node.exponent)
            }
            "$baseStr^$expStr"
        }
        is MathIrNode.Root -> {
            val radStr = serializeToCalcSuits(node.radicand)
            if (node.index == null) {
                "sqrt($radStr)"
            } else {
                val idxStr = serializeToCalcSuits(node.index)
                val baseWrapped = if (isSimpleAtom(node.radicand)) radStr else "($radStr)"
                val idxWrapped = if (isSimpleAtom(node.index)) idxStr else "($idxStr)"
                "$baseWrapped^(1 / $idxWrapped)"
            }
        }
        is MathIrNode.FunctionCall -> {
            if (node.name == "fact") {
                val argStr = if (isSimpleAtom(node.argument)) {
                    serializeToCalcSuits(node.argument)
                } else {
                    "(${serializeToCalcSuits(node.argument)})"
                }
                "$argStr!"
            } else {
                "${node.name}(${serializeToCalcSuits(node.argument)})"
            }
        }
        is MathIrNode.PercentageOf -> {
            "${serializeToCalcSuits(node.percent)}% ${node.keyword} ${serializeToCalcSuits(node.base)}"
        }
        is MathIrNode.Equation -> {
            "${serializeToCalcSuits(node.lhs)} = ${serializeToCalcSuits(node.rhs)}"
        }
    }

    fun collectVariables(node: MathIrNode, acc: LinkedHashSet<String> = LinkedHashSet()): Set<String> {
        when (node) {
            is MathIrNode.Variable -> acc.add(node.name)
            is MathIrNode.UnaryOp -> collectVariables(node.operand, acc)
            is MathIrNode.BinaryOp -> {
                collectVariables(node.left, acc)
                collectVariables(node.right, acc)
            }
            is MathIrNode.Power -> {
                collectVariables(node.base, acc)
                collectVariables(node.exponent, acc)
            }
            is MathIrNode.Fraction -> {
                collectVariables(node.numerator, acc)
                collectVariables(node.denominator, acc)
            }
            is MathIrNode.Root -> {
                collectVariables(node.radicand, acc)
                node.index?.let { collectVariables(it, acc) }
            }
            is MathIrNode.Group -> collectVariables(node.inner, acc)
            is MathIrNode.FunctionCall -> collectVariables(node.argument, acc)
            is MathIrNode.PercentageOf -> {
                collectVariables(node.percent, acc)
                collectVariables(node.base, acc)
            }
            is MathIrNode.Equation -> {
                collectVariables(node.lhs, acc)
                collectVariables(node.rhs, acc)
            }
            is MathIrNode.NumberLiteral, is MathIrNode.Constant -> Unit
        }
        return acc
    }

    fun normalize(rawInput: String?, latexStrict: Boolean = false): MathIrNormalizationResult {
        val surface = cleanRawOcrSurface(rawInput, latexStrict)
        val tokens = tokenizeCleaned(surface.cleaned)
        return try {
            val ast = Parser(tokens).parseTopLevel()
            val normalized = serializeToCalcSuits(ast)
            val vars = collectVariables(ast).toList()
            val kind = when {
                ast is MathIrNode.PercentageOf -> MathExpressionKind.PERCENTAGE_OF
                ast is MathIrNode.Equation -> {
                    if (vars.size == 1 && vars[0] == "x") MathExpressionKind.EQUATION_IN_X
                    else MathExpressionKind.UNSUPPORTED_EQUATION
                }
                vars.size == 1 && vars[0] == "x" -> MathExpressionKind.GRAPHABLE_FUNCTION_OF_X
                vars.isNotEmpty() -> MathExpressionKind.UNSUPPORTED_VARIABLES
                else -> MathExpressionKind.NUMERIC_EVALUABLE
            }
            MathIrNormalizationResult(
                ok = true,
                cleaned = surface.cleaned,
                normalized = normalized,
                ast = ast,
                kind = kind,
                variables = vars,
                warnings = surface.warnings,
                error = null,
            )
        } catch (e: Exception) {
            MathIrNormalizationResult(
                ok = false,
                cleaned = surface.cleaned,
                normalized = surface.cleaned,
                ast = null,
                kind = MathExpressionKind.PARSE_ERROR,
                variables = emptyList(),
                warnings = surface.warnings,
                error = e.message ?: "Parse error",
            )
        }
    }
}
