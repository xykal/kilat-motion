# KILAT Motion — Android Native

Motion graphics editor **native Android** (Kotlin), alur kerja ala **Alight Motion** + UI gelap ala **CapCut**.
Dibangun **tanpa library eksternal** — hanya Android SDK + Kotlin stdlib. APK dibuild otomatis via **GitHub Actions**.

![status](https://img.shields.io/badge/build-passing-2ee6c0) ![minSdk](https://img.shields.io/badge/minSdk-29-blue) ![size](https://img.shields.io/badge/APK-2.4--3.1%20MB-lightgrey) ![tests](https://img.shields.io/badge/tests-43%20passing-2ee6c0)

---

## Baru di v1.5.0 — Color Picker Sendiri, Efek Bergambar, Layer Terkunci

- **Color picker custom penuh** (`KilatColorPicker`): bidang saturasi/value HSV
  yang bisa diseret, bilah hue pelangi, bilah alpha checkerboard, input hex +
  readout RGB, dan 16 palet cepat. Muncul dari tile **+** di semua baris warna
  Inspector dan dari tombol **Warna Custom** di Pengaturan Project. Semua warna
  tetap solid flat, tanpa gradient chrome UI.
- **Efek & filter sekarang bergambar**: preset animasi jadi tile ikon custom,
  dan 7 filter (Normal/Vivid/Hangat/Dingin/Mono/Sepia/Pudar) ditampilkan sebagai
  **thumbnail foto contoh yang benar-benar difilter** oleh `Renderer.filterFor`,
  bukan sekadar teks.
- **Toggle custom** (`KilatSwitch`): saklar ON/OFF solid ala CapCut menggantikan
  chip teks untuk Balik Mask dan garis Tertutup vektor.
- **Kunci layer**: tombol gembok di header timeline (ikon LOCK/UNLOCK baru,
  total 55 ikon). Layer terkunci tidak bisa diseret di kanvas, digeser/ditrim
  di timeline, apalagi keyframenya — dan ditandai ikon gembok di klipnya.
- **Salin/Tempel keyframe**: salin semua keyframe di playhead lalu tempel di
  waktu lain (easing ikut), untuk pola JJ berulang yang presisi.
- **Scrub ruler nempel (snap)**: menggeser playhead di ruler sekarang magnet ke
  tepi klip/keyframe/musik terdekat, jadi pas beat makin gampang.
- **Teks & bentuk makin lengkap**: slider **Spasi Huruf** dan **Spasi Baris**
  untuk tipografi, plus bentuk **Segi Banyak** dengan jumlah sisi 3–12 yang
  bisa diatur. Semua tersimpan di file project.

---

## v1.4.1 — Keyframe Ikut Klip, Nol Shadow Glass, Ikon v5

- **Keyframe sekarang patuh**: menggeser klip di timeline **menggeser semua
  keyframe-nya ikut** (dulu diam di waktu absolut). Diamond keyframe juga bisa
  **diseret** untuk memindahkan waktu grup keyframe (playhead mengikuti,
  diamond membesar saat diseret).
- **Navigasi keyframe**: tombol Keyframe Sebelumnya/Berikutnya + Hapus Keyframe
  di Playhead di kartu Kurva Gerakan.
- **Nol bayangan kaca**: semua `elevation` shadow di chrome UI dicabut (panel
  inspector, bottom sheet, toast, bottom nav, FAB, hero, splash) dan sisa
  warna alpha UI dijadikan solid 100% opaque.
- **Ikon v5**: Folder, Layers, Shape, Film digambar ulang dari render QA;
  Settings jadi gir 6 gigi gemuk yang terbaca jelas.

---

## v1.4.0 — Editor Pro: Kurva, Filter Kategori, Vektor, SVG, Masking & 3D

- **Perbaikan gestur penting**: memilih atau menggeser klip di timeline/kanvas
  **tidak lagi membuka bottom sheet sendiri**. Panel Properti sekarang dibuka
  secara sadar lewat tombol **Properti** atau **double-tap layer**, mengikuti
  alur CapCut/Alight Motion.
- **Bug render teks dibetulkan**: warna isi dan outline teks sekarang benar-benar
  diterapkan Renderer; glow juga bekerja untuk layer gambar.
- **Inspector dipecah per kategori**, tidak dicampur lagi:
  **Konten · Transform · Animasi · Efek Cahaya · Warna & Filter · Gaya · 3D**.
- **Transform super lengkap**: Posisi X/Y, Skala, **Skala X & Y terpisah**,
  Miring/Skew X/Y, Rotasi, Opasitas, Balik Kiri-Kanan/Atas-Bawah, **Tilt 3D X/Y**,
  semua bisa di-keyframe per properti.
- **Editor kurva gerakan**: grafik cubic-bezier dua handle yang bisa diseret,
  preset Pantul JJ/Halus/Nendang/Linear, terapkan ke keyframe di playhead atau
  semua keyframe layer.
- **Warna & filter**: preset Normal, Vivid, Hangat, Dingin, Mono, Sepia, Pudar;
  slider Brightness, Contrast, Saturasi, Hue. Efek Cahaya dipisah: Blur, Glow,
  warna glow, Blend.
- **Gaya lengkap**: Shadow (blur, geser X/Y, warna), Garis Tepi/Border,
  Border Radius bentuk, serta **Masking** Kotak Lembut/Lingkaran/Bintang +
  mode Balik Mask.
- **Layer baru**:
  - **Coret Vektor**: gambar bebas langsung di kanvas jadi layer vektor smooth.
  - **SVG**: impor path SVG (M/L/H/V/C/S/Q/T/Z; busur disederhanakan).
  - **Objek 3D**: Kubus, Piramida, Prisma, Oktahedron, dan **impor file .OBJ**
    (dirender software flat-shaded di Canvas; putar via Tilt X/Y + Rotasi).
- **Semua template lama dihapus**, diganti 6 template JJ baru:
  **JJ Velocity, JJ Lirik DJ, JJ Jedag Jedug 130, JJ Soft Spoken, JJ Tipis,
  JJ Kece Serem**.
- **Ikon v4 (53 ikon)**: digambar ulang untuk ikon yang lemah (gir Settings,
  Save, Trash, Film, Clock, Copy, Shape, Scissors) + ikon Pencil, Vector, Cube,
  Curve. Set ikon dirender ke contact sheet untuk QA visual sebelum rilis.

---

## v1.3.0 — Solid Flat Total, Timeline Bisa Digeser & Zoom, Template ala TikTok

- **UI solid flat 100%**: semua gradient dan warna transparan di UI dicabut —
  tombol aksen, ubin logo, FAB, badge, toast, bar timeline, sampai duotone
  ikon sekarang memakai warna **solid opaque**. Nol kesan kaca/glassmorphism.
- **Timeline bisa digeser & di-zoom**: geser area kosong untuk **pan**,
  **pinch dua jari** atau tombol -/+ untuk **zoom 100%–1000%**, tombol rasio
  untuk kembali fit. Playhead otomatis diikuti saat memutar, ada scrollbar
  mini, zona trim & titik keyframe lebih lebar, dan dialog musik sekarang
  membuka saat diketuk (bukan saat mulai drag).
- **Header timeline baru**: nama layer terpilih + aksi cepat (Pisah, Duplikat,
  Sembunyi/Tampil, Hapus) tepat di atas timeline.
- **Ikon v3 (49 ikon)**: poles detail — gir Settings jadi ring berjari-jari,
  gunting berbilah solid, Maju/Mundur chevron ganda, kepala not miring
  ala notasi asli, duotone jadi campuran warna opaque.
- **3 template viral baru** (total **11**): **Jedag Jedug (JJ)** — hentakan
  zoom + flash putih per beat di grid 120 BPM ala TikTok/Alight Motion,
  tinggal pasang musik; **Morph Shape** — bulat→kotak→bintang berubah mulus
  pakai blur + skala + rotasi; **RGB Glitch** — teks pecah merah-cyan +
  scanline + indikator REC.
- **Preset baru "JJ Zoom"**: satu ketuk bikin hentakan Jedag Jedug
  (zoom 128%→100% dalam 0,12 dtk + shake meluruh) di posisi playhead.

---

## v1.2.0 — Flat Borderless, Ikon Presisi, Editor Makin Lengkap

- **UI flat tanpa garis border**: semua kartu, tombol, field, sheet, dan navigasi
  digambar ulang tanpa stroke pinggir; pemisahan antar-permukaan murni dari
  nada warna tonal + elevasi lembut. Jauh dari kesan glassmorphism.
- **Set ikon v2 (48 ikon)**: ditulis ulang di grid 24 yang presisi — stroke
  utama konsisten 2.0 unit, koordinat snap 0.5 unit, detail mikro per ikon
  (gir 8 gigi, jarum jam bertanda, mata dengan highlight, dst). Tetap 100%
  digambar Canvas, nol emoji.
- **Editor makin lengkap**: **Undo/Redo 40 langkah**, **pisah layer tepat
  di playhead**, urutan layer **Maju/Mundur**, tombol **Tengahkan di Kanvas**,
  **magnet snap** (tengah kanvas + rotasi 15 derajat) yang bisa dimatikan,
  dan tombol lompat ke awal di transport.
- **Template lebih kompleks + 3 baru**: total **8 template**. Baru: Hitung
  Mundur (3-2-1 + cincin berdenyut), Kinetic Type (kata meluncur bergantian),
  Showcase Produk (kartu + badge harga + CTA). Template lama diperkaya
  (confetti di Promo Pop, badge LIVE di Lower Third, outro fade di Intro Bold,
  halo + tanda kutip di Quote Minimal).
- **Optimasi**: paint waveform timeline tidak lagi dialokasi per-frame,
  autosave selalu menyimpan state terkini, thumbnail template ter-cache.

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
|   |-- animation/Anim.kt         -> easing + cubic bezier, evaluasi keyframe, 9 preset
|   |-- render/Renderer.kt        -> satu renderer untuk preview + export + thumbnail (2D/vektor/SVG/3D)
|   |-- audio/                    -> Waveform, AudioUtil (decode), MusicPlayer (preview)
|   `-- export/Exporter.kt        -> MediaCodec H.264 + mux audio + PNG
|-- data/
|   |-- ProjectStore.kt           -> serialisasi JSON project, impor gambar/audio
|   |-- ProjectLibrary.kt         -> pustaka multi-project (files/projects/*.json)
|   |-- Templates.kt              -> demo internal + 6 template JJ + daftar rasio kanvas
|   `-- Prefs.kt                  -> status onboarding
`-- ui/
    |-- theme/KilatTheme.kt       -> warna, radius, tipografi, efek tekan
    |-- components/
    |   |-- Icons.kt              -> 53 ikon Canvas manual (IconPainter + IconView)
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

## Download APK

| Build | Link |
|---|---|
| Release (signed, sideload-ready) | https://github.com/xykal/kilat-motion/releases/download/v1.4.1/app-release.apk |
| Debug | https://github.com/xykal/kilat-motion/releases/download/v1.4.1/app-debug.apk |

APK baru otomatis dibuat setiap push & setiap tag `v*` lewat GitHub Actions.

## Build & install

### Lokal
Butuh **JDK 17** + **Android SDK (platform 34, build-tools 34.0.0)**.

```bash
./gradlew :app:assembleDebug      # APK debug
./gradlew :app:assembleRelease    # APK release (signed pakai debug key → bisa langsung sideload)
./gradlew :app:testDebugUnitTest  # 42 unit test (animasi/bezier, OBJ, SVG, waveform, template, ikon)
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

## Arsitektur

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

## ️ Batasan v1 (jujur)

- **Video layer belum ada** (fase berikutnya pakai Media3/ExoPlayer)
- **Export audio** menyalin track asli (volume export = volume file; slider volume hanya untuk preview)
- Satu musik latar per project (belum multi-track audio)
- **Pinch-to-zoom** belum; resize lewat handle pojok
- Export berjalan **real-time** (durasi export ≈ durasi project). Belum faster-than-realtime (butuh EGL + `eglPresentationTimeANDROID`)
- Preset Glitch memakai jitter keyframe `x`; belum ada RGB split per-channel
- Export butuh encoder H.264 yang tersedia di device; kalau 1080p gagal → otomatis retry 720p
- Build & unit test lolos; **wajib uji di device fisik** untuk validasi runtime (terutama lockCanvas di codec surface pada tiap OEM)

## Roadmap

1. ~~Audio dasar~~ (musik latar + waveform + mux export) → lanjut: multi-track + volume keyframe
2. **Video layer** (Media3 decoder → GL texture) + speed ramp
3. Masking/crop, chroma key, motion blur, particle
4. Text styles (gradient stroke, shadow, animasi per kata)
5. Export faster-than-realtime (EGL) + export GIF
6. Template siap pakai (intro, lower-third, countdown)

## Troubleshooting

| Masalah | Solusi |
|---|---|
| Build gagal: SDK tidak ketemu | Install platform 34 + build-tools 34.0.0, atau set `ANDROID_HOME` |
| Export error "Muxer gagal" | Coba resolusi 720p; cek ruang penyimpanan |
| Video hitam | Device tidak support lockCanvas pada input surface — lapor logcat |
| UI terasa berat saat 20+ layer | Turunkan jumlah layer glow/blur (efek paling berat) |

---

Dibuat dengan — Kotlin · Canvas · MediaCodec · tanpa iklan.
