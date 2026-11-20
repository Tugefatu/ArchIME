from pathlib import Path

p = Path("ArchIME-0.7.2-dev/app/src/main/java/org/archime/ime/ArchInputMethodService.kt")
s = p.read_text(encoding="utf-8")

def add_import(before, line):
    global s
    if line not in s:
        s = s.replace(before, before + line + "\n")

add_import("import android.content.res.ColorStateList\n", "import android.content.res.Configuration")
add_import("import android.widget.Button\n", "import android.widget.FrameLayout")
add_import("import android.widget.TextView\n", "import android.widget.Toast")

if "private var showNumbers" not in s:
    s = s.replace("    private var shifted = false\n", "    private var shifted = false\n    private var showNumbers = false\n    private var numberRow: LinearLayout? = null\n")
if "private val keyHintViews" not in s:
    s = s.replace("    private val letterButtons = linkedMapOf<Char, Button>()\n",
                  "    private val letterButtons = linkedMapOf<Char, Button>()\n    private val keyHintViews = linkedMapOf<Char, TextView>()\n    private var modeButton: Button? = null\n")

a = s.index("    override fun onCreateInputView(): View {")
b = s.index("    private fun keyRipple(", a)
view = r'''    override fun onCreateInputView(): View {
        blurActive = false
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(7), dp(7), dp(7), dp(7))
            background = acrylicPanel()
            elevation = dp(5).toFloat()
        }
        status = TextView(this).apply {
            textSize = 10f
            setTextColor(if (isDarkTheme()) 0xFFB7C0CC.toInt() else 0xFF68717D.toInt())
            text = inputStatus()
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), 0, dp(2), 0)
            maxLines = 1
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(ImageView(this).apply { setImageResource(R.drawable.ic_arch_logo) },
            LinearLayout.LayoutParams(dp(24), dp(24)).apply { marginStart = dp(3); marginEnd = dp(4) })
        header.addView(status, LinearLayout.LayoutParams(0, dp(32), 1f))
        addToolButton(header, "Aa", "切换字母大小写") { shifted = !shifted; refreshKeyLabels() }
        addToolButton(header, "✎", "手写输入尚未接入")
        addToolImage(header, android.R.drawable.ic_btn_speak_now, "语音输入尚未接入")
        addToolButton(header, "⌄", "收起键盘") { requestHideSelf(0) }
        root.addView(header)

        candidates = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(34)
        }
        root.addView(candidates)

        numberRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            visibility = if (showNumbers) View.VISIBLE else View.GONE
        }
        "1234567890".forEach { digit -> addKey(numberRow!!, digit.toString(), 1f, 34) { onDigit(digit) } }
        root.addView(numberRow)

        val rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
        val digitHints = "1234567890".toList()
        val symbolHints = listOf("-", "/", ":", ";", "(", ")", "~", "“", "”")
        rows.forEachIndexed { index, keys ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                if (index == 1) setPadding(dp(12), 0, dp(12), 0)
            }
            if (index == 2) addKey(row, "⇧", 1.25f, 50) { shifted = !shifted; refreshKeyLabels() }
            keys.forEachIndexed { keyIndex, key ->
                val topHint = when (index) {
                    0 -> digitHints[keyIndex].toString()
                    1 -> symbolHints[keyIndex]
                    else -> ""
                }
                addLetterKey(row, key, topHint) { onLetter(key) }
            }
            if (index == 2) addBackspaceKey(row)
            root.addView(row)
        }

        val bottom = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        addKey(bottom, "123", 1.05f, 48) {
            showNumbers = !showNumbers
            numberRow?.visibility = if (showNumbers) View.VISIBLE else View.GONE
        }
        val commaKey = addKey(bottom, ",", .72f, 48) { commit(if (isChineseActive()) "，" else ",") }
        commaKey.setOnLongClickListener {
            if (isChineseActive() && rimeReady) renderUpdate(engine.processKey(';'.code)) else commit(";")
            true
        }
        addKey(bottom, "〰", 3.4f, 48) {
            Toast.makeText(this, "语音识别尚未接入", Toast.LENGTH_SHORT).show()
        }
        modeButton = addKey(bottom, "双/英", 1.05f, 48) {
            chineseMode = !chineseMode
            getSharedPreferences("archime_settings", MODE_PRIVATE).edit().putBoolean("chinese_mode", chineseMode).apply()
            if (rimeReady) renderUpdate(engine.clear()) else refreshCandidates(emptyList())
            status.text = inputStatus()
            refreshKeyLabels()
            refreshModeButton()
        }
        addKey(bottom, "↵", 1.05f, 48) { onEnter() }
        root.addView(bottom)

        val utility = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        addUtilityButton(utility, "☷", "键盘管理设置") {
            try {
                val launch = packageManager.getLaunchIntentForPackage(packageName)
                if (launch != null) startActivity(launch)
                else Toast.makeText(this, "请从 ArchIME 应用打开设置", Toast.LENGTH_SHORT).show()
            } catch (_: Throwable) {
                Toast.makeText(this, "无法打开设置", Toast.LENGTH_SHORT).show()
            }
        }
        utility.addView(View(this), LinearLayout.LayoutParams(0, dp(30), 1f))
        addUtilityButton(utility, "▢▢", "剪贴板") {
            val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = cm.primaryClip
            if (clip != null && clip.itemCount > 0) commit(clip.getItemAt(0).coerceToText(this@ArchInputMethodService).toString())
            else Toast.makeText(this@ArchInputMethodService, "剪贴板为空", Toast.LENGTH_SHORT).show()
        }
        root.addView(utility)
        refreshCandidates(emptyList())
        refreshKeyLabels()
        refreshModeButton()
        return root
    }

'''
s = s[:a] + view + s[b:]

