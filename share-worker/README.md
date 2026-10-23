# fluxa-share (Cloudflare Worker)

Backend share project Fluxa Motion. Domain: https://share.xyverse.my.id (worker `fluxa-share`). Data disimpan di KV (`PRESETS`).

- `POST /presets` butuh header `Authorization: Bearer <Google id_token>`. Audience token harus ada di var `GOOGLE_CLIENT_IDS`.
- `GET /presets/:id` publik, tanpa login.
- Batas ukuran 256 KB per project.

Deploy: upload `src/index.mjs` sebagai module, dengan binding KV `PRESETS` dan plain text `GOOGLE_CLIENT_IDS`.
Setelah client ID Android dibuat, isi `GOOGLE_CLIENT_IDS` dengan ID tersebut (dipisah koma).
