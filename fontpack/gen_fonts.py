#!/usr/bin/env python3
"""
Generates Samsung lock-screen clock fonts (the "More Fonts" list in the lock screen editor).

Same trick as the OPPO Big Clock font: the digits are very tall (about 5.5x as tall as wide), and
One UI scales the glyphs to fit the clock width, so the clock fills the screen height.
Each digit is a stroked centre line, so the stroke thickness is a free parameter.

Usage: gen_fonts.py OUT_DIR   -> OUT_DIR/<id>/{font.ttf,font.xml,meta.txt}, preview.png
"""
import json, math, os, sys
from fontTools.fontBuilder import FontBuilder
from fontTools.pens.ttGlyphPen import TTGlyphPen
from shapely.geometry import LineString, Polygon, MultiPolygon
from shapely.geometry.polygon import orient
from shapely.ops import unary_union

# Glyph box (font units, upem 1000), copied from the OPPO font: digits sit from -3540 up to 671.
X0, X1, YB, YT = 77, 690, -3540, 671
ADV = 767

# thickness name -> stroke width in font units (digit width is ~613)
LEVELS = {"hairline": 28, "thin": 50, "regular": 78, "bold": 112, "heavy": 148, "black": 185}

def bez(p0, p1, p2, p3, n=24):
    out = []
    for i in range(n + 1):
        t = i / n; u = 1 - t
        out.append((u**3*p0[0] + 3*u*u*t*p1[0] + 3*u*t*t*p2[0] + t**3*p3[0],
                    u**3*p0[1] + 3*u*u*t*p1[1] + 3*u*t*t*p2[1] + t**3*p3[1]))
    return out

def path(*segs):
    """segs: ('M',x,y) ('L',x,y) ('C',x1,y1,x2,y2,x3,y3) in unit coords (v=0 is the top)."""
    pts = []; cur = None
    for s in segs:
        if s[0] == 'M': cur = (s[1], s[2]); pts.append(cur)
        elif s[0] == 'L': cur = (s[1], s[2]); pts.append(cur)
        else:
            pts += bez(cur, (s[1], s[2]), (s[3], s[4]), (s[5], s[6]))[1:]; cur = (s[5], s[6])
    return pts

def ellipse(l, t, r, b, n=64):
    cx, cy, rx, ry = (l + r) / 2, (t + b) / 2, (r - l) / 2, (b - t) / 2
    return [(cx + rx * math.cos(2 * math.pi * i / n), cy + ry * math.sin(2 * math.pi * i / n)) for i in range(n + 1)]

def stadium(l, t, r, b, k=0.5, n=24):
    """rounded rectangle, corner radius = k * width (in unit coords the radius is elliptical)."""
    w, h = r - l, b - t
    rx = w * k; ry = min(h / 2, rx * (w and 1) * 0 + 0.12)  # tall box: shallow vertical radius in unit space
    pts = []
    for cx, cy, a0 in ((r - rx, t + ry, -90), (r - rx, b - ry, 0), (l + rx, b - ry, 90), (l + rx, t + ry, 180)):
        for i in range(n + 1):
            a = math.radians(a0 + 90 * i / n)
            pts.append((cx + rx * math.cos(a), cy + ry * math.sin(a)))
    pts.append(pts[0]); return pts

DIGITS = {
    '0': [stadium(0, 0, 1, 1)],
    '1': [path(('M', .12, .12), ('L', .6, 0), ('L', .6, 1))],
    '2': [path(('M', 0, .12), ('C', 0, -.04, 1, -.04, 1, .12), ('C', 1, .3, 0, .78, 0, 1), ('L', 1, 1))],
    '3': [path(('M', 0, .06), ('C', .1, -.02, 1, -.02, 1, .12), ('C', 1, .3, .6, .46, .35, .5),
               ('C', .6, .54, 1, .7, 1, .88), ('C', 1, 1.02, .1, 1.02, 0, .94))],
    '4': [path(('M', .78, 1), ('L', .78, 0), ('L', 0, .66), ('L', 1, .66))],
    '5': [path(('M', 1, 0), ('L', .06, 0), ('L', 0, .42), ('C', .3, .36, 1, .4, 1, .68), ('C', 1, 1.02, .1, 1.02, 0, .92))],
    '6': [path(('M', .95, .05), ('C', .6, -.02, 0, .0, 0, .55)), ellipse(0, .42, 1, 1)],
    '7': [path(('M', 0, 0), ('L', 1, 0), ('L', .3, 1))],
    '8': [ellipse(.04, 0, .96, .5), ellipse(0, .47, 1, 1)],
    '9': [path(('M', .05, .95), ('C', .4, 1.02, 1, 1, 1, .45)), ellipse(0, 0, 1, .58)],
}

def glyph_poly(strokes, r):
    x0, x1, y0, y1 = X0 + r, X1 - r, YB + r, YT - r
    geoms = []
    for s in strokes:
        pts = [(x0 + u * (x1 - x0), y1 - v * (y1 - y0)) for u, v in s]
        geoms.append(LineString(pts).buffer(r, cap_style=1, join_style=1, resolution=10))
    return unary_union(geoms)

def dots_poly(r, with_colon):
    if not with_colon: return None
    d = max(r * 0.8, 40)
    cy = (YB + YT) / 2 - 5
    cx = 189
    return unary_union([Polygon([(cx + d * math.cos(a / 16 * 2 * math.pi), y + d * math.sin(a / 16 * 2 * math.pi))
                                 for a in range(16)]) for y in (cy - 1100 + d, cy + 1100 - d)])

