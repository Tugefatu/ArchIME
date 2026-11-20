package org.archime.ime

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 32, 28, 24)
            setBackgroundColor(0xFF101216.toInt())
        }
        root.addView(TextView(this).apply {
            text = "◢  ArchIME"
            textSize = 28f
            setTextColor(0xFF1793D1.toInt())
        })
        root.addView(TextView(this).apply {
            text = "全新重写 · 第一阶段：稳定基础输入"
            textSize = 14f
            setTextColor(0xFFB8C0CC.toInt())
            setPadding(0, 12, 0, 24)
        })
        root.addView(Button(this).apply {
            text = "① 启用 ArchIME"
            setOnClickListener { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        })
        root.addView(Button(this).apply {
            text = "② 选择 ArchIME"
            setOnClickListener {
                (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
            }
        })
        root.addView(TextView(this).apply {
            text = "测试区"
            textSize = 15f
            setTextColor(0xFF1793D1.toInt())
            setPadding(0, 22, 0, 6)
        })
        val test = android.widget.EditText(this).apply {
            hint = "选择 ArchIME 后，在这里试打英文"
            setSingleLine(false)
            minLines = 2
        }
        root.addView(test, LinearLayout.LayoutParams(-1, 140))
        setContentView(root)
    }
}
