package com.vehicleClient.application;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.*;
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
import java.util.stream.Collectors;

import com.vehicleShared.model.*;
import com.vehicleShared.network.Request;
import com.vehicleShared.network.Response;
import javafx.util.Duration;

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
    private Set<Long> displayedVehicleIds = new HashSet<>();
    private boolean showCanvas = false; // По умолчанию показываем область
    private Map<String, Color> userColors = new HashMap<>();
    private Map<Long, VehicleAnimation> vehicleAnimations = new HashMap<>();

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

        canvas = new Canvas(1000, 86); // Установили размер под область 1000x70
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(Color.web("#2d2f4a")); // Фон чуть темнее #333544
        gc.fillRect(0, 0, 1000, 86);
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
        TableColumn<Vehicle, String> ownerColumn = new TableColumn<>("Автор");
        ownerColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getOwner()));
        tableView.getColumns().addAll(idColumn, nameColumn, xColumn, yColumn, powerColumn, typeColumn, fuelColumn, ownerColumn);
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY); // Убираем лишнее пространство
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
                    isValid &= x >= 0 && x <= 1000 && y >= 0 && y <= 70;
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
                        if (x < 0 || x > 1000 || y < 0 || y > 70) {
                            userLabel.setText("Ошибка: Координаты должны быть x от 0 до 1000 и y от 0 до 70");
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
                        vehicle.setOwner(currentLogin); // Устанавливаем текущего пользователя как автора
                        return vehicle;
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
                        showNotification("Ошибка", response.getMessage());
                    }
                });
                task.setOnFailed(event -> {
                    userLabel.setText("Ошибка: " + task.getException().getMessage());
                    showNotification("Ошибка", task.getException().getMessage());
                });
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
                    showNotification("Ошибка удаления", response.getMessage());
                }
            });
            task.setOnFailed(event -> {
                userLabel.setText("Ошибка: " + task.getException().getMessage());
                showNotification("Ошибка", task.getException().getMessage());
            });
            new Thread(task).start();
        });

        updateButton.setOnAction(e -> {
            Vehicle selected = showCanvas ? findVehicleAt(canvas.getGraphicsContext2D(), canvas.getWidth() / 2, canvas.getHeight() / 2) : tableView.getSelectionModel().getSelectedItem();
            if (selected == null) {
                userLabel.setText("Выберите объект для редактирования");
                return;
            }
            if (!selected.getOwner().equals(currentLogin)) {
                showNotification("Ошибка доступа", "Вы не можете редактировать объект, созданный другим пользователем");
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
                    isValid &= x >= 0 && x <= 1000 && y >= 0 && y <= 70;
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
                        if (x < 0 || x > 1000 || y < 0 || y > 70) {
                            userLabel.setText("Ошибка: Координаты должны быть x от 0 до 1000 и y от 0 до 70");
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
                        updatedVehicle.setOwner(selected.getOwner()); // Сохраняем автора
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
                        showNotification("Ошибка обновления", response.getMessage());
                    }
                });
                task.setOnFailed(event -> {
                    userLabel.setText("Ошибка: " + task.getException().getMessage());
                    showNotification("Ошибка", task.getException().getMessage());
                });
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
                    showNotification("Ошибка", response.getMessage());
                }
            });
            task.setOnFailed(event -> {
                userLabel.setText("Ошибка: " + task.getException().getMessage());
                showNotification("Ошибка", task.getException().getMessage());
            });
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
                        showNotification("Ошибка удаления", response.getMessage());
                    }
                });
                task.setOnFailed(event -> {
                    userLabel.setText("Ошибка: " + task.getException().getMessage());
                    showNotification("Ошибка", task.getException().getMessage());
                });
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
                        showNotification("Ошибка удаления", response.getMessage());
                    }
                });
                task.setOnFailed(event -> {
                    userLabel.setText("Ошибка: " + task.getException().getMessage());
                    showNotification("Ошибка", task.getException().getMessage());
                });
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
                    showNotification("Ошибка", response.getMessage());
                }
            });
            task.setOnFailed(event -> {
                userLabel.setText("Ошибка: " + task.getException().getMessage());
                showNotification("Ошибка", task.getException().getMessage());
            });
            new Thread(task).start();
        });

        replaceIfLowerButton.setOnAction(e -> {
            Vehicle selected = showCanvas ? findVehicleAt(canvas.getGraphicsContext2D(), canvas.getWidth() / 2, canvas.getHeight() / 2) : tableView.getSelectionModel().getSelectedItem();
            if (selected == null) {
                userLabel.setText("Выберите объект для замены");
                return;
            }
            if (!selected.getOwner().equals(currentLogin)) {
                showNotification("Ошибка доступа", "Вы не можете заменить объект, созданный другим пользователем");
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
                    isValid &= x >= 0 && x <= 1000 && y >= 0 && y <= 70;
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
                        if (x < 0 || x > 1000 || y < 0 || y > 70) {
                            userLabel.setText("Ошибка: Координаты должны быть x от 0 до 1000 и y от 0 до 70");
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
                        newVehicle.setOwner(selected.getOwner()); // Сохраняем автора
                        return newVehicle;
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
                        showNotification("Ошибка замены", response.getMessage());
                    }
                });
                task.setOnFailed(event -> {
                    userLabel.setText("Ошибка: " + task.getException().getMessage());
                    showNotification("Ошибка", task.getException().getMessage());
                });
                new Thread(task).start();
            });
        });

        toggleViewButton.setOnAction(e -> {
            showCanvas = !showCanvas;
            toggleViewButton.setText(showCanvas ? "Переключить на таблицу" : "Переключить на область");
            updateDisplay();
        });

        logoutButton.setOnAction(e -> {
            clearDisplayedVehicles();
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

        Scene scene = new Scene(root, 1000, 70); // Установили размер сцены под область
        primaryStage.setMinWidth(1050); // Минимальная ширина
        primaryStage.setMinHeight(300); // Увеличенная минимальная высота (в два раза)
        primaryStage.setTitle("Vehicle Client");
        primaryStage.setScene(scene);
        primaryStage.show();

        updateVehiclesFromResponse(sendRequest(new Request("show", null, currentLogin, currentPassword)));
    }

    private void showNotification(String title, String message) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(message);

        ButtonType okButtonType = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(okButtonType);

        dialog.getDialogPane().setStyle("-fx-background-color: #333544; -fx-text-fill: white;");
        dialog.getDialogPane().lookupButton(okButtonType).setStyle("-fx-background-color: #d24d46; -fx-text-fill: white;");

        dialog.showAndWait();
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
        clearDisplayedVehicles();
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

        // Сохраняем текущие углы перед очисткой
        Map<Long, Double> existingRotations = new HashMap<>();
        for (Vehicle vehicle : vehicles) {
            if (vehicle.getRotationAngle() != null) {
                existingRotations.put(vehicle.getId(), vehicle.getRotationAngle());
            }
        }

        vehicles.clear();

        try {
            String[] lines = message.split("\n");
            Pattern pattern = Pattern.compile(
                    "\\{id=(\\d+), name=([^,]+), coordinates=\\{x=([\\d.]+), y=(\\d+)\\}, " +
                            "creationDate=(\\d{4}_\\d{2}_\\d{2} \\d{2}:\\d{2}), " +
                            "enginePower=([\\d.]+), type=([^,]+), fuelType=([^,]+), owner=([^}]+)\\}"
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
                    vehicle.setOwner(matcher.group(9)); // Устанавливаем владельца
                    // Восстанавливаем сохранённый угол, если есть
                    if (existingRotations.containsKey(vehicle.getId())) {
                        vehicle.setRotationAngle(existingRotations.get(vehicle.getId()));
                    }
                    vehicles.add(vehicle);
                    System.out.println("Добавлен объект: ID " + matcher.group(1) + ", Name: " + matcher.group(2) + ", Owner: " + matcher.group(9));
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

        // Полная очистка холста
        gc.setFill(Color.web("#2d2f4a"));
        gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());

        double offsetX = 10;
        double offsetY = 10;
        Random random = new Random();

        // Очистка старых анимаций
        vehicleAnimations.keySet().removeIf(vehicleId ->
                vehicles.stream().noneMatch(v -> v.getId() == vehicleId)
        );

        // 1. Отрисовка статичных объектов
        for (Vehicle vehicle : vehicles) {
            if (displayedVehicleIds.contains(vehicle.getId())) {
                drawStaticVehicle(gc, vehicle, offsetX, offsetY);
            }
        }

        // 2. Анимация новых объектов
        List<Vehicle> newVehicles = vehicles.stream()
                .filter(v -> !displayedVehicleIds.contains(v.getId()))
                .collect(Collectors.toList());

        for (int i = 0; i < newVehicles.size(); i++) {
            Vehicle vehicle = newVehicles.get(i);

            if (vehicleAnimations.containsKey(vehicle.getId())) {
                continue;
            }

            double targetX = vehicle.getCoordinates().getX() + offsetX;
            double targetY = vehicle.getCoordinates().getY() + offsetY;
            Color color = getUserColor(vehicle.getOwner());
            double size = vehicle.getPower() / 50.0 + 10;

            // Случайное направление появления
            double startX, startY;
            int direction = random.nextInt(4);
            switch (direction) {
                case 0: // слева
                    startX = -size;
                    startY = targetY + random.nextDouble() * 50 - 25;
                    break;
                case 1: // справа
                    startX = canvas.getWidth() + size;
                    startY = targetY + random.nextDouble() * 50 - 25;
                    break;
                case 2: // сверху
                    startX = targetX + random.nextDouble() * 50 - 25;
                    startY = -size;
                    break;
                default: // снизу
                    startX = targetX + random.nextDouble() * 50 - 25;
                    startY = canvas.getHeight() + size;
            }

            DoubleProperty x = new SimpleDoubleProperty(startX);
            DoubleProperty y = new SimpleDoubleProperty(startY);
            DoubleProperty scale = new SimpleDoubleProperty(0.1);
            DoubleProperty opacity = new SimpleDoubleProperty(0.0);
            DoubleProperty angle = new SimpleDoubleProperty(vehicle.getRotationAngle() != null ? vehicle.getRotationAngle() : random.nextDouble() * 360);

            // Для эффекта шлейфа
            List<Point> trailPositions = new ArrayList<>();
            List<Double> trailAngles = new ArrayList<>();

            Timeline animation = new Timeline(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(x, startX),
                            new KeyValue(y, startY),
                            new KeyValue(scale, 0.1),
                            new KeyValue(opacity, 0.0),
                            new KeyValue(angle, vehicle.getRotationAngle() != null ? vehicle.getRotationAngle() : random.nextDouble() * 360)
                    ),
                    new KeyFrame(Duration.millis(100),
                            new KeyValue(opacity, 1.0)
                    ),
                    new KeyFrame(Duration.millis(800),
                            new KeyValue(x, targetX, Interpolator.EASE_OUT),
                            new KeyValue(y, targetY, Interpolator.EASE_OUT),
                            new KeyValue(scale, 1.0, Interpolator.EASE_OUT),
                            new KeyValue(angle, vehicle.getRotationAngle() != null ? vehicle.getRotationAngle() : random.nextDouble() * 360, Interpolator.EASE_BOTH)
                    )
            );

            animation.currentTimeProperty().addListener((obs, oldVal, newVal) -> {
                // Сохраняем позиции и углы для шлейфа
                if (newVal.toMillis() % 10 == 0) {
                    trailPositions.add(new Point(x.get(), y.get()));
                    trailAngles.add(angle.get());
                    if (trailPositions.size() > 15) {
                        trailPositions.remove(0);
                        trailAngles.remove(0);
                    }
                }

                // Очистка холста
                gc.setFill(Color.web("#2d2f4a"));
                gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());

                // Отрисовка статичных объектов
                for (Vehicle v : vehicles) {
                    if (displayedVehicleIds.contains(v.getId())) {
                        drawStaticVehicle(gc, v, offsetX, offsetY);
                    }
                }

                // Отрисовка шлейфа
                for (int j = 0; j < trailPositions.size(); j++) {
                    Point p = trailPositions.get(j);
                    double trailOpacity = 0.2 * (j / (double)trailPositions.size());
                    double trailSize = size * (0.3 + 0.7 * (j / (double)trailPositions.size()));

                    gc.save();
                    gc.translate(p.x, p.y);
                    gc.rotate(trailAngles.get(j));
                    gc.translate(-p.x, -p.y);

                    gc.setFill(Color.color(color.getRed(), color.getGreen(), color.getBlue(), trailOpacity));
                    drawVehicle(gc, p.x, p.y, trailSize, vehicle.getType(), 0.7);
                    gc.restore();
                }

                // Отрисовка анимируемых объектов
                for (VehicleAnimation anim : vehicleAnimations.values()) {
                    drawAnimatedVehicle(gc, anim);
                }
            });

            animation.setOnFinished(e -> {
                displayedVehicleIds.add(vehicle.getId());
                vehicle.setRotationAngle(angle.get()); // Сохраняем угол после анимации
                vehicleAnimations.remove(vehicle.getId());
                Platform.runLater(this::updateCanvas);
            });

            animation.setDelay(Duration.millis(i * 150));

            vehicleAnimations.put(vehicle.getId(),
                    new VehicleAnimation(x, y, scale, opacity, angle, animation, vehicle));
            animation.play();
        }
    }

    private void drawStaticVehicle(GraphicsContext gc, Vehicle vehicle,
                                   double offsetX, double offsetY) {
        double x = vehicle.getCoordinates().getX() + offsetX;
        double y = vehicle.getCoordinates().getY() + offsetY;
        Color color = getUserColor(vehicle.getOwner());
        double size = vehicle.getPower() / 50.0 + 10;
        double angle = vehicle.getRotationAngle() != null ? vehicle.getRotationAngle() : 0; // Используем сохранённый угол

        gc.save();
        gc.translate(x, y);
        gc.rotate(angle);
        gc.translate(-x, -y);
        gc.setFill(color);
        drawVehicle(gc, x, y, size, vehicle.getType(), 1.0);
        gc.restore();
    }

    private void drawAnimatedVehicle(GraphicsContext gc, VehicleAnimation anim) {
        Vehicle vehicle = anim.vehicle;
        Color color = Color.color(
                getUserColor(vehicle.getOwner()).getRed(),
                getUserColor(vehicle.getOwner()).getGreen(),
                getUserColor(vehicle.getOwner()).getBlue(),
                anim.opacity.get()
        );
        double size = vehicle.getPower() / 50.0 + 10;

        gc.save();
        gc.translate(anim.x.get(), anim.y.get());
        gc.rotate(anim.angle.get());
        gc.translate(-anim.x.get(), -anim.y.get());
        gc.setFill(color);
        drawVehicle(gc, anim.x.get(), anim.y.get(), size,
                vehicle.getType(), anim.scale.get());
        gc.restore();
    }

    private static class Point {
        double x, y;
        public Point(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }

    private static class VehicleAnimation {
        DoubleProperty x, y, scale, opacity, angle;
        Timeline timeline;
        Vehicle vehicle;

        public VehicleAnimation(DoubleProperty x, DoubleProperty y,
                                DoubleProperty scale, DoubleProperty opacity,
                                DoubleProperty angle, Timeline timeline,
                                Vehicle vehicle) {
            this.x = x;
            this.y = y;
            this.scale = scale;
            this.opacity = opacity;
            this.angle = angle;
            this.timeline = timeline;
            this.vehicle = vehicle;
        }
    }

    private Map<Long, Timeline> currentAnimations = new HashMap<>();

    private void clearDisplayedVehicles() {
        displayedVehicleIds.clear();
        if (currentAnimations != null) {
            currentAnimations.values().forEach(Timeline::stop);
            currentAnimations.clear();
        }
    }

    private void drawVehicle(GraphicsContext gc, double x, double y, double size, VehicleType type, double scale) {
        double scaledSize = size * scale;
        switch (type) {
            case CAR:
                gc.fillRect(x - scaledSize / 2, y - scaledSize / 4, scaledSize, scaledSize / 2);
                break;
            case BOAT:
                double[] xPoints = {x, x - scaledSize / 2, x + scaledSize / 2};
                double[] yPoints = {y - scaledSize / 4, y + scaledSize / 4, y + scaledSize / 4};
                gc.fillPolygon(xPoints, yPoints, 3);
                break;
            case HOVERBOARD:
                gc.fillOval(x - scaledSize / 2, y - scaledSize / 2, scaledSize, scaledSize);
                break;
        }
    }

    private Vehicle findVehicleAt(GraphicsContext gc, double clickX, double clickY) {
        double offsetX = 10; // Отступ для соответствия с отрисовкой
        double offsetY = 10; // Отступ для соответствия с отрисовкой

        for (Vehicle vehicle : vehicles) {
            double x = vehicle.getCoordinates().getX() + offsetX;
            double y = vehicle.getCoordinates().getY() + offsetY;
            double size = vehicle.getPower() / 50.0 + 10;
            if (Math.abs(clickX - x) < size / 2 && Math.abs(clickY - y) < size / 2) {
                return vehicle;
            }
        }
        return null;
    }

    private Color getUserColor(String username) {
        if (!userColors.containsKey(username)) {
            Random rand = new Random(username.hashCode());
            float r = rand.nextFloat();
            float g = rand.nextFloat();
            float b = rand.nextFloat();
            userColors.put(username, new Color(r, g, b, 1.0));
        }
        return userColors.get(username);
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
