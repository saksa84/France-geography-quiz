"""Synthétise les sons de réponse du quiz : plusieurs candidats à écouter, puis les deux retenus.

Usage (depuis la racine du projet) :
    python -X utf8 tools/build_sounds.py
        -> tools/sons-candidats/ : tous les candidats, à écouter sur le PC (dossier ignoré par Git)
    python -X utf8 tools/build_sounds.py --choisir correct=marimba erreur=quinte
        -> app/src/main/res/raw/answer_correct.wav et answer_wrong.wav : seuls ces deux-là vont dans l'application

Les sons sont synthétisés ici, note par note, avec la bibliothèque standard de Python : aucun
fichier audio extérieur, donc aucune question de licence. Le script est déterministe : le relancer
redonne exactement les mêmes fichiers (mono, 16 bits, 44,1 kHz).
"""

import argparse
import math
import random
import struct
import sys
import wave
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CANDIDATES_DIR = ROOT / "tools" / "sons-candidats"
RAW_DIR = ROOT / "app" / "src" / "main" / "res" / "raw"
RATE = 44_100


def note(freq, duration, harmonics, decay, attack=0.005, glide=0.0):
    """Une note : somme d'harmoniques (rang, amplitude), attaque brève puis décroissance exponentielle.
    `glide` fait glisser la hauteur (fraction de la fréquence perdue sur la durée), pour un « pop » ou un « bonk »."""
    samples = []
    phase = 0.0
    n = int(RATE * duration)
    for i in range(n):
        t = i / RATE
        f = freq * (1 - glide * i / n)
        phase += 2 * math.pi * f / RATE
        envelope = min(1.0, t / attack) * math.exp(-decay * t)
        samples.append(envelope * sum(amp * math.sin(phase * rank) for rank, amp in harmonics))
    fade = int(RATE * 0.01)  # fin adoucie sur 10 ms pour éviter un clic
    for i in range(1, fade + 1):
        samples[-i] *= i / fade
    return samples


def noise_burst(duration, decay, seed, smoothing=0.85):
    """Souffle bref (bruit filtré passe-bas), déterministe grâce à la graine."""
    rng = random.Random(seed)
    out, last = [], 0.0
    for i in range(int(RATE * duration)):
        last = smoothing * last + (1 - smoothing) * rng.uniform(-1, 1)
        out.append(last * math.exp(-decay * i / RATE))
    return out


def silence(duration):
    return [0.0] * int(RATE * duration)


def mix(*tracks):
    length = max(len(t) for t in tracks)
    return [sum(t[i] for t in tracks if i < len(t)) for i in range(length)]


def held_note(freq, duration, harmonics, hold, decay, attack=0.015):
    """Note tenue : attaque, palier de `hold` secondes à pleine intensité, puis décroissance exponentielle."""
    samples = []
    for i in range(int(RATE * duration)):
        t = i / RATE
        envelope = min(1.0, t / attack) * (1.0 if t < hold else math.exp(-decay * (t - hold)))
        samples.append(envelope * sum(amp * math.sin(2 * math.pi * freq * rank * t) for rank, amp in harmonics))
    fade = int(RATE * 0.01)
    for i in range(1, fade + 1):
        samples[-i] *= i / fade
    return samples


def with_echo(samples, delay, gain, repeats=2):
    """Ajoute des répétitions atténuées, comme un écho de salle."""
    out = samples + [0.0] * int(RATE * delay * repeats)
    for r in range(1, repeats + 1):
        offset = int(RATE * delay * r)
        for i, s in enumerate(samples):
            out[i + offset] += s * gain ** r
    return out


def quinte():
    """Ré puis la (293,7 Hz → 440 Hz), fondamentale et octave seulement : son rond et doux.
    Reproduit les caractéristiques mesurées d'un son de Pixabay que j'aimais, sans en reprendre le fichier."""
    first = held_note(293.66, 0.30, [(1, 0.76), (2, 1.0)], hold=0.15, decay=12)
    second = held_note(440.0, 0.70, [(1, 0.91), (2, 1.0)], hold=0.15, decay=7)
    return with_echo(first + second, delay=0.22, gain=0.18)


BELL = [(1, 1.0), (2, 0.35), (3, 0.12)]
MARIMBA = [(1, 1.0), (4, 0.25), (10, 0.05)]
SOFT = [(1, 1.0), (2, 0.15)]
BUZZ = [(1, 1.0), (3, 0.35), (5, 0.18), (7, 0.08)]

# Bonne réponse : courts, clairs, plutôt montants.
CORRECT = {
    "carillon": lambda: note(1046.5, 0.09, BELL, 20) + note(1318.5, 0.09, BELL, 20) + note(1568.0, 0.35, BELL, 8),
    "pop": lambda: note(880.0, 0.12, SOFT, 25, attack=0.002, glide=-0.35),
    "marimba": lambda: note(784.0, 0.10, MARIMBA, 22, attack=0.002) + note(1046.5, 0.30, MARIMBA, 12, attack=0.002),
    "cloche": lambda: note(1174.7, 0.45, [(1, 1.0), (2.76, 0.3), (5.4, 0.1)], 7),
}

# Erreur : graves, brefs, sans agressivité.
WRONG = {
    "buzzer": lambda: note(155.6, 0.28, BUZZ, 9),
    "deux-notes": lambda: note(330.0, 0.14, SOFT, 10) + silence(0.02) + note(311.1, 0.30, SOFT, 8),
    "bonk": lambda: note(220.0, 0.22, MARIMBA, 16, attack=0.002, glide=0.3),
    "souffle": lambda: mix(noise_burst(0.25, 14, seed=7), note(196.0, 0.25, SOFT, 12)),
    "quinte": quinte,
}


def write(path: Path, samples, peak=0.7):
    scale = peak / max(abs(s) for s in samples)
    frames = b"".join(struct.pack("<h", round(s * scale * 32767)) for s in samples)
    path.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(path), "wb") as out:
        out.setnchannels(1)
        out.setsampwidth(2)
        out.setframerate(RATE)
        out.writeframes(frames)
    print(f"{path.relative_to(ROOT)} : {len(samples) / RATE:.2f} s")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--choisir", nargs=2, metavar=("correct=NOM", "erreur=NOM"), help="sons retenus pour l'application")
    args = parser.parse_args()

    if not args.choisir:
        for name, make in CORRECT.items():
            write(CANDIDATES_DIR / f"correct-{name}.wav", make())
        for name, make in WRONG.items():
            write(CANDIDATES_DIR / f"erreur-{name}.wav", make())
        print(f"Écoutez les fichiers de {CANDIDATES_DIR}, puis relancez avec --choisir correct=… erreur=…")
        return 0

    choice = dict(item.split("=", 1) for item in args.choisir)
    correct, wrong = choice.get("correct"), choice.get("erreur")
    if correct not in CORRECT or wrong not in WRONG:
        print(f"ERREUR : choisir parmi correct={sorted(CORRECT)} et erreur={sorted(WRONG)}")
        return 1
    write(RAW_DIR / "answer_correct.wav", CORRECT[correct]())
    write(RAW_DIR / "answer_wrong.wav", WRONG[wrong]())
    return 0


if __name__ == "__main__":
    sys.exit(main())
