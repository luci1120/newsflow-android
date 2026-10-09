#!/usr/bin/env python3
"""
NewsFlow 应用图标生成器
=======================
生成两套产物：
  1. Android 自适应图标（矢量 XML，foreground + background）—— App 内使用
  2. PNG 位图 —— Google Play 商店列表用（512x512 图标 + 1024x500 宣传图）

设计：蓝色渐变底 + 白色播放三角 + 声波弧线
      （视频 + 听力，对应核心学习循环）

用法：
  python tools/make_icon.py --out app/src/main/res
  python tools/make_icon.py --out app/src/main/res --store store_assets
"""

import argparse
import math
import os

from PIL import Image, ImageDraw

# === 设计参数（坐标系：108x108，Android 自适应图标标准）===
CANVAS = 108.0

# 背景渐变
BG_TOP = (43, 123, 243)      # #2B7BF3 亮蓝
BG_BOTTOM = (10, 77, 191)    # #0A4DBF 深蓝

# 前景（白色图形）
FG_COLOR = (255, 255, 255)

# 播放三角（三点 + 圆角半径）
TRI_A = (31.0, 31.0)   # 左上
TRI_B = (31.0, 77.0)   # 左下
TRI_C = (62.0, 54.0)   # 右尖
TRI_RADIUS = 5.0

# 声波弧线
ARC_CENTER = (62.0, 54.0)
ARC_RADII = (10.0, 20.5)
ARC_SPAN_DEG = 80.0    # 左右各 80 度
ARC_WIDTH = 4.5


def rounded_polygon_geometry(pts, radius):
    """
    计算圆角多边形的几何：
      返回 (切点列表(按序), [(圆心, 实际半径), ...])
    切点 = 每条边上圆弧起止点；圆心在多边形内部（不会外凸）。
    """
    n = len(pts)
    tangents = []
    arcs = []

    def unit(a, b):
        dx, dy = b[0] - a[0], b[1] - a[1]
        L = math.hypot(dx, dy) or 1e-9
        return (dx / L, dy / L), L

    for i in range(n):
        V = pts[i]
        P = pts[(i - 1) % n]   # 前一个顶点
        Q = pts[(i + 1) % n]   # 后一个顶点
        u, Lp = unit(V, P)     # 指向前一顶点
        w, Lq = unit(V, Q)     # 指向后一顶点

        cos_t = max(-1.0, min(1.0, u[0] * w[0] + u[1] * w[1]))
        theta = math.acos(cos_t)
        half = theta / 2.0

        # 切点距顶点的距离；限制不超过半边长度
        t = radius / math.tan(half)
        t = min(t, Lp * 0.5, Lq * 0.5)
        r_eff = t * math.tan(half)

        T_prev = (V[0] + u[0] * t, V[1] + u[1] * t)
        T_next = (V[0] + w[0] * t, V[1] + w[1] * t)

        # 圆心沿角平分线向内
        bx, by = u[0] + w[0], u[1] + w[1]
        bL = math.hypot(bx, by) or 1e-9
        bx, by = bx / bL, by / bL
        dist = r_eff / math.sin(half)
        C = (V[0] + bx * dist, V[1] + by * dist)

        tangents.append(T_prev)
        tangents.append(T_next)
        arcs.append((C, r_eff))

    return tangents, arcs


def _draw_rounded_polygon(draw, pts, radius, fill, scale):
    tangents, arcs = rounded_polygon_geometry(pts, radius)
    draw.polygon([(x * scale, y * scale) for x, y in tangents], fill=fill)
    for (cx, cy), r in arcs:
        draw.ellipse(
            [(cx - r) * scale, (cy - r) * scale,
             (cx + r) * scale, (cy + r) * scale],
            fill=fill,
        )


