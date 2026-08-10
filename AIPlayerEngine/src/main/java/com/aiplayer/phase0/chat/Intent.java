package com.aiplayer.phase0.chat;

/** MODE: PARTIAL. Compiles and follows the reviewed patterns, but not independently re-verified line-by-line this session — treat as unverified until it passes mvn test. */

public enum Intent {
    COMMAND_FOLLOW, COMMAND_BUFF, COMMAND_INVITE, COMMAND_TRADE,
    SOCIAL_GREET, SOCIAL_THANKS, SOCIAL_TRASH,
    QUESTION_LEVEL, QUESTION_CLASS, QUESTION_FARM, UNKNOWN
}
