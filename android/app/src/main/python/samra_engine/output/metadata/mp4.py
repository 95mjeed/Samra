_B='publisher'
_A='narrator'
import re
from datetime import date
from samra_engine import logging,AudiobookMetadata,Cover
from mutagen.easymp4 import EasyMP4,EasyMP4Tags
from mutagen.mp4 import MP4,MP4Cover,Chapter as MP4Chapter,MP4Chapters
MP4_EXTENSIONS=['mp4','m4a','m4p','m4b','m4r','m4v']
MP4_CONVERT={'authors':'artist','narrators':_A,'publishers':_B,'series':'album','title':'title','genres':'genre'}
MP4_COVER_FORMATS={'jpg':MP4Cover.FORMAT_JPEG,'jpeg':MP4Cover.FORMAT_JPEG,'png':MP4Cover.FORMAT_PNG}
EasyMP4Tags.RegisterTextKey('year','yrrc')
EasyMP4Tags.RegisterTextKey(_A,'©nrt')
EasyMP4Tags.RegisterTextKey(_B,'©pub')
EasyMP4Tags.RegisterTextKey('track','©trk')
EasyMP4Tags.RegisterFreeformKey('scrape_url','URL')
def is_mp4_file(filepath:str)->bool:A=re.search('(?<=(\\.))\\w+$',filepath);return A is not None and A.group(0)in MP4_EXTENSIONS
def add_mp4_metadata(filepath:str,metadata:AudiobookMetadata):
	E='language';B=EasyMP4(filepath)
	for(A,C)in metadata.all_properties(allow_duplicate_keys=None):
		if A=='release_date':D:date=C;B['date']=D.strftime('%Y-%m-%d');B['year']=str(D.year)
		elif A==E:B.tags.RegisterFreeformKey(A,A.capitalize());B[E]=C.alpha_3
		elif A=='series_order':B['track']=str(C)
		elif A in MP4_CONVERT:B[MP4_CONVERT[A]]=C
		elif A in B.Get.keys():B[A]=C
		else:B.tags.RegisterFreeformKey(A,A.capitalize());B[A]=C
	B.save()
def embed_mp4_cover(filepath:str,cover:Cover):
	A=cover
	if not A.extension in MP4_COVER_FORMATS:return
	B=MP4(filepath);B['covr']=[MP4Cover(A.image,imageformat=MP4_COVER_FORMATS[A.extension])];B.save()