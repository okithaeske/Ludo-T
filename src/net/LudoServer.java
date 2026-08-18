package net;

import adapter.RequestRouter;
import shared.ProtocolCodec;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Listens for clients and gives each one a pair of threads.
 *
 * <h2>Why accepting has a thread of its own</h2>
 * {@link ServerSocket#accept()} blocks. Handling a new client on the accept thread — even
 * briefly — would mean the server is deaf to further connections for that whole time, so a
 * burst of clients arriving together would be serialised at the door. Here the accept thread
 * does the minimum: complete the handshake, register the connection, hand its reader to a
 * cached pool, and loop straight back to {@code accept()}.
 *
 * <p>The reader pool is <em>cached</em> rather than fixed because a reader thread spends
 * essentially all its life blocked on a socket, not consuming CPU. Sizing that pool to core
 * count would cap how many clients could be connected at once, which is not the same limit as
 * how much work the server can do — that limit belongs to {@link RequestQueue}, and is fixed
 * and bounded there on purpose. Connections are cheap; work is rationed.
 */
public final class LudoServer implements AutoCloseable {

    private final int port;
    private final RequestRouter router;
    private final RequestQueue requestQueue;
    private final ConnectionRegistry connections;

    private final ExecutorService readerPool;
    private final AtomicLong connectionSequence = new AtomicLong();
    private final AtomicBoolean running = new AtomicBoolean();

    private ServerSocket serverSocket;
    private Thread acceptThread;

    public LudoServer(int port, RequestRouter router, RequestQueue requestQueue,
                      ConnectionRegistry connections) {
        this.port = port;
        this.router = router;
        this.requestQueue = requestQueue;
        this.connections = connections;
        this.readerPool = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "client-reader");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** Binds the port and starts accepting. Returns as soon as the server is listening. */
    public void start() throws IOException {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        serverSocket = new ServerSocket(port);
        acceptThread = new Thread(this::acceptLoop, "accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    /** The bound port — useful when the server was started on port 0 in a test. */
    public int getPort() {
        return serverSocket == null ? port : serverSocket.getLocalPort();
    }

    public boolean isRunning() {
        return running.get();
    }

    private void acceptLoop() {
        while (running.get()) {
            try {
                Socket socket = serverSocket.accept();
                accept(socket);
            } catch (IOException e) {
                if (running.get()) {
                    System.out.println("[server] accept failed: " + e.getMessage());
                }
                // A closed server socket during shutdown lands here; the loop condition exits.
            }
        }
    }

    private void accept(Socket socket) {
        String connectionId = "c" + connectionSequence.incrementAndGet();
        try {
            // Nagle's algorithm would coalesce small frames and add latency to exactly the
            // pushes that are supposed to feel immediate on another client's screen.
            socket.setTcpNoDelay(true);

            ProtocolCodec codec = ProtocolCodec.open(socket);
            ClientConnection connection = new ClientConnection(
                    connectionId, socket, codec, router, requestQueue, connections);
            connections.add(connection);
            connection.startWriter();
            readerPool.execute(connection);
            System.out.println("[server] " + connectionId + " connected from "
                    + socket.getRemoteSocketAddress());
        } catch (IOException e) {
            System.out.println("[server] handshake with " + connectionId
                    + " failed: " + e.getMessage());
            closeQuietly(socket);
        }
    }

    @Override
    public void close() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        // Closing the server socket is what breaks the accept thread out of accept().
        if (serverSocket != null) {
            closeQuietly(serverSocket);
        }
        connections.closeAll();
        readerPool.shutdownNow();
        if (acceptThread != null) {
            acceptThread.interrupt();
        }
    }

    private static void closeQuietly(AutoCloseable closeable) {
        try {
            closeable.close();
        } catch (Exception ignored) {
            // Shutting down; a failure to close changes nothing.
        }
    }
}
