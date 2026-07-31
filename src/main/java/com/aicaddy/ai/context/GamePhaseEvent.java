package com.aicaddy.ai.context;

/**
 * Adım 4: Events that trigger transitions in Game Phase Progression FSM.
 */
public enum GamePhaseEvent {
    ADVANCE_TO_MID,
    ADVANCE_TO_LATE,
    ADVANCE_TO_ENDGAME,
    RESET_PHASE
}