def _arc_with_caps(draw, center, radius, span_deg, width, fill, scale):
    """画一段带圆头端点的弧。"""
    cx, cy = center
    r = radius * scale
    w = width * scale
    box = [cx * scale - r, cy * scale - r, cx * scale + r, cy * scale + r]
    draw.arc(box, start=-span_deg, end=span_deg, fill=fill, width=int(round(w)))

    # 圆头端点
    cap_r = w / 2.0
    for ang in (-span_deg, span_deg):
        rad = math.radians(ang)
        px = cx * scale + r * math.cos(rad)
        py = cy * scale + r * math.sin(rad)
        draw.ellipse([px - cap_r, py - cap_r, px + cap_r, py + cap_r], fill=fill)


def render_foreground(px, ss=16):
    """白色前景图形，透明底。坐标以 108 为画布基准。"""
    size = int(px * ss)
    scale = size / CANVAS
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    _draw_rounded_polygon(d, [TRI_A, TRI_B, TRI_C], TRI_RADIUS, FG_COLOR, scale)
    for r in ARC_RADII:
        _arc_with_caps(d, ARC_CENTER, r, ARC_SPAN_DEG, ARC_WIDTH, FG_COLOR, scale)

    return img.resize((px, px), Image.LANCZOS)


def render_background(px, ss=8):
    """垂直渐变底。"""
    size = int(px * ss)
    img = Image.new("RGB", (size, size), BG_TOP)
    d = ImageDraw.Draw(img)
    for y in range(size):
        t = y / max(size - 1, 1)
        c = tuple(int(round(BG_TOP[i] + (BG_BOTTOM[i] - BG_TOP[i]) * t)) for i in range(3))
        d.line([(0, y), (size, y)], fill=c)
    return img.resize((px, px), Image.LANCZOS)


def compose(px, ss=16, radius_frac=None):
    """背景 + 前景合成；radius_frac 非空时做圆角裁切。"""
    bg = render_background(px, ss=8).convert("RGBA")
    fg = render_foreground(px, ss=ss)
    out = Image.alpha_composite(bg, fg)

    if radius_frac:
        mask = Image.new("L", (px * 4, px * 4), 0)
        md = ImageDraw.Draw(mask)
        md.rounded_rectangle(
            [0, 0, px * 4 - 1, px * 4 - 1],
            radius=int(px * 4 * radius_frac),
            fill=255,
        )
        mask = mask.resize((px, px), Image.LANCZOS)
        out.putalpha(mask)

    return out


def render_monochrome(px, ss=16):
    """Android 13+ 主题图标用的单色层（白色图形 + 透明底）。"""
    return render_foreground(px, ss=ss)


# === 矢量 XML 生成 ===

def svg_path_triangle():
    """圆角三角形的 SVG path（复用 rounded_polygon_geometry，与 PNG 一致）。"""
    pts = [TRI_A, TRI_B, TRI_C]
    tangents, arcs = rounded_polygon_geometry(pts, TRI_RADIUS)

    # tangents 顺序: [T_prev0, T_next0, T_prev1, T_next1, T_prev2, T_next2]
    # 多边形顶点 = 全部切点；每个顶点处的圆弧用 A 指令连接
    segs = [f"M {tangents[0][0]:.3f} {tangents[0][1]:.3f}"]
    for i in range(3):
        t_next = tangents[i * 2 + 1]          # 本顶点朝下一顶点的切点
        t_prev_next = tangents[((i + 1) % 3) * 2]  # 下一顶点朝本顶点的切点
        _, r = arcs[i]
        # 直线到下一顶点的入切点
        segs.append(f"L {t_prev_next[0]:.3f} {t_prev_next[1]:.3f}")
        # 圆弧
        _, r_next = arcs[(i + 1) % 3]
        t_out = tangents[((i + 1) % 3) * 2 + 1]
        segs.append(
            f"A {r_next:.3f} {r_next:.3f} 0 0 1 {t_out[0]:.3f} {t_out[1]:.3f}"
        )
    segs.append("Z")
    return " ".join(segs)


