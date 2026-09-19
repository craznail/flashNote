"""Apply the user-selected FlashNote logo and floating-ball artwork."""

from pathlib import Path

from PIL import Image, ImageChops, ImageDraw


ROOT = Path(__file__).resolve().parents[1]
SELECTED_LOGO = ROOT / "branding/concepts/flashnote-logo-two-layer-preview.png"
ORIGINAL_CONCEPT = ROOT / "branding/concepts/flashnote-logo-ball-concept-1.png"
ARCHIVED_LOGO = ROOT / "branding/concepts/flashnote-logo-original-concept-1.png"
BALL_SOURCE = ROOT / "branding/flashnote_ball_note_source.png"
BALL_RESOURCE = ROOT / "app/src/main/res/drawable/ic_ball_normal.png"

LAUNCHER_SIZES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}


def save_launcher_icons() -> None:
    logo = Image.open(SELECTED_LOGO).convert("RGB")
    for directory, size in LAUNCHER_SIZES.items():
        scaled = logo.resize((size, size), Image.Resampling.LANCZOS)
        target_dir = ROOT / "app/src/main/res" / directory
        for name in ("ic_launcher.png", "ic_launcher_round.png"):
            scaled.save(target_dir / name, optimize=True)


def extract_original_companion_logo(concept: Image.Image) -> None:
    # The accepted concept board placed the original logo on the left.
    concept.crop((20, 250, 720, 950)).save(ARCHIVED_LOGO, optimize=True)


def extract_selected_ball(concept: Image.Image) -> None:
    # Crop to the visible ball edge so the 48dp view has no translucent halo/padding.
    ball = concept.crop((754, 402, 1193, 841))
    circle = Image.new("L", ball.size, 0)
    ImageDraw.Draw(circle).ellipse((0, 0, ball.width - 1, ball.height - 1), fill=255)
    alpha = ImageChops.multiply(ball.getchannel("A"), circle)
    ball.putalpha(alpha)
    ball.save(BALL_SOURCE, optimize=True)
    ball.resize((160, 160), Image.Resampling.LANCZOS).save(
        BALL_RESOURCE,
        optimize=True,
    )


def main() -> None:
    concept = Image.open(ORIGINAL_CONCEPT).convert("RGBA")
    save_launcher_icons()
    extract_original_companion_logo(concept)
    extract_selected_ball(concept)


if __name__ == "__main__":
    main()
