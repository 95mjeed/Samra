_q='write_json_metadata'
_p='seconds'
_o='minutes'
_n='duration'
_m='ratings'
_l='translators'
_k='metadata'
_j='kidsMode'
_i='includeFormats'
_h='authors'
_g='authorization'
_f='application/x-www-form-urlencoded'
_e='content-type'
_d='originalTitle'
_c='isReleased'
_b='narrators'
_a='bookId'
_Z='isAbridged'
_Y='numberOfRatings'
_X='averageRating'
_W='description'
_V='publisher'
_U='ebook'
_T='category'
_S='items'
_R='books'
_Q='language'
_P='id'
_O='utf-8'
_N='abook'
_M='consumableId'
_L=False
_K=True
_J='isbn'
_I='type'
_H='Leo Da Vinci'
_G='series_order'
_F='series'
_E='formats'
_D='name'
_C='release_date'
_B=None
_A='title'
from requests.models import Response
from.source import Source
from samra_engine import AudiobookFile,Chapter,logging,AudiobookMetadata,Cover,Audiobook,Series,BookId,Result
from samra_engine.exceptions import GenericSamraEngineException,UserNotAuthorized,CloudflareBlocked,BookNotFound,BookHasNoAudiobook,BookNotReleased,DataNotPresent
from samra_engine.output import gen_output_location
from lxml import etree as ET
from Crypto.Cipher import AES
import zipfile
from Crypto.Util.Padding import pad
from typing import Any,List,Dict,Optional,Union
from urllib3.util import parse_url
from urllib.parse import urlunparse,parse_qs
from datetime import datetime,date
import pycountry,json,re,os,uuid
metadata_corrections:Dict[str,Dict[str,Any]]={_R:{'1623721':{_A:'Bibi & Tina: Schatten über dem Martinshof',_C:date(2010,3,12)},'1623873':{_A:'Bibi & Tina: Die ungarischen Reiter',_C:date(2010,9,10)},'1623776':{_A:'Bibi & Tina: Der wilde Hengst',_C:date(2009,11,20)},'1623780':{_A:'Bibi & Tina: Die geheimnisvolle Köchin',_C:date(2012,6,8)},'1623767':{_A:'Bibi & Tina: Der Tiger von Rotenbrunn',_C:date(2013,9,6)},'1623757':{_A:'Bibi & Tina: Die falschen Weihnachtsmänner',_C:date(2013,11,1)},'1623860':{_A:'Bibi & Tina: Indianerpferde in Gefahr',_C:date(2014,9,5)},'1623775':{_A:'Bibi & Tina: Der weiße Mustang',_C:date(2015,9,4)},'1623856':{_A:'Bibi & Tina: Holger verliebt sich',_C:date(2016,9,9)},'1623760':{_A:'Bibi & Tina: Das Fohlen im Schnee',_C:date(2017,9,8)},'1623855':{_A:'Bibi & Tina: Ein heißer Sommer',_C:date(2018,7,6)},'1623857':{_A:'Bibi & Tina: Im Land der weißen Pferde',_C:date(2019,9,6)},'1048495':{_A:'Bibi & Tina: Der mysteriöse Fremde',_W:'Graf Falko von Falkenstein ist verzweifelt! Er leidet unter Schlaflosigkeit und bittet schließlich einen Wunderheiler um Hilfe. Der mysteriöse Fremde heilt nicht nur den Grafen, sondern wickelt sogar Frau Martin um den Finger. Tina ist gar nicht begeistert und auch Bibi misstraut dem Mann. Als die Freundinnen und Alex versuchen, dem Geheimnis des Heilers auf die Spur zu kommen, überschlagen sich die Ereignisse und die Kinder geraten in Gefahr.',_C:date(2020,10,23)},'1397689':{_A:'Bibi & Tina: Ein Monster im Wald',_C:date(2021,10,22)},'1615235':{_A:'Bibi Blocksberg - Hörbuch: Im Tal der wilden Hexen',_C:date(2010,3,20)},'1615295':{_A:'Bibi Blocksberg - Das verhexte Wunschhaus',_C:date(2011,3,4)},'1615294':{_A:'Bibi Blocksberg - Die Gewitterhexe',_C:date(2012,10,19)},'1615236':{_A:'Bibi Blocksberg - Zickia-Alarm!',_C:date(2013,6,7)},'1615182':{_A:'Bibi Blocksberg - Das verhexte Schwein',_C:date(2013,10,11)},'1615288':{_A:'Bibi Blocksberg - Bibi total verknallt!',_C:date(2014,6,6)},'1615205':{_A:'Bibi Blocksberg - Hexkraft gesucht!',_C:date(2014,10,10)},'1615204':{_A:'Bibi Blocksberg - Wo ist Moni?',_C:date(2015,6,12)},'1615203':{_A:'Bibi Blocksberg - Gustav, der Hexendrache',_C:date(2015,10,9)},'1615175':{_A:'Bibi Blocksberg - Abenteuer Indien!',_C:date(2017,10,13)},'1615201':{_A:'Bibi Blocksberg - Die Schule ist weg!',_C:date(2018,10,12)},'1022245':{_A:'Bibi Blocksberg - Bibi und Herr Fu',_C:date(2020,9,18)},'522762':{_A:'Alvin und die Chipmunks: Der Katzenfluch'},'1260956':{_A:'Fast and Furious Spy Racer: Folge 1'},'1878866':{_A:'Ghostforce: Folge 1'},'1878880':{_A:'Ghostforce: Folge 2'},'2642089':{_A:'Ghostforce: Folge 3'},'2642148':{_A:'Ghostforce: Folge 4'},'1168061':{_A:'Leo Da Vinci: Folge 1',_F:_H,_G:1},'1176396':{_A:'Leo Da Vinci: Folge 2',_F:_H,_G:2},'1178721':{_A:'Leo Da Vinci: Folge 3',_F:_H,_G:3},'1176422':{_A:'Leo Da Vinci: Folge 4',_F:_H,_G:4},'1176424':{_A:'Leo Da Vinci: Folge 5',_F:_H,_G:5},'1176462':{_A:'Leo Da Vinci: Folge 6',_F:_H,_G:6},'1262342':{_A:'Leo Da Vinci: Folge 7',_F:_H,_G:7},'1263433':{_A:'Leo Da Vinci: Folge 8',_F:_H,_G:8},'1263421':{_A:'Leo Da Vinci: Folge 9',_F:_H,_G:9},'1309115':{_A:'Leo Da Vinci: Folge 10',_F:_H,_G:10},'1320400':{_A:'Leo Da Vinci: Folge 11',_F:_H,_G:11},'1328193':{_A:'Leo Da Vinci: Folge 12',_F:_H,_G:12}}}
svg_headphone_path='M8.25 12.371h-.625c-1.38 0-2.5 1.121-2.5 2.505v3.12a2.503 2.503 0 0 0 2.5 2.504h.625c.69 0 1.25-.56 1.25-1.252v-5.627c0-.691-.559-1.25-1.25-1.25Zm-.625 6.254a.628.628 0 0 1-.625-.63v-3.12c0-.347.28-.63.625-.63v4.38ZM12 3C6.41 3 2.178 7.652 2 13v4.375c0 .346.28.625.625.625h.625a.626.626 0 0 0 .625-.627V13c0-4.48 3.646-8.117 8.125-8.117 4.48 0 8.125 3.637 8.125 8.117v4.371c-.035.348.281.629.625.629l.625.001c.346 0 .625-.28.625-.625v-4.411C21.82 7.652 17.59 3 12 3Zm4.375 9.371h-.625c-.69 0-1.25.56-1.25 1.252v5.625c0 .692.56 1.252 1.25 1.252h.625c1.38 0 2.5-1.121 2.5-2.505v-3.12a2.503 2.503 0 0 0-2.5-2.504ZM17 17.996a.628.628 0 0 1-.625.629v-4.379c.345 0 .625.283.625.63v3.12Z'
class StorytelSource(Source):
	match=['https?://(?:www.)?(?:storytel|mofibo).com/(?P<language>\\w+)(?:/(?P<language2>\\w+))?/(?P<list_type>(?:books|series|authors|narrators|publishers|categories))/.+'];names=['Storytel','Mofibo'];_authentication_methods=['login'];_download_counter=0;create_storage_dir=_K
	def __init__(A,options)->_B:B=options;super().__init__(B);A.options=B;A._sst='';A.database_directory_books=os.path.join(A.database_directory,_R);A.database_directory_playback_metadata=os.path.join(A.database_directory,'playback-metadata');A.database_directory_lists=os.path.join(A.database_directory,'lists');os.makedirs(A.database_directory_books,exist_ok=_K);os.makedirs(A.database_directory_playback_metadata,exist_ok=_K);os.makedirs(A.database_directory_lists,exist_ok=_K)
	def _get_book_path(A,consumableId:str)->str:return os.path.join(A.database_directory_books,f"{consumableId}.json")
	def _get_playback_metadata_path(A,consumableId:str)->str:return os.path.join(A.database_directory_playback_metadata,f"{consumableId}.json")
	def _get_lists_path(A,list_name:str,languages:str,formats:str)->str:return os.path.join(A.database_directory_lists,f"{list_name}_{languages}_{formats}.json")
	def _skip_download_check(A,book_id:str)->bool:
		if A.skip_downloaded:B=A._get_book_path(book_id);return os.path.exists(B)
		else:return _L
	@staticmethod
	def encrypt_password(password:str)->str:A=b'VQZBJ6TD8M9WBUWT';B=b'joiwef08u23j341a';C=pad(password.encode(),AES.block_size);D=AES.new(A,AES.MODE_CBC,B);E=D.encrypt(C);return E.hex()
	def check_cloudflare_blocked(C,response:Response)->_B:
		A=response
		if A.status_code==403:
			B='<title>Attention Required! | Cloudflare</title>'
			if B in A.text:raise CloudflareBlocked
	def _login(A,url:str,username:str,password:str)->_B:A._url=url;A._username=username;A._password=A.encrypt_password(password);A._session.headers.update({'User-Agent':'Storytel/24.22 (Android 14; Google Pixel 8 Pro) Release/2288629'});A._do_login()
	def _do_login(A)->_B:
		D='accountInfo';E=str(uuid.uuid4());B=A._session.post(f"https://www.storytel.com/api/login.action?m=1&token=guestsv&userid=-1&version=24.22&terminal=android&locale=sv&deviceId={E}&kidsMode=false",data={'uid':A._username,'pwd':A._password},headers={_e:_f})
		if B.status_code!=200:
			if B.status_code==403:A.check_cloudflare_blocked(B)
			raise UserNotAuthorized
		C=B.json();F=C[D]['jwt'];A._sst=C[D].get('singleSignToken','');A._language=C[D]['lang'];A._session.headers.update({_g:f"Bearer {F}"})
	def _relogin_check(A)->_B:
		if A._download_counter>0 and A._download_counter%10==0:logging.debug('refreshing login');A._do_login()
	@staticmethod
	def _clean_share_url(url:str)->str:return url.split('?')[0]
	def download_from_id(A,book_id:str)->Audiobook:A._relogin_check();B=A.download_book_from_book_id(book_id);return B
	def download(A,url:str)->Result:
		B=url;A._relogin_check()
		if(E:=re.match(A.match[0],B)):
			D,F,C=E.groups();logging.debug(f"download: url={B!r}, list_type={C!r}, language={D!r}, language2={F!r}")
			if C==_R:return A.download_book_from_url(B)
			elif C in(_F,_h,_b):return A.download_lists_api(B,C,D)
			else:return A.download_books_from_website(B)
		raise BookNotFound
	def download_lists_api(A,url:str,list_type:str,language:str)->Series[str]:
		F:str=A.get_id_from_url(url);C=A.download_list_books(F,list_type,language);D:List[Union[BookId[str],Audiobook]]=[]
		for B in C[_S]:
			E=[format for format in B[_E]if format[_I]==_N]
			if len(E)>0 and E[0][_c]and not A._skip_download_check(B[_P]):G=BookId(B[_P]);D.append(G)
		return Series(title=C[_A],books=D)
	def download_book_from_book_id(A,consumableId:str)->Audiobook:
		E=consumableId;B=A.download_book_details(E);C=A.get_metadata(B);G=any(A.get(_I)==_N for A in B.get(_E,[]))
		if not G:D=A.download_cover(B);return Audiobook(session=A._session,files=[],metadata=C,cover=D,chapters=[],source_data=B)
		F=A.get_files(B);D=A.download_cover(B);H=A.get_chapters(B);A._update_metadata(E,B,C,F);return Audiobook(session=A._session,files=F,metadata=C,cover=D,chapters=H,source_data=B)
	def download_book_from_url(A,url:str)->Audiobook:B=A.get_id_from_url(url);return A.download_book_from_book_id(B)
	@staticmethod
	def get_id_from_url(url:str)->str:
		A=parse_url(url)
		if A.path is _B:raise DataNotPresent
		return A.path.split('-')[-1]
	@staticmethod
	def _update_metadata(consumableId:str,book_details:Dict[str,Any],metadata:AudiobookMetadata,files:List[AudiobookFile])->_B:
		C=consumableId;A=metadata;G=parse_url(files[0].url);D=parse_qs(G.query)
		if _J in D:E=D[_J][0];book_details['_download_url_isbn']=E;A.isbn=E
		if C in metadata_corrections[_R]:
			H=metadata_corrections[_R][C]
			for(B,F)in H.items():logging.log(f"overriding metadata [yellow]{B}[/] from [blue]{getattr(A,B)}[/] to [magenta]{F}[/]");setattr(A,B,F)
	def download_bookshelf(A)->Dict[str,Any]:
		C=A._session.post('https://api.storytel.net/libraries/bookshelf',json={_S:[]},headers={_e:_f});B:Dict[str,Any]=C.json();D=os.path.join(A.database_directory_lists,f"bookshelf.json")
		with open(D,'w')as E:F=json.dumps(B,indent=2);E.write(F)
		return B
	def download_books_from_website(A,url:str)->Series[str]:
		F=A.find_elems_in_page(url,'h1')[-1].text;G=A.find_elems_in_page(url,'a[href*="/books/"]');B:List[Union[BookId[str],Audiobook]]=[]
		for C in G:
			D:str=C.get('href');H=C.cssselect(f"svg > path[d='{svg_headphone_path}']")
			if len(H)==0:logging.debug(f"skipping {D} (has no audiobook)");continue
			E=A.get_id_from_url(D)
			if not A._skip_download_check(E):I=BookId(E);B.append(I)
		return Series(title=F,books=B)
	def download_list_books(D,list_id:str,list_type:str,languages:str,formats:str=_N)->Dict[str,Any]:
		F=formats;E=languages;B='nextPageToken';L=0;A:Dict[str,Any]={B:_L}
		while A[B]!=_B:
			G:dict[str,str]={'includeListDetails':'true',_i:F,'includeLanguages':E,_j:'false'}
			if A[B]:G[B]=A[B]
			H=D._session.get(f"https://api.storytel.net/explore/lists/{list_type}/{list_id}",params=G);C=H.json()
			if A[B]==0:A=C
			else:A[_S].extend(C[_S]);A[B]=C[B]
		I=D._get_lists_path(A[_P],E,F)
		with open(I,'w')as J:K=json.dumps(A,indent=2);J.write(K)
		return A
	def download_book_details(B,consumableId:str)->Dict[str,Any]:
		A=B._session.get(f"https://api.storytel.net/book-details/consumables/{consumableId}?kidsMode=false&configVariant=default")
		if A.status_code==404:raise BookNotFound
		C=A.json();return C
	def get_audio_url(C,consumableId:str)->str:
		A=C._session.get(f"https://api.storytel.net/assets/v2/consumables/{consumableId}/abook",allow_redirects=_L);C._download_counter+=1
		if A.status_code!=302:raise GenericSamraEngineException(f"request to {A.url} failed, got {A.status_code} response: {A.text}")
		B:str=A.headers['Location'];from urllib.parse import urlparse as D
		if D(B).scheme!='https':raise GenericSamraEngineException(f"refusing non-https audio URL from redirect: {B[:80]}")
		return B
	def get_files(C,book_info)->List[AudiobookFile]:
		E=book_info[_M];D=C.get_audio_url(E);from urllib.parse import urlparse as F;A=(F(D).hostname or'').lower();B=dict(C._session.headers);G=A in('storytel.net','storytel.com')or A.endswith('.storytel.net')or A.endswith('.storytel.com')
		if not G:
			for H in[A for A in B if A.lower()==_g]:B.pop(H,_B)
		return[AudiobookFile(url=D,headers=B,ext='mp3',expected_status_code=200,expected_content_type='audio/mpeg')]
	def get_metadata(J,book_details)->AudiobookMetadata:
		I='releaseDate';H='orderInSeries';G='Audiobook';D='seriesInfo';A=book_details;K=A[_A];B=AudiobookMetadata(K);B.add_genre(G);B.scrape_url=J._clean_share_url(A['shareUrl']);logging.debug(f"URL {B.scrape_url}")
		for L in A[_h]:B.add_author(L[_D])
		for M in A[_b]:B.add_narrator(M[_D])
		if _J in A:
			if A[_J]:B.isbn=A[_J]
		if _W in A:B.description=A[_W]
		if _Q in A:
			if A[_Q]:B.language=pycountry.languages.get(alpha_2=A[_Q])
		if _T in A:
			if _D in A[_T]:B.add_genre(A[_T][_D])
		if D in A and A[D]:
			B.series=A[D][_D]
			if H in A[D]:B.series_order=A[D][H]
		if not _E in A:raise DataNotPresent
		E=[A for A in A[_E]if A[_I]==_N];F=[A for A in A[_E]if A[_I]==_U]
		if len(E)==0:
			if len(F)==0:raise BookHasNoAudiobook
			B.genres=[A for A in B.genres if A!=G];B.add_genre('Ebook');C=F[0]
		elif len(E)>1:raise GenericSamraEngineException('multiple abook formats','found multiple abook formats, please report this audiobook for bugfixing')
		else:C=E[0]
		if _c in C:
			if not C[_c]:raise BookNotReleased
		if _V in C:
			if _D in C[_V]:B.publisher=C[_V][_D]
		if I in C:N:str=C[I];B.release_date=datetime.strptime(N,'%Y-%m-%dT%H:%M:%SZ').date()
		return B
	def download_audiobook_info(B,book_details)->Dict[str,Any]:
		C=book_details[_M];D=f"https://api.storytel.net/playback-metadata/consumable/{C}";A=B._session.get(D).json();E=B._get_playback_metadata_path(C)
		with open(E,'w')as F:G=json.dumps(A,indent=2);F.write(G)
		if not _E in A:raise DataNotPresent
		for format in A[_E]:
			if format[_I]==_N:return format
		raise DataNotPresent
	def get_chapters(I,book_details)->List[Chapter]:
		H='chapters';C=book_details;J=any(A.get(_I)==_N for A in C.get(_E,[]))
		if not J:return[]
		E:List[Chapter]=[];D=C[_A];F=I.download_audiobook_info(C)
		if not H in F:return[]
		G=0
		for B in F[H]:
			if _A in B and B[_A]is not _B:
				A=B[_A]
				if len(A)>len(D)and A.startswith(D):A=A[len(D):].strip(' -')
			else:A=f"Chapter {B["number"]}"
			E.append(Chapter(G,A));G+=B['durationInMilliseconds']
		return E
	def _has_ebook_format(B,book_details:Dict[str,Any])->bool:
		A=book_details
		if _E not in A:return _L
		return any(A.get(_I)==_U for A in A[_E])
	def _find_ebook_edition_id(G,book_details:Dict[str,Any])->Optional[str]:
		B=book_details;A=B.get(_M)
		if not A:return
		E=B.get(_Q)
		if any(A.get(_I)==_U for A in B.get(_E,[])):logging.debug(f"Consumable {A} is itself an ebook — using directly.");return A
		try:
			C=G._session.get(f"https://api.storytel.net/explore/lists/editions/{A}",params={_i:'ebook,abook,podcast',_j:'false'})
			if not C.ok:logging.debug(f"Editions API returned {C.status_code} for {A}");return
			H=C.json();I=H.get(_S,[])
		except Exception as J:logging.debug(f"Editions API request failed: {J}");return
		D=[A for A in I if any(A.get(_I)==_U for A in A.get(_E,[]))]
		if not D:logging.debug(f"No ebook edition found for consumableId {A}");return
		if E:
			F=[A for A in D if A.get(_Q)==E]
			if F:return F[0][_P]
		return D[0][_P]
	def download_ebook_bytes(B,consumable_id:str)->bytes:
		if not B._sst:raise GenericSamraEngineException('No SingleSignToken available — cannot download ebook.')
		C=f"https://www.storytel.com/api/ebookStream.action?token={B._sst}&consumableId={consumable_id}";A=B._session.get(C,allow_redirects=_K)
		if A.status_code!=200:raise GenericSamraEngineException(f"Ebook download failed ({A.status_code}): {A.text[:200]}")
		return A.content
	def _ebook_output_path(A,metadata:AudiobookMetadata)->str:B=gen_output_location(A.options.output_template,metadata,A.options.remove_chars);return f"{B}.epub"
	def _embed_epub_metadata(s,epub_path:str,metadata:AudiobookMetadata,book_details:Optional[Dict[str,Any]]=_B)->_B:
		f='mimetype';e='calibre:series_index';d='calibre:series';c='subject';b='creator';T='content';S='contributor';P=epub_path;A=metadata;Q='http://purl.org/dc/elements/1.1/';D='http://www.idpf.org/2007/opf';G=P+'.tmp'
		try:
			with zipfile.ZipFile(P,'r')as H:
				g=H.read('META-INF/container.xml').decode(_O);U=re.search('full-path=["\\\']([^"\\\']+)["\\\']',g)
				if not U:logging.debug('EPUB: cannot locate OPF path in container.xml');return
				V=U.group(1);h=H.read(V);W=ET.fromstring(h);B=next((A for A in W if A.tag.endswith('}metadata')or A.tag==_k),_B)
				if B is _B:logging.debug('EPUB: no <metadata> element found in OPF');return
				def I(tag:str)->_B:
					for A in B.findall(f"{{{Q}}}{tag}"):B.remove(A)
				def F(tag:str,text:str,attrib:dict={})->_B:I(tag);A=ET.SubElement(B,f"{{{Q}}}{tag}",attrib);A.text=text
				def J(tag:str,text:str,attrib:dict={})->_B:A=ET.SubElement(B,f"{{{Q}}}{tag}",attrib);A.text=text
				F(_A,A.title);I(b)
				for i in A.authors:J(b,i,{f"{{{D}}}role":'aut'})
				I(S)
				for j in A.narrators:J(S,j,{f"{{{D}}}role":'nrt'})
				if A.publisher:F(_V,A.publisher)
				if A.description:F(_W,A.description)
				if A.isbn:F('identifier',A.isbn,{_P:_J})
				if A.language:F(_Q,A.language.alpha_3)
				if A.release_date:F('date',A.release_date.strftime('%Y-%m-%d'))
				I(c)
				for k in A.genres:J(c,k)
				for K in(d,e):
					for l in B.findall(f"{{{D}}}meta[@name='{K}']"):B.remove(l)
				if A.series:L=ET.SubElement(B,f"{{{D}}}meta");L.set(_D,d);L.set(T,A.series)
				if A.series_order is not _B:X=ET.SubElement(B,f"{{{D}}}meta");X.set(_D,e);X.set(T,str(A.series_order))
				def E(name:str,content:str)->_B:
					for C in B.findall(f"{{{D}}}meta[@name='{name}']"):B.remove(C)
					A=ET.SubElement(B,f"{{{D}}}meta");A.set(_D,name);A.set(T,content)
				C=book_details or{};Y=C.get(_d)
				if Y:E('storytel:original_title',Y)
				m=C.get(_l)or[]
				for R in m:
					K=R.get(_D)if isinstance(R,dict)else str(R)
					if K:J(S,K,{f"{{{D}}}role":'trl'})
				M=C.get(_m)or{}
				if M.get(_X)is not _B:E('storytel:average_rating',str(M[_X]))
				if M.get(_Y)is not _B:E('storytel:number_of_ratings',str(M[_Y]))
				N=C.get(_n)or{}
				if N:n=N.get('hours',0);o=N.get(_o,0);L=N.get(_p,0);E('storytel:duration',f"{n:02d}:{o:02d}:{L:02d}")
				if _Z in C:E('storytel:abridged','yes'if C[_Z]else'no')
				Z=C.get(_T)or{}
				if Z.get(_D):E('storytel:category',Z[_D])
				if C.get(_a):E('storytel:book_id',str(C[_a]))
				if C.get(_M):E('storytel:consumable_id',str(C[_M]))
				p=ET.tostring(W,pretty_print=_K,xml_declaration=_K,encoding=_O)
				with zipfile.ZipFile(G,'w')as a:
					a.writestr(zipfile.ZipInfo(f),'application/epub+zip',compress_type=zipfile.ZIP_STORED)
					for O in H.infolist():
						if O.filename==f:continue
						q=p if O.filename==V else H.read(O.filename);a.writestr(O,q)
			os.replace(G,P);logging.log('  Metadata embedded into EPUB ✓')
		except Exception as r:
			logging.debug(f"EPUB metadata embedding failed: {r}")
			try:
				if os.path.exists(G):os.remove(G)
			except Exception:pass
	def _embed_extra_audio_metadata(Z,audio_path:str,book_details:Dict[str,Any],metadata:AudiobookMetadata)->_B:
		I=metadata;H=audio_path
		if not os.path.exists(H):logging.debug(f"Audio file not found for extra metadata: {H}");return
		A=book_details or{};S=list(I.narrators)if I.narrators else[A.get(_D)for A in A.get(_b)or[]if isinstance(A,dict)and A.get(_D)];D='; '.join([A for A in S if A]);F=I.publisher
		if not F:
			for E in A.get(_E,[]):
				O=E.get(_V)or{}
				if O.get(_D):F=O[_D];break
		G=_B
		for E in A.get(_E,[]):
			if E.get(_I)==_N and E.get(_J):G=E[_J];break
		if not G:
			for E in A.get(_E,[]):
				if E.get(_J):G=E[_J];break
		if not G:G=I.isbn
		C:Dict[str,str]={}
		if A.get(_d):C['ORIGINAL_TITLE']=A[_d]
		P=[A.get(_D)for A in A.get(_l)or[]if isinstance(A,dict)and A.get(_D)]
		if P:C['TRANSLATORS']='; '.join(P)
		J=A.get(_m)or{}
		if J.get(_X)is not _B:C['AVERAGE_RATING']=str(J[_X])
		if J.get(_Y)is not _B:C['NUMBER_OF_RATINGS']=str(J[_Y])
		K=A.get(_n)or{}
		if K:T=K.get('hours',0);U=K.get(_o,0);V=K.get(_p,0);C['DURATION']=f"{T:02d}:{U:02d}:{V:02d}"
		if _Z in A:C['ABRIDGED']='yes'if A[_Z]else'no'
		Q=A.get(_T)or{}
		if Q.get(_D):C['CATEGORY']=Q[_D]
		if A.get(_a):C['BOOK_ID']=str(A[_a])
		if A.get(_M):C['CONSUMABLE_ID']=str(A[_M])
		if G:C['ISBN']=str(G)
		L=os.path.splitext(H)[1].lower().lstrip('.')
		try:
			if L in('mp4','m4a','m4b','m4p','m4r','m4v'):
				from mutagen.mp4 import MP4;B=MP4(H)
				if D:B['©nrt']=[D];B['©wrt']=[D];B['----:com.apple.iTunes:narrator']=[D.encode(_O)];B['----:com.apple.iTunes:narrators']=[D.encode(_O)]
				if F:B['©pub']=[F];B['----:com.apple.iTunes:publisher']=[F.encode(_O)]
				for(M,N)in C.items():W=f"----:com.apple.iTunes:{M}";B[W]=[N.encode(_O)]
				B.save()
			elif L=='mp3':
				from mutagen.id3 import ID3,TXXX as R,TPUB,TCOM,ID3NoHeaderError as X
				try:B=ID3(H)
				except X:B=ID3()
				if D:B.add(TCOM(encoding=3,text=[D]));B.add(R(encoding=3,desc='NARRATOR',text=[D]))
				if F:B.add(TPUB(encoding=3,text=[F]))
				for(M,N)in C.items():B.add(R(encoding=3,desc=M,text=[N]))
				B.save(H)
			else:logging.debug(f"Unknown audio ext '{L}', skipping extra metadata");return
			logging.log('  Extra metadata embedded into audiobook ✓')
		except Exception as Y:logging.debug(f"Embedding extra audio metadata failed: {Y}")
	def _write_full_metadata_json(G,media_path:str,book_details:Dict[str,Any],metadata:AudiobookMetadata)->_B:
		try:
			B=os.path.splitext(media_path)[0];A=f"{B}.metadata.json";C={_k:metadata.as_dict(),'storytel_raw':book_details}
			class D(json.JSONEncoder):
				def default(A,z):
					if isinstance(z,(date,datetime)):return str(z)
					if z.__class__.__name__=='Language':return getattr(z,'alpha_3',str(z))
					return super().default(z)
			with open(A,'w',encoding=_O)as E:json.dump(C,E,ensure_ascii=_L,indent=2,cls=D)
			logging.log(f"  Full metadata JSON → [green]{A}[/]")
		except Exception as F:logging.debug(f"Writing metadata JSON failed: {F}")
	def download_and_save_ebook(A,book_details:Dict[str,Any],metadata:AudiobookMetadata,cover:Optional[Cover]=_B)->_B:
		D=book_details;C=metadata;E=A._find_ebook_edition_id(D)
		if not E:logging.debug('No ebook edition found, skipping ebook download.');return
		B=A._ebook_output_path(C)
		if A.skip_downloaded and os.path.exists(B):logging.log(f"  Skipping ebook [blue]{C.title}[/], already exists.");return
		logging.log(f"  Downloading ebook (EPUB) for [bold]{C.title}[/]")
		try:
			G=A.download_ebook_bytes(E);F=os.path.dirname(B)
			if F:os.makedirs(F,exist_ok=_K)
			with open(B,'wb')as H:H.write(G)
			logging.log(f"  Ebook saved → [green]{B}[/]");A._embed_epub_metadata(B,C,D)
			if getattr(A.options,_q,_L):A._write_full_metadata_json(B,D,C)
		except Exception as I:logging.debug(f"Ebook download failed: {I}");logging.log(f"  [yellow]Ebook not available or download failed.[/]")
	def download_cover(C,book_details)->Optional[Cover]:
		A=book_details.get('cover',{}).get('url')
		if not A:return
		try:
			B=C.get(A)
			if B:return Cover(B,'jpg')
		except Exception:pass
	def on_download_complete(B,audiobook:Audiobook)->_B:
		A=audiobook;D=A.source_data[_M];E=B._get_book_path(D)
		with open(E,'w')as F:G=json.dumps(A.source_data,indent=2);F.write(G)
		if A.files:
			try:
				C=gen_output_location(B.options.output_template,A.metadata,B.options.remove_chars);H=len(A.files)==1 or B.options.combine
				if H:I=B.options.output_format or A.files[0].ext;J=f"{C}.{I}";B._embed_extra_audio_metadata(J,A.source_data,A.metadata)
				elif os.path.isdir(C):
					for K in os.listdir(C):B._embed_extra_audio_metadata(os.path.join(C,K),A.source_data,A.metadata)
				if getattr(B.options,_q,_L):B._write_full_metadata_json(f"{C}.x",A.source_data,A.metadata)
			except Exception as L:logging.debug(f"Audiobook metadata embedding failed: {L}")
		M=any(A.get(_I)==_U for A in A.source_data.get(_E,[]))
		if M or getattr(B.options,'download_ebook',_L):B.download_and_save_ebook(A.source_data,A.metadata,A.cover)