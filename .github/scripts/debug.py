#!/usr/bin/env python3
import os
import shutil
import subprocess
from pathlib import Path

def run(command):
    print("$ " + " ".join(command), flush=True)
    subprocess.run(command, check=True)

def main():
    run(["chmod", "+x", "./gradlew"])
    run(["./gradlew", ":app:testDebugUnitTest", ":app:assembleDebug", "--stacktrace"])
    source = Path("app/build/outputs/apk/debug/app-debug.apk")
    if not source.is_file():
        raise SystemExit(f"Debug APK is missing: {source}")
    target = Path(f"Downly-debug-{os.environ.get('GITHUB_SHA', 'local')[:7]}.apk")
    shutil.copy2(source, target)
    print(f"APK: {target} ({target.stat().st_size} bytes)")

if __name__ == "__main__":
    main()
