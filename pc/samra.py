"""
Samra bridge — drives the bundled `samra_engine` engine from the Android UI.

The Kotlin layer calls these functions through Chaquopy. A `listener` object
(implementing com.samra.downloader.SamraListener) is passed in so the engine can
stream human-readable log lines and numeric progress back to the UI while a
download runs on a background thread.

Design notes:
- We re-implement the per-book download loop (instead of calling
  samra_engine.__main__) so we can (a) inject a real progress callback and
  (b) skip ffmpeg-only steps (combine / format conversion) when no ffmpeg
  binary is present, which is the case on a stock Android device.
- Plain downloads keep the source's native format (usually mp3) and need no
  native tools beyond what Chaquopy bundles (lxml, Pillow, pycryptodome).
"""

import json
import os
import shutil
import subprocess
import traceback
from types import SimpleNamespace

# --- ffmpeg routing -------------------------------------------------------
# The engine shells out to a bare "ffmpeg" command. On Android there is no
# ffmpeg on PATH, but we ship a static arm64 binary as libffmpeg.so inside the
# app's native library dir (the one place Android lets us exec a file). The
# Kotlin layer passes its absolute path via set_ffmpeg(); we then (a) rewrite
# subprocess calls whose argv[0] == "ffmpeg" to that path, and (b) make
# shutil.which("ffmpeg") report it so the engine's capability checks pass.

FFMPEG_PATH = None
_orig_run = subprocess.run
_orig_which = shutil.which


def _patched_run(args, *a, **kw):
    if FFMPEG_PATH and isinstance(args, (list, tuple)) and args and args[0] == "ffmpeg":
        args = [FFMPEG_PATH] + list(args[1:])
    return _orig_run(args, *a, **kw)


def _patched_which(cmd, *a, **kw):
    if FFMPEG_PATH and cmd == "ffmpeg":
        return FFMPEG_PATH
    return _orig_which(cmd, *a, **kw)


subprocess.run = _patched_run
shutil.which = _patched_which


def set_ffmpeg(path):
    """Register the bundled ffmpeg binary (called from Kotlin)."""
    global FFMPEG_PATH
    if path and os.path.exists(path):
        FFMPEG_PATH = path
        try:
            os.chmod(path, 0o755)
        except Exception:
            pass
    return bool(FFMPEG_PATH)

# App-private dir for the error log (set from Kotlin) — keeps it OFF public storage.
LOG_DIR = None

def set_log_dir(path):
    """Register an app-private dir for the error log (called from Kotlin)."""
    global LOG_DIR
    if path:
        LOG_DIR = path
    return bool(LOG_DIR)

# --- engine imports -------------------------------------------------------
from samra_engine import logging as adl_logging
from samra_engine.sources import find_compatible_source, get_source_names
from samra_engine.utils.audiobook import Audiobook, Series
from samra_engine.output import output as adl_output
from samra_engine.output import download as adl_download
from samra_engine.output import metadata as adl_metadata
from samra_engine.exceptions import SamraEngineException
from samra_engine.utils import read_asset_file

try:
    from rich.markup import render as _rich_render
except Exception:  # pragma: no cover
    _rich_render = None


# --- helpers --------------------------------------------------------------

def _plain(msg):
    """Strip rich console markup (e.g. [blue]X[/]) for the UI log."""
    if msg is None:
        return ""
    msg = str(msg)
    if _rich_render is not None:
        try:
            return _rich_render(msg).plain
        except Exception:
            pass
    # Fallback: crude tag removal
    out, depth = [], 0
    for ch in msg:
        if ch == "[":
            depth += 1
        elif ch == "]" and depth:
            depth -= 1
        elif depth == 0:
            out.append(ch)
    return "".join(out)


def _describe_exception(e):
    """Render a human-readable message for an SamraEngineException."""
    try:
        from samra_engine import sources as _sources
        data = dict(getattr(e, "data", {}) or {})
        name = getattr(e, "error_description", "generic")
        if name == "no_source_found" and "sources" not in data:
            data["sources"] = "\n".join(
                " - " + n for n in _sources.get_source_names())
        text = read_asset_file("assets/errors/%s.txt" % name)
        return _plain(text.format(**data)).strip()
    except Exception:
        return _plain(str(e) or e.__class__.__name__)


class _Listener:
    """Wraps the Kotlin listener so engine code can call simple Python funcs."""

    def __init__(self, kt):
        self.kt = kt

    def log(self, msg):
        try:
            self.kt.onLog(_plain(msg))
        except Exception:
            pass

    def progress(self, fraction):
        try:
            self.kt.onProgress(float(fraction))
        except Exception:
            pass

    def book(self, title, index, total):
        try:
            self.kt.onBook(_plain(title), int(index), int(total))
        except Exception:
            pass


def _install_logging_bridge(listener):
    """Forward samra_engine's logging calls to the UI listener."""
    adl_logging.debug_mode = False
    adl_logging.quiet_mode = False
    adl_logging.ffmpeg_output = False
    adl_logging.log = lambda msg: listener.log(msg)
    adl_logging.book_update = lambda msg: listener.log("  " + _plain(msg))
    adl_logging.error = lambda msg: listener.log(_plain(msg))
    adl_logging.debug = lambda msg, remove_styling=False: None


