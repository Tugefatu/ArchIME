package org.archime.ime

import android.content.ClipData
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.text.InputType
import android.view.inputmethod.InputConnection
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.view.WindowManager
import android.graphics.drawable.ColorDrawable
import android.view.inputmethod.InputMethodService
import org.archime.ime.engine.InputEngine
import org.archime.ime.engine.NativeRimeEngine
import org.archime.ime.engine.RimeAssetInstaller

/** ArchIME's Xiaomi-style compact keyboard with Rime, optional haptics and tile-click audio. */
class ArchInputMethodService : InputMethodService() {
    private val engine = NativeRimeEngine()
    private var rimeReady = false
    private var chineseMode = true
    private var secureField = false
    private var shifted = false
    private var showNumbers = false
    private var oneHanded = false
    private var oneHandRight = true
    private var featurePanel = false
    private var featurePage = 0
    private var clipboardPanel = false
    private var clipboardTab = 0
    private var candidatesExpanded = false

    private lateinit var root: LinearLayout
    private lateinit var candidateRow: LinearLayout
    private lateinit var numberRow: LinearLayout
    private val letterButtons = linkedMapOf<Char, Button>()
    private var modeButton: TextView? = null
    private var actionButton: TextView? = null
    private var composition = ""
    private var currentCandidates = emptyList<InputEngine.Candidate>()

    private val mainHandler = Handler(Looper.getMainLooper())
    private var repeatingBackspace = false
    private val backspaceRepeat = object : Runnable {
        override fun run() {
            if (!repeatingBackspace) return
            onBackspace()
            mainHandler.postDelayed(this, 58L)
        }
    }

    private lateinit var keySounds: SoundPool
    private var mahjongSampleId = 0
    private var mahjongSampleReady = false

    override fun onCreate() {
        super.onCreate()
        val prefs = getSharedPreferences("archime_settings", MODE_PRIVATE)
        chineseMode = prefs.getBoolean("chinese_mode", true)

        val userRime = filesDir.resolve("rime").apply { mkdirs() }
        val installed = RimeAssetInstaller.install(this, userRime)
        rimeReady = installed.isSuccess && engine.initialize(userRime.absolutePath)

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        keySounds = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(audioAttributes)
            .build()
        keySounds.setOnLoadCompleteListener { _, sampleId, status ->
            if (sampleId == mahjongSampleId && status == 0) mahjongSampleReady = true
        }
        mahjongSampleId = keySounds.load(this, R.raw.mahjong_key, 1)
    }

