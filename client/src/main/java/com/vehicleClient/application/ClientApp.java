package com.vehicleClient.application;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleFloatProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
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
    private Label mainUserLabel = new Label();
    private ObservableList<Vehicle> vehicles = FXCollections.observableArrayList();
    private SocketChannel socketChannel;
    private String currentLogin;
    private String currentPassword;
    private Label usernameLabel = new Label("Username");
    private Label passwordLabel = new Label("Password");
    private Button loginButton = new Button("Login");
    private Button registerButton = new Button("Register");
    private ChoiceBox<String> langChoice = new ChoiceBox<>();
    private Canvas canvas;
    private TableView<Vehicle> tableView;
    private Button addButton;
    private Button removeButton;
    private Button updateButton;
    private Button clearButton;
    private Button logoutButton;
    private Button removeByPowerButton;
    private Button removeGreaterKeyButton;
    private Button sumOfPowerButton;
    private Button replaceIfLowerButton;
    private Button toggleViewButton;
    private boolean showCanvas = true; // По умолчанию показываем область
    private Map<String, Color> userColors = new HashMap<>();

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        showLoginWindow();
    }

    private void showLoginWindow() {
        loginStage = new Stage();
        langChoice.getItems().clear();
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

        canvas = new Canvas(800, 400);
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(Color.BLACK);
        gc.fillRect(0, 0, 800, 400);
        canvas.setOnMouseClicked(this::handleCanvasClick);

        tableView = new TableView<>();
        TableColumn<Vehicle, Long> idColumn = new TableColumn<>("ID");
        idColumn.setCellValueFactory(cellData -> new SimpleLongProperty(cellData.getValue().getId()).asObject());
        TableColumn<Vehicle, String> nameColumn = new TableColumn<>("Название");
        nameColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getName()));
        TableColumn<Vehicle, Float> xColumn = new TableColumn<>("X");
        xColumn.setCellValueFactory(cellData -> new SimpleFloatProperty(cellData.getValue().getCoordinates().getX()).asObject());
        TableColumn<Vehicle, Integer> yColumn = new TableColumn<>("Y");
        yColumn.setCellValueFactory(cellData -> new SimpleIntegerProperty(cellData.getValue().getCoordinates().getY()).asObject());
        TableColumn<Vehicle, Float> powerColumn = new TableColumn<>("Мощность");
        powerColumn.setCellValueFactory(cellData -> new SimpleFloatProperty(cellData.getValue().getPower()).asObject());
        TableColumn<Vehicle, String> typeColumn = new TableColumn<>("Тип");
        typeColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getType().toString()));
        TableColumn<Vehicle, String> fuelColumn = new TableColumn<>("Топливо");
        fuelColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getFuelType().toString()));
        tableView.getColumns().addAll(idColumn, nameColumn, xColumn, yColumn, powerColumn, typeColumn, fuelColumn);
        tableView.setItems(vehicles);

        addButton = new Button("Add");
        removeButton = new Button("Remove");
        updateButton = new Button("Update");
        clearButton = new Button("Clear");
        logoutButton = new Button("Logout");
        removeByPowerButton = new Button("Remove by Power");
        removeGreaterKeyButton = new Button("Remove > Key");
        sumOfPowerButton = new Button("Sum of Power");
        replaceIfLowerButton = new Button("Replace if Lower");
        toggleViewButton = new Button("Переключить вид");

        HBox commandButtons = new HBox(10, addButton, removeButton, updateButton, clearButton, removeByPowerButton, removeGreaterKeyButton, sumOfPowerButton, replaceIfLowerButton, toggleViewButton);
        commandButtons.setHgrow(addButton, Priority.ALWAYS);
        commandButtons.setHgrow(removeButton, Priority.ALWAYS);
        commandButtons.setHgrow(updateButton, Priority.ALWAYS);
        commandButtons.setHgrow(clearButton, Priority.ALWAYS);
        commandButtons.setHgrow(removeByPowerButton, Priority.ALWAYS);
        commandButtons.setHgrow(removeGreaterKeyButton, Priority.ALWAYS);
        commandButtons.setHgrow(sumOfPowerButton, Priority.ALWAYS);
        commandButtons.setHgrow(replaceIfLowerButton, Priority.ALWAYS);
        commandButtons.setHgrow(toggleViewButton, Priority.ALWAYS);
        commandButtons.setPadding(new Insets(10));

        addButton.setStyle("-fx-background-color: #d24d46; -fx-text-fill: white;");
        removeButton.setStyle("-fx-background-color: #d24d46; -fx-text-fill: white;");
        updateButton.setStyle("-fx-background-color: #d24d46; -fx-text-fill: white;");
        clearButton.setStyle("-fx-background-color: #d24d46; -fx-text-fill: white;");
        logoutButton.setStyle("-fx-background-color: #d24d46; -fx-text-fill: white;");
        removeByPowerButton.setStyle("-fx-background-color: #d24d46; -fx-text-fill: white;");
        removeGreaterKeyButton.setStyle("-fx-background-color: #d24d46; -fx-text-fill: white;");
        sumOfPowerButton.setStyle("-fx-background-color: #d24d46; -fx-text-fill: white;");
        replaceIfLowerButton.setStyle("-fx-background-color: #d24d46; -fx-text-fill: white;");
        toggleViewButton.setStyle("-fx-background-color: #d24d46; -fx-text-fill: white;");

        addButton.setOnAction(e -> {
            Dialog<Vehicle> dialog = new Dialog<>();
            dialog.setTitle("Добавить транспортное средство");
            dialog.setHeaderText("Введите данные транспортного средства");

            ButtonType addButtonType = new ButtonType("Добавить", ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().addAll(addButtonType, ButtonType.CANCEL);

            GridPane grid = new GridPane();
            grid.setHgap(10);
            grid.setVgap(10);
            grid.setPadding(new Insets(20, 150, 10, 10));

            TextField nameField = new TextField();
            TextField xField = new TextField();
            TextField yField = new TextField();
            TextField powerField = new TextField();
            ComboBox<VehicleType> typeCombo = new ComboBox<>();
            typeCombo.getItems().setAll(VehicleType.values());
            ComboBox<FuelType> fuelCombo = new ComboBox<>();
            fuelCombo.getItems().setAll(FuelType.values());

            grid.add(new Label("Название:"), 0, 0);
            grid.add(nameField, 1, 0);
            grid.add(new Label("Координата X:"), 0, 1);
            grid.add(xField, 1, 1);
            grid.add(new Label("Координата Y:"), 0, 2);
            grid.add(yField, 1, 2);
            grid.add(new Label("Мощность двигателя:"), 0, 3);
            grid.add(powerField, 1, 3);
            grid.add(new Label("Тип:"), 0, 4);
            grid.add(typeCombo, 1, 4);
            grid.add(new Label("Тип топлива:"), 0, 5);
            grid.add(fuelCombo, 1, 5);

            dialog.getDialogPane().setContent(grid);

            Node addButtonNode = dialog.getDialogPane().lookupButton(addButtonType);
            addButtonNode.setDisable(true);

            ChangeListener<String> validationListener = (observable, oldValue, newValue) -> {
                boolean isValid = !nameField.getText().isEmpty()
                        && !xField.getText().isEmpty()
                        && !yField.getText().isEmpty()
                        && !powerField.getText().isEmpty()
                        && typeCombo.getValue() != null
                        && fuelCombo.getValue() != null;
                try {
                    float x = Float.parseFloat(xField.getText());
                    int y = Integer.parseInt(yField.getText());
                    isValid &= x < 982 && y < 67;
                } catch (NumberFormatException ex) {
                    isValid = false;
                }
                addButtonNode.setDisable(!isValid);
            };

            nameField.textProperty().addListener(validationListener);
            xField.textProperty().addListener(validationListener);
            yField.textProperty().addListener(validationListener);
            powerField.textProperty().addListener(validationListener);
            typeCombo.valueProperty().addListener((obs, oldVal, newVal) -> validationListener.changed(null, null, null));
            fuelCombo.valueProperty().addListener((obs, oldVal, newVal) -> validationListener.changed(null, null, null));

            dialog.setResultConverter(dialogButton -> {
                if (dialogButton == addButtonType) {
                    try {
                        float x = Float.parseFloat(xField.getText());
                        int y = Integer.parseInt(yField.getText());
                        if (x >= 982 || y >= 67) {
                            userLabel.setText("Ошибка: Координаты должны быть x < 982 и y < 67");
                            return null;
                        }
                        Vehicle vehicle = new Vehicle(
                                0,
                                new Coordinates(x, y),
                                nameField.getText(),
                                Float.parseFloat(powerField.getText()),
                                typeCombo.getValue(),
                                fuelCombo.getValue()
                        );
                        return vehicle;
                    } catch (IllegalArgumentException ex) {
                        userLabel.setText("Ошибка: неверный числовой формат или координаты");
                        return null;
                    }
                }
                return null;
            });

            Optional<Vehicle> result = dialog.showAndWait();
            result.ifPresent(vehicle -> {
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
        });

        removeButton.setOnAction(e -> {
            Vehicle selected = showCanvas ? findVehicleAt(canvas.getGraphicsContext2D(), canvas.getWidth() / 2, canvas.getHeight() / 2) : tableView.getSelectionModel().getSelectedItem();
            if (selected == null) {
                userLabel.setText("Выберите объект для удаления");
                return;
            }
            Task<Response> task = new Task<>() {
                @Override
                protected Response call() {
                    Request request = new Request("remove", String.valueOf(selected.getId()), currentLogin, currentPassword);
                    return sendRequest(request);
                }
            };
            task.setOnSucceeded(event -> {
                Response response = task.getValue();
                if (response.isSuccess()) {
                    userLabel.setText("Объект удалён: ID " + selected.getId());
                    updateVehiclesFromResponse(sendRequest(new Request("show", null, currentLogin, currentPassword)));
                } else {
                    userLabel.setText("Ошибка удаления: " + response.getMessage());
                }
            });
            task.setOnFailed(event -> userLabel.setText("Ошибка: " + task.getException().getMessage()));
            new Thread(task).start();
        });

        updateButton.setOnAction(e -> {
            Vehicle selected = showCanvas ? findVehicleAt(canvas.getGraphicsContext2D(), canvas.getWidth() / 2, canvas.getHeight() / 2) : tableView.getSelectionModel().getSelectedItem();
            if (selected == null) {
                userLabel.setText("Выберите объект для редактирования");
                return;
            }

            Dialog<Vehicle> dialog = new Dialog<>();
            dialog.setTitle("Обновить транспортное средство");
            dialog.setHeaderText("Обновите данные транспортного средства");

            ButtonType updateButtonType = new ButtonType("Обновить", ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().addAll(updateButtonType, ButtonType.CANCEL);

            GridPane grid = new GridPane();
            grid.setHgap(10);
            grid.setVgap(10);
            grid.setPadding(new Insets(20, 150, 10, 10));

            TextField nameField = new TextField(selected.getName());
            TextField xField = new TextField(String.valueOf(selected.getCoordinates().getX()));
            TextField yField = new TextField(String.valueOf(selected.getCoordinates().getY()));
            TextField powerField = new TextField(String.valueOf(selected.getPower()));
            ComboBox<VehicleType> typeCombo = new ComboBox<>();
            typeCombo.getItems().setAll(VehicleType.values());
            typeCombo.setValue(selected.getType());
            ComboBox<FuelType> fuelCombo = new ComboBox<>();
            fuelCombo.getItems().setAll(FuelType.values());
            fuelCombo.setValue(selected.getFuelType());

            grid.add(new Label("Название:"), 0, 0);
            grid.add(nameField, 1, 0);
            grid.add(new Label("Координата X:"), 0, 1);
            grid.add(xField, 1, 1);
            grid.add(new Label("Координата Y:"), 0, 2);
            grid.add(yField, 1, 2);
            grid.add(new Label("Мощность двигателя:"), 0, 3);
            grid.add(powerField, 1, 3);
            grid.add(new Label("Тип:"), 0, 4);
            grid.add(typeCombo, 1, 4);
            grid.add(new Label("Тип топлива:"), 0, 5);
            grid.add(fuelCombo, 1, 5);

            dialog.getDialogPane().setContent(grid);

            Node updateButtonNode = dialog.getDialogPane().lookupButton(updateButtonType);
            updateButtonNode.setDisable(true);

            ChangeListener<String> validationListener = (observable, oldValue, newValue) -> {
                boolean isValid = !nameField.getText().isEmpty()
                        && !xField.getText().isEmpty()
                        && !yField.getText().isEmpty()
                        && !powerField.getText().isEmpty()
                        && typeCombo.getValue() != null
                        && fuelCombo.getValue() != null;
                try {
                    float x = Float.parseFloat(xField.getText());
                    int y = Integer.parseInt(yField.getText());
                    isValid &= x < 982 && y < 67;
                } catch (NumberFormatException ex) {
                    isValid = false;
                }
                updateButtonNode.setDisable(!isValid);
            };

            nameField.textProperty().addListener(validationListener);
            xField.textProperty().addListener(validationListener);
            yField.textProperty().addListener(validationListener);
            powerField.textProperty().addListener(validationListener);
            typeCombo.valueProperty().addListener((obs, oldVal, newVal) -> validationListener.changed(null, null, null));
            fuelCombo.valueProperty().addListener((obs, oldVal, newVal) -> validationListener.changed(null, null, null));

            dialog.setResultConverter(dialogButton -> {
                if (dialogButton == updateButtonType) {
                    try {
                        float x = Float.parseFloat(xField.getText());
                        int y = Integer.parseInt(yField.getText());
                        if (x >= 982 || y >= 67) {
                            userLabel.setText("Ошибка: Координаты должны быть x < 982 и y < 67");
                            return null;
                        }
                        Vehicle updatedVehicle = new Vehicle(
                                selected.getId(),
                                new Coordinates(x, y),
                                nameField.getText(),
                                Float.parseFloat(powerField.getText()),
                                typeCombo.getValue(),
                                fuelCombo.getValue()
                        );
                        updatedVehicle.setCreationDate(selected.getCreationDate());
                        return updatedVehicle;
                    } catch (Exception ex) {
                        userLabel.setText("Ошибка: неверный числовой формат или координаты");
                        return null;
                    }
                }
                return null;
            });

            Optional<Vehicle> result = dialog.showAndWait();
            result.ifPresent(vehicle -> {
                Task<Response> task = new Task<>() {
                    @Override
                    protected Response call() {
                        Request request = new Request("update", String.valueOf(vehicle.getId()), currentLogin, currentPassword);
                        request.setVehicle(vehicle);
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
        });

        clearButton.setOnAction(e -> {
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

        removeByPowerButton.setOnAction(e -> {
            Dialog<Float> dialog = new Dialog<>();
            dialog.setTitle("Удалить по мощности двигателя");
            dialog.setHeaderText("Введите мощность двигателя");

            ButtonType removeButtonType = new ButtonType("Удалить", ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().addAll(removeButtonType, ButtonType.CANCEL);

            GridPane grid = new GridPane();
            grid.setHgap(10);
            grid.setVgap(10);
            grid.setPadding(new Insets(20, 150, 10, 10));

            TextField powerField = new TextField();

            grid.add(new Label("Мощность:"), 0, 0);
            grid.add(powerField, 1, 0);

            dialog.getDialogPane().setContent(grid);

            Node removeButtonNode = dialog.getDialogPane().lookupButton(removeButtonType);
            removeButtonNode.setDisable(true);

            powerField.textProperty().addListener((observable, oldValue, newValue) -> {
                removeButtonNode.setDisable(newValue.trim().isEmpty());
            });

            dialog.setResultConverter(dialogButton -> {
                if (dialogButton == removeButtonType) {
                    try {
                        return Float.parseFloat(powerField.getText().trim());
                    } catch (NumberFormatException ex) {
                        userLabel.setText("Ошибка: неверный числовой формат");
                        return null;
                    }
                }
                return null;
            });

            Optional<Float> result = dialog.showAndWait();
            result.ifPresent(power -> {
                Task<Response> task = new Task<>() {
                    @Override
                    protected Response call() {
                        Request request = new Request("remove_all_by_engine_power", String.valueOf(power), currentLogin, currentPassword);
                        return sendRequest(request);
                    }
                };
                task.setOnSucceeded(event -> {
                    Response response = task.getValue();
                    if (response.isSuccess()) {
                        userLabel.setText("Объекты с мощностью " + power + " удалены");
                        updateVehiclesFromResponse(sendRequest(new Request("show", null, currentLogin, currentPassword)));
                    } else {
                        userLabel.setText("Ошибка: " + response.getMessage());
                    }
                });
                task.setOnFailed(event -> userLabel.setText("Ошибка: " + task.getException().getMessage()));
                new Thread(task).start();
            });
        });

        removeGreaterKeyButton.setOnAction(e -> {
            Dialog<Long> dialog = new Dialog<>();
            dialog.setTitle("Удалить объекты с ключом больше");
            dialog.setHeaderText("Введите ID");

            ButtonType removeButtonType = new ButtonType("Удалить", ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().addAll(removeButtonType, ButtonType.CANCEL);

            GridPane grid = new GridPane();
            grid.setHgap(10);
            grid.setVgap(10);
            grid.setPadding(new Insets(20, 150, 10, 10));

            TextField keyField = new TextField();

            grid.add(new Label("ID:"), 0, 0);
            grid.add(keyField, 1, 0);

            dialog.getDialogPane().setContent(grid);

            Node removeButtonNode = dialog.getDialogPane().lookupButton(removeButtonType);
            removeButtonNode.setDisable(true);

            keyField.textProperty().addListener((observable, oldValue, newValue) -> {
                removeButtonNode.setDisable(newValue.trim().isEmpty());
            });

            dialog.setResultConverter(dialogButton -> {
                if (dialogButton == removeButtonType) {
                    try {
                        return Long.parseLong(keyField.getText().trim());
                    } catch (NumberFormatException ex) {
                        userLabel.setText("Ошибка: неверный числовой формат");
                        return null;
                    }
                }
                return null;
            });

            Optional<Long> result = dialog.showAndWait();
            result.ifPresent(key -> {
                Task<Response> task = new Task<>() {
                    @Override
                    protected Response call() {
                        Request request = new Request("remove_greater_key", String.valueOf(key), currentLogin, currentPassword);
                        return sendRequest(request);
                    }
                };
                task.setOnSucceeded(event -> {
                    Response response = task.getValue();
                    if (response.isSuccess()) {
                        userLabel.setText("Объекты с ID больше " + key + " удалены");
                        updateVehiclesFromResponse(sendRequest(new Request("show", null, currentLogin, currentPassword)));
                    } else {
                        userLabel.setText("Ошибка: " + response.getMessage());
                    }
                });
                task.setOnFailed(event -> userLabel.setText("Ошибка: " + task.getException().getMessage()));
                new Thread(task).start();
            });
        });

        sumOfPowerButton.setOnAction(e -> {
            Task<Response> task = new Task<>() {
                @Override
                protected Response call() {
                    Request request = new Request("sum_of_engine_power", null, currentLogin, currentPassword);
                    return sendRequest(request);
                }
            };
            task.setOnSucceeded(event -> {
                Response response = task.getValue();
                if (response.isSuccess()) {
                    Dialog<String> dialog = new Dialog<>();
                    dialog.setTitle("Сумма мощности");
                    dialog.setHeaderText("Результат");

                    ButtonType okButtonType = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
                    dialog.getDialogPane().getButtonTypes().addAll(okButtonType);

                    GridPane grid = new GridPane();
                    grid.setHgap(10);
                    grid.setVgap(10);
                    grid.setPadding(new Insets(20, 150, 10, 10));

                    Label resultLabel = new Label("Сумма мощности: " + response.getMessage());

                    grid.add(resultLabel, 0, 0);

                    dialog.getDialogPane().setContent(grid);

                    dialog.showAndWait();
                } else {
                    userLabel.setText("Ошибка: " + response.getMessage());
                }
            });
            task.setOnFailed(event -> userLabel.setText("Ошибка: " + task.getException().getMessage()));
            new Thread(task).start();
        });

        replaceIfLowerButton.setOnAction(e -> {
            Vehicle selected = showCanvas ? findVehicleAt(canvas.getGraphicsContext2D(), canvas.getWidth() / 2, canvas.getHeight() / 2) : tableView.getSelectionModel().getSelectedItem();
            if (selected == null) {
                userLabel.setText("Выберите объект для замены");
                return;
            }

            Dialog<Vehicle> dialog = new Dialog<>();
            dialog.setTitle("Заменить если меньше");
            dialog.setHeaderText("Введите новые данные");

            ButtonType replaceButtonType = new ButtonType("Заменить", ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().addAll(replaceButtonType, ButtonType.CANCEL);

            GridPane grid = new GridPane();
            grid.setHgap(10);
            grid.setVgap(10);
            grid.setPadding(new Insets(20, 150, 10, 10));

            TextField nameField = new TextField();
            TextField xField = new TextField();
            TextField yField = new TextField();
            TextField powerField = new TextField();
            ComboBox<VehicleType> typeCombo = new ComboBox<>();
            typeCombo.getItems().setAll(VehicleType.values());
            ComboBox<FuelType> fuelCombo = new ComboBox<>();
            fuelCombo.getItems().setAll(FuelType.values());

            grid.add(new Label("Название:"), 0, 0);
            grid.add(nameField, 1, 0);
            grid.add(new Label("Координата X:"), 0, 1);
            grid.add(xField, 1, 1);
            grid.add(new Label("Координата Y:"), 0, 2);
            grid.add(yField, 1, 2);
            grid.add(new Label("Мощность двигателя:"), 0, 3);
            grid.add(powerField, 1, 3);
            grid.add(new Label("Тип:"), 0, 4);
            grid.add(typeCombo, 1, 4);
            grid.add(new Label("Тип топлива:"), 0, 5);
            grid.add(fuelCombo, 1, 5);

            dialog.getDialogPane().setContent(grid);

            Node replaceButtonNode = dialog.getDialogPane().lookupButton(replaceButtonType);
            replaceButtonNode.setDisable(true);

            ChangeListener<String> validationListener = (observable, oldValue, newValue) -> {
                boolean isValid = !nameField.getText().isEmpty()
                        && !xField.getText().isEmpty()
                        && !yField.getText().isEmpty()
                        && !powerField.getText().isEmpty()
                        && typeCombo.getValue() != null
                        && fuelCombo.getValue() != null;
                try {
                    float x = Float.parseFloat(xField.getText());
                    int y = Integer.parseInt(yField.getText());
                    isValid &= x < 982 && y < 67;
                } catch (NumberFormatException ex) {
                    isValid = false;
                }
                replaceButtonNode.setDisable(!isValid);
            };

            nameField.textProperty().addListener(validationListener);
            xField.textProperty().addListener(validationListener);
            yField.textProperty().addListener(validationListener);
            powerField.textProperty().addListener(validationListener);
            typeCombo.valueProperty().addListener((obs, oldVal, newVal) -> validationListener.changed(null, null, null));
            fuelCombo.valueProperty().addListener((obs, oldVal, newVal) -> validationListener.changed(null, null, null));

            dialog.setResultConverter(dialogButton -> {
                if (dialogButton == replaceButtonType) {
                    try {
                        float x = Float.parseFloat(xField.getText());
                        int y = Integer.parseInt(yField.getText());
                        if (x >= 982 || y >= 67) {
                            userLabel.setText("Ошибка: Координаты должны быть x < 982 и y < 67");
                            return null;
                        }
                        Vehicle newVehicle = new Vehicle(
                                selected.getId(),
                                new Coordinates(x, y),
                                nameField.getText(),
                                Float.parseFloat(powerField.getText()),
                                typeCombo.getValue(),
                                fuelCombo.getValue()
                        );
                        newVehicle.setCreationDate(selected.getCreationDate());
                        return newVehicle;
                    } catch (IllegalArgumentException ex) {
                        userLabel.setText("Ошибка: неверный числовой формат или координаты");
                        return null;
                    }
                }
                return null;
            });

            Optional<Vehicle> result = dialog.showAndWait();
            result.ifPresent(vehicle -> {
                Task<Response> task = new Task<>() {
                    @Override
                    protected Response call() {
                        Request request = new Request("replace_if_lower", String.valueOf(vehicle.getId()), currentLogin, currentPassword);
                        request.setVehicle(vehicle);
                        return sendRequest(request);
                    }
                };
                task.setOnSucceeded(event -> {
                    Response response = task.getValue();
                    if (response.isSuccess()) {
                        userLabel.setText("Объект заменён, если мощность меньше");
                        updateVehiclesFromResponse(sendRequest(new Request("show", null, currentLogin, currentPassword)));
                    } else {
                        userLabel.setText("Ошибка: " + response.getMessage());
                    }
                });
                task.setOnFailed(event -> userLabel.setText("Ошибка: " + task.getException().getMessage()));
                new Thread(task).start();
            });
        });

        toggleViewButton.setOnAction(e -> {
            showCanvas = !showCanvas;
            toggleViewButton.setText(showCanvas ? "Переключить на таблицу" : "Переключить на область");
            updateDisplay();
        });

        logoutButton.setOnAction(e -> {
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

        mainUserLabel.setText("Текущий пользователь: " + (currentLogin != null ? currentLogin : "Не авторизован"));
        mainUserLabel.setStyle("-fx-text-fill: white;");

        HBox topPanel = new HBox(10);
        topPanel.getChildren().addAll(langChoice, mainUserLabel, logoutButton);
        HBox.setHgrow(mainUserLabel, Priority.ALWAYS);
        HBox.setMargin(logoutButton, new Insets(0, 10, 0, 0));
        topPanel.setAlignment(Pos.CENTER_LEFT);

        VBox root = new VBox(10, topPanel, commandButtons, showCanvas ? canvas : tableView);
        root.setPadding(new Insets(10));
        root.setStyle("-fx-background-color: #333544;");

        Scene scene = new Scene(root, 800, 400);
        primaryStage.setTitle("Vehicle Client");
        primaryStage.setScene(scene);
        primaryStage.show();

        updateVehiclesFromResponse(sendRequest(new Request("show", null, currentLogin, currentPassword)));
    }

    private void updateDisplay() {
        Platform.runLater(() -> {
            VBox root = (VBox) primaryStage.getScene().getRoot();
            root.getChildren().set(2, showCanvas ? canvas : tableView); // Заменяем отображаемый элемент
            if (showCanvas) {
                updateCanvas();
            } else {
                tableView.refresh(); // Обновляем таблицу
            }
        });
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
                System.out.println("Установлен логин: " + currentLogin);
                Platform.runLater(() -> {
                    mainUserLabel.setText("Текущий пользователь: " + currentLogin);
                    System.out.println("Метка обновлена: " + mainUserLabel.getText());
                });
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
            String[] lines = message.split("\n");
            Pattern pattern = Pattern.compile(
                    "\\{id=(\\d+), name=([^,]+), coordinates=\\{x=([\\d.]+), y=(\\d+)\\}, " +
                            "creationDate=(\\d{4}_\\d{2}_\\d{2} \\d{2}:\\d{2}), " +
                            "enginePower=([\\d.]+), type=([^,]+), fuelType=([^}]+)\\}"
            );

            for (String line : lines) {
                if (line.trim().isEmpty()) continue;

                Matcher matcher = pattern.matcher(line.trim());
                if (matcher.find()) {
                    LocalDateTime localDateTime = LocalDateTime.parse(
                            matcher.group(5),
                            DateTimeFormatter.ofPattern("yyyy_MM_dd HH:mm")
                    );
                    ZonedDateTime creationDate = localDateTime.atZone(ZoneId.systemDefault());

                    Vehicle vehicle = new Vehicle(
                            Long.parseLong(matcher.group(1)),
                            new Coordinates(
                                    Float.parseFloat(matcher.group(3)),
                                    Integer.parseInt(matcher.group(4))
                            ),
                            creationDate,
                            matcher.group(2),
                            Float.parseFloat(matcher.group(6)),
                            VehicleType.valueOf(matcher.group(7)),
                            FuelType.valueOf(matcher.group(8))
                    );
                    vehicles.add(vehicle);
                    System.out.println("Добавлен объект: ID " + matcher.group(1) + ", Name: " + matcher.group(2));
                }
            }

            if (vehicles.isEmpty()) {
                System.out.println("Ни один объект не распознан");
            } else {
                System.out.println("Обновлено " + vehicles.size() + " элементов в таблице");
            }

            updateDisplay();
        } catch (Exception e) {
            System.out.println("Ошибка парсинга: " + e.getMessage());
            userLabel.setText("Ошибка парсинга данных: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void updateCanvas() {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(Color.BLACK);
        gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());

        double centerX = canvas.getWidth() / 2;
        double centerY = canvas.getHeight() / 2;

        for (Vehicle vehicle : vehicles) {
            double x = centerX + (vehicle.getCoordinates().getX() - 491); // Смещаем, чтобы 0,0 было в центре
            double y = centerY - vehicle.getCoordinates().getY(); // Инвертируем Y для нормального отображения
            Color color = getUserColor(vehicle.getId());
            double size = vehicle.getPower() / 50.0 + 10;

            for (double scale = 0.1; scale <= 1.0; scale += 0.1) {
                gc.setFill(color);
                gc.fillOval(x - size * scale / 2, y - size * scale / 2, size * scale, size * scale);
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                gc.setFill(Color.BLACK);
                gc.fillOval(x - size * scale / 2, y - size * scale / 2, size * scale, size * scale);
            }
            gc.setFill(color);
            gc.fillOval(x - size / 2, y - size / 2, size, size);
        }
    }

    private Color getUserColor(long id) {
        if (!userColors.containsKey(String.valueOf(id))) {
            Random rand = new Random();
            float r = rand.nextFloat();
            float g = rand.nextFloat();
            float b = rand.nextFloat();
            userColors.put(String.valueOf(id), new Color(r, g, b, 1.0));
        }
        return userColors.get(String.valueOf(id));
    }

    private Vehicle findVehicleAt(GraphicsContext gc, double clickX, double clickY) {
        double centerX = canvas.getWidth() / 2;
        double centerY = canvas.getHeight() / 2;

        for (Vehicle vehicle : vehicles) {
            double x = centerX + (vehicle.getCoordinates().getX() - 491);
            double y = centerY - vehicle.getCoordinates().getY();
            double size = vehicle.getPower() / 50.0 + 10;
            if (Math.abs(clickX - x) < size / 2 && Math.abs(clickY - y) < size / 2) {
                return vehicle;
            }
        }
        return null;
    }

    private void handleCanvasClick(MouseEvent event) {
        Vehicle selected = findVehicleAt(canvas.getGraphicsContext2D(), event.getX(), event.getY());
        if (selected != null) {
            Dialog<String> dialog = new Dialog<>();
            dialog.setTitle("Информация об объекте");
            dialog.setHeaderText("Детали транспортного средства");

            ButtonType okButtonType = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().addAll(okButtonType);

            GridPane grid = new GridPane();
            grid.setHgap(10);
            grid.setVgap(10);
            grid.setPadding(new Insets(20, 150, 10, 10));

            grid.add(new Label("ID:"), 0, 0);
            grid.add(new Label(String.valueOf(selected.getId())), 1, 0);
            grid.add(new Label("Название:"), 0, 1);
            grid.add(new Label(selected.getName()), 1, 1);
            grid.add(new Label("X:"), 0, 2);
            grid.add(new Label(String.valueOf(selected.getCoordinates().getX())), 1, 2);
            grid.add(new Label("Y:"), 0, 3);
            grid.add(new Label(String.valueOf(selected.getCoordinates().getY())), 1, 3);
            grid.add(new Label("Мощность:"), 0, 4);
            grid.add(new Label(String.valueOf(selected.getPower())), 1, 4);
            grid.add(new Label("Тип:"), 0, 5);
            grid.add(new Label(selected.getType().toString()), 1, 5);
            grid.add(new Label("Тип топлива:"), 0, 6);
            grid.add(new Label(selected.getFuelType().toString()), 1, 6);

            dialog.getDialogPane().setContent(grid);
            dialog.showAndWait();
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