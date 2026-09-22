package com.rockettrading.rocket_trading.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class UserRepositoryTest {

    @Test
    @DisplayName("repository skeleton can be instantiated")
    void repositorySkeletonCanBeInstantiated() {
        assertDoesNotThrow(UserRepository::new);
    }
}
