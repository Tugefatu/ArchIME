package org.archime.ime

import android.inputmethodservice.InputMethodService
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.content.Intent
import android.preference.PreferenceManager
import android.view.KeyEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ImageView

class ArchInputMethodService : InputMethodService() {
    override fun onCreate() {
        super.onCreate()
        keySoundPool = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        ).build().also { pool ->
            pool.setOnLoadCompleteListener { _, sampleId, status ->
                if (status == 0 && sampleId == keySoundId) keySoundReady = true
            }
            keySoundId = pool.load(this, R.raw.arch_key_click, 1)
        }
    }

    override fun onDestroy() {
        keySoundPool?.release()
        keySoundPool = null
        super.onDestroy()
    }

    private var shift = false
    private var keyboardRoot: LinearLayout? = null
    private val letterButtons = linkedMapOf<Char, Button>()
    private var modeLabel: TextView? = null
    private var keySoundPool: SoundPool? = null
    private var keySoundId: Int = 0
    private var keySoundReady = false

    override fun onCreateInputView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), dp(6), dp(6), dp(8))
            background = panel()
        }
        keyboardRoot = root
        letterButtons.clear()
        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(4), 0, dp(2), dp(4))
        }
        val mark = ImageView(this).apply {
            setImageResource(R.drawable.ic_arch_mark)
            contentDescription = "Arch Linux"
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        header.addView(mark, LinearLayout.LayoutParams(dp(34), dp(34)))
        modeLabel = TextView(this).apply {
            text = "ArchIME · English"
            textSize = 12f
            setTextColor(0xFFB9C2CE.toInt())
        }
        header.addView(modeLabel, LinearLayout.LayoutParams(0, dp(34), 1f))
        header.addView(tool("⚙") { startActivity(Intent(this, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) })
        header.addView(tool("⌄") { requestHideSelf(0) })
        root.addView(header)

        listOf("qwertyuiop", "asdfghjkl", "⇧zxcvbnm⌫").forEachIndexed { index, chars ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(if (index == 1) dp(12) else 0, dp(2), if (index == 1) dp(12) else 0, dp(2))
            }
            chars.forEach { ch ->
                val key = when (ch) { '⇧' -> "⇧"; '⌫' -> "⌫"; else -> (if (shift) ch.uppercaseChar() else ch).toString() }
                val keyView = keyButton(key, if (key == "⇧" || key == "⌫") 1.35f else 1f) {
                    when (key) {
                        "⇧" -> { shift = !shift; refreshLetters() }
                        "⌫" -> backspace()
                        else -> commit(key)
                    }
                }
                if (key.length == 1 && key[0].isLetter()) letterButtons[key[0].lowercaseChar()] = keyView
                row.addView(keyView, LinearLayout.LayoutParams(0, dp(PreferenceManager.getDefaultSharedPreferences(this).getInt("key_height", 48)), if (key == "⇧" || key == "⌫") 1.35f else 1f).apply {
                    setMargins(dp(2), 0, dp(2), 0)
                })
            }
            root.addView(row)
        }
        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        bottom.addView(keyButton("?123", 1f) { commit("?") }, weighted(1f))
        bottom.addView(keyButton(",", .8f) { commit(",") }, weighted(.8f))
        bottom.addView(keyButton("English · space", 3.2f) { commit(" ") }, weighted(3.2f))
        bottom.addView(keyButton(".", .8f) { commit(".") }, weighted(.8f))
        bottom.addView(keyButton("↵", 1f) { enter() }, weighted(1f))
        root.addView(bottom)
        return root
    }

    private fun weighted(w: Float) = LinearLayout.LayoutParams(0, dp(46), w).apply {
        setMargins(dp(2), dp(2), dp(2), dp(2))
    }

    private fun keyButton(label: String, weight: Float, action: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = if (label.length > 2) 11f else 17f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.WHITE)
        background = keyBackground(if (label == "↵") 0xFF244C68.toInt() else 0xFF292D34.toInt())
        minWidth = 0
        minimumWidth = 0
        setPadding(0, 0, 0, 0)
        stateListAnimator = null
        setOnClickListener {
            val prefs = PreferenceManager.getDefaultSharedPreferences(this@ArchInputMethodService)
            if (prefs.getBoolean("haptics_enabled", true)) {
                val strength = prefs.getInt("haptic_strength", 45).coerceIn(1, 100)
                try {
                    val vibrator = if (Build.VERSION.SDK_INT >= 31) {
                        (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
                    } else {
                        @Suppress("DEPRECATION")
                        getSystemService(VIBRATOR_SERVICE) as Vibrator
                    }
                    if (Build.VERSION.SDK_INT >= 26 && vibrator.hasAmplitudeControl()) {
                        vibrator.vibrate(VibrationEffect.createOneShot(10, (strength * 2.55f).toInt().coerceIn(1, 255)))
                    } else {
                        performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                    }
                } catch (_: Exception) {
                    performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                }
            }
            if (prefs.getBoolean("sound_enabled", false) && keySoundReady) {
                val volume = prefs.getInt("sound_volume", 35).coerceIn(0, 100) / 100f
                keySoundPool?.play(keySoundId, volume, volume, 1, 0, 1f)
            }
            action()
        }
    }

    private fun tool(label: String, action: () -> Unit) = TextView(this).apply {
        text = label
        textSize = 18f
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(dp(36), dp(34))
    }

    private fun commit(value: String) {
        currentInputConnection?.commitText(value, 1)
        if (shift) { shift = false; refreshLetters() }
    }

    private fun backspace() {
        val ic: InputConnection = currentInputConnection ?: return
        if (!ic.deleteSurroundingText(1, 0)) {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
        }
    }

    private fun enter() {
        val ic = currentInputConnection ?: return
        val action = currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
        if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(action)
        } else {
            ic.commitText("\n", 1)
        }
    }

    private fun refreshLetters() {
        letterButtons.forEach { (key, button) ->
            button.text = if (shift) key.uppercaseChar().toString() else key.toString()
        }
    }

    private fun panel() = GradientDrawable().apply {
        setColor(0xFF171A20.toInt())
        cornerRadius = dp(17).toFloat()
    }

    private fun keyBackground(color: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(9).toFloat()
        setStroke(dp(1), 0xFF343A44.toInt())
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
