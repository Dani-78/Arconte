package view;

import model.*;
import javax.swing.*;
import java.awt.*;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;

/**
 * Dibuja el grafo social se resalta de quien a quien se comparte y el resultado BFS es la propagacion.
 */
public class GraphPanel extends JPanel {

    private GrafoSocial grafo;
    private Jugador origen;
    private Set<Jugador> receptores = new HashSet<>();

    public GraphPanel() {
        setBackground(Color.WHITE);
        setPreferredSize(new Dimension(560, 380));
    }

    public void setDatos(GrafoSocial grafo, Jugador origen, List<Jugador> receptores) {
        this.grafo = grafo;
        this.origen = origen;
        this.receptores = receptores == null ? new HashSet<>() : new HashSet<>(receptores);
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2.setColor(new Color(60, 60, 60));
        g2.setFont(g2.getFont().deriveFont(Font.BOLD, 15f));
        g2.drawString("Grafo social de Ciudad Nova (naranja = quien comparte, amarillo = a quien le llega)", 12, 22);

        if (grafo == null) return;
        List<Jugador> personas = grafo.getPersonas();
        int n = personas.size();
        if (n == 0) return;

        int cx = getWidth() / 2;
        int cy = getHeight() / 2 + 25;
        int radio = Math.max(90, Math.min(getWidth(), getHeight()) / 2 - 90);

        Map<Jugador, Point> pos = new HashMap<>();
        for (int i = 0; i < n; i++) {
            double ang = 2 * Math.PI * i / n - Math.PI / 2;
            int x = cx + (int) (radio * Math.cos(ang));
            int y = cy + (int) (radio * Math.sin(ang));
            pos.put(personas.get(i), new Point(x, y));
        }

        // aristas 
        g2.setStroke(new BasicStroke(2f));
        g2.setColor(new Color(180, 180, 180));
        Set<String> dibujadas = new HashSet<>();
        for (Jugador p : personas) {
            for (Jugador vecino : grafo.vecinos(p)) {
                String clave = clave(p, vecino);
                if (dibujadas.contains(clave)) continue;
                dibujadas.add(clave);
                Point a = pos.get(p);
                Point b = pos.get(vecino);
                g2.drawLine(a.x, a.y, b.x, b.y);
            }
        }

        // nodos
        int r = 38;
        for (Jugador p : personas) {
            Point pt = pos.get(p);
            boolean esOrigen = (p == origen);
            boolean recibio = receptores.contains(p);

            Color relleno;
            if (esOrigen) relleno = new Color(230, 126, 34);
            else if (recibio) relleno = new Color(255, 224, 130);
            else if (p.isBot()) relleno = new Color(213, 213, 230);
            else relleno = new Color(180, 220, 255);

            g2.setColor(relleno);
            g2.fillOval(pt.x - r, pt.y - r, r * 2, r * 2);
            g2.setColor(Color.DARK_GRAY);
            g2.setStroke(new BasicStroke((esOrigen || recibio) ? 2.8f : 1.4f));
            g2.drawOval(pt.x - r, pt.y - r, r * 2, r * 2);

            g2.setFont(g2.getFont().deriveFont(Font.PLAIN, 12f));
            FontMetrics fm = g2.getFontMetrics();
            String nombre = p.getNombre();
            String etiquetaRol = p.isBot() ? p.getRol() + " (bot)" : p.getRol();
            g2.setColor(Color.BLACK);
            g2.drawString(nombre, pt.x - fm.stringWidth(nombre) / 2, pt.y - 3);

            g2.setFont(g2.getFont().deriveFont(Font.PLAIN, 10f));
            fm = g2.getFontMetrics();
            g2.drawString(etiquetaRol, pt.x - fm.stringWidth(etiquetaRol) / 2, pt.y + 12);
        }
    }

    private String clave(Jugador a, Jugador b) {
        int ia = System.identityHashCode(a);
        int ib = System.identityHashCode(b);
        return ia < ib ? (ia + "-" + ib) : (ib + "-" + ia);
    }
}
