package com.rockettrading.rocket_trading.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class UserControllerTest {

    @Test
    @DisplayName("controller skeleton can be instantiated")
    void controllerSkeletonCanBeInstantiated() {
        assertDoesNotThrow(UserController::new);
    }
}

