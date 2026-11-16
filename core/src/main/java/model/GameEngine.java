package model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/**
 * Motor de Arconte. Coordina:
 *  - El árbol de decisiones (consecuencias de cada acción).
 *  - El grafo social (propagación de rumores entre los habitantes de Helíade).
 *  - El estado de la polis y de los jugadores.
 *  - Los presagios (eventos aleatorios de los dioses) y el banco de rumores.
 *  - Los finales: la elección del Arconte y el destino de cada jugador.
 *
 * El motor NO sabe nada de la red: el servidor lo maneja y los clientes solo ven instantáneas.
 */
public class GameEngine {

    // Acciones base (los botones cambian de nombre según el rol, ver Jugador.etiqueta)
    public static final String PREGONAR = "Pregonar";
    public static final String INDAGAR = "Indagar";
    public static final String CALLAR = "Callar";
    public static final String DENUNCIAR = "Denunciar";
    /** Solo el Arconte: discurso en la Pnyx. Es una acción propia, separada de Callar. */
    public static final String DISCURSO = "Discurso";

    // Destinos finales de cada jugador
    public static final String DESTINO_CICUTA = "CICUTA";
    public static final String DESTINO_OSTRACISMO = "OSTRACISMO";
    public static final String DESTINO_HERMES = "HERMES";
    public static final String DESTINO_ANONIMATO = "ANONIMATO";

    /** Umbrales del puntaje general de la polis (arranca en 200; calibrados con simulaciones de 16 turnos). */
    private static final int UMBRAL_EDAD_DE_ORO = 250;
    private static final int UMBRAL_EQUILIBRIO = 200;
    private static final int UMBRAL_DISCORDIA = 140;
    /** Hermes solo bendice si la polis también quedó en buen estado. */
    private static final int UMBRAL_HERMES_POLIS = 230;

    /** Cuántos rumores salen en la partida (con 4 roles: 4 turnos por rol). */
    public static final int PUBLICACIONES_PARA_TERMINAR = 16;

    /** Segundos que tiene el jugador por turno. */
    public static final int SEGUNDOS_POR_TURNO = 30;

    /** Probabilidad de que un rumor sea falso. */
    private static final double PROB_PUBLICACION_FALSA = 0.55;

    /** Rango de puntos de impacto de cada rumor. */
    private static final int IMPACTO_MIN = 20;
    private static final int IMPACTO_MAX = 90;

    /** Probabilidad de acertar al indagar (el Logógrafo siempre acierta). */
    private static final double PROB_BASE_VERIFICACION = 0.55;
    /** Con el presagio de Apate, indagar es mucho más difícil. */
    private static final double PROB_VERIFICACION_APATE = 0.25;

    /** Distancia de amistad a la que llega un pregón normal / una declamación del Sofista. */
    private static final int PROFUNDIDAD_COMPARTIR_NORMAL = 1;
    private static final int PROFUNDIDAD_IMPULSO = 2;

    /**
     * Cada cuántos turnos (globales) el Arconte puede dar un discurso en la Pnyx. Con 4 roles equivale a
     * "un discurso cada dos turnos propios": si habló la última vez, sus hetairoi le aconsejan esperar.
     */
    private static final int COOLDOWN_DECLARACION_TURNOS = 5;
    private static final int BONUS_DECLARACION_CONFIANZA = 8;
    private static final int BONUS_DECLARACION_CONVIVENCIA = 5;
    private static final int PUNTOS_DECLARACION = 6;

    private final DecisionTree arbolDecisiones = new DecisionTree();
    private final GrafoSocial grafo = new GrafoSocial();
    private final CiudadEstado ciudad = new CiudadEstado();
    private final List<Jugador> jugadores = new ArrayList<>();
    private final List<String> bitacora = new ArrayList<>();
    private final Random rnd;

    private int turno = 0;
    private int jugadorActual = 0;
    private int publicacionesResueltas = 0;
    private int turnoUltimaDeclaracion = -1000;
    private Publicacion publicacionActual;
    private List<DecisionTree.NodoDecision> ultimoCamino = new ArrayList<>();

