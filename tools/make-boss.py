#!/usr/bin/env python3
"""
Generate THE PACEMAKER as a real glTF 2.0 binary model.

Why generate rather than license one. A rigged, animated villain found online arrives with a
licence to verify, a rig somebody else designed, and animation clips that do not match the six
states this game actually needs. Generating it means the asset is ours outright, the joint
hierarchy is exactly the one the combat model drives, and the damage states are authored against
the health thresholds rather than approximated by blending somebody else's clips.

The rig is a node hierarchy, not a skinned mesh. glTF animates node transforms natively and
Filament's glTF loader plays those clips without any skinning setup, which buys articulated limbs,
armour that detaches, and a collapse on death, without a vertex-weighting pipeline that would take
a week to get right and could not fail gracefully.

    python3 tools/make-boss.py
    -> android/app/src/main/assets/models/pacemaker.glb

Run it again after changing anything here; the output is deterministic.
"""

import json
import math
import pathlib
import struct

OUT = pathlib.Path(__file__).resolve().parent.parent / "android/app/src/main/assets/models/pacemaker.glb"

# ---------------------------------------------------------------- buffer plumbing

blob = bytearray()
accessors: list[dict] = []
buffer_views: list[dict] = []


def _pad4() -> None:
    while len(blob) % 4:
        blob.append(0)


def _view(data: bytes, target: int | None = None) -> int:
    _pad4()
    off = len(blob)
    blob.extend(data)
    bv = {"buffer": 0, "byteOffset": off, "byteLength": len(data)}
    if target is not None:
        bv["target"] = target
    buffer_views.append(bv)
    return len(buffer_views) - 1


def acc_f32(values: list[tuple], kind: str, target: int | None = None) -> int:
    """A float accessor. `values` is a list of same-length tuples."""
    n = len(values[0])
    flat = [c for v in values for c in v]
    bv = _view(struct.pack(f"<{len(flat)}f", *flat), target)
    mins = [min(v[i] for v in values) for i in range(n)]
    maxs = [max(v[i] for v in values) for i in range(n)]
    accessors.append({
        "bufferView": bv, "componentType": 5126, "count": len(values),
        "type": kind, "min": mins, "max": maxs,
    })
    return len(accessors) - 1


def acc_scalar_f32(values: list[float]) -> int:
    bv = _view(struct.pack(f"<{len(values)}f", *values))
    accessors.append({
        "bufferView": bv, "componentType": 5126, "count": len(values),
        "type": "SCALAR", "min": [min(values)], "max": [max(values)],
    })
    return len(accessors) - 1


def acc_u16(values: list[int]) -> int:
    bv = _view(struct.pack(f"<{len(values)}H", *values), target=34963)
    accessors.append({
        "bufferView": bv, "componentType": 5123, "count": len(values), "type": "SCALAR",
    })
    return len(accessors) - 1


# ---------------------------------------------------------------- geometry

def prism(bw: float, bd: float, tw: float, td: float, h: float, y0: float = 0.0):
    """
    A tapered box from y0 to y0+h. Wider at the bottom than the top reads as a limb; equal reads
    as a plate. Flat-shaded: every face gets its own four vertices so the normals stay crisp,
    which is what makes a machine look machined rather than moulded.
    """
    b, t = bw / 2, tw / 2
    bz, tz = bd / 2, td / 2
    y1 = y0 + h
    corners = [
        (-b, y0, -bz), (b, y0, -bz), (b, y0, bz), (-b, y0, bz),   # bottom 0..3
        (-t, y1, -tz), (t, y1, -tz), (t, y1, tz), (-t, y1, tz),   # top    4..7
    ]
    faces = [
        ((0, 1, 2, 3), (0, -1, 0)),   # bottom
        ((7, 6, 5, 4), (0, 1, 0)),    # top
        ((0, 4, 5, 1), (0, 0, -1)),   # back
        ((3, 2, 6, 7), (0, 0, 1)),    # front
        ((0, 3, 7, 4), (-1, 0, 0)),   # left
        ((1, 5, 6, 2), (1, 0, 0)),    # right
    ]
    pos, nrm, idx = [], [], []
    for quad, n in faces:
        base = len(pos)
        # Slanted sides need a normal that actually points along the slope.
        if n[1] == 0 and bw != tw:
            a, bb, c = corners[quad[0]], corners[quad[1]], corners[quad[2]]
            u = (bb[0] - a[0], bb[1] - a[1], bb[2] - a[2])
            v = (c[0] - a[0], c[1] - a[1], c[2] - a[2])
            n = (u[1] * v[2] - u[2] * v[1], u[2] * v[0] - u[0] * v[2], u[0] * v[1] - u[1] * v[0])
            m = math.sqrt(sum(k * k for k in n)) or 1.0
            n = (n[0] / m, n[1] / m, n[2] / m)
        for ci in quad:
            pos.append(corners[ci])
            nrm.append(n)
        idx += [base, base + 1, base + 2, base, base + 2, base + 3]
    return pos, nrm, idx


