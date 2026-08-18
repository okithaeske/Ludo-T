package test;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import shared.Command;
import shared.ProtocolCodec;
import shared.ProtocolException;
import shared.Request;
import shared.Response;

import java.io.IOException;
import java.io.Serializable;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@DisplayName("ProtocolCodec")
class ProtocolCodecTest {

    private ServerSocket listener;
    private ProtocolCodec clientSide;
    private ProtocolCodec serverSide;
    private ExecutorService pool;

    /** A deliberately mutable frame, for the stream-reset test below. */
    private static final class MutableFrame implements Serializable {

        private static final long serialVersionUID = 1L;

        private int value;

        MutableFrame(int value) {
            this.value = value;
        }
    }

    private void connect() throws Exception {
        listener = new ServerSocket(0);
        pool = Executors.newFixedThreadPool(2);

        // Both ends must build their output stream first or they deadlock in the handshake,
        // so the accept side has to run concurrently with the connect side.
        Future<ProtocolCodec> accepted = pool.submit(() -> {
            Socket socket = listener.accept();
            return ProtocolCodec.open(socket);
        });

        Socket clientSocket = new Socket("localhost", listener.getLocalPort());
        clientSide = ProtocolCodec.open(clientSocket);
        serverSide = accepted.get(10, TimeUnit.SECONDS);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (clientSide != null) clientSide.close();
        if (serverSide != null) serverSide.close();
        if (listener != null) listener.close();
        if (pool != null) pool.shutdownNow();
    }

    @Test
    @DisplayName("should_completeHandshake_when_bothPeersOpenConcurrently")
    void should_completeHandshake_when_bothPeersOpenConcurrently() throws Exception {
        // A deadlock here would hang rather than fail, so the test's own timeout is the guard.
        Assertions.assertTimeoutPreemptively(java.time.Duration.ofSeconds(10), this::connect,
                "Handshake deadlocked — both peers must create the output stream first");
    }

    @Test
    @DisplayName("should_roundTripRequestAndResponse_when_sentBetweenPeers")
    void should_roundTripRequestAndResponse_when_sentBetweenPeers() throws Exception {
        // Arrange
        connect();

        // Act — client asks, server answers
        clientSide.write(Request.of(7L, Command.CREATE_GAME, "mode", "LUDO_T"));
        Request received = serverSide.read(Request.class);
        serverSide.write(Response.ok(received.getId(), "created"));
        Response reply = clientSide.read(Response.class);

        // Assert
        Assertions.assertEquals(7L, received.getId());
        Assertions.assertEquals(Command.CREATE_GAME, received.getCommand());
        Assertions.assertEquals("LUDO_T", received.param("mode"));
        Assertions.assertEquals(7L, reply.getRequestId());
        Assertions.assertTrue(reply.isOk());
    }

    @Test
    @DisplayName("should_deliverLatestState_when_sameObjectIsSentTwiceAfterMutation")
    void should_deliverLatestState_when_sameObjectIsSentTwiceAfterMutation() throws Exception {
        // Guards the ObjectOutputStream back-reference trap: without reset() between frames,
        // the second write sends a reference to the first, and the receiver would see the
        // original value forever while the sender's object visibly changed. On the wire that
        // showed up as a client whose board never advanced.
        connect();
        MutableFrame frame = new MutableFrame(1);

        // Act
        clientSide.write(frame);
        MutableFrame first = serverSide.read(MutableFrame.class);

        frame.value = 2;
        clientSide.write(frame);
        MutableFrame second = serverSide.read(MutableFrame.class);

        // Assert
        Assertions.assertEquals(1, first.value);
        Assertions.assertEquals(2, second.value, "Stale frame received — codec did not reset the stream");
    }

    @Test
    @DisplayName("should_throwProtocolException_when_frameIsNotTheExpectedType")
    void should_throwProtocolException_when_frameIsNotTheExpectedType() throws Exception {
        // Arrange
        connect();
        clientSide.write(Response.ok(1L, "not a request"));

        // Act / Assert
        Assertions.assertThrows(ProtocolException.class, () -> serverSide.read(Request.class));
    }

    @Test
    @DisplayName("should_preserveOrder_when_manyFramesAreSentBackToBack")
    void should_preserveOrder_when_manyFramesAreSentBackToBack() throws Exception {
        // Arrange
        connect();
        int count = 200;

        // Act — fire without waiting for any reply, as the real client does
        for (int i = 0; i < count; i++) {
            clientSide.write(Request.of(i, Command.PING));
        }

        // Assert — TCP ordering must survive the framing
        List<Long> ids = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            ids.add(serverSide.read(Request.class).getId());
        }
        for (int i = 0; i < count; i++) {
            Assertions.assertEquals(i, ids.get(i));
        }
    }
}
