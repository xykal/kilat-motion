#!/usr/bin/env python3
"""Verifikasi SHA-1 sertifikat signer APK (skema v2/v3) terhadap nilai yang diharapkan.

Pemakaian: python3 tools/verify_apk_signer.py <file.apk> <sha1-hex-tanpa-titik-dua>
Exit 0 jika cocok, 1 jika tidak cocok atau sertifikat tidak ditemukan.
Tidak butuh apksigner / build-tools, cukup Python 3 standar.
"""
import hashlib
import struct
import sys

APK_SIG_BLOCK_MAGIC = b"APK Sig Block 42"
PID_V2 = 0x7109871A
PID_V3 = 0xF05368C0


def signer_cert_der(apk_path):
    data = open(apk_path, "rb").read()
    mi = data.rfind(APK_SIG_BLOCK_MAGIC)
    if mi < 0:
        return None
    size = struct.unpack("<Q", data[mi - 8:mi])[0]
    start = mi + 16 - size - 8
    blen = struct.unpack("<Q", data[start:start + 8])[0]
    p = start + 8
    end = start + 8 + blen - 24
    while p < end:
        l = struct.unpack("<Q", data[p:p + 8])[0]
        pid = struct.unpack("<I", data[p + 8:p + 12])[0]
        val = data[p + 12:p + 8 + l]
        if pid in (PID_V2, PID_V3):
            # value: signers_len(4) | signer_len(4) | signed_data_len(4) | signed_data ...
            # Format awal signer v2 dan v3 sama. Untuk APK single-signer, ambil signer pertama.
            sdl = struct.unpack("<I", val[8:12])[0]
            sd = val[12:12 + sdl]
            dl = struct.unpack("<I", sd[0:4])[0]          # panjang blok digests
            cp = 4 + dl                                    # blok certificates
            cl = struct.unpack("<I", sd[cp + 4:cp + 8])[0] # panjang sertifikat pertama
            return sd[cp + 8:cp + 8 + cl]
        p += 8 + l
    return None


def main():
    if len(sys.argv) != 3:
        print(__doc__)
        return 2
    apk, expected = sys.argv[1], sys.argv[2].replace(":", "").lower()
    der = signer_cert_der(apk)
    if not der:
        print(f"GAGAL: sertifikat signer tidak ditemukan di {apk}")
        return 1
    got = hashlib.sha1(der).hexdigest()
    if got != expected:
        print(f"GAGAL: {apk} signer SHA-1 {got}, diharapkan {expected}")
        return 1
    print(f"OK: {apk} ditandatangani kunci {got}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
