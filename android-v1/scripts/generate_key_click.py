#!/usr/bin/env python3
"""Generate a short, original ArchIME key-click sample (PCM WAV)."""
import math, struct, wave
from pathlib import Path

out = Path(__file__).resolve().parents[1] / "app/src/main/res/raw/arch_key_click.wav"
out.parent.mkdir(parents=True, exist_ok=True)
rate = 22050
duration = 0.035
frames = int(rate * duration)
with wave.open(str(out), "wb") as wav:
    wav.setnchannels(1)
    wav.setsampwidth(2)
    wav.setframerate(rate)
    data = bytearray()
    for i in range(frames):
        t = i / rate
        envelope = math.exp(-t * 105.0)
        tone = math.sin(2 * math.pi * (1450 - 500 * t / duration) * t)
        sample = int(10500 * envelope * tone)
        data.extend(struct.pack("<h", max(-32768, min(32767, sample))))
    wav.writeframes(data)
print(f"Generated {out} ({out.stat().st_size} bytes)")
