"""Apply the approved Noto Sans SC Chinese typography contract to a V2 JSON."""

from __future__ import annotations

import argparse
import base64
import copy
import json
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


FONT_PATH = Path(r"C:\Windows\Fonts\NotoSansSC-VF.ttf")


LOGICAL_TEXT = {
    "battery-title": ("电池域", 20, 700, "center"),
    "vehicle-title": ("车辆状态域", 20, 700, "center"),
    "power-title": ("动力域", 20, 700, "center"),
    "soc-label": ("电量", 20, 500, "center"),
    "temp-label": ("温度", 20, 500, "center"),
    "speed-label": ("速度", 20, 500, "center"),
    "coolant-label": ("冷却液", 20, 500, "center"),
    "obd12v-label": ("辅助电池", 20, 500, "center"),
    "icepower-label": ("引擎功率", 20, 500, "left"),
    "rpm-label": ("转速", 20, 500, "center"),
    "hvpower-label": ("动力电池", 20, 500, "center"),
}


BUTTON_TEXT = {
    "button-device": ("设备", 500),
    "button-start": ("开始", 700),
    "button-end": ("结束", 700),
    "settings-button": ("设置", 700),
}


def font(size: int, weight: int) -> ImageFont.FreeTypeFont:
    result = ImageFont.truetype(str(FONT_PATH), size=size)
    result.set_variation_by_name("Bold" if weight >= 700 else "Medium")
    return result


def binary_text(width: int, height: int, text: str, size: int, weight: int, align: str) -> Image.Image:
    image = Image.new("L", (width, height), 0)
    draw = ImageDraw.Draw(image)
    selected_font = font(size, weight)
    lines = str(text).split("\n")
    line_height = max(1, round(size * 1.12))
    block_height = len(lines) * line_height
    first_y = (height - block_height) / 2 + line_height / 2
    if align == "left":
        x, anchor = 0, "lm"
    elif align == "right":
        x, anchor = width, "rm"
    else:
        x, anchor = width / 2, "mm"
    for index, line in enumerate(lines):
        draw.text((x, first_y + index * line_height), line, font=selected_font, fill=255, anchor=anchor)
    return image.point(lambda value: 255 if value >= 96 else 0, mode="1")


def encode(image: Image.Image) -> str:
    return base64.b64encode(bytes(1 if value else 0 for value in image.convert("L").tobytes())).decode("ascii")


def decode(layer: dict) -> Image.Image:
    raw = base64.b64decode(layer["data"])
    expected = int(layer["w"]) * int(layer["h"])
    if len(raw) != expected:
        raise ValueError(f"{layer['id']}: expected {expected} bytes, got {len(raw)}")
    return Image.frombytes("L", (int(layer["w"]), int(layer["h"])), bytes(255 if value else 0 for value in raw))


def empty_data(width: int, height: int) -> str:
    return base64.b64encode(bytes(width * height)).decode("ascii")


def physical_text(text: str, size_px: int, weight: int, align: str, line_height_px: int) -> dict:
    return {
        "text": text,
        "fontFamily": "Noto Sans SC",
        "sizePx": size_px,
        "weight": weight,
        "align": align,
        "valign": "center",
        "lineHeightPx": line_height_px,
        "threshold": 96,
    }


def rebuild_button(layer: dict, text: str, weight: int):
    width, height = 83, 31
    image = Image.new("1", (width, height), 0)
    draw = ImageDraw.Draw(image)
    draw.rectangle((0, 0, width - 1, height - 1), outline=1)
    layer["w"] = width
    layer["h"] = height
    layer["data"] = encode(image)
    layer["physicalText"] = physical_text(text, 40, weight, "center", 44)


def add_power_physical_labels(project: dict, rail: dict):
    rail_image = decode(rail)
    draw = ImageDraw.Draw(rail_image)
    draw.rectangle((0, 58, 41, min(89, 78)), fill=0)
    draw.rectangle((158, 58, 199, min(89, 78)), fill=0)
    rail["data"] = encode(rail_image)

    project["layers"] = [
        layer for layer in project["layers"]
        if layer.get("id") not in {"power-charge-label", "power-discharge-label"}
    ]
    definitions = (
        ("power-charge-label", "充电标签", "充电", int(rail["x"]), int(rail["y"]) + 58),
        ("power-discharge-label", "放电标签", "放电", int(rail["x"]) + 158, int(rail["y"]) + 58),
    )
    for layer_id, name, text, x, y in definitions:
        project["layers"].append(
            {
                "id": layer_id,
                "name": name,
                "kind": "bitmap",
                "x": x,
                "y": y,
                "w": 42,
                "h": 18,
                "z": float(rail["z"]) + 0.1,
                "alpha": float(rail["alpha"]),
                "visible": True,
                "locked": False,
                "role": "physical-text",
                "data": empty_data(42, 18),
                "physicalText": physical_text(text, 20, 500, "center", 22),
            }
        )


def apply(project: dict):
    layers = {layer.get("id"): layer for layer in project["layers"]}
    missing = (set(LOGICAL_TEXT) | set(BUTTON_TEXT) | {"status-block", "minmax-label", "power-rail"}) - set(layers)
    if missing:
        raise ValueError(f"Missing required layers: {sorted(missing)}")

    for layer_id, (text, size, weight, align) in LOGICAL_TEXT.items():
        layer = layers[layer_id]
        layer["data"] = empty_data(int(layer["w"]), int(layer["h"]))
        layer["role"] = "physical-text"
        layer["physicalText"] = physical_text(text, size * 2, weight, align, round(size * 2 * 1.12))

    for layer_id, (text, weight) in BUTTON_TEXT.items():
        rebuild_button(layers[layer_id], text, weight)

    status = layers["status-block"]
    status["data"] = empty_data(int(status["w"]), int(status["h"]))
    status["role"] = "physical-text"
    status["physicalText"] = physical_text(
        "OBDLink MX+ 99905\n蓝牙已连接 / BT LINK\n协议就绪 / CAN 500K\n数据记录中 / LIVE",
        18,
        500,
        "right",
        20,
    )

    minmax = layers["minmax-label"]
    minmax["data"] = empty_data(int(minmax["w"]), int(minmax["h"]))
    minmax["role"] = "physical-text"
    minmax["physicalText"] = physical_text("最高 / 最低", 20, 500, "center", 22)

    add_power_physical_labels(project, layers["power-rail"])
    project["layers"].sort(key=lambda layer: float(layer.get("z", 0)))
    return project


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("input", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()

    if not FONT_PATH.exists():
        raise FileNotFoundError(FONT_PATH)
    project = apply(copy.deepcopy(json.loads(args.input.read_text(encoding="utf-8"))))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(project, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"output": str(args.output), "layers": len(project["layers"])}, ensure_ascii=False))


if __name__ == "__main__":
    main()
