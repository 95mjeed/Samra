import requests
from urllib.parse import urlparse
def redirect_of(url:str,amount:int=1)->str|None:
	C='location';A=url
	for D in range(amount):
		B=requests.get(A,allow_redirects=False)
		if B.status_code not in[301,302]:return
		if C not in B.headers:return
		A=B.headers[C]
		if urlparse(A).scheme!='https':return
	return A