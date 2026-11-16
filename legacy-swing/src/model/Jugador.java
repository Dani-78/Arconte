package model;

/**
 * Acciones de cada rol:
 *  - Ciudadano: Compartir, Verificar, Ignorar, Reportar
 *  - Periodista: Investigar a fondo en vez de verificar, no se equivoca
 *  - Influencer: Impulsar en vez de compartir, llega a mas gente
 *  - Candidato a alcalde: Declaracion publica en vez de ignorar, pero se usa cada tantos turnos
 */
public class Jugador {

    public static final String CIUDADANO = "Ciudadano";
    public static final String PERIODISTA = "Periodista";
    public static final String INFLUENCER = "Influencer";
    public static final String ALCALDE = "Candidato a alcalde";

    private final String nombre;
    private final String rol;
    private final boolean bot;

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
    public int getPuntuacion() { return puntuacion; }
    public int getReputacion() { return reputacion; }
    public int getVerificacionesCorrectas() { return verificacionesCorrectas; }
    public int getCompartidasFalsas() { return compartidasFalsas; }

    public void sumarPuntos(int p) { puntuacion = Math.max(0, puntuacion + p); }
    public void sumarReputacion(int r) { reputacion = Math.max(0, Math.min(100, reputacion + r)); }
    public void registrarVerificacionCorrecta() { verificacionesCorrectas++; }
    public void registrarCompartidaFalsa() { compartidasFalsas++; }

    // El candidato a alcalde gana mas confianza
    public double multiplicadorConfianza() {
        return ALCALDE.equals(rol) ? 1.6 : 1.0;
    }

    public boolean esPeriodista() { return PERIODISTA.equals(rol); }
    public boolean esInfluencer() { return INFLUENCER.equals(rol); }
    public boolean esAlcalde() { return ALCALDE.equals(rol); }

    //     Botones de acciones
     
    public String etiquetaAccion(String accionBase) {
        switch (accionBase) {
            case "Compartir":
                return esInfluencer() ? "Impulsar" : "Compartir";
            case "Verificar":
                return esPeriodista() ? "Investigar a fondo" : "Verificar";
            case "Ignorar":
                return esAlcalde() ? "Declaración pública" : "Ignorar";
            default:
                return accionBase; // "Reportar" es igual para todos los roles
        }
    }

    @Override
    public String toString() {
        return nombre + (bot ? " [BOT]" : "") + " (" + rol + ") - " + puntuacion + " pts";
    }
}
