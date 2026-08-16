#!/usr/bin/env python3
"""repackage.py — 保真重打包 APK：
1. 逐字节保留 aapt2 输出的条目（本地文件头+数据），仅修正 4 字节对齐；
2. 追加 classes.dex（deflate）；
3. 重建中央目录。
用法: repackage.py <in.apk> <classes.dex> <out.apk>
"""
import struct, sys, zlib, zipfile

ALIGN = 4

def dos_time_date(dt):
    y, m, d, hh, mm, ss = dt
    t = (hh << 11) | (mm << 5) | (ss // 2)
    da = ((y - 1980) << 9) | (m << 5) | d
    return t, da

def main(src, dex_path, dst):
    zin = zipfile.ZipFile(src, 'r')
    infos = zin.infolist()
    raw = open(src, 'rb').read()

    # 读取每个条目的原始压缩数据与本地 extra
    entries = []  # (info, raw_data, local_extra)
    for i in infos:
        namelen = len(i.filename.encode('utf-8'))
        hdr = i.header_offset
        assert struct.unpack('<I', raw[hdr:hdr+4])[0] == 0x04034b50
        extralen = struct.unpack('<H', raw[hdr+28:hdr+30])[0]
        data_off = hdr + 30 + namelen + extralen
        data = raw[data_off:data_off + i.compress_size]
        local_extra = raw[hdr+30+namelen:hdr+30+namelen+extralen]
        entries.append((i, data, local_extra))

    # classes.dex
    dex_bytes = open(dex_path, 'rb').read()
    co = zlib.compressobj(9, zlib.DEFLATED, -15)
    dex_comp = co.compress(dex_bytes) + co.flush()
    dex_crc = zlib.crc32(dex_bytes) & 0xffffffff

    out = open(dst, 'wb')
    cd = []
    offset = 0

    def write_entry(name, method, crc, csize, usize, data, local_extra, dt,
                    external_attr, align_data=True, align_cd_end=False):
        nonlocal offset
        nb = name.encode('utf-8')
        base = 30 + len(nb)
        extra = bytearray(local_extra)
        # 对齐数据起始（stored 条目）
        if method == 0 and align_data:
            need = (ALIGN - ((offset + base + len(extra)) % ALIGN)) % ALIGN
            extra = bytearray(b'\x00' * need) + extra
        # 对齐 CD 起点（最后一个条目）
        if align_cd_end:
            end_off = offset + base + len(extra) + csize
            need = (ALIGN - (end_off % ALIGN)) % ALIGN
            extra = extra + bytearray(b'\x00' * need)
        t, da = dos_time_date(dt)
        flags = 0x0800
        hdr = struct.pack('<IHHHHHIIIHH',
                          0x04034b50, 20, flags, method, t, da,
                          crc, csize, usize, len(nb), len(extra))
        local_start = offset
        out.write(hdr)
        out.write(nb)
        out.write(bytes(extra))
        out.write(data)
        offset += 30 + len(nb) + len(extra) + csize
        cd.append((name, method, t, da, crc, csize, usize, external_attr, local_start))

    n = len(entries)
    for idx, (i, data, lextra) in enumerate(entries):
        is_last = (idx == n - 1)  # dex 会在后面追加，所以真正的最后一个条目是 dex
        write_entry(i.filename, i.compress_type, i.CRC, i.compress_size, i.file_size,
                    data, lextra, i.date_time, i.external_attr,
                    align_data=(i.compress_type == 0), align_cd_end=False)

    # classes.dex（最后一个条目，负责 CD 对齐）
    write_entry('classes.dex', 8, dex_crc, len(dex_comp), len(dex_bytes),
                dex_comp, b'', (1980, 1, 1, 0, 0, 0), 0,
                align_data=False, align_cd_end=True)

    # 中央目录
    cd_start = offset
    cd_size = 0
    for (name, method, t, da, crc, csize, usize, extattr, local_start) in cd:
        nb = name.encode('utf-8')
        rec = struct.pack('<IHHHHHHIIIHHHHHII',
                          0x02014b50, 0x1414, 20, 0x0800, method, t, da,
                          crc, csize, usize, len(nb), 0, 0, 0, 0, extattr, local_start)
        out.write(rec)
        out.write(nb)
        cd_size += len(rec) + len(nb)

    eocd = struct.pack('<IHHHHIIH', 0x06054b50, 0, 0, len(cd), len(cd), cd_size, cd_start, 0)
    out.write(eocd)
    out.close()
    zin.close()
    print(f"OK: {dst}  entries={len(cd)}  cd_offset={cd_start} (aligned={cd_start % ALIGN == 0})")

if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2], sys.argv[3])
