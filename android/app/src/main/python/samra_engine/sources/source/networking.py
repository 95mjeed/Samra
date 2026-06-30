from samra_engine import AudiobookFile,exceptions,logging
from samra_engine.utils.audiobook import AESEncryption
from typing import Dict,List
import json,os,m3u8,requests
def post(self,url:str,**B)->bytes:
	A=self._session.post(url,**B)
	if A.status_code==200:return A.content
	logging.debug(f"Failed to download data from: {url}\nResponse:\n{A.content}");raise exceptions.RequestError
def get(self,url:str,force_cookies:bool=False,**D)->bytes:
	C=url;B=self
	if force_cookies:A=B._session.get(C,cookies=_get_all_cookies(B._session),**D)
	else:A=B._session.get(C,**D)
	if A.status_code==200:return A.content
	logging.debug(f"Failed to download data from: {C}\nResponse:\n{A.content}");raise exceptions.RequestError
def post_json(self,url:str,**A)->dict:B=self.post(url,**A);return json.loads(B.decode('utf8'))
def get_json(self,url:str,**A)->dict:B=self.get(url,**A);return json.loads(B.decode('utf8'))
def get_stream_files(self,url:str,headers={},extension=None)->List[AudiobookFile]:
	C=extension;B=headers;F=m3u8.load(url,headers=B);D=[]
	for(G,A)in enumerate(F.segments):
		if C is None:C=os.path.splitext(A.absolute_uri)[1][1:].split('?')[0]
		E=AudiobookFile(url=A.absolute_uri,ext=C,headers=B,expected_content_type='application/octet-stream')
		if hasattr(A.key,'method')and not A.key.method=='NONE':E.encryption_method=AESEncryption(key=self._get_page(A.key.absolute_uri,headers=B),iv=int(A.key.iv,0).to_bytes(16,byteorder='big'))
		D.append(E)
	return D
def _get_all_cookies(session:requests.Session)->Dict[str,str]:
	A={}
	for B in session.cookies:A[B.name]=str(B.value)
	return A