"""Gera o ícone adaptativo do Android a partir do desenho de design/icon.svg (estilo de linha).

Fundo liso; caderno em traço branco de 20 px; avião preenchido saindo do canto, com um vão na cor do
fundo separando os dois. No ícone monocromático (ícones temáticos do Android 13+) não existe "cor do
fundo", então o contorno do caderno já é desenhado interrompido no canto onde fica o avião.

Uso, na raiz do projeto: python design/make_android_icon.py
"""
from pathlib import Path

RES = Path(__file__).resolve().parent.parent / "androidApp" / "src" / "main" / "res"

BG = "#FF00658E"
WHITE = "#FFFFFFFF"

# Coordenadas do SVG (512 × 512), já com o deslocamento (-14, 18) do grupo principal aplicado abaixo.
OFFSET = (-14.0, 18.0)
CENTER = (256.0, 258.0)  # centro do conteúdo depois do deslocamento
SCALE = 0.145  # cabe na zona segura de 66 dp das máscaras redondas (canvas de 108 dp)

PLANE = ("M21 16v-2l-8-5V3.5c0-.83-.67-1.5-1.5-1.5S10 2.67 10 3.5V9l-8 5v2l8-2.5V19l-2 1.5V22"
         "l3.5-1 3.5 1v-1.5L13 19v-5.5l8 2.5z")

NOTEBOOK = "M160,132 H304 A28,28 0 0 1 332,160 V372 A28,28 0 0 1 304,400 H160 A28,28 0 0 1 132,372 V160 A28,28 0 0 1 160,132 Z"
# Mesmo contorno, aberto no canto superior direito (onde o avião passa), para o monocromático.
NOTEBOOK_OPEN = "M268,132 H160 A28,28 0 0 0 132,160 V372 A28,28 0 0 0 160,400 H304 A28,28 0 0 0 332,372 V246"
RINGS = "M112,182 h40 M112,242 h40 M112,302 h40 M112,362 h40"
LINES = "M196,214 h64 M196,266 h96 M196,318 h60"


def stroke(d, width=20):
    return (f'        <path android:pathData="{d}" android:strokeColor="{WHITE}" android:strokeWidth="{width}"\n'
            f'            android:strokeLineCap="round" android:strokeLineJoin="round" />\n')


def plane(fill_knockout):
    body = '        <group android:translateX="368" android:translateY="148" android:rotation="45" android:scaleX="8.4" android:scaleY="8.4">\n'
    body += '            <group android:translateX="-11.5" android:translateY="-12">\n'
    if fill_knockout:
        body += (f'                <path android:pathData="{PLANE}" android:fillColor="{BG}" android:strokeColor="{BG}"\n'
                 '                    android:strokeWidth="5" android:strokeLineJoin="round" />\n')
    body += (f'                <path android:pathData="{PLANE}" android:fillColor="{WHITE}" android:strokeColor="{WHITE}"\n'
             '                    android:strokeWidth="0.8" android:strokeLineJoin="round" />\n')
    return body + "            </group>\n        </group>\n"


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
    tx = 54 + (OFFSET[0] - CENTER[0]) * SCALE
    ty = 54 + (OFFSET[1] - CENTER[1]) * SCALE
    body = f'    <group android:translateX="{tx:.3f}" android:translateY="{ty:.3f}" android:scaleX="{SCALE}" android:scaleY="{SCALE}">\n'
    body += stroke(NOTEBOOK_OPEN if mono else NOTEBOOK)
    body += stroke(RINGS)
    body += stroke(LINES)
    body += plane(fill_knockout=not mono)
    return vector(body + "    </group>\n")


BACKGROUND = vector(f'    <path android:pathData="M0,0 H108 V108 H0 Z" android:fillColor="{BG}" />\n')

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
