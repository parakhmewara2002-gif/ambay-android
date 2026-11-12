package com.ambay.billing.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ambay.billing.R

val BrandDeepBlue = Color(0xFF0B3D91)
val BrandIndigo = Color(0xFF1A237E)
val BrandCyan = Color(0xFF00B8D9)
val BrandSuccess = Color(0xFF10B981)
val BrandWarning = Color(0xFFF59E0B)
val BrandError = Color(0xFFEF4444)

private val googleFontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

private val interGoogleFont = GoogleFont("Inter")

val InterFontFamily = FontFamily(
    Font(googleFont = interGoogleFont, fontProvider = googleFontProvider, weight = FontWeight.Normal),
    Font(googleFont = interGoogleFont, fontProvider = googleFontProvider, weight = FontWeight.Medium),
    Font(googleFont = interGoogleFont, fontProvider = googleFontProvider, weight = FontWeight.SemiBold),
    Font(googleFont = interGoogleFont, fontProvider = googleFontProvider, weight = FontWeight.Bold)
)

val AmbayTypography = Typography(
    displaySmall = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 38.sp),
    headlineMedium = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelSmall = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp)
)

val AmbayShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

private val DarkColors = darkColorScheme(
    primary = BrandCyan,
    onPrimary = Color(0xFF001F29),
    secondary = BrandDeepBlue,
    onSecondary = Color.White,
    background = Color(0xFF040A18),
    onBackground = Color(0xFFF3F7FF),
    surface = Color(0xFF0A1429),
    onSurface = Color(0xFFF3F7FF),
    surfaceVariant = Color(0xFF13223F),
    onSurfaceVariant = Color(0xFFB4C6E7),
    error = BrandError
)

private val LightColors = lightColorScheme(
    primary = BrandDeepBlue,
    onPrimary = Color.White,
    secondary = BrandCyan,
    onSecondary = Color(0xFF002029),
    background = Color(0xFF08255E),
    onBackground = Color.White,
    surface = Color(0xFF0F3275),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF184396),
    onSurfaceVariant = Color(0xFFD6E4FF),
    error = BrandError
)

@Composable
fun AmbayTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AmbayTypography,
        shapes = AmbayShapes,
        content = content
    )
}
