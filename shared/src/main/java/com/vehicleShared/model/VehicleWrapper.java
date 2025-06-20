package com.vehicleShared.model;

import javafx.beans.property.SimpleFloatProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;

public class VehicleWrapper {
    private final SimpleLongProperty id = new SimpleLongProperty();
    private final SimpleStringProperty name = new SimpleStringProperty();
    private final SimpleFloatProperty x = new SimpleFloatProperty();
    private final SimpleIntegerProperty y = new SimpleIntegerProperty();
    private final SimpleFloatProperty enginePower = new SimpleFloatProperty();
    private final SimpleStringProperty type = new SimpleStringProperty();
    private final SimpleStringProperty fuelType = new SimpleStringProperty();
    private final SimpleStringProperty owner = new SimpleStringProperty();
    private final Vehicle vehicle;

    public VehicleWrapper(Vehicle vehicle) {
        this.vehicle = vehicle;
        this.id.set(vehicle.getId());
        this.name.set(vehicle.getName());
        this.x.set(vehicle.getCoordinates().getX());
        this.y.set(vehicle.getCoordinates().getY());
        this.enginePower.set(vehicle.getPower());
        this.type.set(vehicle.getType() != null ? vehicle.getType().toString() : "");
        this.fuelType.set(vehicle.getFuelType() != null ? vehicle.getFuelType().toString() : "");
        this.owner.set(vehicle.getOwner() != null ? vehicle.getOwner() : "");
    }

    // Геттеры свойств
    public SimpleLongProperty idProperty() { return id; }
    public SimpleStringProperty nameProperty() { return name; }
    public SimpleFloatProperty xProperty() { return x; }
    public SimpleIntegerProperty yProperty() { return y; }
    public SimpleFloatProperty enginePowerProperty() { return enginePower; }
    public SimpleStringProperty typeProperty() { return type; }
    public SimpleStringProperty fuelTypeProperty() { return fuelType; }
    public SimpleStringProperty ownerProperty() { return owner; }
    public Vehicle getVehicle() { return vehicle; }
}