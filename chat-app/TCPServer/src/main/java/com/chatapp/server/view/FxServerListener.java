package com.chatapp.server.view;

import com.chatapp.server.model.ConnectedUser;
import com.chatapp.server.model.LogEntry;
import com.chatapp.server.model.NetworkInfo;
import com.chatapp.server.model.ServerListener;
import javafx.application.Platform;

/**
 * Adapter between the model and the JavaFX view: receives model events on server threads
 * and replays them on the JavaFX Application Thread.
 */
public class FxServerListener implements ServerListener {

    private final ServerView view;

    public FxServerListener(ServerView view) {
        this.view = view;
    }

    @Override
    public void onLog(LogEntry entry) {
        Platform.runLater(() -> view.appendLog(entry));
    }

    @Override
    public void onServerStarted(String host, int port) {
        var addresses = NetworkInfo.lanAddresses();
        Platform.runLater(() -> view.showStarted(host, port, addresses));
    }

    @Override
    public void onServerStopped() {
        Platform.runLater(view::showStopped);
    }

    @Override
    public void onUserJoined(ConnectedUser user) {
        Platform.runLater(() -> view.addUser(user));
    }

    @Override
    public void onUserLeft(ConnectedUser user) {
        Platform.runLater(() -> view.removeUser(user));
    }

    @Override
    public void onMessageRelayed(ConnectedUser sender, String text) {
        Platform.runLater(view::messageRelayed);
    }
}
