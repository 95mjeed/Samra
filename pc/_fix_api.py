import re, pathlib

p = pathlib.Path("app/main.py")
s = p.read_text(encoding="utf-8")

# padding.symmetric(X, Y) -> Padding.symmetric(vertical=X, horizontal=Y)
s = re.sub(r"ft\.padding\.symmetric\(\s*([^,]+?)\s*,\s*([^)]+?)\s*\)",
           r"ft.Padding.symmetric(vertical=\1, horizontal=\2)", s)
# padding.only(A, B, C, D) positional -> keyword
s = re.sub(r"ft\.padding\.only\(\s*([^,=]+?)\s*,\s*([^,=]+?)\s*,\s*([^,=]+?)\s*,\s*([^)=]+?)\s*\)",
           r"ft.Padding.only(left=\1, top=\2, right=\3, bottom=\4)", s)
# blanket capitalizations (methods exist on the classes)
s = s.replace("ft.padding.", "ft.Padding.")
s = s.replace("ft.border.all(", "ft.Border.all(")
s = s.replace("ft.border.only(", "ft.Border.only(")
# alignment enum
s = s.replace("ft.alignment.center", "ft.Alignment.CENTER")
s = s.replace("ft.alignment.bottom_left", "ft.Alignment.BOTTOM_LEFT")
s = s.replace("ft.alignment.top_left", "ft.Alignment.TOP_LEFT")
s = s.replace("ft.alignment.bottom_right", "ft.Alignment.BOTTOM_RIGHT")
# image fit
s = s.replace("ft.ImageFit.", "ft.BoxFit.")

p.write_text(s, encoding="utf-8")
print("patched. remaining lowercase refs:",
      [m for m in ["ft.padding.", "ft.alignment.", "ft.border.", "ft.ImageFit"] if m in s])
