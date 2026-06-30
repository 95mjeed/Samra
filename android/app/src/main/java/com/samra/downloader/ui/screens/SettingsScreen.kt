package com.samra.downloader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.samra.downloader.AppViewModel
import com.samra.downloader.i18n.Lang
import com.samra.downloader.i18n.Num
import com.samra.downloader.ui.Sym
import com.samra.downloader.ui.colors
import com.samra.downloader.ui.str

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val c = colors(); val t = str(); val lang = vm.lang
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(t.settingsTitle, color = c.text, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)

        // Output format
        SectionLabel(t.formatLabel)
        val fmts = listOf(
            Triple("mp3", "MP3", if (lang == Lang.AR) "متوافق عالمياً" else "Universal"),
            Triple("m4b", "M4B", if (lang == Lang.AR) "فصول + بيانات" else "Chapters + meta"),
            Triple("m4a", "M4A", if (lang == Lang.AR) "جودة عالية" else "High quality"),
            Triple("opus", "Opus", if (lang == Lang.AR) "حجم أصغر" else "Smallest size"),
        )
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            fmts.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { (k, label, desc) ->
                        val a = vm.format == k
                        Column(
                            Modifier.weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (a) c.accentSoft else c.card)
                                .border(1.dp, if (a) c.accent else c.line, RoundedCornerShape(14.dp))
                                .clickable { vm.format = k }
                                .padding(14.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(label, color = if (a) c.accent else c.text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                                Spacer(Modifier.weight(1f))
                                if (a) Sym("check_circle", c.accent, 18.dp)
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(desc, color = c.text2, fontSize = 12.sp)
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        // Toggles
        ToggleRow(t.tCombine, t.tCombineD, vm.combine) { vm.combine = it }
        ToggleRow(t.tSkip, t.tSkipD, vm.skip) { vm.skip = it }
        ToggleRow(t.tWifi, t.tWifiD, vm.wifi) { vm.wifi = it }

        // Appearance
        SectionLabel(t.appearance)
        Text(t.theme, color = c.text2, fontSize = 13.sp)
        Segmented(
            options = listOf("system" to t.themeSystem, "dark" to t.themeDark, "light" to t.themeLight),
            selected = if (vm.dark) "dark" else "light",
            icons = mapOf("system" to "brightness_auto", "dark" to "dark_mode", "light" to "light_mode"),
        ) { vm.dark = (it != "light") }

        Spacer(Modifier.height(4.dp))
        Text(t.language, color = c.text2, fontSize = 13.sp)
        Segmented(
            options = listOf("ar" to "العربية", "en" to "English"),
            selected = if (vm.lang == Lang.AR) "ar" else "en",
            icons = emptyMap(),
        ) { vm.lang = if (it == "ar") Lang.AR else Lang.EN }

        // Storage (real device usage)
        SectionLabel(t.storage)
        val storage = remember {
            try {
                val s = android.os.StatFs(android.os.Environment.getDataDirectory().path)
                val total = s.totalBytes; val used = total - s.availableBytes
                Triple((used / 1048576.0).toInt(), (total / 1048576.0).toInt(), if (total > 0) used.toFloat() / total else 0f)
            } catch (_: Exception) { Triple(0, 0, 0f) }
        }
        Box(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).background(c.line)) {
            Box(
                Modifier.fillMaxWidth(storage.third.coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(6.dp))
                    .background(Brush.horizontalGradient(listOf(c.accent, c.accentDeep)))
            )
        }
        Text("${Num.size(storage.first, lang)} / ${Num.size(storage.second, lang)}", color = c.text2, fontSize = 12.sp)
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(text, color = colors().text3, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
}

@Composable
fun ToggleRow(label: String, desc: String, on: Boolean, onChange: (Boolean) -> Unit) {
    val c = colors()
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.card)
            .border(1.dp, c.line, RoundedCornerShape(14.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(desc, color = c.text2, fontSize = 12.sp)
        }
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier.width(48.dp).height(28.dp).clip(RoundedCornerShape(16.dp))
                .background(if (on) c.accent else c.line2).clickable { onChange(!on) }
                .padding(3.dp),
            contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(Modifier.size(22.dp).clip(RoundedCornerShape(11.dp)).background(Color.White))
        }
    }
}

@Composable
fun Segmented(
    options: List<Pair<String, String>>,
    selected: String,
    icons: Map<String, String>,
    onSelect: (String) -> Unit,
) {
    val c = colors()
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.card)
            .border(1.dp, c.line, RoundedCornerShape(12.dp)).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEach { (k, label) ->
            val a = selected == k
            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(9.dp))
                    .background(if (a) c.accent else Color.Transparent).clickable { onSelect(k) }
                    .padding(vertical = 9.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                icons[k]?.let { Sym(it, if (a) c.onAccent else c.text2, 16.dp); Spacer(Modifier.width(5.dp)) }
                Text(label, color = if (a) c.onAccent else c.text2, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
