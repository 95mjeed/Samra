package com.samra.downloader.model

import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer

/** Metadata read back from the audio file's own embedded tags (no sidecar). */
data class EmbeddedMeta(
    val title: String = "",
    val author: String = "",
    val narrator: String = "",
    val publisher: String = "",
    val language: String = "",
    val source: String = "",
    val description: String = "",
    val chapters: List<Chapter> = emptyList(),
)

/**
 * Reads metadata that Android's MediaMetadataRetriever can't expose — the long
 * description/summary, narrator, publisher, source and real chapter marks —
 * straight from the file's embedded MP4 atoms / ID3 frames.
 */
object EmbeddedTags {

    fun read(path: String): EmbeddedMeta = try {
        when (path.substringAfterLast('.', "").lowercase()) {
            "m4b", "m4a", "mp4", "m4p", "m4v" -> readMp4(path)
            "mp3" -> readMp3(path)
            else -> EmbeddedMeta()
        }
    } catch (_: Exception) { EmbeddedMeta() }

    // ---- MP4 / M4B -------------------------------------------------------

    private fun readMp4(path: String): EmbeddedMeta {
        val std = HashMap<String, String>()   // ©nrt, ©pub, desc, ©cmt …
        val free = HashMap<String, String>()  // freeform name -> value
        RandomAccessFile(File(path), "r").use { f ->
            val len = f.length()
            val moov = findChild(f, 0, len, "moov") ?: return mp4Build(std, free, path)
            val udta = findChild(f, moov.first, moov.second, "udta") ?: return mp4Build(std, free, path)
            // meta is a FullBox: 4 version/flags bytes before its children
            val meta = findChild(f, udta.first, udta.second, "meta") ?: return mp4Build(std, free, path)
            val ilst = findChild(f, meta.first + 4, meta.second, "ilst") ?: return mp4Build(std, free, path)

            var p = ilst.first
            val end = ilst.first + ilst.second
            while (p + 8 <= end) {
                val size = readU32(f, p)
                if (size < 8 || p + size > end) break
                val type = readType(f, p + 4)
                val cStart = p + 8
                val cEnd = p + size
                if (type == "----") {
                    var q = cStart
                    var name = ""
                    var value = ""
                    while (q + 8 <= cEnd) {
                        val s2 = readU32(f, q)
                        if (s2 < 8 || q + s2 > cEnd) break
                        val t2 = readType(f, q + 4)
                        when (t2) {
                            "name" -> name = readUtf8(f, q + 12, q + s2)        // 4 version/flags
                            "data" -> value = readUtf8(f, q + 16, q + s2)       // 4+4 type/locale
                        }
                        q += s2
                    }
                    if (name.isNotBlank()) free[name] = value
                } else {
                    val data = findChild(f, cStart, size - 8, "data")
                    if (data != null) std[type] = readUtf8(f, data.first + 8, data.first + data.second)
                }
                p += size
            }
        }
        return mp4Build(std, free, path)
    }

    private fun mp4Build(std: Map<String, String>, free: Map<String, String>, path: String): EmbeddedMeta {
        fun freeOf(vararg keys: String) = keys.firstNotNullOfOrNull { k ->
            free.entries.firstOrNull { it.key.equals(k, true) }?.value?.takeIf { it.isNotBlank() }
        } ?: ""
        val desc = listOf(std["desc"], std["ldes"], std["©cmt"], freeOf("Description"))
            .firstOrNull { !it.isNullOrBlank() } ?: ""
        return EmbeddedMeta(
            title = std["©nam"] ?: "",
            author = std["©ART"] ?: std["aART"] ?: "",
            narrator = std["©nrt"] ?: "",
            publisher = std["©pub"] ?: "",
            language = freeOf("Language", "LANGUAGE"),
            source = freeOf("Source", "SOURCE"),
            description = desc,
            chapters = readMp4Chapters(path),
        )
    }

