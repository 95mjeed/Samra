import samra_engine.sources as sources
from .logging import print_error_file, error
from typing import Optional

class SamraEngineException(Exception):
    error_description = "unknown"

    def __init__(self, error_description = None, **kwargs) -> None:
        if error_description:
            self.error_description = error_description
        self.data = kwargs

    def print(self) -> None:
        print_error_file(self.error_description, **self.data)

class DataNotPresent(SamraEngineException):
    error_description = "data_not_present"

class FailedCombining(SamraEngineException):
    error_description = "failed_combining"

class MissingDependency(SamraEngineException):
    error_description = "missing_dependency"

class NoFilesFound(SamraEngineException):
    error_description = "no_files_found"

class NoSourceFound(SamraEngineException):
    error_description = "no_source_found"

    def print(self):
        source_name_list = "\n".join([f" • {name}" for name in sources.get_source_names()])
        print_error_file(self.error_description, sources=source_name_list, **self.data)

class RequestError(SamraEngineException):
    error_description = "request_error"

class UserNotAuthorized(SamraEngineException):
    error_description = "user_not_authorized"

class CloudflareBlocked(SamraEngineException):
    error_description = "cloudflare_blocked"

class MissingBookAccess(SamraEngineException):
    error_description = "book_access"

class BookNotFound(SamraEngineException):
    error_description = "book_not_found"

class BookNotReleased(SamraEngineException):
    error_description = "book_not_released"

class BookHasNoAudiobook(SamraEngineException):
    error_description = "book_has_no_audiobook"

class ConfigNotFound(SamraEngineException):
    error_description = "config_not_found"

class GenericSamraEngineException(SamraEngineException):
    error_description: str = "generic"

    def __init__(self, heading: str, body: Optional[str] = None) -> None:
        self.data = {'heading': heading, 'body': body if body else ""}

class DownloadError(SamraEngineException):
    error_description: str = "download_error"
