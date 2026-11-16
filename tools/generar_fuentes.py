#!/usr/bin/env python3
"""
Genera las fuentes del juego en formato BMFont (.fnt + .png), que libGDX lee directamente.

Por qué: la fuente por defecto de libGDX es de 15 px y al agrandarla se ve borrosa. Aquí cada tamaño se
hornea a su tamaño REAL, así que se ve nítida. Si quieres otros tamaños, edita TAMANOS y vuelve a ejecutar:

    python3 tools/generar_fuentes.py [ruta-a-una-fuente.otf|ttf]

Requiere Pillow y fontTools. Por defecto usa Inter Medium (licencia SIL OFL 1.1).
"""
import os
import sys
from fontTools.ttLib import TTFont
from PIL import Image, ImageDraw, ImageFont

FUENTE = sys.argv[1] if len(sys.argv) > 1 else "/usr/share/fonts/opentype/inter/Inter-Medium.otf"
SALIDA = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "assets", "fuentes")
TAMANOS = {"chica": 18, "normal": 23, "grande": 31, "titulo": 48}

# ASCII, Latin-1 (tildes, ñ, ¿, ¡) y signos tipográficos usados por el juego
CARACTERES = list(range(32, 127)) + list(range(160, 256)) + [
    0x2013, 0x2014, 0x2018, 0x2019, 0x201C, 0x201D, 0x2022, 0x2026]


def generar(nombre, tam):
    cmap = TTFont(FUENTE, fontNumber=0).getBestCmap()
    fuente = ImageFont.truetype(FUENTE, tam)
    ascent, descent = fuente.getmetrics()
    ancho_atlas = alto_atlas = 1024

    atlas = Image.new("RGBA", (ancho_atlas, alto_atlas), (255, 255, 255, 0))
    x = y = fila_alto = 0
    glifos = []
    for cp in CARACTERES:
        if cp not in cmap:
            continue  # la fuente no tiene ese carácter
        ch = chr(cp)
        avance = int(round(fuente.getlength(ch)))
        l, t, r, b = fuente.getbbox(ch, anchor="ls")  # relativo a la línea base
        w, h = max(0, r - l), max(0, b - t)
        if w == 0 or h == 0:
            glifos.append((cp, 0, 0, 0, 0, 0, 0, avance))
            continue
        if x + w + 2 > ancho_atlas:
            x, y, fila_alto = 0, y + fila_alto + 2, 0
        if y + h + 2 > alto_atlas:
            raise SystemExit("El atlas de 1024x1024 se quedó corto para " + nombre)
        mascara = Image.new("L", (w, h), 0)
        ImageDraw.Draw(mascara).text((-l, -t), ch, font=fuente, fill=255, anchor="ls")
        tile = Image.new("RGBA", (w, h), (255, 255, 255, 0))
        tile.putalpha(mascara)  # blanco con alfa: así se puede teñir de cualquier color
        atlas.paste(tile, (x, y))
        glifos.append((cp, x, y, w, h, l, ascent + t, avance))
        x += w + 2
        fila_alto = max(fila_alto, h)

    os.makedirs(SALIDA, exist_ok=True)
    png = "fuente-%s.png" % nombre
    atlas.save(os.path.join(SALIDA, png))
    with open(os.path.join(SALIDA, "fuente-%s.fnt" % nombre), "w", encoding="utf-8", newline="\n") as f:
        f.write('info face="Inter" size=%d bold=0 italic=0 charset="" unicode=1 stretchH=100 smooth=1 aa=1 '
                'padding=0,0,0,0 spacing=0,0\n' % tam)
        f.write("common lineHeight=%d base=%d scaleW=%d scaleH=%d pages=1 packed=0\n"
                % (ascent + descent, ascent, ancho_atlas, alto_atlas))
        f.write('page id=0 file="%s"\n' % png)
        f.write("chars count=%d\n" % len(glifos))
        for cp, gx, gy, gw, gh, xo, yo, av in glifos:
            f.write("char id=%d x=%d y=%d width=%d height=%d xoffset=%d yoffset=%d xadvance=%d page=0 chnl=15\n"
                    % (cp, gx, gy, gw, gh, xo, yo, av))
    print("fuente-%s: %dpx, %d glifos" % (nombre, tam, len(glifos)))


if __name__ == "__main__":
    for n, t in TAMANOS.items():
        generar(n, t)
