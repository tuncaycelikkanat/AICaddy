package com.aicaddy.voice;

/**
 * Adım 5: Events triggering transitions in ConversationFlowFsm.
 */
public enum ConversationEvent {
    START_LISTENING,
    SPEECH_RECOGNIZED,
    SPEECH_ABORTED,
    AI_RESPONSE_READY,
    AI_ERROR,
    TTS_FINISHED,
    BARGE_IN
}
