package com.rockettrading.rocket_trading.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class UserServiceTest {

	@Test
	@DisplayName("service skeleton can be instantiated")
	void serviceSkeletonCanBeInstantiated() {
		assertDoesNotThrow(UserService::new);
	}
}

