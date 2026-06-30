"""Desktop bridge to the Samra engine: threaded downloads + library scan."""
import base64
import os
import shutil
import sys
import threading
import json
from pathlib import Path

# Make the engine + bridge importable (pc/ is the parent of app/).
PC_DIR = Path(__file__).resolve().parent.parent
if str(PC_DIR) not in sys.path:
    sys.path.insert(0, str(PC_DIR))

import samra as engine  # noqa: E402

AUDIO_EXTS = (".mp3", ".m4b", ".m4a", ".opus", ".mp4", ".m4p", ".wav", ".ogg", ".flac", ".aac")
EBOOK_EXTS = (".epub", ".pdf")


def find_ffmpeg() -> str | None:
    """Bundled ffmpeg(.exe) first (next to the exe / in bin/), then system PATH."""
    exe = "ffmpeg.exe" if os.name == "nt" else "ffmpeg"
    candidates = [PC_DIR / "bin" / exe, PC_DIR / exe]
    if getattr(sys, "frozen", False):
        app_dir = Path(sys.executable).resolve().parent
        candidates = [app_dir / "bin" / exe, app_dir / exe,
                      app_dir / "_internal" / "bin" / exe] + candidates
    for c in candidates:
        if c.exists():
            return str(c)
    return shutil.which("ffmpeg")


class _Listener:
    """Python listener object the engine calls (onLog/onProgress/onBook)."""
    def __init__(self, on_log, on_progress, on_book):
        self._log, self._prog, self._book = on_log, on_progress, on_book

    def onLog(self, msg):
        try: self._log(str(msg))
        except Exception: pass

    def onProgress(self, frac):
        try: self._prog(float(frac))
        except Exception: pass

    def onBook(self, title, index, total):
        try: self._book(str(title), int(index), int(total))
        except Exception: pass


def download(url, state, on_log, on_progress, on_book, on_done):
    """Run a download on a background thread. Callbacks fire from that thread."""
    def worker():
        listener = _Listener(on_log, on_progress, on_book)
        os.makedirs(state.output_dir, exist_ok=True)
        try:
            result = engine.run_download(
                url=url,
                username=None, password=None, cookie_path=None,
                output_dir=state.output_dir,
                output_format=state.get("format"),
                combine=state.get("combine"),
                library=None,
                ffmpeg_path=find_ffmpeg(),
                creds_json=state.creds_json(),
                kt_listener=listener,
            )
            data = json.loads(result)
        except Exception as e:  # noqa: BLE001
            data = {"ok": False, "files": [], "error": str(e)}
        on_done(data)

    threading.Thread(target=worker, daemon=True).start()


# ---------- library scan ----------

def _data_uri(raw: bytes) -> str:
    """Wrap raw image bytes as a data: URI (sniff PNG vs JPEG) for Flet's Image.src."""
    mime = "image/png" if raw[:8] == b"\x89PNG\r\n\x1a\n" else "image/jpeg"
    return "data:%s;base64,%s" % (mime, base64.b64encode(raw).decode("ascii"))


def _read_cover_b64(path: str) -> str | None:
    try:
        low = path.lower()
        if low.endswith(".mp3"):
            from mutagen.id3 import ID3
            tags = ID3(path)
            for k in tags.keys():
                if k.startswith("APIC"):
                    return _data_uri(tags[k].data)
        elif low.endswith((".m4b", ".m4a", ".mp4", ".m4p")):
            from mutagen.mp4 import MP4
            tags = MP4(path)
            covr = tags.get("covr")
            if covr:
                return _data_uri(bytes(covr[0]))
    except Exception:
        pass
    return None


def _read_meta(path: str):
    """Return (title, author, cover_b64) for an audio file."""
    title = os.path.splitext(os.path.basename(path))[0]
    author = ""
    try:
        from mutagen import File as MFile
        m = MFile(path, easy=True)
        if m and m.tags:
            title = (m.tags.get("title") or [title])[0]
            author = (m.tags.get("artist") or [""])[0]
    except Exception:
        pass
    return title, author, _read_cover_b64(path)


def audio_durations(paths) -> list:
    """Duration (seconds) of each audio part, via mutagen; 0 on failure."""
    out = []
    for p in paths:
        try:
            import mutagen
            f = mutagen.File(p)
            out.append(float(f.info.length) if f and f.info else 0.0)
        except Exception:
            out.append(0.0)
    return out


def _ebook_title(path: str):
    """(title, author) for an ebook — from EPUB metadata when possible."""
    try:
        if path.lower().endswith(".epub"):
            from app.epub import epub_metadata
            m = epub_metadata(path)
            return (m.get("title") or Path(path).stem), m.get("author", "")
    except Exception:
        pass
    return Path(path).stem, ""


def scan_library(output_dir: str):
    """Scan output_dir for books. Single audio files = one book; sub-dirs with
    audio = a multi-part book. Audiobooks pick up a sibling ebook (epub/pdf);
    ebook-only files/folders become reader-only books."""
    books = []
    root = Path(output_dir)
    if not root.exists():
        return books

    for p in sorted(root.iterdir()):
        if p.is_file() and p.suffix.lower() in AUDIO_EXTS:
            title, author, cover = _read_meta(str(p))
            # sibling ebook with the same stem (e.g. "Book.epub" next to "Book.mp3")
            ebook = next((str(f) for f in p.parent.iterdir()
                          if f.is_file() and f.stem == p.stem
                          and f.suffix.lower() in EBOOK_EXTS), None)
            books.append({
                "title": title, "author": author, "cover": cover,
                "path": str(p), "parts": [str(p)],
                "ext": p.suffix.lstrip(".").lower(), "ebook": ebook,
            })
        elif p.is_file() and p.suffix.lower() in EBOOK_EXTS:
            title, author = _ebook_title(str(p))
            books.append({
                "title": title, "author": author, "cover": None,
                "path": str(p), "parts": [], "ext": p.suffix.lstrip(".").lower(),
                "ebook": str(p),
            })
        elif p.is_dir() and not p.name.startswith("."):
            parts = sorted(str(f) for f in p.iterdir()
                           if f.is_file() and f.suffix.lower() in AUDIO_EXTS)
            ebook = next((str(f) for f in sorted(p.iterdir())
                          if f.is_file() and f.suffix.lower() in EBOOK_EXTS), None)
            if parts:
                title, author, cover = _read_meta(parts[0])
                books.append({
                    "title": p.name or title, "author": author, "cover": cover,
                    "path": parts[0], "parts": parts,
                    "ext": Path(parts[0]).suffix.lstrip(".").lower(), "ebook": ebook,
                })
            elif ebook:
                title, author = _ebook_title(ebook)
                books.append({
                    "title": p.name or title, "author": author, "cover": None,
                    "path": ebook, "parts": [],
                    "ext": Path(ebook).suffix.lstrip(".").lower(), "ebook": ebook,
                })
    return books
