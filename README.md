# ⚡ KILAT Motion — Android Native

Motion graphics editor **native Android** (Kotlin), alur kerja ala **Alight Motion** + UI gelap ala **CapCut**.
Dibangun **tanpa library eksternal** — hanya Android SDK + Kotlin stdlib. APK dibuild otomatis via **GitHub Actions**.

![status](https://img.shields.io/badge/build-passing-2ee6c0) ![minSdk](https://img.shields.io/badge/minSdk-29-blue) ![size](https://img.shields.io/badge/APK-2.1--2.6%20MB-lightgrey)

---

## ✨ Fitur v1.0.0 (MVP)

**Layer & kanvas**
- Layer **Teks**, **Bentuk** (kotak/lingkaran/segitiga/bintang), **Gambar** (galeri)
- Kanvas interaktif: drag pindah, tarik handle pojok = resize, knob atas = rotasi, snap ke tengah
- Rasio 9:16 · 16:9 · 1:1 · 4:5 · 3:4, durasi 2–60 detik, warna latar bebas

**Animasi (inti Alight Motion)**
- **Keyframe engine** per properti: posisi X/Y, skala, rotasi, opasitas, typing, dan semua efek
- Easing: Linear · Ease In · Ease Out · Ease In-Out · **Back** · **Bounce**
- **8 preset sekali klik**: Fade, Slide Up, Zoom Pop, Spin In, Bounce In, **Typing**, **Ken Burns**, **Glitch**
- Timeline: scrub playhead, drag bar = geser waktu, ujung kanan = atur durasi, diamond keyframe, snapping

**Efek**
- Blur, Brightness, Contrast, Saturasi, Hue, **Glow berwarna**
- **Blend mode**: Normal, Screen, Multiply, Overlay, Lighten, Darken, Color Dodge, Soft Light
- Semua efek bisa di-keyframe

**Output & project**
- **Export MP4 (H.264)** via MediaCodec + MediaMuxer → galeri `Movies/KilatMotion` (720p/1080p, auto-fallback)
- **Export frame PNG** → `Pictures/KilatMotion`
- Autosave + load project JSON di internal storage, demo project bawaan

---

## 🚀 Build & install

### Lokal
Butuh **JDK 17** + **Android SDK (platform 34, build-tools 34.0.0)**.

```bash
./gradlew :app:assembleDebug      # APK debug
./gradlew :app:assembleRelease    # APK release (signed pakai debug key → bisa langsung sideload)
./gradlew :app:testDebugUnitTest  # 12 unit test engine animasi
```

APK ada di:
- `app/build/outputs/apk/debug/app-debug.apk`
- `app/build/outputs/apk/release/app-release.apk`

### GitHub Actions (otomatis)
Workflow: `.github/workflows/android.yml`
- Trigger: **push** ke `main`/`master`, **tag** `v*`, **pull request**, atau **manual** (workflow_dispatch)
- Build debug + release, upload sebagai **artifact** di halaman run
- APK siap download di tab **Actions → run → Artifacts**

### Push pertama kali
```bash
git remote add origin https://github.com/<username>/<repo>.git
git push -u origin main
```
Setelah push, buka tab **Actions** — build otomatis jalan, APK muncul di **Artifacts**.

---

## 🧱 Arsitektur

| File | Peran |
|---|---|
| `Model.kt` | `Project`, `Layer`, `Keyframe`, channel, konstanta properti |
| `Anim.kt` | Engine easing + interpolasi keyframe + 8 preset |
| `Renderer.kt` | Satu renderer dipakai preview **dan** export (teks, bentuk, gambar, efek, blend, glow, blur) |
| `PreviewView.kt` | Kanvas preview + gesture move/scale/rotate + overlay seleksi |
| `TimelineView.kt` | Ruler, bar layer, trim, diamond keyframe, playhead |
| `InspectorView.kt` | Panel properti (programatik, tanpa XML layout) |
| `Exporter.kt` | MediaCodec H.264 (input Surface) + MediaMuxer → MP4, PNG frame |
| `ProjectStore.kt` | Save/load JSON + import gambar ke storage app |
| `MainActivity.kt` | Shell UI, playback, dialog, wiring semua komponen |
| `Demo.kt` | Demo project (glow + bintang + teks typing) |

**Kenapa tanpa library eksternal?** APK kecil (~2 MB), build cepat di CI, nol risiko dependency drift, dan semua bagian penting (render + encode) sepenuhnya di tangan kita.

---

## ⚠️ Batasan v1 (jujur)

- **Video layer & audio belum ada** (fase berikutnya pakai Media3/ExoPlayer + audio track di muxer)
- **Pinch-to-zoom** belum; resize lewat handle pojok
- Export berjalan **real-time** (durasi export ≈ durasi project). Belum faster-than-realtime (butuh EGL + `eglPresentationTimeANDROID`)
- Preset Glitch memakai jitter keyframe `x`; belum ada RGB split per-channel
- Export butuh encoder H.264 yang tersedia di device; kalau 1080p gagal → otomatis retry 720p
- Build & unit test lolos; **wajib uji di device fisik** untuk validasi runtime (terutama lockCanvas di codec surface pada tiap OEM)

## 🗺 Roadmap

1. **Audio** (import, waveform, volume keyframe) + transisi antar scene
2. **Video layer** (Media3 decoder → GL texture) + speed ramp
3. Masking/crop, chroma key, motion blur, particle
4. Text styles (gradient stroke, shadow, animasi per kata)
5. Export faster-than-realtime (EGL) + export GIF
6. Template siap pakai (intro, lower-third, countdown)

## 🛠 Troubleshooting

| Masalah | Solusi |
|---|---|
| Build gagal: SDK tidak ketemu | Install platform 34 + build-tools 34.0.0, atau set `ANDROID_HOME` |
| Export error "Muxer gagal" | Coba resolusi 720p; cek ruang penyimpanan |
| Video hitam | Device tidak support lockCanvas pada input surface — lapor logcat |
| UI terasa berat saat 20+ layer | Turunkan jumlah layer glow/blur (efek paling berat) |

---

Dibuat dengan ⚡ — Kotlin · Canvas · MediaCodec · tanpa iklan.
