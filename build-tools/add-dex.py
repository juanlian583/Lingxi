#!/usr/bin/env python3
"""把 classes.dex 加入 aapt2 生成的 APK（压缩存储），保持其余条目不变。"""
import sys, zipfile, shutil

src, dex, dst = sys.argv[1], sys.argv[2], sys.argv[3]

with zipfile.ZipFile(src, 'r') as zin:
    infos = zin.infolist()
    data = {i.filename: zin.read(i.filename) for i in infos}

with zipfile.ZipFile(dst, 'w', zipfile.ZIP_DEFLATED) as zout:
    for i in infos:
        zi = zipfile.ZipInfo(i.filename, i.date_time)
        zi.compress_type = i.compress_type
        zi.external_attr = i.external_attr
        zout.writestr(zi, data[i.filename])
    zi = zipfile.ZipInfo('classes.dex')
    zi.compress_type = zipfile.ZIP_DEFLATED
    with open(dex, 'rb') as f:
        zout.writestr(zi, f.read())

print("added classes.dex ->", dst)
