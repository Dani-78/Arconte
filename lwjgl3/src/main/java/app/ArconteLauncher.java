package app;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import view.ArconteGame;

/** Punto de entrada de escritorio de Arconte. Todo el flujo (menú, sala, partida) está dentro del juego. */
public final class ArconteLauncher {
    private ArconteLauncher() {
    }

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Arconte · La voz de Helíade");
        config.setWindowedMode(1366, 900);
        config.useVsync(true);
        config.setForegroundFPS(60);
        new Lwjgl3Application(new ArconteGame(), config);
    }
}
