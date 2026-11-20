package org.archime.ime

import android.app.Activity
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView

class SettingsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 28, 24, 24)
            setBackgroundColor(0xFF101216.toInt())
        }
        root.addView(TextView(this).apply {
            text = "◢  ArchIME 设置"
            textSize = 25f
            setTextColor(0xFF1793D1.toInt())
        })
        root.addView(TextView(this).apply {
            text = "键盘调整"
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 28, 0, 8)
        })
        val heightLabel = TextView(this).apply {
            text = "按键高度：${prefs.getInt("key_height", 48)} dp"
            textSize = 14f
            setTextColor(0xFFCFD5DE.toInt())
        }
        root.addView(heightLabel)
        root.addView(SeekBar(this).apply {
            max = 24
            progress = (prefs.getInt("key_height", 48) - 40).coerceIn(0, 24)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val value = 40 + progress
                    heightLabel.text = "按键高度：$value dp"
                    prefs.edit().putInt("key_height", value).apply()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        })
        root.addView(Switch(this).apply {
            text = "按键音效"
            textSize = 15f
            setTextColor(0xFFCFD5DE.toInt())
            isChecked = prefs.getBoolean("sound_enabled", false)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean("sound_enabled", checked).apply()
            }
        })
        val volumeLabel = TextView(this).apply {
            text = "按键音量：${prefs.getInt("sound_volume", 35)}%"
            textSize = 14f
            setTextColor(0xFFCFD5DE.toInt())
        }
        root.addView(volumeLabel)
        root.addView(SeekBar(this).apply {
            max = 100
            progress = prefs.getInt("sound_volume", 35).coerceIn(0, 100)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    volumeLabel.text = "按键音量：$progress%"
                    prefs.edit().putInt("sound_volume", progress).apply()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        })
        root.addView(TextView(this).apply {
            text = "按键反馈"
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 24, 0, 8)
        })
        root.addView(Switch(this).apply {
            text = "按键震动"
            textSize = 15f
            setTextColor(0xFFCFD5DE.toInt())
            isChecked = prefs.getBoolean("haptics_enabled", true)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean("haptics_enabled", checked).apply()
            }
        })
        val hapticLabel = TextView(this).apply {
            text = "震动强度：${prefs.getInt("haptic_strength", 45)}%"
            textSize = 14f
            setTextColor(0xFFCFD5DE.toInt())
            setPadding(0, 18, 0, 0)
        }
        root.addView(hapticLabel)
        root.addView(SeekBar(this).apply {
            max = 100
            progress = prefs.getInt("haptic_strength", 45).coerceIn(1, 100)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val value = progress.coerceAtLeast(1)
                    hapticLabel.text = "震动强度：$value%"
                    prefs.edit().putInt("haptic_strength", value).apply()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        })
        root.addView(TextView(this).apply {
            text = "设置会保存在本机。微软双拼由 Rime 引擎提供；首次启动可能需要初始化词库。部分设备不支持精确震动强度。"
            textSize = 12f
            setTextColor(0xFF8D98A8.toInt())
            setPadding(0, 22, 0, 0)
        })
        setContentView(root)
    }
}
