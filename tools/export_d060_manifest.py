"""Freeze local candidate identity without committing or publishing anything.
Includes untracked source/assets, unlike a plain git diff. Does not export keys,
credentials, .git, caches or original vehicle evidence. Existing outputs refused.
"""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    repo = Path(__file__).resolve().parents[1]
    args.output.mkdir(parents=True, exist_ok=False)
    flags = getattr(subprocess, 'CREATE_NO_WINDOW', 0)

    def git(*command):
        return subprocess.run(['git', '-C', str(repo), *command], check=True, capture_output=True,
                              creationflags=flags).stdout.decode('utf-8', errors='replace')

    paths = sorted(set(path for directory in ('app/src', 'tools', 'gradle')
                       for path in (repo / directory).rglob('*') if path.is_file()
                       and '__pycache__' not in path.parts and path.suffix != '.pyc') |
                   set(repo.glob('*.md')) |
                   {repo / path for path in ('build.gradle.kts', 'settings.gradle.kts', 'gradle.properties',
                                            'app/build.gradle.kts', 'app/proguard-rules.pro')})
    records = [{'path': path.relative_to(repo).as_posix(), 'bytes': path.stat().st_size,
                'sha256': hashlib.sha256(path.read_bytes()).hexdigest()}
               for path in paths if path.is_file()]
    apk = repo / 'app/build/outputs/apk/debug/app-debug.apk'
    apk_sha = hashlib.sha256(apk.read_bytes()).hexdigest()
    artifact = args.output / 'RX400h-Monitor-v0.3.5-v28-D060-local-debug.apk'
    shutil.copy2(apk, artifact)
    document = {
        'branch': git('branch', '--show-current').strip(), 'head': git('rev-parse', 'HEAD').strip(),
        'working_tree': git('status', '--short'), 'source_root': str(repo),
        'apk_sha256': apk_sha, 'apk_bytes': artifact.stat().st_size,
        'scope': 'Uncommitted local candidate, not a release. Prior dirty changes included. Source manifest includes untracked files.',
        'files': records,
    }
    (args.output / 'manifest.json').write_text(json.dumps(document, ensure_ascii=False, indent=2), encoding='utf-8')
    (args.output / 'source.sha256').write_text(''.join(f"{row['sha256']}  {row['path']}\n" for row in records), encoding='utf-8')
    (args.output / 'tracked-only.diff').write_text(git('diff', '--no-ext-diff', '--no-color'), encoding='utf-8')
    print(json.dumps({'apk_sha256': apk_sha, 'files': len(records), 'output': str(args.output.resolve())}))


if __name__ == '__main__':
    main()
