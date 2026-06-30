from Crypto.Cipher import AES
from samra_engine.utils.audiobook import AudiobookFileEncryption,AESEncryption
def decrypt_file(path:str,encryption_method:AudiobookFileEncryption):
	A=encryption_method
	if isinstance(A,AESEncryption):decrypt_file_aes(path,A.key,A.iv)
def decrypt_file_aes(path:str,key:bytes,iv:bytes):
	with open(path,'rb')as A:B=AES.new(key,AES.MODE_CBC,iv);C=B.decrypt(A.read())
	with open(path,'wb')as A:A.write(C)