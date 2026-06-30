from.source import Source
from.storytel import StorytelSource
from..exceptions import NoSourceFound
import re
from typing import Iterable,List,Type
def find_compatible_source(url:str)->Type[Source]:
	B=get_source_classes()
	for A in B:
		for(D,C)in enumerate(A.match):
			if not re.match(C,url)is None:return A
	raise NoSourceFound
def get_source_classes()->List[Type[Source]]:return[StorytelSource]
def get_source_names()->Iterable[str]:
	A:List[str]=[]
	for B in get_source_classes():
		for C in B.names:A.append(C)
	return sorted(A,key=lambda x:x.lower())