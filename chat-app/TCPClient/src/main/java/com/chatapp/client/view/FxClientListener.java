package com.chatapp.client.view;

import com.chatapp.client.model.ChatMessage;
import com.chatapp.client.model.ClientListener;
import javafx.application.Platform;

import java.util.List;

/**
 * Decorator that moves every model event from the network thread onto the JavaFX
 * Application Thread before passing it to the real listener.
 */
public class FxClientListener implements ClientListener {

    private final ClientListener delegate;

    public FxClientListener(ClientListener delegate) {
        this.delegate = delegate;
    }

    @Override
    public void onWelcome(String username, boolean readOnly, String color) {
        Platform.runLater(() -> delegate.onWelcome(username, readOnly, color));
    }

    @Override
    public void onMessage(ChatMessage message) {
        Platform.runLater(() -> delegate.onMessage(message));
    }

    @Override
    public void onUserList(List<String> users, int readOnlyCount) {
        Platform.runLater(() -> delegate.onUserList(users, readOnlyCount));
    }

    @Override
    public void onInfo(String text) {
        Platform.runLater(() -> delegate.onInfo(text));
    }

    @Override
    public void onError(String text) {
        Platform.runLater(() -> delegate.onError(text));
    }

    @Override
    public void onDisconnected(String reason) {
        Platform.runLater(() -> delegate.onDisconnected(reason));
    }
}
