package com.samra.downloader.i18n

/** Numeral conversion + formatters — mirrors the prototype helpers. */
object Num {
    private val arDigits = mapOf(
        '0' to '٠', '1' to '١', '2' to '٢', '3' to '٣', '4' to '٤',
        '5' to '٥', '6' to '٦', '7' to '٧', '8' to '٨', '9' to '٩',
    )

    fun ar(s: Any?, lang: Lang): String {
        val str = s?.toString() ?: ""
        if (lang != Lang.AR) return str
        return buildString { for (c in str) append(arDigits[c] ?: c) }
    }

    fun dur(h: Int, m: Int, lang: Lang): String =
        if (lang == Lang.AR) "${ar(h, lang)}س ${ar(m, lang)}د" else "${h}h ${m}m"

    fun size(mb: Double, lang: Lang): String {
        val t = stringsFor(lang)
        return if (mb >= 1024) {
            "${ar(String.format("%.1f", mb / 1024), lang)} ${t.uGB}"
        } else {
            val v = if (mb < 10) String.format("%.1f", mb) else Math.round(mb).toString()
            "${ar(v, lang)} ${t.uMB}"
        }
    }

    fun size(mb: Int, lang: Lang): String = size(mb.toDouble(), lang)

    fun hms(secIn: Int, lang: Lang): String {
        val sec = secIn.coerceAtLeast(0)
        val h = sec / 3600
        val m = sec % 3600 / 60
        val x = sec % 60
        return ar("$h:${m.toString().padStart(2, '0')}:${x.toString().padStart(2, '0')}", lang)
    }

    fun mmss(secIn: Int, lang: Lang): String {
        val sec = secIn.coerceAtLeast(0)
        val m = sec / 60
        val x = sec % 60
        return ar("$m:${x.toString().padStart(2, '0')}", lang)
    }

    fun eta(secIn: Int, lang: Lang): String {
        val t = stringsFor(lang)
        val sec = secIn.coerceAtLeast(0)
        val m = sec / 60
        val s = sec % 60
        val core = if (m > 0) {
            if (lang == Lang.AR) "${ar(m, lang)} د ${ar(s, lang)} ث" else "${m}m ${s}s"
        } else {
            if (lang == Lang.AR) "${ar(s, lang)} ث" else "${s}s"
        }
        return if (lang == Lang.AR) "${t.uLeft} $core" else "$core ${t.uLeft}"
    }
}
