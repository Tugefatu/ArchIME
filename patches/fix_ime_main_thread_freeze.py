from pathlib import Path

p = Path("ArchIME-0.7.2-dev/app/src/main/java/org/archime/ime/ArchInputMethodService.kt")
s = p.read_text(encoding="utf-8")

def replace_once(old: str, new: str, label: str):
    global s
    count = s.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected 1 match, found {count}")
    s = s.replace(old, new, 1)

replace_once(
    "import android.widget.Toast\n",
    "import android.widget.Toast\nimport java.util.concurrent.Executors\nimport java.util.concurrent.RejectedExecutionException\n",
    "executor imports"
)
replace_once(
    "    private var rimeReady = false\n    private var rimeInstallFailed = false\n",
    """    @Volatile private var rimeReady = false
    @Volatile private var rimeInstallFailed = false
    @Volatile private var serviceDestroyed = false
    // All librime calls, including deployment, are serialized away from the IME main thread.
    private val rimeExecutor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "ArchIME-Rime").apply { isDaemon = true }
    }
""",
    "engine executor"
)

replace_once(
"""    override fun onCreate() {
        super.onCreate()
        chineseMode = getSharedPreferences("archime_settings", MODE_PRIVATE).getBoolean("chinese_mode", true)
        val dir = filesDir.resolve("rime").apply { mkdirs() }
        val install = RimeAssetInstaller.install(this, dir)
        rimeInstallFailed = install.isFailure
        rimeReady = install.isSuccess && engine.initialize(dir.absolutePath)
    }
""",
"""    override fun onCreate() {
        super.onCreate()
        chineseMode = getSharedPreferences("archime_settings", MODE_PRIVATE).getBoolean("chinese_mode", true)

        // First-run extraction and Rime's dictionary/schema deployment can take
        // seconds. Never execute either operation on Android's main/IME thread.
        rimeExecutor.execute {
            val ready = try {
                val dir = filesDir.resolve("rime").apply { mkdirs() }
                val install = RimeAssetInstaller.install(this, dir)
                val initialized = install.isSuccess && engine.initialize(dir.absolutePath)
                rimeInstallFailed = !initialized
                initialized
            } catch (_: Throwable) {
                rimeInstallFailed = true
                false
            }
            rimeReady = ready
            mainHandler.post {
                if (serviceDestroyed) return@post
                if (::status.isInitialized) status.text = inputStatus()
                if (::candidates.isInitialized) refreshCandidates(emptyList())
                if (::root.isInitialized) root.invalidate()
            }
        }
    }
""",
    "async Rime onCreate"
)

anchor = """    private fun onLetter(raw: Char) {
"""
helper = """    private fun runRimeAction(
        action: () -> InputEngine.Update,
        onUpdate: (InputEngine.Update) -> Unit = { renderUpdate(it) },
        onUnavailable: () -> Unit = {}
    ) {
        if (serviceDestroyed) return
        try {
            rimeExecutor.execute {
                val update = try {
                    if (rimeReady) action() else null
                } catch (_: Throwable) {
                    null
                }
                mainHandler.post {
                    if (serviceDestroyed) return@post
                    if (update != null) onUpdate(update) else onUnavailable()
                }
            }
        } catch (_: RejectedExecutionException) {
            if (!serviceDestroyed) onUnavailable()
        }
    }

"""
if s.count(anchor) != 1:
    raise SystemExit("Rime action helper insertion point missing")
s = s.replace(anchor, helper + anchor, 1)

replace_once(
"""        val commaKey = addKey(bottom, ",", .72f, 48) { commit(if (isChineseActive()) "，" else ",") }
        commaKey.setOnLongClickListener {
            if (isChineseActive() && rimeReady) renderUpdate(engine.processKey(';'.code)) else commit(";")
            true
        }
""",
"""        val commaKey = addKey(bottom, ",", .72f, 48) { commit(if (isChineseActive()) "，" else ",") }
        commaKey.setOnLongClickListener {
            if (isChineseActive()) {
                runRimeAction({ engine.processKey(';'.code) }, onUnavailable = { commit(";") })
            } else commit(";")
            true
        }
""",
    "comma native call"
)

replace_once(
"""            if (rimeReady) renderUpdate(engine.clear()) else refreshCandidates(emptyList())
""",
"""            runRimeAction({ engine.clear() }, onUnavailable = { refreshCandidates(emptyList()) })
""",
    "mode clear"
)

replace_once(
"""    private fun onLetter(raw: Char) {
        val c = if (shifted) raw.uppercaseChar() else raw
        shifted = false
        refreshKeyLabels()
        // Never swallow key presses if the bundled Rime engine cannot initialize.
        if (!isChineseActive() || !rimeReady) {
            if (isChineseActive() && !rimeReady) {
                status.text = "ArchIME · Rime 引擎未就绪，暂用英文输入"
            }
            commit(c.toString())
            return
        }
        // Rime schemas expect lowercase ASCII even when visual Shift is enabled.
        renderUpdate(engine.processKey(raw.lowercaseChar().code))
    }
""",
"""    private fun onLetter(raw: Char) {
        val value = if (shifted) raw.uppercaseChar() else raw
        shifted = false
        refreshKeyLabels()
        if (!isChineseActive()) {
            commit(value.toString())
            return
        }
        // Queued behind initialization; if Rime fails, preserve usable English input.
        runRimeAction(
            { engine.processKey(raw.lowercaseChar().code) },
            onUnavailable = { commit(value.toString()) }
        )
    }
""",
    "letter engine processing"
)

