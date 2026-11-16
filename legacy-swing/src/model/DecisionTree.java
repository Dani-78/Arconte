package model;

import java.util.ArrayList;
import java.util.List;

/**
 * Arbol de decisiones y consecuencias con lo que puede pasar según las decisiones del usuario
 */
public class DecisionTree {

    /** Nodo del arbol de decisiones. */
    public static class NodoDecision {
        public final String etiqueta;
        public final String consecuencia; 
        public final List<NodoDecision> hijos = new ArrayList<>();

        NodoDecision(String etiqueta) {
            this(etiqueta, null);
        }

        NodoDecision(String etiqueta, String consecuencia) {
            this.etiqueta = etiqueta;
            this.consecuencia = consecuencia;
        }

        void agregarHijo(NodoDecision hijo) {
            hijos.add(hijo);
        }
    }

    private final NodoDecision raiz;

    public DecisionTree() {
        raiz = construirArbol();
    }

    public NodoDecision getRaiz() { return raiz; }

    private NodoDecision construirArbol() {
        NodoDecision publicacion = new NodoDecision("PUBLICACION");

        NodoDecision compartirDirecto = new NodoDecision("Compartir",
                "Compartes sin verificar: la publicacion se difunde de inmediato entre tus contactos.");
        NodoDecision verificar = new NodoDecision("Verificar");
        NodoDecision ignorarDirecto = new NodoDecision("Ignorar",
                "No pasa nada de inmediato, pero pierdes la oportunidad de aportar a la convivencia.");
        NodoDecision reportarDirecto = new NodoDecision("Reportar",
                "La publicacion se envia directamente a moderacion de Civitas.");

        publicacion.agregarHijo(compartirDirecto);
        publicacion.agregarHijo(verificar);
        publicacion.agregarHijo(ignorarDirecto);
        publicacion.agregarHijo(reportarDirecto);

        NodoDecision esVerdadera = new NodoDecision("¿Es verdadera?");
        verificar.agregarHijo(esVerdadera);

        NodoDecision si = new NodoDecision("Si");
        NodoDecision no = new NodoDecision("No");
        esVerdadera.agregarHijo(si);
        esVerdadera.agregarHijo(no);

        // Las 3 decisiones posibles cuando la investigacion dice "es verdadera"
        si.agregarHijo(new NodoDecision("Compartir",
                "Investigaste y era cierta: la compartes. Buena decision, sube confianza e informacion verificada."));
        si.agregarHijo(new NodoDecision("Reportar",
                "Investigaste y era cierta, pero la reportaste de todas formas: es un reporte injustificado."));
        si.agregarHijo(new NodoDecision("Ignorar",
                "Investigaste y era cierta, pero decidiste no hacer nada: pierdes la oportunidad de aportar informacion confiable."));

        // Las 3 decisiones posibles cuando la investigacion dice "es falsa"
        no.agregarHijo(new NodoDecision("Compartir",
                "Investigaste y era falsa, pero la compartiste igual: a pesar de saberlo, ayudas a propagar desinformacion."));
        no.agregarHijo(new NodoDecision("Reportar",
                "Investigaste y era falsa: la reportas. Excelente, baja la desinformacion en Ciudad Nova."));
        no.agregarHijo(new NodoDecision("Ignorar",
                "Investigaste y era falsa, pero no hiciste nada: la publicacion sigue circulando sin moderacion."));

        return publicacion;
    }

    /**
     * DFS busca el recorrido por los nodos/decisiones de la raíz al último nodo
     */
    public List<NodoDecision> buscarCamino(String... etiquetas) {
        List<NodoDecision> camino = new ArrayList<>();
        camino.add(raiz);
        if (dfs(raiz, etiquetas, 0, camino)) {
            return camino;
        }
        return new ArrayList<>();
    }


    private boolean dfs(NodoDecision actual, String[] etiquetas, int nivel, List<NodoDecision> camino) {
        if (nivel == etiquetas.length) return true;
        for (NodoDecision hijo : actual.hijos) {
            camino.add(hijo);
            boolean coincide = hijo.etiqueta.equalsIgnoreCase(etiquetas[nivel]);
            if (dfs(hijo, etiquetas, coincide ? nivel + 1 : nivel, camino)) return true;
            camino.remove(camino.size() - 1);
        }
        return false;
    }

    
    public List<NodoDecision> dfsPreOrdenCompleto() {
        List<NodoDecision> lista = new ArrayList<>();
        dfsPreOrdenCompleto(raiz, lista);
        return lista;
    }

    private void dfsPreOrdenCompleto(NodoDecision n, List<NodoDecision> lista) {
        if (n == null) return;
        lista.add(n);
        for (NodoDecision hijo : n.hijos) dfsPreOrdenCompleto(hijo, lista);
    }
}
