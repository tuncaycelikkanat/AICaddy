package com.aicaddy.voice;

/**
 * Adım 5: Discrete states in the Voice & Chat conversation lifecycle.
 */
public enum ConversationState {
    IDLE("Beklemede"),
    LISTENING("Dinliyor"),
    PROCESSING("Düşünüyor"),
    SPEAKING("Konuşuyor");

    private final String label;

    ConversationState(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
