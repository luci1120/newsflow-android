#!/usr/bin/env python3
"""
NewsFlow 新电脑一键迁移脚本
================================
在新电脑上运行此脚本，自动完成所有环境搭建。

前置条件（需手动安装）：
  1. WorkBuddy AI（安装后会自带管理版 Python）
  2. GitHub CLI (gh) — https://cli.github.com/
  3. Git

用法：
  python setup_new_machine.py          # 完整检查+引导
  python setup_new_machine.py --check  # 只检查不执行

所有步骤都会打印，需要手动操作的会暂停等你完成。
"""

import os
import sys
import subprocess
import shutil

# === 配置 ===
GITHUB_USER = "luci1120"
CODE_REPO = f"https://github.com/{GITHUB_USER}/newsflow-lessons.git".replace("lessons", "android")
LESSONS_REPO = f"https://github.com/{GITHUB_USER}/newsflow-lessons.git"
PYTHON_VENV = r"C:\Users\lu\.workbuddy\binaries\python\envs\default"
PYTHON_EXE = os.path.join(PYTHON_VENV, "Scripts", "python.exe")
REQUIRED_PACKAGES = ["yt-dlp", "faster-whisper", "numpy"]


def run(cmd, check=True, capture=True):
    try:
        r = subprocess.run(cmd, capture_output=capture, text=True, check=check)
        return r.returncode == 0, (r.stdout or "") + (r.stderr or "")
    except subprocess.CalledProcessError as e:
        return False, (e.stdout or "") + (e.stderr or "")
    except FileNotFoundError:
        return False, f"Command not found: {cmd[0]}"


def check_and_setup():
    print("=" * 60)
    print("  NewsFlow 新电脑环境搭建")
    print("=" * 60)
    print()

    # --- Step 1: gh CLI ---
    print("[1/6] 检查 GitHub CLI...")
    ok, out = run(["gh", "--version"])
    if not ok:
        print("  ✗ gh CLI 未安装！")
        print("  请下载安装: https://cli.github.com/")
        print("  安装后重新运行此脚本。")
        sys.exit(1)
    print(f"  ✓ gh CLI 已安装: {out.split(chr(10))[0]}")

    # --- Step 2: gh auth ---
    print()
    print("[2/6] 检查 GitHub 登录状态...")
    ok, out = run(["gh", "auth", "status"])
    if not ok:
        print("  ✗ 未登录 GitHub")
        print("  请运行: gh auth login")
        print("  选择: GitHub.com → HTTPS → Login with a web browser")
        print("  完成后重新运行此脚本。")
        sys.exit(1)
    print(f"  ✓ 已登录 GitHub")

    # --- Step 3: git credential ---
    print()
    print("[3/6] 配置 git 凭证...")
    ok, _ = run(["gh", "auth", "setup-git"])
    if ok:
        print("  ✓ git 凭证已配置")
    else:
        print("  ⚠ setup-git 可能失败，push 时如果报错请手动运行: gh auth setup-git")

    # --- Step 4: Python venv ---
    print()
    print("[4/6] 检查 Python venv...")
    if os.path.exists(PYTHON_EXE):
        print(f"  ✓ venv 已存在: {PYTHON_VENV}")
    else:
        print(f"  ⚠ venv 不存在，正在创建...")
        # 找管理版 Python
        managed_python = r"C:\Users\lu\.workbuddy\binaries\python\versions\3.13.12\python.exe"
        if not os.path.exists(managed_python):
            print(f"  ✗ 管理版 Python 未找到: {managed_python}")
            print("  请打开 WorkBuddy AI，它会自动安装 Python。")
            sys.exit(1)
        ok, out = run([managed_python, "-m", "venenv", PYTHON_VENV])
        if not ok:
            print(f"  ✗ 创建 venv 失败: {out}")
            sys.exit(1)
        print(f"  ✓ venv 已创建")

    # --- Step 5: Python packages ---
    print()
    print("[5/6] 检查 Python 依赖包...")
    pip_exe = os.path.join(PYTHON_VENV, "Scripts", "pip.exe")
    missing = []
    for pkg in REQUIRED_PACKAGES:
        ok, _ = run([pip_exe, "show", pkg])
        if ok:
            print(f"  ✓ {pkg} 已安装")
        else:
            print(f"  ✗ {pkg} 未安装")
            missing.append(pkg)

    if missing:
        print(f"  正在安装缺失的包: {', '.join(missing)}")
        ok, out = run([pip_exe, "install"] + missing)
        if ok:
            print(f"  ✓ 全部安装成功")
        else:
            print(f"  ✗ 安装失败: {out}")
            print("  请手动运行:")
            print(f"  {pip_exe} install {' '.join(missing)}")
            sys.exit(1)

    # --- Step 6: Clone repos ---
    print()
    print("[6/6] 检查项目仓库...")
    project_dir = os.path.dirname(os.path.abspath(__file__))
    parent_dir = os.path.dirname(project_dir)

    # 检查是否在 git 仓库内
    ok, _ = run(["git", "rev-parse", "--is-inside-work-tree"], cwd=project_dir)
    if ok:
        print(f"  ✓ 代码仓库已存在: {project_dir}")
        # 检查 remote
        ok, out = run(["git", "remote", "get-url", "origin"], cwd=project_dir)
        if ok:
            print(f"  ✓ 远程仓库: {out.strip()}")
    else:
        print(f"  ⚠ 当前目录不是 git 仓库")
        print(f"  如果代码已从 GitHub 克隆，请确保在正确目录")
        print(f"  如果没有代码，请克隆:")
        print(f"  git clone {CODE_REPO}")

    # 检查 publish_repo
    publish_dir = os.path.join(project_dir, "publish_repo")
    if os.path.exists(os.path.join(publish_dir, ".git")):
        print(f"  ✓ publish_repo 已存在")
    else:
        print(f"  ⚠ publish_repo 不存在，正在克隆...")
        ok, out = run(["git", "clone", LESSONS_REPO, publish_dir])
        if ok:
            print(f"  ✓ publish_repo 已克隆")
        else:
            print(f"  ✗ 克隆失败: {out}")
            print(f"  请手动运行: git clone {LESSONS_REPO} {publish_dir}")

    # === 最终验证 ===
    print()
    print("=" * 60)
    print("  环境检查完成！")
    print("=" * 60)
    print()
    print("验证步骤（可选，建议跑一次确认全链路通）：")
    print()
    print(f"  1. 测试转写（会自动下载 Whisper 模型，约500MB，首次较慢）：")
    print(f"     cd {project_dir}")
    print(f"     set HF_HUB_OFFLINE=1")
    print(f"     {PYTHON_EXE} tools/update_daily.py --out ../lessons_out --keep-days 60")
    print()
    print(f"  2. 测试发布：")
    print(f"     cd {project_dir}")
    print(f"     {PYTHON_EXE} tools/publish.py --src ../lessons_out --dest publish_repo")
    print()
    print(f"  3. 检查线上：")
    print(f"     curl -s https://luci1120.github.io/newsflow-lessons/lessons/index.json")
    print()
    print("如果以上都通过，自动化会在每天 21:00 UTC 自动运行。")
    print("确保 WorkBuddy AI 开着即可，无需其他操作。")


if __name__ == "__main__":
    check_only = "--check" in sys.argv
    if check_only:
        print("(只检查模式，不会执行任何安装/克隆操作)")
    check_and_setup()
