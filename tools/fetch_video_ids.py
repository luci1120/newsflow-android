#!/usr/bin/env python3
"""
Fetch real YouTube video IDs + titles from the NewsFlow news channels.

Why this exists
---------------
The YouTube Data API needs a key, and the watch page now blocks scripted
requests ("Sign in to confirm you're not a bot"). But each channel's /videos
page still embeds a public `ytInitialData` JSON blob that contains the latest
uploads. This script parses that blob.

Usage
-----
    python fetch_video_ids.py

Paste the printed IDs into `MockData.kt`, or use the YouTube Data API
(see YouTubeService.kt) once you have a key.

Requires: Python 3.8+ and `curl` on PATH.
"""

import json
import re
import subprocess
import sys

UA = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"
)

CHANNELS = [
    ("CNN10", "CNN10", "CNN 10 (student news)"),
    ("CBS", "CBSNews", "CBS News"),
    ("PBS", "PBSNewsHour", "PBS NewsHour"),
    ("ABC", "ABCNews", "ABC News"),
]


def fetch(handle: str) -> str:
    url = f"https://www.youtube.com/@{handle}/videos"
    result = subprocess.run(
        ["curl", "-s", "--max-time", "30", "-A", UA, url],
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="ignore",
    )
    return result.stdout


def parse(html: str):
    """Return (channel_id, [(video_id, title, relative_time), ...])."""
    ch = re.search(r'"externalId":"(UC[A-Za-z0-9_-]{22})"', html)
    channel_id = ch.group(1) if ch else "?"

    m = re.search(r"var ytInitialData = (\{.*?\});</script>", html, re.S)
    if not m:
        return channel_id, []

    data = json.loads(m.group(1))
    videos = []

    def walk(node):
        if isinstance(node, dict):
            if "lockupViewModel" in node:
                lv = node["lockupViewModel"]
                vid = lv.get("contentId")
                meta = lv.get("metadata", {}).get("lockupMetadataViewModel", {})
                title = meta.get("title", {}).get("content")

                # Relative upload time sits in a metadataPart, e.g. "2d ago"
                rel = None
                rows = (
                    meta.get("metadata", {})
                    .get("contentMetadataViewModel", {})
                    .get("metadataRows", [])
                )
                for row in rows:
                    for part in row.get("metadataParts", []):
                        text = part.get("text", {}).get("content", "")
                        if re.search(r"\b(ago|hour|day|week|month|year)s?\b", text):
                            rel = text

                if vid and len(vid) == 11:
                    videos.append((vid, title or "(no title)", rel))
            for value in node.values():
                walk(value)
        elif isinstance(node, list):
            for value in node:
                walk(value)

    walk(data)
    return channel_id, videos


def main():
    for source, handle, label in CHANNELS:
        print(f"### {source} — {label} (@{handle})")
        html = fetch(handle)
        if not html:
            print("  (fetch failed)\n")
            continue
        channel_id, videos = parse(html)
        print(f"  channelId: {channel_id}")
        for vid, title, rel in videos[:6]:
            print(f'  "{vid}",  // [{rel or "?"}] {title[:70]}')
        print()


if __name__ == "__main__":
    sys.exit(main())
