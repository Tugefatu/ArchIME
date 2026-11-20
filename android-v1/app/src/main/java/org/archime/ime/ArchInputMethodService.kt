package org.archime.ime

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.preference.PreferenceManager
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import org.archime.ime.engine.InputEngine
import org.archime.ime.engine.NativeRimeEngine
import org.archime.ime.engine.RimeAssetInstaller
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

/**
 * ArchIME Android keyboard.
 * Rime initialization and every native call are serialized on a worker thread;
 * the IME main thread only creates views and updates the current editor.
 */
class ArchInputMethodService : InputMethodService() {
    private val engine = NativeRimeEngine()
    private val worker = Executors.newSingleThreadExecutor { task ->
        Thread(task, "ArchIME-Rime").apply { isDaemon = true }
    }
    private val main = Handler(Looper.getMainLooper())

    @Volatile private var ready = false
    @Volatile private var destroyed = false
    private var chineseMode = true
    private var secureField = false
    private var shifted = false

    private var candidateRow: LinearLayout? = null
    private var status: TextView? = null
    private var modeButton: Button? = null
    private val letterButtons = linkedMapOf<Char, Button>()
    private var candidates = emptyList<InputEngine.Candidate>()

    private var soundPool: SoundPool? = null
    private var soundId = 0
    @Volatile private var soundReady = false

    override fun onCreate() {
        super.onCreate()
        chineseMode = getSharedPreferences("archime_settings", MODE_PRIVATE)
            .getBoolean("chinese_mode", true)

        soundPool = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build().also { pool ->
                pool.setOnLoadCompleteListener { _, sample, result ->
                    if (result == 0 && sample == soundId) soundReady = true
                }
                soundId = pool.load(this, R.raw.arch_key_click, 1)
            }

        worker.execute {
            val initialized = try {
                val dir = filesDir.resolve("rime").apply { mkdirs() }
                RimeAssetInstaller.install(this, dir).getOrThrow()
                engine.initialize(dir.absolutePath)
            } catch (_: Throwable) {
                false
            }
            ready = initialized
            main.post {
                if (!destroyed) {
                    refreshStatus()
                    refreshCandidates(emptyList())
                }
            }
        }
    }

