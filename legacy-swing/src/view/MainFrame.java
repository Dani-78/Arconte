package view;

import model.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Interfaz grafica
 
 */
public class MainFrame extends JFrame {

    private final GameEngine motor;

    private final JLabel lblPublicacion = new JLabel();
    private final JLabel lblAutor = new JLabel();
    private final JLabel lblTiempo = new JLabel();
    private final JLabel lblTurno = new JLabel();
    private final JLabel lblJugadorActual = new JLabel();
    private final JLabel lblAmigos = new JLabel();
    private final JTextArea areaBitacora = new JTextArea();
    private final JPanel panelIndicadores = new JPanel();

    private final DecisionTreePanel panelDecision = new DecisionTreePanel();
    private final GraphPanel panelGrafo = new GraphPanel();

    private final Map<String, JProgressBar> barras = new HashMap<>();
    private JButton btnAccion1, btnAccion2, btnAccion3, btnAccion4;

    private Timer temporizador;
    private int segundosRestantes;
    private boolean altoContraste = false;
    private boolean modoPostVerificacion = false;

    public MainFrame(GameEngine motor) {
        super("Alcalde Digital - Ciudad Nova");
        this.motor = motor;
        construirUI();
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1300, 820);
        setLocationRelativeTo(null);
        nuevaRonda();
    }

    private void construirUI() {
        setLayout(new BorderLayout(8, 8));
        add(construirPanelSuperior(), BorderLayout.NORTH);
        add(construirPanelCentral(), BorderLayout.CENTER);
        add(construirPanelInferior(), BorderLayout.SOUTH);
    }

    private JPanel construirPanelSuperior() {
        panelIndicadores.setLayout(new GridLayout(1, 6, 8, 4));
        panelIndicadores.setBorder(new EmptyBorder(8, 12, 8, 12));
        String[] nombres = {"Info. verificada", "Confianza", "Convivencia", "Bienestar", "Desinformacion", "Conflictos"};
        for (String nombre : nombres) {
            JPanel mini = new JPanel(new BorderLayout());
            JLabel titulo = new JLabel(nombre, SwingConstants.CENTER);
            titulo.setFont(titulo.getFont().deriveFont(Font.PLAIN, 13f));
            JProgressBar barra = new JProgressBar(0, 100);
            barra.setStringPainted(true);
            barra.setFont(barra.getFont().deriveFont(13f));
            mini.add(titulo, BorderLayout.NORTH);
            mini.add(barra, BorderLayout.CENTER);
            panelIndicadores.add(mini);
            barras.put(nombre, barra);
        }
        JPanel top = new JPanel(new BorderLayout());

        JPanel barraSuperior = new JPanel(new BorderLayout());
        JLabel titulo = new JLabel("  Ciudad Nova - periodo electoral", SwingConstants.LEFT);
        titulo.setFont(titulo.getFont().deriveFont(Font.BOLD, 20f));
        JButton btnAyuda = new JButton("Ayuda");
        btnAyuda.setFont(btnAyuda.getFont().deriveFont(14f));
        btnAyuda.addActionListener(e -> mostrarAyuda());
        JButton btnContraste = new JButton("Alto contraste");
        btnContraste.setFont(btnContraste.getFont().deriveFont(14f));
        btnContraste.addActionListener(e -> alternarContraste());
        JPanel botonesTop = new JPanel();
        botonesTop.add(btnContraste);
        botonesTop.add(btnAyuda);
        barraSuperior.add(titulo, BorderLayout.WEST);
        barraSuperior.add(botonesTop, BorderLayout.EAST);

        top.add(barraSuperior, BorderLayout.NORTH);
        top.add(panelIndicadores, BorderLayout.CENTER);
        return top;
    }

    private JComponent construirPanelCentral() {
        JPanel izquierda = new JPanel();
        izquierda.setLayout(new BoxLayout(izquierda, BoxLayout.Y_AXIS));
        izquierda.setBorder(BorderFactory.createTitledBorder("Publicacion en Civitas"));

        lblPublicacion.setFont(lblPublicacion.getFont().deriveFont(Font.PLAIN, 20f));
        lblPublicacion.setAlignmentX(Component.LEFT_ALIGNMENT);
        lblAutor.setFont(lblAutor.getFont().deriveFont(14f));
        lblAutor.setAlignmentX(Component.LEFT_ALIGNMENT);
        lblAutor.setForeground(Color.GRAY);
        lblTiempo.setAlignmentX(Component.LEFT_ALIGNMENT);
        lblTiempo.setFont(lblTiempo.getFont().deriveFont(Font.BOLD, 18f));
        lblTurno.setAlignmentX(Component.LEFT_ALIGNMENT);
        lblTurno.setFont(lblTurno.getFont().deriveFont(15f));
        lblJugadorActual.setAlignmentX(Component.LEFT_ALIGNMENT);
        lblJugadorActual.setFont(lblJugadorActual.getFont().deriveFont(Font.BOLD, 17f));
        lblAmigos.setAlignmentX(Component.LEFT_ALIGNMENT);
        lblAmigos.setFont(lblAmigos.getFont().deriveFont(13f));
        lblAmigos.setForeground(new Color(70, 70, 70));

        izquierda.add(Box.createVerticalStrut(6));
        izquierda.add(lblTurno);
        izquierda.add(lblJugadorActual);
        izquierda.add(lblAmigos);
        izquierda.add(Box.createVerticalStrut(10));
        izquierda.add(lblPublicacion);
        izquierda.add(lblAutor);
        izquierda.add(Box.createVerticalStrut(10));
        izquierda.add(lblTiempo);
        izquierda.add(Box.createVerticalStrut(14));

        JPanel botones = new JPanel(new GridLayout(2, 2, 10, 10));
        btnAccion1 = new JButton("Compartir");
        btnAccion2 = new JButton("Verificar");
        btnAccion3 = new JButton("Ignorar");
        btnAccion4 = new JButton("Reportar");
        for (JButton b : new JButton[]{btnAccion1, btnAccion2, btnAccion3, btnAccion4}) {
            b.setFont(b.getFont().deriveFont(Font.BOLD, 15f));
        }
        btnAccion1.addActionListener(e -> manejarClicBoton("Compartir"));
        btnAccion2.addActionListener(e -> manejarClicBoton("Verificar"));
        btnAccion3.addActionListener(e -> manejarClicBoton("Ignorar"));
        btnAccion4.addActionListener(e -> manejarClicBoton("Reportar"));
        botones.add(btnAccion1);
        botones.add(btnAccion2);
        botones.add(btnAccion3);
        botones.add(btnAccion4);
        botones.setMaximumSize(new Dimension(420, 110));
        izquierda.add(botones);
        izquierda.add(Box.createVerticalStrut(10));

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(tabs.getFont().deriveFont(14f));
        tabs.addTab("Arbol de decisiones", new JScrollPane(panelDecision));
        tabs.addTab("Grafo social", new JScrollPane(panelGrafo));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, izquierda, tabs);
        split.setDividerLocation(430);
        return split;
    }

    private JPanel construirPanelInferior() {
        JPanel inferior = new JPanel(new BorderLayout());
        areaBitacora.setEditable(false);
        areaBitacora.setLineWrap(true);
        areaBitacora.setWrapStyleWord(true);
        areaBitacora.setFont(areaBitacora.getFont().deriveFont(14f));
        JScrollPane scroll = new JScrollPane(areaBitacora);
        scroll.setPreferredSize(new Dimension(100, 150));
        scroll.setBorder(BorderFactory.createTitledBorder("Bitacora de Ciudad Nova"));
        inferior.add(scroll, BorderLayout.CENTER);
        return inferior;
    }

    // Turnos

    private void nuevaRonda() {
        if (motor.juegoTerminado()) {
            terminarJuego();
            return;
        }
        modoPostVerificacion = false;
        motor.dispararEventoAleatorio();
        motor.generarPublicacion();
        actualizarVista();

        if (motor.getJugadorActual().isBot()) {
            habilitarBotones(false);
            lblTiempo.setText("Turno automatico del bot...");
            Timer pausa = new Timer(1400, e -> resolverTurnoBot());
            pausa.setRepeats(false);
            pausa.start();
        } else {
            habilitarBotones(true);
            iniciarTemporizador();
        }
    }

    private void habilitarBotones(boolean habilitado) {
        btnAccion1.setEnabled(habilitado);
        btnAccion2.setEnabled(habilitado);
        btnAccion3.setEnabled(habilitado);
        btnAccion4.setEnabled(habilitado);
    }

 
    private void manejarClicBoton(String accionBase) {
        if (modoPostVerificacion) {
            if ("Verificar".equals(accionBase)) return; // ya no se puede volver a verificar esta publicacion
            finalizarHumanoTrasVerificar(accionBase);
        } else if ("Verificar".equals(accionBase)) {
            verificarHumano();
        } else {
            resolverHumano(accionBase);
        }
    }

    private void resolverHumano(String accionBase) {
        if (temporizador != null) temporizador.stop();
        String resultado = motor.resolverAccionDirecta(accionBase);
        JOptionPane.showMessageDialog(this, resultado, "Consecuencia", JOptionPane.INFORMATION_MESSAGE);
        actualizarVista();
        continuarODeterminarFin();
    }

    private void verificarHumano() {
        if (temporizador != null) temporizador.stop();
        String resultado = motor.iniciarVerificacion();
        modoPostVerificacion = true;
        JOptionPane.showMessageDialog(this, resultado, "Resultado de tu investigacion", JOptionPane.INFORMATION_MESSAGE);
        actualizarVista();
        btnAccion2.setEnabled(false); // ya investigaste; ahora decides que hacer con los otros 3 botones
        lblTiempo.setText("Ahora decide que hacer con la publicacion (tu decision, sin importar si es sensata).");
    }

    private void finalizarHumanoTrasVerificar(String accionFinal) {
        String resultado = motor.finalizarTrasVerificar(accionFinal);
        modoPostVerificacion = false;
        JOptionPane.showMessageDialog(this, resultado, "Consecuencia", JOptionPane.INFORMATION_MESSAGE);
        actualizarVista();
        continuarODeterminarFin();
    }

    private void resolverTurnoBot() {
        Jugador bot = motor.getJugadorActual();
        String accionBase = motor.decidirAccionBot();
        String resumen;
        if ("Verificar".equals(accionBase)) {
            motor.iniciarVerificacion();
            String accionFinal = motor.decidirAccionBotTrasVerificar();
            motor.finalizarTrasVerificar(accionFinal);
            resumen = "[Turno automatico de " + bot + "] investigo y luego eligio: " + accionFinal;
        } else {
            motor.resolverAccionDirecta(accionBase);
            resumen = "[Turno automatico de " + bot + "] eligio: " + accionBase;
        }
        areaBitacora.append("\n" + resumen);
        actualizarVista();
        continuarODeterminarFin();
    }

    private void continuarODeterminarFin() {
        if (motor.juegoTerminado()) {
            terminarJuego();
        } else {
            nuevaRonda();
        }
    }

    private void iniciarTemporizador() {
        segundosRestantes = GameEngine.SEGUNDOS_POR_TURNO;
        lblTiempo.setText("Tiempo restante: " + segundosRestantes + " s");
        if (temporizador != null) temporizador.stop();
        temporizador = new Timer(1000, e -> {
            segundosRestantes--;
            lblTiempo.setText("Tiempo restante: " + segundosRestantes + " s");
            if (segundosRestantes <= 0) {
                temporizador.stop();
                areaBitacora.append("\n(Se acabo el tiempo: la publicacion se ignora por defecto)");
                resolverHumano("Ignorar");
            }
        });
        temporizador.start();
    }

    private void actualizarVista() {
        Publicacion p = motor.getPublicacionActual();
        if (p != null) {
            lblPublicacion.setText("<html><body style='width:340px'>\"" + p.getTexto() + "\"</body></html>");
            lblAutor.setText("Publicado por: " + p.getAutor());
        }
        lblTurno.setText("Publicaciones resueltas: " + motor.getPublicacionesResueltas() + " / " + GameEngine.PUBLICACIONES_PARA_TERMINAR);

        Jugador actual = motor.getJugadorActual();
        lblJugadorActual.setText("Turno de: " + actual + (actual.isBot() ? "  (automatico)" : ""));

        List<Jugador> amigos = new ArrayList<>(motor.getGrafo().vecinos(actual));
        StringBuilder sbAmigos = new StringBuilder("Tus amigos en Civitas: ");
        for (int i = 0; i < amigos.size(); i++) {
            sbAmigos.append(amigos.get(i).getNombre());
            if (i < amigos.size() - 1) sbAmigos.append(", ");
        }
        lblAmigos.setText(sbAmigos.toString());

        btnAccion1.setText(actual.etiquetaAccion("Compartir"));
        btnAccion2.setText(actual.etiquetaAccion("Verificar"));
        btnAccion3.setText(actual.etiquetaAccion("Ignorar")
                + (actual.esAlcalde() && !motor.puedeDeclaracionPublica() ? " (en espera)" : ""));
        btnAccion4.setText(actual.etiquetaAccion("Reportar"));

        CiudadEstado c = motor.getCiudad();
        barras.get("Info. verificada").setValue(c.getInformacionVerificada());
        barras.get("Confianza").setValue(c.getConfianza());
        barras.get("Convivencia").setValue(c.getConvivencia());
        barras.get("Bienestar").setValue(c.getBienestarDigital());
        barras.get("Desinformacion").setValue(c.getDesinformacion());
        barras.get("Conflictos").setValue(c.getConflictos());

        panelDecision.setDatos(motor.getArbolDecisiones().getRaiz(), motor.getUltimoCamino());
        panelGrafo.setDatos(motor.getGrafo(), motor.getUltimoOrigenPropagacion(), motor.getUltimosReceptores());

        areaBitacora.setText(String.join("\n", motor.getBitacora()));
        areaBitacora.setCaretPosition(areaBitacora.getDocument().getLength());
    }

    private void terminarJuego() {
        if (temporizador != null) temporizador.stop();
        habilitarBotones(false);
        String resultado = motor.realizarEleccion();
        StringBuilder marcador = new StringBuilder("Puntuaciones finales:\n");
        for (Jugador j : motor.getJugadores()) marcador.append(" - ").append(j).append("\n");
        JOptionPane.showMessageDialog(this, resultado + "\n\n" + marcador,
                "Fin de la campaña electoral", JOptionPane.INFORMATION_MESSAGE);
    }

    private void mostrarAyuda() {
        String ayuda = "OBJETIVO\n"
                + "Terminar el proceso electoral con la mayor informacion verificada,\n"
                + "confianza, convivencia y bienestar digital posibles, evitando que\n"
                + "crezcan la desinformacion y los conflictos.\n\n"
                + "COMO FUNCIONA CADA TURNO\n"
                + " 1) Eliges una de las 4 acciones. Si eliges Compartir/Impulsar,\n"
                + "    Ignorar/Declaracion publica o Reportar, el turno termina ahi.\n"
                + " 2) Si eliges Verificar/Investigar a fondo, PRIMERO se te revela\n"
                + "    (o crees que se te revela) si la publicacion es verdadera o\n"
                + "    falsa, y LUEGO tu decides libremente que hacer con eso — puedes\n"
                + "    incluso compartir algo que acabas de confirmar que es falso, si\n"
                + "    quieres jugar asi. La decision es completamente tuya.\n\n"
                + "BOTONES (cambian segun tu rol)\n"
                + " - Compartir / Impulsar (Influencer): el Influencer llega mucho\n"
                + "   mas lejos en el grafo social (para bien o mal).\n"
                + " - Verificar / Investigar a fondo (Periodista): el Periodista\n"
                + "   SIEMPRE descubre la verdad; los demas roles pueden equivocarse.\n"
                + " - Ignorar / Declaracion publica (Alcalde): el Alcalde puede usar\n"
                + "   esto para subir confianza y convivencia directamente, pero solo\n"
                + "   cada varios turnos (cooldown).\n"
                + " - Reportar: igual para todos los roles.\n\n"
                + "ROLES Y BOTS\n"
                + "Ciudadano, Periodista, Influencer y Candidato a alcalde. Si nadie\n"
                + "elige un rol, un BOT lo cubre automaticamente para que las 4\n"
                + "mecanicas siempre esten presentes en la partida.\n\n"
                + "GRAFO SOCIAL\n"
                + "Jugadores y bots son amigos entre si en Civitas. Cuando compartes\n"
                + "algo, se propaga por tus amigos (y, si eres Influencer, tambien por\n"
                + "los amigos de tus amigos). Se ve resaltado en la pestaña 'Grafo social'.\n\n"
                + "COMO SE GANA\n"
                + "Al terminar las publicaciones de la partida, el candidato que resulta\n"
                + "electo depende del puntaje general que dejo la ciudad.";
        JTextArea area = new JTextArea(ayuda);
        area.setEditable(false);
        area.setFont(area.getFont().deriveFont(15f));
        JScrollPane scroll = new JScrollPane(area);
        scroll.setPreferredSize(new Dimension(600, 460));
        JOptionPane.showMessageDialog(this, scroll, "Ayuda", JOptionPane.PLAIN_MESSAGE);
    }

    private void alternarContraste() {
        altoContraste = !altoContraste;
        Color fondo = altoContraste ? Color.BLACK : UIManager.getColor("Panel.background");
        Color texto = altoContraste ? Color.YELLOW : Color.BLACK;
        aplicarColores(this.getContentPane(), fondo, texto);
        panelDecision.setBackground(altoContraste ? Color.BLACK : Color.WHITE);
        panelGrafo.setBackground(altoContraste ? Color.BLACK : Color.WHITE);
        repaint();
    }

    private void aplicarColores(Component c, Color fondo, Color texto) {
        c.setBackground(fondo);
        c.setForeground(texto);
        if (c instanceof Container) {
            for (Component hijo : ((Container) c).getComponents()) {
                aplicarColores(hijo, fondo, texto);
            }
        }
    }

    // Inicio y roles. no deja repetir rol y hace bots para los q falten


    public static List<Jugador> pedirJugadores() {
        List<String> rolesDisponibles = new ArrayList<>(Arrays.asList(
                Jugador.CIUDADANO, Jugador.PERIODISTA, Jugador.INFLUENCER, Jugador.ALCALDE));
        List<Jugador> jugadores = new ArrayList<>();

        int cantidad = 0;
        while (cantidad < 2 || cantidad > 4) {
            String s = JOptionPane.showInputDialog(null, "Escoja el número de jugadores (2 - 4)", "2");
            if (s == null) s = "";
            try {
                cantidad = Integer.parseInt(s.trim());
            } catch (Exception ex) {
                cantidad = 0;
            }
        }

        for (int i = 1; i <= cantidad; i++) {
            String nombre = JOptionPane.showInputDialog(null, "Nombre del jugador " + i, "Jugador" + i);
            if (nombre == null || nombre.isBlank()) nombre = "Jugador" + i;
            String[] opciones = rolesDisponibles.toArray(new String[0]);
            String rol = (String) JOptionPane.showInputDialog(null,
                    "Rol de " + nombre ,
                    "Seleccionar rol", JOptionPane.QUESTION_MESSAGE, null, opciones, opciones[0]);
            if (rol == null) rol = opciones[0];
            jugadores.add(new Jugador(nombre, rol));
            rolesDisponibles.remove(rol);
        }

        for (String rolFaltante : rolesDisponibles) {
            jugadores.add(new Jugador("Bot " + rolFaltante, rolFaltante, true));
        }

        return jugadores;
    }
}