    private boolean esperandoDecisionPostVerificacion = false;
    private Boolean percepcionVerdadera; // lo que el jugador CREE tras indagar (puede estar equivocado)

    // Presagios activos: valen solo para el turno en que se disparan
    private boolean ossaActiva = false;
    private boolean apateActiva = false;
    private boolean hermesActivo = false;
    private String ultimoEvento = "";

    // Propagación en el grafo: quién pregona y a quién le llega.
    private Jugador ultimoOrigenPropagacion;
    private List<Jugador> ultimosReceptores = new ArrayList<>();

    // Bancos de rumores (se barajan y se agotan antes de repetir)
    private static final String[] BANCO_FALSOS = {
            "Dicen que Teodoro quiere cerrar el gimnasio y vender sus terrenos.",
            "Los tesoros del templo de Atenea han desaparecido: culpan al arconte actual.",
            "Cancelarán las Dionisias para pagar las deudas de la ciudad.",
            "Un oráculo de Delfos habría llamado impía a la candidata Dafne.",
            "El trigo del Ponto no llegará este año: habrá hambruna antes del invierno.",
            "Un sofista pagó a los jueces para ganar el próximo juicio.",
            "Las votaciones se harán de noche, solo para los amigos de Lisandro.",
            "Aristea sería hija de un espía enemigo, por eso conoce tantos secretos."
    };
    private static final String[] BANCO_VERDADEROS = {
            "Se inauguró una fuente nueva junto al ágora.",
            "El debate entre candidatos será al amanecer en la Pnyx.",
            "Los escribas guardan en el Metroon la lista de ciudadanos con derecho a voto.",
            "Aristea presentó su plan para asegurar el trigo y reparar el puerto.",
            "Las Dionisias se celebrarán en la fecha de siempre.",
            "El médico del templo atenderá gratis esta semana en la plaza.",
            "Se reparó la muralla norte antes de las lluvias.",
            "Dafne hizo grabar en una estela el gasto de su campaña."
    };
    private static final String[] AUTORES = {
            "un mercader del puerto", "una vendedora de higos", "un viajero de Corinto",
            "un marinero recién llegado", "una tejedora del barrio norte", "un esclavo del pedagogo",
            "un alfarero del ágora", "una sacerdotisa del templo", "un soldado de la muralla"
    };
    private final Deque<String> colaFalsos = new ArrayDeque<>();
    private final Deque<String> colaVerdaderos = new ArrayDeque<>();

    public GameEngine(List<Jugador> jugadores) {
        this(jugadores, new Random());
    }

    /** Con semilla fija se pueden reproducir partidas (útil para pruebas). */
    public GameEngine(List<Jugador> jugadores, Random rnd) {
        this.rnd = rnd;
        this.jugadores.addAll(jugadores);
        construirGrafoSocial();
    }

    /** Grafo de relaciones sociales entre los habitantes (jugadores y bots). */
    private void construirGrafoSocial() {
        for (Jugador j : jugadores) grafo.agregarPersona(j);
        int n = jugadores.size();
        for (int i = 0; i < n; i++) {
            grafo.agregarAmistad(jugadores.get(i), jugadores.get((i + 1) % n));
        }
        if (n >= 4) {
            grafo.agregarAmistad(jugadores.get(0), jugadores.get(2));
        }
    }

    public DecisionTree getArbolDecisiones() { return arbolDecisiones; }
    public GrafoSocial getGrafo() { return grafo; }
    public CiudadEstado getCiudad() { return ciudad; }
    public List<Jugador> getJugadores() { return jugadores; }
    public List<String> getBitacora() { return bitacora; }
    public Publicacion getPublicacionActual() { return publicacionActual; }
    public List<DecisionTree.NodoDecision> getUltimoCamino() { return ultimoCamino; }
    public Jugador getJugadorActual() { return jugadores.get(jugadorActual); }
    public int getIndiceJugadorActual() { return jugadorActual; }
    public int getPublicacionesResueltas() { return publicacionesResueltas; }
    public boolean juegoTerminado() { return publicacionesResueltas >= PUBLICACIONES_PARA_TERMINAR; }
    public Jugador getUltimoOrigenPropagacion() { return ultimoOrigenPropagacion; }
    public List<Jugador> getUltimosReceptores() { return ultimosReceptores; }
    public boolean isEsperandoDecisionPostVerificacion() { return esperandoDecisionPostVerificacion; }
    public String getUltimoEvento() { return ultimoEvento; }

