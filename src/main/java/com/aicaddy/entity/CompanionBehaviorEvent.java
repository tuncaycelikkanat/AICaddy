package com.aicaddy.entity;

/**
 * P10.3 / Adım 3: Events that trigger transitions in Kedi's physical behavior FSM.
 */
public enum CompanionBehaviorEvent {
    COMMAND_SIT,
    COMMAND_FOLLOW,
    THREAT_HIGH,
    THREAT_CRITICAL,
    THREAT_CLEAR,
    INTERESTING_BLOCK_FOUND,
    PLAYER_ACHIEVEMENT,
    TASK_COMPLETE
}
