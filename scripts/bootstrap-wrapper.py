"""Download the official Gradle 8.13 wrapper; no global Gradle needed."""
import hashlib
import pathlib
import sys
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[1]
BASE = "https://raw.githubusercontent.com/gradle/gradle/v8.13.0/"

def download(url):
    with urllib.request.urlopen(url, timeout=30) as response:
        return response.read()

def main():
    files = {name: download(BASE + name) for name in (
        "gradlew", "gradlew.bat", "gradle/wrapper/gradle-wrapper.jar")}
    expected = download("https://services.gradle.org/distributions/gradle-8.13-wrapper.jar.sha256").decode().strip()
    actual = hashlib.sha256(files["gradle/wrapper/gradle-wrapper.jar"]).hexdigest()
    if actual != expected:
        raise ValueError("Wrapper checksum mismatch")
    checksum = download("https://services.gradle.org/distributions/gradle-8.13-bin.zip.sha256").decode().strip()
    if len(checksum) != 64 or any(c not in "0123456789abcdef" for c in checksum):
        raise ValueError("Invalid distribution checksum")
    files["gradle/wrapper/gradle-wrapper.properties"] = (
        "distributionBase=GRADLE_USER_HOME\n"
        "distributionPath=wrapper/dists\n"
        "distributionUrl=https\\://services.gradle.org/distributions/gradle-8.13-bin.zip\n"
        f"distributionSha256Sum={checksum}\n"
        "networkTimeout=10000\nvalidateDistributionUrl=true\n"
        "zipStoreBase=GRADLE_USER_HOME\nzipStorePath=wrapper/dists\n"
    ).encode()
    for name, content in files.items():
        target = ROOT / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(content)
    print("Official wrapper downloaded and checksum verified.")

if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print(f"Wrapper setup failed: {error}", file=sys.stderr)
        sys.exit(1)
