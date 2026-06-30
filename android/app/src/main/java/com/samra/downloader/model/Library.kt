package com.samra.downloader.model

import android.media.MediaMetadataRetriever
import androidx.compose.ui.graphics.Color
import com.samra.downloader.i18n.Lang
import com.samra.downloader.i18n.Num
import com.samra.downloader.reader.EpubParser
import java.io.File

private fun partLabel(n: Int, lang: Lang): String {
    val num = n.toString().padStart(2, '0')
    return if (lang == Lang.AR) "جزء ${Num.ar(num, lang)}" else "Part $num"
}

/** A playable audio file (one of possibly many parts). */
data class AudioFile(val path: String, val sec: Int)

/** A navigation chapter marker (start time within the whole book). */
data class Chapter(val title: String, val startSec: Int)

/** Extra "about this book" metadata for the detail sheet. */
data class BookMeta(
    val genre: String, val publisher: String, val year: String,
    val language: String, val source: String, val bitrate: Int,
    val folder: String, val description: String = "",
)

/** A real downloaded book scanned from the output directory. */
data class LibEntry(
    val book: Book,
    val audioFiles: List<AudioFile>,
    val chapters: List<Chapter>,
    val ebookPath: String?,
    val coverPath: String? = null,
    val meta: BookMeta = BookMeta("", "", "", "", "", 0, ""),
    val bookDir: String? = null,
    val sidecarPath: String? = null,
) {
    val firstAudio: String? get() = audioFiles.firstOrNull()?.path
    val totalSec: Int get() = audioFiles.sumOf { it.sec }
    val hasAudio: Boolean get() = audioFiles.isNotEmpty()
}

private val AUDIO_EXT = setOf("mp3", "m4b", "m4a", "opus", "ogg", "aac", "wav", "flac", "mka")
private val EBOOK_EXT = setOf("epub", "pdf")
private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "webp")

private val coverPairs = listOf(
    Color(0xFFD79B3A) to Color(0xFF5C3A0E),
    Color(0xFF3E6B6B) to Color(0xFF15302F),
    Color(0xFF4A4E8C) to Color(0xFF1E1F3A),
    Color(0xFFB5532E) to Color(0xFF491B0F),
    Color(0xFF6A6A6A) to Color(0xFF272727),
    Color(0xFF4F7A46) to Color(0xFF1D2F19),
    Color(0xFF7C5BD6) to Color(0xFF2E1F4D),
    Color(0xFF2E6BE6) to Color(0xFF142A52),
)

private fun colorsFor(title: String): Pair<Color, Color> {
    var h = 0
    for (ch in title) h = h * 31 + ch.code
    return coverPairs[Math.floorMod(h, coverPairs.size)]
}

private fun ext(f: File) = f.extension.lowercase()

private fun durationSec(path: String): Int = try {
    val r = MediaMetadataRetriever()
    r.setDataSource(path)
    val ms = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
    r.release()
    (ms / 1000).toInt()
} catch (_: Exception) { 0 }

private data class FileMeta(
    val artist: String, val narrator: String, val album: String,
    val title: String, val genre: String, val year: String,
)

private fun metaOf(path: String): FileMeta = try {
    val r = MediaMetadataRetriever()
    r.setDataSource(path)
    fun k(id: Int) = r.extractMetadata(id)?.trim().orEmpty()
    val artist = k(MediaMetadataRetriever.METADATA_KEY_ARTIST)
        .ifBlank { k(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST) }
    val narrator = k(MediaMetadataRetriever.METADATA_KEY_COMPOSER)
    val album = k(MediaMetadataRetriever.METADATA_KEY_ALBUM)
    val title = k(MediaMetadataRetriever.METADATA_KEY_TITLE)
    val genre = k(MediaMetadataRetriever.METADATA_KEY_GENRE)
    val year = k(MediaMetadataRetriever.METADATA_KEY_YEAR)
        .ifBlank { k(MediaMetadataRetriever.METADATA_KEY_DATE).take(4) }
    r.release()
    FileMeta(artist, narrator, album, title, genre, year)
} catch (_: Exception) { FileMeta("", "", "", "", "", "") }

private fun bitrateKbps(path: String): Int = try {
    val r = MediaMetadataRetriever()
    r.setDataSource(path)
    val br = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull() ?: 0L
    r.release()
    (br / 1000).toInt()
} catch (_: Exception) { 0 }

