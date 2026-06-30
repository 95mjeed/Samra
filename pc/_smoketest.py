"""Headless smoke test: build every screen with a stub page to catch Flet API errors."""
import sys, traceback
from types import SimpleNamespace
import flet as ft
from app.main import Samra


class StubPage:
    def __init__(self):
        self.window = SimpleNamespace(width=0, height=0, min_width=0, min_height=0,
                                      center=lambda: None)
        self.controls = []
        self.overlay = []
        self.title = ""; self.padding = 0; self.bgcolor = ""; self.rtl = False
        self.theme_mode = None
    def update(self): pass
    def open(self, *a, **k): pass
    def get_clipboard(self): return ""


def run():
    p = StubPage()
    app = Samra(p)
    # force onboarded so main screens build
    app.s.cfg["onboarded"] = True
    app.lang_chosen = True
    errors = []
    for scr in ["library", "add", "sources", "settings"]:
        try:
            app.s.screen = scr
            app.render()
            print(f"  render {scr}: OK ({len(p.controls)} root control)")
        except Exception:
            errors.append(scr); print(f"  render {scr}: FAIL"); traceback.print_exc()
    # onboarding (both gate + slide)
    try:
        app.s.cfg["onboarded"] = False
        app.lang_chosen = False; app.render(); print("  onboarding gate: OK")
        app.lang_chosen = True; app.onb_step = 1; app.render(); print("  onboarding slide: OK")
    except Exception:
        errors.append("onboarding"); traceback.print_exc()
    # player
    try:
        app.s.cfg["onboarded"] = True
        app.s.playing = {"title": "Test", "author": "X", "cover": None, "path": "x.mp3",
                         "ext": "mp3", "parts": ["x.mp3"], "ebook": None}
        app.s.screen = "player"; app.render(); print("  player: OK")
    except Exception:
        errors.append("player"); traceback.print_exc()
    # reader
    try:
        app._rd_pages = [{"title": "Ch 1", "paras": ["hello", "world"]}]
        app._rd_key = "x.epub"; app._rd_page = 0
        app.s.reading = {"title": "Test", "author": "X", "cover": None, "path": "x.epub",
                         "ext": "epub", "parts": [], "ebook": "x.epub"}
        app.s.screen = "reader"; app.render(); print("  reader: OK")
    except Exception:
        errors.append("reader"); traceback.print_exc()
    print("\nRESULT:", "ALL OK" if not errors else f"FAILED: {errors}")
    return 0 if not errors else 1


if __name__ == "__main__":
    sys.exit(run())
