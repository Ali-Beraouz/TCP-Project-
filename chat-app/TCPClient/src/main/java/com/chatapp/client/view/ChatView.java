package com.chatapp.client.view;

import com.chatapp.client.model.ChatMessage;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Main chat screen, laid out with a {@link GridPane}:
 * <pre>
 *  row 0  header: avatar, username, "Who's online", Online/Offline status (label + circle)
 *  row 1  READ-ONLY MODE banner (only for users without a username)
 *  row 2  scrollable conversation
 *  row 3  text area + SEND + LEAVE
 *  row 4  keyboard / command hint
 * </pre>
 * Pure view: it raises callbacks and renders what the controller gives it.
 */
public class ChatView {

    private final GridPane root = new GridPane();
    private final VBox messages = new VBox(8);
    private final ScrollPane scroll = new ScrollPane(messages);
    private final TextArea input = new TextArea();
    private final Button sendButton = new Button("SEND");
    private final Button leaveButton = new Button("LEAVE");
    private final Button usersButton = new Button("Who's online");
    private final Circle statusDot = new Circle(5);
    private final Label statusLabel = new Label("Online");
    private final boolean readOnly;

    private Consumer<String> onSend = text -> { };
    private Runnable onLeave = () -> { };
    private Runnable onShowUsers = () -> { };

    public ChatView(String username, boolean readOnly, String color, String serverAddress) {
        this.readOnly = readOnly;
        root.getStyleClass().add("chat-root");

        ColumnConstraints column = new ColumnConstraints();
        column.setHgrow(Priority.ALWAYS);
        column.setFillWidth(true);
        root.getColumnConstraints().add(column);

        RowConstraints header = new RowConstraints();
        RowConstraints banner = new RowConstraints();
        RowConstraints conversation = new RowConstraints();
        conversation.setVgrow(Priority.ALWAYS);
        RowConstraints inputRow = new RowConstraints();
        RowConstraints hintRow = new RowConstraints();
        root.getRowConstraints().addAll(header, banner, conversation, inputRow, hintRow);

        root.add(buildHeader(username, color, serverAddress), 0, 0);
        if (readOnly) {
            Label readOnlyBanner = new Label("READ-ONLY MODE  ·  You joined without a username, so you can read "
                    + "the conversation but cannot send messages.");
            readOnlyBanner.getStyleClass().add("readonly-banner");
            readOnlyBanner.setWrapText(true);
            readOnlyBanner.setMaxWidth(Double.MAX_VALUE);
            root.add(readOnlyBanner, 0, 1);
        }
        root.add(buildConversation(), 0, 2);
        root.add(buildInputBar(), 0, 3);

        Label hint = new Label(readOnly
                ? "Use Who's online to list active users, and LEAVE to disconnect."
                : "Enter to send  ·  Shift+Enter for a new line  ·  Commands: allUsers, end, bye");
        hint.getStyleClass().add("hint");
        hint.setMaxWidth(Double.MAX_VALUE);
        root.add(hint, 0, 4);

        setOnline(true);
    }

    public Region getRoot() {
        return root;
    }

    public void setOnSend(Consumer<String> handler) {
        this.onSend = handler;
    }

    public void setOnLeave(Runnable handler) {
        this.onLeave = handler;
    }

    public void setOnShowUsers(Runnable handler) {
        this.onShowUsers = handler;
    }

    public void focusInput() {
        if (!readOnly) {
            input.requestFocus();
        }
    }

    public void clearInput() {
        input.clear();
    }

    // ---------------------------------------------------------------- rendering

