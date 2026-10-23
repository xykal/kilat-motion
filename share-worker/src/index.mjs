// Fluxa Motion - share preset backend (Cloudflare Worker + KV)
// Binding KV : PRESETS
// Var        : GOOGLE_CLIENT_IDS (daftar client ID Google yang boleh menulis, dipisah koma)
//
// POST /presets        body: { "name": string, "data": object }
//                      header: Authorization: Bearer <Google ID token>
//                      -> 201 { "id": "...", "url": "..." }
// GET  /presets/:id    -> 200 { "id", "name", "data", "createdAt" }
// GET  /health         -> 200 { "ok": true }

const MAX_BYTES = 256 * 1024;
const ID_ALPHABET = "abcdefghijkmnpqrstuvwxyz23456789";

const json = (obj, status = 200) =>
  new Response(JSON.stringify(obj), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "access-control-allow-origin": "*",
      "access-control-allow-headers": "authorization, content-type",
      "access-control-allow-methods": "GET, POST, OPTIONS",
    },
  });

function randomId(len = 10) {
  const bytes = new Uint8Array(len);
  crypto.getRandomValues(bytes);
  let out = "";
  for (const b of bytes) out += ID_ALPHABET[b % ID_ALPHABET.length];
  return out;
}

// Verifikasi Google ID token lewat tokeninfo, lalu cek audience.
async function verifyGoogleIdToken(token, allowedAud) {
  const res = await fetch(
    "https://oauth2.googleapis.com/tokeninfo?id_token=" + encodeURIComponent(token)
  );
  if (!res.ok) return null;
  const info = await res.json();
  if (!allowedAud.includes(info.aud)) return null;
  if (info.iss !== "accounts.google.com" && info.iss !== "https://accounts.google.com") return null;
  return info;
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (request.method === "OPTIONS") return json({}, 204);

    if (url.pathname === "/health") return json({ ok: true });

    if (url.pathname === "/presets" && request.method === "POST") {
      const allowed = (env.GOOGLE_CLIENT_IDS || "").split(",").map((s) => s.trim()).filter(Boolean);
      if (allowed.length === 0) return json({ error: "server_not_configured" }, 503);

      const auth = request.headers.get("authorization") || "";
      const token = auth.startsWith("Bearer ") ? auth.slice(7).trim() : "";
      if (!token) return json({ error: "unauthorized" }, 401);
      const info = await verifyGoogleIdToken(token, allowed);
      if (!info) return json({ error: "unauthorized" }, 401);

      const raw = await request.text();
      if (raw.length > MAX_BYTES) return json({ error: "too_large" }, 413);
      let body;
      try {
        body = JSON.parse(raw);
      } catch {
        return json({ error: "bad_json" }, 400);
      }
      if (typeof body.name !== "string" || body.name.length === 0 || body.name.length > 80)
        return json({ error: "bad_name" }, 400);
      if (typeof body.data !== "object" || body.data === null) return json({ error: "bad_data" }, 400);

      const id = randomId();
      const record = {
        id,
        name: body.name,
        data: body.data,
        owner: info.sub,
        createdAt: new Date().toISOString(),
      };
      await env.PRESETS.put(id, JSON.stringify(record));
      return json({ id, url: `${url.origin}/presets/${id}` }, 201);
    }

    const m = url.pathname.match(/^\/presets\/([a-z0-9]{6,32})$/);
    if (m && request.method === "GET") {
      const value = await env.PRESETS.get(m[1]);
      if (!value) return json({ error: "not_found" }, 404);
      const rec = JSON.parse(value);
      return json({ id: rec.id, name: rec.name, data: rec.data, createdAt: rec.createdAt });
    }

    return json({ error: "not_found" }, 404);
  },
};
