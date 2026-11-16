package controller;

import model.GameEngine;
import model.Jugador;
import view.MainFrame;

import java.util.List;

/** Coordina la creación del modelo y la vista al iniciar la aplicación. */
public class GameController {

    public void iniciarPartida() {
        List<Jugador> jugadores = MainFrame.pedirJugadores();
        GameEngine motor = new GameEngine(jugadores);
        MainFrame vista = new MainFrame(motor);
        vista.setVisible(true);
    }
}
