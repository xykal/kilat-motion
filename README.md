# ⚡ KILAT Motion — Android Native

Motion graphics editor **native Android** (Kotlin), alur kerja ala **Alight Motion** + UI gelap ala **CapCut**.
Dibangun **tanpa library eksternal** — hanya Android SDK + Kotlin stdlib. APK dibuild otomatis via **GitHub Actions**.

![status](https://img.shields.io/badge/build-passing-2ee6c0) ![minSdk](https://img.shields.io/badge/minSdk-29-blue) ![size](https://img.shields.io/badge/APK-2.3--2.9%20MB-lightgrey) ![tests](https://img.shields.io/badge/tests-26%20passing-2ee6c0)

---

## Baru di v1.1.0 — Onboarding, Homescreen, UI 100% Custom

- **Splash + Onboarding 4 halaman**: ilustrasi digambar manual pakai Canvas (logo mengorbit, mock timeline ber-playhead, tumpukan layer, export), indikator pil, navigasi tombol + usap. Tampil sekali di peluncuran pertama, bisa diulang dari tab Profil.
- **Homescreen + Bottom Navigation custom**: tab **Beranda | Project | Template | Profil** dengan tombol **Buat** (FAB gradient) mengambang di tengah.
  - Beranda: sapaan, kartu hero Buat Project + chip rasio, kartu "Lanjutkan" project terakhir, deretan template, ubin Mulai Cepat.
  - Project: pustaka project lokal (thumbnail asli dirender dari isi project, hapus per project).
  - Template: 5 template siap edit — Intro KILAT, Intro Bold, Quote Minimal 1:1, Promo Pop, Lower Third 16:9.
  - Profil: statistik, panduan, ulangi onboarding, tentang, hapus semua project.
- **Nol emoji, nol ikon bawaan**: 40 ikon digambar manual satu-satu pakai Canvas (`Icons.kt`) — tiap ikon bergaya beda (filled, outline rounded, duotone).
- **Nol komponen UI bawaan yang terlihat**: dialog diganti bottom sheet `KilatSheet`, dropdown `KilatDropdown`, slider `KilatSlider`, toast `KilatToast`, tombol `KilatButton`, progress `KilatProgressBar`, tombol keyframe wajik Canvas. (Keyboard sistem tetap dipakai untuk mengetik — itu memang ranah OS.)
- **Struktur kode dirombak rapi** (lihat pohon folder di bawah), editor ditulis ulang bersih, penyimpanan naik kelas jadi pustaka multi-project (`ProjectLibrary`).

---

## Fitur Editor (v1.0.0, tetap ada)

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

**Audio (baru)**
- Import **musik latar** dari galeri (mp3/m4a/wav/ogg/aac)
- **Waveform** digambar di timeline (decode native via MediaCodec)
- Drag bar musik = geser posisi, ujung kanan = atur durasi, dialog untuk volume & trim
- **Audio ikut ter-mux ke MP4** saat export (MediaExtractor + MediaMuxer, tanpa FFmpeg)

**Output & project**
- **Export MP4 (H.264)** via MediaCodec + MediaMuxer → galeri `Movies/KilatMotion` (720p/1080p, auto-fallback)
- **Export frame PNG** → `Pictures/KilatMotion`
- Autosave + load project JSON di internal storage, demo project bawaan

---

---

## Arsitektur & Struktur Folder

```
app/src/main/java/com/kilat/motion/
|-- core/
|   |-- model/Model.kt            -> Project, Layer, Keyframe, Music, konstanta
|   |-- animation/Anim.kt         -> easing, evaluasi keyframe, 8 preset
|   |-- render/Renderer.kt        -> satu renderer untuk preview + export + thumbnail
|   |-- audio/                    -> Waveform, AudioUtil (decode), MusicPlayer (preview)
|   `-- export/Exporter.kt        -> MediaCodec H.264 + mux audio + PNG
|-- data/
|   |-- ProjectStore.kt           -> serialisasi JSON project, impor gambar/audio
|   |-- ProjectLibrary.kt         -> pustaka multi-project (files/projects/*.json)
|   |-- Templates.kt              -> demo + 5 template + daftar rasio kanvas
|   `-- Prefs.kt                  -> status onboarding
`-- ui/
    |-- theme/KilatTheme.kt       -> warna, radius, tipografi, efek tekan
    |-- components/
    |   |-- Icons.kt              -> 40 ikon Canvas manual (IconPainter + IconView)
    |   |-- KilatViews.kt         -> Button, IconButton, Slider, Dropdown, Chip, Progress, KeyframeButton
    |   |-- KilatSheet.kt         -> bottom sheet (list/pesan/konfirmasi/progress)
    |   |-- KilatToast.kt         -> toast in-app custom
    |   |-- BottomNav.kt          -> navigasi bawah homescreen + FAB Buat
    |   `-- Thumbnails.kt         -> render thumbnail project/template
    |-- launcher/LauncherActivity.kt   -> splash -> onboarding / homescreen
    |-- onboarding/                    -> OnboardingActivity + ilustrasi Canvas
    |-- home/HomeActivity.kt           -> homescreen 4 tab
    `-- editor/                        -> EditorActivity, PreviewView, TimelineView, InspectorView
```

Prinsip: `core` tidak tahu UI, `data` tidak tahu tampilan, `ui` memakai keduanya.
Tes JVM meniru struktur yang sama di `app/src/test/java/com/kilat/motion/`.

## 📥 Download APK

| Build | Link |
|---|---|
| Release (signed, sideload-ready) | https://github.com/xykal/kilat-motion/releases/download/v1.1.0/app-release.apk |
| Debug | https://github.com/xykal/kilat-motion/releases/download/v1.1.0/app-debug.apk |

APK baru otomatis dibuat setiap push & setiap tag `v*` lewat GitHub Actions.

## 🚀 Build & install

### Lokal
Butuh **JDK 17** + **Android SDK (platform 34, build-tools 34.0.0)**.

```bash
./gradlew :app:assembleDebug      # APK debug
./gradlew :app:assembleRelease    # APK release (signed pakai debug key → bisa langsung sideload)
./gradlew :app:testDebugUnitTest  # 26 unit test (animasi, waveform, template, ikon)
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
| `MusicPlayer.kt` | Preview musik latar, sinkron dengan playhead |
| `AudioUtil.kt` | Decode audio → envelope waveform (MediaCodec) |
| `Waveform.kt` | Utilitas murni: envelope & mono-mix (di-unit-test) |
| `Demo.kt` | Demo project (glow + bintang + teks typing) |

**Kenapa tanpa library eksternal?** APK kecil (~2 MB), build cepat di CI, nol risiko dependency drift, dan semua bagian penting (render + encode) sepenuhnya di tangan kita.

---

## ⚠️ Batasan v1 (jujur)

- **Video layer belum ada** (fase berikutnya pakai Media3/ExoPlayer)
- **Export audio** menyalin track asli (volume export = volume file; slider volume hanya untuk preview)
- Satu musik latar per project (belum multi-track audio)
- **Pinch-to-zoom** belum; resize lewat handle pojok
- Export berjalan **real-time** (durasi export ≈ durasi project). Belum faster-than-realtime (butuh EGL + `eglPresentationTimeANDROID`)
- Preset Glitch memakai jitter keyframe `x`; belum ada RGB split per-channel
- Export butuh encoder H.264 yang tersedia di device; kalau 1080p gagal → otomatis retry 720p
- Build & unit test lolos; **wajib uji di device fisik** untuk validasi runtime (terutama lockCanvas di codec surface pada tiap OEM)

## 🗺 Roadmap

1. ~~Audio dasar~~ ✅ (musik latar + waveform + mux export) → lanjut: multi-track + volume keyframe
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
