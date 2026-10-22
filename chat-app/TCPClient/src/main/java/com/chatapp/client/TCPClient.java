package com.chatapp.client;

import com.chatapp.client.config.ClientConfig;
import com.chatapp.client.view.ClientApp;
import com.chatapp.client.view.ConsoleClient;
import javafx.application.Application;

import java.util.Arrays;

/**
 * Entry point:  {@code java -jar TCPClient.jar <ServerIPAddress> <PortNumber>}
 * e.g. {@code java -jar TCPClient.jar localhost 3000}.
 *
 * <p>Without arguments, the server address is read from {@code client.properties}.
 * Add {@code --console} for the text-mode client (same model, different view).</p>
 *
 * <p>This launcher intentionally does not extend {@link Application}; that is what lets a
 * plain "fat" JAR start JavaFX from the classpath.</p>
 */
public final class TCPClient {

    private TCPClient() {
    }

    public static void main(String[] args) {
        ClientConfig config;
        try {
            config = ClientConfig.resolve(ClientConfig.positional(args));
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            System.err.println(ClientConfig.USAGE);
            System.exit(1);
            return;
        }
        System.out.println("Server: " + config.host() + ":" + config.port() + "  (from " + config.source() + ")");

        if (Arrays.asList(args).contains("--console")) {
            new ConsoleClient(config).run();
        } else {
            Application.launch(ClientApp.class, args);
        }
    }
}
