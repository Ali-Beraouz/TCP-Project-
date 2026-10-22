package com.chatapp.client.view;

import javafx.beans.binding.Bindings;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;
import javafx.scene.shape.SVGPath;

import java.util.function.Consumer;

/**
 * First screen: the user enters a username before reaching the chat.
 * An empty username joins in READ-ONLY mode.
 */
public class LoginView {

    private final GridPane root = new GridPane();
    private final TextField usernameField = new TextField();
    private final Button joinButton = new Button();
    private final Label errorLabel = new Label();
    private Consumer<String> onJoin = name -> { };

    public LoginView(String serverAddress) {
        root.getStyleClass().add("login-root");
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(24));

        // ----- the card is itself a GridPane: one column, one row per element
        GridPane card = new GridPane();
        card.getStyleClass().add("login-card");
        card.setVgap(10);
        card.setMaxWidth(360);
        ColumnConstraints column = new ColumnConstraints();
        column.setHgrow(Priority.ALWAYS);
        column.setFillWidth(true);
        card.getColumnConstraints().add(column);

        Circle badge = new Circle(30);
        badge.getStyleClass().add("logo-circle");
        SVGPath bubble = new SVGPath();
        bubble.setContent("M3 2 H21 A3 3 0 0 1 24 5 V15 A3 3 0 0 1 21 18 H10 L4 23 V18 H3 A3 3 0 0 1 0 15 V5 A3 3 0 0 1 3 2 Z");
        bubble.getStyleClass().add("logo-icon");
        StackPane logo = new StackPane(badge, bubble);
        GridPane.setHalignment(logo, HPos.CENTER);

        Label title = new Label("Group Chat");
        title.getStyleClass().add("login-title");
        GridPane.setHalignment(title, HPos.CENTER);

        Label server = new Label("Server  " + serverAddress);
        server.getStyleClass().add("login-server");
        GridPane.setHalignment(server, HPos.CENTER);

        Label fieldLabel = new Label("Username");
        fieldLabel.getStyleClass().add("field-label");
        usernameField.setPromptText("e.g. youness");
        usernameField.getStyleClass().add("username-field");
        usernameField.textProperty().addListener((obs, old, now) -> showError(null));

        Label hint = new Label("Leave it empty to join in READ-ONLY mode (you can read, not send).");
        hint.getStyleClass().add("login-hint");
        hint.setWrapText(true);

        joinButton.getStyleClass().add("primary-button");
        joinButton.setDefaultButton(true);
        joinButton.setMaxWidth(Double.MAX_VALUE);
        joinButton.textProperty().bind(Bindings.when(usernameField.textProperty().isEmpty())
                .then("Join as read-only").otherwise("Join chat"));
        joinButton.setOnAction(e -> onJoin.accept(usernameField.getText()));

        errorLabel.getStyleClass().add("login-error");
        errorLabel.setWrapText(true);
        errorLabel.managedProperty().bind(errorLabel.visibleProperty());
        errorLabel.setVisible(false);

        Region gap = new Region();
        gap.setMinHeight(6);

        card.addColumn(0, logo, title, server, gap, fieldLabel, usernameField, hint, joinButton, errorLabel);
        root.add(card, 0, 0);
    }

    public Region getRoot() {
        return root;
    }

    public void setOnJoin(Consumer<String> handler) {
        this.onJoin = handler;
    }

    public void focus() {
        usernameField.requestFocus();
    }

    public void setBusy(boolean busy) {
        usernameField.setDisable(busy);
        joinButton.setDisable(busy);
        if (busy) {
            joinButton.textProperty().unbind();
            joinButton.setText("Connecting...");
        } else {
            joinButton.textProperty().bind(Bindings.when(usernameField.textProperty().isEmpty())
                    .then("Join as read-only").otherwise("Join chat"));
        }
    }

    public void showError(String message) {
        boolean show = message != null && !message.isBlank();
        errorLabel.setText(show ? message : "");
        errorLabel.setVisible(show);
    }
}
