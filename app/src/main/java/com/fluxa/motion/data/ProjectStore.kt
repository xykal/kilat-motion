package com.fluxa.motion.data

import com.fluxa.motion.core.model.*

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

object ProjectStore {

    private const val FILE_NAME = "fluxa_project.json"

    private fun projectFile(ctx: Context): File = File(ctx.filesDir, FILE_NAME)

    fun imagesDir(ctx: Context): File {
        val d = File(ctx.filesDir, "images")
        if (!d.exists()) d.mkdirs()
        return d
    }

    // ---------------- SAVE ----------------
    /** Bungkus project jadi string file (.json) — dipakai autosave & ProjectLibrary. */
    fun encode(project: Project): String {
        val root = JSONObject()
        root.put("v", 2)
        root.put("project", projectToJson(project))
        return root.toString()
    }

    fun decode(text: String): Project? {
        return try {
            val root = JSONObject(text)
            val pj = root.optJSONObject("project") ?: return null
            jsonToProject(pj)
        } catch (e: Throwable) {
            null
        }
    }

    fun clearLegacy(ctx: Context) {
        try {
            projectFile(ctx).delete()
        } catch (e: Throwable) {
            // abaikan
        }
    }

    fun save(ctx: Context, project: Project) {
        try {
            projectFile(ctx).writeText(encode(project))
        } catch (e: Throwable) {
            // autosave best-effort; jangan crash app
        }
    }

    fun projectToJson(p: Project): JSONObject {
        val o = JSONObject()
        o.put("id", p.id)
        o.put("updatedAt", p.updatedAt)
        o.put("name", p.name)
        o.put("width", p.width)
        o.put("height", p.height)
        o.put("bg", Format.color(p.bg))
        o.put("duration", p.duration.toDouble())
        o.put("fps", p.fps)
        val arr = JSONArray()
        for (l in p.layers) arr.put(layerToJson(l))
        o.put("layers", arr)
        p.music?.let { m ->
            val mo = JSONObject()
            mo.put("path", m.path)
            mo.put("name", m.name)
            mo.put("start", m.start.toDouble())
            mo.put("dur", m.dur.toDouble())
            mo.put("offset", m.offset.toDouble())
            mo.put("volume", m.volume.toDouble())
            m.wave?.let { w ->
                val wa = JSONArray()
                for (v in w) wa.put(v.toDouble())
                mo.put("wave", wa)
            }
            o.put("music", mo)
        }
        return o
    }

