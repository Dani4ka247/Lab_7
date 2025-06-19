package com.vehicleServer.commands;

import com.vehicleShared.network.Request;
import com.vehicleShared.network.Response;
import com.vehicleShared.managers.CollectionManager;
import com.vehicleShared.managers.DbManager;

import java.sql.SQLException;

public class ClearCommand implements Command {
    private final CollectionManager collectionManager;
    private final DbManager dbManager;

    public ClearCommand(CollectionManager collectionManager, DbManager dbManager) {
        this.collectionManager = collectionManager;
        this.dbManager = dbManager;
    }

    @Override
    public Response execute(Request request) {
        if (collectionManager.isEmpty()) {
            return Response.success("Коллекция уже пуста.");
        }

        String userId = request.getLogin();
        try {
            // Удаляем все записи пользователя из базы данных
            if (dbManager.removeAllVehicles(userId)) {
                // Очищаем локальную коллекцию
                collectionManager.clear();
                return Response.success("Коллекция успешно очищена.");
            } else {
                return Response.error("Не удалось очистить коллекцию в базе данных.");
            }
        } catch (SQLException e) {
            return Response.error("Ошибка при очистке базы данных: " + e.getMessage());
        }
    }

    @Override
    public String getDescription() {
        return "Очищает коллекцию, удаляя все элементы пользователя из базы данных и локальной коллекции.";
    }
}