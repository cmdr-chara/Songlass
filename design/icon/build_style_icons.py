"""
Three launcher icons, one per interface style, from one description each.

  liquid    five glass bars over a deep violet: iOS 26 Liquid Glass, with no container
  material  a light lavender square, a vivid expressive "cookie" and white bars: Material 3 Expressive
  miuix     bold white bars on a blue-violet gradient: HyperOS' flat, confident glyphs

Each icon has a background layer, a foreground layer and a one-colour monochrome layer on Android's
108-unit adaptive grid (safe zone: the central 66 units). Only paths, linear/radial gradients and
even-odd fills are used, so the same description writes the SVG previews and the VectorDrawables.

  python3 design/icon/build_style_icons.py          # SVG previews in design/icon/styles/
  python3 design/icon/build_style_icons.py --apply  # also the Android resources, the splash, the iOS icons
                                                    # and the marks shown inside the app
"""
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


def cookie(cx, cy, r, lobes=9, depth=0.1, steps=240):
    pts = []
    for i in range(steps):
        t = i / steps * 2 * math.pi
        wave = (math.cos(lobes * t) + 1) / 2
        rr = r * (1 - depth * (1 - wave))
        pts.append((cx + rr * math.cos(t), cy + rr * math.sin(t)))
    return "M" + "L".join(f"{x:.2f},{y:.2f}" for x, y in pts) + "z"


def arc_band(cx, cy, r_out, r_in, a0, a1):
    def pt(r, a):
        return cx + r * math.cos(math.radians(a)), cy + r * math.sin(math.radians(a))
    x0, y0 = pt(r_out, a0); x1, y1 = pt(r_out, a1); x2, y2 = pt(r_in, a1); x3, y3 = pt(r_in, a0)
    large = 1 if (a1 - a0) % 360 > 180 else 0
    return (f"M{x0:.2f},{y0:.2f}A{r_out:.2f},{r_out:.2f} 0 {large},1 {x1:.2f},{y1:.2f}"
            f"L{x2:.2f},{y2:.2f}A{r_in:.2f},{r_in:.2f} 0 {large},0 {x3:.2f},{y3:.2f}z")


def bars(width, gap, heights):
    total = len(heights) * width + (len(heights) - 1) * gap
    x = C - total / 2 + width / 2
    out = []
    for h in heights:
        out.append(capsule(x, C, width, h))
        x += width + gap
    return out


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


# --- the three icons --------------------------------------------------------------------------

def liquid(dark=False):
    """Five glass bars: no container, the bars themselves are the glass."""
    top, mid, bottom = ("8C5CFF", "5B2BE0", "2C0F86") if dark else ("B57BFF", "7A3BF2", "4A1CCB")
    background = [
        Shape(rect(0, 0, 108, 108), Lin(20, 0, 88, 108, [(0, a(top)), (0.55, a(mid)), (1, a(bottom))])),
        # One soft pool of light, as if the phone were lit from the top.
        Shape(rect(0, 0, 108, 108), Rad(40, 8, 78, [(0, a("FFFFFF", 0.20 if dark else 0.34)), (1, a("FFFFFF", 0))])),
    ]
    heights = [16, 28, 40, 28, 16]
    foreground = []
    for b, h in zip(bars(7.2, 4.2, heights), heights):
        foreground.append(Shape(
            b, Lin(C - 20, C - h / 2, C - 20 + 12, C + h / 2, [(0, a("FFFFFF", 0.55)), (0.5, a("FFFFFF", 0.22)), (1, a("FFFFFF", 0.38))]),
            Lin(C - 20, C - 20, C + 20, C + 20, [(0, a("FFFFFF", 1)), (0.5, a("FFFFFF", 0.5)), (1, a("FFFFFF", 0.95))]), 1.1))
    mono = [Shape(b, "FFFFFF") for b in bars(7.2, 4.2, heights)]
    return background, foreground, mono


def material():
    background = [Shape(rect(0, 0, 108, 108), "E6DAFF")]
    shape = cookie(C, C, 33.0, lobes=9, depth=0.09)
    heights = [14, 24, 34, 24, 14]
    foreground = [Shape(shape, "6531F0")] + [Shape(b, "FFFFFF") for b in bars(5.2, 3.6, heights)]
    # Themed icons paint one colour: the cookie with the bars cut out of it.
    mono = [Shape(shape + "".join(bars(5.2, 3.6, heights)), "FFFFFF", evenodd=True)]
    return background, foreground, mono


def miuix():
    background = [
        Shape(rect(0, 0, 108, 108), Lin(10, 0, 98, 108, [(0, a("9A74FF")), (1, a("4B3BF0"))])),
        Shape(rect(0, 0, 108, 54), Lin(0, 0, 0, 54, [(0, a("FFFFFF", 0.22)), (1, a("FFFFFF", 0))])),
    ]
    heights = [20, 36, 50, 36, 20]
    foreground = [Shape(b, "FFFFFF") for b in bars(7.0, 4.6, heights)]
    mono = [Shape(b, "FFFFFF") for b in bars(7.0, 4.6, heights)]
    return background, foreground, mono


