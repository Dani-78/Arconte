package net;

import model.CiudadEstado;
import model.GameEngine;
import model.Jugador;
import model.Lugar;
import model.Publicacion;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Servidor de una partida de Arconte. Lo ejecuta el anfitrión dentro de su propio programa.
 *
 * Es AUTORITATIVO: el único GameEngine vive aquí. Los clientes solo envían intenciones (ACCION, FINAL, POS)
 * y reciben instantáneas del estado (STATE). Los roles que nadie ocupa los juegan bots, y si un jugador se
 * desconecta, un bot toma su lugar para que la partida no se detenga.
 *
 * No hay límite de tiempo: el jugador de turno decide cuando quiera y, tras cada turno, el juego espera a que
 * TODOS los humanos pulsen "Continuar" (LISTO) antes de sacar el siguiente rumor. El anfitrión puede forzar el
 * avance (FORZAR) si alguien se quedó dormido. Para actuar hay que estar en el lugar del mapa que corresponde a
 * la acción (ver {@link Lugar}); el servidor lo comprueba con la última posición que informó el jugador.
 */
public final class GameServer implements AutoCloseable {

    public static final int PUERTO_DEFECTO = 5050;
    public static final String VERSION = "ARCONTE/1";
    public static final int MAX_JUGADORES = Jugador.ROLES.length;

    private static final String CERRAR = "\u0000cerrar";

    private enum Fase { SALA, TURNO, VERIFICANDO, RESULTADO, FIN }

    private final Object lock = new Object();
    private final List<Handler> clientes = new ArrayList<>();
    private final int puertoSolicitado;

    private ServerSocket servidor;
    private ScheduledExecutorService reloj;
    private volatile boolean cerrado;

    // pausa antes de que un bot actúe (milisegundos); se puede acelerar para pruebas con setRitmo
    private long botMs = 1200;

    private Fase fase = Fase.SALA;
    private GameEngine motor;
    private long botEn;        // cuándo actúa el bot del turno actual
    // última posición conocida de cada personaje en el mapa (índice = rol), para validar dónde se actúa
    private final float[] posX = new float[Jugador.ROLES.length];
    private final float[] posY = new float[Jugador.ROLES.length];
    private String resultado = "";
    private int ultimoActor;
    private String eventoActual = "";
    private GameEngine.Eleccion eleccionFinal;
    private List<GameEngine.Destino> destinosFinales = new ArrayList<>();
    private int proxId = 1;

    public GameServer(int puerto) {
        this.puertoSolicitado = puerto;
    }

    /** Para pruebas: acelera la espera antes de que un bot actúe. */
    public void setRitmo(long botMs) {
        this.botMs = botMs;
    }

