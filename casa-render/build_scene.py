"""Ricostruisce la casa (geometria di .claude/skills/casa-piantina/assets/casa-3d-v3.html)
in Blender e produce render fotorealistici con Cycles.

Uso:
  python build_scene.py --cams ext_ingresso,int_soggiorno --res 1920 --samples 256 --out renders
  python build_scene.py --save casa.blend        (solo scena, nessun render)

Coordinate di pianta come nel modello HTML, in metri: x verso destra della piantina, y verso il basso.
Orientamento reale (dato da Alex): destra della piantina = NORD, basso = EST, sinistra = SUD, alto = OVEST.
In Blender: X = x, Y = -y, Z = altezza.
"""
import argparse
import math
import os
import sys

import bpy
import bmesh
from mathutils import Vector

H = 2.7          # altezza interna muri
EXT, INT = 0.25, 0.10
GROUND = -0.12   # quota del terreno esterno

# ---------------------------------------------------------------- utilità scena

def reset():
    bpy.ops.wm.read_factory_settings(use_empty=True)


_geo = {}   # (materiale, bevel) -> (verts, faces)


def box(mat, x0, x1, y0, y1, z0, z1, bevel=0.0):
    """Parallelepipedo in coordinate di pianta (y verso sud)."""
    if x1 < x0:
        x0, x1 = x1, x0
    if y1 < y0:
        y0, y1 = y1, y0
    if x1 - x0 < 1e-4 or y1 - y0 < 1e-4 or z1 - z0 < 1e-4:
        return
    X0, X1, Y0, Y1 = x0, x1, -y1, -y0
    v, f = _geo.setdefault((mat, bevel), ([], []))
    o = len(v)
    v += [(X0, Y0, z0), (X1, Y0, z0), (X1, Y1, z0), (X0, Y1, z0),
          (X0, Y0, z1), (X1, Y0, z1), (X1, Y1, z1), (X0, Y1, z1)]
    for q in ((0, 3, 2, 1), (4, 5, 6, 7), (0, 1, 5, 4), (1, 2, 6, 5), (2, 3, 7, 6), (3, 0, 4, 7)):
        f.append(tuple(o + i for i in q))


def B(mat, x, y, w, d, h, z0=0.0, bevel=0.0):
    """Come B() del modello HTML: angolo nord-ovest (x, y), larghezza w (x), profondità d (y)."""
    box(mat, x, x + w, y, y + d, z0, z0 + h, bevel)


def flush_geometry(mats):
    for (mat, bevel), (v, f) in _geo.items():
        me = bpy.data.meshes.new(f"{mat}_{bevel}")
        me.from_pydata(v, [], f)
        me.update()
        ob = bpy.data.objects.new(f"{mat}_{bevel}", me)
        bpy.context.scene.collection.objects.link(ob)
        ob.data.materials.append(mats[mat])
        if bevel > 0:
            m = ob.modifiers.new("bevel", "BEVEL")
            m.width = bevel
            m.segments = 3
            m.limit_method = "ANGLE"
            m.harden_normals = False
            for p in me.polygons:
                p.use_smooth = True
    _geo.clear()


def link(ob):
    bpy.context.scene.collection.objects.link(ob)
    return ob

# ---------------------------------------------------------------- materiali

def new_mat(name):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    nt = m.node_tree
    nt.nodes.clear()
    out = nt.nodes.new("ShaderNodeOutputMaterial")
    bsdf = nt.nodes.new("ShaderNodeBsdfPrincipled")
    nt.links.new(bsdf.outputs[0], out.inputs[0])
    return m, nt, bsdf, out


def inp(node, *names):
    for n in names:
        if n in node.inputs:
            return node.inputs[n]
    raise KeyError(names)


def coords(nt, scale=(1, 1, 1), rot=(0, 0, 0)):
    tc = nt.nodes.new("ShaderNodeTexCoord")
    mp = nt.nodes.new("ShaderNodeMapping")
    mp.inputs["Scale"].default_value = scale
    mp.inputs["Rotation"].default_value = rot
    nt.links.new(tc.outputs["Object"], mp.inputs["Vector"])
    return mp.outputs["Vector"]


def add_bump(nt, bsdf, height_socket, strength=0.1, distance=0.01):
    bump = nt.nodes.new("ShaderNodeBump")
    bump.inputs["Strength"].default_value = strength
    bump.inputs["Distance"].default_value = distance
    nt.links.new(height_socket, bump.inputs["Height"])
    nt.links.new(bump.outputs["Normal"], bsdf.inputs["Normal"])
    return bump


def ramp(nt, fac, c0, c1):
    r = nt.nodes.new("ShaderNodeValToRGB")
    r.color_ramp.elements[0].color = (*c0, 1)
    r.color_ramp.elements[1].color = (*c1, 1)
    nt.links.new(fac, r.inputs[0])
    return r.outputs[0]


def mat_plain(name, color, rough=0.5, metal=0.0, coat=0.0):
    m, nt, b, _ = new_mat(name)
    b.inputs["Base Color"].default_value = (*color, 1)
    b.inputs["Roughness"].default_value = rough
    b.inputs["Metallic"].default_value = metal
    if coat:
        inp(b, "Coat Weight").default_value = coat
    return m


def mat_plaster(name, c0, c1, bump=0.08):
    m, nt, b, _ = new_mat(name)
    v = coords(nt)
    n = nt.nodes.new("ShaderNodeTexNoise")
    n.inputs["Scale"].default_value = 3.0
    n.inputs["Detail"].default_value = 6
    nt.links.new(v, n.inputs["Vector"])
    nt.links.new(ramp(nt, n.outputs["Fac"], c0, c1), b.inputs["Base Color"])
    fine = nt.nodes.new("ShaderNodeTexNoise")
    fine.inputs["Scale"].default_value = 120.0
    fine.inputs["Detail"].default_value = 4
    nt.links.new(v, fine.inputs["Vector"])
    add_bump(nt, b, fine.outputs["Fac"], bump, 0.002)
    b.inputs["Roughness"].default_value = 0.92
    return m


def mat_planks(name, c0, c1, plank_w=0.19, plank_l=1.4, rough=0.42, horiz_axis="x"):
    """Parquet a listoni (pavimenti): coordinate oggetto su piano XY."""
    m, nt, b, _ = new_mat(name)
    rot = (0, 0, 0) if horiz_axis == "x" else (0, 0, math.pi / 2)
    v = coords(nt, rot=rot)
    br = nt.nodes.new("ShaderNodeTexBrick")
    br.offset = 0.37
    br.offset_frequency = 1
    br.inputs["Scale"].default_value = 1.0
    br.inputs["Mortar Size"].default_value = 0.0012
    br.inputs["Brick Width"].default_value = plank_l
    br.inputs["Row Height"].default_value = plank_w
    br.inputs["Bias"].default_value = 0.0
    br.inputs["Color1"].default_value = (*c0, 1)
    br.inputs["Color2"].default_value = (*c1, 1)
    br.inputs["Mortar"].default_value = (0.08, 0.06, 0.045, 1)
    nt.links.new(v, br.inputs["Vector"])
    # venatura
    sep = nt.nodes.new("ShaderNodeMapping")
    sep.inputs["Scale"].default_value = (0.25, 9.0, 1.0)
    nt.links.new(v, sep.inputs["Vector"])
    nz = nt.nodes.new("ShaderNodeTexNoise")
    nz.inputs["Scale"].default_value = 3.0
    nz.inputs["Detail"].default_value = 8
    nz.inputs["Distortion"].default_value = 0.6
    nt.links.new(sep.outputs[0], nz.inputs["Vector"])
    grain = ramp(nt, nz.outputs["Fac"], (0.72, 0.72, 0.72), (1.12, 1.12, 1.12))
    mix = nt.nodes.new("ShaderNodeMix")
    mix.data_type = "RGBA"
    mix.blend_type = "MULTIPLY"
    mix.inputs["Factor"].default_value = 1.0
    nt.links.new(br.outputs["Color"], mix.inputs[6])
    nt.links.new(grain, mix.inputs[7])
    nt.links.new(mix.outputs[2], b.inputs["Base Color"])
    rr = ramp(nt, nz.outputs["Fac"], (rough - 0.08,) * 3, (rough + 0.08,) * 3)
    nt.links.new(rr, b.inputs["Roughness"])
    inv = nt.nodes.new("ShaderNodeMath")
    inv.operation = "SUBTRACT"
    inv.inputs[0].default_value = 1.0
    nt.links.new(br.outputs["Fac"], inv.inputs[1])
    add_bump(nt, b, inv.outputs[0], 0.35, 0.002)
    return m


def mat_tiles(name, c0, c1, size=(0.6, 0.6), rough=0.3, joint=(0.55, 0.55, 0.53), jw=0.003, bump=0.3):
    m, nt, b, _ = new_mat(name)
    v = coords(nt)
    br = nt.nodes.new("ShaderNodeTexBrick")
    br.offset = 0.0
    br.inputs["Scale"].default_value = 1.0
    br.inputs["Mortar Size"].default_value = jw
    br.inputs["Mortar Smooth"].default_value = 0.2
    br.inputs["Brick Width"].default_value = size[0]
    br.inputs["Row Height"].default_value = size[1]
    br.inputs["Color1"].default_value = (*c0, 1)
    br.inputs["Color2"].default_value = (*c1, 1)
    br.inputs["Mortar"].default_value = (*joint, 1)
    nt.links.new(v, br.inputs["Vector"])
    nz = nt.nodes.new("ShaderNodeTexNoise")
    nz.inputs["Scale"].default_value = 6.0
    nz.inputs["Detail"].default_value = 6
    nt.links.new(v, nz.inputs["Vector"])
    mix = nt.nodes.new("ShaderNodeMix")
    mix.data_type = "RGBA"
    mix.blend_type = "OVERLAY"
    mix.inputs["Factor"].default_value = 0.25
    nt.links.new(br.outputs["Color"], mix.inputs[6])
    nt.links.new(nz.outputs["Color"], mix.inputs[7])
    nt.links.new(mix.outputs[2], b.inputs["Base Color"])
    rr = nt.nodes.new("ShaderNodeMapRange")
    rr.inputs["To Min"].default_value = rough
    rr.inputs["To Max"].default_value = 0.9
    nt.links.new(br.outputs["Fac"], rr.inputs["Value"])
    nt.links.new(rr.outputs[0], b.inputs["Roughness"])
    inv = nt.nodes.new("ShaderNodeMath")
    inv.operation = "SUBTRACT"
    inv.inputs[0].default_value = 1.0
    nt.links.new(br.outputs["Fac"], inv.inputs[1])
    add_bump(nt, b, inv.outputs[0], bump, 0.002)
    return m


