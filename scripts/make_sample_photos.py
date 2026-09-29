"""Generate throwaway gradient PNGs so CI can seed the simulator's photo library."""

import struct
import zlib

PNG_SIGNATURE = bytes([137, 80, 78, 71, 13, 10, 26, 10])


def chunk(tag, data):
    body = tag + data
    return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)


def write_png(path, width, height, shade):
    raw = bytearray()
    for y in range(height):
        raw.append(0)
        for x in range(width):
            raw.extend(shade(x / width, y / height))

    header = struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)
    with open(path, "wb") as f:
        f.write(PNG_SIGNATURE)
        f.write(chunk(b"IHDR", header))
        f.write(chunk(b"IDAT", zlib.compress(bytes(raw), 6)))
        f.write(chunk(b"IEND", b""))


write_png("sample1.png", 900, 1600, lambda u, v: (int(40 + 180 * v), int(70 + 120 * u), int(190 - 90 * v)))
write_png("sample2.png", 900, 1600, lambda u, v: (int(210 - 120 * u), int(90 + 140 * v), int(60 + 100 * u)))
write_png("sample3.png", 900, 1600, lambda u, v: (int(30 + 60 * u), int(120 - 60 * v), int(150 + 80 * v)))
