# Knot animations

`author.py` writes `knots.json` (copy it to `app/src/main/assets/knots.json`); `render.py knots.json [ids…]` draws every
step of each knot to `sheet-<id>.png` so you can check the shapes before building the app.

Each rope is a list of control points `[x, y, z, step]` on a 16 × 10 grid: `z` is how high that piece lies (a crossing
where one strand has a lower z than the other shows a gap: it passes under), `step` is the step that draws it. A rope
can instead have `morph` shapes (one per step) for knots tied by reshaping the rope (the alpine butterfly). Props are
posts (`post`) and trees or pegs seen from above (`disc`). The app's guide steps must equal each knot's `steps`
(`KnotDiagramsTest` checks).
