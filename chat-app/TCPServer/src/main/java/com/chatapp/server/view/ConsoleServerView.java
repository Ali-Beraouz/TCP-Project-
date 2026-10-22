package com.chatapp.server.view;

import com.chatapp.server.model.LogEntry;
import com.chatapp.server.model.ServerListener;

/**
 * A second, text-only view of the same server model ({@code java -jar TCPServer.jar --console}).
 * It exists to show that the model works without JavaFX: swapping the view required no
 * change to {@code ChatServer}.
 */
public class ConsoleServerView implements ServerListener {

    @Override
    public void onLog(LogEntry entry) {
        System.out.printf("[%s] %-7s %s%n", entry.time(), entry.level(), entry.message());
    }
}
