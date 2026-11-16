package model;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Motor de juego de Alcalde Digital. Coordina:
 *  - El Arbol de decisiones (consecuencias de cada accion).
 *  - El Grafo social (propagacion de publicaciones entre jugadores/bots).
 *  - El estado de la ciudad y de los jugadores.
 *  - Los eventos aleatorios y el banco de publicaciones.
 */
public class GameEngine {


    /** Cuantas publicaciones salen en la partida */
    public static final int PUBLICACIONES_PARA_TERMINAR = 8;

    /** Segundos que tiene el jugador por turno. */
    public static final int SEGUNDOS_POR_TURNO = 30;

    /** Probabilidad de que una publicacion sea falsa */
    private static final double PROB_PUBLICACION_FALSA = 0.55;

    /** Rango de puntos que suma/resta cada publicacion */
    private static final int IMPACTO_MIN = 20;
    private static final int IMPACTO_MAX = 90;

    /** Probabilidad de tener razón al verificar para NO periodista
     *  El Periodista siempre acierta */
    private static final double PROB_BASE_VERIFICACION = 0.55;

    /** Distancia de amistad a la que llega una publicacion compartida */
    private static final int PROFUNDIDAD_COMPARTIR_NORMAL = 1;

    /** Distancia de amistad a la que llega una publicacion compartida influencer con Impulsar */
    private static final int PROFUNDIDAD_IMPULSO = 2;

    /** Cada cuantos turnos el Alcalde puede hacer declaracion publica */
    private static final int COOLDOWN_DECLARACION_TURNOS = 3;
    private static final int BONUS_DECLARACION_CONFIANZA = 8;
    private static final int BONUS_DECLARACION_CONVIVENCIA = 5;
    private static final int PUNTOS_DECLARACION = 6;

    
    

    private final DecisionTree arbolDecisiones = new DecisionTree();
    private final GrafoSocial grafo = new GrafoSocial();
    private final CiudadEstado ciudad = new CiudadEstado();
    private final List<Jugador> jugadores = new ArrayList<>();
    private final List<String> bitacora = new ArrayList<>();
    private final Random rnd = new Random();

    private int turno = 0;
    private int jugadorActual = 0;
    private int publicacionesResueltas = 0;
    private int turnoUltimaDeclaracion = -1000;
    private Publicacion publicacionActual;
    private List<DecisionTree.NodoDecision> ultimoCamino = new ArrayList<>();

    private boolean esperandoDecisionPostVerificacion = false;
    private Boolean percepcionVerdadera; // lo que el jugador CREE tras investigar (puede estar equivocado)

    // Propagacion en el grafo: quien comparte y a quien le llega.
    private Jugador ultimoOrigenPropagacion;
    private List<Jugador> ultimosReceptores = new ArrayList<>();

    // Publicaciones
    private final String[] BANCO_FALSAS = {
            "El candidato Juan quiere cerrar el colegio.",
            "Mañana cerrarán todos los parques.",
            "El alcalde actual está robando dinero.",
            "Civitas venderá los datos de los usuarios mañana.",
            "Van a cancelar las elecciones por 'problemas tecnicos'."
    };
    private final String[] BANCO_VERDADERAS = {
            "La alcaldia anuncia nuevas luminarias led en el barrio.",
            "El debate entre candidatos sera transmitido el viernes.",
            "Se abrio una jornada gratuita de vacunacion en la plaza.",
            "El censo electoral ya esta disponible para consulta.",
            "La candidata Marta presento su plan de movilidad."
    };

    public GameEngine(List<Jugador> jugadores) {
        this.jugadores.addAll(jugadores);
        construirGrafoSocial();
    }

    /**
     * Grafo de relaciones sociales entre jugadores y "bots"
     */
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
    public int getPublicacionesResueltas() { return publicacionesResueltas; }
    public boolean juegoTerminado() { return publicacionesResueltas >= PUBLICACIONES_PARA_TERMINAR; }
    public Jugador getUltimoOrigenPropagacion() { return ultimoOrigenPropagacion; }
    public List<Jugador> getUltimosReceptores() { return ultimosReceptores; }
    public boolean isEsperandoDecisionPostVerificacion() { return esperandoDecisionPostVerificacion; }

    public boolean puedeDeclaracionPublica() {
        return (turno - turnoUltimaDeclaracion) >= COOLDOWN_DECLARACION_TURNOS;
    }

