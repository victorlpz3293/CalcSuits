package com.calcsuits.domain.usecase

import com.calcsuits.R
import com.calcsuits.domain.model.UiText

sealed class MathError {
    data object CalculationError : MathError()
    data object InvalidOperation : MathError()
    data object InvalidFirstNumber : MathError()
    data object SecondNumberRequired : MathError()
    data object DivisionByZero : MathError()
    data object ExponentRequired : MathError()
    data object ResultOutOfRange : MathError()
    data object DivisorRequired : MathError()
    data object ModuloByZero : MathError()
    data object InvalidNumber : MathError()
    data object UndefinedTangent : MathError()
    data class UndefinedTangentAtDegrees(val value: String) : MathError()
    data object DomainMinusOneOne : MathError()
    data class DomainFnMinusOneOne(val fn: String) : MathError()
    data object DomainPositive : MathError()
    data object LogPositive : MathError()
    data object LnPositive : MathError()
    data object NegativeRoot : MathError()
    data class UnknownFunction(val fn: String) : MathError()
    data object UndefinedResult : MathError()
    data object FactorialRange : MathError()
    data object EmptyExpression : MathError()
    data object NoValidTokens : MathError()
    data object InvalidDecimal : MathError()
    data class UnknownIdentifier(val word: String) : MathError()
    data class InvalidCharacter(val ch: String) : MathError()
    data object UnbalancedParentheses : MathError()
    data class UnknownOperator(val token: String) : MathError()
    data object MissingNegationOperand : MathError()
    data class MissingOperand(val token: String) : MathError()
    data class MissingOperands(val token: String) : MathError()
    data object UnsupportedOperator : MathError()
    data class UnknownToken(val token: String) : MathError()
    data object IncompleteExpression : MathError()
    data class UnsupportedFunction(val fn: String) : MathError()

    fun toUiText(): UiText = when (this) {
        CalculationError -> UiText.StringResource(R.string.error_calculation)
        InvalidOperation -> UiText.StringResource(R.string.error_invalid_operation)
        InvalidFirstNumber -> UiText.StringResource(R.string.error_invalid_first_number)
        SecondNumberRequired -> UiText.StringResource(R.string.error_second_number_required)
        DivisionByZero -> UiText.StringResource(R.string.error_division_by_zero)
        ExponentRequired -> UiText.StringResource(R.string.error_exponent_required)
        ResultOutOfRange -> UiText.StringResource(R.string.error_result_out_of_range)
        DivisorRequired -> UiText.StringResource(R.string.error_divisor_required)
        ModuloByZero -> UiText.StringResource(R.string.error_modulo_by_zero)
        InvalidNumber -> UiText.StringResource(R.string.error_invalid_number)
        UndefinedTangent -> UiText.StringResource(R.string.error_undefined_tangent)
        is UndefinedTangentAtDegrees -> UiText.StringResource(R.string.error_undefined_tangent_deg, value)
        DomainMinusOneOne -> UiText.StringResource(R.string.error_domain_minus_one_one)
        is DomainFnMinusOneOne -> UiText.StringResource(R.string.error_domain_fn_minus_one_one, fn)
        DomainPositive -> UiText.StringResource(R.string.error_domain_positive)
        LogPositive -> UiText.StringResource(R.string.error_log_positive)
        LnPositive -> UiText.StringResource(R.string.error_ln_positive)
        NegativeRoot -> UiText.StringResource(R.string.error_negative_root)
        is UnknownFunction -> UiText.StringResource(R.string.error_unknown_function, fn)
        UndefinedResult -> UiText.StringResource(R.string.error_undefined_result)
        FactorialRange -> UiText.StringResource(R.string.error_factorial_range)
        EmptyExpression -> UiText.StringResource(R.string.error_empty_expression)
        NoValidTokens -> UiText.StringResource(R.string.error_no_valid_tokens)
        InvalidDecimal -> UiText.StringResource(R.string.error_invalid_decimal)
        is UnknownIdentifier -> UiText.StringResource(R.string.error_unknown_identifier, word)
        is InvalidCharacter -> UiText.StringResource(R.string.error_invalid_character, ch)
        UnbalancedParentheses -> UiText.StringResource(R.string.error_unbalanced_parentheses)
        is UnknownOperator -> UiText.StringResource(R.string.error_unknown_operator, token)
        MissingNegationOperand -> UiText.StringResource(R.string.error_missing_negation_operand)
        is MissingOperand -> UiText.StringResource(R.string.error_missing_operand, token)
        is MissingOperands -> UiText.StringResource(R.string.error_missing_operands, token)
        UnsupportedOperator -> UiText.StringResource(R.string.error_unsupported_operator)
        is UnknownToken -> UiText.StringResource(R.string.error_unknown_token, token)
        IncompleteExpression -> UiText.StringResource(R.string.error_incomplete_expression)
        is UnsupportedFunction -> UiText.StringResource(R.string.error_unsupported_function, fn)
    }
}

class MathEvaluationException(
    val error: MathError,
    message: String
) : IllegalArgumentException(message)

class MathArithmeticException(
    val error: MathError,
    message: String
) : ArithmeticException(message)

fun Throwable.toMathUiText(fallback: MathError = MathError.CalculationError): UiText =
    toMathUiText(fallback.toUiText())

fun Throwable.toMathUiText(fallback: UiText): UiText = when (this) {
    is MathEvaluationException -> error.toUiText()
    is MathArithmeticException -> error.toUiText()
    else -> fallback
}
