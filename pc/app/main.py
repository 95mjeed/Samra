"""Samra desktop (Flet) — mirrors the Android app's design & functions."""
import os
import sys
import threading
import time
from pathlib import Path

import flet as ft
try:
    from just_playback import Playback        # miniaudio-backed; plays mp3/wav/flac/ogg
except Exception:
    Playback = None

PC_DIR = Path(__file__).resolve().parent.parent
if str(PC_DIR) not in sys.path:
    sys.path.insert(0, str(PC_DIR))

from app.theme import colors, t, SLIDES, R_CARD, R_BTN, R_CHIP, PAD_SCREEN, ACCENT_SOFT_OPACITY
from app.state import AppState
from app import engine_bridge as eng
try:
    from app.brand import APP_ICON_B64
    APP_ICON_SRC = "data:image/png;base64," + APP_ICON_B64
except Exception:
    APP_ICON_SRC = None


def soft(page_colors, key, op=ACCENT_SOFT_OPACITY):
    return ft.Colors.with_opacity(op, page_colors[key])


def shadow(blur=22, dy=8, op=0.30):
    """Soft drop shadow for depth (dark-UI friendly)."""
    return ft.BoxShadow(spread_radius=0, blur_radius=blur,
                        color=ft.Colors.with_opacity(op, "#000000"),
                        offset=ft.Offset(0, dy))


