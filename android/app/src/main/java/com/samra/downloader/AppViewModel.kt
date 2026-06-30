package com.samra.downloader

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.samra.downloader.i18n.Lang
import com.samra.downloader.model.ChapterSecs
import com.samra.downloader.model.LibEntry
import com.samra.downloader.model.Speeds
import com.samra.downloader.model.scanLibrary
import java.io.File

/** One freehand stroke; points are normalized 0..1 of the drawing area.
 *  [marker] = highlighter (translucent + wide); otherwise an ink pen line. */
data class DrawStroke(val color: Int, val pts: List<androidx.compose.ui.geometry.Offset>, val marker: Boolean = false)

/** A character-range highlight (optionally with a note) inside one paragraph. */
data class RangeAnn(val start: Int, val end: Int, val note: String?, val color: Int = 0xFFF6C915.toInt())

/** Top-level UI state — mirrors the prototype's Component state. */
class AppViewModel(app: Application) : AndroidViewModel(app) {

    // real downloaded library
    val library = mutableStateListOf<LibEntry>()
    // False until the first scan finishes, so the Library screen can avoid
    // flashing the empty state before the books have loaded.
    var libraryLoaded by mutableStateOf(false)

    private val prefs = app.getSharedPreferences("samra", android.content.Context.MODE_PRIVATE)

    private fun deleteEntryFiles(entry: LibEntry) {
        try {
            entry.audioFiles.forEach { File(it.path).delete() }
            entry.ebookPath?.let { File(it).delete() }
            entry.coverPath?.let { File(it).delete() }
            entry.sidecarPath?.let { File(it).delete() }
            entry.bookDir?.let { val d = File(it); if (d.exists()) d.deleteRecursively() }
        } catch (_: Exception) {}
    }

    fun deleteBook(entry: LibEntry) {
        kotlin.concurrent.thread { deleteEntryFiles(entry); refreshLibrary() }
    }

