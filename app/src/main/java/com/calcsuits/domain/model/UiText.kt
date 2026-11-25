package com.calcsuits.domain.model

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes

/**
 * Context-free representation of localizable UI text for domain models, use cases, and ViewModels.
 */
sealed class UiText {
    data class DynamicString(val value: String) : UiText()

    data class StringResource(
        @StringRes val resId: Int,
        val args: List<Any> = emptyList()
    ) : UiText() {
        constructor(@StringRes resId: Int, vararg args: Any) : this(resId, args.toList())
    }

    data class PluralResource(
        @PluralsRes val resId: Int,
        val count: Int,
        val args: List<Any> = emptyList()
    ) : UiText() {
        constructor(@PluralsRes resId: Int, count: Int, vararg args: Any) : this(resId, count, args.toList())
    }
}
