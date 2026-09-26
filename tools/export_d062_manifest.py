"""D062 local review package, compared to the exact D061 source ZIP, never HEAD.

Excludes signing keys, original car ZIPs, build caches and .git. Refuses output
overwrite. No Git mutation, deployment or physical-device action.
"""
import argparse
import difflib
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import zipfile


def sha(data):
    return hashlib.sha256(data).hexdigest()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--baseline", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    repo = Path(__file__).resolve().parents[1]
    with zipfile.ZipFile(args.baseline) as archive:
        before = {name: archive.read(name) for name in archive.namelist()}
    paths = {p for root in ("app/src", "tools", "gradle") for p in (repo / root).rglob("*")
             if p.is_file() and "__pycache__" not in p.parts and p.suffix != ".pyc"}
    paths |= set(repo.glob("*.md"))
    paths |= {repo / p for p in ("build.gradle.kts", "settings.gradle.kts", "gradle.properties",
        "gradlew", "gradlew.bat", "app/build.gradle.kts", "app/proguard-rules.pro")}
    after = {p.relative_to(repo).as_posix(): p.read_bytes() for p in sorted(paths) if p.is_file()}
    records = [{"path": p, "bytes": len(b), "sha256": sha(b)} for p, b in after.items()]
    app_paths = sorted({p for p in set(before) | set(after)
                        if p.startswith("app/") or p == "gradle.properties"})
    changes, diff = [], []
    for name in app_paths:
        old, new = before.get(name), after.get(name)
        if old == new:
            continue
        changes.append({"path": name, "before_sha256": sha(old) if old is not None else None,
                        "after_sha256": sha(new) if new is not None else None})
        if Path(name).suffix in (".kt", ".kts", ".xml", ".properties"):
            diff.extend(difflib.unified_diff((old or b"").decode("utf-8").splitlines(True),
                (new or b"").decode("utf-8").splitlines(True),
                fromfile="D061/" + name, tofile="D062/" + name))

    def stats(files, prefix):
        selected = [b for p, b in files.items() if p.startswith(prefix) and p.endswith(".kt")]
        return {"files": len(selected), "lines": sum(len(b.splitlines()) for b in selected),
                "test_annotations": sum(b.count(b"@Test") for b in selected)}

    args.output.mkdir(parents=True, exist_ok=False)
    artifacts = []
    for kind, label in (("benchmark", "test"), ("debug", "debug-control")):
        target = args.output / f"RX400h-Monitor-v0.3.5-v28-D062-lean-{label}.apk"
        shutil.copy2(repo / f"app/build/outputs/apk/{kind}/app-{kind}.apk", target)
        artifacts.append({"path": target.name, "build_type": kind, "bytes": target.stat().st_size,
                          "sha256": sha(target.read_bytes()), "debuggable": kind == "debug"})
    source_zip = args.output / "D062-source-review.zip"
    with zipfile.ZipFile(source_zip, "x", compression=zipfile.ZIP_DEFLATED) as archive:
        for name, content in after.items():
            archive.writestr(name, content)

    def git(*command):
        return subprocess.run(["git", "-C", str(repo), *command], check=True, capture_output=True,
            creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0)).stdout.decode("utf-8", errors="replace")

    document = {"branch": git("branch", "--show-current").strip(), "head": git("rev-parse", "HEAD").strip(),
        "working_tree": git("status", "--short"), "source_root": str(repo), "artifacts": artifacts,
        "source_zip_sha256": sha(source_zip.read_bytes()),
        "baseline_zip_sha256": sha(args.baseline.read_bytes()), "app_changes_since_d061": changes,
        "main_before": stats(before, "app/src/main/"), "main_after": stats(after, "app/src/main/"),
        "tests_before": stats(before, "app/src/test/"), "tests_after": stats(after, "app/src/test/"),
        "files": records,
        "scope": "Local dirty candidate, includes prior D055-D061. No push, CI, physical install or real5Hz claim. Signing key excluded; fixed signing builds require original local key."}
    (args.output / "manifest.json").write_text(json.dumps(document, ensure_ascii=False, indent=2), encoding="utf-8")
    (args.output / "source.sha256").write_text("".join(f"{r['sha256']}  {r['path']}\n" for r in records), encoding="utf-8")
    (args.output / "D061-to-D062-app.diff").write_text("".join(diff), encoding="utf-8")
    (args.output / "tracked-only.diff").write_text(git("diff", "--no-ext-diff", "--no-color"), encoding="utf-8")
    print(json.dumps({k: v for k, v in document.items() if k not in ("working_tree", "files")}, ensure_ascii=False))


if __name__ == "__main__":
    main()