    fun refreshLibrary() {
        val dirs = Storage.scanDirs(getApplication())
        val lang = this.lang
        kotlin.concurrent.thread {
            val scanned = dirs.flatMap { scanLibrary(it, lang) }
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                library.clear(); library.addAll(scanned)
                libraryLoaded = true
                // Keep the open player/mini-player pointed at the same book even
                // though the list may have been re-sorted by this refresh.
                val key = com.samra.downloader.playback.PlaybackController.bookKey
                if (key != null) {
                    val i = scanned.indexOfFirst { it.firstAudio == key || it.bookDir == key || it.ebookPath == key }
                    if (i >= 0) {
                        com.samra.downloader.playback.PlaybackController.rebind(i)
                        if (playing != null) playing = i
                    }
                }
            }
        }
    }

    private fun loadPrefs() {
        lang = if (prefs.getString("lang", "ar") == "en") Lang.EN else Lang.AR
        dark = prefs.getBoolean("dark", true)
        format = prefs.getString("format", "mp3") ?: "mp3"
        // One-time: conversion formats need ffmpeg, which isn't reliable on-device
        // yet — default those installs back to mp3 so downloads always succeed.
        if (!prefs.getBoolean("fmt_migrated_v2", false)) {
            if (format in listOf("m4b", "m4a", "opus")) format = "mp3"
            prefs.edit().putBoolean("fmt_migrated_v2", true).putString("format", format).apply()
        }
        combine = prefs.getBoolean("combine", true)
        skip = prefs.getBoolean("skip", true)
        wifi = prefs.getBoolean("wifi", true)
        val conn = prefs.getString("connected", "") ?: ""
        connected.clear(); connected.addAll(conn.split(",").filter { it.isNotBlank() })
        val fin = prefs.getString("finished", "") ?: ""
        finished.clear(); finished.addAll(fin.split("\n").filter { it.isNotBlank() })
        loadProgress()
        val hl = prefs.getString("highlights", "") ?: ""
        highlights.clear(); highlights.addAll(hl.split("\n").filter { it.isNotBlank() })
        notes.clear()
        try {
            val o = org.json.JSONObject(prefs.getString("notes", "{}") ?: "{}")
            o.keys().forEach { notes[it] = o.getString(it) }
        } catch (_: Exception) {}
        loadStrokes()
        loadRangeAnns()
        loadBookmarks()
        loadAudioPos()
        loadAudioBookmarks()
        loadReaderPos()
        // restore saved account emails for connected sources
        for (id in connected) {
            com.samra.downloader.auth.CredentialStore.load(getApplication(), id)?.let {
                if (it.username.isNotBlank()) accounts[id] = it.username
            }
        }
    }

    fun savePrefs() {
        prefs.edit()
            .putString("lang", if (lang == Lang.EN) "en" else "ar")
            .putBoolean("dark", dark)
            .putString("format", format)
            .putBoolean("combine", combine)
            .putBoolean("skip", skip)
            .putBoolean("wifi", wifi)
            .putString("connected", connected.joinToString(","))
            .putBoolean("onboarded", screen != "onboarding")
            .apply()
    }
    // shell / theme
    var lang by mutableStateOf(Lang.AR)
    var dark by mutableStateOf(true)
    var screen by mutableStateOf("onboarding")

    // library
    var filter by mutableStateOf("all")
    var search by mutableStateOf("")
    var libView by mutableStateOf("grid")
    val recentSearches = mutableStateListOf<String>()

    fun addRecent(term: String) {
        val tt = term.trim()
        if (tt.isBlank()) return
        recentSearches.remove(tt)
        recentSearches.add(0, tt)
        while (recentSearches.size > 6) recentSearches.removeAt(recentSearches.size - 1)
    }

    // sheets
    var sheetOpen by mutableStateOf(false)
    var sheetMode by mutableStateOf("login") // login|cookies|manage|detail|share
    var sheetSrc by mutableStateOf("storytel")
    var detailBook by mutableStateOf(0)
    var shareAsset by mutableStateOf("audio")

    // auth
    var showPass by mutableStateOf(false)
    var remember by mutableStateOf(false)
    var cookiesPicked by mutableStateOf(false)
    var email by mutableStateOf("")
    var password by mutableStateOf("")
    var cookiePath by mutableStateOf<String?>(null)
    val connected = mutableStateListOf<String>()
    val accounts = androidx.compose.runtime.mutableStateMapOf<String, String>()

    // settings
    var format by mutableStateOf("mp3")
    var combine by mutableStateOf(true)
    var skip by mutableStateOf(true)
    var wifi by mutableStateOf(true)

    // onboarding
    var onbStep by mutableStateOf(0)

    // add / console
    var paste by mutableStateOf("")
    var consoleOpen by mutableStateOf(false)

    // player
    var playing by mutableStateOf<Int?>(null)
    var isPlaying by mutableStateOf(true)
    var playSec by mutableStateOf(0)
    var speedIdx by mutableStateOf(2)
    var sleepMinutes by mutableStateOf<Int?>(null)
    var sleepIsChapter by mutableStateOf(false)
    var sleepLeft by mutableStateOf(0)
    var sleepSheet by mutableStateOf(false)
    val pBookmarks = mutableStateListOf<Int>()
    var bmSheet by mutableStateOf(false)
    var chListOpen by mutableStateOf(false)

    // reader
    var reading by mutableStateOf<Int?>(null)
    var readerPage by mutableStateOf(12)
    var fontScale by mutableStateOf(1f)
    var readerTheme by mutableStateOf("sepia")
    var readerFont by mutableStateOf("serif")
    var lineScale by mutableStateOf(1.95f)
    var readerPanel by mutableStateOf(false)
    var readerToc by mutableStateOf(false)
    var bookmarked by mutableStateOf(false)
    var readerCh by mutableStateOf(1)
    // Pending jump target from tapping a reference/footnote link: scroll to and
    // briefly highlight paragraph `readerJumpPara` in chapter `readerJumpCh`.
    // Cleared (highlight removed) when the user flips the page or taps it.
    var readerJumpCh by mutableStateOf(-1)
    var readerJumpPara by mutableStateOf(-1)

    // reader annotations — paragraph highlights + notes, persisted per book.
    // key = "<bookKey>~@~<chapter>~@~<paragraph>"
    val highlights = mutableStateListOf<String>()
    val notes = androidx.compose.runtime.mutableStateMapOf<String, String>()
    var annotTarget by mutableStateOf<Pair<Int, Int>?>(null)   // (chapter, paragraph) being edited
    var annotListOpen by mutableStateOf(false)
    private fun annKey(bookKey: String?, ch: Int, para: Int) = "${bookKey ?: "?"}~@~$ch~@~$para"
    fun isHighlighted(bookKey: String?, ch: Int, para: Int) = annKey(bookKey, ch, para) in highlights
    fun noteOf(bookKey: String?, ch: Int, para: Int): String? = notes[annKey(bookKey, ch, para)]
    fun hasAnnotation(bookKey: String?, ch: Int, para: Int) =
        isHighlighted(bookKey, ch, para) || !noteOf(bookKey, ch, para).isNullOrBlank()
    fun toggleHighlight(bookKey: String?, ch: Int, para: Int) {
        val k = annKey(bookKey, ch, para)
        if (k in highlights) highlights.remove(k) else highlights.add(k)
        saveAnnotations()
    }
    fun setNote(bookKey: String?, ch: Int, para: Int, text: String) {
        val k = annKey(bookKey, ch, para)
        if (text.isBlank()) notes.remove(k) else notes[k] = text.trim()
        saveAnnotations()
    }
    fun clearAnnotation(bookKey: String?, ch: Int, para: Int) {
        val k = annKey(bookKey, ch, para)
        highlights.remove(k); notes.remove(k); saveAnnotations()
    }
    /** Annotations for one book, as (chapter, paragraph, note) sorted for the review list. */
    fun annotationsFor(bookKey: String?): List<Triple<Int, Int, String?>> {
        val prefix = "${bookKey ?: "?"}~@~"
        val keys = (highlights.filter { it.startsWith(prefix) } + notes.keys.filter { it.startsWith(prefix) }).distinct()
        return keys.mapNotNull { k ->
            val parts = k.removePrefix(prefix).split("~@~")
            val ch = parts.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
            val para = parts.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
            Triple(ch, para, notes[k])
        }.sortedWith(compareBy({ it.first }, { it.second }))
    }
    private fun saveAnnotations() {
        val obj = org.json.JSONObject()
        notes.forEach { (k, v) -> obj.put(k, v) }
        prefs.edit()
            .putString("highlights", highlights.joinToString("\n"))
            .putString("notes", obj.toString())
            .apply()
    }

    // --- selection highlights + notes (real character ranges, per book) ---
    val rangeAnns = androidx.compose.runtime.mutableStateMapOf<String, List<RangeAnn>>()
    // Highlighter colours; the last picked one is reused for the Note action.
    val hlColors = listOf(0xFFF6C915.toInt(), 0xFF5BD6A0.toInt(), 0xFF6FB7FF.toInt(), 0xFFFF8FB1.toInt(), 0xFFFFB066.toInt())
    var hlColor by mutableStateOf(0xFFF6C915.toInt())
    fun rangeKey(bookKey: String?, ch: Int, para: Int) = "${bookKey ?: "?"}~@~$ch~@~$para"
    fun rangeAnnsFor(key: String): List<RangeAnn> = rangeAnns[key] ?: emptyList()
    /** Add a highlight, merging it into any overlapping/adjacent same-colour
     *  note-less highlight. Returns the resulting index. */
    fun addHighlight(key: String, start: Int, end: Int, color: Int = hlColor, note: String? = null): Int {
        if (end <= start) return -1
        hlColor = color
        var s = start; var e = end
        val keep = ArrayList<RangeAnn>()
        for (a in rangeAnnsFor(key)) {
            if (note == null && a.note == null && a.color == color && a.start <= e && a.end >= s) {
                s = minOf(s, a.start); e = maxOf(e, a.end)   // absorb overlapping run
            } else keep.add(a)
        }
        keep.add(RangeAnn(s, e, note, color))
        rangeAnns[key] = keep; saveRangeAnns()
        return keep.lastIndex
    }
    fun setRangeNote(key: String, index: Int, note: String) {
        val l = rangeAnnsFor(key).toMutableList()
        if (index !in l.indices) return
        l[index] = l[index].copy(note = note.ifBlank { null })
        rangeAnns[key] = l; saveRangeAnns()
    }
    fun removeRange(key: String, index: Int) {
        val l = rangeAnnsFor(key).toMutableList()
        if (index !in l.indices) return
        l.removeAt(index)
        if (l.isEmpty()) rangeAnns.remove(key) else rangeAnns[key] = l
        saveRangeAnns()
    }
    /** All highlights/notes for a book as (chapter, paragraph, index, ann). */
    fun bookRangeAnns(bookKey: String?): List<List<Any?>> {
        val prefix = "${bookKey ?: "?"}~@~"
        val out = ArrayList<List<Any?>>()
        rangeAnns.forEach { (k, list) ->
            if (k.startsWith(prefix)) {
                val parts = k.removePrefix(prefix).split("~@~")
                val ch = parts.getOrNull(0)?.toIntOrNull(); val para = parts.getOrNull(1)?.toIntOrNull()
                if (ch != null && para != null) list.forEachIndexed { i, a -> out.add(listOf(ch, para, i, a)) }
            }
        }
        return out.sortedWith(compareBy({ it[0] as Int }, { it[1] as Int }, { (it[3] as RangeAnn).start }))
    }
    private fun saveRangeAnns() {
        val obj = org.json.JSONObject()
        rangeAnns.forEach { (k, list) ->
            val arr = org.json.JSONArray()
            list.forEach { a ->
                val o = org.json.JSONObject().put("s", a.start).put("e", a.end).put("col", a.color)
                if (a.note != null) o.put("n", a.note)
                arr.put(o)
            }
            obj.put(k, arr)
        }
        prefs.edit().putString("rangeAnns", obj.toString()).apply()
    }
    private fun loadRangeAnns() {
        rangeAnns.clear()
        try {
            val obj = org.json.JSONObject(prefs.getString("rangeAnns", "{}") ?: "{}")
            obj.keys().forEach { k ->
                val arr = obj.getJSONArray(k); val list = ArrayList<RangeAnn>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(RangeAnn(o.getInt("s"), o.getInt("e"), if (o.has("n")) o.getString("n") else null, if (o.has("col")) o.getInt("col") else 0xFFF6C915.toInt()))
                }
                rangeAnns[k] = list
            }
        } catch (_: Exception) {}
    }

    // transient text selection (not persisted)
    var selPara by mutableStateOf<Pair<Int, Int>?>(null)   // (chapter, paragraph)
    var selStart by mutableStateOf(0)
    var selEnd by mutableStateOf(0)
    var selText by mutableStateOf("")
    val selActive get() = selPara != null && selEnd > selStart
    fun clearSelection() { selPara = null; selStart = 0; selEnd = 0; selText = "" }
    var noteEdit by mutableStateOf<Pair<String, Int>?>(null)   // (rangeKey, index)

    // reader pen — freehand strokes drawn over a chapter/page, persisted per book.
    // Tool = eraser (penEraser) → highlighter (penMarker) → ink pen (neither).
    var penMode by mutableStateOf(false)
    var penEraser by mutableStateOf(false)
    var penMarker by mutableStateOf(false)
    var penColor by mutableStateOf(0xFFE5484D.toInt())
    val penColors = listOf(0xFFE5484D.toInt(), 0xFF2E9E5B.toInt(), 0xFF2E6BE6.toInt(), 0xFFE0A82E.toInt(), 0xFF1A1A1A.toInt())
    val strokes = androidx.compose.runtime.mutableStateMapOf<String, List<DrawStroke>>()
    fun strokeKey(bookKey: String?, unit: Int) = "${bookKey ?: "?"}~@~$unit"
    fun strokesFor(key: String): List<DrawStroke> = strokes[key] ?: emptyList()
    fun addStroke(key: String, stroke: DrawStroke) { strokes[key] = strokesFor(key) + stroke; saveStrokes() }
    fun undoStroke(key: String) { val l = strokesFor(key); if (l.isNotEmpty()) { strokes[key] = l.dropLast(1); saveStrokes() } }
    fun clearStrokes(key: String) { strokes.remove(key); saveStrokes() }
    /** Precise eraser: drop only the points within [r] of the touch and split
     *  each stroke into the surviving runs (so you can rub out part of a line). */
    fun eraseAt(key: String, x: Float, y: Float, r: Float = 0.03f) {
        val l = strokesFor(key)
        if (l.isEmpty()) return
        val out = ArrayList<DrawStroke>()
        var changed = false
        for (st in l) {
            var touched = false
            val runs = ArrayList<List<androidx.compose.ui.geometry.Offset>>()
            var run = ArrayList<androidx.compose.ui.geometry.Offset>()
            for (p in st.pts) {
                if (kotlin.math.hypot((p.x - x).toDouble(), (p.y - y).toDouble()) < r) {
                    touched = true
                    if (run.size >= 2) runs.add(run.toList())
                    run = ArrayList()
                } else run.add(p)
            }
            if (run.size >= 2) runs.add(run.toList())
            if (!touched) out.add(st) else { changed = true; runs.forEach { out.add(DrawStroke(st.color, it, st.marker)) } }
        }
        if (changed) { if (out.isEmpty()) strokes.remove(key) else strokes[key] = out; saveStrokes() }
    }
    private fun saveStrokes() {
        val obj = org.json.JSONObject()
        strokes.forEach { (k, list) ->
            val arr = org.json.JSONArray()
            list.forEach { st ->
                val flat = org.json.JSONArray()
                st.pts.forEach { flat.put(it.x.toDouble()); flat.put(it.y.toDouble()) }
                arr.put(org.json.JSONObject().put("c", st.color).put("p", flat).put("m", st.marker))
            }
            obj.put(k, arr)
        }
        prefs.edit().putString("strokes", obj.toString()).apply()
    }
    private fun loadStrokes() {
        strokes.clear()
        try {
            val obj = org.json.JSONObject(prefs.getString("strokes", "{}") ?: "{}")
            obj.keys().forEach { k ->
                val arr = obj.getJSONArray(k)
                val list = ArrayList<DrawStroke>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val p = o.getJSONArray("p")
                    val pts = ArrayList<androidx.compose.ui.geometry.Offset>()
                    var j = 0
                    while (j + 1 < p.length()) { pts.add(androidx.compose.ui.geometry.Offset(p.getDouble(j).toFloat(), p.getDouble(j + 1).toFloat())); j += 2 }
                    list.add(DrawStroke(o.getInt("c"), pts, o.optBoolean("m", false)))
                }
                strokes[k] = list
            }
        } catch (_: Exception) {}
    }

    // reader bookmarks — per book, a set of positions (chapter index for EPUB,
    // page number for PDF). Persisted as JSON.
    val bookmarks = androidx.compose.runtime.mutableStateMapOf<String, List<Int>>()
    var bmPanelOpen by mutableStateOf(false)
    fun bookmarksFor(bookKey: String?): List<Int> = bookmarks[bookKey ?: "?"] ?: emptyList()
    fun isBookmarked(bookKey: String?, unit: Int) = unit in bookmarksFor(bookKey)
    fun toggleBookmark(bookKey: String?, unit: Int) {
        val kk = bookKey ?: "?"
        val cur = bookmarksFor(bookKey)
        bookmarks[kk] = if (unit in cur) cur - unit else (cur + unit).sorted()
        saveBookmarks()
    }
    private fun saveBookmarks() {
        val obj = org.json.JSONObject()
        bookmarks.forEach { (k, list) -> obj.put(k, org.json.JSONArray().also { a -> list.forEach { a.put(it) } }) }
        prefs.edit().putString("bookmarks", obj.toString()).apply()
    }
    private fun loadBookmarks() {
        bookmarks.clear()
        try {
            val obj = org.json.JSONObject(prefs.getString("bookmarks", "{}") ?: "{}")
            obj.keys().forEach { k ->
                val arr = obj.getJSONArray(k); val list = ArrayList<Int>()
                for (i in 0 until arr.length()) list.add(arr.getInt(i))
                bookmarks[k] = list
            }
        } catch (_: Exception) {}
    }

    // finished / "read" books — keyed by a stable path (firstAudio/bookDir/ebookPath)
    val finished = mutableStateListOf<String>()
    fun keyOf(e: LibEntry): String? = e.firstAudio ?: e.bookDir ?: e.ebookPath
    fun isFinished(e: LibEntry): Boolean = keyOf(e)?.let { it in finished } == true
    fun markFinished(key: String?) {
        if (key == null || key in finished) return
        finished.add(key)
        prefs.edit().putString("finished", finished.joinToString("\n")).apply()
    }
    fun toggleFinished(e: LibEntry) {
        val key = keyOf(e) ?: return
        if (key in finished) finished.remove(key) else finished.add(key)
        prefs.edit().putString("finished", finished.joinToString("\n")).apply()
    }

    // per-book listening/reading progress fraction (0..1) — shown as a bar on covers.
    val progress = androidx.compose.runtime.mutableStateMapOf<String, Float>()
    fun progressOf(e: LibEntry): Float {
        val k = keyOf(e) ?: return 0f
        if (k in finished) return 1f
        return (progress[k] ?: 0f).coerceIn(0f, 1f)
    }
    fun setProgress(key: String?, frac: Float) {
        if (key == null) return
        val f = frac.coerceIn(0f, 1f)
        val prev = progress[key] ?: 0f
        if (kotlin.math.abs(f - prev) < 0.01f && f < 1f) return   // ignore tiny deltas
        progress[key] = f
        prefs.edit().putString("progress", progress.entries.joinToString(";") { "${it.key}|${it.value}" }).apply()
    }
    private fun loadProgress() {
        val pg = prefs.getString("progress", "") ?: ""
        progress.clear()
        pg.split(";").forEach { e ->
            val i = e.lastIndexOf('|')
            if (i > 0) e.substring(i + 1).toFloatOrNull()?.let { progress[e.substring(0, i)] = it }
        }
    }

    // --- library multi-select / bulk actions ---
    val selectedBooks = mutableStateListOf<String>()
    var libSelectMode by mutableStateOf(false)
        private set
    // A stable, non-null key per book (survives library re-sorts).
    fun libKey(e: LibEntry): String = e.bookDir ?: e.firstAudio ?: e.ebookPath ?: e.coverPath ?: e.book.title(Lang.EN)
    fun isBookSelected(e: LibEntry) = libKey(e) in selectedBooks
    fun startLibSelect(e: LibEntry) {
        libSelectMode = true
        val k = libKey(e); if (k !in selectedBooks) selectedBooks.add(k)
    }
    fun toggleBookSelected(e: LibEntry) {
        val k = libKey(e)
        if (k in selectedBooks) selectedBooks.remove(k) else selectedBooks.add(k)
        if (selectedBooks.isEmpty()) libSelectMode = false
    }
    /** Select-all, or clear if everything is already selected (toggle). */
    fun selectAllBooks(keys: List<String>) {
        if (keys.isNotEmpty() && selectedBooks.size >= keys.size) {
            selectedBooks.clear(); libSelectMode = false
        } else {
            selectedBooks.clear(); selectedBooks.addAll(keys); libSelectMode = true
        }
    }
    fun clearLibSelection() { selectedBooks.clear(); libSelectMode = false }
    fun selectedEntries(): List<LibEntry> {
        val keys = selectedBooks.toHashSet()
        return library.filter { libKey(it) in keys }
    }
    fun deleteSelectedBooks() {
        val entries = selectedEntries()
        clearLibSelection()
        kotlin.concurrent.thread {
            entries.forEach { deleteEntryFiles(it) }
            refreshLibrary()
        }
    }

    // toast
    var toast by mutableStateOf<String?>(null)

    init {
        loadPrefs()
        refreshLibrary()
        if (prefs.getBoolean("onboarded", false)) screen = "library"
    }

    // --- navigation ---
    fun go(s: String) {
        screen = s
        sheetOpen = false
        // Clear any player/reader sub-sheets so they don't leak across screens.
        sleepSheet = false; bmSheet = false; chListOpen = false
        readerPanel = false; readerToc = false
        annotTarget = null; annotListOpen = false; noteEdit = null; bmPanelOpen = false
        penMode = false; penEraser = false; penMarker = false; clearSelection(); clearLibSelection()
    }
    /** True when something dismissible (sheet, selection, or pen mode) is open. */
    // Full-screen cover viewer (tap a cover in the detail popup to review it).
    var coverView by mutableStateOf<Int?>(null)
    fun openCover(i: Int) { coverView = i }

    val anySheetOpen get() = coverView != null || sheetOpen || sleepSheet || bmSheet || chListOpen || readerPanel || readerToc || annotListOpen || noteEdit != null || bmPanelOpen || penMode || selActive || libSelectMode
    fun dismissTopSheet() {
        when {
            coverView != null -> coverView = null
            libSelectMode -> clearLibSelection()
            // Back from a series→detail returns to the series list (not a full close).
            sheetOpen && sheetMode == "detail" && detailFromSeries -> { detailFromSeries = false; sheetMode = "series" }
            sheetOpen -> sheetOpen = false
            noteEdit != null -> noteEdit = null
            selActive -> clearSelection()
            bmPanelOpen -> bmPanelOpen = false
            annotListOpen -> annotListOpen = false
            penMode -> penMode = false
            readerToc -> readerToc = false
            readerPanel -> readerPanel = false
            sleepSheet -> sleepSheet = false
            bmSheet -> bmSheet = false
            chListOpen -> chListOpen = false
        }
    }
    fun closeSheet() { sheetOpen = false }
    /** True when the detail sheet was opened from a series, so Back returns to the series. */
    var detailFromSeries by mutableStateOf(false)
    fun openDetail(i: Int, fromSeries: Boolean = false) { detailBook = i; detailFromSeries = fromSeries; sheetMode = "detail"; sheetOpen = true }
    // series collection sheet — holds the library indices of the volumes
    var seriesName by mutableStateOf("")
    val seriesMembers = mutableStateListOf<Int>()
    fun openSeries(name: String, members: List<Int>) {
        seriesName = name; seriesMembers.clear(); seriesMembers.addAll(members)
        sheetMode = "series"; sheetOpen = true
    }
    fun openShare(i: Int, kind: String) { detailBook = i; shareAsset = kind; sheetMode = "share"; sheetOpen = true }
    fun backToDetail() { sheetMode = "detail" }
    fun openSheet(srcId: String, tag: String) {
        sheetSrc = srcId; sheetMode = tag; email = ""; password = ""; cookiesPicked = false; sheetOpen = true
    }
    fun openManage(srcId: String) { sheetSrc = srcId; sheetMode = "manage"; sheetOpen = true }

    fun openPlayer(i: Int) {
        screen = "player"; sheetOpen = false; playing = i; isPlaying = true
        // Load this book's saved bookmarks into the live list shown on the player.
        val key = library.getOrNull(i)?.let { keyOf(it) }
        pBookmarks.clear(); pBookmarks.addAll(audioBookmarks[key ?: "?"] ?: emptyList())
    }
    fun openReader(i: Int) {
        screen = "reader"; sheetOpen = false; reading = i
        // Resume at the last page/chapter for this book (EPUB → chapter index,
        // PDF → 1-based page); fall back to the start.
        val e = library.getOrNull(i)
        val key = e?.let { keyOf(it) }
        val saved = key?.let { readerPos[it] }
        val isPdf = e?.ebookPath?.substringAfterLast('.', "")?.lowercase() == "pdf"
        if (saved != null && isPdf) { readerPage = saved.coerceAtLeast(1); readerCh = 0 }
        else if (saved != null) { readerCh = saved.coerceAtLeast(0); readerPage = 1 }
        else { readerCh = 0; readerPage = 1 }
    }

    // --- chapter math (delegates to the real player) ---
    fun curChapter(sec: Int): Int {
        val o = com.samra.downloader.playback.PlaybackController.chapterStarts
        var c = 0
        for (i in o.indices) if (sec >= o[i]) c = i
        return c
    }

    // --- player actions ---
    fun addBookmark(): Boolean {
        val sec = com.samra.downloader.playback.PlaybackController.globalPosition()
        if (pBookmarks.any { Math.abs(it - sec) < 3 }) return false
        pBookmarks.add(sec); pBookmarks.sort(); persistAudioBookmarks(); return true
    }
    fun removeBookmark(sec: Int) { pBookmarks.remove(sec); persistAudioBookmarks() }

    // --- persisted audio resume position + per-book audio bookmarks ---
    // Last playback position (seconds) per book, restored when the player opens.
    val audioPos = androidx.compose.runtime.mutableStateMapOf<String, Int>()
    fun audioPosOf(key: String?): Int = key?.let { audioPos[it] } ?: 0
    fun saveAudioPos(key: String?, sec: Int) {
        if (key == null || audioPos[key] == sec) return
        audioPos[key] = sec
        prefs.edit().putString("audioPos", audioPos.entries.joinToString(";") { "${it.key}|${it.value}" }).apply()
    }
    fun clearAudioPos(key: String?) {
        if (key == null || key !in audioPos) return
        audioPos.remove(key)
        prefs.edit().putString("audioPos", audioPos.entries.joinToString(";") { "${it.key}|${it.value}" }).apply()
    }
    private fun loadAudioPos() {
        audioPos.clear()
        (prefs.getString("audioPos", "") ?: "").split(";").forEach { e ->
            val i = e.lastIndexOf('|'); if (i > 0) e.substring(i + 1).toIntOrNull()?.let { audioPos[e.substring(0, i)] = it }
        }
    }

    // Player (time) bookmarks per book — keeps [pBookmarks] (the live list for the
    // open book) in sync with this persisted map.
    val audioBookmarks = androidx.compose.runtime.mutableStateMapOf<String, List<Int>>()
    private fun persistAudioBookmarks() {
        // Prefer the OPEN book (the one pBookmarks was loaded for in openPlayer).
        // PlaybackController.bookKey lags during the async open, so using it first
        // could write the new book's bookmarks under the previous book's key.
        val key = library.getOrNull(playing ?: -1)?.let { keyOf(it) }
            ?: com.samra.downloader.playback.PlaybackController.bookKey ?: return
        audioBookmarks[key] = pBookmarks.toList()
        val obj = org.json.JSONObject()
        audioBookmarks.forEach { (k, list) -> obj.put(k, org.json.JSONArray().also { a -> list.forEach { a.put(it) } }) }
        prefs.edit().putString("audioBookmarks", obj.toString()).apply()
    }
    private fun loadAudioBookmarks() {
        audioBookmarks.clear()
        try {
            val obj = org.json.JSONObject(prefs.getString("audioBookmarks", "{}") ?: "{}")
            obj.keys().forEach { k ->
                val arr = obj.getJSONArray(k); val list = ArrayList<Int>()
                for (i in 0 until arr.length()) list.add(arr.getInt(i))
                audioBookmarks[k] = list
            }
        } catch (_: Exception) {}
    }

    // --- persisted reader resume position (EPUB chapter / PDF page) per book ---
    val readerPos = androidx.compose.runtime.mutableStateMapOf<String, Int>()
    fun saveReaderPos(key: String?, unit: Int) {
        if (key == null || readerPos[key] == unit) return
        readerPos[key] = unit
        prefs.edit().putString("readerPos", readerPos.entries.joinToString(";") { "${it.key}|${it.value}" }).apply()
    }
    private fun loadReaderPos() {
        readerPos.clear()
        (prefs.getString("readerPos", "") ?: "").split(";").forEach { e ->
            val i = e.lastIndexOf('|'); if (i > 0) e.substring(i + 1).toIntOrNull()?.let { readerPos[e.substring(0, i)] = it }
        }
    }
    fun setSleep(minutes: Int?, chapter: Boolean) {
        val pc = com.samra.downloader.playback.PlaybackController
        when {
            chapter -> {
                val c = pc.currentChapter()
                val end = pc.chapterStarts.getOrElse(c) { 0 } + pc.chapterLength(c)
                sleepIsChapter = true; sleepMinutes = null
                sleepLeft = maxOf(1, end - pc.globalPosition())
            }
            minutes == null -> { sleepIsChapter = false; sleepMinutes = null; sleepLeft = 0 }
            else -> { sleepIsChapter = false; sleepMinutes = minutes; sleepLeft = minutes * 60 }
        }
        sleepSheet = false
    }
    val sleepActive get() = sleepMinutes != null || sleepIsChapter

    // --- reader actions ---
    fun rPage(d: Int, max: Int) { readerPage = (readerPage + d).coerceIn(1, max) }
    fun setFont(d: Float) { fontScale = (fontScale + d).coerceIn(0.8f, 1.5f) }
    fun jumpReaderCh(i: Int) { readerCh = i; readerToc = false; readerPage = 12 + i * 60 }
}
