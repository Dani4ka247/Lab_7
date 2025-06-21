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
import java.text.NumberFormat;
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
    private Label usernameLabel = new Label();
    private Label passwordLabel = new Label();
    private Button loginButton = new Button();
    private Button registerButton = new Button();
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
    private boolean showCanvas = false;
    private Map<String, Color> userColors = new HashMap<>();
    private Map<Long, VehicleAnimation> vehicleAnimations = new HashMap<>();
    private ResourceBundle messages;
    private Locale currentLocale;
    private NumberFormat numberFormat;
    private DateTimeFormatter dateTimeFormatter;

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        setLocale(new Locale("ru"));
        showLoginWindow();
    }

    private void setLocale(Locale locale) {
        currentLocale = locale;
        messages = ResourceBundle.getBundle("messages", locale);
        numberFormat = NumberFormat.getInstance(locale);
        dateTimeFormatter = DateTimeFormatter.ofPattern(messages.getString("datetime.format"), locale);
        updateUI();
    }

    private void updateUI() {
        if (loginStage != null && loginStage.isShowing()) {
            updateLoginWindow();
        }
        if (primaryStage != null && primaryStage.isShowing()) {
            updateMainWindow();
        }
    }

    private void updateLoginWindow() {
        usernameLabel.setText(messages.getString("username"));
        passwordLabel.setText(messages.getString("password"));
        loginButton.setText(messages.getString("login"));
        registerButton.setText(messages.getString("register"));
        loginStage.setTitle(messages.getString("login.title"));
    }

    private void showLoginWindow() {
        loginStage = new Stage();
        langChoice.getItems().clear();
        langChoice.getItems().addAll("ru", "cs", "ca", "en_ZA");
        langChoice.setValue(currentLocale.toString());
        langChoice.setOnAction(e -> {
            String selectedLang = langChoice.getValue();
            Locale newLocale = switch (selectedLang) {
                case "cs" -> new Locale("cs");
                case "ca" -> new Locale("ca");
                case "en_ZA" -> new Locale("en", "ZA");
                default -> new Locale("ru");
            };
            setLocale(newLocale);
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
        updateLoginWindow();
        loginStage.setScene(loginScene);
        loginStage.show();

        connectToServer();
    }

    private void updateMainWindow() {
        primaryStage.setTitle(messages.getString("main.title"));
        mainUserLabel.setText(messages.getString("current.user") + (currentLogin != null ? currentLogin : messages.getString("not.authorized")));
        addButton.setText(messages.getString("add"));
        removeButton.setText(messages.getString("remove"));
        updateButton.setText(messages.getString("update"));
        clearButton.setText(messages.getString("clear"));
        logoutButton.setText(messages.getString("logout"));
        removeByPowerButton.setText(messages.getString("remove.by.power"));
        removeGreaterKeyButton.setText(messages.getString("remove.greater.key"));
        sumOfPowerButton.setText(messages.getString("sum.of.power"));
        replaceIfLowerButton.setText(messages.getString("replace.if.lower"));
        toggleViewButton.setText(showCanvas ? messages.getString("switch.to.table") : messages.getString("switch.to.canvas"));

        TableColumn<Vehicle, Long> idColumn = (TableColumn<Vehicle, Long>) tableView.getColumns().get(0);
        TableColumn<Vehicle, String> nameColumn = (TableColumn<Vehicle, String>) tableView.getColumns().get(1);
        TableColumn<Vehicle, Float> xColumn = (TableColumn<Vehicle, Float>) tableView.getColumns().get(2);
        TableColumn<Vehicle, Integer> yColumn = (TableColumn<Vehicle, Integer>) tableView.getColumns().get(3);
        TableColumn<Vehicle, Float> powerColumn = (TableColumn<Vehicle, Float>) tableView.getColumns().get(4);
        TableColumn<Vehicle, String> typeColumn = (TableColumn<Vehicle, String>) tableView.getColumns().get(5);
        TableColumn<Vehicle, String> fuelColumn = (TableColumn<Vehicle, String>) tableView.getColumns().get(6);
        TableColumn<Vehicle, String> ownerColumn = (TableColumn<Vehicle, String>) tableView.getColumns().get(7);

        idColumn.setText(messages.getString("id"));
        nameColumn.setText(messages.getString("name"));
        xColumn.setText(messages.getString("x"));
        yColumn.setText(messages.getString("y"));
        powerColumn.setText(messages.getString("power"));
        typeColumn.setText(messages.getString("type"));
        fuelColumn.setText(messages.getString("fuel"));
        ownerColumn.setText(messages.getString("owner"));
    }

    private void showMainWindow() {
        loginStage.close();

        canvas = new Canvas(1000, 86);
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(Color.web("#2d2f4a"));
        gc.fillRect(0, 0, 1000, 86);
        canvas.setOnMouseClicked(this::handleCanvasClick);

        tableView = new TableView<>();
        TableColumn<Vehicle, Long> idColumn = new TableColumn<>();
        idColumn.setCellValueFactory(cellData -> new SimpleLongProperty(cellData.getValue().getId()).asObject());
        TableColumn<Vehicle, String> nameColumn = new TableColumn<>();
        nameColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getName()));
        TableColumn<Vehicle, Float> xColumn = new TableColumn<>();
        xColumn.setCellValueFactory(cellData -> new SimpleFloatProperty(cellData.getValue().getCoordinates().getX()).asObject());
        TableColumn<Vehicle, Integer> yColumn = new TableColumn<>();
        yColumn.setCellValueFactory(cellData -> new SimpleIntegerProperty(cellData.getValue().getCoordinates().getY()).asObject());
        TableColumn<Vehicle, Float> powerColumn = new TableColumn<>();
        powerColumn.setCellValueFactory(cellData -> new SimpleFloatProperty(cellData.getValue().getPower()).asObject());
        TableColumn<Vehicle, String> typeColumn = new TableColumn<>();
        typeColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getType().toString()));
        TableColumn<Vehicle, String> fuelColumn = new TableColumn<>();
        fuelColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getFuelType().toString()));
        TableColumn<Vehicle, String> ownerColumn = new TableColumn<>();
        ownerColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getOwner()));
        tableView.getColumns().addAll(idColumn, nameColumn, xColumn, yColumn, powerColumn, typeColumn, fuelColumn, ownerColumn);
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tableView.setItems(vehicles);

        addButton = new Button();
        removeButton = new Button();
        updateButton = new Button();
        clearButton = new Button();
        logoutButton = new Button();
        removeByPowerButton = new Button();
        removeGreaterKeyButton = new Button();
        sumOfPowerButton = new Button();
        replaceIfLowerButton = new Button();
        toggleViewButton = new Button();

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
            dialog.setTitle(messages.getString("add.vehicle.title"));
            dialog.setHeaderText(messages.getString("add.vehicle.header"));

            ButtonType addButtonType = new ButtonType(messages.getString("add"), ButtonBar.ButtonData.OK_DONE);
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

            grid.add(new Label(messages.getString("name") + ":"), 0, 0);
            grid.add(nameField, 1, 0);
            grid.add(new Label(messages.getString("x") + ":"), 0, 1);
            grid.add(xField, 1, 1);
            grid.add(new Label(messages.getString("y") + ":"), 0, 2);
            grid.add(yField, 1, 2);
            grid.add(new Label(messages.getString("power") + ":"), 0, 3);
            grid.add(powerField, 1, 3);
            grid.add(new Label(messages.getString("type") + ":"), 0, 4);
            grid.add(typeCombo, 1, 4);
            grid.add(new Label(messages.getString("fuel") + ":"), 0, 5);
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
                            userLabel.setText(messages.getString("error.coordinates"));
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
                        vehicle.setOwner(currentLogin);
                        return vehicle;
                    } catch (Exception ex) {
                        userLabel.setText(messages.getString("error.format"));
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
                        userLabel.setText(messages.getString("object.added"));
                        updateVehiclesFromResponse(sendRequest(new Request("show", null, currentLogin, currentPassword)));
                    } else {
                        userLabel.setText(messages.getString("error") + ": " + response.getMessage());
                        showNotification(messages.getString("error"), response.getMessage());
                    }
                });
                task.setOnFailed(event -> {
                    userLabel.setText(messages.getString("error") + ": " + task.getException().getMessage());
                    showNotification(messages.getString("error"), task.getException().getMessage());
                });
                new Thread(task).start();
            });
        });

        removeButton.setOnAction(e -> {
            Vehicle selected = showCanvas ? findVehicleAt(canvas.getGraphicsContext2D(), canvas.getWidth() / 2, canvas.getHeight() / 2) : tableView.getSelectionModel().getSelectedItem();
            if (selected == null) {
                userLabel.setText(messages.getString("error.select.remove"));
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
                    userLabel.setText(messages.getString("object.removed") + ": ID " + selected.getId());
                    updateVehiclesFromResponse(sendRequest(new Request("show", null, currentLogin, currentPassword)));
                } else {
                    userLabel.setText(messages.getString("error.remove") + ": " + response.getMessage());
                    showNotification(messages.getString("error.remove"), response.getMessage());
                }
            });
            task.setOnFailed(event -> {
                userLabel.setText(messages.getString("error") + ": " + task.getException().getMessage());
                showNotification(messages.getString("error"), task.getException().getMessage());
            });
            new Thread(task).start();
        });

        updateButton.setOnAction(e -> {
            Vehicle selected = showCanvas ? findVehicleAt(canvas.getGraphicsContext2D(), canvas.getWidth() / 2, canvas.getHeight() / 2) : tableView.getSelectionModel().getSelectedItem();
            if (selected == null) {
                userLabel.setText(messages.getString("error.select.update"));
                return;
            }
            if (!selected.getOwner().equals(currentLogin)) {
                showNotification(messages.getString("error.access"), messages.getString("error.not.owner"));
                return;
            }

            Dialog<Vehicle> dialog = new Dialog<>();
            dialog.setTitle(messages.getString("update.vehicle.title"));
            dialog.setHeaderText(messages.getString("update.vehicle.header"));

            ButtonType updateButtonType = new ButtonType(messages.getString("update"), ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().addAll(updateButtonType, ButtonType.CANCEL);

            GridPane grid = new GridPane();
            grid.setHgap(10);
            grid.setVgap(10);
            grid.setPadding(new Insets(20, 150, 10, 10));

            TextField nameField = new TextField(selected.getName());
            TextField xField = new TextField(numberFormat.format(selected.getCoordinates().getX()));
            TextField yField = new TextField(String.valueOf(selected.getCoordinates().getY()));
            TextField powerField = new TextField(numberFormat.format(selected.getPower()));
            ComboBox<VehicleType> typeCombo = new ComboBox<>();
            typeCombo.getItems().setAll(VehicleType.values());
            typeCombo.setValue(selected.getType());
            ComboBox<FuelType> fuelCombo = new ComboBox<>();
            fuelCombo.getItems().setAll(FuelType.values());
            fuelCombo.setValue(selected.getFuelType());

            grid.add(new Label(messages.getString("name") + ":"), 0, 0);
            grid.add(nameField, 1, 0);
            grid.add(new Label(messages.getString("x") + ":"), 0, 1);
            grid.add(xField, 1, 1);
            grid.add(new Label(messages.getString("y") + ":"), 0, 2);
            grid.add(yField, 1, 2);
            grid.add(new Label(messages.getString("power") + ":"), 0, 3);
            grid.add(powerField, 1, 3);
            grid.add(new Label(messages.getString("type") + ":"), 0, 4);
            grid.add(typeCombo, 1, 4);
            grid.add(new Label(messages.getString("fuel") + ":"), 0, 5);
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
                    float x = numberFormat.parse(xField.getText()).floatValue();
                    int y = Integer.parseInt(yField.getText());
                    isValid &= x >= 0 && x <= 1000 && y >= 0 && y <= 70;
                } catch (Exception ex) {
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
                        float x = numberFormat.parse(xField.getText()).floatValue();
                        int y = Integer.parseInt(yField.getText());
                        if (x < 0 || x > 1000 || y < 0 || y > 70) {
                            userLabel.setText(messages.getString("error.coordinates"));
                            return null;
                        }
                        Vehicle updatedVehicle = new Vehicle(
                                selected.getId(),
                                new Coordinates(x, y),
                                nameField.getText(),
                                numberFormat.parse(powerField.getText()).floatValue(),
                                typeCombo.getValue(),
                                fuelCombo.getValue()
                        );
                        updatedVehicle.setCreationDate(selected.getCreationDate());
                        updatedVehicle.setOwner(selected.getOwner());
                        return updatedVehicle;
                    } catch (Exception ex) {
                        userLabel.setText(messages.getString("error.format"));
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
                        userLabel.setText(messages.getString("object.updated"));
                        updateVehiclesFromResponse(sendRequest(new Request("show", null, currentLogin, currentPassword)));
                    } else {
                        userLabel.setText(messages.getString("error") + ": " + response.getMessage());
                        showNotification(messages.getString("error.update"), response.getMessage());
                    }
                });
                task.setOnFailed(event -> {
                    userLabel.setText(messages.getString("error") + ": " + task.getException().getMessage());
                    showNotification(messages.getString("error"), task.getException().getMessage());
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
                    userLabel.setText(messages.getString("collection.cleared"));
                    updateVehiclesFromResponse(sendRequest(new Request("show", null, currentLogin, currentPassword)));
                } else {
                    userLabel.setText(messages.getString("error") + ": " + response.getMessage());
                    showNotification(messages.getString("error"), response.getMessage());
                }
            });
            task.setOnFailed(event -> {
                userLabel.setText(messages.getString("error") + ": " + task.getException().getMessage());
                showNotification(messages.getString("error"), task.getException().getMessage());
            });
            new Thread(task).start();
        });

        removeByPowerButton.setOnAction(e -> {
            Dialog<Float> dialog = new Dialog<>();
            dialog.setTitle(messages.getString("remove.by.power.title"));
            dialog.setHeaderText(messages.getString("remove.by.power.header"));

            ButtonType removeButtonType = new ButtonType(messages.getString("remove"), ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().addAll(removeButtonType, ButtonType.CANCEL);

            GridPane grid = new GridPane();
            grid.setHgap(10);
            grid.setVgap(10);
            grid.setPadding(new Insets(20, 150, 10, 10));

            TextField powerField = new TextField();

            grid.add(new Label(messages.getString("power") + ":"), 0, 0);
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
                        return numberFormat.parse(powerField.getText().trim()).floatValue();
                    } catch (Exception ex) {
                        userLabel.setText(messages.getString("error.format"));
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
                        userLabel.setText(messages.getString("objects.removed.power") + numberFormat.format(power));
                        updateVehiclesFromResponse(sendRequest(new Request("show", null, currentLogin, currentPassword)));
                    } else {
                        userLabel.setText(messages.getString("error") + ": " + response.getMessage());
                        showNotification(messages.getString("error.remove"), response.getMessage());
                    }
                });
                task.setOnFailed(event -> {
                    userLabel.setText(messages.getString("error") + ": " + task.getException().getMessage());
                    showNotification(messages.getString("error"), task.getException().getMessage());
                });
                new Thread(task).start();
            });
        });

        removeGreaterKeyButton.setOnAction(e -> {
            Dialog<Long> dialog = new Dialog<>();
            dialog.setTitle(messages.getString("remove.greater.key.title"));
            dialog.setHeaderText(messages.getString("remove.greater.key.header"));

            ButtonType removeButtonType = new ButtonType(messages.getString("remove"), ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().addAll(removeButtonType, ButtonType.CANCEL);

            GridPane grid = new GridPane();
            grid.setHgap(10);
            grid.setVgap(10);
            grid.setPadding(new Insets(20, 150, 10, 10));

            TextField keyField = new TextField();

            grid.add(new Label(messages.getString("id") + ":"), 0, 0);
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
                        userLabel.setText(messages.getString("error.format"));
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
                        userLabel.setText(messages.getString("objects.removed.key") + key);
                        updateVehiclesFromResponse(sendRequest(new Request("show", null, currentLogin, currentPassword)));
                    } else {
                        userLabel.setText(messages.getString("error") + ": " + response.getMessage());
                        showNotification(messages.getString("error.remove"), response.getMessage());
                    }
                });
                task.setOnFailed(event -> {
                    userLabel.setText(messages.getString("error") + ": " + task.getException().getMessage());
                    showNotification(messages.getString("error"), task.getException().getMessage());
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
                    dialog.setTitle(messages.getString("sum.of.power.title"));
                    dialog.setHeaderText(messages.getString("sum.of.power.header"));

                    ButtonType okButtonType = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
                    dialog.getDialogPane().getButtonTypes().addAll(okButtonType);

                    GridPane grid = new GridPane();
                    grid.setHgap(10);
                    grid.setVgap(10);
                    grid.setPadding(new Insets(20, 150, 10, 10));

                    try {
                        // Extract the numeric part from the response message
                        String message = response.getMessage();
                        // Use regex to extract the number (e.g., "185.0" or "185,0")
                        Pattern pattern = Pattern.compile("[\\d,.]+");
                        Matcher matcher = pattern.matcher(message);
                        if (matcher.find()) {
                            String numberStr = matcher.group();
                            // Parse the number using the locale-specific numberFormat
                            double powerSum = numberFormat.parse(numberStr).doubleValue();
                            // Format the number for display
                            Label resultLabel = new Label(messages.getString("sum.of.power.result") + numberFormat.format(powerSum));
                            grid.add(resultLabel, 0, 0);
                        } else {
                            throw new NumberFormatException("No valid number found in response: " + message);
                        }
                    } catch (Exception ex) {
                        userLabel.setText(messages.getString("error.parsing.data") + ex.getMessage());
                        showNotification(messages.getString("error"), messages.getString("error.parsing.data") + ex.getMessage());
                        return;
                    }

                    dialog.getDialogPane().setContent(grid);
                    dialog.showAndWait();
                } else {
                    userLabel.setText(messages.getString("error") + ": " + response.getMessage());
                    showNotification(messages.getString("error"), response.getMessage());
                }
            });
            task.setOnFailed(event -> {
                userLabel.setText(messages.getString("error") + ": " + task.getException().getMessage());
                showNotification(messages.getString("error"), task.getException().getMessage());
            });
            new Thread(task).start();
        });

        replaceIfLowerButton.setOnAction(e -> {
            Vehicle selected = showCanvas ? findVehicleAt(canvas.getGraphicsContext2D(), canvas.getWidth() / 2, canvas.getHeight() / 2) : tableView.getSelectionModel().getSelectedItem();
            if (selected == null) {
                userLabel.setText(messages.getString("error.select.replace"));
                return;
            }
            if (!selected.getOwner().equals(currentLogin)) {
                showNotification(messages.getString("error.access"), messages.getString("error.not.owner"));
                return;
            }

            Dialog<Vehicle> dialog = new Dialog<>();
            dialog.setTitle(messages.getString("replace.if.lower.title"));
            dialog.setHeaderText(messages.getString("replace.if.lower.header"));

            ButtonType replaceButtonType = new ButtonType(messages.getString("replace"), ButtonBar.ButtonData.OK_DONE);
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

            grid.add(new Label(messages.getString("name") + ":"), 0, 0);
            grid.add(nameField, 1, 0);
            grid.add(new Label(messages.getString("x") + ":"), 0, 1);
            grid.add(xField, 1, 1);
            grid.add(new Label(messages.getString("y") + ":"), 0, 2);
            grid.add(yField, 1, 2);
            grid.add(new Label(messages.getString("power") + ":"), 0, 3);
            grid.add(powerField, 1, 3);
            grid.add(new Label(messages.getString("type") + ":"), 0, 4);
            grid.add(typeCombo, 1, 4);
            grid.add(new Label(messages.getString("fuel") + ":"), 0, 5);
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
                    float x = numberFormat.parse(xField.getText()).floatValue();
                    int y = Integer.parseInt(yField.getText());
                    isValid &= x >= 0 && x <= 1000 && y >= 0 && y <= 70;
                } catch (Exception ex) {
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
                        float x = numberFormat.parse(xField.getText()).floatValue();
                        int y = Integer.parseInt(yField.getText());
                        if (x < 0 || x > 1000 || y < 0 || y > 70) {
                            userLabel.setText(messages.getString("error.coordinates"));
                            return null;
                        }
                        Vehicle newVehicle = new Vehicle(
                                selected.getId(),
                                new Coordinates(x, y),
                                nameField.getText(),
                                numberFormat.parse(powerField.getText()).floatValue(),
                                typeCombo.getValue(),
                                fuelCombo.getValue()
                        );
                        newVehicle.setCreationDate(selected.getCreationDate());
                        newVehicle.setOwner(selected.getOwner());
                        return newVehicle;
                    } catch (Exception ex) {
                        userLabel.setText(messages.getString("error.format"));
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
                        userLabel.setText(messages.getString("object.replaced"));
                        updateVehiclesFromResponse(sendRequest(new Request("show", null, currentLogin, currentPassword)));
                    } else {
                        userLabel.setText(messages.getString("error") + ": " + response.getMessage());
                        showNotification(messages.getString("error.replace"), response.getMessage());
                    }
                });
                task.setOnFailed(event -> {
                    userLabel.setText(messages.getString("error") + ": " + task.getException().getMessage());
                    showNotification(messages.getString("error"), task.getException().getMessage());
                });
                new Thread(task).start();
            });
        });

        toggleViewButton.setOnAction(e -> {
            showCanvas = !showCanvas;
            toggleViewButton.setText(showCanvas ? messages.getString("switch.to.table") : messages.getString("switch.to.canvas"));
            updateDisplay();
        });

        logoutButton.setOnAction(e -> {
            clearDisplayedVehicles();
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle(messages.getString("logout.confirm.title"));
            alert.setHeaderText(messages.getString("logout.confirm.header"));
            alert.setContentText(messages.getString("logout.confirm.content"));

            if (alert.showAndWait().get() == ButtonType.OK) {
                currentLogin = null;
                currentPassword = null;
                vehicles.clear();
                if (socketChannel != null && socketChannel.isOpen()) {
                    try {
                        socketChannel.close();
                        System.out.println(messages.getString("server.connection.closed"));
                    } catch (IOException ex) {
                        System.out.println(messages.getString("error.close.connection") + ex.getMessage());
                    }
                }
                primaryStage.close();
                showLoginWindow();
            }
        });

        mainUserLabel.setStyle("-fx-text-fill: white;");

        HBox topPanel = new HBox(10);
        topPanel.getChildren().addAll(langChoice, mainUserLabel, toggleViewButton, logoutButton);
        HBox.setHgrow(mainUserLabel, Priority.ALWAYS);
        HBox.setMargin(toggleViewButton, new Insets(0, 10, 0, 0)); // Отступ для toggleViewButton
        HBox.setMargin(logoutButton, new Insets(0, 10, 0, 0));
        topPanel.setAlignment(Pos.CENTER_LEFT);

        VBox root = new VBox(10, topPanel, commandButtons, showCanvas ? canvas : tableView);
        root.setPadding(new Insets(10));
        root.setStyle("-fx-background-color: #333544;");

        Scene scene = new Scene(root, 1000, 70);
        primaryStage.setMinWidth(1050);
        primaryStage.setMinHeight(300);
        updateMainWindow();
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
            root.getChildren().set(2, showCanvas ? canvas : tableView);
            if (showCanvas) {
                updateCanvas();
            } else {
                tableView.refresh();
            }
        });
    }

    private void connectToServer() {
        try {
            socketChannel = SocketChannel.open();
            socketChannel.connect(new InetSocketAddress("localhost", 6969));
            System.out.println(messages.getString("server.connected"));
        } catch (IOException e) {
            System.out.println(messages.getString("error.connect") + e.getMessage());
            userLabel.setText(messages.getString("error.connect") + e.getMessage());
        }
    }

    private void handleAuth(String command) {
        clearDisplayedVehicles();
        String login = usernameField.getText().trim();
        String password = passwordField.getText().trim();
        if (login.isEmpty() || password.isEmpty()) {
            userLabel.setText(messages.getString("error.empty.credentials"));
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
            System.out.println(messages.getString("auth.response") + response);
            if (response.isSuccess()) {
                currentLogin = login;
                currentPassword = password;
                System.out.println(messages.getString("login.set") + currentLogin);
                Platform.runLater(() -> {
                    mainUserLabel.setText(messages.getString("current.user") + currentLogin);
                    System.out.println(messages.getString("label.updated") + mainUserLabel.getText());
                });
                showMainWindow();
                updateVehiclesFromResponse(sendRequest(new Request("show", null, currentLogin, currentPassword)));
            } else {
                userLabel.setText(messages.getString("error.auth") + response.getMessage());
            }
        });
        task.setOnFailed(event -> userLabel.setText(messages.getString("error") + ": " + task.getException().getMessage()));
        new Thread(task).start();
    }

    private Response sendRequest(Request request) {
        try {
            if (socketChannel == null || !socketChannel.isOpen()) {
                return Response.error(messages.getString("error.channel.closed"));
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
                    return Response.error(messages.getString("error.server.disconnected"));
                }
                totalBytesRead += bytesRead;
            }
            lengthBuffer.flip();
            int length = lengthBuffer.getInt();
            if (length <= 0 || length > 1_000_000) {
                return Response.error(messages.getString("error.invalid.response"));
            }

            ByteBuffer dataBuffer = ByteBuffer.allocate(length);
            totalBytesRead = 0;
            while (totalBytesRead < length) {
                int bytesRead = socketChannel.read(dataBuffer);
                if (bytesRead == -1) {
                    return Response.error(messages.getString("error.server.disconnected"));
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
            System.out.println(messages.getString("error.request") + e.getMessage());
            return Response.error(messages.getString("error.communication") + e.getMessage());
        }
    }

    private void updateVehiclesFromResponse(Response response) {
        if (!response.isSuccess()) {
            userLabel.setText(messages.getString("error.update.data") + response.getMessage());
            return;
        }

        String message = response.getMessage();
        if (message == null || message.isEmpty()) {
            System.out.println(messages.getString("error.empty.message"));
            return;
        }

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
                    vehicle.setOwner(matcher.group(9));
                    if (existingRotations.containsKey(vehicle.getId())) {
                        vehicle.setRotationAngle(existingRotations.get(vehicle.getId()));
                    }
                    vehicles.add(vehicle);
                    System.out.println(messages.getString("object.added.log") + matcher.group(1) + ", Name: " + matcher.group(2) + ", Owner: " + matcher.group(9));
                }
            }

            if (vehicles.isEmpty()) {
                System.out.println(messages.getString("error.no.objects"));
            } else {
                System.out.println(messages.getString("table.updated") + vehicles.size());
            }

            updateDisplay();
        } catch (Exception e) {
            System.out.println(messages.getString("error.parsing") + e.getMessage());
            userLabel.setText(messages.getString("error.parsing.data") + e.getMessage());
            e.printStackTrace();
        }
    }

    private void updateCanvas() {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(Color.web("#2d2f4a"));
        gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());

        double offsetX = 10;
        double offsetY = 10;
        Random random = new Random();

        vehicleAnimations.keySet().removeIf(vehicleId ->
                vehicles.stream().noneMatch(v -> v.getId() == vehicleId)
        );

        for (Vehicle vehicle : vehicles) {
            if (displayedVehicleIds.contains(vehicle.getId())) {
                drawStaticVehicle(gc, vehicle, offsetX, offsetY);
            }
        }

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

            double startX, startY;
            int direction = random.nextInt(4);
            switch (direction) {
                case 0:
                    startX = -size;
                    startY = targetY + random.nextDouble() * 50 - 25;
                    break;
                case 1:
                    startX = canvas.getWidth() + size;
                    startY = targetY + random.nextDouble() * 50 - 25;
                    break;
                case 2:
                    startX = targetX + random.nextDouble() * 50 - 25;
                    startY = -size;
                    break;
                default:
                    startX = targetX + random.nextDouble() * 50 - 25;
                    startY = canvas.getHeight() + size;
            }

            DoubleProperty x = new SimpleDoubleProperty(startX);
            DoubleProperty y = new SimpleDoubleProperty(startY);
            DoubleProperty scale = new SimpleDoubleProperty(0.1);
            DoubleProperty opacity = new SimpleDoubleProperty(0.0);
            DoubleProperty angle = new SimpleDoubleProperty(vehicle.getRotationAngle() != null ? vehicle.getRotationAngle() : random.nextDouble() * 360);

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
                if (newVal.toMillis() % 10 == 0) {
                    trailPositions.add(new Point(x.get(), y.get()));
                    trailAngles.add(angle.get());
                    if (trailPositions.size() > 15) {
                        trailPositions.remove(0);
                        trailAngles.remove(0);
                    }
                }

                gc.setFill(Color.web("#2d2f4a"));
                gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());

                for (Vehicle v : vehicles) {
                    if (displayedVehicleIds.contains(v.getId())) {
                        drawStaticVehicle(gc, v, offsetX, offsetY);
                    }
                }

                for (int j = 0; j < trailPositions.size(); j++) {
                    Point p = trailPositions.get(j);
                    double trailOpacity = 0.2 * (j / (double) trailPositions.size());
                    double trailSize = size * (0.3 + 0.7 * (j / (double) trailPositions.size()));

                    gc.save();
                    gc.translate(p.x, p.y);
                    gc.rotate(trailAngles.get(j));
                    gc.translate(-p.x, -p.y);

                    gc.setFill(Color.color(color.getRed(), color.getGreen(), color.getBlue(), trailOpacity));
                    drawVehicle(gc, p.x, p.y, trailSize, vehicle.getType(), 0.7);
                    gc.restore();
                }

                for (VehicleAnimation anim : vehicleAnimations.values()) {
                    drawAnimatedVehicle(gc, anim);
                }
            });

            animation.setOnFinished(e -> {
                displayedVehicleIds.add(vehicle.getId());
                vehicle.setRotationAngle(angle.get());
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
        double angle = vehicle.getRotationAngle() != null ? vehicle.getRotationAngle() : 0;

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
        double offsetX = 10;
        double offsetY = 10;

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
            dialog.setTitle(messages.getString("dialog.vehicle.title"));
            dialog.setHeaderText(messages.getString("dialog.vehicle.header"));

            ButtonType okButtonType = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().addAll(okButtonType);

            GridPane grid = new GridPane();
            grid.setHgap(10);
            grid.setVgap(10);
            grid.setPadding(new Insets(20, 40, 10, 40));

            grid.add(new Label(messages.getString("id") + ":"), 0, 0);
            grid.add(new Label(String.valueOf(selected.getId())), 1, 0);
            grid.add(new Label(messages.getString("name") + ":"), 0, 1);
            grid.add(new Label(selected.getName()), 1, 1);
            grid.add(new Label(messages.getString("x") + ":"), 0, 2);
            grid.add(new Label(numberFormat.format(selected.getCoordinates().getX())), 1, 2);
            grid.add(new Label(messages.getString("y") + ":"), 0, 3);
            grid.add(new Label(String.valueOf(selected.getCoordinates().getY())), 1, 3);
            grid.add(new Label(messages.getString("power") + ":"), 0, 4);
            grid.add(new Label(numberFormat.format(selected.getPower())), 1, 4);
            grid.add(new Label(messages.getString("type") + ":"), 0, 5);
            grid.add(new Label(selected.getType().toString()), 1, 5);
            grid.add(new Label(messages.getString("fuel") + ":"), 0, 6);
            grid.add(new Label(selected.getFuelType().toString()), 1, 6);
            grid.add(new Label(messages.getString("creationDate") + ":"), 0, 7);
            grid.add(new Label(selected.getCreationDate().format(dateTimeFormatter)), 1, 7);

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