    public void iniciar() throws IOException {
        servidor = new ServerSocket(puertoSolicitado);
        Thread aceptador = new Thread(this::aceptar, "arconte-aceptar");
        aceptador.setDaemon(true);
        aceptador.start();
        reloj = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "arconte-reloj");
            t.setDaemon(true);
            return t;
        });
        reloj.scheduleAtFixedRate(this::tick, 200, 200, TimeUnit.MILLISECONDS);
    }

    public int getPuerto() {
        return servidor == null ? puertoSolicitado : servidor.getLocalPort();
    }

    /** Direcciones IPv4 de esta máquina en la red local, para decirles a los demás a dónde conectarse. */
    public static List<String> direccionesLocales() {
        List<String> lista = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address && !a.isLoopbackAddress()) lista.add(a.getHostAddress());
                }
            }
        } catch (IOException ignorada) {
            // sin interfaces: la lista queda vacía
        }
        return lista;
    }

    @Override
    public void close() {
        List<Handler> copia;
        synchronized (lock) {
            if (cerrado) return;
            cerrado = true;
            copia = new ArrayList<>(clientes);
            clientes.clear();
        }
        try {
            if (servidor != null) servidor.close();
        } catch (IOException ignorada) {
            // ya estaba cerrado
        }
        if (reloj != null) reloj.shutdownNow();
        for (Handler h : copia) h.cerrarYa();
    }

    // ------------------------------------------------------------------ conexiones

    private void aceptar() {
        while (!cerrado) {
            try {
                Socket s = servidor.accept();
                s.setTcpNoDelay(true);
                new Handler(s).iniciar();
            } catch (IOException e) {
                if (cerrado) return;
            }
        }
    }

    private final class Handler {
        final Socket socket;
        final BlockingQueue<String> salida = new LinkedBlockingQueue<>();
        String nombre = "";
        String rol = "";
        int id;
        int indice = -1;
        boolean saludado;
        boolean listo; // pulsó "Continuar" en la pausa de resultados
        volatile boolean cerrando;

        Handler(Socket socket) {
            this.socket = socket;
        }

        void iniciar() {
            Thread lector = new Thread(this::leer, "arconte-lector");
            lector.setDaemon(true);
            lector.start();
            Thread escritor = new Thread(this::escribir, "arconte-escritor");
            escritor.setDaemon(true);
            escritor.start();
        }

        private void leer() {
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
                String linea;
                while ((linea = r.readLine()) != null) {
                    if (linea.length() > 4096) continue; // los clientes nunca envían líneas largas
                    procesar(this, Msg.decode(linea));
                }
            } catch (IOException ignorada) {
                // el cliente se fue
            } finally {
                desconectar(this);
            }
        }

        private void escribir() {
            try (BufferedWriter w = new BufferedWriter(
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {
                while (true) {
                    String linea = salida.take();
                    if (CERRAR.equals(linea)) break;
                    w.write(linea);
                    w.write('\n');
                    w.flush();
                }
            } catch (IOException | InterruptedException ignorada) {
                // la conexión murió
            } finally {
                cerrarYa();
            }
        }

        void enviar(Msg m) {
            if (!cerrando) salida.offer(m.encode());
        }

        /** Cierra después de enviar lo que ya está en cola (para que llegue un ERROR, por ejemplo). */
        void cerrarSuave() {
            cerrando = true;
            salida.offer(CERRAR);
        }

        void cerrarYa() {
            cerrando = true;
            try {
                socket.close();
            } catch (IOException ignorada) {
                // nada que hacer
            }
        }
    }

    // ------------------------------------------------------------------ mensajes del cliente

    private void procesar(Handler h, Msg m) {
        synchronized (lock) {
            if (cerrado) return;
            if ("HELLO".equals(m.type)) {
                saludar(h, m);
                return;
            }
            if (!h.saludado || !clientes.contains(h)) return;
            switch (m.type) {
                case "ROLE": elegirRol(h, m.s(0)); break;
                case "START": comenzar(h); break;
                case "ACCION": accion(h, m.s(0)); break;
                case "FINAL": accionFinal(h, m.s(0)); break;
                case "POS": posicion(h, m); break;
                case "LISTO": listo(h); break;
                case "FORZAR": forzar(h); break;
                default: break;
            }
        }
    }

    private void saludar(Handler h, Msg m) {
        if (h.saludado) return;
        if (!VERSION.equals(m.s(1))) {
            h.enviar(Msg.of("ERROR", "Versión distinta del juego: el anfitrión usa " + VERSION + "."));
            h.cerrarSuave();
            return;
        }
        if (fase != Fase.SALA) {
            h.enviar(Msg.of("ERROR", "La partida ya comenzó."));
            h.cerrarSuave();
            return;
        }
        if (clientes.size() >= MAX_JUGADORES) {
            h.enviar(Msg.of("ERROR", "La sala está llena (" + MAX_JUGADORES + " jugadores)."));
            h.cerrarSuave();
            return;
        }
        h.nombre = nombreUnico(limpiar(m.s(0)));
        h.id = proxId++;
        h.saludado = true;
        clientes.add(h);
        boolean anfitrion = clientes.get(0) == h;
        h.enviar(Msg.of("WELCOME", h.id, anfitrion ? 1 : 0, String.join(", ", direccionesLocales()), getPuerto()));
        difundirSala();
    }

    private static String limpiar(String nombre) {
        StringBuilder sb = new StringBuilder();
        for (char c : nombre.toCharArray()) {
            if (!Character.isISOControl(c)) sb.append(c);
        }
        String n = sb.toString().trim();
        if (n.length() > 14) n = n.substring(0, 14).trim();
        return n.isEmpty() ? "Jugador" : n;
    }

    private String nombreUnico(String base) {
        String candidato = base;
        int k = 2;
        while (nombreEnUso(candidato)) candidato = base + " (" + k++ + ")";
        return candidato;
    }

    private boolean nombreEnUso(String nombre) {
        for (Handler c : clientes) if (c.nombre.equalsIgnoreCase(nombre)) return true;
        return false;
    }

    private void elegirRol(Handler h, String rol) {
        if (fase != Fase.SALA) return;
        if (!rol.isEmpty() && !esRolValido(rol)) return;
        for (Handler c : clientes) {
            if (c != h && !rol.isEmpty() && rol.equals(c.rol)) {
                h.enviar(Msg.of("ERROR", "Ese rol ya lo eligió " + c.nombre + "."));
                return;
            }
        }
        h.rol = rol;
        difundirSala();
    }

    private static boolean esRolValido(String rol) {
        for (String r : Jugador.ROLES) if (r.equals(rol)) return true;
        return false;
    }

    private void comenzar(Handler h) {
        if (fase != Fase.SALA || clientes.get(0) != h) return;

        // quien no eligió rol recibe el primero libre
        Set<String> usados = new HashSet<>();
        for (Handler c : clientes) if (!c.rol.isEmpty()) usados.add(c.rol);
        for (Handler c : clientes) {
            if (!c.rol.isEmpty()) continue;
            for (String r : Jugador.ROLES) {
                if (!usados.contains(r)) {
                    c.rol = r;
                    usados.add(r);
                    break;
                }
            }
        }

        // los roles van siempre en el mismo orden (también es el orden de turnos); los libres son bots
        List<Jugador> jugadores = new ArrayList<>();
        for (int i = 0; i < Jugador.ROLES.length; i++) {
            String rol = Jugador.ROLES[i];
            Handler dueno = null;
            for (Handler c : clientes) if (rol.equals(c.rol)) dueno = c;
            if (dueno != null) {
                dueno.indice = i;
                jugadores.add(new Jugador(dueno.nombre, rol, false));
            } else {
                jugadores.add(new Jugador("Bot " + rol, rol, true));
            }
        }
        motor = new GameEngine(jugadores);
        for (int i = 0; i < posX.length; i++) {
            posX[i] = Lugar.INICIO[i][0];
            posY[i] = Lugar.INICIO[i][1];
        }

        Object[] nombres = new Object[jugadores.size() + 1];
        for (int i = 0; i < jugadores.size(); i++) nombres[i + 1] = jugadores.get(i).getNombre();
        for (Handler c : clientes) {
            nombres[0] = c.indice;
            c.enviar(Msg.of("START", nombres));
        }
        iniciarRonda();
    }

    private void accion(Handler h, String accion) {
        if (fase != Fase.TURNO || motor == null || h.indice != motor.getIndiceJugadorActual()) return;
        if (!(GameEngine.INDAGAR.equals(accion) || esAccionDirecta(accion))) return;
        // reglas del juego (por ejemplo: el Arconte no puede dar dos discursos seguidos); no gasta el turno
        String motivo = motor.validarAccion(accion);
        if (motivo != null) {
            h.enviar(Msg.of("AVISO", motivo));
            return;
        }
        if (!enSuLugar(h, accion)) return;
        if (GameEngine.INDAGAR.equals(accion)) {
            String mensaje = motor.iniciarVerificacion();
            h.enviar(Msg.of("VERIF", mensaje)); // solo lo ve quien indaga: puede estar equivocado
            fase = Fase.VERIFICANDO;
            difundirEstado();
        } else {
            cerrarConResultado(motor.getJugadorActual(), "", motor.resolverAccionDirecta(accion));
        }
    }

    private void accionFinal(Handler h, String accion) {
        if (fase != Fase.VERIFICANDO || motor == null || h.indice != motor.getIndiceJugadorActual()) return;
        if (!esAccionFinal(accion) || !enSuLugar(h, accion)) return;
        cerrarConResultado(motor.getJugadorActual(), "", motor.finalizarTrasVerificar(accion));
    }

    /** Comprueba que el jugador de turno esté donde corresponde; si no, le avisa a dónde ir. */
    private boolean enSuLugar(Handler h, String accion) {
        Jugador j = motor.getJugadorActual();
        Lugar lugar = Lugar.paraAccion(j.getRol(), accion);
        if (lugar == null || lugar.contiene(posX[h.indice], posY[h.indice])) return true;
        h.enviar(Msg.of("AVISO", Lugar.indicacion(j.getRol(), accion) + "."));
        return false;
    }

    /** Lo que se puede hacer de entrada ante un rumor (además de indagar). */
    private static boolean esAccionDirecta(String accion) {
        return esAccionFinal(accion) || GameEngine.DISCURSO.equals(accion);
    }

    /** Lo que se puede hacer después de indagar. */
    private static boolean esAccionFinal(String accion) {
        return GameEngine.PREGONAR.equals(accion) || GameEngine.CALLAR.equals(accion)
                || GameEngine.DENUNCIAR.equals(accion);
    }

    private void posicion(Handler h, Msg m) {
        if (fase == Fase.SALA || h.indice < 0) return;
        posX[h.indice] = (float) m.d(0);
        posY[h.indice] = (float) m.d(1);
        Msg salida = Msg.of("POS", h.indice, m.d(0), m.d(1));
        for (Handler c : clientes) if (c != h) c.enviar(salida);
    }

    /** Un jugador terminó de leer el resultado; si todos los humanos están listos, sigue el juego. */
    private void listo(Handler h) {
        if (fase != Fase.RESULTADO || h.indice < 0 || h.listo) return;
        h.listo = true;
        if (!revisarAvance()) difundirEstado();
    }

    /**
     * El anfitrión fuerza el avance cuando alguien se quedó dormido: si es el turno de otro jugador, ese turno
     * se resuelve como "Callar"; si el resultado espera a alguien que no pulsa "Continuar", sigue sin él.
     */
    private void forzar(Handler h) {
        if (motor == null || clientes.isEmpty() || clientes.get(0) != h) return;
        Jugador actual = motor.getJugadorActual();
        if (fase == Fase.TURNO) {
            cerrarConResultado(actual, "El anfitrión saltó este turno. ", motor.resolverAccionDirecta(GameEngine.CALLAR));
        } else if (fase == Fase.VERIFICANDO) {
            cerrarConResultado(actual, "El anfitrión saltó este turno. ", motor.finalizarTrasVerificar(GameEngine.CALLAR));
        } else if (fase == Fase.RESULTADO) {
            avanzar();
        }
    }

    /** Si ya nadie falta por pulsar "Continuar", avanza. Devuelve true si avanzó. */
    private boolean revisarAvance() {
        if (fase != Fase.RESULTADO) return false;
        for (Handler c : clientes) if (c.indice >= 0 && !c.listo) return false;
        avanzar();
        return true;
    }

    private void avanzar() {
        if (motor.juegoTerminado()) terminarPartida();
        else iniciarRonda();
    }

    // ------------------------------------------------------------------ desarrollo de la partida

    private void iniciarRonda() {
        eventoActual = motor.dispararEventoAleatorio();
        motor.generarPublicacion();
        resultado = "";
        fase = Fase.TURNO;
        // un humano decide sin límite de tiempo; un bot actúa tras una breve pausa
        botEn = motor.getJugadorActual().isBot() ? System.currentTimeMillis() + botMs : 0;
        difundirEstado();
    }

    /** Guarda el resultado de un turno (con quién lo jugó) y espera a que todos lo lean y pulsen "Continuar". */
    private void cerrarConResultado(Jugador actor, String prefijo, String texto) {
        ultimoActor = motor.getJugadores().indexOf(actor);
        resultado = actor.getNombre() + " (" + actor.getRol() + ")\n" + prefijo + texto;
        fase = Fase.RESULTADO;
        for (Handler c : clientes) c.listo = false;
        if (!revisarAvance()) difundirEstado();
    }

    private void tick() {
        try {
            synchronized (lock) {
                if (cerrado || motor == null) return;
                Jugador actual = motor.getJugadorActual();
                switch (fase) {
                    case TURNO:
                        if (actual.isBot()) {
                            if (botEn == 0) botEn = System.currentTimeMillis() + botMs;
                            if (System.currentTimeMillis() >= botEn) turnoDeBot();
                        }
                        break;
                    case VERIFICANDO:
                        if (actual.isBot()) {
                            // el jugador se desconectó a media indagación: decide el bot
                            String fin = motor.decidirAccionBotTrasVerificar();
                            moverBotA(motor.getIndiceJugadorActual(), Lugar.paraAccion(actual.getRol(), fin));
                            cerrarConResultado(actual, "", motor.finalizarTrasVerificar(fin));
                        }
                        break;
                    default:
                        break;
                }
            }
        } catch (Throwable t) {
            t.printStackTrace(); // el reloj no debe morir por un error aislado
        }
    }

    /** Los bots también "caminan": su personaje aparece en el lugar donde actúan. */
    private void moverBotA(int indice, Lugar lugar) {
        if (lugar == null) return;
        posX[indice] = lugar.centroX();
        posY[indice] = lugar.centroY();
        Msg m = Msg.of("POS", indice, posX[indice], posY[indice]);
        for (Handler c : clientes) c.enviar(m);
    }

    private void turnoDeBot() {
        Jugador bot = motor.getJugadorActual();
        int indice = motor.getIndiceJugadorActual();
        String accion = motor.decidirAccionBot();
        moverBotA(indice, Lugar.paraAccion(bot.getRol(), accion));
        String texto;
        if (GameEngine.INDAGAR.equals(accion)) {
            motor.iniciarVerificacion();
            String fin = motor.decidirAccionBotTrasVerificar();
            moverBotA(indice, Lugar.paraAccion(bot.getRol(), fin));
            texto = "Indagó y eligió " + Jugador.etiqueta(bot.getRol(), fin) + ".\n" + motor.finalizarTrasVerificar(fin);
        } else {
            texto = "Eligió " + Jugador.etiqueta(bot.getRol(), accion) + ".\n" + motor.resolverAccionDirecta(accion);
        }
        cerrarConResultado(bot, "", texto);
    }

    private void terminarPartida() {
        eleccionFinal = motor.realizarEleccion();
        destinosFinales = motor.destinosFinales();
        fase = Fase.FIN;
        difundirEstado();
    }

    // ------------------------------------------------------------------ difusión

    private void difundirSala() {
        Object[] campos = new Object[1 + clientes.size() * 3];
        campos[0] = clientes.size();
        int k = 1;
        for (int i = 0; i < clientes.size(); i++) {
            Handler c = clientes.get(i);
            campos[k++] = c.nombre;
            campos[k++] = c.rol;
            campos[k++] = i == 0 ? 1 : 0;
        }
        Msg m = Msg.of("LOBBY", campos);
        for (Handler c : clientes) c.enviar(m);
    }

    private void difundirEstado() {
        Msg m = construirSnapshot().toMsg();
        for (Handler c : clientes) c.enviar(m);
    }

    private void difundirAviso(String texto) {
        Msg m = Msg.of("AVISO", texto);
        for (Handler c : clientes) c.enviar(m);
    }

    private Snapshot construirSnapshot() {
        Snapshot s = new Snapshot();
        s.fase = fase.name();
        s.actual = motor.getIndiceJugadorActual();
        // durante el resultado el motor ya pasó al siguiente turno: quien jugó es el último actor
        s.actor = (fase == Fase.RESULTADO || fase == Fase.FIN) ? ultimoActor : s.actual;
        s.resueltas = motor.getPublicacionesResueltas();
        s.total = GameEngine.PUBLICACIONES_PARA_TERMINAR;
        s.puedeDeclaracion = motor.puedeDeclaracionPublica();

        Publicacion p = motor.getPublicacionActual();
        if (p != null) {
            s.pubTexto = p.getTexto();
            s.pubAutor = p.getAutor();
        }
        s.evento = eventoActual;

        CiudadEstado c = motor.getCiudad();
        s.ciudad[0] = c.getInformacionVerificada();
        s.ciudad[1] = c.getConfianza();
        s.ciudad[2] = c.getConvivencia();
        s.ciudad[3] = c.getBienestarDigital();
        s.ciudad[4] = c.getDesinformacion();
        s.ciudad[5] = c.getConflictos();

        for (Jugador j : motor.getJugadores()) {
            Snapshot.Jug jj = new Snapshot.Jug();
            jj.nombre = j.getNombre();
            jj.rol = j.getRol();
            jj.bot = j.isBot();
            jj.puntos = j.getPuntuacion();
            jj.reputacion = j.getReputacion();
            jj.verificacionesCorrectas = j.getVerificacionesCorrectas();
            jj.compartidasFalsas = j.getCompartidasFalsas();
            s.jugadores.add(jj);
        }

        if (fase == Fase.RESULTADO || fase == Fase.FIN) {
            s.resultado = resultado;
            s.camino = motor.caminoComoTexto();
            if (motor.getUltimoOrigenPropagacion() != null) {
                s.difusion = motor.getUltimoOrigenPropagacion().getNombre() + " llegó a "
                        + motor.getUltimosReceptores().size() + " personas.";
            }
        }
        for (Jugador amigo : motor.getGrafo().vecinos(motor.getJugadorActual())) s.amigos.add(amigo.getNombre());
        if (fase == Fase.RESULTADO) {
            for (Handler cl : clientes) if (cl.indice >= 0 && !cl.listo) s.pendientes.add(cl.indice);
        }

        if (fase == Fase.FIN) {
            s.finTitulo = eleccionFinal.titulo();
            s.finTexto = eleccionFinal.texto();
            for (GameEngine.Destino d : destinosFinales) {
                Snapshot.Destino dd = new Snapshot.Destino();
                dd.nombre = d.jugador().getNombre();
                dd.rol = d.jugador().getRol();
                dd.clave = d.clave();
                dd.titulo = d.titulo();
                dd.texto = d.texto();
                s.destinos.add(dd);
            }
        }
        return s;
    }

    // ------------------------------------------------------------------ desconexiones

    private void desconectar(Handler h) {
        boolean vacio;
        synchronized (lock) {
            if (!clientes.remove(h)) {
                h.cerrarYa();
                return;
            }
            h.cerrarYa();
            if (fase == Fase.SALA) {
                difundirSala();
            } else if (motor != null && h.indice >= 0 && fase != Fase.FIN) {
                Jugador j = motor.getJugadores().get(h.indice);
                j.setBot(true);
                difundirAviso(j.getNombre() + " se desconectó: un bot toma su lugar.");
                if (fase == Fase.TURNO && motor.getIndiceJugadorActual() == h.indice) {
                    botEn = System.currentTimeMillis() + botMs;
                }
                if (!revisarAvance()) difundirEstado();
            }
            vacio = clientes.isEmpty();
        }
        if (vacio) close(); // si ya no queda nadie, la sala se cierra sola
    }
}