    override fun onCreateInputView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), dp(5), dp(6), dp(7))
            background = panel()
        }

        // Keep the Arch mark at the top-left in every keyboard mode.
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(
            ImageView(this).apply {
                setImageResource(R.drawable.ic_arch_mark)
                contentDescription = "Arch Linux"
                scaleType = ImageView.ScaleType.FIT_CENTER
            },
            LinearLayout.LayoutParams(dp(34), dp(34))
        )

        status = TextView(this).apply {
            text = statusText()
            textSize = 11f
            setTextColor(0xFFB9C2CE.toInt())
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), 0, 0, 0)
        }
        header.addView(status, LinearLayout.LayoutParams(0, dp(34), 1f))
        header.addView(tool("Aa") {
            shifted = !shifted
            refreshLetters()
        })
        header.addView(tool("⚙") {
            startActivity(
                Intent(this, SettingsActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        })
        header.addView(tool("⌄") { requestHideSelf(0) })
        root.addView(header)

        candidateRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(36)
        }
        root.addView(candidateRow)

        listOf("qwertyuiop", "asdfghjkl", "zxcvbnm").forEachIndexed { index, chars ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                if (index == 1) setPadding(dp(10), 0, dp(10), 0)
            }
            if (index == 2) {
                row.addView(key("⇧") {
                    shifted = !shifted
                    refreshLetters()
                }, weighted(1.25f))
            }
            chars.forEach { char ->
                val button = key(char.toString()) { onLetter(char) }
                letterButtons[char] = button
                row.addView(button, weighted(1f))
            }
            if (index == 2) {
                row.addView(key("⌫") { onBackspace() }, weighted(1.25f))
            }
            root.addView(row)
        }

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        bottom.addView(key("?123") { commit("?123") }, weighted(.95f))
        bottom.addView(key(",") { commit(if (isChinese()) "，" else ",") }, weighted(.75f))
        bottom.addView(key("空格") { onSpace() }, weighted(2.7f))
        modeButton = key(if (chineseMode) "双/英" else "英/双") {
            chineseMode = !chineseMode
            getSharedPreferences("archime_settings", MODE_PRIVATE).edit()
                .putBoolean("chinese_mode", chineseMode).apply()
            if (isChinese()) runRime({ engine.clear() })
            refreshMode()
            refreshStatus()
        }
        bottom.addView(modeButton, weighted(1.05f))
        bottom.addView(key("↵") { enter() }, weighted(.9f))
        root.addView(bottom)

        refreshCandidates(emptyList())
        refreshLetters()
        refreshMode()
        return root
    }

    private fun weighted(weight: Float) =
        LinearLayout.LayoutParams(
            0,
            dp(PreferenceManager.getDefaultSharedPreferences(this).getInt("key_height", 48).coerceIn(40, 64)),
            weight
        ).apply {
            setMargins(dp(2), dp(2), dp(2), dp(2))
        }

    private fun key(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = if (label.length > 1) 11f else 17f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.WHITE)
        background = keyBackground(if (label == "↵") 0xFF244C68.toInt() else 0xFF292D34.toInt())
        minWidth = 0
        minimumWidth = 0
        setPadding(0, 0, 0, 0)
        stateListAnimator = null
        setOnClickListener {
            feedback()
            action()
        }
    }

    private fun tool(label: String, action: () -> Unit) = TextView(this).apply {
        text = label
        textSize = 17f
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(dp(34), dp(34))
    }

    private fun feedback() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
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
                    vibrator.vibrate(
                        VibrationEffect.createOneShot(
                            10,
                            (strength * 2.55f).toInt().coerceIn(1, 255)
                        )
                    )
                } else {
                    performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                }
            } catch (_: Exception) {
                performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            }
        }
        if (prefs.getBoolean("sound_enabled", false) && soundReady) {
            val volume = prefs.getInt("sound_volume", 35).coerceIn(0, 100) / 100f
            soundPool?.play(soundId, volume, volume, 1, 0, 1f)
        }
    }

    private fun onLetter(char: Char) {
        val value = if (shifted) char.uppercaseChar() else char
        shifted = false
        refreshLetters()
        if (!isChinese()) {
            commit(value.toString())
            return
        }
        if (!ready) {
            refreshStatus()
            return
        }
        runRime({ engine.processKey(char.lowercaseChar().code) })
    }

    private fun onSpace() {
        if (!isChinese()) {
            commit(" ")
            return
        }
        if (!ready) return
        runRime(
            action = { engine.processKey(' '.code) },
            onUpdate = { update ->
                render(update)
                if (update.composingText.isEmpty() &&
                    update.candidates.isEmpty() &&
                    update.commitText.isEmpty()
                ) commit(" ")
            }
        )
    }

    private fun onBackspace() {
        if (isChinese() && ready) {
            runRime(
                action = { engine.backspace() },
                onUpdate = { update ->
                    if (update.composingText.isEmpty() && update.candidates.isEmpty()) {
                        deleteOne()
                    } else render(update)
                }
            )
        } else {
            deleteOne()
        }
    }

    private fun deleteOne() {
        val ic: InputConnection = currentInputConnection ?: return
        if (!ic.deleteSurroundingText(1, 0)) {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
        }
    }

    private fun enter() {
        val ic = currentInputConnection ?: return
        val action = currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
            ?: EditorInfo.IME_ACTION_NONE
        if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(action)
        } else {
            ic.commitText("\n", 1)
        }
    }

    private fun runRime(
        action: () -> InputEngine.Update,
        onUpdate: (InputEngine.Update) -> Unit = { render(it) }
    ) {
        if (destroyed) return
        try {
            worker.execute {
                val update = try {
                    if (ready) action() else null
                } catch (_: Throwable) {
                    null
                }
                main.post {
                    if (!destroyed && update != null) onUpdate(update)
                }
            }
        } catch (_: RejectedExecutionException) {
            // Service is shutting down; ignore the stale key event.
        }
    }

    private fun render(update: InputEngine.Update) {
        if (update.commitText.isNotEmpty()) {
            currentInputConnection?.commitText(update.commitText, 1)
        }
        if (update.ready) {
            currentInputConnection?.setComposingText(update.composingText, 1)
        }
        refreshCandidates(update.candidates)
        refreshStatus()
    }

    private fun refreshCandidates(items: List<InputEngine.Candidate>) {
        val row = candidateRow ?: return
        row.removeAllViews()
        candidates = items
        if (items.isNotEmpty()) {
            addCandidateNav(row, "‹") { runRime({ engine.changePage(true) }) }
            items.take(5).forEachIndexed { index, candidate ->
                row.addView(
                    TextView(this).apply {
                        text = candidate.text
                        textSize = 16f
                        gravity = Gravity.CENTER
                        setTextColor(Color.WHITE)
                        setPadding(dp(5), 0, dp(5), 0)
                        setOnClickListener {
                            runRime({ engine.selectCandidate(index) })
                        }
                    },
                    LinearLayout.LayoutParams(0, dp(36), 1f)
                )
            }
            addCandidateNav(row, "›") { runRime({ engine.changePage(false) }) }
        } else {
            row.addView(TextView(this).apply {
                text = when {
                    !isChinese() -> "English"
                    ready -> "微软双拼 · 输入中"
                    else -> "Rime 正在启动…"
                }
                textSize = 11f
                setTextColor(0xFF929BA8.toInt())
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(5), 0, 0, 0)
            })
        }
    }

    private fun addCandidateNav(row: LinearLayout, label: String, action: () -> Unit) {
        row.addView(
            TextView(this).apply {
                text = label
                textSize = 22f
                gravity = Gravity.CENTER
                setTextColor(0xFFB9C2CE.toInt())
                setOnClickListener { action() }
            },
            LinearLayout.LayoutParams(dp(28), dp(36))
        )
    }

    private fun refreshLetters() {
        letterButtons.forEach { (char, button) ->
            button.text = if (shifted) char.uppercaseChar().toString() else char.toString()
        }
    }

    private fun refreshMode() {
        modeButton?.text = if (chineseMode) "双/英" else "英/双"
    }

    private fun isChinese() = chineseMode && !secureField

    private fun statusText() = when {
        secureField -> "ArchIME · 安全输入"
        isChinese() && ready -> "ArchIME · 微软双拼"
        isChinese() -> "ArchIME · Rime 启动中"
        else -> "ArchIME · English"
    }

    private fun refreshStatus() {
        status?.text = statusText()
    }

    private fun commit(value: String) {
        currentInputConnection?.commitText(value, 1)
        if (shifted) {
            shifted = false
            refreshLetters()
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

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun isSecure(inputType: Int): Boolean {
        val inputClass = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        return (inputClass == InputType.TYPE_CLASS_TEXT && variation in setOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        )) || (inputClass == InputType.TYPE_CLASS_NUMBER &&
            variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        secureField = isSecure(info?.inputType ?: InputType.TYPE_NULL)
        currentInputConnection?.finishComposingText()
        if (isChinese() && ready) runRime({ engine.clear() })
        refreshStatus()
        refreshMode()
        refreshLetters()
    }

    override fun onFinishInput() {
        if (ready) runRime({ engine.clear() })
        currentInputConnection?.finishComposingText()
        super.onFinishInput()
    }

    override fun onDestroy() {
        destroyed = true
        soundPool?.release()
        soundPool = null
        try {
            worker.execute { engine.close() }
        } catch (_: RejectedExecutionException) {
            // Engine was never initialized or the worker already stopped.
        }
        worker.shutdown()
        super.onDestroy()
    }
}