    private void log(String s) { bitacora.add(s); }

    //  publicaciones y eventos aleatorios

    public Publicacion generarPublicacion() {
        boolean esFalsa = rnd.nextDouble() < PROB_PUBLICACION_FALSA;
        String texto = esFalsa
                ? BANCO_FALSAS[rnd.nextInt(BANCO_FALSAS.length)]
                : BANCO_VERDADERAS[rnd.nextInt(BANCO_VERDADERAS.length)];
        int impacto = IMPACTO_MIN + rnd.nextInt(IMPACTO_MAX - IMPACTO_MIN + 1);

        Publicacion pub = new Publicacion(texto, "ciudadano_" + (rnd.nextInt(900) + 100), !esFalsa, impacto);
        publicacionActual = pub;
        esperandoDecisionPostVerificacion = false;
        percepcionVerdadera = null;
        turno++;
        log("Turno " + turno + ": nueva publicacion " + pub);
        return pub;
    }

   
    public String dispararEventoAleatorio() {
        String[] eventos = { "campaña_convivencia", "discusion_ciudadanos", "reporte_ciudadano", "nada" };
        String e = eventos[rnd.nextInt(eventos.length)];
        switch (e) {
            case "campaña_convivencia":
                ciudad.ajustarConvivencia(5);
                ciudad.ajustarBienestar(3);
                log("Evento: una campaña de convivencia digital se vuelve tendencia. +convivencia, +bienestar.");
                break;
            case "discusion_ciudadanos":
                ciudad.ajustarConflictos(6);
                ciudad.ajustarConvivencia(-4);
                log("Evento: estalla una discusion entre ciudadanos en Civitas. +conflictos, -convivencia.");
                break;
            case "reporte_ciudadano":
                ciudad.ajustarDesinformacion(-3);
                log("Evento: un ciudadano reporta contenido sospechoso por su cuenta. -desinformacion.");
                break;
            default:
                break;
        }
        return e;
    }

    // decisiones de bots

    public String decidirAccionBot() {
        Jugador j = getJugadorActual();
        int r = rnd.nextInt(100);
        if (j.esPeriodista()) {
            return "Verificar"; 
        }
        if (j.esInfluencer()) {
            return r < 65 ? "Compartir" : "Verificar";
        }
        if (j.esAlcalde()) {
            return (puedeDeclaracionPublica() && r < 50) ? "Ignorar" : "Verificar";
        }
        
        if (r < 45) return "Verificar";
        if (r < 65) return "Reportar";
        if (r < 85) return "Ignorar";
        return "Compartir";
    }

    
    public String decidirAccionBotTrasVerificar() {
        return Boolean.TRUE.equals(percepcionVerdadera) ? "Compartir" : "Reportar";
    }

    
    
    // Resultado acciones sin pasar verificar