    public void addMessage(ChatMessage m) {
        VBox bubble = new VBox(3);
        bubble.getStyleClass().addAll("bubble", m.mine() ? "bubble-mine" : "bubble-other");
        bubble.maxWidthProperty().bind(scroll.widthProperty().multiply(0.72));

        if (!m.mine()) {
            Label sender = new Label(m.sender());
            sender.getStyleClass().add("bubble-sender");
            sender.setTextFill(darken(m.color()));
            bubble.setStyle("-fx-background-color: " + m.color() + ";");
            bubble.getChildren().add(sender);
        }
        Label text = new Label(m.text());
        text.getStyleClass().add("bubble-text");
        text.setWrapText(true);
        text.setMinHeight(Region.USE_PREF_SIZE);

        Label time = new Label(m.time());
        time.getStyleClass().add("bubble-time");
        time.setMaxWidth(Double.MAX_VALUE);
        time.setAlignment(Pos.CENTER_RIGHT);
        bubble.getChildren().addAll(text, time);

        HBox row = new HBox(bubble);
        row.setAlignment(m.mine() ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        append(row);
    }

    public void addInfo(String text) {
        append(centered(pill(text, "info-pill")));
    }

    public void addError(String text) {
        append(centered(pill(text, "error-pill")));
    }

    /** Renders the answer to "allUsers". */
    public void addUserList(List<String> users, int readOnlyCount) {
        Label title = new Label("Active users  ·  " + users.size());
        title.getStyleClass().add("users-card-title");

        FlowPane chips = new FlowPane(6, 6);
        for (String user : users) {
            Label chip = new Label(user);
            chip.getStyleClass().add("user-chip");
            chips.getChildren().add(chip);
        }
        if (users.isEmpty()) {
            chips.getChildren().add(new Label("Nobody with a username is online."));
        }

        VBox card = new VBox(8, title, chips);
        if (readOnlyCount > 0) {
            Label viewers = new Label("+ " + readOnlyCount + " read-only viewer" + (readOnlyCount > 1 ? "s" : ""));
            viewers.getStyleClass().add("users-card-note");
            card.getChildren().add(viewers);
        }
        card.getStyleClass().add("users-card");
        card.maxWidthProperty().bind(scroll.widthProperty().multiply(0.8));
        append(centered(card));
    }

    /** Connection closed: switch the indicator to Offline and lock the input. */
    public void showOffline(String reason) {
        setOnline(false);
        addInfo(reason);
        input.setDisable(true);
        sendButton.setDisable(true);
        usersButton.setDisable(true);
        leaveButton.setText("CLOSE");
    }

    // ---------------------------------------------------------------- layout

    private Node buildHeader(String username, String color, String serverAddress) {
        Circle avatarCircle = new Circle(20, darken(color));
        Label initial = new Label(username.substring(0, 1).toUpperCase(Locale.ROOT));
        initial.getStyleClass().add("avatar-letter");
        StackPane avatar = new StackPane(avatarCircle, initial);

        Label name = new Label(username);
        name.getStyleClass().add("header-name");
        Label sub = new Label("Group chat  ·  " + serverAddress);
        sub.getStyleClass().add("header-sub");
        VBox titles = new VBox(1, name, sub);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        usersButton.getStyleClass().add("ghost-button");
        usersButton.setOnAction(e -> onShowUsers.run());

        statusLabel.getStyleClass().add("status-label");
        HBox status = new HBox(6, statusDot, statusLabel);
        status.setAlignment(Pos.CENTER);
        status.getStyleClass().add("status-pill");

        HBox bar = new HBox(12, avatar, titles, spacer, usersButton, status);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("chat-header");
        return bar;
    }

    private Node buildConversation() {
        messages.getStyleClass().add("messages");
        messages.setPadding(new Insets(16));
        messages.setFillWidth(true);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("conversation");
        // keep the newest message visible
        messages.heightProperty().addListener((obs, old, now) -> scroll.setVvalue(1.0));
        return scroll;
    }

    private Node buildInputBar() {
        input.getStyleClass().add("message-input");
        input.setWrapText(true);
        input.setPrefRowCount(2);
        input.setPromptText(readOnly ? "READ-ONLY MODE - sending is disabled" : "Type a message...");
        input.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ENTER) {
                e.consume();
                if (e.isShiftDown()) {
                    input.replaceSelection("\n");
                } else {
                    onSend.accept(input.getText());
                }
            }
        });

        sendButton.getStyleClass().add("send-button");
        sendButton.setOnAction(e -> onSend.accept(input.getText()));
        leaveButton.getStyleClass().add("leave-button");
        leaveButton.setOnAction(e -> onLeave.run());
        sendButton.setMinWidth(Region.USE_PREF_SIZE);
        leaveButton.setMinWidth(Region.USE_PREF_SIZE);

        if (readOnly) {
            input.setDisable(true);
            sendButton.setDisable(true);
        }

        HBox.setHgrow(input, Priority.ALWAYS);
        HBox bar = new HBox(10, input, sendButton, leaveButton);
        bar.setAlignment(Pos.CENTER);
        bar.getStyleClass().add("input-bar");
        return bar;
    }

    // ---------------------------------------------------------------- helpers

    private void setOnline(boolean online) {
        statusDot.setFill(online ? Color.web("#22C55E") : Color.web("#9CA3AF"));
        statusLabel.setText(online ? "Online" : "Offline");
        statusLabel.getStyleClass().removeAll("online", "offline");
        statusLabel.getStyleClass().add(online ? "online" : "offline");
    }

    private void append(Node node) {
        messages.getChildren().add(node);
        if (messages.getChildren().size() > 1_000) {
            messages.getChildren().remove(0);
        }
    }

    private static HBox centered(Node node) {
        HBox row = new HBox(node);
        row.setAlignment(Pos.CENTER);
        return row;
    }

    private static Label pill(String text, String styleClass) {
        Label label = new Label(text);
        label.getStyleClass().add(styleClass);
        label.setWrapText(true);
        return label;
    }

    /** A darker shade of the user's pastel colour, readable as text and avatar fill. */
    private static Color darken(String hex) {
        Color base;
        try {
            base = Color.web(hex);
        } catch (IllegalArgumentException e) {
            base = Color.GRAY;
        }
        return Color.hsb(base.getHue(), Math.min(1.0, base.getSaturation() * 1.6 + 0.15), 0.52);
    }
}
