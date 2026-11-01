import json, math
K = []

def knot(id, steps, ropes, props=(), pull=None, width=0.5):
    K.append({"id": id, "steps": steps, "ropes": ropes, "props": list(props), "pull": pull if pull is not None else len(steps) - 1, "width": width})

def rope(color, pts, work=None, morph=None, width=None):
    r = {"color": color, "pts": [[round(float(p[0]), 2), round(float(p[1]), 2), int(p[2]), int(p[3])] for p in pts]}
    if work is not None: r["work"] = work
    if width is not None: r["width"] = width
    if morph: r["morph"] = {str(k): [[round(float(p[0]), 2), round(float(p[1]), 2), int(p[2])] for p in v] for k, v in morph.items()}
    return r

def POST(x0, x1, y0=0.3, y1=9.7, z=1): return {"type": "post", "x0": x0, "y0": y0, "x1": x1, "y1": y1, "z": z}
def DISC(x, y, r, z=1): return {"type": "disc", "x": x, "y": y, "r": r, "z": z}

def arc(cx, cy, r, a0, a1, n, z, s):
    return [(cx + r * math.cos(math.radians(a0 + (a1 - a0) * i / (n - 1))), cy + r * math.sin(math.radians(a0 + (a1 - a0) * i / (n - 1))), z, s) for i in range(n)]

# ---------- Clove hitch ----------
knot("clove", [
    "Bring the rope across the front of the post.",
    "Wrap it once around the post.",
    "Cross over the first wrap, diagonally, and go around the post again.",
    "Tuck the end under that diagonal, beside the standing part.",
    "Pull both ends tight. The diagonal holds both ends against the post.",
], [rope("blue", [
    (0, 6.4, 2, 0), (4, 6.4, 2, 0), (6.2, 6.4, 2, 0),
    (8, 6.35, 2, 1), (9.5, 6.25, 2, 1), (10.0, 5.95, 1, 1), (9.5, 5.65, 0, 1), (8, 5.0, 0, 1), (6.5, 4.0, 0, 1), (6.0, 3.7, 1, 1), (6.5, 3.4, 3, 1),
    (8.0, 5.0, 3, 2), (9.5, 7.2, 1, 2), (10.0, 7.5, 0, 2), (9.5, 7.8, 0, 2), (8, 6.9, 0, 2), (6.5, 5.5, 1, 2), (6.0, 5.2, 2, 2), (6.5, 4.95, 2, 2),
    (8.0, 4.95, 2, 3), (9.5, 4.95, 2, 3), (11.5, 4.95, 2, 3), (14.5, 4.95, 2, 3),
], work=3)], props=[POST(6.5, 9.5)])

# ---------- Two half hitches ----------
knot("halfhitch", [
    "The rope comes to the post (or tree, or ring).",
    "Pass the end around the post.",
    "Bring it over the long part, around behind it, and out through the gap: one half hitch.",
    "Make a second half hitch the same way, beside the first.",
    "Slide them snug against the post and pull tight.",
], [rope("blue", [
    (16, 4.4, 2, 0), (10, 4.4, 2, 0), (5.2, 4.4, 2, 0),
    (4.6, 4.4, 2, 1), (3.3, 4.5, 2, 1), (2.75, 4.9, 1, 1), (3.3, 5.35, 0, 1), (4.6, 5.5, 0, 1), (5.0, 5.6, 1, 1), (5.8, 5.7, 1, 1),
    (6.3, 5.4, 3, 2), (6.9, 3.5, 1, 2), (7.4, 3.3, 1, 2), (7.7, 3.6, 0, 2), (7.7, 5.2, 1, 2), (8.2, 5.6, 1, 2),
    (9.0, 5.4, 3, 3), (9.5, 3.5, 1, 3), (10.0, 3.3, 1, 3), (10.3, 3.6, 0, 3), (10.3, 5.2, 1, 3), (11, 5.6, 1, 3), (12.5, 5.9, 1, 3),
], work=3)], props=[POST(3.0, 4.6, z=1)])

