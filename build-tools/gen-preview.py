#!/usr/bin/env python3
# 生成 README 社交预览图 docs/screenshot.png
import os
from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "app/src/main/assets/spritesheet.webp")
OUT = os.path.join(ROOT, "docs/screenshot.png")
FONT = "/tmp/NotoSansSC.ttf"

W, H = 1200, 630
img = Image.new("RGB", (W, H))
d = ImageDraw.Draw(img)
for y in range(H):
    t = y / H
    r = int(0x6A + (0x9B - 0x6A) * t)
    g = int(0x93 + (0x6B - 0x93) * t)
    b = int(0xF5 + (0xF2 - 0xF5) * t)
    d.line([(0, y), (W, y)], fill=(r, g, b))

def font(sz):
    return ImageFont.truetype(FONT, sz)

d.text((70, 70), "灵汐 Lingxi", font=font(72), fill=(255, 255, 255))
d.text((74, 170), "桌面 AI 桌宠 · DeepSeek娘", font=font(34), fill=(230, 236, 255))
d.text((74, 240), "显示在所有应用之上 · 单击互动 · 双击摸头 · AI 聊天", font=font(26), fill=(210, 220, 250))

# 角色（待机帧放大）
sheet = Image.open(SRC).convert("RGBA")
cell = sheet.crop((0, 0, 192, 208))
target_h = 430
ratio = target_h / cell.height
cell = cell.resize((int(cell.width * ratio), target_h), Image.LANCZOS)
img.paste(cell, (W - cell.width - 90, H - cell.height - 40), cell)

# 气泡
bw, bh = 330, 96
bx, by = W - cell.width - 90 - bw + 40, 60
d.rounded_rectangle([bx, by, bx + bw, by + bh], radius=24, fill=(255, 255, 255))
d.polygon([(bx + 60, by + bh), (bx + 96, by + bh + 26), (bx + 120, by + bh)], fill=(255, 255, 255))
d.text((bx + 22, by + 26), "嗨～主人好呀！🐳", font=font(28), fill=(60, 66, 95))

os.makedirs(os.path.dirname(OUT), exist_ok=True)
img.save(OUT)
print("saved", OUT, os.path.getsize(OUT), "bytes")
