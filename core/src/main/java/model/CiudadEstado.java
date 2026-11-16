package model;

/**
 */
public class CiudadEstado {

    private int informacionVerificada = 60;
    private int confianza = 55;
    private int convivencia = 65;
    private int bienestarDigital = 60;
    private int desinformacion = 25;
    private int conflictos = 15;

    private int clamp(int v) { return Math.max(0, Math.min(100, v)); }

    public void ajustarInformacionVerificada(int d) { informacionVerificada = clamp(informacionVerificada + d); }
    public void ajustarConfianza(int d) { confianza = clamp(confianza + d); }
    public void ajustarConvivencia(int d) { convivencia = clamp(convivencia + d); }
    public void ajustarBienestar(int d) { bienestarDigital = clamp(bienestarDigital + d); }
    public void ajustarDesinformacion(int d) { desinformacion = clamp(desinformacion + d); }
    public void ajustarConflictos(int d) { conflictos = clamp(conflictos + d); }

    public int getInformacionVerificada() { return informacionVerificada; }
    public int getConfianza() { return confianza; }
    public int getConvivencia() { return convivencia; }
    public int getBienestarDigital() { return bienestarDigital; }
    public int getDesinformacion() { return desinformacion; }
    public int getConflictos() { return conflictos; }

    /** Puntaje general de la ciudad */
    public int puntajeGeneral() {
        int positivo = informacionVerificada + confianza + convivencia + bienestarDigital;
        int negativo = desinformacion + conflictos;
        return positivo - negativo;
    }
}
