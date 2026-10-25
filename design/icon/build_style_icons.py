"""
The Songlass launcher icon, from one description.

  violet  white bars radiating from a yellow heart on violet: the icon for every interface style
  night   the same drawing in gold bars and a red heart on near-black: an optional alternative
  liquid/material/miuix light/dark: bundled finishes of the same original drawing

Each icon has a background layer, a foreground layer and a one-colour monochrome layer on Android's
108-unit adaptive grid (safe zone: the central 66 units). Paths, flat fills and subtle vector gradients
share one description for SVG previews and Android VectorDrawables. Legacy palettes are unchanged.

  python3 design/icon/build_style_icons.py          # SVG previews in design/icon/styles/
  python3 design/icon/build_style_icons.py --apply  # also the Android resources, the splash, the iOS icons
                                                    # and the marks shown inside the app
"""
import hashlib
import json
import math
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "androidApp/src/main/res"
OUT = ROOT / "design/icon/styles"
C = 54.0


# --- geometry ---------------------------------------------------------------------------------

def circle(cx, cy, r):
    return f"M{cx - r:.2f},{cy:.2f}a{r:.2f},{r:.2f} 0 1,0 {2 * r:.2f},0a{r:.2f},{r:.2f} 0 1,0 {-2 * r:.2f},0z"


def capsule(cx, cy, w, h):
    r = w / 2
    return (f"M{cx - r:.2f},{cy - h / 2 + r:.2f}a{r:.2f},{r:.2f} 0 0,1 {w:.2f},0v{h - w:.2f}"
            f"a{r:.2f},{r:.2f} 0 0,1 {-w:.2f},0z")


def rect(x, y, w, h):
    return f"M{x:.2f},{y:.2f}h{w:.2f}v{h:.2f}h{-w:.2f}z"


def radial_bar(angle_deg, r0, r1, width):
    """A capsule lying along the ray at [angle_deg], from radius r0 to r1 (round caps included)."""
    t = math.radians(angle_deg)
    ux, uy = math.cos(t), math.sin(t)
    nx, ny = -uy, ux
    rad = width / 2
    # The cap centres sit one radius inside each end.
    ax, ay = C + ux * (r0 + rad), C + uy * (r0 + rad)
    bx, by = C + ux * (r1 - rad), C + uy * (r1 - rad)
    return (f"M{ax + nx * rad:.2f},{ay + ny * rad:.2f}L{bx + nx * rad:.2f},{by + ny * rad:.2f}"
            f"A{rad:.2f},{rad:.2f} 0 0,0 {bx - nx * rad:.2f},{by - ny * rad:.2f}"
            f"L{ax - nx * rad:.2f},{ay - ny * rad:.2f}A{rad:.2f},{rad:.2f} 0 0,0 {ax + nx * rad:.2f},{ay + ny * rad:.2f}z")


# Ten bars, lengths alternating around the circle; the heart is the single thing in the middle.
BAR_LENGTHS = [19, 12, 15, 9, 13, 18, 13, 9, 15, 12]
BAR_WIDTH, BAR_INNER = 10.2, 21.0
HEART = [  # unit-space cubic segments, y down; tip at (0, 0.92)
    ("M", (0, 0.92)),
    ("C", (-1.15, 0.12), (-1.32, -0.58), (-0.68, -0.78)),
    ("C", (-0.32, -0.88), (0, -0.66), (0, -0.42)),
    ("C", (0, -0.66), (0.32, -0.88), (0.68, -0.78)),
    ("C", (1.32, -0.58), (1.15, 0.12), (0, 0.92)),
]


def heart(scale, cx=C, cy=C + 1.5, size=16.5):
    """The heart, [scale] being the overall scale of the glyph around the icon centre."""
    def pt(p): return f"{C + (cx - C + p[0] * size) * scale:.2f},{C + (cy - C + p[1] * size) * scale:.2f}"
    out = []
    for seg in HEART:
        out.append(seg[0] + (" ".join(pt(p) for p in seg[1:]) if seg[0] == "C" else pt(seg[1])))
    return "".join(out) + "z"


def glyph_bars(scale):
    n = len(BAR_LENGTHS)
    return [radial_bar(-90 + i * 360 / n, BAR_INNER * scale, (BAR_INNER + length) * scale, BAR_WIDTH * scale)
            for i, length in enumerate(BAR_LENGTHS)]


