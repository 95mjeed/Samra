_E='{title}'
_D='ffmpeg'
_C=True
_B=False
_A=None
import json,os,shutil,subprocess,traceback
from types import SimpleNamespace
FFMPEG_PATH=_A
_orig_run=subprocess.run
_orig_which=shutil.which
def _patched_run(args,*B,**C):
	A=args
	if FFMPEG_PATH and isinstance(A,(list,tuple))and A and A[0]==_D:A=[FFMPEG_PATH]+list(A[1:])
	return _orig_run(A,*B,**C)
def _patched_which(cmd,*A,**B):
	if FFMPEG_PATH and cmd==_D:return FFMPEG_PATH
	return _orig_which(cmd,*A,**B)
subprocess.run=_patched_run
shutil.which=_patched_which
def set_ffmpeg(path):
	A=path;global FFMPEG_PATH
	if A and os.path.exists(A):
		FFMPEG_PATH=A
		try:os.chmod(A,493)
		except Exception:pass
	return bool(FFMPEG_PATH)
LOG_DIR=_A
def set_log_dir(path):
	global LOG_DIR
	if path:LOG_DIR=path
	return bool(LOG_DIR)
from samra_engine import logging as adl_logging
from samra_engine.sources import find_compatible_source,get_source_names
from samra_engine.utils.audiobook import Audiobook,Series
from samra_engine.output import output as adl_output
from samra_engine.output import download as adl_download
from samra_engine.output import metadata as adl_metadata
from samra_engine.exceptions import SamraEngineException
from samra_engine.utils import read_asset_file
try:from rich.markup import render as _rich_render
except Exception:_rich_render=_A
def _plain(msg):
	A=msg
	if A is _A:return''
	A=str(A)
	if _rich_render is not _A:
		try:return _rich_render(A).plain
		except Exception:pass
	D,B=[],0
	for C in A:
		if C=='[':B+=1
		elif C==']'and B:B-=1
		elif B==0:D.append(C)
	return''.join(D)
def _describe_exception(e):
	C='sources'
	try:
		from samra_engine import sources as D;A=dict(getattr(e,'data',{})or{});B=getattr(e,'error_description','generic')
		if B=='no_source_found'and C not in A:A[C]='\n'.join(' - '+A for A in D.get_source_names())
		E=read_asset_file('assets/errors/%s.txt'%B);return _plain(E.format(**A)).strip()
	except Exception:return _plain(str(e)or e.__class__.__name__)
class _Listener:
	def __init__(A,kt):A.kt=kt
	def log(A,msg):
		try:A.kt.onLog(_plain(msg))
		except Exception:pass
	def progress(A,fraction):
		try:A.kt.onProgress(float(fraction))
		except Exception:pass
	def book(A,title,index,total):
		try:A.kt.onBook(_plain(title),int(index),int(total))
		except Exception:pass
def _install_logging_bridge(listener):A=listener;adl_logging.debug_mode=_B;adl_logging.quiet_mode=_B;adl_logging.ffmpeg_output=_B;adl_logging.log=lambda msg:A.log(msg);adl_logging.book_update=lambda msg:A.log('  '+_plain(msg));adl_logging.error=lambda msg:A.log(_plain(msg));adl_logging.debug=lambda msg,remove_styling=_B:_A
def _make_options(output_dir,output_template,output_format,combine,library):A=output_dir;B=os.path.join(A,'.samra_db');C=os.path.join(A,output_template or _E);return SimpleNamespace(urls=[],cookie_file=_A,combine=bool(combine),output_template=C,remove_chars='',debug=_B,quiet=_B,print_output=_B,cover=_B,no_chapters=_B,output_format=output_format or _A,ffmpeg_output=_B,input_file=_A,username=_A,password=_A,library=library or _A,skip_downloaded=_B,database_directory=B,write_json_metadata=_B,config_location=_A,download_ebook=_B)
def _ffmpeg_available():return shutil.which(_D)is not _A
def _authenticate(source,url,username,password,cookie_path,listener):
	E=listener;D=cookie_path;A=source
	if D and A.supports_cookies and os.path.exists(D):E.log('Loading cookies');A.load_cookie_file(D)
	if A.supports_login and not A.authenticated:
		E.log('Logging in to '+A.name);C={}
		for B in A.login_data:
			if B=='username':C[B]=username or''
			elif B=='password':C[B]=password or''
			else:C[B]=''
		A.login(url,**C)
def _download_one(source,audiobook,options,listener):
	K=.0;J='acc';D=options;C=audiobook;A=listener;E=adl_output.gen_output_location(D.output_template,C.metadata,D.remove_chars)
	if not C.files:A.log('No audio files for this title (ebook only) — skipping.');return[]
	F=len(C.files);L={J:K}
	def N(advance):L[J]+=float(advance);B=L[J]/F if F else K;A.progress(max(K,min(1.,B)))
	if F>1:os.makedirs(E,exist_ok=_C)
	else:
		H=os.path.dirname(E)
		if H and not os.path.exists(H):os.makedirs(H,exist_ok=_C)
	A.log('Downloading audio (%d file%s)'%(F,''if F==1 else's'));B=adl_download.download_files(C,E,N);A.progress(1.);I,G=adl_download.get_output_audio_format(D.output_format,B)
	if D.combine and len(B)>1:
		if _ffmpeg_available():A.log('Combining files');M='%s.%s'%(E,I);adl_output.combine_audiofiles(B,E,M);B=[M]
		else:A.log('ffmpeg not available — keeping separate part files.')
	if I!=G:
		if _ffmpeg_available():A.log('Converting to '+G);B=adl_output.convert_output(B,G)
		else:A.log('ffmpeg not available — keeping .'+I+' (requested .'+G+').')
	_embed_metadata(B,C,D,getattr(source,'name',''),A);return B
