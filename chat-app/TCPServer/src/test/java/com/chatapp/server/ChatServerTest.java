package com.chatapp.server;

import com.chatapp.server.config.ServerConfig;
import com.chatapp.server.model.ChatServer;
import com.chatapp.server.model.ConnectedUser;
import com.chatapp.server.model.LogEntry;
import com.chatapp.server.model.ServerListener;
import com.chatapp.server.protocol.Packet;
import com.chatapp.server.protocol.Protocol;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests: a real ChatServer on a free port, real TCP sockets, no JavaFX.
 * They pass without any view, which demonstrates the model/view separation.
 */
class ChatServerTest {

    private final RecordingListener events = new RecordingListener();
    private final List<TestClient> clients = new ArrayList<>();
    private ChatServer server;

    @BeforeEach
    void startServer() throws Exception {
        server = new ChatServer(new ServerConfig("127.0.0.1", 0, "test"), events);
        server.start();
    }

    @AfterEach
    void stopServer() throws Exception {
        for (TestClient c : clients) {
            c.close();
        }
        server.stop();
    }

    @Test
    void serverLogsStartupAndWaitsForClients() throws Exception {
        join("alice");
        assertTrue(events.logged("Server Started"));
        assertTrue(events.logged("Waiting for Client"));
        waitUntil(() -> events.logged("Welcome alice"));
    }

    @Test
    void messageIsFormattedWithSenderAndTimeAndSentToAllActiveConnections() throws Exception {
        TestClient alice = join("alice");
        TestClient bob = join("bob");

        alice.send("MSG|hello everyone");

        for (TestClient receiver : List.of(alice, bob)) {
            String[] f = receiver.next(Protocol.CHAT).fields(4);
            assertTrue(f[0].matches("\\d{2}:\\d{2}"), "time must be HH:mm, was " + f[0]);
            assertEquals("alice", f[1]);
            assertTrue(f[2].matches("#[0-9A-F]{6}"), "colour must be #RRGGBB, was " + f[2]);
            assertEquals("hello everyone", f[3]);
        }
    }

    @Test
    void userWithoutUsernameIsReadOnlyAndCannotSend() throws Exception {
        TestClient alice = join("alice");
        TestClient guest = connect();
        guest.send("JOIN|");

        String[] welcome = guest.next(Protocol.WELCOME).fields(3);
        assertTrue(welcome[0].startsWith("Guest-"));
        assertEquals(Protocol.MODE_READ_ONLY, welcome[1]);

        guest.send("MSG|can anyone hear me?");
        assertTrue(guest.next(Protocol.ERROR).payload().contains("READ-ONLY"));
        assertTrue(alice.receivesNo(Protocol.CHAT, 300), "read-only messages must not be relayed");

        alice.send("MSG|read-only users still receive messages");
        assertEquals("read-only users still receive messages", guest.next(Protocol.CHAT).fields(4)[3]);
    }

    @Test
    void allUsersCommandAnswersOnlyTheRequester() throws Exception {
        TestClient alice = join("alice");
        TestClient bob = join("bob");
        TestClient guest = connect();
        guest.send("JOIN|");
        guest.next(Protocol.WELCOME);

        bob.send("MSG|allUsers");
        String[] f = bob.next(Protocol.USER_LIST).fields(2);
        assertEquals("alice,bob", f[0]);
        assertEquals("1", f[1]);
        assertTrue(alice.receivesNo(Protocol.USER_LIST, 300));

        alice.send(Protocol.USERS); // dedicated frame used by the GUI button
        assertEquals("alice,bob", alice.next(Protocol.USER_LIST).fields(2)[0]);
    }

    @Test
    void duplicateUsernamesAreMadeUnique() throws Exception {
        TestClient first = connect();
        first.send("JOIN|sam");
        assertEquals("sam", first.next(Protocol.WELCOME).fields(3)[0]);
        TestClient second = connect();
        second.send("JOIN|SAM");
        assertEquals("SAM2", second.next(Protocol.WELCOME).fields(3)[0]);
    }

    @Test
    void byeDisconnectsAndNotifiesOthers() throws Exception {
        TestClient alice = join("alice");
        TestClient bob = join("bob");

        bob.send(Protocol.BYE);
        assertTrue(bob.waitForClose(), "server must close the connection after BYE");
        assertEquals("bob joined the chat", alice.next(Protocol.INFO).payload());
        assertEquals("bob left the chat", alice.next(Protocol.INFO).payload());
        waitUntil(() -> server.getActiveUsers().size() == 1);
        assertEquals("alice", server.getActiveUsers().get(0).name());
        assertTrue(events.left.stream().anyMatch(u -> u.name().equals("bob")));
    }

    @Test
    void typingEndOrByeAsAMessageAlsoDisconnects() throws Exception {
        TestClient alice = join("alice");
        alice.send("MSG|end");
        assertTrue(alice.waitForClose());
        TestClient bob = join("bob");
        bob.send("MSG|bye");
        assertTrue(bob.waitForClose());
        waitUntil(() -> server.getActiveUsers().isEmpty());
    }

    @Test
    void multilineTextAndPipesSurviveTheRoundTrip() throws Exception {
        TestClient alice = join("alice");
        String text = "first line\nsecond | line";
        alice.send("MSG|" + Protocol.escape(text));
        assertEquals(text, Protocol.unescape(alice.next(Protocol.CHAT).fields(4)[3]));
    }

    @Test
    void handlesManyConcurrentClients() throws Exception {
        int count = 50;
        List<TestClient> group = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            group.add(join("user" + i));
        }
        CountDownLatch sent = new CountDownLatch(count);
        for (int i = 0; i < count; i++) {
            TestClient c = group.get(i);
            int n = i;
            Thread.ofVirtual().start(() -> {
                try {
                    c.send("MSG|message " + n);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                sent.countDown();
            });
        }
        assertTrue(sent.await(5, TimeUnit.SECONDS));
        for (TestClient c : group) {
            for (int i = 0; i < count; i++) {
                c.next(Protocol.CHAT); // every client receives all 50 messages
            }
        }
        assertEquals(count, server.getActiveUsers().size());
    }

    @Test
    void stoppingTheServerNotifiesAndDisconnectsClients() throws Exception {
        TestClient alice = join("alice");
        server.stop();
        assertTrue(alice.next(Protocol.INFO).payload().contains("shutting down"));
        assertTrue(alice.waitForClose());
        assertTrue(events.logged("Server Stopped"));
    }

    // ---------------------------------------------------------------- helpers

    private TestClient connect() throws Exception {
        TestClient c = new TestClient(server.getPort());
        clients.add(c);
        return c;
    }

    private TestClient join(String name) throws Exception {
        TestClient c = connect();
        c.send("JOIN|" + name);
        Packet welcome = c.next(Protocol.WELCOME);
        assertEquals(name, welcome.fields(3)[0]);
        assertEquals(Protocol.MODE_FULL, welcome.fields(3)[1]);
        return c;
    }

    private static void waitUntil(java.util.function.BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("condition not met within 5 s");
            }
            Thread.sleep(20);
        }
    }

    private static final class RecordingListener implements ServerListener {
        final List<LogEntry> log = new CopyOnWriteArrayList<>();
        final List<ConnectedUser> left = new CopyOnWriteArrayList<>();

        @Override
        public void onLog(LogEntry entry) {
            log.add(entry);
        }

        @Override
        public void onUserLeft(ConnectedUser user) {
            left.add(user);
        }

        boolean logged(String fragment) {
            return log.stream().anyMatch(e -> e.message().contains(fragment));
        }
    }
}
