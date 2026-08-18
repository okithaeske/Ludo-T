package shared;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.Socket;

/**
 * Frames objects onto a socket in both directions.
 *
 * <p><b>Two details here are load-bearing and easy to get wrong.</b>
 *
 * <p><i>Handshake order.</i> Constructing an {@link ObjectOutputStream} writes a short header,
 * and constructing an {@link ObjectInputStream} <em>blocks</em> until it has read the peer's
 * header. If both ends built their input stream first they would deadlock immediately, before
 * a single message was exchanged. {@link #open(Socket)} therefore always builds the output
 * stream, flushes the header, and only then builds the input stream — and both the server and
 * the client go through this one method, so the ordering cannot drift apart.
 *
 * <p><i>Stream reset.</i> {@link ObjectOutputStream} remembers every object it has written and
 * sends a back-reference the next time it sees the same instance. Snapshots are rebuilt each
 * round but the surrounding {@code ServerEvent} shapes repeat, so without {@link
 * ObjectOutputStream#reset()} a client would receive the <em>first</em> board forever while
 * the game visibly advanced on the server. {@link #write} resets after every frame.
 *
 * <p><b>Threading.</b> Not thread-safe by design. A connection confines {@link #read} to its
 * reader thread and {@link #write} to its writer thread, so neither needs a lock; that
 * confinement is the whole reason a slow client cannot stall the game loop.
 */
public final class ProtocolCodec implements Closeable {

    private final Socket socket;
    private final ObjectOutputStream out;
    private final ObjectInputStream in;

    private ProtocolCodec(Socket socket, ObjectOutputStream out, ObjectInputStream in) {
        this.socket = socket;
        this.out = out;
        this.in = in;
    }

    /**
     * Wraps {@code socket}. Both peers must call this — see the handshake note above.
     *
     * @throws IOException if the streams cannot be established
     */
    public static ProtocolCodec open(Socket socket) throws IOException {
        ObjectOutputStream out = new ObjectOutputStream(
                new BufferedOutputStream(socket.getOutputStream()));
        out.flush(); // release our header so the peer's input stream can stop blocking
        ObjectInputStream in = new ObjectInputStream(
                new BufferedInputStream(socket.getInputStream()));
        return new ProtocolCodec(socket, out, in);
    }

    /** Writes one frame and flushes it. Call only from the owning writer thread. */
    public void write(Serializable frame) throws IOException {
        out.writeObject(frame);
        out.reset(); // drop the back-reference table; see the class note
        out.flush();
    }

    /**
     * Reads one frame, blocking until it arrives. Call only from the owning reader thread.
     *
     * @throws EOFException      when the peer closed cleanly
     * @throws ProtocolException when the frame cannot be decoded
     */
    public Object read() throws IOException {
        try {
            return in.readObject();
        } catch (ClassNotFoundException e) {
            throw new ProtocolException("Unknown type on the wire: " + e.getMessage(), e);
        }
    }

    /** Reads one frame and checks its type, so callers do not scatter casts. */
    public <T> T read(Class<T> expected) throws IOException {
        Object frame = read();
        if (!expected.isInstance(frame)) {
            throw new ProtocolException("Expected " + expected.getSimpleName()
                    + " but received " + (frame == null ? "null" : frame.getClass().getName()));
        }
        return expected.cast(frame);
    }

    public Socket getSocket() {
        return socket;
    }

    @Override
    public void close() {
        // Closing the socket is what actually unblocks a peer parked in read().
        closeQuietly(in);
        closeQuietly(out);
        closeQuietly(socket);
    }

    private static void closeQuietly(Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException ignored) {
            // Already torn down, or the peer vanished — nothing useful left to do.
        }
    }
}