def mat_wood(name, c0, c1, rough=0.45, scale=(1.0, 1.0, 1.0)):
    """Legno per mobili: venatura 3D lungo l'asse X."""
    m, nt, b, _ = new_mat(name)
    v = coords(nt, scale=(0.6 * scale[0], 14.0 * scale[1], 14.0 * scale[2]))
    wv = nt.nodes.new("ShaderNodeTexWave")
    wv.wave_type = "BANDS"
    wv.bands_direction = "Y"
    wv.inputs["Scale"].default_value = 0.6
    wv.inputs["Distortion"].default_value = 6.0
    wv.inputs["Detail"].default_value = 3
    wv.inputs["Detail Scale"].default_value = 1.5
    nt.links.new(v, wv.inputs["Vector"])
    nt.links.new(ramp(nt, wv.outputs["Fac"], c0, c1), b.inputs["Base Color"])
    b.inputs["Roughness"].default_value = rough
    add_bump(nt, b, wv.outputs["Fac"], 0.04, 0.001)
    return m


def mat_fabric(name, color, sheen=0.6):
    m, nt, b, _ = new_mat(name)
    v = coords(nt)
    n = nt.nodes.new("ShaderNodeTexNoise")
    n.inputs["Scale"].default_value = 500.0
    n.inputs["Detail"].default_value = 2
    nt.links.new(v, n.inputs["Vector"])
    big = nt.nodes.new("ShaderNodeTexNoise")
    big.inputs["Scale"].default_value = 4.0
    nt.links.new(v, big.inputs["Vector"])
    c0 = tuple(c * 0.88 for c in color)
    nt.links.new(ramp(nt, big.outputs["Fac"], c0, color), b.inputs["Base Color"])
    b.inputs["Roughness"].default_value = 0.95
    inp(b, "Sheen Weight").default_value = sheen
    inp(b, "Sheen Roughness").default_value = 0.4
    add_bump(nt, b, n.outputs["Fac"], 0.25, 0.001)
    return m


def mat_stone(name, c0, c1, rough=0.25, scale=2.0, veins=True):
    m, nt, b, _ = new_mat(name)
    v = coords(nt)
    n = nt.nodes.new("ShaderNodeTexNoise")
    n.inputs["Scale"].default_value = scale
    n.inputs["Detail"].default_value = 12
    n.inputs["Distortion"].default_value = 2.5 if veins else 0.2
    nt.links.new(v, n.inputs["Vector"])
    nt.links.new(ramp(nt, n.outputs["Fac"], c0, c1), b.inputs["Base Color"])
    b.inputs["Roughness"].default_value = rough
    return m


def mat_glass(name, tint=(0.95, 0.98, 0.97), rough=0.0):
    """Vetro che lascia passare le ombre (evita interni scuri e rumorosi)."""
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    nt = m.node_tree
    nt.nodes.clear()
    out = nt.nodes.new("ShaderNodeOutputMaterial")
    glass = nt.nodes.new("ShaderNodeBsdfPrincipled")
    glass.inputs["Base Color"].default_value = (*tint, 1)
    glass.inputs["Roughness"].default_value = rough
    glass.inputs["IOR"].default_value = 1.45
    inp(glass, "Transmission Weight").default_value = 1.0
    tr = nt.nodes.new("ShaderNodeBsdfTransparent")
    tr.inputs["Color"].default_value = (*tint, 1)
    lp = nt.nodes.new("ShaderNodeLightPath")
    mx = nt.nodes.new("ShaderNodeMixShader")
    mx2 = nt.nodes.new("ShaderNodeMath")
    mx2.operation = "MAXIMUM"
    nt.links.new(lp.outputs["Is Shadow Ray"], mx2.inputs[0])
    nt.links.new(lp.outputs["Is Diffuse Ray"], mx2.inputs[1])
    nt.links.new(mx2.outputs[0], mx.inputs[0])
    nt.links.new(glass.outputs[0], mx.inputs[1])
    nt.links.new(tr.outputs[0], mx.inputs[2])
    nt.links.new(mx.outputs[0], out.inputs[0])
    return m


def mat_emit(name, color, strength):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    nt = m.node_tree
    nt.nodes.clear()
    out = nt.nodes.new("ShaderNodeOutputMaterial")
    e = nt.nodes.new("ShaderNodeEmission")
    e.inputs["Color"].default_value = (*color, 1)
    e.inputs["Strength"].default_value = strength
    nt.links.new(e.outputs[0], out.inputs[0])
    return m


def mat_grass(name):
    m, nt, b, _ = new_mat(name)
    v = coords(nt)
    n = nt.nodes.new("ShaderNodeTexNoise")
    n.inputs["Scale"].default_value = 0.6
    n.inputs["Detail"].default_value = 10
    nt.links.new(v, n.inputs["Vector"])
    vo = nt.nodes.new("ShaderNodeTexVoronoi")
    vo.inputs["Scale"].default_value = 260.0
    nt.links.new(v, vo.inputs["Vector"])
    col = ramp(nt, n.outputs["Fac"], (0.035, 0.075, 0.018), (0.11, 0.17, 0.04))
    mix = nt.nodes.new("ShaderNodeMix")
    mix.data_type = "RGBA"
    mix.blend_type = "MULTIPLY"
    mix.inputs["Factor"].default_value = 0.55
    nt.links.new(col, mix.inputs[6])
    nt.links.new(ramp(nt, vo.outputs["Distance"], (0.5, 0.5, 0.5), (1.3, 1.3, 1.3)), mix.inputs[7])
    nt.links.new(mix.outputs[2], b.inputs["Base Color"])
    b.inputs["Roughness"].default_value = 0.85
    add_bump(nt, b, vo.outputs["Distance"], 0.6, 0.01)
    return m


def mat_leaves(name, c0=(0.03, 0.07, 0.02), c1=(0.10, 0.16, 0.04)):
    """Foglia su quad: sagoma ellittica via UV, colore variabile per foglia, traslucenza."""
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    nt = m.node_tree
    nt.nodes.clear()
    out = nt.nodes.new("ShaderNodeOutputMaterial")
    b = nt.nodes.new("ShaderNodeBsdfPrincipled")
    at = nt.nodes.new("ShaderNodeAttribute")
    at.attribute_name = "leafvar"
    at.attribute_type = "GEOMETRY"
    col = ramp(nt, at.outputs["Fac"], c0, c1)
    nt.links.new(col, b.inputs["Base Color"])
    b.inputs["Roughness"].default_value = 0.5
    tl = nt.nodes.new("ShaderNodeBsdfTranslucent")
    nt.links.new(col, tl.inputs["Color"])
    mixl = nt.nodes.new("ShaderNodeMixShader")
    mixl.inputs[0].default_value = 0.3
    nt.links.new(b.outputs[0], mixl.inputs[1])
    nt.links.new(tl.outputs[0], mixl.inputs[2])
    # sagoma: ellisse dalle UV del quad (u lungo la foglia)
    uv = nt.nodes.new("ShaderNodeTexCoord")
    sep = nt.nodes.new("ShaderNodeSeparateXYZ")
    nt.links.new(uv.outputs["UV"], sep.inputs[0])
    def math_node(op, a_, b_=None, v=None):
        n = nt.nodes.new("ShaderNodeMath")
        n.operation = op
        if isinstance(a_, float):
            n.inputs[0].default_value = a_
        else:
            nt.links.new(a_, n.inputs[0])
        if b_ is not None:
            if isinstance(b_, float):
                n.inputs[1].default_value = b_
            else:
                nt.links.new(b_, n.inputs[1])
        return n.outputs[0]
    du = math_node("MULTIPLY", math_node("SUBTRACT", sep.outputs["X"], 0.5), 2.0)
    dv = math_node("MULTIPLY", math_node("SUBTRACT", sep.outputs["Y"], 0.5), 2.0)
    r2 = math_node("ADD", math_node("POWER", du, 2.0), math_node("POWER", dv, 2.0))
    mask = math_node("LESS_THAN", r2, 1.0)
    tr = nt.nodes.new("ShaderNodeBsdfTransparent")
    mx = nt.nodes.new("ShaderNodeMixShader")
    nt.links.new(mask, mx.inputs[0])
    nt.links.new(tr.outputs[0], mx.inputs[1])
    nt.links.new(mixl.outputs[0], mx.inputs[2])
    nt.links.new(mx.outputs[0], out.inputs[0])
    return m


def mat_grass_hair(name):
    m, nt, b, _ = new_mat(name)
    hi = nt.nodes.new("ShaderNodeHairInfo")
    col = ramp(nt, hi.outputs["Random"], (0.05, 0.09, 0.02), (0.16, 0.20, 0.05))
    dark = nt.nodes.new("ShaderNodeMix")
    dark.data_type = "RGBA"
    dark.blend_type = "MULTIPLY"
    nt.links.new(ramp(nt, hi.outputs["Intercept"], (0.35, 0.35, 0.35), (1, 1, 1)), dark.inputs[7])
    nt.links.new(col, dark.inputs[6])
    dark.inputs["Factor"].default_value = 1.0
    nt.links.new(dark.outputs[2], b.inputs["Base Color"])
    b.inputs["Roughness"].default_value = 0.55
    inp(b, "Subsurface Weight").default_value = 0.1
    return m


def mat_garage_door(name):
    m, nt, b, _ = new_mat(name)
    v = coords(nt)
    wv = nt.nodes.new("ShaderNodeTexWave")
    wv.wave_type = "BANDS"
    wv.bands_direction = "Z"
    wv.wave_profile = "SAW"
    wv.inputs["Scale"].default_value = 1.1
    nt.links.new(v, wv.inputs["Vector"])
    b.inputs["Base Color"].default_value = (0.045, 0.047, 0.05, 1)
    b.inputs["Roughness"].default_value = 0.45
    b.inputs["Metallic"].default_value = 0.4
    add_bump(nt, b, wv.outputs["Fac"], 0.25, 0.01)
    return m


