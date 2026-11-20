package com.toddlercamera

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings as AndroidSettings
import java.io.File
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

/** Parent settings. Every change is saved and applied straight away. */
class ParentSettingsActivity : Activity() {

    private lateinit var settings: Settings
    private lateinit var sounds: Sounds
    private lateinit var col: LinearLayout
    private lateinit var locationSwitch: Switch
    private lateinit var recoveryInfo: TextView

    private fun dp(v: Int) = Ui.dp(this, v)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = Settings(this)
        sounds = Sounds()

        col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(24), dp(16), dp(32))
        }
        col.addView(TextView(this).apply {
            text = "Parent settings"
            textSize = 26f
        })

        header("What your toddler can use")
        switchRow("Photo camera", settings.photoEnabled) { on ->
            if (!on && !settings.videoEnabled) false.also { toast("Keep at least photo or video switched on") }
            else { settings.photoEnabled = on; on }
        }
        switchRow("Video camera", settings.videoEnabled) { on ->
            if (!on && !settings.photoEnabled) false.also { toast("Keep at least photo or video switched on") }
            else { settings.videoEnabled = on; on }
        }
        switchRow("Gallery", settings.galleryEnabled) { on -> settings.galleryEnabled = on; on }

        header("Gallery")
        switchRow("Show the last picture on the gallery button", settings.galleryThumb) { on ->
            settings.galleryThumb = on
            on
        }
        choiceRow("Show", Settings.GALLERY_SCOPE, settings.galleryScope) { settings.galleryScope = it }
        choiceRow("Which kinds", Settings.GALLERY_TYPES, settings.galleryTypes) { settings.galleryTypes = it }

        header("Camera")
        choiceRow("Start with", Settings.START_CAMERA, settings.startCamera) { settings.startCamera = it }
        choiceRow("Picture size", Settings.PHOTO_RES, settings.photoRes) { settings.photoRes = it }
        choiceRow("Flash (pictures)", Settings.FLASH, settings.flash) { settings.flash = it }
        note("Flash only works on a camera that has one, usually the back camera.")
        choiceRow("Shutter sound", Settings.SHUTTER, settings.shutter) {
            settings.shutter = it
            sounds.playShutter(it) // a quick preview
        }
        note("Camera click is the phone's own sound. The others play at the media volume.")
        switchRow("Recording start and stop sounds", settings.recSounds) { on -> settings.recSounds = on; on }
        switchRow("Flash the screen when a picture is taken", settings.screenFlash) { on ->
            settings.screenFlash = on
            on
        }
        locationSwitch = switchRow("Save location in pictures and videos", settings.saveLocation) { on ->
            if (on && !hasLocationPermission()) {
                requestPermissions(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 21
                )
                false // switched on once the permission is granted
            } else {
                settings.saveLocation = on
                on
            }
        }
        note("Uses the phone's last known position, so it can be a little out of date.")

        header("Video")
        choiceRow("Quality", Settings.VIDEO_QUALITY, settings.videoQuality) { settings.videoQuality = it }
        note("If the phone can't do the chosen quality, the closest it can do is used.")
        choiceRow("Longest video", Settings.VIDEO_LIMIT, settings.videoLimitSec.toString()) {
            settings.videoLimitSec = it.toInt()
        }
        switchRow("Light on while recording", settings.videoLight) { on -> settings.videoLight = on; on }

        header("Toddler protection")
        switchRow("Toddler lock (screen pinning)", settings.lock) { on ->
            settings.lock = on
            try {
                if (on) startLockTask() else stopLockTask()
            } catch (e: Exception) {
            }
            on
        }
        note("Stops your toddler leaving the app. To leave without this menu, hold Back and Overview together.")
        choiceRow("Play time limit", Settings.TIME_LIMIT, settings.timeLimitMin.toString()) {
            settings.timeLimitMin = it.toInt()
            Session.restart()
        }
        note("When time is up the screen goes blank until you hold the gear and enter the PIN.")

        header("Parent PIN")
        recoveryInfo = note("")
        updateRecoveryInfo()
        button("Change PIN") { showChangePin() }

        header("App updates")
        updateStatus = note("Version ${Updater.installedVersion(this)}")
        button("Check for updates") { checkForUpdates(prompt = true) }

        header("This device")
        note(String.format(Locale.UK, "Free space: %.1f GB", freeBytes() / 1e9))
        button("Back to the camera") { finish() }
        button("Close the app") {
            try {
                stopLockTask()
            } catch (e: Exception) {
            }
            finishAffinity()
        }

        setContentView(ScrollView(this).apply { addView(col) })

        // Opening the parent area quietly looks for an update and asks Install or Skip
        checkForUpdates(prompt = false)
    }

    // ---------- App updates ----------
    private lateinit var updateStatus: TextView

    /** [prompt] true = the button was tapped, so report every result; false = only speak up for a new, unskipped version. */
    private fun checkForUpdates(prompt: Boolean) {
        val prefs = getSharedPreferences("updates", MODE_PRIVATE)
        if (prompt) updateStatus.text = "Checking..."
        Thread {
            try {
                val latest = Updater.fetchLatest()
                val current = Updater.installedVersion(this)
                runOnUiThread {
                    if (isFinishing) return@runOnUiThread
                    val newer = latest != null && Updater.isNewer(latest.version, current)
                    if (latest == null || !newer) {
                        if (prompt) updateStatus.text = "You have the latest version ($current)"
                    } else {
                        updateStatus.text = "Version ${latest.version} is available (you have $current)"
                        if (prompt || latest.version != prefs.getString("skippedVersion", "")) {
                            AlertDialog.Builder(this)
                                .setTitle("Update available")
                                .setMessage("Version ${latest.version} is available (you have $current). Install it now?")
                                .setPositiveButton("Install") { _, _ -> startUpdate(latest) }
                                .setNegativeButton("Skip") { _, _ ->
                                    prefs.edit().putString("skippedVersion", latest.version).apply()
                                }
                                .show()
                        }
                    }
                }
            } catch (e: Exception) {
                if (prompt) runOnUiThread { updateStatus.text = "Couldn't check: ${e.message}" }
            }
        }.start()
    }

    private fun startUpdate(release: Updater.Release) {
        if (!packageManager.canRequestPackageInstalls()) {
            AlertDialog.Builder(this)
                .setTitle("Allow updates")
                .setMessage(
                    "Android needs you to allow this app to install updates. Tap Open settings, switch on " +
                        "\"Allow from this source\", then come back and tap Check for updates again."
                )
                .setPositiveButton("Open settings") { _, _ ->
                    startActivity(
                        Intent(AndroidSettings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))
                    )
                }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }
        // Screen pinning would block Android's install screen
        try {
            stopLockTask()
        } catch (e: Exception) {
        }
        val file = File(cacheDir, "update.apk")
        updateStatus.text = "Downloading..."
        Thread {
            try {
                Updater.download(release.apkUrl, file) { percent ->
                    runOnUiThread { updateStatus.text = "Downloading... $percent%" }
                }
                runOnUiThread { updateStatus.text = "Installing... confirm on the screen that appears" }
                Updater.install(this, file)
            } catch (e: Exception) {
                runOnUiThread { updateStatus.text = "Update failed: ${e.message}" }
            }
        }.start()
    }

    // ---------- Row helpers ----------

    private fun header(text: String) {
        col.addView(TextView(this).apply {
            this.text = text
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Ui.BLUE)
            setPadding(0, dp(28), 0, dp(4))
        })
    }

    private fun note(text: String): TextView {
        val tv = TextView(this).apply {
            this.text = text
            textSize = 13f
            setTextColor(Color.GRAY)
            setPadding(0, dp(2), 0, dp(6))
        }
        col.addView(tv)
        return tv
    }

    /** onChange returns the state the switch should end up in. */
    private fun switchRow(label: String, checked: Boolean, onChange: (Boolean) -> Boolean): Switch {
        val sw = Switch(this).apply {
            text = label
            textSize = 16f
            isChecked = checked
            setPadding(0, dp(10), 0, dp(10))
        }
        sw.setOnCheckedChangeListener { button, isOn ->
            val result = onChange(isOn)
            if (result != isOn) {
                // Put it back without re-triggering this listener
                button.setOnCheckedChangeListener(null)
                (button as Switch).isChecked = result
                button.setOnCheckedChangeListener { b2, on2 ->
                    val r2 = onChange(on2)
                    if (r2 != on2) (b2 as Switch).isChecked = r2
                }
            }
        }
        col.addView(sw)
        return sw
    }

    private fun choiceRow(
        label: String,
        options: List<Pair<String, String>>,
        current: String,
        onPick: (String) -> Unit
    ) {
        col.addView(TextView(this).apply {
            text = label
            textSize = 16f
            setPadding(0, dp(10), 0, 0)
        })
        val spinner = Spinner(this)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, options.map { it.second })
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        val start = options.indexOfFirst { it.first == current }.coerceAtLeast(0)
        spinner.setSelection(start)
        var shown = start
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position != shown) {
                    shown = position
                    onPick(options[position].first)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        col.addView(spinner)
    }

    private fun button(label: String, onClick: () -> Unit) {
        col.addView(
            Button(this).apply {
                text = label
                isAllCaps = false
                setOnClickListener { onClick() }
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        sounds.release()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    // ---------- Location permission ----------

    private fun hasLocationPermission() =
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @Deprecated("Framework callback")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 21) {
            if (hasLocationPermission()) {
                settings.saveLocation = true
                locationSwitch.isChecked = true
            } else {
                toast("Location permission was not given")
            }
        }
    }

    // ---------- PIN ----------

    private fun updateRecoveryInfo() {
        recoveryInfo.text =
            if (settings.hasRecoveryWord()) "A recovery word is set, for \"Forgot PIN?\"."
            else "Using the default PIN. Change it and set a recovery word."
    }

    private fun showChangePin() {
        val newPin = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "New PIN (4 to 8 digits)"
        }
        val again = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "New PIN again"
        }
        val word = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            hint = "Recovery word, for a forgotten PIN"
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), 0)
            addView(newPin)
            addView(again)
            addView(word)
        }
        AlertDialog.Builder(this)
            .setTitle("Change PIN")
            .setView(box)
            .setPositiveButton("Save") { _, _ ->
                val pin = newPin.text.toString()
                when {
                    pin.length !in 4..8 -> toast("The PIN needs 4 to 8 digits")
                    pin != again.text.toString() -> toast("The two PINs don't match")
                    word.text.toString().isBlank() -> toast("Please enter a recovery word")
                    else -> {
                        settings.setPin(pin, word.text.toString())
                        updateRecoveryInfo()
                        toast("PIN changed")
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun freeBytes(): Long =
        try {
            StatFs(Environment.getDataDirectory().path).availableBytes
        } catch (e: Exception) {
            0L
        }
}
