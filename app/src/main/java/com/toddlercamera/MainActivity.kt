package com.toddlercamera

import android.Manifest
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Locale

class MainActivity : ComponentActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var messageView: TextView
    private lateinit var photoModeBtn: RoundIconButton
    private lateinit var videoModeBtn: RoundIconButton
    private lateinit var galleryBtn: RoundIconButton
    private lateinit var captureBtn: RoundIconButton
    private lateinit var flipBtn: RoundIconButton
    private lateinit var gearBtn: RoundIconButton

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null

    private var videoMode = false
    private var useFront = false

    private val holdHandler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("toddler", MODE_PRIVATE) }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            onPermissionsResult()
        }

    private fun has(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun wantedPermissions(): Array<String> {
        val list = mutableListOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) {
            list.add(Manifest.permission.READ_MEDIA_IMAGES)
            list.add(Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            list.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        return list.toTypedArray()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        buildScreen()

        // With the toddler lock on, the back button does nothing
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!prefs.getBoolean("lock", false)) {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        permissionLauncher.launch(wantedPermissions())
    }

    override fun onResume() {
        super.onResume()
        if (prefs.getBoolean("lock", false)) {
            try {
                startLockTask()
            } catch (e: Exception) {
                // Pinning not available; carry on without it
            }
        }
    }

    // ---------- Screen ----------

    private fun buildScreen() {
        val root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)

        previewView = PreviewView(this)
        root.addView(
            previewView,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )

        messageView = TextView(this).apply {
            textSize = 22f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(Ui.dp(context, 24), 0, Ui.dp(context, 24), 0)
            visibility = android.view.View.GONE
        }
        root.addView(
            messageView,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )

        // Top: photo / video choice
        photoModeBtn = Ui.roundButton(this, Icon.CAMERA, Ui.BLUE, 76, "Photo mode")
        videoModeBtn = Ui.roundButton(this, Icon.VIDEO, Ui.ORANGE, 76, "Video mode")
        val modes = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        modes.addView(photoModeBtn, spaced(76))
        modes.addView(videoModeBtn, spaced(76))
        root.addView(
            modes,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.TOP
                topMargin = Ui.dp(this@MainActivity, 40)
            }
        )

        // Small parent button, top right: hold for two seconds
        gearBtn = Ui.roundButton(this, Icon.GEAR, Ui.DARK, 44, "Parent menu (hold)")
        root.addView(
            gearBtn,
            FrameLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)).apply {
                gravity = Gravity.TOP or Gravity.END
                topMargin = Ui.dp(this@MainActivity, 12)
                marginEnd = Ui.dp(this@MainActivity, 12)
            }
        )

        // Bottom: gallery, take picture / record, flip camera
        galleryBtn = Ui.roundButton(this, Icon.GALLERY, Ui.BLUE, 92, "Gallery")
        flipBtn = Ui.roundButton(this, Icon.FLIP, Ui.PURPLE, 92, "Switch camera")
        captureBtn = Ui.roundButton(this, Icon.NONE, Color.WHITE, 116, "Take picture")

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        for (b in listOf(galleryBtn, captureBtn, flipBtn)) {
            bar.addView(cell(b), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        root.addView(
            bar,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.BOTTOM
                bottomMargin = Ui.dp(this@MainActivity, 36)
            }
        )
        setContentView(root)

        // Actions
        photoModeBtn.setOnClickListener { setMode(false) }
        videoModeBtn.setOnClickListener { setMode(true) }
        captureBtn.setOnClickListener { if (videoMode) toggleRecording() else takePhoto() }
        flipBtn.setOnClickListener { flipCamera() }
        galleryBtn.setOnClickListener { startActivity(Intent(this, GalleryActivity::class.java)) }
        gearBtn.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> holdHandler.postDelayed({ showParentMenu() }, 2000)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> holdHandler.removeCallbacksAndMessages(null)
            }
            true
        }
        updateButtons()
    }

    private fun spaced(sizeDp: Int) = LinearLayout.LayoutParams(Ui.dp(this, sizeDp), Ui.dp(this, sizeDp)).apply {
        setMargins(Ui.dp(this@MainActivity, 12), 0, Ui.dp(this@MainActivity, 12), 0)
    }

    private fun cell(button: android.view.View): FrameLayout = FrameLayout(this).apply {
        addView(
            button,
            FrameLayout.LayoutParams(button.layoutParams.width, button.layoutParams.height).apply {
                gravity = Gravity.CENTER
            }
        )
    }

    private fun updateButtons() {
        val busy = recording != null
        photoModeBtn.alpha = if (!videoMode) 1f else 0.45f
        videoModeBtn.alpha = if (videoMode) 1f else 0.45f
        photoModeBtn.isEnabled = !busy
        videoModeBtn.isEnabled = !busy
        flipBtn.isEnabled = !busy
        flipBtn.alpha = if (busy) 0.4f else 1f
        galleryBtn.isEnabled = !busy
        galleryBtn.alpha = if (busy) 0.4f else 1f

        if (!videoMode) {
            captureBtn.fill = Color.WHITE
            captureBtn.ring = Ui.RED
            captureBtn.icon = Icon.NONE
            captureBtn.contentDescription = "Take picture"
        } else {
            captureBtn.fill = Ui.RED
            captureBtn.ring = Color.WHITE
            captureBtn.icon = if (busy) Icon.STOP else Icon.NONE
            captureBtn.contentDescription = if (busy) "Stop recording" else "Start recording"
        }
    }

    // ---------- Permissions ----------

    private fun onPermissionsResult() {
        if (has(Manifest.permission.CAMERA)) {
            messageView.visibility = android.view.View.GONE
            startCamera()
        } else {
            messageView.text = "The camera needs permission.\n\nTap here to allow it."
            messageView.visibility = android.view.View.VISIBLE
            messageView.setOnClickListener { permissionLauncher.launch(wantedPermissions()) }
        }
    }

    // ---------- Camera ----------

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            cameraProvider = provider
            // A TV or tablet may only have a front camera
            if (!safeHasCamera(CameraSelector.DEFAULT_BACK_CAMERA) &&
                safeHasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
            ) useFront = true
            bindUseCases()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun safeHasCamera(selector: CameraSelector): Boolean =
        try {
            cameraProvider?.hasCamera(selector) ?: false
        } catch (e: Exception) {
            false
        }

    private fun bindUseCases() {
        val provider = cameraProvider ?: return
        val selector =
            if (useFront) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
        try {
            provider.unbindAll()
            if (videoMode) {
                val recorder = Recorder.Builder()
                    .setQualitySelector(
                        QualitySelector.from(Quality.HD, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD))
                    )
                    .build()
                val vc = VideoCapture.withOutput(recorder)
                videoCapture = vc
                imageCapture = null
                provider.bindToLifecycle(this, selector, preview, vc)
            } else {
                val ic = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                imageCapture = ic
                videoCapture = null
                provider.bindToLifecycle(this, selector, preview, ic)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Camera problem: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun setMode(video: Boolean) {
        if (recording != null || videoMode == video) return
        videoMode = video
        updateButtons()
        bindUseCases()
    }

    private fun flipCamera() {
        if (recording != null) return
        val target =
            if (useFront) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
        if (!safeHasCamera(target)) {
            Toast.makeText(this, "No other camera on this device", Toast.LENGTH_SHORT).show()
            return
        }
        useFront = !useFront
        bindUseCases()
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.UK).format(System.currentTimeMillis())

    private fun takePhoto() {
        val ic = imageCapture ?: return
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "TC_${timestamp()}")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/ToddlerCamera")
        }
        val options = ImageCapture.OutputFileOptions.Builder(
            contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
        ).build()
        ic.takePicture(options, ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    Toast.makeText(this@MainActivity, "📷 Saved", Toast.LENGTH_SHORT).show()
                }

                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(this@MainActivity, "Photo failed: ${exception.message}", Toast.LENGTH_LONG).show()
                }
            })
    }

    private fun toggleRecording() {
        val vc = videoCapture ?: return
        val current = recording
        if (current != null) {
            current.stop()
            return
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "TC_${timestamp()}")
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/ToddlerCamera")
        }
        val output = MediaStoreOutputOptions.Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(values)
            .build()
        var pending = vc.output.prepareRecording(this, output)
        if (has(Manifest.permission.RECORD_AUDIO)) pending = pending.withAudioEnabled()
        recording = pending.start(ContextCompat.getMainExecutor(this)) { event ->
            if (event is VideoRecordEvent.Finalize) {
                recording = null
                val msg = if (event.hasError()) "Video problem (code ${event.error})" else "🎥 Saved"
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
            updateButtons()
        }
        updateButtons()
    }

    // ---------- Parent menu (hold the small gear for 2 seconds) ----------

    private fun showParentMenu() {
        val lockOn = prefs.getBoolean("lock", false)
        AlertDialog.Builder(this)
            .setTitle("Parent menu")
            .setItems(
                arrayOf(if (lockOn) "Turn toddler lock OFF" else "Turn toddler lock ON", "Close app")
            ) { _, which ->
                when (which) {
                    0 -> setLock(!lockOn)
                    1 -> {
                        try {
                            stopLockTask()
                        } catch (e: Exception) {
                        }
                        finishAffinity()
                    }
                }
            }
            .show()
    }

    private fun setLock(on: Boolean) {
        prefs.edit().putBoolean("lock", on).apply()
        try {
            if (on) startLockTask() else stopLockTask()
        } catch (e: Exception) {
        }
        Toast.makeText(this, if (on) "Toddler lock ON" else "Toddler lock OFF", Toast.LENGTH_SHORT).show()
    }
}
