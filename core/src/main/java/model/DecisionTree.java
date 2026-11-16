package model;

import java.util.ArrayList;
import java.util.List;

/**
 * Árbol de decisiones y consecuencias: lo que puede pasar según lo que haga el jugador ante un rumor.
 */
public class DecisionTree {

    /** Nodo del árbol de decisiones. */
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
        NodoDecision rumor = new NodoDecision("RUMOR");

        NodoDecision pregonarDirecto = new NodoDecision(GameEngine.PREGONAR,
                "Pregonas sin comprobar: el rumor corre de boca en boca entre tus allegados.");
        NodoDecision indagar = new NodoDecision(GameEngine.INDAGAR);
        NodoDecision callarDirecto = new NodoDecision(GameEngine.CALLAR,
                "No pasa nada de inmediato, pero pierdes la ocasión de aportar al buen orden de la polis.");
        NodoDecision denunciarDirecto = new NodoDecision(GameEngine.DENUNCIAR,
                "El rumor se lleva ante los escribas de la tablilla para que lo examinen.");
        NodoDecision discurso = new NodoDecision(GameEngine.DISCURSO,
                "El candidato deja el rumor de lado y le habla al pueblo desde la Pnyx.");

        rumor.agregarHijo(pregonarDirecto);
        rumor.agregarHijo(indagar);
        rumor.agregarHijo(callarDirecto);
        rumor.agregarHijo(denunciarDirecto);
        rumor.agregarHijo(discurso);

        NodoDecision esVerdadero = new NodoDecision("¿Es verdadero?");
        indagar.agregarHijo(esVerdadero);

        NodoDecision si = new NodoDecision("Si");
        NodoDecision no = new NodoDecision("No");
        esVerdadero.agregarHijo(si);
        esVerdadero.agregarHijo(no);

        // Las 3 decisiones posibles cuando la indagación dice "es verdadero"
        si.agregarHijo(new NodoDecision(GameEngine.PREGONAR,
                "Indagaste y era cierto: lo pregonas. Buena decisión, sube la confianza y la verdad en el ágora."));
        si.agregarHijo(new NodoDecision(GameEngine.DENUNCIAR,
                "Indagaste y era cierto, pero lo denunciaste de todos modos: es una acusación injustificada."));
        si.agregarHijo(new NodoDecision(GameEngine.CALLAR,
                "Indagaste y era cierto, pero decidiste callar: pierdes la oportunidad de aportar información confiable."));

        // Las 3 decisiones posibles cuando la indagación dice "es falso"
        no.agregarHijo(new NodoDecision(GameEngine.PREGONAR,
                "Indagaste y era falso, pero lo pregonaste igual: a pesar de saberlo, ayudas a que Apate engañe a la ciudad."));
        no.agregarHijo(new NodoDecision(GameEngine.DENUNCIAR,
                "Indagaste y era falso: lo denuncias ante la tablilla. Excelente, baja el engaño en Helíade."));
        no.agregarHijo(new NodoDecision(GameEngine.CALLAR,
                "Indagaste y era falso, pero callaste: el rumor sigue circulando sin que nadie lo desmienta."));

        return rumor;
    }

    /** DFS: recorrido por los nodos/decisiones desde la raíz hasta el último nodo pedido. */
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
