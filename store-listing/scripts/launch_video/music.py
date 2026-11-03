"""Soundtrack for the SMS Toolbox brag video: 120 BPM, A minor, 24 s, one beat grid from frame 0."""
import numpy as np
import wave

SR = 48000
BPM = 120
BEAT = 60 / BPM
DUR = 24.0
N = int(SR * DUR)
rng = np.random.default_rng(7)

def t_arr(n):
    return np.arange(n) / SR

def midi(m):
    return 440.0 * 2 ** ((m - 69) / 12)

def add(buf, start_s, sig, gain=1.0):
    i = int(round(start_s * SR))
    if i >= len(buf):
        return
    sig = sig.copy()
    r = min(len(sig), int(0.012 * SR))  # 12 ms release so nothing ends on a click
    sig[-r:] *= np.linspace(1, 0, r)
    j = min(len(buf), i + len(sig))
    buf[i:j] += sig[: j - i] * gain

def env_adsr(n, a=0.005, d=0.1, s=0.6, r=0.1, hold=None):
    t = t_arr(n)
    hold = hold if hold is not None else n / SR - r
    e = np.where(t < a, t / a, 1.0)
    e = np.where((t >= a) & (t < a + d), 1 - (1 - s) * (t - a) / d, e)
    e = np.where((t >= a + d) & (t < hold), s, e)
    e = np.where(t >= hold, s * np.clip(1 - (t - hold) / r, 0, 1), e)
    return e

def lowpass(x, cutoff):
    # one-pole, run twice for 12 dB/oct
    a = np.exp(-2 * np.pi * cutoff / SR)
    for _ in range(2):
        y = np.empty_like(x)
        acc = 0.0
        for k in range(len(x)):
            acc = (1 - a) * x[k] + a * acc
            y[k] = acc
        x = y
    return x

def lp_fast(x, cutoff):
    # vectorized one-pole via scipy-free IIR using cumulative trick is messy; use FFT brickwall-ish smooth filter
    X = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    H = 1 / np.sqrt(1 + (f / cutoff) ** 4)
    return np.fft.irfft(X * H, len(x))

def hp_fast(x, cutoff):
    X = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    H = 1 - 1 / np.sqrt(1 + (f / cutoff) ** 4)
    return np.fft.irfft(X * H, len(x))

def saw(freq, n, detune=0.0):
    t = t_arr(n)
    out = np.zeros(n)
    for d in (-detune, 0, detune):
        ph = (t * freq * (1 + d)) % 1.0
        out += 2 * ph - 1
    return out / 3

