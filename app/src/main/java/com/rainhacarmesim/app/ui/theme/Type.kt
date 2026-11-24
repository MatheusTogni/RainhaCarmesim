package com.rainhacarmesim.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Fonte monoespaçada para números, IPs e endereços (alinha colunas e reforça a estética técnica). */
val MonoFamily: FontFamily = FontFamily.Monospace

val RcTypography = Typography(
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 26.sp, letterSpacing = 0.5.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = 0.4.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, letterSpacing = 0.3.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, letterSpacing = 0.2.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 1.2.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = 0.4.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.6.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.8.sp),
)

val MonoValue = TextStyle(fontFamily = MonoFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
val MonoBig = TextStyle(fontFamily = MonoFamily, fontWeight = FontWeight.Bold, fontSize = 28.sp)
