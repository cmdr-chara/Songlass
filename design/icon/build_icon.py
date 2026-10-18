"""
Builds the Songlass icon from one vector description into:
  - design/icon/songlass-icon.svg            (master, full-bleed square)
  - androidApp .../drawable/ic_launcher_background.xml / ic_launcher_foreground.xml
  - androidApp .../drawable/splash_icon.xml  (animated: bubble swells, bars grow)
  - iosApp .../AppIcon.appiconset/AppIcon.png (rendered with rsvg-convert)

Only features both SVG and Android VectorDrawable support are used: paths, linear and radial
gradients (with alpha), clip paths and group transforms. No blur or filters.
Coordinates use Android's 108-unit adaptive icon grid; the bubble fits the 66-unit safe zone.

Run from the repository root: python3 design/icon/build_icon.py
"""
import math
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "androidApp/src/main/res"

CX, CY, R = 54.0, 54.0, 30.0


# --- primitives -------------------------------------------------------------------------------

def circle(cx, cy, r):
    return f"M{cx - r:.2f},{cy:.2f}a{r:.2f},{r:.2f} 0 1,0 {2 * r:.2f},0a{r:.2f},{r:.2f} 0 1,0 {-2 * r:.2f},0z"


def ellipse(cx, cy, rx, ry):
    return f"M{cx - rx:.2f},{cy:.2f}a{rx:.2f},{ry:.2f} 0 1,0 {2 * rx:.2f},0a{rx:.2f},{ry:.2f} 0 1,0 {-2 * rx:.2f},0z"


def capsule(cx, cy, w, h):
    r = w / 2
    left, top = cx - r, cy - h / 2
    return (f"M{left:.2f},{top + r:.2f}a{r:.2f},{r:.2f} 0 0,1 {w:.2f},0v{h - w:.2f}"
            f"a{r:.2f},{r:.2f} 0 0,1 {-w:.2f},0z")


def arc_band(cx, cy, r_out, r_in, a0, a1):
    """Crescent between two radii from angle a0 to a1 (degrees, clockwise from +x, y down)."""
    def pt(r, a):
        return cx + r * math.cos(math.radians(a)), cy + r * math.sin(math.radians(a))
    x0, y0 = pt(r_out, a0)
    x1, y1 = pt(r_out, a1)
    x2, y2 = pt(r_in, a1)
    x3, y3 = pt(r_in, a0)
    large = 1 if (a1 - a0) % 360 > 180 else 0
    return (f"M{x0:.2f},{y0:.2f}A{r_out:.2f},{r_out:.2f} 0 {large},1 {x1:.2f},{y1:.2f}"
            f"L{x2:.2f},{y2:.2f}A{r_in:.2f},{r_in:.2f} 0 {large},0 {x3:.2f},{y3:.2f}z")


class Linear:
    def __init__(self, x1, y1, x2, y2, stops):
        self.x1, self.y1, self.x2, self.y2, self.stops = x1, y1, x2, y2, stops


class Radial:
    def __init__(self, cx, cy, r, stops):
        self.cx, self.cy, self.r, self.stops = cx, cy, r, stops


def rgba(hex_rgb, alpha):
    return hex_rgb, alpha


# A shape: path data, fill (colour tuple or gradient), optional stroke.
class Shape:
    def __init__(self, d, fill=None, stroke=None, stroke_width=0.0, name=None):
        self.d, self.fill, self.stroke, self.stroke_width, self.name = d, fill, stroke, stroke_width, name


class Group:
    def __init__(self, children, clip=None, name=None, scale=1.0, pivot=(CX, CY), sy=1.0):
        self.children, self.clip, self.name, self.scale, self.pivot, self.sy = children, clip, name, scale, pivot, sy


def glow(cx, cy, r, stops, squash):
    """A soft elliptical glow: a radial gradient disc squashed vertically, so no edge shows."""
    return Group([Shape(circle(cx, cy, r), Radial(cx, cy, r, stops))], pivot=(cx, cy), sy=squash)


# --- the artwork ------------------------------------------------------------------------------

BACKGROUND = [
    Shape("M0,0h108v108h-108z", Linear(0, 0, 108, 108, [(0, rgba("B396FB", 1)), (0.45, rgba("7C55F6", 1)), (1, rgba("4C22E4", 1))])),
    # Soft light from the top left and a deeper corner at the bottom right.
    Shape("M0,0h108v108h-108z", Radial(14, 8, 60, [(0, rgba("FFFFFF", 0.14)), (1, rgba("FFFFFF", 0))])),
]

# Bars measured on the generated artwork (centre x, width, height in source px; centre at 627).
SOURCE_BARS = [(352, 85, 140), (477, 105, 320), (627, 125, 520), (777, 105, 300), (902, 85, 130)]
BAR_SCALE = (R * 2 * 0.71) / 635.0  # the bars span ~71% of the bubble