    private fun readMp4Chapters(path: String): List<Chapter> {
        val ex = MediaExtractor()
        return try {
            ex.setDataSource(path)
            var track = -1
            for (i in 0 until ex.trackCount) {
                val mime = (ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: "").lowercase()
                if (mime.startsWith("audio/") || mime.startsWith("video/") || mime.startsWith("image/")) continue
                if (mime.contains("text") || mime.contains("tx3g") || mime.contains("3gpp") ||
                    mime.contains("subtitle") || mime.contains("quicktime")
                ) { track = i; break }
            }
            if (track < 0) return emptyList()
            ex.selectTrack(track)
            val buf = ByteBuffer.allocate(64 * 1024)
            val out = ArrayList<Chapter>()
            while (true) {
                val size = ex.readSampleData(buf, 0)
                if (size < 0) break
                val startSec = (ex.sampleTime / 1_000_000L).toInt()
                val title = if (size >= 2) {
                    val n = (((buf.get(0).toInt() and 0xFF) shl 8) or (buf.get(1).toInt() and 0xFF))
                        .coerceAtMost(size - 2)
                    if (n > 0) { val b = ByteArray(n); buf.position(2); buf.get(b, 0, n); String(b, Charsets.UTF_8) } else ""
                } else ""
                if (title.isNotBlank()) out.add(Chapter(title.trim(), startSec))
                ex.advance()
            }
            out
        } catch (_: Exception) { emptyList() } finally { ex.release() }
    }

    private fun findChild(f: RandomAccessFile, start: Long, regionSize: Long, type: String): Pair<Long, Long>? {
        var p = start
        val end = start + regionSize
        while (p + 8 <= end) {
            var size = readU32(f, p).toLong()
            var header = 8L
            if (size == 1L) { size = readU64(f, p + 8); header = 16L }   // 64-bit largesize
            if (size == 0L) size = end - p
            if (size < header || p + size > end) return null
            if (readType(f, p + 4) == type) return (p + header) to (size - header)
            p += size
        }
        return null
    }

    private fun readU32(f: RandomAccessFile, at: Long): Long {
        f.seek(at); val b = ByteArray(4); f.readFully(b)
        return ((b[0].toLong() and 0xFF) shl 24) or ((b[1].toLong() and 0xFF) shl 16) or
            ((b[2].toLong() and 0xFF) shl 8) or (b[3].toLong() and 0xFF)
    }

    private fun readU64(f: RandomAccessFile, at: Long): Long {
        f.seek(at); val b = ByteArray(8); f.readFully(b)
        var v = 0L; for (x in b) v = (v shl 8) or (x.toLong() and 0xFF); return v
    }

    private fun readType(f: RandomAccessFile, at: Long): String {
        f.seek(at); val b = ByteArray(4); f.readFully(b); return String(b, Charsets.ISO_8859_1)
    }

    private fun readUtf8(f: RandomAccessFile, start: Long, end: Long): String {
        val n = (end - start).toInt()
        if (n <= 0 || n > 1_000_000) return ""
        f.seek(start); val b = ByteArray(n); f.readFully(b)
        return String(b, Charsets.UTF_8).trim()
    }

    // ---- MP3 / ID3v2 -----------------------------------------------------

    private fun readMp3(path: String): EmbeddedMeta {
        RandomAccessFile(File(path), "r").use { f ->
            val head = ByteArray(10)
            f.seek(0); f.readFully(head)
            if (head[0] != 'I'.code.toByte() || head[1] != 'D'.code.toByte() || head[2] != '3'.code.toByte())
                return EmbeddedMeta()
            val major = head[3].toInt() and 0xFF
            val tagSize = synchsafe(head, 6)
            val body = ByteArray(tagSize.coerceAtMost((f.length() - 10).toInt()))
            f.seek(10); f.readFully(body)

            var description = ""; var publisher = ""; var source = ""
            val chapters = ArrayList<Chapter>()
            var i = 0
            while (i + 10 <= body.size) {
                val id = String(body, i, 4, Charsets.ISO_8859_1)
                if (id[0] == ' ') break
                val size = if (major >= 4) synchsafe(body, i + 4) else beU32(body, i + 4)
                val start = i + 10
                if (size <= 0 || start + size > body.size) break
                when (id) {
                    "COMM" -> if (description.isBlank()) description = parseComm(body, start, start + size)
                    "TPUB" -> publisher = parseText(body, start, start + size)
                    "TXXX" -> { val (d, v) = parseTxxx(body, start, start + size); if (d.equals("Source", true)) source = v }
                    "CHAP" -> parseChap(body, start, start + size)?.let { chapters.add(it) }
                }
                i = start + size
            }
            chapters.sortBy { it.startSec }
            return EmbeddedMeta(publisher = publisher, source = source, description = description, chapters = chapters)
        }
    }