def build_materials():
    M = {}
    M["plaster_ext"] = mat_plaster("Intonaco esterno", (0.80, 0.78, 0.74), (0.86, 0.84, 0.80), 0.12)
    M["roof"] = mat_plaster("Copertura", (0.80, 0.78, 0.74), (0.86, 0.84, 0.80), 0.12)
    M["plaster_int"] = mat_plaster("Intonaco interno", (0.85, 0.84, 0.81), (0.88, 0.87, 0.84), 0.03)
    M["ceiling"] = mat_plaster("Soffitto", (0.86, 0.86, 0.85), (0.88, 0.88, 0.87), 0.01)
    M["oak"] = mat_planks("Parquet rovere", (0.42, 0.29, 0.17), (0.50, 0.36, 0.22))
    M["oak_y"] = mat_planks("Parquet rovere Y", (0.42, 0.29, 0.17), (0.50, 0.36, 0.22), horiz_axis="y")
    M["porcelain"] = mat_tiles("Gres 120x60", (0.62, 0.60, 0.56), (0.66, 0.63, 0.59), size=(1.2, 0.6), rough=0.22, jw=0.0015, bump=0.15)
    M["bath_tile"] = mat_tiles("Gres bagno", (0.46, 0.46, 0.45), (0.50, 0.50, 0.49), size=(0.6, 0.6), rough=0.25)
    M["concrete"] = mat_stone("Cemento", (0.30, 0.30, 0.29), (0.38, 0.38, 0.37), rough=0.8, scale=4, veins=False)
    M["threshold"] = mat_stone("Soglia pietra", (0.55, 0.53, 0.50), (0.62, 0.60, 0.57), rough=0.4, scale=8, veins=False)
    M["plinth"] = mat_stone("Zoccolo pietra", (0.17, 0.17, 0.17), (0.23, 0.23, 0.22), rough=0.6, scale=6, veins=False)
    M["paving"] = mat_tiles("Pavimentazione esterna", (0.42, 0.40, 0.36), (0.48, 0.45, 0.40), size=(0.9, 0.45), rough=0.75, joint=(0.25, 0.25, 0.24), jw=0.004, bump=0.6)
    M["drive"] = mat_tiles("Masselli vialetto", (0.25, 0.23, 0.20), (0.33, 0.30, 0.26), size=(0.3, 0.2), rough=0.85, joint=(0.18, 0.17, 0.16), jw=0.008, bump=0.8)
    M["grass"] = mat_grass("Prato")
    M["leaves"] = mat_leaves("Foglie")
    M["leaves2"] = mat_leaves("Siepe", (0.02, 0.05, 0.015), (0.06, 0.11, 0.03))
    M["hedge"] = mat_plain("Siepe fitta", (0.025, 0.05, 0.015), rough=0.8)
    M["grass_hair"] = mat_grass_hair("Erba")
    M["bark"] = mat_stone("Corteccia", (0.10, 0.08, 0.06), (0.20, 0.16, 0.12), rough=0.9, scale=30)
    M["frame"] = mat_plain("Alluminio antracite", (0.035, 0.037, 0.04), rough=0.4, metal=0.3)
    M["glass"] = mat_glass("Vetro")
    M["garage"] = mat_garage_door("Portone garage")
    M["door_ext"] = mat_wood("Portoncino noce", (0.12, 0.07, 0.04), (0.20, 0.12, 0.07), rough=0.4)
    M["walnut"] = mat_wood("Noce", (0.15, 0.09, 0.05), (0.25, 0.16, 0.09), rough=0.4)
    M["oak_furn"] = mat_wood("Rovere mobili", (0.40, 0.27, 0.15), (0.55, 0.39, 0.23), rough=0.45)
    M["lacquer"] = mat_plain("Laccato greige", (0.62, 0.59, 0.54), rough=0.35)
    M["lacquer_w"] = mat_plain("Laccato bianco", (0.80, 0.79, 0.76), rough=0.3)
    M["top_stone"] = mat_stone("Top gres scuro", (0.03, 0.03, 0.03), (0.09, 0.09, 0.09), rough=0.28, scale=3)
    M["ceramic"] = mat_plain("Ceramica", (0.85, 0.85, 0.84), rough=0.08, coat=0.5)
    M["steel"] = mat_plain("Acciaio", (0.75, 0.75, 0.75), rough=0.25, metal=1.0)
    M["black_glass"] = mat_plain("Vetro nero", (0.005, 0.005, 0.005), rough=0.05)
    M["black_matte"] = mat_plain("Nero opaco", (0.02, 0.02, 0.02), rough=0.6)
    M["mirror"] = mat_plain("Specchio", (0.9, 0.9, 0.9), rough=0.01, metal=1.0)
    M["sofa"] = mat_fabric("Tessuto divano", (0.46, 0.40, 0.33))
    M["sofa2"] = mat_fabric("Tessuto cuscini", (0.34, 0.29, 0.24))
    M["linen"] = mat_fabric("Lino letto", (0.72, 0.70, 0.66), 0.3)
    M["rug"] = mat_fabric("Tappeto", (0.55, 0.52, 0.47), 0.8)
    M["chair"] = mat_fabric("Tessuto sedie", (0.25, 0.22, 0.19))
    M["pot"] = mat_plain("Vaso", (0.30, 0.18, 0.12), rough=0.7)
    M["lamp_shade"] = mat_plain("Paralume", (0.03, 0.03, 0.03), rough=0.35, metal=0.5)
    M["lamp_glow"] = mat_emit("Luce calda", (1.0, 0.78, 0.55), 12.0)
    M["led"] = mat_emit("Striscia LED", (1.0, 0.8, 0.6), 6.0)
    M["tv"] = M["black_glass"]
    M["car"] = mat_plain("Carrozzeria", (0.12, 0.16, 0.22), rough=0.15, metal=0.8, coat=1.0)
    return M

# ---------------------------------------------------------------- muri

TYPES = {"w": (0.9, 2.3), "w1": (1.0, 2.4), "wk": (1.1, 2.0), "wc": (1.10, 2.00),
         "d": (0, 2.1), "g": (0, 2.4), "p": (0, 2.3), "sc": (0, 2.1)}

# [x1,y1,x2,y2,spessore,[[da,a,tipo],...], lato esterno (per perimetro) , estendi estremi]
WALLS = [
    # perimetro
    [0.125, 0.125, 6.975, 0.125, EXT, [[1.65, 2.55, "w1"], [3.05, 3.95, "d"], [5.00, 6.30, "w"]], "N"],
    [6.975, 0.125, 6.975, 5.625, EXT, [], "E"],
    [7.10, 5.625, 8.575, 5.625, EXT, [[7.30, 8.20, "d"]], "N"],
    [8.575, 5.625, 8.575, 18.625, EXT, [], "E"],
    [4.125, 18.625, 8.575, 18.625, EXT, [[5.60, 7.10, "wc"]], "S"],
    [4.125, 11.275, 4.125, 18.625, EXT, [[11.30, 12.30, "w"], [12.60, 13.60, "d"], [16.70, 17.90, "p"]], "W"],
    [4.375, 6.775, 4.375, 11.025, EXT, [], None],
    [0.125, 6.775, 4.375, 6.775, EXT, [[3.05, 3.95, "d"]], None],
    [-3.675, 11.025, 4.375, 11.025, EXT, [[-3.175, -0.375, "g"], [1.40, 3.20, "w"]], "S"],
    [0.125, 0.125, 0.125, 11.025, EXT, [[2.30, 3.10, "w1"], [4.00, 5.20, "w"]], "W"],
    # garage e w.c. del garage
    [-3.675, 5.375, 0.0, 5.375, EXT, [[-3.40, -2.50, "wk"], [-1.65, -0.75, "d"]], "N"],
    [-3.675, 5.375, -3.675, 11.025, EXT, [], "W"],
    [-1.80, 5.50, -1.80, 6.75, INT, [], None],
    [-3.55, 6.75, -1.80, 6.75, INT, [[-3.40, -2.50, "d"]], None],
    # blocco notte
    [2.90, 0.25, 2.90, 6.65, INT, [[0.65, 1.55, "d"], [2.55, 3.45, "d"], [5.65, 6.55, "d"]], None],
    [0.25, 1.80, 2.85, 1.80, INT, [], None],
    [0.25, 3.60, 2.85, 3.60, INT, [], None],
    [2.95, 2.40, 4.05, 2.40, INT, [[3.05, 3.95, "d"]], None],
    [4.10, 0.25, 4.10, 5.65, INT, [[3.35, 4.25, "d"]], None],
    [4.05, 4.60, 6.85, 4.60, INT, [], None],
    [5.50, 4.65, 5.50, 5.65, INT, [], None],
    # bagno grande e lavanderia
    [5.65, 5.65, 5.65, 9.40, INT, [[6.20, 7.10, "d"], [8.30, 9.20, "d"]], None],
    [5.60, 5.70, 7.10, 5.70, INT, [[5.70, 6.85, "d"]], None],
    [5.70, 7.60, 8.45, 7.60, INT, [[7.70, 8.40, "d"]], None],
    [5.60, 9.40, 8.45, 9.40, INT, [], None],
    [4.50, 9.40, 5.60, 9.40, INT, [[4.50, 5.60, "sc"]], None],
    # cucina / soggiorno: apertura netta 1,80 m (estremi non estesi)
    [4.25, 14.50, 5.475, 14.50, INT, [], None, False],
    [7.275, 14.50, 8.45, 14.50, INT, [], None, False],
]

PORTALS = []   # aperture esterne per le luci portale: (cx, cy, larghezza, z0, z1, orizzontale, lato)


def wall_piece(mat, horiz, fixed, a, b, t, z0, z1, off=0.0):
    hw = t / 2
    if horiz:
        box(mat, a, b, fixed - hw + off, fixed + hw + off, z0, z1)
    else:
        box(mat, fixed - hw + off, fixed + hw + off, a, b, z0, z1)


def window(horiz, fixed, a, b, t, z0, z1, side, kind):
    """Telaio in alluminio antracite + vetro + davanzale esterno."""
    fw = 0.06          # larghezza profilo
    ft = 0.07          # profondità telaio
    sgn = {"N": -1, "S": 1, "E": 1, "W": -1}.get(side, 0)
    off = sgn * (t / 2 - 0.08) if side else 0.0      # telaio verso l'esterno
    def fbox(m, s0, s1, zz0, zz1, th, o=0.0):
        wall_piece(m, horiz, fixed, s0, s1, th, zz0, zz1, off + o)
    # telaio perimetrale
    fbox("frame", a, b, z1 - fw, z1, ft)
    fbox("frame", a, b, z0, z0 + fw, ft)
    fbox("frame", a, a + fw, z0, z1, ft)
    fbox("frame", b - fw, b, z0, z1, ft)
    n = 2 if (b - a) > 1.05 else 1
    if kind == "d":
        n = 0
    for i in range(1, n):
        m = a + (b - a) * i / n
        fbox("frame", m - fw * 0.8, m + fw * 0.8, z0, z1, ft)
    fbox("glass", a + fw, b - fw, z0 + fw, z1 - fw, 0.024)
    # davanzale / soglia in pietra
    if side:
        if z0 > 0.01:
            ext = t / 2 + 0.04
            if horiz:
                y0, y1 = (fixed + off - 0.03, fixed + sgn * ext) if sgn > 0 else (fixed - ext, fixed + off + 0.03)
                box("threshold", a - 0.03, b + 0.03, y0, y1, z0 - 0.04, z0)
            else:
                x0, x1 = (fixed + off - 0.03, fixed + sgn * ext) if sgn > 0 else (fixed - ext, fixed + off + 0.03)
                box("threshold", x0, x1, a - 0.03, b + 0.03, z0 - 0.04, z0)
        PORTALS.append((horiz, fixed, a, b, z0, z1, side, t))