def octahedron(r: float, y0: float = 0.0):
    """The core. Eight flat faces, so it catches light as a cut stone rather than a ball."""
    top, bot = (0, y0 + r, 0), (0, y0 - r, 0)
    ring = [(r, y0, 0), (0, y0, r), (-r, y0, 0), (0, y0, -r)]
    pos, nrm, idx = [], [], []
    for i in range(4):
        a, b = ring[i], ring[(i + 1) % 4]
        for tri in ((top, a, b), (bot, b, a)):
            base = len(pos)
            u = (tri[1][0] - tri[0][0], tri[1][1] - tri[0][1], tri[1][2] - tri[0][2])
            v = (tri[2][0] - tri[0][0], tri[2][1] - tri[0][1], tri[2][2] - tri[0][2])
            n = (u[1] * v[2] - u[2] * v[1], u[2] * v[0] - u[0] * v[2], u[0] * v[1] - u[1] * v[0])
            m = math.sqrt(sum(k * k for k in n)) or 1.0
            for p in tri:
                pos.append(p)
                nrm.append((n[0] / m, n[1] / m, n[2] / m))
            idx += [base, base + 1, base + 2]
    return pos, nrm, idx


meshes: list[dict] = []


def add_mesh(geo, material: int, name: str) -> int:
    pos, nrm, idx = geo
    meshes.append({
        "name": name,
        "primitives": [{
            "attributes": {"POSITION": acc_f32(pos, "VEC3", 34962), "NORMAL": acc_f32(nrm, "VEC3", 34962)},
            "indices": acc_u16(idx),
            "material": material,
        }],
    })
    return len(meshes) - 1


# ---------------------------------------------------------------- materials

def pbr(name, colour, metallic=1.0, rough=0.35, emissive=(0, 0, 0), strength=0.0):
    m = {
        "name": name,
        "pbrMetallicRoughness": {
            "baseColorFactor": list(colour) + [1.0],
            "metallicFactor": metallic,
            "roughnessFactor": rough,
        },
        "doubleSided": False,
    }
    if strength > 0:
        m["emissiveFactor"] = [c * min(1.0, strength) for c in emissive]
        if strength > 1.0:
            m["extensions"] = {"KHR_materials_emissive_strength": {"emissiveStrength": strength}}
    return m


# The palette is the app's: Ember on Panel, with Brass trim.
MATERIALS = [
    pbr("shell", (0.267, 0.286, 0.337), metallic=0.30, rough=0.52),         # 0 dark plate
    pbr("shell_lit", (0.404, 0.427, 0.486), metallic=0.25, rough=0.44),     # 1 lit plate
    pbr("brass", (0.788, 0.588, 0.275), metallic=0.65, rough=0.28),         # 2 trim
    pbr("core", (1.0, 0.35, 0.16), metallic=0.0, rough=0.5,
        emissive=(1.0, 0.33, 0.13), strength=1.1),                          # 3 glowing core
    pbr("eye", (1.0, 0.62, 0.25), metallic=0.0, rough=0.4,
        emissive=(1.0, 0.62, 0.25), strength=1.6),                          # 4 eye
]

SHELL, SHELL_LIT, BRASS, CORE, EYE = 0, 1, 2, 3, 4

