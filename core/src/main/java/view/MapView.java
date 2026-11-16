package view;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Widget;
import com.badlogic.gdx.utils.Align;
import model.Lugar;
import model.Polis;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Mapa navegable de Helíade. Cada jugador mueve SOLO su propio personaje (WASD o flechas) y ve moverse a los
 * demás: la vista envía su posición al servidor y recibe la de los otros con {@link #setRemote}.
 *
 * El mapa es parte del juego: las acciones se hacen en lugares (Ágora, Puerto, Metroon, Heliea, Pnyx). Aquí se
 * dibujan con su nombre, y los que le sirven al jugador en su turno se resaltan con un pulso.
 * Por dónde se camina lo decide {@link Polis}.
 */
public class MapView extends Widget {
    private static final float WALK_SPEED = 320f;
    /** Velocidad con que los demás jugadores "caminan" hacia la posición que reportan. */
    private static final float REMOTE_SPEED = 420f;
    /** Altura de un personaje en unidades del mapa; en pantalla se limita para que siempre se vea bien. */
    private static final float ALTURA_PERSONAJE = 105f;
    private static final int FRENTE = 0;
    private static final int LADO = 1;
    private static final int ESPALDA = 2;
    private static final Color OBJETIVO = Color.valueOf("ffd23f");

    private final Texture mapTexture;
    private static final int PERSONAJES = 4;
    private final Texture atlas;
    /** frames[personaje][vista * 2 + cuadro]: 3 vistas (frente, lado, espalda) x 2 cuadros de caminata. */
    private final TextureRegion[][] frames = new TextureRegion[PERSONAJES][6];
    private final Texture turnRing;
    private final Texture placeRing;
    private final Texture disc;
    private final BitmapFont font; // prestada: la crea y la libera quien construye la vista
    private final String[] names;
    private final float[] xs;       // posición real (la del jugador local) o destino (los demás)
    private final float[] ys;
    private final float[] px = new float[PERSONAJES];   // posición que se dibuja
    private final float[] py = new float[PERSONAJES];
    private final int[] vista = new int[PERSONAJES];
    private final boolean[] haciaIzquierda = new boolean[PERSONAJES];
    private final float[] ultimoPaso = new float[PERSONAJES];
    private final int localIndex;
    private Set<Lugar> objetivos = EnumSet.noneOf(Lugar.class);
    private int turnIndex = -1;
    private float tiempo;
    private boolean moved;
    private boolean up;
    private boolean down;
    private boolean left;
    private boolean right;

    /**
     * @param names      nombres de los personajes, en el orden de los roles (el índice decide forma y color)
     * @param localIndex cuál de ellos controla este jugador
     * @param font       fuente para los nombres (chica y nítida)
     */
    public MapView(List<String> names, int localIndex, BitmapFont font) {
        this.font = font;
        mapTexture = new Texture(Gdx.files.internal("Polis.png"));
        mapTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        // los sprites se dibujan más chicos que su tamaño original: con mipmaps se ven suaves y sin dientes
        atlas = new Texture(Gdx.files.internal("personajes.png"), true);
        atlas.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
        int cw = atlas.getWidth() / 6;
        int ch = atlas.getHeight() / PERSONAJES;
        for (int f = 0; f < PERSONAJES; f++) {
            for (int c = 0; c < 6; c++) frames[f][c] = new TextureRegion(atlas, c * cw, f * ch, cw, ch);
        }
        turnRing = makeRing(64, 26, 30);
        placeRing = makeRing(128, 59, 63);
        disc = makeDisc();

        this.names = new String[PERSONAJES];
        this.xs = new float[PERSONAJES];
        this.ys = new float[PERSONAJES];
        for (int i = 0; i < PERSONAJES; i++) {
            this.names[i] = i < names.size() ? names.get(i) : "";
            xs[i] = Lugar.INICIO[i][0];
            ys[i] = Lugar.INICIO[i][1];
            px[i] = xs[i];
            py[i] = ys[i];
            ultimoPaso[i] = -10f;
        }
        this.localIndex = Math.max(0, Math.min(PERSONAJES - 1, localIndex));
        setTouchable(Touchable.disabled);
        setSize(800, 450);
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        tiempo += delta;
        moverLocal(delta);
        moverRemotos(delta);
    }

    private void moverLocal(float delta) {
        float dx = (right ? 1f : 0f) - (left ? 1f : 0f);
        float dy = (down ? 1f : 0f) - (up ? 1f : 0f);
        if (dx == 0f && dy == 0f) return;
        if (dx != 0f && dy != 0f) {
            dx *= 0.70710678f;
            dy *= 0.70710678f;
        }
        float step = WALK_SPEED * Math.min(delta, 0.05f);
        float nextX = clamp(xs[localIndex] + dx * step, Polis.RADIO, Polis.ANCHO - Polis.RADIO);
        float nextY = clamp(ys[localIndex] + dy * step, Polis.RADIO, Polis.ALTO - Polis.RADIO);
        boolean avanzo = false;
        // cada eje por separado: así el personaje se desliza a lo largo de las paredes en vez de trabarse
        if (Polis.caminable(nextX, ys[localIndex])) {
            xs[localIndex] = nextX;
            avanzo = true;
        }
        if (Polis.caminable(xs[localIndex], nextY)) {
            ys[localIndex] = nextY;
            avanzo = true;
        }
        if (avanzo) {
            moved = true;
            px[localIndex] = xs[localIndex];
            py[localIndex] = ys[localIndex];
            mirar(localIndex, dx, dy);
        }
    }

    /** Los demás (humanos y bots) caminan hacia su destino en línea recta en vez de aparecer de golpe. */
    private void moverRemotos(float delta) {
        for (int i = 0; i < PERSONAJES; i++) {
            if (i == localIndex) continue;
            float dx = xs[i] - px[i];
            float dy = ys[i] - py[i];
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            if (dist < 0.5f) {
                px[i] = xs[i];
                py[i] = ys[i];
                continue;
            }
            float paso = Math.min(dist, REMOTE_SPEED * delta);
            px[i] += dx / dist * paso;
            py[i] += dy / dist * paso;
            mirar(i, dx, dy);
        }
    }

    /** Elige la vista del personaje según hacia dónde camina, y marca que acaba de dar un paso. */
    private void mirar(int i, float dx, float dy) {
        ultimoPaso[i] = tiempo;
        if (Math.abs(dx) > Math.abs(dy)) {
            vista[i] = LADO;
            haciaIzquierda[i] = dx < 0;
        } else {
            vista[i] = dy > 0 ? FRENTE : ESPALDA;
        }
    }

    public void setDirection(int keycode, boolean pressed) {
        switch (keycode) {
            case Input.Keys.UP:
            case Input.Keys.W: up = pressed; break;
            case Input.Keys.DOWN:
            case Input.Keys.S: down = pressed; break;
            case Input.Keys.LEFT:
            case Input.Keys.A: left = pressed; break;
            case Input.Keys.RIGHT:
            case Input.Keys.D: right = pressed; break;
            default: break;
        }
    }

    /** Posición de otro jugador recibida por la red. */
    public void setRemote(int index, float x, float y) {
        if (index < 0 || index >= xs.length || index == localIndex) return;
        xs[index] = x;
        ys[index] = y;
    }

    /** Quién tiene el turno (se le dibuja un aro amarillo). */
    public void setTurnIndex(int index) {
        turnIndex = index;
    }

    /** Lugares a los que le conviene ir al jugador local (se resaltan con un pulso). */
    public void setObjetivos(Set<Lugar> lugares) {
        objetivos = lugares.isEmpty() ? EnumSet.noneOf(Lugar.class) : EnumSet.copyOf(lugares);
    }

    /** true si el personaje local se movió desde la última vez que se preguntó (para enviar su posición). */
    public boolean consumeMoved() {
        boolean m = moved;
        moved = false;
        return m;
    }

    public float getLocalX() {
        return xs[localIndex];
    }

    public float getLocalY() {
        return ys[localIndex];
    }

    /** El lugar donde está parado el personaje local, o null. */
    public Lugar getLugarLocal() {
        return Lugar.en(xs[localIndex], ys[localIndex]);
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        validate();
        MapBounds bounds = mapBounds();
        float mapX = getX() + bounds.offsetX;
        float mapY = getY() + bounds.offsetY;
        batch.setColor(Color.WHITE);
        batch.draw(mapTexture, mapX, mapY, Polis.ANCHO * bounds.scale, Polis.ALTO * bounds.scale);

        drawLugares(batch, bounds, mapX, mapY);

        // se dibujan de arriba hacia abajo en pantalla: el que está más "al fondo" queda detrás
        Integer[] orden = {0, 1, 2, 3};
        java.util.Arrays.sort(orden, (a, b) -> {
            int c = Float.compare(py[a], py[b]);
            if (c != 0) return c;
            return Boolean.compare(a == localIndex, b == localIndex);
        });
        for (int i : orden) drawPersonaje(batch, bounds, mapX, mapY, i);
        batch.setColor(Color.WHITE);
    }

    private void drawLugares(Batch batch, MapBounds bounds, float mapX, float mapY) {
        float pulso = 0.5f + 0.5f * (float) Math.sin(tiempo * 4.0);
        for (Lugar lugar : Lugar.values()) {
            float cx = mapX + lugar.x * bounds.scale;
            float cy = mapY + (Polis.ALTO - lugar.y) * bounds.scale;
            float r = lugar.radio * bounds.scale;
            boolean esObjetivo = objetivos.contains(lugar);
            boolean aqui = lugar == getLugarLocal();
            if (esObjetivo) {
                batch.setColor(OBJETIVO.r, OBJETIVO.g, OBJETIVO.b, 0.12f + 0.16f * pulso);
                batch.draw(disc, cx - r, cy - r, r * 2, r * 2);
                batch.setColor(OBJETIVO.r, OBJETIVO.g, OBJETIVO.b, 0.65f + 0.35f * pulso);
            } else {
                batch.setColor(1f, 1f, 1f, aqui ? 0.75f : 0.4f);
            }
            batch.draw(placeRing, cx - r, cy - r, r * 2, r * 2);

            // nombre del lugar, con sombra para que se lea sobre cualquier fondo
            float ty = cy + r + 22f;
            font.setColor(0f, 0f, 0f, 0.9f);
            font.draw(batch, lugar.nombre, cx + 1f, ty - 1f, 0f, Align.center, false);
            font.setColor(esObjetivo ? OBJETIVO : Color.WHITE);
            font.draw(batch, lugar.nombre, cx, ty, 0f, Align.center, false);
        }
        font.setColor(Color.WHITE);
    }

    private void drawPersonaje(Batch batch, MapBounds bounds, float mapX, float mapY, int i) {
        float feetX = mapX + px[i] * bounds.scale;
        float feetY = mapY + (Polis.ALTO - py[i]) * bounds.scale;
        float h = clamp(ALTURA_PERSONAJE * bounds.scale, 52f, 110f);
        TextureRegion celda = frames[i][0];
        float w = h * celda.getRegionWidth() / (float) celda.getRegionHeight();

        boolean caminando = tiempo - ultimoPaso[i] < 0.18f;
        int cuadro = caminando ? ((int) (tiempo * 7f)) % 2 : 0;
        float salto = caminando ? Math.abs((float) Math.sin(tiempo * 14f)) * h * 0.035f : 0f;

        // sombra en el suelo y, si es su turno, aro amarillo
        batch.setColor(0f, 0f, 0f, 0.28f);
        batch.draw(disc, feetX - w * 0.30f, feetY - h * 0.05f, w * 0.60f, h * 0.12f);
        if (i == turnIndex) {
            batch.setColor(Color.YELLOW);
            batch.draw(turnRing, feetX - w * 0.55f, feetY - h * 0.13f, w * 1.10f, h * 0.26f);
        }

        // el sprite tiene unos píxeles de margen bajo los pies; se corrige para que pise exactamente su punto
        float suelo = h * 3f / celda.getRegionHeight();
        TextureRegion region = frames[i][vista[i] * 2 + cuadro];
        batch.setColor(Color.WHITE);
        if (vista[i] == LADO && haciaIzquierda[i]) {
            batch.draw(region, feetX + w / 2f, feetY - suelo + salto, -w, h); // ancho negativo = espejo
        } else {
            batch.draw(region, feetX - w / 2f, feetY - suelo + salto, w, h);
        }

        String label = names[i] + (i == localIndex ? " (tú)" : "");
        float ly = feetY + h + 6f;
        font.setColor(0f, 0f, 0f, 0.9f);
        font.draw(batch, label, feetX + 1f, ly - 1f, 0f, Align.center, false);
        font.setColor(i == localIndex ? OBJETIVO : Color.WHITE);
        font.draw(batch, label, feetX, ly, 0f, Align.center, false);
    }

    private MapBounds mapBounds() {
        float scale = Math.min(getWidth() / Polis.ANCHO, getHeight() / Polis.ALTO);
        float offsetX = (getWidth() - Polis.ANCHO * scale) / 2f;
        float offsetY = (getHeight() - Polis.ALTO * scale) / 2f;
        return new MapBounds(scale, offsetX, offsetY);
    }

    /** Aro blanco (se tiñe al dibujar). */
    private Texture makeRing(int lado, int radioMin, int radioMax) {
        Pixmap pixmap = new Pixmap(lado, lado, Pixmap.Format.RGBA8888);
        pixmap.setBlending(Pixmap.Blending.None);
        pixmap.setColor(1f, 1f, 1f, 0f);
        pixmap.fill();
        pixmap.setColor(Color.WHITE);
        for (int radius = radioMin; radius <= radioMax; radius++) pixmap.drawCircle(lado / 2, lado / 2, radius);
        return textura(pixmap);
    }

    /** Disco blanco (se tiñe al dibujar). */
    private Texture makeDisc() {
        Pixmap pixmap = new Pixmap(128, 128, Pixmap.Format.RGBA8888);
        pixmap.setBlending(Pixmap.Blending.None);
        pixmap.setColor(1f, 1f, 1f, 0f);
        pixmap.fill();
        pixmap.setColor(Color.WHITE);
        pixmap.fillCircle(64, 64, 60);
        return textura(pixmap);
    }

    private Texture textura(Pixmap pixmap) {
        Texture texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        pixmap.dispose();
        return texture;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public float getPrefWidth() {
        return 800;
    }

    @Override
    public float getPrefHeight() {
        return 450;
    }

    public void dispose() {
        mapTexture.dispose();
        turnRing.dispose();
        placeRing.dispose();
        disc.dispose();
        atlas.dispose();
    }

    private static final class MapBounds {
        final float scale;
        final float offsetX;
        final float offsetY;

        MapBounds(float scale, float offsetX, float offsetY) {
            this.scale = scale;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
        }
    }
}
