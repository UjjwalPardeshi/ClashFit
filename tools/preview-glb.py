#!/usr/bin/env python3
"""
Render a GLB to a PNG with a small software rasteriser, so a generated model can be looked at
without a phone, a 3D engine, or a viewer.

This exists because tools/make-boss.py writes a model nobody has ever seen. Shipping geometry
sight-unseen is how you find out on stage that an arm points backwards.

    python3 tools/preview-glb.py <model.glb> <out.png> [--anim NAME] [--t 0.5] [--yaw 25]

It walks the node hierarchy applying TRS, samples any requested animation clip at time --t,
projects orthographically, and z-buffers flat-shaded triangles. No perspective, no PBR: enough to
verify a pose, a proportion, and that nothing is inside out.
"""

import argparse
import json
import math
import struct
import sys

from PIL import Image

BG = (10, 11, 13)


def load_glb(path):
    data = open(path, "rb").read()
    magic, version, _ = struct.unpack_from("<III", data, 0)
    assert magic == 0x46546C67, "not a GLB"
    assert version == 2, f"glTF version {version}, expected 2"
    off, js, bin_ = 12, None, b""
    while off < len(data):
        length, kind = struct.unpack_from("<II", data, off)
        chunk = data[off + 8: off + 8 + length]
        if kind == 0x4E4F534A:
            js = json.loads(chunk)
        elif kind == 0x004E4942:
            bin_ = chunk
        off += 8 + length
    assert js is not None, "no JSON chunk"
    return js, bin_


def read_accessor(g, bin_, idx):
    a = g["accessors"][idx]
    bv = g["bufferViews"][a["bufferView"]]
    base = bv.get("byteOffset", 0) + a.get("byteOffset", 0)
    ncomp = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4}[a["type"]]
    fmt, size = {5126: ("f", 4), 5123: ("H", 2), 5125: ("I", 4)}[a["componentType"]]
    out = []
    for i in range(a["count"]):
        o = base + i * ncomp * size
        v = struct.unpack_from(f"<{ncomp}{fmt}", bin_, o)
        out.append(v[0] if ncomp == 1 else v)
    return out


def quat_to_m(q):
    x, y, z, w = q
    return [
        [1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
        [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
        [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)],
    ]


def mat4(t, r, s):
    m = quat_to_m(r)
    return [
        [m[0][0] * s[0], m[0][1] * s[1], m[0][2] * s[2], t[0]],
        [m[1][0] * s[0], m[1][1] * s[1], m[1][2] * s[2], t[1]],
        [m[2][0] * s[0], m[2][1] * s[1], m[2][2] * s[2], t[2]],
        [0, 0, 0, 1],
    ]


def mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(4)) for j in range(4)] for i in range(4)]


def xform(m, p):
    return (
        m[0][0] * p[0] + m[0][1] * p[1] + m[0][2] * p[2] + m[0][3],
        m[1][0] * p[0] + m[1][1] * p[1] + m[1][2] * p[2] + m[1][3],
        m[2][0] * p[0] + m[2][1] * p[1] + m[2][2] * p[2] + m[2][3],
    )


def xform_dir(m, p):
    v = (
        m[0][0] * p[0] + m[0][1] * p[1] + m[0][2] * p[2],
        m[1][0] * p[0] + m[1][1] * p[1] + m[1][2] * p[2],
        m[2][0] * p[0] + m[2][1] * p[1] + m[2][2] * p[2],
    )
    n = math.sqrt(sum(c * c for c in v)) or 1.0
    return (v[0] / n, v[1] / n, v[2] / n)


