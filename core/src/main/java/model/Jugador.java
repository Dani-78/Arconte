package model;

import java.util.ArrayList;
import java.util.List;

/**
 * Un participante de Helíade (humano conectado por red o bot).
 *
 * Acciones de cada rol (la acción interna es la misma, cambia la etiqueta y el efecto):
 *  - Polites:   Pregonar, Indagar, Callar, Denunciar
 *  - Logógrafo: "Consultar el Metroon" en vez de Indagar (nunca se equivoca)
 *  - Sofista:   "Declamar" en vez de Pregonar (su voz llega más lejos)
 *  - Arconte:   además puede dar un "Discurso en la Pnyx" (no más seguido que cada dos turnos propios)
 */
public class Jugador {

    public static final String POLITES = "Polites";
    public static final String LOGOGRAFO = "Logógrafo";
    public static final String SOFISTA = "Sofista";
    public static final String ARCONTE = "Arconte";

    /** Orden fijo de los roles: también es el orden de turnos y el de los personajes del mapa. */
    public static final String[] ROLES = { POLITES, LOGOGRAFO, SOFISTA, ARCONTE };

    private final String nombre;
    private final String rol;
    private boolean bot;

    private int puntuacion;
    private int reputacion;
    private int verificacionesCorrectas;
    private int compartidasFalsas;

    public Jugador(String nombre, String rol) {
        this(nombre, rol, false);
    }

    public Jugador(String nombre, String rol, boolean bot) {
        this.nombre = nombre;
        this.rol = rol;
        this.bot = bot;
        this.puntuacion = 0;
        this.reputacion = 50;
    }

    public String getNombre() { return nombre; }
    public String getRol() { return rol; }
    public boolean isBot() { return bot; }
    /** Un jugador que se desconecta pasa a ser controlado por un bot. */
    public void setBot(boolean bot) { this.bot = bot; }
    public int getPuntuacion() { return puntuacion; }
    public int getReputacion() { return reputacion; }
    public int getVerificacionesCorrectas() { return verificacionesCorrectas; }
    public int getCompartidasFalsas() { return compartidasFalsas; }

    public void sumarPuntos(int p) { puntuacion = Math.max(0, puntuacion + p); }
    public void sumarReputacion(int r) { reputacion = Math.max(0, Math.min(100, reputacion + r)); }
    public void registrarVerificacionCorrecta() { verificacionesCorrectas++; }
    public void registrarCompartidaFalsa() { compartidasFalsas++; }

    /** El Arconte gana más confianza del pueblo con lo que dice. */
    public double multiplicadorConfianza() {
        return ARCONTE.equals(rol) ? 1.6 : 1.0;
    }

    public boolean esLogografo() { return LOGOGRAFO.equals(rol); }
    public boolean esSofista() { return SOFISTA.equals(rol); }
    public boolean esArconte() { return ARCONTE.equals(rol); }

    /** Texto del botón de una acción base para un rol. */
    public static String etiqueta(String rol, String accionBase) {
        switch (accionBase) {
            case GameEngine.PREGONAR:
                return SOFISTA.equals(rol) ? "Declamar" : "Pregonar";
            case GameEngine.INDAGAR:
                return LOGOGRAFO.equals(rol) ? "Consultar el Metroon" : "Indagar";
            case GameEngine.DISCURSO:
                return "Discurso en la Pnyx";
            default:
                return accionBase; // "Denunciar" es igual para todos los roles
        }
    }

    public String etiquetaAccion(String accionBase) {
        return etiqueta(rol, accionBase);
    }

    /** Descripción corta del rol para la sala de espera. */
    public static String descripcion(String rol) {
        switch (rol) {
            case POLITES:
                return "Ciudadano libre de Helíade. Su voz es pequeña, pero su voto decide la elección.";
            case LOGOGRAFO:
                return "Cronista y escritor de discursos. Al consultar el Metroon (el archivo de la ciudad) nunca se equivoca.";
            case SOFISTA:
                return "Maestro de la palabra. Al declamar, su voz llega más lejos: para bien o para mal.";
            case ARCONTE:
                return "Candidato a magistrado. Gana más confianza del pueblo y puede dar un discurso en la Pnyx (el anfiteatro), pero no dos turnos seguidos.";
            default:
                return "";
        }
    }