def to_glyph(geom):
    pen = TTGlyphPen(None)
    if geom is not None and not geom.is_empty:
        polys = list(geom.geoms) if isinstance(geom, MultiPolygon) else [geom]
        for p in polys:
            p = orient(p.simplify(1.5), sign=-1.0)  # outer clockwise, holes counter-clockwise
            for ring in [p.exterior] + list(p.interiors):
                c = [(round(x), round(y)) for x, y in ring.coords[:-1]]
                pen.moveTo(c[0])
                for q in c[1:]: pen.lineTo(q)
                pen.closePath()
    return pen.glyph()

def build(path_out, name, r, with_colon):
    order = ['.notdef', 'space', 'zero', 'one', 'two', 'three', 'four', 'five', 'six', 'seven', 'eight', 'nine', 'colon']
    names = dict(zip('0123456789', order[2:12]))
    fb = FontBuilder(1000, isTTF=True)
    fb.setupGlyphOrder(order)
    fb.setupCharacterMap({0x20: 'space', 0xA0: 'space', 0x3A: 'colon', **{ord(k): v for k, v in names.items()}})
    glyphs = {'.notdef': to_glyph(None), 'space': to_glyph(None), 'colon': to_glyph(dots_poly(r, with_colon))}
    for k, v in names.items(): glyphs[v] = to_glyph(glyph_poly(DIGITS[k], r))
    fb.setupGlyf(glyphs)
    adv = {g: ADV for g in order}; adv.update({'space': 370, 'colon': 378, '.notdef': 500})
    metrics = {}
    for g in order:
        gl = glyphs[g]; gl.recalcBounds(fb.font['glyf']) if hasattr(gl, 'recalcBounds') else None
        metrics[g] = (adv[g], getattr(gl, 'xMin', 0) if gl.numberOfContours else 0)
    fb.setupHorizontalMetrics(metrics)
    fb.setupHorizontalHeader(ascent=700, descent=-3540)
    fb.setupNameTable({'familyName': name, 'styleName': 'Regular'})
    fb.setupOS2(sTypoAscender=700, sTypoDescender=-3540, sTypoLineGap=0, usWinAscent=700, usWinDescent=3540,
                sxHeight=531, sCapHeight=725, fsSelection=64, usWeightClass=400, xAvgCharWidth=500)
    fb.setupPost()
    fb.save(path_out)

def preview(out_dir, items):
    from PIL import Image, ImageDraw
    from fontTools.ttLib import TTFont
    from fontTools.pens.recordingPen import RecordingPen
    W = 150 * len(items); img = Image.new('RGB', (W, 520), (30, 34, 44)); d = ImageDraw.Draw(img)
    for i, (id_, ttf) in enumerate(items):
        f = TTFont(ttf); gs = f.getGlyphSet(); cmap = f.getBestCmap(); x = 8 + i * 150
        for row, txt in enumerate(("23", "38")):
            for ch in txt:
                rec = RecordingPen(); gs[cmap[ord(ch)]].draw(rec)
                s = 220 / 4211; ox = x; oy = 20 + row * 245
                for op, args in rec.value:
                    pass
                pts = []
                for op, args in rec.value:
                    if op == 'moveTo': pts = [args[0]]
                    elif op == 'lineTo': pts.append(args[0])
                    elif op in ('closePath', 'endPath') and len(pts) > 2:
                        area = sum(a[0]*b[1]-b[0]*a[1] for a, b in zip(pts, pts[1:]+pts[:1]))
                        d.polygon([(ox + (px - X0) * s, oy + (671 - py) * s) for px, py in pts],
                                  fill=(240, 240, 245) if area < 0 else (30, 34, 44))
                x += 62
            x -= 124
        d.text((x, 500), id_, fill=(160, 160, 170))
    img.save(os.path.join(out_dir, 'preview.png'))

if __name__ == '__main__':
    out = sys.argv[1]; os.makedirs(out, exist_ok=True); items = []
    for lvl, r in LEVELS.items():
        for colon in (False, True):
            id_ = f"{lvl}{'_colon' if colon else ''}"
            d = os.path.join(out, id_); os.makedirs(d, exist_ok=True)
            label = f"Depth {lvl.capitalize()}{' :' if colon else ''} - 12:34"
            build(os.path.join(d, 'font.ttf'), f"Depth {lvl}", r, colon)
            open(os.path.join(d, 'font.xml'), 'w').write(
                '<?xml version="1.0" encoding="utf-8"?>\n<font displayname="%s">\n\t<sans>\n\t\t<file>\n\t\t\t<filename>LOCKSCREENDepth%s.ttf</filename>\n\t\t\t<droidname>DroidSans.ttf</droidname>\n\t\t</file>\n\t\t<file>\n\t\t\t<filename>LOCKSCREENDepth%s.ttf</filename>\n\t\t\t<droidname>DroidSans-Bold.ttf</droidname>\n\t\t</file>\n\t</sans>\n</font>\n' % (label, id_, id_))
            json.dump({'id': id_, 'label': label, 'pkg': 'com.monotype.android.font.depthclock' + id_.replace('_', '')}, open(os.path.join(d, 'meta.json'), 'w'))
            if not colon: items.append((lvl, os.path.join(d, 'font.ttf')))
    preview(out, items)
