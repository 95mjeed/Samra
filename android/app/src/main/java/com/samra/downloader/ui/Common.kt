package com.samra.downloader.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.samra.downloader.i18n.Lang
import com.samra.downloader.i18n.LocalLang
import com.samra.downloader.i18n.LocalStrings
import com.samra.downloader.i18n.Str
import com.samra.downloader.ui.theme.LocalSamraColors
import com.samra.downloader.ui.theme.SamraColors

@Composable @ReadOnlyComposable
fun colors(): SamraColors = LocalSamraColors.current

@Composable @ReadOnlyComposable
fun str(): Str = LocalStrings.current

@Composable @ReadOnlyComposable
fun lang(): Lang = LocalLang.current

@Composable
fun Sym(name: String, tint: Color, size: Dp = 20.dp, modifier: Modifier = Modifier) {
    Icon(imageVector = sym(name), contentDescription = null, tint = tint, modifier = modifier.size(size))
}