# ---------- Taut-line hitch (seen from above: the peg is a circle) ----------
knot("taut", [
    "The line comes from the tent to a peg.",
    "Pass it around the peg and bring the end back alongside the line.",
    "Wrap the end twice around the line, working back towards the peg.",
    "Then once more around the line, on the far side of the first two wraps.",
    "Pull tight. Slide the knot to tighten the line; it grips when loaded.",
], [rope("blue", [
    (16, 4.0, 2, 0), (8, 4.0, 2, 0), (3.6, 4.0, 2, 0),
    (2.4, 3.95, 2, 1), (1.4, 4.4, 2, 1), (1.1, 5.4, 2, 1), (1.6, 6.3, 2, 1), (2.7, 6.6, 2, 1), (5, 6.7, 2, 1), (8.6, 6.7, 2, 1),
    (9.6, 6.3, 2, 2), (10.0, 5.6, 3, 2), (10.3, 3.2, 1, 2), (9.8, 3.0, 0, 2), (9.5, 4.9, 1, 2), (9.2, 5.5, 3, 2), (9.0, 3.2, 1, 2), (8.5, 3.0, 0, 2), (8.2, 4.9, 1, 2), (8.3, 5.4, 1, 2),
    (10.2, 5.7, 1, 3), (11.4, 5.5, 3, 3), (11.7, 3.2, 1, 3), (12.2, 3.0, 0, 3), (12.4, 4.8, 1, 3), (13.2, 5.6, 1, 3), (14.4, 6.2, 1, 3),
], work=3)], props=[DISC(2.2, 5.3, 0.8)])

# ---------- Bowline ----------
knot("bowline", [
    "Make a small loop (the hole), with the end side on top. Leave a big loop below.",
    "Bring the end up through the hole from underneath.",
    "Pass it behind the standing part (round the tree).",
    "Bring it back down through the hole, beside itself.",
    "Hold the end against the big loop and pull the standing part tight.",
], [rope("blue", [
    (8, 0.2, 1, 0), (8, 2.6, 1, 0), (8, 3.8, 1, 0), (8.05, 4.7, 1, 0), (7.6, 5.5, 1, 0), (6.6, 5.6, 1, 0), (5.9, 4.8, 1, 0), (6.1, 3.7, 1, 0), (6.9, 3.3, 1, 0), (7.6, 3.6, 2, 0), (8.6, 4.7, 1, 0),
    (9.3, 6.0, 1, 0), (9.6, 7.6, 1, 0), (8.8, 9.0, 1, 0), (7.2, 9.3, 1, 0), (5.8, 8.6, 1, 0), (5.4, 7.4, 1, 0),
    (6.0, 6.5, 0, 1), (6.5, 4.5, 2, 1), (6.6, 3.0, 1, 1), (6.9, 2.0, 0, 1),
    (8.0, 1.6, 0, 2), (9.0, 1.6, 1, 2), (9.4, 2.3, 2, 2),
    (8.0, 3.0, 2, 3), (7.4, 3.3, 2, 3), (7.15, 4.6, 0, 3), (7.0, 6.3, 1, 3), (7.0, 7.4, 1, 3),
], work=17)])

# ---------- Figure-eight loop, tied around something and followed back ----------
eight = [
    (9.5, 6.2, 1), (8.0, 6.2, 1), (6.6, 5.9, 1), (6.0, 4.9, 1), (6.4, 3.9, 1), (7.4, 3.5, 1), (8.4, 3.9, 2), (9.0, 5.4, 2), (9.4, 7.0, 1),
    (10.3, 7.5, 0), (10.8, 6.6, 0), (10.8, 5.2, 1), (10.3, 4.6, 2), (8.7, 4.7, 2), (7.6, 4.9, 1), (7.0, 5.4, 0), (6.6, 6.6, 1), (5.6, 7.3, 1),
]
def follow(pts, d):
    """The eight traced back, beside itself: reverse, shift to one side, keep each crossing's over/under."""
    rev = list(reversed(pts))
    out = []
    for j, p in enumerate(rev):
        a = rev[max(j - 1, 0)]; b = rev[min(j + 1, len(rev) - 1)]
        dx, dy = b[0] - a[0], b[1] - a[1]; L = math.hypot(dx, dy) or 1
        nx, ny = -dy / L, dx / L
        z = rev[j + 1][2] if j + 1 < len(rev) else p[2]
        out.append((p[0] + nx * d, p[1] + ny * d, z))
    return out
