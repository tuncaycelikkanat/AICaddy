package com.aicaddy.entity;

/**
 * P10.3 / Adım 3: Physical in-game behaviors of Kedi companion.
 */
public enum CompanionBehaviorState {
    IDLE("Boşta"),
    FOLLOW_PLAYER("Takipte"),
    SIT_AND_WAIT("Oturuyor"),
    INSPECT_BLOCK("İnceliyor"),
    FLEE_DANGER("Tehlikeden Kaçıyor"),
    CELEBRATE("Kutlama Yapıyor");

    private final String label;

    CompanionBehaviorState(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
