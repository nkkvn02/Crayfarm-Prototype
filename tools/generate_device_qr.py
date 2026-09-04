"""Generate a printable CrayFarm ESP32 QR label.

Usage:
    python tools/generate_device_qr.py CRAY-ESP32-001
"""

from __future__ import annotations

import argparse
import re
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont
from reportlab.graphics.barcode import qr


DEVICE_ID_PATTERN = re.compile(r"^[A-Za-z0-9_-]{6,32}$")
QR_PREFIX = "CRAYFARM_DEVICE:"
CANVAS_WIDTH = 1400
CANVAS_HEIGHT = 1680
QR_SIZE = 1180
CRAYFARM_BLUE = "#2639A6"


def load_font(size: int, bold: bool = False) -> ImageFont.ImageFont:
    candidates = (
        "C:/Windows/Fonts/arialbd.ttf" if bold else "C:/Windows/Fonts/arial.ttf",
        "DejaVuSans-Bold.ttf" if bold else "DejaVuSans.ttf",
    )
    for candidate in candidates:
        try:
            return ImageFont.truetype(candidate, size=size)
        except OSError:
            continue
    return ImageFont.load_default()


def centered_text(
    draw: ImageDraw.ImageDraw,
    y: int,
    text: str,
    font: ImageFont.ImageFont,
    fill: str,
) -> None:
    bounds = draw.textbbox((0, 0), text, font=font)
    text_width = bounds[2] - bounds[0]
    draw.text(((CANVAS_WIDTH - text_width) / 2, y), text, font=font, fill=fill)


def generate_label(device_id: str, output_path: Path) -> None:
    normalized_id = device_id.strip().upper()
    if not DEVICE_ID_PATTERN.fullmatch(normalized_id):
        raise ValueError(
            "Device ID must contain 6-32 letters, numbers, hyphens, or underscores."
        )

    payload = f"{QR_PREFIX}{normalized_id}"
    widget = qr.QrCodeWidget(payload)
    widget.barLevel = "M"
    widget.qr.make()
    modules = widget.qr.modules
    module_count = widget.qr.moduleCount
    quiet_zone = 4
    total_modules = module_count + (quiet_zone * 2)
    module_size = QR_SIZE // total_modules
    rendered_size = module_size * total_modules
    qr_image = Image.new("RGB", (rendered_size, rendered_size), "white")
    qr_draw = ImageDraw.Draw(qr_image)

    for row_index, row in enumerate(modules):
        for column_index, is_dark in enumerate(row):
            if not is_dark:
                continue
            left = (column_index + quiet_zone) * module_size
            top = (row_index + quiet_zone) * module_size
            qr_draw.rectangle(
                (left, top, left + module_size - 1, top + module_size - 1),
                fill="black",
            )

    label = Image.new("RGB", (CANVAS_WIDTH, CANVAS_HEIGHT), "white")
    label.paste(qr_image, ((CANVAS_WIDTH - rendered_size) // 2, 80))
    draw = ImageDraw.Draw(label)

    centered_text(draw, 1300, "CRAYFARM DEVICE", load_font(70, bold=True), CRAYFARM_BLUE)
    centered_text(draw, 1400, normalized_id, load_font(74, bold=True), "#111827")
    centered_text(
        draw,
        1515,
        "Scan in CrayFarm > Add Pond",
        load_font(38),
        "#4B5563",
    )

    output_path.parent.mkdir(parents=True, exist_ok=True)
    label.save(output_path, format="PNG", dpi=(300, 300), optimize=True)
    print(f"Created {output_path.resolve()}")
    print(f"Payload: {payload}")


def main() -> None:
    parser = argparse.ArgumentParser(description="Generate a CrayFarm device QR label.")
    parser.add_argument("device_id", help="ESP32 Device ID, such as CRAY-ESP32-001")
    parser.add_argument(
        "--output",
        type=Path,
        help="PNG output path (defaults to artifacts/<device-id>-qr-label.png)",
    )
    args = parser.parse_args()
    normalized_id = args.device_id.strip().upper()
    output_path = args.output or Path("artifacts") / f"{normalized_id}-qr-label.png"
    generate_label(normalized_id, output_path)


if __name__ == "__main__":
    main()
