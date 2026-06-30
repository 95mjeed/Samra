package com.samra.downloader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.samra.downloader.AppViewModel
import com.samra.downloader.i18n.Lang
import com.samra.downloader.i18n.Num
import com.samra.downloader.model.Speeds
import com.samra.downloader.playback.PlaybackController
import com.samra.downloader.ui.Sym
import com.samra.downloader.ui.Motion
import com.samra.downloader.ui.bounceClick
import com.samra.downloader.ui.colors
import com.samra.downloader.ui.coverBrush
import com.samra.downloader.ui.str
import kotlinx.coroutines.launch

/**
 * Localize a chapter label: generic embedded titles (blank, "1", "Chapter 1",
 * "Track 2", «الفصل ٣» …) become «الفصل N» / "Chapter N" by the app language;
 * real chapter names are kept as-is.
 */
private fun chapterName(title: String, index: Int, lang: Lang): String {
    val t = title.trim()
    val generic = t.isEmpty() ||
        Regex("^\\d+$").matches(t) ||
        Regex("(?i)^(chapter|track|part|section)\\s*[\\d٠-٩]*$").matches(t) ||
        Regex("^(الفصل|الجزء|المقطع|مقطع|الباب)\\s*[\\d٠-٩]*$").matches(t)
    return if (generic) {
        if (lang == Lang.AR) "الفصل ${Num.ar(index + 1, lang)}" else "Chapter ${index + 1}"
    } else t
}

