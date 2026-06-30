from samra_engine import logging, AudiobookMetadata
from samra_engine.exceptions import FailedCombining

import os
import shutil
import platform
import subprocess
from typing import Sequence, Mapping

LOCATION_DEFAULTS = {
    'album': 'NA',
    'artist': 'NA',
}

COMBINE_CHUNK_SIZE = 500

def gen_output_filename(booktitle: str, file: Mapping[str, str], template: str) -> str:
    """Generates an output filename based on different attributes of the
    file"""
    arguments = {**file, **{"booktitle": booktitle}}
    filename = template.format(**arguments)
    return _fix_output(filename)


def combine_audiofiles(filepaths: Sequence[str], tmp_dir: str, output_path: str):
    """
    Combines the given audiofiles in `path` into a new file

    :param filepaths: Paths to audio files
    :param tmp_dir: Temporary directory with audio files
    :param output_path: Path of combined audio files
    """
    output_extension = get_extension(output_path)
    tmp_input = os.path.join(tmp_dir, f"input_file.{output_extension}")
    tmp_output = os.path.join(tmp_dir, f"output_file.{output_extension}")
    shutil.move(filepaths[0], tmp_input)
    for i in range(1, len(filepaths), COMBINE_CHUNK_SIZE):
        inputs = "|".join(filepaths[i:i+COMBINE_CHUNK_SIZE])
        subprocess.run(
            [
                "ffmpeg", "-y", "-nostdin",
                "-i", f"concat:{tmp_input}|{inputs}",
                "-safe", "0",
                "-codec", "copy",
                tmp_output
            ],
            capture_output=not logging.ffmpeg_output,
        )
        os.remove(tmp_input)
        shutil.move(tmp_output, tmp_input)
    shutil.move(tmp_input, output_path)
    # A missing OR empty output means ffmpeg failed — treat as a failed combine
    # so we never leave a broken 0-byte file behind.
    if not os.path.exists(output_path) or os.path.getsize(output_path) == 0:
        raise FailedCombining
    shutil.rmtree(tmp_dir)


def get_extension(path: str) -> str:
    """
    Get extension from path

    :param path: Path to get extension from
    :returns: Extension of path
    """
    return os.path.splitext(path)[1][1:]


def can_copy_codec(input_format: str, output_format: str) -> bool:
    """
    Checks whether the codec can be copies to the new output

    :param input_format: Input file filetype
    :param output_format: Output file filetype
    :returns: True if the codec can be copied
    """
    # Container rewraps that need no re-encode (stream copy is safe):
    #   mp3 → m4b / m4a : MP4 containers accept MP3 audio streams directly.
    #                      This avoids a full MP3→AAC re-encode (which is slow
    #                      and double-lossy). Audiobookshelf, VLC, and most
    #                      players handle MP3-in-M4B without issues.
    #   mp3 → mp4 / m4r  : same reasoning.
    _MP3_TO_MP4 = {
        ("mp3", "m4b"),
        ("mp3", "m4a"),
        ("mp3", "m4p"),
        ("mp3", "m4r"),
        ("mp3", "mp4"),
    }
    if (input_format.lstrip("."), output_format.lstrip(".")) in _MP3_TO_MP4:
        return True
    return output_format == "mkv" \
        or output_format == "mka" \
        or (input_format == "ts" and output_format == "mp3")


