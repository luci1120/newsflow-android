#!/usr/bin/env python3
"""
Post-process lesson JSON produced by transcribe.py.

Whisper output needs a light cleanup pass:
  - capitalise the first letter of every sentence
  - fix known proper nouns the model habitually gets wrong
  - normalise spacing around punctuation

Usage
-----
    python fixup.py <dir_or_file> [...]
"""

import json
import os
import re
import sys

# Terms Whisper reliably mishears, mapped to the correct spelling.
# Matching is case-insensitive and only replaces whole words / phrases.
CORRECTIONS = {
    "koi wire": "Coy Wire",
    "coy wire": "Coy Wire",
    "cnn 10 less lock in": "CNN 10, let's lock in",
    "let's lock in": "let's lock in",
    "less lock in": "let's lock in",
    "rise up": "rise up",
    "openai": "OpenAI",
    "anthropic": "Anthropic",
    "meta": "Meta",
    "google": "Google",
    "microsoft": "Microsoft",
    "nvidia": "Nvidia",
    "chatgpt": "ChatGPT",
    "the white house": "the White House",
    "federal reserve": "Federal Reserve",
    "south africa": "South Africa",
    "cape town": "Cape Town",
    "tyrannosaurus rex": "Tyrannosaurus rex",
    "lewis and clark": "Lewis and Clark",
    "meriwether lewis": "Meriwether Lewis",
    "william clark": "William Clark",
    "maryweather lewis": "Meriwether Lewis",
}


def fix_sentence(text: str) -> str:
    """Apply corrections and capitalise sentence starts."""
    for wrong, right in CORRECTIONS.items():
        text = re.sub(rf"\b{re.escape(wrong)}\b", right, text, flags=re.IGNORECASE)

    # Capitalise the first letter of each sentence
    text = re.sub(
        r"(^|[.!?]\s+)([a-z])",
        lambda m: m.group(1) + m.group(2).upper(),
        text,
    )
    # tidy spacing
    text = re.sub(r"\s+([,.!?;:])", r"\1", text)
    text = re.sub(r"\s{2,}", " ", text)
    return text.strip()


def process(path: str):
    with open(path, encoding="utf-8") as f:
        data = json.load(f)

    changed = 0
    for seg in data.get("segments", []):
        before = seg["transcript"]
        after = fix_sentence(before)
        if after != before:
            changed += 1
        seg["transcript"] = after
        seg["solution"] = [[tok] for tok in after.split()]

    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
    print(f"  {os.path.basename(path)}: {len(data.get('segments', []))} segments, {changed} fixed")


def main():
    targets = []
    for arg in sys.argv[1:]:
        if os.path.isdir(arg):
            targets += [
                os.path.join(arg, f) for f in sorted(os.listdir(arg))
                if f.endswith(".json")
            ]
        else:
            targets.append(arg)
    for t in targets:
        process(t)


if __name__ == "__main__":
    main()