@Composable
fun PlayerScreen(vm: AppViewModel) {
    val c = colors(); val t = str(); val lang = vm.lang
    val ctx = LocalContext.current
    val entry = vm.library.getOrNull(vm.playing ?: -1)
    if (entry == null) {
        Box(Modifier.fillMaxSize().background(c.bg), contentAlignment = Alignment.Center) {
            Text("—", color = c.text2)
        }
        return
    }
    val b = entry.book

    LaunchedEffect(vm.playing) {
        if (PlaybackController.entryIndex != vm.playing)
            PlaybackController.open(ctx, entry, vm.playing!!, vm.audioPosOf(vm.keyOf(entry)))
    }

    var pos by remember { mutableStateOf(PlaybackController.globalPosition()) }
    LaunchedEffect(Unit) {
        while (true) {
            pos = PlaybackController.globalPosition()
            if (vm.sleepActive && vm.sleepLeft > 0) {
                vm.sleepLeft -= 1
                if (vm.sleepLeft <= 0) { PlaybackController.pause(); vm.sleepMinutes = null; vm.sleepIsChapter = false }
            }
            kotlinx.coroutines.delay(1000)
        }
    }

    val playing = PlaybackController.isPlaying
    val total = PlaybackController.totalSec.coerceAtLeast(1)
    val offs = PlaybackController.chapterStarts
    val cur = pos.coerceIn(0, total)
    var curCh = 0; for (i in offs.indices) if (cur >= offs[i]) curCh = i
    val chTitles = entry.chapters.map { it.title }
    val chStart = offs.getOrElse(curCh) { 0 }; val chLen = PlaybackController.chapterLength(curCh)
    val chDone = cur - chStart; val chPct = if (chLen > 0) chDone.toFloat() / chLen else 0f
    val ppct = cur.toFloat() / total

    // Swipe down (from the top bar) to dismiss the player back to the library,
    // with the whole surface following the drag and springing back if released short.
    val scope = rememberCoroutineScope()
    val dragY = remember { Animatable(0f) }
    val dismissPx = with(LocalDensity.current) { 140.dp.toPx() }
    val dragHeader = Modifier.pointerInput(Unit) {
        detectVerticalDragGestures(
            onVerticalDrag = { _, dy -> scope.launch { dragY.snapTo((dragY.value + dy).coerceAtLeast(0f)) } },
            onDragEnd = { if (dragY.value > dismissPx) vm.go("library") else scope.launch { dragY.animateTo(0f) } },
            onDragCancel = { scope.launch { dragY.animateTo(0f) } },
        )
    }

    Box(
        Modifier.fillMaxSize()
            .offset { IntOffset(0, dragY.value.roundToInt()) }
            .background(c.bg).background(
            Brush.radialGradient(listOf(b.c1.copy(alpha = 0.25f), c.bg), center = Offset(0.5f * 1000f, -0.08f * 1000f), radius = 900f)
        )
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp)) {
            Row(Modifier.fillMaxWidth().then(dragHeader), verticalAlignment = Alignment.CenterVertically) {
                Sym("expand_more", c.text, 26.dp, Modifier.clip(CircleShape).bounceClick(scaleDown = 0.8f) { vm.go("library") })
                Spacer(Modifier.weight(1f))
                Text(t.nowPlaying, color = c.text2, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp)
                Spacer(Modifier.weight(1f))
                Box(contentAlignment = Alignment.Center) {
                    Sym("bookmarks", c.text, 24.dp, Modifier.clip(CircleShape).clickable { vm.bmSheet = true })
                    if (vm.pBookmarks.isNotEmpty()) Box(
                        Modifier.align(Alignment.TopEnd).offset(x = 8.dp, y = (-6).dp).clip(RoundedCornerShape(9.dp)).background(c.accent).padding(horizontal = 5.dp),
                    ) { Text(Num.ar(vm.pBookmarks.size, lang), color = c.onAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                }
            }

            Spacer(Modifier.height(20.dp))
            // Cover centered, shown in full (no crop).
            com.samra.downloader.ui.PlayerCover(entry, lang, Modifier.size(248.dp).align(Alignment.CenterHorizontally))

            Spacer(Modifier.height(16.dp))
            Text(b.title(lang), color = c.text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Start, modifier = Modifier.fillMaxWidth(), maxLines = 2, overflow = TextOverflow.Ellipsis)
            run {
                val author = b.author(lang)
                val narr = b.narrator(lang)
                val sub = listOfNotNull(
                    author.ifBlank { null },
                    if (narr.isNotBlank()) "${t.narratedBy} $narr" else null,
                ).joinToString(" · ")
                if (sub.isNotBlank()) Text(sub, color = c.text2, fontSize = 13.sp, textAlign = TextAlign.Start, modifier = Modifier.fillMaxWidth(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(c.card).border(1.dp, c.line, RoundedCornerShape(13.dp)).padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Sym("graphic_eq", c.accent, 18.dp); Spacer(Modifier.width(8.dp))
                Text("${chapterName(chTitles.getOrElse(curCh) { "" }, curCh, lang)} · ${Num.ar(curCh + 1, lang)}/${Num.ar(chTitles.size, lang)}", color = c.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("${Num.mmss(chDone, lang)} / ${Num.mmss(chLen, lang)}", color = c.text3, fontSize = 12.sp)
            }

            Spacer(Modifier.height(16.dp))
            // The timeline (progress, times, transport controls) always reads
            // left-to-right — it maps to time, not text — so the ◀◀ / ▶▶ and
            // ±30s icons keep matching their direction & function in Arabic too.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Scrubber(ppct, offs, total, vm.pBookmarks, c.line, c.accent, c.bg) { sec ->
                    PlaybackController.seekGlobal(sec); pos = sec
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(Num.hms(cur, lang), color = c.text2, fontSize = 11.sp)
                    Text("-${Num.hms(total - cur, lang)}", color = c.text2, fontSize = 11.sp)
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    Sym("skip_previous", c.text, 30.dp, Modifier.clip(CircleShape).bounceClick(scaleDown = 0.82f) { PlaybackController.prevChapter() })
                    Sym("replay_30", c.text, 30.dp, Modifier.clip(CircleShape).bounceClick(scaleDown = 0.82f) { PlaybackController.skip(-30) })
                    Box(
                        Modifier.size(76.dp).clip(CircleShape).background(c.accent).bounceClick(scaleDown = 0.88f) { PlaybackController.toggle() },
                        contentAlignment = Alignment.Center,
                    ) { Sym(if (playing) "pause" else "play_arrow", c.onAccent, 38.dp) }
                    Sym("forward_30", c.text, 30.dp, Modifier.clip(CircleShape).bounceClick(scaleDown = 0.82f) { PlaybackController.skip(30) })
                    Sym("skip_next", c.text, 30.dp, Modifier.clip(CircleShape).bounceClick(scaleDown = 0.82f) { PlaybackController.nextChapter() })
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                SecCtl("speed", "${Num.ar(Speeds[PlaybackController.speedIdx], lang)}×", c.accent) { PlaybackController.cycleSpeed() }
                val sleepLabel = if (vm.sleepIsChapter) t.endOfChapter else if (vm.sleepActive) Num.mmss(vm.sleepLeft, lang) else t.sleepShort
                SecCtl(if (vm.sleepActive) "bedtime" else "bedtime_off", sleepLabel, if (vm.sleepActive) c.accent else c.text2) { vm.sleepSheet = true }
                SecCtl("bookmark_add", t.addBm, c.text2) { if (vm.addBookmark()) vm.toast = if (lang == Lang.AR) "تمت إضافة علامة" else "Bookmark added" }
            }

            if (chTitles.size > 1) {
                Spacer(Modifier.height(20.dp))
                // Collapsible Chapters button
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(c.card).border(1.dp, c.line, RoundedCornerShape(13.dp))
                        .clickable { vm.chListOpen = !vm.chListOpen }.padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Sym("toc", c.accent, 18.dp); Spacer(Modifier.width(8.dp))
                    Text(t.chapters, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                    Text(if (lang == Lang.AR) "${Num.ar(chTitles.size, lang)} فصول" else "${chTitles.size} chapters", color = c.text3, fontSize = 12.sp)
                    Spacer(Modifier.width(8.dp))
                    Sym(if (vm.chListOpen) "expand_less" else "expand_more", c.text2, 20.dp)
                }
                if (vm.chListOpen) {
                Spacer(Modifier.height(8.dp))
                chTitles.forEachIndexed { i, ti ->
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp)).background(if (i == curCh) c.accentSoft else Color.Transparent).clickable { PlaybackController.jumpChapter(i) }.padding(10.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Sym(if (i == curCh) "graphic_eq" else if (i < curCh) "check_circle" else "play_circle", if (i == curCh) c.accent else if (i < curCh) c.success else c.text3, 18.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(chapterName(ti, i, lang), color = if (i == curCh) c.accent else if (i < curCh) c.text3 else c.text, fontSize = 14.sp, fontWeight = if (i == curCh) FontWeight.ExtraBold else FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text(Num.mmss(PlaybackController.chapterLength(i), lang), color = c.text3, fontSize = 12.sp)
                        }
                        if (i == curCh) {
                            Spacer(Modifier.height(6.dp))
                            Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(c.line)) {
                                Box(Modifier.fillMaxWidth(chPct.coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(c.accent))
                            }
                        }
                    }
                }
                }
            }
            Spacer(Modifier.height(40.dp))
        }

        if (vm.sleepSheet) SleepSheet(vm)
        if (vm.bmSheet) BookmarksSheet(vm)
    }
}

@Composable
private fun Scrubber(
    pct: Float, offs: List<Int>, total: Int, bookmarks: List<Int>,
    track: Color, accent: Color, bg: Color, onSeek: (Int) -> Unit,
) {
    // BiasAlignment is layout-direction aware, so ticks/thumb position correctly in RTL too.
    fun bias(f: Float) = f.coerceIn(0f, 1f) * 2f - 1f
    val ld = LocalLayoutDirection.current
    var widthPx by remember { mutableStateOf(1) }
    var scrubbing by remember { mutableStateOf(false) }
    val thumbSize by animateDpAsState(if (scrubbing) 22.dp else 16.dp, Motion.snappy(), label = "thumb")
    fun seekAt(x: Float) {
        val raw = (x / widthPx).coerceIn(0f, 1f)
        val frac = if (ld == LayoutDirection.Rtl) 1f - raw else raw
        onSeek((frac * total).toInt())
    }
    Box(
        Modifier.fillMaxWidth().height(26.dp)
            .onSizeChanged { widthPx = it.width.coerceAtLeast(1) }
            .pointerInput(total) { detectTapGestures { o -> seekAt(o.x) } }
            .pointerInput(total) {
                detectHorizontalDragGestures(
                    onDragStart = { scrubbing = true },
                    onDragEnd = { scrubbing = false },
                    onDragCancel = { scrubbing = false },
                ) { ch, _ -> ch.consume(); seekAt(ch.position.x) }
            }
    ) {
        Box(Modifier.fillMaxWidth().height(6.dp).align(Alignment.Center).clip(RoundedCornerShape(3.dp)).background(track)) {
            Box(Modifier.fillMaxWidth(pct.coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(accent))
        }
        offs.drop(1).forEach { o ->
            Box(Modifier.align(BiasAlignment(bias(o.toFloat() / total), 0f)).width(2.dp).height(6.dp).background(bg.copy(alpha = 0.7f)))
        }
        bookmarks.forEach { bm ->
            Box(Modifier.align(BiasAlignment(bias(bm.toFloat() / total), 0f)).size(8.dp).clip(CircleShape).background(accent))
        }
        Box(Modifier.align(BiasAlignment(bias(pct), 0f)).size(thumbSize).clip(CircleShape).background(accent))
    }
}

@Composable
private fun SecCtl(icon: String, label: String, color: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clip(RoundedCornerShape(10.dp)).bounceClick(scaleDown = 0.88f) { onClick() }.padding(horizontal = 10.dp, vertical = 4.dp)) {
        Sym(icon, color, 22.dp)
        Spacer(Modifier.height(3.dp))
        Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}