back = follow(eight, 0.62)
fig = [(16, 6.2, 1, 0), (12, 6.2, 1, 0)] + [(x, y, z, 0 if i < 9 else 1 if i < 12 else 2) for i, (x, y, z) in enumerate(eight)]
tree = [(4.6, 7.6, 1, 3), (3.6, 8.3, 1, 3), (2.3, 8.0, 1, 3), (1.8, 6.8, 1, 3), (2.4, 5.8, 1, 3), (3.6, 5.6, 1, 3), (4.6, 6.4, 1, 3)]
fig += tree + [(x, y, z, 4) for (x, y, z) in back] + [(12, 6.85, 1, 4), (14.5, 6.85, 1, 4)]
knot("figure8", [
    "Take an arm's length of the end. Make a loop that crosses over the standing part.",
    "Take the end around behind the standing part.",
    "Bring it over and back through the first loop: a figure eight.",
    "Pass the end around the tree (or through your harness).",
    "Follow the eight back with the end, strand beside strand, all the way.",
    "Pull all four strands tight: two neat parallel eights. Leave a tail.",
], [rope("blue", fig, work=3)], props=[DISC(3.2, 6.9, 0.85)])

# ---------- Sheet bend (blue thick rope, orange thin rope) ----------
knot("sheetbend", [
    "Make a U-bend in the end of the thicker rope.",
    "Pass the thin end up through the U from underneath.",
    "Take it around behind both legs of the U.",
    "Tuck it under itself, not through the U. Both short ends come out on the same side.",
    "Pull the thin rope's long part to tighten.",
], [
    rope("blue", [(0, 3.5, 1, 0), (5, 3.5, 1, 0), (8.0, 3.5, 1, 0), (9.4, 4.0, 1, 0), (9.9, 5.0, 1, 0), (9.4, 6.0, 1, 0), (8.0, 6.5, 1, 0), (5, 6.5, 1, 0), (2.5, 6.5, 1, 0)], width=0.7),
    rope("orange", [(16, 5.2, 1, 0), (12, 5.2, 1, 0), (11.0, 5.2, 0, 0),
                    (8.6, 5.3, 2, 1), (7.4, 5.3, 2, 1), (6.6, 4.9, 2, 1),
                    (6.2, 2.5, 1, 2), (5.6, 2.2, 0, 2), (5.1, 3.6, 0, 2), (5.0, 6.4, 0, 2), (5.3, 7.6, 1, 2), (6.0, 7.8, 2, 2),
                    (6.9, 6.9, 2, 3), (7.4, 5.9, 0, 3), (7.2, 4.6, 1, 3), (6.0, 4.4, 1, 3), (3.4, 4.4, 1, 3)]),
])

# ---------- Reef knot: one rope's U-bend, the other end threaded through ----------
knot("reef", [
    "Make a U-bend in one end (blue). Bring the other end (orange) alongside.",
    "Take the orange end over the U, then under it.",
    "Go around behind both legs of the U.",
    "Come back the same way: under, then over the U. Both orange parts lie side by side.",
    "Pull all four parts tight. It lies flat; each rope's two parts stay together.",
], [
    rope("blue", [(16, 4.1, 2, 0), (12, 4.1, 2, 0), (8.8, 4.1, 2, 0), (6.4, 1.8, 0, 0), (4.4, 5.0, 0, 0), (6.4, 8.2, 2, 0), (8.8, 5.9, 2, 0), (12, 5.9, 2, 0), (16, 5.9, 2, 0)]),
    rope("orange", [(0, 7.2, 1, 0), (3.0, 7.2, 1, 0),
                    (5.6, 7.2, 1, 1), (7.4, 7.2, 1, 1), (10, 7.2, 1, 1),
                    (11.6, 7.1, 1, 2), (12.9, 5.0, 1, 2), (11.6, 2.9, 1, 2),
                    (9, 2.8, 1, 3), (7.4, 2.8, 1, 3), (5.6, 2.8, 1, 3), (3.0, 2.8, 1, 3), (0.5, 2.8, 1, 3)]),
])

