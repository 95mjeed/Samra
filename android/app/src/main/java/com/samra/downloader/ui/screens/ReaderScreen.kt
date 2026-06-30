package com.samra.downloader.ui.screens

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.samra.downloader.DrawStroke
import com.samra.downloader.RangeAnn
import kotlin.math.roundToInt
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.samra.downloader.AppViewModel
import com.samra.downloader.i18n.Lang
import com.samra.downloader.i18n.Num
import com.samra.downloader.model.ReaderParas
import com.samra.downloader.reader.EpubChapter
import com.samra.downloader.reader.EpubParser
import com.samra.downloader.ui.Sym
import com.samra.downloader.ui.colors
import com.samra.downloader.ui.str
import com.samra.downloader.ui.Motion
import com.samra.downloader.ui.bounceClick
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import java.io.File

private data class RTheme(val bg: Color, val tx: Color, val mu: Color, val line: Color, val card: Color)

private val ReaderThemes = mapOf(
    "paper" to RTheme(Color(0xFFFBF7EF), Color(0xFF2A2622), Color(0xFF9A8E78), Color(0xFFE7DEC9), Color(0xFFFFFFFF)),
    "sepia" to RTheme(Color(0xFFEFE3CB), Color(0xFF473C29), Color(0xFF9A8868), Color(0xFFDCCBA6), Color(0xFFF7EFDA)),
    "night" to RTheme(Color(0xFF15120D), Color(0xFFCFC8BA), Color(0xFF867C68), Color(0xFF2B261D), Color(0xFF1F1B14)),
)

/** A chapter's display title, falling back to a localized "Chapter N" / "الفصل N". */
private fun chapterLabel(title: String, idx: Int, lang: Lang): String =
    title.ifBlank { if (lang == Lang.AR) "الفصل ${Num.ar(idx + 1, lang)}" else "Chapter ${idx + 1}" }