def add_walls():
    for w in WALLS:
        x1, y1, x2, y2, t, ops, side = w[:7]
        extend = w[7] if len(w) > 7 else True
        horiz = (y1 == y2)
        fixed = y1 if horiz else x1
        a0 = min(x1, x2) if horiz else min(y1, y2)
        a1 = max(x1, x2) if horiz else max(y1, y2)
        e = t / 2 - 0.0015 if extend else 0.0
        mat = "plaster_ext" if t == EXT else "plaster_int"
        cur = a0 - e
        for a, b, k in sorted(ops):
            sill, top = TYPES[k]
            wall_piece(mat, horiz, fixed, cur, a, t, 0, H)
            if sill > 0:
                wall_piece(mat, horiz, fixed, a, b, t, 0, sill)
            wall_piece(mat, horiz, fixed, a, b, t, top, H)
            if k[0] == "w" or k == "p":
                window(horiz, fixed, a, b, t, sill, top, side, k)
            elif k == "g":
                wall_piece("garage", horiz, fixed, a, b, t * 0.3, 0, top, t * 0.25)
                PORTALS.append((horiz, fixed, a, b, 0, top, None, t))
            elif k == "d" and side:
                door_leaf(horiz, fixed, a, b, t, top, side)
            cur = b
        wall_piece(mat, horiz, fixed, cur, a1 + e, t, 0, H)


def door_leaf(horiz, fixed, a, b, t, top, side):
    """Portoncino esterno: anta in noce con telaio antracite, chiusa."""
    sgn = {"N": -1, "S": 1, "E": 1, "W": -1}[side]
    off = sgn * (t / 2 - 0.1)
    wall_piece("frame", horiz, fixed, a, a + 0.05, 0.08, 0, top, off)
    wall_piece("frame", horiz, fixed, b - 0.05, b, 0.08, 0, top, off)
    wall_piece("frame", horiz, fixed, a, b, 0.08, top - 0.05, top, off)
    wall_piece("door_ext", horiz, fixed, a + 0.05, b - 0.05, 0.06, 0.0, top - 0.05, off)
    # maniglione verticale in acciaio
    s = a + 0.14 if (b - a) > 0.95 else b - 0.14
    wall_piece("steel", horiz, fixed, s - 0.012, s + 0.012, 0.05, 0.6, 1.8, off + sgn * 0.08)

# ---------------------------------------------------------------- pavimenti, solai, esterno

FOOTPRINT = [  # rettangoli non sovrapposti che coprono la casa (per solaio e basamento)
    (0.0, 7.1, 0.0, 5.5),
    (0.0, 8.7, 5.5, 6.9),
    (0.0, 8.7, 6.9, 11.15),
    (4.0, 8.7, 11.15, 18.75),
    (-3.8, 0.0, 5.25, 11.15),
]


def add_floors():
    def rect(m, x1, y1, x2, y2):
        box(m, x1, x2, y1, y2, -0.015, 0.0)
    rect("oak", 0.25, 0.25, 2.85, 1.75)          # ripostiglio
    rect("bath_tile", 0.25, 1.85, 2.85, 3.55)    # bagno piccolo
    rect("oak", 0.25, 3.65, 2.85, 6.65)          # studio
    rect("oak_y", 2.95, 0.25, 4.05, 2.35)        # disimpegno
    rect("oak_y", 2.95, 2.45, 4.05, 5.65)        # corridoio
    rect("oak", 2.95, 5.65, 5.60, 6.65)
    rect("oak_y", 4.50, 6.65, 5.60, 9.45)
    rect("bath_tile", 4.15, 4.65, 5.45, 5.65)    # ripostiglio
    rect("bath_tile", 5.55, 4.65, 6.85, 5.65)    # doccia
    rect("oak", 4.15, 0.25, 6.85, 4.55)          # cameretta
    rect("bath_tile", 5.70, 5.75, 8.45, 7.55)    # bagno grande
    rect("bath_tile", 5.70, 7.65, 8.45, 9.35)    # lavanderia
    rect("oak", 0.25, 6.90, 4.25, 10.90)         # camera
    rect("concrete", -3.55, 6.80, 0.0, 10.90)    # garage
    rect("concrete", -1.75, 5.50, 0.0, 6.80)
    rect("bath_tile", -3.55, 5.50, -1.85, 6.70)  # w.c. garage
    rect("oak_y", 4.50, 9.45, 8.45, 11.15)       # soggiorno
    rect("oak_y", 4.25, 11.15, 8.45, 14.50)
    rect("porcelain", 4.25, 14.50, 8.45, 18.50)  # cucina
    # basamento (soglie tra i vani) e zoccolo esterno in pietra scura
    for x0, x1, y0, y1 in FOOTPRINT:
        box("threshold", x0, x1, y0, y1, -0.03, -0.001)
        box("plinth", x0, x1, y0, y1, GROUND - 0.02, -0.03)
    # soffitto + copertura piana con cornice sottile
    for x0, x1, y0, y1 in FOOTPRINT:
        box("ceiling", x0, x1, y0, y1, H, H + 0.02)
    o = 0.35
    roof = [(-0.0 - o, 7.1 + o, 0.0 - o, 5.5), (-0.0 - o, 8.7 + o, 5.5, 11.15),
            (4.0 - o, 8.7 + o, 11.15, 18.75 + o), (-3.8 - o, 0.0 - o, 5.25 - o, 11.15 + o)]
    for x0, x1, y0, y1 in roof:
        box("roof", x0, x1, y0, y1, H + 0.02, H + 0.30)
    # risvolto della cornice sul fronte sud del blocco notte (tra garage e cucina)
    box("roof", -0.35, 4.0 - o, 11.15, 11.15 + o, H + 0.02, H + 0.30)
    box("roof", -3.8 - o, -0.35, 5.25 - o, 5.25, H + 0.02, H + 0.30)


def _leaf_tex(seed):
    tex = bpy.data.textures.get("foliage")
    if tex is None:
        tex = bpy.data.textures.new("foliage", "VORONOI")
        tex.noise_scale = 0.06
        tex.distance_metric = "DISTANCE"
    return tex


def blob(mat, center, radius, n=0, seed=0, squash=1.0, leaf=0.07, density=2600):
    """Chioma fatta di singole foglie (quad con sagoma) distribuite in un ellissoide."""
    import numpy as np
    rng = np.random.default_rng(seed)
    count = int(density * 4 * math.pi * radius ** 2 * (0.4 + 0.6 * squash) * (leaf / 0.07) ** -2 * 0.25)
    # punti nell'ellissoide, addensati verso la superficie
    d = rng.normal(size=(count, 3))
    d /= np.linalg.norm(d, axis=1)[:, None]
    rad = rng.uniform(0, 1, count) ** 0.25
    p = d * rad[:, None] * radius
    p[:, 2] *= squash
    # schiacciamento della parte bassa (chioma più piatta sotto)
    p[:, 2] = np.where(p[:, 2] < 0, p[:, 2] * 0.7, p[:, 2])
    # orientamento casuale, tendenzialmente verso l'esterno e in su
    nrm = d + rng.normal(scale=0.8, size=(count, 3)) + np.array([0, 0, 0.6])
    nrm /= np.linalg.norm(nrm, axis=1)[:, None]
    ref = np.where(np.abs(nrm[:, 2:3]) < 0.9, np.array([[0, 0, 1.0]]), np.array([[1.0, 0, 0]]))
    u = np.cross(nrm, ref)
    u /= np.linalg.norm(u, axis=1)[:, None]
    v = np.cross(nrm, u)
    ang = rng.uniform(0, 2 * math.pi, count)[:, None]
    uu = u * np.cos(ang) + v * np.sin(ang)
    vv = np.cross(nrm, uu)
    s_ = leaf * rng.uniform(0.7, 1.3, count)[:, None]
    L, W = uu * s_, vv * s_ * 0.45
    c = p + np.array([center[0], -center[1], center[2]])
    verts = np.stack([c - L - W, c + L - W, c + L + W, c - L + W], axis=1).reshape(-1, 3)
    me = bpy.data.meshes.new(f"chioma{seed}")
    me.vertices.add(len(verts))
    me.vertices.foreach_set("co", verts.astype(np.float32).ravel())
    nl = count
    me.loops.add(nl * 4)
    me.loops.foreach_set("vertex_index", np.arange(nl * 4, dtype=np.int32))
    me.polygons.add(nl)
    me.polygons.foreach_set("loop_start", np.arange(0, nl * 4, 4, dtype=np.int32))
    me.update()
    uvl = me.uv_layers.new(name="UVMap")
    uvl.data.foreach_set("uv", np.tile(np.array([0, 0, 1, 0, 1, 1, 0, 1], dtype=np.float32), nl))
    at = me.attributes.new("leafvar", "FLOAT", "FACE")
    at.data.foreach_set("value", rng.uniform(0, 1, nl).astype(np.float32))
    ob = bpy.data.objects.new(f"chioma{seed}", me)
    link(ob)
    ob.data.materials.append(bpy.data.materials[mat])
    # nucleo scuro per non vedere attraverso la chioma
    bpy.ops.mesh.primitive_uv_sphere_add(radius=radius * 0.72, location=(c[0][0] if False else center[0], -center[1], center[2]))
    core = bpy.context.active_object
    core.scale.z = squash
    core.data.materials.append(bpy.data.materials["Siepe fitta"])
    return ob


def tree(x, y, h=4.2, r=1.4, seed=1, density=2600):
    import random
    rnd = random.Random(seed)
    zc = GROUND + h - r * 0.8
    bpy.ops.mesh.primitive_cone_add(vertices=12, radius1=0.14, radius2=0.06, depth=zc - GROUND,
                                    location=(x, -y, GROUND + (zc - GROUND) / 2))
    bpy.context.active_object.data.materials.append(bpy.data.materials["Corteccia"])
    for i in range(5):   # rami principali
        a = rnd.uniform(0, 2 * math.pi)
        L = r * 0.8
        bpy.ops.mesh.primitive_cone_add(vertices=8, radius1=0.05, radius2=0.015, depth=L,
                                        location=(x + math.cos(a) * L * 0.35, -y + math.sin(a) * L * 0.35, zc - 0.2 + L * 0.3),
                                        rotation=(0, math.radians(50), a))
        ob = bpy.context.active_object
        ob.rotation_mode = "XYZ"
        ob.rotation_euler = (math.radians(50) * math.sin(a), -math.radians(50) * math.cos(a), 0)
        ob.data.materials.append(bpy.data.materials["Corteccia"])
    for k in range(4):   # chioma irregolare: più masse sovrapposte
        a = rnd.uniform(0, 2 * math.pi)
        off = r * 0.45 if k else 0.0
        blob("Foglie", (x + math.cos(a) * off, y + math.sin(a) * off, zc + rnd.uniform(-0.3, 0.35) * r),
             r * (0.75 if k == 0 else rnd.uniform(0.5, 0.65)), seed=seed * 10 + k, squash=0.8, leaf=0.075, density=density)


