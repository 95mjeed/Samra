"""Minimal EPUB reader support (stdlib only): metadata + chapter plain text.

Enough to render a clean, paginated reading view and track position — not a full
rendering engine. Parses the OPF spine in order and strips each XHTML document to
paragraphs."""
import html
import os
import re
import zipfile
from xml.etree import ElementTree as ET

_NS = {"opf": "http://www.idpf.org/2007/opf", "dc": "http://purl.org/dc/elements/1.1/",
       "cnt": "urn:oasis:names:tc:entry:xmlns:xml:container"}


def _opf_path(z: zipfile.ZipFile) -> str | None:
    try:
        root = ET.fromstring(z.read("META-INF/container.xml"))
        rf = root.find(".//cnt:rootfiles/cnt:rootfile", _NS)
        if rf is not None:
            return rf.get("full-path")
    except Exception:
        pass
    # fallback: first .opf in the archive
    for n in z.namelist():
        if n.lower().endswith(".opf"):
            return n
    return None


def _read_opf(path: str):
    with zipfile.ZipFile(path) as z:
        opf = _opf_path(z)
        if not opf:
            return None, None, {}, []
        tree = ET.fromstring(z.read(opf))
        base = os.path.dirname(opf)
        meta = tree.find("opf:metadata", _NS)
        title = author = ""
        if meta is not None:
            t = meta.find("dc:title", _NS)
            a = meta.find("dc:creator", _NS)
            title = (t.text or "").strip() if t is not None else ""
            author = (a.text or "").strip() if a is not None else ""
        # manifest id -> href
        items = {}
        man = tree.find("opf:manifest", _NS)
        if man is not None:
            for it in man.findall("opf:item", _NS):
                items[it.get("id")] = it.get("href")
        # spine order
        order = []
        sp = tree.find("opf:spine", _NS)
        if sp is not None:
            for ref in sp.findall("opf:itemref", _NS):
                href = items.get(ref.get("idref"))
                if href:
                    full = os.path.normpath(os.path.join(base, href)).replace("\\", "/")
                    order.append(full)
        return title, author, items, order


def epub_metadata(path: str) -> dict:
    try:
        title, author, _, _ = _read_opf(path)
        return {"title": title or "", "author": author or ""}
    except Exception:
        return {"title": "", "author": ""}


_TAG = re.compile(r"<[^>]+>")
_BLOCK = re.compile(r"</(p|div|h[1-6]|li|blockquote|br|tr)>", re.I)
_HEAD = re.compile(r"<title[^>]*>(.*?)</title>", re.I | re.S)


def _to_paras(xhtml: str) -> tuple[str, list[str]]:
    """Return (chapter title, paragraphs) from one XHTML document."""
    title = ""
    mh = _HEAD.search(xhtml)
    if mh:
        title = html.unescape(_TAG.sub("", mh.group(1))).strip()
    # drop head/style/script blocks
    body = re.sub(r"<(head|style|script)[^>]*>.*?</\1>", " ", xhtml, flags=re.I | re.S)
    body = _BLOCK.sub("\n", body)          # block ends become line breaks
    body = _TAG.sub("", body)              # strip remaining tags
    body = html.unescape(body)
    paras = [re.sub(r"[ \t]+", " ", ln).strip() for ln in body.split("\n")]
    paras = [p for p in paras if p]
    return title, paras


def epub_chapters(path: str) -> list[dict]:
    """Ordered chapters: [{'title': str, 'paras': [str, ...]}]. Empty docs skipped."""
    try:
        _, _, _, order = _read_opf(path)
        out = []
        with zipfile.ZipFile(path) as z:
            names = {n.replace("\\", "/"): n for n in z.namelist()}
            for i, href in enumerate(order):
                real = names.get(href) or names.get(href.lstrip("/"))
                if not real:
                    continue
                try:
                    raw = z.read(real).decode("utf-8", "replace")
                except Exception:
                    continue
                title, paras = _to_paras(raw)
                if paras:
                    out.append({"title": title or f"", "paras": paras})
        return out
    except Exception:
        return []
