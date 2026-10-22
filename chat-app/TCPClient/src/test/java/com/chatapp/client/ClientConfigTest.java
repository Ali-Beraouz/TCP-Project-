package com.chatapp.client;

import com.chatapp.client.config.ClientConfig;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClientConfigTest {

    @Test
    void commandLineArgumentsGiveHostAndPort() {
        ClientConfig config = ClientConfig.resolve(List.of("192.168.1.20", "3000"));
        assertEquals("192.168.1.20", config.host());
        assertEquals(3000, config.port());
        assertEquals("command line", config.source());
    }

    @Test
    void flagsAreNotPositionalArguments() {
        assertEquals(List.of("localhost", "3000"),
                ClientConfig.positional(new String[]{"localhost", "--console", "3000"}));
    }

    @Test
    void wrongArgumentCountOrPortIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> ClientConfig.resolve(List.of("localhost")));
        assertThrows(IllegalArgumentException.class, () -> ClientConfig.resolve(List.of("localhost", "port")));
        assertThrows(IllegalArgumentException.class, () -> ClientConfig.resolve(List.of("localhost", "0")));
        assertThrows(IllegalArgumentException.class, () -> ClientConfig.resolve(List.of("a", "1", "b")));
    }

    @Test
    void withoutArgumentsTheConfigurationFileIsUsed() throws Exception {
        Path file = Files.createTempFile("client", ".properties");
        try {
            Files.writeString(file, "server.host=10.0.0.7\nserver.port=4000\n");
            System.setProperty(ClientConfig.CONFIG_PROPERTY, file.toString());
            ClientConfig config = ClientConfig.resolve(List.of());
            assertEquals("10.0.0.7", config.host());
            assertEquals(4000, config.port());
        } finally {
            System.clearProperty(ClientConfig.CONFIG_PROPERTY);
            Files.deleteIfExists(file);
        }
    }
}
