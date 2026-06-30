from.import id3,mp4,ffmpeg
from samra_engine import logging,Chapter,AudiobookMetadata,Cover
from samra_engine.utils import program_in_path
import os
from typing import Sequence
def add_metadata(filepath:str,metadata:AudiobookMetadata):
	B=metadata;A=filepath
	if id3.is_id3_file(A):id3.add_id3_metadata(A,B)
	elif mp4.is_mp4_file(A):mp4.add_mp4_metadata(A,B)
	else:logging.debug('Could not add any metadata')
def embed_cover(filepath:str,cover:Cover):
	B=cover;A=filepath
	if id3.is_id3_file(A):id3.embed_id3_cover(A,B)
	elif mp4.is_mp4_file(A):mp4.embed_mp4_cover(A,B)
	else:logging.debug('Could not embed cover')
def add_chapters(filepath:str,chapters:Sequence[Chapter]):
	B=chapters;A=filepath
	if id3.is_id3_file(A):id3.add_id3_chapters(A,B)
	elif program_in_path('ffmpeg'):ffmpeg.add_chapters_ffmpeg(A,B)
	elif logging.debug_mode:logging.debug('Could not add chapters')
	else:C=os.path.splitext(A)[1][1:];logging.print_error_file('chapters_add',filetype=C)