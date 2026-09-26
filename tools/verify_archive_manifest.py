"""Read-only document baseline verification against canonical Git blobs.

Use --index --emit while preparing an archive; paste output into the manifest.
Default checks committed HEAD, independent of Windows checkout line endings.
"""
import argparse
import hashlib
from pathlib import Path
import subprocess


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--index', action='store_true')
    parser.add_argument('--emit', action='store_true')
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]

    def blob(path):
        ref = ':' + path if args.index else 'HEAD:' + path
        return subprocess.run(['git', '-C', str(root), 'show', ref], check=True,
                              capture_output=True).stdout

    records = blob('BASELINE_MANIFEST.sha256').decode('utf-8').splitlines()
    for row in records:
        expected, path = row.split('  ', 1)
        actual = hashlib.sha256(blob(path)).hexdigest()
        if args.emit:
            print(actual + '  ' + path)
        elif actual != expected:
            raise ValueError('Baseline mismatch: ' + path)
    if not args.emit:
        print(f'PASS: {len(records)} canonical Git document hashes')


if __name__ == '__main__':
    main()
