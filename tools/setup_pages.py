#!/usr/bin/env python3
"""
One-time setup: publish the lesson site to GitHub Pages.

Run this once. After that, the daily automation keeps it updated.

    python setup_pages.py
    python setup_pages.py --repo newsflow-lessons --user luci1120

Requires the GitHub CLI, authenticated:
    gh auth login
"""

import argparse
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
PUBLISH = os.path.abspath(os.path.join(HERE, "..", "publish"))


def run(cmd, cwd=None, check=True, capture=True):
    return subprocess.run(
        cmd, cwd=cwd, check=check,
        capture_output=capture, text=True,
    )


def gh_logged_in() -> bool:
    try:
        run(["gh", "auth", "status"])
        return True
    except (subprocess.CalledProcessError, FileNotFoundError):
        return False


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--repo", default="newsflow-lessons")
    ap.add_argument("--user", default="luci1120")
    ap.add_argument("--branch", default="main")
    args = ap.parse_args()

    if not os.path.isdir(PUBLISH):
        print(f"publish directory not found: {PUBLISH}")
        print("Run tools/publish.py first to generate the site.")
        return 1

    print("=== 1. GitHub CLI 登录检查 ===")
    if not gh_logged_in():
        print("  未登录。请先运行（交互式，需要你自己操作）：")
        print("      gh auth login")
        print("  选 GitHub.com → HTTPS → 用浏览器授权")
        print("\n  授权后重新运行本脚本即可。")
        return 1
    print("  已登录 ✓")

    print("\n=== 2. 初始化本地仓库 ===")
    if not os.path.isdir(os.path.join(PUBLISH, ".git")):
        run(["git", "init", "-b", args.branch], cwd=PUBLISH)
        print(f"  git init -b {args.branch}")
    else:
        print("  已是 git 仓库，跳过")

    run(["git", "add", "-A"], cwd=PUBLISH)
    status = run(["git", "status", "--porcelain"], cwd=PUBLISH).stdout.strip()
    if status:
        run(["git", "commit", "-m", "Initial lesson publish"], cwd=PUBLISH)
        print("  已提交")
    else:
        print("  无变更")

    print("\n=== 3. 创建 GitHub 仓库并推送 ===")
    try:
        run(["gh", "repo", "create", args.repo, "--public",
             "--source", ".", "--remote", "origin", "--push"],
            cwd=PUBLISH)
        print(f"  已创建并推送 github.com/{args.user}/{args.repo}")
    except subprocess.CalledProcessError as e:
        out = ((e.stdout or "") + (e.stderr or "")).strip()
        if "already exists" in out:
            print("  仓库已存在，直接推送")
            run(["git", "remote", "remove", "origin"], cwd=PUBLISH, check=False)
            run(["git", "remote", "add", "origin",
                 f"https://github.com/{args.user}/{args.repo}.git"], cwd=PUBLISH)
            run(["git", "push", "-u", "origin", args.branch], cwd=PUBLISH)
        else:
            print("  失败：")
            print("  " + out[:500])
            return 1

    print("\n=== 4. 开启 GitHub Pages ===")
    try:
        run(["gh", "api", "-X", "POST",
             f"repos/{args.user}/{args.repo}/pages",
             "-f", f"source[branch]={args.branch}",
             "-f", "source[path]=/"])
        print("  Pages 已开启（可能需等 1-2 分钟生效）")
    except subprocess.CalledProcessError as e:
        out = ((e.stdout or "") + (e.stderr or "")).strip()
        if "already enabled" in out or "409" in out:
            print("  Pages 已开启")
        else:
            print("  自动开启失败，请手动去仓库 Settings → Pages 选 main 分支")
            print("  " + out[:300])

    url = f"https://{args.user}.github.io/{args.repo}/lessons"
    print("\n" + "=" * 60)
    print("完成。App 里的地址应该是：")
    print(f"  {url}")
    print("=" * 60)
    print("\n验证：等 1-2 分钟后访问")
    print(f"  {url}/index.json")
    return 0


if __name__ == "__main__":
    sys.exit(main())
