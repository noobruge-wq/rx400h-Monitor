"""Replace only the RX400h vehicle pixel masks in an exported V0.3.5 UI JSON.

The artwork is authored directly on the 640x360 logical-pixel grid.  No
antialiasing or fractional transforms are used, so a 2x nearest-neighbour
render remains faithful on the 1280x720 target display.
"""

from __future__ import annotations

import argparse
import base64
import copy
import json
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


PHOSPHOR = (0x76, 0xFF, 0x96)
NOTO_SANS_SC = Path(r"C:\Windows\Fonts\NotoSansSC-VF.ttf")


def new_mask(width: int, height: int) -> Image.Image:
    return Image.new("1", (width, height), 0)


def poly(draw: ImageDraw.ImageDraw, points, *, closed=False, width=1):
    points = [(int(x), int(y)) for x, y in points]
    draw.line(points + ([points[0]] if closed else []), fill=1, width=width)


def build_top_car() -> Image.Image:
    """Gen-1 RX400h top view, rear at top and front at bottom."""
    image = new_mask(124, 225)
    d = ImageDraw.Draw(image)

    # Main body silhouette.  The shoulders, mirrors and tapered nose follow
    # the supplied RX400h reference instead of a generic capsule outline.
    outer = [
        (43, 3), (81, 3), (92, 7), (101, 17), (107, 31), (111, 49),
        (112, 62), (116, 67), (121, 68), (123, 72), (123, 77),
        (119, 81), (113, 81), (112, 108), (111, 139), (110, 170),
        (108, 194), (103, 209), (94, 217), (79, 222), (62, 224),
        (45, 222), (30, 217), (21, 209), (16, 194), (14, 170),
        (13, 139), (12, 108), (11, 81), (5, 81), (1, 77), (1, 72),
        (3, 68), (8, 67), (12, 62), (13, 49), (17, 31), (23, 17),
        (32, 7),
    ]
    poly(d, outer, closed=True)

    # Rear spoiler, hatch glass and tail-lamp shoulders.
    poly(d, [(37, 8), (87, 8), (94, 13), (30, 13), (37, 8)])
    poly(d, [(31, 18), (93, 18), (100, 34), (98, 52), (26, 52), (24, 34)], closed=True)
    poly(d, [(17, 35), (25, 32), (25, 49), (16, 54)])
    poly(d, [(107, 35), (99, 32), (99, 49), (108, 54)])
    d.line((21, 58, 103, 58), fill=1)
    d.line((20, 62, 104, 62), fill=1)

    # Roof rails and cabin frame around the functional SOC battery area.
    poly(d, [(21, 64), (18, 91), (19, 176), (25, 198)])
    poly(d, [(103, 64), (106, 91), (105, 176), (99, 198)])
    poly(d, [(29, 69), (95, 69), (102, 90), (103, 178), (96, 198)])
    poly(d, [(95, 198), (29, 198), (21, 178), (22, 90), (29, 69)])
    d.line((27, 85, 97, 85), fill=1)
    d.line((27, 202, 97, 202), fill=1)

    # Door/side glazing cues retained from the source photograph.
    d.line((16, 88, 25, 92), fill=1)
    d.line((108, 88, 99, 92), fill=1)
    d.line((15, 116, 21, 116), fill=1)
    d.line((109, 116, 103, 116), fill=1)
    d.line((15, 151, 20, 151), fill=1)
    d.line((109, 151, 104, 151), fill=1)
    poly(d, [(14, 77), (7, 75), (4, 72), (10, 70), (14, 71)], closed=True)
    poly(d, [(110, 77), (117, 75), (120, 72), (114, 70), (110, 71)], closed=True)

    # Front windscreen, hood creases, lamps and bumper identify the nose.
    poly(d, [(28, 202), (96, 202), (101, 211), (91, 217), (33, 217), (23, 211)], closed=True)
    d.line((31, 205, 93, 205), fill=1)
    poly(d, [(20, 194), (31, 200), (26, 209), (17, 202)], closed=True)
    poly(d, [(104, 194), (93, 200), (98, 209), (107, 202)], closed=True)
    d.line((46, 218, 78, 218), fill=1)
    d.rectangle((59, 211, 65, 216), outline=1)
    d.line((62, 204, 62, 221), fill=1)

    # Subtle left/right body character lines; all are one logical pixel.
    poly(d, [(18, 69), (16, 98), (17, 166), (21, 190)])
    poly(d, [(106, 69), (108, 98), (107, 166), (103, 190)])
    return image


