package com.samra.downloader.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.samra.downloader.model.LibEntry
import com.samra.downloader.model.Speeds
import java.io.File

/** Connects to PlaybackService via a MediaController. Plays a book's audio
 *  files as one timeline; chapters are navigation markers within that timeline
 *  (so a single file can still expose many chapters). */
object PlaybackController {
    private var controller: MediaController? = null
    private var connecting = false
    private var pending: (() -> Unit)? = null

    var entryIndex by mutableStateOf<Int?>(null)
        private set
    /** Stable identity of the open book, so the index can be re-resolved after
     *  the library list is re-sorted/refreshed. */
    var bookKey: String? = null
        private set

    /** Point [entryIndex] at the book's new position after a library refresh. */
    fun rebind(index: Int) { if (index >= 0) entryIndex = index }

    /** Invoked when a book plays through to the end (with its [bookKey]). */
    var onFinished: ((String?) -> Unit)? = null
    var isPlaying by mutableStateOf(false)
        private set
    // True once real playback has actually started for the current book. Guards
    // against a transient STATE_ENDED (e.g. empty/just-connected controller)
    // marking a book "read" before it was ever played through.
    private var hasPlayed = false
    var speedIdx by mutableStateOf(0)
        private set

    // playable files
    private var fileOffsets: List<Int> = emptyList()
    private var fileSecs: List<Int> = emptyList()

    // chapter markers (start seconds within the whole book)
    var chapterStarts: List<Int> = emptyList()
        private set
    var totalSec: Int = 0
        private set

    fun init(context: Context) {
        if (controller != null || connecting) return
        connecting = true
        val token = SessionToken(context.applicationContext, ComponentName(context.applicationContext, PlaybackService::class.java))
        val future = MediaController.Builder(context.applicationContext, token).buildAsync()
        future.addListener({
            try {
                val c = future.get()
                c.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) {
                        isPlaying = playing
                        if (playing) hasPlayed = true
                    }
                    override fun onPlaybackStateChanged(state: Int) {
                        isPlaying = controller?.isPlaying == true
                        // Book finished — mark it read, then reset to the start (paused)
                        // instead of leaving it stuck at the very end. Only count it
                        // as finished if it was actually played through (hasPlayed) and
                        // there is real media — never on a spurious/empty ENDED.
                        if (state == Player.STATE_ENDED && hasPlayed && (controller?.mediaItemCount ?: 0) > 0) {
                            onFinished?.invoke(bookKey)
                            hasPlayed = false
                            controller?.let { it.pause(); it.seekTo(0, 0L) }
                        }
                    }
                })
                controller = c
                connecting = false
                pending?.invoke(); pending = null
            } catch (_: Exception) { connecting = false }
        }, ContextCompat.getMainExecutor(context))
    }

    fun open(context: Context, entry: LibEntry, index: Int, startSec: Int = 0) {
        init(context)
        val action = {
            val c = controller
            fileSecs = entry.audioFiles.map { it.sec }
            val foffs = ArrayList<Int>(); var a = 0
            for (s in fileSecs) { foffs.add(a); a += s }
            fileOffsets = foffs
            totalSec = if (a > 0) a else (entry.chapters.lastOrNull()?.startSec ?: 0)
            chapterStarts = entry.chapters.map { it.startSec.coerceIn(0, maxOf(0, totalSec)) }
            entryIndex = index
            bookKey = entry.firstAudio ?: entry.bookDir ?: entry.ebookPath
            hasPlayed = false
            if (c != null) {
                val items = entry.audioFiles.map { f ->
                    MediaItem.Builder()
                        .setUri(Uri.fromFile(File(f.path)))
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(entry.book.ar.t)
                                .setArtist(entry.book.ar.a.ifBlank { "Samra" })
                                .build()
                        ).build()
                }
                c.setMediaItems(items)
                c.setPlaybackSpeed(Speeds[speedIdx].toFloat())
                // Resume where the listener left off (skip if at/over the end).
                val resume = startSec.coerceIn(0, maxOf(0, totalSec - 1))
                if (resume > 0) {
                    var fi = 0
                    for (i in fileOffsets.indices) if (resume >= fileOffsets[i]) fi = i
                    c.seekTo(fi, ((resume - fileOffsets.getOrElse(fi) { 0 }) * 1000).toLong())
                }
                c.prepare()
                c.play()
            }
        }
        if (controller != null) action() else pending = action
    }

    fun globalPosition(): Int {
        val c = controller ?: return 0
        val idx = c.currentMediaItemIndex
        val pos = (c.currentPosition / 1000).toInt().coerceAtLeast(0)
        return (fileOffsets.getOrElse(idx) { 0 } + pos).coerceIn(0, totalSec)
    }

    fun currentChapter(): Int {
        val g = globalPosition()
        var ch = 0
        for (i in chapterStarts.indices) if (g >= chapterStarts[i]) ch = i
        return ch
    }

    /** Length of chapter i (start of next chapter − this one, or to the end). */
    fun chapterLength(i: Int): Int {
        if (i !in chapterStarts.indices) return 0
        val end = if (i + 1 < chapterStarts.size) chapterStarts[i + 1] else totalSec
        return (end - chapterStarts[i]).coerceAtLeast(0)
    }

    fun toggle() { controller?.let { if (it.isPlaying) it.pause() else it.play() } }

    fun seekGlobal(sec: Int) {
        val c = controller ?: return
        val s = sec.coerceIn(0, totalSec)
        var idx = 0
        for (i in fileOffsets.indices) if (s >= fileOffsets[i]) idx = i
        c.seekTo(idx, ((s - fileOffsets.getOrElse(idx) { 0 }) * 1000).toLong())
    }

    fun skip(d: Int) = seekGlobal(globalPosition() + d)

    fun jumpChapter(i: Int) {
        if (chapterStarts.isEmpty()) return
        seekGlobal(chapterStarts[i.coerceIn(0, chapterStarts.size - 1)])
        controller?.play()
    }

    fun prevChapter() {
        if (chapterStarts.isEmpty()) return
        val ch = currentChapter()
        if (globalPosition() - chapterStarts[ch] > 4) jumpChapter(ch) else jumpChapter(maxOf(0, ch - 1))
    }

    fun nextChapter() {
        if (chapterStarts.isEmpty()) return
        jumpChapter(minOf(chapterStarts.size - 1, currentChapter() + 1))
    }

    fun cycleSpeed() {
        speedIdx = (speedIdx + 1) % Speeds.size
        controller?.setPlaybackSpeed(Speeds[speedIdx].toFloat())
    }

    fun pause() { controller?.pause() }

    /** Stop playback and clear the open book so the mini-player disappears. */
    fun stop() {
        controller?.let { it.pause(); it.clearMediaItems(); it.stop() }
        entryIndex = null
        bookKey = null
        isPlaying = false
        fileOffsets = emptyList(); fileSecs = emptyList()
        chapterStarts = emptyList(); totalSec = 0
    }

    val hasBook get() = entryIndex != null
}
