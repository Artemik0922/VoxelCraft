"""Synthesize thunder rumble clips as 16-bit PCM mono WAV.

Outputs to src/main/resources/sounds/thunder/0.wav and 1.wav.
AudioManager plays "sounds/thunder" (a random clip from the folder).

A thunder clip is filtered noise: a sharp crack at the start that rolls
into a low rumble, matching the visual lightning flash.
"""
import math
import os
import random
import struct
import wave

SR = 22050
OUT_DIR = os.path.join(os.path.dirname(__file__), "..",
                       "src", "main", "resources", "sounds", "thunder")


def write_wav(path, samples):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        frames = bytearray()
        for s in samples:
            v = max(-1.0, min(1.0, s))
            frames += struct.pack("<h", int(v * 32767))
        w.writeframes(bytes(frames))
    print("wrote", os.path.normpath(path))


def thunder_clip(dur, rng):
    """White noise whose cutoff frequency drops over time (crack -> rumble)."""
    n = int(SR * dur)
    out = []
    # One-pole low-pass with a time-varying coefficient
    lp = 0.0
    for i in range(n):
        t = i / n
        # Attack: 0.02s sharp burst
        attack = 1.0 if i < int(0.02 * SR) else 0.6
        # Cutoff slides from ~4kHz down to ~120Hz
        cutoff = 4000.0 * math.exp(-t * 5.0) + 120.0
        alpha = cutoff / (cutoff + SR)
        lp = lp + alpha * (rng.uniform(-1.0, 1.0) - lp)
        # Slow overall decay with a couple of louder rumble swells
        swell = 1.0 + 0.5 * math.sin(t * math.pi * 3.0) * math.exp(-t * 2.0)
        gain = attack * swell * math.exp(-t * 2.2) * 0.9
        out.append(lp * gain)
    return out


def main():
    rng = random.Random(2026)
    clips = [
        thunder_clip(4.5, rng),
        thunder_clip(5.5, rng),
    ]
    for i, samples in enumerate(clips):
        write_wav(os.path.join(OUT_DIR, f"{i}.wav"), samples)
    print(f"OK thunder: {len(clips)} clip(s)")


if __name__ == "__main__":
    main()