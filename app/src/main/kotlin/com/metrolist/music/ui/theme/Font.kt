package com.metrolist.music.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.metrolist.music.R
import com.metrolist.music.constants.AppFont

val bbhBartle = FontFamily(
    Font(R.font.bbh_bartle_regular, FontWeight.Normal)
)

val UrbanistFontFamily = FontFamily(
    Font(R.font.urbanist_variable, FontWeight.Light),
    Font(R.font.urbanist_variable, FontWeight.Normal),
    Font(R.font.urbanist_variable, FontWeight.Medium),
    Font(R.font.urbanist_variable, FontWeight.SemiBold),
    Font(R.font.urbanist_variable, FontWeight.Bold),
    Font(R.font.urbanist_variable, FontWeight.ExtraBold),
    Font(R.font.urbanist_variable, FontWeight.Black),
)

val PlayfairDisplayFontFamily = FontFamily(
    Font(R.font.playfair_display_variable, FontWeight.Normal),
    Font(R.font.playfair_display_variable, FontWeight.Medium),
    Font(R.font.playfair_display_variable, FontWeight.SemiBold),
    Font(R.font.playfair_display_variable, FontWeight.Bold),
    Font(R.font.playfair_display_variable, FontWeight.ExtraBold),
    Font(R.font.playfair_display_variable, FontWeight.Black),
)

@Composable
fun rememberAppTypography(appFont: AppFont): Typography = remember(appFont) {
    val defaultTypo = Typography()
    when (appFont) {
        AppFont.SYSTEM -> defaultTypo
        AppFont.URBANIST -> Typography(
            displayLarge = defaultTypo.displayLarge.copy(fontFamily = UrbanistFontFamily),
            displayMedium = defaultTypo.displayMedium.copy(fontFamily = UrbanistFontFamily),
            displaySmall = defaultTypo.displaySmall.copy(fontFamily = UrbanistFontFamily),
            headlineLarge = defaultTypo.headlineLarge.copy(fontFamily = UrbanistFontFamily),
            headlineMedium = defaultTypo.headlineMedium.copy(fontFamily = UrbanistFontFamily),
            headlineSmall = defaultTypo.headlineSmall.copy(fontFamily = UrbanistFontFamily),
            titleLarge = defaultTypo.titleLarge.copy(fontFamily = UrbanistFontFamily),
            titleMedium = defaultTypo.titleMedium.copy(fontFamily = UrbanistFontFamily),
            titleSmall = defaultTypo.titleSmall.copy(fontFamily = UrbanistFontFamily),
            bodyLarge = defaultTypo.bodyLarge.copy(fontFamily = UrbanistFontFamily),
            bodyMedium = defaultTypo.bodyMedium.copy(fontFamily = UrbanistFontFamily),
            bodySmall = defaultTypo.bodySmall.copy(fontFamily = UrbanistFontFamily),
            labelLarge = defaultTypo.labelLarge.copy(fontFamily = UrbanistFontFamily),
            labelMedium = defaultTypo.labelMedium.copy(fontFamily = UrbanistFontFamily),
            labelSmall = defaultTypo.labelSmall.copy(fontFamily = UrbanistFontFamily),
        )
        AppFont.MERCURY_PREMIUM -> Typography(
            displayLarge = defaultTypo.displayLarge.copy(fontFamily = PlayfairDisplayFontFamily, fontWeight = FontWeight.Bold),
            displayMedium = defaultTypo.displayMedium.copy(fontFamily = PlayfairDisplayFontFamily, fontWeight = FontWeight.Bold),
            displaySmall = defaultTypo.displaySmall.copy(fontFamily = PlayfairDisplayFontFamily, fontWeight = FontWeight.SemiBold),
            headlineLarge = defaultTypo.headlineLarge.copy(fontFamily = PlayfairDisplayFontFamily, fontWeight = FontWeight.Bold),
            headlineMedium = defaultTypo.headlineMedium.copy(fontFamily = PlayfairDisplayFontFamily, fontWeight = FontWeight.SemiBold),
            headlineSmall = defaultTypo.headlineSmall.copy(fontFamily = PlayfairDisplayFontFamily, fontWeight = FontWeight.Bold),
            titleLarge = defaultTypo.titleLarge.copy(fontFamily = PlayfairDisplayFontFamily, fontWeight = FontWeight.Bold),
            titleMedium = defaultTypo.titleMedium.copy(fontFamily = UrbanistFontFamily, fontWeight = FontWeight.SemiBold),
            titleSmall = defaultTypo.titleSmall.copy(fontFamily = UrbanistFontFamily, fontWeight = FontWeight.Medium),
            bodyLarge = defaultTypo.bodyLarge.copy(fontFamily = UrbanistFontFamily),
            bodyMedium = defaultTypo.bodyMedium.copy(fontFamily = UrbanistFontFamily),
            bodySmall = defaultTypo.bodySmall.copy(fontFamily = UrbanistFontFamily),
            labelLarge = defaultTypo.labelLarge.copy(fontFamily = UrbanistFontFamily, fontWeight = FontWeight.SemiBold),
            labelMedium = defaultTypo.labelMedium.copy(fontFamily = UrbanistFontFamily, fontWeight = FontWeight.Medium),
            labelSmall = defaultTypo.labelSmall.copy(fontFamily = UrbanistFontFamily, fontWeight = FontWeight.Medium),
        )
    }
}

