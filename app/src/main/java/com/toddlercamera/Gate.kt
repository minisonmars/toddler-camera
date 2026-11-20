package com.toddlercamera

import android.app.Activity
import android.app.AlertDialog
import android.text.InputType
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast

/** Asks for the parent PIN before letting something through. */
object Gate {

    fun ask(activity: Activity, settings: Settings, onOk: () -> Unit) {
        val pin = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "PIN"
        }
        AlertDialog.Builder(activity)
            .setTitle("Parent PIN")
            .setView(boxed(activity, pin))
            .setPositiveButton("OK") { _, _ ->
                if (settings.checkPin(pin.text.toString())) onOk()
                else Toast.makeText(activity, "Wrong PIN", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Forgot PIN?") { _, _ -> forgot(activity, settings, onOk) }
            .show()
    }

    private fun forgot(activity: Activity, settings: Settings, onOk: () -> Unit) {
        if (!settings.hasRecoveryWord()) {
            AlertDialog.Builder(activity)
                .setTitle("No recovery word set")
                .setMessage(
                    "To reset the PIN, clear this app's storage in Android Settings " +
                        "(Apps > Toddler Camera > Storage). This also resets all the app's settings."
                )
                .setPositiveButton("OK", null)
                .show()
            return
        }
        val word = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            hint = "Recovery word"
        }
        AlertDialog.Builder(activity)
            .setTitle("Reset PIN")
            .setMessage("Enter your recovery word. The PIN goes back to the default, so set a new one in Settings.")
            .setView(boxed(activity, word))
            .setPositiveButton("Reset") { _, _ ->
                if (settings.checkRecoveryWord(word.text.toString())) {
                    settings.resetPin()
                    Toast.makeText(activity, "PIN reset to the default", Toast.LENGTH_LONG).show()
                    onOk()
                } else {
                    Toast.makeText(activity, "That isn't the recovery word", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun boxed(activity: Activity, view: EditText): FrameLayout =
        FrameLayout(activity).apply {
            val pad = Ui.dp(activity, 24)
            setPadding(pad, Ui.dp(activity, 8), pad, 0)
            addView(view)
        }
}
