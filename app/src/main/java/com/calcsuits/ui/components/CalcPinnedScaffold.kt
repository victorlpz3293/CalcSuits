package com.calcsuits.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shared layout scaffold enforcing the CalcSuits scroll rule:
 * contextual headers ([pinnedContent]) remain fixed at the top while only the
 * secondary body ([scrollableContent]) takes the remaining height via [Modifier.weight]
 * and scrolls when needed.
 *
 * On very short viewports (below [compactHeightThreshold], e.g. landscape or when the
 * system IME covers most of the screen on views with tall pinned cards), the layout
 * automatically falls back to a single scrollable container so no control or result is
 * clipped or unreachable.
 */
@Composable
fun CalcPinnedScaffold(
    topBar: @Composable () -> Unit,
    pinnedContent: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    snackbarHost: @Composable () -> Unit = {},
    containerColor: Color = MaterialTheme.colorScheme.background,
    applyImePadding: Boolean = true,
    pinnedPadding: PaddingValues = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 8.dp),
    pinnedSpacing: Dp = 10.dp,
    contentPadding: PaddingValues = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 16.dp),
    contentSpacing: Dp = 14.dp,
    scrollState: ScrollState = rememberScrollState(),
    enableContentScroll: Boolean = true,
    compactHeightThreshold: Dp = 240.dp,
    scrollableContent: @Composable ColumnScope.() -> Unit
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = topBar,
        snackbarHost = snackbarHost,
        containerColor = containerColor
    ) { padding ->
        val rootModifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .consumeWindowInsets(padding)
            .let { if (applyImePadding) it.imePadding() else it }

        BoxWithConstraints(modifier = rootModifier) {
            if (maxHeight < compactHeightThreshold) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(pinnedPadding),
                        verticalArrangement = Arrangement.spacedBy(pinnedSpacing),
                        content = pinnedContent
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(contentPadding),
                        verticalArrangement = Arrangement.spacedBy(contentSpacing),
                        content = scrollableContent
                    )
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(pinnedPadding),
                        verticalArrangement = Arrangement.spacedBy(pinnedSpacing),
                        content = pinnedContent
                    )
                    val bodyModifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .let { if (enableContentScroll) it.verticalScroll(scrollState) else it }
                        .padding(contentPadding)
                    Column(
                        modifier = bodyModifier,
                        verticalArrangement = Arrangement.spacedBy(contentSpacing),
                        content = scrollableContent
                    )
                }
            }
        }
    }
}
