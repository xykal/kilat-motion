package com.kilat.motion.ui.theme

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.TextView
import kotlin.math.roundToInt

/**
 * Design system KILAT Motion.
 * Semua warna, radius, dan tipografi UI di satu tempat supaya konsisten
 * di onboarding, homescreen, dan editor.
 */
object KColors {
    val Bg = 0xFF0A0B0F.toInt()
    val BgTop = 0xFF0D0F15.toInt()
    val Panel = 0xFF12151F.toInt()
    val Card = 0xFF171B27.toInt()
    val CardHi = 0xFF212738.toInt()
    val Field = 0xFF11141D.toInt()
    val Line = 0xFF222837.toInt()
    val Text = 0xFFEEF2F8.toInt()
    val Muted = 0xFF98A3B8.toInt()
    val Dim = 0xFF687389.toInt()
    val Faint = 0xFF4D5871.toInt()

    val Accent = 0xFF2EE6C0.toInt()      // teal kilat (utama)
    val AccentInk = 0xFF06231D.toInt()   // teks di atas accent
    val Violet = 0xFF8B7CFF.toInt()
    val Sky = 0xFF59E3FF.toInt()
    val Amber = 0xFFFFD166.toInt()
    val Rose = 0xFFFF5C7A.toInt()
    val Orange = 0xFFFF8A3D.toInt()
    val Green = 0xFF22C55E.toInt()
    val Danger = 0xFFFF5C7A.toInt()
    val DangerInk = 0xFF2B0F1A.toInt()
}

fun Context.dp(v: Float): Int = (v * resources.displayMetrics.density).roundToInt()
fun View.dp(v: Float): Int = (v * resources.displayMetrics.density).roundToInt()
fun Context.dpf(v: Float): Float = v * resources.displayMetrics.density
fun View.dpf(v: Float): Float = v * resources.displayMetrics.density

/** Background kotak rounded custom (pengganti style bawaan Android). */
fun roundedRect(fill: Int, radiusPx: Float, stroke: Int? = null, strokePx: Int = 1): GradientDrawable {
    val d = GradientDrawable()
    d.cornerRadius = radiusPx
    d.setColor(fill)
    if (stroke != null) d.setStroke(strokePx, stroke)
    return d
}

/** Background rounded hanya di sudut atas (bottom sheet). */
fun roundedTop(fill: Int, radiusPx: Float, stroke: Int? = null, strokePx: Int = 1): GradientDrawable {
    val d = GradientDrawable()
    d.cornerRadii = floatArrayOf(radiusPx, radiusPx, radiusPx, radiusPx, 0f, 0f, 0f, 0f)
    d.setColor(fill)
    if (stroke != null) d.setStroke(strokePx, stroke)
    return d
}

fun ovalBg(fill: Int, stroke: Int? = null, strokePx: Int = 1): GradientDrawable {
    val d = GradientDrawable()
    d.shape = GradientDrawable.OVAL
    d.setColor(fill)
    if (stroke != null) d.setStroke(strokePx, stroke)
    return d
}

/** Aksen solid rata khas KILAT (tanpa gradient, tanpa transparansi). */
fun kilatSolid(radiusPx: Float): GradientDrawable {
    val d = GradientDrawable()
    d.cornerRadius = radiusPx
    d.setColor(KColors.Accent)
    return d
}

fun kilatSolidOval(): GradientDrawable {
    val d = GradientDrawable()
    d.shape = GradientDrawable.OVAL
    d.setColor(KColors.Accent)
    return d
}

/** TextView polos dengan gaya KILAT (tanpa style/material bawaan). */
fun Context.kText(
    text: String,
    sizeSp: Float,
    color: Int = KColors.Text,
    bold: Boolean = false,
    font: String = "sans-serif"
): TextView {
    val tv = TextView(this)
    tv.text = text
    tv.setTextColor(color)
    tv.textSize = sizeSp
    tv.typeface = Typeface.create(font, if (bold) Typeface.BOLD else Typeface.NORMAL)
    tv.includeFontPadding = false
    return tv
}

/** Label seksi kecil huruf kapital berjarak, ala header panel KILAT. */
fun Context.kSectionLabel(text: String): TextView {
    val tv = kText(text.uppercase(), 9.5f, KColors.Dim, bold = true)
    tv.letterSpacing = 0.14f
    return tv
}

/** Efek tekan: skala turun dikit + alpha, terasa "hidup" tanpa ripple bawaan. */
fun View.pressEffect(scaleTo: Float = 0.96f) {
    setOnTouchListener { v, e ->
        when (e.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN ->
                v.animate().scaleX(scaleTo).scaleY(scaleTo).setDuration(70).start()
            android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL ->
                v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
        }
        false
    }
}
