from samra_engine import Chapter,utils,logging
from mutagen import File as MutagenFile
import subprocess,os
from typing import Sequence
TMP_CHAPTER_FILE='chapters.tmp.txt'
TMP_MEDIA_FILE='audiobook.tmp.mp4'
def _escape_ffmetadata(value:str)->str:A=value;A=''.join(A for A in str(A)if A>=' ');return A.replace('\\','\\\\').replace('=','\\=').replace(';','\\;').replace('#','\\#')
def create_chapter_text(title:str,start:int,end:int)->str:A=utils.read_asset_file('assets/ffmpeg_chapter_template.txt');return A.format(title=_escape_ffmetadata(title),start=start,end=end)
def create_tmp_chapter_file(filepath:str,chapters:Sequence[Chapter])->str:
	A=chapters;B=';FFMETADATA1\n'
	for C in range(len(A)-1):D=A[C];B+=create_chapter_text(D.title,D.start,A[C+1].start)
	F=MutagenFile(filepath).info.length*1000;E=A[-1];B+=create_chapter_text(title=E.title,start=E.start,end=int(F));return B
def add_chapters_ffmpeg(filepath:str,chapters:Sequence[Chapter]):
	A=filepath
	try:
		with open(TMP_CHAPTER_FILE,'w',encoding='utf-8')as B:B.write(create_tmp_chapter_file(A,chapters))
		subprocess.run(['ffmpeg','-y','-i',A,'-i',TMP_CHAPTER_FILE,'-map_chapters','1','-c','copy','-map','0','-metadata:s:a:0','title=',TMP_MEDIA_FILE],capture_output=not logging.ffmpeg_output);os.remove(A);os.rename(TMP_MEDIA_FILE,A)
	finally:os.remove(TMP_CHAPTER_FILE)