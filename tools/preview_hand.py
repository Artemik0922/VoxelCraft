# Offline preview of the first-person hand poses: rasterises the same
# transform chains as HeldItemRenderer with the real sprite/skin colours,
# so poses can be tuned without launching the game.
# Usage: python tools/preview_hand.py [variant]   ->  tools/hand_preview_<name>.png
import numpy as np
from PIL import Image
import math, sys

W, H = 1280, 720
ASPECT = W / H
TAN = math.tan(math.radians(35.0))  # 70 deg vertical FOV -> half-angle 35
P = 1.0 / 16.0

def m_identity(): return np.eye(4)

def m_mul(a, b): return a @ b

def m_translate(m, x, y, z):
    t = np.eye(4); t[:3, 3] = (x, y, z); return m @ t

def m_rot(m, axis, deg):
    r = np.eye(4); c, s = math.cos(math.radians(deg)), math.sin(math.radians(deg))
    if axis == 'x': r[:3, :3] = [[1,0,0],[0,c,-s],[0,s,c]]
    if axis == 'y': r[:3, :3] = [[c,0,s],[0,1,0],[-s,0,c]]
    if axis == 'z': r[:3, :3] = [[c,-s,0],[s,c,0],[0,0,1]]
    return m @ r

def m_scale(m, s):
    t = np.eye(4); t[0,0]=t[1,1]=t[2,2]=s; return m @ t

def transform(m, pts):
    pts = np.asarray(pts, dtype=float)
    hom = np.hstack([pts, np.ones((len(pts),1))])
    out = (m @ hom.T).T[:, :3]
    return out

def project(pts):
    # view space -> pixel coords (Y down), returns (xy, depth)
    z = -pts[:, 2]
    z = np.where(np.abs(z) < 1e-6, 1e-6, z)
    nx = pts[:, 0] / (z * TAN * ASPECT)
    ny = pts[:, 1] / (z * TAN)
    px = (nx * 0.5 + 0.5) * W
    py = (0.5 - ny * 0.5) * H
    return np.stack([px, py], 1), z

# ---------------------------------------------------------------- sprite model
def build_slab(tex, depth=1/16.0):
    # tex: 16x16 RGBA; returns list of (quad(4x3), color(3), shade)
    tris = []
    n = tex.shape[0]
    for py in range(n):
        for px in range(n):
            a = tex[py, px, 3]
            if a < 128: continue
            col = tex[py, px, :3].astype(float) / 255.0
            xL, xR = px/n - 0.5, (px+1)/n - 0.5
            yB, yT = 0.5 - (py+1)/n, 0.5 - py/n
            zB, zF = -depth/2, depth/2
            F = [[xL,yB,zF],[xR,yB,zF],[xR,yT,zF],[xL,yT,zF]]
            B = [[xL,yB,zB],[xR,yB,zB],[xR,yT,zB],[xL,yT,zB]]
            tris.append((F, col, 1.00))
            tris.append((B, col, 0.80))
            top = py > 0 and tex[py-1, px, 3] >= 128
            bot = py < n-1 and tex[py+1, px, 3] >= 128
            lef = px > 0 and tex[py, px-1, 3] >= 128
            rig = px < n-1 and tex[py, px+1, 3] >= 128
            if not top: tris.append(([[xL,yT,zF],[xR,yT,zF],[xR,yT,zB],[xL,yT,zB]], col, 0.95))
            if not bot: tris.append(([[xL,yB,zB],[xR,yB,zB],[xR,yB,zF],[xL,yB,zF]], col, 0.60))
            if not lef: tris.append(([[xL,yB,zB],[xL,yT,zB],[xL,yT,zF],[xL,yB,zF]], col, 0.70))
            if not rig: tris.append(([[xR,yB,zF],[xR,yT,zF],[xR,yT,zB],[xR,yB,zB]], col, 0.85))
    return tris

def build_box(size=(1,1,1), center=(0,0,0), col=(0.55,0.55,0.58)):
    w,h,d = size; cx,cy,cz = center
    x0,x1 = cx-w/2, cx+w/2; y0,y1 = cy-h/2, cy+h/2; z0,z1 = cz-d/2, cz+d/2
    c = np.array(col, dtype=float)/255.0 if max(col) > 1 else np.array(col, dtype=float)
    faces = []
    q = lambda a,b,cc,d2,sh: faces.append(( [a,b,cc,d2], c, sh))
    q([x0,y0,z1],[x1,y0,z1],[x1,y1,z1],[x0,y1,z1], 1.0)   # front +Z
    q([x0,y0,z0],[x1,y0,z0],[x1,y1,z0],[x0,y1,z0], 0.8)   # back
    q([x0,y1,z0],[x1,y1,z0],[x1,y1,z1],[x0,y1,z1], 0.95)  # top
    q([x0,y0,z1],[x1,y0,z1],[x1,y0,z0],[x0,y0,z0], 0.6)   # bottom
    q([x0,y0,z0],[x0,y0,z1],[x0,y1,z1],[x0,y1,z0], 0.7)   # left
    q([x1,y0,z1],[x1,y0,z0],[x1,y1,z0],[x1,y1,z1], 0.85)  # right
    return faces