helpers = r'''    private fun addToolButton(row: LinearLayout, label: String, message: String, action: (() -> Unit)? = null) {
        val button = TextView(this).apply {
            text = label
            textSize = if (label == "Aa") 14f else 18f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (isDarkTheme()) Color.WHITE else 0xFF20242A.toInt())
            background = keyRipple(if (isDarkTheme()) 0xFF242424.toInt() else Color.WHITE)
            setOnClickListener {
                if (action != null) action()
                else Toast.makeText(this@ArchInputMethodService, message, Toast.LENGTH_SHORT).show()
            }
        }
        row.addView(button, LinearLayout.LayoutParams(dp(31), dp(30)).apply { setMargins(dp(2), 0, dp(2), 0) })
    }


    private fun addToolImage(row: LinearLayout, iconRes: Int, message: String) {
        val image = ImageView(this).apply {
            setImageResource(iconRes)
            setColorFilter(if (isDarkTheme()) Color.WHITE else 0xFF20242A.toInt())
            contentDescription = message
            setOnClickListener {
                Toast.makeText(this@ArchInputMethodService, message, Toast.LENGTH_SHORT).show()
            }
        }
        row.addView(image, LinearLayout.LayoutParams(dp(31), dp(30)).apply {
            setMargins(dp(2), 0, dp(2), 0)
        })
    }

    private fun addUtilityButton(row: LinearLayout, label: String, message: String, action: () -> Unit) {
        val button = TextView(this).apply {
            text = label
            textSize = 17f
            gravity = Gravity.CENTER
            setTextColor(if (isDarkTheme()) 0xFFBFC5CE.toInt() else 0xFF424951.toInt())
            setOnClickListener { action() }
            contentDescription = message
        }
        row.addView(button, LinearLayout.LayoutParams(dp(42), dp(30)))
    }

    private fun addKey(row: LinearLayout, label: String, weight: Float, height: Int, action: () -> Unit): Button {
        val button = Button(this).apply {
            text = label
            isAllCaps = false
            textSize = if (label.length > 1) 12f else 17f
            setTextColor(if (isDarkTheme()) Color.WHITE else 0xFF171A1F.toInt())
            typeface = Typeface.DEFAULT_BOLD
            background = keyRipple(when (label) {
                "〰", "↵" -> if (isDarkTheme()) 0xFF30343B.toInt() else Color.WHITE
                else -> if (isDarkTheme()) 0xFF252525.toInt() else Color.WHITE
            })
            setPadding(0, 0, 0, 0)
            minWidth = 0
            stateListAnimator = null
            setOnClickListener { performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); action() }
        }
        row.addView(button, LinearLayout.LayoutParams(0, dp(height), weight).apply {
            setMargins(dp(2), dp(3), dp(2), dp(3))
        })
        return button
    }

    private fun addLetterKey(row: LinearLayout, key: Char, topHint: String, action: () -> Unit) {
        val frame = FrameLayout(this)
        val button = Button(this).apply {
            text = if (shifted) key.uppercaseChar().toString() else key.toString()
            isAllCaps = false
            textSize = 17f
            setTextColor(if (isDarkTheme()) Color.WHITE else 0xFF171A1F.toInt())
            typeface = Typeface.DEFAULT_BOLD
            background = keyRipple(if (isDarkTheme()) 0xFF252525.toInt() else Color.WHITE)
            setPadding(0, dp(5), 0, dp(5))
            minWidth = 0
            stateListAnimator = null
            setOnClickListener { performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); action() }
        }
        frame.addView(button, FrameLayout.LayoutParams(-1, -1))
        if (topHint.isNotEmpty()) {
            frame.addView(TextView(this).apply {
                text = topHint
                textSize = 8f
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                setTextColor(if (isDarkTheme()) 0xFF9BA3AE.toInt() else 0xFF7B838D.toInt())
                isClickable = false
                isFocusable = false
            }, FrameLayout.LayoutParams(-1, dp(13), Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        }
        val hint = TextView(this).apply {
            text = microsoftHints[key] ?: ""
            textSize = 7f
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            setTextColor(if (isDarkTheme()) 0xFF8E9AA8.toInt() else 0xFF9098A2.toInt())
            isClickable = false
            isFocusable = false
        }
        frame.addView(hint, FrameLayout.LayoutParams(-1, dp(12), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
        row.addView(frame, LinearLayout.LayoutParams(0, dp(50), 1f).apply {
            setMargins(dp(2), dp(3), dp(2), dp(3))
        })
        letterButtons[key] = button
        keyHintViews[key] = hint
    }

    private fun addBackspaceKey(row: LinearLayout) {
        val button = Button(this).apply {
            text = "⌫"
            isAllCaps = false
            textSize = 20f
            setTextColor(if (isDarkTheme()) Color.WHITE else 0xFF171A1F.toInt())
            typeface = Typeface.DEFAULT_BOLD
            background = keyRipple(if (isDarkTheme()) 0xFF252525.toInt() else Color.WHITE)
            setPadding(0, 0, 0, 0)
            minWidth = 0
            stateListAnimator = null
            setOnClickListener { performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); onBackspace() }
            setOnLongClickListener {
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                repeatingBackspace = true
                mainHandler.postDelayed(backspaceRepeat, 420L)
                true
            }
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                    repeatingBackspace = false
                    mainHandler.removeCallbacks(backspaceRepeat)
                }
                false
            }
        }
        row.addView(button, LinearLayout.LayoutParams(0, dp(50), 1.25f).apply {
            setMargins(dp(2), dp(3), dp(2), dp(3))
        })
    }

'''
a = s.index("    private fun keyRipple(")
s = s[:a] + helpers + s[a:]

