#!/usr/bin/env python3
"""
Recorta el arte de los personajes para el juego. Lee las dos imágenes de tools/arte-original/ y genera:

  assets/personajes.png        atlas de sprites: 4 filas (una por rol, en el orden de Jugador.ROLES) x 6 columnas:
                               frente-0, frente-1, lado-0, lado-1, espalda-0, espalda-1
                               (el lado SIEMPRE mira a la derecha; para ir a la izquierda el juego lo voltea)
  assets/retratos/<rol>.png    el retrato de cada rol (para la sala y la bienvenida)

Para cambiar el arte: reemplaza las imágenes de tools/arte-original/ y vuelve a ejecutar.
Si tu hoja nueva tiene otro orden o tamaño, ajusta las constantes de abajo.

    python3 tools/recortar_arte.py        (requiere Pillow, numpy y scipy)
"""
import os
import numpy as np
import scipy.ndimage as ndi
from PIL import Image

AQUI = os.path.dirname(os.path.abspath(__file__))
HOJA = os.path.join(AQUI, "arte-original", "hoja-personajes.png")
TARJETAS = os.path.join(AQUI, "arte-original", "tarjetas-roles.png")
ASSETS = os.path.join(AQUI, "..", "assets")

# Orden de los roles en el JUEGO (Jugador.ROLES) -> intervalo horizontal que ocupa en la hoja
# (en la hoja el orden es Arconte, Polites, Logógrafo, Sofista).
PERSONAJES = [
    ("polites",   (450, 735)),
    ("logografo", (815, 1100)),
    ("sofista",   (1185, 1485)),
    ("arconte",   (40, 345)),
]
# La hoja tiene 2 filas de sprites (arriba y abajo) y 3 vistas por fila: frente, lado, espalda.
FILAS_Y = [(284, 438), (440, 606)]
# La 2.ª fila es el mismo personaje dando un paso (sirve de segundo cuadro al caminar)...
# ...salvo en el Arconte, cuya 2.ª fila está mal dibujada (otro peinado): ahí se repite la 1.ª.
SEGUNDA_FILA_VALIDA = {"polites": True, "logografo": True, "sofista": True, "arconte": False}
# En el Arconte la vista lateral de la 1.ª fila mira a la izquierda: se voltea para que mire a la derecha.
VOLTEAR_LADO_FILA1 = {"arconte"}

# Píxeles que se recortan de más en cada borde del retrato para no arrastrar el marco de la tarjeta
MARGEN = 4

# Recuadro del arte de cada tarjeta (x0, y0, x1, y1) en tarjetas-roles.png
RETRATOS = {
    "arconte":   (25, 69, 423, 354),
    "polites":   (472, 69, 868, 354),
    "logografo": (916, 69, 1311, 354),
    "sofista":   (1358, 69, 1751, 354),
}


def extraer_sprites(hoja, x0, x1, y0, y1):
    """Devuelve los 3 sprites (frente, lado, espalda) de una fila, como imágenes RGBA recortadas."""
    zona = hoja.crop((x0, y0, x1, y1))
    alfa = np.array(zona)[:, :, 3] > 40
    unido = ndi.binary_dilation(alfa, iterations=4)  # une detalles sueltos (hojas de laurel, pendientes)
    etiquetas, n = ndi.label(unido)
    piezas = []
    for i, caja in enumerate(ndi.find_objects(etiquetas), start=1):
        if (etiquetas[caja] == i).sum() < 1500:
            continue
        sprite = np.array(zona)[caja].copy()
        dentro = ndi.binary_dilation(etiquetas[caja] == i, iterations=2)
        sprite[~dentro, 3] = 0  # borra cualquier resto de un vecino
        piezas.append((caja[1].start, Image.fromarray(sprite)))
    piezas.sort(key=lambda p: p[0])
    if len(piezas) != 3:
        raise SystemExit("Se esperaban 3 sprites en la fila y hay %d (x=%d..%d, y=%d..%d)" % (len(piezas), x0, x1, y0, y1))
    return [im.crop(im.getbbox()) for _, im in piezas]


def main():
    hoja = Image.open(HOJA).convert("RGBA")
    celdas = {}  # (rol, columna) -> sprite
    for rol, (x0, x1) in PERSONAJES:
        filas = [extraer_sprites(hoja, x0, x1, y0, y1) for y0, y1 in FILAS_Y]
        for vista in range(3):
            for cuadro in range(2):
                fila = filas[cuadro if SEGUNDA_FILA_VALIDA[rol] else 0]
                sprite = fila[vista]
                if vista == 1 and rol in VOLTEAR_LADO_FILA1:
                    sprite = sprite.transpose(Image.FLIP_LEFT_RIGHT)
                celdas[(rol, vista * 2 + cuadro)] = sprite

    ancho = max(s.width for s in celdas.values()) + 8
    alto = max(s.height for s in celdas.values()) + 8
    ancho += ancho % 2
    alto += alto % 2
    atlas = Image.new("RGBA", (ancho * 6, alto * len(PERSONAJES)), (0, 0, 0, 0))
    for f, (rol, _) in enumerate(PERSONAJES):
        for c in range(6):
            s = celdas[(rol, c)]
            a = np.array(s)[:, :, 3].astype(float)
            cx = (a.sum(axis=0) * np.arange(s.width)).sum() / a.sum()  # centro de masa: evita que "salten" al caminar
            px = int(round(ancho / 2 - cx))
            px = max(0, min(ancho - s.width, px))
            py = alto - s.height - 3  # los pies siempre a la misma altura
            atlas.alpha_composite(s, (c * ancho + px, f * alto + py))
    os.makedirs(ASSETS, exist_ok=True)
    atlas.save(os.path.join(ASSETS, "personajes.png"), optimize=True)
    print("personajes.png: celdas de %dx%d, atlas %dx%d" % (ancho, alto, atlas.width, atlas.height))

    tarjetas = Image.open(TARJETAS).convert("RGB")
    os.makedirs(os.path.join(ASSETS, "retratos"), exist_ok=True)
    for rol, caja in RETRATOS.items():
        x0, y0, x1, y1 = caja
        r = tarjetas.crop((x0 + MARGEN, y0 + MARGEN, x1 - MARGEN, y1 - MARGEN))  # sin la rendija del marco
        r.save(os.path.join(ASSETS, "retratos", rol + ".png"), optimize=True)
        print("retratos/%s.png: %dx%d" % (rol, r.width, r.height))


if __name__ == "__main__":
    main()
