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
