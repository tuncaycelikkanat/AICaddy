package com.aicaddy.ai.event;

import com.aicaddy.ExampleMod;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Enterprise Reactive Event Bus for AI Caddy.
 * Provides debouncing (500ms) and throttling (2000ms) for in-game events
 * to protect the LLM pipeline from event flooding and spam.
 */
public final class CompanionEventBus {

	public record CompanionEvent(
			String type,
			UUID playerUuid,
			String description,
			long timestamp,
			int priority // 0 = low, 5 = normal, 10 = critical (bypasses throttle)
	) {}

	private static final List<Consumer<CompanionEvent>> SUBSCRIBERS = new CopyOnWriteArrayList<>();

	// Debounce map: key = playerUuid + "_" + type -> last timestamp
	private static final Map<String, Long> LAST_EVENT_TIMES = new ConcurrentHashMap<>();

	// Throttle map: key = playerUuid -> last notification timestamp
	private static final Map<UUID, Long> LAST_NOTIFIED_TIMES = new ConcurrentHashMap<>();

	private static final long DEBOUNCE_MS = 500L;
	private static final long THROTTLE_MS = 2000L;

	private CompanionEventBus() {}

	/**
	 * Subscribes a listener to receive debounced/throttled companion events.
	 */
	public static void subscribe(Consumer<CompanionEvent> listener) {
		if (listener != null) {
			SUBSCRIBERS.add(listener);
		}
	}

	/**
	 * Unsubscribes all listeners and clears debounce/throttle caches.
	 */
	public static void clear() {
		SUBSCRIBERS.clear();
		LAST_EVENT_TIMES.clear();
		LAST_NOTIFIED_TIMES.clear();
	}

	/**
	 * Publishes a companion event with debounce and throttle filtering.
	 *
	 * @return true if the event was dispatched to subscribers, false if filtered by debounce or throttle.
	 */
	public static boolean publish(String type, UUID playerUuid, String description, int priority) {
		if (type == null || playerUuid == null) return false;

		long now = System.currentTimeMillis();
		String debounceKey = playerUuid + "_" + type;

		// 1. Debounce check (unless priority is critical >= 10)
		if (priority < 10) {
			Long lastEventMs = LAST_EVENT_TIMES.get(debounceKey);
			if (lastEventMs != null && (now - lastEventMs) < DEBOUNCE_MS) {
				ExampleMod.LOGGER.debug("🔇 [CompanionEventBus] Debounced duplicate event '{}' for {}", type, playerUuid);
				return false;
			}
		}
		LAST_EVENT_TIMES.put(debounceKey, now);

		// 2. Throttle check (unless priority is critical >= 10)
		if (priority < 10) {
			Long lastNotifiedMs = LAST_NOTIFIED_TIMES.get(playerUuid);
			if (lastNotifiedMs != null && (now - lastNotifiedMs) < THROTTLE_MS) {
				ExampleMod.LOGGER.debug("⏳ [CompanionEventBus] Throttled event '{}' for {}", type, playerUuid);
				return false;
			}
		}
		LAST_NOTIFIED_TIMES.put(playerUuid, now);

		// 3. Dispatch to all subscribers
		CompanionEvent event = new CompanionEvent(type, playerUuid, description, now, priority);
		for (Consumer<CompanionEvent> subscriber : SUBSCRIBERS) {
			try {
				subscriber.accept(event);
			} catch (Exception e) {
				ExampleMod.LOGGER.error("❌ [CompanionEventBus] Subscriber error: {}", e.getMessage());
			}
		}

		return true;
	}

	/**
	 * Convenience overload with default normal priority (5).
	 */
	public static boolean publish(String type, UUID playerUuid, String description) {
		return publish(type, playerUuid, description, 5);
	}
}
