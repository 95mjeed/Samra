import importlib.resources
from typing import Sequence
import shutil
from urllib3.poolmanager import PoolManager
from requests.adapters import HTTPAdapter
from ssl import SSLContext
def levenstein_distance(a:str,b:str)->int:
	if len(a)==0:return len(b)
	if len(b)==0:return len(a)
	if a[0]==b[0]:return levenstein_distance(a[1:],b[1:])
	return 1+min(levenstein_distance(a,b[1:]),levenstein_distance(a[1:],b),levenstein_distance(a[1:],b[1:]))
def nearest_string(input:str,list:Sequence[str])->str:return sorted(list,key=lambda x:levenstein_distance(input,x))[0]
def read_asset_file(path:str)->str:return importlib.resources.files('samra_engine').joinpath(path).read_text(encoding='utf8')
def program_in_path(program:str)->bool:return shutil.which(program)is not None
class CustomSSLContextHTTPAdapter(HTTPAdapter):
	def __init__(A,ssl_context:SSLContext,**B)->None:(A.ssl_context):SSLContext=ssl_context;super().__init__(**B)
	def init_poolmanager(A,connections,maxsize,block=False):A.poolmanager=PoolManager(num_pools=connections,maxsize=maxsize,block=block,ssl_context=A.ssl_context)