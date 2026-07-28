package com.example.ai.resilience;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Lightweight Circuit Breaker pattern implementation.
 * Opens for 60 seconds after 3 consecutive failures to prevent cascading timeouts.
 */
public class CircuitBreaker {

	private final String name;
	private final int failureThreshold;
	private final long openDurationMs;

	private final AtomicInteger failureCount = new AtomicInteger(0);
	private final AtomicLong openedUntil = new AtomicLong(0);

	public CircuitBreaker(String name, int failureThreshold, long openDurationMs) {
		this.name = name;
		this.failureThreshold = failureThreshold;
		this.openDurationMs = openDurationMs;
	}

	public CircuitBreaker(String name) {
		this(name, 3, 60_000L); // default 3 failures -> open for 60 seconds
	}

	/**
	 * Returns true if the circuit is currently open (unhealthy / cooling down).
	 */
	public boolean isOpen() {
		long until = openedUntil.get();
		if (until == 0) return false;
		if (System.currentTimeMillis() > until) {
			// Cooldown elapsed — enter half-open state (reset openedUntil)
			openedUntil.set(0);
			failureCount.set(0);
			return false;
		}
		return true;
	}

	/**
	 * Records a successful operation, resetting failure counters.
	 */
	public void recordSuccess() {
		failureCount.set(0);
		openedUntil.set(0);
	}

	/**
	 * Records a failed operation. If threshold is reached, opens the circuit.
	 */
	public void recordFailure() {
		int count = failureCount.incrementAndGet();
		if (count >= failureThreshold) {
			long until = System.currentTimeMillis() + openDurationMs;
			openedUntil.set(until);
		}
	}

	public String getName() {
		return name;
	}
}
