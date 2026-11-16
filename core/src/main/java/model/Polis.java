package model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * La geografía de Helíade (el mapa assets/Polis.png): por dónde se puede caminar. Los lugares de acción
 * están en {@link Lugar}. Es Java puro (sin libGDX) para poder probar el mapa sin abrir el juego.
 *
 * Coordenadas en píxeles de la imagen del mapa, con origen arriba a la izquierda.
 */
public final class Polis {

    public static final float ANCHO = 1376f;
    public static final float ALTO = 768f;
    /** Radio con que los personajes chocan contra los bordes de las zonas caminables. */
    public static final float RADIO = 9f;

    /**
     * Zonas por donde se puede caminar: {x, y, ancho, alto}. Se solapan a propósito para que no haya cortes en
     * los cruces. Las primeras 15 las marcó el equipo a mano; el resto abre el camino al Metroon, al Pnyx,
     * a la Heliea y a la plaza del puerto.
     */
    private static final float[][] CAMINABLE = {
        {258, 0, 46, 390},      // calle vertical del barrio noroeste y cruce central
        {258, 120, 240, 46},    // conexión con la calle del barrio norte
        {458, 0, 46, 260},      // calle vertical al norte del Ágora
        {458, 218, 430, 48},    // calle entre el barrio norte y el teatro
        {858, 0, 48, 510},      // corredor vertical junto al teatro y lado oriental del Ágora
        {0, 354, 520, 52},      // puente y calle transversal occidental
        {286, 350, 610, 58},    // cruce transversal por el Ágora
        {286, 474, 618, 52},    // calle horizontal del barrio sur
        {660, 350, 52, 418},    // avenida vertical desde el Ágora al borde sur
        {872, 474, 348, 52},    // corredor oriental hacia el puerto
        {1144, 474, 66, 176},   // acceso y muelle del puerto
        {176, 622, 1044, 56},   // avenida sur
        {176, 570, 48, 110},    // conexión del barrio suroeste con la avenida sur
        {630, 230, 270, 60},    // acceso pavimentado que enlaza con el Ágora
        {492, 260, 392, 218},   // plaza central del Ágora
        // ---- nuevas
        {640, 128, 100, 130},   // escalinata y frente del Metroon
        {880, 244, 210, 36},    // camino hacia el anfiteatro
        {1050, 170, 44, 110},   // túnel de entrada al anfiteatro
        {1010, 112, 120, 80},   // la orquesta del anfiteatro (Pnyx)
        {1000, 395, 56, 110},   // escalinata de la Heliea
        {900, 405, 240, 50},    // explanada frente a la Heliea
        {1100, 474, 140, 190}   // plaza pavimentada del puerto
    };

    private Polis() {
    }

    public static boolean caminable(float x, float y) {
        for (float[] z : CAMINABLE) {
            if (x >= z[0] + RADIO && x <= z[0] + z[2] - RADIO && y >= z[1] + RADIO && y <= z[1] + z[3] - RADIO) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ pruebas de conectividad

    /**
     * Revisa que todos los lugares y puntos de inicio sean caminables y estén conectados entre sí
     * (búsqueda en anchura sobre una cuadrícula de 2 px: detecta cualquier hueco mayor). Devuelve la lista de problemas (vacía si todo bien).
     */
    public static List<String> verificarConectividad() {
        List<String> problemas = new ArrayList<>();
        float[][] inicio = Lugar.INICIO;
        for (Lugar l : Lugar.values()) {
            if (!caminable(l.x, l.y)) problemas.add("El centro de " + l.nombre + " no es caminable");
        }
        for (int i = 0; i < inicio.length; i++) {
            if (!caminable(inicio[i][0], inicio[i][1])) problemas.add("El inicio " + i + " no es caminable");
        }
        if (!problemas.isEmpty()) return problemas;

        boolean[][] alcanzado = inundar(inicio[0][0], inicio[0][1]);
        for (Lugar l : Lugar.values()) {
            if (!alcanzado[celda(l.y)][celda(l.x)]) problemas.add(l.nombre + " no se puede alcanzar desde el Ágora");
        }
        for (int i = 1; i < inicio.length; i++) {
            if (!alcanzado[celda(inicio[i][1])][celda(inicio[i][0])]) problemas.add("El inicio " + i + " está aislado");
        }
        return problemas;
    }

    private static final int PASO = 2;

    private static int celda(float v) {
        return Math.round(v / PASO);
    }

    private static boolean[][] inundar(float x0, float y0) {
        int cols = celda(ANCHO) + 1;
        int filas = celda(ALTO) + 1;
        boolean[][] visto = new boolean[filas][cols];
        Deque<int[]> cola = new ArrayDeque<>();
        visto[celda(y0)][celda(x0)] = true;
        cola.add(new int[]{celda(x0), celda(y0)});
        while (!cola.isEmpty()) {
            int[] c = cola.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    int nx = c[0] + dx;
                    int ny = c[1] + dy;
                    if (nx < 0 || ny < 0 || nx >= cols || ny >= filas || visto[ny][nx]) continue;
                    if (!caminable(nx * PASO, ny * PASO)) continue;
                    visto[ny][nx] = true;
                    cola.add(new int[]{nx, ny});
                }
            }
        }
        return visto;
    }
}