def build_side_car() -> Image.Image:
    """Recognisable first-generation RX400h side profile."""
    image = new_mask(182, 101)
    d = ImageDraw.Draw(image)

    # RX400h roof line, short nose, upright hatch and bumper silhouette.
    poly(
        d,
        [
            (1, 63), (5, 55), (14, 50), (29, 47), (47, 42),
            (58, 28), (70, 17), (84, 12), (115, 12), (129, 17),
            (142, 27), (152, 40), (160, 47), (174, 52), (179, 57),
            (181, 67), (178, 75), (166, 80), (163, 80),
        ],
    )
    poly(d, [(1, 63), (1, 73), (7, 79), (19, 82), (21, 82)])
    d.arc((21, 59, 57, 95), 180, 360, fill=1)
    d.line((57, 82, 127, 82), fill=1)
    d.arc((127, 59, 163, 95), 180, 360, fill=1)
    poly(d, [(163, 82), (174, 81), (180, 75)])
    d.line((57, 87, 127, 87), fill=1)
    d.line((66, 90, 119, 90), fill=1)

    # Roof rail and a four-part greenhouse with the RX rear quarter window.
    poly(d, [(76, 9), (119, 9), (132, 15)])
    poly(d, [(58, 40), (72, 19), (84, 15), (95, 15), (95, 41)], closed=True)
    poly(d, [(99, 15), (115, 15), (128, 20), (139, 30), (145, 41), (99, 41)], closed=True)
    d.line((62, 43, 150, 43), fill=1)
    d.line((95, 15, 95, 42), fill=1)
    d.line((128, 20, 128, 42), fill=1)
    poly(d, [(139, 29), (151, 41), (145, 41)])

    # A-pillar mirror, doors, handles, fuel flap and lower character line.
    poly(d, [(57, 39), (51, 42), (53, 46), (61, 45)], closed=True)
    d.line((60, 45, 57, 73), fill=1)
    d.line((98, 44, 98, 76), fill=1)
    d.line((137, 44, 139, 70), fill=1)
    d.line((59, 75, 126, 75), fill=1)
    d.line((69, 51, 88, 51), fill=1)
    d.line((107, 51, 126, 51), fill=1)
    d.rectangle((80, 47, 88, 49), outline=1)
    d.rectangle((119, 47, 127, 49), outline=1)
    d.rectangle((143, 50, 153, 58), outline=1)
    poly(d, [(12, 67), (30, 64), (50, 61), (57, 55)])
    poly(d, [(60, 79), (80, 78), (124, 78)])

    # Front lamp/grille and the distinctive high rear lamp/hatch treatment.
    poly(d, [(5, 56), (20, 53), (28, 55), (20, 62), (6, 64)], closed=True)
    d.rectangle((2, 66, 12, 72), outline=1)
    d.line((4, 69, 11, 69), fill=1)
    poly(d, [(161, 50), (175, 54), (177, 63), (164, 61)], closed=True)
    d.line((157, 44, 158, 72), fill=1)
    d.line((166, 77, 176, 75), fill=1)
    return image


