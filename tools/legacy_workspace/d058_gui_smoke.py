"""Explicit, emulator-only visual smoke for the already-installed D058 fixture.

Run AFTER controlled captures, not concurrently. Touch positions are grounded
in the inspected 1280x720 / density160 framebuffer and production settings
geometry (672px app height plus navigation bar). Never contacts OBD or uses
MainActivity. No sliders are changed. Closing settings persists the unchanged
defaults in this disposable emulator session. Generates new evidence only.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import time
import uuid

PACKAGE = "com.guanyu.rx400hprobe.debug"
EXPECTED_APK = "b388efed7e3a09153d8b2c2428d130ea07b963e0e14d01ced65b0193220e68b7"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--serial", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if not re.fullmatch(r"emulator-\d+", args.serial):
        raise ValueError("Emulator serial required")
    flags = getattr(subprocess, "CREATE_NO_WINDOW", 0)
    base = ["C:/AndroidCodex/Sdk/platform-tools/adb.exe", "-s", args.serial]

    def adb(*command, check=True):
        result = subprocess.run(base + list(command), capture_output=True, timeout=30, creationflags=flags)
        if check and result.returncode:
            raise RuntimeError(result.stderr.decode(errors="replace"))
        return result.stdout.decode("utf-8", errors="replace").strip()

    if adb("shell", "getprop", "ro.kernel.qemu") != "1":
        raise ValueError("Not an emulator")
    size = adb("shell", "wm", "size")
    density = adb("shell", "wm", "density")
    if size != "Physical size: 1280x720" or density != "Physical density: 160":
        raise ValueError("Unexpected viewport; do not use stale touch coordinates")
    package_path = adb("shell", "pm", "path", PACKAGE).removeprefix("package:")
    if adb("shell", "sha256sum", package_path).split()[0] != EXPECTED_APK:
        raise ValueError("Unexpected APK")
    args.output.mkdir(parents=True, exist_ok=False)
    evidence = {"purpose": "GUI only; not a controlled performance run", "serial": args.serial,
                "apk_sha256": EXPECTED_APK, "screen": size, "density": density, "events": [], "screenshots": {}}

    def event(name, detail=""):
        evidence["events"].append({"name": name, "host_monotonic_s": time.monotonic(), "detail": detail})

    def shot(name):
        png = subprocess.run(base + ["exec-out", "screencap", "-p"], capture_output=True, timeout=30,
                             check=True, creationflags=flags).stdout
        if not png.startswith(b"\x89PNG\r\n\x1a\n"):
            raise ValueError("Not PNG")
        (args.output / name).write_bytes(png)
        evidence["screenshots"][name] = hashlib.sha256(png).hexdigest()
        event(name)

    def launch(activity, *extras):
        adb("shell", "am", "force-stop", PACKAGE)
        event("force_stop_fixture")
        result = adb("shell", "am", "start", "-W", "-n", PACKAGE + "/com.guanyu.rx400hprobe." + activity, *extras)
        if "Error" in result:
            raise RuntimeError(result)
        event(activity, result)

    evidence["preferences_before"] = adb("shell", "run-as", PACKAGE, "cat", "shared_prefs/crt_display_v035.xml", check=False)
    with (args.output / "logcat.txt").open("wb") as output:
        log = subprocess.Popen(base + ["logcat", "-v", "threadtime", "-T", "1", "RX400hReplay:I", "RX400hRenderer:D", "AndroidRuntime:E", "*:S"],
                               stdout=output, stderr=subprocess.STDOUT, creationflags=flags)
        try:
            for active in (False, True):
                launch("DashboardPreviewActivity", "--ez", "idle_active", str(active).lower())
                time.sleep(2)
                shot("idle-active.png" if active else "preview-inactive.png")
            run_id = "gui-wheel-" + uuid.uuid4().hex
            evidence["settings_run_id"] = run_id
            launch("DashboardReplayActivity", "--es", "replay_mode", "wheel", "--es", "replay_run_id", run_id, "--ez", "autostart", "true")
            time.sleep(11)
            shot("wheel-before-settings.png")
            adb("shell", "input", "tap", "850", "50")
            event("open_settings", "850,50; no slider touched")
            time.sleep(2)
            shot("settings-open.png")
            time.sleep(12)
            shot("settings-open-later.png")
            adb("shell", "input", "tap", "943", "101")
            event("close_settings", "943,101")
            time.sleep(2)
            shot("settings-closed.png")
            time.sleep(8)
            adb("shell", "input", "keyevent", "3")
            event("background_home")
            time.sleep(6)
            evidence["preferences_after"] = adb("shell", "run-as", PACKAGE, "cat", "shared_prefs/crt_display_v035.xml", check=False)
            # Separate self-test visual run, explicitly not included in CPU tables.
            visual_id = "gui-selftest-" + uuid.uuid4().hex
            remote_video = "/sdcard/" + visual_id + ".mp4"
            video = subprocess.Popen(base + ["shell", "screenrecord", "--time-limit", "8", "--bit-rate", "4000000", remote_video],
                                     stdout=subprocess.PIPE, stderr=subprocess.PIPE, creationflags=flags)
            try:
                time.sleep(0.5)
                launch("DashboardReplayActivity", "--es", "replay_mode", "stress5", "--es", "replay_run_id", visual_id, "--ez", "autostart", "true")
                # Host-timed samples, not claimed as exact animation timestamps.
                # Video retains the continuous trajectory; PNGs allow inspection
                # without requiring a video decoder in the validation host.
                time.sleep(1.2)
                shot("selftest-expand.png")
                time.sleep(0.2)
                shot("selftest-near-peak.png")
                time.sleep(0.2)
                shot("selftest-return.png")
                stdout, stderr = video.communicate(timeout=20)
                if video.returncode:
                    raise RuntimeError(stderr.decode(errors="replace"))
                adb("pull", remote_video, str(args.output / "selftest.mp4"))
                event("selftest_video", "8s video; emulator-side temporary copy expires with read-only session")
            finally:
                if video.poll() is None:
                    video.terminate()
                    video.wait(timeout=10)
            shot("selftest-settled.png")
            adb("shell", "input", "keyevent", "3")
            time.sleep(1)
        finally:
            log.terminate()
            log.wait(timeout=10)
            adb("shell", "am", "force-stop", PACKAGE, check=False)
            (args.output / "gui.json").write_text(json.dumps(evidence, indent=2, ensure_ascii=False), encoding="utf-8")
    text = (args.output / "logcat.txt").read_text(encoding="utf-8")
    if f"reason=background_abort mode=wheel runId={run_id}" not in text:
        raise RuntimeError("Expected background abort absent: investigate, not GUI pass")
    print("GUI evidence captured; review screenshots, window metrics and video before claiming a pass.")


if __name__ == "__main__":
    main()
