package com.fluxa.motion.ui.components

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.fluxa.motion.ui.theme.FluxaColors
import com.fluxa.motion.ui.theme.dp
import com.fluxa.motion.ui.theme.roundedRect

/**
 * Tab bar kategori gaya CapCut / Figma: ikon di atas, label di bawah, dan
 * indikator aksen tipis di bawah tab aktif. Tanpa garis tepi, tanpa kotak.
 * Dipakai untuk memilih kategori properti (Konten, Transform, dst).
 */
class FluxaTabBar(
    context: Context,
    items: List<Pair<FluxaIcon, String>>,
    initial: Int,
    private val onSelect: (Int) -> Unit
) : LinearLayout(context) {

    private val icons = ArrayList<IconView>()
    private val labels = ArrayList<TextView>()
    private val indicators = ArrayList<View>()
    var selected: Int = initial.coerceIn(0, (items.size - 1).coerceAtLeast(0))
        private set

    init {
        orientation = HORIZONTAL
        for ((i, item) in items.withIndex()) {
            val (icon, label) = item
            val col = LinearLayout(context)
            col.orientation = VERTICAL
            col.gravity = Gravity.CENTER_HORIZONTAL
            col.setPadding(context.dp(14f), context.dp(6f), context.dp(14f), 0)
            col.isClickable = true

            val iv = IconView(context, icon, FluxaColors.Muted)
            iv.iconInset = 0.06f
            icons.add(iv)
            col.addView(iv, LayoutParams(context.dp(24f), context.dp(24f)))

            val tv = TextView(context)
            tv.text = label
            tv.textSize = 11.5f
            tv.setTextColor(FluxaColors.Muted)
            tv.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            tv.gravity = Gravity.CENTER
            tv.setPadding(0, context.dp(4f), 0, 0)
            labels.add(tv)
            col.addView(tv, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))

            val ind = View(context)
            ind.background = roundedRect(FluxaColors.Accent, context.dp(2f).toFloat())
            indicators.add(ind)
            val lpInd = LayoutParams(context.dp(20f), context.dp(3f))
            lpInd.topMargin = context.dp(6f)
            col.addView(ind, lpInd)

            col.setOnClickListener {
                if (selected != i) {
                    select(i)
                    onSelect(i)
                }
            }
            addView(col, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        }
        paintSelection()
    }

    /** Pilih tab tanpa memicu [onSelect] (dipakai saat binding ulang). */
    fun select(index: Int) {
        if (index < 0 || index >= icons.size) return
        selected = index
        paintSelection()
    }

    private fun paintSelection() {
        for (i in icons.indices) {
            val on = i == selected
            val color = if (on) FluxaColors.Accent else FluxaColors.Muted
            icons[i].tintColor = color
            labels[i].setTextColor(color)
            indicators[i].visibility = if (on) View.VISIBLE else View.INVISIBLE
        }
    }
}
