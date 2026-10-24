#!/usr/bin/env python3
"""
Tes UI otomatis di emulator (dijalankan dari GitHub Actions).

Menjelajah layar utama Fluxa, menyimpan screenshot ke ui-shots/, dan mencatat
teks yang terlihat di tiap layar ke ui-shots/report.txt. Juga menangkap crash.
Tiap langkah dibungkus try/except supaya satu gagal tidak menghentikan sisanya.
"""
import glob
import os
import re
import subprocess
import time
import xml.etree.ElementTree as ET

PKG = "com.fluxa.motion"
OUT = "ui-shots"
os.makedirs(OUT, exist_ok=True)
REPORT = open(os.path.join(OUT, "report.txt"), "w", encoding="utf-8")


def log(msg=""):
    print(msg, flush=True)
    REPORT.write(msg + "\n")
    REPORT.flush()


def adb(*args, binary=False):
    r = subprocess.run(["adb", *args], capture_output=True)
    return r.stdout if binary else r.stdout.decode("utf-8", "replace")


def shot(name):
    data = adb("exec-out", "screencap", "-p", binary=True)
    with open(os.path.join(OUT, f"{name}.png"), "wb") as f:
        f.write(data)
    log(f"  [shot] {name}.png")


def dump():
    for _ in range(3):
        adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
        raw = adb("shell", "cat", "/sdcard/ui.xml")
        i = raw.find("<?xml")
        if i >= 0:
            try:
                return ET.fromstring(raw[i:])
            except ET.ParseError:
                pass
        time.sleep(1)
    return None


def all_nodes(root):
    return list(root.iter("node")) if root is not None else []


def center(bounds):
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds or "")
    if not m:
        return None
    x1, y1, x2, y2 = map(int, m.groups())
    return (x1 + x2) // 2, (y1 + y2) // 2


def visible_texts(root):
    out = []
    for n in all_nodes(root):
        t = (n.get("text") or "").strip()
        d = (n.get("content-desc") or "").strip()
        label = t or d
        if label and label not in out:
            out.append(label)
    return out


def tap(x, y):
    adb("shell", "input", "tap", str(x), str(y))
    time.sleep(1.2)


def tap_text(text, root=None, exact=True):
    root = root or dump()
    for n in all_nodes(root):
        t = (n.get("text") or "").strip()
        d = (n.get("content-desc") or "").strip()
        ok = (t == text or d == text) if exact else (text.lower() in (t + " " + d).lower())
        if ok:
            c = center(n.get("bounds"))
            if c:
                log(f"  [tap] '{text}' at {c}")
                tap(*c)
                return True
    return False


def swipe_up():
    adb("shell", "input", "swipe", "540", "1700", "540", "600", "350")
    time.sleep(1)


def clear_anr():
    # dialog "... isn't responding" dari launcher emulator bisa menutupi layar: pilih Wait
    if tap_text("Wait", exact=True):
        time.sleep(2)


def step(name, fn):
    log(f"== {name}")
    clear_anr()
    try:
        fn()
    except Exception as e:  # noqa: BLE001
        log(f"  [ERROR] {name}: {e!r}")


def snapshot(name):
    clear_anr()
    root = dump()
    shot(name)
    texts = visible_texts(root)
    log("  texts: " + " | ".join(texts[:80]))
    return root


