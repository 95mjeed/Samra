from requests.models import Response
from .source import Source
from samra_engine import (
    AudiobookFile,
    Chapter,
    logging,
    AudiobookMetadata,
    Cover,
    Audiobook,
    Series,
    BookId,
    Result,
)
from samra_engine.exceptions import (
    GenericSamraEngineException,
    UserNotAuthorized,
    CloudflareBlocked,
    BookNotFound,
    BookHasNoAudiobook,
    BookNotReleased,
    DataNotPresent,
)
from samra_engine.output import gen_output_location
from lxml import etree as ET
from Crypto.Cipher import AES
import zipfile
from Crypto.Util.Padding import pad
from typing import Any, List, Dict, Optional, Union
from urllib3.util import parse_url
from urllib.parse import urlunparse, parse_qs
from datetime import datetime, date
import pycountry
import json
import re
import os
import uuid

# fmt: off
metadata_corrections: Dict[str, Dict[str, Any]] = {
    "books": {
        "1623721": { "title": "Bibi & Tina: Schatten über dem Martinshof", "release_date": date(2010,3,12) },
        "1623873": { "title": "Bibi & Tina: Die ungarischen Reiter", "release_date": date(2010,9,10) },
        "1623776": { "title": "Bibi & Tina: Der wilde Hengst", "release_date": date(2009,11,20) },
        "1623780": { "title": "Bibi & Tina: Die geheimnisvolle Köchin", "release_date": date(2012,6,8) },
        "1623767": { "title": "Bibi & Tina: Der Tiger von Rotenbrunn", "release_date": date(2013,9,6) },
        "1623757": { "title": "Bibi & Tina: Die falschen Weihnachtsmänner", "release_date": date(2013,11,1) },
        "1623860": { "title": "Bibi & Tina: Indianerpferde in Gefahr", "release_date": date(2014,9,5) },
        "1623775": { "title": "Bibi & Tina: Der weiße Mustang", "release_date": date(2015,9,4) },
        "1623856": { "title": "Bibi & Tina: Holger verliebt sich", "release_date": date(2016,9,9) },
        "1623760": { "title": "Bibi & Tina: Das Fohlen im Schnee", "release_date": date(2017,9,8) },
        "1623855": { "title": "Bibi & Tina: Ein heißer Sommer", "release_date": date(2018,7,6) },
        "1623857": { "title": "Bibi & Tina: Im Land der weißen Pferde", "release_date": date(2019,9,6) },
        "1048495": { "title": "Bibi & Tina: Der mysteriöse Fremde", "description": "Graf Falko von Falkenstein ist verzweifelt! Er leidet unter Schlaflosigkeit und bittet schließlich einen Wunderheiler um Hilfe. Der mysteriöse Fremde heilt nicht nur den Grafen, sondern wickelt sogar Frau Martin um den Finger. Tina ist gar nicht begeistert und auch Bibi misstraut dem Mann. Als die Freundinnen und Alex versuchen, dem Geheimnis des Heilers auf die Spur zu kommen, überschlagen sich die Ereignisse und die Kinder geraten in Gefahr.", "release_date": date(2020,10,23) }, # MP3 contains the audio twice in a row (second one starts at 2:34:52)
        "1397689": { "title": "Bibi & Tina: Ein Monster im Wald", "release_date": date(2021,10,22) },

        "1615235": { "title": "Bibi Blocksberg - Hörbuch: Im Tal der wilden Hexen", "release_date": date(2010,3,20) },
        "1615295": { "title": "Bibi Blocksberg - Das verhexte Wunschhaus", "release_date": date(2011,3,4) },
        "1615294": { "title": "Bibi Blocksberg - Die Gewitterhexe", "release_date": date(2012,10,19) },
        "1615236": { "title": "Bibi Blocksberg - Zickia-Alarm!", "release_date": date(2013,6,7) },
        "1615182": { "title": "Bibi Blocksberg - Das verhexte Schwein", "release_date": date(2013,10,11) },
        "1615288": { "title": "Bibi Blocksberg - Bibi total verknallt!", "release_date": date(2014,6,6) },
        "1615205": { "title": "Bibi Blocksberg - Hexkraft gesucht!", "release_date": date(2014,10,10) },
        "1615204": { "title": "Bibi Blocksberg - Wo ist Moni?", "release_date": date(2015,6,12) },
        "1615203": { "title": "Bibi Blocksberg - Gustav, der Hexendrache", "release_date": date(2015,10,9) },
        "1615175": { "title": "Bibi Blocksberg - Abenteuer Indien!", "release_date": date(2017,10,13) },
        "1615201": { "title": "Bibi Blocksberg - Die Schule ist weg!", "release_date": date(2018,10,12) },
        "1022245": { "title": "Bibi Blocksberg - Bibi und Herr Fu", "release_date": date(2020,9,18) },

        "522762": { "title": "Alvin und die Chipmunks: Der Katzenfluch" },

        "1260956": { "title": "Fast and Furious Spy Racer: Folge 1" },

        "1878866": { "title": "Ghostforce: Folge 1" },
        "1878880": { "title": "Ghostforce: Folge 2" },
        "2642089": { "title": "Ghostforce: Folge 3" },
        "2642148": { "title": "Ghostforce: Folge 4" },

        "1168061": { "title": "Leo Da Vinci: Folge 1", "series": "Leo Da Vinci", "series_order": 1 },
        "1176396": { "title": "Leo Da Vinci: Folge 2", "series": "Leo Da Vinci", "series_order": 2 },
        "1178721": { "title": "Leo Da Vinci: Folge 3", "series": "Leo Da Vinci", "series_order": 3 },
        "1176422": { "title": "Leo Da Vinci: Folge 4", "series": "Leo Da Vinci", "series_order": 4 },
        "1176424": { "title": "Leo Da Vinci: Folge 5", "series": "Leo Da Vinci", "series_order": 5 },
        "1176462": { "title": "Leo Da Vinci: Folge 6", "series": "Leo Da Vinci", "series_order": 6 },
        "1262342": { "title": "Leo Da Vinci: Folge 7", "series": "Leo Da Vinci", "series_order": 7 },
        "1263433": { "title": "Leo Da Vinci: Folge 8", "series": "Leo Da Vinci", "series_order": 8 },
        "1263421": { "title": "Leo Da Vinci: Folge 9", "series": "Leo Da Vinci", "series_order": 9 },
        "1309115": { "title": "Leo Da Vinci: Folge 10", "series": "Leo Da Vinci", "series_order": 10 },
        "1320400": { "title": "Leo Da Vinci: Folge 11", "series": "Leo Da Vinci", "series_order": 11 },
        "1328193": { "title": "Leo Da Vinci: Folge 12", "series": "Leo Da Vinci", "series_order": 12 },
    }
}
# fmt: on


