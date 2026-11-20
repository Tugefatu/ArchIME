from pathlib import Path

p = Path("ArchIME-0.7.2-dev/app/src/main/java/org/archime/ime/ArchInputMethodService.kt")
s = p.read_text(encoding="utf-8")

def once(old: str, new: str, label: str):
    global s
    n = s.count(old)
    if n != 1:
        raise SystemExit(f"{label}: expected exactly one match, got {n}")
    s = s.replace(old, new, 1)

once(
    "import android.widget.Toast\n",
    "import android.widget.Toast\nimport java.util.concurrent.Executors\nimport java.util.concurrent.RejectedExecutionException\n",
    "worker imports"
)
once(
    "    private var rimeReady = false\n    private var rimeInstallFailed = false\n",
    """    @Volatile private var rimeReady = false
    @Volatile private var rimeInstallFailed = false
    @Volatile private var serviceDestroyed = false
    private val rimeWorker = Executors.newSingleThreadExecutor { task ->
        Thread(task, "ArchIME-RimeInit").apply { isDaemon = true }
    }
""",
    "worker state"
)
once(
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

        // First-run asset extraction and librime maintenance/deployment can take
        // seconds. Keep all of that work off Android's main/IME thread.
        rimeWorker.execute {
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
                if (::candidates.isInitialized) {
                    refreshCandidates(emptyList())
                }
                if (::root.isInitialized) root.invalidate()
            }
        }
    }
""",
    "async initialization"
)
once(
"""    override fun onDestroy() {
        repeatingBackspace = false
        mainHandler.removeCallbacks(backspaceRepeat)
        engine.close()
        super.onDestroy()
    }
""",
"""    override fun onDestroy() {
        serviceDestroyed = true
        repeatingBackspace = false
        mainHandler.removeCallbacks(backspaceRepeat)
        // Native teardown is serialized after startup so it cannot race initialize().
        try {
            rimeWorker.execute { try { engine.close() } catch (_: Throwable) { } }
        } catch (_: RejectedExecutionException) { }
        rimeWorker.shutdown()
        super.onDestroy()
    }
""",
    "serialized teardown"
)

p.write_text(s, encoding="utf-8")
print("Rime asset installation and native deployment now run off the IME main thread.")


# Second pass: serialize every Rime session operation on the same worker.
s = p.read_text(encoding="utf-8")

def replace_one(old: str, new: str, label: str):
    global s
    count = s.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one match, found {count}")
    s = s.replace(old, new, 1)

helper_anchor = "    private fun onLetter(raw: Char) {"
helper = """    private fun runRimeAction(
        action: () -> InputEngine.Update,
        onUpdate: (InputEngine.Update) -> Unit = { renderUpdate(it) },
        onUnavailable: () -> Unit = {}
    ) {
        if (serviceDestroyed) return
        try {
            rimeWorker.execute {
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
if s.count(helper_anchor) != 1:
    raise SystemExit("Rime worker helper insertion point missing")
s = s.replace(helper_anchor, helper + helper_anchor, 1)

replace_one(
"""            if (isChineseActive() && rimeReady) renderUpdate(engine.processKey(';'.code)) else commit(";")
""",
"""            if (isChineseActive()) runRimeAction(
                { engine.processKey(';'.code) },
                onUnavailable = { commit(";") }
            ) else commit(";")
""",
    "semicolon key"
)
replace_one(
"""            if (rimeReady) renderUpdate(engine.clear()) else refreshCandidates(emptyList())
""",
"""            if (isChineseActive()) runRimeAction({ engine.clear() })
            else refreshCandidates(emptyList())
""",
    "mode clear"
)
replace_one(
"""    private fun onLetter(raw: Char) {
        val c = if (shifted) raw.uppercaseChar() else raw
        shifted = false
        refreshKeyLabels()
        if (!isChineseActive()) { commit(c.toString()); return }
        if (!rimeReady) {
            status.text = "中文引擎尚未接入；切换「英」可正常输入"
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
        if (!isChineseActive()) { commit(value.toString()); return }
        runRimeAction(
            { engine.processKey(raw.lowercaseChar().code) },
            onUnavailable = { commit(value.toString()) }
        )
    }
""",
    "letter key"
)
replace_one(
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
            runRimeAction(
                { engine.selectCandidate(candidateIndex) },
                onUnavailable = { commit(digit.toString()) }
            )
        } else commit(digit.toString())
    }
""",
    "digit key"
)
replace_one(
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
    "backspace key"
)
replace_one(
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
    "space key"
)
replace_one(
"""            addCandidateNav("‹") { renderUpdate(engine.changePage(true)) }
""",
"""            addCandidateNav("‹") { runRimeAction({ engine.changePage(true) }) }
""",
    "previous candidate page"
)
replace_one(
"""                setOnClickListener { renderUpdate(engine.selectCandidate(index)) }
""",
"""                setOnClickListener { runRimeAction({ engine.selectCandidate(index) }) }
""",
    "candidate selection"
)
replace_one(
"""            addCandidateNav("›") { renderUpdate(engine.changePage(false)) }
""",
"""            addCandidateNav("›") { runRimeAction({ engine.changePage(false) }) }
""",
    "next candidate page"
)
replace_one(
"""        if (rimeReady) renderUpdate(engine.clear())
""",
"""        if (isChineseActive()) runRimeAction({ engine.clear() })
""",
    "start input clear"
)
replace_one(
"""    override fun onFinishInput() {
        if (rimeReady) {
            val update = engine.clear()
            currentInputConnection?.setComposingText("", 1)
            refreshCandidates(update.candidates)
        }
        super.onFinishInput()
    }
""",
"""    override fun onFinishInput() {
        if (isChineseActive()) runRimeAction({ engine.clear() })
        currentInputConnection?.finishComposingText()
        super.onFinishInput()
    }
""",
    "finish input clear"
)

p.write_text(s, encoding="utf-8")
print("All Rime session key, candidate, page, and clear operations are serialized on the background worker.")
