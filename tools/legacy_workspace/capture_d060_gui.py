"""Same-APK renderer comparison and idle settings smoke on a read-only emulator.
Never presses Start/End or accesses Bluetooth. The only settings mutation is the
emulator-local next-session strategy, restored to HA replica before completion.
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
    parser.add_argument('--serial', required=True)
    parser.add_argument('--apk', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if not re.fullmatch(r'emulator-\d+', args.serial):
        raise ValueError('Emulator serial required')
    base = ['C:/AndroidCodex/Sdk/platform-tools/adb.exe', '-s', args.serial]
    flags = getattr(subprocess, 'CREATE_NO_WINDOW', 0)
    package = 'com.guanyu.rx400hprobe.debug'

    def raw(*command):
        return subprocess.run(base + list(command), capture_output=True, timeout=30,
                              check=True, creationflags=flags).stdout

    def adb(*command):
        return raw(*command).decode('utf-8', errors='replace').strip()

    if adb('shell', 'getprop', 'ro.kernel.qemu') != '1':
        raise ValueError('Not emulator')
    apk_sha = hashlib.sha256(args.apk.read_bytes()).hexdigest()
    installed = adb('shell', 'pm', 'path', package).removeprefix('package:')
    if adb('shell', 'sha256sum', installed).split()[0] != apk_sha:
        raise ValueError('Wrong installed APK')
    if adb('shell', 'wm', 'size') != 'Physical size: 1280x720':
        raise ValueError('Unexpected viewport')
    args.output.mkdir(parents=True, exist_ok=False)
    record = {'apk_sha256': apk_sha, 'serial': args.serial, 'screenshots': {}, 'events': [],
              'scope': 'same-current-content reference composition; not historical exact APK dynamic A/B'}

    def shot(name):
        png = raw('exec-out', 'screencap', '-p')
        if not png.startswith(b'\x89PNG\r\n\x1a\n'):
            raise ValueError('Not PNG')
        (args.output / name).write_bytes(png)
        record['screenshots'][name] = hashlib.sha256(png).hexdigest()

    def launch(activity, *extras):
        adb('shell', 'am', 'force-stop', package)
        result = adb('shell', 'am', 'start', '-W', '-n', package + '/com.guanyu.rx400hprobe.' + activity, *extras)
        if 'Error' in result:
            raise RuntimeError(result)
        record['events'].append({'launch': activity, 'extras': extras})
        time.sleep(2)

    for reference in (False, True):
        launch('DashboardPreviewActivity', '--ez', 'reference_renderer', str(reference).lower())
        shot('reference.png' if reference else 'optimized.png')
    launch('MainActivity')
    shot('main-idle.png')
    hierarchy = adb('shell', 'dumpsys', 'activity', 'top')
    (args.output / 'view-hierarchy.txt').write_text(hierarchy, encoding='utf-8')
    matches = re.findall(r'PixelDashboardView\{[^\n}]*? (\d+),(\d+)-(\d+),(\d+)', hierarchy)
    if not matches or tuple(map(int, matches[-1])) != (0, 0, 1280, 672):
        raise ValueError('Unexpected custom View bounds; do not reuse stale touch coordinates')
    scale = 672 / 720
    left = (1280 - 1280 * scale) / 2

    def tap(x, y):
        adb('shell', 'input', 'tap', str(round(left + x * scale)), str(round(y * scale)))
        time.sleep(.35)

    def open_settings():
        tap(863, 57)  # Authored settings layer (390,13,83,31), logical x2.

    def select(x):
        tap(x, 162)
        shot('confirm-' + str(x) + '.png')
        tap(810, 548)

    open_settings()
    shot('settings-default.png')
    select(660)  # Steady.
    shot('settings-steady.png')
    stored = adb('shell', 'run-as', package, 'cat', 'shared_prefs/probe.xml')
    if '>STEADY<' not in stored:
        raise AssertionError('Steady selection not persisted')
    launch('MainActivity')
    open_settings()
    shot('settings-steady-after-reopen.png')
    select(860)  # Restore HA replica.
    shot('settings-ha-restored.png')
    stored = adb('shell', 'run-as', package, 'cat', 'shared_prefs/probe.xml')
    if '>HA_REPLICA<' not in stored:
        raise AssertionError('HA selection not persisted')
    tap(965, 110)  # Close; the five effect settings are not altered.
    shot('main-after-settings.png')
    record['strategy_persistence_passed'] = True
    (args.output / 'result.json').write_text(json.dumps(record, indent=2), encoding='utf-8')
    print(json.dumps(record))


if __name__ == '__main__':
    main()