    public boolean puedeDeclaracionPublica() {
        return (turno - turnoUltimaDeclaracion) >= COOLDOWN_DECLARACION_TURNOS;
    }

    private void log(String s) { bitacora.add(s); }

    // ---------------------------------------------------------------- rumores y presagios

    private String siguienteTexto(Deque<String> cola, String[] banco) {
        if (cola.isEmpty()) {
            List<String> mezcla = new ArrayList<>(Arrays.asList(banco));
            Collections.shuffle(mezcla, rnd);
            cola.addAll(mezcla);
        }
        return cola.poll();
    }

    public Publicacion generarPublicacion() {
        boolean esFalsa = rnd.nextDouble() < PROB_PUBLICACION_FALSA;
        String texto = esFalsa
                ? siguienteTexto(colaFalsos, BANCO_FALSOS)
                : siguienteTexto(colaVerdaderos, BANCO_VERDADEROS);
        int impacto = IMPACTO_MIN + rnd.nextInt(IMPACTO_MAX - IMPACTO_MIN + 1);

        Publicacion pub = new Publicacion(texto, AUTORES[rnd.nextInt(AUTORES.length)], !esFalsa, impacto);
        publicacionActual = pub;
        esperandoDecisionPostVerificacion = false;
        percepcionVerdadera = null;
        turno++;
        log("Turno " + turno + ": nuevo rumor " + pub);
        return pub;
    }

    /**
     * Presagio de los dioses al comenzar cada turno. Devuelve el texto para mostrarlo a todos.
     * Ossa, Apate y Hermes alteran SOLO el turno que empieza.
     */
    public String dispararEventoAleatorio() {
        String[] eventos = { "festival", "eris", "tablilla", "ossa", "apate", "hermes", "nada", "nada", "nada" };
        String e = eventos[rnd.nextInt(eventos.length)];
        String texto;
        switch (e) {
            case "festival":
                ciudad.ajustarConvivencia(5);
                ciudad.ajustarBienestar(3);
                texto = "Las Dionisias llenan el ágora de música y banquetes. Suben el orden (Eunomía) y la dicha (Eudaimonía).";
                break;
            case "eris":
                ciudad.ajustarConflictos(6);
                ciudad.ajustarConvivencia(-4);
                texto = "Eris arroja su manzana de la discordia en el mercado. Suben los conflictos y baja el orden.";
                break;
            case "tablilla":
                ciudad.ajustarDesinformacion(-3);
                texto = "Un polites fija una prueba en el leukoma, la tablilla de la verdad. Baja el engaño (Apate).";
                break;
            case "ossa":
                ossaActiva = true;
                texto = "Ossa, la voz del rumor, corre por la ciudad: lo que se pregone este turno llegará un paso más lejos.";
                break;
            case "apate":
                apateActiva = true;
                texto = "Apate susurra en los oídos del pueblo: indagar este turno es mucho más difícil (salvo en el Metroon).";
                break;
            case "hermes":
                hermesActivo = true;
                texto = "Hermes, el mensajero, cruza el ágora: quien indague este turno hallará la verdad.";
                break;
            default:
                texto = "Una jornada sin presagios en Helíade.";
                break;
        }
        ultimoEvento = texto;
        log("Presagio: " + texto);
        return texto;
    }

    // ---------------------------------------------------------------- decisiones de bots

    public String decidirAccionBot() {
        Jugador j = getJugadorActual();
        int r = rnd.nextInt(100);
        if (j.esLogografo()) {
            return INDAGAR;
        }
        if (j.esSofista()) {
            return r < 65 ? PREGONAR : INDAGAR;
        }
        if (j.esArconte()) {
            return (puedeDeclaracionPublica() && r < 50) ? DISCURSO : INDAGAR;
        }
        if (r < 45) return INDAGAR;
        if (r < 65) return DENUNCIAR;
        if (r < 85) return CALLAR;
        return PREGONAR;
    }