@Composable
fun ReaderScreen(vm: AppViewModel) {
    val c = colors(); val t = str(); val lang = vm.lang
    val entry = vm.library.getOrNull(vm.reading ?: -1)
    if (entry == null) {
        Box(Modifier.fillMaxSize().background(c.bg), contentAlignment = Alignment.Center) { Text("—", color = c.text2) }
        return
    }
    val b = entry.book
    val rth = ReaderThemes[vm.readerTheme] ?: ReaderThemes["sepia"]!!
    val serifFam = com.samra.downloader.ui.theme.LocalSerifFont.current
    val sansFam = com.samra.downloader.ui.theme.LocalUiFont.current
    val fam = if (vm.readerFont == "serif") serifFam else sansFam
    val ebookPath = entry.ebookPath
    val mode = when (ebookPath?.substringAfterLast('.', "")?.lowercase()) {
        "epub" -> "epub"; "pdf" -> "pdf"; else -> "none"
    }

    // Page count is read off the main thread (opening a PdfRenderer is disk IO +
    // native init) so opening a large PDF doesn't drop frames on the slide-in.
    var pdfCount by remember(ebookPath) { mutableStateOf(1) }
    LaunchedEffect(ebookPath, mode) {
        if (mode == "pdf") pdfCount = withContext(Dispatchers.IO) { pdfPageCount(ebookPath) }
    }

    // EPUB content
    var chapters by remember(ebookPath) { mutableStateOf<List<EpubChapter>?>(null) }
    LaunchedEffect(ebookPath, mode) {
        if (mode == "epub" && ebookPath != null) chapters = withContext(Dispatchers.IO) { EpubParser.parse(ebookPath) }
    }

    // Clamp a restored resume index into range (the saved value may exceed the
    // freshly-parsed chapter/page count), then mark "read" at the end.
    LaunchedEffect(vm.readerCh, vm.readerPage, mode, chapters, pdfCount) {
        if (mode == "epub") chapters?.let { vm.readerCh = vm.readerCh.coerceIn(0, (it.size - 1).coerceAtLeast(0)) }
        else if (mode == "pdf") vm.readerPage = vm.readerPage.coerceIn(1, pdfCount.coerceAtLeast(1))
        val done = when (mode) {
            "epub" -> (chapters?.size ?: 0) > 1 && vm.readerCh >= (chapters!!.size - 1)
            "pdf" -> pdfCount > 1 && vm.readerPage >= pdfCount
            else -> false
        }
        if (done) vm.markFinished(vm.keyOf(entry))
    }
    // Persist progress + resume position, debounced so a fast seek-bar drag across
    // a long book doesn't fire a burst of SharedPreferences writes.
    LaunchedEffect(mode, chapters, pdfCount) {
        snapshotFlow { if (mode == "pdf") vm.readerPage else vm.readerCh }
            .collectLatest {
                delay(350)   // collectLatest cancels this on a new page → debounce
                val total = when (mode) { "epub" -> chapters?.size ?: 0; "pdf" -> pdfCount; else -> 0 }
                val cur = when (mode) { "epub" -> vm.readerCh + 1; "pdf" -> vm.readerPage; else -> 0 }
                if (total > 0) vm.setProgress(vm.keyOf(entry), cur.toFloat() / total)
                if (mode != "none") vm.saveReaderPos(vm.keyOf(entry), if (mode == "pdf") vm.readerPage else vm.readerCh)
            }
    }
    // Always flush the latest resume position when leaving the reader (covers a
    // quick exit within the debounce window).
    DisposableEffect(mode, entry.ebookPath) {
        onDispose {
            if (mode != "none") vm.saveReaderPos(vm.keyOf(entry), if (mode == "pdf") vm.readerPage else vm.readerCh)
        }
    }

    val key = vm.keyOf(entry)
    val ld = LocalLayoutDirection.current
    // Step one page (pdf) / chapter (epub). Used by the buttons and swipe.
    val step: (Int) -> Unit = { dir ->
        when (mode) {
            "pdf" -> vm.readerPage = (vm.readerPage + dir).coerceIn(1, pdfCount.coerceAtLeast(1))
            "epub" -> vm.readerCh = (vm.readerCh + dir).coerceIn(0, ((chapters?.size ?: 1) - 1).coerceAtLeast(0))
            else -> {}
        }
    }
    // Flipping to a different chapter (by any means) clears the reference
    // highlight. A footnote jump sets readerCh == readerJumpCh, so it survives
    // the jump itself and only clears on the NEXT flip (or a page tap).
    LaunchedEffect(vm.readerCh) {
        if (vm.readerJumpPara >= 0 && vm.readerCh != vm.readerJumpCh) {
            vm.readerJumpPara = -1; vm.readerJumpCh = -1
        }
    }

    Box(Modifier.fillMaxSize().background(rth.bg)) {
        Column(Modifier.fillMaxSize()) {
            // Top bar
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Sym("arrow_back", rth.tx, 24.dp, Modifier.clip(CircleShape).clickable { vm.go("library") })
                Text(b.title(lang), color = rth.tx, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                // Bookmark the current chapter (epub) / page (pdf). Stored 0-based
                // for both (PDF readerPage is 1-based) to match the bookmarks panel,
                // which displays/jumps with unit+1.
                val curUnit = if (mode == "pdf") vm.readerPage - 1 else vm.readerCh
                val marked = vm.isBookmarked(key, curUnit)
                Sym(if (marked) "bookmark" else "bookmark_border", if (marked) c.accent else rth.mu, 22.dp, Modifier.clip(CircleShape).clickable { vm.toggleBookmark(key, curUnit) })
                if (mode != "none") {
                    Spacer(Modifier.width(6.dp))
                    Sym("bookmarks", rth.tx, 21.dp, Modifier.clip(CircleShape).clickable { vm.bmPanelOpen = true })
                    Spacer(Modifier.width(6.dp))
                    Sym("draw", if (vm.penMode) c.accent else rth.tx, 21.dp, Modifier.clip(CircleShape).clickable { vm.penMode = !vm.penMode; vm.penEraser = false; vm.penMarker = false })
                }
                if (mode == "epub") {
                    Spacer(Modifier.width(6.dp))
                    Sym("toc", rth.tx, 22.dp, Modifier.clip(CircleShape).clickable { vm.readerToc = !vm.readerToc; vm.readerPanel = false })
                }
                Spacer(Modifier.width(8.dp))
                Box(Modifier.clip(RoundedCornerShape(8.dp)).clickable { vm.readerPanel = !vm.readerPanel; vm.readerToc = false }.padding(horizontal = 6.dp)) {
                    Text("Aa", color = rth.tx, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }

            val strokeUnit = when (mode) { "epub" -> vm.readerCh; "pdf" -> vm.readerPage; else -> 0 }
            val sKey = vm.strokeKey(key, strokeUnit)

            Box(
                Modifier.weight(1f).fillMaxWidth()
                    // Tap the page (not a link) → clear the reference highlight.
                    .pointerInput(Unit) {
                        detectTapGestures { if (vm.readerJumpPara >= 0) { vm.readerJumpPara = -1; vm.readerJumpCh = -1 } }
                    }
                    .then(
                    // Page-turn swipe is disabled while drawing with the pen.
                    if (!vm.penMode) Modifier.pointerInput(mode, chapters, pdfCount) {
                        var dx = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { dx = 0f },
                            onHorizontalDrag = { ch, d -> ch.consume(); dx += d },
                            onDragEnd = {
                                if (kotlin.math.abs(dx) > 70f) {
                                    val dir = if (dx < 0) 1 else -1        // LTR: swipe left → next
                                    step(if (ld == LayoutDirection.Rtl) -dir else dir)
                                }
                            },
                        )
                    } else Modifier,
                ),
            ) {
                // A clean horizontal page-slide: the outgoing page slides off one
                // edge while the new one slides in from the other (RTL-aware), with
                // a soft cross-fade — like turning a page, not a small nudge.
                AnimatedContent(
                    targetState = strokeUnit,
                    transitionSpec = {
                        val forward = targetState > initialState
                        val sign = if ((ld == LayoutDirection.Rtl) == forward) -1 else 1
                        (slideInHorizontally(tween(Motion.Slow, easing = Motion.Emphasized)) { w -> sign * w } +
                            fadeIn(tween(Motion.Med)))
                            .togetherWith(
                                slideOutHorizontally(tween(Motion.Slow, easing = Motion.Emphasized)) { w -> -sign * w } +
                                    fadeOut(tween(Motion.Med))
                            )
                            .using(SizeTransform(clip = false))
                    },
                    label = "page",
                    modifier = Modifier.fillMaxSize(),
                ) { unit ->
                    when (mode) {
                        "epub" -> EpubBody(vm, chapters, rth, fam, lang, key, unit)
                        "pdf" -> PdfBody(vm, ebookPath!!, rth, key, unit)
                        else -> SampleBody(vm, rth, fam, lang)
                    }
                }
                // Pen toolbar is fixed to the viewport; the drawing canvas itself
                // lives inside each body's scroll so strokes stick to the page.
                if (vm.penMode) PenToolbar(vm, sKey, rth, Modifier.align(Alignment.BottomCenter))
            }

            // Footer
            val (cur, total) = when (mode) {
                "epub" -> (vm.readerCh.coerceIn(0, ((chapters?.size ?: 1) - 1).coerceAtLeast(0)) + 1) to (chapters?.size ?: 1)
                "pdf" -> vm.readerPage to pdfCount
                else -> vm.readerCh + 1 to 8
            }
            val pct = if (total > 0) cur.toFloat() / total else 0f
            // Drag/tap on the bar to jump chapter (epub) or page (pdf). RTL-aware.
            val seekReader: (Float) -> Unit = { raw ->
                val frac = (if (ld == LayoutDirection.Rtl) 1f - raw else raw).coerceIn(0f, 1f)
                when (mode) {
                    "epub" -> { val n = chapters?.size ?: 0; if (n > 0) vm.readerCh = (frac * n).toInt().coerceIn(0, n - 1) }
                    "pdf" -> { if (pdfCount > 0) vm.readerPage = ((frac * pdfCount).toInt() + 1).coerceIn(1, pdfCount) }
                    else -> {}
                }
            }
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).border(1.dp, rth.line, CircleShape).bounceClick(scaleDown = 0.85f) {
                    when (mode) {
                        "pdf" -> vm.readerPage = (vm.readerPage - 1).coerceAtLeast(1)
                        else -> vm.readerCh = (vm.readerCh - 1).coerceAtLeast(0)
                    }
                }, contentAlignment = Alignment.Center) { Sym("chevron_left", rth.tx, 22.dp) }
                Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                    var barW by remember { mutableStateOf(1) }
                    Box(
                        Modifier.fillMaxWidth().height(22.dp)
                            .onSizeChanged { barW = it.width.coerceAtLeast(1) }
                            .pointerInput(mode, total) { detectTapGestures { o -> seekReader(o.x / barW) } }
                            .pointerInput(mode, total) { detectHorizontalDragGestures { ch, _ -> ch.consume(); seekReader(ch.position.x / barW) } },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(rth.line)) {
                            Box(Modifier.fillMaxWidth(pct.coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(c.accent))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        (if (lang == Lang.AR) "${Num.ar(cur, lang)} / ${Num.ar(total, lang)}" else "$cur / $total") + "  ·  ${Num.ar((pct * 100).toInt(), lang)}%",
                        color = rth.mu, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                    )
                }
                Box(Modifier.size(44.dp).clip(CircleShape).background(c.accent).bounceClick(scaleDown = 0.85f) {
                    when (mode) {
                        "pdf" -> vm.readerPage = (vm.readerPage + 1).coerceAtMost(pdfCount)
                        else -> vm.readerCh = (vm.readerCh + 1).coerceAtMost(((chapters?.size ?: 1) - 1).coerceAtLeast(0))
                    }
                }, contentAlignment = Alignment.Center) { Sym("chevron_right", c.onAccent, 22.dp) }
            }
        }

        if (vm.readerPanel) ReaderSettingsSheet(vm, rth.tx, rth.card, rth.line)
        if (vm.readerToc && mode == "epub") ReaderTocSheet(vm, rth, chapters?.mapIndexed { i, cc -> chapterLabel(cc.title, i, lang) } ?: emptyList())
        if (vm.bmPanelOpen && mode != "none") BookmarksPanel(vm, rth, chapters, mode, pdfCount, key, lang)
    }
}

