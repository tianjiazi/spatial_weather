"""Render the actual GLB meshes to PNG. pip install numpy pillow.

This small software rasterizer also checks all accessors, normals, triangle
indices and finite normalized model bounds; it does not replace device checks.
"""
import json
import math
import struct
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app/src/main/assets/models"
PREVIEWS = ROOT / ".private/model-previews"
FONT = "/System/Library/Fonts/STHeiti Medium.ttc"


def render(path, size=240):
    raw = path.read_bytes()
    magic, version, length = struct.unpack_from("<III", raw)
    assert magic == 0x46546C67 and version == 2 and length == len(raw)
    json_length, tag = struct.unpack_from("<II",raw,12)
    assert tag == 0x4E4F534A
    doc = json.loads(raw[20:20+json_length])
    bin_start = 28 + json_length
    def read(index):
        a = doc["accessors"][index]
        v = doc["bufferViews"][a["bufferView"]]
        count = {"VEC3":3,"SCALAR":1}[a["type"]]
        dtype = {5126:"<f4",5125:"<u4"}[a["componentType"]]
        values = np.frombuffer(raw,dtype=dtype,count=a["count"]*count,
                               offset=bin_start+v.get("byteOffset",0)+a.get("byteOffset",0))
        return values.reshape(-1,count) if count>1 else values
    angle, pitch = math.radians(-16), math.radians(8)
    rotate_y = np.array([[math.cos(angle),0,math.sin(angle)],[0,1,0],
                         [-math.sin(angle),0,math.cos(angle)]])
    rotate_x = np.array([[1,0,0],[0,math.cos(pitch),-math.sin(pitch)],
                         [0,math.sin(pitch),math.cos(pitch)]])
    rotation = rotate_x @ rotate_y
    depth = np.full((size,size),-np.inf)
    image = np.zeros((size,size,4),dtype=np.uint8)
    light = np.array([-.4,.7,.7]); light /= np.linalg.norm(light)
    all_bounds = []
    for primitive in doc["meshes"][0]["primitives"]:
        original = read(primitive["attributes"]["POSITION"])
        normals = read(primitive["attributes"]["NORMAL"])
        indices = read(primitive["indices"]).reshape(-1,3)
        assert np.isfinite(original).all() and np.isfinite(normals).all()
        assert indices.max() < len(original)
        assert np.allclose(np.linalg.norm(normals,axis=1),1,atol=.003)
        assert np.abs(original).max() <= 1.001
        all_bounds.append(original)
        vertices = original @ rotation.T
        normals = normals @ rotation.T
        screen = np.column_stack((size/2+vertices[:,0]*size*.36,
                                  size/2-vertices[:,1]*size*.36))
        base = np.array(doc["materials"][primitive["material"]]
                        ["pbrMetallicRoughness"]["baseColorFactor"][:3])
        for tri in indices:
            pts = screen[tri]
            lo = np.maximum(np.floor(pts.min(axis=0)).astype(int),0)
            hi = np.minimum(np.ceil(pts.max(axis=0)).astype(int),size-1)
            if np.any(hi<lo):
                continue
            x,y = np.meshgrid(np.arange(lo[0],hi[0]+1)+.5,
                              np.arange(lo[1],hi[1]+1)+.5)
            a,b,c = pts
            denominator = (b[1]-c[1])*(a[0]-c[0])+(c[0]-b[0])*(a[1]-c[1])
            if abs(denominator)<1e-6:
                continue
            u = ((b[1]-c[1])*(x-c[0])+(c[0]-b[0])*(y-c[1]))/denominator
            v = ((c[1]-a[1])*(x-c[0])+(a[0]-c[0])*(y-c[1]))/denominator
            w = 1-u-v
            z = u*vertices[tri[0],2]+v*vertices[tri[1],2]+w*vertices[tri[2],2]
            bounds = np.s_[lo[1]:hi[1]+1,lo[0]:hi[0]+1]
            mask = (u>=0)&(v>=0)&(w>=0)&(z>depth[bounds])
            if not mask.any():
                continue
            n = u[...,None]*normals[tri[0]]+v[...,None]*normals[tri[1]]+w[...,None]*normals[tri[2]]
            n /= np.maximum(np.linalg.norm(n,axis=-1,keepdims=True),1e-6)
            shade = .54+.45*np.maximum(0,n@light)
            color = np.clip(base*shade[...,None]*255,0,255).astype(np.uint8)
            image[bounds][mask,:3] = color[mask]
            image[bounds][mask,3] = 255
            depth[bounds][mask] = z[mask]
    assert (image[:,:,3]>0).sum()>size*size*.08, f"Empty model {path}"
    return Image.fromarray(image),len(np.concatenate(all_bounds))


def main():
    PREVIEWS.mkdir(parents=True,exist_ok=True)
    catalogue = json.loads((ASSETS/"catalogue.json").read_text())
    tile_w,tile_h,columns = 270,290,7
    rows = math.ceil(len(catalogue)/columns)
    sheet = Image.new("RGB",(columns*tile_w+48,rows*tile_h+132),(238,245,252))
    draw = ImageDraw.Draw(sheet)
    title_font = ImageFont.truetype(FONT,32)
    font = ImageFont.truetype(FONT,18)
    draw.text((28,24),"PICO 天气 · 69 款原创 3D 天气模型",font=title_font,fill=(32,55,81))
    draw.text((28,72),"统一粉彩卡通风格 / 真实 GLB 网格渲染 / 高德天气标签对应",font=font,fill=(98,118,139))
    for i,item in enumerate(catalogue):
        thumb,vertices = render(ASSETS/item["asset"])
        thumb.save(PREVIEWS/(Path(item["asset"]).stem+".png"))
        x,y = 24+(i%columns)*tile_w,120+(i//columns)*tile_h
        draw.rounded_rectangle((x+5,y+5,x+tile_w-7,y+tile_h-8),radius=22,fill=(255,255,255))
        sheet.paste(thumb,(x+15,y+6),thumb)
        label = item["weather"]
        box = draw.textbbox((0,0),label,font=font)
        draw.text((x+(tile_w-box[2])/2,y+248),label,font=font,fill=(40,60,82))
        if (i+1)%10==0:
            print(f"Rendered and validated {i+1}/{len(catalogue)}",flush=True)
    output = ROOT.parent/"PICO天气-1.2-模型总览.png"
    sheet.save(output)
    print(f"Validated all {len(catalogue)} GLBs. Saved {output}",flush=True)


if __name__ == "__main__":
    main()