# ---------------------------------------------------------------- the body

M_PELVIS = add_mesh(prism(0.62, 0.40, 0.52, 0.34, 0.26), SHELL_LIT, "pelvis")
M_SPINE = add_mesh(prism(0.52, 0.34, 0.74, 0.44, 0.52), SHELL, "spine")
M_CHEST = add_mesh(prism(0.74, 0.44, 0.60, 0.38, 0.30), SHELL_LIT, "chest")
M_NECK = add_mesh(prism(0.20, 0.20, 0.17, 0.17, 0.16), BRASS, "neck")
M_HEAD = add_mesh(prism(0.46, 0.42, 0.38, 0.34, 0.40), SHELL_LIT, "head")
M_VISOR = add_mesh(prism(0.34, 0.07, 0.34, 0.07, 0.06), EYE, "visor")
M_CORE = add_mesh(octahedron(0.26), CORE, "core")
M_SHOULDER = add_mesh(prism(0.26, 0.26, 0.20, 0.20, 0.18), BRASS, "shoulder")
M_UPPERARM = add_mesh(prism(0.21, 0.21, 0.16, 0.16, 0.44), SHELL, "upper_arm")
M_FOREARM = add_mesh(prism(0.17, 0.17, 0.22, 0.22, 0.42), SHELL_LIT, "forearm")
M_FIST = add_mesh(prism(0.24, 0.24, 0.22, 0.22, 0.18), BRASS, "fist")
M_THIGH = add_mesh(prism(0.24, 0.24, 0.19, 0.19, 0.46), SHELL, "thigh")
M_SHIN = add_mesh(prism(0.19, 0.19, 0.16, 0.16, 0.44), SHELL_LIT, "shin")
M_FOOT = add_mesh(prism(0.22, 0.34, 0.20, 0.30, 0.12), BRASS, "foot")
M_PLATE = add_mesh(prism(0.30, 0.09, 0.22, 0.07, 0.34), SHELL, "plate")
M_PAULDRON = add_mesh(prism(0.38, 0.34, 0.22, 0.22, 0.20), BRASS, "pauldron")

nodes: list[dict] = []


def node(name, mesh=None, t=(0, 0, 0), r=None, s=None, children=None) -> int:
    n = {"name": name, "translation": list(t)}
    if mesh is not None:
        n["mesh"] = mesh
    if r is not None:
        n["rotation"] = list(r)
    if s is not None:
        n["scale"] = list(s)
    if children:
        n["children"] = children
    nodes.append(n)
    return len(nodes) - 1


def q_axis(axis: str, deg: float):
    """A quaternion for a rotation about one axis, in glTF's xyzw order."""
    h = math.radians(deg) / 2
    s, c = math.sin(h), math.cos(h)
    return {"x": (s, 0, 0, c), "y": (0, s, 0, c), "z": (0, 0, s, c)}[axis]


def arm(side: str, sign: int) -> int:
    """
    One arm, hanging from the chest.

    The elbow node sits at the elbow. That sounds obvious, and it was wrong: with the pivot at the
    shoulder, bending the elbow swung the entire arm from the shoulder instead, so every angry pose
    read as a shrug. Meshes grow along +Y from their own origin, so a limb hanging downward is a
    mesh translated down by its own length.
    """
    fist = node(f"fist_{side}", M_FIST, t=(0, -0.20, 0))
    forearm = node(f"forearm_{side}", M_FOREARM, t=(0, -0.42, 0), children=[fist])
    elbow = node(f"elbow_{side}", None, t=(0, -0.44, 0), children=[forearm])
    upper = node(f"upper_arm_{side}", M_UPPERARM, t=(0, -0.44, 0))
    pauldron = node(f"pauldron_{side}", M_PAULDRON, t=(sign * 0.09, -0.04, 0))
    shoulder = node(f"shoulder_{side}", M_SHOULDER, t=(0, -0.16, 0))
    return node(
        f"arm_{side}", None, t=(sign * 0.46, 0.18, 0),
        r=q_axis("z", sign * -7), children=[shoulder, pauldron, upper, elbow],
    )


