package com.ambay.billing.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.dp
import com.ambay.billing.ui.theme.BrandAccent

@Composable
fun ScanViewfinder(
    isTorchOn: Boolean,
    onTorchToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "laser_anim")
    val laserYRatio by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_y"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            val frameWidth = canvasWidth * 0.85f
            val frameHeight = canvasHeight * 0.70f

            val left = (canvasWidth - frameWidth) / 2f
            val top = (canvasHeight - frameHeight) / 2f
            val right = left + frameWidth
            val bottom = top + frameHeight

            // Draw darkened scrim outside the viewfinder window
            val cutOutPath = Path().apply {
                addRoundRect(
                    RoundRect(
                        rect = Rect(left, top, right, bottom),
                        cornerRadius = CornerRadius(16.dp.toPx())
                    )
                )
            }

            clipPath(cutOutPath, clipOp = ClipOp.Difference) {
                drawRect(color = Color.Black.copy(alpha = 0.50f))
            }

            // Draw Corner Brackets (Gold / Brand Accent)
            val strokeWidth = 5.dp.toPx()
            val bracketLength = 28.dp.toPx()
            val bracketColor = BrandAccent

            // Top-Left
            drawLine(bracketColor, Offset(left, top), Offset(left + bracketLength, top), strokeWidth)
            drawLine(bracketColor, Offset(left, top), Offset(left, top + bracketLength), strokeWidth)

            // Top-Right
            drawLine(bracketColor, Offset(right, top), Offset(right - bracketLength, top), strokeWidth)
            drawLine(bracketColor, Offset(right, top), Offset(right, top + bracketLength), strokeWidth)

            // Bottom-Left
            drawLine(bracketColor, Offset(left, bottom), Offset(left + bracketLength, bottom), strokeWidth)
            drawLine(bracketColor, Offset(left, bottom), Offset(left, bottom - bracketLength), strokeWidth)

            // Bottom-Right
            drawLine(bracketColor, Offset(right, bottom), Offset(right - bracketLength, bottom), strokeWidth)
            drawLine(bracketColor, Offset(right, bottom), Offset(right, bottom - bracketLength), strokeWidth)

            // Subtle animated red/orange laser scan-line
            val laserY = top + (frameHeight * laserYRatio)
            drawLine(
                color = Color(0xFFFF5252).copy(alpha = 0.85f),
                start = Offset(left + 8.dp.toPx(), laserY),
                end = Offset(right - 8.dp.toPx(), laserY),
                strokeWidth = 2.5.dp.toPx()
            )
        }

        // Torch button in bottom-right corner of viewfinder
        FloatingActionButton(
            onClick = onTorchToggle,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = Color.Black.copy(alpha = 0.6f),
            contentColor = if (isTorchOn) BrandAccent else Color.White
        ) {
            Icon(
                imageVector = if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                contentDescription = "Torch Toggle"
            )
        }
    }
}