# ---------- Double fisherman's ----------
def wraps(x0, y_top, y_bot, n, step, s, d=-1.25):
    pts = []
    x = x0
    for i in range(n):
        pts += [(x, y_top, 2, s), (x + d * 0.5, y_bot, 1, s), (x + d * 0.75, y_bot + 0.35, 0, s), (x + d, y_top, 1, s)]
        x += d
    return pts
blue_df = [(0, 4.6, 1, 0), (6, 4.6, 1, 0), (10.8, 4.6, 1, 0),
           (11.6, 4.0, 1, 1)] + wraps(11.2, 3.7, 6.2, 2, 1, 1) + [(8.6, 6.4, 1, 1), (8.4, 5.0, 1, 1), (10.2, 5.0, 1, 1), (11.8, 5.0, 1, 1), (12.8, 5.1, 1, 1)]
orange_df = [(16, 5.4, 1, 0), (10, 5.4, 1, 0), (5.2, 5.4, 1, 0),
             (4.4, 6.0, 1, 2)] + [(x, 10 - y, z, s) for (x, y, z, s) in wraps(4.8, 10 - 6.3, 10 - 3.8, 2, 2, 2, d=1.25)] + [(7.4, 3.6, 1, 2), (7.6, 5.0, 1, 2), (5.8, 5.0, 1, 2), (4.2, 5.0, 1, 2), (3.2, 4.9, 1, 2)]
knot("doublefish", [
    "Lay the two ends side by side, pointing opposite ways, overlapping an arm's length.",
    "Wrap the blue end twice around both ropes, back towards itself, then tuck it through both wraps.",
    "Do the same with the orange end on the other side.",
    "Pull the two long parts: the two knots slide together and sit side by side.",
], [rope("blue", blue_df), rope("orange", orange_df)])

# ---------- Trucker's hitch ----------
knot("trucker", [
    "Tie one end to the first anchor.",
    "Halfway, make a small fixed loop in the rope.",
    "Take the free end around the second anchor and back through the loop.",
    "Pull the free end towards the first anchor: it pulls with about twice your strength.",
    "Hold it and finish with two half hitches below the loop.",
], [rope("blue", [
    (2.0, 4.0, 2, 0), (1.0, 4.4, 2, 0), (0.9, 5.5, 2, 0), (1.6, 6.2, 2, 0), (2.6, 6.0, 2, 0), (3.0, 5.0, 2, 0), (2.7, 4.1, 2, 0), (3.6, 4.0, 2, 0), (5.5, 4.0, 2, 0),
    (6.8, 4.0, 1, 1), (7.6, 4.4, 1, 1), (7.8, 5.6, 1, 1), (8.4, 6.5, 1, 1), (9.0, 5.6, 2, 1), (8.7, 4.5, 2, 1), (7.9, 4.0, 1, 1), (9.4, 4.0, 1, 1),
    (12.5, 4.0, 1, 2), (14.3, 4.0, 1, 2), (15.3, 4.6, 1, 2), (15.3, 5.8, 1, 2), (14.3, 6.4, 1, 2), (12, 6.6, 1, 2), (9.6, 6.6, 1, 2), (8.8, 6.0, 0, 2), (7.8, 6.0, 2, 2), (7.2, 6.4, 1, 2),
    (6.6, 7.2, 1, 3), (5.4, 7.6, 1, 3),
    (6.1, 6.9, 3, 4), (6.4, 5.7, 1, 4), (6.9, 5.5, 0, 4), (6.9, 7.2, 1, 4), (5.4, 7.4, 3, 4), (5.6, 5.8, 1, 4), (6.0, 5.6, 0, 4), (5.9, 7.4, 1, 4), (5.2, 8.6, 1, 4),
], work=9)], props=[DISC(2.0, 5.1, 0.75), DISC(14.3, 5.2, 0.75)], pull=3)