ICONS = {"liquid": liquid, "material": material, "miuix": miuix}



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
    """A violet disc whose glass bars grow from the middle, one after the other."""
    disc_r = 30.0
    fit = 35.4 / disc_r  # a hair inside the 36-unit circle the system shows, so the rim is not clipped
    _, fg, _ = liquid()
    disc = Shape(circle(C, C, disc_r), Lin(C - disc_r, C - disc_r, C + disc_r, C + disc_r, [(0, a("B57BFF")), (0.5, a("7A3BF2")), (1, a("4A1CCB"))]))
    order = [2, 1, 3, 0, 4]
    groups = "".join(
        f'<group android:name="bar{i}" android:pivotX="{C}" android:pivotY="{C}" android:scaleY="0">{vector_paths([bar])}</group>'
        for i, bar in enumerate(fg))
    drawable = (f'<vector xmlns:android="http://schemas.android.com/apk/res/android" xmlns:aapt="http://schemas.android.com/aapt" '
                f'android:width="240dp" android:height="240dp" android:viewportWidth="108" android:viewportHeight="108">'
                f'<group android:pivotX="{C}" android:pivotY="{C}" android:scaleX="{fit:.3f}" android:scaleY="{fit:.3f}">'
                f'<group android:name="disc" android:pivotX="{C}" android:pivotY="{C}" android:scaleX="0.6" android:scaleY="0.6">{vector_paths([disc])}</group>'
                f'{groups}</group></vector>')
    targets = "".join(
        f'<target android:name="bar{i}"><aapt:attr name="android:animation"><objectAnimator android:propertyName="scaleY" '
        f'android:valueFrom="0" android:valueTo="1" android:startOffset="{180 + order.index(i) * 70}" android:duration="320" '
        f'android:interpolator="@android:anim/overshoot_interpolator"/></aapt:attr></target>' for i in range(len(fg)))
    return ('<?xml version="1.0" encoding="utf-8"?>\n<!-- Generated by design/icon/build_style_icons.py: edit the script, not this file. -->\n'
            '<animated-vector xmlns:android="http://schemas.android.com/apk/res/android" xmlns:aapt="http://schemas.android.com/aapt">'
            f'<aapt:attr name="android:drawable">{drawable}</aapt:attr>'
            '<target android:name="disc"><aapt:attr name="android:animation"><set android:ordering="together">'
            '<objectAnimator android:propertyName="scaleX" android:valueFrom="0.6" android:valueTo="1" android:duration="420" android:interpolator="@android:anim/overshoot_interpolator"/>'
            '<objectAnimator android:propertyName="scaleY" android:valueFrom="0.6" android:valueTo="1" android:duration="420" android:interpolator="@android:anim/overshoot_interpolator"/>'
            f'</set></aapt:attr></target>{targets}</animated-vector>\n')


def render_png(background, foreground, size, path, mask="none"):
    import subprocess, tempfile
    with tempfile.NamedTemporaryFile("w", suffix=".svg", delete=False) as f:
        f.write(svg(background, foreground, mask))
    subprocess.run(["rsvg-convert", "-w", str(size), "-h", str(size), f.name, "-o", str(path)], check=True)


def write_apply():
    import json
    bg, fg, mono = liquid()
    dark_bg, dark_fg, _ = liquid(dark=True)
    # The main icon is the Liquid one (it is the default style); Android reads its layers from the liquid files.
    (RES / "mipmap-anydpi/ic_launcher.xml").write_text(
        (RES / "mipmap-anydpi/ic_launcher_liquid.xml").read_text())
    (RES / "drawable/splash_icon.xml").write_text(splash())
    ios = ROOT / "iosApp/iosApp/Assets.xcassets/AppIcon.appiconset"
    render_png(bg, fg, 1024, ios / "AppIcon.png")
    render_png(dark_bg, dark_fg, 1024, ios / "AppIcon-dark.png")
    # Tinted icons are grey values that iOS recolours: white bars on black.
    render_png([Shape(rect(0, 0, 108, 108), "141414")], [Shape(b, "FFFFFF") for b in bars(7.2, 4.2, [16, 28, 40, 28, 16])], 1024, ios / "AppIcon-tinted.png")
    shared = ROOT / "shared/src/commonMain/composeResources/drawable"
    render_png(bg, fg, 256, shared / "songlass_brand_light.png")
    render_png(dark_bg, dark_fg, 256, shared / "songlass_brand_dark.png")


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for name, make in ICONS.items():
        bg, fg, mono = make()
        (OUT / f"{name}.svg").write_text(svg(bg, fg, "none"))
        (OUT / f"{name}-squircle.svg").write_text(svg(bg, fg, "squircle"))
        (OUT / f"{name}-circle.svg").write_text(svg(bg, fg, "circle"))
        if "--apply" in sys.argv:
            (RES / f"drawable/ic_launcher_{name}_background.xml").write_text(vector(bg))
            (RES / f"drawable/ic_launcher_{name}_foreground.xml").write_text(vector(fg))
            (RES / f"drawable/ic_launcher_{name}_monochrome.xml").write_text(vector(mono))
            (RES / f"mipmap-anydpi/ic_launcher_{name}.xml").write_text(
                '<?xml version="1.0" encoding="utf-8"?>\n<!-- Generated by design/icon/build_style_icons.py -->\n'
                '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
                f'    <background android:drawable="@drawable/ic_launcher_{name}_background" />\n'
                f'    <foreground android:drawable="@drawable/ic_launcher_{name}_foreground" />\n'
                f'    <monochrome android:drawable="@drawable/ic_launcher_{name}_monochrome" />\n'
                '</adaptive-icon>\n')
    if "--apply" in sys.argv:
        write_apply()


if __name__ == "__main__":
    main()