    /** Qué sería este rol en la versión moderna del juego (redes sociales y elecciones). */
    public static String equivalente(String rol) {
        switch (rol) {
            case POLITES: return "Ciudadano";
            case LOGOGRAFO: return "Periodista";
            case SOFISTA: return "Influencer";
            case ARCONTE: return "Candidato a alcalde";
            default: return "";
        }
    }

    /** Artículo que acompaña al rol (coincide con el personaje del arte: la Polites, el Logógrafo...). */
    public static String articulo(String rol) {
        return (POLITES.equals(rol) || SOFISTA.equals(rol)) ? "la" : "el";
    }

    /** Lo que debe hacer el rol, para completar la frase "Eres ... y ...". */
    public static String mision(String rol) {
        switch (rol) {
            case POLITES:
                return "debes ser la voz del pueblo: decide qué rumores creer, cuáles pregonar en el Ágora y cuáles denunciar ante la Heliea.";
            case LOGOGRAFO:
                return "debes investigar y contrastar: consulta los archivos del Metroon y cuenta solo lo que de verdad ocurre en la ciudad.";
            case SOFISTA:
                return "debes conquistar la atención de Helíade con tu elocuencia: tu voz llega más lejos que la de nadie, para bien o para mal.";
            case ARCONTE:
                return "debes ganarte la confianza del pueblo y la elección: habla desde la Pnyx y cuida que los rumores no destruyan tu candidatura.";
            default:
                return "";
        }
    }

    /** Las acciones del rol y dónde se hacen, una por línea (para las tarjetas de la sala). */
    public static String[] acciones(String rol) {
        List<String> lineas = new ArrayList<>();
        if (ARCONTE.equals(rol)) {
            lineas.add(etiqueta(rol, GameEngine.DISCURSO) + " (no dos turnos seguidos)");
        }
        lineas.add(etiqueta(rol, GameEngine.PREGONAR) + " " + Lugar.AGORA.donde + (SOFISTA.equals(rol) ? " (llegas más lejos)" : ""));
        Lugar indagar = Lugar.paraAccion(rol, GameEngine.INDAGAR);
        lineas.add(etiqueta(rol, GameEngine.INDAGAR) + " " + indagar.donde + (LOGOGRAFO.equals(rol) ? " (nunca te equivocas)" : ""));
        lineas.add(etiqueta(rol, GameEngine.DENUNCIAR) + " " + Lugar.HELIEA.donde);
        lineas.add(etiqueta(rol, GameEngine.CALLAR) + ", donde sea");
        return lineas.toArray(new String[0]);
    }

    /** El nombre del rol sin tildes ni mayúsculas, para nombres de archivo (retratos/logografo.png). */
    public static String archivo(String rol) {
        return rol.toLowerCase()
                .replace('á', 'a').replace('é', 'e').replace('í', 'i').replace('ó', 'o').replace('ú', 'u');
    }

    /** Texto de bienvenida personalizado que ve cada jugador antes de entrar a la partida. */
    public static String bienvenida(String nombre, String rol) {
        return "Te damos la bienvenida a Arconte, " + nombre + ".\n\n"
                + "Eres " + articulo(rol) + " " + rol + " y " + mision(rol) + "\n\n"
                + "Se acerca la elección del Arconte y Ossa, el espíritu de los rumores, ronda por la polis. "
                + "Tus decisiones inclinarán la balanza.\n\n"
                + "¿Traerás la luz de la verdad de Aletheia o la devastación de Eris?";
    }

    @Override
    public String toString() {
        return nombre + (bot ? " [BOT]" : "") + " (" + rol + ") - " + puntuacion + " pts";
    }
}
