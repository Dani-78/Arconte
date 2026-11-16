package model;

/**
 * Lugares de Helíade donde se realiza cada acción. Son círculos sobre el mapa assets/Polis.png (1376 x 768,
 * origen arriba a la izquierda). Viven en el modelo porque los usan la vista (para dibujarlos y habilitar
 * botones) y el servidor (para comprobar que nadie actúa desde donde no debe).
 *
 *  - Ágora:   pregonar / declamar (la plaza central)
 *  - Puerto:  indagar preguntando a marineros y mercaderes, junto al tablón de anuncios
 *  - Metroon: el Logógrafo consulta el archivo de la ciudad (el templo del norte)
 *  - Heliea:  denunciar ante el tribunal (el templo del este)
 *  - Pnyx:    el Arconte da su discurso (el anfiteatro)
 *  Callar se puede hacer en cualquier lugar.
 *
 * Las coordenadas se verificaron sobre la imagen y cada lugar es alcanzable a pie (ver {@link Polis}).
 */
public enum Lugar {
    AGORA("Ágora", "al Ágora", "en el Ágora", 700, 430, 85,
            "El ágora era la plaza central de la polis: allí se comerciaba, se discutía de política y se pregonaban "
                    + "las noticias. Un rumor dicho en el ágora recorría la ciudad entera en pocas horas."),
    PUERTO("Puerto", "al Puerto", "en el Puerto", 1190, 500, 60,
            "El puerto era la puerta de la polis hacia el mundo: marineros y mercaderes traían mercancías y también "
                    + "noticias de otras ciudades, y allí se fijaban avisos para que todos los leyeran."),
    METROON("Metroon", "al Metroon", "en el Metroon", 690, 145, 50,
            "El Metroon guardaba los archivos del Estado ateniense: leyes, decretos y registros públicos. "
                    + "Era el sitio para comprobar un dato con los documentos en la mano."),
    HELIEA("Heliea", "a la Heliea", "en la Heliea", 1030, 425, 55,
            "La Heliea era el gran tribunal popular de Atenas: un jurado de ciudadanos elegidos por sorteo juzgaba "
                    + "las acusaciones. Un jurado así, de 501 ciudadanos, fue el que condenó a Sócrates."),
    PNYX("Pnyx", "a la Pnyx", "en la Pnyx", 1070, 150, 55,
            "La Pnyx era la colina donde se reunía la asamblea del pueblo: cualquier ciudadano podía subir a hablar "
                    + "y todos votaban a mano alzada. Allí se ganaban o se perdían los apoyos.");

    /** Lo que se muestra cuando el personaje camina fuera de cualquier lugar. */
    public static final String CALLES = "Las calles de Helíade. Los rumores no solo se pregonaban en la plaza: "
            + "también pasaban de puerta en puerta, de vecino en vecino.";

    /** Donde empiezan los cuatro personajes (en el orden de los roles): las baldosas del Ágora. */
    public static final float[][] INICIO = {{640, 435}, {682, 435}, {724, 435}, {766, 435}};

    public final String nombre;
    /** Para frases como "Ve al Ágora" o "Ve a la Heliea" (ya lleva la preposición y el artículo). */
    public final String irA;
    /** Para frases como "Pregonar en el Ágora". */
    public final String donde;
    /** Qué era este lugar en la antigua Grecia (se muestra mientras el jugador está ahí). */
    public final String descripcion;
    public final float x;
    public final float y;
    public final float radio;

    Lugar(String nombre, String irA, String donde, float x, float y, float radio, String descripcion) {
        this.nombre = nombre;
        this.irA = irA;
        this.donde = donde;
        this.descripcion = descripcion;
        this.x = x;
        this.y = y;
        this.radio = radio;
    }

    public boolean contiene(float px, float py) {
        float dx = px - x;
        float dy = py - y;
        return dx * dx + dy * dy <= radio * radio;
    }

    public float centroX() {
        return x;
    }

    public float centroY() {
        return y;
    }

    /** El lugar que ocupa un punto del mapa, o null si no está en ninguno. */
    public static Lugar en(float px, float py) {
        for (Lugar l : values()) if (l.contiene(px, py)) return l;
        return null;
    }

    /** Dónde hay que estar para hacer una acción, o null si se puede hacer en cualquier parte (Callar). */
    public static Lugar paraAccion(String rol, String accion) {
        switch (accion) {
            case GameEngine.PREGONAR:
                return AGORA;
            case GameEngine.INDAGAR:
                return Jugador.LOGOGRAFO.equals(rol) ? METROON : PUERTO;
            case GameEngine.DENUNCIAR:
                return HELIEA;
            case GameEngine.DISCURSO:
                return PNYX;
            default:
                return null;
        }
    }

    /** Frase corta de a dónde ir ("Ve a la Heliea para denunciar"), vacía si la acción es libre. */
    public static String indicacion(String rol, String accion) {
        Lugar l = paraAccion(rol, accion);
        if (l == null) return "";
        String verbo;
        switch (accion) {
            case GameEngine.PREGONAR:
                verbo = Jugador.SOFISTA.equals(rol) ? "declamar" : "pregonar";
                break;
            case GameEngine.INDAGAR:
                verbo = Jugador.LOGOGRAFO.equals(rol) ? "consultar los archivos" : "preguntar a la gente";
                break;
            case GameEngine.DENUNCIAR:
                verbo = "denunciar";
                break;
            default:
                verbo = "dar tu discurso";
                break;
        }
        return "Ve " + l.irA + " para " + verbo;
    }
}