def bar_shapes(index, cx_src, w_src, h_src):
    cx = CX + (cx_src - 627) * BAR_SCALE
    w, h = w_src * BAR_SCALE, h_src * BAR_SCALE
    top, bottom = CY - h / 2, CY + h / 2
    body = Shape(
        capsule(cx, CY, w, h),
        Linear(cx, top, cx, bottom, [
            (0, rgba("EEF3FF", 0.62)), (0.35, rgba("C8B8FF", 0.30)), (0.7, rgba("FFB0CF", 0.40)), (1, rgba("FFC79A", 0.78)),
        ]),
        stroke=("FFFFFF", 0.8), stroke_width=0.42,
    )
    # Specular streak down the left side of each bar.
    streak = Shape(
        capsule(cx - w * 0.2, CY - h * 0.12, w * 0.3, h * 0.5),
        Linear(cx, top, cx, bottom, [(0, rgba("FFFFFF", 0.38)), (1, rgba("FFFFFF", 0.0))]),
    )
    # Warm light refracted along the right edge.
    edge = Shape(
        capsule(cx + w * 0.18, CY + h * 0.1, w * 0.36, h * 0.62),
        Linear(cx, top, cx, bottom, [(0, rgba("FFB27A", 0.0)), (0.65, rgba("FFB27A", 0.32)), (1, rgba("FFB27A", 0.0))]),
    )
    return Group([body, edge, streak], name=f"bar{index}", scale=1.0)


GLOW = [
    # Warm light under the bubble where it passes through the glass, with a cool core.
    glow(42, 86, 15, [(0, rgba("FFA36E", 0.7)), (1, rgba("FF8FD0", 0))], 0.32),
    glow(66, 86, 15, [(0, rgba("FFA36E", 0.7)), (1, rgba("FF8FD0", 0))], 0.32),
    glow(54, 88.5, 15, [(0, rgba("7FC8FF", 0.65)), (1, rgba("7FC8FF", 0))], 0.25),
]

BUBBLE = [
    # Body: almost clear, brighter towards the rim (Fresnel).
    Shape(circle(CX, CY, R), Radial(CX - 3, CY - 5, R + 3, [
        (0, rgba("FFFFFF", 0.0)), (0.7, rgba("C9B8FF", 0.05)), (0.9, rgba("FFFFFF", 0.16)), (1, rgba("FFFFFF", 0.42)),
    ])),
    Group([
        # Coloured light caught inside the glass: warm on the sides and at the bottom, cool at the top right.
        Group([glow(CX - 28, CY - 2, 15, [(0, rgba("FFA06A", 0.85)), (0.6, rgba("FF7AD0", 0.3)), (1, rgba("FF7AD0", 0))], 1.0)]),
        Group([glow(CX + 26, CY - 12, 13, [(0, rgba("FFAE70", 0.85)), (0.6, rgba("FF8AD0", 0.3)), (1, rgba("FF8AD0", 0))], 1.0)]),
        glow(CX - 6, CY + 30, 24, [(0, rgba("FFB08A", 0.38)), (0.5, rgba("FFB0C8", 0.14)), (1, rgba("FFB0C8", 0))], 0.5),
        glow(CX + 14, CY - 24, 14, [(0, rgba("9FE3FF", 0.45)), (1, rgba("9FE3FF", 0))], 0.45),
        # Highlight along the top left inside the rim.
        Shape(arc_band(CX, CY, R - 1.4, R - 9, 200, 315), Linear(CX - 10, CY - 29, CX - 4, CY - 12, [
            (0, rgba("FFFFFF", 0.75)), (1, rgba("FFFFFF", 0.0)),
        ])),
        # Faint reflection at the bottom right.
        Shape(arc_band(CX, CY, R - 2, R - 4.5, 25, 80), Linear(CX + 24, CY + 10, CX + 6, CY + 28, [
            (0, rgba("FFFFFF", 0.0)), (0.5, rgba("FFFFFF", 0.45)), (1, rgba("FFFFFF", 0.0)),
        ])),
    ], clip=circle(CX, CY, R)),
    # Iridescent rim: white with cool and warm tints around the edge.
    Shape(circle(CX, CY, R - 0.4), None, stroke=Linear(CX - R, CY - R, CX + R, CY + R, [
        (0, rgba("FFFFFF", 1)), (0.3, rgba("BFE6FF", 0.95)), (0.5, rgba("FFFFFF", 0.85)), (0.72, rgba("FFB8DA", 0.95)), (1, rgba("FFC28A", 1)),
    ]), stroke_width=1.15),
    Shape(circle(CX, CY, R - 2.2), None, stroke=("FFFFFF", 0.22), stroke_width=0.5),
]

