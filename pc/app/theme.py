"""Samra desktop — design tokens (mirrors the Android Compose theme)."""

DARK = {
    "bg": "#0E0F12", "surface": "#15171D", "card": "#1B1E26", "card2": "#23262F",
    "input": "#0A0B0E", "line": "#262A33", "line2": "#343A47",
    "text": "#F3F1EC", "text2": "#9BA1AC", "text3": "#5F646F",
    "accent": "#ECA93C", "accent_deep": "#C9881F", "on_accent": "#1A1206",
    "success": "#4FB985", "info": "#5B8DEF", "error": "#E5594E", "queued": "#6B7280",
}

LIGHT = {
    "bg": "#FBF8F1", "surface": "#FFFFFF", "card": "#FFFFFF", "card2": "#F4EFE6",
    "input": "#F4EFE6", "line": "#EBE4D7", "line2": "#DCD4C4",
    "text": "#1A1C20", "text2": "#5B616A", "text3": "#9AA0A8",
    "accent": "#C9881F", "accent_deep": "#A86E12", "on_accent": "#FFFFFF",
    "success": "#2E9E66", "info": "#3B6FD4", "error": "#CE4438", "queued": "#A7ADB5",
}

# Accent at ~13% alpha for soft fills (ARGB hex Flet accepts via opacity in code instead).
ACCENT_SOFT_OPACITY = 0.13

# Corner radii / spacing conventions from the spec.
R_CARD = 14
R_BTN = 12
R_CHIP = 11
PAD_SCREEN = 18


def colors(dark: bool) -> dict:
    return DARK if dark else LIGHT


