package view;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ProgressBar;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Scaling;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import model.GameEngine;
import model.Jugador;
import model.Lugar;
import net.GameClient;
import net.GameServer;
import net.Msg;
import net.Snapshot;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Arconte: un juego sobre rumores, verdad y poder en el ágora de Helíade.
 *
 * Esta clase es SOLO vista: no contiene reglas. Todo lo que pasa en la partida lo decide el GameServer
 * (que corre en la máquina del anfitrión); aquí se dibuja lo que llega por la red y se envían las intenciones
 * del jugador. Pantallas: MENU -> SALA -> INTRO (bienvenida) -> JUEGO -> FIN.
 *
 * En la partida no hay temporizador: cada turno se resuelve cuando el jugador actúa, y después de ver el
 * resultado cada humano pulsa "Continuar". Las acciones se hacen en lugares del mapa (ver {@link Lugar}).
 */
public class ArconteGame extends ApplicationAdapter {

    private static final Color FONDO = Color.valueOf("101827");
    private static final Color PANEL = Color.valueOf("1b293b");
    private static final Color TEXTO = Color.valueOf("edf3fa");
    private static final Color TENUE = Color.valueOf("aab6c5");
    private static final Color ACENTO = Color.valueOf("42c7a5");
    private static final Color ORO = Color.valueOf("f2c14e");
    private static final Color ROJO = Color.valueOf("e0645c");

    private static final String[] NOMBRES_STATS = {
            "Aletheia (verdad)", "Pistis (confianza)", "Eunomía (orden)",
            "Eudaimonía (dicha)", "Apate (engaño)", "Eris (discordia)"
    };

    private static final float ANCHO_SIDEBAR = 520f;

    private enum Pantalla { MENU, SALA, INTRO, JUEGO, FIN }

    private Pantalla pantalla = Pantalla.MENU;
    private Stage stage;
    private Skin skin;
    private Table root;
    private BitmapFont fChica;
    private BitmapFont fNormal;
    private BitmapFont fGrande;
    private BitmapFont fTitulo;
    /** Un retrato por rol, en el orden de Jugador.ROLES. */
    private final Texture[] retratos = new Texture[Jugador.ROLES.length];

    // ---- red
    private GameServer server;      // solo si soy el anfitrión
    private GameClient client;
    private boolean conectando;
    private boolean soyAnfitrion;
    private String direcciones = "";
    private int puertoSala;
    private String miNombre = "";
    private int miIndice = -1;
    private final List<String> nombresPersonajes = new ArrayList<>();
    private final List<String[]> sala = new ArrayList<>(); // {nombre, rol, esAnfitrion}
    private Snapshot snap;
    private String aviso = "";
    private String errorRed = ""; // último ERROR enviado por el servidor (motivo de un rechazo)

    // ---- menú
    private TextField campoNombre;
    private TextField campoIp;
    private TextField campoPuerto;
    private Label estadoMenu;

    // ---- juego
    private Table stats;
    private Label publicationLabel;
    private Label authorLabel;
    private Label playerLabel;
    private Label saberLabel;
    private Label postLabel;
    private Label detalleLabel;
    private Label marcadorLabel;
    private Label roundLabel;
    private Label activityLabel;
    private Label lugarLabel;
    private Label lugarDescLabel;
    /** Última posición conocida de cada personaje (para colocarlos bien al entrar al mapa). */
    private final float[][] ultimaPos = new float[Jugador.ROLES.length][2];
    private TextButton continuarBoton;
    private TextButton saltarBoton;
    private MapView mapView;
    private final List<TextButton> botonesAccion = new ArrayList<>();
    private String verifTexto = "";      // lo que averiguó (en privado) quien indagó
    private Lugar lugarPrevio;
    private float temporizadorPos;
    private boolean accionEnviada;
    private int resueltasPrevias = -1;
    private String fasePrevia = "";

    // =============================================================== ciclo de vida

