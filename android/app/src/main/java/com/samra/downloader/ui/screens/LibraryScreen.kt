package com.samra.downloader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import com.samra.downloader.ui.bounceClick
import com.samra.downloader.ui.bounceCombined
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.samra.downloader.AppViewModel
import com.samra.downloader.i18n.Lang
import com.samra.downloader.i18n.Num
import com.samra.downloader.model.Book
import com.samra.downloader.model.DemoBooks
import com.samra.downloader.model.LibEntry
import com.samra.downloader.ui.BookCover
import com.samra.downloader.ui.Sym
import com.samra.downloader.ui.colors
import com.samra.downloader.ui.coverBrush
import com.samra.downloader.ui.str

@Composable
fun LibraryScreen(vm: AppViewModel) {
    val c = colors(); val t = str(); val lang = vm.lang
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { vm.refreshLibrary() }

    val term = vm.search.trim().lowercase()
    // Split into words so "harry stone" matches a book that has both, in any order.
    val terms = term.split(" ").filter { it.isNotEmpty() }
    var list = vm.library.mapIndexed { i, e -> i to e }
    if (terms.isNotEmpty()) list = list.filter {
        val b = it.second.book
        val hay = (b.ar.t + " " + b.ar.a + " " + b.en.t + " " + b.en.a + " " + b.fmt).lowercase()
        terms.all { w -> hay.contains(w) }
    }
    if (vm.filter != "all") list = list.filter { it.second.book.fmt == vm.filter }

    Column(Modifier.fillMaxSize().background(c.bg).padding(horizontal = 14.dp)) {
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(t.library, color = c.text, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, softWrap = false, overflow = TextOverflow.Visible)
            }
        }

        Spacer(Modifier.height(14.dp))
        // Inline live search — tap and type, the library filters as you go
        // (no screen navigation, so it never jumps).
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.card)
                .border(1.dp, c.line, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Sym("search", c.text3, 18.dp)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) {
                if (vm.search.isEmpty()) Text(t.searchPh, color = c.text3, fontSize = 14.sp)
                BasicTextField(
                    value = vm.search, onValueChange = { vm.search = it },
                    textStyle = TextStyle(color = c.text, fontSize = 14.sp),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(c.accent), singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (vm.search.isNotEmpty()) Sym(
                "close", c.text3, 18.dp,
                Modifier.clip(androidx.compose.foundation.shape.CircleShape).clickable { vm.search = "" },
            )
        }

        Spacer(Modifier.height(12.dp))
        // Filter chips
        val fmts = listOf("all") + vm.library.map { it.book.fmt }.distinct()
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            fmts.forEach { k ->
                val a = vm.filter == k
                Box(
                    Modifier.clip(RoundedCornerShape(11.dp)).background(if (a) c.accent else c.card)
                        .border(1.dp, if (a) c.accent else c.line, RoundedCornerShape(11.dp))
                        .clickable { vm.filter = k }.padding(horizontal = 15.dp, vertical = 8.dp),
                ) {
                    Text(if (k == "all") t.filterAll else k, color = if (a) c.onAccent else c.text2, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        // View switcher row
        Row(verticalAlignment = Alignment.CenterVertically) {
            val count = if (lang == Lang.AR) "${Num.ar(list.size, lang)} ${if (list.size == 1) "كتاب" else "كتب"}"
            else "${list.size} ${if (list.size == 1) "book" else "books"}"
            Text(count, color = c.text2, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Row(
                Modifier.clip(RoundedCornerShape(11.dp)).background(c.card).border(1.dp, c.line, RoundedCornerShape(11.dp)).padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                listOf("grid" to "grid_view", "list" to "view_list", "shelf" to "view_agenda").forEach { (k, ic) ->
                    val a = vm.libView == k
                    Box(
                        Modifier.size(width = 32.dp, height = 28.dp).clip(RoundedCornerShape(8.dp))
                            .background(if (a) c.accent else Color.Transparent).clickable { vm.libView = k },
                        contentAlignment = Alignment.Center,
                    ) { Sym(ic, if (a) c.onAccent else c.text3, 18.dp) }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        // Content area fills the remaining space: empty state is centered,
        // books scroll within it (so nothing gets clipped above the keyboard).
        // One-time gentle fade-in once the first scan finishes, so the books
        // don't pop in abruptly after the brief blank while scanning.
        val contentAlpha = animateFloatAsState(
            if (vm.libraryLoaded) 1f else 0f,
            animationSpec = androidx.compose.animation.core.tween(com.samra.downloader.ui.Motion.Med),
            label = "libFade",
        ).value
        // Only wrap in an alpha layer during the brief fade-in; once loaded, drop
        // the layer so the list scrolls without compositing to an offscreen buffer.
        Box(Modifier.weight(1f).fillMaxWidth().then(if (contentAlpha < 1f) Modifier.alpha(contentAlpha) else Modifier)) {
            if (list.isEmpty()) {
                // Only show the empty state once the first scan has finished —
                // otherwise it flashes for a frame before the books load.
                if (vm.libraryLoaded) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (term.isNotEmpty()) NoResults(vm.search) else EmptyLibrary(vm)
                    }
                }
            } else {
                // Each view is its own Lazy scroller so items can animate into
                // place when the library re-sorts (download finished) or a book
                // is deleted. Crossfade handles the grid/list/shelf switch.
                androidx.compose.animation.Crossfade(
                    targetState = vm.libView,
                    animationSpec = androidx.compose.animation.core.tween(com.samra.downloader.ui.Motion.Med),
                    label = "libView",
                    modifier = Modifier.fillMaxSize(),
                ) { view ->
                    when (view) {
                        "list" -> ListView(list, lang, vm)
                        "shelf" -> ShelfView(list, lang, vm)
                        // Grid groups multi-part series into one collection tile.
                        else -> GridView(buildLibRows(list), lang, vm)
                    }
                }
            }

            // Floating contextual action bar — selection happens IN-PLACE on the
            // SAME view: the header (title/search/filters/view switcher) stays
            // untouched, and this bar just slides up over the list while selecting.
            androidx.compose.animation.AnimatedVisibility(
                visible = vm.libSelectMode,
                enter = androidx.compose.animation.slideInVertically(
                    androidx.compose.animation.core.tween(com.samra.downloader.ui.Motion.Med),
                ) { it } + androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.slideOutVertically(
                    androidx.compose.animation.core.tween(com.samra.downloader.ui.Motion.Fast),
                ) { it } + androidx.compose.animation.fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
            ) {
                SelectionBar(vm, list.map { entryKey(it.second) }) {
                    shareSelectedBooks(ctx, vm, t.shareTitle) { msg -> vm.toast = msg }
                }
            }
        }
    }
}

private fun entryKey(e: LibEntry): String =
    e.bookDir ?: e.firstAudio ?: e.ebookPath ?: e.coverPath ?: e.book.title(Lang.EN)

// A library grid row is either a single book or a collapsed multi-part series.
private sealed interface LibRow {
    data class Single(val index: Int, val entry: LibEntry) : LibRow
    data class Group(val name: String, val members: List<Pair<Int, LibEntry>>) : LibRow
}

private val arOrdinals = mapOf(
    "الأول" to 1, "الثاني" to 2, "الثالث" to 3, "الرابع" to 4, "الخامس" to 5,
    "السادس" to 6, "السابع" to 7, "الثامن" to 8, "التاسع" to 9, "العاشر" to 10,
)

private fun arDigitsToInt(s: String): Int? {
    val conv = s.map { mapOf('٠' to '0','١' to '1','٢' to '2','٣' to '3','٤' to '4','٥' to '5','٦' to '6','٧' to '7','٨' to '8','٩' to '9')[it] ?: it }.joinToString("")
    return Regex("\\d+").find(conv)?.value?.toIntOrNull()
}

/** Detect a series base name + part number from a title, e.g. «… الجزء الثاني» / "… Part 2". */
private fun detectSeries(title: String): Pair<String, Int>? {
    val t = title.trim()
    Regex("^(.*?)\\s*[:،-]?\\s*الجزء\\s+(.+)$").find(t)?.let { m ->
        val base = m.groupValues[1].trim().trimEnd(':', '،', '-', ' ')
        val part = arOrdinals[m.groupValues[2].trim()] ?: arDigitsToInt(m.groupValues[2])
        if (base.length >= 2 && part != null) return base to part
    }
    Regex("(?i)^(.*?)\\s*[:,-]?\\s*(?:part|vol\\.?|volume|book)\\s+(\\d+)$").find(t)?.let { m ->
        val base = m.groupValues[1].trim().trimEnd(':', ',', '-', ' ')
        val part = m.groupValues[2].toIntOrNull()
        if (base.length >= 2 && part != null) return base to part
    }
    return null
}

/** Collapse multi-part series (≥2 volumes) into one Group; everything else stays Single. */
private fun buildLibRows(list: List<Pair<Int, LibEntry>>): List<LibRow> {
    val order = ArrayList<String>()
    val groups = LinkedHashMap<String, MutableList<Pair<Pair<Int, LibEntry>, Int>>>()
    val singles = HashMap<String, Pair<Int, LibEntry>>()
    for (p in list) {
        val b = p.second.book
        val s = detectSeries(b.title(Lang.AR)) ?: detectSeries(b.title(Lang.EN))
        if (s != null) {
            if (s.first !in groups) { groups[s.first] = ArrayList(); order.add("G:${s.first}") }
            groups[s.first]!!.add(p to s.second)
        } else {
            val id = "S:${p.first}"; singles[id] = p; order.add(id)
        }
    }
    val rows = ArrayList<LibRow>()
    for (key in order) {
        if (key.startsWith("G:")) {
            val m = groups[key.substring(2)]!!
            if (m.size >= 2) rows.add(LibRow.Group(key.substring(2), m.sortedBy { it.second }.map { it.first }))
            else rows.add(LibRow.Single(m[0].first.first, m[0].first.second))
        } else singles[key]?.let { rows.add(LibRow.Single(it.first, it.second)) }
    }
    return rows
}

@Composable
private fun GridView(rows: List<LibRow>, lang: Lang, vm: AppViewModel) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        gridItems(rows, key = { row -> when (row) { is LibRow.Single -> "s:${entryKey(row.entry)}"; is LibRow.Group -> "g:${row.name}" } }) { row ->
            val mod = Modifier.animateItem(
                fadeInSpec = androidx.compose.animation.core.tween(com.samra.downloader.ui.Motion.Med),
                fadeOutSpec = androidx.compose.animation.core.tween(com.samra.downloader.ui.Motion.Fast),
            )
            when (row) {
                is LibRow.Single -> GridTile(row.index, row.entry, lang, vm, mod)
                is LibRow.Group -> GridGroupTile(row, lang, vm, mod)
            }
        }
    }
}

@Composable
private fun GridTile(i: Int, e: LibEntry, lang: Lang, vm: AppViewModel, mod: Modifier) {
    val c = colors(); val b = e.book; val sel = vm.isBookSelected(e)
    val startSel = rememberStartSelect(vm)
    Column(mod.bounceCombined(
        scaleDown = 0.96f,
        onClick = { if (vm.libSelectMode) vm.toggleBookSelected(e) else vm.openDetail(i) },
        onLongClick = { startSel(e) },
    )) {
        SelectableCover(e, lang, vm, sel, Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Text(b.title(lang), color = c.text, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(b.author(lang), color = c.text2, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${Num.dur(b.h, b.m, lang)} · ${Num.size(b.mb + (b.ep?.mb ?: 0.0), lang)}", color = c.text3, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun GridGroupTile(group: LibRow.Group, lang: Lang, vm: AppViewModel, mod: Modifier) {
    val c = colors()
    val first = group.members.first().second
    val n = group.members.size
    val allSel = group.members.all { vm.isBookSelected(it.second) }
    val startSel = rememberStartSelect(vm)
    Column(mod.bounceCombined(
        scaleDown = 0.96f,
        onClick = {
            if (vm.libSelectMode) {
                val target = !allSel
                group.members.forEach { if (vm.isBookSelected(it.second) != target) vm.toggleBookSelected(it.second) }
            } else vm.openSeries(group.name, group.members.map { it.first })
        },
        onLongClick = { group.members.forEach { startSel(it.second) } },
    )) {
        Box(Modifier.fillMaxWidth()) {
            BookCover(first, lang, Modifier.fillMaxWidth())
            // collection badge: stack icon + part count
            Box(
                Modifier.align(Alignment.TopEnd).padding(8.dp).clip(RoundedCornerShape(9.dp)).background(c.accent)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Sym("auto_stories", c.onAccent, 13.dp)
                    Spacer(Modifier.width(4.dp))
                    Text(Num.ar(n, lang), color = c.onAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (vm.libSelectMode) SelectRing(allSel, Modifier.align(Alignment.TopStart).padding(7.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(group.name, color = c.text, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            if (lang == Lang.AR) "${Num.ar(n, lang)} أجزاء" else "$n parts",
            color = c.text2, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ListView(list: List<Pair<Int, LibEntry>>, lang: Lang, vm: AppViewModel) {
    val c = colors()
    val startSel = rememberStartSelect(vm)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        lazyItems(list, key = { entryKey(it.second) }) { (i, e) ->
            val b = e.book
            val sel = vm.isBookSelected(e)
            Row(
                Modifier.animateItem(
                    fadeInSpec = androidx.compose.animation.core.tween(com.samra.downloader.ui.Motion.Med),
                    fadeOutSpec = androidx.compose.animation.core.tween(com.samra.downloader.ui.Motion.Fast),
                ).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (sel) c.accentSoft else c.card)
                    .border(1.dp, if (sel) c.accent else c.line, RoundedCornerShape(14.dp))
                    .bounceCombined(
                        scaleDown = 0.98f,
                        onClick = { if (vm.libSelectMode) vm.toggleBookSelected(e) else vm.openDetail(i) },
                        onLongClick = { startSel(e) },
                    ).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                com.samra.downloader.ui.ListCover(e, lang, Modifier.size(width = 52.dp, height = 70.dp), finished = vm.isFinished(e), progress = vm.progressOf(e))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(b.title(lang), color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(b.author(lang), color = c.text2, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Chip(b.fmt, c.accentSoft, c.accent)
                        b.ep?.let { Chip(it.fmt, c.card2, c.text2) }
                        Text(Num.dur(b.h, b.m, lang), color = c.text3, fontSize = 11.sp)
                    }
                }
                if (vm.libSelectMode) SelectRing(sel) else Sym("chevron_right", c.text3, 20.dp)
            }
        }
    }
}

// Storytel-style BOOKSHELF: covers stand upright on wooden ledges, 3 per shelf.
// Reuses the generated BookCover so each book keeps your cover design.
private const val SHELF_COLS = 3

@Composable
private fun ShelfView(list: List<Pair<Int, LibEntry>>, lang: Lang, vm: AppViewModel) {
    val startSel = rememberStartSelect(vm)
    val rows = list.chunked(SHELF_COLS)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 4.dp, bottom = 28.dp),
    ) {
        lazyItems(rows, key = { row -> row.joinToString("|") { entryKey(it.second) } }) { row ->
            Column(
                Modifier.animateItem(
                    fadeInSpec = androidx.compose.animation.core.tween(com.samra.downloader.ui.Motion.Med),
                    fadeOutSpec = androidx.compose.animation.core.tween(com.samra.downloader.ui.Motion.Fast),
                ),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    row.forEach { (i, e) ->
                        val sel = vm.isBookSelected(e)
                        Box(Modifier.weight(1f), contentAlignment = Alignment.BottomCenter) {
                            Box(
                                Modifier.fillMaxWidth(0.84f)
                                    .shadow(16.dp, RoundedCornerShape(11.dp), clip = false)
                                    .bounceCombined(
                                        scaleDown = 0.94f,
                                        onClick = { if (vm.libSelectMode) vm.toggleBookSelected(e) else vm.openDetail(i) },
                                        onLongClick = { startSel(e) },
                                    ),
                            ) {
                                SelectableCover(e, lang, vm, sel, Modifier.fillMaxWidth())
                            }
                        }
                    }
                    // keep a partial last shelf left-aligned
                    repeat(SHELF_COLS - row.size) { Spacer(Modifier.weight(1f)) }
                }
                ShelfLedge()
                Spacer(Modifier.height(22.dp))
            }
        }
    }
}

/** The wooden ledge the books stand on — a thin 3D shelf, theme-aware. */
@Composable
private fun ShelfLedge() {
    val c = colors()
    Column(Modifier.fillMaxWidth().padding(top = 2.dp)) {
        // catch-light on the top surface
        Box(Modifier.fillMaxWidth().height(2.dp).clip(RoundedCornerShape(2.dp)).background(c.text3.copy(alpha = 0.20f)))
        // ledge body, fading from a lit top edge to a darker front
        Box(
            Modifier.fillMaxWidth().height(9.dp)
                .clip(RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp))
                .background(Brush.verticalGradient(listOf(c.card2, c.line))),
        )
    }
}

@Composable
fun Chip(label: String, bg: Color, fg: Color) {
    Box(Modifier.clip(RoundedCornerShape(7.dp)).background(bg).padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(label, color = fg, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
    }
}

/** Long-press handler that fires a haptic tick (like Photos/Gmail) then selects. */
@Composable
private fun rememberStartSelect(vm: AppViewModel): (LibEntry) -> Unit {
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    return { e ->
        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
        vm.startLibSelect(e)
    }
}

/**
 * A book cover that animates into a "selected" state like Google Photos:
 * the cover scales down to reveal an accent frame behind it, with a check ring.
 */
@Composable
private fun SelectableCover(e: LibEntry, lang: Lang, vm: AppViewModel, sel: Boolean, modifier: Modifier = Modifier) {
    val c = colors()
    val scale = androidx.compose.animation.core.animateFloatAsState(
        if (sel) 0.84f else 1f, com.samra.downloader.ui.Motion.snappy(), label = "selScale",
    ).value
    Box(modifier.clip(RoundedCornerShape(13.dp)).background(if (sel) c.accent else Color.Transparent)) {
        BookCover(e, lang, Modifier.fillMaxWidth().scale(scale), finished = vm.isFinished(e), progress = vm.progressOf(e))
        if (vm.libSelectMode) SelectRing(sel, Modifier.align(Alignment.TopEnd).padding(7.dp))
    }
}

/** Round selection indicator drawn on a book (filled check when selected). */
@Composable
private fun SelectRing(selected: Boolean, modifier: Modifier = Modifier) {
    val c = colors()
    Box(
        modifier.size(24.dp).clip(androidx.compose.foundation.shape.CircleShape)
            .background(if (selected) c.accent else Color.Black.copy(alpha = 0.40f))
            .border(2.dp, Color.White, androidx.compose.foundation.shape.CircleShape),
        contentAlignment = Alignment.Center,
    ) { if (selected) Sym("check", Color.White, 15.dp) }
}

/** Contextual top bar shown while selecting: count + select-all / share / delete. */
@Composable
private fun SelectionBar(vm: AppViewModel, allKeys: List<String>, onShare: () -> Unit) {
    val c = colors(); val t = str(); val lang = vm.lang
    val n = vm.selectedBooks.size
    val confirmDelete = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    Row(
        Modifier.fillMaxWidth()
            .shadow(14.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp)).background(c.surface)
            .border(1.dp, c.line, RoundedCornerShape(18.dp))
            .padding(start = 6.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(38.dp).clip(androidx.compose.foundation.shape.CircleShape)
                .bounceClick(scaleDown = 0.85f) { vm.clearLibSelection() },
            contentAlignment = Alignment.Center,
        ) { Sym("close", c.accent, 23.dp) }
        Spacer(Modifier.width(4.dp))
        Text(
            if (lang == Lang.AR) "${Num.ar(n, lang)} محدد" else "$n selected",
            color = c.text, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f),
        )
        SelBarAction("select_all", c.text2) { vm.selectAllBooks(allKeys) }
        SelBarAction("share", if (n > 0) c.accent else c.text3) { if (n > 0) onShare() }
        SelBarAction("delete", if (n > 0) c.error else c.text3) { if (n > 0) confirmDelete.value = true }
    }

    if (confirmDelete.value) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDelete.value = false },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { confirmDelete.value = false; vm.deleteSelectedBooks() }) {
                    Text(t.deleteShort, color = c.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmDelete.value = false }) {
                    Text(if (lang == Lang.AR) "إلغاء" else "Cancel", color = c.text2)
                }
            },
            title = { Text(if (lang == Lang.AR) "حذف $n عنصر؟" else "Delete $n item(s)?", color = c.text, fontWeight = FontWeight.Bold) },
            text = { Text(if (lang == Lang.AR) "سيتم حذف الملفات نهائياً من الجهاز." else "The files will be permanently removed from the device.", color = c.text2) },
            containerColor = c.card,
        )
    }
}

@Composable
private fun SelBarAction(icon: String, tint: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(androidx.compose.foundation.shape.CircleShape).bounceClick(scaleDown = 0.85f) { onClick() },
        contentAlignment = Alignment.Center,
    ) { Sym(icon, tint, 22.dp) }
}

/** Bulk-share the selected books via the native Android share sheet (one file each). */
private fun shareSelectedBooks(ctx: android.content.Context, vm: AppViewModel, title: String, onError: (String) -> Unit) {
    val uris = ArrayList<android.net.Uri>()
    vm.selectedEntries().forEach { e ->
        val p = e.firstAudio ?: e.ebookPath
        if (p != null) {
            val f = java.io.File(p)
            if (f.exists()) runCatching {
                uris.add(androidx.core.content.FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f))
            }
        }
    }
    if (uris.isEmpty()) { onError(if (vm.lang == Lang.AR) "لا شيء للمشاركة" else "Nothing to share"); return }
    vm.clearLibSelection()
    try {
        val send = android.content.Intent(android.content.Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, uris)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(android.content.Intent.createChooser(send, title).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        onError(if (vm.lang == Lang.AR) "تعذّرت المشاركة" else (e.message ?: "Share failed"))
    }
}

@Composable
private fun NoResults(query: String) {
    val c = colors(); val t = str()
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Sym("search_off", c.text3, 44.dp)
        Spacer(Modifier.height(12.dp))
        Text("${t.noResults} «$query»", color = c.text3, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun EmptyLibrary(vm: AppViewModel) {
    val c = colors(); val t = str()
    val ctx = LocalContext.current
    val path = com.samra.downloader.Storage.outputDir(ctx).absolutePath
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(100.dp).clip(RoundedCornerShape(30.dp)).background(c.accentSoft), contentAlignment = Alignment.Center) {
            Sym("cloud_download", c.accent, 46.dp)
        }
        Spacer(Modifier.height(16.dp))
        Text(t.emptyTitle, color = c.text, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(6.dp))
        Text(t.emptySub, color = c.text2, fontSize = 14.sp, modifier = Modifier.widthIn(max = 250.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Button(onClick = { vm.go("add") }, colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent), shape = RoundedCornerShape(12.dp)) {
            Sym("add", c.onAccent, 18.dp); Spacer(Modifier.width(6.dp)); Text(t.emptyCta, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(18.dp))
        Text(if (vm.lang == Lang.AR) "تُحفظ التنزيلات في:" else "Downloads are saved to:", color = c.text3, fontSize = 11.sp)
        Text(path, color = c.text3, fontSize = 10.sp, fontFamily = FontFamily.Monospace, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(horizontal = 16.dp))
    }
}
