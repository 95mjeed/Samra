"""App state + persistence for Samra desktop.

Config + credentials live under ~/.samra (per-user, off the project). Downloads
go to ~/Downloads/Samra by default.
"""
import json
import os
from pathlib import Path

HOME = Path.home()
CONFIG_DIR = HOME / ".samra"
CONFIG_FILE = CONFIG_DIR / "config.json"
CREDS_FILE = CONFIG_DIR / "creds.json"
STATE_FILE = CONFIG_DIR / "state.json"     # resume positions + bookmarks
DEFAULT_OUTPUT = HOME / "Downloads" / "Samra"

DEFAULTS = {
    "lang": "ar",
    "dark": True,
    "format": "mp3",
    "combine": False,
    "skip": True,
    "onboarded": False,
    "connected": [],            # ["storytel"]
    "output_dir": str(DEFAULT_OUTPUT),
}


class AppState:
    def __init__(self):
        CONFIG_DIR.mkdir(parents=True, exist_ok=True)
        self.cfg = dict(DEFAULTS)
        self._load()
        # transient (not persisted)
        self.screen = "library"        # library | add | sources | settings | player
        self.accounts = {}             # src -> email (loaded from creds)
        self.email = ""
        self.password = ""
        self.remember = True
        self.library = []              # list of book dicts
        self.playing = None            # book dict
        self.reading = None            # book dict (ebook reader)
        self._refresh_accounts()
        # resume positions + bookmarks, keyed by book path
        self._state = {"audio_pos": {}, "audio_bm": {}, "reader_pos": {}, "reader_bm": {}}
        self._load_state()

    # ---- persistence ----
    def _load(self):
        try:
            if CONFIG_FILE.exists():
                self.cfg.update(json.loads(CONFIG_FILE.read_text(encoding="utf-8")))
        except Exception:
            pass

    def save(self):
        try:
            CONFIG_FILE.write_text(json.dumps(self.cfg, ensure_ascii=False, indent=2),
                                   encoding="utf-8")
        except Exception:
            pass

    def get(self, k):
        return self.cfg.get(k, DEFAULTS.get(k))

    def set(self, k, v):
        self.cfg[k] = v
        self.save()

    # ---- credentials (plaintext on the user's own machine, off-VCS) ----
    def _creds(self) -> dict:
        try:
            if CREDS_FILE.exists():
                return json.loads(CREDS_FILE.read_text(encoding="utf-8"))
        except Exception:
            pass
        return {}

    def _write_creds(self, d: dict):
        try:
            CREDS_FILE.write_text(json.dumps(d, ensure_ascii=False), encoding="utf-8")
            try:
                os.chmod(CREDS_FILE, 0o600)
            except Exception:
                pass
        except Exception:
            pass

    def _refresh_accounts(self):
        self.accounts = {}
        for src, rec in self._creds().items():
            if rec.get("u"):
                self.accounts[src] = rec["u"]

    def save_creds(self, src, email, password):
        d = self._creds()
        d[src] = {"u": email, "p": password, "c": None}
        self._write_creds(d)
        if src not in self.cfg["connected"]:
            self.cfg["connected"].append(src)
        self.save()
        self._refresh_accounts()

    def clear_creds(self, src):
        d = self._creds()
        d.pop(src, None)
        self._write_creds(d)
        self.cfg["connected"] = [s for s in self.cfg["connected"] if s != src]
        self.save()
        self._refresh_accounts()

    def creds_json(self) -> str:
        return json.dumps(self._creds())

    def is_connected(self, src="storytel") -> bool:
        return src in self.cfg.get("connected", [])

    # ---- resume positions + bookmarks (state.json, keyed by book path) ----
    def _load_state(self):
        try:
            if STATE_FILE.exists():
                d = json.loads(STATE_FILE.read_text(encoding="utf-8"))
                for k in self._state:
                    if isinstance(d.get(k), dict):
                        self._state[k] = d[k]
        except Exception:
            pass

    def _save_state(self):
        try:
            STATE_FILE.write_text(json.dumps(self._state, ensure_ascii=False),
                                  encoding="utf-8")
        except Exception:
            pass

    # audio resume position (seconds)
    def audio_pos(self, key) -> float:
        try:
            return float(self._state["audio_pos"].get(key, 0) or 0)
        except Exception:
            return 0.0

    def set_audio_pos(self, key, sec):
        if not key:
            return
        sec = max(0.0, float(sec))
        if abs(self._state["audio_pos"].get(key, -1) - sec) < 1:
            return
        self._state["audio_pos"][key] = round(sec, 1)
        self._save_state()

    def clear_audio_pos(self, key):
        if key in self._state["audio_pos"]:
            self._state["audio_pos"].pop(key, None)
            self._save_state()

    # ebook resume position (page index)
    def reader_pos(self, key) -> int:
        try:
            return int(self._state["reader_pos"].get(key, 0) or 0)
        except Exception:
            return 0

    def set_reader_pos(self, key, page):
        if not key:
            return
        if self._state["reader_pos"].get(key) == int(page):
            return
        self._state["reader_pos"][key] = int(page)
        self._save_state()

    # bookmarks: audio = list of seconds; reader = list of page indices
    def bookmarks(self, kind, key) -> list:
        return list(self._state[f"{kind}_bm"].get(key, []))

    def add_bookmark(self, kind, key, value) -> bool:
        if not key:
            return False
        lst = self._state[f"{kind}_bm"].setdefault(key, [])
        v = round(float(value), 1) if kind == "audio" else int(value)
        if any(abs(x - v) < (3 if kind == "audio" else 1) for x in lst):
            return False
        lst.append(v)
        lst.sort()
        self._save_state()
        return True

    def remove_bookmark(self, kind, key, value):
        lst = self._state[f"{kind}_bm"].get(key, [])
        if value in lst:
            lst.remove(value)
            self._save_state()

    @property
    def lang(self):
        return self.cfg.get("lang", "ar")

    @property
    def dark(self):
        return bool(self.cfg.get("dark", True))

    @property
    def output_dir(self):
        return self.cfg.get("output_dir", str(DEFAULT_OUTPUT))