a = s.index("    private fun keyRipple(")
b = s.index("    /** Android 12+ cross-window blur", a)
theme = r'''    private fun isDarkTheme(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    private fun keyRipple(baseColor: Int) = RippleDrawable(
        ColorStateList.valueOf(if (isDarkTheme()) 0x447FC4FF else 0x22708FB7),
        GradientDrawable().apply {
            setColor(baseColor)
            cornerRadius = dp(10).toFloat()
            setStroke(dp(1), if (isDarkTheme()) 0x22000000 else 0xFFE7E9ED.toInt())
        },
        null
    )

    private fun acrylicPanel() = GradientDrawable().apply {
        setColor(if (isDarkTheme()) Color.BLACK else 0xFFF0F2F5.toInt())
        cornerRadius = dp(18).toFloat()
        setStroke(dp(1), if (isDarkTheme()) 0xFF171717.toInt() else 0xFFE4E7EB.toInt())
    }

'''
s = s[:a] + theme + s[b:]

hints = r'''    private val microsoftHints = mapOf(
        'q' to "IU", 'w' to "IA/UA", 'e' to "E", 'r' to "ER/UAN", 't' to "UE",
        'y' to "Ü/UAI", 'u' to "SH", 'i' to "CH", 'o' to "UO", 'p' to "UN/ÜN",
        'a' to "A", 's' to "ONG/IONG", 'd' to "UANG/IANG", 'f' to "EN", 'g' to "ENG",
        'h' to "ANG", 'j' to "AN", 'k' to "AO", 'l' to "AI",
        'z' to "EI", 'x' to "IE", 'c' to "IAO", 'v' to "ZH/UI",
        'b' to "OU", 'n' to "IN", 'm' to "IAN"
    )

    private fun refreshKeyLabels() {
        letterButtons.forEach { (key, button) ->
            button.text = if (shifted) key.uppercaseChar().toString() else key.toString()
        }
        keyHintViews.forEach { (key, hint) ->
            hint.text = if (isChineseActive()) microsoftHints[key] ?: "" else ""
        }
    }

    private fun refreshModeButton() {
        val button = modeButton ?: return
        val label = android.text.SpannableString("双/英")
        if (!chineseMode) {
            label.setSpan(android.text.style.ForegroundColorSpan(0xFF2997F3.toInt()), 2, 3,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        button.text = label
    }

'''
if "    private fun refreshKeyLabels()" in s:
    a = s.index("    private fun refreshKeyLabels()")
    b = s.index("    private fun isChineseActive()", a)
    s = s[:a] + hints + s[b:]
