package com.chatapp.server;

import com.chatapp.server.config.ServerConfig;
import com.chatapp.server.model.ChatServer;
import com.chatapp.server.view.ConsoleServerView;
import com.chatapp.server.view.ServerApp;
import javafx.application.Application;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;

/**
 * Entry point:  {@code java -jar TCPServer.jar}
 *
 * <p>Network settings come from {@code server.properties} (see {@link ServerConfig}).
 * Add {@code --console} to run without a window: the same model, a different view.</p>
 *
 * <p>This launcher intentionally does not extend {@link Application}; that is what lets a
 * plain "fat" JAR start JavaFX from the classpath.</p>
 */
public final class TCPServer {

    private TCPServer() {
    }

    public static void main(String[] args) throws Exception {
        if (Arrays.asList(args).contains("--console")) {
            runConsole();
        } else {
            Application.launch(ServerApp.class, args);
        }
    }

    private static void runConsole() throws Exception {
        ServerConfig config = ServerConfig.load();
        ChatServer server = new ChatServer(config, new ConsoleServerView());
        CountDownLatch stopped = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop();
            stopped.countDown();
        }));
        server.start();
        stopped.await(); // keep running until Ctrl+C
    }
}
