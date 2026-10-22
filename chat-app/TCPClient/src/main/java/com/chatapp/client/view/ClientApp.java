package com.chatapp.client.view;

import com.chatapp.client.config.ClientConfig;
import javafx.application.Application;
import javafx.stage.Stage;

/** JavaFX application: resolves the configuration and hands the window to the controller. */
public class ClientApp extends Application {

    private ClientController controller;

    @Override
    public void start(Stage stage) {
        ClientConfig config = ClientConfig.resolve(ClientConfig.positional(
                getParameters().getRaw().toArray(String[]::new)));
        controller = new ClientController(config, stage);
        controller.show();
    }

    @Override
    public void stop() {
        if (controller != null) {
            controller.shutdown();
        }
    }
}