private fun buildEntry(name: String, audio: List<File>, ebook: File?, bookDir: File?, lang: Lang): LibEntry? {
    if (audio.isEmpty()) return null

    val files = audio.map { AudioFile(it.absolutePath, durationSec(it.absolutePath)) }
    val totalSec = files.sumOf { it.sec }
    val totalBytes = audio.sumOf { it.length() } + (ebook?.length() ?: 0L)
    val mb = (totalBytes / (1024.0 * 1024.0)).toInt()
    val first = audio.first().absolutePath

    val coverFile = bookDir?.listFiles()?.firstOrNull {
        it.isFile && it.nameWithoutExtension.lowercase() == "cover" && ext(it) in IMAGE_EXT
    } ?: audio.first().parentFile?.listFiles()?.firstOrNull {
        it.isFile && it.name.startsWith("$name.cover.") && ext(it) in IMAGE_EXT
    }

    val mmr = metaOf(first)
    val embedded = EmbeddedTags.read(first)
    val epubMeta = ebook?.takeIf { ext(it) == "epub" }?.let { EpubParser.metadata(it.absolutePath) }

    val title = mmr.title.ifBlank { embedded.title }.ifBlank { mmr.album }.ifBlank { name }
    val author = mmr.artist.ifBlank { embedded.author }.ifBlank { epubMeta?.author ?: "" }
    val narrator = mmr.narrator.ifBlank { embedded.narrator }

    // Chapters: prefer the file's real embedded marks; else one per part file.
    val chapters: List<Chapter> = when {
        embedded.chapters.size > 1 -> embedded.chapters
        files.size > 1 -> {
            var acc = 0
            files.mapIndexed { i, f -> Chapter(partLabel(i + 1, lang), acc).also { acc += f.sec } }
        }
        embedded.chapters.size == 1 -> embedded.chapters
        else -> listOf(Chapter(title, 0))
    }

    val (c1, c2) = colorsFor(title)
    val fmt = ext(audio.first()).uppercase().let { if (it == "MP3") "MP3" else it.replaceFirstChar { c -> c } }
    val ep = ebook?.let { Ebook(ext(it).uppercase(), it.length() / (1024.0 * 1024.0), 0) }
    val loc = Loc(title, author, narrator)
    val book = Book(
        ar = loc, en = loc, fmt = fmt,
        h = totalSec / 3600, m = (totalSec % 3600) / 60, mb = mb, ch = chapters.size,
        ep = ep, c1 = c1, c2 = c2,
    )
    val bmeta = BookMeta(
        genre = mmr.genre,
        publisher = embedded.publisher.ifBlank { epubMeta?.publisher ?: "" },
        year = mmr.year.ifBlank { epubMeta?.year ?: "" },
        language = embedded.language.ifBlank { epubMeta?.language ?: "" },
        source = embedded.source,
        bitrate = bitrateKbps(first),
        folder = bookDir?.absolutePath ?: audio.first().parent ?: "",
        description = embedded.description.ifBlank { epubMeta?.description ?: "" },
    )
    return LibEntry(book, files, chapters, ebook?.absolutePath, coverFile?.absolutePath, bmeta, bookDir?.absolutePath, null)
}

private fun buildEbookEntry(name: String, ebook: File, bookDir: File?): LibEntry {
    val em = if (ext(ebook) == "epub") EpubParser.metadata(ebook.absolutePath) else null
    val title = em?.title?.ifBlank { null } ?: name
    val author = em?.author ?: ""
    val (c1, c2) = colorsFor(title)
    val mb = (ebook.length() / (1024.0 * 1024.0)).toInt()
    val ep = Ebook(ext(ebook).uppercase(), ebook.length() / (1024.0 * 1024.0), 0)
    val loc = Loc(title, author, "")
    val book = Book(ar = loc, en = loc, fmt = ext(ebook).uppercase(), h = 0, m = 0, mb = mb, ch = 0, ep = ep, c1 = c1, c2 = c2)
    val bmeta = BookMeta(
        "", em?.publisher ?: "", em?.year ?: "",
        em?.language ?: "", "", 0,
        bookDir?.absolutePath ?: ebook.parent ?: "",
        em?.description ?: "",
    )
    return LibEntry(book, emptyList(), emptyList(), ebook.absolutePath, null, bmeta, bookDir?.absolutePath, null)
}

/** Scan the output dir for downloaded books — audio (single files or part
 *  folders), audio+ebook (sibling files), or ebook-only. */
fun scanLibrary(dir: File, lang: Lang = Lang.EN): List<LibEntry> {
    if (!dir.exists()) return emptyList()
    val out = ArrayList<LibEntry>()
    val children = dir.listFiles()?.sortedBy { it.name.lowercase() } ?: return emptyList()

    // 1) Folder books (parts inside a folder)
    for (child in children) {
        if (!child.isDirectory) continue
        val files = child.listFiles()?.toList() ?: emptyList()
        val audio = files.filter { it.isFile && ext(it) in AUDIO_EXT }.sortedBy { it.name.lowercase() }
        val ebook = files.firstOrNull { it.isFile && ext(it) in EBOOK_EXT }
        if (audio.isNotEmpty()) buildEntry(child.name, audio, ebook, child, lang)?.let { out.add(it) }
        else if (ebook != null) out.add(buildEbookEntry(child.name, ebook, child))
    }

    // 2) Root files, grouped by base name → audio (+ sibling ebook) or ebook-only
    val rootMedia = children.filter { it.isFile && (ext(it) in AUDIO_EXT || ext(it) in EBOOK_EXT) }
    val byBase = rootMedia.groupBy { it.nameWithoutExtension }
    for ((base, files) in byBase) {
        val audio = files.filter { ext(it) in AUDIO_EXT }.sortedBy { it.name.lowercase() }
        val ebook = files.firstOrNull { ext(it) in EBOOK_EXT }
        if (audio.isNotEmpty()) buildEntry(base, audio, ebook, null, lang)?.let { out.add(it) }
        else if (ebook != null) out.add(buildEbookEntry(base, ebook, null))
    }
    return out.sortedBy { it.book.ar.t.lowercase() }
}
