package com.toddlercamera

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Size
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
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
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private lateinit var settings: Settings

    private lateinit var previewView: PreviewView
    private lateinit var messageView: TextView
    private lateinit var timeUpView: FrameLayout
    private lateinit var modes: LinearLayout
    private lateinit var galleryCell: View
    private lateinit var photoModeBtn: RoundIconButton
    private lateinit var videoModeBtn: RoundIconButton
    private lateinit var galleryBtn: RoundIconButton
    private lateinit var captureBtn: RoundIconButton
    private lateinit var flipBtn: RoundIconButton
    private lateinit var gearBtn: RoundIconButton

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var starting = false
    private var torchOn = false
    private var lastSig = ""

    private var videoMode = false
    private var useFront = false

    private val holdHandler = Handler(Looper.getMainLooper())
    private val uiHandler = Handler(Looper.getMainLooper())
    private lateinit var sounds: Sounds
    private lateinit var flashOverlay: View
    private val thumbPool = Executors.newSingleThreadExecutor()

    private val ticker = object : Runnable {
        override fun run() {
            checkTimeLimit()
            uiHandler.postDelayed(this, 2000)
        }
    }

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
        settings = Settings(this)
        useFront = when (settings.startCamera) {
            "front" -> true
            "last" -> settings.lastFront
            else -> false
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        buildScreen()

        sounds = Sounds()

        // With the toddler lock on (or time up), the back button does nothing
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!settings.lock && timeUpView.visibility != View.VISIBLE) {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        permissionLauncher.launch(wantedPermissions())
    }

    override fun onResume() {
        super.onResume()
        applyFeatures()
        if (settings.lock) {
            try {
                startLockTask()
            } catch (e: Exception) {
                // Pinning not available; carry on without it
            }
        }
        uiHandler.post(ticker)
        refreshThumbnail()
        if (cameraProvider != null && signature() != lastSig) bindUseCases()
        // Quietly looks for a new version at most every 6 hours; only the parent area shows the result
        Updater.checkInBackground(this)
    }

    override fun onPause() {
        super.onPause()
        uiHandler.removeCallbacks(ticker)
    }

    override fun onDestroy() {
        super.onDestroy()
        sounds.release()
    }

    // ---------- Screen ----------

    private fun buildScreen() {
        val root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)

        previewView = PreviewView(this)
        root.addView(previewView, matchParent())

        messageView = TextView(this).apply {
            textSize = 22f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(Ui.dp(context, 24), 0, Ui.dp(context, 24), 0)
            visibility = View.GONE
        }
        root.addView(messageView, matchParent())

        // White flash shown when a picture is taken
        flashOverlay = View(this).apply {
            setBackgroundColor(Color.WHITE)
            alpha = 0f
            visibility = View.GONE
        }
        root.addView(flashOverlay, matchParent())

        // Top: photo / video choice (hidden unless both are switched on)
        photoModeBtn = Ui.roundButton(this, Icon.CAMERA, Ui.BLUE, 76, "Photo mode")
        videoModeBtn = Ui.roundButton(this, Icon.VIDEO, Ui.ORANGE, 76, "Video mode")
        modes = LinearLayout(this).apply {
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
        galleryCell = cell(galleryBtn)
        for (c in listOf(galleryCell, cell(captureBtn), cell(flipBtn))) {
            bar.addView(c, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        root.addView(
            bar,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.BOTTOM
                bottomMargin = Ui.dp(this@MainActivity, 36)
            }
        )

        // "Time's up" cover, only shown when a time limit is set
        timeUpView = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            visibility = View.GONE
            isClickable = true // blocks touches to the camera underneath
        }
        timeUpView.addView(
            TextView(this).apply {
                text = "All done for now"
                textSize = 32f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
            },
            matchParent()
        )
        val unlockGear = Ui.roundButton(this, Icon.GEAR, Ui.DARK, 44, "Parent unlock (hold)")
        timeUpView.addView(
            unlockGear,
            FrameLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)).apply {
                gravity = Gravity.TOP or Gravity.END
                topMargin = Ui.dp(this@MainActivity, 12)
                marginEnd = Ui.dp(this@MainActivity, 12)
            }
        )
        root.addView(timeUpView, matchParent())
        setContentView(root.fitSystemBars())

        // Actions
        photoModeBtn.setOnClickListener { setMode(false) }
        videoModeBtn.setOnClickListener { setMode(true) }
        captureBtn.setOnClickListener { if (videoMode) toggleRecording() else takePhoto() }
        flipBtn.setOnClickListener { flipCamera() }
        galleryBtn.setOnClickListener { startActivity(Intent(this, GalleryActivity::class.java)) }
        attachHold(gearBtn) {
            Gate.ask(this, settings) { startActivity(Intent(this, ParentSettingsActivity::class.java)) }
        }
        attachHold(unlockGear) {
            Gate.ask(this, settings) { unlockAfterTimeUp() }
        }
        updateButtons()
    }

    private fun matchParent() =
        FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

    private fun spaced(sizeDp: Int) = LinearLayout.LayoutParams(Ui.dp(this, sizeDp), Ui.dp(this, sizeDp)).apply {
        setMargins(Ui.dp(this@MainActivity, 12), 0, Ui.dp(this@MainActivity, 12), 0)
    }

    private fun cell(button: View): FrameLayout = FrameLayout(this).apply {
        addView(
            button,
            FrameLayout.LayoutParams(button.layoutParams.width, button.layoutParams.height).apply {
                gravity = Gravity.CENTER
            }
        )
    }

    /** A button that only reacts when held for two seconds. */
    private fun attachHold(button: View, action: () -> Unit) {
        button.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> holdHandler.postDelayed({ action() }, 2000)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> holdHandler.removeCallbacksAndMessages(null)
            }
            true
        }
    }

    /** Show or hide buttons according to the parent's settings. */
    private fun applyFeatures() {
        val photo = settings.photoEnabled
        val video = settings.videoEnabled
        if (photo && !video) videoMode = false
        if (video && !photo) videoMode = true
        val both = photo && video
        photoModeBtn.visibility = if (both) View.VISIBLE else View.GONE
        videoModeBtn.visibility = if (both) View.VISIBLE else View.GONE
        // Invisible (not gone) so the big button stays in the middle
        galleryCell.visibility = if (settings.galleryEnabled) View.VISIBLE else View.INVISIBLE
        updateButtons()
    }

    private fun updateButtons() {
        val busy = recording != null || starting
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
            captureBtn.icon = if (recording != null) Icon.STOP else Icon.NONE
            captureBtn.contentDescription = if (recording != null) "Stop recording" else "Start recording"
        }
    }

    // ---------- Time limit ----------

    private fun checkTimeLimit() {
        if (timeUpView.visibility != View.VISIBLE && Session.expired(settings)) showTimeUp()
    }

    private fun showTimeUp() {
        recording?.stop()
        timeUpView.visibility = View.VISIBLE
        cameraProvider?.unbindAll()
    }

    private fun unlockAfterTimeUp() {
        Session.restart()
        timeUpView.visibility = View.GONE
        bindUseCases()
    }

    // ---------- Permissions ----------

    private fun onPermissionsResult() {
        if (has(Manifest.permission.CAMERA)) {
            messageView.visibility = View.GONE
            startCamera()
        } else {
            messageView.text = "The camera needs permission.\n\nTap here to allow it."
            messageView.visibility = View.VISIBLE
            messageView.setOnClickListener { permissionLauncher.launch(wantedPermissions()) }
        }
    }

    // ---------- Camera ----------

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            cameraProvider = future.get()
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

    /** Changes to any of these mean the camera has to be set up again. */
    private fun signature() =
        "${settings.photoRes}|${settings.flash}|${settings.videoQuality}|$videoMode|$useFront"

    private fun photoResolution(): ResolutionSelector {
        val closest = ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
        val strategy = when (settings.photoRes) {
            "high" -> ResolutionStrategy(Size(4000, 3000), closest)
            "medium" -> ResolutionStrategy(Size(2592, 1944), closest)
            "low" -> ResolutionStrategy(Size(1600, 1200), closest)
            else -> ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY
        }
        return ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .setResolutionStrategy(strategy)
            .build()
    }

    private fun flashMode() = when (settings.flash) {
        "on" -> ImageCapture.FLASH_MODE_ON
        "auto" -> ImageCapture.FLASH_MODE_AUTO
        else -> ImageCapture.FLASH_MODE_OFF
    }

    private fun videoQuality(): Quality = when (settings.videoQuality) {
        "sd" -> Quality.SD
        "fhd" -> Quality.FHD
        "uhd" -> Quality.UHD
        else -> Quality.HD
    }

    private fun bindUseCases() {
        if (timeUpView.visibility == View.VISIBLE) return
        val provider = cameraProvider ?: return
        val selector =
            if (useFront) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
        try {
            provider.unbindAll()
            if (videoMode) {
                val q = videoQuality()
                val recorder = Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(q, FallbackStrategy.lowerQualityOrHigherThan(q)))
                    .build()
                val vc = VideoCapture.withOutput(recorder)
                videoCapture = vc
                imageCapture = null
                camera = provider.bindToLifecycle(this, selector, preview, vc)
            } else {
                val ic = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setResolutionSelector(photoResolution())
                    .setFlashMode(flashMode())
                    .build()
                imageCapture = ic
                videoCapture = null
                camera = provider.bindToLifecycle(this, selector, preview, ic)
            }
            lastSig = signature()
        } catch (e: Exception) {
            Toast.makeText(this, "Camera problem: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun setMode(video: Boolean) {
        if (recording != null || starting || videoMode == video) return
        videoMode = video
        updateButtons()
        bindUseCases()
    }

    private fun flipCamera() {
        if (recording != null || starting) return
        val target =
            if (useFront) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
        if (!safeHasCamera(target)) {
            Toast.makeText(this, "No other camera on this device", Toast.LENGTH_SHORT).show()
            return
        }
        useFront = !useFront
        settings.lastFront = useFront
        bindUseCases()
    }

    private fun flashScreen() {
        flashOverlay.animate().cancel()
        flashOverlay.alpha = 0.85f
        flashOverlay.visibility = View.VISIBLE
        flashOverlay.animate().alpha(0f).setDuration(220).withEndAction {
            flashOverlay.visibility = View.GONE
        }.start()
    }

    /** Show the newest picture (or video) on the gallery button, if the parent wants that. */
    private fun refreshThumbnail(hint: Uri? = null) {
        if (!settings.galleryEnabled || !settings.galleryThumb) {
            galleryBtn.thumbnail = null
            return
        }
        thumbPool.execute {
            val bmp = try {
                val uri = hint ?: Media.load(this, settings, 1).firstOrNull()?.uri
                if (uri == null) null else contentResolver.loadThumbnail(uri, Size(256, 256), null)
            } catch (e: Exception) {
                null
            }
            runOnUiThread { galleryBtn.thumbnail = bmp }
        }
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.UK).format(System.currentTimeMillis())

    /** Last known position, if the parent switched location on and allowed it. */
    private fun currentLocation(): Location? {
        if (!settings.saveLocation) return null
        if (!has(Manifest.permission.ACCESS_FINE_LOCATION) && !has(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            return null
        }
        return try {
            val lm = getSystemService(LOCATION_SERVICE) as LocationManager
            lm.getProviders(true).mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time }
        } catch (e: SecurityException) {
            null
        }
    }

    private fun takePhoto() {
        val ic = imageCapture ?: return
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "TC_${timestamp()}")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/ToddlerCamera")
        }
        val builder = ImageCapture.OutputFileOptions.Builder(
            contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
        )
        currentLocation()?.let { loc ->
            builder.setMetadata(ImageCapture.Metadata().apply { location = loc })
        }
        sounds.playShutter(settings.shutter)
        if (settings.screenFlash) flashScreen()
        ic.takePicture(builder.build(), ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    Toast.makeText(this@MainActivity, "Saved", Toast.LENGTH_SHORT).show()
                    refreshThumbnail(output.savedUri)
                }

                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(this@MainActivity, "Photo failed: ${exception.message}", Toast.LENGTH_LONG).show()
                }
            })
    }

    private fun toggleRecording() {
        if (starting) return
        val current = recording
        if (current != null) {
            current.stop() // the Finalize event below tidies up
            return
        }
        if (settings.recSounds) {
            // Let the "start" beep finish so it isn't recorded
            starting = true
            updateButtons()
            sounds.playRecordStart()
            uiHandler.postDelayed({
                starting = false
                startRecordingNow()
            }, 400)
        } else {
            startRecordingNow()
        }
    }

    private fun startRecordingNow() {
        val vc = videoCapture ?: run {
            updateButtons()
            return
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "TC_${timestamp()}")
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/ToddlerCamera")
        }
        var options = MediaStoreOutputOptions.Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(values)
        if (settings.videoLimitSec > 0) options = options.setDurationLimitMillis(settings.videoLimitSec * 1000L)
        currentLocation()?.let { options = options.setLocation(it) }

        var pending = vc.output.prepareRecording(this, options.build())
        if (has(Manifest.permission.RECORD_AUDIO)) pending = pending.withAudioEnabled()

        // Light on while recording (back camera only, if the phone has one)
        val cam = camera
        if (settings.videoLight && cam != null && cam.cameraInfo.hasFlashUnit()) {
            cam.cameraControl.enableTorch(true)
            torchOn = true
        }

        recording = pending.start(ContextCompat.getMainExecutor(this)) { event ->
            if (event is VideoRecordEvent.Finalize) {
                recording = null
                if (torchOn) {
                    camera?.cameraControl?.enableTorch(false)
                    torchOn = false
                }
                if (settings.recSounds) sounds.playRecordStop()
                val limitHit = event.error == VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED
                val msg = when {
                    limitHit -> "Saved (longest video reached)"
                    event.hasError() -> "Video problem (code ${event.error})"
                    else -> "Saved"
                }
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                val saved = event.outputResults.outputUri
                if (saved != Uri.EMPTY) refreshThumbnail(saved)
            }
            updateButtons()
        }
        updateButtons()
    }
}
