package com.rockettrading.rocket_trading.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class UserServiceTest {

	@Test
	@DisplayName("service can be instantiated with repositories")
	void serviceCanBeInstantiatedWithRepositories() {
		assertDoesNotThrow(() -> new UserService(null, null));
	}
}
