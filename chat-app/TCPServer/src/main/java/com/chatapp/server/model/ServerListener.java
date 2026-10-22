package com.chatapp.server.model;

/**
 * Callback interface through which the model reports what happens.
 *
 * <p>This is the only bridge between {@link ChatServer} and any user interface. The model
 * never imports JavaFX; a view implements this interface (the JavaFX window, or the
 * console logger) and decides how to display the events. Callbacks arrive on server
 * worker threads, so a GUI must hand them to its own UI thread.</p>
 */
public interface ServerListener {

    void onLog(LogEntry entry);

    default void onServerStarted(String host, int port) {
    }

    default void onServerStopped() {
    }

    default void onUserJoined(ConnectedUser user) {
    }

    default void onUserLeft(ConnectedUser user) {
    }

    default void onMessageRelayed(ConnectedUser sender, String text) {
    }
}