    @Override
    public void create() {
        createSkin();
        stage = new Stage(new ScreenViewport());
        Gdx.input.setInputProcessor(new InputMultiplexer(stage, new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                if (pantalla == Pantalla.INTRO && (keycode == Input.Keys.ENTER || keycode == Input.Keys.SPACE)) {
                    entrar();
                    return true;
                }
                if (pantalla != Pantalla.JUEGO || mapView == null) return false;
                if ((keycode == Input.Keys.ENTER || keycode == Input.Keys.SPACE)
                        && snap != null && Snapshot.RESULTADO.equals(snap.fase)) {
                    continuar();
                    return true;
                }
                mapView.setDirection(keycode, true);
                return esTeclaDeMovimiento(keycode);
            }

            @Override
            public boolean keyUp(int keycode) {
                if (pantalla != Pantalla.JUEGO || mapView == null) return false;
                mapView.setDirection(keycode, false);
                return esTeclaDeMovimiento(keycode);
            }

            private boolean esTeclaDeMovimiento(int keycode) {
                return keycode == Input.Keys.UP || keycode == Input.Keys.DOWN
                        || keycode == Input.Keys.LEFT || keycode == Input.Keys.RIGHT
                        || keycode == Input.Keys.W || keycode == Input.Keys.A
                        || keycode == Input.Keys.S || keycode == Input.Keys.D;
            }
        }));
        mostrarMenu();
    }

    @Override
    public void render() {
        float delta = Gdx.graphics.getDeltaTime();
        procesarRed();
        if (pantalla == Pantalla.JUEGO && snap != null && mapView != null) {
            // al entrar o salir de un lugar cambian los botones disponibles
            Lugar aqui = mapView.getLugarLocal();
            if (aqui != lugarPrevio) {
                lugarPrevio = aqui;
                actualizarBotones();
                actualizarLugarLabel();
            }
            temporizadorPos += delta;
            if (client != null && temporizadorPos >= 0.1f && mapView.consumeMoved()) {
                temporizadorPos = 0f;
                client.enviar(Msg.of("POS", mapView.getLocalX(), mapView.getLocalY()));
            }
        }
        ScreenUtils.clear(FONDO);
        stage.act(delta);
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        if (stage != null) stage.getViewport().update(width, height, true);
    }

    @Override
    public void dispose() {
        cerrarRed();
        if (mapView != null) mapView.dispose();
        if (stage != null) stage.dispose();
        for (Texture t : retratos) if (t != null) t.dispose();
        if (skin != null) skin.dispose(); // también libera las fuentes registradas en el skin
    }

    // =============================================================== skin y utilidades de UI

    private BitmapFont cargarFuente(String nombre) {
        // fuentes horneadas a su tamaño real (ver tools/generar_fuentes.py): nítidas, sin escalar
        return new BitmapFont(Gdx.files.internal("fuentes/fuente-" + nombre + ".fnt"));
    }

    private void createSkin() {
        skin = new Skin();
        Pixmap pixel = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixel.setColor(Color.WHITE);
        pixel.fill();
        Texture white = new Texture(pixel);
        pixel.dispose();
        skin.add("white", white);

        fChica = cargarFuente("chica");
        fNormal = cargarFuente("normal");
        fGrande = cargarFuente("grande");
        fTitulo = cargarFuente("titulo");
        skin.add("fuente-chica", fChica);
        skin.add("fuente-normal", fNormal);
        skin.add("fuente-grande", fGrande);
        skin.add("fuente-titulo", fTitulo);

        // retratos de los roles (se muestran más chicos que el original: mipmaps para que se vean suaves)
        for (int i = 0; i < Jugador.ROLES.length; i++) {
            retratos[i] = new Texture(Gdx.files.internal("retratos/" + Jugador.archivo(Jugador.ROLES[i]) + ".png"), true);
            retratos[i].setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
        }

        skin.add("default", new Label.LabelStyle(fNormal, TEXTO));
        skin.add("chica", new Label.LabelStyle(fChica, TEXTO), Label.LabelStyle.class);
        skin.add("grande", new Label.LabelStyle(fGrande, TEXTO), Label.LabelStyle.class);
        skin.add("titulo", new Label.LabelStyle(fTitulo, TEXTO), Label.LabelStyle.class);

        TextButton.TextButtonStyle buttonStyle = new TextButton.TextButtonStyle();
        buttonStyle.up = skin.newDrawable("white", Color.valueOf("26374c"));
        buttonStyle.down = skin.newDrawable("white", Color.valueOf("344b65"));
        buttonStyle.over = skin.newDrawable("white", Color.valueOf("30445c"));
        buttonStyle.disabled = skin.newDrawable("white", Color.valueOf("182436"));
        buttonStyle.font = fNormal;
        buttonStyle.fontColor = TEXTO;
        buttonStyle.disabledFontColor = Color.valueOf("6f7f92");
        skin.add("default", buttonStyle);

        TextField.TextFieldStyle fieldStyle = new TextField.TextFieldStyle();
        fieldStyle.font = fNormal;
        fieldStyle.fontColor = TEXTO;
        fieldStyle.background = conMargen(skin.newDrawable("white", Color.valueOf("26374c")));
        fieldStyle.cursor = skin.newDrawable("white", ACENTO);
        fieldStyle.selection = skin.newDrawable("white", Color.valueOf("3b6ea5"));
        fieldStyle.messageFont = fNormal;
        fieldStyle.messageFontColor = Color.valueOf("7d8ba0");
        skin.add("default", fieldStyle);

        ProgressBar.ProgressBarStyle progressStyle = new ProgressBar.ProgressBarStyle();
        progressStyle.background = skin.newDrawable("white", Color.valueOf("334155"));
        progressStyle.knob = skin.newDrawable("white", ACENTO);
        progressStyle.knobBefore = skin.newDrawable("white", ACENTO);
        skin.add("default-horizontal", progressStyle);

        // barra de desplazamiento visible (si no, el panel lateral parecería cortado)
        ScrollPane.ScrollPaneStyle scrollStyle = new ScrollPane.ScrollPaneStyle();
        scrollStyle.vScroll = barra(Color.valueOf("22324a"));
        scrollStyle.vScrollKnob = barra(Color.valueOf("5b7390"));
        skin.add("default", scrollStyle);
    }

    private Drawable barra(Color color) {
        Drawable d = skin.newDrawable("white", color);
        d.setMinWidth(10);
        return d;
    }

    private Drawable conMargen(Drawable d) {
        d.setLeftWidth(10);
        d.setRightWidth(10);
        d.setTopHeight(8);
        d.setBottomHeight(8);
        return d;
    }

    /** Vacía la pantalla y prepara una tabla raíz nueva. */
    private void limpiar() {
        if (mapView != null) {
            mapView.dispose();
            mapView = null;
        }
        botonesAccion.clear();
        stage.clear();
        stage.setKeyboardFocus(null);
        root = new Table();
        root.setFillParent(true);
        root.pad(16);
        root.defaults().space(10);
        stage.addActor(root);
    }

    private Table panelTable() {
        Table table = new Table();
        table.setBackground(skin.newDrawable("white", PANEL));
        table.pad(18);
        return table;
    }

    private Label sectionTitle(String text) {
        Label label = new Label(text, skin, "chica");
        label.setColor(ACENTO);
        return label;
    }

    /** Texto con ajuste de línea: el ancho lo da la celda donde se coloque. */
    private Label texto(String text, String estilo, Color color) {
        Label label = new Label(text, skin, estilo);
        label.setWrap(true);
        label.setColor(color);
        return label;
    }

    private TextButton boton(String texto, Runnable alPulsar) {
        TextButton button = new TextButton(texto, skin);
        button.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                alPulsar.run();
            }
        });
        return button;
    }

    // =============================================================== menú

    private void mostrarMenu() {
        pantalla = Pantalla.MENU;
        cerrarRed();
        snap = null;
        miIndice = -1;
        limpiar();

        Table centro = new Table();
        centro.defaults().space(10);
        Label titulo = new Label("ARCONTE", skin, "titulo");
        titulo.setColor(ACENTO);
        centro.add(titulo).row();
        centro.add(new Label("La voz de Helíade", skin, "grande")).row();
        centro.add(texto("Un juego sobre rumores, verdad y poder en el ágora. Cada uno juega desde su propio dispositivo.",
                "default", TENUE)).width(700).padBottom(14).row();

        Table form = panelTable();
        form.defaults().left().space(6);
        form.add(new Label("Tu nombre", skin)).row();
        campoNombre = new TextField(nombreAnterior(), skin);
        campoNombre.setMaxLength(14);
        form.add(campoNombre).width(440).row();
        form.add(new Label("Dirección del anfitrión (solo para unirte)", skin)).padTop(10).row();
        campoIp = new TextField("", skin);
        campoIp.setMessageText("por ejemplo 192.168.1.20");
        form.add(campoIp).width(440).row();
        form.add(new Label("Puerto", skin)).padTop(10).row();
        campoPuerto = new TextField(String.valueOf(GameServer.PUERTO_DEFECTO), skin);
        form.add(campoPuerto).width(160).row();
        centro.add(form).width(520).row();

        Table botones = new Table();
        botones.defaults().width(330).height(58).space(12);
        botones.add(boton("Crear sala (soy anfitrión)", this::crearSala));
        botones.add(boton("Unirme a una sala", this::unirse));
        centro.add(botones).padTop(6).row();

        estadoMenu = texto(aviso, "default", ORO);
        aviso = "";
        centro.add(estadoMenu).width(700).row();
        centro.add(texto("Todos deben estar en la misma red Wi-Fi, o conectados por una VPN. "
                + "Quien crea la sala le comparte su dirección a los demás.", "chica", TENUE)).width(700);

        root.add(centro).expand().center();
        stage.setKeyboardFocus(campoNombre);
    }

    private String nombreAnterior() {
        return miNombre.isEmpty() ? "Jugador" : miNombre;
    }

    private String nombreEscrito() {
        String n = campoNombre.getText().trim();
        return n.isEmpty() ? "Jugador" : n;
    }

    private int puertoEscrito() {
        try {
            int p = Integer.parseInt(campoPuerto.getText().trim());
            if (p >= 1024 && p <= 65535) return p;
        } catch (NumberFormatException ignorada) {
            // se avisa abajo
        }
        estadoMenu.setText("El puerto debe ser un número entre 1024 y 65535.");
        return -1;
    }

    private void crearSala() {
        if (conectando) return;
        final String nombre = nombreEscrito();
        final int puerto = puertoEscrito();
        if (puerto < 0) return;
        conectando = true;
        estadoMenu.setText("Creando la sala…");
        new Thread(() -> {
            GameServer nuevo = new GameServer(puerto);
            try {
                nuevo.iniciar();
                GameClient c = GameClient.conectar("127.0.0.1", nuevo.getPuerto(), 3000);
                c.enviar(Msg.of("HELLO", nombre, GameServer.VERSION));
                Gdx.app.postRunnable(() -> {
                    server = nuevo;
                    client = c;
                    miNombre = nombre;
                    conectando = false;
                    mostrarSala();
                });
            } catch (IOException ex) {
                nuevo.close();
                Gdx.app.postRunnable(() -> {
                    conectando = false;
                    estadoMenu.setText("No se pudo crear la sala (" + ex.getMessage() + "). ¿Ya hay una sala en ese puerto?");
                });
            }
        }, "arconte-crear").start();
    }

    private void unirse() {
        if (conectando) return;
        final String nombre = nombreEscrito();
        final String ip = campoIp.getText().trim();
        final int puerto = puertoEscrito();
        if (puerto < 0) return;
        if (ip.isEmpty()) {
            estadoMenu.setText("Escribe la dirección que te dio el anfitrión.");
            return;
        }
        conectando = true;
        estadoMenu.setText("Conectando con " + ip + ":" + puerto + "…");
        new Thread(() -> {
            try {
                GameClient c = GameClient.conectar(ip, puerto, 5000);
                c.enviar(Msg.of("HELLO", nombre, GameServer.VERSION));
                Gdx.app.postRunnable(() -> {
                    client = c;
                    server = null;
                    miNombre = nombre;
                    conectando = false;
                    mostrarSala();
                });
            } catch (IOException ex) {
                Gdx.app.postRunnable(() -> {
                    conectando = false;
                    estadoMenu.setText("No se pudo conectar con " + ip + ":" + puerto
                            + ". Revisa la dirección, que el anfitrión ya creó la sala y que estén en la misma red.");
                });
            }
        }, "arconte-unirse").start();
    }

    private void cerrarRed() {
        if (client != null) client.close();
        if (server != null) server.close();
        client = null;
        server = null;
        soyAnfitrion = false;
        sala.clear();
    }

    // =============================================================== sala de espera

    private void mostrarSala() {
        pantalla = Pantalla.SALA;
        limpiar();

        float total = Math.min(1320f, Gdx.graphics.getWidth() - 60f);
        Table centro = new Table();
        centro.defaults().space(10);
        centro.add(new Label("SALA DE HELÍADE", skin, "titulo")).row();

        // ---- barra superior: a dónde conectarse, quién está y los botones principales
        Table barra = panelTable();
        barra.pad(14);
        String info;
        if (soyAnfitrion) {
            String donde = direcciones.isEmpty()
                    ? "127.0.0.1 (no se detectó red; sirve solo para probar en este equipo)"
                    : direcciones.replace(", ", "  o  ");
            info = "Pídeles a tus amigos que se unan con la dirección: " + donde + "  y el puerto " + puertoSala;
        } else {
            info = "Conectado a la sala. Esperando a que el anfitrión comience la partida.";
        }
        barra.add(texto(info, "default", ORO)).growX().left();
        Table botones = new Table();
        botones.defaults().width(250).height(52).space(10);
        if (soyAnfitrion) {
            botones.add(boton("Comenzar partida", () -> {
                aviso = "";
                if (client != null) client.enviar(Msg.of("START"));
            }));
        }
        botones.add(boton("Salir", () -> mostrarMenu()));
        barra.add(botones).right().padLeft(16).row();
        StringBuilder quien = new StringBuilder("Jugadores (" + sala.size() + "/" + GameServer.MAX_JUGADORES + "):  ");
        for (int i = 0; i < sala.size(); i++) {
            String[] fila = sala.get(i);
            if (i > 0) quien.append("   ·   ");
            quien.append(fila[0]).append(fila[1].isEmpty() ? " (sin rol)" : " — " + fila[1])
                    .append("1".equals(fila[2]) ? " [anfitrión]" : "");
        }
        barra.add(texto(quien.toString(), "chica", TENUE)).colspan(2).growX().left().padTop(8);
        centro.add(barra).width(total).row();

        centro.add(texto("Elige tu rol. Los roles que nadie elija los juegan bots.", "default", TEXTO)).width(total).padTop(6).row();

        // ---- una tarjeta por rol, con su retrato y lo que hace
        float anchoTarjeta = Math.min(330f, (total - 3 * 12f) / 4f);
        Table tarjetas = new Table();
        tarjetas.defaults().top().space(12);
        String miRol = rolDe(miNombre);
        for (int i = 0; i < Jugador.ROLES.length; i++) {
            tarjetas.add(tarjetaRol(i, anchoTarjeta, miRol)).width(anchoTarjeta).top();
        }
        centro.add(tarjetas).row();

        centro.add(texto("Cada turno sale un rumor en el ágora. Recorre Helíade: pregona en el Ágora, indaga en el Puerto "
                + "(el Logógrafo, en el Metroon), denuncia en la Heliea. Quien siembre mentiras terminará en la cicuta; "
                + "quien lleve bien la palabra de los hombres tendrá el favor de Hermes.", "chica", TENUE)).width(total).row();
        centro.add(texto(aviso, "default", ROJO)).width(total);

        // la sala puede ser más alta que la ventana: va dentro de un panel con scroll
        ScrollPane scroll = new ScrollPane(centro, skin);
        scroll.setFadeScrollBars(false);
        scroll.setScrollingDisabled(true, false);
        root.add(scroll).grow();
        stage.setScrollFocus(scroll);
    }

    /** La tarjeta de un rol: retrato, nombre, qué era en la versión moderna, descripción, acciones y botón. */
    private Table tarjetaRol(int indice, float ancho, String miRol) {
        final String rol = Jugador.ROLES[indice];
        String dueno = duenoDe(rol);
        final boolean mio = rol.equals(miRol);
        boolean ocupado = dueno != null && !mio;
        float interior = ancho - 24f;

        Table t = panelTable();
        t.pad(12);
        t.setBackground(skin.newDrawable("white", mio ? Color.valueOf("21485a") : PANEL));

        Image retrato = new Image(new TextureRegionDrawable(new TextureRegion(retratos[indice])));
        retrato.setScaling(Scaling.fit);
        t.add(retrato).width(interior).height(interior * 0.71f).row();
        t.add(new Label(rol.toUpperCase(), skin, "grande")).padTop(8).row();
        Label hoy = texto("Hoy sería: " + Jugador.equivalente(rol).toLowerCase(), "chica", ACENTO);
        hoy.setAlignment(Align.center);
        t.add(hoy).width(interior).row();
        t.add(texto(Jugador.descripcion(rol), "chica", TEXTO)).width(interior).left().padTop(8).row();
        t.add(sectionTitle("TUS ACCIONES")).left().padTop(10).row();
        for (String linea : Jugador.acciones(rol)) {
            t.add(texto("• " + linea, "chica", TENUE)).width(interior).left().padTop(3).row();
        }
        String etiqueta = mio ? "Tu rol (pulsa para quitar)" : ocupado ? "Elegido por " + dueno : "Elegir este rol";
        TextButton b = boton(etiqueta, () -> {
            aviso = "";
            if (client != null) client.enviar(Msg.of("ROLE", mio ? "" : rol));
        });
        b.setDisabled(ocupado);
        t.add(b).width(interior).height(48).padTop(12);
        return t;
    }

    private String rolDe(String nombre) {
        for (String[] fila : sala) if (fila[0].equals(nombre)) return fila[1];
        return "";
    }

    private String duenoDe(String rol) {
        for (String[] fila : sala) if (fila[1].equals(rol)) return fila[0];
        return null;
    }

    // =============================================================== mensajes de red

    private void procesarRed() {
        if (client == null) return;
        GameClient actual = client;
        Msg m;
        while (client == actual && (m = actual.siguiente()) != null) manejar(m);
    }

    private void manejar(Msg m) {
        switch (m.type) {
            case "WELCOME":
                soyAnfitrion = m.i(1) == 1;
                direcciones = m.s(2);
                puertoSala = m.i(3);
                break;
            case "LOBBY":
                sala.clear();
                int n = m.i(0);
                for (int i = 0; i < n; i++) {
                    sala.add(new String[]{m.s(1 + i * 3), m.s(2 + i * 3), m.s(3 + i * 3)});
                }
                if (pantalla == Pantalla.SALA) mostrarSala();
                break;
            case "START":
                miIndice = m.i(0);
                nombresPersonajes.clear();
                for (int i = 0; i < Jugador.ROLES.length; i++) nombresPersonajes.add(m.s(1 + i));
                aviso = "";
                verifTexto = "";
                resueltasPrevias = -1;
                fasePrevia = "";
                for (int i = 0; i < ultimaPos.length; i++) {
                    ultimaPos[i][0] = Lugar.INICIO[i][0];
                    ultimaPos[i][1] = Lugar.INICIO[i][1];
                }
                mostrarIntro();
                break;
            case "STATE":
                snap = Snapshot.fromMsg(m);
                accionEnviada = false;
                if (Snapshot.FIN.equals(snap.fase)) {
                    mostrarFin();
                } else if (pantalla == Pantalla.JUEGO) {
                    aplicarEstado();
                }
                break;
            case "VERIF":
                // lo averiguado es privado: solo lo recibe quien indagó
                verifTexto = m.s(0);
                break;
            case "POS": {
                int quien = m.i(0);
                if (quien >= 0 && quien < ultimaPos.length) {
                    ultimaPos[quien][0] = (float) m.d(1);
                    ultimaPos[quien][1] = (float) m.d(2);
                }
                if (mapView != null) mapView.setRemote(quien, (float) m.d(1), (float) m.d(2));
                break;
            }
            case "AVISO":
                mostrarAviso(m.s(0));
                break;
            case "ERROR":
                errorRed = m.s(0);
                if (pantalla == Pantalla.JUEGO) {
                    mostrarAviso(errorRed);
                } else {
                    aviso = errorRed;
                    if (pantalla == Pantalla.SALA) mostrarSala();
                    else if (pantalla == Pantalla.MENU && estadoMenu != null) estadoMenu.setText(aviso);
                }
                break;
            case "CLOSED":
                if (pantalla == Pantalla.SALA || pantalla == Pantalla.INTRO || pantalla == Pantalla.JUEGO) {
                    aviso = errorRed.isEmpty() ? "Se perdió la conexión con la sala." : errorRed;
                    errorRed = "";
                    mostrarMenu();
                }
                break;
            default:
                break;
        }
    }

    /** Un consejo o rechazo del servidor ("ve al Puerto", "tus hetairoi te aconsejan..."). No gasta el turno. */
    private void mostrarAviso(String texto) {
        aviso = texto;
        accionEnviada = false;
        if (pantalla == Pantalla.JUEGO && activityLabel != null) {
            activityLabel.setText(aviso);
            actualizarBotones();
        }
    }

    // =============================================================== bienvenida

    /** Antes de entrar al mapa, cada jugador ve quién es, qué debe hacer y de qué va la campaña. */
    private void mostrarIntro() {
        pantalla = Pantalla.INTRO;
        limpiar();
        String rol = rolMio();
        int indice = Math.max(0, Math.min(Jugador.ROLES.length - 1, miIndice));
        String nombre = indice < nombresPersonajes.size() ? nombresPersonajes.get(indice) : miNombre;

        Table fila = new Table();
        fila.defaults().top().space(16);

        Table izquierda = panelTable();
        izquierda.pad(14);
        Image retrato = new Image(new TextureRegionDrawable(new TextureRegion(retratos[indice])));
        retrato.setScaling(Scaling.fit);
        izquierda.add(retrato).width(430).height(430 * 0.71f).row();
        izquierda.add(new Label(rol.toUpperCase(), skin, "grande")).padTop(10).row();
        Label hoy = texto("Hoy sería: " + Jugador.equivalente(rol).toLowerCase(), "chica", ACENTO);
        hoy.setAlignment(Align.center);
        izquierda.add(hoy).width(430).row();
        fila.add(izquierda).top();

        Table derecha = panelTable();
        derecha.pad(28);
        String[] partes = Jugador.bienvenida(nombre, rol).split("\n\n");
        for (int i = 0; i < partes.length; i++) {
            boolean extremo = i == 0 || i == partes.length - 1;
            Color color = i == 0 ? ORO : i == partes.length - 1 ? ACENTO : TEXTO;
            derecha.add(texto(partes[i], extremo ? "grande" : "default", color)).width(600).left().padTop(i == 0 ? 0 : 16).row();
        }
        fila.add(derecha).top();

        Table centro = new Table();
        centro.defaults().space(16);
        centro.add(fila).row();
        centro.add(boton("Entrar a Helíade  (Enter)", this::entrar)).width(380).height(60);

        ScrollPane scroll = new ScrollPane(centro, skin);
        scroll.setFadeScrollBars(false);
        scroll.setScrollingDisabled(true, false);
        root.add(scroll).grow();
        stage.setScrollFocus(scroll);
    }

    private void entrar() {
        if (pantalla == Pantalla.INTRO) mostrarJuego();
    }

    // =============================================================== partida

    private void mostrarJuego() {
        pantalla = Pantalla.JUEGO;
        limpiar();
        lugarPrevio = null;

        Label title = new Label("ARCONTE  ·  HELÍADE", skin, "grande");
        root.add(title).left().expandX();
        Table headingInfo = new Table();
        roundLabel = new Label("", skin);
        headingInfo.add(roundLabel).right().row();
        Label tagline = new Label("El ágora  ·  campaña por el cargo de Arconte", skin, "chica");
        tagline.setColor(ACENTO);
        headingInfo.add(tagline).right();
        root.add(headingInfo).right().row();

        stats = new Table();
        stats.setBackground(skin.newDrawable("white", PANEL));
        stats.pad(12);
        root.add(stats).colspan(2).growX().row();

        // ---- mapa (ocupa todo el ancho que sobre)
        Table mapPanel = panelTable();
        mapPanel.pad(14);
        mapPanel.add(sectionTitle("MAPA DE LA POLIS")).left().row();
        mapView = new MapView(nombresPersonajes, Math.max(0, miIndice), fChica);
        for (int i = 0; i < ultimaPos.length; i++) {
            if (i != miIndice) mapView.setRemote(i, ultimaPos[i][0], ultimaPos[i][1]);
        }
        mapPanel.add(mapView).grow().minSize(480, 270).padTop(8).row();
        lugarLabel = texto("", "default", ACENTO);
        mapPanel.add(lugarLabel).growX().left().padTop(8).row();
        lugarDescLabel = texto("", "chica", TENUE);
        lugarDescLabel.setAlignment(Align.topLeft);
        mapPanel.add(lugarDescLabel).growX().height(96).left().top().padTop(2);

        // ---- panel lateral con desplazamiento
        Table lateral = new Table();
        lateral.defaults().width(ANCHO_SIDEBAR).space(10);

        Table feed = panelTable();
        feed.add(sectionTitle("VOCES DEL ÁGORA")).left().row();
        publicationLabel = texto("Esperando el primer rumor…", "grande", TEXTO);
        feed.add(publicationLabel).width(ANCHO_SIDEBAR - 36).left().padTop(10).row();
        authorLabel = texto("", "chica", TENUE);
        feed.add(authorLabel).width(ANCHO_SIDEBAR - 36).left().padTop(6).row();
        playerLabel = texto("", "default", ACENTO);
        feed.add(playerLabel).width(ANCHO_SIDEBAR - 36).left().padTop(12).row();
        saberLabel = texto("", "default", ORO);
        feed.add(saberLabel).width(ANCHO_SIDEBAR - 36).left().padTop(10).row();
        lateral.add(feed).row();

        Table overview = panelTable();
        overview.add(sectionTitle("PRESAGIOS Y RESULTADO")).left().row();
        postLabel = texto("", "default", TEXTO);
        overview.add(postLabel).width(ANCHO_SIDEBAR - 36).left().padTop(8).row();
        detalleLabel = texto("", "chica", TENUE);
        overview.add(detalleLabel).width(ANCHO_SIDEBAR - 36).left().padTop(8).row();
        continuarBoton = boton("Continuar", this::continuar);
        overview.add(continuarBoton).width(ANCHO_SIDEBAR - 36).height(58).padTop(14).row();
        saltarBoton = boton("Saltar turno (anfitrión)", this::saltar);
        if (soyAnfitrion) overview.add(saltarBoton).width(ANCHO_SIDEBAR - 36).height(44).padTop(8).row();
        lateral.add(overview).row();

        Table marcador = panelTable();
        marcador.add(sectionTitle("MARCADOR")).left().row();
        marcadorLabel = texto("", "chica", TEXTO);
        marcador.add(marcadorLabel).width(ANCHO_SIDEBAR - 36).left().padTop(8).row();
        lateral.add(marcador).row();

        ScrollPane scroll = new ScrollPane(lateral, skin);
        scroll.setFadeScrollBars(false);
        scroll.setScrollingDisabled(true, false);

        Table main = new Table();
        main.defaults().space(12).top();
        main.add(mapPanel).grow();
        main.add(scroll).width(ANCHO_SIDEBAR + 14).growY();
        root.add(main).colspan(2).grow().row();
        stage.setScrollFocus(scroll);

        // ---- botones de acción (el Arconte tiene además su discurso)
        List<String> acciones = new ArrayList<>();
        acciones.add(GameEngine.PREGONAR);
        acciones.add(GameEngine.INDAGAR);
        acciones.add(GameEngine.CALLAR);
        acciones.add(GameEngine.DENUNCIAR);
        if (Jugador.ARCONTE.equals(rolMio())) acciones.add(GameEngine.DISCURSO);
        float ancho = Math.min(290f, (Gdx.graphics.getWidth() - 64f - 12f * (acciones.size() - 1)) / acciones.size());
        Table actions = new Table();
        actions.defaults().width(ancho).height(80).space(12);
        for (String accion : acciones) {
            TextButton b = boton(accion, () -> pulsarAccion(accion));
            b.setUserObject(accion);
            botonesAccion.add(b);
            actions.add(b);
        }
        root.add(actions).colspan(2).center().row();

        activityLabel = texto(aviso, "default", ORO);
        root.add(activityLabel).colspan(2).growX().left().row();

        if (snap != null) aplicarEstado();
    }

    private void pulsarAccion(String accion) {
        if (client == null || accionEnviada || snap == null || snap.actual != miIndice) return;
        boolean traIndagar = Snapshot.VERIFICANDO.equals(snap.fase);
        if (!traIndagar && !Snapshot.TURNO.equals(snap.fase)) return;
        accionEnviada = true;
        aviso = "";
        activityLabel.setText("");
        actualizarBotones();
        client.enviar(Msg.of(traIndagar ? "FINAL" : "ACCION", accion));
    }

    private void continuar() {
        if (client == null || snap == null || !Snapshot.RESULTADO.equals(snap.fase)) return;
        if (!snap.pendientes.contains(miIndice)) return; // ya había pulsado
        snap.pendientes.remove(Integer.valueOf(miIndice));
        actualizarContinuar();
        client.enviar(Msg.of("LISTO"));
    }

    private void saltar() {
        if (client != null && soyAnfitrion) client.enviar(Msg.of("FORZAR"));
    }

    private void aplicarEstado() {
        if (snap == null || pantalla != Pantalla.JUEGO || snap.jugadores.size() < Jugador.ROLES.length) return;

        // el consejo o error anterior se borra cuando el juego avanza de turno o de fase
        if (snap.resueltas != resueltasPrevias || !snap.fase.equals(fasePrevia)) {
            if (resueltasPrevias != -1) {
                aviso = "";
                activityLabel.setText("");
            }
            resueltasPrevias = snap.resueltas;
            fasePrevia = snap.fase;
        }
        if (!Snapshot.VERIFICANDO.equals(snap.fase) && !Snapshot.TURNO.equals(snap.fase)) verifTexto = "";
        if (Snapshot.TURNO.equals(snap.fase) && snap.actual != miIndice) verifTexto = "";

        publicationLabel.setText("“" + snap.pubTexto + "”");
        authorLabel.setText("Lo cuenta " + snap.pubAutor + ".");
        roundLabel.setText("Rumores resueltos: " + snap.resueltas + " / " + snap.total);
        playerLabel.setText(textoDeTurno());
        saberLabel.setText(Snapshot.VERIFICANDO.equals(snap.fase) && snap.actual == miIndice && !verifTexto.isEmpty()
                ? "Lo que averiguaste:\n" + verifTexto : "");

        refreshStats();
        if (mapView != null) mapView.setTurnIndex(Snapshot.RESULTADO.equals(snap.fase) ? snap.actor : snap.actual);
        actualizarBotones();
        actualizarPanelDecisiones();
        actualizarMarcador();
        actualizarContinuar();
        actualizarLugarLabel();
    }

    private String textoDeTurno() {
        Snapshot.Jug actual = snap.jugadores.get(snap.actual);
        boolean mio = snap.actual == miIndice;
        if (Snapshot.VERIFICANDO.equals(snap.fase)) {
            return mio ? "Ya indagaste. Decide qué hacer: ve al lugar de tu acción y pulsa su botón."
                    : actual.nombre + " (" + actual.rol + ") está indagando…";
        }
        if (Snapshot.RESULTADO.equals(snap.fase)) {
            // durante el resultado "actual" ya es el SIGUIENTE jugador: quien jugó es "actor"
            Snapshot.Jug quien = snap.jugadores.get(snap.actor);
            Snapshot.Jug sigue = snap.jugadores.get(snap.actual);
            String t = "Consecuencias del turno de " + quien.nombre + " (" + quien.rol + "). Léelas con calma y pulsa Continuar.";
            t += "\nDespués juega " + (snap.actual == miIndice ? "¡tú!" : sigue.nombre + " (" + sigue.rol + ").");
            return t;
        }
        if (mio) {
            String t = "¡Es tu turno, " + actual.nombre + "! Elige una acción: los lugares que brillan en el mapa "
                    + "son donde puedes hacerlas.";
            if (Jugador.ARCONTE.equals(rolMio()) && !snap.puedeDeclaracion) {
                t += "\nTus hetairoi, tu círculo de campaña, te aconsejan no dar discurso hoy: el pueblo aún recuerda el último.";
            }
            return t;
        }
        return "Turno de " + actual.nombre + " (" + actual.rol + ")" + (actual.bot ? " [bot]" : "");
    }

    private String rolMio() {
        return miIndice >= 0 && miIndice < Jugador.ROLES.length ? Jugador.ROLES[miIndice] : "";
    }

    /**
     * Cada botón se habilita solo si la acción está permitida ahora Y el personaje está en el lugar que le toca.
     * Al lado del nombre se muestra a dónde ir; los lugares que sirven se resaltan en el mapa.
     */
    private void actualizarBotones() {
        if (snap == null || botonesAccion.isEmpty()) return;
        String rol = rolMio();
        boolean miTurno = snap.actual == miIndice;
        boolean decidiendo = miTurno && Snapshot.TURNO.equals(snap.fase);
        boolean traIndagar = miTurno && Snapshot.VERIFICANDO.equals(snap.fase);
        Lugar aqui = mapView != null ? mapView.getLugarLocal() : null;
        Set<Lugar> objetivos = EnumSet.noneOf(Lugar.class);

        for (TextButton b : botonesAccion) {
            String base = (String) b.getUserObject();
            boolean permitida;
            switch (base) {
                case GameEngine.INDAGAR:
                    permitida = decidiendo;
                    break;
                case GameEngine.DISCURSO:
                    permitida = decidiendo && snap.puedeDeclaracion;
                    break;
                default:
                    permitida = decidiendo || traIndagar;
                    break;
            }
            Lugar donde = Lugar.paraAccion(rol, base);
            boolean enSitio = donde == null || donde == aqui;
            String etiqueta = Jugador.etiqueta(rol, base);
            if (permitida) {
                etiqueta += "\n" + (donde == null ? "(donde sea)" : enSitio ? "(estás aquí)" : "(ve " + donde.irA + ")");
                if (donde != null) objetivos.add(donde);
            } else if (GameEngine.DISCURSO.equals(base) && decidiendo) {
                etiqueta += "\n(hoy no: hetairoi)";
            }
            b.setText(etiqueta);
            b.setDisabled(accionEnviada || !permitida || !enSitio);
        }
        if (mapView != null) mapView.setObjetivos(objetivos);
    }

    private void actualizarLugarLabel() {
        if (lugarLabel == null || mapView == null) return;
        Lugar aqui = mapView.getLugarLocal();
        String donde = aqui == null ? "Caminas por las calles de Helíade." : "Estás en: " + aqui.nombre + ".";
        lugarLabel.setText(donde + "   Muévete con WASD o las flechas.");
        lugarDescLabel.setText(aqui == null ? Lugar.CALLES : aqui.descripcion);
    }

    private void actualizarContinuar() {
        if (continuarBoton == null || snap == null) return;
        boolean resultado = Snapshot.RESULTADO.equals(snap.fase);
        boolean yaListo = resultado && !snap.pendientes.contains(miIndice);
        if (!resultado) {
            continuarBoton.setText("Continuar");
        } else if (!yaListo) {
            continuarBoton.setText("Continuar  (Enter)");
        } else if (snap.pendientes.isEmpty()) {
            continuarBoton.setText("Continuando…");
        } else {
            StringBuilder sb = new StringBuilder("Esperando a: ");
            for (int i = 0; i < snap.pendientes.size(); i++) {
                int idx = snap.pendientes.get(i);
                if (i > 0) sb.append(", ");
                sb.append(idx < snap.jugadores.size() ? snap.jugadores.get(idx).nombre : "?");
            }
            continuarBoton.setText(sb.toString());
        }
        continuarBoton.setDisabled(!resultado || yaListo);

        if (soyAnfitrion && saltarBoton != null) {
            boolean puedeSaltar = resultado ? !snap.pendientes.isEmpty() && !(snap.pendientes.size() == 1
                    && snap.pendientes.get(0) == miIndice) : snap.actual != miIndice;
            saltarBoton.setDisabled(!puedeSaltar);
        }
    }

    private void actualizarPanelDecisiones() {
        StringBuilder sb = new StringBuilder();
        if (!snap.evento.isEmpty()) sb.append("Presagio: ").append(snap.evento);
        if (Snapshot.RESULTADO.equals(snap.fase)) {
            sb.append("\n\n").append(snap.resultado);
        } else {
            Snapshot.Jug actual = snap.jugadores.get(snap.actual);
            sb.append("\n\nAmigos de ").append(actual.nombre).append(" en Helíade: ");
            if (snap.amigos.isEmpty()) sb.append("ninguno");
            for (int i = 0; i < snap.amigos.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(snap.amigos.get(i));
            }
        }
        postLabel.setText(sb.toString());

        StringBuilder det = new StringBuilder();
        if (Snapshot.RESULTADO.equals(snap.fase)) {
            if (!snap.difusion.isEmpty()) det.append("Difusión: ").append(snap.difusion);
            if (!snap.camino.isEmpty()) {
                if (det.length() > 0) det.append("\n");
                det.append("Ruta del árbol de decisiones: ").append(snap.camino);
            }
        }
        detalleLabel.setText(det.toString());
    }

    private void actualizarMarcador() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < snap.jugadores.size(); i++) {
            Snapshot.Jug j = snap.jugadores.get(i);
            sb.append(j.rol).append(": ").append(j.nombre).append(j.bot ? " [bot]" : "").append(i == miIndice ? " (tú)" : "")
                    .append("  |  ").append(j.puntos).append(" pts, reputación ").append(j.reputacion).append("\n");
        }
        marcadorLabel.setText(sb.toString().trim());
    }

    private void refreshStats() {
        stats.clearChildren();
        for (int i = 0; i < NOMBRES_STATS.length; i++) addStat(NOMBRES_STATS[i], snap.ciudad[i]);
    }

    private void addStat(String name, int value) {
        Table column = new Table();
        Label label = new Label(name + "  " + value, skin, "chica");
        column.add(label).left().row();
        ProgressBar bar = new ProgressBar(0, 100, 1, false, skin);
        bar.setValue(value);
        column.add(bar).width(150).height(12).padTop(6);
        stats.add(column).expandX().fillX().padRight(8);
    }

    // =============================================================== final

    private void mostrarFin() {
        pantalla = Pantalla.FIN;
        limpiar();

        Table centro = new Table();
        centro.defaults().space(10);
        centro.add(new Label("FIN DE LA CAMPAÑA", skin, "titulo")).row();
        Label resultado = texto(snap.finTitulo, "grande", ORO);
        resultado.setAlignment(Align.center);
        centro.add(resultado).width(900).row();
        Label polis = texto(snap.finTexto, "default", TEXTO);
        polis.setAlignment(Align.center);
        centro.add(polis).width(900).row();

        if (miIndice >= 0 && miIndice < snap.destinos.size()) {
            Snapshot.Destino mio = snap.destinos.get(miIndice);
            Table destino = panelTable();
            destino.add(sectionTitle("TU DESTINO")).left().row();
            destino.add(texto(mio.titulo.toUpperCase(), "grande", colorDestino(mio.clave))).width(840).left().padTop(8).row();
            destino.add(texto(mio.texto, "default", TEXTO)).width(840).left().padTop(8).row();
            centro.add(destino).width(900).row();
        }

        Table todos = panelTable();
        todos.add(sectionTitle("EL DESTINO DE TODOS")).left().row();
        for (int i = 0; i < snap.destinos.size(); i++) {
            Snapshot.Destino d = snap.destinos.get(i);
            Snapshot.Jug j = i < snap.jugadores.size() ? snap.jugadores.get(i) : null;
            String extra = j == null ? "" : "   (" + j.puntos + " pts, reputación " + j.reputacion
                    + ", rumores falsos pregonados: " + j.compartidasFalsas + ")";
            Label linea = texto(d.rol + " · " + d.nombre + (i == miIndice ? " (tú)" : "") + ":  " + d.titulo + extra,
                    "default", colorDestino(d.clave));
            todos.add(linea).width(840).left().padTop(8).row();
        }
        centro.add(todos).width(900).row();

        centro.add(boton("Salir al menú", this::mostrarMenu)).width(280).height(56).padTop(6);

        // el contenido puede ser más alto que la ventana: va dentro de un panel con scroll
        ScrollPane scroll = new ScrollPane(centro, skin);
        scroll.setFadeScrollBars(false);
        scroll.setScrollingDisabled(true, false);
        root.add(scroll).grow();
        stage.setScrollFocus(scroll);
    }

    private static Color colorDestino(String clave) {
        switch (clave) {
            case GameEngine.DESTINO_CICUTA: return ROJO;
            case GameEngine.DESTINO_OSTRACISMO: return Color.valueOf("e8924a");
            case GameEngine.DESTINO_HERMES: return ORO;
            default: return TENUE;
        }
    }
}
