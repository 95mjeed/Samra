"""Build the Samra desktop icon — IDENTICAL to the design-system app icon.

Source of truth: design/claude-design/components/app-icon/preview.html (the shipping
mark). Amber diagonal gradient tile, dark open book (Material Rounded menu_book) + dark
headphones badge with amber headphones, clipped to the EXACT squircle path from the
design (not an approximation). Output: samra.png (512) + multi-size samra.ico.

All path data (gradient, glyphs, squircle clip, transforms) is copied verbatim from
preview.html so the Windows icon matches the Android adaptive icon pixel-for-pixel."""
import io
import resvg_py
from PIL import Image

S = 512

# Squircle clip path — verbatim from preview.html (clipPath id="squircle").
SVG = '''<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="{S}" height="{S}" viewBox="0 0 108 108">
  <defs>
    <linearGradient id="amber" x1="0" y1="0" x2="108" y2="108" gradientUnits="userSpaceOnUse">
      <stop offset="0" stop-color="#ECA93C"/><stop offset="1" stop-color="#C9881F"/>
    </linearGradient>
    <clipPath id="squircle" clipPathUnits="userSpaceOnUse">
      <path d="M54,0 C16,0 0,16 0,54 C0,92 16,108 54,108 C92,108 108,92 108,54 C108,16 92,0 54,0 Z"/>
    </clipPath>
    <path id="book" d="M17.5 4.5c-1.95 0-4.05.4-5.5 1.5-1.45-1.1-3.55-1.5-5.5-1.5-1.45 0-2.99.22-4.28.79C1.49 5.62 1 6.33 1 7.14v11.28c0 1.3 1.22 2.26 2.48 1.94.98-.25 2.02-.36 3.02-.36 1.56 0 3.22.26 4.56.92.6.3 1.28.3 1.87 0 1.34-.67 3-.92 4.56-.92 1 0 2.04.11 3.02.36 1.26.33 2.48-.63 2.48-1.94V7.14c0-.81-.49-1.52-1.22-1.85-1.28-.57-2.82-.79-4.27-.79zM21 17.23c0 .63-.58 1.09-1.2.98-.75-.14-1.53-.2-2.3-.2-1.7 0-4.15.65-5.5 1.5V8c1.35-.85 3.8-1.5 5.5-1.5.92 0 1.83.09 2.7.28.46.1.8.51.8.98v9.47zM13.98 11.01c-.32 0-.61-.2-.71-.52-.13-.39.09-.82.48-.94 1.54-.5 3.53-.66 5.36-.45.41.05.71.42.66.83-.05.41-.42.71-.83.66-1.62-.19-3.39-.04-4.73.39-.08.01-.16.03-.23.03zm0 2.66c-.32 0-.61-.2-.71-.52-.13-.39.09-.82.48-.94 1.53-.5 3.53-.66 5.36-.45.41.05.71.42.66.83-.05.41-.42.71-.83.66-1.62-.19-3.39-.04-4.73.39a.97.97 0 0 1-.23.03zm0 2.66c-.32 0-.61-.2-.71-.52-.13-.39.09-.82.48-.94 1.53-.5 3.53-.66 5.36-.45.41.05.71.42.66.83-.05.41-.42.7-.83.66-1.62-.19-3.39-.04-4.73.39a.97.97 0 0 1-.23.03z"/>
    <path id="phones" d="M3 12v7c0 1.1.9 2 2 2h2c1.1 0 2-.9 2-2v-4c0-1.1-.9-2-2-2H5v-1c0-3.87 3.13-7 7-7s7 3.13 7 7v1h-2c-1.1 0-2 .9-2 2v4c0 1.1.9 2 2 2h2c1.1 0 2-.9 2-2v-7a9 9 0 0 0-18 0z"/>
    <g id="iconLauncher">
      <rect width="108" height="108" fill="url(#amber)"/>
      <use xlink:href="#book" transform="translate(31,25) scale(1.917)" fill="#1A1206"/>
      <path d="M31,56 L41,56 A9,9 0 0 1 50,65 L50,75 A9,9 0 0 1 41,84 L31,84 A9,9 0 0 1 22,75 L22,65 A9,9 0 0 1 31,56 Z" fill="#0E0F12"/>
      <path d="M32,59 L40,59 A7,7 0 0 1 47,66 L47,74 A7,7 0 0 1 40,81 L32,81 A7,7 0 0 1 25,74 L25,66 A7,7 0 0 1 32,59 Z" fill="#23262F"/>
      <use xlink:href="#phones" transform="translate(28,62) scale(0.667)" fill="#ECA93C"/>
    </g>
  </defs>
  <g clip-path="url(#squircle)"><use xlink:href="#iconLauncher"/></g>
</svg>'''.format(S=S)

png = resvg_py.svg_to_bytes(svg_string=SVG, width=S, height=S)
icon = Image.open(io.BytesIO(bytes(png))).convert("RGBA")

icon.save("samra.png")
sizes = [256, 128, 64, 48, 32, 16]
icon.save("samra.ico", sizes=[(s, s) for s in sizes])
print("wrote samra.png (512) + samra.ico", sizes, "from design preview.html squircle")
