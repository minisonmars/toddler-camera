package com.toddlercamera

import android.content.Context
import android.os.SystemClock
import java.security.MessageDigest
import java.security.SecureRandom

/** All the parent-controlled options, saved on the device and applied straight away. */
class Settings(ctx: Context) {

    private val p = ctx.applicationContext.getSharedPreferences("toddler", Context.MODE_PRIVATE)

    private fun getB(k: String, d: Boolean) = p.getBoolean(k, d)
    private fun putB(k: String, v: Boolean) = p.edit().putBoolean(k, v).apply()
    private fun getS(k: String, d: String) = p.getString(k, d) ?: d
    private fun putS(k: String, v: String) = p.edit().putString(k, v).apply()
    private fun getI(k: String, d: Int) = p.getInt(k, d)
    private fun putI(k: String, v: Int) = p.edit().putInt(k, v).apply()

    // Which buttons the toddler sees
    var photoEnabled: Boolean
        get() = getB("photoEnabled", true)
        set(v) = putB("photoEnabled", v)
    var videoEnabled: Boolean
        get() = getB("videoEnabled", true)
        set(v) = putB("videoEnabled", v)
    var galleryEnabled: Boolean
        get() = getB("galleryEnabled", true)
        set(v) = putB("galleryEnabled", v)

    // Gallery
    var galleryScope: String // "all" or "app"
        get() = getS("galleryScope", "all")
        set(v) = putS("galleryScope", v)
    var galleryTypes: String // "both", "photos" or "videos"
        get() = getS("galleryTypes", "both")
        set(v) = putS("galleryTypes", v)

    // Camera
    var startCamera: String // "back", "front" or "last"
        get() = getS("startCamera", "back")
        set(v) = putS("startCamera", v)
    var lastFront: Boolean
        get() = getB("lastFront", false)
        set(v) = putB("lastFront", v)
    var photoRes: String // "max", "high", "medium" or "low"
        get() = getS("photoRes", "max")
        set(v) = putS("photoRes", v)
    var flash: String // "off", "auto" or "on"
        get() = getS("flash", "off")
        set(v) = putS("flash", v)
    var shutterSound: Boolean
        get() = getB("shutterSound", true)
        set(v) = putB("shutterSound", v)
    var saveLocation: Boolean
        get() = getB("saveLocation", false)
        set(v) = putB("saveLocation", v)

    // Video
    var videoQuality: String // "sd", "hd", "fhd" or "uhd"
        get() = getS("videoQuality", "hd")
        set(v) = putS("videoQuality", v)
    var videoLimitSec: Int // 0 = no limit
        get() = getI("videoLimitSec", 120)
        set(v) = putI("videoLimitSec", v)
    var videoLight: Boolean
        get() = getB("videoLight", false)
        set(v) = putB("videoLight", v)

    // Toddler protection
    var lock: Boolean
        get() = getB("lock", false)
        set(v) = putB("lock", v)
    var timeLimitMin: Int // 0 = no limit
        get() = getI("timeLimitMin", 0)
        set(v) = putI("timeLimitMin", v)

    // PIN (stored as a salted hash, never the PIN itself)
    fun checkPin(pin: String): Boolean {
        val salt = p.getString("pinSalt", null)
        val hash = p.getString("pinHash", null)
        if (salt == null || hash == null) return pin == DEFAULT_PIN
        return sha(salt + pin) == hash
    }

    fun setPin(pin: String, recoveryWord: String) {
        val salt = randomSalt()
        val rsalt = randomSalt()
        p.edit()
            .putString("pinSalt", salt)
            .putString("pinHash", sha(salt + pin))
            .putString("recSalt", rsalt)
            .putString("recHash", sha(rsalt + normalise(recoveryWord)))
            .apply()
    }

    fun hasRecoveryWord(): Boolean = p.getString("recHash", null) != null

    fun checkRecoveryWord(word: String): Boolean {
        val salt = p.getString("recSalt", null) ?: return false
        val hash = p.getString("recHash", null) ?: return false
        return sha(salt + normalise(word)) == hash
    }

    /** Back to the default PIN (the recovery word is kept). */
    fun resetPin() {
        p.edit().remove("pinSalt").remove("pinHash").apply()
    }

    private fun normalise(s: String) = s.trim().lowercase()

    private fun randomSalt(): String {
        val b = ByteArray(8)
        SecureRandom().nextBytes(b)
        return b.joinToString("") { "%02x".format(it) }
    }

    private fun sha(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    companion object {
        const val DEFAULT_PIN = "3580"

        // value to label, in the order shown
        val GALLERY_SCOPE = listOf("all" to "Everything on this device", "app" to "Only Toddler Camera's own")
        val GALLERY_TYPES = listOf("both" to "Pictures and videos", "photos" to "Pictures only", "videos" to "Videos only")
        val START_CAMERA = listOf("back" to "Back camera", "front" to "Front camera", "last" to "Last used")
        val PHOTO_RES = listOf(
            "max" to "Maximum", "high" to "High (about 12 MP)",
            "medium" to "Medium (about 5 MP)", "low" to "Low (about 2 MP)"
        )
        val FLASH = listOf("off" to "Off", "auto" to "Auto", "on" to "On")
        val VIDEO_QUALITY = listOf(
            "sd" to "Standard (SD)", "hd" to "HD (720p)", "fhd" to "Full HD (1080p)", "uhd" to "Ultra HD (4K)"
        )
        val VIDEO_LIMIT = listOf("60" to "1 minute", "120" to "2 minutes", "300" to "5 minutes", "0" to "No limit")
        val TIME_LIMIT = listOf(
            "0" to "No limit", "5" to "5 minutes", "10" to "10 minutes",
            "15" to "15 minutes", "30" to "30 minutes", "60" to "1 hour"
        )
    }
}

/** Tracks how long the current play session has lasted, for the optional time limit. */
object Session {
    var start: Long = SystemClock.elapsedRealtime()

    fun restart() {
        start = SystemClock.elapsedRealtime()
    }

    fun expired(s: Settings): Boolean =
        s.timeLimitMin > 0 && SystemClock.elapsedRealtime() - start >= s.timeLimitMin * 60_000L
}
