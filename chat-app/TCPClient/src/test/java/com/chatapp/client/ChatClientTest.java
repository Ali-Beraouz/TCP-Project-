package com.chatapp.client;

import com.chatapp.client.model.ChatClient;
import com.chatapp.client.model.ChatClient.SendResult;
import com.chatapp.client.model.ChatMessage;
import com.chatapp.client.model.ClientListener;
import com.chatapp.client.protocol.Protocol;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the client model against a tiny fake server socket - no JavaFX involved,
 * which shows the model works independently of the view.
 */
class ChatClientTest {

    private ServerSocket fakeServer;
    private Socket serverSide;
    private BufferedReader fromClient;
    private BufferedWriter toClient;
    private final Events events = new Events();
    private ChatClient client;

    @BeforeEach
    void setUp() throws IOException {
        fakeServer = new ServerSocket(0);
        client = new ChatClient(events);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (serverSide != null) {
            serverSide.close();
        }
        fakeServer.close();
    }

    @Test
    void connectSendsJoinWithUsernameAndHandlesWelcome() throws Exception {
        connect("youness");
        assertEquals("JOIN|youness", fromClient.readLine());

        reply("WELCOME|youness|FULL|#AABBCC");
        assertEquals("welcome youness readOnly=false #AABBCC", events.next());
        assertFalse(client.isReadOnly());
        assertEquals("youness", client.getUsername());
    }

    @Test
    void messagesAreEscapedAndCommandsAreTranslated() throws Exception {
        connect("youness");
        fromClient.readLine();
        reply("WELCOME|youness|FULL|#AABBCC");
        events.next();

        assertEquals(SendResult.SENT, client.send("hello\nworld"));
        assertEquals("MSG|hello\\nworld", fromClient.readLine());

        assertEquals(SendResult.COMMAND, client.send("allUsers"));
        assertEquals("USERS", fromClient.readLine());

        assertEquals(SendResult.EMPTY, client.send("   "));
    }

    @Test
    void serverFramesBecomeEvents() throws Exception {
        connect("youness");
        fromClient.readLine();
        reply("WELCOME|youness|FULL|#AABBCC");
        events.next();

        reply("CHAT|10:42|ali|#112233|hi | there\\nsecond line");
        assertEquals("message 10:42 ali mine=false text=hi | there\nsecond line", events.next());

        reply("CHAT|10:43|youness|#AABBCC|my own");
        assertEquals("message 10:43 youness mine=true text=my own", events.next());

        reply("USERLIST|ali,youness|2");
        assertEquals("users [ali, youness] readOnly=2", events.next());

        reply("INFO|ali joined the chat");
        assertEquals("info ali joined the chat", events.next());
    }

    @Test
    void readOnlyUserCannotSendMessages() throws Exception {
        connect("");
        assertEquals("JOIN|", fromClient.readLine());
        reply("WELCOME|Guest-1|READONLY|#AABBCC");
        assertEquals("welcome Guest-1 readOnly=true #AABBCC", events.next());

        assertEquals(SendResult.BLOCKED_READ_ONLY, client.send("let me talk"));
        assertTrue(events.next().startsWith("error READ-ONLY MODE"));

        assertEquals(SendResult.COMMAND, client.send("allUsers")); // queries are still allowed
        assertEquals("USERS", fromClient.readLine());
    }

    @Test
    void byeNotifiesServerAndDisconnects() throws Exception {
        connect("youness");
        fromClient.readLine();
        reply("WELCOME|youness|FULL|#AABBCC");
        events.next();

        assertEquals(SendResult.COMMAND, client.send("bye"));
        assertEquals(Protocol.BYE, fromClient.readLine());
        serverSide.close(); // the real server closes after BYE
        assertEquals("disconnected You left the chat.", events.next());
        assertFalse(client.isConnected());
        assertEquals(SendResult.NOT_CONNECTED, client.send("anyone?"));
    }

    @Test
    void lostConnectionIsReported() throws Exception {
        connect("youness");
        fromClient.readLine();
        serverSide.close();
        assertEquals("disconnected Connection to the server was lost.", events.next());
    }

    // ---------------------------------------------------------------- helpers

    private void connect(String username) throws IOException {
        client.connect("127.0.0.1", fakeServer.getLocalPort(), username);
        serverSide = fakeServer.accept();
        fromClient = new BufferedReader(new InputStreamReader(serverSide.getInputStream(), StandardCharsets.UTF_8));
        toClient = new BufferedWriter(new OutputStreamWriter(serverSide.getOutputStream(), StandardCharsets.UTF_8));
    }

    private void reply(String line) throws IOException {
        toClient.write(line);
        toClient.newLine();
        toClient.flush();
    }

    /** Records listener callbacks as strings, in arrival order. */
    private static final class Events implements ClientListener {
        private final BlockingQueue<String> queue = new LinkedBlockingQueue<>();

        String next() throws InterruptedException {
            String event = queue.poll(5, TimeUnit.SECONDS);
            if (event == null) {
                throw new AssertionError("no event received");
            }
            return event;
        }

        @Override
        public void onWelcome(String username, boolean readOnly, String color) {
            queue.add("welcome " + username + " readOnly=" + readOnly + " " + color);
        }

        @Override
        public void onMessage(ChatMessage m) {
            queue.add("message " + m.time() + " " + m.sender() + " mine=" + m.mine() + " text=" + m.text());
        }

        @Override
        public void onUserList(List<String> users, int readOnlyCount) {
            queue.add("users " + users + " readOnly=" + readOnlyCount);
        }

        @Override
        public void onInfo(String text) {
            queue.add("info " + text);
        }

        @Override
        public void onError(String text) {
            queue.add("error " + text);
        }

        @Override
        public void onDisconnected(String reason) {
            queue.add("disconnected " + reason);
        }
    }
}
