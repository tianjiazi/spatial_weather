"""Rebuild the original pastel weather collection. Python 3, no dependencies.

All geometry is real indexed, smooth-normal 3D geometry in glTF 2.0 GLB.
One mesh per material keeps loading/draw cost low. Model coordinates are
normalized to a 2-unit cube; +Z faces the viewer. No external textures.
"""
import json
import math
import struct
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "app/src/main/assets/models"
PALETTE = {
    "cream": (0.95, 0.97, 1.0), "lavender": (0.65, 0.72, 0.90),
    "sun": (1.0, 0.72, 0.24), "rays": (1.0, 0.85, 0.46),
    "blue": (0.32, 0.73, 0.98), "ice": (0.68, 0.92, 1.0),
    "pink": (1.0, 0.57, 0.66), "eyes": (0.12, 0.20, 0.34),
    "white": (1.0, 1.0, 1.0), "sand": (0.91, 0.64, 0.43),
}
# Ordered exact labels, including Amap transition and extended condition names.
GROUPS = [
    ("sun", ["晴"]), ("cloud", ["多云", "少云", "晴间多云"]),
    ("overcast", ["阴"]),
    ("rain", ["阵雨", "小雨", "中雨", "大雨", "暴雨", "大暴雨", "特大暴雨",
              "小雨-中雨", "中雨-大雨", "大雨-暴雨", "暴雨-大暴雨", "大暴雨-特大暴雨",
              "强阵雨", "极端降雨", "毛毛雨/细雨", "雨", "冻雨"]),
    ("thunder", ["雷阵雨", "强雷阵雨", "雷阵雨并伴有冰雹"]),
    ("sleet", ["雨夹雪"]),
    ("snow", ["阵雪", "小雪", "中雪", "大雪", "暴雪", "小雪-中雪",
              "中雪-大雪", "大雪-暴雪", "雪", "弱高吹雪", "吹雪"]),
    ("fog", ["雾", "轻雾", "大雾", "浓雾", "强浓雾", "特强浓雾", "中度雾"]),
    ("haze", ["霾", "中度霾", "重度霾", "严重霾"]),
    ("dust", ["浮尘", "扬沙", "沙尘暴", "强沙尘暴"]),
    ("wind", ["平静", "微风", "和风", "清风", "强风/劲风", "疾风", "大风",
              "烈风", "风暴", "狂爆风", "飓风", "热带风暴", "飑"]),
    ("tornado", ["龙卷风"]), ("hot", ["热"]), ("cold", ["冷"]),
    ("unknown", ["未知"]),
]