def arc_svg_path(center, radius, span_deg):
    """弧线的 SVG path（含圆头端点的近似）。"""
    cx, cy = center
    a0 = math.radians(-span_deg)
    a1 = math.radians(span_deg)
    x0, y0 = cx + radius * math.cos(a0), cy + radius * math.sin(a0)
    x1, y1 = cx + radius * math.cos(a1), cy + radius * math.sin(a1)
    large = 0
    sweep = 1
    return (
        f"M {x0:.3f} {y0:.3f} "
        f"A {radius:.3f} {radius:.3f} 0 {large} {sweep} {x1:.3f} {y1:.3f}"
    )


def write_foreground_xml(path):
    tri = svg_path_triangle()
    arcs = []
    for r in ARC_RADII:
        arcs.append(
            f'    <path\n'
            f'        android:pathData="{arc_svg_path(ARC_CENTER, r, ARC_SPAN_DEG)}"\n'
            f'        android:strokeColor="#FFFFFF"\n'
            f'        android:strokeWidth="{ARC_WIDTH}"\n'
            f'        android:strokeLineCap="round" />'
        )
    arcs = "\n".join(arcs)

    xml = f'''<?xml version="1.0" encoding="utf-8"?>
<!-- NewsFlow 图标前景层：播放三角 + 声波弧线 -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:pathData="{tri}"
        android:fillColor="#FFFFFF" />
{arcs}
</vector>
'''
    with open(path, "w", encoding="utf-8") as f:
        f.write(xml)


def write_background_xml(path):
    xml = f'''<?xml version="1.0" encoding="utf-8"?>
<!-- NewsFlow 图标背景层：垂直蓝色渐变 -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr xmlns:aapt="http://schemas.android.com/aapt" name="android:fillColor">
            <gradient
                android:type="linear"
                android:startX="0" android:startY="0"
                android:endX="0" android:endY="108">
                <item android:offset="0" android:color="#FF2B7BF3" />
                <item android:offset="1" android:color="#FF0A4DBF" />
            </gradient>
        </aapt:attr>
    </path>
</vector>
'''
    with open(path, "w", encoding="utf-8") as f:
        f.write(xml)


def write_anydpi_xml(path, round_icon=False):
    tag = "ic_launcher_round" if round_icon else "ic_launcher"
    xml = f'''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
'''
    with open(path, "w", encoding="utf-8") as f:
        f.write(xml)


def _load_font(size, bold=True):
    """找一个可用的系统字体。"""
    candidates = [
        r"C:\Windows\Fonts\segoeuib.ttf" if bold else r"C:\Windows\Fonts\segoeui.ttf",
        r"C:\Windows\Fonts\arialbd.ttf" if bold else r"C:\Windows\Fonts\arial.ttf",
        r"C:\Windows\Fonts\calibrib.ttf" if bold else r"C:\Windows\Fonts\calibri.ttf",
    ]
    for c in candidates:
        if os.path.exists(c):
            try:
                from PIL import ImageFont
                return ImageFont.truetype(c, size)
            except Exception:
                continue
    from PIL import ImageFont
    return ImageFont.load_default()


def _fit_font(draw, text, max_w, start_size, bold=True, min_size=10):
    """自动缩小字号直到文本宽度 <= max_w。"""
    size = start_size
    while size > min_size:
        f = _load_font(size, bold=bold)
        bbox = draw.textbbox((0, 0), text, font=f)
        if bbox[2] - bbox[0] <= max_w:
            return f
        size -= 2
    return _load_font(min_size, bold=bold)


