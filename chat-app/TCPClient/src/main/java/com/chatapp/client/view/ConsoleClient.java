package com.chatapp.client.view;

import com.chatapp.client.config.ClientConfig;
import com.chatapp.client.model.ChatClient;
import com.chatapp.client.model.ChatMessage;
import com.chatapp.client.model.ClientListener;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Text-mode view of the same {@link ChatClient} model
 * ({@code java -jar TCPClient.jar localhost 3000 --console}).
 * It shows that the model needs no JavaFX: only the view changed.
 */
public final class ConsoleClient implements ClientListener {

    private final ClientConfig config;
    private final ChatClient client = new ChatClient(this);

    public ConsoleClient(ClientConfig config) {
        this.config = config;
    }

    public void run() {
        BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        try {
            System.out.print("Username (leave empty for READ-ONLY mode): ");
            String name = stdin.readLine();
            client.connect(config.host(), config.port(), name == null ? "" : name);

            String line;
            while ((line = stdin.readLine()) != null && client.isConnected()) {
                client.send(line);
            }
            client.leave();
        } catch (IOException e) {
            System.err.println("Cannot reach the server at " + config.host() + ":" + config.port() + " - " + e.getMessage());
            System.exit(1);
        }
    }

    @Override
    public void onWelcome(String username, boolean readOnly, String color) {
        System.out.println(readOnly
                ? "Connected as " + username + " - READ-ONLY MODE (you can read but not send). Type bye to leave."
                : "Welcome " + username + "! Type a message, allUsers to list users, end or bye to leave.");
    }

    @Override
    public void onMessage(ChatMessage m) {
        System.out.println("[" + m.time() + "] " + (m.mine() ? "you" : m.sender()) + ": " + m.text());
    }

    @Override
    public void onUserList(List<String> users, int readOnlyCount) {
        System.out.println("Active users (" + users.size() + "): " + String.join(", ", users)
                + (readOnlyCount > 0 ? "  + " + readOnlyCount + " read-only" : ""));
    }

    @Override
    public void onInfo(String text) {
        System.out.println("* " + text);
    }

    @Override
    public void onError(String text) {
        System.out.println("! " + text);
    }

    @Override
    public void onDisconnected(String reason) {
        System.out.println(reason);
        System.exit(0);
    }
}
