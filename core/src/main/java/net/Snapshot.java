package net;

import java.util.ArrayList;
import java.util.List;

/**
 * Lo que el servidor le cuenta a cada cliente sobre la partida. Es un simple contenedor de datos:
 * NO incluye si el rumor actual es verdadero, así que un cliente no puede hacer trampa leyendo la red.
 */
public final class Snapshot {

    public static final String TURNO = "TURNO";
    public static final String VERIFICANDO = "VERIFICANDO";
    public static final String RESULTADO = "RESULTADO";
    public static final String FIN = "FIN";

    public static final class Jug {
        public String nombre = "";
        public String rol = "";
        public boolean bot;
        public int puntos;
        public int reputacion;
        public int verificacionesCorrectas;
        public int compartidasFalsas;
    }

    public static final class Destino {
        public String nombre = "";
        public String rol = "";
        public String clave = "";
        public String titulo = "";
        public String texto = "";
    }

    public String fase = TURNO;
    /** Índice de quien juega ahora (o de quien jugará a continuación, si se está mostrando un resultado). */
    public int actual;
    /**
     * Índice de quien jugó el turno cuyo resultado se muestra. Mientras alguien juega es igual a {@link #actual}.
     * Durante el resultado, {@code actual} ya apunta al SIGUIENTE jugador, por eso hace falta este campo.
     */
    public int actor;
    public int resueltas;
    public int total;
    public boolean puedeDeclaracion;

    public String pubTexto = "";
    public String pubAutor = "";
    public String evento = "";
    public String resultado = "";
    public String camino = "";
    public String difusion = "";

    /** verdad, confianza, orden, bienestar, engaño, discordia (en ese orden). */
    public final int[] ciudad = new int[6];
    public final List<Jug> jugadores = new ArrayList<>();
    public final List<String> amigos = new ArrayList<>();
    /** Índices de los jugadores humanos que aún no pulsan "Continuar" (solo en la fase RESULTADO). */
    public final List<Integer> pendientes = new ArrayList<>();

    public String finTitulo = "";
    public String finTexto = "";
    public final List<Destino> destinos = new ArrayList<>();

    public Msg toMsg() {
        List<Object> o = new ArrayList<>();
        o.add(fase);
        o.add(actual);
        o.add(actor);
        o.add(resueltas);
        o.add(total);
        o.add(puedeDeclaracion ? 1 : 0);
        o.add(pubTexto);
        o.add(pubAutor);
        o.add(evento);
        o.add(resultado);
        o.add(camino);
        o.add(difusion);
        for (int v : ciudad) o.add(v);
        o.add(jugadores.size());
        for (Jug j : jugadores) {
            o.add(j.nombre);
            o.add(j.rol);
            o.add(j.bot ? 1 : 0);
            o.add(j.puntos);
            o.add(j.reputacion);
            o.add(j.verificacionesCorrectas);
            o.add(j.compartidasFalsas);
        }
        o.add(amigos.size());
        o.addAll(amigos);
        o.add(pendientes.size());
        o.addAll(pendientes);
        o.add(finTitulo);
        o.add(finTexto);
        o.add(destinos.size());
        for (Destino d : destinos) {
            o.add(d.nombre);
            o.add(d.rol);
            o.add(d.clave);
            o.add(d.titulo);
            o.add(d.texto);
        }
        return Msg.of("STATE", o.toArray());
    }

    public static Snapshot fromMsg(Msg m) {
        Snapshot s = new Snapshot();
        int k = 0;
        s.fase = m.s(k++);
        s.actual = m.i(k++);
        s.actor = m.i(k++);
        s.resueltas = m.i(k++);
        s.total = m.i(k++);
        s.puedeDeclaracion = m.i(k++) == 1;
        s.pubTexto = m.s(k++);
        s.pubAutor = m.s(k++);
        s.evento = m.s(k++);
        s.resultado = m.s(k++);
        s.camino = m.s(k++);
        s.difusion = m.s(k++);
        for (int c = 0; c < s.ciudad.length; c++) s.ciudad[c] = m.i(k++);
        int nj = m.i(k++);
        for (int c = 0; c < nj; c++) {
            Jug j = new Jug();
            j.nombre = m.s(k++);
            j.rol = m.s(k++);
            j.bot = m.i(k++) == 1;
            j.puntos = m.i(k++);
            j.reputacion = m.i(k++);
            j.verificacionesCorrectas = m.i(k++);
            j.compartidasFalsas = m.i(k++);
            s.jugadores.add(j);
        }
        int na = m.i(k++);
        for (int c = 0; c < na; c++) s.amigos.add(m.s(k++));
        int np = m.i(k++);
        for (int c = 0; c < np; c++) s.pendientes.add(m.i(k++));
        s.finTitulo = m.s(k++);
        s.finTexto = m.s(k++);
        int nd = m.i(k++);
        for (int c = 0; c < nd; c++) {
            Destino d = new Destino();
            d.nombre = m.s(k++);
            d.rol = m.s(k++);
            d.clave = m.s(k++);
            d.titulo = m.s(k++);
            d.texto = m.s(k++);
            s.destinos.add(d);
        }
        return s;
    }
}