def main():
    apks = sorted(glob.glob("app/build/outputs/apk/debug/*x86_64*.apk")) or sorted(
        glob.glob("app/build/outputs/apk/debug/*.apk")
    )
    log(f"APK: {apks}")
    if apks:
        log(adb("install", "-r", apks[0]).strip())

    adb("shell", "pm", "grant", PKG, "android.permission.POST_NOTIFICATIONS")
    adb("logcat", "-c")
    adb("shell", "monkey", "-p", PKG, "-c", "android.intent.category.LAUNCHER", "1")
    time.sleep(8)

    # Lewati onboarding / izin jika muncul
    def clear_intro():
        for _ in range(6):
            root = dump()
            texts = visible_texts(root)
            if any(t in texts for t in ["Lewati", "Skip"]):
                tap_text("Lewati" if "Lewati" in texts else "Skip", root)
            elif any(t in texts for t in ["Mulai", "Mulai Sekarang", "Lanjut", "Selesai", "Get started"]):
                for label in ["Mulai Sekarang", "Mulai", "Selesai", "Lanjut", "Get started"]:
                    if label in texts and tap_text(label, root):
                        break
            elif any(t in texts for t in ["Izinkan", "Allow", "While using the app"]):
                for label in ["Izinkan", "Allow", "While using the app"]:
                    if label in texts and tap_text(label, root):
                        break
            else:
                break
            time.sleep(1.5)

    step("intro", clear_intro)
    step("home", lambda: snapshot("01-home"))

    for label, shotname in [("Project", "02-project"), ("Template", "03-template"), ("Profil", "04-profil")]:
        def go(label=label, shotname=shotname):
            tap_text(label)
            time.sleep(1.5)
            snapshot(shotname)
        step(f"tab {label}", go)

    def profil_scroll():
        swipe_up()
        snapshot("05-profil-bawah")
        swipe_up()
        snapshot("06-profil-paling-bawah")
    step("profil scroll", profil_scroll)

    def open_create():
        # dialog ANR launcher emulator bisa menutupi layar; pilih Wait dulu
        if tap_text("Wait", exact=True):
            time.sleep(2)
        # FAB + di tengah bawah (tidak punya teks) membuka sheet Navigasi
        size = adb("shell", "wm", "size")
        m = re.search(r"(\d+)x(\d+)", size)
        w, h = (int(m.group(1)), int(m.group(2))) if m else (1080, 2400)
        # pusat FAB berada ~89% tinggi layar (bar kapsul melayang di bawah)
        tap(w // 2, int(h * 0.893))
        time.sleep(1.5)
        # pilih "Buat project baru" di sheet navigasi
        if not tap_text("Buat project baru"):
            raise RuntimeError("item 'Buat project baru' tidak ditemukan di sheet navigasi")
        time.sleep(1.5)
        snapshot("07-sheet-buat")
    step("create sheet", open_create)

    def open_editor():
        root = dump()
        texts = visible_texts(root)
        ratio = [t for t in texts if "(" in t and " x " in t]
        if not ratio:
            raise RuntimeError("item rasio tidak ditemukan di sheet buat project")
        tap_text(ratio[0], root)
        time.sleep(4)
        # validasi: harus benar-benar di editor (ada tombol Musik/Tambah atau timecode)
        ed = visible_texts(dump())
        if not any(t in ed for t in ("Musik", "Tambah")) and not any(" / " in t for t in ed):
            raise RuntimeError("editor belum terbuka setelah pilih rasio")
        snapshot("08-editor")
    step("open editor", open_editor)

    def editor_panels():
        for label, name in [("Musik", "09-editor-musik"), ("Tambah", "10-editor-tambah")]:
            if tap_text(label):
                time.sleep(1.5)
                snapshot(name)
                # tutup dialog/sheet: tombol Batal (dialog musik) atau Tutup (sheet), baru BACK
                if not (tap_text("Batal", exact=True) or tap_text("Tutup", exact=True)):
                    adb("shell", "input", "keyevent", "KEYCODE_BACK")
                time.sleep(1.5)
    step("editor panels", editor_panels)

    def effect_catalog():
        # tambah teks dari bar bawah, buka Properti, cari bagian Stack efek lalu ambil katalog kategori
        if not tap_text("Teks"):
            raise RuntimeError("tombol Teks di bar bawah tidak ditemukan")
        # addTextLayer() langsung membuka panel Inspector; jangan ketuk bar bawah lagi
        # (ketukan di y bawah mengenai isi panel, misalnya tombol impor font).
        time.sleep(2.5)
        # stack efek ada di tab "Efek" pada panel Inspector
        tap_text("Efek", exact=True)
        time.sleep(1.5)
        for _ in range(10):
            texts = visible_texts(dump())
            if "Recent" in texts or "Recent files" in texts:
                adb("shell", "input", "keyevent", "KEYCODE_BACK")
                time.sleep(1.5)
                continue
            if "Stack efek" in texts:
                break
            swipe_up()
        time.sleep(1)
        snapshot("13-efek-warna")
        if tap_text("Gerak"):
            time.sleep(1)
            snapshot("14-efek-gerak")
    step("katalog efek", effect_catalog)

    # status proses dan crash
    def template_velocity():
        tap_text("Beranda")
        time.sleep(1.5)
        tap_text("JJ Velocity")
        time.sleep(4)
        snapshot("11-template-velocity")
    step("template velocity", template_velocity)

    def dismiss_anr():
        # dialog "... isn't responding" dari launcher emulator menutupi layar; pilih Wait
        if tap_text("Wait", exact=True):
            time.sleep(2)

    def template_reportase():
        dismiss_anr()
        # tutup editor JJ Velocity yang masih terbuka dari langkah sebelumnya
        for _ in range(2):
            if not tap_text("Kembali", exact=True):
                break
            time.sleep(1.5)
        tap_text("Template")
        time.sleep(1.5)
        for _ in range(8):
            if tap_text("JJ Reportase", exact=True):
                break
            swipe_up()
        time.sleep(4)
        snapshot("12-template-reportase")
    step("template reportase", template_reportase)

    def prop_rows():
        # pilih layer "Kredit" (baris paling atas, punya keyframe) lewat kolom nama timeline,
        # lalu baris per properti harus muncul di bawahnya
        tap(150, 1779)
        time.sleep(1.5)
        snapshot("15-timeline-props")
    step("baris properti", prop_rows)

    def health():
        alive = adb("shell", "pidof", PKG).strip()
        log(f"  pid: {alive or 'MATI'}")
        lines = adb("logcat", "-d").splitlines()
        crashes = [l for l in lines if "FATAL EXCEPTION" in l or "ANR in" in l]
        log(f"  crash/ANR: {len(crashes)}")
        for c in crashes[:10]:
            log("    " + c)
        thumb = [l for l in lines if "FluxaThumb" in l]
        log(f"  log thumbnail (gagal + kecerahan): {len(thumb)}")
        for t in thumb[:6]:
            log("    " + t)
        # stack trace pertama sesudah baris thumbnail
        diag = [l for l in lines if "FluxaDiag" in l]
        log(f"  diagnosis layer: {len(diag)}")
        for d in diag:
            log("    " + d)
        for i, l in enumerate(lines):
            if "FluxaThumb" in l:
                for x in lines[i:i + 12]:
                    log("      " + x)
                break
    step("health", health)

    REPORT.close()


if __name__ == "__main__":
    main()
