"""Generate fixed Noto Sans SC text sprites for the V0.3.5 pixel dashboard.

The editable JSON remains the layout source of truth.  Android draws every
logical bitmap at 2x nearest-neighbour scale and overlays these pre-rasterized
physical-pixel text sprites.  Dynamic status text is deliberately excluded and
is rendered by the app because its content changes at runtime.
"""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


DEFAULT_FONT = Path(r"C:\Windows\Fonts\NotoSansSC-VF.ttf")
PHOSPHOR = (0x76, 0xFF, 0x96, 0xFF)


def safe_id(value: str) -> str:
    return re.sub(r"[^a-z0-9_]+", "_", value.lower().replace("-", "_")).strip("_")


def load_font(path: Path, size: int, weight: int) -> ImageFont.FreeTypeFont:
    selected = ImageFont.truetype(str(path), size=size)
    try:
        selected.set_variation_by_name("Bold" if weight >= 700 else "Medium")
    except OSError:
        pass
    return selected


def render_layer(layer: dict, font_path: Path) -> Image.Image:
    config = layer["physicalText"]
    width = int(layer["w"]) * 2
    height = int(layer["h"]) * 2
    mask = Image.new("L", (width, height), 0)
    draw = ImageDraw.Draw(mask)
    size = int(config["sizePx"])
    selected_font = load_font(font_path, size, int(config.get("weight", 500)))
    lines = str(config["text"]).split("\n")
    line_height = max(1, round(float(config.get("lineHeightPx", size * 1.12))))
    block_height = len(lines) * line_height
    first_y = (height - block_height) / 2 + line_height / 2
    align = config.get("align", "center")
    if align == "left":
        x, anchor = 0, "lm"
    elif align == "right":
        x, anchor = width, "rm"
    else:
        x, anchor = width / 2, "mm"
    for index, line in enumerate(lines):
        draw.text(
            (x, first_y + index * line_height),
            line,
            font=selected_font,
            fill=255,
            anchor=anchor,
        )
    threshold = int(config.get("threshold", 96))
    binary = mask.point(lambda value: 255 if value >= threshold else 0)
    rgba = Image.new("RGBA", (width, height), PHOSPHOR)
    rgba.putalpha(binary)
    return rgba


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("layout", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--font", type=Path, default=DEFAULT_FONT)
    args = parser.parse_args()

    if not args.font.is_file():
        raise FileNotFoundError(args.font)
    project = json.loads(args.layout.read_text(encoding="utf-8"))
    args.output.mkdir(parents=True, exist_ok=True)
    generated: list[str] = []
    for layer in project["layers"]:
        if not layer.get("visible", True) or not layer.get("physicalText"):
            continue
        if layer.get("id") == "status-block":
            continue
        filename = f"physical_{safe_id(layer['id'])}.png"
        render_layer(layer, args.font).save(args.output / filename, optimize=True)
        generated.append(filename)
    print(json.dumps({"generated": generated, "count": len(generated)}, ensure_ascii=False))


if __name__ == "__main__":
    main()
