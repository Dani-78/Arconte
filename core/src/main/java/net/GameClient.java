package net;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Conexión de un jugador con el servidor. Un hilo lee los mensajes y los deja en una cola; la vista
 * (el hilo de render de LibGDX) los saca con {@link #siguiente()} una vez por cuadro, sin bloquearse.
 * Cuando se cae la conexión llega un mensaje local "CLOSED".
 */
public final class GameClient implements AutoCloseable {

    private final Socket socket;
    private final BufferedWriter salida;
    private final Queue<Msg> entrada = new ConcurrentLinkedQueue<>();
    private volatile boolean conectado = true;

    /** Se conecta (bloquea hasta timeoutMs). Llamar fuera del hilo de render. */
    public static GameClient conectar(String host, int puerto, int timeoutMs) throws IOException {
        Socket s = new Socket();
        try {
            s.connect(new InetSocketAddress(host, puerto), timeoutMs);
            s.setTcpNoDelay(true);
            return new GameClient(s);
        } catch (IOException e) {
            try {
                s.close();
            } catch (IOException ignorada) {
                // nada que hacer
            }
            throw e;
        }
    }

    private GameClient(Socket socket) throws IOException {
        this.socket = socket;
        this.salida = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        Thread lector = new Thread(this::leer, "arconte-cliente");
        lector.setDaemon(true);
        lector.start();
    }

    private void leer() {
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
            String linea;
            while ((linea = r.readLine()) != null) entrada.add(Msg.decode(linea));
        } catch (IOException ignorada) {
            // se cayó la conexión
        } finally {
            conectado = false;
            entrada.add(Msg.of("CLOSED"));
        }
    }

    public synchronized void enviar(Msg m) {
        if (!conectado) return;
        try {
            salida.write(m.encode());
            salida.write('\n');
            salida.flush();
        } catch (IOException e) {
            conectado = false;
        }
    }

    /** Siguiente mensaje recibido, o null si no hay ninguno. */
    public Msg siguiente() {
        return entrada.poll();
    }

    public boolean estaConectado() {
        return conectado;
    }

    @Override
    public void close() {
        conectado = false;
        try {
            socket.close();
        } catch (IOException ignorada) {
            // ya estaba cerrado
        }
    }
}
