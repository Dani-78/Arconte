package view;

import model.*;
import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Dibuja el Arbol de decisiones y consecuencias y resalta el camino que se recorrio con DFS
 */
public class DecisionTreePanel extends JPanel {

    private DecisionTree.NodoDecision raiz;
    private List<DecisionTree.NodoDecision> camino;

    private final Map<DecisionTree.NodoDecision, Point> posiciones = new HashMap<>();
    private int contadorX;

    private static final int SEP_X = 90;
    private static final int SEP_Y = 65;
    private static final int MARGEN = 40;
    private static final int RADIO = 8; 

    public DecisionTreePanel() {
        setBackground(Color.WHITE);
        setPreferredSize(new Dimension(950, 420));
    }

    public void setDatos(DecisionTree.NodoDecision raiz, List<DecisionTree.NodoDecision> camino) {
        this.raiz = raiz;
        this.camino = camino;
        repaint();
    }

    private int calcularAncho(DecisionTree.NodoDecision n) {
        if (n.hijos.isEmpty()) return 1;
        int suma = 0;
        for (DecisionTree.NodoDecision h : n.hijos) suma += calcularAncho(h);
        return Math.max(1, suma);
    }

    private void calcularPosiciones(DecisionTree.NodoDecision n, int profundidad) {
        if (n.hijos.isEmpty()) {
            int x = MARGEN + contadorX * SEP_X + 40;
            int y = MARGEN + profundidad * SEP_Y;
            posiciones.put(n, new Point(x, y));
            contadorX++;
            return;
        }
        int inicio = contadorX;
        for (DecisionTree.NodoDecision h : n.hijos) {
            calcularPosiciones(h, profundidad + 1);
        }
        int fin = contadorX - 1;
        int xIni = posiciones.get(n.hijos.get(0)).x;
        int xFin = posiciones.get(n.hijos.get(n.hijos.size() - 1)).x;
        int x = (xIni + xFin) / 2;
        int y = MARGEN + profundidad * SEP_Y;
        posiciones.put(n, new Point(x, y));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(new Color(60, 60, 60));
        g2.drawString("Arbol de decisiones y consecuencias (resaltado = ultimo recorrido DFS)", 12, 18);

        if (raiz == null) return;

        posiciones.clear();
        contadorX = 0;
        calcularPosiciones(raiz, 0);

        Set<DecisionTree.NodoDecision> nodosCamino = new HashSet<>();
        if (camino != null) nodosCamino.addAll(camino);

        dibujarAristas(g2, raiz, nodosCamino);
        dibujarNodos(g2, raiz, nodosCamino);
    }

    private boolean esAristaResaltada(DecisionTree.NodoDecision padre, DecisionTree.NodoDecision hijo,
                                       Set<DecisionTree.NodoDecision> nodosCamino) {
        if (camino == null) return false;
        int i = camino.indexOf(padre);
        if (i < 0 || i + 1 >= camino.size()) return false;
        return camino.get(i + 1) == hijo;
    }

    private void dibujarAristas(Graphics2D g2, DecisionTree.NodoDecision n, Set<DecisionTree.NodoDecision> nodosCamino) {
        Point p = posiciones.get(n);
        for (DecisionTree.NodoDecision h : n.hijos) {
            Point ph = posiciones.get(h);
            boolean resaltada = esAristaResaltada(n, h, nodosCamino);
            g2.setColor(resaltada ? new Color(230, 126, 34) : new Color(170, 170, 170));
            g2.setStroke(new BasicStroke(resaltada ? 3.2f : 1.6f));
            g2.drawLine(p.x, p.y, ph.x, ph.y);
            dibujarAristas(g2, h, nodosCamino);
        }
    }

    private void dibujarNodos(Graphics2D g2, DecisionTree.NodoDecision n, Set<DecisionTree.NodoDecision> nodosCamino) {
        Point p = posiciones.get(n);
        boolean enCamino = nodosCamino.contains(n);

        FontMetrics fm = g2.getFontMetrics();
        int w = Math.max(50, fm.stringWidth(n.etiqueta) + 16);
        int h = 24;

        g2.setColor(enCamino ? new Color(255, 224, 178) : new Color(225, 235, 245));
        g2.fillRoundRect(p.x - w / 2, p.y - h / 2, w, h, 12, 12);
        g2.setColor(enCamino ? new Color(211, 84, 0) : Color.GRAY);
        g2.setStroke(new BasicStroke(enCamino ? 2.4f : 1.2f));
        g2.drawRoundRect(p.x - w / 2, p.y - h / 2, w, h, 12, 12);

        g2.setColor(Color.BLACK);
        g2.drawString(n.etiqueta, p.x - fm.stringWidth(n.etiqueta) / 2, p.y + 4);

        for (DecisionTree.NodoDecision hijo : n.hijos) dibujarNodos(g2, hijo, nodosCamino);
    }
}
