"""Read-only emulator identity check, then debug-only exact composition oracle.
No Bluetooth or user files. Test outputs must use a new directory.
"""
import argparse
import json
from pathlib import Path
import re
import subprocess
import time


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    base = ['C:/AndroidCodex/Sdk/platform-tools/adb.exe', '-s', 'emulator-5560']
    package = 'com.guanyu.rx400hprobe.debug'
    flags = getattr(subprocess, 'CREATE_NO_WINDOW', 0)

    def adb(*command):
        return subprocess.run(base + list(command), capture_output=True, check=True,
                              timeout=30, creationflags=flags).stdout.decode('utf-8', errors='replace').strip()

    if adb('shell', 'getprop', 'ro.kernel.qemu') != '1':
        raise ValueError('Not a verified emulator')
    apk = adb('shell', 'pm', 'path', package).removeprefix('package:')
    identity = {'apk_sha256': adb('shell', 'sha256sum', apk).split()[0],
                'scope': '40 UI-only snapshots with startup, transitions, stale/fresh, Idle; no OBD'}
    args.output.mkdir(parents=True, exist_ok=False)
    adb('shell', 'am', 'force-stop', package)
    with (args.output / 'logcat.txt').open('wb') as log:
        proc = subprocess.Popen(base + ['logcat', '-v', 'threadtime', '-T', '1',
                                       'RX400hPixelOracle:I', 'AndroidRuntime:E', '*:S'],
                                stdout=log, stderr=subprocess.STDOUT, creationflags=flags)
        try:
            adb('shell', 'am', 'start', '-W', '-n', package + '/com.guanyu.rx400hprobe.DashboardPreviewActivity',
                '--ez', 'verify_composition', 'true')
            time.sleep(10)
        finally:
            proc.terminate()
            proc.wait(timeout=10)
    content = (args.output / 'logcat.txt').read_text(encoding='utf-8', errors='replace')
    frames = [int(value) for value in re.findall(r'PASS frames=(\d+)', content)]
    identity['checked_frames'] = max(frames, default=0)
    identity['passed'] = ('COMPLETE samples=40' in content and identity['checked_frames'] >= 40
                          and 'FATAL EXCEPTION' not in content)
    (args.output / 'result.json').write_text(json.dumps(identity, indent=2), encoding='utf-8')
    print(json.dumps(identity))
    if not identity['passed']:
        raise RuntimeError('Pixel oracle incomplete or failed; inspect preserved log')


if __name__ == '__main__':
    main()
