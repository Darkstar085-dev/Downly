#!/usr/bin/env python3
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path

def run(command):
    print("$ " + " ".join(command), flush=True)
    subprocess.run(command, check=True)

def output(command):
    return subprocess.check_output(command, text=True).strip()

def version_info():
    text = Path("app/build.gradle.kts").read_text(encoding="utf-8")
    match = re.search(r'versionName = "([^"]+)"', text)
    if not match:
        raise SystemExit("Could not determine app versionName.")
    version = match.group(1)
    return version, f"v{version}", f"Downly_v{version}.apk"

def apk_path():
    return Path(os.environ.get("GITHUB_WORKSPACE", ".")) / version_info()[2]

def validate(requested):
    version, _, _ = version_info()
    if requested != version:
        raise SystemExit(f"Requested version {requested!r} does not match app version {version!r}.")
    if not re.fullmatch(r"[0-9]+(?:\.[0-9]+){1,3}(?:[-+][A-Za-z0-9.-]+)?", requested):
        raise SystemExit("Use a semantic version such as 1.0 or 1.2.3.")
    print(f"Release version validated: {version}")

def build():
    run(["chmod", "+x", "./gradlew"])
    run(["./gradlew", "assembleRelease", "--stacktrace"])
    candidates = sorted(Path("app/build/outputs/apk/release").glob("*.apk"))
    if not candidates:
        raise SystemExit("No release APK was produced.")
    target = apk_path()
    shutil.copy2(candidates[0], target)
    print(f"Release APK: {target} ({target.stat().st_size} bytes)")

def verify():
    target = apk_path()
    if not target.is_file() or target.stat().st_size == 0:
        raise SystemExit(f"Release APK is missing or empty: {target}")
    sdk_root = os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME")
    if not sdk_root:
        raise SystemExit("Android SDK root is not configured.")
    candidates = []
    for path in (Path(sdk_root) / "build-tools").glob("*/apksigner"):
        match = re.search(r"/build-tools/([0-9]+(?:\.[0-9]+)*)/", str(path))
        if path.is_file() and match:
            candidates.append((tuple(int(x) for x in match.group(1).split(".")), path))
    if not candidates:
        raise SystemExit("Android Build Tools apksigner was not found.")
    signer = sorted(candidates, key=lambda item: item[0])[-1][1]
    run([str(signer), "verify", "--verbose", str(target)])
    run([str(signer), "verify", "--print-certs", str(target)])

def release_tags():
    repo = os.environ["GITHUB_REPOSITORY"]
    text = output(["gh", "release", "list", "--repo", repo, "--limit", "100", "--json", "tagName", "--jq", ".[].tagName"])
    return [line for line in text.splitlines() if line]

def notes():
    _, tag, _ = version_info()
    tags = release_tags()
    previous = next((item for item in tags if item != tag), "")
    range_value = f"{previous}..HEAD" if previous else "HEAD"
    commits = output(["git", "log", range_value, "--no-merges", "--pretty=format:%s"]).splitlines()
    entries, seen = [], set()
    for subject in commits:
        if ":" not in subject:
            continue
        item = subject.split(":", 1)[1].strip()
        if item and item not in seen:
            seen.add(item)
            entries.append(f"- {item[0].upper()}{item[1:]}")
    if not entries:
        entries.append("- Maintenance and improvements.")
    if previous:
        entries += ["", f"**Full Changelog:** https://github.com/{os.environ['GITHUB_REPOSITORY']}/compare/{previous}...{tag}"]
    Path("RELEASE_NOTES.md").write_text("## What's new\n\n" + "\n".join(entries) + "\n", encoding="utf-8")
    print(Path("RELEASE_NOTES.md").read_text(encoding="utf-8"))

def manifest():
    version, tag, filename = version_info()
    apk = apk_path()
    if not apk.is_file():
        raise SystemExit(f"Release APK is missing: {apk}")
    payload = {
        "version": version,
        "tag": tag,
        "name": f"Downly {version}",
        "description": Path("RELEASE_NOTES.md").read_text(encoding="utf-8"),
        "download_url": f"https://github.com/{os.environ['GITHUB_REPOSITORY']}/releases/download/{tag}/{filename}",
        "size": apk.stat().st_size,
        "sha256": hashlib.sha256(apk.read_bytes()).hexdigest(),
        "published_at": output(["date", "-u", "+%Y-%m-%dT%H:%M:%SZ"])
    }
    Path("app-release.json").write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
    print("Generated app-release.json")

def release():
    _, tag, _ = version_info()
    apk, manifest_file = apk_path(), Path("app-release.json")
    repo = os.environ["GITHUB_REPOSITORY"]
    if not apk.is_file() or not manifest_file.is_file():
        raise SystemExit("Release APK or update manifest is missing.")
    view = subprocess.run(["gh", "release", "view", tag, "--repo", repo], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    if view.returncode == 0:
        raise SystemExit(f"Release {tag} already exists. Bump app version before publishing.")
    run(["gh", "release", "create", tag, str(apk), str(manifest_file), "--repo", repo, "--title", f"Downly {version_info()[0]}", "--notes-file", "RELEASE_NOTES.md", "--latest"])

def telegram():
    import urllib.request
    token = os.environ.get("TELEGRAM_BOT_TOKEN")
    chat_id = os.environ.get("RELEASE_CHAT_ID")
    apk = apk_path()
    if not token or not chat_id:
        raise SystemExit("Telegram release secrets are not configured.")
    if not apk.is_file():
        raise SystemExit(f"Release APK is missing: {apk}")
    _, tag, _ = version_info()
    boundary = "----DownlyReleaseBoundary"
    fields = {"chat_id": chat_id, "caption": f"Downly {version_info()[0]} is available.\nhttps://github.com/{os.environ['GITHUB_REPOSITORY']}/releases/tag/{tag}"}
    chunks = []
    for key, value in fields.items():
        chunks.append(f"--{boundary}\r\nContent-Disposition: form-data; name=\"{key}\"\r\n\r\n{value}\r\n".encode())
    chunks.append(f"--{boundary}\r\nContent-Disposition: form-data; name=\"document\"; filename=\"{apk.name}\"\r\nContent-Type: application/vnd.android.package-archive\r\n\r\n".encode() + apk.read_bytes() + f"\r\n--{boundary}--\r\n".encode())
    req = urllib.request.Request(f"https://api.telegram.org/bot{token}/sendDocument", data=b"".join(chunks), headers={"Content-Type": f"multipart/form-data; boundary={boundary}"}, method="POST")
    with urllib.request.urlopen(req, timeout=900) as response:
        data = json.loads(response.read().decode("utf-8"))
    if not data.get("ok"):
        raise RuntimeError("Telegram release upload failed.")
    print("Release APK sent through Telegram.")

if __name__ == "__main__":
    stage = sys.argv[1] if len(sys.argv) > 1 else ""
    if stage == "validate": validate(sys.argv[2] if len(sys.argv) > 2 else "")
    elif stage == "build": build()
    elif stage == "verify": verify()
    elif stage == "notes": notes()
    elif stage == "manifest": manifest()
    elif stage == "release": release()
    elif stage == "telegram": telegram()
    else: raise SystemExit("Usage: release.py {validate|build|verify|notes|manifest|release|telegram} [version]")
