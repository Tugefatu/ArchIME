#!/usr/bin/env python3
"""Generate a short original mahjong-tile-like key click for ArchIME."""
import math
import random
import struct
import wave
from pathlib import Path

random.seed(23)
sample_rate = 44100
duration = 0.105
count = int(sample_rate * duration)
samples = []
for i in range(count):
    t = i / sample_rate
    # Two short, wooden/ceramic resonances with a tiny broadband attack.
    attack = math.exp(-t * 115.0)
    body = (
        0.72 * math.sin(2 * math.pi * 1180 * t) * math.exp(-t * 42.0)
        + 0.34 * math.sin(2 * math.pi * 2360 * t) * math.exp(-t * 57.0)
        + 0.16 * math.sin(2 * math.pi * 3470 * t) * math.exp(-t * 74.0)
    )
    noise = (random.random() * 2.0 - 1.0) * attack * 0.18
    envelope = min(1.0, t / 0.0015) * math.exp(-t * 5.0)
    value = max(-1.0, min(1.0, (body + noise) * envelope))
    samples.append(struct.pack("<h", int(value * 24500)))

out = Path("ArchIME-0.7.2-dev/app/src/main/res/raw/mahjong_tile.wav")
out.parent.mkdir(parents=True, exist_ok=True)
with wave.open(str(out), "wb") as wav:
    wav.setnchannels(1)
    wav.setsampwidth(2)
    wav.setframerate(sample_rate)
    wav.writeframes(b"".join(samples))
print(f"Generated {out} ({out.stat().st_size} bytes)")
