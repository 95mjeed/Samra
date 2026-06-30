package com.samra.downloader.ui

import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.blur
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.samra.downloader.i18n.Lang
import com.samra.downloader.model.Book
import com.samra.downloader.model.LibEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun coverBrush(c1: Color, c2: Color) =
    Brush.linearGradient(colors = listOf(c1, c2), start = Offset(0f, 0f), end = Offset(220f, 360f))

private val motifBrush = Brush.linearGradient(
    0.00f to Color.White.copy(alpha = 0.05f),
    0.08f to Color.White.copy(alpha = 0.05f),
    0.08f to Color.Transparent,
    1.00f to Color.Transparent,
    start = Offset(0f, 0f), end = Offset(13f, -13f),
    tileMode = TileMode.Repeated,
)

// Cap decoded covers to this max dimension: crisp enough for the full-width
// player, while keeping memory bounded for high-res (1400px) embedded covers.
private const val COVER_MAX_PX = 1000

private fun sampleSize(w: Int, h: Int): Int {
    var s = 1
    val m = maxOf(w, h)
    while (m / (s * 2) >= COVER_MAX_PX) s *= 2
    return s
}

/** Downscale a decoded bitmap so its largest side is <= COVER_MAX_PX. */
private fun cap(bm: android.graphics.Bitmap?): android.graphics.Bitmap? {
    if (bm == null) return null
    val m = maxOf(bm.width, bm.height)
    if (m <= COVER_MAX_PX) return bm
    val scale = COVER_MAX_PX.toFloat() / m
    val out = android.graphics.Bitmap.createScaledBitmap(bm, (bm.width * scale).toInt().coerceAtLeast(1), (bm.height * scale).toInt().coerceAtLeast(1), true)
    if (out !== bm) bm.recycle()
    return out
}

private fun decodeSampledFile(path: String): android.graphics.Bitmap? {
    val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, b)
    val o = BitmapFactory.Options().apply { inSampleSize = sampleSize(b.outWidth, b.outHeight) }
    return cap(BitmapFactory.decodeFile(path, o))
}

private fun decodeSampledBytes(data: ByteArray): android.graphics.Bitmap? {
    val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(data, 0, data.size, b)
    val o = BitmapFactory.Options().apply { inSampleSize = sampleSize(b.outWidth, b.outHeight) }
    return cap(BitmapFactory.decodeByteArray(data, 0, data.size, o))
}

private fun loadCoverBitmap(coverPath: String?, audioPath: String?, ebookPath: String?): ImageBitmap? {
    try {
        if (coverPath != null) decodeSampledFile(coverPath)?.let { return it.asImageBitmap() }
        if (audioPath != null) {
            val mmr = MediaMetadataRetriever()
            mmr.setDataSource(audioPath)
            val art = mmr.embeddedPicture
            mmr.release()
            if (art != null) decodeSampledBytes(art)?.let { return it.asImageBitmap() }
        }
        if (ebookPath != null && ebookPath.endsWith(".epub", true)) {
            val art = com.samra.downloader.reader.EpubParser.coverBytes(ebookPath)
            if (art != null) decodeSampledBytes(art)?.let { return it.asImageBitmap() }
        }
    } catch (_: Exception) {}
    return null
}

@Composable
fun rememberCover(coverPath: String?, audioPath: String?, ebookPath: String? = null): ImageBitmap? =
    produceState<ImageBitmap?>(initialValue = null, coverPath, audioPath, ebookPath) {
        value = withContext(Dispatchers.IO) { loadCoverBitmap(coverPath, audioPath, ebookPath) }
    }.value

/**
 * Show a real cover so it FILLS a uniform 2:3 card without cropping: a blurred,
 * cropped copy fills the card behind, then the WHOLE cover is drawn on top (Fit).
 * Square Storytel covers fill the card (soft blurred top/bottom), portrait ebook
 * covers fit exactly — every card is the SAME size. Like Storytel/Spotify.
 */
@Composable
private fun CoverArtFill(art: ImageBitmap) {
    Image(art, null, Modifier.fillMaxSize().blur(24.dp), contentScale = ContentScale.Crop)
    // Subtle navy tint over the blurred backdrop — cooler/“premium” look that helps
    // non-fitting (e.g. square) covers blend, without washing out the sharp cover on top.
    Box(Modifier.fillMaxSize().background(Color(0xFF12284D).copy(alpha = 0.30f)))
    Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
}

/** Full generated/real book cover (grid, shelf, player, detail) — uniform 2:3 card. */
@Composable
fun BookCover(entry: LibEntry, lang: Lang, modifier: Modifier = Modifier, finished: Boolean = false, progress: Float = 0f) {
    val art = rememberCover(entry.coverPath, entry.firstAudio, entry.ebookPath)
    val book = entry.book
    val shape = RoundedCornerShape(13.dp)
    Box(
        modifier
            .aspectRatio(2f / 3f)          // uniform card size (the agreed Storytel-style fit)
            .clip(shape)
            .background(coverBrush(book.c1, book.c2))
            .border(1.dp, Color.White.copy(alpha = 0.08f), shape),
    ) {
        if (art != null) {
            CoverArtFill(art)
        } else {
            GeneratedCover(book, lang)
        }
        // Format badges, centred at the bottom (on both art & generated covers).
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
        ) {
            if (entry.hasAudio) CoverBadge("headphones")
            if (book.hasEbook) CoverBadge("menu_book")
        }
        if (finished) ReadBadge(Modifier.align(Alignment.TopStart).padding(8.dp))
        // Listening/reading progress bar pinned to the bottom edge.
        if (!finished && progress > 0.01f && progress < 0.99f) {
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Color.Black.copy(alpha = 0.32f))) {
                Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(4.dp).background(colors().accent))
            }
        }
    }
}

