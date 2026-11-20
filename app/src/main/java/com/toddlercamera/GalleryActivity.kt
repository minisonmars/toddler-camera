package com.toddlercamera

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
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
import java.util.concurrent.Executors

/** Every picture and video on the device, in big tiles. Tap one to open it. No deleting. */
class GalleryActivity : Activity() {

    private lateinit var grid: GridView
    private lateinit var emptyView: TextView
    private val adapter = Adapter()
    private var items: List<Media.Item> = emptyList()
    private val cache = LruCache<Uri, Bitmap>(120)
    private val pool = Executors.newFixedThreadPool(3)
    private var cellPx = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dm = resources.displayMetrics
        val cols = (dm.widthPixels / dm.density / 160f).toInt().coerceAtLeast(2)
        cellPx = dm.widthPixels / cols

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(context, 16), Ui.dp(context, 24), Ui.dp(context, 16), Ui.dp(context, 8))
        }
        val back = Ui.roundButton(this, Icon.ARROW_LEFT, Ui.BLUE, 72, "Back")
        back.setOnClickListener { finish() }
        top.addView(back)
        top.addView(TextView(this).apply {
            text = "Gallery"
            textSize = 26f
            setTextColor(Color.WHITE)
            setPadding(Ui.dp(context, 16), 0, 0, 0)
        })
        root.addView(top)

        grid = GridView(this).apply {
            numColumns = cols
            adapter = this@GalleryActivity.adapter
            setOnItemClickListener { _, _, position, _ ->
                startActivity(Intent(this@GalleryActivity, ViewerActivity::class.java).putExtra("index", position))
            }
        }
        emptyView = TextView(this).apply {
            textSize = 22f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        val body = FrameLayout(this)
        body.addView(grid, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        body.addView(emptyView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun granted(p: String) = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    private fun mediaPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= 33)
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

    private fun refresh() {
        if (mediaPermissions().any { granted(it) }) {
            items = Media.load(this)
            adapter.notifyDataSetChanged()
            if (items.isEmpty()) {
                emptyView.text = "No pictures or videos yet"
                emptyView.setOnClickListener(null)
                emptyView.visibility = View.VISIBLE
            } else {
                emptyView.visibility = View.GONE
            }
        } else {
            emptyView.text = "Tap here to allow\naccess to pictures and videos"
            emptyView.setOnClickListener { requestPermissions(mediaPermissions(), 7) }
            emptyView.visibility = View.VISIBLE
        }
    }

    @Deprecated("Framework callback")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refresh()
    }

    private inner class Adapter : BaseAdapter() {
        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val cell = (convertView as? FrameLayout) ?: FrameLayout(this@GalleryActivity).apply {
                layoutParams = AbsListView.LayoutParams(cellPx, cellPx)
                addView(
                    ImageView(context).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        setPadding(2, 2, 2, 2)
                    },
                    FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                )
                addView(
                    Ui.roundButton(context, Icon.PLAY, Color.argb(150, 0, 0, 0), 52, "Video")
                        .apply { isFocusable = false },
                    FrameLayout.LayoutParams(Ui.dp(context, 52), Ui.dp(context, 52))
                        .apply { gravity = Gravity.CENTER }
                )
            }
            val image = cell.getChildAt(0) as ImageView
            val badge = cell.getChildAt(1)
            val item = items[position]
            badge.visibility = if (item.isVideo) View.VISIBLE else View.GONE

            val cached = cache.get(item.uri)
            image.tag = item.uri
            if (cached != null) {
                image.setImageBitmap(cached)
            } else {
                image.setImageDrawable(null)
                pool.execute {
                    val bmp = try {
                        contentResolver.loadThumbnail(item.uri, Size(cellPx, cellPx), null)
                    } catch (e: Exception) {
                        null
                    }
                    if (bmp != null) {
                        cache.put(item.uri, bmp)
                        runOnUiThread { if (image.tag == item.uri) image.setImageBitmap(bmp) }
                    }
                }
            }
            return cell
        }
    }
}