def _make_options(output_dir, output_template, output_format, combine, library):
    """Build an argparse-like options object the engine expects."""
    db_dir = os.path.join(output_dir, ".samra_db")
    template = os.path.join(output_dir, output_template or "{title}")
    return SimpleNamespace(
        urls=[],
        cookie_file=None,
        combine=bool(combine),
        output_template=template,
        remove_chars="",
        debug=False,
        quiet=False,
        print_output=False,
        cover=False,
        no_chapters=False,
        output_format=(output_format or None),
        ffmpeg_output=False,
        input_file=None,
        username=None,
        password=None,
        library=(library or None),
        skip_downloaded=False,
        database_directory=db_dir,
        write_json_metadata=False,
        config_location=None,
        download_ebook=False,
    )


def _ffmpeg_available():
    return shutil.which("ffmpeg") is not None


def _authenticate(source, url, username, password, cookie_path, listener):
    if cookie_path and source.supports_cookies and os.path.exists(cookie_path):
        listener.log("Loading cookies")
        source.load_cookie_file(cookie_path)
    if source.supports_login and not source.authenticated:
        listener.log("Logging in to " + source.name)
        login_data = {}
        for name in source.login_data:
            if name == "username":
                login_data[name] = username or ""
            elif name == "password":
                login_data[name] = password or ""
            else:
                login_data[name] = ""
        source.login(url, **login_data)


def _download_one(source, audiobook, options, listener):
    """Download a single Audiobook with live progress. Returns list of paths."""
    output_base = adl_output.gen_output_location(
        options.output_template, audiobook.metadata, options.remove_chars
    )
    if not audiobook.files:
        listener.log("No audio files for this title (ebook only) — skipping.")
        return []

    total_files = len(audiobook.files)
    state = {"acc": 0.0}

    def update_progress(advance):
        state["acc"] += float(advance)
        frac = state["acc"] / total_files if total_files else 0.0
        listener.progress(max(0.0, min(1.0, frac)))

    # Ensure output directories exist
    if total_files > 1:
        os.makedirs(output_base, exist_ok=True)
    else:
        parent = os.path.dirname(output_base)
        if parent and not os.path.exists(parent):
            os.makedirs(parent, exist_ok=True)

    listener.log("Downloading audio (%d file%s)" % (
        total_files, "" if total_files == 1 else "s"))
    filepaths = adl_download.download_files(audiobook, output_base, update_progress)
    listener.progress(1.0)

    # Format handling (combine / convert) — needs ffmpeg
    current_format, output_format = adl_download.get_output_audio_format(
        options.output_format, filepaths
    )
    if options.combine and len(filepaths) > 1:
        if _ffmpeg_available():
            listener.log("Combining files")
            output_path = "%s.%s" % (output_base, current_format)
            adl_output.combine_audiofiles(filepaths, output_base, output_path)
            filepaths = [output_path]
        else:
            listener.log("ffmpeg not available — keeping separate part files.")
    if current_format != output_format:
        if _ffmpeg_available():
            listener.log("Converting to " + output_format)
            filepaths = adl_output.convert_output(filepaths, output_format)
        else:
            listener.log("ffmpeg not available — keeping ." + current_format
                         + " (requested ." + output_format + ").")

    # Embed all metadata + cover + chapters directly into the audio file(s)
    # so each book is self-describing (no separate sidecar / cover files).
    _embed_metadata(filepaths, audiobook, options, getattr(source, "name", ""), listener)

    return filepaths


def _embed_metadata(filepaths, audiobook, options, source_name, listener):
    """Write tags, cover art, chapters and the source name into the file(s)."""
    try:
        if len(filepaths) == 1:
            # add_metadata_to_file embeds tags + chapters + cover
            adl_download.add_metadata_to_file(audiobook, filepaths[0], options)
        else:
            # Multi-part: embed tags + cover into every part (no separate cover)
            for fp in filepaths:
                adl_metadata.add_metadata(fp, audiobook.metadata)
                if getattr(audiobook, "cover", None):
                    adl_metadata.embed_cover(fp, audiobook.cover)
        if audiobook.chapters:
            listener.log("Embedded %d chapter mark(s)" % len(audiobook.chapters))
    except Exception as e:  # noqa: BLE001
        listener.log("Metadata step skipped: " + str(e))

    # Tag the source name into the file so the app can show it
    if source_name:
        for fp in filepaths:
            try:
                _tag_source(fp, source_name)
            except Exception:  # noqa: BLE001
                pass


def _tag_source(filepath, source_name):
    """Store the source name as a freeform/TXXX tag the app reads back."""
    low = filepath.lower()
    if low.endswith(".mp3"):
        from mutagen.id3 import ID3, TXXX
        try:
            tags = ID3(filepath)
        except Exception:  # noqa: BLE001
            return
        tags.add(TXXX(encoding=3, desc="Source", text=[source_name]))
        tags.save()
    elif any(low.endswith(e) for e in (".m4b", ".m4a", ".mp4", ".m4p")):
        from mutagen.mp4 import MP4
        tags = MP4(filepath)
        tags["----:com.apple.iTunes:Source"] = [source_name.encode("utf-8")]
        tags.save()


