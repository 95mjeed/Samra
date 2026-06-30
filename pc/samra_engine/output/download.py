from samra_engine import AudiobookFile, Source, logging, Audiobook
from samra_engine.exceptions import UserNotAuthorized, NoFilesFound, DownloadError
from . import metadata, output, encryption

import os
import shutil
import time
from functools import partial
from typing import Any, Iterable, List, Optional, Sequence, Tuple, Union
from rich.progress import Progress, BarColumn, ProgressColumn, SpinnerColumn
from rich.prompt import Confirm
from multiprocessing.pool import ThreadPool
from pathlib import Path
from math import log10


DOWNLOAD_PROGRESS: List[Union[str, ProgressColumn]] = [
    SpinnerColumn(),
    "{task.description}",
    BarColumn(),
    "[progress.percentage]{task.percentage:>3.0f}%"
]


def download(audiobook: Audiobook, options):
    """
    Download contents of audiobook

    :param audiobook: Audiobook to download
    :param options: Cli options
    """
    try:
        output_dir = output.gen_output_location(
            options.output_template,
            audiobook.metadata,
            options.remove_chars
        )
        download_audiobook(audiobook, output_dir, options)
    except KeyboardInterrupt:
        logging.book_update("Stopped download")
        logging.book_update("Cleaning up files")
        if len(audiobook.files) == 1:
            filepath, filepath_tmp = create_filepath(audiobook, output_dir, 0)
            os.remove(filepath_tmp)
        else:
            shutil.rmtree(output_dir)


def download_audiobook(audiobook: Audiobook, output_dir: str, options):
    """Download, convert, combine, and add metadata to files from `Audiobook` object"""
    # Ebook-only books have no audio files — nothing to download here
    if not audiobook.files:
        logging.log(f"  [yellow]No audio files for [blue]{audiobook.title}[/] — ebook only.[/]")
        return
    # Check if file/dir exists and should be skipped
    if options.skip_downloaded:
        is_single_file = len(audiobook.files) == 1 or options.combine
        if is_single_file:
            if audiobook.files:
                current_format = audiobook.files[0].ext
                output_format = options.output_format or current_format
                output_path = f"{output_dir}.{output_format}"
                if os.path.exists(output_path):
                    logging.log(f"Skipping [blue]{audiobook.title}[/], file already exists.")
                    return
        elif os.path.isdir(output_dir):  # multiple files, check for directory
            logging.log(f"Skipping [blue]{audiobook.title}[/], directory already exists.")
            return

    # Downloading files
    filepaths = download_files_with_cli_output(audiobook, output_dir)
    # Converting files
    current_format, output_format = get_output_audio_format(options.output_format, filepaths)
    # Combine files
    if options.combine and len(filepaths) > 1:
        logging.book_update("Combining files")
        output_path = f"{output_dir}.{current_format}"
        output.combine_audiofiles(filepaths, output_dir, output_path)
        filepaths = [output_path]
    if current_format != output_format:
        logging.book_update("Converting files")
        filepaths = output.convert_output(filepaths, output_format)
    # Add metadata
    if len(filepaths) == 1:
        add_metadata_to_file(audiobook, filepaths[0], options)
    else:
        add_metadata_to_dir(audiobook, filepaths, output_dir, options)


def add_metadata_to_file(audiobook: Audiobook, filepath: str, options):
    """
    Embed metadata into a single file

    :param audiobook: Audiobook object. Stores metadata
    :param filepath: Filepath of output file
    :options: Cli options
    """
    # General metadata
    logging.book_update("Adding metadata")
    metadata.add_metadata(filepath, audiobook.metadata)
    if options.write_json_metadata:
        with open(f"{filepath}.json", "w") as f:
            f.write(audiobook.metadata.as_json())
    # Chapters
    if audiobook.chapters and not options.no_chapters:
        logging.book_update("Adding chapters")
        metadata.add_chapters(filepath, audiobook.chapters)
    # Cover
    if audiobook.cover:
        logging.book_update("Embedding cover")
        metadata.embed_cover(filepath, audiobook.cover)


def add_metadata_to_dir(audiobook: Audiobook, filepaths: Iterable[str], output_dir: str, options):
    """
    Add metadata to a directory with audio files

    :param audiobook: Audiobook object. Stores metadata
    :param filepaths: Iterable over filepaths of output files
    :param output_dir: Directory where files are stored
    :param optiosn: Cli options
    """
    logging.book_update("Adding metadata")
    for filepath in filepaths:
        metadata.add_metadata(filepath, audiobook.metadata)
    if options.write_json_metadata:
        metadata_file_path = os.path.join(output_dir, "metadata.json")
        with open(metadata_file_path, "w") as f:
            f.write(audiobook.metadata.as_json())
    if audiobook.cover:
        logging.book_update("Adding cover")
        cover_path = os.path.join(output_dir, f"cover.{audiobook.cover.extension}")
        with open(cover_path, "wb") as f:
            f.write(audiobook.cover.image)


def download_files_with_cli_output(audiobook: Audiobook, output_dir: str) -> List[str]:
    """
    Download `audiobook` with cli progress bar

    :param audiobook: Audiobook to download
    :param output_dir: Output directory where files are downloaded to
    :returns: A list of paths of the downloaded files
    """
    if len(audiobook.files) > 1:
        setup_download_dir(output_dir)
    else:
        parent = Path(output_dir).parent
        if not parent.exists():
            os.makedirs(parent)
    with logging.progress(DOWNLOAD_PROGRESS) as progress:
        task = progress.add_task(
            f"Downloading [blue]{audiobook.title}",
            total = len(audiobook.files)
        )
        update_progress = partial(progress.advance, task)
        filepaths = download_files(audiobook, output_dir, update_progress)
        # Make sure progress bar is at 100%
        remaining_progress: float = progress.tasks[0].remaining or 0
        update_progress(remaining_progress)
        # Return filenames of downloaded files
        return filepaths