# --- paint ------------------------------------------------------------------------------------

class Lin:
    def __init__(self, x1, y1, x2, y2, stops): self.x1, self.y1, self.x2, self.y2, self.stops = x1, y1, x2, y2, stops


class Rad:
    def __init__(self, cx, cy, r, stops): self.cx, self.cy, self.r, self.stops = cx, cy, r, stops


class Shape:
    """fill: "RRGGBB", (hex, alpha) or a gradient; stops are (offset, (hex, alpha))."""
    def __init__(self, d, fill=None, stroke=None, stroke_width=0.0, evenodd=False):
        self.d, self.fill, self.stroke, self.stroke_width, self.evenodd = d, fill, stroke, stroke_width, evenodd


def a(hex_, alpha=1.0): return (hex_, alpha)


# --- the icons --------------------------------------------------------------------------------

# 0.82 puts the tip of the longest bar on the edge of Android's 66-unit safe zone.
SAFE = 0.82

PALETTES = {
    #          background  bars      heart
    "violet": ("6C3BEE", "FFFFFF", "FFD23F"),
    "night": ("0D0B14", "FFD23F", "FF2A2A"),
}


def icon(name, scale=SAFE):
    bg, bars_color, heart_color = PALETTES[name]
    background = [Shape(rect(0, 0, 108, 108), bg)]
    foreground = [Shape(b, bars_color) for b in glyph_bars(scale)] + [Shape(heart(scale), heart_color)]
    mono = [Shape(b, "FFFFFF") for b in glyph_bars(scale)] + [Shape(heart(scale), "FFFFFF")]
    return background, foreground, mono


def violet(): return icon("violet")
def night(): return icon("night")


# Bundled alternatives share the exact same heart and ten-bar geometry.
VARIANT_PALETTES = {
    "liquid_light": (Lin(12, 0, 96, 108, [(0, a("FFFFFF")), (0.48, a("ECEEF5")), (1, a("CFD6E3"))]), "58657D", "D45561"),
    "liquid_dark": (Lin(8, 0, 100, 108, [(0, a("414653")), (0.5, a("242833")), (1, a("11151E"))]), "E8EDF6", "FFD16C"),
    "material_light": ("DDE3FF", "485B95", "A93650"),
    "material_dark": ("26324F", "B7C7FF", "FFB4AB"),
    "miuix_light": ("16B9C2", "FFFFFF", "9B173F"),
    "miuix_dark": ("DE626B", "FFF0DF", "234E60"),
}


def variant_icon(name, scale=SAFE):
    bg, bars, centre = VARIANT_PALETTES[name]
    return ([Shape(rect(0, 0, 108, 108), bg)],
            [Shape(b, bars) for b in glyph_bars(scale)] + [Shape(heart(scale), centre)],
            [Shape(b, "FFFFFF") for b in glyph_bars(scale)] + [Shape(heart(scale), "FFFFFF")])


ICONS = {"violet": violet, "night": night,
         **{name: (lambda name=name: variant_icon(name)) for name in VARIANT_PALETTES}}
IOS_SETS = {
    "night": "AppIconNight", "liquid_light": "AppIconLiquidLight", "liquid_dark": "AppIconLiquidDark",
    "material_light": "AppIconMaterialLight", "material_dark": "AppIconMaterialDark",
    "miuix_light": "AppIconMiuixLight", "miuix_dark": "AppIconMiuixDark",
}



# --- writers ----------------------------------------------------------------------------------

def _stops(stops):
    return "".join(f'<stop offset="{o}" stop-color="#{c}" stop-opacity="{al}"/>' for o, (c, al) in stops)