# path data of the headphone icon on the website used to identify audiobooks
svg_headphone_path = "M8.25 12.371h-.625c-1.38 0-2.5 1.121-2.5 2.505v3.12a2.503 2.503 0 0 0 2.5 2.504h.625c.69 0 1.25-.56 1.25-1.252v-5.627c0-.691-.559-1.25-1.25-1.25Zm-.625 6.254a.628.628 0 0 1-.625-.63v-3.12c0-.347.28-.63.625-.63v4.38ZM12 3C6.41 3 2.178 7.652 2 13v4.375c0 .346.28.625.625.625h.625a.626.626 0 0 0 .625-.627V13c0-4.48 3.646-8.117 8.125-8.117 4.48 0 8.125 3.637 8.125 8.117v4.371c-.035.348.281.629.625.629l.625.001c.346 0 .625-.28.625-.625v-4.411C21.82 7.652 17.59 3 12 3Zm4.375 9.371h-.625c-.69 0-1.25.56-1.25 1.252v5.625c0 .692.56 1.252 1.25 1.252h.625c1.38 0 2.5-1.121 2.5-2.505v-3.12a2.503 2.503 0 0 0-2.5-2.504ZM17 17.996a.628.628 0 0 1-.625.629v-4.379c.345 0 .625.283.625.63v3.12Z"


