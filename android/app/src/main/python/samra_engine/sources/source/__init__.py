_D='cookies'
_C='utf8'
_B=None
_A=True
from.import networking
from samra_engine import logging,AudiobookFile,Chapter,AudiobookMetadata,Cover,Result,Audiobook,BookId
from samra_engine.exceptions import DataNotPresent,GenericSamraEngineException
from samra_engine.utils import CustomSSLContextHTTPAdapter
import requests,lxml.html
from lxml.cssselect import CSSSelector
import re,os
from http.cookiejar import MozillaCookieJar
from typing import Any,Dict,List,Optional,TypeVar,Generic
from ssl import SSLContext
import urllib3
T=TypeVar('T')
class Source(Generic[T]):
	login_data:List[str]=['username','password'];match:List[str]=[];names:List[str]=[];_authentication_methods:List[str]=[_D];create_storage_dir:bool=False;__authenticated=False;__pages:Dict[str,bytes]={}
	def __init__(A,options:Any):
		B=options;A.database_directory=os.path.join(B.database_directory,A.name);A.skip_downloaded=B.skip_downloaded;(A._session):requests.Session=A.create_session(B)
		if A.create_storage_dir:os.makedirs(A.database_directory,exist_ok=_A)
	@property
	def name(self)->str:return self.names[0].lower()
	@property
	def requires_authentication(self):return len(self._authentication_methods)>0
	@property
	def authenticated(self):return self.__authenticated
	@property
	def supports_cookies(self):return _D in self._authentication_methods
	def load_cookie_file(A,cookie_file:str):
		B=cookie_file
		if A.supports_cookies:logging.debug(f"Loading cookies from '{B}'");C=MozillaCookieJar();C.load(B,ignore_expires=_A);A._session.cookies.update(C);A.__authenticated=_A
	@property
	def supports_login(self):return'login'in self._authentication_methods
	def _login(A,url:str,username:str,password:str):0
	def login(A,url:str,**B)->_B:
		if A.supports_login:logging.debug('Logging in');A._login(url,**B);A.__authenticated=_A
	def download_from_id(A,book_id:T)->Audiobook:raise NotImplementedError
	def download(A,url:str)->Result:raise NotImplementedError
	def on_download_complete(A,audiobook:Audiobook):0
	def _get_page(A,url:str,use_cache:bool=_A,**D)->bytes:
		C=use_cache;B=url
		if B not in A.__pages and C:
			E=A.get(B,**D)
			if C:A.__pages[B]=E
		return A.__pages[B]
	def find_elem_in_page(D,url:str,selector:str,data=_B,**E):
		A=selector;B=D.find_elems_in_page(url,A,**E)
		if len(B)==0:logging.debug(f"Could not find matching element from {url} with {A}");raise DataNotPresent
		C=B[0]
		if data is _B:return C.text
		return C.get(data)
	def find_elems_in_page(A,url:str,selector:str,**B)->Any:C=CSSSelector(selector);D:bytes=A._get_page(url,**B);E=lxml.html.fromstring(D.decode(_C));F=C(E);return F
	def find_in_page(C,url:str,regex:str,group_index:int=0,**D)->str:
		A=regex;E=C._get_page(url,**D).decode(_C);B=re.search(A,E)
		if B is _B:logging.debug(f"Could not find match from {url} with {A}");raise DataNotPresent
		return B.group(group_index)
	def find_all_in_page(A,url:str,regex:str,**B)->list:return re.findall(regex,A._get_page(url,**B).decode(_C))
	post=networking.post;get=networking.get;post_json=networking.post_json;get_json=networking.get_json;get_stream_files=networking.get_stream_files
	def create_ssl_context(B,options:Any)->SSLContext:
		try:A:SSLContext=urllib3.util.create_urllib3_context();A.load_default_certs();A.options&=~16;return A
		except AttributeError:raise GenericSamraEngineException(f"Please update urllib3 to version >= 2 using the command 'pip install -U urllib3'")
	def create_session(B,options:Any)->requests.Session:A=requests.Session();C:SSLContext=B.create_ssl_context(options);A.mount('https://',CustomSSLContextHTTPAdapter(C));return A