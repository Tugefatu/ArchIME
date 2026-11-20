from pathlib import Path

p = Path("ArchIME-0.7.2-dev/app/src/main/java/org/archime/ime/ArchInputMethodService.kt")
s = p.read_text(encoding="utf-8")

imports = [
    "import android.media.AudioAttributes",
    "import android.media.SoundPool",
]
for line in imports:
    if line not in s:
        s = s.replace("import android.os.Build\n", "import android.os.Build\n" + line + "\n")

if "private var mahjongPool: SoundPool?" not in s:
    s = s.replace(
        "    private var visibleCandidates = emptyList<InputEngine.Candidate>()\n",
        """    private var visibleCandidates = emptyList<InputEngine.Candidate>()
    private var mahjongPool: SoundPool? = null
    private var mahjongSoundId = 0
    private var mahjongSoundReady = false
    private var hapticsEnabled = true
    private var mahjongSoundEnabled = true
"""
    )

needle = "        rimeReady = install.isSuccess && engine.initialize(dir.absolutePath)\n"
if "mahjongPool = SoundPool.Builder()" not in s:
    s = s.replace(
        needle,
        needle + """
        val feedbackPrefs = getSharedPreferences("archime_settings", MODE_PRIVATE)
        hapticsEnabled = feedbackPrefs.getBoolean("haptics_enabled", true)
        mahjongSoundEnabled = feedbackPrefs.getBoolean("mahjong_sound_enabled", true)
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        mahjongPool = SoundPool.Builder().setMaxStreams(3)
            .setAudioAttributes(audioAttributes).build().also { pool ->
                pool.setOnLoadCompleteListener { _, sampleId, status ->
                    if (sampleId == mahjongSoundId && status == 0) mahjongSoundReady = true
                }
                mahjongSoundId = pool.load(this, R.raw.mahjong_tile, 1)
            }
"""
    )

# Route existing key-tap feedback through the configurable haptic + tile sound helper.
s = s.replace("performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); action()", "feedback(this); action()")
s = s.replace("performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); onBackspace()", "feedback(this); onBackspace()")
s = s.replace("performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)", "performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)")
if "private fun feedback(view: View)" not in s:
    insert_at = s.index("    private fun commit(value: String)")
    helper = """    private fun feedback(view: View) {
        if (hapticsEnabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        if (mahjongSoundEnabled && mahjongSoundReady && mahjongSoundId != 0) {
            mahjongPool?.play(mahjongSoundId, 0.42f, 0.42f, 1, 0, 1f)
        }
    }

"""
    s = s[:insert_at] + helper + s[insert_at:]

if "mahjongPool?.release()" not in s:
    s = s.replace(
        "        engine.close()\n        super.onDestroy()",
        "        mahjongPool?.release()\n        mahjongPool = null\n        engine.close()\n        super.onDestroy()"
    )

# Give the keyboard the requested charcoal base, remove key borders, and use the only accent blue on the action key.
s = s.replace("setColor(if (isDarkTheme()) Color.BLACK else 0xFFF0F2F5.toInt())", "setColor(if (isDarkTheme()) 0xFF111318.toInt() else 0xFFF0F2F5.toInt())")
s = s.replace("setStroke(dp(1), if (isDarkTheme()) 0x22000000 else 0xFFE7E9ED.toInt())", "setStroke(0, Color.TRANSPARENT)")
s = s.replace('"〰", "↵" -> if (isDarkTheme()) 0xFF30343B.toInt() else Color.WHITE', '"发送", "搜索", "确定", "↵" -> if (isDarkTheme()) 0xFF2F7BFF.toInt() else 0xFF2F7BFF.toInt()')
s = s.replace('"〰", "↵" ->', '"发送", "搜索", "确定", "↵" ->')
s = s.replace("        addKey(bottom, \"↵\", 1.05f, 48) { onEnter() }", """        val actionLabel = when (currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)) {
            EditorInfo.IME_ACTION_SEND -> "发送"
            EditorInfo.IME_ACTION_SEARCH -> "搜索"
            EditorInfo.IME_ACTION_GO -> "前往"
            else -> "确定"
        }
        addKey(bottom, actionLabel, 1.35f, 48) { onEnter() }""")
s = s.replace("        repeatingBackspace = false\n        mainHandler.removeCallbacks(backspaceRepeat)\n        engine.close()", "        repeatingBackspace = false\n        mainHandler.removeCallbacks(backspaceRepeat)\n        mahjongPool?.release()\n        mahjongPool = null\n        engine.close()")

p.write_text(s, encoding="utf-8")
print("Added configurable Android key haptics, synthesized mahjong tile sound, charcoal palette and adaptive action key.")
