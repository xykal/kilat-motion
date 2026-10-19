# 🚀 Push ke GitHub (username: xykal)

**Nama repo rekomendasi: `kilat-motion`** — singkat, sesuai brand, gampang di-ingat.
Alternatif: `kilat-motion-android`, `kilat-motion-studio`.

Repo lokal di folder `kilat-android/` **sudah siap** (sudah di-`git init` + commit), tinggal push.

---

## Opsi A — Gua (Arena) yang push
Kirim ke chat:
1. **username**: `xykal`
2. **token**: fine-grained PAT
3. **private atau public**

Cara bikin token (2 menit):
1. GitHub → **Settings** → **Developer settings** → **Personal access tokens** → **Fine-grained tokens** → **Generate new token**
2. **Repository access** → `Only select repositories` → pilih repo `kilat-motion` (kalau repo belum ada: pilih **All repositories** dulu, atau buat repo kosong manual)
3. **Permissions**:
   - `Contents` → **Read and write**  ← wajib
   - `Administration` → **Read and write** ← hanya kalau mau gua sekalian bikin repo-nya
4. **Expiration**: 7 hari (biar aman)
5. Generate → copy token (`github_pat_...`)

⚠️ Setelah selesai, **revoke token** di halaman yang sama. Gua nggak simpan token ke file manapun.

---

## Opsi B — Lo push sendiri (paling aman, 3 perintah)

1. Bikin repo kosong di GitHub: nama **`kilat-motion`**, **jangan** centang "Add README"
2. Di folder project:
```bash
cd kilat-android
git remote add origin https://github.com/xykal/kilat-motion.git
git push -u origin main
```
3. Buka **https://github.com/xykal/kilat-motion/actions** → workflow "Build APK" jalan otomatis
4. Ambil APK di **Artifacts** (`kilat-motion-debug` / `kilat-motion-release`)

Atau pakai script yang sudah gua siapkan (butuh token juga):
```bash
export GITHUB_TOKEN=github_pat_xxx
python3 tools/push_to_github.py --user xykal --repo kilat-motion --private
```

---

## Setelah push
- **Actions** → build debug + release (~2–3 menit)
- **Releases**: bikin tag biar APK otomatis nempel di release:
```bash
git tag v1.0.0 && git push origin v1.0.0
```
Workflow akan build + upload APK ke halaman Release.

## Troubleshooting CI
| Error | Solusi |
|---|---|
| `SDK location not found` | Workflow sudah set `sdkmanager`; pastikan `local.properties` tidak di-commit (sudah masuk .gitignore) |
| `Permission denied ./gradlew` | Workflow sudah `chmod +x gradlew`; kalau lokal: `chmod +x gradlew` |
| Build lambat pertama kali | Normal (download Gradle + AGP + Kotlin). Berikutnya pakai cache |