@Composable
private fun EpubBody(vm: AppViewModel, chapters: List<EpubChapter>?, rth: RTheme, fam: FontFamily, lang: Lang, bookKey: String?, chapterIndex: Int) {
    val c = colors()
    if (chapters == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("…", color = rth.mu) }
        return
    }
    if (chapters.isEmpty()) { SampleBody(vm, rth, fam, lang); return }
    val idx = chapterIndex.coerceIn(0, chapters.size - 1)
    val ch = chapters[idx]
    val sKey = vm.strokeKey(bookKey, idx)

    // Jump targets for footnotes / references, built once per book.
    val fileToChapter = remember(chapters) { chapters.mapIndexed { i, cc -> cc.file to i }.toMap() }
    val anchorMap = remember(chapters) {
        val m = HashMap<String, Pair<Int, Int>>()
        chapters.forEachIndexed { i, cc -> cc.anchors.forEach { (id, p) -> m["${cc.file}#$id"] = i to p } }
        m
    }

    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val paraY = remember(idx) { mutableStateMapOf<Int, Int>() }

    fun jumpTo(href: String) {
        val hash = href.indexOf('#')
        val filePart = if (hash >= 0) href.substring(0, hash) else href
        val anchor = if (hash >= 0) href.substring(hash + 1) else ""
        val curFile = chapters[idx].file
        val baseDir = curFile.substringBeforeLast('/', "")
        val targetFile = if (filePart.isBlank()) curFile else resolvePath(baseDir, filePart)
        val target = if (anchor.isNotBlank())
            anchorMap["$targetFile#$anchor"] ?: anchorMap.entries.firstOrNull { it.key.endsWith("#$anchor") }?.value
        else fileToChapter[targetFile]?.let { it to 0 }
        if (target != null) {
            // Jump + highlight: set readerCh (flips to the target chapter) and the
            // highlight target. Works for same-chapter and cross-chapter refs.
            vm.readerJumpCh = target.first
            vm.readerJumpPara = target.second
            vm.readerCh = target.first
        }
    }

    // When this chapter is the jump target, wait for the paragraph to lay out then
    // scroll it into view. Re-runs on every new jump (incl. same-chapter refs).
    LaunchedEffect(idx, vm.readerJumpCh, vm.readerJumpPara) {
        if (idx == vm.readerJumpCh && vm.readerJumpPara in ch.paragraphs.indices) {
            val target = vm.readerJumpPara
            var tries = 0
            while (paraY[target] == null && tries < 80) { kotlinx.coroutines.delay(16); tries++ }
            val top = with(density) { 48.dp.roundToPx() }
            paraY[target]?.let { scroll.animateScrollTo((it - top).coerceAtLeast(0)) }
        }
    }

    val baseStyle = TextStyle(
        color = rth.tx, fontFamily = fam, fontSize = (16.5f * vm.fontScale).sp,
        lineHeight = (16.5f * vm.fontScale * vm.lineScale).sp, textAlign = TextAlign.Justify,
    )

    Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
        Box(Modifier.fillMaxWidth()) {
            // Pen/highlighter canvas matches the page size and lives inside the
            // scroll, so annotations stick to the text (move with the page).
            Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp)) {
                Text(chapterLabel(ch.title, idx, lang), color = c.accent, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                Spacer(Modifier.height(16.dp))
                ch.paragraphs.forEachIndexed { pIdx, para ->
                    val spans = ch.links.getOrNull(pIdx).orEmpty()
                    // Highlight the jumped-to paragraph (fades out when cleared).
                    // Drawn BEHIND the text (drawBehind) so it never changes the
                    // text layout / line breaks — only a tint appears.
                    val isTarget = idx == vm.readerJumpCh && pIdx == vm.readerJumpPara
                    val hl = animateFloatAsState(if (isTarget) 1f else 0f, tween(Motion.Med), label = "refHl").value
                    val hlColor = c.accent
                    val posMod = Modifier.fillMaxWidth()
                        .onGloballyPositioned { paraY[pIdx] = it.boundsInParent().top.roundToInt() }
                        .then(if (hl > 0f) Modifier.drawBehind {
                            val pad = 6.dp.toPx()
                            drawRoundRect(
                                color = hlColor.copy(alpha = 0.16f * hl),
                                topLeft = androidx.compose.ui.geometry.Offset(-pad, -pad / 2f),
                                size = androidx.compose.ui.geometry.Size(size.width + pad * 2, size.height + pad),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()),
                            )
                        } else Modifier)
                    if (spans.isEmpty()) {
                        Text(para, style = baseStyle, modifier = posMod)
                    } else {
                        val ann = buildAnnotatedString {
                            append(para)
                            spans.forEach { l ->
                                val s = l.start.coerceIn(0, para.length); val e = l.end.coerceIn(s, para.length)
                                if (e > s) {
                                    addStyle(SpanStyle(color = c.accent, fontWeight = FontWeight.Bold), s, e)
                                    addStringAnnotation("href", l.href, s, e)
                                }
                            }
                        }
                        ClickableText(ann, style = baseStyle, modifier = posMod) { off ->
                            ann.getStringAnnotations("href", off, off).firstOrNull()?.let { jumpTo(it.item) }
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                }
                Spacer(Modifier.height(30.dp))
            }
            DrawingOverlay(vm, sKey, Modifier.matchParentSize())
        }
    }
}

