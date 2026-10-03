#!/usr/bin/env python3
"""
Daily lesson updater — fetches new episodes and prunes old ones.

What it does each run
---------------------
1. Lists the newest uploads for each source channel
2. Skips anything already transcribed  ← old lessons are KEPT, never re-done
3. Transcribes the new ones and records their publish date
4. Prunes lessons older than --keep-days so the bundle can't grow forever
5. Rebuilds index.json

Usage
-----
    python update_daily.py --out out --keep-days 60
    python update_daily.py --out out --sources CNN10 PBS --per-source 1
    python update_daily.py --out out --no-prune          # keep everything
"""

import argparse
import json
import os
import re
import subprocess
import sys
from datetime import date, datetime, timedelta, timezone

HERE = os.path.dirname(os.path.abspath(__file__))

CHANNELS = {
    "CNN10": "CNN10",
    "CBS": "CBSNews",
    "PBS": "PBSNewsHour",
    "ABC": "ABCNews",
}

# Display order used in index.json and the app.
# VOA is intentionally excluded — its videos are region-blocked.
SOURCE_ORDER = ["CNN10", "CBS", "PBS", "ABC"]

MONTHS = {
    "january": 1, "february": 2, "march": 3, "april": 4, "may": 5, "june": 6,
    "july": 7, "august": 8, "september": 9, "october": 10, "november": 11,
    "december": 12,
}


def parse_relative(rel: str, today: date) -> str:
    """Turn '2d ago' / '3 weeks ago' into an ISO date."""
    if not rel:
        return today.isoformat()
    m = re.search(r"(\d+)\s*(hour|day|week|month|year)", rel, re.I)
    if not m:
        return today.isoformat()
    n = int(m.group(1))
    unit = m.group(2).lower()
    days = {
        "hour": 0, "day": 1, "week": 7, "month": 30, "year": 365,
    }[unit] * n
    return (today - timedelta(days=days)).isoformat()


def parse_title_date(title: str):
    """CNN 10 titles end with '| October 1, 2026'. Returns ISO or None."""
    m = re.search(r"([A-Z][a-z]+)\s+(\d{1,2}),\s*(\d{4})", title)
    if not m:
        return None
    month = MONTHS.get(m.group(1).lower())
    if not month:
        return None
    try:
        return date(int(m.group(3)), month, int(m.group(2))).isoformat()
    except ValueError:
        return None


def lesson_date(title: str, rel: str, today: date) -> str:
    """Prefer an explicit date in the title, else derive from the relative time."""
    return parse_title_date(title) or parse_relative(rel, today)


def latest_videos(handle: str, limit: int):
    sys.path.insert(0, HERE)
    from fetch_video_ids import fetch, parse

    html = fetch(handle)
    if not html:
        return []
    _, videos = parse(html)
    return videos[:limit]


def already_have(out_dir: str, video_id: str) -> bool:
    return os.path.isfile(os.path.join(out_dir, f"{video_id}.json"))


def prune(out_dir: str, keep_days: int):
    """Delete lesson files whose publishedAt is older than keep_days."""
    if keep_days <= 0:
        return 0
    cutoff = (date.today() - timedelta(days=keep_days)).isoformat()
    removed = 0
    for name in os.listdir(out_dir):
        if not name.endswith(".json") or name == "index.json":
            continue
        path = os.path.join(out_dir, name)
        try:
            with open(path, encoding="utf-8") as f:
                data = json.load(f)
        except Exception:
            continue
        published = data.get("publishedAt") or ""
        if published and published < cutoff:
            os.remove(path)
            print(f"  pruned {name} (published {published})")
            removed += 1
    return removed


def rebuild_index(out_dir: str):
    """Write index.json grouped by source, newest first inside each source."""
    entries = []
    for name in os.listdir(out_dir):
        if not name.endswith(".json") or name == "index.json":
            continue
        try:
            with open(os.path.join(out_dir, name), encoding="utf-8") as f:
                data = json.load(f)
        except Exception:
            continue
        entries.append({
            "file": name,
            "videoId": data.get("videoId", ""),
            "title": data.get("title", ""),
            "source": data.get("source", ""),
            "durationSecs": data.get("durationSecs", 0),
            "publishedAt": data.get("publishedAt", ""),
            "segments": len(data.get("segments", [])),
        })

    def sort_key(e):
        src = e["source"]
        rank = SOURCE_ORDER.index(src) if src in SOURCE_ORDER else len(SOURCE_ORDER)
        # source order first, then newest-first inside each source
        return (rank, _neg_date(e["publishedAt"]), e["file"])

    entries.sort(key=sort_key)

    # Also emit an explicit grouping so clients don't have to re-derive it
    groups = []
    for src in SOURCE_ORDER:
        files = [e["file"] for e in entries if e["source"] == src]
        if files:
            groups.append({"source": src, "count": len(files), "lessons": files})

    index = {
        "updatedAt": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "count": len(entries),
        "keepDays": None,
        "lessons": [e["file"] for e in entries],
        "entries": entries,
        "groups": groups,
    }
    with open(os.path.join(out_dir, "index.json"), "w", encoding="utf-8") as f:
        json.dump(index, f, ensure_ascii=False, indent=2)
    print(f"index.json -> {len(entries)} lessons in {len(groups)} sources")


def _neg_date(iso: str):
    """Sort helper: ISO date string -> a key that orders newest first."""
    return "".join(chr(255 - ord(c)) for c in (iso or "0000-00-00"))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default="out")
    ap.add_argument("--sources", nargs="*", default=list(CHANNELS.keys()))
    ap.add_argument("--per-source", type=int, default=1)
    ap.add_argument("--keep-days", type=int, default=60,
                    help="prune lessons older than this (0 = keep forever)")
    ap.add_argument("--no-prune", action="store_true")
    ap.add_argument("--model", default="small.en")
    ap.add_argument("--workdir", default=None)
    args = ap.parse_args()

    os.makedirs(args.out, exist_ok=True)
    workdir = args.workdir or os.path.join(args.out, "audio")
    today = date.today()

    added = 0
    for source in args.sources:
        handle = CHANNELS.get(source)
        if not handle:
            print(f"unknown source {source}, skipping")
            continue

        print(f"\n=== {source} (@{handle}) ===")
        got = 0
        for vid, title, rel in latest_videos(handle, args.per_source * 4):
            if got >= args.per_source:
                break
            if already_have(args.out, vid):
                print(f"  {vid} already have it — keeping")
                continue

            published = lesson_date(title, rel, today)
            print(f"  transcribing {vid} [{published}] — {title[:52]}")
            try:
                subprocess.run(
                    [sys.executable, os.path.join(HERE, "transcribe.py"),
                     f"https://www.youtube.com/watch?v={vid}",
                     f"--video-id={vid}", f"--title={title}",
                     f"--source={source}", f"--model={args.model}",
                     f"--published={published}",
                     f"--out={args.out}", f"--workdir={workdir}"],
                    check=True,
                )
                subprocess.run(
                    [sys.executable, os.path.join(HERE, "fixup.py"),
                     os.path.join(args.out, f"{vid}.json")],
                    check=True,
                )
                added += 1
                got += 1
            except subprocess.CalledProcessError as e:
                print(f"    failed: {e}")

    if not args.no_prune and args.keep_days > 0:
        print(f"\n=== pruning (keep {args.keep_days} days) ===")
        removed = prune(args.out, args.keep_days)
        print(f"  {removed} removed")

    rebuild_index(args.out)
    print(f"\ndone — {added} new lesson(s)")


if __name__ == "__main__":
    main()
