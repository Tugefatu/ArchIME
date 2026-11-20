from pathlib import Path

import os
root = Path(os.environ.get('ARCHIME_SOURCE_ROOT', 'ArchIME-0.7.2-dev/app/src/main'))
p = root / 'java/org/archime/ime/ArchInputMethodService.kt'
s = p.read_text(encoding='utf-8')

def replace_once(old: str, new: str, name: str):
    global s
    count = s.count(old)
    if count != 1:
        raise SystemExit(f'{name}: expected exactly one match, got {count}')
    s = s.replace(old, new, 1)

# Keep the Arch Linux emblem as the first, fixed element in the top-left header.
replace_once('''        header.addView(ImageView(this).apply { setImageResource(R.drawable.ic_arch_logo) },
            LinearLayout.LayoutParams(dp(24), dp(24)).apply { marginStart = dp(3); marginEnd = dp(4) })
        header.addView(status, LinearLayout.LayoutParams(0, dp(32), 1f))
        addToolButton(header, "Aa", "切换字母大小写") { shifted = !shifted; refreshKeyLabels() }
        addToolButton(header, "✎", "手写输入尚未接入")
        addToolImage(header, android.R.drawable.ic_btn_speak_now, "语音输入尚未接入")
        addToolButton(header, "⌄", "收起键盘") { requestHideSelf(0) }
''','''        header.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_arch_logo)
            contentDescription = "Arch Linux · ArchIME"
            scaleType = ImageView.ScaleType.FIT_CENTER
        }, LinearLayout.LayoutParams(dp(26), dp(26)).apply { marginStart = dp(3); marginEnd = dp(7) })
        header.addView(status, LinearLayout.LayoutParams(0, dp(32), 1f))
        addToolButton(header, "⚙", "输入设置") { openSettings() }
        addToolButton(header, "⌄", "收起键盘") { requestHideSelf(0) }
''','header')

# Make the candidate strip calm and text-first, as in WeChat Input Method.
replace_once('''                text = "${index + 1} ${candidate.text}"
                textSize = 15f
                setTextColor(if (isDarkTheme()) Color.WHITE else 0xFF20242A.toInt())
                gravity = Gravity.CENTER
                setPadding(dp(9), dp(5), dp(9), dp(5))
                if (index == 0) background = rounded(0x551793D1, 10)
''','''                text = candidate.text
                textSize = 16f
                typeface = if (index == 0) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setTextColor(if (isDarkTheme()) Color.WHITE else 0xFF20242A.toInt())
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(dp(7), dp(5), dp(7), dp(5))
                if (index == 0) background = rounded(if (isDarkTheme()) 0x442F7BFF else 0x222F7BFF, 8)
''','candidate style')

# The central wide key is a real space key, not a fake voice button.
replace_once('''        addKey(bottom, "〰", 3.4f, 48) {
            Toast.makeText(this, "语音识别尚未接入", Toast.LENGTH_SHORT).show()
        }
''','''        addKey(bottom, "空格", 3.4f, 48) { onSpace() }
''','space key')

# Use an editor-aware action key, like WeChat's Send/Search/Return key.
replace_once('''        addKey(bottom, "↵", 1.05f, 48) { onEnter() }
''','''        actionButton = addKey(bottom, actionKeyLabel(), 1.12f, 48) { onEnter() }
''','action key')

# Add a reference to the adaptive key.
replace_once('''    private var modeButton: Button? = null
''','''    private var modeButton: Button? = null
    private var actionButton: Button? = null
''','action button reference')

# The action button uses ArchIME blue while all ordinary keys stay neutral.
replace_once('''            textSize = if (label.length > 1) 12f else 17f
            setTextColor(if (isDarkTheme()) Color.WHITE else 0xFF171A1F.toInt())
            typeface = Typeface.DEFAULT_BOLD
            background = keyRipple(when (label) {
                "〰", "↵" -> if (isDarkTheme()) 0xFF30343B.toInt() else Color.WHITE
                else -> if (isDarkTheme()) 0xFF252525.toInt() else Color.WHITE
            })
''','''            val primaryAction = label in setOf("发送", "搜索", "前往", "下一步", "完成")
            textSize = if (label.length > 1) 11f else 17f
            setTextColor(if (primaryAction) Color.WHITE else if (isDarkTheme()) Color.WHITE else 0xFF171A1F.toInt())
            typeface = Typeface.DEFAULT_BOLD
            background = keyRipple(when {
                primaryAction -> 0xFF2F7BFF.toInt()
                label == "空格" -> if (isDarkTheme()) 0xFF30343B.toInt() else Color.WHITE
                else -> if (isDarkTheme()) 0xFF252525.toInt() else Color.WHITE
            })
''','key styling')

# Add adaptive action-label helpers.
insert_before = '''    private fun onEnter() {
'''
helpers = '''    private fun actionKeyLabel(): String {
        val action = currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
            ?: EditorInfo.IME_ACTION_NONE
        return when (action) {
            EditorInfo.IME_ACTION_SEND -> "发送"
            EditorInfo.IME_ACTION_SEARCH -> "搜索"
            EditorInfo.IME_ACTION_GO -> "前往"
            EditorInfo.IME_ACTION_NEXT -> "下一步"
            EditorInfo.IME_ACTION_DONE -> "完成"
            else -> "换行"
        }
    }

'''
if s.count(insert_before) != 1:
    raise SystemExit('action helper insertion point missing or ambiguous')
s = s.replace(insert_before, helpers + insert_before, 1)

# A single settings entry point is used by the compact header.
insert_before = '''    private fun addUtilityButton(row: LinearLayout, label: String, message: String, action: () -> Unit) {
'''
settings = '''    private fun openSettings() {
        try {
            val launch = packageManager.getLaunchIntentForPackage(packageName)
            if (launch != null) startActivity(launch)
            else Toast.makeText(this, "请从 ArchIME 应用打开设置", Toast.LENGTH_SHORT).show()
        } catch (_: Throwable) {
            Toast.makeText(this, "无法打开设置", Toast.LENGTH_SHORT).show()
        }
    }

'''
if s.count(insert_before) != 1:
    raise SystemExit('settings helper insertion point missing or ambiguous')
s = s.replace(insert_before, settings + insert_before, 1)

# Keep the existing bottom utility action wired to the same real settings screen.
old_settings = '''        addUtilityButton(utility, "☷", "键盘管理设置") {
            try {
                val launch = packageManager.getLaunchIntentForPackage(packageName)
                if (launch != null) startActivity(launch)
                else Toast.makeText(this, "请从 ArchIME 应用打开设置", Toast.LENGTH_SHORT).show()
            } catch (_: Throwable) {
                Toast.makeText(this, "无法打开设置", Toast.LENGTH_SHORT).show()
            }
        }
'''
replace_once(old_settings, '''        addUtilityButton(utility, "☷", "键盘管理设置") { openSettings() }
''', 'settings button')

# Refresh the adaptive action label whenever a new editor is focused.
replace_once('''        status.text = inputStatus()
        shifted = false
        refreshKeyLabels()
''','''        status.text = inputStatus()
        actionButton?.text = actionKeyLabel()
        shifted = false
        refreshKeyLabels()
''','refresh editor action')

p.write_text(s, encoding='utf-8')
print('Applied clean, WeChat-inspired ArchIME keyboard adjustments.')