    public String resolverAccionDirecta(String accionBase) {
        if (publicacionActual == null) return "No hay publicacion activa.";
        if ("Verificar".equals(accionBase)) return "Usa iniciarVerificacion() para verificar.";
        Jugador j = getJugadorActual();
        StringBuilder resultado = new StringBuilder();

        ultimoOrigenPropagacion = null;
        ultimosReceptores = new ArrayList<>();

        switch (accionBase) {
            case "Compartir": {
                ultimoCamino = arbolDecisiones.buscarCamino("Compartir");
                boolean esImpulso = j.esInfluencer();
                int profundidad = esImpulso ? PROFUNDIDAD_IMPULSO : PROFUNDIDAD_COMPARTIR_NORMAL;
                List<Jugador> receptores = grafo.alcanceBFS(j, profundidad);
                ultimoOrigenPropagacion = j;
                ultimosReceptores = receptores;
                int alcance = receptores.size();
                String verbo = esImpulso ? "Impulsaste" : "Compartiste";

                if (publicacionActual.isVerdadera()) {
                    ciudad.ajustarInformacionVerificada(2 + alcance);
                    ciudad.ajustarConfianza((int) Math.round((2 + alcance) * j.multiplicadorConfianza()));
                    j.sumarPuntos(5 + alcance * 2);
                    resultado.append(verbo).append(" algo que resulto ser cierto y le llego a ")
                            .append(alcance).append(" personas de tu red.");
                } else {
                    int penalizacionDesinfo = 8 + alcance * (esImpulso ? 4 : 2);
                    ciudad.ajustarDesinformacion(penalizacionDesinfo);
                    ciudad.ajustarConfianza(-(5 + alcance));
                    j.sumarPuntos(-(8 + alcance * 2));
                    j.sumarReputacion(-10);
                    j.registrarCompartidaFalsa();
                    resultado.append(verbo).append(" sin verificar y era falso, le llego a ")
                            .append(alcance).append(" personas de tu red y sube la desinformacion.");
                }
                break;
            }
            case "Ignorar": {
                ultimoCamino = arbolDecisiones.buscarCamino("Ignorar");
                boolean declaracion = j.esAlcalde() && puedeDeclaracionPublica();
                if (declaracion) {
                    ciudad.ajustarConfianza(BONUS_DECLARACION_CONFIANZA);
                    ciudad.ajustarConvivencia(BONUS_DECLARACION_CONVIVENCIA);
                    j.sumarPuntos(PUNTOS_DECLARACION);
                    turnoUltimaDeclaracion = turno;
                    resultado.append("Como candidato, aprovechas el momento para dar una declaracion publica de campaña: sube la confianza y la convivencia en Ciudad Nova.");
                } else if (j.esAlcalde()) {
                    ciudad.ajustarBienestar(-1);
                    resultado.append("Tu equipo de campaña te pide esperar antes de otra declaracion publica. Por ahora, ignoras la publicacion.");
                } else {
                    ciudad.ajustarBienestar(-1);
                    resultado.append("Ignoraste la publicacion. No pasa nada grave, pero tampoco ayudas a la ciudad.");
                }
                break;
            }
            case "Reportar": {
                ultimoCamino = arbolDecisiones.buscarCamino("Reportar");
                if (!publicacionActual.isVerdadera()) {
                    ciudad.ajustarDesinformacion(-6);
                    ciudad.ajustarConvivencia(2);
                    j.sumarPuntos(9);
                    j.sumarReputacion(4);
                    resultado.append("Reportaste una publicacion falsa: Civitas la modera y baja la desinformacion.");
                } else {
                    ciudad.ajustarConfianza(-3);
                    j.sumarPuntos(-5);
                    resultado.append("Reportaste algo que en realidad era cierto: reporte injustificado.");
                }
                break;
            }
            default:
                return "Accion desconocida.";
        }

        anexarConsecuenciaDelArbol(resultado);
        cerrarTurno(accionBase, resultado.toString());
        return resultado.toString();
    }

    // Resultado verificar

   
    public String iniciarVerificacion() {
        if (publicacionActual == null) return "No hay publicacion activa.";
        Jugador j = getJugadorActual();
        boolean esPeriodista = j.esPeriodista();
        boolean descubreVerdad = esPeriodista || rnd.nextDouble() < PROB_BASE_VERIFICACION;
        publicacionActual.setVerificada(true);

        boolean percibida = descubreVerdad ? publicacionActual.isVerdadera() : !publicacionActual.isVerdadera();
        percepcionVerdadera = percibida;
        esperandoDecisionPostVerificacion = true;
        ultimoCamino = arbolDecisiones.buscarCamino("Verificar", percibida ? "Si" : "No");

        if (percibida == publicacionActual.isVerdadera()) {
            j.registrarVerificacionCorrecta();
        }

        String verbo = esPeriodista ? "Investigaste a fondo" : "Verificaste";
        String mensaje = verbo + " y todo indica que la publicacion es " + (percibida ? "VERDADERA" : "FALSA")
                + ".\n\n¿Que quieres hacer ahora que sabes esto? (tu decision, sea sensata o no, es tuya)";
        log((j + ": " + verbo + " -> parece " + (percibida ? "VERDADERA" : "FALSA") + " (esperando su decision)"));
        return mensaje;
    }

    // Resultado acciones luego de verificar
    