def leg(side: str, sign: int) -> int:
    foot = node(f"foot_{side}", M_FOOT, t=(0, -0.12, 0.04))
    shin = node(f"shin_{side}", M_SHIN, t=(0, -0.44, 0), children=[foot])
    knee = node(f"knee_{side}", None, t=(0, -0.46, 0), children=[shin])
    thigh = node(f"thigh_{side}", M_THIGH, t=(0, -0.46, 0))
    return node(f"leg_{side}", None, t=(sign * 0.20, -0.02, 0), children=[thigh, knee])


# Eight armour plates in a ring around the chest. Each is its own node so it can be blown off.
plate_nodes = []
for i in range(8):
    # Offset by half a step so no plate sits dead centre in front of the core. Armour should hide
    # the heart, not erase it: the player needs to see it glowing between the plates and getting
    # brighter as the thing gets angry.
    a = i * 360 / 8 + 22.5
    rad = math.radians(a)
    pivot = node(
        f"plate_{i}", M_PLATE,
        t=(math.sin(rad) * 0.78, 0.0, math.cos(rad) * 0.62),
        r=q_axis("y", a),
    )
    plate_nodes.append(pivot)
ring = node("ring", None, t=(0, 0.72, 0), children=plate_nodes)

visor = node("visor", M_VISOR, t=(0, 0.21, 0.21))
head = node("head", M_HEAD, t=(0, 0.10, 0), children=[visor])
neck = node("neck", M_NECK, t=(0, 0.30, 0), children=[head])
# On the chest, proud of the plate, facing the player. It is the health bar you can see.
core = node("core", M_CORE, t=(0, 0.15, 0.32))
chest = node("chest", M_CHEST, t=(0, 0.52, 0), children=[neck, core, arm("l", -1), arm("r", 1)])
spine = node("spine", M_SPINE, t=(0, 0.26, 0), children=[chest])
pelvis = node("pelvis", M_PELVIS, t=(0, 0, 0), children=[spine])
hips = node("hips", None, t=(0, 1.62, 0), children=[pelvis, ring, leg("l", -1), leg("r", 1)])
root = node("pacemaker", None, t=(0, 0, 0), children=[hips])

BY_NAME = {n["name"]: i for i, n in enumerate(nodes)}

# ---------------------------------------------------------------- animation

animations: list[dict] = []


def clip(name: str, tracks: list[tuple]):
    """
    tracks: (node name, path, [(time, value), ...]).
    Every clip starts and ends at the same pose so it loops without a jump.
    """
    samplers, channels = [], []
    for node_name, path, keys in tracks:
        times = [k[0] for k in keys]
        vals = [k[1] for k in keys]
        kind = {"rotation": "VEC4", "translation": "VEC3", "scale": "VEC3"}[path]
        samplers.append({
            "input": acc_scalar_f32(times),
            "output": acc_f32(vals, kind),
            "interpolation": "LINEAR",
        })
        channels.append({
            "sampler": len(samplers) - 1,
            "target": {"node": BY_NAME[node_name], "path": path},
        })
    animations.append({"name": name, "samplers": samplers, "channels": channels})


IDENT = (0.0, 0.0, 0.0, 1.0)


def sway(node_name, axis, deg, period, phase=0.0, steps=8):
    """A smooth back-and-forth on one axis, sampled as keyframes."""
    keys = []
    for i in range(steps + 1):
        t = period * i / steps
        a = deg * math.sin(2 * math.pi * (i / steps) + phase)
        keys.append((t, q_axis(axis, a)))
    return (node_name, "rotation", keys)


def breathe(node_name, amount, period, steps=8):
    keys = []
    for i in range(steps + 1):
        t = period * i / steps
        s = 1.0 + amount * math.sin(2 * math.pi * (i / steps))
        keys.append((t, (1.0, s, 1.0)))
    return (node_name, "scale", keys)