# --- public API (called from Kotlin) --------------------------------------

def list_sources():
    """Return a JSON array of supported source names."""
    try:
        return json.dumps(list(get_source_names()))
    except Exception:
        return json.dumps([])


# engine source.name -> our source id (where they differ)
_SOURCE_ALIAS = {
    "audiobooks.com": "abcom",
    "audiobooksdotcom": "abcom",
    "yourcloudlibrary": "cloudlib",
}


def _select_creds(source, creds_json, username, password, cookie_path):
    """Pick stored credentials matching the detected source, if any."""
    u, p, c = username, password, cookie_path
    if creds_json:
        try:
            m = json.loads(creds_json)
            name = source.name
            rec = m.get(name) or m.get(_SOURCE_ALIAS.get(name, name)) \
                or m.get(name.replace(".", ""))
            if rec:
                u = rec.get("u") or u
                p = rec.get("p") or p
                ck = rec.get("c")
                if ck:
                    c = ck
        except Exception:
            pass
    return u, p, c


def _log_error(output_dir, url, msg, trace=""):
    """Append a failure to <output_dir>/.samra_errors.log so it can be inspected
    later (the in-app console is in-memory only)."""
    try:
        import time as _t
        # Write to the app-private dir (LOG_DIR) so the log + book URLs/tracebacks are
        # NOT exposed on public shared storage. Fall back to output_dir only if unset.
        base = LOG_DIR or output_dir
        path = os.path.join(base, ".samra_errors.log")
        with open(path, "a", encoding="utf-8") as f:
            f.write("==== %s ====\nURL:   %s\nERROR: %s\n" % (
                _t.strftime("%Y-%m-%d %H:%M:%S"), url, msg))
            if trace:
                f.write(trace + "\n")
            f.write("\n")
    except Exception:
        pass


def run_download(url, username, password, cookie_path, output_dir,
                 output_format, combine, library, ffmpeg_path, creds_json, kt_listener):
    """
    Download the audiobook(s) at `url` into `output_dir`.

    Returns a JSON string: {"ok": bool, "files": [...], "error": str|None}
    """
    listener = _Listener(kt_listener)
    _install_logging_bridge(listener)
    set_ffmpeg(ffmpeg_path)

    all_files = []
    prev_cwd = None
    try:
        os.makedirs(output_dir, exist_ok=True)
        # Some engine steps write temp files relative to cwd; keep them in the
        # (writable) output dir.
        try:
            prev_cwd = os.getcwd()
            os.chdir(output_dir)
        except Exception:
            prev_cwd = None
        options = _make_options(output_dir, "{title}", output_format, combine, library)

        url = url.strip()
        if not (url.startswith("http://") or url.startswith("https://")):
            url = "https://" + url

        listener.log("Finding compatible source")
        source_class = find_compatible_source(url)
        source = source_class(options)
        listener.log("Source: " + source.name)

        if source.requires_authentication and not source.authenticated:
            u, p, c = _select_creds(source, creds_json, username, password, cookie_path)
            _authenticate(source, url, u, p, c, listener)

        listener.log("Fetching book details")
        result = source.download(url)

        if isinstance(result, Series):
            books = result.books
            total = len(books)
            listener.log("Series '%s' — %d book(s)" % (_plain(result.title), total))
            for i, book in enumerate(books):
                try:
                    if isinstance(book, Audiobook):
                        audiobook = book
                    else:
                        audiobook = source.download_from_id(book.id)
                    listener.book(audiobook.title, i + 1, total)
                    listener.log("[%d/%d] %s" % (i + 1, total, _plain(audiobook.title)))
                    all_files += _download_one(source, audiobook, options, listener)
                    source.on_download_complete(audiobook)
                except SamraEngineException as e:
                    msg = _describe_exception(e)
                    listener.log("Skipped a book: " + msg)
                    _log_error(output_dir, url, "skipped book: " + msg)
                    continue
        elif isinstance(result, Audiobook):
            listener.book(result.title, 1, 1)
            listener.log("Downloading: " + _plain(result.title))
            all_files += _download_one(source, result, options, listener)
            source.on_download_complete(result)
        else:
            raise RuntimeError("Unexpected result type from source")

        listener.log("Done. %d file(s) saved." % len(all_files))
        return json.dumps({"ok": True, "files": all_files, "error": None})

    except SamraEngineException as e:
        msg = _describe_exception(e)
        listener.log("Error: " + msg)
        _log_error(output_dir, url, msg)
        return json.dumps({"ok": False, "files": all_files, "error": msg})
    except Exception as e:  # noqa: BLE001
        tb = traceback.format_exc()
        listener.log("Error: " + str(e))
        _log_error(output_dir, url, str(e), tb)
        return json.dumps({"ok": False, "files": all_files,
                           "error": str(e), "trace": tb})
    finally:
        if prev_cwd:
            try:
                os.chdir(prev_cwd)
            except Exception:
                pass