def sample(g, bin_, clip, node_idx, path, t):
    """The animated value for one node/path at time t, or None if this clip does not touch it."""
    for ch in clip["channels"]:
        if ch["target"]["node"] != node_idx or ch["target"]["path"] != path:
            continue
        s = clip["samplers"][ch["sampler"]]
        times = read_accessor(g, bin_, s["input"])
        vals = read_accessor(g, bin_, s["output"])
        if t <= times[0]:
            return vals[0]
        if t >= times[-1]:
            return vals[-1]
        for i in range(len(times) - 1):
            if times[i] <= t <= times[i + 1]:
                span = times[i + 1] - times[i]
                f = 0.0 if span == 0 else (t - times[i]) / span
                a, b = vals[i], vals[i + 1]
                out = tuple(a[k] + (b[k] - a[k]) * f for k in range(len(a)))
                if path == "rotation":
                    n = math.sqrt(sum(c * c for c in out)) or 1.0
                    out = tuple(c / n for c in out)
                return out
    return None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("glb")
    ap.add_argument("out")
    ap.add_argument("--anim", default=None)
    ap.add_argument("--t", type=float, default=0.0)
    ap.add_argument("--yaw", type=float, default=22.0)
    ap.add_argument("--pitch", type=float, default=8.0)
    ap.add_argument("--size", type=int, default=520)
    args = ap.parse_args()

    g, bin_ = load_glb(args.glb)
    clip = None
    if args.anim:
        for a in g.get("animations", []):
            if a["name"] == args.anim:
                clip = a
        if clip is None:
            sys.exit(f"no clip named {args.anim}; have "
                     f"{[a['name'] for a in g.get('animations', [])]}")

    tris = []

    def walk(idx, parent):
        n = g["nodes"][idx]
        t = list(n.get("translation", [0, 0, 0]))
        r = list(n.get("rotation", [0, 0, 0, 1]))
        s = list(n.get("scale", [1, 1, 1]))
        if clip:
            t = list(sample(g, bin_, clip, idx, "translation", args.t) or t)
            r = list(sample(g, bin_, clip, idx, "rotation", args.t) or r)
            s = list(sample(g, bin_, clip, idx, "scale", args.t) or s)
        world = mul(parent, mat4(t, r, s))
        if "mesh" in n:
            for prim in g["meshes"][n["mesh"]]["primitives"]:
                pos = read_accessor(g, bin_, prim["attributes"]["POSITION"])
                nrm = read_accessor(g, bin_, prim["attributes"]["NORMAL"])
                idxs = read_accessor(g, bin_, prim["indices"])
                mat = g["materials"][prim["material"]]
                base = mat["pbrMetallicRoughness"]["baseColorFactor"][:3]
                emis = mat.get("emissiveFactor", [0, 0, 0])
                for k in range(0, len(idxs), 3):
                    a, b, c = idxs[k], idxs[k + 1], idxs[k + 2]
                    tris.append((
                        [xform(world, pos[a]), xform(world, pos[b]), xform(world, pos[c])],
                        xform_dir(world, nrm[a]), base, emis,
                    ))
        for child in n.get("children", []):
            walk(child, world)

    ident = [[1 if i == j else 0 for j in range(4)] for i in range(4)]
    for root in g["scenes"][g.get("scene", 0)]["nodes"]:
        walk(root, ident)

    # Orbit the camera, then fit the model to the frame.
    cy, sy = math.cos(math.radians(args.yaw)), math.sin(math.radians(args.yaw))
    cp, sp = math.cos(math.radians(args.pitch)), math.sin(math.radians(args.pitch))

    def view(p):
        x, y, z = p
        x, z = x * cy + z * sy, -x * sy + z * cy
        y, z = y * cp - z * sp, y * sp + z * cp
        return (x, y, z)

    pts = [view(v) for tri in tris for v in tri[0]]
    if not pts:
        sys.exit("nothing to draw")
    xs, ys = [p[0] for p in pts], [p[1] for p in pts]
    minx, maxx, miny, maxy = min(xs), max(xs), min(ys), max(ys)
    span = max(maxx - minx, maxy - miny) * 1.12 or 1.0
    W = H = args.size
    scale = W / span
    ox = W / 2 - (minx + maxx) / 2 * scale
    oy = H / 2 + (miny + maxy) / 2 * scale

    img = Image.new("RGB", (W, H), BG)
    px = img.load()
    zbuf = [[1e9] * W for _ in range(H)]
    light = (-0.42, 0.72, 0.55)

    for verts, n, base, emis in tris:
        v = [view(p) for p in verts]
        nv = view(n)
        screen = [(p[0] * scale + ox, oy - p[1] * scale, p[2]) for p in v]
        lam = max(0.0, nv[0] * light[0] + nv[1] * light[1] + nv[2] * light[2])
        shade = 0.34 + 0.66 * lam
        col = tuple(
            min(255, int(255 * (base[i] * shade + emis[i] * 0.85)))
            for i in range(3)
        )
        x0 = max(0, int(min(p[0] for p in screen)))
        x1 = min(W - 1, int(max(p[0] for p in screen)) + 1)
        y0 = max(0, int(min(p[1] for p in screen)))
        y1 = min(H - 1, int(max(p[1] for p in screen)) + 1)
        ax, ay, _ = screen[0]
        bx, by, _ = screen[1]
        cx2, cy2, _ = screen[2]
        den = (by - cy2) * (ax - cx2) + (cx2 - bx) * (ay - cy2)
        if abs(den) < 1e-9:
            continue
        for py in range(y0, y1 + 1):
            for pxx in range(x0, x1 + 1):
                w0 = ((by - cy2) * (pxx - cx2) + (cx2 - bx) * (py - cy2)) / den
                w1 = ((cy2 - ay) * (pxx - cx2) + (ax - cx2) * (py - cy2)) / den
                w2 = 1 - w0 - w1
                if w0 < 0 or w1 < 0 or w2 < 0:
                    continue
                z = w0 * screen[0][2] + w1 * screen[1][2] + w2 * screen[2][2]
                if z < zbuf[py][pxx]:
                    zbuf[py][pxx] = z
                    px[pxx, py] = col

    img.save(args.out)
    print(f"{args.out}  {W}x{H}  {len(tris)} triangles"
          + (f"  clip={args.anim} t={args.t}" if clip else ""))


if __name__ == "__main__":
    main()
