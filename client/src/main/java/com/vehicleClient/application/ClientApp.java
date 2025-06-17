package com.vehicleClient.application;

import com.vehicleShared.model.*;
import com.vehicleShared.network.Request;
import com.vehicleShared.network.Response;
import javafx.application.Application;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Locale;
import java.util.ResourceBundle;

public class ClientApp extends Application {
    private ResourceBundle bundle;
    private Stage primaryStage;
    private TextField usernameField = new TextField();
    private PasswordField passwordField = new PasswordField();
    private Label userLabel = new Label();
    private ObservableList<VehicleWrapper> vehicles = FXCollections.observableArrayList();
    private Canvas canvas = new Canvas(300, 300);
    private SocketChannel socketChannel;
    private String currentLogin;
    private String currentPassword;
    private Label usernameLabel = new Label();
    private Label passwordLabel = new Label();
    private Button loginButton = new Button();
    private Button registerButton = new Button();

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;

        ChoiceBox<String> langChoice = new ChoiceBox<>();
        langChoice.getItems().addAll("ru", "cs", "ca", "en_ZA");
        langChoice.setValue("ru");
        langChoice.setOnAction(e -> switchLanguage(langChoice.getValue()));

        switchLanguage("ru");

        loginButton.setOnAction(e -> handleAuth("login"));
        registerButton.setOnAction(e -> handleAuth("register"));

        TableView<VehicleWrapper> table = new TableView<>();
        TableColumn<VehicleWrapper, Long> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(cellData -> cellData.getValue().idProperty().asObject());
        TableColumn<VehicleWrapper, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(cellData -> cellData.getValue().nameProperty());
        table.getColumns().addAll(idCol, nameCol);
        table.setItems(vehicles);

        GraphicsContext gc = canvas.getGraphicsContext2D();
        drawVehicles(gc);
        canvas.setOnMouseClicked(e -> showVehicleInfo(e.getX(), e.getY(), gc));

        VBox root = new VBox(10, langChoice, usernameLabel, usernameField, passwordLabel, passwordField,
                loginButton, registerButton, userLabel, table, canvas);
        root.setPadding(new Insets(10));
        root.setStyle("-fx-background-color: #333544;");
        loginButton.setStyle("-fx-background-color: #d24d46; -fx-text-fill: white;");
        registerButton.setStyle("-fx-background-color: #d24d46; -fx-text-fill: white;");

        Scene scene = new Scene(root, 600, 400);
        primaryStage.setTitle("Vehicle Client");
        primaryStage.setScene(scene);
        primaryStage.show();

        connectToServer();
    }

    private void switchLanguage(String lang) {
        Locale locale = new Locale(lang);
        bundle = ResourceBundle.getBundle("messages", locale);
        primaryStage.setTitle(getStringSafe("login") + " - Vehicle Client");
        usernameLabel.setText(getStringSafe("username"));
        passwordLabel.setText(getStringSafe("password"));
        usernameField.setPromptText(getStringSafe("username"));
        passwordField.setPromptText(getStringSafe("password"));
        loginButton.setText(getStringSafe("login"));
        registerButton.setText(getStringSafe("register"));
        userLabel.setText("");
    }

    private String getStringSafe(String key) {
        return bundle.containsKey(key) ? bundle.getString(key) : key;
    }

    private void connectToServer() {
        try {
            socketChannel = SocketChannel.open();
            socketChannel.connect(new InetSocketAddress("localhost", 12345));
            System.out.println("подключено к серверу");
        } catch (IOException e) {
            System.out.println("ошибка подключения: " + e.getMessage());
        }
    }

    private void handleAuth(String command) {
        String login = usernameField.getText();
        String password = passwordField.getText();
        Request request = new Request(command, null, login, password);
        Response response = sendRequest(request);
        if (response.isSuccess() && command.equals("login")) {
            currentLogin = login;
            currentPassword = password;
            userLabel.setText("Текущий пользователь: " + login);
            // Загрузка данных (пока заглушка)
            vehicles.add(new VehicleWrapper(new Vehicle(1, new Coordinates(1f, 1), "Car", 100f, VehicleType.CAR, FuelType.GASOLINE)));
            vehicles.add(new VehicleWrapper(new Vehicle(2, new Coordinates(2f, 2), "Truck", 200f, VehicleType.HOVERBOARD, FuelType.NUCLEAR)));
        } else {
            userLabel.setText("Ошибка: " + response.getMessage());
        }
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
            return Response.error("ошибка связи: " + e.getMessage());
        }
    }

    private void drawVehicles(GraphicsContext gc) {
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
        for (VehicleWrapper vw : vehicles) {
            Vehicle v = vw.getVehicle();
            gc.setFill(v.getId() == 1 ? Color.BLUE : Color.GREEN);
            gc.fillRect(v.getCoordinates().getX() * 50, v.getCoordinates().getY() * 50, 20, 20);
        }
    }

    private void showVehicleInfo(double x, double y, GraphicsContext gc) {
        for (VehicleWrapper vw : vehicles) {
            Vehicle v = vw.getVehicle();
            double vx = v.getCoordinates().getX() * 50;
            double vy = v.getCoordinates().getY() * 50;
            if (x >= vx && x <= vx + 20 && y >= vy && y <= vy + 20) {
                System.out.println("Объект: " + v.getName() + ", ID: " + v.getId());
                break;
            }
        }
    }

    public static void main(String[] args) {
        launch(args);
    }

    // Внутренний класс для обёртки
    public static class VehicleWrapper {
        private final SimpleLongProperty id = new SimpleLongProperty();
        private final SimpleStringProperty name = new SimpleStringProperty();
        private final Vehicle vehicle;

        public VehicleWrapper(Vehicle vehicle) {
            this.vehicle = vehicle;
            this.id.set(vehicle.getId());
            this.name.set(vehicle.getName());
        }

        public SimpleLongProperty idProperty() { return id; }
        public SimpleStringProperty nameProperty() { return name; }
        public Vehicle getVehicle() { return vehicle; }
    }
}