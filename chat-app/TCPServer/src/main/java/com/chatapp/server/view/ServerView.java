package com.chatapp.server.view;

import com.chatapp.server.model.ConnectedUser;
import com.chatapp.server.model.LogEntry;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.ColumnConstraints;
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

/**
 * The JavaFX presentation of the server: a {@link GridPane} with a header, the live list of
 * connected users (each on its own random background colour) and the activity log.
 *
 * <p>Pure view: it only displays what it is told and exposes one callback (start/stop).
 * It never touches sockets. All methods must be called on the JavaFX Application Thread.</p>
 */
public class ServerView {

    private static final int MAX_LOG_LINES = 2_000;

    private final GridPane root = new GridPane();
    private final ObservableList<ConnectedUser> users = FXCollections.observableArrayList();
    private final ObservableList<LogEntry> logEntries = FXCollections.observableArrayList();

    private final Label addressLabel = new Label("Starting...");
    private final Label statusLabel = new Label("Stopped");
    private final Circle statusDot = new Circle(6);
    private final Button toggleButton = new Button("Stop server");
    private final Label userCount = new Label("0");
    private final Label statsLabel = new Label();
    private final ListView<LogEntry> logList = new ListView<>(logEntries);

    private int messagesRelayed;
    private int connectionsServed;
    private Runnable onToggle = () -> { };

    public ServerView() {
        root.getStyleClass().add("server-root");
        root.setPadding(new Insets(20));
        root.setHgap(16);
        root.setVgap(16);

        ColumnConstraints left = new ColumnConstraints();
        left.setPercentWidth(34);
        ColumnConstraints right = new ColumnConstraints();
        right.setPercentWidth(66);
        root.getColumnConstraints().addAll(left, right);

        RowConstraints headerRow = new RowConstraints();
        RowConstraints contentRow = new RowConstraints();
        contentRow.setVgrow(Priority.ALWAYS);
        RowConstraints footerRow = new RowConstraints();
        root.getRowConstraints().addAll(headerRow, contentRow, footerRow);

        root.add(buildHeader(), 0, 0, 2, 1);
        root.add(buildUsersCard(), 0, 1);
        root.add(buildLogCard(), 1, 1);
        root.add(statsLabel, 0, 2, 2, 1);
        statsLabel.getStyleClass().add("footer");

        toggleButton.setOnAction(e -> onToggle.run());
        showStopped();
        refreshStats();
    }

    public Region getRoot() {
        return root;
    }

    public void setOnToggle(Runnable action) {
        this.onToggle = action;
    }

    // ---------------------------------------------------------------- updates from the model

    public void showStarted(String host, int port, List<String> lanAddresses) {
        statusDot.setFill(Color.web("#22C55E"));
        statusLabel.setText("Running");
        toggleButton.setText("Stop server");
        toggleButton.setDisable(false);
        String where = lanAddresses.isEmpty()
                ? "localhost:" + port
                : "localhost:" + port + "   ·   LAN: " + String.join(", ", lanAddresses.stream().map(a -> a + ":" + port).toList());
        addressLabel.setText("Listening on " + host + ":" + port + "   ·   Clients connect to " + where);
    }

    public void showStopped() {
        statusDot.setFill(Color.web("#EF4444"));
        statusLabel.setText("Stopped");
        toggleButton.setText("Start server");
        toggleButton.setDisable(false);
        addressLabel.setText("Server is not accepting connections");
        users.clear();
        userCount.setText("0");
    }

    public void showBusy() {
        toggleButton.setDisable(true);
    }

    public void addUser(ConnectedUser user) {
        users.add(user);
        connectionsServed++;
        userCount.setText(String.valueOf(users.size()));
        refreshStats();
    }

    public void removeUser(ConnectedUser user) {
        users.removeIf(u -> u.name().equals(user.name()));
        userCount.setText(String.valueOf(users.size()));
    }

    public void appendLog(LogEntry entry) {
        logEntries.add(entry);
        if (logEntries.size() > MAX_LOG_LINES) {
            logEntries.remove(0, logEntries.size() - MAX_LOG_LINES);
        }
        logList.scrollTo(logEntries.size() - 1);
    }

    public void messageRelayed() {
        messagesRelayed++;
        refreshStats();
    }

    // ---------------------------------------------------------------- layout

