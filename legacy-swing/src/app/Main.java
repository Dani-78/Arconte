package app;

import controller.GameController;
import javax.swing.*;
import javax.swing.plaf.FontUIResource;
import java.util.Enumeration;

/**
 * Alcalde Digital - Ciudad Nova
 
 */
public class Main {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            agrandarFuentesPorDefecto(4f); // letra mas grande en toda la interfaz (botones, dialogos, etc.)

            JOptionPane.showMessageDialog(null,
                    "Bienvenido a Ciudad Nova.\n\n"
                    + "Faltan pocos dias para elegir al proximo alcalde. Los ciudadanos\n"
                    + "usan la red social Civitas para informarse... pero tambien circulan\n"
                    + "rumores, noticias falsas y ataques entre candidatos.\n\n"
                    + "Cada jugador debera decidir que hacer con lo que ve en su muro:\n"
                    + "¿Compartir? ¿Verificar? ¿Ignorar? ¿Reportar?\n"
                    + "Sus decisiones cambiaran el destino de la ciudad.",
                    "Alcalde Digital", JOptionPane.PLAIN_MESSAGE);

            new GameController().iniciarPartida();
        });
    }

    // Tamaño de la letra
    private static void agrandarFuentesPorDefecto(float incremento) {
        UIDefaults defaults = UIManager.getLookAndFeelDefaults();
        Enumeration<Object> claves = defaults.keys();
        while (claves.hasMoreElements()) {
            Object clave = claves.nextElement();
            Object valor = defaults.get(clave);
            if (valor instanceof FontUIResource) {
                FontUIResource fuente = (FontUIResource) valor;
                UIManager.put(clave, new FontUIResource(fuente.deriveFont(fuente.getSize2D() + incremento)));
            }
        }
    }
}
