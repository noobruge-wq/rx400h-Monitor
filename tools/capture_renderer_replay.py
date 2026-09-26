"""Capture a preinstalled debug fixture on an ALREADY RUNNING emulator only.

Does not install APKs, start an emulator, change graphics/CPU/effect settings,
clear logs/data or access physical devices. Logs and PNG are new local outputs.
"""
import argparse
import json
from pathlib import Path
import re
import subprocess
import time
import uuid

PACKAGE = "com.guanyu.rx400hprobe.debug"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--adb", type=Path, default=Path("C:/AndroidCodex/Sdk/platform-tools/adb.exe"))
    parser.add_argument("--serial", required=True)
    parser.add_argument("--mode", choices=("recorded", "stress5", "wheel", "parked"), required=True)
    parser.add_argument("--output", type=Path, required=True, help="New directory; existing output is never overwritten")
    parser.add_argument("--reference", action="store_true", help="Debug-only D057 software-composition algorithm; same current content")
    args = parser.parse_args()
    if not re.fullmatch(r"emulator-\d+", args.serial):
        parser.error("Only explicit emulator-* serials are permitted")
    flags = getattr(subprocess, "CREATE_NO_WINDOW", 0)
    base = [str(args.adb), "-s", args.serial]

    def adb(*command, check=True):
        result = subprocess.run(base + list(command), capture_output=True, timeout=30, creationflags=flags)
        if check and result.returncode:
            raise RuntimeError(result.stderr.decode(errors="replace"))
        return result.stdout.decode("utf-8", errors="replace").strip()

    if adb("shell", "getprop", "ro.kernel.qemu") != "1":
        raise RuntimeError("Not a running Android emulator; no actions taken")
    package_path = adb("shell", "pm", "path", PACKAGE)
    if not package_path.startswith("package:"):
        raise RuntimeError("Install the validation APK separately before capture")
    # A GUI fixture left on the Activity back stack can retain another whole
    # dashboard and its heartbeat. Each controlled run needs a fresh process.
    adb("shell", "am", "force-stop", PACKAGE)
    args.output.mkdir(parents=True, exist_ok=False)
    identity = {
        "mode": args.mode, "serial": args.serial, "reference_renderer": args.reference,
        "fresh_process": True,
        "run_id": uuid.uuid4().hex,
        "apk_sha256": adb("shell", "sha256sum", package_path.splitlines()[0].removeprefix("package:")),
        "screen": adb("shell", "wm", "size"), "density": adb("shell", "wm", "density"),
        "api": adb("shell", "getprop", "ro.build.version.sdk"),
        "hardware": adb("shell", "getprop", "ro.hardware"),
        "online_cpus": adb("shell", "cat", "/sys/devices/system/cpu/online"),
        "guest_memory": adb("shell", "cat", "/proc/meminfo"),
        "effect_preferences": adb("shell", "run-as", PACKAGE, "cat", "shared_prefs/crt_display_v035.xml", check=False),
        "duration_seconds": 952 if args.mode == "recorded" else 126,
        "notes": "Use identical AVD/graphics/effects for comparisons. Renderer UI only; not OBD. No Android/CRT preferences are changed."
    }
    (args.output / "identity.json").write_text(json.dumps(identity, indent=2), encoding="utf-8")
    # No global logcat clear. The start event and APK identity delimit this run.
    with (args.output / "logcat.txt").open("wb") as log:
        process = subprocess.Popen(base + ["logcat", "-v", "threadtime", "-T", "1",
                                          "RX400hReplay:I", "RX400hRenderer:D", "AndroidRuntime:E", "*:S"],
                                   stdout=log, stderr=subprocess.STDOUT, creationflags=flags)
        try:
            launch = adb("shell", "am", "start", "-W", "-n", PACKAGE + "/com.guanyu.rx400hprobe.DashboardReplayActivity",
                         "--es", "replay_mode", args.mode, "--es", "replay_run_id", identity["run_id"],
                         "--ez", "autostart", "true", "--ez", "reference_renderer", str(args.reference).lower())
            (args.output / "launch.txt").write_text(launch, encoding="utf-8")
            if "Error" in launch:
                raise RuntimeError(launch)
            started = time.monotonic()
            screenshot_done = False
            while time.monotonic() - started < identity["duration_seconds"]:
                if process.poll() is not None:
                    raise RuntimeError("Logcat terminated before capture completed")
                if not screenshot_done and time.monotonic() - started > 5:
                    shot = subprocess.run(base + ["exec-out", "screencap", "-p"], capture_output=True,
                                          timeout=30, check=True, creationflags=flags).stdout
                    if not shot.startswith(b"\x89PNG\r\n\x1a\n"):
                        raise RuntimeError("Invalid screenshot; refusing to label it PNG")
                    (args.output / "dynamic.png").write_bytes(shot)
                    screenshot_done = True
                time.sleep(1)
            for service in ("gfxinfo", "meminfo"):
                (args.output / f"{service}.txt").write_text(adb("shell", "dumpsys", service, PACKAGE), encoding="utf-8")
        finally:
            process.terminate()
            process.wait(timeout=10)
    log_text = (args.output / "logcat.txt").read_text(encoding="utf-8", errors="replace")
    if f"reason=complete mode={args.mode} runId={identity['run_id']}" not in log_text:
        raise RuntimeError("No completion event: retain capture as INCOMPLETE, never a performance pass")
    print(f"Capture complete: {args.output.resolve()} (requires review, not an automatic pass)")


if __name__ == "__main__":
    main()
