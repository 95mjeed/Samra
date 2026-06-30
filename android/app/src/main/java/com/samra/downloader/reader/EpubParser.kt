package com.samra.downloader.reader

import android.util.Xml
import androidx.core.text.HtmlCompat
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.util.zip.ZipFile

/** A tappable link inside a paragraph: [start,end) in the paragraph's clean text. */
data class EpubLink(val start: Int, val end: Int, val href: String)

data class EpubChapter(
    val title: String,
    val paragraphs: List<String>,
    /** Per-paragraph link spans (same indices as [paragraphs]). */
    val links: List<List<EpubLink>> = emptyList(),
    /** Resolved spine file path of this chapter (for resolving link targets). */
    val file: String = "",
    /** Anchor id -> paragraph index, for jump targets (footnotes, refs). */
    val anchors: Map<String, Int> = emptyMap(),
)

/** Minimal EPUB reader: container.xml → OPF spine → XHTML → plain text. */
object EpubParser {

    fun parse(path: String): List<EpubChapter> {
        return try {
            ZipFile(File(path)).use { zip ->
                val opfPath = rootfilePath(zip) ?: return emptyList()
                val base = opfPath.substringBeforeLast('/', "")
                val (manifest, spine) = parseOpf(zip, opfPath)
                val chapters = ArrayList<EpubChapter>()
                var n = 0
                for (idref in spine) {
                    val href = manifest[idref] ?: continue
                    val full = resolve(base, href).substringBefore('#')
                    val entry = zip.getEntry(full) ?: zip.getEntry(href) ?: continue
                    val html = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                    val rich = htmlToRich(html)
                    if (rich.paras.isEmpty()) continue
                    n++
                    chapters.add(EpubChapter(rich.title ?: "", rich.paras, rich.links, full, rich.anchors))
                }
                chapters
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Extract the EPUB's cover image bytes (cover meta → properties → heuristic). */
    fun coverBytes(path: String): ByteArray? {
        return try {
            ZipFile(File(path)).use { zip ->
                val opfPath = rootfilePath(zip) ?: return null
                val base = opfPath.substringBeforeLast('/', "")
                val opf = zip.getEntry(opfPath)?.let { zip.getInputStream(it).bufferedReader().use { r -> r.readText() } } ?: return null

                val items = Regex("(?is)<item\\b[^>]*>").findAll(opf).map { tag ->
                    Regex("([\\w:-]+)\\s*=\\s*\"([^\"]*)\"").findAll(tag.value)
                        .associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                }.toList()

                val coverId = Regex("(?is)<meta\\b[^>]*name=\"cover\"[^>]*content=\"([^\"]+)\"").find(opf)?.groupValues?.get(1)
                    ?: Regex("(?is)<meta\\b[^>]*content=\"([^\"]+)\"[^>]*name=\"cover\"").find(opf)?.groupValues?.get(1)

                val href = items.firstOrNull { it["id"] == coverId && it["href"] != null }?.get("href")
                    ?: items.firstOrNull { (it["properties"] ?: "").contains("cover-image") }?.get("href")
                    ?: items.firstOrNull {
                        (it["media-type"] ?: "").startsWith("image") &&
                            ((it["id"] ?: "").contains("cover", true) || (it["href"] ?: "").contains("cover", true))
                    }?.get("href")
                    ?: return null

                val full = resolve(base, href).substringBefore('#')
                val entry = zip.getEntry(full) ?: zip.getEntry(href) ?: return null
                zip.getInputStream(entry).use { it.readBytes() }
            }
        } catch (_: Exception) { null }
    }

    data class EpubMeta(
        val title: String, val author: String, val publisher: String,
        val language: String, val year: String, val description: String,
    )

    /** Read the EPUB's OPF Dublin Core metadata (title/author/publisher/…). */
    fun metadata(path: String): EpubMeta? {
        return try {
            ZipFile(File(path)).use { zip ->
                val opfPath = rootfilePath(zip) ?: return null
                val opf = zip.getEntry(opfPath)?.let { zip.getInputStream(it).bufferedReader().use { r -> r.readText() } } ?: return null
                fun dc(tag: String): String {
                    val raw = Regex("(?is)<dc:$tag[^>]*>(.*?)</dc:$tag>").find(opf)?.groupValues?.get(1)
                        ?: Regex("(?is)<$tag[^>]*>(.*?)</$tag>").find(opf)?.groupValues?.get(1) ?: return ""
                    return HtmlCompat.fromHtml(raw, HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim()
                }
                EpubMeta(
                    title = dc("title"), author = dc("creator"), publisher = dc("publisher"),
                    language = dc("language"), year = dc("date").take(4), description = dc("description"),
                )
            }
        } catch (_: Exception) { null }
    }

    /** Extract the EPUB's <dc:description> as plain text. */
    fun description(path: String): String? {
        return try {
            ZipFile(File(path)).use { zip ->
                val opfPath = rootfilePath(zip) ?: return null
                val opf = zip.getEntry(opfPath)?.let { zip.getInputStream(it).bufferedReader().use { r -> r.readText() } } ?: return null
                val raw = Regex("(?is)<dc:description[^>]*>(.*?)</dc:description>").find(opf)?.groupValues?.get(1)
                    ?: Regex("(?is)<description[^>]*>(.*?)</description>").find(opf)?.groupValues?.get(1)
                raw?.let { HtmlCompat.fromHtml(it, HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim() }?.takeIf { it.isNotBlank() }
            }
        } catch (_: Exception) { null }
    }

    private fun rootfilePath(zip: ZipFile): String? {
        val e = zip.getEntry("META-INF/container.xml") ?: return null
        val text = zip.getInputStream(e).bufferedReader().use { it.readText() }
        val m = Regex("full-path=\"([^\"]+)\"").find(text)
        return m?.groupValues?.get(1)
    }

    private fun parseOpf(zip: ZipFile, opfPath: String): Pair<Map<String, String>, List<String>> {
        val manifest = HashMap<String, String>()
        val spine = ArrayList<String>()
        val e = zip.getEntry(opfPath) ?: return manifest to spine
        zip.getInputStream(e).use { input ->
            val p = Xml.newPullParser()
            p.setInput(input, null)
            var ev = p.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG) {
                    when (p.name) {
                        "item" -> {
                            val id = p.getAttributeValue(null, "id")
                            val href = p.getAttributeValue(null, "href")
                            if (id != null && href != null) manifest[id] = href
                        }
                        "itemref" -> {
                            p.getAttributeValue(null, "idref")?.let { spine.add(it) }
                        }
                    }
                }
                ev = p.next()
            }
        }
        return manifest to spine
    }

    private fun resolve(base: String, href: String): String {
        var h = href
        var b = base
        while (h.startsWith("../")) { h = h.substring(3); b = b.substringBeforeLast('/', "") }
        h = h.removePrefix("./")
        return if (b.isEmpty()) h else "$b/$h"
    }

    // Private-use markers injected into the HTML so anchors + links survive the
    // HtmlCompat plain-text conversion, then parsed back out into spans/offsets.
    private const val AO = ''  // anchor open   (followed by id, then AC)
    private const val AC = ''  // anchor close
    private const val LO = ''  // link open     (followed by href, then LM)
    private const val LM = ''  // link mid      (href ends, link text starts)
    private const val LC = ''  // link close

    private data class Rich(
        val title: String?, val paras: List<String>,
        val links: List<List<EpubLink>>, val anchors: Map<String, Int>,
    )

    /**
     * Convert XHTML to plain paragraphs while preserving (a) every `id` anchor's
     * paragraph index and (b) every `<a href>` as a span, so footnote/reference
     * links can jump to their target.
     */
    private fun htmlToRich(htmlIn: String): Rich {
        var html = htmlIn
            .replace(Regex("(?is)<head.*?</head>"), "")
            .replace(Regex("(?is)<style.*?</style>"), "")
            .replace(Regex("(?is)<script.*?</script>"), "")
        val title = Regex("(?is)<h[1-3][^>]*>(.*?)</h[1-3]>").find(html)
            ?.groupValues?.get(1)?.let { strip(it) }?.takeIf { it.isNotBlank() }

        // Mark every element that carries an id (anchor target) at its content start.
        html = Regex("(?is)(<[a-z][a-z0-9]*\\b[^>]*\\bid\\s*=\\s*\"([^\"]+)\"[^>]*>)").replace(html) {
            it.groupValues[1] + AO + it.groupValues[2] + AC
        }
        // Wrap each link so its href + text are recoverable: LO href LM text LC
        html = Regex("(?is)<a\\b[^>]*\\bhref\\s*=\\s*\"([^\"]+)\"[^>]*>(.*?)</a>").replace(html) {
            LO + it.groupValues[1] + LM + it.groupValues[2] + LC
        }

        val text = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
        val rawParas = text.split(Regex("\n{1,}"))

        val paras = ArrayList<String>()
        val linksAll = ArrayList<List<EpubLink>>()
        val anchors = HashMap<String, Int>()

        for (raw in rawParas) {
            val sb = StringBuilder()
            val spans = ArrayList<EpubLink>()
            val localAnchors = ArrayList<String>()
            var i = 0; var linkStart = -1; var linkHref = ""
            while (i < raw.length) {
                when (raw[i]) {
                    AO -> {
                        val end = raw.indexOf(AC, i + 1)
                        if (end > i) { localAnchors.add(raw.substring(i + 1, end)); i = end + 1 } else i++
                    }
                    LO -> {
                        val mid = raw.indexOf(LM, i + 1)
                        if (mid > i) { linkHref = raw.substring(i + 1, mid); linkStart = sb.length; i = mid + 1 } else i++
                    }
                    LC -> {
                        if (linkStart >= 0 && sb.length > linkStart) spans.add(EpubLink(linkStart, sb.length, linkHref))
                        linkStart = -1; linkHref = ""; i++
                    }
                    else -> { sb.append(raw[i]); i++ }
                }
            }
            val full = sb.toString()
            val leading = full.length - full.trimStart().length
            val clean = full.trim()
            if (clean.length <= 1 && spans.isEmpty() && localAnchors.isEmpty()) continue
            val index = paras.size
            paras.add(clean)
            linksAll.add(spans.mapNotNull { l ->
                val s = (l.start - leading).coerceAtLeast(0)
                val e = (l.end - leading).coerceIn(0, clean.length)
                if (e > s) EpubLink(s, e, l.href) else null
            })
            localAnchors.forEach { anchors[it] = index }
        }
        return Rich(title, paras, linksAll, anchors)
    }

    private fun strip(s: String): String =
        HtmlCompat.fromHtml(s, HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim()
}