/** Resolve an EPUB-relative href (handles ./ and ../) against a base directory. */
private fun resolvePath(base: String, href: String): String {
    var h = href; var b = base
    while (h.startsWith("../")) { h = h.substring(3); b = b.substringBeforeLast('/', "") }
    h = h.removePrefix("./")
    return if (b.isEmpty()) h else "$b/$h"
}

@Composable
private fun NoteEditSheet(vm: AppViewModel, rth: RTheme, chapters: List<EpubChapter>?, bookKey: String?, lang: Lang) {
    val c = colors(); val ar = lang == Lang.AR
    val target = vm.noteEdit ?: return
    val (key, index) = target
    val ann = vm.rangeAnnsFor(key).getOrNull(index) ?: run { vm.noteEdit = null; return }
    val parts = key.removePrefix("${bookKey ?: "?"}~@~").split("~@~")
    val cch = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val cpara = parts.getOrNull(1)?.toIntOrNull() ?: 0
    val paraText = chapters?.getOrNull(cch)?.paragraphs?.getOrNull(cpara) ?: ""
    val snippet = if (paraText.isNotEmpty()) paraText.substring(ann.start.coerceIn(0, paraText.length), ann.end.coerceIn(0, paraText.length)) else ""
    var text by remember(target) { mutableStateOf(ann.note ?: "") }
    SheetScrim({ vm.noteEdit = null }) {
        SheetSurface(rth.card, onDismiss = { vm.noteEdit = null }) {
            Text(if (ar) "ملاحظة" else "Note", color = rth.tx, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(12.dp))
            Text("“" + snippet.take(180) + (if (snippet.length > 180) "…" else "") + "”", color = c.accent, fontSize = 13.sp, lineHeight = 19.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier.fillMaxWidth().heightIn(min = 88.dp).clip(RoundedCornerShape(12.dp)).background(c.input)
                    .border(1.dp, rth.line, RoundedCornerShape(12.dp)).padding(13.dp),
            ) {
                if (text.isEmpty()) Text(if (ar) "اكتب ملاحظتك هنا…" else "Write your note here…", color = rth.mu, fontSize = 14.sp)
                BasicTextField(
                    value = text, onValueChange = { text = it },
                    textStyle = TextStyle(color = rth.tx, fontSize = 14.sp, lineHeight = 20.sp),
                    cursorBrush = SolidColor(c.accent), modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(c.accent).clickable {
                        vm.setRangeNote(key, index, text); vm.noteEdit = null
                    }.padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(if (ar) "حفظ" else "Save", color = c.onAccent, fontWeight = FontWeight.ExtraBold) }
                Box(
                    Modifier.clip(RoundedCornerShape(12.dp)).border(1.dp, rth.line, RoundedCornerShape(12.dp)).clickable {
                        vm.removeRange(key, index); vm.noteEdit = null
                    }.padding(horizontal = 16.dp, vertical = 13.dp),
                    contentAlignment = Alignment.Center,
                ) { Sym("delete", c.error, 20.dp) }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun DrawingOverlay(vm: AppViewModel, sKey: String, modifier: Modifier = Modifier) {
    val saved = vm.strokesFor(sKey)
    var current by remember(sKey) { mutableStateOf<List<Offset>>(emptyList()) }
    var sz by remember { mutableStateOf(IntSize.Zero) }
    val marker = vm.penMarker
    val capture = if (vm.penMode) Modifier.pointerInput(sKey, vm.penColor, vm.penEraser, vm.penMarker) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            down.consume()
            fun erase(p: Offset) { if (sz.width > 0 && sz.height > 0) vm.eraseAt(sKey, p.x / sz.width, p.y / sz.height) }
            if (vm.penEraser) {
                erase(down.position)
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change == null || !change.pressed) break
                    erase(change.position); change.consume()
                }
            } else {
                var pts = listOf(down.position)
                current = pts
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change == null || !change.pressed) break
                    pts = pts + change.position
                    current = pts
                    change.consume()
                }
                if (pts.size > 1 && sz.width > 0 && sz.height > 0) {
                    vm.addStroke(sKey, DrawStroke(vm.penColor, pts.map { Offset(it.x / sz.width, it.y / sz.height) }, marker))
                }
                current = emptyList()
            }
        }
    } else Modifier
    Canvas(modifier.onSizeChanged { sz = it }.then(capture)) {
        val w = size.width; val h = size.height
        fun pathOf(pts: List<Offset>, denorm: Boolean): Path {
            val p = Path()
            pts.forEachIndexed { i, o ->
                val x = if (denorm) o.x * w else o.x
                val y = if (denorm) o.y * h else o.y
                if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            return p
        }
        // Highlighter = translucent + wide; ink pen = opaque + thin.
        fun strokeStyle(m: Boolean) = Stroke(width = (if (m) 17f else 3.5f).dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        saved.forEach { st ->
            if (st.pts.size > 1) drawPath(pathOf(st.pts, true), Color(st.color).copy(alpha = if (st.marker) 0.32f else 1f), style = strokeStyle(st.marker))
        }
        if (current.size > 1) drawPath(pathOf(current, false), Color(vm.penColor).copy(alpha = if (marker) 0.32f else 1f), style = strokeStyle(marker))
    }
}

@Composable
private fun PenToolbar(vm: AppViewModel, sKey: String, rth: RTheme, modifier: Modifier) {
    val c = colors()
    // Cap the bar to the screen width and let it scroll, so no button is clipped.
    val maxW = (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp - 20).dp
    Row(
        modifier.padding(bottom = 14.dp, start = 10.dp, end = 10.dp)
            .widthIn(max = maxW)
            .clip(RoundedCornerShape(22.dp)).background(rth.card)
            .border(1.dp, rth.line, RoundedCornerShape(22.dp))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 9.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        // Tool selector: ink pen / highlighter / eraser.
        ToolBtn("draw", !vm.penEraser && !vm.penMarker, rth) { vm.penEraser = false; vm.penMarker = false }
        ToolBtn("border_color", !vm.penEraser && vm.penMarker, rth) { vm.penEraser = false; vm.penMarker = true }
        ToolBtn("eraser", vm.penEraser, rth) { vm.penEraser = true }
        Box(Modifier.width(1.dp).height(22.dp).background(rth.line))
        vm.penColors.forEach { col ->
            val sel = !vm.penEraser && vm.penColor == col
            Box(
                Modifier.size(if (sel) 23.dp else 19.dp).clip(CircleShape).background(Color(col))
                    .border(if (sel) 2.dp else 1.dp, if (sel) c.accent else rth.line, CircleShape)
                    .clickable { vm.penColor = col; vm.penEraser = false },
            )
        }
        Box(Modifier.width(1.dp).height(22.dp).background(rth.line))
        Sym("undo", rth.tx, 21.dp, Modifier.clip(CircleShape).clickable { vm.undoStroke(sKey) })
        Sym("delete", c.error, 21.dp, Modifier.clip(CircleShape).clickable { vm.clearStrokes(sKey) })
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(c.accent).clickable { vm.penMode = false; vm.penEraser = false; vm.penMarker = false },
            contentAlignment = Alignment.Center,
        ) { Sym("check", c.onAccent, 19.dp) }
    }
}

