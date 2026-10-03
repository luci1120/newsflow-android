#!/usr/bin/env python3
"""
Publish lessons to a static host (GitHub Pages by default).

Steps
-----
1. Copy lesson JSONs into the publish directory
2. Regenerate index.json (grouped by source, newest first)
3. Write a small landing page so the URL is browsable
4. git add / commit / push, if a remote is configured

Usage
-----
    python publish.py --src out --dest publish
    python publish.py --src out --dest publish --no-push
    python publish.py --src out --dest publish --remote origin --branch main

First-time setup (once):
    gh auth login
    gh repo create newsflow-lessons --public --source publish --remote origin --push
"""

import argparse
import json
import os
import shutil
import subprocess
import sys
from datetime import datetime, timezone

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from update_daily import SOURCE_ORDER, rebuild_index  # noqa: E402


def run(cmd, cwd=None, check=True):
    return subprocess.run(cmd, cwd=cwd, check=check,
                          capture_output=True, text=True)


def is_git_repo(path: str) -> bool:
    try:
        run(["git", "rev-parse", "--is-inside-work-tree"], cwd=path)
        return True
    except (subprocess.CalledProcessError, FileNotFoundError):
        return False


def write_landing(dest: str, index: dict):
    rows = []
    for group in index.get("groups", []):
        src = group["source"]
        rows.append(f'<h2>{src} <span class="n">{group["count"]}</span></h2>')
        rows.append('<ul>')
        for f in group["lessons"]:
            e = next((x for x in index["entries"] if x["file"] == f), None)
            if not e:
                continue
            mins = e["durationSecs"] // 60
            secs = e["durationSecs"] % 60
            rows.append(
                f'  <li><a href="lessons/{f}">{e["title"]}</a>'
                f'<span class="meta">{e["publishedAt"]} · '
                f'{mins}:{secs:02d} · {e["segments"]} segments</span></li>'
            )
        rows.append('</ul>')

    html = f"""<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>NewsFlow lessons</title>
<style>
  body {{ font: 15px/1.6 -apple-system, system-ui, sans-serif;
         max-width: 720px; margin: 40px auto; padding: 0 20px; color: #222; }}
  h1 {{ font-size: 22px; font-weight: 600; }}
  h2 {{ font-size: 15px; font-weight: 600; margin: 28px 0 8px;
        color: #555; text-transform: uppercase; letter-spacing: .04em; }}
  .n {{ color: #999; font-weight: 400; }}
  ul {{ list-style: none; padding: 0; margin: 0; }}
  li {{ padding: 10px 0; border-bottom: 1px solid #eee; }}
  a {{ color: #185FA5; text-decoration: none; }}
  a:hover {{ text-decoration: underline; }}
  .meta {{ display: block; color: #888; font-size: 13px; margin-top: 2px; }}
  .top {{ color: #888; font-size: 13px; }}
</style>
</head>
<body>
<h1>NewsFlow lessons</h1>
<p class="top">Updated {index.get('updatedAt', '')} · {index.get('count', 0)} lessons.
Endpoint for the app: <code>lessons/index.json</code></p>
{chr(10).join(rows)}
</body>
</html>
"""
    with open(os.path.join(dest, "index.html"), "w", encoding="utf-8") as f:
        f.write(html)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--src", default="out", help="directory holding lesson JSON")
    ap.add_argument("--dest", default="publish", help="static site root")
    ap.add_argument("--remote", default="origin")
    ap.add_argument("--branch", default="main")
    ap.add_argument("--no-push", action="store_true")
    args = ap.parse_args()

    src = os.path.abspath(args.src)
    dest = os.path.abspath(args.dest)
    lessons_dest = os.path.join(dest, "lessons")
    os.makedirs(lessons_dest, exist_ok=True)

    # 1) copy lesson files (skip index.json, regenerate it below)
    copied = 0
    for name in os.listdir(src):
        if not name.endswith(".json") or name == "index.json":
            continue
        shutil.copyfile(os.path.join(src, name), os.path.join(lessons_dest, name))
        copied += 1
    print(f"copied {copied} lesson file(s) -> {lessons_dest}")

    # 2) rebuild index inside the publish dir
    rebuild_index(lessons_dest)

    # 3) landing page
    with open(os.path.join(lessons_dest, "index.json"), encoding="utf-8") as f:
        index = json.load(f)
    write_landing(dest, index)
    print("wrote index.html")

    # 4) git
    if args.no_push:
        print("skipping git (--no-push)")
        return

    if not is_git_repo(dest):
        print("\nNot a git repo yet. Run these once:")
        print(f"  cd {dest}")
        print("  git init -b main")
        print("  gh repo create newsflow-lessons --public "
              "--source . --remote origin --push")
        return

    try:
        run(["git", "add", "-A"], cwd=dest)
        stamp = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M UTC")
        run(["git", "commit", "-m", f"Update lessons {stamp}"], cwd=dest)
        run(["git", "push", args.remote, args.branch], cwd=dest)
        print(f"pushed to {args.remote}/{args.branch}")
    except subprocess.CalledProcessError as e:
        out = (e.stdout or "") + (e.stderr or "")
        if "nothing to commit" in out:
            print("nothing to commit — content unchanged")
        else:
            print("git step failed:")
            print(out.strip()[:600])


if __name__ == "__main__":
    main()
