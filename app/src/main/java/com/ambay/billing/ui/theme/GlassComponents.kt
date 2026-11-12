package com.ambay.billing.ui.theme

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.rememberLottieComposition
import com.ambay.billing.R
import com.ambay.billing.core.MoneyEngine
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 30-second looping animated gradient mesh (deep blue -> indigo -> cyan, or near-black with blue glow in dark mode).
 * Zero per-frame allocations.
 */
@Composable
fun LivingGlassBackground(modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    val phase by rememberInfiniteTransition(label = "mesh").animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 30_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "mesh_phase"
    )

    val baseBg = if (dark) Color(0xFF030712) else Color(0xFF061B44)
    val glow1 = if (dark) BrandDeepBlue.copy(alpha = 0.55f) else BrandDeepBlue.copy(alpha = 0.90f)
    val glow2 = if (dark) BrandCyan.copy(alpha = 0.25f) else BrandCyan.copy(alpha = 0.45f)
    val glow3 = if (dark) BrandIndigo.copy(alpha = 0.40f) else BrandIndigo.copy(alpha = 0.75f)

    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(color = baseBg)
        val w = size.width
        val h = size.height

        val c1 = Offset(w * (0.25f + 0.15f * cos(phase)), h * (0.20f + 0.10f * sin(phase)))
        val c2 = Offset(w * (0.80f + 0.12f * sin(phase)), h * (0.35f + 0.15f * cos(phase)))
        val c3 = Offset(w * (0.50f + 0.18f * cos(phase + 1.5f)), h * (0.82f + 0.10f * sin(phase + 1.5f)))

        drawCircle(
            brush = Brush.radialGradient(colors = listOf(glow1, Color.Transparent), center = c1, radius = w * 0.85f),
            center = c1,
            radius = w * 0.85f
        )
        drawCircle(
            brush = Brush.radialGradient(colors = listOf(glow2, Color.Transparent), center = c2, radius = w * 0.70f),
            center = c2,
            radius = w * 0.70f
        )
        drawCircle(
            brush = Brush.radialGradient(colors = listOf(glow3, Color.Transparent), center = c3, radius = w * 0.80f),
            center = c3,
            radius = w * 0.80f
        )
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    cornerRadius: Dp = 24.dp,
    tintColor: Color = Color.White.copy(alpha = 0.13f),
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val useRealBlur = hazeState != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val baseModifier = modifier
        .shadow(elevation = 12.dp, shape = shape, ambientColor = Color.Black.copy(alpha = 0.25f))
        .clip(shape)
        .let { m ->
            if (useRealBlur) {
                m.hazeEffect(
                    state = hazeState!!,
                    style = HazeStyle(
                        backgroundColor = Color(0xFF0A1D42),
                        tint = HazeTint(tintColor),
                        blurRadius = 26.dp
                    )
                )
            } else {
                m.background(Color(0xFF0D234D).copy(alpha = 0.82f))
            }
        }
        .border(width = 1.dp, color = Color.White.copy(alpha = 0.22f), shape = shape)

    Column(
        modifier = baseModifier.padding(16.dp),
        content = content
    )
}

@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = BrandCyan,
    contentColor: Color = Color(0xFF001C24),
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "btn_scale"
    )
    val shape = RoundedCornerShape(18.dp)

    Row(
        modifier = modifier
            .scale(scale)
            .defaultMinSize(minHeight = 48.dp)
            .clip(shape)
            .background(if (enabled) containerColor else containerColor.copy(alpha = 0.4f))
            .border(1.dp, Color.White.copy(alpha = 0.28f), shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled
            ) {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                onClick()
            }
            .padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            color = contentColor,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

data class GlassNavItem(
    val label: String,
    val icon: ImageVector
)

/**
 * Floating iOS-glass pill bottom bar with spring-animated sliding selection bubble + haptic tick.
 */
@Composable
fun GlassPillBottomBar(
    items: List<GlassNavItem>,
    selectedIndex: Int,
    hazeState: HazeState?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val shape = RoundedCornerShape(28.dp)
    val useRealBlur = hazeState != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .height(64.dp)
            .shadow(16.dp, shape, ambientColor = Color.Black.copy(alpha = 0.4f))
            .clip(shape)
            .let { m ->
                if (useRealBlur) {
                    m.hazeEffect(
                        state = hazeState!!,
                        style = HazeStyle(
                            backgroundColor = Color(0xFF071530),
                            tint = HazeTint(Color.White.copy(alpha = 0.14f)),
                            blurRadius = 28.dp
                        )
                    )
                } else {
                    m.background(Color(0xFF081A3D).copy(alpha = 0.90f))
                }
            }
            .border(1.dp, Color.White.copy(alpha = 0.22f), shape)
            .padding(4.dp)
    ) {
        val tabWidth = maxWidth / items.size.coerceAtLeast(1)
        val bubbleOffset by animateDpAsState(
            targetValue = tabWidth * selectedIndex,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "nav_bubble"
        )

        // Sliding glass bubble
        Box(
            modifier = Modifier
                .offset(x = bubbleOffset)
                .width(tabWidth)
                .fillMaxHeight()
                .clip(RoundedCornerShape(24.dp))
                .background(BrandCyan.copy(alpha = 0.24f))
                .border(1.dp, BrandCyan.copy(alpha = 0.45f), RoundedCornerShape(24.dp))
        )

        Row(modifier = Modifier.fillMaxSize()) {
            items.forEachIndexed { idx, item ->
                val active = idx == selectedIndex
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(24.dp))
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            onSelect(idx)
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        tint = if (active) BrandCyan else Color.White.copy(alpha = 0.72f),
                        modifier = Modifier.size(21.dp)
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        color = if (active) Color.White else Color.White.copy(alpha = 0.70f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun AnimatedMoneyText(
    paise: Long,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.headlineMedium,
    color: Color = Color.White
) {
    val animated by animateIntAsState(
        targetValue = paise.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt(),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "money_countup"
    )
    Text(
        text = MoneyEngine.fmtINR(animated.toLong()),
        modifier = modifier,
        style = style,
        color = color
    )
}

@Composable
fun ShimmerBox(modifier: Modifier = Modifier) {
    val shift by rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "shimmer_x"
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.06f),
                        Color.White.copy(alpha = 0.16f),
                        Color.White.copy(alpha = 0.06f)
                    ),
                    start = Offset(shift - 300f, 0f),
                    end = Offset(shift, 100f)
                )
            )
    )
}

@Composable
fun LottieSuccessBadge(modifier: Modifier = Modifier) {
    val comp by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.lottie_success))
    LottieAnimation(
        composition = comp,
        iterations = 1,
        modifier = modifier.size(72.dp)
    )
}

@Composable
fun LottieEmptyState(message: String, modifier: Modifier = Modifier) {
    val comp by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.lottie_empty))
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LottieAnimation(composition = comp, iterations = 3, modifier = Modifier.size(96.dp))
        Spacer(Modifier.height(8.dp))
        Text(text = message, color = Color.White.copy(alpha = 0.78f), style = MaterialTheme.typography.bodyMedium)
    }
}
