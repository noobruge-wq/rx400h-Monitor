"""Exact-APK emulator GUI comparison without run-as (non-debuggable build).

Reuses already-reviewed debug images; switch steady, reopen, restore HA.
No Start, Bluetooth, effects change, log clearing or physical device access.
"""
import argparse
import hashlib
import io
import json
from pathlib import Path
import re
import subprocess
import time
import numpy as np
from PIL import Image


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--apk', type=Path, required=True)
    parser.add_argument('--debug-gui', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    base = ['C:/AndroidCodex/Sdk/platform-tools/adb.exe', '-s', 'emulator-5560']
    package = 'com.guanyu.rx400hprobe.debug'
    flags = getattr(subprocess, 'CREATE_NO_WINDOW', 0)

    def raw(*command):
        return subprocess.run(base + list(command), check=True, capture_output=True,
                              timeout=30, creationflags=flags).stdout

    def adb(*command):
        return raw(*command).decode('utf-8', errors='replace').strip()

    assert adb('shell', 'getprop', 'ro.kernel.qemu') == '1'
    installed = adb('shell', 'pm', 'path', package).removeprefix('package:')
    expected = hashlib.sha256(args.apk.read_bytes()).hexdigest()
    assert adb('shell', 'sha256sum', installed).split()[0] == expected
    assert adb('shell', 'wm', 'size') == 'Physical size: 1280x720'
    args.output.mkdir(parents=True, exist_ok=False)
    results = {'apk_sha256': expected, 'pixel_matches': {}, 'passed': False}

    def shot(name):
        png = raw('exec-out', 'screencap', '-p')
        assert png.startswith(b'\x89PNG\r\n\x1a\n')
        (args.output / name).write_bytes(png)
        a = np.asarray(Image.open(io.BytesIO(png)).convert('RGB'))
        b = np.asarray(Image.open(args.debug_gui / name).convert('RGB'))
        assert a.shape == b.shape == (720, 1280, 3)
        mask = np.ones((720, 1280), dtype=bool)
        # Moving wheel phase is not synchronized by launch-to-screenshot time.
        exclusions = [(485, 320, 582, 411), (681, 320, 782, 411)] if name == 'optimized.png' else []
        for left, top, right, bottom in exclusions:
            mask[top:bottom, left:right] = False
        changed = int(np.sum(np.any(a != b, axis=2) & mask))
        results['pixel_matches'][name] = {'changed_pixels': changed, 'compared_pixels': int(mask.sum()),
                                         'excluded_rectangles': exclusions}
        assert changed == 0, f'{name}: benchmark/debug screenshot differs'

    def launch(activity):
        adb('shell', 'am', 'force-stop', package)
        result = adb('shell', 'am', 'start', '-W', '-n', package + '/com.guanyu.rx400hprobe.' + activity)
        assert 'Error' not in result
        time.sleep(2)

    try:
        launch('DashboardPreviewActivity')
        shot('optimized.png')
        launch('MainActivity')
        shot('main-idle.png')
        hierarchy = adb('shell', 'dumpsys', 'activity', 'top')
        matches = re.findall(r'PixelDashboardView\{[^\n}]*? (\d+),(\d+)-(\d+),(\d+)', hierarchy)
        assert matches and tuple(map(int, matches[-1])) == (0, 0, 1280, 672)
        scale = 672 / 720
        left = (1280 - 1280 * scale) / 2

        def tap(x, y):
            adb('shell', 'input', 'tap', str(round(left + x * scale)), str(round(y * scale)))
            time.sleep(.35)

        tap(863, 57)
        shot('settings-default.png')
        tap(660, 162)
        shot('confirm-660.png')
        tap(810, 548)
        shot('settings-steady.png')
        launch('MainActivity')
        tap(863, 57)
        shot('settings-steady-after-reopen.png')
        tap(860, 162)
        shot('confirm-860.png')
        tap(810, 548)
        shot('settings-ha-restored.png')
        tap(965, 110)
        shot('main-after-settings.png')
        results['passed'] = True
    finally:
        (args.output / 'result.json').write_text(json.dumps(results, indent=2), encoding='utf-8')
        print(json.dumps(results))


if __name__ == '__main__':
    main()