    private fun layerToJson(l: Layer): JSONObject {
        val o = JSONObject()
        o.put("id", l.id)
        o.put("name", l.name)
        o.put("kind", l.kind)
        o.put("start", l.start.toDouble())
        o.put("dur", l.dur.toDouble())
        o.put("hidden", l.hidden)
        o.put("locked", l.locked)
        o.put("x", l.x.toDouble())
        o.put("y", l.y.toDouble())
        o.put("scale", l.scale.toDouble())
        o.put("rot", l.rot.toDouble())
        o.put("alpha", l.alpha.toDouble())
        o.put("typing", l.typing.toDouble())
        o.put("text", l.text)
        o.put("fontKey", l.fontKey)
        o.put("textSize", l.textSize.toDouble())
        o.put("bold", l.bold)
        o.put("align", l.align)
        o.put("color", Format.color(l.color))
        o.put("strokeW", l.strokeW.toDouble())
        o.put("letterSpacing", l.letterSpacing.toDouble())
        o.put("lineSpacing", l.lineSpacing.toDouble())
        o.put("strokeColor", Format.color(l.strokeColor))
        o.put("shape", l.shape)
        o.put("w", l.w.toDouble())
        o.put("h", l.h.toDouble())
        o.put("radius", l.radius.toDouble())
        o.put("fill", Format.color(l.fill))
        o.put("shapeSides", l.shapeSides)
        o.put("shapeStrokeW", l.shapeStrokeW.toDouble())
        o.put("shapeStrokeColor", Format.color(l.shapeStrokeColor))
        o.put("imgPath", l.imgPath)
        o.put("imgW", l.imgW.toDouble())
        o.put("imgH", l.imgH.toDouble())
        o.put("fxBlur", l.fxBlur.toDouble())
        o.put("fxGlass", l.fxGlass.toDouble())
        o.put("glassTint", l.glassTint.toDouble())
        o.put("glassBorder", l.glassBorder.toDouble())
        o.put("blurType", l.blurType)
        o.put("blurAmount", l.blurAmount.toDouble())
        o.put("fxBright", l.fxBright.toDouble())
        o.put("fxContrast", l.fxContrast.toDouble())
        o.put("fxSaturate", l.fxSaturate.toDouble())
        o.put("fxHue", l.fxHue.toDouble())
        o.put("fxGlow", l.fxGlow.toDouble())
        o.put("glowColor", Format.color(l.glowColor))
        o.put("blend", l.blend)
        val fxArr = JSONArray()
        for (e in l.effects) {
            val eo = JSONObject()
            eo.put("type", e.type)
            eo.put("amount", e.amount.toDouble())
            eo.put("size", e.size.toDouble())
            eo.put("color", Format.color(e.color))
            eo.put("enabled", e.enabled)
            fxArr.put(eo)
        }
        o.put("effects", fxArr)
        o.put("scaleX", l.scaleX.toDouble())
        o.put("scaleY", l.scaleY.toDouble())
        o.put("skewX", l.skewX.toDouble())
        o.put("skewY", l.skewY.toDouble())
        o.put("rotX", l.rotX.toDouble())
        o.put("rotY", l.rotY.toDouble())
        o.put("shadowBlur", l.shadowBlur.toDouble())
        o.put("shadowDx", l.shadowDx.toDouble())
        o.put("shadowDy", l.shadowDy.toDouble())
        o.put("shadowColor", Format.color(l.shadowColor))
        o.put("maskShape", l.maskShape)
        o.put("maskInvert", l.maskInvert)
        o.put("bezX1", l.bezX1.toDouble())
        o.put("bezY1", l.bezY1.toDouble())
        o.put("bezX2", l.bezX2.toDouble())
        o.put("bezY2", l.bezY2.toDouble())
        o.put("vecPoints", l.vecPoints)
        o.put("vecStrokeW", l.vecStrokeW.toDouble())
        o.put("vecClosed", l.vecClosed)
        o.put("svgData", l.svgData)
        o.put("svgVbX", l.svgVbX.toDouble())
        o.put("svgVbY", l.svgVbY.toDouble())
        o.put("svgVbW", l.svgVbW.toDouble())
        o.put("svgVbH", l.svgVbH.toDouble())
        o.put("objKind", l.objKind)
        o.put("objPath", l.objPath)
        val ch = JSONObject()
        for ((key, channel) in l.channels) {
            val arr = JSONArray()
            for (k in channel.keys) {
                val ko = JSONObject()
                ko.put("t", k.t.toDouble())
                ko.put("v", k.v.toDouble())
                ko.put("e", k.ease)
                arr.put(ko)
            }
            ch.put(key, arr)
        }
        o.put("channels", ch)
        return o
    }

    // ---------------- LOAD ----------------
    fun hasSave(ctx: Context): Boolean = projectFile(ctx).exists()

    fun load(ctx: Context): Project? {
        val f = projectFile(ctx)
        if (!f.exists()) return null
        return try {
            val root = JSONObject(f.readText())
            val pj = root.optJSONObject("project") ?: return null
            jsonToProject(pj)
        } catch (e: Throwable) {
            null
        }
    }