    public String finalizarTrasVerificar(String accionFinal) {
        if (!esperandoDecisionPostVerificacion || publicacionActual == null) {
            return "No hay una verificacion pendiente.";
        }
        Jugador j = getJugadorActual();
        boolean real = publicacionActual.isVerdadera();
        boolean percibida = Boolean.TRUE.equals(percepcionVerdadera);
        esperandoDecisionPostVerificacion = false;

        ultimoCamino = arbolDecisiones.buscarCamino("Verificar", percibida ? "Si" : "No", accionFinal);
        StringBuilder resultado = new StringBuilder();
        ultimoOrigenPropagacion = null;
        ultimosReceptores = new ArrayList<>();

        switch (accionFinal) {
            case "Compartir": {
                boolean esImpulso = j.esInfluencer();
                int profundidad = esImpulso ? PROFUNDIDAD_IMPULSO : PROFUNDIDAD_COMPARTIR_NORMAL;
                List<Jugador> receptores = grafo.alcanceBFS(j, profundidad);
                ultimoOrigenPropagacion = j;
                ultimosReceptores = receptores;
                int alcance = receptores.size();

                if (real) {
                    ciudad.ajustarInformacionVerificada(4 + alcance);
                    ciudad.ajustarConfianza((int) Math.round((4 + alcance) * j.multiplicadorConfianza()));
                    j.sumarPuntos(8 + alcance * 2);
                    resultado.append("Decidiste compartirla: era cierta, asi que ayudas a que llegue informacion confiable a ")
                            .append(alcance).append(" personas.");
                } else {
                    int penal = 6 + alcance * (esImpulso ? 4 : 2);
                    ciudad.ajustarDesinformacion(penal);
                    ciudad.ajustarConfianza(-(4 + alcance));
                    j.sumarPuntos(-(6 + alcance * 2));
                    j.sumarReputacion(-8);
                    j.registrarCompartidaFalsa();
                    resultado.append(percibida
                            ? "Tu investigacion no fue suficiente: creiste que era cierta, la compartiste y en realidad era falsa. Sube la desinformacion."
                            : "Sabias que era falsa y la compartiste de todas formas: sube la desinformacion.");
                }
                break;
            }
            case "Reportar": {
                if (!real) {
                    ciudad.ajustarDesinformacion(-8);
                    j.sumarPuntos(11);
                    j.sumarReputacion(5);
                    resultado.append("Reportaste la publicacion falsa: baja la desinformacion en Ciudad Nova.");
                } else {
                    ciudad.ajustarConfianza(-3);
                    j.sumarPuntos(-4);
                    resultado.append("La reportaste, pero en realidad era cierta: reporte injustificado.");
                }
                break;
            }
            case "Ignorar": {
                ciudad.ajustarBienestar(-1);
                resultado.append("Despues de investigar, decidiste no hacer nada con la publicacion.");
                break;
            }
            default:
                esperandoDecisionPostVerificacion = true; // no se consumio el turno
                return "Accion final desconocida.";
        }

        anexarConsecuenciaDelArbol(resultado);
        cerrarTurno(accionFinal, resultado.toString());
        return resultado.toString();
    }

    
    // otras cosas

    private void anexarConsecuenciaDelArbol(StringBuilder resultado) {
        if (!ultimoCamino.isEmpty()) {
            DecisionTree.NodoDecision hoja = ultimoCamino.get(ultimoCamino.size() - 1);
            if (hoja.consecuencia != null) {
                resultado.append("\nCamino en el arbol: ")
                        .append(caminoComoTexto())
                        .append("\n").append(hoja.consecuencia);
            }
        }
    }

    private void cerrarTurno(String accionAplicada, String textoResultado) {
        publicacionActual.setEstado(accionAplicada.toUpperCase());
        publicacionesResueltas++;
        jugadorActual = (jugadorActual + 1) % jugadores.size();
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

    // Final del juego

    public String realizarEleccion() {
        int puntaje = ciudad.puntajeGeneral();
        String[] candidatos = {"Marta Reyes", "Juan Osorio", "Diana Prieto", "Camilo Vera"};
        String ganador;
        String motivo;
        if (puntaje >= 180) {
            ganador = candidatos[0];
            motivo = "Ciudad Nova mantuvo alta confianza y baja desinformacion: gana la candidata mas asociada a la transparencia.";
        } else if (puntaje >= 120) {
            ganador = candidatos[2];
            motivo = "El resultado fue equilibrado, con una ciudadania medianamente informada.";
        } else if (puntaje >= 60) {
            ganador = candidatos[1];
            motivo = "La desinformacion y los conflictos pesaron mas de la cuenta durante la campaña.";
        } else {
            ganador = candidatos[3];
            motivo = "Ciudad Nova termino la campaña dividida y con mucha desinformacion circulando.";
        }
        return "Resultado de la eleccion: gana " + ganador + ".\n" + motivo
                + "\nPuntaje general de la ciudad: " + puntaje;
    }
}
