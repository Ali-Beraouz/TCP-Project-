package com.chatapp.client.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * Where the client connects to.
 *
 * <p>Resolution order:</p>
 * <ol>
 *   <li>command-line arguments: {@code java -jar TCPClient.jar <ServerIPAddress> <PortNumber>}</li>
 *   <li>if no arguments are given, {@code client.properties}: the file named by
 *       {@code -Dclient.config=...}, else {@code ./client.properties} next to the JAR,
 *       else the default packaged inside the JAR</li>
 * </ol>
 *
 * @param host   server IP address or host name
 * @param port   server TCP port
 * @param source where the values came from (shown to the user)
 */
public record ClientConfig(String host, int port, String source) {

    public static final String USAGE =
            "Usage: java -jar TCPClient.jar <ServerIPAddress> <PortNumber>   (e.g. java -jar TCPClient.jar localhost 3000)";
    public static final String FILE_NAME = "client.properties";
    public static final String CONFIG_PROPERTY = "client.config";
    public static final String KEY_HOST = "server.host";
    public static final String KEY_PORT = "server.port";
    public static final String DEFAULT_HOST = "localhost";
    public static final int DEFAULT_PORT = 3000;

    public ClientConfig {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Server IP address must not be empty");
        }
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException("Port must be between 1 and 65535, got " + port);
        }
        host = host.trim();
    }

    /** Arguments that are not flags (flags start with "--", e.g. --console). */
    public static List<String> positional(String[] args) {
        return Arrays.stream(args).filter(a -> !a.startsWith("--")).toList();
    }

    public static ClientConfig resolve(List<String> positionalArgs) {
        return switch (positionalArgs.size()) {
            case 2 -> new ClientConfig(positionalArgs.get(0), parsePort(positionalArgs.get(1), "command line"), "command line");
            case 0 -> load();
            default -> throw new IllegalArgumentException("Expected 2 arguments, got " + positionalArgs.size());
        };
    }

    public static ClientConfig load() {
        String explicit = System.getProperty(CONFIG_PROPERTY);
        if (explicit != null && !explicit.isBlank()) {
            return fromFile(Path.of(explicit));
        }
        Path local = Path.of(FILE_NAME);
        if (Files.isRegularFile(local)) {
            return fromFile(local);
        }
        try (InputStream in = ClientConfig.class.getResourceAsStream("/" + FILE_NAME)) {
            if (in != null) {
                Properties props = new Properties();
                props.load(in);
                return fromProperties(props, "built-in " + FILE_NAME);
            }
        } catch (IOException e) {
            // fall through to defaults
        }
        return new ClientConfig(DEFAULT_HOST, DEFAULT_PORT, "defaults");
    }

    public static ClientConfig fromFile(Path file) {
        Properties props = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            props.load(reader);
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot read configuration file " + file.toAbsolutePath(), e);
        }
        return fromProperties(props, file.toAbsolutePath().normalize().toString());
    }

    public static ClientConfig fromProperties(Properties props, String source) {
        String host = props.getProperty(KEY_HOST, DEFAULT_HOST);
        int port = parsePort(props.getProperty(KEY_PORT, String.valueOf(DEFAULT_PORT)), source);
        return new ClientConfig(host, port, source);
    }

    private static int parsePort(String text, String source) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid port number '" + text + "' (" + source + ")");
        }
    }
}
