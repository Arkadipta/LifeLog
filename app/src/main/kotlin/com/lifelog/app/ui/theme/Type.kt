package com.lifelog.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val LifeLogTypography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontWeight = FontWeight.Bold),
        displayMedium = displayMedium.copy(fontWeight = FontWeight.Bold),
        displaySmall = displaySmall.copy(fontWeight = FontWeight.SemiBold),
        headlineLarge = headlineLarge.copy(fontWeight = FontWeight.SemiBold),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Medium),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.15.sp),
        titleSmall = titleSmall.copy(fontWeight = FontWeight.Medium),
        bodyLarge = bodyLarge.copy(lineHeight = 24.sp),
        bodyMedium = bodyMedium.copy(lineHeight = 20.sp),
        bodySmall = bodySmall.copy(lineHeight = 16.sp),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
        labelMedium = labelMedium.copy(fontWeight = FontWeight.Medium),
        labelSmall = labelSmall.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp)
    )
}