def build_wheel_base() -> Image.Image:
    image = new_mask(182, 101)
    d = ImageDraw.Draw(image)
    for cx, cy in ((39, 77), (145, 77)):
        d.ellipse((cx - 18, cy - 18, cx + 18, cy + 18), outline=1)
        d.ellipse((cx - 15, cy - 15, cx + 15, cy + 15), outline=1)
        d.ellipse((cx - 11, cy - 11, cx + 11, cy + 11), outline=1)
        d.ellipse((cx - 3, cy - 3, cx + 3, cy + 3), outline=1)
    return image


def build_wheel_spokes() -> Image.Image:
    image = new_mask(182, 101)
    d = ImageDraw.Draw(image)
    # Five forked spokes echo the supplied production wheel while keeping the
    # rotating mask sparse enough for a weak head unit.
    points = ((0, -10), (10, -3), (6, 8), (-6, 8), (-10, -3))
    for cx, cy in ((39, 77), (145, 77)):
        for px, py in points:
            d.line((cx, cy, cx + px, cy + py), fill=1)
        d.rectangle((cx - 1, cy - 1, cx + 1, cy + 1), fill=1)
    return image


def encode_mask(image: Image.Image) -> str:
    values = bytes(1 if value else 0 for value in image.convert("L").tobytes())
    return base64.b64encode(values).decode("ascii")


def decode_mask(layer) -> Image.Image:
    raw = base64.b64decode(layer.get("data", ""))
    expected = int(layer["w"]) * int(layer["h"])
    raw = (raw + bytes(expected))[:expected]
    return Image.frombytes("L", (int(layer["w"]), int(layer["h"])), bytes(255 if v else 0 for v in raw))


def patch_project(project):
    builders = {
        "top-car": build_top_car,
        "side-car": build_side_car,
        "wheel-base": build_wheel_base,
        "wheel-spokes": build_wheel_spokes,
    }
    changed = []
    for layer in project["layers"]:
        builder = builders.get(layer.get("id"))
        if not builder:
            continue
        artwork = builder()
        if artwork.size != (layer["w"], layer["h"]):
            raise ValueError(f"{layer['id']} has unexpected size {layer['w']}x{layer['h']}")
        layer["data"] = encode_mask(artwork)
        changed.append(layer["id"])
    if set(changed) != set(builders):
        raise ValueError(f"Missing expected vehicle layers: {set(builders) - set(changed)}")
    return changed


def tint_mask(mask: Image.Image, alpha: float, color=PHOSPHOR) -> Image.Image:
    result = Image.new("RGBA", mask.size, (*color, 0))
    result.putalpha(mask.point(lambda value: round(value * max(0.0, min(1.0, alpha)))))
    return result


def render_physical_text(layer: dict) -> Image.Image:
    config = layer["physicalText"]
    width, height = int(layer["w"]) * 2, int(layer["h"]) * 2
    mask = Image.new("L", (width, height), 0)
    draw = ImageDraw.Draw(mask)
    selected_font = ImageFont.truetype(str(NOTO_SANS_SC), int(config["sizePx"]))
    selected_font.set_variation_by_name("Bold" if int(config.get("weight", 500)) >= 700 else "Medium")
    lines = str(config["text"]).split("\n")
    line_height = max(1, int(config.get("lineHeightPx", round(int(config["sizePx"]) * 1.12))))
    block = len(lines) * line_height
    first_y = (height - block) / 2 + line_height / 2
    align = config.get("align", "center")
    if align == "left":
        x, anchor = 0, "lm"
    elif align == "right":
        x, anchor = width, "rm"
    else:
        x, anchor = width / 2, "mm"
    for index, line in enumerate(lines):
        draw.text((x, first_y + index * line_height), line, font=selected_font, fill=255, anchor=anchor)
    threshold = int(config.get("threshold", 96))
    mask = mask.point(lambda value: 255 if value >= threshold else 0)
    return tint_mask(mask, float(layer.get("alpha", 1.0)))


