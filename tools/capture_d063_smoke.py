"""Capture D063 real file-fault/JSON results on an explicit, already installed emulator.
No APK installation, OBD access, global logcat clearing or physical-device access.
All host outputs are new files. The Android fixture only touches its own new data.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import time
import uuid


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--serial', required=True)
    parser.add_argument('--apk', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if not re.fullmatch(r'emulator-\d+', args.serial):
        raise ValueError('Explicit emulator serial required')
    base = ['C:/AndroidCodex/Sdk/platform-tools/adb.exe', '-s', args.serial]
    flags = getattr(subprocess, 'CREATE_NO_WINDOW', 0)
    package = 'com.guanyu.rx400hprobe.debug'

    def adb(*command):
        return subprocess.run(base + list(command), capture_output=True, timeout=30,
                              check=True, creationflags=flags).stdout.decode('utf-8', errors='replace').strip()

    if adb('shell', 'getprop', 'ro.kernel.qemu') != '1':
        raise ValueError('Not an emulator')
    expected = hashlib.sha256(args.apk.read_bytes()).hexdigest()
    installed = adb('shell', 'pm', 'path', package).removeprefix('package:')
    if adb('shell', 'sha256sum', installed).split()[0] != expected:
        raise ValueError('Installed APK differs from input artifact')
    args.output.mkdir(parents=True, exist_ok=False)
    run_id = str(uuid.uuid4())
    metadata = {'run_id': run_id, 'apk_sha256': expected, 'serial': args.serial,
                'api': adb('shell', 'getprop', 'ro.build.version.sdk'),
                'screen': adb('shell', 'wm', 'size'), 'density': adb('shell', 'wm', 'density'),
                'scope': 'isolated Android files only; not actual Bluetooth/End/reconnect integration'}
    (args.output / 'identity.json').write_text(json.dumps(metadata, indent=2), encoding='utf-8')
    launch = adb('shell', 'am', 'start', '-W', '-n',
                 package + '/com.guanyu.rx400hprobe.SessionLifecycleSmokeActivity',
                 '--es', 'smoke_run_id', run_id)
    (args.output / 'launch.txt').write_text(launch, encoding='utf-8')
    deadline = time.monotonic() + 30
    complete = None
    while time.monotonic() < deadline:
        log = adb('logcat', '-d', '-s', 'RX400hLifecycleSmoke:I', 'AndroidRuntime:E', '*:S')
        (args.output / 'logcat.txt').write_text(log, encoding='utf-8')
        for line in log.splitlines():
            if run_id in line and 'COMPLETE {' in line:
                complete = json.loads(line[line.index('COMPLETE ') + len('COMPLETE '):])
        if complete:
            break
        time.sleep(0.5)
    if not complete:
        raise RuntimeError('Fixture timed out: no matching completion; inspect preserved log')
    (args.output / 'result.json').write_text(json.dumps(complete, indent=2), encoding='utf-8')
    print(json.dumps(complete))
    if complete['passed'] != 8 or complete['failed']:
        raise RuntimeError('Android file-fault suite failed')


if __name__ == '__main__':
    main()