    override fun onCreateInputView(): View {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = true
            clipToPadding = true
            setPadding(dp(4), dp(3), dp(4), dp(2))
            background = GradientDrawable().apply {
                setColor(CANVAS)
                cornerRadius = dp(16).toFloat()
            }
        }
        renderSurface()
        return root
    }

    private fun renderSurface() {
        if (!::root.isInitialized) return
        root.removeAllViews()
        root.setBackgroundColor(CANVAS)
        val sideInset = if (oneHanded && !featurePanel && !clipboardPanel) dp(34) else dp(4)
        root.setPadding(if (oneHanded && !oneHandRight) sideInset else dp(4), dp(3),
            if (oneHanded && oneHandRight) sideInset else dp(4), dp(2))
        root.gravity = if (oneHanded && !featurePanel && !clipboardPanel) {
            if (oneHandRight) Gravity.END else Gravity.START
        } else Gravity.CENTER_HORIZONTAL

        when {
            clipboardPanel -> buildClipboardPanel()
            featurePanel -> buildFeaturePanel()
            else -> buildKeyboard()
        }
    }

    private fun buildKeyboard() {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val brand = TextView(this).apply {
            text = "mi"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = rounded(0xFF3B4049.toInt(), 8)
            contentDescription = "ArchIME keyboard"
        }
        header.addView(brand, LinearLayout.LayoutParams(dp(34), dp(30)).apply {
            setMargins(dp(2), dp(1), dp(6), dp(1))
        })
        addToolbarIcon(header, "➤", "隔空传送工具面板") {
            featurePanel = true
            featurePage = 0
            renderSurface()
        }
        addToolbarIcon(header, "☝", "单手模式") {
            oneHanded = !oneHanded
            oneHandRight = true
            renderSurface()
        }
        addToolbarIcon(header, "♩", "语音输入") {
            toast("语音转文字尚未接入")
        }
        addToolbarIcon(header, "∨", "收起键盘") { requestHideSelf(0) }
        root.addView(header, LinearLayout.LayoutParams(-1, dp(34)))

        candidateRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
        }
        root.addView(candidateRow, LinearLayout.LayoutParams(-1, dp(35)))

        numberRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            visibility = if (showNumbers) View.VISIBLE else View.GONE
        }
        "1234567890".forEach { digit ->
            addKey(numberRow, digit.toString(), 1f, 35) { onDigit(digit) }
        }
        root.addView(numberRow, LinearLayout.LayoutParams(-1, dp(39)))

        val rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
        val digitHints = "1234567890".map { it.toString() }
        val rowTwoHints = listOf("-", "/", ":", ";", "(", ")", "~", "“", "”")
        val rowThreeHints = listOf("@", "#", "?", "!", "…", "&", "'")
        rows.forEachIndexed { rowIndex, keys ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                if (rowIndex == 1) setPadding(dp(10), 0, dp(10), 0)
            }
            if (rowIndex == 2) addKey(row, "⇧", 1.2f, 49) {
                shifted = !shifted
                refreshKeyLabels()
            }
            keys.forEachIndexed { keyIndex, key ->
                val topHint = when (rowIndex) {
                    0 -> digitHints[keyIndex]
                    1 -> rowTwoHints[keyIndex]
                    else -> rowThreeHints[keyIndex]
                }
                addLetterKey(row, key, topHint) { onLetter(key) }
            }
            if (rowIndex == 2) addBackspaceKey(row)
            root.addView(row, LinearLayout.LayoutParams(-1, dp(55)))
        }

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        addKey(bottom, "123", 1.05f, 47) {
            showNumbers = !showNumbers
            numberRow.visibility = if (showNumbers) View.VISIBLE else View.GONE
        }
        addKey(bottom, ",", .65f, 47) {
            commit(if (isChineseActive()) "，" else ",")
        }
        val space = addKey(bottom, "Ⅲ", 3.25f, 47) { onSpace() }
        space.textSize = 20f
        space.setOnLongClickListener {
            feedback()
            toast("语音识别尚未接入")
            true
        }
        modeButton = addModeKey(bottom)
        actionButton = addActionKey(bottom)
        root.addView(bottom, LinearLayout.LayoutParams(-1, dp(53)))

        val utility = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        addUtilityIcon(utility, "☷", "键盘工具面板") {
            featurePanel = true
            featurePage = 0
            renderSurface()
        }
        utility.addView(View(this), LinearLayout.LayoutParams(0, dp(25), 1f))
        addUtilityIcon(utility, "▢", "剪贴板") {
            clipboardPanel = true
            clipboardTab = 0
            renderSurface()
        }
        root.addView(utility, LinearLayout.LayoutParams(-1, dp(27)))

        refreshCandidates(currentCandidates)
        refreshKeyLabels()
        refreshModeButton()
        refreshActionKey()
    }

    private fun buildFeaturePanel() {
        addPanelHeader()
        val pageItems = featureItems(featurePage)
        repeat(2) { rowIndex ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                clipChildren = true
            }
            val start = rowIndex * 4
            val normalCount = minOf(4, (pageItems.size - start).coerceAtLeast(0))
            repeat(normalCount) { column ->
                val item = pageItems[start + column]
                row.addView(featureTile(item), LinearLayout.LayoutParams(0, dp(76), 1f).apply {
                    setMargins(dp(3), dp(3), dp(3), dp(3))
                })
            }
            // Page two deliberately lets the ninth tile peek in at the far right edge.
            if (featurePage == 1 && rowIndex == 1 && pageItems.size > 8) {
                row.addView(featureTile(pageItems[8]), LinearLayout.LayoutParams(dp(78), dp(76)).apply {
                    setMargins(dp(3), dp(3), dp(3), dp(3))
                })
            }
            if (normalCount > 0 || (featurePage == 1 && rowIndex == 1)) {
                root.addView(row, LinearLayout.LayoutParams(-1, dp(82)))
            }
        }
        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val firstDot = panelDot(if (featurePage == 0) BLUE else 0xFF666B75.toInt()) {
            featurePage = 0
            renderSurface()
        }
        val secondDot = panelDot(if (featurePage == 1) BLUE else 0xFF666B75.toInt()) {
            featurePage = 1
            renderSurface()
        }
        footer.addView(firstDot, LinearLayout.LayoutParams(dp(24), dp(22)))
        footer.addView(secondDot, LinearLayout.LayoutParams(dp(24), dp(22)))
        root.addView(footer, LinearLayout.LayoutParams(-1, dp(24)))
        addPanelBackRow()
    }

    private fun addPanelHeader() {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        addToolbarIcon(header, "⌃", "收起工具面板") {
            featurePanel = false
            renderSurface()
        }
        val title = TextView(this).apply {
            text = "➤  隔空传送"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
        }
        header.addView(title, LinearLayout.LayoutParams(0, dp(34), 1f))
        addToolbarIcon(header, "⚙", "键盘设置") { openSettings() }
        root.addView(header, LinearLayout.LayoutParams(-1, dp(38)))
    }

    private data class FeatureTile(val icon: String, val title: String, val action: () -> Unit)

    private fun featureItems(page: Int): List<FeatureTile> {
        val clipboard = {
            clipboardPanel = true
            clipboardTab = 0
            renderSurface()
        }
        val oneHand = {
            oneHanded = !oneHanded
            featurePanel = false
            renderSurface()
        }
        return if (page == 0) listOf(
            FeatureTile("♩", "语音转文字") { toast("语音转文字尚未接入") },
            FeatureTile("☺", "表情") { featurePanel = false; renderSurface(); commit("😀") },
            FeatureTile("双", "双拼键盘") { chineseMode = true; saveMode(); featurePanel = false; renderSurface() },
            FeatureTile("☷", "定制工具栏") { openSettings() },
            FeatureTile("▢", "剪贴板", clipboard),
            FeatureTile("➤", "快捷发送") { featurePanel = false; renderSurface(); onEnter() },
            FeatureTile("✎", "手写找字") { toast("手写识别尚未接入") },
            FeatureTile("AI", "问 AI") { toast("AI 功能入口尚未接入") }
        ) else listOf(
            FeatureTile("文", "翻译") { toast("翻译功能尚未接入") },
            FeatureTile("▧", "排版成图") { toast("排版成图功能尚未接入") },
            FeatureTile("繁", "繁体输入") { toast("繁体切换还在开发中") },
            FeatureTile("↕", "键盘调节") { toast("键盘高度调节还在开发中") },
            FeatureTile("☝", "单手模式", oneHand),
            FeatureTile("✧", "灵动表达") { toast("灵动表达功能尚未接入") },
            FeatureTile("▦", "小程序") { toast("小程序入口尚未接入") },
            FeatureTile("▣", "悬浮键盘") { toast("悬浮键盘尚未接入") },
            FeatureTile("A", "字体调节") { toast("字体调节还在开发中") }
        )
    }

    private fun featureTile(item: FeatureTile): View {
        val tile = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = rounded(KEY, 12)
            isClickable = true
            isFocusable = true
            setOnClickListener { item.action() }
        }
        val icon = TextView(this).apply {
            text = item.icon
            textSize = if (item.icon == "AI") 18f else 24f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT
            setTextColor(Color.WHITE)
        }
        val label = TextView(this).apply {
            text = item.title
            textSize = 10f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            maxLines = 1
        }
        tile.addView(icon, LinearLayout.LayoutParams(-1, dp(40)))
        tile.addView(label, LinearLayout.LayoutParams(-1, dp(21)))
        wireKeyFeedback(tile)
        return tile
    }

    private fun panelDot(color: Int, action: () -> Unit) = TextView(this).apply {
        text = "●"
        textSize = 11f
        gravity = Gravity.CENTER
        setTextColor(color)
        setOnClickListener { action() }
    }

    private fun addPanelBackRow() {
        val footer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END }
        addUtilityIcon(footer, "⌄", "返回键盘") {
            featurePanel = false
            renderSurface()
        }
        root.addView(footer, LinearLayout.LayoutParams(-1, dp(25)))
    }

    private fun buildClipboardPanel() {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        addToolbarIcon(header, "∨", "返回键盘") {
            clipboardPanel = false
            renderSurface()
        }
        addTab(header, "剪贴板", clipboardTab == 0) { clipboardTab = 0; renderSurface() }
        addTab(header, "常用语", clipboardTab == 1) { clipboardTab = 1; renderSurface() }
        header.addView(View(this), LinearLayout.LayoutParams(0, dp(34), 1f))
        addToolbarIcon(header, "▤", "清空剪贴板") {
            try {
                val manager = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                manager.setPrimaryClip(ClipData.newPlainText("", ""))
            } catch (_: Throwable) {}
            renderSurface()
        }
        root.addView(header, LinearLayout.LayoutParams(-1, dp(38)))

        val scroll = ScrollView(this).apply { fillViewport = true }
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val entries = if (clipboardTab == 1) {
            listOf("你好！", "谢谢你。", "收到，我马上处理。", "好的，没问题。", "稍后回复你。")
        } else {
            val manager = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = try { manager.primaryClip } catch (_: Throwable) { null }
            buildList {
                if (clip != null) {
                    for (index in 0 until clip.itemCount) {
                        val value = clip.getItemAt(index).coerceToText(this@ArchInputMethodService)?.toString().orEmpty()
                        if (value.isNotBlank()) add(value)
                    }
                }
            }.distinct()
        }
        if (entries.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "暂无剪贴板内容"
                textSize = 13f
                gravity = Gravity.CENTER
                setTextColor(MUTED)
            }, LinearLayout.LayoutParams(-1, dp(100)))
        } else {
            entries.forEach { value ->
                val item = TextView(this).apply {
                    text = value
                    textSize = 14f
                    gravity = Gravity.CENTER_VERTICAL
                    setTextColor(Color.WHITE)
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                    background = rounded(KEY, 10)
                    setOnClickListener {
                        commit(value)
                        clipboardPanel = false
                        renderSurface()
                    }
                }
                list.addView(item, LinearLayout.LayoutParams(-1, dp(48)).apply {
                    setMargins(dp(4), dp(3), dp(4), dp(3))
                })
            }
        }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
    }

    private fun addTab(row: LinearLayout, label: String, selected: Boolean, action: () -> Unit) {
        val tab = TextView(this).apply {
            text = label
            textSize = 12f
            gravity = Gravity.CENTER
            typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            setTextColor(if (selected) BLUE else Color.WHITE)
            setOnClickListener { action() }
        }
        row.addView(tab, LinearLayout.LayoutParams(dp(66), dp(34)))
    }

    private fun addToolbarIcon(row: LinearLayout, glyph: String, description: String, action: () -> Unit) {
        val icon = TextView(this).apply {
            text = glyph
            textSize = 19f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            contentDescription = description
            background = ripple(KEY)
            setOnClickListener { action() }
        }
        row.addView(icon, LinearLayout.LayoutParams(dp(38), dp(31)).apply {
            setMargins(dp(1), 0, dp(1), 0)
        })
        wireKeyFeedback(icon)
    }

    private fun addUtilityIcon(row: LinearLayout, glyph: String, description: String, action: () -> Unit) {
        val icon = TextView(this).apply {
            text = glyph
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(MUTED)
            contentDescription = description
            setOnClickListener { action() }
        }
        row.addView(icon, LinearLayout.LayoutParams(dp(42), dp(27)))
        wireKeyFeedback(icon)
    }

    private fun addKey(row: LinearLayout, label: String, weight: Float, height: Int, action: () -> Unit): Button {
        val button = Button(this).apply {
            text = label
            isAllCaps = false
            textSize = if (label.length > 1) 12f else 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = ripple(if (label == "↵") BLUE else KEY)
            setPadding(0, 0, 0, 0)
            minWidth = 0
            minHeight = 0
            stateListAnimator = null
            setOnClickListener { action() }
        }
        row.addView(button, LinearLayout.LayoutParams(0, dp(height), weight).apply {
            setMargins(dp(2), dp(3), dp(2), dp(3))
        })
        wireKeyFeedback(button)
        return button
    }

    private fun addLetterKey(row: LinearLayout, key: Char, topHint: String, action: () -> Unit) {
        val frame = FrameLayout(this)
        val button = Button(this).apply {
            text = if (shifted) key.uppercaseChar().toString() else key.toString()
            isAllCaps = false
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = ripple(KEY)
            setPadding(0, dp(4), 0, dp(3))
            minWidth = 0
            minHeight = 0
            stateListAnimator = null
            setOnClickListener { action() }
        }
        frame.addView(button, FrameLayout.LayoutParams(-1, -1))
        frame.addView(TextView(this).apply {
            text = topHint
            textSize = 8f
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            setTextColor(0xFF8A8F98.toInt())
            isClickable = false
            isFocusable = false
        }, FrameLayout.LayoutParams(-1, dp(13), Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        row.addView(frame, LinearLayout.LayoutParams(0, dp(49), 1f).apply {
            setMargins(dp(2), dp(3), dp(2), dp(3))
        })
        letterButtons[key] = button
        wireKeyFeedback(button)
    }

    private fun addBackspaceKey(row: LinearLayout) {
        val button = Button(this).apply {
            text = "⌫"
            isAllCaps = false
            textSize = 20f
            typeface = Typeface.DEFAULT
            setTextColor(Color.WHITE)
            background = ripple(KEY)
            setPadding(0, 0, 0, 0)
            minWidth = 0
            stateListAnimator = null
            setOnClickListener { onBackspace() }
            setOnLongClickListener {
                repeatingBackspace = true
                mainHandler.postDelayed(backspaceRepeat, 420L)
                true
            }
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) feedback()
                if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                    repeatingBackspace = false
                    mainHandler.removeCallbacks(backspaceRepeat)
                }
                false
            }
        }
        row.addView(button, LinearLayout.LayoutParams(0, dp(49), 1.2f).apply {
            setMargins(dp(2), dp(3), dp(2), dp(3))
        })
    }

    private fun addModeKey(row: LinearLayout): TextView {
        val view = TextView(this).apply {
            text = "双\n英"
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = ripple(KEY)
            setOnClickListener {
                chineseMode = !chineseMode
                saveMode()
                if (rimeReady) renderUpdate(engine.clear()) else refreshCandidates(emptyList())
                refreshModeButton()
                refreshKeyLabels()
            }
        }
        row.addView(view, LinearLayout.LayoutParams(0, dp(47), 0.9f).apply {
            setMargins(dp(2), dp(3), dp(2), dp(3))
        })
        wireKeyFeedback(view)
        return view
    }

    private fun addActionKey(row: LinearLayout): TextView {
        val view = TextView(this).apply {
            text = editorActionLabel()
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = ripple(BLUE)
            setOnClickListener { onEnter() }
        }
        row.addView(view, LinearLayout.LayoutParams(0, dp(47), 1.05f).apply {
            setMargins(dp(2), dp(3), dp(2), dp(3))
        })
        wireKeyFeedback(view)
        return view
    }

    private fun refreshCandidates(items: List<InputEngine.Candidate>) {
        if (!::candidateRow.isInitialized) return
        currentCandidates = items
        candidateRow.removeAllViews()
        val show = isChineseActive() && (composition.isNotEmpty() || items.isNotEmpty())
        candidateRow.visibility = if (show) View.VISIBLE else View.GONE
        if (!show) return

        val code = TextView(this).apply {
            text = composition
            textSize = 11f
            setTextColor(MUTED)
            gravity = Gravity.CENTER
            maxLines = 1
            setPadding(dp(4), 0, dp(5), 0)
        }
        candidateRow.addView(code, LinearLayout.LayoutParams(dp(60), dp(32)))

        val scroller = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
        }
        val wordRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val count = if (candidatesExpanded) items.size else minOf(5, items.size)
        items.take(count).forEachIndexed { index, candidate ->
            val word = TextView(this).apply {
                text = candidate.text
                textSize = 16f
                typeface = if (index == 0) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(dp(8), 0, dp(8), 0)
                setOnClickListener { renderUpdate(engine.selectCandidate(index)) }
            }
            wordRow.addView(word, LinearLayout.LayoutParams(-2, dp(32)))
        }
        scroller.addView(wordRow)
        candidateRow.addView(scroller, LinearLayout.LayoutParams(0, dp(32), 1f))
        val expand = TextView(this).apply {
            text = if (candidatesExpanded) "⌃" else "∨"
            textSize = 17f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setOnClickListener {
                candidatesExpanded = !candidatesExpanded
                refreshCandidates(currentCandidates)
            }
        }
        candidateRow.addView(expand, LinearLayout.LayoutParams(dp(30), dp(32)))
    }

    private fun wireKeyFeedback(view: View) {
        view.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) feedback()
            false
        }
    }

    private fun feedback() {
        val prefs = getSharedPreferences("archime_settings", MODE_PRIVATE)
        if (prefs.getBoolean("key_vibration", true)) {
            try {
                val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
                if (vibrator.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(18L, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(18L)
                    }
                }
            } catch (_: Throwable) {}
        }
        if (prefs.getBoolean("mahjong_sound", true) && mahjongSampleReady && mahjongSampleId != 0) {
            try { keySounds.play(mahjongSampleId, 0.28f, 0.28f, 1, 0, 1.0f) } catch (_: Throwable) {}
        }
    }

    private fun onLetter(raw: Char) {
        val value = if (shifted) raw.uppercaseChar() else raw
        shifted = false
        refreshKeyLabels()
        if (!isChineseActive() || !rimeReady) {
            commit(value.toString())
            return
        }
        renderUpdate(engine.processKey(raw.lowercaseChar().code))
    }

    private fun onDigit(digit: Char) {
        val candidateIndex = digit.digitToIntOrNull()?.let { if (it == 0) 9 else it - 1 }
        if (isChineseActive() && rimeReady && candidateIndex != null && candidateIndex < currentCandidates.size) {
            renderUpdate(engine.selectCandidate(candidateIndex))
        } else {
            commit(digit.toString())
        }
    }

    private fun onBackspace() {
        if (isChineseActive() && rimeReady) renderUpdate(engine.backspace())
        else currentInputConnection?.deleteSurroundingText(1, 0)
    }

    private fun onSpace() {
        if (isChineseActive() && rimeReady) {
            val update = engine.processKey(' '.code)
            renderUpdate(update)
            if (update.composingText.isEmpty() && update.candidates.isEmpty() && update.commitText.isEmpty()) {
                currentInputConnection?.commitText(" ", 1)
            }
        } else commit(" ")
    }

    private fun onEnter() {
        val ic = currentInputConnection ?: return
        val action = currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
        if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(action)
        } else {
            ic.commitText("\n", 1)
        }
    }

    private fun editorActionLabel(): String {
        val action = currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
        return when (action) {
            EditorInfo.IME_ACTION_SEND -> "发送"
            EditorInfo.IME_ACTION_SEARCH -> "搜索"
            EditorInfo.IME_ACTION_DONE, EditorInfo.IME_ACTION_GO, EditorInfo.IME_ACTION_NEXT -> "确定"
            else -> "确定"
        }
    }

    private fun refreshActionKey() {
        actionButton?.text = editorActionLabel()
    }

    private fun renderUpdate(update: InputEngine.Update) {
        if (update.commitText.isNotEmpty()) {
            currentInputConnection?.commitText(update.commitText, 1)
            currentInputConnection?.finishComposingText()
        }
        composition = update.composingText
        if (update.ready) currentInputConnection?.setComposingText(update.composingText, 1)
        refreshCandidates(update.candidates)
    }

    private fun refreshKeyLabels() {
        letterButtons.forEach { (key, button) ->
            button.text = if (shifted) key.uppercaseChar().toString() else key.toString()
        }
    }

    private fun refreshModeButton() {
        modeButton?.text = if (chineseMode) "双\n英" else "双\n英"
        modeButton?.let { button ->
            val text = android.text.SpannableString("双\n英")
            val selectedIndex = if (chineseMode) 0 else 2
            text.setSpan(android.text.style.ForegroundColorSpan(BLUE), selectedIndex, selectedIndex + 1,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            button.text = text
        }
    }

    private fun saveMode() {
        getSharedPreferences("archime_settings", MODE_PRIVATE).edit()
            .putBoolean("chinese_mode", chineseMode).apply()
    }

    private fun isChineseActive() = chineseMode && !secureField

    private fun isSecureInput(inputType: Int): Boolean {
        val inputClass = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        return (inputClass == InputType.TYPE_CLASS_TEXT && variation in setOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        )) || (inputClass == InputType.TYPE_CLASS_NUMBER &&
            variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
    }

    private fun openSettings() {
        try {
            packageManager.getLaunchIntentForPackage(packageName)?.let { startActivity(it) }
        } catch (_: Throwable) {
            toast("请从 ArchIME 应用打开设置")
        }
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    private fun commit(value: String) { currentInputConnection?.commitText(value, 1) }

    private fun ripple(color: Int) = RippleDrawable(
        ColorStateList.valueOf(0x553F83E8),
        rounded(color, 12),
        null
    )

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        secureField = isSecureInput(info?.inputType ?: InputType.TYPE_NULL)
        composition = ""
        currentCandidates = emptyList()
        candidatesExpanded = false
        if (rimeReady) {
            val update = engine.clear()
            currentInputConnection?.setComposingText("", 1)
            composition = update.composingText
        }
        currentInputConnection?.finishComposingText()
        featurePanel = false
        clipboardPanel = false
        shifted = false
        refreshActionKey()
        refreshKeyLabels()
        if (::root.isInitialized) renderSurface()
    }

    override fun onFinishInput() {
        if (rimeReady) {
            engine.clear()
            currentInputConnection?.setComposingText("", 1)
        }
        composition = ""
        currentCandidates = emptyList()
        super.onFinishInput()
    }

    override fun onDestroy() {
        repeatingBackspace = false
        mainHandler.removeCallbacks(backspaceRepeat)
        engine.close()
        try { keySounds.release() } catch (_: Throwable) {}
        super.onDestroy()
    }

    companion object {
        private const val CANVAS = 0xFF111318.toInt()
        private const val KEY = 0xFF1E2128.toInt()
        private const val BLUE = 0xFF2F7BFF.toInt()
        private const val MUTED = 0xFF8A8F98.toInt()
    }
}
