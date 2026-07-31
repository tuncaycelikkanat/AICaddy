package com.aicaddy.ai.resilience;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Enterprise-grade Circuit Breaker with CLOSED, OPEN, and HALF_OPEN states.
 * Prevents cascading timeouts by failing fast when an LLM provider is degraded,
 * and automatically tests recovery using HALF_OPEN state.
 */
public class CircuitBreaker {

	public enum CircuitState {
		CLOSED,    // Normal operation, requests flow freely
		OPEN,      // Provider degraded, fail fast / switch to backup
		HALF_OPEN  // Recovery test window: allow 1 request to test if provider healed
	}

	private final String name;
	private final int failureThreshold;
	private final long openDurationMs;

	private final AtomicInteger failureCount = new AtomicInteger(0);
	private final AtomicLong openedUntil = new AtomicLong(0);
	private final AtomicReference<CircuitState> state = new AtomicReference<>(CircuitState.CLOSED);

	public CircuitBreaker(String name, int failureThreshold, long openDurationMs) {
		this.name = name;
		this.failureThreshold = failureThreshold;
		this.openDurationMs = openDurationMs;
	}

	public CircuitBreaker(String name) {
		this(name, 3, 60_000L); // Default: open after 3 consecutive failures for 60s
	}

	/**
	 * Gets the current circuit state, automatically transitioning OPEN -> HALF_OPEN when cooldown expires.
	 */
	public synchronized CircuitState getState() {
		CircuitState current = state.get();
		if (current == CircuitState.OPEN) {
			long until = openedUntil.get();
			if (System.currentTimeMillis() > until && until != 0) {
				// Cooldown elapsed — enter HALF_OPEN state to test provider health
				state.set(CircuitState.HALF_OPEN);
				failureCount.set(0);
				return CircuitState.HALF_OPEN;
			}
		}
		return state.get();
	}

	/**
	 * Returns true if requests should be allowed (CLOSED or HALF_OPEN).
	 */
	public boolean isHealthy() {
		CircuitState current = getState();
		return current == CircuitState.CLOSED || current == CircuitState.HALF_OPEN;
	}

	/**
	 * Returns true if the circuit is currently OPEN (unhealthy / cooling down).
	 */
	public boolean isOpen() {
		return getState() == CircuitState.OPEN;
	}

	/**
	 * Records a successful operation, closing the circuit if it was HALF_OPEN.
	 */
	public synchronized void recordSuccess() {
		failureCount.set(0);
		openedUntil.set(0);
		state.set(CircuitState.CLOSED);
	}

	/**
	 * Records a failed operation. If threshold reached or in HALF_OPEN, opens the circuit.
	 */
	public synchronized void recordFailure() {
		CircuitState current = getState();
		if (current == CircuitState.HALF_OPEN) {
			// Failed during recovery test — immediately open again
			openCircuit();
			return;
		}

		int count = failureCount.incrementAndGet();
		if (count >= failureThreshold) {
			openCircuit();
		}
	}

	private void openCircuit() {
		long until = System.currentTimeMillis() + openDurationMs;
		openedUntil.set(until);
		state.set(CircuitState.OPEN);
	}

	/**
	 * Resets the circuit breaker manually to CLOSED state.
	 */
	public synchronized void reset() {
		failureCount.set(0);
		openedUntil.set(0);
		state.set(CircuitState.CLOSED);
	}

	public String getName() {
		return name;
	}

	public int getFailureCount() {
		return failureCount.get();
	}
}
