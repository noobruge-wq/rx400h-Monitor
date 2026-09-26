"""Crop the settings-button layer to the exact bounds drawn by the user."""

from __future__ import annotations

import argparse
import base64
import copy
import json
from pathlib import Path


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("input", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()

    source = json.loads(args.input.read_text(encoding="utf-8"))
    project = copy.deepcopy(source)
    layer = next(item for item in project["layers"] if item.get("id") == "settings-button")
    width, height = int(layer["w"]), int(layer["h"])
    pixels = base64.b64decode(layer["data"])
    if len(pixels) != width * height:
        raise ValueError("settings-button mask length does not match its geometry")

    lit = [(index % width, index // width) for index, value in enumerate(pixels) if value]
    if not lit:
        raise ValueError("settings-button artwork is empty")
    min_x = min(x for x, _ in lit)
    max_x = max(x for x, _ in lit)
    min_y = min(y for _, y in lit)
    max_y = max(y for _, y in lit)
    cropped_width = max_x - min_x + 1
    cropped_height = max_y - min_y + 1

    cropped = bytearray(cropped_width * cropped_height)
    for y in range(cropped_height):
        source_row = (min_y + y) * width + min_x
        target_row = y * cropped_width
        cropped[target_row : target_row + cropped_width] = pixels[source_row : source_row + cropped_width]

    # Preserve the exact absolute location of every drawn pixel even if a
    # future edit leaves blank padding on the layer's top or left edge.
    layer["x"] = int(layer["x"]) + min_x
    layer["y"] = int(layer["y"]) + min_y
    layer["w"] = cropped_width
    layer["h"] = cropped_height
    layer["data"] = base64.b64encode(cropped).decode("ascii")

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(project, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(
        json.dumps(
            {
                "before": {"x": source["layers"][[x.get("id") for x in source["layers"]].index("settings-button")]["x"], "y": source["layers"][[x.get("id") for x in source["layers"]].index("settings-button")]["y"], "w": width, "h": height},
                "after": {"x": layer["x"], "y": layer["y"], "w": layer["w"], "h": layer["h"]},
            },
            ensure_ascii=False,
        )
    )


if __name__ == "__main__":
    main()
