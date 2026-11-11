package com.ambay.billing.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

val BrandBlue = Color(0xFF0B3D91)
val BrandBlueDark = Color(0xFF062357)
val BrandBlueLight = Color(0xFF1E5BB8)
val BrandAccent = Color(0xFFFF9800)
val BrandSuccess = Color(0xFF2E7D32)
val BrandError = Color(0xFFD32F2F)

// Light Palette
val BackgroundLight = Color(0xFFF4F6FB)
val SurfaceLight = Color(0xFFFFFFFF)
val CardLight = Color(0xFFFFFFFF)
val TextPrimaryLight = Color(0xFF1A1C24)
val TextSecondaryLight = Color(0xFF6B7280)

// Dark Palette
val BackgroundDark = Color(0xFF0F1117)
val SurfaceDark = Color(0xFF171A23)
val CardDark = Color(0xFF1E222E)
val TextPrimaryDark = Color(0xFFF3F4F6)
val TextSecondaryDark = Color(0xFF9CA3AF)

val BrandGradient = Brush.horizontalGradient(
    colors = listOf(BrandBlue, BrandBlueLight)
)

val CardGradient = Brush.verticalGradient(
    colors = listOf(Color(0xFF0F47A8), BrandBlue)
)
