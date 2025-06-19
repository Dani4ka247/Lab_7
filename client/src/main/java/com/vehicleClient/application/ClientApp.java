package com.vehicleClient.application;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.vehicleShared.model.*;
import com.vehicleShared.network.Request;
import com.vehicleShared.network.Response;

public class ClientApp extends Application {
    private Stage primaryStage;
    private Stage loginStage;
    private TextField usernameField = new TextField();
    private PasswordField passwordField = new PasswordField();
    private Label userLabel = new Label();
    private ObservableList<Vehicle> vehicles = FXCollections.observableArrayList();
    private SocketChannel socketChannel;
    private String currentLogin;
    private String currentPassword;
    private Label usernameLabel = new Label("Username");
    private Label passwordLabel = new Label("Password");
    private Button loginButton = new Button("Login");
    private Button registerButton = new Button("Register");
    private ChoiceBox<String> langChoice = new ChoiceBox<>();
    private TableView<Vehicle> table;
    private Button addButton;
    private Button removeButton;
    private Button updateButton;
    private Button clearButton;
    private Button logoutButton;

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        showLoginWindow();
    }

    private void showLoginWindow() {
        loginStage = new Stage();
        langChoice.getItems().clear(); // Очищаем перед добавлением
        langChoice.getItems().addAll("ru", "en_ZA");
        langChoice.setValue("ru");
        langChoice.setOnAction(e -> {
            if (langChoice.getValue().equals("ru")) {
                usernameLabel.setText("Логин");
                passwordLabel.setText("Пароль");
                loginButton.setText("Войти");
                registerButton.setText("Зарегистрироваться");
            } else {
                usernameLabel.setText("Username");
                passwordLabel.setText("Password");
                loginButton.setText("Login");
                registerButton.setText("Register");
            }
        });

        loginButton.setOnAction(e -> handleAuth("login"));
        registerButton.setOnAction(e -> handleAuth("register"));

        VBox loginRoot = new VBox(10, langChoice, usernameLabel, usernameField, passwordLabel, passwordField,
                loginButton, registerButton, userLabel);
        loginRoot.setPadding(new Insets(10));
        loginRoot.setStyle("-fx-background-color: #333544;");
        loginButton.setStyle("-fx-background-color: #d24d46; -fx-text-fill: white;");
        registerButton.setStyle("-fx-background-color: #d24d46; -fx-text-fill: white;");

        Scene loginScene = new Scene(loginRoot, 300, 250);
        loginStage.setTitle("Vehicle Client - Login");
        loginStage.setScene(loginScene);
        loginStage.show();

        connectToServer();
    }

    private void showMainWindow() {
        loginStage.close();

        table = new TableView<>();
        TableColumn<Vehicle, Long> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(cellData -> new SimpleLongProperty(cellData.getValue().getId()).asObject());
        TableColumn<Vehicle, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getName()));
        table.getColumns().addAll(idCol, nameCol);
        table.setItems(vehicles);

        addButton = new Button("Add");
        removeButton = new Button("Remove");
        updateButton = new Button("Update");
        clearButton = new Button("Clear");
        logoutButton = new Button("Logout");
        HBox commandButtons = new HBox(10, addButton, removeButton, updateButton, clearButton, logoutButton);
        commandButtons.setPadding(new Insets(10));

        addButton.setStyle("-fx-background-color: #d24d46; -fx-text-fill: white;");
        removeButton.setStyle("-fx-background-color: #d24d46; -fx-text-fill: white;");
        updateButton.setStyle("-fx-background-color: #d24d46; -fx-text-fill: white;");
        clearButton.setStyle("-fx-background-color: #d24d46; -fx-text-fill: white;");
        logoutButton.setStyle("-fx-background-color: #d24d46; -fx-text-fill: white;");

        addButton.setOnAction(e -> {
            System.out.println("Добавление объекта...");
            Vehicle vehicle = new Vehicle(0, new Coordinates(0f, 0), "New Vehicle", 100f, VehicleType.CAR, FuelType.GASOLINE);
            Task<Response> task = new Task<>() {
                @Override
                protected Response call() {
                    Request request = new Request("insert", null, currentLogin, currentPassword);
                    request.setVehicle(vehicle);
                    return sendRequest(request);
                }
            };
            task.setOnSucceeded(event -> {
                Response response = task.getValue();
                if (response.isSuccess()) {
                    userLabel.setText("Объект добавлен");
                    updateVehiclesFromResponse(sendRequest(new Request("show", null, currentLogin, currentPassword)));
                } else {
                    userLabel.setText("Ошибка: " + response.getMessage());
                }
            });
            task.setOnFailed(event -> userLabel.setText("Ошибка: " + task.getException().getMessage()));
            new Thread(task).start();
        });

        removeButton.setOnAction(e -> {
            System.out.println("Удаление объекта...");
            Vehicle selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) {
                userLabel.setText("Выберите объект для удаления");
                return;
            }
            Task<Response> task = new Task<>() {
                @Override
                protected Response call() {
                    Request request = new Request("remove_key", String.valueOf(selected.getId()), currentLogin, currentPassword);
                    return sendRequest(request);
                }
            };
            task.setOnSucceeded(event -> {
                Response response = task.getValue();
                if (response.isSuccess()) {
                    userLabel.setText("Объект удалён");
                    updateVehiclesFromResponse(sendRequest(new Request("show", null, currentLogin, currentPassword)));
                } else {
                    userLabel.setText("Ошибка: " + response.getMessage());
                }
            });
            task.setOnFailed(event -> userLabel.setText("Ошибка: " + task.getException().getMessage()));
            new Thread(task).start();
        });

        updateButton.setOnAction(e -> {
            System.out.println("Обновление объекта...");
            Vehicle selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) {
                userLabel.setText("Выберите объект для редактирования");
                return;
            }
            selected.setName(selected.getName() + "_updated");
            Task<Response> task = new Task<>() {
                @Override
                protected Response call() {
                    Request request = new Request("update", String.valueOf(selected.getId()), currentLogin, currentPassword);
                    request.setVehicle(selected);
                    return sendRequest(request);
                }
            };
            task.setOnSucceeded(event -> {
                Response response = task.getValue();
                if (response.isSuccess()) {
                    userLabel.setText("Объект обновлён");
                    updateVehiclesFromResponse(sendRequest(new Request("show", null, currentLogin, currentPassword)));
                } else {
                    userLabel.setText("Ошибка: " + response.getMessage());
                }
            });
            task.setOnFailed(event -> userLabel.setText("Ошибка: " + task.getException().getMessage()));
            new Thread(task).start();
        });

        clearButton.setOnAction(e -> {
            System.out.println("Очистка коллекции...");
            Task<Response> task = new Task<>() {
                @Override
                protected Response call() {
                    Request request = new Request("clear", null, currentLogin, currentPassword);
                    return sendRequest(request);
                }
            };
            task.setOnSucceeded(event -> {
                Response response = task.getValue();
                if (response.isSuccess()) {
                    userLabel.setText("Коллекция очищена");
                    updateVehiclesFromResponse(sendRequest(new Request("show", null, currentLogin, currentPassword)));
                } else {
                    userLabel.setText("Ошибка: " + response.getMessage());
                }
            });
            task.setOnFailed(event -> userLabel.setText("Ошибка: " + task.getException().getMessage()));
            new Thread(task).start();
        });

        logoutButton.setOnAction(e -> {
            System.out.println("Выход из аккаунта...");
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Подтверждение выхода");
            alert.setHeaderText("Вы уверены, что хотите выйти?");
            alert.setContentText("Все несохранённые данные будут потеряны.");

            if (alert.showAndWait().get() == ButtonType.OK) {
                currentLogin = null;
                currentPassword = null;
                vehicles.clear();
                if (socketChannel != null && socketChannel.isOpen()) {
                    try {
                        socketChannel.close();
                        System.out.println("Соединение с сервером закрыто");
                    } catch (IOException ex) {
                        System.out.println("Ошибка закрытия соединения: " + ex.getMessage());
                    }
                }
                primaryStage.close();
                showLoginWindow();
            }
        });

        VBox root = new VBox(10, langChoice, userLabel, commandButtons, table);
        root.setPadding(new Insets(10));
        root.setStyle("-fx-background-color: #333544;");

        Scene scene = new Scene(root, 400, 300);
        primaryStage.setTitle("Vehicle Client");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void connectToServer() {
        try {
            socketChannel = SocketChannel.open();
            socketChannel.connect(new InetSocketAddress("localhost", 6969));
            System.out.println("подключено к серверу");
        } catch (IOException e) {
            System.out.println("ошибка подключения: " + e.getMessage());
            userLabel.setText("Ошибка подключения: " + e.getMessage());
        }
    }

    private void handleAuth(String command) {
        String login = usernameField.getText().trim();
        String password = passwordField.getText().trim();
        if (login.isEmpty() || password.isEmpty()) {
            userLabel.setText("Ошибка: Логин и пароль не могут быть пустыми");
            return;
        }
        currentLogin = null;
        currentPassword = null;
        if (socketChannel == null || !socketChannel.isOpen()) {
            connectToServer();
        }
        Request request = new Request(command, null, login, password);
        Task<Response> task = new Task<>() {
            @Override
            protected Response call() {
                return sendRequest(request);
            }
        };
        task.setOnSucceeded(event -> {
            Response response = task.getValue();
            System.out.println("Ответ на авторизацию: " + response);
            if (response.isSuccess()) {
                currentLogin = login;
                currentPassword = password;
                userLabel.setText("Текущий пользователь: " + login);
                showMainWindow();
                updateVehiclesFromResponse(sendRequest(new Request("show", null, currentLogin, currentPassword)));
            } else {
                userLabel.setText("Ошибка авторизации: " + response.getMessage());
            }
        });
        task.setOnFailed(event -> userLabel.setText("Ошибка: " + task.getException().getMessage()));
        new Thread(task).start();
    }

    private Response sendRequest(Request request) {
        try {
            if (socketChannel == null || !socketChannel.isOpen()) {
                return Response.error("канал закрыт");
            }
            socketChannel.socket().setSoTimeout(5000);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
                oos.writeObject(request);
            }
            byte[] data = baos.toByteArray();
            ByteBuffer buffer = ByteBuffer.allocate(4 + data.length);
            buffer.putInt(data.length);
            buffer.put(data);
            buffer.flip();
            while (buffer.hasRemaining()) {
                socketChannel.write(buffer);
            }

            ByteBuffer lengthBuffer = ByteBuffer.allocate(4);
            int totalBytesRead = 0;
            while (totalBytesRead < 4) {
                int bytesRead = socketChannel.read(lengthBuffer);
                if (bytesRead == -1) {
                    return Response.error("сервер отключился");
                }
                totalBytesRead += bytesRead;
            }
            lengthBuffer.flip();
            int length = lengthBuffer.getInt();
            if (length <= 0 || length > 1_000_000) {
                return Response.error("некорректная длина ответа");
            }

            ByteBuffer dataBuffer = ByteBuffer.allocate(length);
            totalBytesRead = 0;
            while (totalBytesRead < length) {
                int bytesRead = socketChannel.read(dataBuffer);
                if (bytesRead == -1) {
                    return Response.error("сервер отключился");
                }
                totalBytesRead += bytesRead;
            }
            dataBuffer.flip();
            byte[] responseData = new byte[length];
            dataBuffer.get(responseData);

            try (ByteArrayInputStream bais = new ByteArrayInputStream(responseData);
                 ObjectInputStream ois = new ObjectInputStream(bais)) {
                return (Response) ois.readObject();
            }
        } catch (Exception e) {
            System.out.println("Ошибка отправки запроса: " + e.getMessage());
            return Response.error("ошибка связи: " + e.getMessage());
        }
    }

    private void updateVehiclesFromResponse(Response response) {
        if (!response.isSuccess()) {
            userLabel.setText("Ошибка обновления данных: " + response.getMessage());
            return;
        }

        String message = response.getMessage();
        if (message == null || message.isEmpty()) {
            System.out.println("Сообщение пустое, коллекция не обновлена");
            return;
        }

        vehicles.clear();

        try {
            // Разделяем строку на id и данные
            String[] parts = message.split(" : ", 2);
            if (parts.length != 2) return;

            // Извлекаем данные с помощью регулярного выражения
            Matcher matcher = Pattern.compile(
                    "\\{id=(\\d+), name=([^,]+), coordinates=\\{x=([\\d.]+), y=(\\d+)\\}, " +
                            "creationDate=(\\d{4}_\\d{2}_\\d{2} \\d{2}:\\d{2}), " +
                            "enginePower=([\\d.]+), type=([^,]+), fuelType=([^}]+)\\}"
            ).matcher(parts[1]);

            if (!matcher.find()) return;

            Vehicle vehicle = new Vehicle(
                    Long.parseLong(matcher.group(1)),
                    new Coordinates(
                            Float.parseFloat(matcher.group(3)),
                            Integer.parseInt(matcher.group(4))
                    ),
                    matcher.group(2),
                    Float.parseFloat(matcher.group(6)),
                    VehicleType.valueOf(matcher.group(7)),
                    FuelType.valueOf(matcher.group(8))
            );

            vehicle.setCreationDate(LocalDateTime.parse(
                    matcher.group(5),
                    DateTimeFormatter.ofPattern("yyyy_MM_dd HH:mm")
            ).atZone(ZoneId.systemDefault()));

            vehicles.add(vehicle);
            System.out.println("Обновлено 1 элемент в таблице");

        } catch (Exception e) {
            System.out.println("Ошибка парсинга: " + e.getMessage());
        }
    }

    @Override
    public void stop() throws Exception {
        if (socketChannel != null && socketChannel.isOpen()) {
            socketChannel.close();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}