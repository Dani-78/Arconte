package model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * Grafo social de la ciudad. Los vertices son los jugadores y "bots" y las aristas representan amistad
 */
public class GrafoSocial {

    private final Map<Jugador, Set<Jugador>> adyacencia = new LinkedHashMap<>();

    public void agregarPersona(Jugador j) {
        adyacencia.putIfAbsent(j, new LinkedHashSet<>());
    }

    public void agregarAmistad(Jugador a, Jugador b) {
        agregarPersona(a);
        agregarPersona(b);
        if (a != b) {
            adyacencia.get(a).add(b);
            adyacencia.get(b).add(a);
        }
    }

    public Set<Jugador> vecinos(Jugador j) {
        return adyacencia.getOrDefault(j, Collections.emptySet());
    }

    public List<Jugador> getPersonas() {
        return new ArrayList<>(adyacencia.keySet());
    }

    /**
     *BFS: personas alcanzadas / profundidad
     */
    public List<Jugador> alcanceBFS(Jugador origen, int profundidadMax) {
        List<Jugador> resultado = new ArrayList<>();
        Set<Jugador> visitados = new HashSet<>();
        visitados.add(origen);

        Queue<Jugador> cola = new LinkedList<>();
        Queue<Integer> profundidades = new LinkedList<>();
        cola.add(origen);
        profundidades.add(0);

        while (!cola.isEmpty()) {
            Jugador actual = cola.poll();
            int profundidad = profundidades.poll();
            if (profundidad == profundidadMax) continue;
            for (Jugador vecino : vecinos(actual)) {
                if (!visitados.contains(vecino)) {
                    visitados.add(vecino);
                    resultado.add(vecino);
                    cola.add(vecino);
                    profundidades.add(profundidad + 1);
                }
            }
        }
        return resultado;
    }

    /**
     * DFS: todas las personas alcanzables desde el origen
     */
    public List<Jugador> alcanzablesDFS(Jugador origen) {
        List<Jugador> resultado = new ArrayList<>();
        Set<Jugador> visitados = new HashSet<>();
        dfs(origen, visitados, resultado);
        resultado.remove(origen);
        return resultado;
    }

    private void dfs(Jugador actual, Set<Jugador> visitados, List<Jugador> resultado) {
        visitados.add(actual);
        resultado.add(actual);
        for (Jugador vecino : vecinos(actual)) {
            if (!visitados.contains(vecino)) {
                dfs(vecino, visitados, resultado);
            }
        }
    }
}