# ---------- Prusik ----------
up = []
for y in [7.0, 5.8, 4.6]:
    up += [(6.8, y + 0.1, 2, 1), (9.2, y - 0.25, 2, 1), (9.6, y - 0.45, 1, 1), (9.2, y - 0.65, 0, 1), (6.8, y - 0.95, 0, 1), (6.4, y - 0.8, 1, 1)]
down = []
for y in [3.0, 4.2, 5.4]:
    down += [(6.8, y + 0.1, 2, 2), (9.2, y + 0.4, 2, 2), (9.6, y + 0.6, 1, 2), (9.2, y + 0.8, 0, 2), (6.8, y + 1.05, 0, 2), (6.4, y + 0.95, 1, 2)]
knot("prusik", [
    "Bring a closed loop of thinner cord to the rope.",
    "Wrap it around the rope three times, going up, each wrap inside the loop.",
    "Cross over at the top and wrap back down, beside the first wraps.",
    "Both legs come out in the middle. Make the wraps lie flat and parallel.",
    "Pull down on the loop: it grips. Push the wraps (not the loop) to slide it.",
], [
    rope("blue", [(8, 0.0, 1, 0), (8, 5, 1, 0), (8, 10, 1, 0)], width=0.8),
    rope("orange", [(7.6, 9.6, 1, 0), (6.6, 8.4, 1, 0), (6.4, 7.6, 1, 0)] + up + [(6.6, 3.4, 2, 2), (8.0, 2.4, 0, 2), (9.0, 2.3, 1, 2), (6.0, 2.9, 1, 2)] + down + [(8.4, 7.2, 1, 3), (9.4, 8.0, 1, 3), (8.4, 9.6, 1, 3)], width=0.3),
], props=[])

# ---------- Square lashing ----------
lash = [(0, 8.0, 3, 0), (4, 8.0, 3, 0), (6.6, 7.9, 3, 0), (8.8, 7.6, 3, 0), (9.3, 7.3, 1, 0), (8.8, 7.0, 0, 0), (7.2, 7.0, 0, 0), (6.6, 6.9, 3, 0), (8.0, 7.3, 3, 0), (8.6, 8.2, 3, 0)]
lash = [(0, 7.6, 3, 0), (4, 7.6, 3, 0), (6.7, 7.6, 3, 0), (8.6, 7.4, 3, 0), (9.2, 7.1, 1, 0), (8.6, 6.8, 0, 0), (7.2, 6.8, 0, 0), (6.6, 6.6, 1, 0)]
for i, (xl, xr) in enumerate([(6.5, 9.5), (5.9, 10.1), (5.3, 10.7)]):
    s = 1 if i < 2 else 2
    lash += [(xl, 6.3, 3, s), (xl, 3.7, 3, s), (xl + 0.3, 3.3, 0, s), (8, 3.2, 0, s), (xr - 0.3, 3.3, 1, s), (xr, 3.7, 3, s), (xr, 6.3, 3, s), (xr - 0.3, 6.7, 0, s), (8, 6.8, 0, s), (xl - 0.3, 6.7 - 0.0, 1, s)]
