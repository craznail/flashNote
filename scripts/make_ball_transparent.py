"""Cut the blue ball circle out of the white-background source and export a
transparent-background ic_ball_normal.png (RGBA) for the overlay ball."""
from PIL import Image, ImageDraw

SRC = "branding/flashnote_ball_normal_source.png"
DST = "app/src/main/res/drawable/ic_ball_normal.png"
OUT_SIZE = 160  # matches previous asset (40dp @ xxxhdpi)

img = Image.open(SRC).convert("RGB")
w, h = img.size

# Locate the blue circle: bounding box of non-(near-)white pixels.
gray = img.convert("L")
mask = gray.point(lambda p: 255 if p < 245 else 0)
bbox = mask.getbbox()
assert bbox, "no non-white pixels found"
cx = (bbox[0] + bbox[2]) / 2.0
cy = (bbox[1] + bbox[3]) / 2.0
radius = max(bbox[2] - bbox[0], bbox[3] - bbox[1]) / 2.0

# Supersampled circular alpha mask (4x) for smooth edges.
SS = 4
big = Image.new("L", (w * SS, h * SS), 0)
d = ImageDraw.Draw(big)
d.ellipse(
    [(cx - radius) * SS, (cy - radius) * SS, (cx + radius) * SS, (cy + radius) * SS],
    fill=255,
)
alpha = big.resize((w, h), Image.LANCZOS)

out = img.convert("RGBA")
out.putalpha(alpha)

# Crop to the circle bbox (square, centered) then downscale.
pad = 2
left = int(max(0, cx - radius - pad))
top = int(max(0, cy - radius - pad))
right = int(min(w, cx + radius + pad))
bottom = int(min(h, cy + radius + pad))
side = max(right - left, bottom - top)
# square crop centered on the circle
left = int(max(0, cx - side / 2))
top = int(max(0, cy - side / 2))
out = out.crop((left, top, left + side, top + side))
out = out.resize((OUT_SIZE, OUT_SIZE), Image.LANCZOS)
out.save(DST)
print(f"circle center=({cx:.1f},{cy:.1f}) r={radius:.1f} -> {DST} {out.size}")
