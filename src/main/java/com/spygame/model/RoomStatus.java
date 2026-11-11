package com.spygame.model;

/**
 * Status of a Game Room during its lifecycle.
 * LOBBY -> PLAYING -> VOTING -> ROUND_SUMMARY -> LAST_GUESS -> RESULT
 */
public enum RoomStatus {
    LOBBY,          // Waiting chamber (adding players, config)
    PLAYING,        // Active turn-based discussion & card peeking
    DISCUSSION,     // Alias for PLAYING for backwards compatibility
    VOTING,         // Accusation & voting phase
    ROUND_SUMMARY,  // Summary after elimination, host advances to next round
    LAST_GUESS,     // 20s last-chance guess for eliminated Spy / Mr. White
    RESULT          // Game over, reveal words/roles & penalty wheel
}
