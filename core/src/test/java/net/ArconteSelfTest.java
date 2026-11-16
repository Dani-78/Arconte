package net;

import model.GameEngine;
import model.Jugador;
import model.Lugar;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Prueba de integración SIN interfaz gráfica: levanta un servidor real, conecta clientes reales por socket
 * y juega una partida completa. Sale con código 1 si algo falla.
 *
 * Ejecutar (desde la carpeta del proyecto, con un JDK 17+):
 *   javac -encoding UTF-8 -d /tmp/arconte-test core/src/main/java/model/*.java core/src/main/java/net/*.java core/src/test/java/net/*.java
 *   java -cp /tmp/arconte-test net.ArconteSelfTest
 */
public final class ArconteSelfTest {

    private static int fallos = 0;

    private static void check(boolean ok, String descripcion) {
        System.out.println((ok ? "  OK   " : "  FALLA ") + descripcion);
        if (!ok) fallos++;
    }

    /** Un jugador de prueba: lee mensajes y decide solo qué hacer, caminando al lugar de cada acción. */
    private static final class Bot {
        final String nombre;
        final GameClient cliente;
        final Random rnd;
        int miIndice = -1;
        boolean esAnfitrion;
        Snapshot ultimo;
        final List<String> errores = new ArrayList<>();
        final List<String> avisos = new ArrayList<>();
        final List<String> verifs = new ArrayList<>();
        final List<double[]> posiciones = new ArrayList<>();
        final List<String> resultados = new ArrayList<>();
        int resultadosVistos;
        int actorIncoherente;
        List<String> lobbyNombres = new ArrayList<>();
        List<String> lobbyRoles = new ArrayList<>();

        boolean pasivo;          // no juega su turno
        boolean noContinua;      // no pulsa "Continuar"
        boolean probarFuera;     // en su primer turno intenta actuar desde un lugar equivocado
        boolean esperandoProbe;  // ya intentó actuar fuera de lugar y espera a que el test lo revise
        int turnoActuado = -1;
        int listoEnviado = -1;

        Bot(String nombre, int puerto, long semilla) throws Exception {
            this.nombre = nombre;
            this.rnd = new Random(semilla);
            this.cliente = GameClient.conectar("127.0.0.1", puerto, 3000);
            cliente.enviar(Msg.of("HELLO", nombre, GameServer.VERSION));
        }

        void bombear() {
            Msg m;
            while ((m = cliente.siguiente()) != null) manejar(m);
            reaccionar();
        }

        private void irA(String accion) {
            Lugar l = Lugar.paraAccion(Jugador.ROLES[miIndice], accion);
            if (l != null) cliente.enviar(Msg.of("POS", l.centroX(), l.centroY()));
        }

        private void reaccionar() {
            if (ultimo == null || miIndice < 0) return;
            if (Snapshot.TURNO.equals(ultimo.fase) && ultimo.actual == miIndice && !pasivo && !esperandoProbe
                    && turnoActuado != ultimo.resueltas) {
                if (probarFuera) {
                    // intenta pregonar desde una esquina del mapa, donde no hay ningún lugar
                    cliente.enviar(Msg.of("POS", 10, 10));
                    cliente.enviar(Msg.of("ACCION", GameEngine.PREGONAR));
                    esperandoProbe = true;
                    return;
                }
                turnoActuado = ultimo.resueltas;
                String[] opciones = {GameEngine.PREGONAR, GameEngine.INDAGAR, GameEngine.INDAGAR,
                        GameEngine.CALLAR, GameEngine.DENUNCIAR};
                String accion = opciones[rnd.nextInt(opciones.length)];
                irA(accion);
                cliente.enviar(Msg.of("ACCION", accion));
            }
            if (Snapshot.RESULTADO.equals(ultimo.fase) && ultimo.pendientes.contains(miIndice) && !noContinua
                    && listoEnviado != ultimo.resueltas) {
                listoEnviado = ultimo.resueltas;
                cliente.enviar(Msg.of("LISTO"));
            }
        }