def svg_layers(layers, defs, counter):
    out = []
    for s in layers:
        def paint(p, opaque=True):
            if p is None: return 'none', 1.0
            if isinstance(p, str): return f"#{p}", 1.0
            if isinstance(p, tuple): return f"#{p[0]}", p[1]
            counter[0] += 1; gid = f"g{counter[0]}"
            if isinstance(p, Lin):
                defs.append(f'<linearGradient id="{gid}" gradientUnits="userSpaceOnUse" x1="{p.x1}" y1="{p.y1}" x2="{p.x2}" y2="{p.y2}">{_stops(p.stops)}</linearGradient>')
            else:
                defs.append(f'<radialGradient id="{gid}" gradientUnits="userSpaceOnUse" cx="{p.cx}" cy="{p.cy}" r="{p.r}">{_stops(p.stops)}</radialGradient>')
            return f"url(#{gid})", 1.0
        fill, fa = paint(s.fill); stroke, _ = paint(s.stroke)
        extra = f' stroke="{stroke}" stroke-width="{s.stroke_width}"' if s.stroke is not None else ""
        rule = ' fill-rule="evenodd"' if s.evenodd else ""
        out.append(f'<path d="{s.d}" fill="{fill}" fill-opacity="{fa}"{rule}{extra}/>')
    return "".join(out)


def svg(background, foreground, mask="squircle", size=108):
    defs, counter = [], [0]
    bg = svg_layers(background, defs, counter); fg = svg_layers(foreground, defs, counter)
    clip = {"squircle": '<rect x="0" y="0" width="108" height="108" rx="26" ry="26"/>',
            "circle": '<circle cx="54" cy="54" r="54"/>', "none": '<rect width="108" height="108"/>'}[mask]
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" viewBox="0 0 108 108">'
            f'<defs>{"".join(defs)}<clipPath id="m">{clip}</clipPath></defs><g clip-path="url(#m)">{bg}{fg}</g></svg>')


def vd_paint(p, attr="fillColor"):
    def argb(h, al): return f"#{int(round(al * 255)):02X}{h}"
    if isinstance(p, str): return f'android:{attr}="#FF{p}"'
    if isinstance(p, tuple): return f'android:{attr}="{argb(*p)}"'
    items = "".join(f'<item android:offset="{o}" android:color="{argb(c, al)}"/>' for o, (c, al) in p.stops)
    if isinstance(p, Lin):
        g = f'<gradient android:type="linear" android:startX="{p.x1}" android:startY="{p.y1}" android:endX="{p.x2}" android:endY="{p.y2}">{items}</gradient>'
    else:
        g = f'<gradient android:type="radial" android:centerX="{p.cx}" android:centerY="{p.cy}" android:gradientRadius="{p.r}">{items}</gradient>'
    return f'<aapt:attr name="android:{attr}">{g}</aapt:attr>'


def vector_paths(layers):
    body = []
    for s in layers:
        attrs = [f'android:pathData="{s.d}"']
        inner = ""
        if s.fill is not None:
            if isinstance(s.fill, (str, tuple)): attrs.append(vd_paint(s.fill))
            else: inner += vd_paint(s.fill)
        if s.stroke is not None:
            attrs.append(f'android:strokeWidth="{s.stroke_width}"')
            if isinstance(s.stroke, (str, tuple)): attrs.append(vd_paint(s.stroke, "strokeColor"))
            else: inner += vd_paint(s.stroke, "strokeColor")
        if s.evenodd: attrs.append('android:fillType="evenOdd"')
        body.append("<path " + " ".join(attrs) + (f">{inner}</path>" if inner else "/>"))
    return "".join(body)


def vector(layers):
    return ('<?xml version="1.0" encoding="utf-8"?>\n<!-- Generated by design/icon/build_style_icons.py: edit the script, not this file. -->\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android" xmlns:aapt="http://schemas.android.com/aapt" '
            'android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">'
            + vector_paths(layers) + "</vector>\n")