    fun jsonToProject(pj: JSONObject): Project {
        val p = Project()
        p.name = pj.optString("name", "Project")
        p.width = pj.optInt("width", 1080)
        p.height = pj.optInt("height", 1920)
        p.bg = Format.parseColor(pj.optString("bg", "#FF05070C"), 0xFF05070C.toInt())
        p.duration = pj.optDouble("duration", 10.0).toFloat()
        p.fps = pj.optInt("fps", 30)
        val arr = pj.optJSONArray("layers") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val lo = arr.optJSONObject(i) ?: continue
            val l = Layer()
            l.id = lo.optString("id", UUID.randomUUID().toString().substring(0, 8))
            l.name = lo.optString("name", "Layer")
            l.kind = lo.optString("kind", KIND_TEXT)
            l.start = lo.optDouble("start", 0.0).toFloat()
            l.dur = lo.optDouble("dur", 4.0).toFloat()
            l.hidden = lo.optBoolean("hidden", false)
            l.locked = lo.optBoolean("locked", false)
            l.x = lo.optDouble("x", 540.0).toFloat()
            l.y = lo.optDouble("y", 960.0).toFloat()
            l.scale = lo.optDouble("scale", 1.0).toFloat()
            l.rot = lo.optDouble("rot", 0.0).toFloat()
            l.alpha = lo.optDouble("alpha", 1.0).toFloat()
            l.typing = lo.optDouble("typing", 1.0).toFloat()
            l.text = lo.optString("text", "")
            l.fontKey = lo.optString("fontKey", "sans_black")
            l.textSize = lo.optDouble("textSize", 120.0).toFloat()
            l.bold = lo.optBoolean("bold", true)
            l.align = lo.optString("align", "center")
            l.color = Format.parseColor(lo.optString("color", "#FFFFFFFF"), 0xFFFFFFFF.toInt())
            l.strokeW = lo.optDouble("strokeW", 0.0).toFloat()
            l.letterSpacing = lo.optDouble("letterSpacing", 0.0).toFloat()
            l.lineSpacing = lo.optDouble("lineSpacing", 1.24).toFloat()
            l.strokeColor = Format.parseColor(lo.optString("strokeColor", "#FF000000"), 0xFF000000.toInt())
            l.shape = lo.optString("shape", "rect")
            l.w = lo.optDouble("w", 320.0).toFloat()
            l.h = lo.optDouble("h", 320.0).toFloat()
            l.radius = lo.optDouble("radius", 0.0).toFloat()
            l.fill = Format.parseColor(lo.optString("fill", "#FF2EE6C0"), 0xFF2EE6C0.toInt())
            l.shapeSides = lo.optInt("shapeSides", 6)
            l.shapeStrokeW = lo.optDouble("shapeStrokeW", 0.0).toFloat()
            l.shapeStrokeColor = Format.parseColor(lo.optString("shapeStrokeColor", "#FFFFFFFF"), 0xFFFFFFFF.toInt())
            l.imgPath = lo.optString("imgPath", "")
            l.imgW = lo.optDouble("imgW", 480.0).toFloat()
            l.imgH = lo.optDouble("imgH", 480.0).toFloat()
            l.fxBlur = lo.optDouble("fxBlur", 0.0).toFloat()
            l.fxGlass = lo.optDouble("fxGlass", 0.0).toFloat()
            l.glassTint = lo.optDouble("glassTint", 0.0).toFloat()
            l.glassBorder = lo.optDouble("glassBorder", 0.0).toFloat()
            l.blurType = lo.optString("blurType", "none")
            l.blurAmount = lo.optDouble("blurAmount", 0.0).toFloat()
            l.fxBright = lo.optDouble("fxBright", 100.0).toFloat()
            l.fxContrast = lo.optDouble("fxContrast", 100.0).toFloat()
            l.fxSaturate = lo.optDouble("fxSaturate", 100.0).toFloat()
            l.fxHue = lo.optDouble("fxHue", 0.0).toFloat()
            l.fxGlow = lo.optDouble("fxGlow", 0.0).toFloat()
            l.glowColor = Format.parseColor(lo.optString("glowColor", "#FF2EE6C0"), 0xFF2EE6C0.toInt())
            l.blend = lo.optString("blend", "normal")
            val efArr = lo.optJSONArray("effects")
            if (efArr != null) {
                for (j in 0 until efArr.length()) {
                    val eo = efArr.optJSONObject(j) ?: continue
                    val type = eo.optString("type", "")
                    if (type !in EFFECT_TYPES) continue
                    l.effects.add(
                        LayerEffect(
                            type = type,
                            amount = eo.optDouble("amount", 0.5).toFloat().coerceIn(0f, 1f),
                            size = eo.optDouble("size", 0.5).toFloat().coerceIn(0f, 1f),
                            color = Format.parseColor(eo.optString("color", "#FF000000"), 0xFF000000.toInt()),
                            enabled = eo.optBoolean("enabled", true)
                        )
                    )
                }
            }
            l.scaleX = lo.optDouble("scaleX", 1.0).toFloat()
            l.scaleY = lo.optDouble("scaleY", 1.0).toFloat()
            l.skewX = lo.optDouble("skewX", 0.0).toFloat()
            l.skewY = lo.optDouble("skewY", 0.0).toFloat()
            l.rotX = lo.optDouble("rotX", 0.0).toFloat()
            l.rotY = lo.optDouble("rotY", 0.0).toFloat()
            l.shadowBlur = lo.optDouble("shadowBlur", 0.0).toFloat()
            l.shadowDx = lo.optDouble("shadowDx", 0.0).toFloat()
            l.shadowDy = lo.optDouble("shadowDy", 12.0).toFloat()
            l.shadowColor = Format.parseColor(lo.optString("shadowColor", "#FF000000"), 0xFF000000.toInt())
            l.maskShape = lo.optString("maskShape", "none")
            l.maskInvert = lo.optBoolean("maskInvert", false)
            l.bezX1 = lo.optDouble("bezX1", 0.3).toFloat()
            l.bezY1 = lo.optDouble("bezY1", 1.4).toFloat()
            l.bezX2 = lo.optDouble("bezX2", 0.6).toFloat()
            l.bezY2 = lo.optDouble("bezY2", 1.0).toFloat()
            l.vecPoints = lo.optString("vecPoints", "")
            l.vecStrokeW = lo.optDouble("vecStrokeW", 14.0).toFloat()
            l.vecClosed = lo.optBoolean("vecClosed", false)
            l.svgData = lo.optString("svgData", "")
            l.svgVbX = lo.optDouble("svgVbX", 0.0).toFloat()
            l.svgVbY = lo.optDouble("svgVbY", 0.0).toFloat()
            l.svgVbW = lo.optDouble("svgVbW", 100.0).toFloat()
            l.svgVbH = lo.optDouble("svgVbH", 100.0).toFloat()
            l.objKind = lo.optString("objKind", "cube")
            l.objPath = lo.optString("objPath", "")
            val chObj = lo.optJSONObject("channels")
            if (chObj != null) {
                val keys = chObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val karr = chObj.optJSONArray(key) ?: continue
                    val channel = Channel()
                    for (j in 0 until karr.length()) {
                        val ko = karr.optJSONObject(j) ?: continue
                        channel.keys.add(
                            Keyframe(
                                ko.optDouble("t", 0.0).toFloat(),
                                ko.optDouble("v", 0.0).toFloat(),
                                ko.optString("e", "easeOut")
                            )
                        )
                    }
                    channel.keys.sortBy { it.t }
                    if (channel.keys.isNotEmpty()) l.channels[key] = channel
                }
            }
            p.layers.add(l)
        }
        val mo = pj.optJSONObject("music")
        if (mo != null) {
            val m = Music()
            m.path = mo.optString("path", "")
            m.name = mo.optString("name", "Musik")
            m.start = mo.optDouble("start", 0.0).toFloat()
            m.dur = mo.optDouble("dur", p.duration.toDouble()).toFloat()
            m.offset = mo.optDouble("offset", 0.0).toFloat()
            m.volume = mo.optDouble("volume", 0.8).toFloat()
            val wa = mo.optJSONArray("wave")
            if (wa != null && wa.length() > 0) {
                val w = FloatArray(wa.length())
                for (i in 0 until wa.length()) w[i] = wa.optDouble(i, 0.0).toFloat()
                m.wave = w
            }
            if (m.path.isNotEmpty() && File(m.path).exists()) p.music = m
        }
        return p
    }

    // ---------------- AUDIO IMPORT ----------------
    fun importAudio(ctx: Context, uri: Uri, displayName: String?): String? {
        return try {
            val safe = (displayName ?: "audio").replace(Regex("[^A-Za-z0-9._-]"), "_").take(28)
            val name = "aud_" + UUID.randomUUID().toString().substring(0, 6) + "_" + safe
            val dest = File(imagesDir(ctx).parentFile, "audio").let { d ->
                if (!d.exists()) d.mkdirs()
                File(d, name)
            }
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            dest.absolutePath
        } catch (e: Throwable) {
            null
        }
    }

    // ---------------- IMAGE IMPORT ----------------
    /** Menyalin video ke penyimpanan app. Frame dibaca lewat VideoFrames. */
    /**
     * Salin video ke penyimpanan app. Dengan [key] (id + ukuran + tanggal ubah dari MediaStore),
     * video yang sama tidak disalin ulang. Salinan ditulis ke ".part" dulu lalu di-rename,
     * jadi file setengah jadi tidak pernah dipakai. [onProgress] menerima 0..1 bila ukuran diketahui.
     */
    fun importVideo(ctx: Context, uri: Uri, key: String? = null, onProgress: ((Float) -> Unit)? = null): String? {
        return try {
            val dir = File(ctx.filesDir, "videos").apply { if (!exists()) mkdirs() }
            val safe = key?.replace(Regex("[^A-Za-z0-9_-]"), "")?.takeIf { it.isNotEmpty() }
            val name = if (safe != null) "vid_$safe.mp4"
            else "vid_" + UUID.randomUUID().toString().substring(0, 8) + ".mp4"
            val dest = File(dir, name)
            if (dest.exists() && dest.length() > 0L) {
                onProgress?.invoke(1f)
                return dest.absolutePath
            }
            val total = ctx.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
            val tmp = File(dir, "$name.part")
            val input = ctx.contentResolver.openInputStream(uri) ?: return null
            input.use { src ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var copied = 0L
                    while (true) {
                        val n = src.read(buf)
                        if (n <= 0) break
                        out.write(buf, 0, n)
                        copied += n
                        if (total > 0) onProgress?.invoke((copied.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            if (!tmp.renameTo(dest)) {
                tmp.delete()
                return null
            }
            dest.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    fun importImage(ctx: Context, uri: Uri): String? {
        return try {
            val name = "img_" + UUID.randomUUID().toString().substring(0, 8) + ".jpg"
            val dest = File(imagesDir(ctx), name)
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            dest.absolutePath
        } catch (e: Throwable) {
            null
        }
    }
}
