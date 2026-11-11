package com.ambay.billing.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.ambay.billing.core.model.Money

@Composable
fun NumCountUp(
    targetPaise: Long,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier
) {
    val animVal = remember { Animatable(0f) }

    LaunchedEffect(targetPaise) {
        animVal.animateTo(
            targetValue = targetPaise.toFloat(),
            animationSpec = tween(durationMillis = 600)
        )
    }

    val currentPaise = animVal.value.toLong()
    Text(
        text = Money.formatInr(currentPaise),
        style = style,
        color = color,
        modifier = modifier
    )
}