class Model:
    def __init__(self):
        self.parts = {}

    def sphere(self, center, scale, mat, rings=14, segments=22):
        vertices, normals, indices = self.parts.setdefault(mat, ([], [], []))
        offset = len(vertices)
        for j in range(rings + 1):
            v = math.pi * j / rings
            for i in range(segments + 1):
                u = 2 * math.pi * i / segments
                n = (math.sin(v) * math.cos(u), math.cos(v), math.sin(v) * math.sin(u))
                vertices.append(tuple(center[k] + scale[k] * n[k] for k in range(3)))
                normal = tuple(n[k] / scale[k] for k in range(3))
                length = math.sqrt(sum(x*x for x in normal))
                normals.append(tuple(x / length for x in normal))
        for j in range(rings):
            for i in range(segments):
                a = offset + j * (segments + 1) + i
                b = a + segments + 1
                indices.extend([a, a+1, b, a+1, b+1, b])

    def tube(self, points, radius, mat):
        # Swept smooth-normal eight-sided tube with rounded end caps.
        vertices, normals, indices = self.parts.setdefault(mat, ([], [], []))
        offset = len(vertices)
        segments = 8
        for i, p in enumerate(points):
            a, b = points[max(0,i-1)], points[min(len(points)-1,i+1)]
            tangent = tuple(b[k]-a[k] for k in range(3))
            length = math.sqrt(sum(t*t for t in tangent)) or 1
            tangent = tuple(t/length for t in tangent)
            side = (-tangent[1],tangent[0],0)
            length = math.sqrt(sum(t*t for t in side)) or 1
            side = tuple(t/length for t in side)
            up = (tangent[1]*side[2]-tangent[2]*side[1],
                  tangent[2]*side[0]-tangent[0]*side[2],
                  tangent[0]*side[1]-tangent[1]*side[0])
            for j in range(segments):
                angle = j*math.tau/segments
                n = tuple(side[k]*math.cos(angle)+up[k]*math.sin(angle) for k in range(3))
                vertices.append(tuple(p[k]+radius*n[k] for k in range(3)))
                normals.append(n)
        for i in range(len(points)-1):
            for j in range(segments):
                a = offset+i*segments+j
                b = offset+i*segments+(j+1)%segments
                indices.extend([a,b,a+segments,b,b+segments,a+segments])
        for p in (points[0],points[-1]):
            self.sphere(p,(radius,radius,radius),mat,6,10)

    def face(self, x=0, y=0, z=0.50, width=1):
        for side in [-1, 1]:
            self.sphere((x + side*.19*width, y+.03, z), (.046, .074, .027), "eyes")
            self.sphere((x + side*.19*width-.012, y+.055, z+.026), (.012,.018,.009), "white", 8, 12)
            self.sphere((x + side*.31*width, y-.085, z-.009), (.077,.044,.020), "pink")
        points = [(x + .09*math.cos(t), y-.09-.08*math.sin(t), z+.018)
                  for t in [math.pi*i/12 for i in range(13)]]
        self.tube(points, .015, "eyes")

    def cloud(self, mat="cream"):
        for c, s in [
            ((-.42,.12,0),(.44,.39,.34)), ((.04,.26,-.04),(.49,.49,.38)),
            ((.47,.08,-.02),(.34,.34,.30)), ((.0,-.02,.02),(.69,.27,.39)),
        ]:
            self.sphere(c, s, mat)
        self.face(y=.05, z=.405)

    def sun(self, x=0, y=0, r=.50, face=True):
        self.sphere((x,y,-.12), (r,r,r*.65), "sun")
        for i in range(10):
            a = i * math.tau / 10
            p = [(x+math.cos(a)*r*1.18, y+math.sin(a)*r*1.18,-.16),
                 (x+math.cos(a)*r*1.48, y+math.sin(a)*r*1.48,-.16)]
            self.tube(p, r*.07, "rays")
        if face:
            self.face(x,y,r*.65-.10)

    def drop(self, x, y, size=.14):
        self.sphere((x,y,.08), (size*.68,size,size*.6), "blue")
        self.sphere((x,y+size*.65,.08), (size*.40,size*.65,size*.38), "blue")
        self.sphere((x-size*.2,y+size*.2,.08+size*.54), (size*.12,size*.30,size*.08), "white", 8, 12)

    def snowflake(self, x, y, r=.14):
        for i in range(3):
            a = i*math.pi/3
            self.tube([(x-r*math.cos(a),y-r*math.sin(a),.12),
                       (x+r*math.cos(a),y+r*math.sin(a),.12)], .024, "ice")
        self.sphere((x,y,.13), (.05,.05,.045), "white", 8, 12)

    def save(self, path):
        all_vertices = [v for vs, _, _ in self.parts.values() for v in vs]
        low = [min(v[k] for v in all_vertices) for k in range(3)]
        high = [max(v[k] for v in all_vertices) for k in range(3)]
        center = [(low[k]+high[k])/2 for k in range(3)]
        factor = 1.9 / max(high[k]-low[k] for k in range(3))
        blob = bytearray()
        doc = {"asset": {"version":"2.0","generator":"PICO Weather pastel collection"},
               "scene":0,"scenes":[{"nodes":[0]}],"nodes":[{"mesh":0}],
               "meshes":[{"primitives":[]}],"materials":[],"bufferViews":[],"accessors":[]}
        def accessor(values, components, kind, gltype):
            while len(blob) % 4:
                blob.append(0)
            start = len(blob)
            flat = [v for row in values for v in row] if components > 1 else values
            blob.extend(struct.pack("<" + kind*len(flat), *flat))
            view = len(doc["bufferViews"])
            doc["bufferViews"].append({"buffer":0,"byteOffset":start,"byteLength":len(blob)-start})
            item = {"bufferView":view,"componentType":gltype,"count":len(values),
                    "type":{1:"SCALAR",3:"VEC3"}[components]}
            if components == 3:
                item["min"] = [min(v[k] for v in values) for k in range(3)]
                item["max"] = [max(v[k] for v in values) for k in range(3)]
            doc["accessors"].append(item)
            return len(doc["accessors"])-1
        triangles = 0
        for mat, (vs, ns, inds) in self.parts.items():
            vs = [tuple((v[k]-center[k])*factor for k in range(3)) for v in vs]
            mi = len(doc["materials"])
            color = PALETTE[mat]
            doc["materials"].append({"name":mat,"pbrMetallicRoughness":{
                "baseColorFactor":[*color,1],"metallicFactor":0,"roughnessFactor":.64},
                "emissiveFactor":[c*.08 for c in color]})
            doc["meshes"][0]["primitives"].append({"attributes":{
                "POSITION":accessor(vs,3,"f",5126),"NORMAL":accessor(ns,3,"f",5126)},
                "indices":accessor(inds,1,"I",5125),"material":mi})
            triangles += len(inds)//3
        doc["buffers"] = [{"byteLength":len(blob)}]
        raw = json.dumps(doc,separators=(",",":")).encode()
        raw += b" " * (-len(raw)%4)
        blob += b"\0" * (-len(blob)%4)
        result = struct.pack("<III",0x46546C67,2,12+8+len(raw)+8+len(blob))
        result += struct.pack("<II",len(raw),0x4E4F534A)+raw
        result += struct.pack("<II",len(blob),0x004E4942)+blob
        path.write_bytes(result)
        return {"bytes":len(result),"triangles":triangles,"materials":len(self.parts)}


