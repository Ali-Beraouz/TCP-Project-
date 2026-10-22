package com.chatapp.server.view;

import com.chatapp.server.config.ServerConfig;
import com.chatapp.server.model.ChatServer;
import com.chatapp.server.model.LogEntry;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.Objects;

/**
 * JavaFX application: wires the {@link ServerView} to the {@link ChatServer} model.
 * The server starts automatically when the window opens.
 */
public class ServerApp extends Application {

    private ChatServer server;
    private ServerView view;

    @Override
    public void start(Stage stage) {
        view = new ServerView();

        ServerConfig config;
        try {
            config = ServerConfig.load();
        } catch (RuntimeException e) {
            view.appendLog(LogEntry.of(LogEntry.Level.ERROR, e.getMessage() + " - using defaults"));
            config = new ServerConfig(ServerConfig.DEFAULT_HOST, ServerConfig.DEFAULT_PORT, "defaults");
        }

        server = new ChatServer(config, new FxServerListener(view));
        view.setOnToggle(this::toggleServer);

        Scene scene = new Scene(view.getRoot(), 1000, 640);
        scene.getStylesheets().add(Objects.requireNonNull(
                getClass().getResource("/css/server.css")).toExternalForm());
        stage.setTitle("TCP Chat Server");
        stage.setMinWidth(780);
        stage.setMinHeight(480);
        stage.setScene(scene);
        stage.show();

        startServer();
    }

    @Override
    public void stop() {
        if (server != null) {
            server.stop();
        }
    }

    private void toggleServer() {
        view.showBusy();
        if (server.isRunning()) {
            // stop() waits briefly for client threads, so keep it off the UI thread
            Thread.ofVirtual().start(server::stop);
        } else {
            startServer();
        }
    }

    private void startServer() {
        try {
            server.start();
        } catch (IOException e) {
            view.appendLog(LogEntry.of(LogEntry.Level.ERROR, "Could not start on port "
                    + server.getConfig().port() + ": " + e.getMessage()
                    + ". Is another server already running? Change server.port in server.properties."));
            view.showStopped();
        }
    }
}
