"""Build the idle/docked FlashNote ball from the accepted high-blue side background.

The active ball stays untouched. The idle asset keeps the active ball's center artwork
and alpha silhouette, replacing only the left/right edge regions with the new blue-heavy
glass background so the small exposed sliver remains recognizable when retracted.
"""
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ACTIVE = ROOT / "app/src/main/res/drawable/ic_ball_normal.png"
HIDE_SOURCE = ROOT / "branding/flashnote_ball_hide_source.png"
OUTPUT = ROOT / "app/src/main/res/drawable/ic_ball_idle.png"


def crop_alpha(image: Image.Image) -> Image.Image:
    rgba = image.convert("RGBA")
    bbox = rgba.getchannel("A").getbbox()
    return rgba.crop(bbox) if bbox else rgba


def main() -> None:
    active = Image.open(ACTIVE).convert("RGBA")
    source = crop_alpha(Image.open(HIDE_SOURCE))
    source = source.resize(active.size, Image.Resampling.LANCZOS)

    width, height = active.size
    active_px = active.load()
    source_px = source.load()

    # Preserve the center exactly. Blend progressively into the blue-heavy source
    # only near the horizontal edges, where the retracted ball remains visible.
    center_x = (width - 1) / 2.0
    radius = width / 2.0
    blend_start = 0.54  # fraction of radius: center remains untouched
    blend_full = 0.82   # full replacement toward the extreme side arcs

    out = Image.new("RGBA", active.size, (0, 0, 0, 0))
    out_px = out.load()

    for y in range(height):
        for x in range(width):
            nx = abs(x - center_x) / radius
            t = (nx - blend_start) / (blend_full - blend_start)
            t = max(0.0, min(1.0, t))
            # Smoothstep avoids a visible vertical seam.
            t = t * t * (3.0 - 2.0 * t)

            ar, ag, ab, aa = active_px[x, y]
            sr, sg, sb, sa = source_px[x, y]
            # Generated source pixels outside/along its anti-aliased orb can carry black
            # RGB under transparent alpha. Never let those hidden RGB values contaminate
            # the active ball's translucent rim; weight the replacement by source alpha.
            effective_t = t * (sa / 255.0)
            out_px[x, y] = (
                round(ar * (1.0 - effective_t) + sr * effective_t),
                round(ag * (1.0 - effective_t) + sg * effective_t),
                round(ab * (1.0 - effective_t) + sb * effective_t),
                aa,  # exact same outer silhouette/anti-aliasing as active asset
            )

    out.save(OUTPUT, optimize=True)
    print(f"idle ball -> {OUTPUT} ({width}x{height})")


if __name__ == "__main__":
    main()