def hedge(x0, x1, y0, y1, h, seed=0):
    """Siepe squadrata: parallelepipedo suddiviso e deformato."""
    bpy.ops.mesh.primitive_cube_add(size=1, location=((x0 + x1) / 2, -(y0 + y1) / 2, GROUND + h / 2))
    ob = bpy.context.active_object
    ob.scale = (x1 - x0, y1 - y0, h)
    bpy.ops.object.transform_apply(scale=True)
    bv = ob.modifiers.new("bevel", "BEVEL")
    bv.width = 0.15
    bv.segments = 3
    rm = ob.modifiers.new("remesh", "REMESH")
    rm.mode = "VOXEL"
    rm.voxel_size = 0.06
    d = ob.modifiers.new("disp", "DISPLACE")
    d.texture = _leaf_tex(seed)
    d.texture_coords = "GLOBAL"
    d.strength = 0.06
    ob.data.materials.append(bpy.data.materials["Siepe fitta"])
    bpy.ops.object.shade_smooth()
    leaves_on_box(x0, x1, y0, y1, h, seed)


def leaves_on_box(x0, x1, y0, y1, h, seed):
    """Foglie sparse sulla superficie della siepe."""
    import numpy as np
    rng = np.random.default_rng(100 + seed)
    area = 2 * (x1 - x0) * h + 2 * (y1 - y0) * h + (x1 - x0) * (y1 - y0)
    n = int(area * 1400)
    face = rng.choice(5, n, p=np.array([(x1 - x0) * h, (x1 - x0) * h, (y1 - y0) * h, (y1 - y0) * h, (x1 - x0) * (y1 - y0)]) / area)
    X = rng.uniform(x0, x1, n); Y = rng.uniform(y0, y1, n); Z = rng.uniform(GROUND, GROUND + h, n)
    Y = np.where(face == 0, y0, np.where(face == 1, y1, Y))
    X = np.where(face == 2, x0, np.where(face == 3, x1, X))
    Z = np.where(face == 4, GROUND + h, Z)
    pts = np.stack([X, Y, Z], 1) + rng.normal(scale=0.05, size=(n, 3))
    _leaf_mesh(pts, 0.055, rng, "Siepe", f"siepe{seed}")


def _leaf_mesh(pts, leaf, rng, mat, name):
    import numpy as np
    count = len(pts)
    nrm = rng.normal(size=(count, 3)) + np.array([0, 0, 0.5])
    nrm /= np.linalg.norm(nrm, axis=1)[:, None]
    ref = np.where(np.abs(nrm[:, 2:3]) < 0.9, np.array([[0, 0, 1.0]]), np.array([[1.0, 0, 0]]))
    u = np.cross(nrm, ref); u /= np.linalg.norm(u, axis=1)[:, None]
    v = np.cross(nrm, u)
    s_ = leaf * rng.uniform(0.7, 1.3, count)[:, None]
    L, W = u * s_, v * s_ * 0.45
    c = pts * np.array([1, -1, 1])
    verts = np.stack([c - L - W, c + L - W, c + L + W, c - L + W], axis=1).reshape(-1, 3)
    me = bpy.data.meshes.new(name)
    me.vertices.add(len(verts))
    me.vertices.foreach_set("co", verts.astype(np.float32).ravel())
    me.loops.add(count * 4)
    me.loops.foreach_set("vertex_index", np.arange(count * 4, dtype=np.int32))
    me.polygons.add(count)
    me.polygons.foreach_set("loop_start", np.arange(0, count * 4, 4, dtype=np.int32))
    me.update()
    uvl = me.uv_layers.new(name="UVMap")
    uvl.data.foreach_set("uv", np.tile(np.array([0, 0, 1, 0, 1, 1, 0, 1], dtype=np.float32), count))
    at = me.attributes.new("leafvar", "FLOAT", "FACE")
    at.data.foreach_set("value", rng.uniform(0, 1, count).astype(np.float32))
    ob = bpy.data.objects.new(name, me)
    link(ob)
    ob.data.materials.append(bpy.data.materials[mat])


def lawn(rects, per_m2=3200, length=0.042):
    verts, faces = [], []
    area = 0.0
    for x0, x1, y0, y1 in rects:
        o = len(verts)
        verts += [(x0, -y1, GROUND - 0.02), (x1, -y1, GROUND - 0.02), (x1, -y0, GROUND - 0.02), (x0, -y0, GROUND - 0.02)]
        faces.append((o, o + 1, o + 2, o + 3))
        area += (x1 - x0) * (y1 - y0)
    me = bpy.data.meshes.new("prato")
    me.from_pydata(verts, [], faces)
    ob = link(bpy.data.objects.new("prato", me))
    ob.data.materials.append(bpy.data.materials["Prato"])
    ob.data.materials.append(bpy.data.materials["Erba"])
    ob.modifiers.new("erba", "PARTICLE_SYSTEM")
    ps = ob.particle_systems[0]
    ps.seed = 3
    st = ps.settings
    st.type = "HAIR"
    st.count = int(area * per_m2)
    st.hair_length = length
    st.emit_from = "FACE"
    st.distribution = "RAND"
    st.use_advanced_hair = True
    st.normal_factor = length
    st.factor_random = length * 0.35
    st.brownian_factor = 0.0
    st.material_slot = "Erba"
    st.display_step = 2
    st.render_step = 2
    st.root_radius = 1.0
    st.tip_radius = 0.0
    st.radius_scale = 0.0035
    st.use_close_tip = True


# Lotto (misure date da Alex): 25 x 12,5 m. Filo interno dei muri di confine:
#   sud y = 21,20 (cucina -> muro 2,45 m; garage -> muro 10 m)
#   nord y = -3,10 (ripostiglio -> muro 3,2 m; porta finestra bagno grande -> muro 8,6 m)
#   ovest x = -3,80 ed est x = 8,70: la casa è larga 12,5 m e tocca i due confini laterali
#   cancelletto pedonale a 7,2 m dal muro sinistro (x = 3,40)
LOT = (-3.8, 8.7, -3.1, 21.2)
WALL_T = 0.25


def add_exterior(M):
    x0, x1, y0, y1 = LOT
    t = WALL_T
    # terreno oltre il lotto
    box("grass", -60, 70, -60, 80, GROUND - 0.3, GROUND - 0.02)
    # prato del lotto (con fili d'erba)
    lawn([(-0.3, 2.9, 12.35, y1), (2.9, 3.4, 19.85, y1), (4.4, x1, 19.85, y1),
          (x0, x1, y0, -1.0), (x0, 2.8, -1.0, 0.0), (4.2, x1, -1.0, 0.0),
          (x0, 0.0, 0.0, 4.25), (x0, -1.9, 4.25, 5.25), (-0.5, 0.0, 4.25, 5.25),
          (7.1, x1, 0.0, 4.5)])
    # marciapiede perimetrale e piazzale
    box("paving", -3.8, 4.0, 11.15, 12.35, GROUND - 0.02, GROUND + 0.02)      # fronte garage/camera
    box("paving", 2.9, 4.0, 12.35, 18.75, GROUND - 0.02, GROUND + 0.02)       # lungo la facciata d'ingresso
    box("paving", 2.9, 8.7, 18.75, 19.85, GROUND - 0.02, GROUND + 0.02)       # fronte cucina
    box("paving", 3.4, 4.4, 19.85, y1, GROUND - 0.02, GROUND + 0.02)          # dal cancelletto
    box("drive", -3.8, -0.3, 12.35, y1, GROUND - 0.02, GROUND + 0.015)        # passo carrabile
    box("paving", 2.8, 4.2, -1.0, 0.0, GROUND - 0.02, GROUND + 0.02)          # uscita sul retro (disimpegno)
    box("paving", 7.1, 8.7, 4.5, 5.5, GROUND - 0.02, GROUND + 0.02)           # porta finestra bagno grande
    box("paving", -1.9, -0.5, 4.25, 5.25, GROUND - 0.02, GROUND + 0.02)       # porta posteriore garage
    # muri di confine: su strada (sud) h 1,20 con cancelli, laterali e retro h 1,80
    hs, hb = 1.2, 1.8
    gates = ((-3.6, -0.6), (3.4, 4.4))            # carrabile davanti al garage, pedonale a 7,2 m dal muro sinistro
    segs = [(x0 - t, gates[0][0]), (gates[0][1], gates[1][0]), (gates[1][1], x1 + t)]
    for a_, b_ in segs:
        box("plaster_ext", a_, b_, y1, y1 + t, GROUND, GROUND + hs)
        box("threshold", a_ - 0.02, b_ + 0.02, y1 - 0.02, y1 + t + 0.02, GROUND + hs, GROUND + hs + 0.04)
    box("plaster_ext", x0 - t, x1 + t, y0 - t, y0, GROUND, GROUND + hb)                   # retro
    box("plaster_ext", x0 - t, x0, y0, y1, GROUND, GROUND + hb)                           # sinistro (ovest)
    box("plaster_ext", x1, x1 + t, y0, y1, GROUND, GROUND + hb)                           # destro (est)
    for xa, xb in ((x0 - t, x0), (x1, x1 + t)):
        box("threshold", xa - 0.02, xb + 0.02, y0 - t, y1, GROUND + hb, GROUND + hb + 0.04)
    box("threshold", x0 - t, x1 + t, y0 - t - 0.02, y0 + 0.02, GROUND + hb, GROUND + hb + 0.04)
    # cancelli a doghe verticali
    for gx0, gx1 in gates:
        yc = y1 + t / 2
        box("frame", gx0, gx1, yc - 0.04, yc + 0.04, GROUND + 0.05, GROUND + 0.1)
        box("frame", gx0, gx1, yc - 0.04, yc + 0.04, GROUND + 1.35, GROUND + 1.4)
        x = gx0 + 0.02
        while x < gx1 - 0.05:
            box("frame", x, x + 0.04, yc - 0.03, yc + 0.03, GROUND + 0.05, GROUND + 1.4)
            x += 0.09
    # arbusti: sotto la finestra della camera, davanti alla cucina, sul retro
    for i, xx in enumerate([0.4, 1.3, 2.2]):
        blob("Siepe", (xx, 12.9, GROUND + 0.3), 0.45, seed=20 + i, squash=0.7, leaf=0.05)
    for i, xx in enumerate([5.2, 6.5, 7.8]):
        blob("Siepe", (xx, 20.6, GROUND + 0.35), 0.5, seed=30 + i, squash=0.7, leaf=0.05)
    for i, xx in enumerate([-3.0, -1.0, 1.0, 5.5, 7.6]):
        blob("Siepe", (xx, -2.5, GROUND + 0.4), 0.55, seed=50 + i, squash=0.8, leaf=0.05)
    # alberi (uno al centro del giardino come nella piantina, uno sul retro) + contesto fuori lotto
    tree(0.2, 19.9, h=4.6, r=1.5, seed=3)
    tree(-2.2, 1.6, h=4.2, r=1.4, seed=4)
    tree(-2.6, 25.5, h=5.0, r=1.8, seed=5)
    tree(12.5, 8.0, h=6.0, r=2.4, seed=7)
    tree(-8.0, 3.0, h=6.5, r=2.6, seed=9)
    for i, (tx, ty, th, tr) in enumerate(((-9.5, 14.0, 7.0, 2.8), (15.0, 18.0, 6.5, 2.5), (18.0, -2.0, 8.0, 3.0),
                                          (-6.0, -9.0, 7.5, 3.0), (6.0, -10.0, 7.0, 2.8), (-14.0, 26.0, 6.0, 2.6))):
        tree(tx, ty, th, tr, seed=40 + i, density=900)
    # applique a parete accanto al portoncino
    box("frame", 3.93, 4.0, 12.35, 12.47, 1.95, 2.25)
    box("led", 3.925, 3.93, 12.37, 12.45, 1.97, 1.99)
    # lampioncini da giardino lungo il marciapiede
    for yy in (16.6, 19.2):
        box("frame", 2.95, 3.07, yy - 0.06, yy + 0.06, GROUND, GROUND + 0.55)
        box("led", 2.96, 2.98, yy - 0.04, yy + 0.04, GROUND + 0.42, GROUND + 0.50)

