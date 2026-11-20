#!/usr/bin/env python3
from pathlib import Path
import math
import random
import struct
import wave

root = Path("ArchIME-0.7.2-dev/app/src/main")
source = Path("patches/ArchInputMethodService.kt")
target = root / "java/org/archime/ime/ArchInputMethodService.kt"
target.parent.mkdir(parents=True, exist_ok=True)
target.write_text(source.read_text(encoding="utf-8"), encoding="utf-8")

# Short original, low-volume mahjong-tile-like wooden clack. No external audio assets.
raw_dir = root / "res/raw"
raw_dir.mkdir(parents=True, exist_ok=True)
out = raw_dir / "mahjong_key.wav"
rate = 22050
duration = 0.095
samples = []
rng = random.Random(20261120)
for i in range(int(rate * duration)):
    t = i / rate
    envelope = math.exp(-t * 52.0)
    # Two tiny, slightly detuned resonances plus a little woody transient noise.
    tone = 0.48 * math.sin(2 * math.pi * 720 * t) + 0.22 * math.sin(2 * math.pi * 1180 * t)
    noise = (rng.random() * 2.0 - 1.0) * math.exp(-t * 95.0) * 0.28
    # A very short second tick gives a tile-on-tile "tak" character.
    second = 0.18 * math.sin(2 * math.pi * 940 * max(0.0, t - 0.018)) * math.exp(-max(0.0, t - 0.018) * 85.0) if t >= 0.018 else 0.0
    value = max(-1.0, min(1.0, (tone * envelope + noise + second) * 0.52))
    samples.append(int(value * 32767))
with wave.open(str(out), "wb") as wav:
    wav.setnchannels(1)
    wav.setsampwidth(2)
    wav.setframerate(rate)
    wav.writeframes(b"".join(struct.pack("<h", x) for x in samples))

# Add explicit, independent feedback switches to the setup page.
manifest = root / "AndroidManifest.xml"
manifest_text = manifest.read_text(encoding="utf-8")
permission = '<uses-permission android:name="android.permission.VIBRATE" />'
if permission not in manifest_text:
    manifest_end = manifest_text.find(">")
    if manifest_end < 0:
        raise SystemExit("Malformed AndroidManifest.xml: opening manifest tag not found")
    manifest_text = manifest_text[:manifest_end + 1] + "\\n    " + permission + manifest_text[manifest_end + 1:]
manifest.write_text(manifest_text, encoding="utf-8")

activity = root / "java/org/archime/ime/MainActivity.kt"
text = activity.read_text(encoding="utf-8")
marker = '        root.addView(label("切换开关后，重新弹出键盘即可生效。", 11f, 0xFF8292A8.toInt()).apply { setPadding(dp(4), 0, 0, dp(18)) })'
if marker not in text:
    raise SystemExit("Could not locate settings insertion point in MainActivity.kt")
if 'name="key_vibration"' not in text:
    addition = '''
        root.addView(Switch(this).apply {
            text = "按键震动反馈"
            setTextColor(Color.WHITE)
            isChecked = prefs.getBoolean("key_vibration", true)
            setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean("key_vibration", on).apply() }
            setPadding(dp(4), dp(6), dp(4), dp(2))
        })
        root.addView(Switch(this).apply {
            text = "麻将牌轻敲音效"
            setTextColor(Color.WHITE)
            isChecked = prefs.getBoolean("mahjong_sound", true)
            setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean("mahjong_sound", on).apply() }
            setPadding(dp(4), dp(2), dp(4), dp(12))
        })
'''
    text = text.replace(marker, marker + addition)
activity.write_text(text, encoding="utf-8")
print(f"Installed keyboard service: {target}")
print(f"Generated original Mahjong key sound: {out} ({out.stat().st_size} bytes)")
print("Added separate haptic and sound toggles to setup screen.")