    private Node buildHeader() {
        Label title = new Label("TCP Chat Server");
        title.getStyleClass().add("title");
        addressLabel.getStyleClass().add("subtitle");
        VBox titles = new VBox(2, title, addressLabel);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        statusLabel.getStyleClass().add("status-text");
        HBox statusPill = new HBox(8, statusDot, statusLabel);
        statusPill.setAlignment(Pos.CENTER);
        statusPill.getStyleClass().add("status-pill");

        toggleButton.getStyleClass().add("toggle-button");

        HBox header = new HBox(14, titles, spacer, statusPill, toggleButton);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("header");
        return header;
    }

    private Node buildUsersCard() {
        Label heading = new Label("Connected clients");
        heading.getStyleClass().add("card-title");
        userCount.getStyleClass().add("count-badge");
        HBox top = new HBox(8, heading, userCount);
        top.setAlignment(Pos.CENTER_LEFT);

        ListView<ConnectedUser> list = new ListView<>(users);
        list.getStyleClass().add("user-list");
        list.setPlaceholder(new Label("No clients connected yet"));
        list.setCellFactory(v -> new UserCell());
        list.setFocusTraversable(false);
        VBox.setVgrow(list, Priority.ALWAYS);

        VBox card = new VBox(12, top, list);
        card.getStyleClass().add("card");
        return card;
    }

    private Node buildLogCard() {
        Label heading = new Label("Activity log");
        heading.getStyleClass().add("card-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button clear = new Button("Clear");
        clear.getStyleClass().add("ghost-button");
        clear.setOnAction(e -> logEntries.clear());
        HBox top = new HBox(8, heading, spacer, clear);
        top.setAlignment(Pos.CENTER_LEFT);

        logList.getStyleClass().add("log-list");
        logList.setCellFactory(v -> new LogCell());
        logList.setFocusTraversable(false);
        VBox.setVgrow(logList, Priority.ALWAYS);

        VBox card = new VBox(12, top, logList);
        card.getStyleClass().add("card");
        return card;
    }

    private void refreshStats() {
        statsLabel.setText("Messages relayed: " + messagesRelayed + "   ·   Clients served: " + connectionsServed
                + "   ·   One virtual thread per client");
    }

    // ---------------------------------------------------------------- cells

    /** A user row painted with the user's server-assigned random background colour. */
    private static final class UserCell extends ListCell<ConnectedUser> {
        @Override
        protected void updateItem(ConnectedUser user, boolean empty) {
            super.updateItem(user, empty);
            setText(null);
            if (empty || user == null) {
                setGraphic(null);
                return;
            }
            Color base = Color.web(user.color());

            Circle avatar = new Circle(17, base.deriveColor(0, 1.35, 0.62, 1));
            Label initial = new Label(user.name().substring(0, 1).toUpperCase(Locale.ROOT));
            initial.getStyleClass().add("avatar-letter");
            StackPane avatarPane = new StackPane(avatar, initial);

            Label name = new Label(user.name());
            name.getStyleClass().add("user-name");
            Label details = new Label("since " + user.joinedAt() + "  ·  " + user.address());
            details.getStyleClass().add("user-details");
            VBox text = new VBox(1, name, details);

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            HBox row = new HBox(10, avatarPane, text, spacer);
            if (user.readOnly()) {
                Label tag = new Label("READ-ONLY");
                tag.getStyleClass().add("readonly-tag");
                row.getChildren().add(tag);
            }
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("user-row");
            row.setStyle("-fx-background-color: " + user.color() + ";");
            setGraphic(row);
        }
    }

    private static final class LogCell extends ListCell<LogEntry> {
        @Override
        protected void updateItem(LogEntry entry, boolean empty) {
            super.updateItem(entry, empty);
            setText(null);
            if (empty || entry == null) {
                setGraphic(null);
                return;
            }
            Label time = new Label(entry.time().toString());
            time.getStyleClass().add("log-time");
            Label level = new Label(entry.level().name());
            level.getStyleClass().addAll("log-level", "level-" + entry.level().name().toLowerCase(Locale.ROOT));
            level.setMinWidth(Region.USE_PREF_SIZE);
            Label message = new Label(entry.message());
            message.getStyleClass().add("log-message");
            HBox row = new HBox(10, time, level, message);
            row.setAlignment(Pos.CENTER_LEFT);
            setGraphic(row);
        }
    }
}
