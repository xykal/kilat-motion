# 🚀 Repo & CI — SUDAH LIVE

Repo  : **https://github.com/xykal/kilat-motion** (private)
CI    : https://github.com/xykal/kilat-motion/actions
Release: https://github.com/xykal/kilat-motion/releases/tag/v1.0.0

## Download APK (langsung, tanpa login)
- **Release (disarankan)**: https://github.com/xykal/kilat-motion/releases/download/v1.0.0/app-release.apk
- **Debug**: https://github.com/xykal/kilat-motion/releases/download/v1.0.0/app-debug.apk

> Repo private → link di atas butuh login GitHub. Kalau mau download tanpa login,
> ubah repo jadi public: **Settings → General → Danger Zone → Change visibility**.

## Status build
| Run | Trigger | Hasil |
|---|---|---|
| #1 | push `main` | ✅ sukses — artifact debug + release |
| #4 | tag `v1.0.0` | ✅ sukses — APK terlampir di Release |

Rata-rata build: **±1 menit** (setelah cache Gradle), cold build ±4 menit.

## Alur rilis berikutnya
```bash
git commit -am "feat: ..."
git push
git tag v1.0.1 && git push origin v1.0.1   # → build + Release otomatis
```

## Kalau mau ubah visibility jadi public
GitHub → repo → **Settings** → scroll ke **Danger Zone** → **Change repository visibility** → Public.