BARS = [bar_shapes(i, *b) for i, b in enumerate(SOURCE_BARS)]
FOREGROUND = GLOW + BUBBLE + BARS


# --- SVG output -------------------------------------------------------------------------------

class SvgWriter:
    def __init__(self):
        self.defs, self.n = [], 0

    def _id(self, prefix):
        self.n += 1
        return f"{prefix}{self.n}"

    def paint(self, p):
        if p is None:
            return "none", None
        if isinstance(p, tuple):
            return f"#{p[0]}", p[1]
        gid = self._id("g")
        stops = "".join(f'<stop offset="{o}" stop-color="#{c}" stop-opacity="{a}"/>' for o, (c, a) in p.stops)
        if isinstance(p, Linear):
            self.defs.append(f'<linearGradient id="{gid}" gradientUnits="userSpaceOnUse" x1="{p.x1}" y1="{p.y1}" x2="{p.x2}" y2="{p.y2}">{stops}</linearGradient>')
        else:
            self.defs.append(f'<radialGradient id="{gid}" gradientUnits="userSpaceOnUse" cx="{p.cx}" cy="{p.cy}" r="{p.r}">{stops}</radialGradient>')
        return f"url(#{gid})", None

    def node(self, n):
        if isinstance(n, Group):
            attrs = ""
            if n.sy != 1.0:
                px, py = n.pivot
                attrs += f' transform="translate({px},{py}) scale(1,{n.sy}) translate({-px},{-py})"'
            if n.clip:
                cid = self._id("c")
                self.defs.append(f'<clipPath id="{cid}"><path d="{n.clip}"/></clipPath>')
                attrs += f' clip-path="url(#{cid})"'
            return f"<g{attrs}>" + "".join(self.node(c) for c in n.children) + "</g>"
        fill, fa = self.paint(n.fill)
        stroke, sa = self.paint(n.stroke)
        a = f'<path d="{n.d}" fill="{fill}"'
        if fa is not None:
            a += f' fill-opacity="{fa}"'
        if n.stroke is not None:
            a += f' stroke="{stroke}" stroke-width="{n.stroke_width}"'
            if sa is not None:
                a += f' stroke-opacity="{sa}"'
        return a + "/>"

    def document(self, layers, scale=1.0):
        body = "".join(self.node(n) for n in layers[0])
        fg = "".join(self.node(n) for n in layers[1])
        t = f'translate({CX},{CY}) scale({scale}) translate({-CX},{-CY})'
        return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="1024" height="1024">'
                f'<defs>{"".join(self.defs)}</defs>{body}<g transform="{t}">{fg}</g></svg>')


# --- Android VectorDrawable output ------------------------------------------------------------

def argb(c, a):
    return f"#{round(a * 255):02X}{c}"


def vd_gradient(p, attr):
    stops = "".join(f'\n                <item android:offset="{o}" android:color="{argb(c, a)}" />' for o, (c, a) in p.stops)
    if isinstance(p, Linear):
        g = (f'<gradient android:type="linear" android:startX="{p.x1}" android:startY="{p.y1}" '
             f'android:endX="{p.x2}" android:endY="{p.y2}">')
    else:
        g = f'<gradient android:type="radial" android:centerX="{p.cx}" android:centerY="{p.cy}" android:gradientRadius="{p.r}">'
    return (f'\n            <aapt:attr name="android:{attr}">\n            {g}{stops}\n            </gradient>'
            f'\n            </aapt:attr>')


def vd_node(n, indent="        "):
    if isinstance(n, Group):
        attrs = f' android:name="{n.name}"' if n.name else ""
        if n.name and n.name.startswith("bar"):
            attrs += f' android:pivotX="{CX}" android:pivotY="{CY}"'
        if n.sy != 1.0:
            attrs += f' android:pivotX="{n.pivot[0]}" android:pivotY="{n.pivot[1]}" android:scaleY="{n.sy}"'
        out = f"{indent}<group{attrs}>\n"
        if n.clip:
            out += f'{indent}    <clip-path android:pathData="{n.clip}" />\n'
        out += "".join(vd_node(c, indent + "    ") for c in n.children)
        return out + f"{indent}</group>\n"
    inline, nested = [f'android:pathData="{n.d}"'], ""
    if isinstance(n.fill, tuple):
        inline.append(f'android:fillColor="{argb(*n.fill)}"')
    elif n.fill is not None:
        nested += vd_gradient(n.fill, "fillColor")
    if n.stroke is not None:
        inline.append(f'android:strokeWidth="{n.stroke_width}"')
        if isinstance(n.stroke, tuple):
            inline.append(f'android:strokeColor="{argb(*n.stroke)}"')
        else:
            nested += vd_gradient(n.stroke, "strokeColor")
    attrs = "\n".join(f"{indent}    {a}" for a in inline)
    if nested:
        return f"{indent}<path\n{attrs}>{nested}\n{indent}</path>\n"
    return f"{indent}<path\n{attrs} />\n"


