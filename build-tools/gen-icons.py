#!/usr/bin/env python3
# 从 DeepSeek娘 精灵图生成：启动图标（各密度 + 自适应图标前景）+ 通知小图标
import os
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "app/src/main/assets/spritesheet.webp")
RES = os.path.join(ROOT, "app/src/main/res")

sheet = Image.open(SRC).convert("RGBA")
# 待机第 0 帧：cell (0,0) 192x208
cell = sheet.crop((0, 0, 192, 208))
bbox = cell.getbbox()  # 裁掉透明边
char = cell.crop(bbox)

def gradient(w, h, c1, c2):
    """垂直渐变背景"""
    img = Image.new("RGB", (w, h))
    d = ImageDraw.Draw(img)
    for y in range(h):
        t = y / max(1, h - 1)
        r = int(c1[0] + (c2[0] - c1[0]) * t)
        g = int(c1[1] + (c2[1] - c1[1]) * t)
        b = int(c1[2] + (c2[2] - c1[2]) * t)
        d.line([(0, y), (w, y)], fill=(r, g, b))
    return img

def rounded(img, radius):
    mask = Image.new("L", img.size, 0)
    d = ImageDraw.Draw(mask)
    d.rounded_rectangle([0, 0, img.size[0] - 1, img.size[1] - 1], radius=radius, fill=255)
    out = Image.new("RGBA", img.size, (0, 0, 0, 0))
    out.paste(img, (0, 0), mask)
    return out

def legacy_icon(size):
    """蓝渐变圆角底 + 角色居中"""
    bg = gradient(size, size, (0x6A, 0x93, 0xF5), (0x9B, 0x6B, 0xF2)).convert("RGBA")
    bg = rounded(bg, int(size * 0.22))
    target = int(size * 0.72)
    ch = char.copy()
    ch.thumbnail((target, target), Image.LANCZOS)
    x = (size - ch.width) // 2
    y = (size - ch.height) // 2 + int(size * 0.02)
    bg.paste(ch, (x, y), ch)
    return bg

# 传统启动图标（API<26 及兜底）
sizes = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
for dpi, size in sizes.items():
    d = os.path.join(RES, f"mipmap-{dpi}")
    os.makedirs(d, exist_ok=True)
    legacy_icon(size).save(os.path.join(d, "ic_launcher.png"))

# 自适应图标前景（432x432，角色占 ~60%，居中偏下）
FG = 432
fg = Image.new("RGBA", (FG, FG), (0, 0, 0, 0))
target = int(FG * 0.62)
ch = char.copy()
ch.thumbnail((target, target), Image.LANCZOS)
fx = (FG - ch.width) // 2
fy = (FG - ch.height) // 2 + int(FG * 0.05)
fg.paste(ch, (fx, fy), ch)
fg.save(os.path.join(RES, "mipmap-anydpi-v26", "ic_launcher_foreground.png"))
# 兜底：普通密度目录也放一份前景引用（某些 launcher 需要）
os.makedirs(os.path.join(RES, "mipmap-xxxhdpi"), exist_ok=True)
fg.save(os.path.join(RES, "mipmap-xxxhdpi", "ic_launcher_foreground.png"))

# 通知小图标：白色爱心（透明底）
def heart_points(cx, cy, s, n=64):
    import math
    pts = []
    for i in range(n):
        t = 2 * math.pi * i / n
        x = 16 * (math.sin(t) ** 3)
        y = 13 * math.cos(t) - 5 * math.cos(2 * t) \
            - 2 * math.cos(3 * t) - math.cos(4 * t)
        pts.append((cx + x * s, cy - y * s))
    return pts

NOTIF = 96
nicon = Image.new("RGBA", (NOTIF, NOTIF), (0, 0, 0, 0))
nd = ImageDraw.Draw(nicon)
nd.polygon(heart_points(NOTIF / 2, NOTIF / 2 + 2, NOTIF / 34), fill=(255, 255, 255, 255))
d = os.path.join(RES, "drawable-nodpi")
os.makedirs(d, exist_ok=True)
nicon.save(os.path.join(d, "ic_notif.png"))

print("icons generated:")
for root_, _, files in os.walk(RES):
    for f in files:
        if f.startswith("ic_launcher") or f == "ic_notif.png":
            print(" ", os.path.relpath(os.path.join(root_, f), RES))
