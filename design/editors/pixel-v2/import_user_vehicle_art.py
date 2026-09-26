"""Import the user's final RX400h PNG artwork into the V0.3.5 pixel JSON."""

from __future__ import annotations

import argparse
import base64
import copy
import json
from pathlib import Path

from PIL import Image, ImageDraw


TARGETS = {
    "side-car": (182, 101),
    "top-car": (124, 225),
    "wheel-base": (182, 101),
    "wheel-spokes": (182, 101),
}
WHEEL_CENTERS = ((39, 77), (145, 77))


def alpha_mask(path: Path, size: tuple[int, int], threshold: int) -> Image.Image:
    image = Image.open(path).convert("RGBA")
    if image.size != size:
        raise ValueError(f"{path.name}: expected {size[0]}x{size[1]}, got {image.width}x{image.height}")
    alpha = image.getchannel("A")
    return alpha.point(lambda value: 255 if value >= threshold else 0, mode="1")


def clear_static_wheels(side: Image.Image) -> Image.Image:
    result = side.copy()
    draw = ImageDraw.Draw(result)
    # Clear the wheel discs from the side-body layer.  The wheel-arch bodywork
    # outside the 14-pixel radius is retained, while the user's separate wheel
    # sprite becomes the only rotating content inside each arch.
    for cx, cy in WHEEL_CENTERS:
        draw.ellipse((cx - 14, cy - 14, cx + 14, cy + 14), fill=0)
    return result


def repeated_wheel_mask(path: Path, threshold: int) -> Image.Image:
    sprite = alpha_mask(path, (29, 30), threshold)
    result = Image.new("1", TARGETS["wheel-spokes"], 0)
    for cx, cy in WHEEL_CENTERS:
        # The even sprite height has a half-pixel centre; align its lower
        # centre pixel with the editor/runtime's integer rotation centre.
        result.paste(sprite, (cx - 14, cy - 15))
    return result


def encode_mask(image: Image.Image) -> str:
    raw = bytes(1 if value else 0 for value in image.convert("L").tobytes())
    return base64.b64encode(raw).decode("ascii")


def decode_mask(layer: dict) -> Image.Image:
    raw = base64.b64decode(layer["data"])
    expected = int(layer["w"]) * int(layer["h"])
    if len(raw) != expected:
        raise ValueError(f"{layer['id']}: expected {expected} bytes, got {len(raw)}")
    return Image.frombytes("L", (int(layer["w"]), int(layer["h"])), bytes(255 if v else 0 for v in raw))


def import_art(project: dict, side_path: Path, wheel_path: Path, top_path: Path, threshold: int):
    masks = {
        "top-car": alpha_mask(top_path, TARGETS["top-car"], threshold),
        "side-car": clear_static_wheels(alpha_mask(side_path, TARGETS["side-car"], threshold)),
        "wheel-base": Image.new("1", TARGETS["wheel-base"], 0),
        "wheel-spokes": repeated_wheel_mask(wheel_path, threshold),
    }
    found = set()
    for layer in project["layers"]:
        layer_id = layer.get("id")
        if layer_id not in masks:
            continue
        expected = TARGETS[layer_id]
        if (layer["w"], layer["h"]) != expected:
            raise ValueError(f"{layer_id}: JSON geometry changed from expected {expected}")
        layer["data"] = encode_mask(masks[layer_id])
        found.add(layer_id)
    if found != set(TARGETS):
        raise ValueError(f"Missing target layers: {sorted(set(TARGETS) - found)}")
    return masks


def contact_sheet(side_path: Path, wheel_path: Path, top_path: Path, output: Path):
    thresholds = (16, 32, 64, 96, 128, 160, 192)
    cell_w, cell_h = 440, 480
    sheet = Image.new("RGB", (cell_w * 4, cell_h * 2), "black")
    label_draw = ImageDraw.Draw(sheet)
    for index, threshold in enumerate(thresholds):
        masks = {
            "side": clear_static_wheels(alpha_mask(side_path, (182, 101), threshold)),
            "wheel": repeated_wheel_mask(wheel_path, threshold),
            "top": alpha_mask(top_path, (124, 225), threshold),
        }
        side = Image.new("L", (182, 101), 0)
        side.paste(masks["side"])
        side = Image.frombytes("L", side.size, bytes(max(a, b) for a, b in zip(side.tobytes(), masks["wheel"].convert("L").tobytes())))
        x = (index % 4) * cell_w
        y = (index // 4) * cell_h
        label_draw.text((x + 12, y + 8), f"alpha >= {threshold}", fill=(118, 255, 150))
        top_rgba = colorize(masks["top"]).resize((124 * 2, 225 * 2), Image.Resampling.NEAREST)
        side_rgba = colorize(side).resize((182 * 2, 101 * 2), Image.Resampling.NEAREST)
        sheet.paste(top_rgba, (x + 10, y + 28), top_rgba)
        sheet.paste(side_rgba, (x + 268, y + 130), side_rgba)
    sheet.save(output)


def colorize(mask: Image.Image) -> Image.Image:
    result = Image.new("RGBA", mask.size, (118, 255, 150, 0))
    result.putalpha(mask.convert("L"))
    return result


def focused_preview(masks: dict, output: Path):
    sheet = Image.new("RGB", (1200, 720), "black")
    top = colorize(masks["top-car"]).resize((372, 675), Image.Resampling.NEAREST)
    side_composite = Image.new("L", TARGETS["side-car"], 0)
    side_composite.paste(masks["side-car"].convert("L"))
    side_composite = Image.frombytes(
        "L",
        side_composite.size,
        bytes(max(a, b) for a, b in zip(side_composite.tobytes(), masks["wheel-spokes"].convert("L").tobytes())),
    )
    side = colorize(side_composite).resize((728, 404), Image.Resampling.NEAREST)
    sheet.paste(top, (28, 22), top)
    sheet.paste(side, (438, 158), side)
    sheet.save(output)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("base_json", type=Path)
    parser.add_argument("side_png", type=Path)
    parser.add_argument("wheel_png", type=Path)
    parser.add_argument("top_png", type=Path)
    parser.add_argument("output_json", type=Path)
    parser.add_argument("--threshold", type=int, default=96)
    parser.add_argument("--preview", type=Path)
    parser.add_argument("--threshold-sheet", type=Path)
    args = parser.parse_args()

    if not 1 <= args.threshold <= 255:
        raise ValueError("threshold must be in 1..255")
    project = copy.deepcopy(json.loads(args.base_json.read_text(encoding="utf-8")))
    masks = import_art(project, args.side_png, args.wheel_png, args.top_png, args.threshold)
    args.output_json.parent.mkdir(parents=True, exist_ok=True)
    args.output_json.write_text(json.dumps(project, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    if args.preview:
        focused_preview(masks, args.preview)
    if args.threshold_sheet:
        contact_sheet(args.side_png, args.wheel_png, args.top_png, args.threshold_sheet)
    print(json.dumps({"threshold": args.threshold, "output": str(args.output_json)}, ensure_ascii=False))


if __name__ == "__main__":
    main()