    private fun synchsafe(b: ByteArray, at: Int): Int =
        ((b[at].toInt() and 0x7F) shl 21) or ((b[at + 1].toInt() and 0x7F) shl 14) or
            ((b[at + 2].toInt() and 0x7F) shl 7) or (b[at + 3].toInt() and 0x7F)

    private fun beU32(b: ByteArray, at: Int): Int =
        ((b[at].toInt() and 0xFF) shl 24) or ((b[at + 1].toInt() and 0xFF) shl 16) or
            ((b[at + 2].toInt() and 0xFF) shl 8) or (b[at + 3].toInt() and 0xFF)

    private fun decode(enc: Int, b: ByteArray, from: Int, to: Int): String {
        if (to <= from) return ""
        val cs = when (enc) { 1 -> Charsets.UTF_16; 2 -> Charsets.UTF_16BE; 3 -> Charsets.UTF_8; else -> Charsets.ISO_8859_1 }
        return String(b, from, to - from, cs).trim(' ', ' ', '\n', '\r')
    }

    /** Skip a null-terminated string for the given encoding; return index after terminator. */
    private fun afterTerminator(enc: Int, b: ByteArray, from: Int, to: Int): Int {
        var j = from
        if (enc == 1 || enc == 2) {
            while (j + 1 < to && !(b[j].toInt() == 0 && b[j + 1].toInt() == 0)) j += 2
            return j + 2
        }
        while (j < to && b[j].toInt() != 0) j++
        return j + 1
    }

    private fun parseText(b: ByteArray, from: Int, to: Int): String =
        if (to > from) decode(b[from].toInt() and 0xFF, b, from + 1, to) else ""

    private fun parseComm(b: ByteArray, from: Int, to: Int): String {
        if (to - from < 4) return ""
        val enc = b[from].toInt() and 0xFF
        val textStart = afterTerminator(enc, b, from + 4, to)   // skip enc(1)+lang(3)+short desc
        return decode(enc, b, textStart, to)
    }

    private fun parseTxxx(b: ByteArray, from: Int, to: Int): Pair<String, String> {
        if (to <= from) return "" to ""
        val enc = b[from].toInt() and 0xFF
        val valStart = afterTerminator(enc, b, from + 1, to)
        return decode(enc, b, from + 1, valStart) to decode(enc, b, valStart, to)
    }

    private fun parseChap(b: ByteArray, from: Int, to: Int): Chapter? {
        var j = from
        while (j < to && b[j].toInt() != 0) j++        // element id
        j++
        if (j + 16 > to) return null
        val startMs = beU32(b, j)
        j += 16                                         // start,end,startOffset,endOffset
        var title = ""
        // embedded sub-frames (look for TIT2)
        while (j + 10 <= to) {
            val sid = String(b, j, 4, Charsets.ISO_8859_1)
            val ssize = beU32(b, j + 4)
            val sstart = j + 10
            if (ssize <= 0 || sstart + ssize > to) break
            if (sid == "TIT2") title = parseText(b, sstart, sstart + ssize)
            j = sstart + ssize
        }
        return if (title.isNotBlank()) Chapter(title, startMs / 1000) else null
    }
}