def build(family, label, level):
    m = Model()
    if family in ("sun", "hot"):
        m.sun()
        if family == "hot":
            for x in [-.38, .0, .38]:
                m.tube([(x,-.74,.1),(x-.06,-.9,.1),(x+.03,-1.03,.1)],.025,"pink")
        return m
    if family == "cloud":
        m.sun(.45,.50,.36,False)
    m.cloud("lavender" if family in ("overcast","thunder","haze") else
            "sand" if family == "dust" else "cream")
    heavy = any(word in label for word in ["大","暴","强","极端","严重","重度"])
    count = 5 if heavy else 3
    if family in ("rain","thunder","sleet"):
        for i in range(count):
            m.drop(-.50+i*(1.0/(count-1)), -.48-(i%2)*.13, .12 if heavy else .10)
        if label == "冻雨":
            m.snowflake(.58,-.64)
    if family == "thunder":
        m.tube([(.06,-.33,.28),(-.10,-.56,.28),(.10,-.54,.28),(-.03,-.84,.28)],.06,"sun")
        if "冰雹" in label:
            for x in [-.48,.45]:
                m.sphere((x,-.87,.10),(.10,.10,.10),"ice",10,16)
    if family in ("snow","sleet","cold"):
        for i in range(3 if family != "sleet" else 2):
            m.snowflake(-.45+i*.43, -.55-(i%2)*.16, .15 if heavy else .12)
    if family in ("fog","haze","dust","wind"):
        color = "sand" if family=="dust" else "lavender" if family=="haze" else "ice"
        for i in range(4 if heavy else 3):
            y = -.42-i*.14
            points = [(x, y+.03*math.sin(x*7+i), .09+i*.01)
                      for x in [-.64,-.48,-.32,-.16,0,.16,.32,.48,.64]]
            if family == "wind":
                points += [(.72,y+.08,.10),(.68,y+.17,.10),(.56,y+.16,.10)]
            m.tube(points,.033,color)
        if family == "dust":
            for i in range(8):
                m.sphere((-.64+i*.18,-.50-(i%3)*.16,.20),(.028,.028,.028),"rays",6,10)
    if family == "tornado":
        for i in range(7):
            radius = .45-i*.055
            y = -.28-i*.10
            m.tube([(radius*math.cos(t),y+.022*math.sin(t),radius*.3*math.sin(t))
                    for t in [j*math.tau/24 for j in range(25)]],.048,"lavender")
    if family == "unknown":
        m.tube([(-.08,-.42,.15),(0,-.36,.15),(.12,-.40,.15),(.13,-.51,.15),
                (0,-.58,.15),(0,-.65,.15)],.033,"lavender")
        m.sphere((0,-.76,.15),(.037,.037,.037),"lavender",8,12)
    return m


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    catalogue = []
    entries = []
    for family, labels in GROUPS:
        for level, label in enumerate(labels):
            name = f"{family}_{level:02d}"
            metrics = build(family,label,level).save(OUT / f"{name}.glb")
            catalogue.append({"weather":label,"family":family,"asset":f"{name}.glb",**metrics})
            entries.append(f'    "{label}" to "{name}",')
    (OUT/"catalogue.json").write_text(json.dumps(catalogue,ensure_ascii=False,indent=2)+"\n")
    kotlin = '''package com.pico.spatialweather.content

// Generated alongside the original GLB collection by tools/generate_weather_models.py.
private val weatherModels = mapOf(
'''+"\n".join(entries)+'''
)

fun weatherModel(condition: String): String {
    val normalized = condition.trim().replace("－", "-").replace("转", "-").replace("到", "-")
    weatherModels[normalized]?.let { return it }
    val family = when {
        normalized.contains("雷") -> "thunder"
        normalized.contains("雨夹雪") -> "sleet"
        normalized.contains("雪") -> "snow"
        normalized.contains("雨") -> "rain"
        normalized.contains("沙") || normalized.contains("尘") -> "dust"
        normalized.contains("霾") -> "haze"
        normalized.contains("雾") -> "fog"
        normalized.contains("龙卷") -> "tornado"
        normalized.contains("风") || normalized.contains("飑") -> "wind"
        normalized.contains("云") -> "cloud"
        normalized.contains("晴") -> "sun"
        normalized.contains("阴") -> "overcast"
        normalized.contains("热") -> "hot"
        normalized.contains("冷") -> "cold"
        else -> "unknown"
    }
    return "${family}_00"
}
'''
    (ROOT/"app/src/main/java/com/pico/spatialweather/content/WeatherModels.kt").write_text(kotlin)
    print(f"Generated {len(catalogue)} GLBs; {sum(c['bytes'] for c in catalogue)/1024/1024:.1f} MiB; "
          f"maximum {max(c['triangles'] for c in catalogue)} triangles/model")


if __name__ == "__main__":
    main()
