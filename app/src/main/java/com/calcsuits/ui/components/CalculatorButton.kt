package com.calcsuits.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calcsuits.ui.theme.JetBrainsMonoFontFamily

@Composable
fun CalculatorButton(
    symbol: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconContentDescription: String? = null,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    borderColor: Color? = null,
    textStyle: TextStyle = MaterialTheme.typography.titleLarge,
    isEqualButton: Boolean = false,
    height: Dp? = null,
    shape: Shape = RoundedCornerShape(16.dp),
    testTagName: String = "calc_btn_$symbol",
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "calculator_button_scale"
    )

    val sizeModifier = if (height != null) {
        modifier.fillMaxWidth().height(height)
    } else {
        modifier.fillMaxSize()
    }

    Surface(
        onClick = onClick,
        modifier = sizeModifier
            .scale(scale)
            .testTag(testTagName),
        shape = shape,
        color = backgroundColor,
        contentColor = contentColor,
        border = borderColor?.let { BorderStroke(1.dp, it) },
        tonalElevation = if (isEqualButton) 4.dp else 0.dp,
        interactionSource = interactionSource
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = iconContentDescription ?: symbol,
                    tint = contentColor,
                    modifier = Modifier.size(22.dp)
                )
            } else {
                Text(
                    text = symbol,
                    style = textStyle.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = when {
                            symbol.length >= 4 -> 13.sp
                            symbol.length == 3 -> 15.sp
                            symbol.length == 2 -> 18.sp
                            else -> 24.sp
                        },
                        fontWeight = if (isEqualButton) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = contentColor,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}