# 0 · IDLE — alive but waiting. Slow breath, a small sway, the ring turning.
clip("idle", [
    breathe("chest", 0.035, 3.2),
    sway("hips", "y", 3.0, 3.2),
    sway("spine", "x", 2.0, 3.2, phase=0.6),
    sway("arm_l", "x", 5.0, 3.2, phase=0.3),
    sway("arm_r", "x", 5.0, 3.2, phase=3.4),
    ("ring", "rotation", [(0.0, q_axis("y", 0)), (4.0, q_axis("y", 179)), (8.0, q_axis("y", 358))]),
    ("core", "scale", [(0.0, (1, 1, 1)), (0.8, (1.14, 1.14, 1.14)), (1.6, (1, 1, 1)),
                       (2.0, (1.08, 1.08, 1.08)), (3.2, (1, 1, 1))]),
])

# 1 · HIT — a short recoil. Played over the top of whatever else is running.
clip("hit", [
    ("hips", "translation", [(0.0, (0, 1.62, 0)), (0.06, (0, 1.60, -0.16)), (0.24, (0, 1.62, 0))]),
    ("chest", "rotation", [(0.0, IDENT), (0.06, q_axis("x", 11)), (0.24, IDENT)]),
    ("head", "rotation", [(0.0, IDENT), (0.08, q_axis("x", 17)), (0.26, IDENT)]),
    ("core", "scale", [(0.0, (1, 1, 1)), (0.05, (1.5, 1.5, 1.5)), (0.24, (1, 1, 1))]),
])

# 2 · ENRAGE — hunched, faster, arms up. Phase two.
clip("enrage", [
    breathe("chest", 0.06, 1.5),
    sway("hips", "y", 6.0, 1.5),
    ("spine", "rotation", [(0.0, q_axis("x", 9)), (0.75, q_axis("x", 13)), (1.5, q_axis("x", 9))]),
    ("arm_l", "rotation", [(0.0, q_axis("z", -34)), (0.75, q_axis("z", -40)), (1.5, q_axis("z", -34))]),
    ("arm_r", "rotation", [(0.0, q_axis("z", 34)), (0.75, q_axis("z", 40)), (1.5, q_axis("z", 34))]),
    ("elbow_l", "rotation", [(0.0, q_axis("x", -50)), (0.75, q_axis("x", -62)), (1.5, q_axis("x", -50))]),
    ("elbow_r", "rotation", [(0.0, q_axis("x", -50)), (0.75, q_axis("x", -62)), (1.5, q_axis("x", -50))]),
    ("ring", "rotation", [(0.0, q_axis("y", 0)), (0.75, q_axis("y", 179)), (1.5, q_axis("y", 358))]),
    ("core", "scale", [(0.0, (1.15, 1.15, 1.15)), (0.38, (1.4, 1.4, 1.4)), (0.75, (1.15, 1.15, 1.15)),
                       (1.12, (1.35, 1.35, 1.35)), (1.5, (1.15, 1.15, 1.15))]),
])

# 3 · STAGGER — the shell opens and the core is exposed. The window to hit it hard.
stagger_tracks = [
    ("spine", "rotation", [(0.0, IDENT), (0.3, q_axis("x", -22)), (1.4, q_axis("x", -18)), (1.8, IDENT)]),
    ("arm_l", "rotation", [(0.0, IDENT), (0.3, q_axis("z", -74)), (1.4, q_axis("z", -70)), (1.8, IDENT)]),
    ("arm_r", "rotation", [(0.0, IDENT), (0.3, q_axis("z", 74)), (1.4, q_axis("z", 70)), (1.8, IDENT)]),
    ("head", "rotation", [(0.0, IDENT), (0.3, q_axis("x", -26)), (1.8, IDENT)]),
    ("core", "scale", [(0.0, (1, 1, 1)), (0.3, (1.75, 1.75, 1.75)), (1.4, (1.6, 1.6, 1.6)), (1.8, (1, 1, 1))]),
]
for i, _ in enumerate(plate_nodes):
    a = math.radians(i * 360 / 8 + 22.5)
    out = (math.sin(a) * 1.34, 0.12, math.cos(a) * 1.06)
    home = (math.sin(a) * 0.78, 0.0, math.cos(a) * 0.62)
    stagger_tracks.append((f"plate_{i}", "translation",
                           [(0.0, home), (0.3, out), (1.4, out), (1.8, home)]))
