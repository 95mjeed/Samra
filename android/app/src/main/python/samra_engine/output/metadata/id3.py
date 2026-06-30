_D='image/jpeg'
_C='commercialurl'
_B='comment'
_A='WCOM'
import re,os
from datetime import date
from samra_engine import logging,Chapter,AudiobookMetadata,Cover
from mutagen import File as MutagenFile
from mutagen.easyid3 import EasyID3,EasyID3KeyError
from mutagen.mp3 import MP3
from mutagen.id3 import ID3,APIC,CHAP,TIT2,CTOC,CTOCFlags,WCOM,ID3NoHeaderError
from requests import utils
from typing import Sequence
EasyID3.RegisterTextKey(_B,'COMM')
EasyID3.RegisterTextKey('year','TYER')
EasyID3.RegisterTextKey('originalreleaseyear','TORY')
EasyID3.RegisterTXXXKey('isbn','ISBN')
def commercialurl_get(id3,key):
	A=[A.url for A in id3.getall(_A)]
	if A:return A
	else:raise EasyID3KeyError(key)
def commercialurl_set(id3,key,value):
	id3.delall(_A)
	for A in value:B=utils.requote_uri(A);id3.add(WCOM(url=B))
def commercialurl_delete(id3,key):id3.delall(_A)
EasyID3.RegisterKey(_C,commercialurl_get,commercialurl_set,commercialurl_delete)
ID3_CONVERT={'authors':'artist','series':'album','title':'title','publisher':'organization','description':_B,'genres':'genre','scrape_url':_C}
ID3_FORMATS=['mp3']
EXTENSION_TO_MIMETYPE={'jpeg':_D,'jpg':_D,'png':'image/png'}
def is_id3_file(filepath:str)->bool:A=re.search('(?<=(\\.))\\w+$',filepath);return A is not None and A.group(0)in ID3_FORMATS
def add_id3_metadata(filepath:str,metadata:AudiobookMetadata):
	E='language';D='originaldate';A=MP3(filepath,ID3=EasyID3)
	for(B,C)in metadata.all_properties(allow_duplicate_keys=None):
		if B=='release_date':A[D]=C.strftime('%Y-%m-%d');A['year']=A[D]
		elif B==E:A[E]=C.alpha_3
		elif B=='narrators':A['composer']=C;A['performer']=C
		elif B=='series_order':A['tracknumber']=str(C)
		elif B in ID3_CONVERT:A[ID3_CONVERT[B]]=C
		elif B in EasyID3.valid_keys.keys():A[B]=C
	A.save(v2_version=4)
def embed_id3_cover(filepath:str,cover:Cover):
	A=cover;C=EXTENSION_TO_MIMETYPE[A.extension]
	try:B=ID3(filepath)
	except ID3NoHeaderError:return
	B.add(APIC(type=0,data=A.image,mime=C));B.save()
def add_id3_chapter(audio:ID3,start:int,end:int,title:str,index:int):audio.add(CHAP(element_id='chp'+str(index),start_time=int(start),end_time=int(end),sub_frames=[TIT2(text=[title])]))
def add_id3_chapters(filepath:str,chapters:Sequence[Chapter]):
	D=filepath;A=chapters;C=ID3(D)
	for B in range(len(A)-1):add_id3_chapter(C,start=A[B].start,end=A[B+1].start,title=A[B].title,index=B+1)
	E=MutagenFile(D).info.length*1000;add_id3_chapter(C,A[-1].start,E,A[-1].title,len(A));C.save()