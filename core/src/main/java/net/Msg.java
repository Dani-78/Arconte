package net;

import java.util.ArrayList;
import java.util.List;

/**
 * Mensaje del protocolo de Arconte: una línea de texto UTF-8, campos separados por tabulador.
 * Los tabuladores, saltos de línea y barras invertidas dentro de un campo se escapan.
 *
 * Cliente -> servidor:  HELLO nombre version | ROLE rol | START | ACCION accion | FINAL accion | POS x y
 * Servidor -> cliente:  WELCOME id esAnfitrion direcciones | LOBBY ... | START miIndice nombres... |
 *                       STATE ... | VERIF texto | POS indice x y | AVISO texto | ERROR texto
 * Local (cliente):      CLOSED  (la conexión se cerró)
 */
public final class Msg {

    public final String type;
    public final List<String> f;

    private Msg(String type, List<String> f) {
        this.type = type;
        this.f = f;
    }

    public static Msg of(String type, Object... fields) {
        List<String> l = new ArrayList<>();
        for (Object o : fields) l.add(String.valueOf(o));
        return new Msg(type, l);
    }

    public String s(int i) {
        return i >= 0 && i < f.size() ? f.get(i) : "";
    }

    public int i(int idx) {
        try {
            return Integer.parseInt(s(idx).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public double d(int idx) {
        try {
            return Double.parseDouble(s(idx).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public String encode() {
        StringBuilder sb = new StringBuilder(esc(type));
        for (String x : f) sb.append('\t').append(esc(x));
        return sb.toString();
    }

    public static Msg decode(String line) {
        List<String> parts = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (int k = 0; k < line.length(); k++) {
            char c = line.charAt(k);
            if (c == '\t') {
                parts.add(cur.toString());
                cur.setLength(0);
            } else if (c == '\\' && k + 1 < line.length()) {
                char n = line.charAt(++k);
                switch (n) {
                    case 't': cur.append('\t'); break;
                    case 'n': cur.append('\n'); break;
                    case 'r': cur.append('\r'); break;
                    default: cur.append(n); break;
                }
            } else {
                cur.append(c);
            }
        }
        parts.add(cur.toString());
        String type = parts.remove(0);
        return new Msg(type, parts);
    }

    private static String esc(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int k = 0; k < s.length(); k++) {
            char c = s.charAt(k);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '\t': sb.append("\\t"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                default: sb.append(c); break;
            }
        }
        return sb.toString();
    }
}