clip("stagger", stagger_tracks)

# 4 · DEATH — 1.6 seconds. Plates blow off, the knees go, the core dies last.
death_tracks = [
    ("hips", "translation", [(0.0, (0, 1.62, 0)), (0.25, (0, 1.70, 0)), (0.9, (0, 0.72, 0)), (1.6, (0, 0.46, 0))]),
    ("hips", "rotation", [(0.0, IDENT), (0.9, q_axis("x", 18)), (1.6, q_axis("x", 46))]),
    ("spine", "rotation", [(0.0, IDENT), (0.5, q_axis("x", 26)), (1.6, q_axis("x", 52))]),
    ("head", "rotation", [(0.0, IDENT), (0.6, q_axis("x", 34)), (1.6, q_axis("x", 74))]),
    ("knee_l", "rotation", [(0.0, IDENT), (0.9, q_axis("x", -78)), (1.6, q_axis("x", -96))]),
    ("knee_r", "rotation", [(0.0, IDENT), (0.9, q_axis("x", -78)), (1.6, q_axis("x", -96))]),
    ("arm_l", "rotation", [(0.0, IDENT), (0.4, q_axis("z", -52)), (1.6, q_axis("z", -18))]),
    ("arm_r", "rotation", [(0.0, IDENT), (0.4, q_axis("z", 52)), (1.6, q_axis("z", 18))]),
    # The core flares, then goes out. It is the last thing to die, which is the point.
    ("core", "scale", [(0.0, (1, 1, 1)), (0.2, (2.3, 2.3, 2.3)), (0.55, (0.9, 0.9, 0.9)),
                       (1.1, (0.3, 0.3, 0.3)), (1.6, (0.02, 0.02, 0.02))]),
    ("visor", "scale", [(0.0, (1, 1, 1)), (0.3, (1, 1, 1)), (0.7, (0.05, 1, 1)), (1.6, (0.02, 1, 1))]),
]
for i, _ in enumerate(plate_nodes):
    a = math.radians(i * 360 / 8 + 22.5)
    home = (math.sin(a) * 0.78, 0.0, math.cos(a) * 0.62)
    gone = (math.sin(a) * 3.4, 1.6 - (i % 3) * 0.5, math.cos(a) * 2.6)
    death_tracks.append((f"plate_{i}", "translation",
                         [(0.0, home), (0.18, home), (1.6, gone)]))
    death_tracks.append((f"plate_{i}", "rotation",
                         [(0.0, q_axis("y", i * 45)), (1.6, q_axis("z", 300 + i * 40))]))
clip("death", death_tracks)

# ---------------------------------------------------------------- write the GLB

_pad4()
gltf = {
    "asset": {"version": "2.0", "generator": "ClashFit tools/make-boss.py"},
    "extensionsUsed": ["KHR_materials_emissive_strength"],
    "scene": 0,
    "scenes": [{"name": "pacemaker", "nodes": [root]}],
    "nodes": nodes,
    "meshes": meshes,
    "materials": MATERIALS,
    "accessors": accessors,
    "bufferViews": buffer_views,
    "buffers": [{"byteLength": len(blob)}],
    "animations": animations,
}

json_chunk = json.dumps(gltf, separators=(",", ":")).encode("utf-8")
while len(json_chunk) % 4:
    json_chunk += b" "
bin_chunk = bytes(blob)

glb = bytearray()
glb += struct.pack("<III", 0x46546C67, 2, 12 + 8 + len(json_chunk) + 8 + len(bin_chunk))
glb += struct.pack("<II", len(json_chunk), 0x4E4F534A) + json_chunk
glb += struct.pack("<II", len(bin_chunk), 0x004E4942) + bin_chunk

OUT.parent.mkdir(parents=True, exist_ok=True)
OUT.write_bytes(bytes(glb))

print(f"{OUT.relative_to(OUT.parents[4])}")
print(f"  {len(glb) / 1024:.1f} KB · {len(nodes)} nodes · {len(meshes)} meshes · "
      f"{len(animations)} clips · {len(accessors)} accessors")
for a in animations:
    print(f"    {a['name']:<9} {len(a['channels'])} channels")