        private void manejar(Msg m) {
            switch (m.type) {
                case "WELCOME": esAnfitrion = m.i(1) == 1; break;
                case "LOBBY": {
                    lobbyNombres.clear();
                    lobbyRoles.clear();
                    int n = m.i(0);
                    for (int i = 0; i < n; i++) {
                        lobbyNombres.add(m.s(1 + i * 3));
                        lobbyRoles.add(m.s(2 + i * 3));
                    }
                    break;
                }
                case "START": miIndice = m.i(0); break;
                case "ERROR": errores.add(m.s(0)); break;
                case "AVISO": avisos.add(m.s(0)); break;
                case "POS": posiciones.add(new double[]{m.i(0), m.d(1), m.d(2)}); break;
                case "VERIF": {
                    verifs.add(m.s(0));
                    if (!pasivo) {
                        String[] opciones = {GameEngine.PREGONAR, GameEngine.CALLAR, GameEngine.DENUNCIAR};
                        String accion = opciones[rnd.nextInt(3)];
                        irA(accion);
                        cliente.enviar(Msg.of("FINAL", accion));
                    }
                    break;
                }
                case "STATE": {
                    ultimo = Snapshot.fromMsg(m);
                    if (Snapshot.RESULTADO.equals(ultimo.fase) && !ultimo.resultado.isEmpty()) {
                        resultadosVistos++;
                        // regresión: antes la pantalla decía "consecuencias del turno de X" con el jugador SIGUIENTE
                        if (!ultimo.resultado.contains("(" + Jugador.ROLES[ultimo.actor] + ")")
                                || ultimo.actor == ultimo.actual) actorIncoherente++;
                    }
                    if (Snapshot.RESULTADO.equals(ultimo.fase) && !ultimo.resultado.isEmpty()
                            && (resultados.isEmpty() || !resultados.get(resultados.size() - 1).equals(ultimo.resultado))) {
                        resultados.add(ultimo.resultado);
                    }
                    break;
                }
                default: break;
            }
        }
    }

