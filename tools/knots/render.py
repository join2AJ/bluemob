import json, math, sys
from PIL import Image, ImageDraw, ImageFont
W, H, S = 16, 10, 40  # grid and pixels per unit
BG = (243, 238, 228)
COL = {"blue": (47, 111, 176), "orange": (232, 137, 43), "green": (46, 140, 90), "purple": (124, 107, 214)}
WOOD = (154, 107, 63)

def cr(pts, n=10):
    """Catmull-Rom through control points; each dense point carries (z, step, color) of its segment."""
    out = []
    P = [pts[0]] + pts + [pts[-1]]
    for i in range(1, len(P) - 2):
        p0, p1, p2, p3 = P[i - 1], P[i], P[i + 1], P[i + 2]
        for k in range(n):
            t = k / n
            t2, t3 = t * t, t * t * t
            x = 0.5 * ((2 * p1[0]) + (-p0[0] + p2[0]) * t + (2 * p0[0] - 5 * p1[0] + 4 * p2[0] - p3[0]) * t2 + (-p0[0] + 3 * p1[0] - 3 * p2[0] + p3[0]) * t3)
            y = 0.5 * ((2 * p1[1]) + (-p0[1] + p2[1]) * t + (2 * p0[1] - 5 * p1[1] + 4 * p2[1] - p3[1]) * t2 + (-p0[1] + 3 * p1[1] - 3 * p2[1] + p3[1]) * t3)
            out.append((x, y, p1[2], p2[3], i - 1))
    last = pts[-1]
    out.append((last[0], last[1], last[2], last[3], len(pts) - 1))
    return out

def render(knot, step, frac=1.0, scale=S):
    img = Image.new("RGB", (W * scale, H * scale), BG)
    d = ImageDraw.Draw(img)
    w = knot.get("width", 0.55) * scale
    layers = {}
    for pr in knot.get("props", []):
        layers.setdefault(pr.get("z", 1), []).append(("prop", pr))
    tips = []
    for r in knot["ropes"]:
        pts = r["pts"]
        mo = r.get("morph")
        if mo:
            keys = sorted(int(k) for k in mo)
            def shape(k):
                ks = [x for x in keys if x <= k]
                return mo[str(ks[-1])] if ks else mo[str(keys[0])]
            a0 = shape(step - 1) if step > 0 else shape(0)
            a1 = shape(step)
            f = frac
            pts = [[p0[0] + (p1[0] - p0[0]) * f, p0[1] + (p1[1] - p0[1]) * f, (p1[2] if f > 0.5 else p0[2]), 0] for p0, p1 in zip(a0, a1)]
        dense = cr(pts)
        work = r.get("work", 10 ** 9)
        segs = []
        # segment j from dense[j] to dense[j+1]; its step is dense[j][3] (step of the control point it heads to)
        cur = [(a, b) for a, b in zip(dense, dense[1:])]
        stepsegs = [s for s in cur if s[0][3] == step]
        total = sum(math.dist(a[:2], b[:2]) for a, b in stepsegs)
        drawn = 0
        lim = total * frac
        tip = None
        for a, b in cur:
            st = a[3]
            if st > step:
                continue
            if st == step:
                L = math.dist(a[:2], b[:2])
                if drawn >= lim:
                    continue
                if drawn + L > lim:
                    f = (lim - drawn) / L
                    b = (a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f) + b[2:]
                    tip = b
                drawn += L
                tip = b
            color = COL[r.get("color", "blue")] if a[4] < work else COL["orange"]
            layers.setdefault(a[2], []).append(("seg", (a, b, color, r.get("width", knot.get("width", 0.5)) * scale)))
        if tip is not None:
            tips.append(tip)
    for z in sorted(layers):
        items = layers[z]
        for kind, it in items:
            if kind == "prop":
                pr = it
                if pr["type"] == "post":
                    d.rectangle([pr["x0"] * scale, pr["y0"] * scale, pr["x1"] * scale, pr["y1"] * scale], fill=WOOD)
                elif pr["type"] == "ring":
                    cx, cy, rr = pr["x"] * scale, pr["y"] * scale, pr["r"] * scale
                    d.ellipse([cx - rr, cy - rr, cx + rr, cy + rr], outline=(120, 120, 130), width=int(0.35 * scale))
                elif pr["type"] == "disc":
                    cx, cy, rr = pr["x"] * scale, pr["y"] * scale, pr["r"] * scale
                    d.ellipse([cx - rr, cy - rr, cx + rr, cy + rr], fill=WOOD)
        for kind, it in items:
            if kind == "seg":
                a, b, c, w = it
                d.line([a[0] * scale, a[1] * scale, b[0] * scale, b[1] * scale], fill=BG, width=int(w + 0.3 * scale))
        for kind, it in items:
            if kind == "seg":
                a, b, c, w = it
                r0 = w / 2
                d.line([a[0] * scale, a[1] * scale, b[0] * scale, b[1] * scale], fill=c, width=int(w))
                d.ellipse([a[0] * scale - r0 + 1, a[1] * scale - r0 + 1, a[0] * scale + r0 - 1, a[1] * scale + r0 - 1], fill=c)
    for t in tips:
        cx, cy = t[0] * scale, t[1] * scale
        d.ellipse([cx - w * 0.75, cy - w * 0.75, cx + w * 0.75, cy + w * 0.75], outline=(20, 20, 20), width=3)
    return img

def sheet(knot, path):
    n = len(knot["steps"])
    sc = 30
    tiles = [render(knot, i, 1.0, sc) for i in range(n)]
    cols = 3
    rows = (n + cols - 1) // cols
    out = Image.new("RGB", (cols * W * sc + (cols - 1) * 6, rows * (H * sc + 30) ), (255, 255, 255))
    d = ImageDraw.Draw(out)
    for i, t in enumerate(tiles):
        x = (i % cols) * (W * sc + 6)
        y = (i // cols) * (H * sc + 30)
        out.paste(t, (x, y + 28))
        d.text((x + 4, y + 6), f"{i}: {knot['steps'][i][:70]}", fill=(0, 0, 0))
    out.save(path)

if __name__ == "__main__":
    data = json.load(open(sys.argv[1]))
    for k in data:
        if len(sys.argv) > 2 and k["id"] not in sys.argv[2:]:
            continue
        sheet(k, f"sheet-{k['id']}.png")
        print("wrote", k["id"])
