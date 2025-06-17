package com.vehicleShared.model;

import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;

public class VehicleWrapper {
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