def _embed_metadata(filepaths,audiobook,options,source_name,listener):
	E=listener;D=source_name;B=filepaths;A=audiobook
	try:
		if len(B)==1:adl_download.add_metadata_to_file(A,B[0],options)
		else:
			for C in B:
				adl_metadata.add_metadata(C,A.metadata)
				if getattr(A,'cover',_A):adl_metadata.embed_cover(C,A.cover)
		if A.chapters:E.log('Embedded %d chapter mark(s)'%len(A.chapters))
	except Exception as F:E.log('Metadata step skipped: '+str(F))
	if D:
		for C in B:
			try:_tag_source(C,D)
			except Exception:pass
def _tag_source(filepath,source_name):
	C=source_name;B=filepath;D=B.lower()
	if D.endswith('.mp3'):
		from mutagen.id3 import ID3,TXXX
		try:A=ID3(B)
		except Exception:return
		A.add(TXXX(encoding=3,desc='Source',text=[C]));A.save()
	elif any(D.endswith(A)for A in('.m4b','.m4a','.mp4','.m4p')):from mutagen.mp4 import MP4;A=MP4(B);A['----:com.apple.iTunes:Source']=[C.encode('utf-8')];A.save()
def list_sources():
	try:return json.dumps(list(get_source_names()))
	except Exception:return json.dumps([])
_SOURCE_ALIAS={'audiobooks.com':'abcom','audiobooksdotcom':'abcom','yourcloudlibrary':'cloudlib'}
def _select_creds(source,creds_json,username,password,cookie_path):
	F=creds_json;C,D,G=username,password,cookie_path
	if F:
		try:
			E=json.loads(F);A=source.name;B=E.get(A)or E.get(_SOURCE_ALIAS.get(A,A))or E.get(A.replace('.',''))
			if B:
				C=B.get('u')or C;D=B.get('p')or D;H=B.get('c')
				if H:G=H
		except Exception:pass
	return C,D,G
def _log_error(output_dir,url,msg,trace=''):
	B=trace
	try:
		import time as C;D=LOG_DIR or output_dir;E=os.path.join(D,'.samra_errors.log')
		with open(E,'a',encoding='utf-8')as A:
			A.write('==== %s ====\nURL:   %s\nERROR: %s\n'%(C.strftime('%Y-%m-%d %H:%M:%S'),url,msg))
			if B:A.write(B+'\n')
			A.write('\n')
	except Exception:pass
def run_download(url,username,password,cookie_path,output_dir,output_format,combine,library,ffmpeg_path,creds_json,kt_listener):
	U='Error: ';T='https://';P='error';O='files';N='ok';F=output_dir;B=url;A=_Listener(kt_listener);_install_logging_bridge(A);set_ffmpeg(ffmpeg_path);G=[];J=_A
	try:
		os.makedirs(F,exist_ok=_C)
		try:J=os.getcwd();os.chdir(F)
		except Exception:J=_A
		K=_make_options(F,_E,output_format,combine,library);B=B.strip()
		if not(B.startswith('http://')or B.startswith(T)):B=T+B
		A.log('Finding compatible source');V=find_compatible_source(B);C=V(K);A.log('Source: '+C.name)
		if C.requires_authentication and not C.authenticated:W,X,Y=_select_creds(C,creds_json,username,password,cookie_path);_authenticate(C,B,W,X,Y,A)
		A.log('Fetching book details');D=C.download(B)
		if isinstance(D,Series):
			Q=D.books;L=len(Q);A.log("Series '%s' — %d book(s)"%(_plain(D.title),L))
			for(R,M)in enumerate(Q):
				try:
					if isinstance(M,Audiobook):I=M
					else:I=C.download_from_id(M.id)
					A.book(I.title,R+1,L);A.log('[%d/%d] %s'%(R+1,L,_plain(I.title)));G+=_download_one(C,I,K,A);C.on_download_complete(I)
				except SamraEngineException as E:H=_describe_exception(E);A.log('Skipped a book: '+H);_log_error(F,B,'skipped book: '+H);continue
		elif isinstance(D,Audiobook):A.book(D.title,1,1);A.log('Downloading: '+_plain(D.title));G+=_download_one(C,D,K,A);C.on_download_complete(D)
		else:raise RuntimeError('Unexpected result type from source')
		A.log('Done. %d file(s) saved.'%len(G));return json.dumps({N:_C,O:G,P:_A})
	except SamraEngineException as E:H=_describe_exception(E);A.log(U+H);_log_error(F,B,H);return json.dumps({N:_B,O:G,P:H})
	except Exception as E:S=traceback.format_exc();A.log(U+str(E));_log_error(F,B,str(E),S);return json.dumps({N:_B,O:G,P:str(E),'trace':S})
	finally:
		if J:
			try:os.chdir(J)
			except Exception:pass