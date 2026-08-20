"""Synthesize short animal sounds as 16-bit PCM mono WAV clips.

Outputs to src/main/resources/sounds/animal/<type>/0.wav and 1.wav.
AudioManager plays "sounds/animal/cow" etc.; SoundLoader scans 0.wav, 1.wav.
"""
import math
import os
import random
import struct
import wave

SR = 22050
OUT_DIR = os.path.join(os.path.dirname(__file__), "..",
                       "src", "main", "resources", "sounds", "animal")


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


def envelope(n, attack, release):
    """Per-sample gain: fast attack, slow release."""
    out = []
    for i in range(n):
        g = 1.0
        if i < attack * SR:
            g = i / (attack * SR)
        elif i > n - release * SR:
            g = max(0.0, (n - i) / (release * SR))
        out.append(g)
    return out


def tone(freq_start, freq_end, dur, harm=(1.0, 0.4, 0.18), vibrato=(0.0, 0.0), rng=None):
    n = int(SR * dur)
    env = envelope(n, 0.08, 0.25)
    out = []
    for i in range(n):
        t = i / SR
        prog = t / dur
        f = freq_start + (freq_end - freq_start) * prog
        f += vibrato[1] * math.sin(2 * math.pi * vibrato[0] * t)
        s = 0.0
        for h, amp in enumerate(harm):
            s += amp * math.sin(2 * math.pi * f * (h + 1) * t)
        if rng is not None:
            s += (rng.random() - 0.5) * 0.05
        out.append(s * env[i] * 0.55)
    return out


def noise_bursts(dur, clicks, rng=None):
    """Short percussive bursts separated by gaps (chicken cluck)."""
    n = int(SR * dur)
    env = envelope(n, 0.01, 0.05)
    r = rng or random
    out = [0.0] * n
    for at in clicks:
        start = int(at * SR)
        length = int(0.06 * SR)
        for i in range(length):
            if start + i >= n:
                break
            g = (i / (0.01 * SR)) if i < 0.01 * SR else max(0.0, 1.0 - (i - 0.01 * SR) / (0.05 * SR))
            out[start + i] += (r.random() - 0.5) * g * 0.6
    return out


def main():
    random.seed(7)
    base = os.path.join(OUT_DIR)

    # Cow: deep "moo", descending pitch with a soft vibrato
    write_wav(os.path.join(base, "cow", "0.wav"),
              tone(155, 105, 1.0, (1.0, 0.35, 0.15), (4.0, 6.0)))
    write_wav(os.path.join(base, "cow", "1.wav"),
              tone(170, 120, 0.9, (1.0, 0.3, 0.12), (3.5, 5.0)))

    # Pig: short squealing "oink"
    write_wav(os.path.join(base, "pig", "0.wav"),
              tone(380, 620, 0.3, (1.0, 0.5, 0.25), (18.0, 40.0), random.Random(1)))
    write_wav(os.path.join(base, "pig", "1.wav"),
              tone(420, 680, 0.28, (1.0, 0.45, 0.2), (20.0, 50.0), random.Random(2)))

    # Chicken: three clucks in a row
    write_wav(os.path.join(base, "chicken", "0.wav"),
              noise_bursts(0.5, (0.02, 0.13, 0.24), random.Random(3)))
    write_wav(os.path.join(base, "chicken", "1.wav"),
              noise_bursts(0.55, (0.02, 0.12, 0.23, 0.34), random.Random(4)))

    # Sheep: bleating "baa" with a strong tremolo
    write_wav(os.path.join(base, "sheep", "0.wav"),
              tone(240, 190, 0.75, (1.0, 0.4, 0.2), (7.0, 90.0)))
    write_wav(os.path.join(base, "sheep", "1.wav"),
              tone(260, 210, 0.7, (1.0, 0.35, 0.18), (6.0, 80.0)))


if __name__ == "__main__":
    main()