def vector(nodes, size_dp=108, wrap_name=None, wrap_scale=None):
    body = "".join(vd_node(n) for n in nodes)
    if wrap_name:
        body = (f'        <group android:name="{wrap_name}" android:pivotX="{CX}" android:pivotY="{CY}"'
                f' android:scaleX="{wrap_scale}" android:scaleY="{wrap_scale}">\n'
                + "".join(vd_node(n, "            ") for n in nodes) + "        </group>\n")
    return (f'<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            f'    xmlns:aapt="http://schemas.android.com/aapt"\n'
            f'    android:width="{size_dp}dp"\n    android:height="{size_dp}dp"\n'
            f'    android:viewportWidth="108"\n    android:viewportHeight="108">\n{body}</vector>\n')


HEADER = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<!-- Generated by design/icon/build_icon.py: edit the script, not this file. -->\n"


def splash():
    """
    The bubble alone, its violet seen through the glass and no ring around it: the disc behind it
    has the bubble's own radius, and both are scaled so the rim lands on the 160 dp circle the
    system shows. The bubble swells in, then the bars grow from the middle.
    """
    disc = Shape(circle(CX, CY, R), Linear(CX - R, CY - R, CX + R, CY + R, [(0, rgba("B9A3FF", 1)), (0.5, rgba("7E5AF5", 1)), (1, rgba("4A26DF", 1))]))
    nodes = [disc] + BUBBLE
    bars = [Group(b.children, name=b.name) for b in BARS]
    v = vector([], size_dp=240)
    inner = (f'        <group android:name="bubble" android:pivotX="{CX}" android:pivotY="{CY}" android:scaleX="0.6" android:scaleY="0.6">\n'
             + "".join(vd_node(n, "            ") for n in nodes) + "        </group>\n"
             + "".join(vd_node(b).replace(f'android:name="{b.name}"', f'android:name="{b.name}" android:scaleY="0"', 1) for b in bars))
    fit = 35.4 / R  # a hair inside the 36-unit circle so the rim is not clipped
    inner = (f'        <group android:pivotX="{CX}" android:pivotY="{CY}" android:scaleX="{fit}" android:scaleY="{fit}">\n'
             + inner + "        </group>\n")
    v = v.replace("</vector>", inner + "</vector>")
    order = [2, 1, 3, 0, 4]
    targets = "".join(f'''
    <target android:name="bar{i}">
        <aapt:attr name="android:animation">
            <objectAnimator android:propertyName="scaleY" android:valueFrom="0" android:valueTo="1"
                android:startOffset="{180 + order.index(i) * 70}" android:duration="320"
                android:interpolator="@android:anim/overshoot_interpolator" />
        </aapt:attr>
    </target>''' for i in range(len(BARS)))
    return f'''{HEADER}<animated-vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt">
    <aapt:attr name="android:drawable">
{v.replace(chr(10), chr(10) + "    ").rstrip()}
    </aapt:attr>
    <target android:name="bubble">
        <aapt:attr name="android:animation">
            <set android:ordering="together">
                <objectAnimator android:propertyName="scaleX" android:valueFrom="0.6" android:valueTo="1"
                    android:duration="420" android:interpolator="@android:anim/overshoot_interpolator" />
                <objectAnimator android:propertyName="scaleY" android:valueFrom="0.6" android:valueTo="1"
                    android:duration="420" android:interpolator="@android:anim/overshoot_interpolator" />
            </set>
        </aapt:attr>
    </target>{targets}
</animated-vector>
'''


def main():
    out = Path(__file__).parent
    # Master SVG: as on iOS, the bubble is a bit larger than in Android's safe zone.
    svg = SvgWriter().document((BACKGROUND, FOREGROUND), scale=1.28)
    (out / "songlass-icon.svg").write_text(svg)
    (RES / "drawable/ic_launcher_background.xml").write_text(HEADER + vector(BACKGROUND))
    (RES / "drawable/ic_launcher_foreground.xml").write_text(HEADER + vector(FOREGROUND))
    (RES / "drawable/splash_icon.xml").write_text(splash())
    ios = ROOT / "iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/AppIcon.png"
    subprocess.run(["rsvg-convert", "-w", "1024", "-h", "1024", "-b", "#4A26DF", str(out / "songlass-icon.svg"), "-o", str(ios)], check=True)


if __name__ == "__main__":
    main()