def render(tris, out):
    img = np.ones((H, W, 3), dtype=float)
    sky = np.array([0.55, 0.75, 0.95])
    img[:] = sky * 0.9
    # ground hint
    for i in range(H):
        pass
    # painter: collect projected quads, sort by mean depth (far first)
    quads = []
    for quad, col, shade in tris:
        pts = transform(QUAD_M, quad)
        xy, z = project(pts)
        quads.append((z.mean(), xy, col * shade))
    quads.sort(key=lambda q: -q[0])
    from PIL import ImageDraw
    im = Image.fromarray((img * 255).astype(np.uint8))
    dr = ImageDraw.Draw(im)
    for zmean, xy, col in quads:
        # backface cull: screen-space signed area (Y down -> CCW negative)
        a = xy[0]; b = xy[1]; c2 = xy[2]
        cross = (b[0]-a[0])*(c2[1]-a[1]) - (b[1]-a[1])*(c2[0]-a[0])
        if cross < 0:
            continue
        c = tuple(int(v*255) for v in np.clip(col,0,1))
        dr.polygon([tuple(p) for p in xy], fill=c)
    im.save(out)
    print("saved", out)

# ---------------------------------------------------------------- poses
def base_hand(anchor=(0.56,-0.52,-0.72), attack=45):
    m = m_identity()
    m = m_translate(m, *anchor)
    m = m_rot(m, 'y', attack)
    return m

def item_chain(rotY=-90, rotZ=25):
    m = base_hand()
    m = m_translate(m, 1.13*P, 3.2*P, 1.13*P)
    m = m_rot(m, 'y', rotY); m = m_rot(m, 'z', rotZ)
    m = m_scale(m, 0.68)
    return m

def block_chain():
    m = base_hand()
    m = m_rot(m, 'y', 45)
    m = m_scale(m, 0.40)
    m = m_translate(m, 0.5, 0.5, 0.5)
    return m

def arm_chain(rollZ=37, yaw=-30, hand=(0.48,-0.38,-0.62), pitch=0):
    m = m_identity()
    m = m_translate(m, *hand)
    m = m_rot(m, 'y', yaw)
    m = m_rot(m, 'x', pitch)
    m = m_rot(m, 'z', rollZ)
    return m

SKIN = np.array([[0xE0,0xAC,0x7A],[0xC9,0x8F,0x5E]], dtype=float)/255.0

def arm_faces():
    # box in px: x -2..2, y -12..0 (hand at y=0, shoulder at y=-12), z -2..2
    faces = []
    col1 = SKIN[0]; col2 = SKIN[1]
    x0,x1 = -2*P, 2*P; y0,y1 = 0, -12*P; z0,z1 = -2*P, 2*P
    q = lambda a,b,c,d,sh,col: faces.append(([a,b,c,d], col, sh))
    q([x0,y0,z1],[x1,y0,z1],[x1,y1,z1],[x0,y1,z1], 1.0, col1)   # palm side
    q([x0,y0,z0],[x1,y0,z0],[x1,y1,z0],[x0,y1,z0], 0.8, col1)
    q([x0,y1,z0],[x1,y1,z0],[x1,y1,z1],[x0,y1,z1], 0.95, col2)  # elbow cap
    q([x0,y0,z1],[x1,y0,z1],[x1,y0,z0],[x0,y0,z0], 0.6, col2)   # wrist cap
    q([x0,y0,z0],[x0,y0,z1],[x0,y1,z1],[x0,y1,z0], 0.7, col1)
    q([x1,y0,z1],[x1,y0,z0],[x1,y1,z0],[x1,y1,z1], 0.85, col1)
    return faces

if __name__ == "__main__":
    atlas_tile = Image.open("src/main/resources/textures/blocks/iron_sword.png").convert("RGBA")
    if atlas_tile.size != (16,16):
        atlas_tile = atlas_tile.resize((16,16), Image.NEAREST)
    tex = np.array(atlas_tile)

    global QUAD_M
    variant = sys.argv[1] if len(sys.argv) > 1 else "all"

    def shoot(name, m, tris):
        global QUAD_M
        QUAD_M = m
        render(tris, f"tools/hand_preview_{name}.png")

    if variant in ("all", "sword"):
        for tag, rz in (("z25", -25), ("z50", -50), ("z65", -65), ("z80", -80), ("z20p", 20)):
            shoot(f"sword_{tag}", item_chain(90, rz), build_slab(tex))
    if variant in ("all", "block"):
        m = base_hand()
        m = m_scale(m, 0.40)
        m = m_translate(m, -0.5, 0.2, 0)
        m = m_rot(m, 'y', 30); m = m_rot(m, 'x', -80); m = m_rot(m, 'y', 60)
        m = m_translate(m, 0.5, 0.5, 0.5)
        shoot("block", m, build_box())
    if variant in ("all", "arm"):
        for tag, args in (("a", (35,-25,(0.42,-0.32,-0.62),-5)),
                          ("b", (42,-15,(0.40,-0.28,-0.58),-8))):
            shoot(f"arm_{tag}", arm_chain(*args), arm_faces())