    public String decidirAccionBotTrasVerificar() {
        return Boolean.TRUE.equals(percepcionVerdadera) ? PREGONAR : DENUNCIAR;
    }

    // ---------------------------------------------------------------- validación

    /**
     * Revisa si el jugador del turno puede hacer una acción. Devuelve null si puede, o el motivo (para
     * mostrárselo) si no. Un rechazo NO gasta el turno: el jugador puede elegir otra cosa.
     */
    public String validarAccion(String accion) {
        if (DISCURSO.equals(accion)) {
            if (!getJugadorActual().esArconte()) return "Solo el Arconte puede dar un discurso en la Pnyx.";
            if (!puedeDeclaracionPublica()) {
                return "Tus hetairoi, tu círculo de campaña, te aconsejan no hablar hoy: el pueblo aún recuerda "
                        + "tu último discurso. Elige otra acción.";
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- acciones sin indagar

    public String resolverAccionDirecta(String accionBase) {
        if (publicacionActual == null) return "No hay rumor activo.";
        if (INDAGAR.equals(accionBase)) return "Usa iniciarVerificacion() para indagar.";
        Jugador j = getJugadorActual();
        StringBuilder resultado = new StringBuilder();

        ultimoOrigenPropagacion = null;
        ultimosReceptores = new ArrayList<>();

        switch (accionBase) {
            case PREGONAR: {
                ultimoCamino = arbolDecisiones.buscarCamino(PREGONAR);
                boolean esImpulso = j.esSofista();
                int alcance = propagar(j, esImpulso);
                String verbo = esImpulso ? "Declamaste" : "Pregonaste";

                if (publicacionActual.isVerdadera()) {
                    ciudad.ajustarInformacionVerificada(2 + alcance);
                    ciudad.ajustarConfianza((int) Math.round((2 + alcance) * j.multiplicadorConfianza()));
                    j.sumarPuntos(5 + alcance * 2);
                    j.sumarReputacion(4);
                    resultado.append(verbo).append(" algo que resultó ser cierto y llegó a ")
                            .append(alcance).append(" personas de tu círculo.");
                } else {
                    int penalizacionDesinfo = 8 + alcance * (esImpulso ? 4 : 2);
                    ciudad.ajustarDesinformacion(penalizacionDesinfo);
                    ciudad.ajustarConfianza(-(5 + alcance));
                    j.sumarPuntos(-(8 + alcance * 2));
                    j.sumarReputacion(-10);
                    j.registrarCompartidaFalsa();
                    resultado.append(verbo).append(" sin comprobar y era falso: llegó a ")
                            .append(alcance).append(" personas de tu círculo y Apate se alegra.");
                }
                break;
            }
            case CALLAR: {
                ultimoCamino = arbolDecisiones.buscarCamino(CALLAR);
                ciudad.ajustarBienestar(-1);
                resultado.append("Callaste ante el rumor. No pasa nada grave, pero tampoco ayudas a la ciudad.");
                break;
            }
            case DISCURSO: {
                String motivo = validarAccion(DISCURSO);
                if (motivo != null) return motivo; // no gasta el turno
                ultimoCamino = arbolDecisiones.buscarCamino(DISCURSO);
                ciudad.ajustarConfianza(BONUS_DECLARACION_CONFIANZA);
                ciudad.ajustarConvivencia(BONUS_DECLARACION_CONVIVENCIA);
                j.sumarPuntos(PUNTOS_DECLARACION);
                j.sumarReputacion(2);
                turnoUltimaDeclaracion = turno;
                resultado.append("Como candidato, subes a la Pnyx y pronuncias un discurso de campaña: suben la confianza (Pistis) y el orden (Eunomía) en Helíade.");
                break;
            }
            case DENUNCIAR: {
                ultimoCamino = arbolDecisiones.buscarCamino(DENUNCIAR);
                if (!publicacionActual.isVerdadera()) {
                    ciudad.ajustarDesinformacion(-6);
                    ciudad.ajustarConvivencia(2);
                    j.sumarPuntos(9);
                    j.sumarReputacion(4);
                    resultado.append("Denunciaste un rumor falso ante la tablilla: los escribas lo desmienten y baja el engaño (Apate).");
                } else {
                    ciudad.ajustarConfianza(-3);
                    j.sumarPuntos(-5);
                    j.sumarReputacion(-3);
                    resultado.append("Denunciaste algo que en realidad era cierto: acusación injustificada y el pueblo pierde confianza.");
                }
                break;
            }
            default:
                return "Acción desconocida.";
        }

        anexarConsecuenciaDelArbol(resultado);
        cerrarTurno(accionBase, resultado.toString());
        return resultado.toString();
    }

    /** Calcula a cuántos llega un pregón (con Ossa, un paso más lejos) y lo registra. */
    private int propagar(Jugador j, boolean esImpulso) {
        int profundidad = (esImpulso ? PROFUNDIDAD_IMPULSO : PROFUNDIDAD_COMPARTIR_NORMAL) + (ossaActiva ? 1 : 0);
        List<Jugador> receptores = grafo.alcanceBFS(j, profundidad);
        ultimoOrigenPropagacion = j;
        ultimosReceptores = receptores;
        return receptores.size();
    }

    // ---------------------------------------------------------------- indagar

    public String iniciarVerificacion() {
        if (publicacionActual == null) return "No hay rumor activo.";
        Jugador j = getJugadorActual();
        boolean esLogografo = j.esLogografo();

        double prob = PROB_BASE_VERIFICACION;
        if (hermesActivo) prob = 1.0;
        else if (apateActiva) prob = PROB_VERIFICACION_APATE;
        boolean descubreVerdad = esLogografo || rnd.nextDouble() < prob;
        publicacionActual.setVerificada(true);

        boolean percibida = descubreVerdad ? publicacionActual.isVerdadera() : !publicacionActual.isVerdadera();
        percepcionVerdadera = percibida;
        esperandoDecisionPostVerificacion = true;
        ultimoCamino = arbolDecisiones.buscarCamino(INDAGAR, percibida ? "Si" : "No");

        if (percibida == publicacionActual.isVerdadera()) {
            j.registrarVerificacionCorrecta();
            j.sumarReputacion(2);
        }

        String verbo = esLogografo ? "Consultaste el Metroon" : "Indagaste con testigos";
        String mensaje = verbo + " y todo indica que el rumor es " + (percibida ? "VERDADERO" : "FALSO")
                + ".\n\n¿Qué quieres hacer ahora que sabes esto? (tu decisión, sea sensata o no, es tuya)";
        log(j + ": " + verbo + " -> parece " + (percibida ? "VERDADERO" : "FALSO") + " (esperando su decisión)");
        return mensaje;
    }

    // ---------------------------------------------------------------- acciones tras indagar

    public String finalizarTrasVerificar(String accionFinal) {
        if (!esperandoDecisionPostVerificacion || publicacionActual == null) {
            return "No hay una indagación pendiente.";
        }
        Jugador j = getJugadorActual();
        boolean real = publicacionActual.isVerdadera();
        boolean percibida = Boolean.TRUE.equals(percepcionVerdadera);
        esperandoDecisionPostVerificacion = false;

        ultimoCamino = arbolDecisiones.buscarCamino(INDAGAR, percibida ? "Si" : "No", accionFinal);
        StringBuilder resultado = new StringBuilder();
        ultimoOrigenPropagacion = null;
        ultimosReceptores = new ArrayList<>();

        switch (accionFinal) {
            case PREGONAR: {
                boolean esImpulso = j.esSofista();
                int alcance = propagar(j, esImpulso);

                if (real) {
                    ciudad.ajustarInformacionVerificada(4 + alcance);
                    ciudad.ajustarConfianza((int) Math.round((4 + alcance) * j.multiplicadorConfianza()));
                    j.sumarPuntos(8 + alcance * 2);
                    j.sumarReputacion(6);
                    resultado.append("Decidiste pregonarlo: era cierto, así que ayudas a que llegue información confiable a ")
                            .append(alcance).append(" personas.");
                } else {
                    int penal = 6 + alcance * (esImpulso ? 4 : 2);
                    ciudad.ajustarDesinformacion(penal);
                    ciudad.ajustarConfianza(-(4 + alcance));
                    j.sumarPuntos(-(6 + alcance * 2));
                    j.sumarReputacion(-8);
                    j.registrarCompartidaFalsa();
                    resultado.append(percibida
                            ? "Tu indagación no fue suficiente: creíste que era cierto, lo pregonaste y en realidad era falso. Sube el engaño (Apate)."
                            : "Sabías que era falso y lo pregonaste de todas formas: sube el engaño (Apate).");
                }
                break;
            }
            case DENUNCIAR: {
                if (!real) {
                    ciudad.ajustarDesinformacion(-8);
                    j.sumarPuntos(11);
                    j.sumarReputacion(5);
                    resultado.append("Denunciaste el rumor falso: baja el engaño (Apate) en Helíade.");
                } else {
                    ciudad.ajustarConfianza(-3);
                    j.sumarPuntos(-4);
                    j.sumarReputacion(-3);
                    resultado.append("Lo denunciaste, pero en realidad era cierto: acusación injustificada.");
                }
                break;
            }
            case CALLAR: {
                ciudad.ajustarBienestar(-1);
                resultado.append("Después de indagar, decidiste no hacer nada con el rumor.");
                break;
            }
            default:
                esperandoDecisionPostVerificacion = true; // no se consumió el turno
                return "Acción final desconocida.";
        }

        anexarConsecuenciaDelArbol(resultado);
        cerrarTurno(accionFinal, resultado.toString());
        return resultado.toString();
    }

    // ---------------------------------------------------------------- utilidades

    private void anexarConsecuenciaDelArbol(StringBuilder resultado) {
        if (!ultimoCamino.isEmpty()) {
            DecisionTree.NodoDecision hoja = ultimoCamino.get(ultimoCamino.size() - 1);
            if (hoja.consecuencia != null) {
                resultado.append("\n").append(hoja.consecuencia);
            }
        }
    }

    private void cerrarTurno(String accionAplicada, String textoResultado) {
        publicacionActual.setEstado(accionAplicada.toUpperCase());
        publicacionesResueltas++;
        jugadorActual = (jugadorActual + 1) % jugadores.size();
        // los presagios solo duran el turno en que se dispararon
        ossaActiva = false;
        apateActiva = false;
        hermesActivo = false;
        log(textoResultado.replace("\n", " | "));
    }

    public String caminoComoTexto() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ultimoCamino.size(); i++) {
            sb.append(ultimoCamino.get(i).etiqueta);
            if (i < ultimoCamino.size() - 1) sb.append(" -> ");
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------- finales

    /** Resultado de la elección del Arconte. */
    public record Eleccion(String titulo, String texto) { }

    /** Destino personal de un jugador al terminar la campaña. */
    public record Destino(Jugador jugador, String clave, String titulo, String texto) { }

    /** Clave del destino de un jugador según su reputación y los rumores falsos que pregonó. */
    public String claveDestino(Jugador j) {
        int falsas = j.getCompartidasFalsas();
        int rep = j.getReputacion();
        boolean ciudadEnganada = ciudad.getDesinformacion() >= 60;
        if (falsas >= 3 || (falsas >= 2 && rep <= 30 && ciudadEnganada)) return DESTINO_CICUTA;
        if (falsas >= 2 || rep <= 30) return DESTINO_OSTRACISMO;
        // Hermes solo bendice a quien dijo la verdad y además dejó una polis que confía en la palabra
        if (falsas == 0 && rep >= 70 && j.getVerificacionesCorrectas() >= 2 && ciudad.puntajeGeneral() >= UMBRAL_HERMES_POLIS) return DESTINO_HERMES;
        return DESTINO_ANONIMATO;
    }

    public List<Destino> destinosFinales() {
        List<Destino> lista = new ArrayList<>();
        for (Jugador j : jugadores) {
            String clave = claveDestino(j);
            lista.add(new Destino(j, clave, tituloDestino(clave), textoDestino(j, clave)));
        }
        return lista;
    }

    private static String tituloDestino(String clave) {
        switch (clave) {
            case DESTINO_CICUTA: return "La copa de cicuta";
            case DESTINO_OSTRACISMO: return "Diez años de ostracismo";
            case DESTINO_HERMES: return "Bendecido por Hermes";
            default: return "El olvido tranquilo";
        }
    }

    private static String textoDestino(Jugador j, String clave) {
        String rol = j.getRol();
        switch (clave) {
            case DESTINO_CICUTA: {
                String extra = Jugador.SOFISTA.equals(rol)
                        ? "Tus discursos brillaron, pero Némesis los pesó contra la verdad."
                        : Jugador.ARCONTE.equals(rol)
                        ? "Quien aspiraba a gobernar la ciudad terminó envenenándola con sus palabras."
                        : Jugador.LOGOGRAFO.equals(rol)
                        ? "Tus crónicas debían ser testimonio, y fueron arma."
                        : "Un ciudadano común repitió demasiadas mentiras.";
                return "La Heliea, el jurado de ciudadanos, te declara culpable de envenenar la palabra pública. "
                        + "Al atardecer te dan la copa de cicuta, como a Sócrates. " + extra;
            }
            case DESTINO_OSTRACISMO:
                return "Los ciudadanos escriben tu nombre en fragmentos de cerámica y la mayoría decide: "
                        + "diez años de exilio lejos de Helíade. Volverás cuando la ciudad haya olvidado tus rumores.";
            case DESTINO_HERMES: {
                String extra = Jugador.LOGOGRAFO.equals(rol)
                        ? "Tus crónicas se copian por toda Grecia."
                        : Jugador.SOFISTA.equals(rol)
                        ? "Peitho sonríe: tu elocuencia sirvió a la verdad."
                        : Jugador.ARCONTE.equals(rol)
                        ? "Tu palabra pesa tanto como tus actos."
                        : "Tu voz de ciudadano común fue la más confiable del ágora.";
                return "Hermes, patrón de heraldos y mensajeros, bendice tu palabra. La ciudad levanta una herma "
                        + "con tu nombre junto al ágora. " + extra;
            }
            default:
                return "Ni cicuta ni gloria: Helíade te olvida pronto. Quizá la próxima vez tu palabra pese más.";
        }
    }

    /** La elección: gana el jugador Arconte si la polis salió bien y no cayó en desgracia; si no, un rival. */
    public Eleccion realizarEleccion() {
        int puntaje = ciudad.puntajeGeneral();

        String estadoPolis;
        String rival;
        if (puntaje >= UMBRAL_EDAD_DE_ORO) {
            estadoPolis = "Aletheia brilla en el ágora: Helíade confía en su palabra.";
            rival = "Aristea";
        } else if (puntaje >= UMBRAL_EQUILIBRIO) {
            estadoPolis = "Helíade queda dividida pero en pie: se habló mucho y se comprobó lo justo.";
            rival = "Dafne";
        } else if (puntaje >= UMBRAL_DISCORDIA) {
            estadoPolis = "Eris pasea por las calles de Helíade: los rumores pesaron más que las pruebas.";
            rival = "Teodoro";
        } else {
            estadoPolis = "Apate reina en Helíade. Cansada del caos, la polis entrega el poder a un solo hombre.";
            rival = "Lisandro";
        }

        Jugador arconte = null;
        for (Jugador j : jugadores) if (j.esArconte()) arconte = j;
        boolean arconteCae = arconte != null && (claveDestino(arconte).equals(DESTINO_CICUTA)
                || claveDestino(arconte).equals(DESTINO_OSTRACISMO));

        String titulo;
        String texto;
        if (arconte != null && puntaje >= UMBRAL_EQUILIBRIO && !arconteCae) {
            titulo = "El pueblo elige a " + arconte.getNombre() + " como Arconte";
            texto = estadoPolis;
        } else if (arconteCae) {
            titulo = "Gana " + rival + ": " + arconte.getNombre() + " cayó en desgracia";
            texto = estadoPolis + " El candidato fue arrastrado por sus propias palabras.";
        } else {
            titulo = "Gana " + rival + " la elección de Arconte";
            texto = estadoPolis;
        }
        return new Eleccion(titulo, texto + "\nPuntaje general de la polis: " + puntaje);
    }
}
