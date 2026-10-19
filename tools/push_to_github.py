#!/usr/bin/env python3
"""
Push KILAT Motion ke GitHub + (opsional) buat repo otomatis.

Pakai token FINE-GRAINED dengan scope minimal:
  - Contents: Read and write
  - Administration: Read and write  (hanya kalau ingin script membuat repo)

Contoh:
  export GITHUB_TOKEN=github_pat_xxx
  python3 tools/push_to_github.py --user xykal --repo kilat-motion --private

Script TIDAK menyimpan token ke file/git config (remote URL dibersihkan setelah push).
"""
import argparse
import json
import os
import subprocess
import sys
import urllib.error
import urllib.request

API = "https://api.github.com"


def api(method: str, path: str, token: str, payload: dict | None = None) -> tuple[int, dict]:
    data = json.dumps(payload).encode() if payload is not None else None
    req = urllib.request.Request(
        API + path,
        data=data,
        method=method,
        headers={
            "Authorization": f"Bearer {token}",
            "Accept": "application/vnd.github+json",
            "X-GitHub-Api-Version": "2022-11-28",
            "User-Agent": "kilat-motion-pusher",
            **({"Content-Type": "application/json"} if data else {}),
        },
    )
    try:
        with urllib.request.urlopen(req, timeout=20) as r:
            body = r.read().decode() or "{}"
            return r.status, json.loads(body)
    except urllib.error.HTTPError as e:
        body = e.read().decode() or "{}"
        try:
            return e.code, json.loads(body)
        except Exception:
            return e.code, {"message": body[:300]}


def run(cmd: list[str], quiet: bool = False) -> int:
    if not quiet:
        print("$", " ".join(c if "TOKEN" not in c else "<redacted>" for c in cmd))
    return subprocess.call(cmd)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--user", required=True, help="username GitHub, mis. xykal")
    ap.add_argument("--repo", default="kilat-motion", help="nama repo (default: kilat-motion)")
    ap.add_argument("--private", action="store_true", help="buat repo private")
    ap.add_argument("--skip-create", action="store_true", help="jangan buat repo (sudah ada)")
    ap.add_argument("--desc", default="⚡ KILAT Motion — motion graphics editor native Android (Kotlin, Canvas, MediaCodec). Tanpa library eksternal.")
    args = ap.parse_args()

    token = os.environ.get("GITHUB_TOKEN", "").strip()
    if not token:
        print("ERROR: set GITHUB_TOKEN dulu (fine-grained, Contents: RW).", file=sys.stderr)
        return 2

    status, me = api("GET", "/user", token)
    if status != 200:
        print(f"ERROR: token tidak valid / tidak punya akses ({status}): {me.get('message')}", file=sys.stderr)
        return 2
    login = me.get("login", "?")
    print(f"Login sebagai: {login}")
    if login.lower() != args.user.lower():
        print(f"PERINGATAN: token milik '{login}', bukan '{args.user}'. Lanjut?", end=" ")
        if input().strip().lower() not in ("y", "ya", "yes"):
            return 1

    repo_full = f"{args.user}/{args.repo}"
    if not args.skip_create:
        status, res = api("POST", "/user/repos", token, {
            "name": args.repo,
            "description": args.desc,
            "private": bool(args.private),
            "has_issues": True,
            "has_wiki": False,
            "auto_init": False,
        })
        if status == 201:
            print(f"✅ Repo dibuat: {res.get('html_url')}")
        elif status == 422:
            print(f"ℹ️  Repo '{repo_full}' sudah ada — lanjut push.")
        else:
            print(f"⚠️  Gagal buat repo ({status}): {res.get('message')}", file=sys.stderr)
            print("    Kalau repo sudah dibuat manual, jalankan ulang dengan --skip-create", file=sys.stderr)
            return 1

    # pastikan ada commit
    if run(["git", "rev-parse", "--verify", "HEAD"], quiet=True) != 0:
        print("ERROR: belum ada commit di repo lokal.", file=sys.stderr)
        return 2

    # push via remote sementara yang membawa token, lalu dibersihkan
    remote_url = f"https://x-access-token:{token}@github.com/{repo_full}.git"
    run(["git", "remote", "remove", "origin"], quiet=True)
    run(["git", "remote", "add", "origin", remote_url], quiet=True)

    branch = subprocess.check_output(["git", "rev-parse", "--abbrev-ref", "HEAD"]).decode().strip() or "main"
    code = run(["git", "push", "-u", "origin", branch])

    # bersihkan token dari git config
    run(["git", "remote", "set-url", "origin", f"https://github.com/{repo_full}.git"], quiet=True)

    if code == 0:
        print()
        print("🎉 BERHASIL PUSH!")
        print(f"   Repo     : https://github.com/{repo_full}")
        print(f"   Actions  : https://github.com/{repo_full}/actions")
        print("   → Buka tab Actions, tunggu ~2-3 menit, APK ada di Artifacts.")
    else:
        print("❌ Push gagal. Cek apakah token punya izin Contents: Read and write.", file=sys.stderr)
    return code


if __name__ == "__main__":
    sys.exit(main())
