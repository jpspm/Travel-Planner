"""Gera o ícone adaptativo do Android a partir do desenho de design/icon.svg.

Caderno aberto: pin com caminho tracejado na página da esquerda, checklist na da direita. O fundo azul
vai na camada de fundo; páginas e detalhes no primeiro plano, na mesma posição do quadrado arredondado
do SVG (o quadrado de 512 vira a área visível de 72 dp do ícone de 108 dp).

No ícone monocromático (ícones temáticos do Android 13+) tudo tem uma cor só, então as páginas viram
contornos e os detalhes ficam por cima, sem preenchimento.

Uso, na raiz do projeto: python design/make_android_icon.py
"""
from pathlib import Path

RES = Path(__file__).resolve().parent.parent / "androidApp" / "src" / "main" / "res"

BLUE = "#FF4288EA"
WHITE = "#FFFFFFFF"
NAVY = "#FF1B2E5C"

SCALE = 72 / 512  # o quadrado do SVG ocupa a área visível (72 dp) do canvas de 108 dp
OFFSET = 54 - 256 * SCALE

LEFT_PAGE = ("M80,146 Q80,128 97,133.6 L244,182.2 Q249.5,184 249.5,189 L249.5,417 Q249.5,422 244.5,420.2 "
             "L97,368 Q80,362 80,345 Z")
RIGHT_PAGE = ("M432,146 Q432,128 415,133.6 L268,182.2 Q262.5,184 262.5,189 L262.5,417 Q262.5,422 267.5,420.2 "
              "L415,368 Q432,362 432,345 Z")
CHECKS = ("M291.8,226.6 L301.6,235 L317.7,216.7 M291.8,279.2 L301.6,287.6 L317.7,269.3 "
          "M291.8,331.8 L301.6,340.2 L317.7,321.9")
LINES = "M338.8,221.9 L403.3,207.9 M338.8,274.3 L403.3,260.2 M338.8,326.2 L403.3,310.7"
PIN = ("M147.3,274 L116.7,228.9 A36.5,36.5 0 1 1 177.9,228.9 Z "
       "M160.6,212.5 A13.3,13.3 0 1 0 134,212.5 A13.3,13.3 0 1 0 160.6,212.5 Z")
# O vector drawable não tem tracejado: os traços do caminho são segmentos curtos ao longo da curva.
ROUTE = (147.3, 293, 150, 315, 170, 326, 196, 334), (196, 334, 218, 341, 232, 355, 236, 376)
DASH, GAP = 10, 17


def cubic(seg, t):
    x0, y0, x1, y1, x2, y2, x3, y3 = seg
    u = 1 - t
    return (u ** 3 * x0 + 3 * u * u * t * x1 + 3 * u * t * t * x2 + t ** 3 * x3,
            u ** 3 * y0 + 3 * u * u * t * y1 + 3 * u * t * t * y2 + t ** 3 * y3)


def dashes():
    points = [cubic(seg, i / 200) for seg in ROUTE for i in range(201)]
    parts, walked, drawing, current = [], 0.0, True, [points[0]]
    for (ax, ay), (bx, by) in zip(points, points[1:]):
        walked += ((bx - ax) ** 2 + (by - ay) ** 2) ** 0.5
        if drawing:
            current.append((bx, by))
        limit = DASH if drawing else GAP
        if walked >= limit:
            if drawing:
                parts.append(current)
            drawing, walked, current = not drawing, 0.0, [(bx, by)]
    if drawing and len(current) > 1:
        parts.append(current)
    return " ".join("M" + " L".join(f"{x:.1f},{y:.1f}" for x, y in part) for part in parts)


def stroke(d, color, width):
    return (f'        <path android:pathData="{d}" android:strokeColor="{color}" android:strokeWidth="{width}"\n'
            f'            android:strokeLineCap="round" android:strokeLineJoin="round" />\n')


def fill(d, color, even_odd=False):
    rule = ' android:fillType="evenOdd"' if even_odd else ""
    return (f'        <path android:pathData="{d}" android:fillColor="{color}"{rule}\n'
            f'            android:strokeColor="{color}" android:strokeWidth="3" android:strokeLineJoin="round" />\n')


def vector(body):
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<!-- Gerado por design/make_android_icon.py a partir de design/icon.svg. -->\n'
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    android:width="108dp" android:height="108dp"\n'
        '    android:viewportWidth="108" android:viewportHeight="108">\n'
        f"{body}</vector>\n"
    )


def foreground(mono):
    ink = WHITE if mono else NAVY
    body = f'    <group android:translateX="{OFFSET:.3f}" android:translateY="{OFFSET:.3f}" android:scaleX="{SCALE:.5f}" android:scaleY="{SCALE:.5f}">\n'
    if mono:
        body += stroke(LEFT_PAGE, WHITE, 10) + stroke(RIGHT_PAGE, WHITE, 10)
    else:
        body += fill(LEFT_PAGE, WHITE) + fill(RIGHT_PAGE, WHITE)
    body += stroke(dashes(), ink, 9)
    body += stroke(CHECKS, ink, 7.5)
    body += stroke(LINES, ink, 8.5)
    body += fill(PIN, ink, even_odd=True)
    return vector(body + "    </group>\n")


BACKGROUND = vector(f'    <path android:pathData="M0,0 H108 V108 H0 Z" android:fillColor="{BLUE}" />\n')

ADAPTIVE = (
    '<?xml version="1.0" encoding="utf-8"?>\n'
    '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
    '    <background android:drawable="@drawable/ic_launcher_background" />\n'
    '    <foreground android:drawable="@drawable/ic_launcher_foreground" />\n'
    '    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />\n'
    "</adaptive-icon>\n"
)

if __name__ == "__main__":
    (RES / "drawable" / "ic_launcher_background.xml").write_text(BACKGROUND, encoding="utf-8")
    (RES / "drawable" / "ic_launcher_foreground.xml").write_text(foreground(mono=False), encoding="utf-8")
    (RES / "drawable" / "ic_launcher_monochrome.xml").write_text(foreground(mono=True), encoding="utf-8")
    for name in ("ic_launcher.xml", "ic_launcher_round.xml"):
        (RES / "mipmap-anydpi-v26" / name).write_text(ADAPTIVE, encoding="utf-8")
    print("icone gerado em", RES)
