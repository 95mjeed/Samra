# -*- mode: python ; coding: utf-8 -*-
from PyInstaller.utils.hooks import collect_all

# Bundle just_playback (in-app audio) incl. its native miniaudio library, and
# mutagen (tag/duration reading). collect_all grabs binaries + data + submodules.
_extra_bins, _extra_datas = [], []
# _ma_playback (cffi native module) + tinytag are imported by just_playback but
# live at top level — list them explicitly so they're never dropped.
_extra_hidden = ['app.epub', 'mutagen', 'just_playback', '_ma_playback',
                 'tinytag', '_cffi_backend']
for _pkg in ('just_playback', 'mutagen', 'tinytag'):
    _d, _b, _h = collect_all(_pkg)
    _extra_datas += _d; _extra_bins += _b; _extra_hidden += _h

a = Analysis(
    ['run.py'],
    pathex=[],
    binaries=_extra_bins,
    datas=[('samra_engine/assets', 'samra_engine/assets'), ('C:\\Users\\mjeed1\\AppData\\Local\\Programs\\Python\\Python312\\Lib\\site-packages\\pycountry/databases', 'pycountry/databases'), ('C:\\Users\\mjeed1\\AppData\\Local\\Programs\\Python\\Python312\\Lib\\site-packages\\pycountry/locales', 'pycountry/locales')] + _extra_datas,
    hiddenimports=_extra_hidden,
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludes=[],
    noarchive=False,
    optimize=0,
)
pyz = PYZ(a.pure)

exe = EXE(
    pyz,
    a.scripts,
    [],
    exclude_binaries=True,
    name='Samra',
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=True,
    console=False,
    disable_windowed_traceback=False,
    argv_emulation=False,
    target_arch=None,
    codesign_identity=None,
    entitlements_file=None,
    version=None,
    icon=['samra.ico'],
)
coll = COLLECT(
    exe,
    a.binaries,
    a.datas,
    strip=False,
    upx=True,
    upx_exclude=[],
    name='Samra',
)