def convert_output(filenames: Sequence[str], output_format: str):
    """Converts a list of audio files into another format and return new
    files"""
    new_paths = []
    for old_path in filenames:
        path_without_ext, old_ext = os.path.splitext(old_path)
        new_path = f"{path_without_ext}.{output_format}"
        # old_ext from splitext keeps the dot (".mp3"); compare without it.
        if output_format == old_ext.lstrip("."):
            new_paths.append(old_path)
            continue

        # Convert into a TEMP file first, then atomically move it into place only
        # if ffmpeg actually succeeded. This guarantees we never leave a 0-byte /
        # half-written output (e.g. when the target already existed from a
        # duplicate download, or ffmpeg failed), and never lose the original.
        tmp_out = f"{new_path}.converting"
        if os.path.exists(tmp_out):
            try:
                os.remove(tmp_out)
            except OSError:
                pass
        if can_copy_codec(old_ext, output_format):
            # When stream-copying mp3 into an m4b/m4a, the extension auto-selects
            # ffmpeg's 'ipod' muxer, which REJECTS mp3 streams. Force the generic
            # mp4 muxer so the copy succeeds.
            extra = (
                ["-f", "mp4"]
                if output_format.lstrip(".") in ("m4b", "m4a", "m4p", "m4r")
                else []
            )
            result = subprocess.run(
                ["ffmpeg", "-y", "-nostdin", "-i", old_path, "-codec", "copy"] + extra + [tmp_out],
                capture_output=not logging.ffmpeg_output
            )
        else:
            result = subprocess.run(
                ["ffmpeg", "-y", "-nostdin", "-i", old_path, tmp_out],
                capture_output=not logging.ffmpeg_output
            )

        ok = (getattr(result, "returncode", 1) == 0
              and os.path.exists(tmp_out) and os.path.getsize(tmp_out) > 0)
        if ok:
            os.replace(tmp_out, new_path)           # atomic overwrite
            if os.path.abspath(old_path) != os.path.abspath(new_path):
                try:
                    os.remove(old_path)
                except OSError:
                    pass
            new_paths.append(new_path)
        else:
            # Failed conversion — drop the broken temp and keep the original so
            # the download is never lost or left as a 0-byte file.
            if os.path.exists(tmp_out):
                try:
                    os.remove(tmp_out)
                except OSError:
                    pass
            logging.book_update("Conversion failed — keeping original format")
            new_paths.append(old_path)
    return new_paths

def get_max_name_length() -> int:
    """
    Get the max length for file names supported by the OS

    :returns: max length for file names
    """
    try:
        # should work on Linux/MacOS
        return os.pathconf(".", "PC_NAME_MAX")
    except:
        try:
            # Windows
            from ctypes.wintypes import MAX_PATH
            return MAX_PATH
        except:
            # default
            return 255

def gen_output_location(template: str, metadata: AudiobookMetadata, remove_chars: str) -> str:
    """
    Generates the location of the output based on attributes of the audiobook.

    :param template: Python string template audiobook metadata is put into
    :param metadata: Audiobook metadata,
    :param remove_chars: List of characters to be removed from the final path
    :returns: `template` with metadata inserted
    """
    max_name_length = get_max_name_length()

    if metadata is None:
        metadata = {}
    title = _fix_output(metadata.title)
    title_bytes = title.encode('utf-8')
    title_len = len(title_bytes)
    ext_len = 9 # extra length needed for file extensions: len('.mp3.json')
    if title_len > max_name_length - ext_len:
        title = title_bytes[0:max_name_length-ext_len].decode('utf-8', errors='ignore')
        logging.log(f"title to long, using [blue]{title}[/blue] as filename base")
    metadata_dict = {**LOCATION_DEFAULTS, **metadata.all_properties_dict()}
    metadata_dict['title'] = title
    formatted = template.format(**metadata_dict)
    formatted = _remove_chars(formatted, remove_chars)
    return formatted


def _fix_output(title: str) -> str:
    """Returns title without characters the filesystem can't handle.

    Android shared storage (FAT/exFAT, like the SD card / Download folder) forbids
    \\ / : * ? " < > | in filenames — the SAME set as Windows. The old code only
    stripped these on Windows, so titles with a colon (e.g. «الحرب والسلم: الجزء
    الأول») produced an illegal path and failed with "Operation not permitted".
    So ALWAYS sanitize, regardless of platform.
    """
    # Strip control characters (NUL and other C0) — an embedded NUL in a server-supplied
    # title otherwise crashes the file/path operation (one-download DoS).
    title = "".join(ch for ch in title if ch >= " ")
    # Keep readability: turn separators into " - " instead of deleting them.
    title = title.replace("/", "-").replace("\\", "-").replace(":", " -")
    title = _remove_chars(title, '*?"<>|')
    title = " ".join(title.split())   # collapse doubled spaces from replacements
    return title.strip(" .")          # FAT dislikes leading/trailing space or dot


def _remove_chars(s: str, chars: str) -> str:
    """Removes `chars` from `s`"""
    for i in chars:
        s = s.replace(i, "")
    return s