# progression per 2-s bar: Am F C G (x3)
CHORDS = [
    (57, [57, 60, 64]),  # Am
    (53, [53, 57, 60]),  # F
    (48, [48, 52, 55]),  # C
    (55, [55, 59, 62]),  # G
]
def chord_at(t):
    return CHORDS[int(t // (4 * BEAT)) % 4]

mus = {k: np.zeros(N) for k in ("kick", "snare", "hat", "bass", "pad", "arp", "sfx")}

# --- pad: whole track, swells in during the hook
for bar in range(12):
    t0 = bar * 4 * BEAT
    root, notes = chord_at(t0)
    n = int(4 * BEAT * SR) + int(0.3 * SR)
    sig = np.zeros(n)
    for m in notes:
        sig += saw(midi(m), n, 0.004) + 0.5 * saw(midi(m + 12), n, 0.006)
    sig = lp_fast(sig, 1400 if t0 >= 3 else 900)
    e = env_adsr(n, a=0.25, d=0.3, s=0.8, r=0.3)
    add(mus["pad"], t0, sig * e, 0.10)

# --- kick: from the reveal (3.0 s) to the end of the outro hit
for b in range(48):
    t0 = b * BEAT
    if t0 < 3.0 or t0 >= 23.0:
        continue
    n = int(0.35 * SR)
    t = t_arr(n)
    f = 45 + 85 * np.exp(-t * 28)
    ph = 2 * np.pi * np.cumsum(f) / SR
    sig = np.sin(ph) * np.exp(-t * 9) + 0.15 * np.sin(ph * 2) * np.exp(-t * 30)
    add(mus["kick"], t0, sig, 0.9)

# --- snare/clap on 2 and 4, from 5.0
for b in range(48):
    t0 = b * BEAT
    if t0 < 5.0 or t0 >= 23.0 or b % 2 == 0:
        continue
    n = int(0.25 * SR)
    t = t_arr(n)
    noise = hp_fast(rng.standard_normal(n), 1200)
    body = np.sin(2 * np.pi * 196 * t) * np.exp(-t * 25)
    sig = noise * np.exp(-t * 22) * 0.6 + body * 0.4
    add(mus["snare"], t0, sig, 0.35)

# --- hats: 8ths, soft, from 5.0
for k in range(96):
    t0 = k * BEAT / 2
    if t0 < 5.0 or t0 >= 23.0:
        continue
    n = int(0.06 * SR)
    t = t_arr(n)
    sig = hp_fast(rng.standard_normal(n), 7000) * np.exp(-t * (70 if k % 2 else 45))
    add(mus["hat"], t0, sig, 0.09 if k % 2 else 0.06)

# --- bass: 8th-note roots with an octave pop, from 3.0
for k in range(96):
    t0 = k * BEAT / 2
    if t0 < 3.0 or t0 >= 23.0:
        continue
    root, _ = chord_at(t0)
    m = root - 12 + (12 if k % 4 == 3 else 0)
    n = int(0.24 * SR)
    t = t_arr(n)
    sig = 0.7 * np.sin(2 * np.pi * midi(m) * t) + 0.3 * lp_fast(saw(midi(m), n), 600)
    add(mus["bass"], t0, sig * env_adsr(n, a=0.004, d=0.08, s=0.7, r=0.06), 0.42)

# --- arp: 8th-note chord tones, from 5.0
for k in range(96):
    t0 = k * BEAT / 2
    if t0 < 5.0 or t0 >= 21.0:
        continue
    _, notes = chord_at(t0)
    pattern = [0, 1, 2, 1]
    m = notes[pattern[k % 4]] + 12
    n = int(0.3 * SR)
    t = t_arr(n)
    sig = (np.sin(2 * np.pi * midi(m) * t) + 0.3 * np.sin(2 * np.pi * midi(m) * 2 * t)) * np.exp(-t * 10)
    add(mus["arp"], t0, sig, 0.09)

# --- outro: final Am hit at 21.0, rings out
n = int(3.0 * SR)
t = t_arr(n)
hit = np.zeros(n)
for m in (57, 60, 64, 69, 72):
    hit += np.sin(2 * np.pi * midi(m) * t) * np.exp(-t * 1.2)
add(mus["arp"], 21.0, hit, 0.10)

# --- sfx (tuned to the key, sitting under the music)
def bell(m, n_s=0.8):
    n = int(n_s * SR)
    t = t_arr(n)
    return (np.sin(2 * np.pi * midi(m) * t) + 0.25 * np.sin(2 * np.pi * midi(m) * 3.01 * t) * np.exp(-t * 8)) * np.exp(-t * 5)

def whoosh(length=0.45):
    n = int(length * SR)
    t = t_arr(n)
    sig = lp_fast(rng.standard_normal(n), 2500)
    e = (t / length) ** 2
    return sig * e

for cut in (3.0, 5.0, 9.5, 13.0, 17.0, 21.0):
    add(mus["sfx"], cut - 0.45, whoosh(0.45), 0.06)
    root, _ = chord_at(cut)
    add(mus["sfx"], cut, bell(root + 24), 0.07)

# hook: soft typing ticks on the words, a bubble pop
add(mus["sfx"], 0.05, bell(76, 0.4), 0.05)
for i in range(6):
    add(mus["sfx"], 0.6 + i * 0.09, bell(81, 0.12), 0.025)

# backup: mail rows arrive on beats
for i in range(5):
    t0 = 6.0 + i * 0.5
    _, notes = chord_at(t0)
    add(mus["sfx"], t0, bell(notes[i % 3] + 24, 0.3), 0.04)
# restore: tap + completion
add(mus["sfx"], 10.25, bell(76, 0.15), 0.05)
add(mus["sfx"], 12.1, bell(81, 0.6), 0.05)
add(mus["sfx"], 12.35, bell(88, 0.6), 0.04)
# redirect: notification two-note ding, forward arrival
add(mus["sfx"], 13.5, bell(76, 0.4), 0.06)
add(mus["sfx"], 13.75, bell(81, 0.5), 0.06)
add(mus["sfx"], 14.5, bell(84, 0.4), 0.05)
# schedule: save tap + item lands
add(mus["sfx"], 19.6, bell(79, 0.15), 0.05)
add(mus["sfx"], 20.0, bell(84, 0.5), 0.05)

# --- mix: sidechain duck pad/arp from the kick
duck = np.ones(N)
for b in range(48):
    t0 = b * BEAT
    if 3.0 <= t0 < 23.0:
        i = int(t0 * SR)
        n = int(0.25 * SR)
        j = min(N, i + n)
        duck[i:j] = np.minimum(duck[i:j], 0.55 + 0.45 * (np.arange(j - i) / n))

mix = (mus["kick"] + mus["snare"] + mus["hat"] + mus["bass"]
       + (mus["pad"] + mus["arp"]) * duck + mus["sfx"])

# fade in 30 ms, fade out last 0.8 s
mix[: int(0.03 * SR)] *= np.linspace(0, 1, int(0.03 * SR))
fo = int(0.8 * SR)
mix[-fo:] *= np.linspace(1, 0, fo) ** 1.5

mix = np.tanh(mix * 1.1)
mix /= np.max(np.abs(mix)) / 0.89  # about -1 dBFS peak

stereo = np.stack([mix, mix], axis=1)
with wave.open("music.wav", "wb") as w:
    w.setnchannels(2)
    w.setsampwidth(2)
    w.setframerate(SR)
    w.writeframes((stereo * 32767).astype(np.int16).tobytes())
print("wrote music.wav", DUR, "s")
