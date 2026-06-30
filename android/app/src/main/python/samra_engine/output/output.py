_C='-codec'
_B='-nostdin'
_A='ffmpeg'
from samra_engine import logging,AudiobookMetadata
from samra_engine.exceptions import FailedCombining
import os,shutil,platform,subprocess
from typing import Sequence,Mapping
LOCATION_DEFAULTS={'album':'NA','artist':'NA'}
COMBINE_CHUNK_SIZE=500
def gen_output_filename(booktitle:str,file:Mapping[str,str],template:str)->str:A={**file,**{'booktitle':booktitle}};B=template.format(**A);return _fix_output(B)
def combine_audiofiles(filepaths:Sequence[str],tmp_dir:str,output_path:str):
	D=tmp_dir;C=filepaths;B=output_path;E=get_extension(B);A=os.path.join(D,f"input_file.{E}");F=os.path.join(D,f"output_file.{E}");shutil.move(C[0],A)
	for G in range(1,len(C),COMBINE_CHUNK_SIZE):H='|'.join(C[G:G+COMBINE_CHUNK_SIZE]);subprocess.run([_A,'-y',_B,'-i',f"concat:{A}|{H}",'-safe','0',_C,'copy',F],capture_output=not logging.ffmpeg_output);os.remove(A);shutil.move(F,A)
	shutil.move(A,B)
	if not os.path.exists(B)or os.path.getsize(B)==0:raise FailedCombining
	shutil.rmtree(D)
def get_extension(path:str)->str:return os.path.splitext(path)[1][1:]
def can_copy_codec(input_format:str,output_format:str)->bool:
	C=input_format;B=output_format;A='mp3';D={(A,'m4b'),(A,'m4a'),(A,'m4p'),(A,'m4r'),(A,'mp4')}
	if(C.lstrip('.'),B.lstrip('.'))in D:return True
	return B=='mkv'or B=='mka'or C=='ts'and B==A
def convert_output(filenames:Sequence[str],output_format:str):
	C=output_format;D=[]
	for B in filenames:
		H,F=os.path.splitext(B);E=f"{H}.{C}"
		if C==F.lstrip('.'):D.append(B);continue
		A=f"{E}.converting"
		if os.path.exists(A):
			try:os.remove(A)
			except OSError:pass
		if can_copy_codec(F,C):I=['-f','mp4']if C.lstrip('.')in('m4b','m4a','m4p','m4r')else[];G=subprocess.run([_A,'-y',_B,'-i',B,_C,'copy']+I+[A],capture_output=not logging.ffmpeg_output)
		else:G=subprocess.run([_A,'-y',_B,'-i',B,A],capture_output=not logging.ffmpeg_output)
		J=getattr(G,'returncode',1)==0 and os.path.exists(A)and os.path.getsize(A)>0
		if J:
			os.replace(A,E)
			if os.path.abspath(B)!=os.path.abspath(E):
				try:os.remove(B)
				except OSError:pass
			D.append(E)
		else:
			if os.path.exists(A):
				try:os.remove(A)
				except OSError:pass
			logging.book_update('Conversion failed — keeping original format');D.append(B)
	return D
def get_max_name_length()->int:
	try:return os.pathconf('.','PC_NAME_MAX')
	except:
		try:from ctypes.wintypes import MAX_PATH as A;return A
		except:return 255
def gen_output_location(template:str,metadata:AudiobookMetadata,remove_chars:str)->str:
	H='utf-8';A=metadata;D=get_max_name_length()
	if A is None:A={}
	B=_fix_output(A.title);E=B.encode(H);I=len(E);F=9
	if I>D-F:B=E[0:D-F].decode(H,errors='ignore');logging.log(f"title to long, using [blue]{B}[/blue] as filename base")
	G={**LOCATION_DEFAULTS,**A.all_properties_dict()};G['title']=B;C=template.format(**G);C=_remove_chars(C,remove_chars);return C
def _fix_output(title:str)->str:A=title;A=''.join(A for A in A if A>=' ');A=A.replace('/','-').replace('\\','-').replace(':',' -');A=_remove_chars(A,'*?"<>|');A=' '.join(A.split());return A.strip(' .')
def _remove_chars(s:str,chars:str)->str:
	for A in chars:s=s.replace(A,'')
	return s