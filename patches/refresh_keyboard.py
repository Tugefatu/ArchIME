from pathlib import Path
p = Path("ArchIME-0.7.2-dev/app/src/main/java/org/archime/ime/ArchInputMethodService.kt")
s = p.read_text(encoding="utf-8")
if "private val shuangpinHints" in s:
    print("Keyboard layout patch already present.")
    raise SystemExit(0)
s = s.replace("    private var shifted = false\n", "    private var shifted = false\n    private var showNumbers = false\n    private var numberRow: LinearLayout? = null\n")
s = s.replace("        header.addView(status)\n        root.addView(header)\n", """        header.addView(status, LinearLayout.LayoutParams(0, dp(28), 1f))
        addToolButton(header, "✎", "手写输入暂未接入")
        addToolButton(header, "♩", "语音输入暂未接入")
        addToolButton(header, "⌄", "收起键盘") { requestHideSelf(0) }
        root.addView(header)
""")
s = s.replace("""        val numberRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        "1234567890".forEach { digit ->
            addKey(numberRow, digit.toString(), 1f, 40) { onDigit(digit) }
        }
        root.addView(numberRow)
""", """        numberRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            visibility = if (showNumbers) View.VISIBLE else View.GONE
        }
        "1234567890".forEach { digit -> addKey(numberRow!!, digit.toString(), 1f, 34) { onDigit(digit) } }
        root.addView(numberRow)
""")
s = s.replace("""        addKey(bottom, if (chineseMode) "中" else "英", 1.15f, 48) {
            chineseMode = !chineseMode
            getSharedPreferences("archime_settings", MODE_PRIVATE).edit().putBoolean("chinese_mode", chineseMode).apply()
            status.text = inputStatus()
            renderUpdate(if (rimeReady) engine.clear() else InputEngine.Update("", emptyList(), false))
        }
""", """        addKey(bottom, "123", 1.05f, 48) {
            showNumbers = !showNumbers
            numberRow?.visibility = if (showNumbers) View.VISIBLE else View.GONE
        }
""")
s = s.replace("""        addKey(bottom, "空格", 3.8f, 48) { onSpace() }
        addKey(bottom, "。", .75f, 48) { commit(if (isChineseActive()) "。" else ".") }
""", """        addKey(bottom, "〰 空格", 3.3f, 48) { onSpace() }
        addKey(bottom, if (chineseMode) "双拼" else "英文", 1.2f, 48) {
            chineseMode = !chineseMode
            getSharedPreferences("archime_settings", MODE_PRIVATE).edit().putBoolean("chinese_mode", chineseMode).apply()
            status.text = inputStatus()
            renderUpdate(if (rimeReady) engine.clear() else InputEngine.Update("", emptyList(), false))
            refreshKeyLabels()
        }
""")
s = s.replace("""        root.addView(bottom)
        refreshCandidates(emptyList())
""", """        root.addView(bottom)
        val utility = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        addKey(utility, "⌨", 1f, 32) {
            try { switchToNextInputMethod(false) } catch (_: Throwable) {
                Toast.makeText(this@ArchInputMethodService, "请使用系统键盘切换按钮", Toast.LENGTH_SHORT).show()
            }
        }
        addKey(utility, "▤ 剪贴板", 1f, 32) {
            val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = cm.primaryClip
            if (clip != null && clip.itemCount > 0) commit(clip.getItemAt(0).coerceToText(this@ArchInputMethodService).toString())
            else Toast.makeText(this@ArchInputMethodService, "剪贴板为空", Toast.LENGTH_SHORT).show()
        }
        root.addView(utility)
        refreshCandidates(emptyList())
        refreshKeyLabels()
""")
s = s.replace("    private fun addKey(row: LinearLayout, label: String, weight: Float, height: Int, action: () -> Unit): Button {\n", """    private fun addToolButton(row: LinearLayout, label: String, message: String, action: (() -> Unit)? = null) {
        val button = TextView(this).apply {
            text = label; textSize = 18f; gravity = Gravity.CENTER
            setTextColor(0xFFE4EDF8.toInt()); background = keyRipple(0x6634445A)
            setOnClickListener {
                if (action != null) action()
                else Toast.makeText(this@ArchInputMethodService, message, Toast.LENGTH_SHORT).show()
            }
        }
        row.addView(button, LinearLayout.LayoutParams(dp(32), dp(28)).apply { setMargins(dp(2), 0, dp(2), 0) })
    }

    private fun addKey(row: LinearLayout, label: String, weight: Float, height: Int, action: () -> Unit): Button {
""")
s = s.replace("""    private fun refreshKeyLabels() {
        letterButtons.forEach { (key, button) -> button.text = if (shifted) key.uppercaseChar().toString() else key.toString() }
    }
""", """    private val shuangpinHints = mapOf(
        'q' to "IU", 'w' to "EI", 'e' to "E", 'r' to "UAN", 't' to "UE",
        'y' to "UN", 'u' to "SH", 'i' to "CH", 'o' to "UO", 'p' to "IE",
        'a' to "A", 's' to "ONG", 'd' to "UA", 'f' to "EN", 'g' to "ENG",
        'h' to "ANG", 'j' to "AN", 'k' to "AO", 'l' to "AI",
        'z' to "ING", 'x' to "IAO", 'c' to "AO", 'v' to "ZH",
        'b' to "OU", 'n' to "IN", 'm' to "IAN"
    )
    private fun refreshKeyLabels() {
        letterButtons.forEach { (key, button) ->
            val letter = if (shifted) key.uppercaseChar() else key
            button.text = if (isChineseActive()) "$letter\\n\${shuangpinHints[key] ?: ""}" else letter.toString()
            button.textSize = if (isChineseActive()) 13f else 17f
        }
    }
""")
if "import android.widget.Toast" not in s:
    s = s.replace("import android.widget.TextView\n", "import android.widget.TextView\nimport android.widget.Toast\n")
s = s.replace(r"\${shuangpinHints", "${shuangpinHints")
p.write_text(s, encoding="utf-8")
print("Applied ArchIME keyboard layout refresh.")