class StorytelSource(Source):
    match = [
        r"https?://(?:www.)?(?:storytel|mofibo).com/(?P<language>\w+)(?:/(?P<language2>\w+))?/(?P<list_type>(?:books|series|authors|narrators|publishers|categories))/.+",
    ]
    names = ["Storytel", "Mofibo"]
    _authentication_methods = [
        "login",
    ]
    _download_counter = 0
    create_storage_dir = True

    def __init__(self, options) -> None:
        super().__init__(options)
        self.options = options  # kept for resolving the ebook output path
        self._sst = ""  # SingleSignToken used for ebook downloads
        self.database_directory_books = os.path.join(self.database_directory, "books")
        self.database_directory_playback_metadata = os.path.join(
            self.database_directory, "playback-metadata"
        )
        self.database_directory_lists = os.path.join(self.database_directory, "lists")
        os.makedirs(self.database_directory_books, exist_ok=True)
        os.makedirs(self.database_directory_playback_metadata, exist_ok=True)
        os.makedirs(self.database_directory_lists, exist_ok=True)

    def _get_book_path(self, consumableId: str) -> str:
        return os.path.join(self.database_directory_books, f"{consumableId}.json")

    def _get_playback_metadata_path(self, consumableId: str) -> str:
        return os.path.join(
            self.database_directory_playback_metadata, f"{consumableId}.json"
        )

    def _get_lists_path(self, list_name: str, languages: str, formats: str) -> str:
        return os.path.join(
            self.database_directory_lists, f"{list_name}_{languages}_{formats}.json"
        )

    def _skip_download_check(self, book_id: str) -> bool:
        if self.skip_downloaded:
            book_path = self._get_book_path(book_id)
            return os.path.exists(book_path)
        else:
            return False

    @staticmethod
    def encrypt_password(password: str) -> str:
        """
        Encrypt password with predefined keys.
        This encrypted password is used for login.

        :param password: User defined password
        :returns: Encrypted password
        """
        # Storytel's login endpoint expects the password AES-CBC encrypted with
        # these fixed protocol constants (public client-side values, no secrecy).
        key = b"VQZBJ6TD8M9WBUWT"
        iv = b"joiwef08u23j341a"
        msg = pad(password.encode(), AES.block_size)
        cipher = AES.new(key, AES.MODE_CBC, iv)
        cipher_text = cipher.encrypt(msg)
        return cipher_text.hex()

    def check_cloudflare_blocked(self, response: Response) -> None:
        if response.status_code == 403:
            error_str = "<title>Attention Required! | Cloudflare</title>"
            if error_str in response.text:
                raise CloudflareBlocked

    def _login(self, url: str, username: str, password: str) -> None:
        self._url = url
        self._username = username
        self._password = self.encrypt_password(password)
        self._session.headers.update(
            {
                "User-Agent": "Storytel/24.22 (Android 14; Google Pixel 8 Pro) Release/2288629",
            }
        )
        self._do_login()

    def _do_login(self) -> None:
        # Generate a new UUID for each request
        generated_device_id = str(uuid.uuid4())

        resp = self._session.post(
            f"https://www.storytel.com/api/login.action?m=1&token=guestsv&userid=-1&version=24.22"
            f"&terminal=android&locale=sv&deviceId={generated_device_id}&kidsMode=false",

            data={
                "uid": self._username,
                "pwd": self._password,
            },
            headers={"content-type": "application/x-www-form-urlencoded"},
        )

        if resp.status_code != 200:
            if resp.status_code == 403:
                self.check_cloudflare_blocked(resp)
            raise UserNotAuthorized

        user_data = resp.json()
        jwt = user_data["accountInfo"]["jwt"]
        self._sst = user_data["accountInfo"].get("singleSignToken", "")
        self._language = user_data["accountInfo"]["lang"]
        self._session.headers.update({"authorization": f"Bearer {jwt}"})

    def _relogin_check(self) -> None:
        """
        There's a ratelimit for the MP3 download, if triggered, it will invalidate all sessions and you'll get an email about suspicios activities on your account
        To avoid the rate limtier we regularly re-login to get a new session token (which seems to byepass the rate limiter)
        """
        if self._download_counter > 0 and self._download_counter % 10 == 0:
            logging.debug("refreshing login")
            self._do_login()

    @staticmethod
    def _clean_share_url(url: str) -> str:
        """remove query string/fragment from url"""
        return url.split("?")[0]

    def download_from_id(self, book_id: str) -> Audiobook:
        self._relogin_check()
        audiobook = self.download_book_from_book_id(book_id)
        return audiobook

    def download(self, url: str) -> Result:
        self._relogin_check()

        if m := re.match(self.match[0], url):
            language, language2, list_type = m.groups()
            logging.debug(f"download: {url=}, {list_type=}, {language=}, {language2=}")
            # individual books
            if list_type == "books":
                return self.download_book_from_url(url)
            # use API when possible
            elif list_type in ("series", "authors", "narrators"):
                return self.download_lists_api(url, list_type, language)
            # some lists are not avaialble via the API, use website scrapting
            else:
                return self.download_books_from_website(url)
        raise BookNotFound

    def download_lists_api(
        self,
        url: str,
        list_type: str,
        language: str,
    ) -> Series[str]:
        list_id: str = self.get_id_from_url(url)
        list_details = self.download_list_books(list_id, list_type, language)

        books: List[Union[BookId[str], Audiobook]] = []
        for item in list_details["items"]:
            abook_formats = [
                format for format in item["formats"] if format["type"] == "abook"
            ]
            if (
                len(abook_formats) > 0
                and abook_formats[0]["isReleased"]
                and not self._skip_download_check(item["id"])
            ):
                book_id = BookId(item["id"])
                books.append(book_id)

        return Series(
            title=list_details["title"],
            books=books,
        )

    def download_book_from_book_id(
        self,
        consumableId: str,
    ) -> Audiobook:
        book_details = self.download_book_details(consumableId)
        metadata = self.get_metadata(book_details)

        # Ebook-only: skip the audiobook asset endpoint (returns 410) and
        # return a file-less Audiobook so on_download_complete can download
        # the EPUB via the --ebook path.
        has_abook = any(
            f.get("type") == "abook"
            for f in book_details.get("formats", [])
        )
        if not has_abook:
            cover = self.download_cover(book_details)
            return Audiobook(
                session=self._session,
                files=[],
                metadata=metadata,
                cover=cover,
                chapters=[],
                source_data=book_details,
            )

        files = self.get_files(book_details)
        cover = self.download_cover(book_details)
        chapters = self.get_chapters(book_details)
        self._update_metadata(consumableId, book_details, metadata, files)

        return Audiobook(
            session=self._session,
            files=files,
            metadata=metadata,
            cover=cover,
            chapters=chapters,
            source_data=book_details,
        )

    def download_book_from_url(self, url: str) -> Audiobook:
        consumableId = self.get_id_from_url(url)
        return self.download_book_from_book_id(consumableId)

    @staticmethod
    def get_id_from_url(url: str) -> str:
        """
        Find book id in url

        :param url: Url to book
        :returns: Id of book from url
        """
        parsed = parse_url(url)
        if parsed.path is None:
            raise DataNotPresent
        return parsed.path.split("-")[-1]

    @staticmethod
    def _update_metadata(
        consumableId: str,
        book_details: Dict[str, Any],
        metadata: AudiobookMetadata,
        files: List[AudiobookFile],
    ) -> None:
        """
        update metadata once all data is available
        """
        # The ISBN is only available from the download link
        parsed = parse_url(files[0].url)
        q = parse_qs(parsed.query)
        if "isbn" in q:
            isbn = q["isbn"][0]
            book_details["_download_url_isbn"] = isbn
            metadata.isbn = isbn
        if consumableId in metadata_corrections["books"]:
            corrections = metadata_corrections["books"][consumableId]
            for key, value in corrections.items():
                logging.log(
                    f"overriding metadata [yellow]{key}[/] from [blue]{getattr(metadata, key)}[/] to [magenta]{value}[/]"
                )
                setattr(metadata, key, value)

    def download_bookshelf(self) -> Dict[str, Any]:
        """Download bookshelf data"""
        resp = self._session.post(
            "https://api.storytel.net/libraries/bookshelf",
            json={"items": []},
            headers={"content-type": "application/x-www-form-urlencoded"},
        )
        data: Dict[str, Any] = resp.json()

        bookshelf_path = os.path.join(self.database_directory_lists, f"bookshelf.json")
        with open(bookshelf_path, "w") as json_file:
            json_data = json.dumps(data, indent=2)
            json_file.write(json_data)

        return data

    def download_books_from_website(self, url: str) -> Series[str]:
        """Download series details

        :param formats: comma serapted list of formats (abook,ebook,podcast)
        :param languages: comma seperated list of languages (en,de,tr,ar,ru,pl,it,es,sv,fr,nl)
        """
        title = self.find_elems_in_page(url, "h1")[-1].text
        items = self.find_elems_in_page(url, 'a[href*="/books/"]')
        books: List[Union[BookId[str], Audiobook]] = []
        for item in items:
            href: str = item.get("href")
            # check for headphone icon to filter out non audiobooks
            svg_headphone_element = item.cssselect(
                f"svg > path[d='{svg_headphone_path}']"
            )
            if len(svg_headphone_element) == 0:
                logging.debug(f"skipping {href} (has no audiobook)")
                continue

            consumableId = self.get_id_from_url(href)
            if not self._skip_download_check(consumableId):
                book_id = BookId(consumableId)
                books.append(book_id)

        return Series(
            title=title,
            books=books,
        )

    def download_list_books(
        self,
        list_id: str,
        list_type: str,
        languages: str,
        formats: str = "abook",
    ) -> Dict[str, Any]:
        """Download details about book list

        :param formats: comma serapted list of formats (abook,ebook,podcast)
        :param languages: comma seperated list of languages (en,de,tr,ar,ru,pl,it,es,sv,fr,nl)
        """
        nextPageToken = 0

        # API returns only 10 items per request
        # if the nextPageToken
        result: Dict[str, Any] = {"nextPageToken": False}

        while result["nextPageToken"] != None:
            params: dict[str, str] = {
                "includeListDetails": "true",  # include listMetadata,filterOptions,sortOption sections
                "includeFormats": formats,
                "includeLanguages": languages,
                "kidsMode": "false",
            }
            if result["nextPageToken"]:
                params["nextPageToken"] = result["nextPageToken"]

            resp = self._session.get(
                f"https://api.storytel.net/explore/lists/{list_type}/{list_id}",
                params=params,
            )

            data = resp.json()
            if result["nextPageToken"] == 0:
                result = data
            else:
                result["items"].extend(data["items"])
                result["nextPageToken"] = data["nextPageToken"]

        lists_path = self._get_lists_path(result["id"], languages, formats)
        with open(lists_path, "w") as json_file:
            json_data = json.dumps(result, indent=2)
            json_file.write(json_data)

        return result

    def download_book_details(self, consumableId: str) -> Dict[str, Any]:
        """Download books details"""
        resp = self._session.get(
            f"https://api.storytel.net/book-details/consumables/{consumableId}?kidsMode=false&configVariant=default"
        )
        if resp.status_code == 404:
            raise BookNotFound
        data = resp.json()
        return data

    def get_audio_url(self, consumableId: str) -> str:
        """get audio URL

        Get the final Audio URL by sending a requests to the assets API and return the redirect location.
        """
        resp = self._session.get(
            f"https://api.storytel.net/assets/v2/consumables/{consumableId}/abook",
            allow_redirects=False,
        )
        self._download_counter += 1
        if resp.status_code != 302:
            raise GenericSamraEngineException(
                f"request to {resp.url} failed, got {resp.status_code} response: {resp.text}"
            )
        location: str = resp.headers["Location"]
        # Security (H2): the final audio URL comes from a redirect Location; require https
        # so the token/content can never be sent over cleartext or an http downgrade.
        from urllib.parse import urlparse
        if urlparse(location).scheme != "https":
            raise GenericSamraEngineException(
                f"refusing non-https audio URL from redirect: {location[:80]}"
            )
        return location

    def get_files(self, book_info) -> List[AudiobookFile]:
        consumableId = book_info["consumableId"]
        audio_url = self.get_audio_url(consumableId)

        # Security (H2): never leak the Storytel bearer token to a non-Storytel host.
        # The asset redirect target is normally a pre-signed CDN URL that needs no auth,
        # so strip Authorization unless the host is Storytel-owned.
        from urllib.parse import urlparse
        host = (urlparse(audio_url).hostname or "").lower()
        headers = dict(self._session.headers)
        storytel_owned = (
            host in ("storytel.net", "storytel.com")
            or host.endswith(".storytel.net")
            or host.endswith(".storytel.com")
        )
        if not storytel_owned:
            # requests stores headers case-insensitively (key is "authorization");
            # remove it case-insensitively so the strip actually fires.
            for k in [hk for hk in headers if hk.lower() == "authorization"]:
                headers.pop(k, None)

        return [
            AudiobookFile(
                url=audio_url,
                headers=headers,
                ext="mp3",
                expected_status_code=200,
                expected_content_type="audio/mpeg",
            )
        ]

    def get_metadata(self, book_details) -> AudiobookMetadata:
        title = book_details["title"]
        metadata = AudiobookMetadata(title)
        metadata.add_genre("Audiobook")
        metadata.scrape_url = self._clean_share_url(book_details["shareUrl"])
        logging.debug(f"URL {metadata.scrape_url}")
        for author in book_details["authors"]:
            metadata.add_author(author["name"])
        for narrator in book_details["narrators"]:
            metadata.add_narrator(narrator["name"])
        if "isbn" in book_details:
            if book_details["isbn"]:
                metadata.isbn = book_details["isbn"]
        if "description" in book_details:
            metadata.description = book_details["description"]
        if "language" in book_details:
            if book_details["language"]:
                metadata.language = pycountry.languages.get(
                    alpha_2=book_details["language"]
                )
        if "category" in book_details:
            if "name" in book_details["category"]:
                metadata.add_genre(book_details["category"]["name"])
        if "seriesInfo" in book_details and book_details["seriesInfo"]:
            metadata.series = book_details["seriesInfo"]["name"]
            if "orderInSeries" in book_details["seriesInfo"]:
                metadata.series_order = book_details["seriesInfo"]["orderInSeries"]

        if not "formats" in book_details:
            raise DataNotPresent

        abook_formats = [f for f in book_details["formats"] if f["type"] == "abook"]
        ebook_formats = [f for f in book_details["formats"] if f["type"] == "ebook"]

        # For audiobook metadata: use abook format.
        # For ebook-only books: fall back to the ebook format so we still get
        # publisher / release_date without crashing.
        if len(abook_formats) == 0:
            if len(ebook_formats) == 0:
                # No playable format at all
                raise BookHasNoAudiobook
            # Ebook-only book — swap genre tag and use ebook format for the rest
            metadata.genres = [g for g in metadata.genres if g != "Audiobook"]
            metadata.add_genre("Ebook")
            active_format = ebook_formats[0]
        elif len(abook_formats) > 1:
            raise GenericSamraEngineException(
                "multiple abook formats",
                "found multiple abook formats, please report this audiobook for bugfixing",
            )
        else:
            active_format = abook_formats[0]

        if "isReleased" in active_format:
            if not active_format["isReleased"]:
                raise BookNotReleased
        if "publisher" in active_format:
            if "name" in active_format["publisher"]:
                metadata.publisher = active_format["publisher"]["name"]
        if "releaseDate" in active_format:
            date_str: str = active_format["releaseDate"]
            metadata.release_date = datetime.strptime(
                date_str, "%Y-%m-%dT%H:%M:%SZ"
            ).date()
        return metadata

    def download_audiobook_info(self, book_details) -> Dict[str, Any]:
        """Download information about the audiobook files"""
        consumableId = book_details["consumableId"]
        url = f"https://api.storytel.net/playback-metadata/consumable/{consumableId}"
        playback_metadata = self._session.get(url).json()
        playback_metadata_path = self._get_playback_metadata_path(consumableId)
        with open(playback_metadata_path, "w") as json_file:
            json_data = json.dumps(playback_metadata, indent=2)
            json_file.write(json_data)
        if not "formats" in playback_metadata:
            raise DataNotPresent
        for format in playback_metadata["formats"]:
            if format["type"] == "abook":
                return format
        raise DataNotPresent

    def get_chapters(self, book_details) -> List[Chapter]:
        # Ebook-only books have no audio playback metadata — chapters are
        # embedded inside the EPUB itself, so there is nothing to fetch here.
        has_abook = any(
            f.get("type") == "abook"
            for f in book_details.get("formats", [])
        )
        if not has_abook:
            return []

        chapters: List[Chapter] = []
        book_title = book_details["title"]
        file_metadata = self.download_audiobook_info(book_details)
        if not "chapters" in file_metadata:
            return []
        start_time = 0
        for chapter in file_metadata["chapters"]:
            if "title" in chapter and chapter["title"] is not None:
                title = chapter["title"]
                # remove book title prefix from chapter title
                if len(title) > len(book_title) and title.startswith(book_title):
                    title = title[len(book_title) :].strip(" -")
            else:
                title = f"Chapter {chapter['number']}"
            chapters.append(Chapter(start_time, title))
            start_time += chapter["durationInMilliseconds"]
        return chapters

    # ──────────────────────────────────────────────
    # Ebook support (EPUB via ebookStream API)
    # ──────────────────────────────────────────────

    def _has_ebook_format(self, book_details: Dict[str, Any]) -> bool:
        """Return True if the book has an ebook (EPUB) format available."""
        if "formats" not in book_details:
            return False
        return any(f.get("type") == "ebook" for f in book_details["formats"])

    def _find_ebook_edition_id(self, book_details: Dict[str, Any]) -> Optional[str]:
        """
        Look up sibling editions via the explore/lists/editions API and return
        the consumableId of an ebook edition (same language preferred).

        Storytel stores each format (audiobook, ebook) as a SEPARATE consumable,
        so the audiobook's own formats list never contains an ebook entry.
        The editions endpoint lists all related consumables for a title and is
        the only reliable way to find the matching ebook edition.

        :param book_details: Book details dict from the API
        :returns: consumableId of the ebook edition, or None if not found
        """
        consumable_id = book_details.get("consumableId")
        if not consumable_id:
            return None
        preferred_lang = book_details.get("language")

        # Shortcut: if this consumable IS itself an ebook (ebook-only URL),
        # skip the editions lookup and use it directly.
        if any(f.get("type") == "ebook" for f in book_details.get("formats", [])):
            logging.debug(f"Consumable {consumable_id} is itself an ebook — using directly.")
            return consumable_id

        try:
            resp = self._session.get(
                f"https://api.storytel.net/explore/lists/editions/{consumable_id}",
                params={"includeFormats": "ebook,abook,podcast", "kidsMode": "false"},
            )
            if not resp.ok:
                logging.debug(f"Editions API returned {resp.status_code} for {consumable_id}")
                return None
            data = resp.json()
            items = data.get("items", [])
        except Exception as exc:
            logging.debug(f"Editions API request failed: {exc}")
            return None

        # Keep only items that have an ebook format
        ebook_items = [
            item for item in items
            if any(f.get("type") == "ebook" for f in item.get("formats", []))
        ]
        if not ebook_items:
            logging.debug(f"No ebook edition found for consumableId {consumable_id}")
            return None

        # Prefer same language as the audiobook
        if preferred_lang:
            same_lang = [i for i in ebook_items if i.get("language") == preferred_lang]
            if same_lang:
                return same_lang[0]["id"]

        return ebook_items[0]["id"]

    def download_ebook_bytes(self, consumable_id: str) -> bytes:
        """
        Download ebook (EPUB) bytes from Storytel's ebookStream API.

        Uses the SingleSignToken (SST) obtained during login.
        The endpoint follows a redirect to the actual EPUB file.

        :param consumable_id: The ebook consumableId
        :returns: Raw EPUB bytes
        """
        if not self._sst:
            raise GenericSamraEngineException(
                "No SingleSignToken available — cannot download ebook."
            )
        url = (
            f"https://www.storytel.com/api/ebookStream.action"
            f"?token={self._sst}&consumableId={consumable_id}"
        )
        resp = self._session.get(url, allow_redirects=True)
        if resp.status_code != 200:
            raise GenericSamraEngineException(
                f"Ebook download failed ({resp.status_code}): {resp.text[:200]}"
            )
        return resp.content

    def _ebook_output_path(self, metadata: AudiobookMetadata) -> str:
        """
        Build the EPUB output path so it sits next to the audiobook, using the
        same output template (``-o``). The audiobook is saved as
        ``<output_location>.<ext>``; the ebook mirrors that as
        ``<output_location>.epub``.
        """
        output_location = gen_output_location(
            self.options.output_template,
            metadata,
            self.options.remove_chars,
        )
        return f"{output_location}.epub"

    def _embed_epub_metadata(
        self,
        epub_path: str,
        metadata: AudiobookMetadata,
        book_details: Optional[Dict[str, Any]] = None,
    ) -> None:
        """
        Open the downloaded EPUB, update its OPF metadata section with every
        field from AudiobookMetadata PLUS every extra field Storytel exposes,
        then write it back in place.

        Standard fields:
          title, authors (dc:creator/aut), narrators (dc:contributor/nrt),
          publisher, description, isbn, language, release_date, genres
          (dc:subject), series + series_order (Calibre/Audiobookshelf meta tags)

        Extra Storytel fields (from book_details):
          originalTitle, translators (dc:contributor/trl), averageRating,
          numberOfRatings, duration, isAbridged, bookId, category, shareUrl

        Uses lxml (already a project dependency) for namespace-aware XML
        handling.  The zip rewrite preserves the mandatory uncompressed mimetype
        entry as the first item per the EPUB specification.
        """
        DC  = "http://purl.org/dc/elements/1.1/"
        OPF = "http://www.idpf.org/2007/opf"
        tmp_path = epub_path + ".tmp"

        try:
            with zipfile.ZipFile(epub_path, "r") as zin:
                # ── 1. Locate OPF path from container.xml ────────────────
                container_xml = zin.read("META-INF/container.xml").decode("utf-8")
                m = re.search(r'full-path=["\']([^"\']+)["\']', container_xml)
                if not m:
                    logging.debug("EPUB: cannot locate OPF path in container.xml")
                    return
                opf_path = m.group(1)

                # ── 2. Parse OPF XML ─────────────────────────────────────
                opf_bytes = zin.read(opf_path)
                tree = ET.fromstring(opf_bytes)

                # Find <metadata> regardless of namespace prefix
                meta_elem = next(
                    (c for c in tree
                     if c.tag.endswith("}metadata") or c.tag == "metadata"),
                    None,
                )
                if meta_elem is None:
                    logging.debug("EPUB: no <metadata> element found in OPF")
                    return

                # ── 3. Helpers ───────────────────────────────────────────
                def _clear(tag: str) -> None:
                    for old in meta_elem.findall(f"{{{DC}}}{tag}"):
                        meta_elem.remove(old)

                def _set(tag: str, text: str, attrib: dict = {}) -> None:
                    _clear(tag)
                    el = ET.SubElement(meta_elem, f"{{{DC}}}{tag}", attrib)
                    el.text = text

                def _add(tag: str, text: str, attrib: dict = {}) -> None:
                    el = ET.SubElement(meta_elem, f"{{{DC}}}{tag}", attrib)
                    el.text = text

                # ── 4. Write every metadata field ────────────────────────
                _set("title", metadata.title)

                _clear("creator")
                for author in metadata.authors:
                    _add("creator", author, {f"{{{OPF}}}role": "aut"})

                _clear("contributor")
                for narrator in metadata.narrators:
                    _add("contributor", narrator, {f"{{{OPF}}}role": "nrt"})

                if metadata.publisher:
                    _set("publisher", metadata.publisher)
                if metadata.description:
                    _set("description", metadata.description)
                if metadata.isbn:
                    _set("identifier", metadata.isbn, {"id": "isbn"})
                if metadata.language:
                    _set("language", metadata.language.alpha_3)
                if metadata.release_date:
                    _set("date", metadata.release_date.strftime("%Y-%m-%d"))

                _clear("subject")
                for genre in metadata.genres:
                    _add("subject", genre)

                # Calibre-compatible series tags (Audiobookshelf reads these)
                for name in ("calibre:series", "calibre:series_index"):
                    for old in meta_elem.findall(f"{{{OPF}}}meta[@name='{name}']"):
                        meta_elem.remove(old)
                if metadata.series:
                    s = ET.SubElement(meta_elem, f"{{{OPF}}}meta")
                    s.set("name", "calibre:series")
                    s.set("content", metadata.series)
                if metadata.series_order is not None:
                    si = ET.SubElement(meta_elem, f"{{{OPF}}}meta")
                    si.set("name", "calibre:series_index")
                    si.set("content", str(metadata.series_order))

                # ── 4b. Extra Storytel-only fields ───────────────────────
                def _meta(name: str, content: str) -> None:
                    """Add a generic <meta name=.. content=..> tag."""
                    for old in meta_elem.findall(f"{{{OPF}}}meta[@name='{name}']"):
                        meta_elem.remove(old)
                    el = ET.SubElement(meta_elem, f"{{{OPF}}}meta")
                    el.set("name", name)
                    el.set("content", content)

                bd = book_details or {}

                # Original (untranslated) title
                original_title = bd.get("originalTitle")
                if original_title:
                    _meta("storytel:original_title", original_title)

                # Translators → dc:contributor role="trl"
                translators = bd.get("translators") or []
                for translator in translators:
                    name = translator.get("name") if isinstance(translator, dict) else str(translator)
                    if name:
                        _add("contributor", name, {f"{{{OPF}}}role": "trl"})

                # Ratings
                ratings = bd.get("ratings") or {}
                if ratings.get("averageRating") is not None:
                    _meta("storytel:average_rating", str(ratings["averageRating"]))
                if ratings.get("numberOfRatings") is not None:
                    _meta("storytel:number_of_ratings", str(ratings["numberOfRatings"]))

                # Duration (of the audiobook edition)
                duration = bd.get("duration") or {}
                if duration:
                    h = duration.get("hours", 0)
                    mn = duration.get("minutes", 0)
                    s = duration.get("seconds", 0)
                    _meta("storytel:duration", f"{h:02d}:{mn:02d}:{s:02d}")

                # Abridged flag
                if "isAbridged" in bd:
                    _meta("storytel:abridged", "yes" if bd["isAbridged"] else "no")

                # Category / shelf
                category = bd.get("category") or {}
                if category.get("name"):
                    _meta("storytel:category", category["name"])

                # Storytel internal id + share url
                if bd.get("bookId"):
                    _meta("storytel:book_id", str(bd["bookId"]))
                if bd.get("consumableId"):
                    _meta("storytel:consumable_id", str(bd["consumableId"]))

                updated_opf = ET.tostring(
                    tree,
                    pretty_print=True,
                    xml_declaration=True,
                    encoding="utf-8",
                )

                # ── 5. Rewrite EPUB zip with updated OPF ─────────────────
                with zipfile.ZipFile(tmp_path, "w") as zout:
                    # mimetype MUST be first and uncompressed per EPUB spec
                    zout.writestr(
                        zipfile.ZipInfo("mimetype"),
                        "application/epub+zip",
                        compress_type=zipfile.ZIP_STORED,
                    )
                    for item in zin.infolist():
                        if item.filename == "mimetype":
                            continue
                        data = (
                            updated_opf
                            if item.filename == opf_path
                            else zin.read(item.filename)
                        )
                        zout.writestr(item, data)

            os.replace(tmp_path, epub_path)
            logging.log("  Metadata embedded into EPUB ✓")

        except Exception as exc:
            logging.debug(f"EPUB metadata embedding failed: {exc}")
            try:
                if os.path.exists(tmp_path):
                    os.remove(tmp_path)
            except Exception:
                pass

    def _embed_extra_audio_metadata(
        self,
        audio_path: str,
        book_details: Dict[str, Any],
        metadata: AudiobookMetadata,
    ) -> None:
        """
        Embed metadata directly into the audiobook file, running LAST (in
        on_download_complete, after the ffmpeg chapter re-mux).

        IMPORTANT: the core pipeline writes narrator (©nrt) and publisher
        (©pub) via EasyMP4, but the ffmpeg chapter step that runs afterwards
        re-muxes the file and silently DROPS those non-standard atoms. So we
        re-write narrator / publisher / ISBN here (this method is the final
        write) to guarantee they persist, alongside the extra Storytel fields.

        Standard atoms re-written (survive ffmpeg):
          narrator → ©nrt + ©wrt (composer; Audiobookshelf reads both)
          publisher → ©pub
          isbn      → ----:com.apple.iTunes:ISBN

        Extra Storytel fields:
          originalTitle, translators, averageRating, numberOfRatings,
          duration, isAbridged, category, bookId, consumableId.
        """
        if not os.path.exists(audio_path):
            logging.debug(f"Audio file not found for extra metadata: {audio_path}")
            return

        bd = book_details or {}

        # ── Standard fields that ffmpeg stripped — re-derive from data ──────
        narrators = list(metadata.narrators) if metadata.narrators else [
            n.get("name") for n in (bd.get("narrators") or [])
            if isinstance(n, dict) and n.get("name")
        ]
        narrator_str = "; ".join([n for n in narrators if n])

        publisher = metadata.publisher
        if not publisher:
            for f in bd.get("formats", []):
                pub = f.get("publisher") or {}
                if pub.get("name"):
                    publisher = pub["name"]
                    break

        # ISBN: prefer the abook format isbn, then any format, then metadata
        isbn = None
        for f in bd.get("formats", []):
            if f.get("type") == "abook" and f.get("isbn"):
                isbn = f["isbn"]
                break
        if not isbn:
            for f in bd.get("formats", []):
                if f.get("isbn"):
                    isbn = f["isbn"]
                    break
        if not isbn:
            isbn = metadata.isbn

        # ── Build the extra (freeform) key→value map ───────────────────────
        extra: Dict[str, str] = {}
        if bd.get("originalTitle"):
            extra["ORIGINAL_TITLE"] = bd["originalTitle"]
        translators = [
            t.get("name") for t in (bd.get("translators") or [])
            if isinstance(t, dict) and t.get("name")
        ]
        if translators:
            extra["TRANSLATORS"] = "; ".join(translators)
        ratings = bd.get("ratings") or {}
        if ratings.get("averageRating") is not None:
            extra["AVERAGE_RATING"] = str(ratings["averageRating"])
        if ratings.get("numberOfRatings") is not None:
            extra["NUMBER_OF_RATINGS"] = str(ratings["numberOfRatings"])
        duration = bd.get("duration") or {}
        if duration:
            h = duration.get("hours", 0)
            mn = duration.get("minutes", 0)
            s = duration.get("seconds", 0)
            extra["DURATION"] = f"{h:02d}:{mn:02d}:{s:02d}"
        if "isAbridged" in bd:
            extra["ABRIDGED"] = "yes" if bd["isAbridged"] else "no"
        category = bd.get("category") or {}
        if category.get("name"):
            extra["CATEGORY"] = category["name"]
        if bd.get("bookId"):
            extra["BOOK_ID"] = str(bd["bookId"])
        if bd.get("consumableId"):
            extra["CONSUMABLE_ID"] = str(bd["consumableId"])
        if isbn:
            extra["ISBN"] = str(isbn)

        ext = os.path.splitext(audio_path)[1].lower().lstrip(".")
        try:
            if ext in ("mp4", "m4a", "m4b", "m4p", "m4r", "m4v"):
                from mutagen.mp4 import MP4
                audio = MP4(audio_path)
                # Standard atoms — readable by Apple Books / most players.
                # NOTE: ffmpeg (used by Audiobookshelf to read tags) does NOT
                # expose ©nrt or ©pub. It DOES expose ©wrt (composer) and any
                # freeform ----:com.apple.iTunes:* tags. So we write BOTH:
                # standard atoms for players, freeform atoms for ABS/ffmpeg.
                if narrator_str:
                    audio["\xa9nrt"] = [narrator_str]   # narrator (players)
                    audio["\xa9wrt"] = [narrator_str]   # composer (ABS reads this)
                    audio["----:com.apple.iTunes:narrator"] = [narrator_str.encode("utf-8")]
                    audio["----:com.apple.iTunes:narrators"] = [narrator_str.encode("utf-8")]
                if publisher:
                    audio["\xa9pub"] = [publisher]      # publisher (players)
                    audio["----:com.apple.iTunes:publisher"] = [publisher.encode("utf-8")]
                # Freeform extras
                for key, value in extra.items():
                    atom = f"----:com.apple.iTunes:{key}"
                    audio[atom] = [value.encode("utf-8")]
                audio.save()
            elif ext == "mp3":
                from mutagen.id3 import (
                    ID3, TXXX, TPUB, TCOM, ID3NoHeaderError
                )
                try:
                    audio = ID3(audio_path)
                except ID3NoHeaderError:
                    audio = ID3()
                if narrator_str:
                    audio.add(TCOM(encoding=3, text=[narrator_str]))  # composer
                    audio.add(TXXX(encoding=3, desc="NARRATOR", text=[narrator_str]))
                if publisher:
                    audio.add(TPUB(encoding=3, text=[publisher]))
                for key, value in extra.items():
                    audio.add(TXXX(encoding=3, desc=key, text=[value]))
                audio.save(audio_path)
            else:
                logging.debug(f"Unknown audio ext '{ext}', skipping extra metadata")
                return
            logging.log("  Extra metadata embedded into audiobook ✓")
        except Exception as exc:
            logging.debug(f"Embedding extra audio metadata failed: {exc}")

    def _write_full_metadata_json(
        self,
        media_path: str,
        book_details: Dict[str, Any],
        metadata: AudiobookMetadata,
    ) -> None:
        """
        Write a complete ``<name>.metadata.json`` sidecar next to the media
        file containing BOTH the parsed AudiobookMetadata and the full raw
        Storytel ``book_details`` response — i.e. literally every field the
        API returns, nothing dropped.

        :param media_path:   Path to the saved media file (.epub / .m4b / .mp3)
        :param book_details: Full raw book details dict from the API
        :param metadata:     Parsed AudiobookMetadata object
        """
        try:
            base = os.path.splitext(media_path)[0]
            json_path = f"{base}.metadata.json"
            payload = {
                "metadata": metadata.as_dict(),
                "storytel_raw": book_details,
            }

            class _Encoder(json.JSONEncoder):
                def default(self, z):
                    if isinstance(z, (date, datetime)):
                        return str(z)
                    if z.__class__.__name__ == "Language":
                        return getattr(z, "alpha_3", str(z))
                    return super().default(z)

            with open(json_path, "w", encoding="utf-8") as f:
                json.dump(payload, f, ensure_ascii=False, indent=2, cls=_Encoder)
            logging.log(f"  Full metadata JSON → [green]{json_path}[/]")
        except Exception as exc:
            logging.debug(f"Writing metadata JSON failed: {exc}")

    def download_and_save_ebook(
        self,
        book_details: Dict[str, Any],
        metadata: AudiobookMetadata,
        cover: Optional[Cover] = None,
    ) -> None:
        """
        Find the sibling ebook edition via the editions API, download it,
        save it as an EPUB next to the audiobook (same ``-o`` template), then
        embed all metadata fields into the EPUB's OPF document.
        Respects ``--skip-downloaded``: skips if the .epub already exists.

        :param book_details: Book details dict from the API
        :param metadata:     AudiobookMetadata (drives output path + EPUB tags)
        :param cover:        Cover image (reserved for future cover embedding)
        """
        ebook_consumable_id = self._find_ebook_edition_id(book_details)
        if not ebook_consumable_id:
            logging.debug("No ebook edition found, skipping ebook download.")
            return

        ebook_path = self._ebook_output_path(metadata)

        # Honour --skip-downloaded
        if self.skip_downloaded and os.path.exists(ebook_path):
            logging.log(f"  Skipping ebook [blue]{metadata.title}[/], already exists.")
            return

        logging.log(f"  Downloading ebook (EPUB) for [bold]{metadata.title}[/]")
        try:
            ebook_bytes = self.download_ebook_bytes(ebook_consumable_id)
            parent = os.path.dirname(ebook_path)
            if parent:
                os.makedirs(parent, exist_ok=True)
            with open(ebook_path, "wb") as f:
                f.write(ebook_bytes)
            logging.log(f"  Ebook saved → [green]{ebook_path}[/]")
            # Embed all metadata fields into the EPUB OPF document
            self._embed_epub_metadata(ebook_path, metadata, book_details)
            # Write a complete JSON sidecar only when --write-json-metadata is set
            if getattr(self.options, "write_json_metadata", False):
                self._write_full_metadata_json(ebook_path, book_details, metadata)
        except Exception as e:
            logging.debug(f"Ebook download failed: {e}")
            logging.log(f"  [yellow]Ebook not available or download failed.[/]")

    def download_cover(self, book_details) -> Optional[Cover]:
        # NOTE: Storytel's CDN only serves SPECIFIC cover sizes — jpg-640 is the
        # reliable one; other sizes (jpg-1130/1400/…) return HTTP 400. Requesting
        # an unsupported size made get() raise and failed the WHOLE download.
        # So we use the URL as-is, and a cover error NEVER fails the book.
        cover_url = book_details.get("cover", {}).get("url")
        if not cover_url:
            return None
        try:
            cover_data = self.get(cover_url)
            if cover_data:
                return Cover(cover_data, "jpg")
        except Exception:  # noqa: BLE001 — cover is optional, don't fail the download
            pass
        return None

    def on_download_complete(self, audiobook: Audiobook) -> None:
        consumableId = audiobook.source_data["consumableId"]
        book_path = self._get_book_path(consumableId)
        with open(book_path, "w") as json_file:
            json_data = json.dumps(audiobook.source_data, indent=2)
            json_file.write(json_data)

        # Embed extra Storytel fields into the audiobook file(s) and write a
        # complete metadata JSON sidecar (every Storytel field).
        if audiobook.files:
            try:
                audio_base = gen_output_location(
                    self.options.output_template,
                    audiobook.metadata,
                    self.options.remove_chars,
                )
                is_single = len(audiobook.files) == 1 or self.options.combine
                if is_single:
                    out_fmt = self.options.output_format or audiobook.files[0].ext
                    audio_path = f"{audio_base}.{out_fmt}"
                    self._embed_extra_audio_metadata(
                        audio_path, audiobook.source_data, audiobook.metadata
                    )
                else:
                    # Multi-file output: embed into every part inside the folder
                    if os.path.isdir(audio_base):
                        for fname in os.listdir(audio_base):
                            self._embed_extra_audio_metadata(
                                os.path.join(audio_base, fname),
                                audiobook.source_data,
                                audiobook.metadata,
                            )
                # Full JSON sidecar only when --write-json-metadata is set
                if getattr(self.options, "write_json_metadata", False):
                    self._write_full_metadata_json(
                        f"{audio_base}.x", audiobook.source_data, audiobook.metadata
                    )
            except Exception as exc:
                logging.debug(f"Audiobook metadata embedding failed: {exc}")

        # Ebook download policy ("download what the link contains"):
        #   • If the requested consumable ITSELF has an ebook format, the link
        #     IS an ebook → always download the EPUB (independent of --ebook).
        #   • --ebook is the opt-in extra for grabbing the SIBLING ebook edition
        #     of an audiobook link (the GUI no longer sends it by default).
        self_is_ebook = any(
            f.get("type") == "ebook"
            for f in audiobook.source_data.get("formats", [])
        )
        if self_is_ebook or getattr(self.options, "download_ebook", False):
            self.download_and_save_ebook(
                audiobook.source_data, audiobook.metadata, audiobook.cover
            )
