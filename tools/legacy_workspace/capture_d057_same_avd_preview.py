"""GUI-only historical APK control on explicit running emulator-5560.

Installs exact preserved D057 debug APK into the disposable emulator, captures
its existing deterministic Preview (no OBD), and force-stops that package.
Does not change host/AVD settings or claim a dynamic performance comparison.
"""
import hashlib
import json
from pathlib import Path
import subprocess
import time

base = ["C:/AndroidCodex/Sdk/platform-tools/adb.exe", "-s", "emulator-5560"]
flags = getattr(subprocess, "CREATE_NO_WINDOW", 0)
package = "com.guanyu.rx400hprobe.debug"
apk = Path("deliverables/RX400h-Monitor-v0.3.5-v28-D057-renderer-debug.apk")
expected = "b8cfe5d5497e13eb30a8eb4d02695ae2eed9de50a91bac265cd74af2cc28bbab"
out = Path("outputs/d058-runtime-20260906/d057-same-avd-preview")


def adb(*args, check=True):
    r = subprocess.run(base + list(args), capture_output=True, timeout=30, creationflags=flags)
    if check and r.returncode:
        raise RuntimeError(r.stderr.decode(errors="replace"))
    return r.stdout.decode("utf-8", errors="replace").strip()


if hashlib.sha256(apk.read_bytes()).hexdigest() != expected:
    raise ValueError("Unexpected historical APK")
if adb("shell", "getprop", "ro.kernel.qemu") != "1":
    raise ValueError("Not emulator")
out.mkdir(parents=True, exist_ok=False)
metadata = {"purpose": "same-AVD old APK static visual control, not dynamic A/B", "sha256": expected,
            "screen": adb("shell", "wm", "size"), "density": adb("shell", "wm", "density"),
            "preferences": adb("shell", "run-as", package, "cat", "shared_prefs/crt_display_v035.xml", check=False)}
adb("shell", "am", "force-stop", package)
metadata["install"] = adb("install", "-r", str(apk))
if "Success" not in metadata["install"]:
    raise RuntimeError(metadata["install"])
path = adb("shell", "pm", "path", package).removeprefix("package:")
metadata["installed_sha256"] = adb("shell", "sha256sum", path)
if metadata["installed_sha256"].split()[0] != expected:
    raise ValueError("Installed identity mismatch")
metadata["launch"] = adb("shell", "am", "start", "-W", "-n", package + "/com.guanyu.rx400hprobe.DashboardPreviewActivity", "--ez", "idle_active", "false")
if "Error" in metadata["launch"]:
    raise RuntimeError(metadata["launch"])
time.sleep(2)
shot = subprocess.run(base + ["exec-out", "screencap", "-p"], capture_output=True, check=True, timeout=30, creationflags=flags).stdout
if not shot.startswith(b"\x89PNG\r\n\x1a\n"):
    raise ValueError("Invalid screenshot")
(out / "preview.png").write_bytes(shot)
metadata["screenshot_sha256"] = hashlib.sha256(shot).hexdigest()
adb("shell", "am", "force-stop", package)
(out / "metadata.json").write_text(json.dumps(metadata, indent=2), encoding="utf-8")
print("Exact D057 static control captured; review pixels.")
