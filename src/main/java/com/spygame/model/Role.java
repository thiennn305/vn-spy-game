package com.spygame.model;

/**
 * Roles in the Undercover Cat Party Game.
 * Conforms to .antigravity/rules.md (Section 2).
 */
public enum Role {
    CIVILIAN,   // Dân thường - Mèo Ngơ Ngác (receives civilianWord)
    SPY,        // Gián điệp - Mèo Mờ Ám (receives spyWord)
    MR_WHITE    // Trùm Mù - Mèo Đội Thùng Carton (secretWord = null)
}
