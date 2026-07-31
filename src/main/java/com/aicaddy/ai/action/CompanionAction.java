package com.aicaddy.ai.action;

/**
 * P10.5 — Structured Minecraft Action emitted by AI Companion (Tool Calling).
 * Represents physical actions that the companion cat will execute in the Minecraft world.
 */
public record CompanionAction(
        String type,
        String target,
        int x,
        int y,
        int z,
        String reason
) {
    public boolean isValid() {
        return type != null && !type.isBlank();
    }
}