# ---------------------------------------------------------------- arredi

def cabinets(x0, x1, y0, y1, z0, z1, axis, n, mat="lacquer", body_mat="lacquer", plinth=0.1):
    """Mobili a moduli con fughe da 3 mm e zoccolo arretrato."""
    if plinth:
        if axis == "y":
            box("black_matte", x0 + 0.05, x1, y0, y1, z0, z0 + plinth)
        else:
            box("black_matte", x0, x1, y0, y1 - 0.05, z0, z0 + plinth)
    zz0 = z0 + plinth
    if axis == "y":
        L = y1 - y0
        for i in range(n):
            a = y0 + L * i / n + 0.0015
            b = y0 + L * (i + 1) / n - 0.0015
            box(mat, x0, x1, a, b, zz0, z1, bevel=0.002)
    else:
        L = x1 - x0
        for i in range(n):
            a = x0 + L * i / n + 0.0015
            b = x0 + L * (i + 1) / n - 0.0015
            box(mat, a, b, y0, y1, zz0, z1, bevel=0.002)


def chair(x, y, facing):
    """Sedia imbottita con gambe in legno; facing = N/S/E/W della piantina (verso dove guarda chi siede)."""
    w = 0.44
    for dx in (0.03, w - 0.06):
        for dy in (0.03, w - 0.06):
            box("walnut", x + dx, x + dx + 0.03, y + dy, y + dy + 0.03, 0, 0.44)
    box("chair", x, x + w, y, y + w, 0.44, 0.50, bevel=0.015)
    if facing == "S":
        box("chair", x + 0.01, x + w - 0.01, y, y + 0.05, 0.50, 0.86, bevel=0.015)
    elif facing == "N":
        box("chair", x + 0.01, x + w - 0.01, y + w - 0.05, y + w, 0.50, 0.86, bevel=0.015)
    elif facing == "W":
        box("chair", x + w - 0.05, x + w, y + 0.01, y + w - 0.01, 0.50, 0.86, bevel=0.015)
    else:
        box("chair", x, x + 0.05, y + 0.01, y + w - 0.01, 0.50, 0.86, bevel=0.015)


def pendant(x, y, z_bottom=1.55):
    bpy.ops.mesh.primitive_cylinder_add(vertices=48, radius=0.13, depth=0.20,
                                        location=(x, -y, z_bottom + 0.11), end_fill_type="NOTHING")
    ob = bpy.context.active_object
    ob.data.materials.append(bpy.data.materials["Paralume"])
    sol = ob.modifiers.new("sol", "SOLIDIFY")
    sol.thickness = 0.004
    bpy.ops.object.shade_smooth()
    bpy.ops.mesh.primitive_cylinder_add(vertices=24, radius=0.13, depth=0.004,
                                        location=(x, -y, z_bottom + 0.22))
    bpy.context.active_object.data.materials.append(bpy.data.materials["Paralume"])
    bpy.ops.mesh.primitive_cylinder_add(vertices=8, radius=0.003, depth=H - z_bottom - 0.22,
                                        location=(x, -y, (H + z_bottom + 0.22) / 2))
    bpy.context.active_object.data.materials.append(bpy.data.materials["Paralume"])
    bpy.ops.mesh.primitive_uv_sphere_add(radius=0.05, location=(x, -y, z_bottom + 0.12))
    bpy.context.active_object.data.materials.append(bpy.data.materials["Luce calda"])
    li = bpy.data.lights.new("pendant", "POINT")
    li.energy = 25
    li.color = (1.0, 0.8, 0.6)
    li.shadow_soft_size = 0.05
    lo = bpy.data.objects.new("pendant", li)
    lo.location = (x, -y, z_bottom + 0.1)
    link(lo)


def potted_plant(x, y, h=1.5, seed=11):
    bpy.ops.mesh.primitive_cylinder_add(vertices=40, radius=0.2, depth=0.45, location=(x, -y, 0.225))
    bpy.context.active_object.data.materials.append(bpy.data.materials["Nero opaco"])
    bpy.ops.object.shade_smooth()
    bpy.ops.mesh.primitive_cylinder_add(vertices=8, radius=0.02, depth=h - 0.5, location=(x, -y, 0.45 + (h - 0.5) / 2))
    bpy.context.active_object.data.materials.append(bpy.data.materials["Corteccia"])
    blob("Foglie", (x, y, h - 0.35), 0.35, seed=seed, squash=1.4, leaf=0.09, density=3500)


