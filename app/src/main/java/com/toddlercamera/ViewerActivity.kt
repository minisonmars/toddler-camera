package com.toddlercamera

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageDecoder
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.VideoView
import java.util.concurrent.Executors
import kotlin.math.max

/** Full-screen picture or video with big back / next buttons. No deleting. */
class ViewerActivity : Activity() {

    private lateinit var imageView: ImageView
    private lateinit var videoView: VideoView
    private var items: List<Media.Item> = emptyList()
    private var index = 0
    private val pool = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        items = Media.load(this, Settings(this))
        if (items.isEmpty()) {
            finish()
            return
        }
        index = intent.getIntExtra("index", 0).coerceIn(0, items.size - 1)

        val root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)

        imageView = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        root.addView(imageView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        videoView = VideoView(this)
        root.addView(
            videoView,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                .apply { gravity = Gravity.CENTER }
        )
        // Tap a video to pause or play
        videoView.setOnClickListener { if (videoView.isPlaying) videoView.pause() else videoView.start() }

        val prev = Ui.roundButton(this, Icon.ARROW_LEFT, Ui.BLUE, 84, "Previous")
        val close = Ui.roundButton(this, Icon.CLOSE, Ui.RED, 84, "Close")
        val next = Ui.roundButton(this, Icon.ARROW_RIGHT, Ui.BLUE, 84, "Next")
        prev.setOnClickListener { show((index - 1 + items.size) % items.size) }
        next.setOnClickListener { show((index + 1) % items.size) }
        close.setOnClickListener { finish() }

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        for (b in listOf(prev, close, next)) {
            bar.addView(
                b,
                LinearLayout.LayoutParams(b.layoutParams.width, b.layoutParams.height)
                    .apply { setMargins(Ui.dp(this@ViewerActivity, 16), 0, Ui.dp(this@ViewerActivity, 16), 0) }
            )
        }
        root.addView(
            bar,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.BOTTOM
                bottomMargin = Ui.dp(this@ViewerActivity, 36)
            }
        )
        setContentView(root.fitSystemBars())
        show(index)
    }

    private fun show(i: Int) {
        index = i
        val item = items[i]
        videoView.stopPlayback()
        if (item.isVideo) {
            imageView.visibility = View.GONE
            videoView.visibility = View.VISIBLE
            videoView.setVideoURI(item.uri)
            videoView.setOnPreparedListener { videoView.start() }
        } else {
            videoView.visibility = View.GONE
            imageView.visibility = View.VISIBLE
            imageView.setImageDrawable(null)
            pool.execute {
                val bmp: Bitmap? = try {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, item.uri)) { decoder, info, _ ->
                        val longest = max(info.size.width, info.size.height)
                        if (longest > 2048) decoder.setTargetSampleSize(longest / 2048)
                    }
                } catch (e: Exception) {
                    null
                }
                runOnUiThread { if (index == i && bmp != null) imageView.setImageBitmap(bmp) }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (::videoView.isInitialized) videoView.pause()
    }
}
