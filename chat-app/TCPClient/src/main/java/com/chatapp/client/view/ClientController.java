package com.chatapp.client.view;

import com.chatapp.client.config.ClientConfig;
import com.chatapp.client.model.ChatClient;
import com.chatapp.client.model.ChatClient.SendResult;
import com.chatapp.client.model.ChatMessage;
import com.chatapp.client.model.ClientListener;
import com.chatapp.client.protocol.Protocol;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Objects;

/**
 * Connects the views (login screen, chat screen) to the {@link ChatClient} model.
 * Model events reach this class on the JavaFX thread thanks to {@link FxClientListener}.
 */
public class ClientController implements ClientListener {

    private final ClientConfig config;
    private final Stage stage;
    private final ChatClient client;
    private final LoginView loginView;
    private final Scene scene;
    private ChatView chatView;

    public ClientController(ClientConfig config, Stage stage) {
        this.config = config;
        this.stage = stage;
        this.client = new ChatClient(new FxClientListener(this));
        this.loginView = new LoginView(serverAddress());
        loginView.setOnJoin(this::join);

        scene = new Scene(loginView.getRoot(), 480, 720);
        scene.getStylesheets().add(Objects.requireNonNull(
                getClass().getResource("/css/client.css")).toExternalForm());
    }

    public void show() {
        stage.setTitle("TCP Chat  ·  " + serverAddress());
        stage.setMinWidth(380);
        stage.setMinHeight(520);
        stage.setScene(scene);
        stage.show();
        loginView.focus();
    }

    /** Called when the window closes: tell the server we are leaving. */
    public void shutdown() {
        client.leave();
    }

    // ---------------------------------------------------------------- user actions

    private void join(String requestedName) {
        String name = requestedName == null ? "" : requestedName.strip();
        if (!name.isEmpty() && !Protocol.isValidUsername(name)) {
            loginView.showError("Usernames are 1-20 characters: letters, digits, '.', '_' or '-' (no spaces).");
            return;
        }
        loginView.setBusy(true);
        Thread.ofVirtual().start(() -> {
            try {
                client.connect(config.host(), config.port(), name);
            } catch (IOException e) {
                String message = "Cannot reach the server at " + serverAddress() + ": " + describe(e);
                Platform.runLater(() -> {
                    loginView.setBusy(false);
                    loginView.showError(message);
                });
            }
        });
    }

    private void send(String text) {
        SendResult result = client.send(text);
        switch (result) {
            case SENT, COMMAND, EMPTY -> chatView.clearInput();
            case NOT_CONNECTED -> chatView.addError("You are offline.");
            case BLOCKED_READ_ONLY -> { /* onError already shows the reason */ }
        }
    }

    private void leaveOrClose() {
        if (client.isConnected()) {
            client.leave();
        } else {
            stage.close();
        }
    }

    // ---------------------------------------------------------------- model events (FX thread)

    @Override
    public void onWelcome(String username, boolean readOnly, String color) {
        chatView = new ChatView(username, readOnly, color, serverAddress());
        chatView.setOnSend(this::send);
        chatView.setOnLeave(this::leaveOrClose);
        chatView.setOnShowUsers(client::requestUserList);
        scene.setRoot(chatView.getRoot());
        stage.setTitle("TCP Chat  ·  " + username + (readOnly ? " (read-only)" : ""));
        chatView.addInfo(readOnly
                ? "Connected as " + username + " in READ-ONLY mode"
                : "Welcome " + username + "! You are connected to " + serverAddress());
        chatView.focusInput();
    }

    @Override
    public void onMessage(ChatMessage message) {
        if (chatView != null) {
            chatView.addMessage(message);
        }
    }

    @Override
    public void onUserList(List<String> users, int readOnlyCount) {
        if (chatView != null) {
            chatView.addUserList(users, readOnlyCount);
        }
    }

    @Override
    public void onInfo(String text) {
        if (chatView != null) {
            chatView.addInfo(text);
        }
    }

    @Override
    public void onError(String text) {
        if (chatView != null) {
            chatView.addError(text);
        } else {
            loginView.showError(text);
        }
    }

    @Override
    public void onDisconnected(String reason) {
        if (chatView != null) {
            chatView.showOffline(reason);
        } else {
            loginView.setBusy(false);
            loginView.showError(reason);
        }
    }

    // ---------------------------------------------------------------- helpers

    private String serverAddress() {
        return config.host() + ":" + config.port();
    }

    private static String describe(IOException e) {
        if (e instanceof ConnectException) {
            return "connection refused. Is TCPServer running?";
        }
        if (e instanceof UnknownHostException) {
            return "unknown host.";
        }
        if (e instanceof SocketTimeoutException) {
            return "timed out.";
        }
        return e.getMessage();
    }
}