/** "Read"/finished marker — a check chip shown on covers. */
@Composable
fun ReadBadge(modifier: Modifier = Modifier, size: Int = 22) {
    Box(
        modifier.size(size.dp).clip(RoundedCornerShape((size / 3).dp))
            .background(Color(0xFF2E9E5B)),
        contentAlignment = Alignment.Center,
    ) { Sym("check", Color.White, (size * 0.62f).dp) }
}

/** Square cover for the player (radius 24). */
@Composable
fun PlayerCover(entry: LibEntry, lang: Lang, modifier: Modifier = Modifier) {
    val art = rememberCover(entry.coverPath, entry.firstAudio, entry.ebookPath)
    val book = entry.book
    Box(modifier.clip(RoundedCornerShape(24.dp)).background(coverBrush(book.c1, book.c2))) {
        if (art != null) CoverArtFill(art)   // full cover, no crop (blur-fills the square)
        else GeneratedCover(book, lang)
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
        ) {
            CoverBadge("headphones")
            if (book.hasEbook) CoverBadge("menu_book")
        }
    }
}

@Composable
private fun CoverBadge(icon: String) {
    Box(
        Modifier.size(22.dp).clip(RoundedCornerShape(7.dp)).background(Color.Black.copy(alpha = 0.34f)),
        contentAlignment = Alignment.Center,
    ) { Sym(icon, Color.White.copy(alpha = 0.92f), 13.dp) }
}

@Composable
private fun GeneratedCover(book: Book, lang: Lang) {
    val serif = FontFamily.Serif
    Box(Modifier.fillMaxSize()) {
        // sheen
        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.20f), Color.Transparent),
                    center = Offset(0.82f * 220f, 0.02f * 330f), radius = 240f,
                )
            )
        )
        // diagonal motif
        Box(Modifier.fillMaxSize().background(motifBrush))
        // spine on inline-start
        Box(Modifier.fillMaxSize().width(9.dp).background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.34f), Color.Transparent))))
        Box(Modifier.fillMaxSize().padding(start = 9.dp).width(1.5.dp).background(Color.White.copy(alpha = 0.16f)))
        // inset frame
        Box(Modifier.fillMaxSize().padding(start = 15.dp, top = 10.dp, end = 10.dp, bottom = 10.dp).border(1.dp, Color.White.copy(alpha = 0.24f), RoundedCornerShape(5.dp)))
        // content
        Column(
            Modifier.fillMaxSize().padding(start = 23.dp, top = 20.dp, end = 18.dp, bottom = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            androidx.compose.material3.Text("✦", color = Color.White.copy(alpha = 0.78f), fontFamily = serif, fontSize = 13.sp, modifier = Modifier.rotate(45f))
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    androidx.compose.material3.Text(
                        book.title(lang), color = Color(0xFFFCF8EF), fontFamily = serif,
                        fontSize = 17.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center, maxLines = 4, overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(11.dp))
                    Box(Modifier.width(26.dp).height(1.5.dp).clip(RoundedCornerShape(1.dp)).background(Color.White.copy(alpha = 0.5f)))
                    Spacer(Modifier.height(11.dp))
                    val author = book.author(lang)
                    if (author.isNotBlank()) androidx.compose.material3.Text(
                        author.uppercase(), color = Color.White.copy(alpha = 0.78f),
                        fontSize = 9.5.sp, letterSpacing = 0.6.sp, textAlign = TextAlign.Center,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(26.dp)) // room for the badges overlay
        }
    }
}

/** Compact cover for list rows: real art, else gradient with the title initial. */
@Composable
fun ListCover(entry: LibEntry, lang: Lang, modifier: Modifier = Modifier, finished: Boolean = false, progress: Float = 0f) {
    val art = rememberCover(entry.coverPath, entry.firstAudio, entry.ebookPath)
    val book = entry.book
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(coverBrush(book.c1, book.c2)), contentAlignment = Alignment.Center) {
        if (art != null) {
            Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Box(Modifier.fillMaxSize().width(5.dp).background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.34f), Color.Transparent))))
            androidx.compose.material3.Text(
                book.title(lang).trim().take(1), color = Color(0xFFFCF8EF),
                fontFamily = FontFamily.Serif, fontSize = 22.sp, fontWeight = FontWeight.Bold,
            )
        }
        if (finished) ReadBadge(Modifier.align(Alignment.TopStart).padding(3.dp), size = 16)
        if (!finished && progress > 0.01f && progress < 0.99f) {
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color.Black.copy(alpha = 0.32f))) {
                Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(3.dp).background(colors().accent))
            }
        }
    }
}
