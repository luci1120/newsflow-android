#!/usr/bin/env python3
"""
Transcribe a news video's audio into sentence-level learning segments.

Pipeline
--------
1. yt-dlp downloads the audio track (see fetch_audio in this file)
2. faster-whisper transcribes it with word-level timestamps
3. Words are grouped into sentences using punctuation + pause detection
4. Output is written as JSON shaped for the NewsFlow app

Usage
-----
    python transcribe.py <youtube_url_or_id> [--model base.en] [--out dir]

    # or transcribe a local audio file directly
    python transcribe.py --audio path/to/file.mp3 --video-id ABC123

Requirements
------------
    pip install faster-whisper yt-dlp
    ffmpeg on PATH
"""

import argparse
import json
import os
import re
import subprocess
import sys

SENTENCE_END = re.compile(r"[.!?]['\")\]]*$")
CLAUSE_END = re.compile(r"[,;:]['\")\]]*$")

MAX_SEGMENT_SEC = 12.0
MIN_SEGMENT_SEC = 1.2
PAUSE_SPLIT_SEC = 0.75
TARGET_SEGMENT_SEC = 6.0


def fetch_audio(url_or_id: str, out_dir: str) -> str:
    """Download the audio track with yt-dlp. Returns the mp3 path."""
    os.makedirs(out_dir, exist_ok=True)
    if not url_or_id.startswith("http"):
        url_or_id = f"https://www.youtube.com/watch?v={url_or_id}"
    subprocess.run(
        [sys.executable, "-m", "yt_dlp", "--no-warnings",
         "-f", "bestaudio/best", "-x", "--audio-format", "mp3",
         "--audio-quality", "5",
         "-o", os.path.join(out_dir, "%(id)s.%(ext)s"), url_or_id],
        check=True,
    )
    vid = url_or_id.split("v=")[-1].split("&")[0]
    return os.path.join(out_dir, f"{vid}.mp3")


def fix_hf_cache():
    """
    Repair the HuggingFace cache on Windows.

    huggingface_hub normally symlinks snapshot files to blobs. On Windows
    without Developer Mode, symlink creation silently produces 0-byte files,
    and ctranslate2 then fails with "model.bin is incomplete". This copies
    the real blob content into place when a snapshot file is empty.
    """
    import shutil
    from pathlib import Path

    hub = Path.home() / ".cache" / "huggingface" / "hub"
    if not hub.exists():
        return

    for model_dir in hub.glob("models--*"):
        blobs = model_dir / "blobs"
        snaps = model_dir / "snapshots"
        if not blobs.is_dir() or not snaps.is_dir():
            continue

        blob_sizes = {b.name: b.stat().st_size for b in blobs.iterdir() if b.is_file()}

        for snap in snaps.iterdir():
            if not snap.is_dir():
                continue
            for f in snap.iterdir():
                if not f.is_file() or f.stat().st_size > 0:
                    continue
                # Find the largest blob that plausibly matches this filename
                target = None
                for name, size in sorted(blob_sizes.items(), key=lambda kv: -kv[1]):
                    cand = blobs / name
                    if cand.stat().st_size == 0:
                        continue
                    # model.bin is the big one; others matched by content sniff
                    if f.name == "model.bin" and size > 10_000_000:
                        target = cand
                        break
                    if f.name == "tokenizer.json" and 500_000 < size < 10_000_000:
                        target = cand
                        break
                    if f.name == "vocabulary.txt" and 100_000 < size < 1_000_000:
                        target = cand
                        break
                    if f.name.endswith(".json") and size < 100_000:
                        with open(cand, "rb") as fh:
                            if b"alignment_heads" in fh.read(4096):
                                target = cand
                                break
                if target:
                    print(f"  [cache] repairing {model_dir.name}/{f.name}")
                    shutil.copyfile(target, f)


def decode_to_pcm(audio_path: str):
    """
    Decode any audio file to a 16 kHz mono float32 numpy array via ffmpeg.

    We do this instead of letting faster-whisper use PyAV, because PyAV's
    `av.open(metadata_errors=...)` signature drifts between releases and
    breaks faster-whisper's decoder.
    """
    import numpy as np

    proc = subprocess.run(
        ["ffmpeg", "-loglevel", "error", "-i", audio_path,
         "-f", "s16le", "-acodec", "pcm_s16le",
         "-ar", "16000", "-ac", "1", "-"],
        capture_output=True, check=True,
    )
    pcm = np.frombuffer(proc.stdout, dtype=np.int16).astype(np.float32)
    return pcm / 32768.0