# Bilingual strings (AR / EN). Keyed; pick by lang.
STRINGS = {
    "app_name_ar": "سمره",
    "app_name_en": "SAMRA",
    "library":  {"ar": "مكتبتي", "en": "My Library"},
    "nav_library": {"ar": "المكتبة", "en": "Library"},
    "nav_download": {"ar": "التنزيل", "en": "Download"},
    "nav_storytel": {"ar": "Storytel", "en": "Storytel"},
    "nav_settings": {"ar": "الإعدادات", "en": "Settings"},
    "search_hint": {"ar": "ابحث في مكتبتك…", "en": "Search your library…"},
    "all": {"ar": "الكل", "en": "All"},
    "lib_empty_title": {"ar": "مكتبتك فارغة", "en": "Your library is empty"},
    "lib_empty_sub": {"ar": "نزّل كتاباً تملكه للاستماع دون اتصال.",
                       "en": "Download a book you own to listen offline."},
    "add_title": {"ar": "إضافة وتنزيل", "en": "Add & Download"},
    "add_sub": {"ar": "الصق روابط الكتب التي تملكها — نتعرّف عليها تلقائياً.",
                 "en": "Paste links to books you own — we detect them automatically."},
    "paste_hint": {"ar": "الصق رابطاً واحداً أو أكثر هنا…",
                    "en": "Paste one or more links here…"},
    "paste": {"ar": "لصق", "en": "Paste"},
    "download": {"ar": "تنزيل", "en": "Download"},
    "links_detected": {"ar": "روابط مكتشفة", "en": "links detected"},
    "no_downloads": {"ar": "لا تنزيلات بعد", "en": "No downloads yet"},
    "queue": {"ar": "قائمة التنزيل", "en": "Download queue"},
    "overall": {"ar": "التقدّم الكلّي", "en": "OVERALL PROGRESS"},
    "log": {"ar": "السجل", "en": "Console"},
    "storytel_title": {"ar": "Storytel", "en": "Storytel"},
    "storytel_sub": {"ar": "سجّل دخولك إلى حساب Storytel لتنزيل كتبك الصوتية والإلكترونية.",
                      "en": "Sign in to your Storytel account to download your audiobooks and e-books."},
    "email": {"ar": "البريد الإلكتروني", "en": "Email"},
    "password": {"ar": "كلمة المرور", "en": "Password"},
    "remember": {"ar": "تذكّر بياناتي (تُحفظ على هذا الجهاز فقط)",
                  "en": "Remember my credentials (stored on this device only)"},
    "sign_in": {"ar": "تسجيل الدخول", "en": "Sign in"},
    "reauth": {"ar": "إعادة المصادقة", "en": "Re-authenticate"},
    "disconnect": {"ar": "قطع الاتصال", "en": "Disconnect"},
    "connected": {"ar": "متّصل", "en": "Connected"},
    "not_signed": {"ar": "لم تسجّل الدخول", "en": "Not signed in"},
    "privacy": {"ar": "تُرسل بياناتك إلى الخدمة التي تسجّل دخولك إليها فقط، وتُحفظ على جهازك إن اخترت ذلك.",
                 "en": "Your details are sent only to the service you sign into, and stored on your device only if you choose."},
    "settings_title": {"ar": "الإعدادات", "en": "Settings"},
    "output_format": {"ar": "صيغة الإخراج", "en": "OUTPUT FORMAT"},
    "combine": {"ar": "دمج الفصول في ملف واحد", "en": "Combine chapters into one file"},
    "combine_sub": {"ar": "احفظ كل كتاب كملف صوتي واحد.", "en": "Save each book as a single audio file."},
    "skip": {"ar": "تخطّي ما تم تنزيله", "en": "Skip already-downloaded"},
    "skip_sub": {"ar": "لا تُعد تنزيل الملفات الموجودة.", "en": "Don't re-download existing files."},
    "appearance": {"ar": "المظهر", "en": "Appearance"},
    "theme": {"ar": "السمة", "en": "Theme"},
    "dark": {"ar": "داكن", "en": "Dark"},
    "light": {"ar": "فاتح", "en": "Light"},
    "language": {"ar": "اللغة", "en": "Language"},
    "now_playing": {"ar": "يُشغّل الآن", "en": "NOW PLAYING"},
    "bookmarks_t": {"ar": "العلامات", "en": "Bookmarks"},
    "add_bm": {"ar": "إضافة علامة", "en": "Add bookmark"},
    "next": {"ar": "التالي", "en": "Next"},
    "skip_intro": {"ar": "تخطّي", "en": "Skip"},
    "get_started": {"ar": "لنبدأ", "en": "Get started"},
    "choose_lang": {"ar": "اختر لغتك", "en": "Choose your language"},
    "arabic": {"ar": "العربية", "en": "العربية"},
    "english": {"ar": "English", "en": "English"},
    "done": {"ar": "تم", "en": "Done"},
    "downloading": {"ar": "يُنزَّل", "en": "Downloading"},
    "failed": {"ar": "فشل", "en": "Failed"},
    "by": {"ar": "تأليف", "en": "By"},
    "play": {"ar": "تشغيل", "en": "Play"},
    "delete": {"ar": "حذف", "en": "Delete"},
}

# Onboarding slides (title, desc) per lang.
SLIDES = {
    "ar": [
        ("حمّل ما تملكه", "نزّل كتبك الصوتية من Storytel للاستماع دون اتصال."),
        ("مكتبة Storytel بين يديك", "كل كتبك في مكان واحد، منظّمة وجاهزة."),
        ("خاص — يبقى على جهازك", "لا تتبّع ولا حسابات إضافية. بياناتك لك وحدك."),
    ],
    "en": [
        ("Download what you own", "Save your Storytel audiobooks to listen offline."),
        ("Your Storytel library, in hand", "Everything in one place — organized and ready."),
        ("Private — stays on your device", "No tracking, no extra accounts. Your data is yours."),
    ],
}


def t(key: str, lang: str) -> str:
    v = STRINGS.get(key)
    if isinstance(v, dict):
        return v.get(lang, v.get("en", key))
    return v or key