def splash():
    """A violet disc whose bars grow outwards one after the other, then the heart pops in."""
    disc_r = 30.0
    fit = 35.4 / disc_r  # a hair inside the 36-unit circle the system shows, so the rim is not clipped
    scale = 0.72  # the glyph sits well inside the disc
    disc = Shape(circle(C, C, disc_r), PALETTES["violet"][0])
    bars_ = [Shape(b, "FFFFFF") for b in glyph_bars(scale)]
    heart_ = Shape(heart(scale), PALETTES["violet"][2])
    n = len(bars_)
    groups = "".join(
        f'<group android:name="bar{i}" android:pivotX="{C}" android:pivotY="{C}" android:scaleX="0" android:scaleY="0">{vector_paths([bar])}</group>'
        for i, bar in enumerate(bars_))
    heart_group = (f'<group android:name="heart" android:pivotX="{C}" android:pivotY="{C}" android:scaleX="0" android:scaleY="0">'
                   f'{vector_paths([heart_])}</group>')
    drawable = (f'<vector xmlns:android="http://schemas.android.com/apk/res/android" '
                f'android:width="240dp" android:height="240dp" android:viewportWidth="108" android:viewportHeight="108">'
                f'<group android:pivotX="{C}" android:pivotY="{C}" android:scaleX="{fit:.3f}" android:scaleY="{fit:.3f}">'
                f'<group android:name="disc" android:pivotX="{C}" android:pivotY="{C}" android:scaleX="0.6" android:scaleY="0.6">{vector_paths([disc])}</group>'
                f'{groups}{heart_group}</group></vector>')

    def grow(name, offset, duration=320):
        return (f'<target android:name="{name}"><aapt:attr name="android:animation"><set android:ordering="together">'
                + "".join(f'<objectAnimator android:propertyName="{p}" android:valueFrom="0" android:valueTo="1" '
                          f'android:startOffset="{offset}" android:duration="{duration}" '
                          f'android:interpolator="@android:anim/overshoot_interpolator"/>' for p in ("scaleX", "scaleY"))
                + '</set></aapt:attr></target>')
    targets = "".join(grow(f"bar{i}", 160 + i * 45) for i in range(n)) + grow("heart", 160 + n * 45 + 40, 380)
    return ('<?xml version="1.0" encoding="utf-8"?>\n<!-- Generated by design/icon/build_style_icons.py: edit the script, not this file. -->\n'
            '<animated-vector xmlns:android="http://schemas.android.com/apk/res/android" xmlns:aapt="http://schemas.android.com/aapt">'
            f'<aapt:attr name="android:drawable">{drawable}</aapt:attr>'
            '<target android:name="disc"><aapt:attr name="android:animation"><set android:ordering="together">'
            '<objectAnimator android:propertyName="scaleX" android:valueFrom="0.6" android:valueTo="1" android:duration="420" android:interpolator="@android:anim/overshoot_interpolator"/>'
            '<objectAnimator android:propertyName="scaleY" android:valueFrom="0.6" android:valueTo="1" android:duration="420" android:interpolator="@android:anim/overshoot_interpolator"/>'
            f'</set></aapt:attr></target>{targets}</animated-vector>\n')


def render_png(background, foreground, size, path, mask="none"):
    """Rasterise deterministic SVG via the existing renderer; no metadata or temporary files."""
    import subprocess
    payload = svg(background, foreground, mask).encode("utf-8")
    result = subprocess.run(["rsvg-convert", "-w", str(size), "-h", str(size)],
                            input=payload, stdout=subprocess.PIPE, check=True)
    path.write_bytes(result.stdout)


def ios_contents():
    return {"images": [
        {"idiom": "universal", "platform": "ios", "size": "1024x1024", "filename": "AppIcon.png"},
        {"idiom": "universal", "platform": "ios", "size": "1024x1024", "filename": "AppIcon-dark.png",
         "appearances": [{"appearance": "luminosity", "value": "dark"}]},
        {"idiom": "universal", "platform": "ios", "size": "1024x1024", "filename": "AppIcon-tinted.png",
         "appearances": [{"appearance": "luminosity", "value": "tinted"}]},
    ], "info": {"author": "xcode", "version": 1}}