class Samra:
    def __init__(self, page: ft.Page):
        self.page = page
        self.s = AppState()
        # transient UI state
        self.onb_step = 0
        self.lang_chosen = self.s.get("onboarded")
        self.paste = ""
        self.q_items = []          # [{title, status, pct}]
        self.console_lines = []
        self.console_open = False
        self.running = False
        # control refs created lazily
        self.prog_bar = None
        self.prog_label = None
        self.console_box = None
        self.queue_box = None

        # ---- in-app audio (resume + bookmarks), driven from Python ----
        # Playback() can raise if no audio device is available (e.g. headless /
        # locked session) — degrade to the OS player instead of crashing.
        try:
            self._pb = Playback() if Playback else None
        except Exception:
            self._pb = None
        # player runtime (a book is a timeline over one or more part files)
        self._pl_parts, self._pl_durs, self._pl_offs = [], [], []
        self._pl_total = 0.0      # whole-book seconds
        self._pl_idx = 0          # current part index
        self._pl_pos = 0.0        # seconds within current part
        self._pl_playing = False
        self._pl_key = None
        # player control refs (updated live during playback)
        self.pl_slider = self.pl_cur = self.pl_rem = self.pl_play_icon = self.pl_bm_box = None
        # reader runtime
        self._rd_pages, self._rd_key, self._rd_page = [], None, 0

        page.title = "Samra"
        page.window.width = 1200
        page.window.height = 780
        page.window.min_width = 1000
        page.window.min_height = 640
        page.window.center()
        page.padding = 0
        self.apply_theme()
        self.s.library = eng.scan_library(self.s.output_dir)
        self.render()
        # ticker on Flet's event loop: track playback position, save, advance parts.
        try:
            page.run_task(self._pb_loop)
        except Exception:
            pass

    # ---- helpers ----
    def C(self):
        return colors(self.s.dark)

    def tr(self, key):
        return t(key, self.s.lang)

    def apply_theme(self):
        c = self.C()
        self.page.bgcolor = c["bg"]
        self.page.rtl = (self.s.lang == "ar")
        self.page.theme_mode = ft.ThemeMode.DARK if self.s.dark else ft.ThemeMode.LIGHT

    def go(self, screen):
        self.s.screen = screen
        self.render()

    def toast(self, msg):
        c = self.C()
        sb = ft.SnackBar(ft.Text(msg, color=c["text"]), bgcolor=c["card2"])
        self.page.open(sb)

    # ---- styled atoms ----
    def primary_btn(self, label, on_click, expand=True, icon=None):
        c = self.C()
        content = ft.Row(
            [ft.Icon(icon, color=c["on_accent"], size=18)] if icon else [],
            alignment=ft.MainAxisAlignment.CENTER, spacing=8, tight=True)
        content.controls.append(ft.Text(label, color=c["on_accent"],
                                        weight=ft.FontWeight.W_700, size=15))
        return ft.Container(
            content=content, bgcolor=c["accent"], border_radius=R_BTN,
            padding=ft.Padding.symmetric(vertical=14, horizontal=18), alignment=ft.Alignment.CENTER,
            on_click=lambda e: on_click(), ink=True, expand=expand,
            shadow=ft.BoxShadow(spread_radius=0, blur_radius=18,
                                color=ft.Colors.with_opacity(0.32, c["accent"]),
                                offset=ft.Offset(0, 6)))

    def outline_btn(self, label, on_click, expand=False, icon=None, color=None):
        c = self.C()
        col = color or c["text"]
        row = ft.Row([], alignment=ft.MainAxisAlignment.CENTER, spacing=8, tight=True)
        if icon:
            row.controls.append(ft.Icon(icon, color=col, size=18))
        row.controls.append(ft.Text(label, color=col, weight=ft.FontWeight.W_600, size=14))
        return ft.Container(
            content=row, border=ft.Border.all(1, c["line2"]), border_radius=R_BTN,
            padding=ft.Padding.symmetric(vertical=13, horizontal=18), alignment=ft.Alignment.CENTER,
            on_click=lambda e: on_click(), ink=True, expand=expand)

    def card(self, content, pad=14, elevate=False):
        c = self.C()
        return ft.Container(content=content, bgcolor=c["card"],
                            border=ft.Border.all(1, c["line"]), border_radius=R_CARD,
                            padding=pad, shadow=shadow(16, 5, 0.22) if elevate else None)

    def app_mark(self, size=92):
        """The real Samra squircle icon (book + headphones badge)."""
        c = self.C()
        if APP_ICON_SRC:
            return ft.Container(
                content=ft.Image(src=APP_ICON_SRC, width=size, height=size,
                                 fit=ft.BoxFit.CONTAIN),
                width=size, height=size,
                shadow=shadow(int(size * 0.22), int(size * 0.08), 0.30))
        # fallback: drawn glyph (if brand asset missing)
        glyph = ft.Stack([
            ft.Container(
                content=ft.Icon(ft.Icons.MENU_BOOK_ROUNDED, color=c["on_accent"],
                                size=size * 0.46),
                alignment=ft.Alignment.CENTER, width=size, height=size),
            ft.Container(
                content=ft.Container(
                    content=ft.Icon(ft.Icons.HEADPHONES_ROUNDED, color=c["accent"],
                                    size=size * 0.2),
                    bgcolor=c["card"], width=size * 0.34, height=size * 0.34,
                    border_radius=size * 0.1, alignment=ft.Alignment.CENTER),
                alignment=ft.Alignment.BOTTOM_LEFT,
                padding=ft.Padding.only(left=size * 0.06, bottom=size * 0.06),
                width=size, height=size),
        ])
        return ft.Container(
            content=glyph, width=size, height=size,
            border_radius=size * 0.30,
            gradient=ft.LinearGradient(
                begin=ft.Alignment.TOP_LEFT, end=ft.Alignment.BOTTOM_RIGHT,
                colors=[c["accent"], c["accent_deep"]]))

    def page_header(self, title, subtitle=None, trailing=None):
        """Consistent screen header: title (+ optional subtitle) left, action right."""
        c = self.C()
        left = ft.Column([
            ft.Text(title, size=25, weight=ft.FontWeight.W_800, color=c["text"]),
        ], spacing=3, expand=True)
        if subtitle:
            left.controls.append(ft.Text(subtitle, size=13, color=c["text2"]))
        row = [left]
        if trailing is not None:
            row.append(trailing)
        return ft.Row(row, vertical_alignment=ft.CrossAxisAlignment.CENTER)

    # ================= SCREENS =================
    def render(self):
        self.apply_theme()
        c = self.C()
        if not self.s.get("onboarded"):
            body = self.screen_onboarding()
            self.page.controls = [body]
            self.page.update()
            return
        if self.s.screen == "player" and self.s.playing:
            body = self.screen_player()
            self.page.controls = [body]
            self.page.update()
            return
        if self.s.screen == "reader" and self.s.reading:
            body = self.screen_reader()
            self.page.controls = [body]
            self.page.update()
            return

        screens = {
            "library": self.screen_library,
            "add": self.screen_add,
            "sources": self.screen_sources,
            "settings": self.screen_settings,
        }
        builder = screens.get(self.s.screen, self.screen_library)
        # Library uses the full width for its grid; forms (add/sources/settings)
        # are constrained to a comfortable column and centered so fields don't
        # stretch the whole window.
        if self.s.screen == "library":
            inner = ft.Container(content=builder(), expand=True)
        else:
            inner = ft.Container(content=builder(), width=620)
        content = ft.Container(
            content=ft.Row([inner], alignment=ft.MainAxisAlignment.CENTER, expand=True),
            expand=True, bgcolor=c["bg"],
            padding=ft.Padding.only(left=40, top=34, right=40, bottom=28))
        root = ft.Row([self.sidebar(), content], spacing=0, expand=True)
        self.page.controls = [root]
        self.page.update()

    # ---- onboarding + language gate ----
    def screen_onboarding(self):
        c = self.C()
        col = ft.Column(
            [], horizontal_alignment=ft.CrossAxisAlignment.CENTER,
            alignment=ft.MainAxisAlignment.CENTER, spacing=16, expand=False)
        col.controls += [
            self.app_mark(104),
            ft.Text(self.tr("app_name_ar"), size=44, weight=ft.FontWeight.W_800,
                    color=c["text"]),
            ft.Text("SAMRA", size=14, weight=ft.FontWeight.BOLD, color=c["accent"]),
        ]
        if not self.lang_chosen:
            col.controls += [
                ft.Container(height=10),
                ft.Text(t("choose_lang", "ar") + "  /  " + t("choose_lang", "en"),
                        size=16, color=c["text2"]),
                ft.Container(height=6),
                ft.Container(self.primary_btn("العربية", lambda: self._pick_lang("ar")),
                             width=240),
                ft.Container(self.outline_btn("English", lambda: self._pick_lang("en"),
                                              expand=True), width=240),
            ]
        else:
            title, desc = SLIDES[self.s.lang][self.onb_step]
            dots = ft.Row(
                [ft.Container(width=20 if i == self.onb_step else 8, height=8,
                              bgcolor=c["accent"] if i == self.onb_step else c["line2"],
                              border_radius=4) for i in range(3)],
                alignment=ft.MainAxisAlignment.CENTER, spacing=6)
            col.controls += [
                ft.Container(height=14),
                ft.Text(title, size=24, weight=ft.FontWeight.W_800, color=c["text"],
                        text_align=ft.TextAlign.CENTER),
                ft.Container(ft.Text(desc, size=14, color=c["text2"],
                                     text_align=ft.TextAlign.CENTER), width=300),
                ft.Container(height=8), dots, ft.Container(height=10),
            ]
            last = self.onb_step == 2
            col.controls.append(ft.Container(
                self.primary_btn(self.tr("get_started") if last else self.tr("next"),
                                 self._onb_next), width=300))
            if not last:
                col.controls.append(
                    ft.TextButton(self.tr("skip_intro"), on_click=lambda e: self._finish_onb()))
        return ft.Container(
            content=ft.Container(col, width=460),
            expand=True, padding=24, alignment=ft.Alignment.CENTER, bgcolor=c["bg"])

    def _pick_lang(self, lang):
        self.s.set("lang", lang)
        self.lang_chosen = True
        self.render()

    def _onb_next(self):
        if self.onb_step < 2:
            self.onb_step += 1
            self.render()
        else:
            self._finish_onb()

    def _finish_onb(self):
        self.s.set("onboarded", True)
        self.s.screen = "library"
        self.render()

    # ---- library ----
    def screen_library(self):
        c = self.C()
        books = self.s.library
        cta = ft.Container(
            self.primary_btn(self.tr("nav_download"), lambda: self.go("add"),
                             expand=False, icon=ft.Icons.ADD_ROUNDED),
            width=180)
        head = self.page_header(
            self.tr("library"),
            subtitle=(f"{len(books)} " + ("كتب" if self.s.lang == "ar" else "books"))
            if books else None,
            trailing=cta if books else None)
        if not books:
            empty = ft.Column([
                ft.Container(ft.Icon(ft.Icons.MENU_BOOK_ROUNDED, size=40, color=c["accent"]),
                             width=84, height=84, bgcolor=soft(c, "accent"),
                             border_radius=20, alignment=ft.Alignment.CENTER),
                ft.Container(height=10),
                ft.Text(self.tr("lib_empty_title"), size=18, weight=ft.FontWeight.W_700,
                        color=c["text"]),
                ft.Text(self.tr("lib_empty_sub"), size=13, color=c["text2"],
                        text_align=ft.TextAlign.CENTER),
                ft.Container(height=12),
                ft.Container(self.primary_btn(self.tr("nav_download"),
                                              lambda: self.go("add"), expand=False),
                             width=200),
            ], horizontal_alignment=ft.CrossAxisAlignment.CENTER,
               alignment=ft.MainAxisAlignment.CENTER, expand=True, spacing=4)
            return ft.Column([head, ft.Container(empty, expand=True)], expand=True, spacing=14)

        grid = ft.GridView(expand=True, max_extent=190, child_aspect_ratio=0.62,
                           spacing=14, run_spacing=16)
        for b in books:
            grid.controls.append(self._book_tile(b))
        return ft.Column([head, ft.Container(height=4), grid], expand=True, spacing=10)

    def _book_tile(self, b):
        c = self.C()
        if b.get("cover"):
            cover = ft.Container(
                ft.Image(src=b["cover"], fit=ft.BoxFit.COVER,
                         width=180, height=180, border_radius=12),
                border_radius=12, shadow=shadow(18, 8, 0.34))
        else:
            cover = ft.Container(
                ft.Icon(ft.Icons.MENU_BOOK_ROUNDED, color=c["accent"], size=40),
                width=180, height=180, bgcolor=c["card2"], border_radius=12,
                alignment=ft.Alignment.CENTER, shadow=shadow(18, 8, 0.30))
        return ft.Container(
            ft.Column([
                cover,
                ft.Text(b["title"], size=13, weight=ft.FontWeight.W_600, color=c["text"],
                        max_lines=2, overflow=ft.TextOverflow.ELLIPSIS),
                ft.Text(b.get("author") or "", size=11, color=c["text2"], max_lines=1,
                        overflow=ft.TextOverflow.ELLIPSIS),
            ], spacing=4),
            on_click=lambda e, bk=b: self._open_book(bk), ink=True, border_radius=12)

    def _open_book(self, book):
        # Audio present → player (the player offers a "Read" button if an ebook
        # is bundled). Ebook-only → reader.
        if book.get("parts"):
            self._open_player(book)
        elif book.get("ebook"):
            self._open_reader(book)
        else:
            self._open_player(book)

    # ---- add / download ----
    def screen_add(self):
        c = self.C()
        head = self.page_header(self.tr("add_title"), subtitle=self.tr("add_sub"))

        field = ft.TextField(
            value=self.paste, hint_text=self.tr("paste_hint"), multiline=True,
            min_lines=3, max_lines=5, bgcolor=c["input"], color=c["text"],
            border_color=c["line"], focused_border_color=c["accent"],
            border_radius=R_CARD, text_size=14,
            content_padding=ft.Padding.symmetric(vertical=14, horizontal=14),
            on_change=lambda e: setattr(self, "paste", e.control.value))

        n = len([u for u in self.paste.replace(",", "\n").split("\n") if u.strip()])
        detected = ft.Text(f"{n} {self.tr('links_detected')}" if n else "",
                           size=12, color=c["accent"])

        btn_row = ft.Row([
            self.primary_btn(self.tr("download"), self._start_download, icon=ft.Icons.DOWNLOAD),
            self.outline_btn(self.tr("paste"), self._paste_clipboard, icon=ft.Icons.CONTENT_PASTE),
        ], spacing=10)

        self.prog_label = ft.Text("", size=11, weight=ft.FontWeight.W_700, color=c["text3"])
        self.prog_bar = ft.ProgressBar(value=0, color=c["info"], bgcolor=c["card2"],
                                       border_radius=6, height=8)
        prog_card = ft.Container(
            self.card(ft.Column([
                ft.Row([self.prog_label], alignment=ft.MainAxisAlignment.START),
                self.prog_bar,
            ], spacing=8)),
            visible=self.running)
        self._prog_card = prog_card

        self.queue_box = ft.Column(spacing=8)
        self._rebuild_queue()
        queue_section = ft.Column([
            ft.Text(self.tr("queue"), size=12, color=c["text3"]),
            self.queue_box,
        ], spacing=8) if self.q_items else ft.Container()

        self.console_box = ft.Column(
            [ft.Text(l, size=11, color=c["text2"], font_family="Consolas")
             for l in self.console_lines[-200:]],
            spacing=2, scroll=ft.ScrollMode.AUTO)
        console = self.card(ft.Column([
            ft.Row([ft.Icon(ft.Icons.TERMINAL, size=16, color=c["text2"]),
                    ft.Text(self.tr("log"), size=13, weight=ft.FontWeight.W_600,
                            color=c["text"])], spacing=8),
            ft.Container(self.console_box, height=160) if self.console_open else ft.Container(),
        ], spacing=8))
        console.on_click = lambda e: self._toggle_console()

        return ft.Column([
            head, ft.Container(height=6), field, detected, btn_row,
            prog_card, ft.Container(height=4), queue_section,
            ft.Container(height=4), console,
        ], spacing=10, scroll=ft.ScrollMode.AUTO, expand=True,
           horizontal_alignment=ft.CrossAxisAlignment.STRETCH)

    def _rebuild_queue(self):
        if self.queue_box is None:
            return
        c = self.C()
        self.queue_box.controls = []
        for it in self.q_items:
            if it["status"] == "done":
                badge = ft.Container(ft.Icon(ft.Icons.CHECK, color="#FFFFFF", size=16),
                                     bgcolor=c["success"], width=30, height=30,
                                     border_radius=15, alignment=ft.Alignment.CENTER)
                sub = ft.Text(self.tr("done"), size=11, color=c["success"])
            elif it["status"] == "failed":
                badge = ft.Container(ft.Icon(ft.Icons.CLOSE, color="#FFFFFF", size=16),
                                     bgcolor=c["error"], width=30, height=30,
                                     border_radius=15, alignment=ft.Alignment.CENTER)
                sub = ft.Text(self.tr("failed"), size=11, color=c["error"])
            else:
                badge = ft.Container(ft.ProgressRing(width=18, height=18, stroke_width=2,
                                                     color=c["info"]),
                                     width=30, height=30, alignment=ft.Alignment.CENTER)
                sub = ft.Text(f"{self.tr('downloading')} {int(it['pct']*100)}%",
                              size=11, color=c["info"])
            row = ft.Column([
                ft.Row([badge, ft.Column([
                    ft.Text(it["title"], size=13, weight=ft.FontWeight.W_600, color=c["text"],
                            max_lines=1, overflow=ft.TextOverflow.ELLIPSIS, expand=True),
                    sub], spacing=2, expand=True)], spacing=10),
                ft.ProgressBar(value=it["pct"], color=c["info"], bgcolor=c["card2"],
                               height=6, border_radius=3)
                if it["status"] == "running" else ft.Container(),
            ], spacing=6)
            self.queue_box.controls.append(self.card(row, pad=12))

    def _paste_clipboard(self):
        try:
            txt = self.page.get_clipboard() or ""
        except Exception:
            txt = ""
        if txt:
            self.paste = (self.paste + "\n" + txt).strip() if self.paste else txt
            self.toast("Samra: " + ("تم اللصق" if self.s.lang == "ar" else "pasted"))
            self.render()

    def _start_download(self):
        urls = [u.strip() for u in self.paste.replace(",", "\n").split("\n") if u.strip()]
        if not urls:
            return
        if not self.s.is_connected("storytel"):
            self.toast("Storytel: " + self.tr("not_signed"))
            self.go("sources")
            return
        self.running = True
        self.q_items = [{"title": u.split("/")[-1][:40] or u, "status": "queued", "pct": 0.0}
                        for u in urls]
        self.console_lines = []
        self.render()
        threading.Thread(target=self._download_all, args=(urls,), daemon=True).start()

    def _download_all(self, urls):
        for idx, url in enumerate(urls):
            self.q_items[idx]["status"] = "running"
            self._ui_refresh_dl()
            done_evt = threading.Event()
            result = {}

            def on_log(msg):
                self.console_lines.append(msg)
                self._ui_refresh_dl()

            def on_progress(frac, i=idx):
                self.q_items[i]["pct"] = frac
                if self.prog_bar:
                    self.prog_bar.value = frac
                self._ui_refresh_dl()

            def on_book(title, i_, total, i=idx):
                self.q_items[i]["title"] = title
                self._ui_refresh_dl()

            def on_done(data, i=idx):
                result.update(data)
                self.q_items[i]["status"] = "done" if data.get("ok") else "failed"
                self.q_items[i]["pct"] = 1.0 if data.get("ok") else self.q_items[i]["pct"]
                done_evt.set()

            eng.download(url, self.s, on_log, on_progress, on_book, on_done)
            done_evt.wait()
        self.running = False
        self.s.library = eng.scan_library(self.s.output_dir)
        self._ui_refresh_dl(full=True)

    def _ui_refresh_dl(self, full=False):
        try:
            if self.prog_label is not None:
                self.prog_label.value = self.tr("overall")
            if hasattr(self, "_prog_card") and self._prog_card is not None:
                self._prog_card.visible = self.running
            self._rebuild_queue()
            if self.console_box is not None and self.console_open:
                c = self.C()
                self.console_box.controls = [
                    ft.Text(l, size=11, color=c["text2"], font_family="Consolas")
                    for l in self.console_lines[-200:]]
            if full:
                self.render()
            else:
                self.page.update()
        except Exception:
            pass

    def _toggle_console(self):
        self.console_open = not self.console_open
        self.render()

    # ---- sources (Storytel login) ----
    def screen_sources(self):
        c = self.C()
        head = self.page_header(self.tr("storytel_title"), subtitle=self.tr("storytel_sub"))

        connected = self.s.is_connected("storytel")
        email = self.s.accounts.get("storytel", "")

        brand = ft.Container(ft.Text("St", color="#FFFFFF", weight=ft.FontWeight.W_800,
                                     size=18),
                             width=54, height=54, bgcolor="#F1583C", border_radius=14,
                             alignment=ft.Alignment.CENTER)
        status = ft.Column([
            ft.Text("Storytel", size=16, weight=ft.FontWeight.W_700, color=c["text"]),
            ft.Text(email if connected else self.tr("not_signed"),
                    size=12, color=c["success"] if connected else c["text3"]),
        ], spacing=2, expand=True)
        check = ft.Icon(ft.Icons.CHECK_CIRCLE, color=c["success"], size=24) if connected \
            else ft.Container(width=1)
        header_card = self.card(ft.Row([check, status, brand], spacing=12,
                                       vertical_alignment=ft.CrossAxisAlignment.CENTER),
                                elevate=True)

        controls = [head, ft.Container(height=4), header_card]

        if connected:
            controls += [
                self.outline_btn(self.tr("reauth"), lambda: self._reauth(), expand=True,
                                 icon=ft.Icons.REFRESH),
                self.outline_btn(self.tr("disconnect"), lambda: self._disconnect(),
                                 expand=True, icon=ft.Icons.LOGOUT, color=c["error"]),
            ]
        else:
            self._email_field = ft.TextField(
                value=self.s.email, hint_text=self.tr("email"), bgcolor=c["input"],
                color=c["text"], border_color=c["line"], focused_border_color=c["accent"],
                border_radius=R_BTN, prefix_icon=ft.Icons.MAIL_OUTLINE, text_size=14,
                content_padding=ft.Padding.symmetric(vertical=16, horizontal=14),
                on_change=lambda e: setattr(self.s, "email", e.control.value))
            self._pw_field = ft.TextField(
                value=self.s.password, hint_text=self.tr("password"), password=True,
                can_reveal_password=True, bgcolor=c["input"], color=c["text"],
                border_color=c["line"], focused_border_color=c["accent"],
                border_radius=R_BTN, prefix_icon=ft.Icons.LOCK_OUTLINE, text_size=14,
                content_padding=ft.Padding.symmetric(vertical=16, horizontal=14),
                on_change=lambda e: setattr(self.s, "password", e.control.value))
            remember = ft.Row([
                ft.Switch(value=self.s.remember, active_color=c["accent"],
                          on_change=lambda e: setattr(self.s, "remember", e.control.value)),
                ft.Text(self.tr("remember"), size=12, color=c["text2"], expand=True),
            ], spacing=8)
            controls += [
                self._email_field, self._pw_field, remember,
                self.primary_btn(self.tr("sign_in"), self._sign_in, icon=ft.Icons.LOGIN),
            ]

        controls.append(ft.Container(
            ft.Row([ft.Icon(ft.Icons.SHIELD_OUTLINED, color=c["accent"], size=20),
                    ft.Text(self.tr("privacy"), size=12, color=c["text2"], expand=True)],
                   spacing=10, vertical_alignment=ft.CrossAxisAlignment.START),
            bgcolor=soft(c, "accent"), border_radius=R_CARD, padding=14))

        return ft.Column(controls, spacing=12, scroll=ft.ScrollMode.AUTO, expand=True)

    def _sign_in(self):
        email = (self.s.email or "").strip()
        pw = self.s.password or ""
        if not email or not pw:
            self.toast(self.tr("email") + " / " + self.tr("password"))
            return
        self.s.save_creds("storytel", email, pw)
        self.s.password = ""
        self.toast("Storytel: " + self.tr("connected"))
        self.render()

    def _reauth(self):
        self.s.clear_creds("storytel")
        self.render()

    def _disconnect(self):
        self.s.clear_creds("storytel")
        self.toast("Storytel: " + self.tr("not_signed"))
        self.render()

    # ---- settings ----
    def screen_settings(self):
        c = self.C()
        head = self.page_header(self.tr("settings_title"))

        fmts = [("mp3", "MP3"), ("m4b", "M4B"), ("m4a", "M4A"), ("opus", "Opus")]
        fmt_row = ft.Row([], spacing=8, wrap=True)
        for key, label in fmts:
            active = self.s.get("format") == key
            fmt_row.controls.append(ft.Container(
                ft.Text(label, color=c["on_accent"] if active else c["text"],
                        weight=ft.FontWeight.W_700, size=13),
                bgcolor=c["accent"] if active else c["card"],
                border=ft.Border.all(1, c["accent"] if active else c["line"]),
                border_radius=R_CHIP, padding=ft.Padding.symmetric(vertical=10, horizontal=18),
                on_click=lambda e, k=key: self._set_fmt(k), ink=True))

        def toggle_row(key, label, subtxt):
            return self.card(ft.Row([
                ft.Column([
                    ft.Text(label, size=14, weight=ft.FontWeight.W_600, color=c["text"]),
                    ft.Text(subtxt, size=12, color=c["text2"]),
                ], spacing=2, expand=True),
                ft.Switch(value=self.s.get(key), active_color=c["accent"],
                          on_change=lambda e, k=key: self._set_toggle(k, e.control.value)),
            ], vertical_alignment=ft.CrossAxisAlignment.CENTER))

        def seg(options, current, on_pick):
            row = ft.Row([], spacing=0)
            for val, label in options:
                active = current == val
                row.controls.append(ft.Container(
                    ft.Text(label, color=c["on_accent"] if active else c["text2"],
                            weight=ft.FontWeight.W_600, size=13),
                    bgcolor=c["accent"] if active else c["card"],
                    padding=ft.Padding.symmetric(vertical=10, horizontal=18),
                    on_click=lambda e, v=val: on_pick(v), ink=True))
            return ft.Container(row, border=ft.Border.all(1, c["line"]),
                                border_radius=R_BTN, clip_behavior=ft.ClipBehavior.HARD_EDGE)

        return ft.Column([
            head,
            ft.Container(height=4),
            ft.Text(self.tr("output_format"), size=12, color=c["text3"]),
            fmt_row,
            ft.Container(height=6),
            toggle_row("combine", self.tr("combine"), self.tr("combine_sub")),
            toggle_row("skip", self.tr("skip"), self.tr("skip_sub")),
            ft.Container(height=6),
            ft.Text(self.tr("appearance"), size=12, color=c["text3"]),
            ft.Row([ft.Text(self.tr("theme"), size=14, color=c["text"], expand=True),
                    seg([(True, self.tr("dark")), (False, self.tr("light"))],
                        self.s.dark, self._set_dark)],
                   vertical_alignment=ft.CrossAxisAlignment.CENTER),
            ft.Row([ft.Text(self.tr("language"), size=14, color=c["text"], expand=True),
                    seg([("ar", "العربية"), ("en", "English")], self.s.lang, self._set_lang)],
                   vertical_alignment=ft.CrossAxisAlignment.CENTER),
            ft.Container(height=20),
            ft.Text(f"Samra • {self.s.output_dir}", size=11, color=c["text3"]),
        ], spacing=12, scroll=ft.ScrollMode.AUTO, expand=True)

    def _set_fmt(self, k):
        self.s.set("format", k); self.render()

    def _set_toggle(self, k, v):
        self.s.set(k, v)

    def _set_dark(self, v):
        self.s.set("dark", v); self.render()

    def _set_lang(self, v):
        self.s.set("lang", v); self.render()

    # ================= AUDIO PLAYER =================
    @staticmethod
    def _fmt_time(sec):
        sec = int(max(0, sec)); h = sec // 3600; m = (sec % 3600) // 60; s = sec % 60
        return f"{h}:{m:02d}:{s:02d}" if h else f"{m}:{s:02d}"

    def _audio_global(self):
        off = self._pl_offs[self._pl_idx] if self._pl_idx < len(self._pl_offs) else 0.0
        return off + self._pl_pos

    def _load_part(self, idx, within=0.0, play=True):
        """Load part [idx] into the engine and (optionally) start at [within] sec."""
        self._pl_idx = idx
        self._pl_pos = max(0.0, within)
        self._pb.load_file(self._pl_parts[idx])
        if play:
            self._pb.play()
            self._pl_playing = True
        if within > 0:
            try:
                self._pb.seek(within)
            except Exception:
                pass

    def _audio_open(self, book) -> bool:
        """Load a book's parts as one timeline and resume where we left off.
        Returns False if the format can't be decoded (caller falls back to OS)."""
        key = book["path"]
        self._pl_parts = book.get("parts") or [book["path"]]
        self._pl_durs = eng.audio_durations(self._pl_parts)
        offs, acc = [], 0.0
        for d in self._pl_durs:
            offs.append(acc); acc += d
        self._pl_offs = offs
        self._pl_total = acc if acc > 0 else 1.0
        resume = min(self.s.audio_pos(key), max(0.0, self._pl_total - 2))
        idx = 0
        for i, o in enumerate(offs):
            if resume >= o:
                idx = i
        try:
            self._pl_key = key
            self._load_part(idx, resume - offs[idx], play=True)
            return True
        except Exception:
            self._pl_key = None
            return False

    async def _pb_loop(self):
        """Poll playback position ~2×/s on the UI event loop: persist it, refresh
        the UI, advance parts. Runs on Flet's loop so page.update() is safe."""
        import asyncio
        while True:
            try:
                if self._pb is not None and self._pl_key and self._pl_playing:
                    if self._pb.active:
                        self._pl_pos = self._pb.curr_pos
                        self.s.set_audio_pos(self._pl_key, self._audio_global())
                        self._update_player_ui()
                    else:
                        self._on_part_end()
            except Exception:
                pass
            await asyncio.sleep(0.5)

    def _on_part_end(self):
        if self._pl_idx + 1 < len(self._pl_parts):
            self._load_part(self._pl_idx + 1, 0.0, play=True)
        else:
            self._pl_playing = False
            self.s.clear_audio_pos(self._pl_key)
            self._update_player_ui()

    def _audio_toggle(self):
        if self._pb is None:
            return
        if self._pl_playing:
            self._pb.pause(); self._pl_playing = False
        else:
            if self._pb.active:
                self._pb.resume()
            else:                       # finished/stopped → reload current part
                self._load_part(self._pl_idx, self._pl_pos, play=True)
            self._pl_playing = True
        self._update_player_ui()

    def _audio_seek_global(self, sec):
        if self._pb is None:
            return
        sec = max(0.0, min(float(sec), self._pl_total))
        idx = 0
        for i, o in enumerate(self._pl_offs):
            if sec >= o:
                idx = i
        within = sec - self._pl_offs[idx]
        if idx != self._pl_idx or not self._pb.active:
            self._load_part(idx, within, play=self._pl_playing or True)
        else:
            self._pb.seek(within)
            self._pl_pos = within
        if self._pl_key:
            self.s.set_audio_pos(self._pl_key, sec)
        self._update_player_ui()

    def _audio_skip(self, d):
        self._audio_seek_global(self._audio_global() + d)

    def _update_player_ui(self):
        if self.s.screen != "player" or self.pl_slider is None:
            return
        try:
            g = self._audio_global()
            self.pl_slider.max = max(1.0, self._pl_total)
            self.pl_slider.value = min(g, self._pl_total)
            if self.pl_cur:
                self.pl_cur.value = self._fmt_time(g)
            if self.pl_rem:
                self.pl_rem.value = "-" + self._fmt_time(self._pl_total - g)
            if self.pl_play_icon:
                self.pl_play_icon.name = (ft.Icons.PAUSE_ROUNDED if self._pl_playing
                                          else ft.Icons.PLAY_ARROW_ROUNDED)
            self.page.update()
        except Exception:
            pass

    def _open_player(self, book):
        if self._pb is None:                   # no audio backend → OS player
            self._play_external(book["path"]); return
        if self._pl_key != book["path"]:
            if not self._audio_open(book):     # undecodable format → OS player
                self._play_external(book["path"]); return
        self.s.playing = book
        self.go("player")

    def _audio_add_bm(self):
        if self.s.add_bookmark("audio", self._pl_key, self._audio_global()):
            self.toast(("تمت إضافة علامة" if self.s.lang == "ar" else "Bookmark added"))
            self.render()

    def _bm_chips(self, kind, key, fmt, on_jump):
        """A wrap of bookmark chips (tap to jump, × to delete)."""
        c = self.C()
        chips = []
        for v in self.s.bookmarks(kind, key):
            chips.append(ft.Container(
                ft.Row([
                    ft.Text(fmt(v), size=12, color=c["accent"], weight=ft.FontWeight.W_700),
                    ft.Container(ft.Icon(ft.Icons.CLOSE, size=13, color=c["text3"]),
                                 on_click=lambda e, vv=v: self._del_bm(kind, key, vv), ink=True),
                ], spacing=6, tight=True),
                bgcolor=soft(c, "accent"), border_radius=R_CHIP,
                padding=ft.Padding.symmetric(vertical=6, horizontal=10),
                on_click=lambda e, vv=v: on_jump(vv), ink=True))
        return ft.Row(chips, wrap=True, spacing=8, run_spacing=8)

    def _del_bm(self, kind, key, value):
        self.s.remove_bookmark(kind, key, value)
        self.render()

    def screen_player(self):
        c = self.C(); b = self.s.playing
        if b.get("cover"):
            cover = ft.Container(ft.Image(src=b["cover"], width=240, height=240,
                                          fit=ft.BoxFit.COVER, border_radius=24),
                                 border_radius=24, shadow=shadow(28, 12, 0.40))
        else:
            cover = ft.Container(ft.Icon(ft.Icons.MENU_BOOK_ROUNDED, size=80, color=c["accent"]),
                                 width=240, height=240, bgcolor=c["card2"], border_radius=24,
                                 alignment=ft.Alignment.CENTER, shadow=shadow(28, 12, 0.36))
        header = ft.Row([
            ft.IconButton(ft.Icons.KEYBOARD_ARROW_DOWN, icon_color=c["text"],
                          on_click=lambda e: self.go("library")),
            ft.Text(self.tr("now_playing"), size=12, color=c["text3"],
                    weight=ft.FontWeight.W_700, expand=True, text_align=ft.TextAlign.CENTER),
            (ft.IconButton(ft.Icons.MENU_BOOK_ROUNDED, icon_color=c["accent"],
                           tooltip=("قراءة" if self.s.lang == "ar" else "Read"),
                           on_click=lambda e: self._open_reader(b))
             if b.get("ebook") else ft.Container(width=40)),
        ])

        g = self._audio_global()
        self.pl_cur = ft.Text(self._fmt_time(g), size=11, color=c["text2"])
        self.pl_rem = ft.Text("-" + self._fmt_time(self._pl_total - g), size=11, color=c["text2"])
        self.pl_slider = ft.Slider(min=0, max=max(1.0, self._pl_total), value=min(g, self._pl_total),
                                   active_color=c["accent"], inactive_color=c["line2"],
                                   on_change_end=lambda e: self._audio_seek_global(e.control.value))
        self.pl_play_icon = ft.Icon(ft.Icons.PAUSE_ROUNDED if self._pl_playing
                                    else ft.Icons.PLAY_ARROW_ROUNDED, color=c["on_accent"], size=42)

        multi = len(self._pl_parts) > 1
        transport = ft.Row([
            *( [ft.IconButton(ft.Icons.SKIP_PREVIOUS_ROUNDED, icon_color=c["text"], icon_size=28,
                              on_click=lambda e: self._audio_seek_global(
                                  self._pl_offs[max(0, self._pl_idx - 1)]))] if multi else []),
            ft.IconButton(ft.Icons.REPLAY_30, icon_color=c["text"], icon_size=30,
                          on_click=lambda e: self._audio_skip(-30)),
            ft.Container(self.pl_play_icon, bgcolor=c["accent"], width=76, height=76,
                         border_radius=38, alignment=ft.Alignment.CENTER, shadow=shadow(18, 6, 0.32),
                         on_click=lambda e: self._audio_toggle(), ink=True),
            ft.IconButton(ft.Icons.FORWARD_30, icon_color=c["text"], icon_size=30,
                          on_click=lambda e: self._audio_skip(30)),
            *( [ft.IconButton(ft.Icons.SKIP_NEXT_ROUNDED, icon_color=c["text"], icon_size=28,
                              on_click=lambda e: self._audio_seek_global(
                                  self._pl_offs[min(len(self._pl_parts) - 1, self._pl_idx + 1)]))] if multi else []),
        ], alignment=ft.MainAxisAlignment.CENTER, spacing=18,
           vertical_alignment=ft.CrossAxisAlignment.CENTER)

        bms = self.s.bookmarks("audio", b["path"])
        bm_section = ft.Column([
            ft.Row([ft.Icon(ft.Icons.BOOKMARKS_ROUNDED, size=16, color=c["text2"]),
                    ft.Text(self.tr("bookmarks_t"), size=12, color=c["text3"], expand=True),
                    self.outline_btn(self.tr("add_bm"), self._audio_add_bm,
                                     icon=ft.Icons.BOOKMARK_ADD_ROUNDED)],
                   vertical_alignment=ft.CrossAxisAlignment.CENTER, spacing=8),
            self._bm_chips("audio", b["path"], self._fmt_time, self._audio_seek_global),
        ], spacing=10) if bms else ft.Row([
            self.outline_btn(self.tr("add_bm"), self._audio_add_bm,
                             icon=ft.Icons.BOOKMARK_ADD_ROUNDED)],
            alignment=ft.MainAxisAlignment.CENTER)

        body = ft.Column([
            header, ft.Container(height=10), cover, ft.Container(height=18),
            ft.Text(b["title"], size=20, weight=ft.FontWeight.W_800, color=c["text"],
                    text_align=ft.TextAlign.CENTER, max_lines=2),
            ft.Text(b.get("author") or "", size=14, color=c["text2"]),
            ft.Container(height=14),
            self.pl_slider,
            ft.Row([self.pl_cur, self.pl_rem], alignment=ft.MainAxisAlignment.SPACE_BETWEEN),
            ft.Container(height=8), transport,
            ft.Container(height=18), bm_section,
        ], horizontal_alignment=ft.CrossAxisAlignment.CENTER, spacing=4,
           scroll=ft.ScrollMode.AUTO)
        return ft.Container(ft.Container(body, width=560),
                            expand=True, padding=24, alignment=ft.Alignment.CENTER, bgcolor=c["bg"])

    # ================= EBOOK READER =================
    def _paginate(self, epub_path):
        from app.epub import epub_chapters
        pages = []
        for ch in epub_chapters(epub_path):
            title, buf, n = ch["title"], [], 0
            for para in ch["paras"]:
                buf.append(para); n += len(para)
                if n >= 1400:
                    pages.append({"title": title, "paras": buf}); buf, n, title = [], 0, ""
            if buf:
                pages.append({"title": title, "paras": buf})
        return pages or [{"title": "", "paras": ["—"]}]

    def _open_reader(self, book):
        eb = book.get("ebook") or book["path"]
        if eb.lower().endswith(".pdf"):
            self._play_external(eb)   # no in-app PDF view yet
            self.toast("PDF: " + ("فُتح في العارض الافتراضي" if self.s.lang == "ar"
                                  else "opened in default viewer"))
            return
        key = book["path"]
        self.s.reading = book
        if self._rd_key != key:
            self._rd_key = key
            self._rd_pages = self._paginate(eb)
        self._rd_page = max(0, min(self.s.reader_pos(key), len(self._rd_pages) - 1))
        self.go("reader")

    def _reader_go(self, delta):
        self._reader_jump(self._rd_page + delta)

    def _reader_jump(self, page):
        self._rd_page = max(0, min(int(page), len(self._rd_pages) - 1))
        self.s.set_reader_pos(self._rd_key, self._rd_page)
        self.render()

    def _reader_toggle_bm(self):
        if self._rd_page in self.s.bookmarks("reader", self._rd_key):
            self.s.remove_bookmark("reader", self._rd_key, self._rd_page)
        else:
            self.s.add_bookmark("reader", self._rd_key, self._rd_page)
        self.render()

    def screen_reader(self):
        c = self.C(); b = self.s.reading; key = b["path"]
        total = len(self._rd_pages)
        pg = self._rd_page = max(0, min(self._rd_page, total - 1))
        page = self._rd_pages[pg]
        marked = pg in self.s.bookmarks("reader", key)

        header = ft.Row([
            ft.IconButton(ft.Icons.ARROW_BACK_ROUNDED, icon_color=c["text"],
                          on_click=lambda e: self.go("library")),
            ft.Text(b["title"], size=14, weight=ft.FontWeight.W_700, color=c["text"],
                    expand=True, max_lines=1, overflow=ft.TextOverflow.ELLIPSIS,
                    text_align=ft.TextAlign.CENTER),
            ft.IconButton(ft.Icons.BOOKMARK_ROUNDED if marked else ft.Icons.BOOKMARK_BORDER_ROUNDED,
                          icon_color=c["accent"] if marked else c["text2"],
                          on_click=lambda e: self._reader_toggle_bm()),
        ], vertical_alignment=ft.CrossAxisAlignment.CENTER)

        paras = []
        if page["title"]:
            paras.append(ft.Text(page["title"], size=13, color=c["accent"],
                                 weight=ft.FontWeight.W_800))
            paras.append(ft.Container(height=8))
        for para in page["paras"]:
            paras.append(ft.Text(para, size=17, color=c["text"], selectable=True))
        content = ft.Container(
            ft.Column(paras, spacing=14, scroll=ft.ScrollMode.AUTO, expand=True),
            expand=True, padding=ft.Padding.symmetric(vertical=8, horizontal=6))

        bms = self.s.bookmarks("reader", key)
        bm_row = (self._bm_chips("reader", key,
                                 lambda v: (f"ص {v + 1}" if self.s.lang == "ar" else f"p{v + 1}"),
                                 self._reader_jump) if bms else None)

        slider = ft.Slider(min=0, max=max(1, total - 1), value=pg, active_color=c["accent"],
                           inactive_color=c["line2"], divisions=max(1, total - 1),
                           on_change_end=lambda e: self._reader_jump(e.control.value))
        footer = ft.Row([
            ft.IconButton(ft.Icons.CHEVRON_LEFT_ROUNDED, icon_color=c["text"],
                          on_click=lambda e: self._reader_go(-1)),
            ft.Column([slider,
                       ft.Text(f"{pg + 1} / {total}", size=11, color=c["text3"],
                               text_align=ft.TextAlign.CENTER)],
                      expand=True, spacing=0, horizontal_alignment=ft.CrossAxisAlignment.CENTER),
            ft.IconButton(ft.Icons.CHEVRON_RIGHT_ROUNDED, icon_color=c["text"],
                          on_click=lambda e: self._reader_go(1)),
        ], vertical_alignment=ft.CrossAxisAlignment.CENTER)

        col = [header, ft.Container(content, expand=True, width=720)]
        if bm_row is not None:
            col.append(ft.Container(bm_row, width=720))
        col.append(ft.Container(footer, width=720))
        return ft.Container(
            ft.Column(col, horizontal_alignment=ft.CrossAxisAlignment.CENTER, expand=True, spacing=10),
            expand=True, padding=ft.Padding.symmetric(vertical=16, horizontal=24),
            alignment=ft.Alignment.TOP_CENTER, bgcolor=c["bg"])

    def _play_external(self, path):
        try:
            os.startfile(path if isinstance(path, str) else path["path"])
        except Exception:
            self.toast("Cannot open file")

    # ---- desktop sidebar ----
    def sidebar(self):
        c = self.C()
        # brand header
        brand = ft.Row([
            self.app_mark(40),
            ft.Column([
                ft.Text(self.tr("app_name_ar"), size=20, weight=ft.FontWeight.W_800,
                        color=c["text"]),
                ft.Text("SAMRA", size=10, weight=ft.FontWeight.BOLD, color=c["accent"]),
            ], spacing=0),
        ], spacing=12, vertical_alignment=ft.CrossAxisAlignment.CENTER)

        items = [
            ("library", ft.Icons.MENU_BOOK_ROUNDED, self.tr("nav_library")),
            ("add", ft.Icons.DOWNLOAD_ROUNDED, self.tr("nav_download")),
            ("sources", ft.Icons.HUB_ROUNDED, self.tr("nav_storytel")),
            ("settings", ft.Icons.SETTINGS_ROUNDED, self.tr("nav_settings")),
        ]
        nav_items = []
        for key, icon, label in items:
            active = self.s.screen == key
            bar = ft.Container(width=3, height=20,
                               bgcolor=c["accent"] if active else "#00000000",
                               border_radius=2)
            row = ft.Row([
                bar,
                ft.Icon(icon, color=c["accent"] if active else c["text2"], size=21),
                ft.Text(label, size=14,
                        weight=ft.FontWeight.W_700 if active else ft.FontWeight.W_500,
                        color=c["accent"] if active else c["text2"]),
            ], spacing=12, vertical_alignment=ft.CrossAxisAlignment.CENTER)
            nav_items.append(ft.Container(
                row, on_click=lambda e, k=key: self.go(k), ink=True,
                bgcolor=soft(c, "accent") if active else None, border_radius=R_BTN,
                padding=ft.Padding.only(left=8, top=12, right=14, bottom=12)))

        # account footer chip
        connected = self.s.is_connected("storytel")
        email = self.s.accounts.get("storytel", "")
        footer = ft.Container(
            ft.Row([
                ft.Container(ft.Text("St", color="#FFFFFF", weight=ft.FontWeight.W_800, size=13),
                             width=34, height=34, bgcolor="#F1583C", border_radius=10,
                             alignment=ft.Alignment.CENTER),
                ft.Column([
                    ft.Text("Storytel", size=12, weight=ft.FontWeight.W_600, color=c["text"]),
                    ft.Text(email if connected else self.tr("not_signed"), size=10,
                            color=c["success"] if connected else c["text3"], max_lines=1,
                            overflow=ft.TextOverflow.ELLIPSIS),
                ], spacing=0, expand=True),
            ], spacing=10, vertical_alignment=ft.CrossAxisAlignment.CENTER),
            bgcolor=c["card"], border=ft.Border.all(1, c["line"]), border_radius=R_CARD,
            padding=10, on_click=lambda e: self.go("sources"), ink=True)

        menu_label = ft.Container(
            ft.Text(("القائمة" if self.s.lang == "ar" else "MENU"), size=10,
                    weight=ft.FontWeight.W_700, color=c["text3"]),
            padding=ft.Padding.only(left=10, bottom=2))
        col = ft.Column([
            brand,
            ft.Container(height=20),
            menu_label,
            *nav_items,
            ft.Container(expand=True),   # push footer down
            footer,
            ft.Container(
                ft.Text("Samra · v1.0", size=10, color=c["text3"]),
                padding=ft.Padding.only(left=10, top=10)),
        ], spacing=6, expand=True)
        return ft.Container(
            col, width=256, bgcolor=c["surface"],
            border=ft.Border.only(right=ft.BorderSide(1, c["line"])),
            padding=ft.Padding.only(left=16, top=24, right=16, bottom=18))


def main(page: ft.Page):
    Samra(page)


if __name__ == "__main__":
    ft.app(target=main)