    private static void esperar(long ms, Bot... bots) throws InterruptedException {
        long fin = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < fin) {
            for (Bot b : bots) b.bombear();
            Thread.sleep(10);
        }
    }

    private static boolean esperarHasta(long maxMs, java.util.function.BooleanSupplier cond, Bot... bots)
            throws InterruptedException {
        long fin = System.currentTimeMillis() + maxMs;
        while (System.currentTimeMillis() < fin) {
            for (Bot b : bots) b.bombear();
            if (cond.getAsBoolean()) return true;
            Thread.sleep(10);
        }
        return cond.getAsBoolean();
    }

    public static void main(String[] args) throws Exception {
        System.out.println("1) Mensajes: escape y ida y vuelta");
        Msg original = Msg.of("X", "con\ttab", "con\nsalto", "con \\ barra", "áéíóú ñ Logógrafo", "");
        Msg vuelta = Msg.decode(original.encode());
        check(vuelta.type.equals("X") && vuelta.s(0).equals("con\ttab") && vuelta.s(1).equals("con\nsalto")
                && vuelta.s(2).equals("con \\ barra") && vuelta.s(3).equals("áéíóú ñ Logógrafo") && vuelta.s(4).isEmpty(),
                "los campos con tabuladores, saltos, barras, tildes y vacíos sobreviven");
        check(!original.encode().contains("\n"), "un mensaje nunca ocupa más de una línea");

        System.out.println("2) Lugares del mapa");
        boolean inicio = true;
        for (float[] p : Lugar.INICIO) inicio &= Lugar.AGORA.contiene(p[0], p[1]);
        check(inicio, "los cuatro personajes empiezan dentro del Ágora (se puede pregonar desde el principio)");
        boolean sinSolape = true;
        for (Lugar a : Lugar.values()) {
            for (Lugar b : Lugar.values()) {
                if (a != b && a.contiene(b.centroX(), b.centroY())) sinSolape = false;
            }
        }
        check(sinSolape, "ningún lugar contiene el centro de otro");
        check(Lugar.paraAccion(Jugador.LOGOGRAFO, GameEngine.INDAGAR) == Lugar.METROON
                && Lugar.paraAccion(Jugador.POLITES, GameEngine.INDAGAR) == Lugar.PUERTO
                && Lugar.paraAccion(Jugador.POLITES, GameEngine.PREGONAR) == Lugar.AGORA
                && Lugar.paraAccion(Jugador.SOFISTA, GameEngine.PREGONAR) == Lugar.AGORA
                && Lugar.paraAccion(Jugador.POLITES, GameEngine.DENUNCIAR) == Lugar.HELIEA
                && Lugar.paraAccion(Jugador.ARCONTE, GameEngine.DISCURSO) == Lugar.PNYX,
                "cada acción tiene su lugar según el rol");
        boolean callarLibre = true;
        for (String rol : Jugador.ROLES) callarLibre &= Lugar.paraAccion(rol, GameEngine.CALLAR) == null;
        check(callarLibre, "Callar se puede hacer en cualquier parte, también el Arconte (el discurso es otra acción)");
        boolean textosRol = true;
        for (String rol : Jugador.ROLES) {
            String intro = Jugador.bienvenida("Danna", rol);
            textosRol &= !Jugador.mision(rol).isEmpty() && !Jugador.equivalente(rol).isEmpty()
                    && Jugador.acciones(rol).length >= 4 && intro.contains("Danna") && intro.contains(rol)
                    && intro.contains("Ossa") && intro.contains("Aletheia") && intro.contains("Eris")
                    && !Jugador.archivo(rol).matches(".*[^a-z].*");
        }
        check(textosRol, "cada rol tiene misión, equivalente moderno, acciones y una bienvenida completa");
        boolean descripciones = true;
        for (Lugar l : Lugar.values()) descripciones &= l.descripcion.length() > 60 && l.donde.startsWith("en ");
        check(descripciones && !Lugar.CALLES.isEmpty(), "cada lugar explica qué era en la antigua Grecia");
        java.util.List<String> problemasMapa = model.Polis.verificarConectividad();
        check(problemasMapa.isEmpty(), "el mapa es caminable: los 5 lugares y los 4 inicios están conectados " + problemasMapa);

        System.out.println("3) Sala: máximo 4 jugadores y nombres únicos");
        GameServer s1 = new GameServer(0);
        s1.iniciar();
        // se conectan de a uno, como en la vida real: el anfitrión siempre entra primero
        Bot a = new Bot("Danna", s1.getPuerto(), 1);
        esperar(200, a);
        Bot b = new Bot("Danna", s1.getPuerto(), 2);
        esperar(100, a, b);
        Bot c = new Bot("Carlos", s1.getPuerto(), 3);
        esperar(100, a, b, c);
        Bot d = new Bot("Eva", s1.getPuerto(), 4);
        esperar(400, a, b, c, d);
        check(a.esAnfitrion && !b.esAnfitrion, "el primero en entrar es el anfitrión");
        check(a.lobbyNombres.size() == 4 && a.lobbyNombres.contains("Danna (2)"), "nombre repetido se renombra: " + a.lobbyNombres);
        Bot e = new Bot("Sobrante", s1.getPuerto(), 5);
        esperar(400, a, e);
        check(!e.errores.isEmpty() && e.errores.get(0).contains("llena"), "el quinto jugador es rechazado: " + e.errores);
        a.cliente.enviar(Msg.of("ROLE", Jugador.SOFISTA));
        esperar(150, a, b);
        b.cliente.enviar(Msg.of("ROLE", Jugador.SOFISTA));
        esperar(250, a, b);
        check(!b.errores.isEmpty(), "no se puede elegir un rol ya ocupado: " + b.errores);
        b.cliente.enviar(Msg.of("ROLE", "RolInventado"));
        esperar(150, b);
        check(!b.lobbyRoles.contains("RolInventado"), "un rol inventado se ignora");
        a.cliente.close(); b.cliente.close(); c.cliente.close(); d.cliente.close();
        esperar(500);
        s1.close();

        System.out.println("4) Partida completa: 2 humanos + 2 bots, sin reloj, con \"Continuar\" y lugares del mapa");
        GameServer s2 = new GameServer(0);
        s2.setRitmo(60);
        s2.iniciar();
        Bot ana = new Bot("Ana", s2.getPuerto(), 11);
        Bot beto = new Bot("Beto", s2.getPuerto(), 12);
        esperar(300, ana, beto);
        ana.cliente.enviar(Msg.of("ROLE", Jugador.SOFISTA));
        beto.cliente.enviar(Msg.of("ROLE", Jugador.POLITES));
        esperar(300, ana, beto);
        beto.pasivo = true;       // Beto (Polites) abre la partida y no juega: el turno debe esperarlo
        beto.noContinua = true;   // y tampoco pulsará "Continuar" cuando le toque
        ana.probarFuera = true;   // Ana intentará su primera acción desde un lugar equivocado
        beto.cliente.enviar(Msg.of("START"));
        esperar(300, ana, beto);
        check(ana.miIndice == -1 && beto.miIndice == -1, "solo el anfitrión puede comenzar la partida");
        ana.cliente.enviar(Msg.of("START"));
        check(esperarHasta(2000, () -> ana.ultimo != null && beto.ultimo != null && ana.miIndice >= 0, ana, beto),
                "la partida arranca y ambos reciben su estado");
        check(beto.miIndice == 0 && ana.miIndice == 2, "índices por rol: Polites=0, Sofista=2 (Beto=" + beto.miIndice + ", Ana=" + ana.miIndice + ")");
        check(ana.ultimo.jugadores.size() == 4 && ana.ultimo.jugadores.get(1).bot && ana.ultimo.jugadores.get(3).bot,
                "Logógrafo y Arconte son bots");
        check(ana.ultimo.total == GameEngine.PUBLICACIONES_PARA_TERMINAR, "el total de rumores viaja en el estado");

        ana.cliente.enviar(Msg.of("POS", 123.5, 456.5));
        check(esperarHasta(1000, () -> !beto.posiciones.isEmpty(), ana, beto)
                && beto.posiciones.get(0)[0] == 2 && beto.posiciones.get(0)[1] == 123.5,
                "la posición de Ana en el mapa llega a Beto");

        // sin límite de tiempo: el turno de Beto espera aunque pase mucho rato
        esperar(2500, ana, beto);
        check(ana.resultados.isEmpty() && Snapshot.TURNO.equals(ana.ultimo.fase) && ana.ultimo.actual == 0,
                "el turno de un humano inactivo espera sin límite de tiempo (2,5 s después sigue siendo su turno)");

        // Beto sigue dormido: solo el anfitrión puede saltarle el turno
        beto.cliente.enviar(Msg.of("FORZAR"));
        esperar(300, ana, beto);
        check(Snapshot.TURNO.equals(ana.ultimo.fase) && ana.ultimo.actual == 0,
                "un jugador que no es anfitrión no puede saltar turnos");
        ana.cliente.enviar(Msg.of("FORZAR"));
        check(esperarHasta(3000, () -> !ana.resultados.isEmpty()
                        && ana.resultados.get(0).contains("El anfitrión saltó este turno"), ana, beto),
                "el anfitrión puede saltar el turno de alguien que se quedó dormido");
        beto.pasivo = false; // en los demás turnos Beto sí juega
        esperar(900, ana, beto);
        check(Snapshot.RESULTADO.equals(ana.ultimo.fase) && ana.ultimo.pendientes.equals(java.util.List.of(0)),
                "el juego no avanza mientras Beto no pulse Continuar (pendientes=" + ana.ultimo.pendientes + ")");
        beto.noContinua = false;
        check(esperarHasta(3000, () -> ana.resultados.size() >= 2, ana, beto),
                "al pulsar Continuar el juego sigue con el siguiente rumor");

        // los bots caminan a su lugar (el Logógrafo bot siempre indaga: va al Metroon)
        check(esperarHasta(4000, () -> {
            for (double[] p : beto.posiciones) {
                if (p[0] == 1 && Math.abs(p[1] - Lugar.METROON.centroX()) < 1 && Math.abs(p[2] - Lugar.METROON.centroY()) < 1) return true;
            }
            return false;
        }, ana, beto), "el Logógrafo bot aparece en el Metroon cuando indaga");

        // Ana intenta actuar desde un lugar equivocado: debe ser rechazada con un aviso
        check(esperarHasta(8000, () -> ana.esperandoProbe, ana, beto), "llega el turno de Ana (Sofista)");
        check(esperarHasta(1500, () -> !ana.avisos.isEmpty(), ana, beto)
                        && ana.avisos.get(ana.avisos.size() - 1).contains("Ágora"),
                "actuar fuera de lugar es rechazado con una indicación: " + ana.avisos);
        esperar(300, ana, beto);
        check(Snapshot.TURNO.equals(ana.ultimo.fase) && ana.ultimo.actual == 2,
                "tras el intento fuera de lugar, el turno de Ana sigue abierto");
        int avisosAntesDiscurso = ana.avisos.size();
        ana.cliente.enviar(Msg.of("ACCION", GameEngine.DISCURSO));
        check(esperarHasta(1500, () -> ana.avisos.size() > avisosAntesDiscurso
                        && ana.avisos.get(ana.avisos.size() - 1).contains("Solo el Arconte"), ana, beto)
                        && Snapshot.TURNO.equals(ana.ultimo.fase),
                "un Sofista no puede dar el discurso del Arconte y no pierde su turno");
        ana.probarFuera = false;
        ana.esperandoProbe = false;

        // FORZAR en una pausa de resultados: solo el anfitrión puede seguir sin alguien que no pulsa Continuar
        beto.noContinua = true;
        check(esperarHasta(8000, () -> Snapshot.RESULTADO.equals(ana.ultimo.fase) && ana.ultimo.pendientes.contains(0),
                ana, beto), "llega otra pausa de resultados con Beto sin continuar");
        int resueltasAntes = ana.ultimo.resueltas;
        beto.cliente.enviar(Msg.of("FORZAR"));
        esperar(400, ana, beto);
        check(Snapshot.RESULTADO.equals(ana.ultimo.fase) && ana.ultimo.resueltas == resueltasAntes,
                "un jugador que no es anfitrión no puede forzar el avance");
        ana.cliente.enviar(Msg.of("FORZAR"));
        check(esperarHasta(2000, () -> !Snapshot.RESULTADO.equals(ana.ultimo.fase) || ana.ultimo.resueltas > resueltasAntes, ana, beto),
                "el anfitrión sí puede forzar el avance");
        beto.noContinua = false;

        // Ana bombardea al servidor con acciones ilegales mientras avanza la partida; ninguna debe tener efecto
        check(esperarHasta(25000, () -> {
            if (ana.ultimo != null && ana.ultimo.actual != 2) {
                ana.cliente.enviar(Msg.of("ACCION", GameEngine.PREGONAR));
                ana.cliente.enviar(Msg.of("FINAL", GameEngine.PREGONAR));
            }
            ana.cliente.enviar(Msg.of("ACCION", "InventadaXYZ"));
            return ana.ultimo != null && ana.ultimo.resueltas >= 8;
        }, ana, beto), "se llega a la mitad de la partida bajo un bombardeo de acciones ilegales");

        // a mitad de la partida Beto se desconecta
        beto.cliente.close();
        check(esperarHasta(3000, () -> !ana.avisos.isEmpty() && ana.avisos.get(ana.avisos.size() - 1).contains("desconect"), ana),
                "Ana recibe el aviso de desconexión: " + ana.avisos.get(ana.avisos.size() - 1));
        check(esperarHasta(3000, () -> ana.ultimo.jugadores.get(0).bot, ana), "el Polites que se fue queda como bot");

        check(esperarHasta(40000, () -> ana.ultimo != null && Snapshot.FIN.equals(ana.ultimo.fase), ana),
                "la partida llega al final aunque un humano se haya ido");
        Snapshot fin = ana.ultimo;
        check(fin.resueltas == GameEngine.PUBLICACIONES_PARA_TERMINAR, "se resolvieron los " + fin.total + " rumores");
        check(fin.destinos.size() == 4, "hay un destino por jugador: " + destinos(fin));
        check(!fin.finTitulo.isEmpty() && !fin.finTexto.isEmpty(), "hay resultado de la elección: " + fin.finTitulo);
        boolean claves = true;
        for (Snapshot.Destino dd : fin.destinos) {
            claves &= dd.clave.equals(GameEngine.DESTINO_CICUTA) || dd.clave.equals(GameEngine.DESTINO_OSTRACISMO)
                    || dd.clave.equals(GameEngine.DESTINO_HERMES) || dd.clave.equals(GameEngine.DESTINO_ANONIMATO);
        }
        check(claves, "todas las claves de destino son válidas");

        boolean orden = ana.resultados.size() == GameEngine.PUBLICACIONES_PARA_TERMINAR;
        for (int i = 0; orden && i < ana.resultados.size(); i++) {
            orden = ana.resultados.get(i).contains("(" + Jugador.ROLES[i % 4] + ")");
        }
        check(ana.resultadosVistos >= 10 && ana.actorIncoherente == 0,
                "en cada pantalla de resultados el 'actor' es quien jugó, no el siguiente (" + ana.resultadosVistos
                        + " resultados revisados, " + ana.actorIncoherente + " incoherentes)");
        check(orden, "los " + ana.resultados.size() + " resultados llegaron completos y en orden de turnos (Polites, Logógrafo, Sofista, Arconte...)");
        check(beto.verifs.size() + ana.verifs.size() > 0, "hubo indagaciones durante la partida (Ana=" + ana.verifs.size() + ", Beto=" + beto.verifs.size() + ")");

        ana.cliente.close();
        esperar(400);
        s2.close();

        System.out.println("5) El Arconte humano: discurso en la Pnyx y consejo de los hetairoi");
        GameServer s3 = new GameServer(0);
        s3.setRitmo(40);
        s3.iniciar();
        Bot dario = new Bot("Dario", s3.getPuerto(), 21);
        esperar(300, dario);
        dario.cliente.enviar(Msg.of("ROLE", Jugador.ARCONTE));
        esperar(200, dario);
        dario.pasivo = true; // el test juega por él
        dario.cliente.enviar(Msg.of("START"));
        check(esperarHasta(10000, () -> dario.ultimo != null && Snapshot.TURNO.equals(dario.ultimo.fase)
                && dario.ultimo.actual == 3, dario), "llega el primer turno del Arconte (los 3 bots ya jugaron)");
        check(dario.ultimo.puedeDeclaracion, "en su primer turno el Arconte puede dar discurso");
        int av0 = dario.avisos.size();
        dario.cliente.enviar(Msg.of("ACCION", GameEngine.DISCURSO)); // desde el Ágora, no desde la Pnyx
        check(esperarHasta(1500, () -> dario.avisos.size() > av0 && dario.avisos.get(dario.avisos.size() - 1).contains("Pnyx"), dario)
                        && Snapshot.TURNO.equals(dario.ultimo.fase),
                "el discurso desde el lugar equivocado se rechaza diciendo que vaya a la Pnyx: " + dario.avisos.get(dario.avisos.size() - 1));
        dario.cliente.enviar(Msg.of("POS", Lugar.PNYX.centroX(), Lugar.PNYX.centroY()));
        dario.cliente.enviar(Msg.of("ACCION", GameEngine.DISCURSO));
        check(esperarHasta(3000, () -> !dario.resultados.isEmpty() && dario.resultados.get(dario.resultados.size() - 1).contains("(Arconte)")
                        && dario.resultados.get(dario.resultados.size() - 1).contains("discurso"), dario),
                "en la Pnyx el discurso se da y gasta el turno");
        check(esperarHasta(10000, () -> Snapshot.TURNO.equals(dario.ultimo.fase) && dario.ultimo.actual == 3
                && dario.ultimo.resueltas == 7, dario), "llega su siguiente turno (dos turnos propios después)");
        check(!dario.ultimo.puedeDeclaracion, "el estado avisa que aún no puede volver a hablar");
        int av1 = dario.avisos.size();
        int resultadosAntes = dario.resultados.size();
        dario.cliente.enviar(Msg.of("ACCION", GameEngine.DISCURSO));
        check(esperarHasta(1500, () -> dario.avisos.size() > av1 && dario.avisos.get(dario.avisos.size() - 1).contains("hetairoi"), dario),
                "el segundo discurso seguido lo desaconsejan los hetairoi: " + dario.avisos.get(dario.avisos.size() - 1));
        esperar(200, dario);
        check(Snapshot.TURNO.equals(dario.ultimo.fase) && dario.resultados.size() == resultadosAntes,
                "ese rechazo NO gasta el turno (ya no dice que 'ignoraste el evento')");
        dario.cliente.enviar(Msg.of("ACCION", GameEngine.CALLAR)); // Callar es libre: vale desde cualquier parte
        check(esperarHasta(3000, () -> dario.resultados.size() > resultadosAntes
                        && dario.resultados.get(dario.resultados.size() - 1).contains("Callaste"), dario),
                "en cambio puede Callar, y eso es solo callar");
        dario.cliente.close();
        esperar(300);
        s3.close();

        System.out.println(fallos == 0 ? "\nTODO BIEN" : "\nHAY " + fallos + " FALLO(S)");
        System.exit(fallos == 0 ? 0 : 1);
    }

    private static String destinos(Snapshot s) {
        StringBuilder sb = new StringBuilder();
        for (Snapshot.Destino d : s.destinos) sb.append(d.nombre).append("=").append(d.clave).append(" ");
        return sb.toString().trim();
    }
}
