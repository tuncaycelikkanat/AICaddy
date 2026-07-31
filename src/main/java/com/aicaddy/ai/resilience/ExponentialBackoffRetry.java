package com.aicaddy.ai.resilience;

import com.aicaddy.ExampleMod;

import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Enterprise Exponential Backoff & Jitter retry helper for transient network failures.
 * Integrates directly with CircuitBreaker to open circuits when retries are exhausted.
 */
public final class ExponentialBackoffRetry {

	private final int maxRetries;
	private final long baseDelayMs;
	private final long maxDelayMs;

	public ExponentialBackoffRetry(int maxRetries, long baseDelayMs, long maxDelayMs) {
		this.maxRetries = maxRetries;
		this.baseDelayMs = baseDelayMs;
		this.maxDelayMs = maxDelayMs;
	}

	public ExponentialBackoffRetry() {
		this(3, 500L, 5000L); // Default: up to 3 retries, starting at 500ms up to 5s
	}

	/**
	 * Executes the action with exponential backoff and jitter, updating the given CircuitBreaker.
	 *
	 * @return Optional containing the result if successful, or empty if all retries failed or circuit was OPEN.
	 */
	public <T> Optional<T> execute(Supplier<T> action, CircuitBreaker breaker) {
		if (breaker != null && breaker.isOpen()) {
			ExampleMod.LOGGER.warn("🛑 [Resilience] CircuitBreaker '{}' is OPEN. Skipping execution.", breaker.getName());
			return Optional.empty();
		}

		for (int attempt = 1; attempt <= maxRetries; attempt++) {
			try {
				T result = action.get();
				if (breaker != null) {
					breaker.recordSuccess();
				}
				return Optional.ofNullable(result);
			} catch (Exception e) {
				ExampleMod.LOGGER.warn("⚠️ [Resilience] Attempt {}/{} failed for '{}': {}",
						attempt, maxRetries, (breaker != null ? breaker.getName() : "Action"), e.getMessage());

				if (breaker != null) {
					breaker.recordFailure();
				}

				if (attempt == maxRetries || (breaker != null && breaker.isOpen())) {
					ExampleMod.LOGGER.error("❌ [Resilience] All {} attempts exhausted or circuit opened.", attempt);
					break;
				}

				long sleepTime = calculateDelay(attempt);
				try {
					Thread.sleep(sleepTime);
				} catch (InterruptedException ie) {
					Thread.currentThread().interrupt();
					break;
				}
			}
		}

		return Optional.empty();
	}

	private long calculateDelay(int attempt) {
		long delay = baseDelayMs * (1L << (attempt - 1));
		delay = Math.min(delay, maxDelayMs);
		// Add 10-20% random jitter to avoid thundering herd
		long jitter = ThreadLocalRandom.current().nextLong(0, delay / 5 + 1);
		return delay + jitter;
	}
}
