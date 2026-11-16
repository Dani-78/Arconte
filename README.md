# Arconte · La voz de Helíade

Juego sobre rumores, desinformación y poder, ambientado en la antigua Grecia. Cada jugador juega desde **su propio dispositivo**.

Roles: **Polites** (ciudadano), **Logógrafo** (periodista), **Sofista** (influencer) y **Arconte** (candidato a alcalde).

## Cómo ejecutarlo

Necesitas JDK 17 o superior y Gradle.

```
gradle lwjgl3:run
```

Para repartirlo a tus compañeros sin que instalen nada más que Java:

```
gradle lwjgl3:installDist
```

Eso genera `lwjgl3/build/install/Arconte/` (con `bin/Arconte` y `bin/Arconte.bat`). Comprime esa carpeta y compártela; el mapa va incluido.

## Cómo se juega en red

1. **Uno crea la sala** (botón "Crear sala"). Ese programa hace de servidor, así que debe quedarse abierto toda la partida.
2. La pantalla de la sala muestra la **dirección IP** del anfitrión. Los demás escriben esa dirección y el puerto (5050 por defecto) y pulsan "Unirme a una sala".
3. Cada uno elige su rol. Los roles que nadie elija los juegan **bots**. El anfitrión pulsa "Comenzar partida".
4. Si alguien se desconecta, **un bot toma su lugar** y la partida sigue.

Requisitos de red:

- Todos en la **misma red Wi-Fi**, o conectados por una VPN (Tailscale, ZeroTier, Radmin VPN).
- Las redes de universidades suelen **aislar los dispositivos entre sí**. Si no conecta, prueben con el punto de acceso del celular de uno de ustedes.
- La primera vez, Windows pregunta si permitir el acceso a la red de Java: hay que aceptar (solo el anfitrión lo necesita).
- Para jugar por Internet sin VPN, el anfitrión tendría que abrir el puerto 5050 en su router.

## Cómo se juega

Cada turno sale un rumor en el ágora. **No hay temporizador**: el turno se resuelve cuando quien lo tiene actúa, y después de ver el resultado **cada jugador pulsa "Continuar"** (o Enter/Espacio). Si alguien se queda dormido, el anfitrión puede pulsar "Saltar turno".

Te mueves por Helíade con **WASD o las flechas**, y **cada acción se hace en un lugar del mapa**. Los lugares que te sirven en tu turno brillan:

| Acción | Dónde | Detalle |
|---|---|---|
| **Pregonar** | **Ágora** | Lo cuentas a tu círculo. Si es falso, sube Apate (el engaño). Sofista: **Declamar** (llega más lejos). |
| **Indagar** | **Puerto** | Preguntas a marineros y mercaderes, junto al tablón de anuncios; puedes equivocarte. Logógrafo: **Consultar el Metroon** (templo del norte; nunca se equivoca). |
| **Denunciar** | **Heliea** | El tribunal (templo del este). Sirve si el rumor es falso; si era cierto, es una acusación injustificada. |
| **Callar** | Donde sea | No haces nada. |
| **Discurso en la Pnyx** | **Pnyx** | Solo el Arconte (anfiteatro). No puede darlo dos turnos propios seguidos: sus *hetairoi* (su círculo político de campaña) le aconsejan esperar. |

Al empezar la partida cada jugador ve una **bienvenida personalizada** con su retrato, su rol y lo que debe hacer. En la parte baja del mapa se explica qué era, en la antigua Grecia, el lugar donde estás parado.

Después de indagar sigues en tu turno: te enteras de lo que averiguaste y decides si pregonar, callar o denunciar (caminando al lugar que corresponda).

Hay presagios al empezar cada turno (Ossa, Apate, Hermes, Eris, las Dionisias, la tablilla).
La partida dura 16 rumores. Al final:

- **La elección:** gana el Arconte si la polis salió bien y él no cayó en desgracia; si no, gana un rival.
- **Destino personal:** *cicuta* (muchas mentiras), *ostracismo* (mala reputación), *bendición de Hermes* (verdad sostenida en una polis que confía en la palabra) o *olvido tranquilo*.

## Estructura

```
core/src/main/java/model/   reglas del juego y geografía del mapa (sin red ni gráficos)
core/src/main/java/net/     protocolo, servidor y cliente
core/src/main/java/view/    pantallas (libGDX)
core/src/test/java/net/     prueba de integración sin interfaz
assets/Polis.png            el mapa; assets/fuentes/ la letra; assets/personajes.png y assets/retratos/ el arte
tools/generar_fuentes.py    regenera las fuentes si quieres otros tamaños
tools/recortar_arte.py      regenera los sprites y retratos desde tools/arte-original/
lwjgl3/                     lanzador de escritorio
legacy-swing/               la versión Swing original de un solo dispositivo (ya no se compila)
```

El **servidor es autoritativo**: el único motor de juego vive en la máquina del anfitrión. Los clientes solo envían lo que quieren hacer y reciben una instantánea del estado, que **no incluye si el rumor es verdadero**, así que nadie puede hacer trampa leyendo la red.

## Prueba de la parte de red

```
javac -encoding UTF-8 -d /tmp/arconte-test core/src/main/java/model/*.java core/src/main/java/net/*.java core/src/test/java/net/*.java
java -cp /tmp/arconte-test net.ArconteSelfTest
```

Levanta un servidor real, conecta clientes por socket y comprueba: la sala (cupo, roles, nombres), que el mapa sea caminable hasta los 5 lugares, que cada acción solo se acepte en su lugar, el botón "Continuar", el salto de turno del anfitrión, el discurso del Arconte con el consejo de los hetairoi, acciones ilegales y una desconexión a mitad de partida.

## Arte: personajes y retratos

Los muñequitos del mapa y los retratos de la sala salen de **dos imágenes** que están en `tools/arte-original/`:

- `hoja-personajes.png`: la hoja con los 4 personajes (3 vistas: frente, lado y espalda; 2 filas).
- `tarjetas-roles.png`: las tarjetas de los roles (de ahí se recorta el retrato de cada una).

`tools/recortar_arte.py` los convierte en lo que usa el juego: `assets/personajes.png` (un atlas con todos los sprites) y `assets/retratos/<rol>.png`. **Para cambiar el arte**: reemplaza esas imágenes y ejecuta `python3 tools/recortar_arte.py` (requiere Pillow, numpy y scipy). Si tu hoja nueva tiene otro orden, otro tamaño o los personajes miran para otro lado, ajusta las constantes del principio del script (están comentadas).

Notas sobre la hoja actual: la 2.ª fila de cada personaje es el mismo dibujo dando un paso, y se usa como segundo cuadro al caminar; la 2.ª fila del Arconte tiene otro peinado, así que él solo usa la 1.ª (se mueve con un pequeño rebote). La vista lateral siempre mira a la derecha; para ir a la izquierda el juego la voltea.

## Mapa y fuentes

- **Mapa:** los lugares están en `model/Lugar.java` (círculos sobre `Polis.png`) y las zonas por donde se camina en `model/Polis.java`. Si cambian el mapa, ajusten esas coordenadas; el test comprueba que todos los lugares sigan siendo alcanzables a pie.
- **Fuentes:** la letra es **Inter** (licencia SIL OFL, en `assets/fuentes/LICENCIA-Inter.txt`), "horneada" a su tamaño real en `assets/fuentes/` para que se vea nítida y no borrosa. Para cambiar tamaños, edita `TAMANOS` en `tools/generar_fuentes.py` y ejecútalo (requiere Pillow y fontTools).
