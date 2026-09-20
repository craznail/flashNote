"""Generate the FlashNote floating-ball resource from the selected brand source.

This script intentionally does not touch launcher/mipmap assets. The app logo
is a separate brand asset and must remain unchanged when refining the overlay.
"""

from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFilter


ROOT = Path(__file__).resolve().parents[1]
# This is the previously accepted blue-white glass ball with the note element.
BALL_SOURCE = ROOT / "branding/flashnote_ball_note_source.png"
BALL_RESOURCE = ROOT / "app/src/main/res/drawable/ic_ball_normal.png"
RESOURCE_SIZE = 224  # supports the 56dp EXTRA_LARGE ball at xxxhdpi


def circular_alpha(image: Image.Image) -> Image.Image:
    """Trim transparent padding and constrain the artwork to a clean circle."""
    rgba = image.convert("RGBA")
    bbox = rgba.getchannel("A").getbbox()
    if bbox:
        rgba = rgba.crop(bbox)

    side = max(rgba.width, rgba.height)
    square = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    square.alpha_composite(
        rgba,
        ((side - rgba.width) // 2, (side - rgba.height) // 2),
    )

    mask = Image.new("L", (side * 4, side * 4), 0)
    draw = ImageDraw.Draw(mask)
    draw.ellipse((0, 0, side * 4 - 1, side * 4 - 1), fill=255)
    mask = mask.resize((side, side), Image.Resampling.LANCZOS)
    square.putalpha(ImageChops.multiply(square.getchannel("A"), mask))
    return square


def apply_spherical_wrap(image: Image.Image) -> Image.Image:
    """Apply a very mild barrel warp so the note reads as painted onto the glass."""
    ball = image.convert("RGBA")
    width, height = ball.size
    cols = rows = 8
    mesh = []

    def source_point(x: float, y: float) -> tuple[float, float]:
        nx = (x - width / 2) / (width / 2)
        ny = (y - height / 2) / (height / 2)
        radius2 = nx * nx + ny * ny
        scale = 1.0 + 0.018 * radius2
        sx = width / 2 + nx * (width / 2) * scale
        sy = height / 2 + ny * (height / 2) * scale
        return (
            min(max(sx, 0.0), width - 1.0),
            min(max(sy, 0.0), height - 1.0),
        )

    for row in range(rows):
        y0 = row * height / rows
        y1 = (row + 1) * height / rows
        for col in range(cols):
            x0 = col * width / cols
            x1 = (col + 1) * width / cols
            ul = source_point(x0, y0)
            ll = source_point(x0, y1)
            lr = source_point(x1, y1)
            ur = source_point(x1, y0)
            mesh.append(((int(x0), int(y0), int(x1), int(y1)), ul + ll + lr + ur))

    return ball.transform(
        ball.size,
        Image.Transform.MESH,
        mesh,
        resample=Image.Resampling.BICUBIC,
    )


def apply_ball_surface_finish(image: Image.Image) -> Image.Image:
    """Bond the note visually to the sphere while keeping it readable at 44dp."""
    ball = image.convert("RGBA")
    width, height = ball.size
    overlay = Image.new("RGBA", ball.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(overlay)

    # Let the sphere lighting pass softly over the note instead of giving the
    # note a separate floating-card shadow.
    draw.ellipse(
        (
            int(width * 0.08),
            int(height * 0.03),
            int(width * 0.90),
            int(height * 0.70),
        ),
        fill=(255, 255, 255, 22),
    )
    draw.ellipse(
        (
            int(width * 0.12),
            int(height * 0.45),
            int(width * 0.96),
            int(height * 1.04),
        ),
        fill=(20, 70, 135, 15),
    )
    overlay = overlay.filter(ImageFilter.GaussianBlur(max(1, width // 40)))
    return Image.alpha_composite(ball, overlay)


def generate_overlay_ball() -> None:
    source = Image.open(BALL_SOURCE)
    ball = circular_alpha(source)
    ball = apply_spherical_wrap(ball)
    ball = circular_alpha(ball)
    ball = apply_ball_surface_finish(ball)
    ball.resize(
        (RESOURCE_SIZE, RESOURCE_SIZE),
        Image.Resampling.LANCZOS,
    ).save(BALL_RESOURCE, optimize=True)


def main() -> None:
    generate_overlay_ball()
    print(f"overlay ball -> {BALL_RESOURCE} ({RESOURCE_SIZE}x{RESOURCE_SIZE})")
    print("launcher icons unchanged")


if __name__ == "__main__":
    main()