def add_furniture():
    # --- bagno grande + doccia
    B("ceramic", 5.55, 4.65, 1.30, 1.00, 0.06)
    B("glass", 5.70, 5.68, 1.15, 0.03, 2.0)
    B("ceramic", 6.30, 7.35, 0.36, 0.20, 0.8, bevel=0.03)
    B("ceramic", 6.30, 6.90, 0.36, 0.45, 0.4, bevel=0.05)
    B("ceramic", 6.95, 7.00, 0.36, 0.55, 0.4, bevel=0.05)
    B("oak_furn", 7.40, 7.10, 0.70, 0.45, 0.35, 0.5, bevel=0.005)
    # --- bagno piccolo
    B("oak_furn", 0.45, 1.85, 0.60, 0.45, 0.35, 0.5, bevel=0.005)
    B("ceramic", 1.55, 1.85, 0.36, 0.20, 0.8, bevel=0.03)
    B("ceramic", 1.55, 2.05, 0.36, 0.45, 0.4, bevel=0.05)
    B("ceramic", 2.05, 1.85, 0.36, 0.55, 0.4, bevel=0.05)
    # --- w.c. garage
    B("ceramic", -2.30, 5.50, 0.36, 0.20, 0.8, bevel=0.03)
    B("ceramic", -2.30, 5.70, 0.36, 0.45, 0.4, bevel=0.05)
    B("lacquer_w", -3.50, 5.55, 0.45, 0.50, 0.85, bevel=0.005)
    # --- lavanderia
    B("lacquer_w", 7.85, 8.15, 0.6, 0.6, 0.85, bevel=0.01)
    B("lacquer_w", 7.85, 8.75, 0.6, 0.6, 0.85, bevel=0.01)
    B("lacquer", 6.40, 8.85, 0.9, 0.5, 0.85, bevel=0.005)

    # --- cucina (lineare sul muro est + lavello sotto il finestrone sud)
    cabinets(7.85, 8.45, 14.72, 15.43, 0, 2.30, "y", 1, plinth=0.1)            # colonna frigo
    cabinets(7.85, 8.45, 15.45, 18.45, 0, 0.86, "y", 5)                        # basi lato est
    box("top_stone", 7.82, 8.45, 15.45, 18.45, 0.86, 0.90, bevel=0.002)
    cabinets(5.00, 7.85, 17.90, 18.50, 0, 0.86, "x", 5)                        # basi lato sud
    box("top_stone", 5.00, 7.82, 17.87, 18.50, 0.86, 0.90, bevel=0.002)
    box("top_stone", 8.43, 8.45, 15.45, 18.45, 0.90, 1.45)                     # alzatina
    box("top_stone", 5.00, 8.45, 18.48, 18.50, 0.90, 1.10)
    B("steel", 6.05, 17.95, 0.70, 0.45, 0.012, 0.9)                            # lavello
    B("black_matte", 6.08, 17.98, 0.64, 0.39, 0.012, 0.892)
    box("steel", 6.37, 6.41, 18.40, 18.44, 0.90, 1.18)                         # rubinetto
    box("steel", 6.37, 6.41, 18.22, 18.44, 1.15, 1.18)
    B("black_glass", 7.90, 16.90, 0.55, 0.58, 0.006, 0.9)                      # piano cottura
    cabinets(8.10, 8.45, 15.45, 16.80, 1.45, 2.20, "y", 2, plinth=0)            # pensili
    cabinets(8.10, 8.45, 17.55, 18.45, 1.45, 2.20, "y", 1, plinth=0)
    box("steel", 8.05, 8.45, 16.85, 17.50, 1.62, 1.70, bevel=0.003)            # cappa sottopensile
    box("led", 8.11, 8.13, 15.46, 16.79, 1.44, 1.45)
    # angolo pranzo: porta finestra spostata a 60 cm dall'angolo (a filo dei mobili del lavello),
    # 2,15 m di muro libero -> divanetto a panca 200 cm, tavolo 160 x 85, 2 sedie: 5 posti.
    # Passaggio dall'apertura di 1,80 m: libero tra y 14,55 e 15,45 davanti alla colonna frigo.
    box("black_matte", 4.27, 4.93, 14.62, 16.58, 0.0, 0.06)                     # zoccolo
    box("sofa", 4.25, 4.95, 14.60, 16.60, 0.06, 0.34, bevel=0.02)                # base
    box("sofa", 4.40, 4.97, 14.62, 16.58, 0.34, 0.45, bevel=0.035)               # seduta
    box("sofa", 4.25, 4.42, 14.60, 16.60, 0.34, 0.90, bevel=0.035)               # schienale
    for yy in (14.66, 15.32, 15.98):                                             # cuscini
        box("sofa2", 4.40, 4.56, yy, yy + 0.58, 0.45, 0.82, bevel=0.06)
    box("walnut", 5.05, 5.90, 14.80, 16.40, 0.72, 0.76, bevel=0.004)             # tavolo 160 x 85
    for lx, ly in ((5.11, 14.86), (5.80, 14.86), (5.11, 16.30), (5.80, 16.30)):
        B("black_matte", lx, ly, 0.04, 0.04, 0.72)
    chair(6.00, 15.45, "W")                                                      # lato mobili
    chair(6.00, 15.95, "W")
    pendant(5.48, 15.20)
    pendant(5.48, 16.00)
    # TV 32" (0,73 x 0,43 m) su braccio orientabile, sul muretto accanto alla vetrata
    box("black_matte", 4.98, 5.14, 14.55, 14.57, 1.25, 1.45)                     # piastra a muro
    box("black_matte", 5.04, 5.08, 14.57, 14.66, 1.33, 1.37)                     # braccio
    box("tv", 4.70, 5.43, 14.66, 14.70, 1.15, 1.58, bevel=0.003)

    # --- soggiorno: parete attrezzata sul muro della lavanderia
    B("walnut", 5.75, 9.45, 2.7, 0.45, 0.45, 0.05, bevel=0.004)
    B("black_matte", 5.80, 9.50, 2.6, 0.40, 0.05, 0.0)
    B("walnut", 5.75, 9.45, 0.5, 0.45, 1.85, 0.5, bevel=0.004)
    B("walnut", 7.90, 9.45, 0.55, 0.45, 1.85, 0.5, bevel=0.004)
    B("walnut", 6.30, 9.45, 1.6, 0.30, 0.06, 1.95, bevel=0.003)
    B("led", 6.32, 9.70, 1.56, 0.02, 0.005, 1.945)
    B("tv", 6.45, 9.46, 1.30, 0.04, 0.75, 0.95, bevel=0.003)
    # divano angolare Tosaro 240 x 165
    SY = 12.50
    B("sofa", 6.05, SY - 0.30, 2.40, 0.30, 0.55, 0.14, bevel=0.04)             # schienale
    B("sofa", 6.05, SY - 0.95, 2.40, 0.65, 0.29, 0.14, bevel=0.03)             # base
    B("sofa", 7.74, SY - 1.65, 0.71, 0.72, 0.29, 0.14, bevel=0.03)             # chaise
    B("sofa", 6.26, SY - 0.80, 0.735, 0.50, 0.08, 0.43, bevel=0.035)           # sedute
    B("sofa", 7.00, SY - 0.80, 0.735, 0.50, 0.08, 0.43, bevel=0.035)
    B("sofa", 7.74, SY - 1.62, 0.71, 1.32, 0.08, 0.43, bevel=0.035)
    B("sofa", 6.05, SY - 0.95, 0.20, 0.95, 0.46, 0.14, bevel=0.04)             # bracciolo sx
    B("sofa", 8.25, SY - 0.60, 0.20, 0.60, 0.46, 0.14, bevel=0.04)             # bracciolo dx
    for px, ph in ((6.28, 0.45), (6.95, 0.40), (7.62, 0.40)):                   # poggiatesta
        B("sofa2", px, SY - 0.36, 0.62, 0.16, ph, 0.50, bevel=0.06)
    for px, py in ((6.08, SY - 0.93), (6.10, SY - 0.10), (8.28, SY - 0.58), (8.28, SY - 0.10),
                   (7.77, SY - 1.62), (8.40, SY - 1.62)):
        B("black_matte", px, py, 0.04, 0.04, 0.14)
    box("rug", 5.95, 8.20, 10.25, 12.35, 0.0, 0.012, bevel=0.004)
    potted_plant(4.85, 11.75, 1.6, seed=13)
    # lampada da terra ad arco accanto al divano
    box("black_matte", 5.60, 5.85, SY - 0.35, SY - 0.10, 0.0, 0.03)
    box("black_matte", 5.71, 5.74, SY - 0.24, SY - 0.21, 0.03, 1.55)
    bpy.ops.mesh.primitive_cone_add(vertices=40, radius1=0.20, radius2=0.08, depth=0.22,
                                    location=(5.725, -(SY - 0.225), 1.55), end_fill_type="NOTHING")
    ob = bpy.context.active_object
    ob.data.materials.append(bpy.data.materials["Lino letto"])
    ob.modifiers.new("sol", "SOLIDIFY").thickness = 0.004
    li = bpy.data.lights.new("floorlamp", "POINT")
    li.energy = 30
    li.color = (1.0, 0.78, 0.55)
    lo = bpy.data.objects.new("floorlamp", li)
    lo.location = (5.725, -(SY - 0.225), 1.52)
    link(lo)

    # vetrata telescopica 3 ante (apertura 1,80 m) accatastate sul lato est
    box("frame", 5.475, 7.275, 14.33, 14.45, H - 0.06, H)                     # binario a soffitto
    box("frame", 5.475, 7.275, 14.33, 14.45, 0.0, 0.012)                      # guida a pavimento
    for i, (px, py) in enumerate(((6.05, 14.345), (6.35, 14.38), (6.65, 14.415))):
        pw, fw = 0.62, 0.035
        box("frame", px, px + pw, py, py + 0.028, 0.012, 0.012 + fw)
        box("frame", px, px + pw, py, py + 0.028, H - 0.06 - fw, H - 0.06)
        box("frame", px, px + fw, py, py + 0.028, 0.012, H - 0.06)
        box("frame", px + pw - fw, px + pw, py, py + 0.028, 0.012, H - 0.06)
        box("glass", px + fw, px + pw - fw, py + 0.008, py + 0.02, 0.012 + fw, H - 0.06 - fw)
    # porta scorrevole in vetro tra corridoio e soggiorno (chiusa)
    box("frame", 4.45, 6.75, 9.26, 9.35, H - 0.10, H)
    px0, px1 = 4.52, 5.64
    box("frame", px0, px1, 9.30, 9.335, 0.01, 0.05)
    box("frame", px0, px1, 9.30, 9.335, 2.03, 2.07)
    box("frame", px0, px0 + 0.03, 9.30, 9.335, 0.01, 2.07)
    box("frame", px1 - 0.03, px1, 9.30, 9.335, 0.01, 2.07)
    box("glass", px0 + 0.03, px1 - 0.03, 9.31, 9.325, 0.05, 2.03)
    # mobile d'ingresso ad angolo con specchio
    B("oak_furn", 4.25, 13.65, 0.30, 0.50, 0.86, 0.04, bevel=0.003)
    B("oak_furn", 4.25, 14.15, 1.20, 0.28, 0.86, 0.04, bevel=0.003)
    B("black_matte", 4.28, 13.68, 0.24, 0.72, 0.04)
    B("walnut", 4.25, 13.65, 0.30, 0.50, 0.03, 0.90, bevel=0.002)
    B("walnut", 4.25, 14.15, 1.20, 0.28, 0.03, 0.90, bevel=0.002)
    B("mirror", 4.55, 14.42, 0.80, 0.02, 1.05, 0.98, bevel=0.002)
    B("black_matte", 4.54, 14.435, 0.82, 0.01, 1.07, 0.97)

    # --- camera
    B("walnut", 0.30, 8.10, 0.06, 1.60, 1.05, 0.0, bevel=0.003)               # testiera
    B("walnut", 0.36, 8.10, 1.96, 1.60, 0.25, 0.06, bevel=0.01)
    B("linen", 0.38, 8.12, 1.90, 1.56, 0.20, 0.31, bevel=0.05)
    B("linen", 0.42, 8.20, 0.45, 0.62, 0.14, 0.50, bevel=0.06)
    B("linen", 0.42, 8.95, 0.45, 0.62, 0.14, 0.50, bevel=0.06)
    B("walnut", 0.30, 7.65, 0.40, 0.40, 0.45, bevel=0.005)
    B("walnut", 0.30, 9.75, 0.40, 0.40, 0.45, bevel=0.005)
    cabinets(3.62, 4.22, 7.80, 10.00, 0, 2.3, "y", 4, mat="lacquer_w", plinth=0.05)
    # --- cameretta
    B("oak_furn", 5.95, 1.10, 0.9, 2.0, 0.3, bevel=0.01)
    B("linen", 5.97, 1.12, 0.86, 1.96, 0.2, 0.3, bevel=0.04)
    B("linen", 6.05, 1.15, 0.6, 0.4, 0.12, 0.5, bevel=0.05)
    cabinets(4.20, 4.80, 0.85, 2.65, 0, 2.3, "y", 3, mat="lacquer_w", plinth=0.05)
    B("oak_furn", 5.00, 0.30, 1.2, 0.6, 0.04, 0.72, bevel=0.003)
    B("oak_furn", 5.00, 0.30, 0.04, 0.6, 0.72)
    B("oak_furn", 6.16, 0.30, 0.04, 0.6, 0.72)
    # --- studio
    B("oak_furn", 0.30, 4.10, 0.7, 1.4, 0.04, 0.72, bevel=0.003)
    B("oak_furn", 0.30, 4.10, 0.7, 0.04, 0.72)
    B("oak_furn", 0.30, 5.46, 0.7, 0.04, 0.72)
    B("chair", 1.10, 4.55, 0.45, 0.45, 0.45, bevel=0.02)
    B("oak_furn", 0.90, 3.70, 1.6, 0.35, 1.9, bevel=0.003)
    # --- ripostiglio + pilastro
    B("oak_furn", 0.30, 0.50, 0.35, 1.10, 1.9, bevel=0.003)
    B("plaster_int", 4.15, 5.20, 0.27, 0.45, 2.7)

# ---------------------------------------------------------------- luce e camere

# Azimut misurato nel sistema della piantina (0 = alto, 90 = destra). Azimut reale = SUN_AZ + 270:
# 235 -> 145 gradi reali, sole da sud-est a metà mattina. Illumina la facciata d'ingresso (sud),
# il fronte del garage (est) ed entra dal finestrone della cucina (est).
SUN_AZ = math.radians(235)
SUN_EL = math.radians(32)


def setup_world(strength=0.35):
    w = bpy.data.worlds.new("Cielo")
    bpy.context.scene.world = w
    w.use_nodes = True
    nt = w.node_tree
    nt.nodes.clear()
    out = nt.nodes.new("ShaderNodeOutputWorld")
    bg = nt.nodes.new("ShaderNodeBackground")
    sky = nt.nodes.new("ShaderNodeTexSky")
    for t in ("MULTIPLE_SCATTERING", "NISHITA", "SINGLE_SCATTERING", "HOSEK_WILKIE"):
        try:
            sky.sky_type = t
            break
        except TypeError:
            continue
    if hasattr(sky, "sun_disc"):
        sky.sun_disc = False
    if hasattr(sky, "sun_elevation"):
        sky.sun_elevation = SUN_EL
        # rotazione Blender: 0 = sole verso +Y (nord); le nostre azimut sono da nord in senso orario
        sky.sun_rotation = math.pi / 2 - SUN_AZ + math.pi
    if hasattr(sky, "air_density"):
        sky.air_density = 1.2
    if hasattr(sky, "aerosol_density"):
        sky.aerosol_density = 1.5
    bg.inputs["Strength"].default_value = strength
    nt.links.new(sky.outputs[0], bg.inputs[0])
    nt.links.new(bg.outputs[0], out.inputs[0])

    sun = bpy.data.lights.new("Sole", "SUN")
    sun.energy = 4.2
    sun.angle = math.radians(0.8)
    sun.color = (1.0, 0.93, 0.84)
    so = bpy.data.objects.new("Sole", sun)
    # direzione della luce: dal sole verso la scena
    d = Vector((math.sin(SUN_AZ) * math.cos(SUN_EL), math.cos(SUN_AZ) * math.cos(SUN_EL), math.sin(SUN_EL)))
    so.rotation_mode = "QUATERNION"
    so.rotation_quaternion = (-d).to_track_quat("-Z", "Y")
    link(so)


