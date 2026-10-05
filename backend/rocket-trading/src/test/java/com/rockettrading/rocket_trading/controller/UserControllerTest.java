package com.rockettrading.rocket_trading.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class UserControllerTest {

    @Test
    @DisplayName("controller can be instantiated with its service")
    void controllerCanBeInstantiatedWithItsService() {
        assertDoesNotThrow(() -> new UserController(new com.rockettrading.rocket_trading.service.UserService(null, null)));
    }
}
