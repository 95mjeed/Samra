package com.samra.downloader.model

import androidx.compose.ui.graphics.Color
import com.samra.downloader.i18n.Lang

data class Loc(val t: String, val a: String, val n: String)
data class Ebook(val fmt: String, val mb: Double, val pages: Int)

data class Book(
    val ar: Loc,
    val en: Loc,
    val fmt: String,
    val h: Int,
    val m: Int,
    val mb: Int,
    val ch: Int,
    val ep: Ebook?,
    val c1: Color,
    val c2: Color,
) {
    fun loc(lang: Lang): Loc = if (lang == Lang.AR) ar else en
    fun title(lang: Lang) = loc(lang).t
    fun author(lang: Lang) = loc(lang).a
    fun narrator(lang: Lang) = loc(lang).n
    val hasEbook get() = ep != null
}

data class Source(
    val id: String,
    val name: String,
    val tag: String, // "login" | "cookies"
    val tint: Color,
    val icon: String,
    val initials: String,
)

/** Sample library — mirrors the prototype's BOOKS (generated covers, no art). */
val DemoBooks = listOf(
    Book(Loc("ساق البامبو", "سعود السنعوسي", "أحمد العلي"), Loc("The Bamboo Stalk", "Saud Alsanousi", "Ahmed Al-Ali"),
        "M4B", 12, 40, 412, 24, Ebook("EPUB", 3.2, 392), Color(0xFFD79B3A), Color(0xFF5C3A0E)),
    Book(Loc("مدن الملح", "عبدالرحمن منيف", "سارة الدوسري"), Loc("Cities of Salt", "Abdelrahman Munif", "Sara Aldosari"),
        "M4B", 21, 5, 640, 31, null, Color(0xFF3E6B6B), Color(0xFF15302F)),
    Book(Loc("موسم الهجرة إلى الشمال", "الطيب صالح", "خالد المنصور"), Loc("Season of Migration", "Tayeb Salih", "Khaled Almansour"),
        "MP3", 9, 12, 198, 12, Ebook("PDF", 6.1, 169), Color(0xFF4A4E8C), Color(0xFF1E1F3A)),
    Book(Loc("رجال في الشمس", "غسان كنفاني", "ليلى حسن"), Loc("Men in the Sun", "Ghassan Kanafani", "Layla Hassan"),
        "M4A", 6, 30, 162, 14, null, Color(0xFFB5532E), Color(0xFF491B0F)),
    Book(Loc("الخبز الحافي", "محمد شكري", "عمر فاروق"), Loc("For Bread Alone", "Mohamed Choukri", "Omar Farouk"),
        "Opus", 8, 5, 118, 16, Ebook("EPUB", 2.4, 224), Color(0xFF6A6A6A), Color(0xFF272727)),
    Book(Loc("فرانكشتاين في بغداد", "أحمد سعداوي", "حسن الجابري"), Loc("Frankenstein in Baghdad", "Ahmed Saadawi", "Hassan Aljabri"),
        "M4B", 14, 20, 388, 29, Ebook("EPUB", 4.0, 416), Color(0xFF4F7A46), Color(0xFF1D2F19)),
)

// Storytel-only build: this app downloads from Storytel exclusively.
val Sources = listOf(
    Source("storytel", "Storytel", "login", Color(0xFFF2543F), "graphic_eq", "St"),
)

/** Reader sample paragraphs (per-chapter body text). */
val ReaderParas = mapOf(
    Lang.AR to listOf(
        "في المساء، حين تهدأ الأصوات وتمتدّ الظلال على الرمل الدافئ، تبدأ الحكاية بهدوء. لم يكن في الطريق سوى ضوءٍ خافت وصوتٍ بعيدٍ لا يعرف أحدٌ مصدره.",
        "مضى وقتٌ طويل قبل أن يلتفت إلى ما خلّفه وراءه: مدينةٌ تنام، وبابٌ مواربٌ، ووعدٌ لم يُقَل بصوتٍ عالٍ. كان يعرف أنّ العودة لن تكون كما كانت.",
        "قال لنفسه إنّ الليل سيمرّ كما تمرّ كلّ الليالي، لكنّ شيئاً في الهواء كان مختلفاً تلك المرّة؛ رائحةُ مطرٍ قادم، أو ذكرى لم تكتمل.",
    ),
    Lang.EN to listOf(
        "In the evening, when the voices settle and the shadows stretch across the warm sand, the story begins quietly. There was nothing on the road but a faint light and a distant sound no one could place.",
        "A long time passed before he turned to what he had left behind: a sleeping city, a door left ajar, and a promise never said aloud. He knew the return would not be as it once was.",
        "He told himself the night would pass as all nights pass, yet something in the air was different this time — the smell of coming rain, or a memory left unfinished.",
    ),
)

val Speeds = listOf(1.0, 1.25, 1.5, 1.75, 2.0, 3.0)

/** Chapter durations (seconds) used by the player/reader timeline. */
val ChapterSecs = listOf(500, 2530, 2335, 3090, 2712, 2890, 2480, 760)

fun chapterTitles(lang: Lang): List<String> = if (lang == Lang.AR)
    listOf("المقدمة", "الفصل الأول", "الفصل الثاني", "الفصل الثالث", "الفصل الرابع", "الفصل الخامس", "الفصل السادس", "الخاتمة")
else
    listOf("Prologue", "Chapter One", "Chapter Two", "Chapter Three", "Chapter Four", "Chapter Five", "Chapter Six", "Epilogue")
