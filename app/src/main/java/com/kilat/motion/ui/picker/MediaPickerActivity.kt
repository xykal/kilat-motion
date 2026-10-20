package com.kilat.motion.ui.picker

import android.Manifest
import android.app.Activity
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.kilat.motion.ui.components.IconView
import com.kilat.motion.ui.components.KilatIcon
import com.kilat.motion.ui.components.KilatIconButton
import com.kilat.motion.ui.theme.KColors
import com.kilat.motion.ui.theme.dp
import com.kilat.motion.ui.theme.dpf
import com.kilat.motion.ui.theme.kText
import com.kilat.motion.ui.theme.roundedRect
import java.util.concurrent.Executors

/**
 * Picker media custom (bukan pemilih sistem): grid foto/video dan daftar musik dari MediaStore.
 * Tampilannya digambar sendiri (flat, gelap). Hasil dikembalikan sebagai content Uri.
 */
class MediaPickerActivity : Activity() {

    enum class Kind { IMAGE, VIDEO, AUDIO }

    data class Item(
        val id: Long,
        val uri: android.net.Uri,
        val name: String,
        val durMs: Long,
        val kind: Kind,
        val sub: String
    )

    private lateinit var kind: Kind
    private val items = ArrayList<Item>()
    private lateinit var grid: GridView
    private lateinit var status: TextView
    private val pool = Executors.newFixedThreadPool(2)
    private val thumbs = object : LruCache<Long, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: Long, value: Bitmap): Int = value.byteCount
    }

    companion object {
        const val EXTRA_MODE = "kilat.picker.mode"
        const val MODE_IMAGE = "image"
        const val MODE_VIDEO = "video"
        const val MODE_AUDIO = "audio"
        const val RESULT_FALLBACK = 7001
        private const val PERM_REQ = 4411
        private const val MAX_ITEMS = 240
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        kind = when (intent.getStringExtra(EXTRA_MODE)) {
            MODE_VIDEO -> Kind.VIDEO
            MODE_AUDIO -> Kind.AUDIO
            else -> Kind.IMAGE
        }
        window.statusBarColor = KColors.Bg
        window.navigationBarColor = KColors.Bg

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(KColors.Bg)

        // header
        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.setPadding(dp(12f), dp(40f), dp(12f), dp(10f))
        val back = KilatIconButton(this, KilatIcon.CLOSE, KColors.Muted, filled = false, sizeDp = 38f)
        back.setOnClickListener { finish() }
        header.addView(back, LinearLayout.LayoutParams(dp(38f), dp(38f)))
        val title = kText(
            when (kind) {
                Kind.IMAGE -> "Pilih Gambar"
                Kind.VIDEO -> "Pilih Video"
                Kind.AUDIO -> "Pilih Musik"
            },
            16f, KColors.Text, bold = true
        )
        val lpT = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        lpT.marginStart = dp(8f)
        header.addView(title, lpT)
        root.addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val content = FrameLayout(this)
        grid = GridView(this).apply {
            numColumns = if (kind == Kind.AUDIO) 1 else 3
            horizontalSpacing = dp(4f)
            verticalSpacing = dp(4f)
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            setPadding(dp(4f), 0, dp(4f), dp(16f))
            clipToPadding = false
            selector = ColorDrawable(Color.TRANSPARENT)
            cacheColorHint = Color.TRANSPARENT
            visibility = View.GONE
            setOnItemClickListener { _, _, pos, _ -> pick(items[pos]) }
        }
        content.addView(grid, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        status = kText("Memuat...", 12f, KColors.Muted)
        status.gravity = Gravity.CENTER
        content.addView(status, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        root.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)

        if (hasPermission()) load() else requestPerm()
    }

    private fun permissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= 33 -> when (kind) {
            Kind.IMAGE -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
            Kind.VIDEO -> arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
            Kind.AUDIO -> arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
        }
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    private fun hasPermission(): Boolean = permissions().all {
        checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPerm() {
        status.text = "Butuh izin akses media untuk menampilkan galeri."
        status.setOnClickListener { requestPermissions(permissions(), PERM_REQ) }
        status.setTextColor(KColors.Accent)
        requestPermissions(permissions(), PERM_REQ)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != PERM_REQ) return
        if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            load()
        } else {
            // izin ditolak: kembalikan ke pemilih sistem supaya user tetap bisa memilih file
            setResult(RESULT_FALLBACK)
            finish()
        }
    }

    private fun load() {
        status.text = "Memuat..."
        pool.execute {
            val list = query()
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                items.clear()
                items.addAll(list)
                if (items.isEmpty()) {
                    status.visibility = View.VISIBLE
                    status.text = when (kind) {
                        Kind.IMAGE -> "Tidak ada gambar di perangkat."
                        Kind.VIDEO -> "Tidak ada video di perangkat."
                        Kind.AUDIO -> "Tidak ada musik di perangkat."
                    }
                } else {
                    status.visibility = View.GONE
                    grid.visibility = View.VISIBLE
                    grid.adapter = adapter
                }
            }
        }
    }

    private fun query(): List<Item> {
        val out = ArrayList<Item>()
        val (uriBase, projection, sort) = when (kind) {
            Kind.IMAGE -> Triple(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME),
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )
            Kind.VIDEO -> Triple(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME, MediaStore.Video.Media.DURATION),
                "${MediaStore.Video.Media.DATE_ADDED} DESC"
            )
            Kind.AUDIO -> Triple(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                arrayOf(
                    MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME,
                    MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.ARTIST
                ),
                "${MediaStore.Audio.Media.DATE_ADDED} DESC"
            )
        }
        val selection = if (kind == Kind.AUDIO) "${MediaStore.Audio.Media.IS_MUSIC} != 0" else null
        var c: Cursor? = null
        try {
            c = contentResolver.query(uriBase, projection, selection, null, sort)
            c?.let {
                while (it.moveToNext() && out.size < MAX_ITEMS) {
                    val id = it.getLong(it.getColumnIndexOrThrow(projection[0]))
                    val name = it.getString(it.getColumnIndexOrThrow(projection[1])) ?: "media"
                    val dur = if (projection.size > 2) it.getLong(it.getColumnIndexOrThrow(projection[2])) else 0L
                    val artist = if (projection.size > 3) it.getString(it.getColumnIndexOrThrow(projection[3])) else null
                    out.add(
                        Item(
                            id = id,
                            uri = ContentUris.withAppendedId(uriBase, id),
                            name = name,
                            durMs = dur,
                            kind = kind,
                            sub = artist?.takeIf { a -> a.isNotBlank() && a != "<unknown>" } ?: ""
                        )
                    )
                }
            }
        } catch (_: Throwable) {
        } finally {
            c?.close()
        }
        return out
    }

    private fun pick(item: Item) {
        val data = Intent().apply {
            setData(item.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra("kilat.picker.name", item.name)
        }
        setResult(RESULT_OK, data)
        finish()
    }

    private fun fmtDur(ms: Long): String {
        val s = (ms / 1000).toInt()
        return "%d:%02d".format(s / 60, s % 60)
    }

    private fun loadThumb(item: Item, into: ImageView) {
        if (item.kind == Kind.AUDIO) return
        thumbs.get(item.id)?.let { into.setImageBitmap(it); return }
        into.setImageDrawable(null)
        pool.execute {
            val bmp = try {
                contentResolver.loadThumbnail(item.uri, Size(240, 240), null)
            } catch (_: Throwable) {
                null
            }
            if (bmp != null) {
                thumbs.put(item.id, bmp)
                runOnUiThread {
                    if (!isFinishing && into.tag == item.id) into.setImageBitmap(bmp)
                }
            }
        }
    }

    private val adapter = object : BaseAdapter() {
        override fun getCount(): Int = items.size
        override fun getItem(position: Int): Any = items[position]
        override fun getItemId(position: Int): Long = items[position].id

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val item = items[position]
            return if (item.kind == Kind.AUDIO) audioRow(item, convertView) else thumbCell(item, convertView)
        }
    }

    private fun thumbCell(item: Item, convertView: View?): View {
        val cell: FrameLayout
        val img: ImageView
        val tag: TextView
        if (convertView is FrameLayout) {
            cell = convertView
            img = cell.getChildAt(0) as ImageView
            tag = cell.getChildAt(1) as TextView
        } else {
            cell = FrameLayout(this)
            img = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
            cell.addView(img, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            tag = kText("", 9.5f, KColors.Text, bold = true, font = "monospace")
            tag.setPadding(dp(5f), dp(2f), dp(5f), dp(2f))
            tag.background = roundedRect(0xFF0A0C11.toInt(), dpf(5f))
            val lp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.END)
            lp.setMargins(dp(4f), dp(4f), dp(4f), dp(4f))
            cell.addView(tag, lp)
        }
        val h = (resources.displayMetrics.widthPixels - dp(20f)) / 3
        cell.layoutParams = AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h)
        cell.background = roundedRect(KColors.Card, dpf(8f))
        img.tag = item.id
        img.setImageDrawable(null)
        loadThumb(item, img)
        if (item.kind == Kind.VIDEO) {
            tag.visibility = View.VISIBLE
            tag.text = fmtDur(item.durMs)
        } else {
            tag.visibility = View.GONE
        }
        cell.setOnClickListener { pick(item) }
        cell.isClickable = true
        return cell
    }

    private fun audioRow(item: Item, convertView: View?): View {
        val row: LinearLayout
        val title: TextView
        val sub: TextView
        val time: TextView
        if (convertView is LinearLayout) {
            row = convertView
            title = (row.getChildAt(1) as LinearLayout).getChildAt(0) as TextView
            sub = (row.getChildAt(1) as LinearLayout).getChildAt(1) as TextView
            time = row.getChildAt(2) as TextView
        } else {
            row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(dp(12f), dp(10f), dp(12f), dp(10f))
            val badge = IconView(this, KilatIcon.MUSIC, KColors.Accent)
            val pad = dp(9f)
            badge.setPadding(pad, pad, pad, pad)
            badge.background = roundedRect(KColors.Field, dpf(10f))
            row.addView(badge, LinearLayout.LayoutParams(dp(42f), dp(42f)))
            val col = LinearLayout(this)
            col.orientation = LinearLayout.VERTICAL
            title = kText("", 13f, KColors.Text, bold = true)
            title.maxLines = 1
            title.ellipsize = android.text.TextUtils.TruncateAt.END
            sub = kText("", 10.5f, KColors.Muted)
            sub.maxLines = 1
            sub.ellipsize = android.text.TextUtils.TruncateAt.END
            col.addView(title)
            col.addView(sub)
            val lpC = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            lpC.marginStart = dp(12f)
            row.addView(col, lpC)
            time = kText("", 11f, KColors.Muted, font = "monospace")
            row.addView(time)
        }
        title.text = item.name.substringBeforeLast('.').take(40)
        sub.text = item.sub.ifEmpty { "Musik" }
        time.text = fmtDur(item.durMs)
        row.background = roundedRect(KColors.Card, dpf(10f))
        row.layoutParams = AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        row.setOnClickListener { pick(item) }
        row.isClickable = true
        return row
    }

    override fun onDestroy() {
        pool.shutdownNow()
        super.onDestroy()
    }
}