def transcribe(audio_path: str, model_size: str = "base.en"):
    """Return a flat list of word dicts with start/end seconds."""
    from faster_whisper import WhisperModel

    try:
        model = WhisperModel(model_size, device="cpu", compute_type="int8")
    except RuntimeError as e:
        if "incomplete" not in str(e):
            raise
        print("  [cache] model file incomplete — repairing and retrying")
        fix_hf_cache()
        model = WhisperModel(model_size, device="cpu", compute_type="int8")

    audio = decode_to_pcm(audio_path)
    segments, info = model.transcribe(
        audio,
        language="en",
        word_timestamps=True,
        vad_filter=True,
        beam_size=5,
        condition_on_previous_text=False,
    )
    print(f"  detected language: {info.language} (p={info.language_probability:.2f})")

    words = []
    for seg in segments:
        for w in (seg.words or []):
            token = w.word.strip()
            if token:
                words.append({"word": token, "start": w.start, "end": w.end})
    return words


def group_into_segments(words):
    """Group words into sentence-level segments."""
    if not words:
        return []

    groups = []
    current = []

    for i, w in enumerate(words):
        current.append(w)
        nxt = words[i + 1] if i + 1 < len(words) else None

        gap = (nxt["start"] - w["end"]) if nxt else 0.0
        span = current[-1]["end"] - current[0]["start"]
        text = w["word"]

        should_break = False
        if SENTENCE_END.search(text):
            should_break = True
        elif nxt and gap >= PAUSE_SPLIT_SEC:
            should_break = True
        elif span >= MAX_SEGMENT_SEC:
            should_break = True
        elif span >= TARGET_SEGMENT_SEC and CLAUSE_END.search(text):
            should_break = True

        if should_break:
            groups.append(current)
            current = []

    if current:
        groups.append(current)

    # Merge segments that are too short into the previous one
    merged = []
    for g in groups:
        dur = g[-1]["end"] - g[0]["start"]
        if merged and dur < MIN_SEGMENT_SEC:
            prev_dur = merged[-1][-1]["end"] - merged[-1][0]["start"]
            if prev_dur + dur <= MAX_SEGMENT_SEC:
                merged[-1].extend(g)
                continue
        merged.append(g)

    return merged


def build_solution(text: str):
    """Split text into the per-position accepted-answer structure."""
    return [[tok] for tok in text.split()]


def clean_text(raw: str) -> str:
    """Tidy Whisper's word-level output into readable prose."""
    t = raw.strip()
    # no space before punctuation
    t = re.sub(r"\s+([,.!?;:'\"%])", r"\1", t)
    # re-join hyphenated words Whisper splits ("self -policing" -> "self-policing")
    t = re.sub(r"(\w)\s+-\s*(\w)", r"\1-\2", t)
    # re-join split currency / units ("$ 5" -> "$5")
    t = re.sub(r"\$\s+(\d)", r"$\1", t)
    # lone lowercase i -> I
    t = re.sub(r"\bi\b", "I", t)
    # collapse whitespace
    t = re.sub(r"\s{2,}", " ", t)
    return t.strip()


def to_json(video_id, title, source, duration_secs, groups, published_at=""):
    segments = []
    for g in groups:
        text = clean_text(" ".join(w["word"] for w in g))
        if not text:
            continue
        segments.append({
            "index": len(segments),
            "startMs": int(round(g[0]["start"] * 1000)),
            "endMs": int(round(g[-1]["end"] * 1000)),
            "transcript": text,
            "solution": build_solution(text),
        })
    return {
        "videoId": video_id,
        "title": title,
        "source": source,
        "durationSecs": duration_secs,
        "publishedAt": published_at,
        "segments": segments,
    }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("url", nargs="?", help="YouTube URL or video id")
    ap.add_argument("--audio", help="local audio file (skips download)")
    ap.add_argument("--video-id", default="unknown")
    ap.add_argument("--title", default="")
    ap.add_argument("--source", default="CNN10")
    ap.add_argument("--published", default="", help="ISO date, e.g. 2026-10-01")
    ap.add_argument("--model", default="base.en")
    ap.add_argument("--out", default="out")
    ap.add_argument("--workdir", default=".work")
    args = ap.parse_args()

    if args.audio:
        audio_path = args.audio
        video_id = args.video_id
    elif args.url:
        video_id = args.url.split("v=")[-1].split("&")[0] if "http" in args.url else args.url
        print(f"[1/3] downloading audio for {video_id} ...")
        audio_path = fetch_audio(args.url, args.workdir)
    else:
        ap.error("provide a url or --audio")

    print(f"[2/3] transcribing {audio_path} with {args.model} ...")
    words = transcribe(audio_path, args.model)
    print(f"  {len(words)} words")

    print("[3/3] grouping into sentence segments ...")
    groups = group_into_segments(words)
    duration = words[-1]["end"] if words else 0
    data = to_json(
        video_id, args.title or video_id, args.source, int(duration), groups,
        published_at=args.published,
    )

    os.makedirs(args.out, exist_ok=True)
    path = os.path.join(args.out, f"{video_id}.json")
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)

    print(f"\n  wrote {len(data['segments'])} segments -> {path}")
    for s in data["segments"][:5]:
        print(f"    #{s['index']+1} {s['startMs']/1000:7.2f}s -> {s['endMs']/1000:7.2f}s  {s['transcript'][:64]}")


if __name__ == "__main__":
    main()