def add_portals():
    """Luci portale sulle finestre: guidano il campionamento del cielo negli interni."""
    for horiz, fixed, a, b, z0, z1, side, t in PORTALS:
        li = bpy.data.lights.new("portal", "AREA")
        li.shape = "RECTANGLE"
        li.size = b - a
        li.size_y = z1 - z0
        li.cycles.is_portal = True
        lo = bpy.data.objects.new("portal", li)
        c = (a + b) / 2
        zc = (z0 + z1) / 2
        # la luce area emette verso -Z locale: orientata verso l'interno
        if horiz:
            sgn = {"N": -1, "S": 1}.get(side, 1)
            lo.location = (c, -(fixed + sgn * (t / 2 + 0.01)), zc)
            # interno è verso -sgn in y di pianta -> +sgn in Y Blender
            direction = Vector((0, sgn, 0))
        else:
            sgn = {"W": -1, "E": 1}.get(side, 1)
            lo.location = (fixed + sgn * (t / 2 + 0.01), -c, zc)
            direction = Vector((-sgn, 0, 0))
        lo.rotation_mode = "QUATERNION"
        lo.rotation_quaternion = direction.to_track_quat("-Z", "Z")
        link(lo)


def add_ceiling_spots(points, energy=6):
    for (x, y) in points:
        bpy.ops.mesh.primitive_cylinder_add(vertices=24, radius=0.045, depth=0.004, location=(x, -y, H - 0.002))
        bpy.context.active_object.data.materials.append(bpy.data.materials["Luce calda"])
        li = bpy.data.lights.new("spot", "SPOT")
        li.energy = energy
        li.spot_size = math.radians(80)
        li.spot_blend = 0.6
        li.shadow_soft_size = 0.03
        li.color = (1.0, 0.82, 0.62)
        lo = bpy.data.objects.new("spot", li)
        lo.location = (x, -y, H - 0.01)
        link(lo)


CAMERAS = {
    # nome: (posizione pianta x,y,z), (target x,y,z), lente mm, esposizione
    "ext_ingresso": ((-2.4, 20.8, 1.6), (3.6, 12.6, 1.5), 20, -0.25),
    "ext_aerea": ((-11.5, 30.5, 12.5), (3.0, 10.0, 0.5), 28, -0.25),
    "int_soggiorno": ((4.75, 14.15, 1.45), (7.6, 9.9, 1.05), 17, 1.7),
    "int_divano": ((5.15, 9.95, 1.35), (7.3, 13.9, 0.95), 16, 1.7),
    "int_cucina": ((7.45, 17.45, 1.5), (4.5, 15.0, 1.0), 16, 1.5),
    "ext_retro": ((-3.4, -2.8, 1.65), (3.5, 3.0, 1.3), 18, -0.25),
    "int_cucina2": ((4.6, 17.9, 1.55), (7.2, 14.6, 1.0), 16, 1.5),
}


def add_camera(name, pos, target, lens):
    cam = bpy.data.cameras.new(name)
    cam.lens = lens
    cam.sensor_width = 36
    cam.clip_start = 0.05
    ob = bpy.data.objects.new(name, cam)
    ob.location = (pos[0], -pos[1], pos[2])
    t = Vector((target[0], -target[1], target[2]))
    ob.rotation_mode = "QUATERNION"
    ob.rotation_quaternion = (t - ob.location).to_track_quat("-Z", "Y")
    link(ob)
    return ob


def setup_render(res, samples):
    sc = bpy.context.scene
    sc.render.engine = "CYCLES"
    sc.cycles.device = "CPU"
    sc.cycles.samples = samples
    sc.cycles.use_adaptive_sampling = True
    sc.cycles.adaptive_threshold = 0.02
    sc.cycles.use_denoising = True
    try:
        sc.cycles.denoiser = "OPENIMAGEDENOISE"
    except TypeError:
        pass
    sc.cycles.max_bounces = 10
    sc.cycles.diffuse_bounces = 5
    sc.cycles.glossy_bounces = 4
    sc.cycles.transmission_bounces = 8
    sc.cycles.transparent_max_bounces = 16
    sc.cycles.caustics_reflective = False
    sc.cycles.caustics_refractive = False
    sc.cycles.sample_clamp_indirect = 8.0
    sc.render.resolution_x = res
    sc.render.resolution_y = int(res * 9 / 16)
    sc.render.resolution_percentage = 100
    sc.render.image_settings.file_format = "JPEG"
    sc.render.image_settings.quality = 92
    sc.render.threads_mode = "AUTO"
    sc.render.use_persistent_data = True
    vs = sc.view_settings
    try:
        vs.view_transform = "AgX"
        vs.look = "AgX - Medium High Contrast"
    except TypeError:
        vs.view_transform = "Filmic"


def build():
    reset()
    M = build_materials()
    add_walls()
    add_floors()
    add_exterior(M)
    add_furniture()
    flush_geometry(M)
    setup_world()
    add_portals()
    add_ceiling_spots([(5.4, 10.4), (7.4, 10.4), (5.4, 13.4), (7.4, 13.4),
                       (5.2, 15.2), (7.3, 15.2), (5.2, 17.3), (7.3, 17.3),
                       (5.5, 1.6), (5.5, 3.6), (1.55, 4.6), (1.55, 5.9), (1.55, 2.7), (1.55, 1.0),
                       (7.0, 6.6), (6.2, 5.15), (7.0, 8.5), (3.5, 1.3), (3.5, 3.4), (3.5, 5.0),
                       (4.2, 6.15), (5.05, 7.6), (5.05, 8.8), (1.3, 8.1), (3.0, 9.7),
                       (-2.6, 7.9), (-1.0, 7.9), (-2.6, 9.9), (-1.0, 9.9), (-2.7, 6.1)])
    for n, (p, t, lens, _) in CAMERAS.items():
        add_camera(n, p, t, lens)


NO_EXPORT = {"Foglie", "Siepe", "Siepe fitta", "Corteccia", "Prato", "Erba"}


def export_glb(path):
    bpy.ops.object.select_all(action="DESELECT")
    for ob in bpy.context.scene.objects:
        if ob.type != "MESH":
            continue
        mats = {m.name for m in ob.data.materials if m}
        if mats & NO_EXPORT:
            continue
        ob.select_set(True)
    bpy.ops.export_scene.gltf(filepath=os.path.abspath(path), export_format="GLB", use_selection=True,
                              export_apply=True, export_materials="EXPORT", export_lights=False,
                              export_cameras=False, export_normals=True, export_texcoords=False)


PANOS = {
    # nome: (x, y, z) in pianta
    "soggiorno": (5.6, 13.55, 1.55),
    "cucina": (7.15, 16.75, 1.55),
    "camera": (2.75, 10.1, 1.55),
    "giardino": (1.1, 17.4, 1.55),
    "retro": (-1.0, -1.3, 1.6),
    "cameretta": (5.3, 3.4, 1.55),
    "studio": (1.9, 5.4, 1.55),
    "bagno_grande": (7.0, 6.3, 1.55),
    "bagno_piccolo": (1.6, 3.0, 1.55),
    "lavanderia": (6.9, 8.2, 1.55),
    "corridoio": (3.5, 4.3, 1.55),
    "garage": (-1.8, 8.9, 1.55),
}
PANO_EXPOSURE = {"giardino": -0.25, "retro": -0.25, "garage": 2.8, "bagno_grande": 2.3, "bagno_piccolo": 2.1,
                 "lavanderia": 2.3, "corridoio": 2.2, "studio": 1.8, "cameretta": 1.6}
# facce del cubo: (nome, direzione, "alto" dell'immagine) in coordinate Blender
FACES = [("n", (0, 1, 0), (0, 0, 1)), ("e", (1, 0, 0), (0, 0, 1)), ("s", (0, -1, 0), (0, 0, 1)),
         ("w", (-1, 0, 0), (0, 0, 1)), ("u", (0, 0, 1), (0, -1, 0)), ("d", (0, 0, -1), (0, 1, 0))]


def render_panos(names, size, samples, out):
    from mathutils import Matrix
    sc = bpy.context.scene
    sc.render.resolution_x = sc.render.resolution_y = size
    sc.cycles.samples = samples
    sc.render.image_settings.quality = 86
    cam = bpy.data.cameras.new("pano")
    cam.lens_unit = "FOV"
    cam.angle = math.radians(90)
    cam.sensor_fit = "HORIZONTAL"
    cam.clip_start = 0.05
    ob = link(bpy.data.objects.new("pano", cam))
    sc.camera = ob
    os.makedirs(out, exist_ok=True)
    for n in names:
        x, y, z = PANOS[n]
        ob.location = (x, -y, z)
        sc.view_settings.exposure = PANO_EXPOSURE.get(n, 1.5)
        for f, d, u in FACES:
            fw, up = Vector(d), Vector(u)
            right = fw.cross(up)
            ob.rotation_mode = "QUATERNION"
            ob.rotation_quaternion = Matrix((right, up, -fw)).transposed().to_quaternion()
            sc.render.filepath = os.path.abspath(os.path.join(out, f"{n}_{f}.jpg"))
            bpy.ops.render.render(write_still=True)
            print("RENDERED", sc.render.filepath, flush=True)


def main():
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else sys.argv[1:]
    ap = argparse.ArgumentParser()
    ap.add_argument("--cams", default="")
    ap.add_argument("--res", type=int, default=960)
    ap.add_argument("--samples", type=int, default=64)
    ap.add_argument("--out", default="renders")
    ap.add_argument("--save", default="")
    ap.add_argument("--glb", default="")
    ap.add_argument("--panos", default="")
    ap.add_argument("--pano-size", type=int, default=768)
    a = ap.parse_args(argv)
    build()
    setup_render(a.res, a.samples)
    if a.save:
        bpy.ops.wm.save_as_mainfile(filepath=os.path.abspath(a.save))
    if a.glb:
        export_glb(a.glb)
    if a.panos:
        render_panos([p for p in a.panos.split(",") if p], a.pano_size, a.samples, a.out)
        return
    os.makedirs(a.out, exist_ok=True)
    for n in [c for c in a.cams.split(",") if c]:
        sc = bpy.context.scene
        sc.camera = bpy.data.objects[n]
        sc.view_settings.exposure = CAMERAS[n][3]
        sc.render.filepath = os.path.abspath(os.path.join(a.out, n + ".jpg"))
        bpy.ops.render.render(write_still=True)
        print("RENDERED", sc.render.filepath, flush=True)


if __name__ == "__main__":
    main()
