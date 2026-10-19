package com.kilat.motion

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

object ProjectStore {

    private const val FILE_NAME = "kilat_project.json"

    private fun projectFile(ctx: Context): File = File(ctx.filesDir, FILE_NAME)

    fun imagesDir(ctx: Context): File {
        val d = File(ctx.filesDir, "images")
        if (!d.exists()) d.mkdirs()
        return d
    }

    // ---------------- SAVE ----------------
    fun save(ctx: Context, project: Project) {
        try {
            val root = JSONObject()
            root.put("v", 1)
            root.put("project", projectToJson(project))
            projectFile(ctx).writeText(root.toString())
        } catch (e: Throwable) {
            // autosave best-effort; jangan crash app
        }
    }

    fun projectToJson(p: Project): JSONObject {
        val o = JSONObject()
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
        o.put("strokeColor", Format.color(l.strokeColor))
        o.put("shape", l.shape)
        o.put("w", l.w.toDouble())
        o.put("h", l.h.toDouble())
        o.put("radius", l.radius.toDouble())
        o.put("fill", Format.color(l.fill))
        o.put("shapeStrokeW", l.shapeStrokeW.toDouble())
        o.put("shapeStrokeColor", Format.color(l.shapeStrokeColor))
        o.put("imgPath", l.imgPath)
        o.put("imgW", l.imgW.toDouble())
        o.put("imgH", l.imgH.toDouble())
        o.put("fxBlur", l.fxBlur.toDouble())
        o.put("fxBright", l.fxBright.toDouble())
        o.put("fxContrast", l.fxContrast.toDouble())
        o.put("fxSaturate", l.fxSaturate.toDouble())
        o.put("fxHue", l.fxHue.toDouble())
        o.put("fxGlow", l.fxGlow.toDouble())
        o.put("glowColor", Format.color(l.glowColor))
        o.put("blend", l.blend)
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
            l.strokeColor = Format.parseColor(lo.optString("strokeColor", "#FF000000"), 0xFF000000.toInt())
            l.shape = lo.optString("shape", "rect")
            l.w = lo.optDouble("w", 320.0).toFloat()
            l.h = lo.optDouble("h", 320.0).toFloat()
            l.radius = lo.optDouble("radius", 0.0).toFloat()
            l.fill = Format.parseColor(lo.optString("fill", "#FF2EE6C0"), 0xFF2EE6C0.toInt())
            l.shapeStrokeW = lo.optDouble("shapeStrokeW", 0.0).toFloat()
            l.shapeStrokeColor = Format.parseColor(lo.optString("shapeStrokeColor", "#FFFFFFFF"), 0xFFFFFFFF.toInt())
            l.imgPath = lo.optString("imgPath", "")
            l.imgW = lo.optDouble("imgW", 480.0).toFloat()
            l.imgH = lo.optDouble("imgH", 480.0).toFloat()
            l.fxBlur = lo.optDouble("fxBlur", 0.0).toFloat()
            l.fxBright = lo.optDouble("fxBright", 100.0).toFloat()
            l.fxContrast = lo.optDouble("fxContrast", 100.0).toFloat()
            l.fxSaturate = lo.optDouble("fxSaturate", 100.0).toFloat()
            l.fxHue = lo.optDouble("fxHue", 0.0).toFloat()
            l.fxGlow = lo.optDouble("fxGlow", 0.0).toFloat()
            l.glowColor = Format.parseColor(lo.optString("glowColor", "#FF2EE6C0"), 0xFF2EE6C0.toInt())
            l.blend = lo.optString("blend", "normal")
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
