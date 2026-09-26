"""Package only the local D061 candidates, source snapshot and provenance.

Does not commit, push, include signing keys, original vehicle ZIPs, .git or caches.
Includes pre-existing untracked source/assets. Existing output is refused.
"""
import argparse
import difflib
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import zipfile


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--baseline-source', type=Path, required=True)
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
                       'gradlew', 'gradlew.bat', 'app/build.gradle.kts', 'app/proguard-rules.pro')})
    records = [{'path': path.relative_to(repo).as_posix(), 'bytes': path.stat().st_size, 'sha256': digest(path)}
               for path in paths if path.is_file()]
    artifacts = []
    for kind, label in [('benchmark', 'test'), ('debug', 'debug-control')]:
        source = repo / f'app/build/outputs/apk/{kind}/app-{kind}.apk'
        target = args.output / f'RX400h-Monitor-v0.3.5-v28-D061-AB-{label}.apk'
        shutil.copy2(source, target)
        artifacts.append({'path': target.name, 'build_type': kind, 'bytes': target.stat().st_size,
                          'sha256': digest(target), 'debuggable': kind == 'debug'})
    source_zip = args.output / 'D061-source-review.zip'
    with zipfile.ZipFile(source_zip, 'x', compression=zipfile.ZIP_DEFLATED) as archive:
        for row in records:
            archive.write(repo / row['path'], row['path'])
    changes = []
    diff = []
    relative_paths = {p.relative_to(repo / 'app').as_posix() for p in (repo / 'app/src').rglob('*') if p.is_file()}
    relative_paths |= {p.relative_to(args.baseline_source).as_posix() for p in args.baseline_source.rglob('*') if p.is_file()}
    relative_paths.add('build.gradle.kts')
    for relative in sorted(relative_paths):
        old = args.baseline_source / relative
        new = repo / 'app' / relative
        old_hash = digest(old) if old.is_file() else None
        new_hash = digest(new) if new.is_file() else None
        if old_hash == new_hash:
            continue
        changes.append({'path': 'app/' + relative, 'before_sha256': old_hash, 'after_sha256': new_hash})
        if new.suffix in {'.kt', '.kts', '.xml'}:
            before = old.read_text(encoding='utf-8').splitlines(keepends=True) if old_hash else []
            after = new.read_text(encoding='utf-8').splitlines(keepends=True) if new_hash else []
            diff.extend(difflib.unified_diff(before, after, fromfile='D060/app/' + relative, tofile='D061/app/' + relative))
    document = {'branch': git('branch', '--show-current').strip(), 'head': git('rev-parse', 'HEAD').strip(),
        'working_tree': git('status', '--short'), 'source_root': str(repo), 'artifacts': artifacts,
        'source_zip_sha256': digest(source_zip), 'app_changes_since_d060': changes, 'files': records,
        'scope': 'Local dirty candidate; no GitHub build/push, firmware or real-car verification. Source archive excludes signing key; building with fixed signing requires the existing local repository key. Prior dirty D055-D060 changes are included.'}
    (args.output / 'manifest.json').write_text(json.dumps(document, ensure_ascii=False, indent=2), encoding='utf-8')
    (args.output / 'source.sha256').write_text(''.join(f"{r['sha256']}  {r['path']}\n" for r in records), encoding='utf-8')
    (args.output / 'D060-to-D061-app.diff').write_text(''.join(diff), encoding='utf-8')
    (args.output / 'tracked-only.diff').write_text(git('diff', '--no-ext-diff', '--no-color'), encoding='utf-8')
    print(json.dumps({'artifacts': artifacts, 'source_files': len(records), 'app_changes': len(changes),
                      'output': str(args.output.resolve())}))


if __name__ == '__main__':
    main()
