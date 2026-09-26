"""D064 one-shot UI/settings/Back checks on an explicit emulator, no physical OBD.

Uses the authored button bounds and observed View bounds, not fixed screen taps.
Works with benchmark: no debug fixture or run-as requirement.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import time


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--serial", required=True)
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--mapping", type=Path)
    args = parser.parse_args()
    if not re.fullmatch(r"emulator-\d+", args.serial):
        raise ValueError("Explicit emulator only")
    base = ["C:/AndroidCodex/Sdk/platform-tools/adb.exe", "-s", args.serial]
    package = "com.guanyu.rx400hprobe.debug"
    flags = getattr(subprocess, "CREATE_NO_WINDOW", 0)

    def raw(*cmd):
        return subprocess.run(base + list(cmd), check=True, capture_output=True, timeout=30,
                              creationflags=flags).stdout

    def adb(*cmd):
        return raw(*cmd).decode("utf-8", errors="replace").strip()

    assert adb("shell", "getprop", "ro.kernel.qemu") == "1"
    expected = hashlib.sha256(args.apk.read_bytes()).hexdigest()
    installed = adb("shell", "pm", "path", package).removeprefix("package:")
    assert adb("shell", "sha256sum", installed).split()[0] == expected
    assert adb("shell", "wm", "size") == "Physical size: 1280x720"
    args.output.mkdir(parents=True, exist_ok=False)
    shots = {}

    def shot(name):
        data = raw("exec-out", "screencap", "-p")
        assert data.startswith(b"\x89PNG\r\n\x1a\n")
        (args.output / name).write_bytes(data)
        shots[name] = hashlib.sha256(data).hexdigest()

    def active(name):
        state = adb("shell", "dumpsys", "activity", "activities")
        assert re.search(r"mResumedActivity:.*" + re.escape(name), state), state[-3000:]

    def main_view():
        result = adb("shell", "am", "start", "-W", "-n", package + "/com.guanyu.rx400hprobe.MainActivity")
        assert "Error" not in result, result
        if not shots:
            shot("first-drawn.png")
        time.sleep(1)
        active("MainActivity")

    adb("shell", "am", "force-stop", package)
    main_view()
    shot("main.png")
    hierarchy = adb("shell", "dumpsys", "activity", "top")
    (args.output / "main-hierarchy.txt").write_text(hierarchy, encoding="utf-8")
    view_class = "PixelDashboardView"
    if args.mapping:
        mapped = re.search(r"^com\.guanyu\.rx400hprobe\.PixelDashboardView -> ([^:]+):$",
                           args.mapping.read_text(encoding="utf-8"), re.MULTILINE)
        assert mapped, "Missing actual R8 View mapping"
        view_class = mapped.group(1)
    match = re.findall(re.escape(view_class) + r"\{[^\n}]*? (\d+),(\d+)-(\d+),(\d+)", hierarchy)
    assert match and tuple(map(int, match[-1])) == (0, 0, 1280, 672)
    scale = 672 / 720
    left = (1280 - 1280 * scale) / 2
    layout = json.loads((Path(__file__).resolve().parents[1] /
                        "app/src/main/assets/rx400h_ui/layout_v035_pixel_v2.json").read_text(encoding="utf-8"))

    def button(layer_id):
        layer = next(x for x in layout["layers"] if x["id"] == layer_id)
        x = left + (layer["x"] + layer["w"] / 2) * 2 * scale
        y = (layer["y"] + layer["h"] / 2) * 2 * scale
        adb("shell", "input", "tap", str(round(x)), str(round(y)))
        time.sleep(1)

    # Retired controls must not respond, even though the archived design JSON retains their bounds.
    for retired in ("button-device", "button-start", "button-end"):
        button(retired)
        active("MainActivity")
    button("settings-button")
    shot("settings.png")
    adb("shell", "input", "tap", str(round(left + 760 * scale)), str(round(162 * scale)))
    time.sleep(.5)
    active("DevicePickerActivity")
    shot("device-picker.png")
    (args.output / "device-hierarchy.txt").write_text(adb("shell", "dumpsys", "activity", "top"), encoding="utf-8")
    adb("shell", "input", "keyevent", "4")
    time.sleep(1)
    active("MainActivity")
    shot("return-from-device.png")
    adb("shell", "input", "keyevent", "3")
    time.sleep(1)
    main_view()
    shot("resume-with-settings.png")
    # Close only the in-app overlay. Does not select a strategy or start a session.
    adb("shell", "input", "tap", str(round(left + 965 * scale)), str(round(110 * scale)))
    time.sleep(.5)
    shot("resumed-main.png")
    button("side-car")  # No device: stays reachable, no retry loop/dialog.
    shot("manual-reconnect-no-device.png")
    adb("shell", "input", "keyevent", "4")
    time.sleep(.5)
    state = adb("shell", "dumpsys", "activity", "activities")
    assert not re.search(r"mResumedActivity:.*MainActivity", state)
    main_view()
    shot("reopened.png")
    identity = {"apk_sha256": expected, "api": adb("shell", "getprop", "ro.build.version.sdk"),
                "serial": args.serial, "passed": True, "screenshots": shots,
                "scope": "1280x720 settled GUI, retired buttons inert, settings/device entry, foreground resume, car tap without device, Back exit and reopen; no LIVE, Bluetooth connection, actual first frame or API28+ cutout test"}
    (args.output / "result.json").write_text(json.dumps(identity, indent=2), encoding="utf-8")
    print(json.dumps(identity))


if __name__ == "__main__":
    main()
