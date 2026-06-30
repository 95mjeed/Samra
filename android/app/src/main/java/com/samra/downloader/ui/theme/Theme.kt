package com.samra.downloader.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.view.WindowCompat
import com.samra.downloader.i18n.Lang
import com.samra.downloader.i18n.LocalLang
import com.samra.downloader.i18n.LocalStrings
import com.samra.downloader.i18n.stringsFor

@Composable
fun SamraTheme(lang: Lang, dark: Boolean, content: @Composable () -> Unit) {
    val c = if (dark) DarkColors else LightColors
    val scheme = if (dark)
        darkColorScheme(
            primary = c.accent, onPrimary = c.onAccent,
            background = c.bg, onBackground = c.text,
            surface = c.surface, onSurface = c.text,
            surfaceVariant = c.card, onSurfaceVariant = c.text2,
            error = c.error,
        )
    else
        lightColorScheme(
            primary = c.accent, onPrimary = c.onAccent,
            background = c.bg, onBackground = c.text,
            surface = c.surface, onSurface = c.text,
            surfaceVariant = c.card, onSurfaceVariant = c.text2,
            error = c.error,
        )
    val ar = lang == Lang.AR
    val ui = uiFamily(ar)

    // Make the status bar (top) and navigation bar (bottom) fully transparent so
    // the app background flows edge-to-edge behind them — exactly like Instagram /
    // X / Telegram / WhatsApp. The bar icons flip dark/light to match the theme,
    // and the nav-bar contrast scrim is removed so there's no grey strip. This
    // runs reactively, so it updates the moment the user toggles dark/light.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            @Suppress("DEPRECATION")
            run {
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    CompositionLocalProvider(
        LocalSamraColors provides c,
        LocalStrings provides stringsFor(lang),
        LocalLang provides lang,
        LocalSerifFont provides serifFamily(ar),
        LocalUiFont provides ui,
        LocalLayoutDirection provides if (ar) LayoutDirection.Rtl else LayoutDirection.Ltr,
    ) {
        MaterialTheme(colorScheme = scheme) {
            CompositionLocalProvider(
                LocalTextStyle provides TextStyle(fontFamily = ui, color = c.text),
                content = content,
            )
        }
    }
}