def write_apply():
    bg, fg, mono = violet()
    night_bg, night_fg, _ = night()
    # Legacy assets retain their original geometry, colours and rendering.
    (RES / "mipmap-anydpi/ic_launcher.xml").write_text((RES / "mipmap-anydpi/ic_launcher_violet.xml").read_text())
    (RES / "drawable/splash_icon.xml").write_text(splash())
    ios = ROOT / "iosApp/iosApp/Assets.xcassets/AppIcon.appiconset"
    render_png(bg, fg, 1024, ios / "AppIcon.png")
    deep = [Shape(rect(0, 0, 108, 108), "2A1472")]
    render_png(deep, fg, 1024, ios / "AppIcon-dark.png")
    tinted_bg = [Shape(rect(0, 0, 108, 108), "141414")]
    tinted_fg = [Shape(b, "FFFFFF") for b in glyph_bars(SAFE)] + [Shape(heart(SAFE), "C8C8C8")]
    render_png(tinted_bg, tinted_fg, 1024, ios / "AppIcon-tinted.png")
    shared = ROOT / "shared/src/commonMain/composeResources/drawable"
    render_png(bg, fg, 256, shared / "songlass_brand_light.png")
    render_png(deep, fg, 256, shared / "songlass_brand_dark.png")
    render_png(night_bg, night_fg, 256, shared / "songlass_icon_night.png")
    for name, asset_name in IOS_SETS.items():
        icon_bg, icon_fg, _ = ICONS[name]()
        dark_name = name.replace("_light", "_dark")
        dark_bg, dark_fg, _ = ICONS[dark_name]()
        target = ios.parent / (asset_name + ".appiconset")
        target.mkdir(exist_ok=True)
        (target / "Contents.json").write_text(json.dumps(ios_contents(), indent=2) + "\n")
        render_png(icon_bg, icon_fg, 1024, target / "AppIcon.png")
        render_png(dark_bg, dark_fg, 1024, target / "AppIcon-dark.png")
        render_png(tinted_bg, tinted_fg, 1024, target / "AppIcon-tinted.png")
        if name != "night":
            render_png(icon_bg, icon_fg, 256, shared / f"songlass_icon_{name}.png")


def generated_paths(apply):
    """Explicit allowlist; unknown files are never removed or inventoried as generated."""
    paths = [OUT / f"{name}{suffix}.svg" for name in ICONS for suffix in ("", "-squircle", "-circle")]
    if apply:
        paths += [RES / f"drawable/ic_launcher_{name}_{layer}.xml" for name in ICONS
                  for layer in ("background", "foreground", "monochrome")]
        paths += [RES / f"mipmap-anydpi/ic_launcher_{name}.xml" for name in ICONS]
        paths += [RES / "mipmap-anydpi/ic_launcher.xml", RES / "drawable/splash_icon.xml"]
        assets = ROOT / "iosApp/iosApp/Assets.xcassets"
        paths += [assets / f"{asset}.appiconset/{filename}" for asset in ("AppIcon", *IOS_SETS.values())
                  for filename in ("Contents.json", "AppIcon.png", "AppIcon-dark.png", "AppIcon-tinted.png")]
        shared = ROOT / "shared/src/commonMain/composeResources/drawable"
        paths += [shared / filename for filename in ("songlass_brand_light.png", "songlass_brand_dark.png", "songlass_icon_night.png")]
        paths += [shared / f"songlass_icon_{name}.png" for name in VARIANT_PALETTES]
    return paths


def main():
    apply = "--apply" in sys.argv
    OUT.mkdir(parents=True, exist_ok=True)
    for name, make in ICONS.items():
        bg, fg, mono = make()
        for suffix, mask in (("", "none"), ("-squircle", "squircle"), ("-circle", "circle")):
            (OUT / f"{name}{suffix}.svg").write_text(svg(bg, fg, mask))
        if apply:
            for layer, shapes in (("background", bg), ("foreground", fg), ("monochrome", mono)):
                (RES / f"drawable/ic_launcher_{name}_{layer}.xml").write_text(vector(shapes))
            (RES / f"mipmap-anydpi/ic_launcher_{name}.xml").write_text(
                '<?xml version="1.0" encoding="utf-8"?>\n<!-- Generated by design/icon/build_style_icons.py -->\n'
                '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
                f'    <background android:drawable="@drawable/ic_launcher_{name}_background" />\n'
                f'    <foreground android:drawable="@drawable/ic_launcher_{name}_foreground" />\n'
                f'    <monochrome android:drawable="@drawable/ic_launcher_{name}_monochrome" />\n'
                '</adaptive-icon>\n')
    if apply:
        write_apply()
    manifest = {"generator": "design/icon/build_style_icons.py", "applied": apply,
                "variants": list(ICONS), "files": [
                    {"path": str(p.relative_to(ROOT)), "sha256": hashlib.sha256(p.read_bytes()).hexdigest(),
                     "bytes": p.stat().st_size} for p in generated_paths(apply)]}
    (ROOT / "design/icon/generated-manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")


if __name__ == "__main__":
    main()
