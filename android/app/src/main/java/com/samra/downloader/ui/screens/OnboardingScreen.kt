package com.samra.downloader.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import com.samra.downloader.AppViewModel
import com.samra.downloader.R
import com.samra.downloader.i18n.Lang
import com.samra.downloader.ui.Sym
import com.samra.downloader.ui.squirclePath
import com.samra.downloader.ui.colors
import com.samra.downloader.ui.str

@Composable
fun OnboardingScreen(vm: AppViewModel) {
    val c = colors(); val t = str()
    // FIRST thing on first launch: ask the language, before the welcome slides.
    val langPicked = remember { mutableStateOf(false) }
    if (!langPicked.value) {
        LanguageGate(vm) { langPicked.value = true }
        return
    }
    val isLast = vm.onbStep >= 2
    val slide = t.onb.getOrElse(vm.onbStep) { t.onb[0] }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.bg)
            .background(
                Brush.radialGradient(
                    colors = listOf(c.accent.copy(alpha = 0.16f), c.bg),
                    center = Offset(0.5f * 1000f, -0.05f * 1000f), radius = 1100f,
                )
            )
            .padding(horizontal = 28.dp),
    ) {
        // Scrollable hero — always fits, even on short screens
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Spacer(Modifier.height(24.dp))
            // App mark: book + headphones badge on the amber gradient (shared with the language gate)
            AppMark(108.dp)

            Spacer(Modifier.height(20.dp))
            Text(t.appNameAr, color = c.text, fontSize = 44.sp, fontWeight = FontWeight.ExtraBold)
            Text("SAMRA", color = c.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 6.sp)

            Spacer(Modifier.height(36.dp))
            Text(slide.first, color = c.text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Text(slide.second, color = c.text2, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 270.dp), lineHeight = 20.sp)

            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                t.onb.indices.forEach { i ->
                    Box(Modifier.height(7.dp).width(if (i == vm.onbStep) 22.dp else 7.dp).clip(RoundedCornerShape(4.dp)).background(if (i == vm.onbStep) c.accent else c.line2))
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        // Pinned actions
        Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            if (!isLast) {
                Button(
                    onClick = { vm.onbStep = (vm.onbStep + 1).coerceAtMost(2) },
                    colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
                    shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(),
                ) { Text(t.next, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold) }
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = { vm.go("library") }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text(t.skip, color = c.text3, fontSize = 13.sp) }
            } else {
                Button(
                    onClick = { vm.go("library") },
                    colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
                    shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(),
                ) { Text(if (vm.lang == Lang.AR) "ابدأ" else "Get started", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold) }
            }
        }
    }
}

/** First-launch language picker — shown before the welcome slides. */
@Composable
private fun LanguageGate(vm: AppViewModel, onPicked: () -> Unit) {
    val c = colors()
    Column(
        Modifier.fillMaxSize().background(c.bg)
            .background(
                Brush.radialGradient(
                    colors = listOf(c.accent.copy(alpha = 0.16f), c.bg),
                    center = Offset(0.5f * 1000f, -0.05f * 1000f), radius = 1100f,
                )
            )
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        AppMark(108.dp)
        Spacer(Modifier.height(22.dp))
        Text("اختر لغتك", color = c.text, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(2.dp))
        Text("Choose your language", color = c.text2, fontSize = 14.sp)
        Spacer(Modifier.height(30.dp))
        Button(
            onClick = { vm.lang = Lang.AR; vm.savePrefs(); onPicked() },
            colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
            shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(),
        ) { Text("العربية", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold) }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = { vm.lang = Lang.EN; vm.savePrefs(); onPicked() }, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
            Text("English", color = c.text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.weight(1f))
    }
}

/**
 * The canonical Samra app mark. It renders the EXACT same vector layers as the launcher
 * icon (`ic_launcher_background` + `ic_launcher_foreground`), clipped to a squircle-style
 * rounded tile, so the language gate, the welcome slides, and the home-screen launcher icon
 * are pixel-for-pixel identical — one source of truth, no differences.
 */
@Composable
private fun AppMark(size: Dp) {
    Box(Modifier.size(size)) {
        // Antialiased amber squircle tile — drawn (not clipped) so the edge is smooth,
        // matching the launcher mask + its #ECA93C→#C9881F background gradient.
        Canvas(Modifier.fillMaxSize()) {
            drawPath(
                path = squirclePath(this.size),
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFECA93C), Color(0xFFC9881F)),
                    start = Offset.Zero,
                    end = Offset(this.size.width, this.size.height),
                ),
            )
        }
        // Exact same book + headphones glyphs as the launcher foreground (vector, antialiased).
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
