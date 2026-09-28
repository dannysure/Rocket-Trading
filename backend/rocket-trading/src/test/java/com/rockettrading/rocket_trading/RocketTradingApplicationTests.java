package com.rockettrading.rocket_trading;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lightweight unit test for the application bootstrap.
 *
 * This test DOES NOT load the full Spring context, to keep the test suite fast
 * during TDD iterations. Integration tests with @SpringBootTest should be
 * created only when testing Spring-specific functionality like MVC endpoints
 * or service orchestration.
 */
class RocketTradingApplicationTests {

	@Test
	void applicationClassIsMarkedAsSpringBootApplication() {
		assertTrue(RocketTradingApplication.class.isAnnotationPresent(SpringBootApplication.class));
	}

	@Test
	public void simpleArithmeticTest() {
		assertEquals(2 + 2, 4);
	}

}