def write_feature_graphic(path, w=1024, h=500):
    """Play 商店宣传图：渐变底 + 白色图形 + 应用名（自动适配宽度）。"""
    SS = 2
    W, H = w * SS, h * SS
    img = Image.new("RGB", (W, H), BG_TOP)
    d = ImageDraw.Draw(img)
    for y in range(H):
        t = y / (H - 1)
        c = tuple(int(round(BG_TOP[i] + (BG_BOTTOM[i] - BG_TOP[i]) * t)) for i in range(3))
        d.line([(0, y), (W, y)], fill=c)

    # 左侧：纯白图形（透明底，与渐变无缝融合）
    raw = render_foreground(900, ss=6)
    bbox = raw.getbbox()
    glyph = raw.crop(bbox) if bbox else raw
    target_h = int(H * 0.46)
    scale = target_h / glyph.height
    glyph = glyph.resize((max(1, int(glyph.width * scale)), target_h), Image.LANCZOS)

    gx = int(W * 0.085)
    gy = (H - target_h) // 2
    img.paste(glyph, (gx, gy), glyph)

    # 右侧文字
    tx = gx + glyph.width + int(W * 0.055)
    max_w = W - tx - int(W * 0.06)

    title = "Newsflow Dictation"
    sub = "Learn English with real news videos"

    title_font = _fit_font(d, title, max_w, int(H * 0.16), bold=True)
    sub_font = _fit_font(d, sub, max_w, int(H * 0.072), bold=False)

    d.text((tx, H * 0.375), title, font=title_font, fill=(255, 255, 255), anchor="lm")
    d.text((tx, H * 0.545), sub, font=sub_font, fill=(208, 226, 255), anchor="lm")
    d.text((tx, H * 0.655), "Listen  ·  Type  ·  Master",
           font=sub_font, fill=(208, 226, 255), anchor="lm")

    img = img.resize((w, h), Image.LANCZOS)
    img.save(path, "PNG")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default="app/src/main/res",
                    help="Android res 目录（写入 drawable/ 和 mipmap-anydpi-v26/）")
    ap.add_argument("--store", default=None,
                    help="Play 商店素材输出目录（512 图标 + 1024x500 宣传图）")
    args = ap.parse_args()

    res = args.out
    drawable = os.path.join(res, "drawable")
    anydpi = os.path.join(res, "mipmap-anydpi-v26")
    os.makedirs(drawable, exist_ok=True)
    os.makedirs(anydpi, exist_ok=True)

    # 1) 矢量层
    write_foreground_xml(os.path.join(drawable, "ic_launcher_foreground.xml"))
    write_background_xml(os.path.join(drawable, "ic_launcher_background.xml"))
    write_anydpi_xml(os.path.join(anydpi, "ic_launcher.xml"), round_icon=False)
    write_anydpi_xml(os.path.join(anydpi, "ic_launcher_round.xml"), round_icon=True)
    print(f"[矢量] 已写入 {drawable} 和 {anydpi}")

    # 2) 高密度 PNG（部分设备/启动器会用到）
    for name, px in [("mipmap-mdpi", 48), ("mipmap-hdpi", 72),
                     ("mipmap-xhdpi", 96), ("mipmap-xxhdpi", 144),
                     ("mipmap-xxxhdpi", 192)]:
        d2 = os.path.join(res, name)
        os.makedirs(d2, exist_ok=True)
        compose(px, ss=16, radius_frac=0.22).save(
            os.path.join(d2, "ic_launcher.png"), "PNG")
        compose(px, ss=16, radius_frac=0.5).save(
            os.path.join(d2, "ic_launcher_round.png"), "PNG")
    print("[PNG] 已写入各密度 mipmap")

    # 3) 商店素材
    if args.store:
        os.makedirs(args.store, exist_ok=True)
        compose(512, ss=16).save(os.path.join(args.store, "play_icon_512.png"), "PNG")
        write_feature_graphic(os.path.join(args.store, "feature_graphic_1024x500.png"))
        print(f"[商店] 已写入 {args.store}")

    # 4) 预览图（方便肉眼检查）
    compose(432, ss=16).save("icon_preview_432.png", "PNG")
    print("[预览] icon_preview_432.png")


if __name__ == "__main__":
    main()