@Composable
private fun ToolBtn(icon: String, active: Boolean, rth: RTheme, onClick: () -> Unit) {
    val c = colors()
    Box(
        Modifier.size(32.dp).clip(CircleShape).background(if (active) c.accentSoft else Color.Transparent).clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) { Sym(icon, if (active) c.accent else rth.tx, 21.dp) }
}

@Composable
private fun BookmarksPanel(vm: AppViewModel, rth: RTheme, chapters: List<EpubChapter>?, mode: String, pdfCount: Int, bookKey: String?, lang: Lang) {
    val c = colors(); val ar = lang == Lang.AR
    val items = vm.bookmarksFor(bookKey)
    SheetScrim({ vm.bmPanelOpen = false }) {
        SheetSurface(rth.card, onDismiss = { vm.bmPanelOpen = false }) {
            Text(if (ar) "العلامات المرجعية" else "Bookmarks", color = rth.tx, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(12.dp))
            if (items.isEmpty()) {
                Text(if (ar) "لا توجد علامات بعد — اضغط أيقونة العلامة لإضافة واحدة." else "No bookmarks yet — tap the bookmark icon to add one.", color = rth.mu, fontSize = 13.sp, lineHeight = 19.sp)
                Spacer(Modifier.height(8.dp))
            } else {
                Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                    items.forEach { unit ->
                        val label = if (mode == "pdf") (if (ar) "صفحة ${Num.ar(unit + 1, lang)}" else "Page ${unit + 1}")
                        else chapterLabel(chapters?.getOrNull(unit)?.title ?: "", unit, lang)
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp)).clickable {
                                if (mode == "pdf") vm.readerPage = (unit + 1).coerceIn(1, pdfCount.coerceAtLeast(1)) else vm.readerCh = unit
                                vm.bmPanelOpen = false
                            }.padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Sym("bookmark", c.accent, 18.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(label, color = rth.tx, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Sym("delete", c.error, 18.dp, Modifier.clip(CircleShape).clickable { vm.toggleBookmark(bookKey, unit) })
                        }
                        Box(Modifier.fillMaxWidth().height(1.dp).background(rth.line))
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun AnnotationListSheet(vm: AppViewModel, rth: RTheme, chapters: List<EpubChapter>?, bookKey: String?, lang: Lang) {
    val c = colors()
    val ar = lang == Lang.AR
    val items = vm.bookRangeAnns(bookKey)
    SheetScrim({ vm.annotListOpen = false }) {
        SheetSurface(rth.card, onDismiss = { vm.annotListOpen = false }) {
            Text(if (ar) "الملاحظات والتظليلات" else "Notes & highlights", color = rth.tx, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(12.dp))
            if (items.isEmpty()) {
                Text(if (ar) "لا توجد ملاحظات بعد. حدّد نصاً ثم اضغط تظليل." else "No notes yet. Select text, then tap Highlight.", color = rth.mu, fontSize = 13.sp, lineHeight = 19.sp)
                Spacer(Modifier.height(8.dp))
            } else {
                Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                    items.forEach { row ->
                        val ch = row[0] as Int; val para = row[1] as Int; val index = row[2] as Int; val ann = row[3] as RangeAnn
                        val paraText = chapters?.getOrNull(ch)?.paragraphs?.getOrNull(para) ?: ""
                        val quote = if (paraText.isNotEmpty()) paraText.substring(ann.start.coerceIn(0, paraText.length), ann.end.coerceIn(0, paraText.length)) else ""
                        val title = chapterLabel(chapters?.getOrNull(ch)?.title ?: "", ch, lang)
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp)).clickable { vm.readerCh = ch; vm.annotListOpen = false; vm.noteEdit = vm.rangeKey(bookKey, ch, para) to index }.padding(vertical = 11.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Sym(if (ann.note != null) "sticky_note_2" else "border_color", c.accent, 18.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(title, color = c.accent, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.height(3.dp))
                                Text("“" + quote.take(120) + (if (quote.length > 120) "…" else "") + "”", color = rth.tx, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                if (!ann.note.isNullOrBlank()) {
                                    Spacer(Modifier.height(2.dp))
                                    Text(ann.note, color = rth.mu, fontSize = 12.sp, lineHeight = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            Sym("delete", c.error, 18.dp, Modifier.clip(CircleShape).clickable { vm.removeRange(vm.rangeKey(bookKey, ch, para), index) })
                        }
                        Box(Modifier.fillMaxWidth().height(1.dp).background(rth.line))
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun SampleBody(vm: AppViewModel, rth: RTheme, fam: FontFamily, lang: Lang) {
    val c = colors()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp)) {
        Text(if (lang == Lang.AR) "نص تجريبي" else "Sample text", color = c.accent, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
        Spacer(Modifier.height(16.dp))
        ReaderParas[lang]!!.forEach { para ->
            Text(para, color = rth.tx, fontFamily = fam, fontSize = (16.5f * vm.fontScale).sp, lineHeight = (16.5f * vm.fontScale * vm.lineScale).sp, textAlign = TextAlign.Justify)
            Spacer(Modifier.height(20.dp))
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun PdfBody(vm: AppViewModel, path: String, rth: RTheme, bookKey: String?, page: Int) {
    val sKey = vm.strokeKey(bookKey, page)
    var bitmap by remember(path, page) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(path, page) {
        bitmap = withContext(Dispatchers.IO) { renderPdfPage(path, page - 1) }
    }
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), contentAlignment = Alignment.TopCenter) {
        val bmp = bitmap
        if (bmp != null) {
            Box(Modifier.fillMaxWidth()) {
                Image(bmp.asImageBitmap(), null, Modifier.fillMaxWidth())
                DrawingOverlay(vm, sKey, Modifier.matchParentSize())
            }
        } else Text("…", color = rth.mu)
    }
}

private fun renderPdfPage(path: String, index: Int): Bitmap? = try {
    val fd = ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY)
    val renderer = PdfRenderer(fd)
    val i = index.coerceIn(0, renderer.pageCount - 1)
    val page = renderer.openPage(i)
    val scale = (1080f / page.width).coerceAtMost(3f)
    val w = (page.width * scale).toInt().coerceAtLeast(1)
    val h = (page.height * scale).toInt().coerceAtLeast(1)
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    bmp.eraseColor(android.graphics.Color.WHITE)
    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
    page.close(); renderer.close(); fd.close()
    bmp
} catch (_: Exception) { null }

private fun pdfPageCount(path: String?): Int = try {
    if (path == null) 1 else {
        val fd = ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY)
        val r = PdfRenderer(fd); val n = r.pageCount; r.close(); fd.close(); n.coerceAtLeast(1)
    }
} catch (_: Exception) { 1 }

@Composable
private fun ReaderSettingsSheet(vm: AppViewModel, tx: Color, card: Color, line: Color) {
    val c = colors(); val t = str()
    val serifFam = com.samra.downloader.ui.theme.LocalSerifFont.current
    val sansFam = com.samra.downloader.ui.theme.LocalUiFont.current
    SheetScrim({ vm.readerPanel = false }) {
        SheetSurface(card) {
            Text(t.rSettings, color = tx, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(16.dp))
            Text(t.rSize, color = tx, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).border(1.dp, line, RoundedCornerShape(10.dp)).clickable { vm.setFont(-0.1f) }.padding(vertical = 12.dp), contentAlignment = Alignment.Center) { Text("A−", color = tx, fontWeight = FontWeight.Bold) }
                Box(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).border(1.dp, line, RoundedCornerShape(10.dp)).clickable { vm.setFont(0.1f) }.padding(vertical = 12.dp), contentAlignment = Alignment.Center) { Text("A+", color = tx, fontWeight = FontWeight.Bold) }
            }
            Spacer(Modifier.height(16.dp))
            Text(t.rTheme, color = tx, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("paper", "sepia", "night").forEach { k ->
                    val th = ReaderThemes[k]!!
                    val sel = vm.readerTheme == k
                    Box(Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(10.dp)).background(th.bg).border(2.dp, if (sel) c.accent else Color.Transparent, RoundedCornerShape(10.dp)).clickable { vm.readerTheme = k }, contentAlignment = Alignment.Center) {
                        Text("Aa", color = th.tx, fontFamily = serifFam, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(t.rFontL, color = tx, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("serif" to t.rSerif, "sans" to t.rSans).forEach { (k, lbl) ->
                    val sel = vm.readerFont == k
                    Box(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (sel) c.accent else Color.Transparent).border(1.dp, line, RoundedCornerShape(10.dp)).clickable { vm.readerFont = k }.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                        Text(lbl, color = if (sel) c.onAccent else tx, fontFamily = if (k == "serif") serifFam else sansFam, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(t.rSpacing, color = tx, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(1.6f to "density_small", 1.95f to "density_medium", 2.3f to "density_large").forEach { (v, icon) ->
                    val sel = Math.abs(vm.lineScale - v) < 0.05f
                    Box(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (sel) c.accent else Color.Transparent).border(1.dp, line, RoundedCornerShape(10.dp)).clickable { vm.lineScale = v }.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                        Sym(icon, if (sel) c.onAccent else tx, 20.dp)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun ReaderTocSheet(vm: AppViewModel, rth: RTheme, titles: List<String>) {
    val c = colors(); val t = str()
    SheetScrim({ vm.readerToc = false }) {
        SheetSurface(rth.card) {
            Text(t.rContents, color = rth.tx, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(12.dp))
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                titles.forEachIndexed { i, ti ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { vm.readerCh = i; vm.readerToc = false }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(ti, color = if (i == vm.readerCh) c.accent else rth.tx, fontSize = 14.sp, fontWeight = if (i == vm.readerCh) FontWeight.ExtraBold else FontWeight.Medium, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