lash[-1] = (lash[-1][0], lash[-1][1], 1, 2)
lash += [(4.6, 5.0, 4, 3), (8, 4.85, 4, 3), (11.4, 5.0, 4, 3)]
lash += [(12.4, 3.9, 3, 4), (12.7, 6.2, 1, 4), (13.1, 6.4, 0, 4), (13.2, 4.0, 3, 4), (13.0, 3.5, 3, 4), (13.9, 4.1, 3, 4), (14.8, 6.2, 3, 4), (15.4, 7.0, 3, 4)]
knot("lashing", [
    "Tie a clove hitch on the upright pole, just below the crossbar.",
    "Wrap over the crossbar, behind the upright, over the crossbar again, behind the upright: a square.",
    "Do it three or four times, each wrap tight and beside the last.",
    "Frap: wrap two or three times between the poles, around the wraps, to pull them tight.",
    "Finish with a clove hitch on the crossbar.",
], [rope("blue", lash, work=8)], props=[POST(7.2, 8.8, 0.2, 9.8, z=1), {"type": "post", "x0": 0.6, "y0": 4.3, "x1": 15.4, "y1": 5.7, "z": 2}], pull=-1)

# ---------- Alpine butterfly (the rope changes shape: each step is a keyframe) ----------
final = [
    (0, 6.2, 1), (4.0, 6.2, 1), (6.2, 6.0, 2), (8.0, 5.2, 2), (9.4, 4.4, 2), (9.0, 3.4, 2), (8.0, 3.0, 2), (7.2, 3.4, 1),
    (6.9, 4.4, 0), (6.9, 6.0, 0), (7.0, 7.4, 1), (7.4, 8.4, 1), (8.0, 8.7, 1), (8.6, 8.4, 1), (9.0, 7.4, 0), (9.1, 6.0, 0),
    (9.1, 4.6, 1), (8.8, 3.8, 0), (8.0, 3.7, 1), (6.9, 3.6, 1), (6.5, 4.3, 1), (7.0, 5.0, 1), (8.0, 5.6, 1), (9.8, 6.0, 1), (12, 6.2, 1), (16, 6.2, 1),
]
n = len(final)
straight = [(16 * i / (n - 1), 6.2, 1) for i in range(n)]
loop = [(0, 6.2, 1), (4.0, 6.2, 1), (5.8, 6.1, 1), (6.4, 5.4, 1), (6.4, 4.4, 1), (6.3, 3.4, 1), (6.4, 2.4, 1), (6.9, 1.4, 1),
        (7.6, 0.9, 1), (8.4, 0.9, 1), (9.1, 1.4, 1), (9.6, 2.4, 1), (9.7, 3.4, 1), (9.6, 4.4, 1), (9.6, 5.4, 1), (10.1, 6.0, 1),
        (10.6, 6.1, 1), (11.0, 6.15, 1), (11.4, 6.2, 1), (11.8, 6.2, 1), (12.2, 6.2, 1), (12.6, 6.2, 1), (13.0, 6.2, 1), (13.6, 6.2, 1), (14.6, 6.2, 1), (16, 6.2, 1)]
twist = [(0, 6.2, 1), (4.0, 6.2, 1), (6.2, 6.0, 2), (8.0, 5.2, 2), (9.4, 4.4, 1), (9.2, 3.8, 2), (8.0, 3.0, 2), (7.0, 2.2, 1),
         (6.8, 1.4, 1), (7.4, 0.8, 1), (8.0, 0.7, 1), (8.6, 0.8, 1), (9.2, 1.4, 1), (9.0, 2.2, 1), (8.4, 2.7, 1), (8.0, 3.0, 1),
         (7.4, 3.5, 1), (6.8, 4.0, 1), (6.6, 4.4, 1), (6.7, 4.7, 1), (7.0, 5.0, 1), (8.0, 5.3, 1), (9.0, 5.8, 1), (9.8, 6.0, 1), (12, 6.2, 1), (16, 6.2, 1)]
assert len(loop) == n and len(twist) == n
knot("butterfly", [
    "Lay the rope out where you want the loop.",
    "Make a loop and let it hang.",
    "Twist the loop twice, the same way: a figure-eight shape with a small opening in the middle.",
    "Fold the top loop down in front and push it up through the bottom opening.",
    "Hold the loop and pull both ends apart to tighten.",
], [rope("blue", [(x, y, z, 0) for (x, y, z) in final], morph={0: straight, 1: loop, 2: twist, 3: final})], pull=4)

json.dump(K, open("knots.json", "w"), indent=0)
print(len(K), "knots")
