"""Read-only pixel comparison, no edited/difference images are generated."""
import argparse
import json
from pathlib import Path
import numpy as np
from PIL import Image


def compare(first, second, exclusions=()):
    a = np.asarray(Image.open(first).convert("RGB"), dtype=np.int16)
    b = np.asarray(Image.open(second).convert("RGB"), dtype=np.int16)
    if a.shape != b.shape or a.shape != (720, 1280, 3):
        raise ValueError("Unexpected/mismatched framebuffer")
    delta = np.abs(a - b)
    mask = np.ones(a.shape[:2], dtype=bool)
    for left, top, right, bottom in exclusions:
        mask[top:bottom, left:right] = False
    errors = delta.max(axis=2)
    changed = mask & (errors != 0)
    ys, xs = np.where(changed)
    return {
        "first": str(first), "second": str(second), "excluded_rectangles": exclusions,
        "compared_pixels": int(mask.sum()), "changed_pixels": int(changed.sum()),
        "pixels_channel_error_over_2": int((mask & (errors > 2)).sum()),
        "max_channel_error": int(errors[mask].max()),
        "mean_absolute_channel_error": float(delta[mask].mean()),
        "changed_bounds": [int(xs.min()), int(ys.min()), int(xs.max()) + 1, int(ys.max()) + 1] if xs.size else None,
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("gui", type=Path)
    parser.add_argument("--old-preview", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--same-environment", action="store_true")
    parser.add_argument("--historical-preview", type=Path)
    args = parser.parse_args()
    # Conservative wheel+halo bounds grounded in inspected 1280x720 frames.
    wheels = [(485, 320, 582, 411), (681, 320, 782, 411)]
    idle = (949, 548, 1117, 635)
    results = {
        "settings_hold": compare(args.gui / "settings-open.png", args.gui / "settings-open-later.png"),
        "settings_restored_non_wheel": compare(args.gui / "wheel-before-settings.png", args.gui / "settings-closed.png", wheels),
        "idle_change_outside_idle_and_wheels": compare(args.gui / "preview-inactive.png", args.gui / "idle-active.png", wheels + [idle]),
        "old_d057_preview_non_wheel": compare(args.old_preview, args.gui / "preview-inactive.png", wheels),
        "same_environment_control": args.same_environment,
        "limits": ("Old APK was recaptured on the same emulator/graphics/preferences." if args.same_environment else
                   "Old preview uses a different emulator version; differences cannot be assigned solely to renderer changes.") +
                  " Excluded rectangles and raw images are preserved for review; no claim about unseen animation frames."
    }
    if args.historical_preview:
        results["historical_old_vs_current_old_non_wheel"] = compare(args.historical_preview, args.old_preview, wheels)
    args.output.write_text(json.dumps(results, indent=2), encoding="utf-8")
    print(json.dumps(results, indent=2))


if __name__ == "__main__":
    main()