def create_filepath(audiobook: Audiobook, output_dir: str, index: int) -> Tuple[str, str]:
    """
    Create output file path for file number `index` in `audibook`

    :param audiobook: Currently downloading audiobook
    :param output_dir: Directory where file should be stored
    :param index: Index in audiobooks list of files
    :returns: Filepath, Filepath_tmp
    """
    extension = audiobook.files[index].ext
    if len(audiobook.files) == 1:
        path = f"{output_dir}.{extension}"
    else:
        padded_index = str(index).zfill(int(log10(len(audiobook.files))))
        name = f"Part {padded_index}.{extension}"
        path = os.path.join(output_dir, name)
    path_tmp = f"{path}.tmp"
    return path, path_tmp


# How many times to retry a single part on a transient network error before
# giving up. Large multi-chapter audiobooks make dozens-to-hundreds of requests,
# so without retries a single dropped connection / timeout / rate-limit response
# would abort the whole book. Each attempt restarts that ONE part from scratch.
_MAX_PART_ATTEMPTS = 5
_PART_TIMEOUT = 60  # seconds for connect/read on each part request


def _download_file_once(audiobook, file, filepath_tmp, update_progress) -> None:
    """Fetch one part to the tmp file. Raises on any network/HTTP problem."""
    request = audiobook.session.get(
        file.url, headers=file.headers, stream=True, timeout=_PART_TIMEOUT
    )
    content_type: Optional[str] = request.headers.get("Content-type", None)

    invalid_content_type = file.expected_content_type and file.expected_content_type != content_type
    invalid_status_code = file.expected_status_code and file.expected_status_code != request.status_code
    if invalid_content_type or invalid_status_code:
        raise DownloadError(
            status_code=request.status_code,
            content_type=content_type,
            expected_status_code=file.expected_status_code,
            expected_content_type=file.expected_content_type,
            body=request.content,
            url=file.url,
        )

    try:
        total_filesize = int(request.headers.get("Content-length") or 0)
    except (TypeError, ValueError):
        total_filesize = 0

    # Download file to tmp file
    with open(filepath_tmp, "wb") as f:
        for chunk in request.iter_content(chunk_size=8192):
            if not chunk:
                continue
            f.write(chunk)
            if total_filesize:
                update_progress(len(chunk) / total_filesize)


def download_file(args: Tuple[Audiobook, str, int, Any]) -> str:
    # Prepare download
    audiobook, output_dir, index, update_progress = args
    file = audiobook.files[index]
    filepath, filepath_tmp = create_filepath(audiobook, output_dir, index)
    logging.debug(f"Starting downloading file: {file.url}")

    last_error: Optional[Exception] = None
    for attempt in range(1, _MAX_PART_ATTEMPTS + 1):
        try:
            _download_file_once(audiobook, file, filepath_tmp, update_progress)
            last_error = None
            break
        except Exception as e:  # noqa: BLE001 — retry transient network/HTTP errors
            last_error = e
            # Drop the partial tmp file so the retry starts clean.
            try:
                if os.path.exists(filepath_tmp):
                    os.remove(filepath_tmp)
            except OSError:
                pass
            if attempt < _MAX_PART_ATTEMPTS:
                # Exponential backoff (2,4,8,16s, capped) — eases transient drops
                # and Storytel rate-limiting without hammering the server.
                time.sleep(min(2 ** attempt, 16))
    if last_error is not None:
        raise last_error

    # Decrypt file if necessary
    if file.encryption_method:
        encryption.decrypt_file(filepath_tmp, file.encryption_method)
    # rename file after download is complete
    os.rename(filepath_tmp, filepath)
    # Return filepath
    return filepath


def download_files(audiobook: Audiobook, output_dir: str, update_progress) -> List[str]:
    """Download files from audiobook and return paths of the downloaded files"""
    filepaths = []
    # 8 concurrent connections (was 20): plenty of throughput while putting much
    # less strain on the server — fewer rate-limit / reset failures on large,
    # many-chapter books. Combined with per-part retries above, this is robust.
    workers = max(1, min(8, len(audiobook.files)))
    with ThreadPool(processes=workers) as pool:
        arguments = []
        for index in range(len(audiobook.files)):
            arguments.append((audiobook, output_dir, index, update_progress))
        for filepath in pool.imap(download_file, arguments):
            filepaths.append(filepath)
    return filepaths


def get_output_audio_format(option: Optional[str], files: Sequence[str]) -> Tuple[str, str]:
    """
    Get output format for files

    `option` is used if specified; else it's based on the file extensions
    :param option: User specified value
    :param files: Audio file names
    :returns: A tuple with current format and output format
    """
    current_format = os.path.splitext(files[0])[1][1:]
    if option:
        output_format = option
    else:
        output_format = current_format
    return current_format, output_format


def setup_download_dir(path: str) -> None:
    """
    Creates output folder for the audiobook.
    Will give a prompt if the folder already exists.

    :param path: Path of output folder
    :returns: Nothing
    """
    logging.book_update("Creating output dir")
    if os.path.isdir(path):
        answer = Confirm.ask(
            f"The folder '[blue]{path}[/blue]' already exists. Do you want to override it?"
        )
        if answer:
            shutil.rmtree(path)
        else:
            exit()
    os.makedirs(path)