replace_once(
"""    private fun onDigit(digit: Char) {
        val candidateIndex = digit.digitToIntOrNull()?.let { if (it == 0) 9 else it - 1 }
        if (isChineseActive() && rimeReady && candidateIndex != null && candidateIndex < visibleCandidates.size) {
            renderUpdate(engine.selectCandidate(candidateIndex))
        } else {
            commit(digit.toString())
        }
    }
""",
"""    private fun onDigit(digit: Char) {
        val candidateIndex = digit.digitToIntOrNull()?.let { if (it == 0) 9 else it - 1 }
        if (isChineseActive() && candidateIndex != null && candidateIndex < visibleCandidates.size) {
            runRimeAction({ engine.selectCandidate(candidateIndex) }, onUnavailable = { commit(digit.toString()) })
        } else {
            commit(digit.toString())
        }
    }
""",
    "digit engine processing"
)

replace_once(
"""    private fun onBackspace() {
        if (isChineseActive() && rimeReady) renderUpdate(engine.backspace())
        else currentInputConnection?.deleteSurroundingText(1, 0)
    }
""",
"""    private fun onBackspace() {
        if (isChineseActive()) {
            runRimeAction(
                { engine.backspace() },
                onUnavailable = { currentInputConnection?.deleteSurroundingText(1, 0) }
            )
        } else currentInputConnection?.deleteSurroundingText(1, 0)
    }
""",
    "backspace engine processing"
)

replace_once(
"""    private fun onSpace() {
        if (isChineseActive() && rimeReady) {
            val update = engine.processKey(' '.code)
            renderUpdate(update)
            if (update.composingText.isEmpty() && update.candidates.isEmpty() && update.commitText.isEmpty()) currentInputConnection?.commitText(" ", 1)
        } else commit(" ")
    }
""",
"""    private fun onSpace() {
        if (isChineseActive()) {
            runRimeAction(
                { engine.processKey(' '.code) },
                onUpdate = { update ->
                    renderUpdate(update)
                    if (update.composingText.isEmpty() && update.candidates.isEmpty() && update.commitText.isEmpty()) {
                        currentInputConnection?.commitText(" ", 1)
                    }
                },
                onUnavailable = { commit(" ") }
            )
        } else commit(" ")
    }
""",
    "space engine processing"
)

replace_once(
"""            addCandidateNav("‹") { renderUpdate(engine.changePage(true)) }
""",
"""            addCandidateNav("‹") { runRimeAction({ engine.changePage(true) }) }
""",
    "previous candidate page"
)
replace_once(
"""                setOnClickListener { renderUpdate(engine.selectCandidate(index)) }
""",
"""                setOnClickListener { runRimeAction({ engine.selectCandidate(index) }) }
""",
    "candidate selection"
)
replace_once(
"""            addCandidateNav("›") { renderUpdate(engine.changePage(false)) }
""",
"""            addCandidateNav("›") { runRimeAction({ engine.changePage(false) }) }
""",
    "next candidate page"
)

start = s.index("    private fun inputStatus():")
end = s.index("\n    private fun isSecureInput", start)
new_status = """    private fun inputStatus(): String = when {
        secureField -> "ArchIME · 安全输入 · 不启用中文引擎"
        chineseMode -> when {
            rimeReady -> "ArchIME · 微软双拼已就绪"
            rimeInstallFailed -> "ArchIME · 中文引擎不可用，英文仍可用"
            else -> "ArchIME · 中文引擎正在后台启动"
        }
        else -> "ArchIME · English mode"
    }
"""
s = s[:start] + new_status + s[end:]

replace_once(
"""        if (rimeReady) renderUpdate(engine.clear())
        currentInputConnection?.finishComposingText()
""",
"""        if (isChineseActive()) runRimeAction({ engine.clear() })
        currentInputConnection?.finishComposingText()
""",
    "onStartInputView clear"
)

replace_once(
"""    override fun onFinishInput() {
        if (rimeReady) {
            val update = engine.clear()
            currentInputConnection?.setComposingText("", 1)
            refreshCandidates(update.candidates)
        }
        super.onFinishInput()
    }

    override fun onDestroy() {
        repeatingBackspace = false
        mainHandler.removeCallbacks(backspaceRepeat)
        engine.close()
        super.onDestroy()
    }
""",
"""    override fun onFinishInput() {
        if (isChineseActive()) runRimeAction({ engine.clear() })
        currentInputConnection?.finishComposingText()
        super.onFinishInput()
    }

    override fun onDestroy() {
        serviceDestroyed = true
        repeatingBackspace = false
        mainHandler.removeCallbacks(backspaceRepeat)
        // Queue native teardown after any init or key operation already in flight.
        try {
            rimeExecutor.execute { try { engine.close() } catch (_: Throwable) { } }
        } catch (_: RejectedExecutionException) { }
        rimeExecutor.shutdown()
        super.onDestroy()
    }
""",
    "safe asynchronous teardown"
)

p.write_text(s, encoding="utf-8")
print("Moved Rime install/deploy and all JNI session operations off the IME main thread.")
