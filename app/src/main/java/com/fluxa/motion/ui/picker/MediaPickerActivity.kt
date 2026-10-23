package com.fluxa.motion.ui.picker

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
import com.fluxa.motion.ui.components.FluxaSheet
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
import com.fluxa.motion.ui.components.IconView
import com.fluxa.motion.ui.components.FluxaIcon
import com.fluxa.motion.ui.components.FluxaIconButton
import com.fluxa.motion.ui.theme.FluxaColors
import com.fluxa.motion.ui.theme.dp
import com.fluxa.motion.ui.theme.dpf
import com.fluxa.motion.ui.theme.kText
import com.fluxa.motion.ui.theme.roundedRect
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
        val sub: String,
        val bucket: String = "",   // nama folder (MediaStore BUCKET_DISPLAY_NAME)
        val key: String = ""       // kunci stabil untuk cache impor (id + ukuran + tanggal ubah)
    )

    private val allItems = ArrayList<Item>()
    private var bucketFilter: String? = null   // null = semua folder
    private lateinit var folderChip: TextView

    private lateinit var kind: Kind
    private val items = ArrayList<Item>()
    private lateinit var grid: GridView
    private lateinit var status: TextView
    private val pool = Executors.newFixedThreadPool(2)
    private val thumbs = object : LruCache<Long, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: Long, value: Bitmap): Int = value.byteCount
    }

    companion object {
        const val EXTRA_MODE = "fluxa.picker.mode"
        const val MODE_IMAGE = "image"
        const val MODE_VIDEO = "video"
        const val MODE_AUDIO = "audio"
        const val RESULT_FALLBACK = 7001
        const val EXTRA_KEY = "fluxa.picker.key"
        private const val PERM_REQ = 4411
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        kind = when (intent.getStringExtra(EXTRA_MODE)) {
            MODE_VIDEO -> Kind.VIDEO
            MODE_AUDIO -> Kind.AUDIO
            else -> Kind.IMAGE
        }
        window.statusBarColor = FluxaColors.Bg
        window.navigationBarColor = FluxaColors.Bg

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(FluxaColors.Bg)

        // header
        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.setPadding(dp(12f), dp(40f), dp(12f), dp(10f))
        val back = FluxaIconButton(this, FluxaIcon.CLOSE, FluxaColors.Muted, filled = false, sizeDp = 44f)
        back.setOnClickListener { finish() }
        header.addView(back, LinearLayout.LayoutParams(dp(44f), dp(44f)))
        val title = kText(
            when (kind) {
                Kind.IMAGE -> "Pilih Gambar"
                Kind.VIDEO -> "Pilih Video"
                Kind.AUDIO -> "Pilih Musik"
            },
            16f, FluxaColors.Text, bold = true
        )
        val lpT = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        lpT.marginStart = dp(8f)
        header.addView(title, lpT)
        root.addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        folderChip = kText("Semua folder  \u25BE", 13f, FluxaColors.Text, bold = true)
        folderChip.setPadding(dp(14f), dp(8f), dp(14f), dp(8f))
        folderChip.background = roundedRect(FluxaColors.Card, dpf(16f))
        folderChip.setOnClickListener { showFolderSheet() }
        folderChip.visibility = View.GONE
        val chipRow = LinearLayout(this)
        chipRow.orientation = LinearLayout.HORIZONTAL
        chipRow.setPadding(dp(12f), dp(2f), dp(12f), dp(8f))
        chipRow.addView(folderChip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(chipRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

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
        status = kText("Memuat...", 12f, FluxaColors.Muted)
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
        status.setTextColor(FluxaColors.Accent)
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
                allItems.clear()
                allItems.addAll(list)
                applyFilter()
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
                    folderChip.visibility = if (kind == Kind.AUDIO) View.GONE else View.VISIBLE
                }
            }
        }
    }

    private fun query(): List<Item> {
        val out = ArrayList<Item>()
        val uriBase: android.net.Uri = when (kind) {
            Kind.IMAGE -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            Kind.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            Kind.AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val bucketCol = when (kind) {
            Kind.IMAGE -> MediaStore.Images.Media.BUCKET_DISPLAY_NAME
            Kind.VIDEO -> MediaStore.Video.Media.BUCKET_DISPLAY_NAME
            Kind.AUDIO -> null
        }
        val durCol = when (kind) {
            Kind.IMAGE -> null
            Kind.VIDEO -> MediaStore.Video.Media.DURATION
            Kind.AUDIO -> MediaStore.Audio.Media.DURATION
        }
        val artistCol = if (kind == Kind.AUDIO) MediaStore.Audio.Media.ARTIST else null
        val sizeCol = if (kind == Kind.VIDEO) MediaStore.MediaColumns.SIZE else null
        val modCol = if (kind == Kind.VIDEO) MediaStore.MediaColumns.DATE_MODIFIED else null
        val cols = listOfNotNull(
            MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME,
            bucketCol, durCol, artistCol, sizeCol, modCol
        ).toTypedArray()
        val sort = when (kind) {
            Kind.IMAGE -> "${MediaStore.Images.Media.DATE_ADDED} DESC"
            Kind.VIDEO -> "${MediaStore.Video.Media.DATE_ADDED} DESC"
            Kind.AUDIO -> "${MediaStore.Audio.Media.DATE_ADDED} DESC"
        }
        val selection = if (kind == Kind.AUDIO) "${MediaStore.Audio.Media.IS_MUSIC} != 0" else null
        var c: Cursor? = null
        try {
            c = contentResolver.query(uriBase, cols, selection, null, sort)
            c?.let { cur ->
                fun col(n: String?): Int? = n?.let { x -> cur.getColumnIndex(x).takeIf { i -> i >= 0 } }
                val iId = col(MediaStore.MediaColumns._ID) ?: return@let
                val iName = col(MediaStore.MediaColumns.DISPLAY_NAME)
                val iBucket = col(bucketCol)
                val iDur = col(durCol)
                val iArtist = col(artistCol)
                val iSize = col(sizeCol)
                val iMod = col(modCol)
                while (cur.moveToNext()) {
                    val id = cur.getLong(iId)
                    val name = iName?.let { cur.getString(it) } ?: "media"
                    val bucket = iBucket?.let { cur.getString(it) } ?: ""
                    val dur = iDur?.let { cur.getLong(it) } ?: 0L
                    val artist = iArtist?.let { cur.getString(it) }
                    val size = iSize?.let { cur.getLong(it) } ?: 0L
                    val mod = iMod?.let { cur.getLong(it) } ?: 0L
                    out.add(
                        Item(
                            id = id,
                            uri = ContentUris.withAppendedId(uriBase, id),
                            name = name,
                            durMs = dur,
                            kind = kind,
                            sub = artist?.takeIf { a -> a.isNotBlank() && a != "<unknown>" } ?: "",
                            bucket = bucket,
                            key = if (kind == Kind.VIDEO) "v${id}_${size}_$mod" else ""
                        )
                    )
                }
            }
        } catch (_: Exception) {
        } finally {
            c?.close()
        }
        return out
    }

    /** Terapkan filter folder ke daftar yang tampil di grid. */
    private fun applyFilter() {
        items.clear()
        items.addAll(if (bucketFilter == null) allItems else allItems.filter { it.bucket == bucketFilter })
        folderChip.text = "${bucketFilter?.let { bucketLabel(it) } ?: "Semua folder"}  \u25BE"
        if (::grid.isInitialized && grid.adapter != null) adapter.notifyDataSetChanged()
    }

    private fun bucketLabel(b: String): String = b.ifBlank { "Lainnya" }

    /** Daftar folder dengan jumlah item, dibuka sebagai sheet gelap. */
    private fun showFolderSheet() {
        val groups = allItems.groupingBy { it.bucket }.eachCount().toList()
            .sortedBy { bucketLabel(it.first).lowercase() }
        val list = ArrayList<FluxaSheet.Item>()
        list.add(FluxaSheet.Item("Semua folder", "${allItems.size} item"))
        groups.forEach { (b, n) -> list.add(FluxaSheet.Item(bucketLabel(b), "$n item")) }
        FluxaSheet.showList(this, "Pilih folder", list) { idx ->
            bucketFilter = if (idx == 0) null else groups[idx - 1].first
            applyFilter()
        }
    }

    private fun pick(item: Item) {
        val data = Intent().apply {
            setData(item.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra("fluxa.picker.name", item.name)
            putExtra(EXTRA_KEY, item.key)
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
            tag = kText("", 9.5f, FluxaColors.Text, bold = true, font = "monospace")
            tag.setPadding(dp(5f), dp(2f), dp(5f), dp(2f))
            tag.background = roundedRect(0xFF0A0C11.toInt(), dpf(5f))
            val lp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.END)
            lp.setMargins(dp(4f), dp(4f), dp(4f), dp(4f))
            cell.addView(tag, lp)
        }
        val h = (resources.displayMetrics.widthPixels - dp(20f)) / 3
        cell.layoutParams = AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h)
        cell.background = roundedRect(FluxaColors.Card, dpf(8f))
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
            val badge = IconView(this, FluxaIcon.MUSIC, FluxaColors.Accent)
            val pad = dp(9f)
            badge.setPadding(pad, pad, pad, pad)
            badge.background = roundedRect(FluxaColors.Field, dpf(10f))
            row.addView(badge, LinearLayout.LayoutParams(dp(42f), dp(42f)))
            val col = LinearLayout(this)
            col.orientation = LinearLayout.VERTICAL
            title = kText("", 13f, FluxaColors.Text, bold = true)
            title.maxLines = 1
            title.ellipsize = android.text.TextUtils.TruncateAt.END
            sub = kText("", 10.5f, FluxaColors.Muted)
            sub.maxLines = 1
            sub.ellipsize = android.text.TextUtils.TruncateAt.END
            col.addView(title)
            col.addView(sub)
            val lpC = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            lpC.marginStart = dp(12f)
            row.addView(col, lpC)
            time = kText("", 11f, FluxaColors.Muted, font = "monospace")
            row.addView(time)
        }
        title.text = item.name.substringBeforeLast('.').take(40)
        sub.text = item.sub.ifEmpty { "Musik" }
        time.text = fmtDur(item.durMs)
        row.background = roundedRect(FluxaColors.Card, dpf(10f))
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