def render_vehicle_preview(project, output: Path):
    # A focused proof sheet at 4x logical scale makes individual authored
    # pixels visible without CRT blur hiding the geometry.
    top = next(layer for layer in project["layers"] if layer["id"] == "top-car")
    side = next(layer for layer in project["layers"] if layer["id"] == "side-car")
    wheel_base = next(layer for layer in project["layers"] if layer["id"] == "wheel-base")
    wheel_spokes = next(layer for layer in project["layers"] if layer["id"] == "wheel-spokes")

    sheet = Image.new("RGB", (1200, 720), (0, 0, 0))
    top_rgba = tint_mask(decode_mask(top), 1.0)
    side_rgba = tint_mask(decode_mask(side), 1.0)
    side_rgba.alpha_composite(tint_mask(decode_mask(wheel_base), 1.0))
    side_rgba.alpha_composite(tint_mask(decode_mask(wheel_spokes), 1.0))
    top_scaled = top_rgba.resize((top["w"] * 3, top["h"] * 3), Image.Resampling.NEAREST)
    side_scaled = side_rgba.resize((side["w"] * 4, side["h"] * 4), Image.Resampling.NEAREST)
    sheet.paste(top_scaled, (28, 22), top_scaled)
    sheet.paste(side_scaled, (438, 158), side_scaled)
    sheet.save(output)


def render_full_preview(project, output: Path):
    width = int(project["canvas"]["width"])
    height = int(project["canvas"]["height"])
    canvas = Image.new("RGBA", (width, height), (0, 0, 0, 255))
    layers = sorted(project["layers"], key=lambda item: item.get("z", 0))
    for layer in layers:
        if not layer.get("visible", True) or layer.get("kind") != "bitmap":
            continue
        role = layer.get("role")
        # Dynamic numeric slots are rendered by the app; here the static UI
        # and the redesigned vehicle geometry are the objects under review.
        alpha = float(layer.get("alpha", 1.0))
        mask = decode_mask(layer)
        if role == "soc-fill":
            # Representative 61.5% value using the agreed nonlinear mapping.
            fraction = 0.15 + (61.5 - 30.0) / 50.0 * 0.75
            cutoff = mask.height - round(mask.height * fraction)
            clipped = Image.new("L", mask.size, 0)
            clipped.paste(mask.crop((0, cutoff, mask.width, mask.height)), (0, cutoff))
            mask = clipped
        elif role == "power-fill":
            # Representative -8.4 kW: show only the left-hand active segment.
            clipped = Image.new("L", mask.size, 0)
            zero = 100
            left = round(zero - 8.4 / 50.0 * 92)
            clipped.paste(mask.crop((left, 0, zero + 1, mask.height)), (left, 0))
            mask = clipped
        elif role == "power-cursor":
            # The cursor has runtime positioning; omit its editor-origin copy.
            continue
        tinted = tint_mask(mask, alpha)
        canvas.alpha_composite(tinted, (int(layer["x"]), int(layer["y"])))

    output_width = int(project["canvas"].get("outputWidth", width * 2))
    output_height = int(project["canvas"].get("outputHeight", height * 2))
    sharp = canvas.convert("RGB").resize((output_width, output_height), Image.Resampling.NEAREST)
    sharp = sharp.convert("RGBA")
    for layer in layers:
        if not layer.get("visible", True) or not layer.get("physicalText"):
            continue
        text = render_physical_text(layer)
        sharp.alpha_composite(text, (int(layer["x"]) * 2, int(layer["y"]) * 2))
    sharp = sharp.convert("RGB")
    sharp.save(output)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("input", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--preview", type=Path)
    parser.add_argument("--full-preview", type=Path)
    args = parser.parse_args()

    original = json.loads(args.input.read_text(encoding="utf-8"))
    project = copy.deepcopy(original)
    changed = patch_project(project)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(project, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    if args.preview:
        render_vehicle_preview(project, args.preview)
    if args.full_preview:
        render_full_preview(project, args.full_preview)
    print(json.dumps({"changed": changed, "output": str(args.output)}, ensure_ascii=False))


if __name__ == "__main__":
    main()
