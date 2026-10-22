package com.chatapp.client.model;

import java.util.List;

/**
 * Events reported by {@link ChatClient}. This interface is the only link between the model
 * and a user interface: the JavaFX controller and the console client both implement it.
 * Callbacks arrive on the network thread.
 */
public interface ClientListener {

    /** The server accepted the JOIN. {@code readOnly} is true when no username was given. */
    default void onWelcome(String username, boolean readOnly, String color) {
    }

    default void onMessage(ChatMessage message) {
    }

    /** Answer to the "allUsers" command. */
    default void onUserList(List<String> users, int readOnlyCount) {
    }

    /** Server notice, e.g. "ali joined the chat". */
    default void onInfo(String text) {
    }

    default void onError(String text) {
    }

    /** The connection is closed; {@code reason} says whether the user left or it was lost. */
    default void onDisconnected(String reason) {
    }
}
