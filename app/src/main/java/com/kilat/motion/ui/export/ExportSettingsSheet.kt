package com.kilat.motion.ui.export

import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.kilat.motion.ui.components.KilatIcon
import com.kilat.motion.ui.components.KilatSheet
import com.kilat.motion.ui.components.KilatSlider
import com.kilat.motion.ui.theme.KColors
import com.kilat.motion.ui.theme.dp
import com.kilat.motion.ui.theme.dpf
import com.kilat.motion.ui.theme.kText
import com.kilat.motion.ui.theme.roundedRect
import kotlin.math.roundToInt

/**
 * Pengaturan export lengkap: resolusi, frame rate, dan bitrate (Mbps) yang bisa diatur manual.
 * Semua kontrol digambar sendiri (chip flat + slider custom), tanpa default Android UI.
 * State dipegang lokal per dialog, jadi aman dipanggil berkali-kali.
 */
object ExportSettingsSheet {

    data class Result(val longSide: Int, val fps: Int, val mbps: Int)

    private val resolutions = listOf("720p" to 1280, "1080p" to 1920, "1440p" to 2560, "4K" to 3840)
    private val fpsOptions = listOf(24, 30, 48, 60, 120)

    fun show(
        activity: Activity,
        projectW: Int,
        projectH: Int,
        durationSec: Float,
        onRender: (Result) -> Unit
    ) {
        val ctx: Activity = activity
        var resIdx = 1
        var fpsIdx = 1
        var mbps = 16

        val wrap = LinearLayout(ctx)
        wrap.orientation = LinearLayout.VERTICAL

        val bitrateValue = ctx.kText("", 15f, KColors.Text, bold = true, font = "monospace")
        val info = ctx.kText("", 11.5f, KColors.Muted)
        info.setLineSpacing(ctx.dpf(3f), 1.05f)

        fun dims(longSide: Int): Pair<Int, Int> {
            val aspect = projectW.toFloat() / projectH.toFloat()
            val w: Int
            val h: Int
            if (projectW >= projectH) {
                w = longSide
                h = (longSide / aspect).roundToInt()
            } else {
                h = longSide
                w = (longSide * aspect).roundToInt()
            }
            return (w / 2 * 2) to (h / 2 * 2)
        }

        fun refreshInfo() {
            val (w, h) = dims(resolutions[resIdx].second)
            val fps = fpsOptions[fpsIdx]
            val recommended = (w.toLong() * h * fps * 0.10 / 1_000_000).roundToInt().coerceIn(4, 80)
            val sizeMb = mbps * durationSec / 8f
            bitrateValue.text = "$mbps Mbps"
            info.text = "Output $w × $h · $fps fps\n" +
                "Perkiraan ukuran ±${"%.1f".format(sizeMb)} MB untuk ${"%.1f".format(durationSec)} detik\n" +
                "Rekomendasi untuk setelan ini: $recommended Mbps"
        }

        // ----- resolusi -----
        wrap.addView(ctx.kText("RESOLUSI", 9.5f, KColors.Dim, bold = true), lpTop(ctx, 0f))
        val resChips = ArrayList<TextView>()
        val resRow = LinearLayout(ctx)
        resRow.orientation = LinearLayout.HORIZONTAL
        fun paintRes() = paint(resChips, resIdx)
        resolutions.forEachIndexed { i, (label, _) ->
            val c = chip(ctx, label) { resIdx = i; paintRes(); refreshInfo() }
            resChips.add(c)
            resRow.addView(c, chipLp(ctx, i))
        }
        wrap.addView(resRow, lpTop(ctx, 6f))

        // ----- frame rate -----
        wrap.addView(ctx.kText("FRAME RATE", 9.5f, KColors.Dim, bold = true), lpTop(ctx, 14f))
        val fpsChips = ArrayList<TextView>()
        val fpsRow = LinearLayout(ctx)
        fpsRow.orientation = LinearLayout.HORIZONTAL
        fun paintFps() = paint(fpsChips, fpsIdx)
        fpsOptions.forEachIndexed { i, f ->
            val c = chip(ctx, "$f fps") { fpsIdx = i; paintFps(); refreshInfo() }
            fpsChips.add(c)
            fpsRow.addView(c, chipLp(ctx, i))
        }
        wrap.addView(fpsRow, lpTop(ctx, 6f))

        // ----- bitrate -----
        wrap.addView(ctx.kText("BITRATE (MBPS)", 9.5f, KColors.Dim, bold = true), lpTop(ctx, 14f))
        wrap.addView(bitrateValue, lpTop(ctx, 6f))
        val slider = KilatSlider(ctx)
        slider.max = 79
        slider.progress = mbps - 1
        slider.onProgress = { p -> mbps = p + 1; refreshInfo() }
        wrap.addView(slider, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ctx.dp(30f)))

        // ----- ringkasan -----
        wrap.addView(info, lpTop(ctx, 14f))

        paintRes()
        paintFps()
        refreshInfo()

        val scroll = ScrollView(ctx)
        scroll.isVerticalScrollBarEnabled = false
        scroll.addView(wrap)

        KilatSheet.show(
            ctx, "Pengaturan Export", scroll,
            positiveText = "Render", positiveIcon = KilatIcon.FILM,
            onPositive = { onRender(Result(resolutions[resIdx].second, fpsOptions[fpsIdx], mbps)) },
            negativeText = "Batal",
            maxHeightFraction = 0.78f
        )
    }

    private fun paint(chips: List<TextView>, selected: Int) {
        chips.forEachIndexed { i, c ->
            val on = i == selected
            c.background = roundedRect(if (on) KColors.Accent else KColors.Card, c.context.dpf(10f))
            c.setTextColor(if (on) KColors.AccentInk else KColors.Muted)
            c.typeface = if (on) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
    }

    private fun chip(ctx: Context, label: String, onClick: () -> Unit): TextView {
        val tv = TextView(ctx)
        tv.text = label
        tv.textSize = 12.5f
        tv.gravity = Gravity.CENTER
        tv.setPadding(ctx.dp(4f), ctx.dp(11f), ctx.dp(4f), ctx.dp(11f))
        tv.isClickable = true
        tv.setOnClickListener { onClick() }
        return tv
    }

    private fun chipLp(ctx: Context, index: Int): LinearLayout.LayoutParams {
        val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        if (index > 0) lp.marginStart = ctx.dp(6f)
        return lp
    }

    private fun lpTop(ctx: Context, dpTop: Float): LinearLayout.LayoutParams {
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = ctx.dp(dpTop)
        return lp
    }
}
