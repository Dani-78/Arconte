package model;


public class Publicacion {

    private static int contadorId = 0;

    private final int id;
    private String texto;
    private String autor;
    private boolean esVerdadera;   
    private boolean verificada;    // si el jugador ya la confirmo
    private int impacto;           // 1-100
    private String estado;         // PENDIENTE, COMPARTIDA, VERIFICADA, IGNORADA, REPORTADA

    public Publicacion(String texto, String autor, boolean esVerdadera, int impacto) {
        this.id = ++contadorId;
        this.texto = texto;
        this.autor = autor;
        this.esVerdadera = esVerdadera;
        this.impacto = Math.max(1, Math.min(100, impacto));
        this.verificada = false;
        this.estado = "PENDIENTE";
    }

    public int getId() { return id; }
    public String getTexto() { return texto; }
    public String getAutor() { return autor; }
    public boolean isVerdadera() { return esVerdadera; }
    public boolean isVerificada() { return verificada; }
    public void setVerificada(boolean v) { this.verificada = v; }
    public int getImpacto() { return impacto; }
    public void setImpacto(int impacto) { this.impacto = impacto; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    @Override
    public String toString() {
        String resumen = texto.length() > 28 ? texto.substring(0, 28) + "..." : texto;
        return "#" + id + " (impacto " + impacto + ", " + estado + ") " + resumen;
    }
}