else:
    a = s.index("    private fun isChineseActive()")
    s = s[:a] + hints + s[a:]

s = s.replace("setTextColor(Color.WHITE)\n                gravity = Gravity.CENTER", "setTextColor(if (isDarkTheme()) Color.WHITE else 0xFF20242A.toInt())\n                gravity = Gravity.CENTER")
s = s.replace("setTextColor(0xFF8292A8.toInt())\n                gravity = Gravity.CENTER_VERTICAL", "setTextColor(if (isDarkTheme()) 0xFF8E98A5.toInt() else 0xFF737C87.toInt())\n                gravity = Gravity.CENTER_VERTICAL")
s = s.replace("setTextColor(0xFFB7C5D8.toInt())\n            gravity = Gravity.CENTER", "setTextColor(if (isDarkTheme()) 0xFFB7C0CC.toInt() else 0xFF6D7681.toInt())\n            gravity = Gravity.CENTER")

old = '''        if (!isChineseActive() || !rimeReady) {
            if (isChineseActive() && !rimeReady) {
                status.text = "ArchIME · Rime 未打包，暂用英文输入"
            }
            commit(c.toString())
            return
        }
'''
new = '''        if (isChineseActive() && !rimeReady) {
            status.text = "微软双拼引擎未连接 · 中文暂不可用"
            return
        }
        if (!isChineseActive()) {
            commit(c.toString())
            return
        }
'''
s = s.replace(old, new)
s = s.replace('else "中文引擎未就绪"', 'else "微软双拼引擎未连接"')
s = s.replace("双拼候选栏 · 等待 Rime", "微软双拼候选栏 · 引擎未连接")

p.write_text(s, encoding="utf-8")
print("Applied ArchIME QWERTY layout, light/dark skins, and Microsoft Shuangpin labels.")
