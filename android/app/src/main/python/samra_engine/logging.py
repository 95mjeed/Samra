_A=False
from rich.text import Text
from rich.style import Style
from rich.markup import render,escape
from rich.console import Console
from rich.progress import Progress,ProgressColumn
from typing import Union,List
from samra_engine.utils import read_asset_file
import traceback
debug_mode=_A
quiet_mode=_A
ffmpeg_output=_A
console=Console(stderr=True)
DEBUG_PREFIX=render('[yellow bold]DEBUG[/]')
INFO_PREFIX=render('[cyan bold] INFO[/]')
def debug(msg:str,remove_styling=_A):
	if debug_mode:
		if remove_styling:A=render(msg,style=Style(bold=_A,color='white'));console.print(DEBUG_PREFIX,A)
		else:console.print(DEBUG_PREFIX,msg)
def log(msg:str):
	if not quiet_mode:
		if debug_mode:console.print(INFO_PREFIX,msg)
		else:console.print(msg)
def book_update(msg:str):
	if debug_mode:log(msg)
	else:log(f"  {msg}")
def error(msg:str):console.print(msg)
def print_error_file(name:str,**B):A=read_asset_file(f"assets/errors/{name}.txt").format(**B);A=A.strip();error(A)
def print_asset_file(path:str):console.print(read_asset_file(path))
def simple_help()->None:print_asset_file('assets/simple_help.txt')
def progress(progress_format:List[Union[str,ProgressColumn]])->Progress:return Progress(*progress_format,console=console)
def print_traceback()->None:console.print();console.print('[underline white bold]Traceback[/]');traceback.print_exc()