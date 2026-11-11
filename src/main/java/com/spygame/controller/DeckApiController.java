package com.spygame.controller;

import com.spygame.dto.CustomDeckDto;
import com.spygame.model.GameRoom;
import com.spygame.service.RoomService;
import com.spygame.service.WordBankService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API for managing Custom Decks and selecting active word decks.
 */
@RestController
@RequestMapping("/api/decks")
public class DeckApiController {

    private final WordBankService wordBankService;
    private final RoomService roomService;

    public DeckApiController(WordBankService wordBankService, RoomService roomService) {
        this.wordBankService = wordBankService;
        this.roomService = roomService;
    }

    /**
     * Get all available decks for the current user and locale.
     */
    @GetMapping
    public ResponseEntity<List<CustomDeckDto>> getDecks(
            @RequestParam(defaultValue = "vi") String lang,
            @RequestParam(required = false) String creatorToken,
            @CookieValue(name = "cat_creator_token", required = false) String cookieToken) {

        String token = (creatorToken != null && !creatorToken.trim().isEmpty())
                ? creatorToken.trim()
                : cookieToken;

        List<CustomDeckDto> decks = wordBankService.getAvailableDecks(lang, token);
        return ResponseEntity.ok(decks);
    }

    /**
     * Save or update a custom deck.
     */
    @PostMapping
    public ResponseEntity<?> saveDeck(
            @RequestBody CustomDeckDto dto,
            @RequestParam(required = false) String creatorToken,
            @CookieValue(name = "cat_creator_token", required = false) String cookieToken) {

        String token = (creatorToken != null && !creatorToken.trim().isEmpty())
                ? creatorToken.trim()
                : cookieToken;

        if (token == null || token.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing creatorToken"));
        }

        try {
            CustomDeckDto saved = wordBankService.saveCustomDeck(token, dto);
            return ResponseEntity.ok(saved);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Delete a custom deck.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteDeck(
            @PathVariable Long id,
            @RequestParam(required = false) String creatorToken,
            @CookieValue(name = "cat_creator_token", required = false) String cookieToken) {

        String token = (creatorToken != null && !creatorToken.trim().isEmpty())
                ? creatorToken.trim()
                : cookieToken;

        if (token == null || token.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing creatorToken"));
        }

        boolean deleted = wordBankService.deleteCustomDeck(token, id);
        if (deleted) {
            return ResponseEntity.ok(Map.of("status", "deleted", "id", id));
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Recover decks using PIN.
     */
    @PostMapping("/recover")
    public ResponseEntity<?> recoverDecks(
            @RequestParam String pin,
            @RequestParam(required = false) String creatorToken,
            @CookieValue(name = "cat_creator_token", required = false) String cookieToken) {

        String token = (creatorToken != null && !creatorToken.trim().isEmpty())
                ? creatorToken.trim()
                : cookieToken;

        if (token == null || token.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing creatorToken"));
        }

        int count = wordBankService.recoverDecks(token, pin);
        return ResponseEntity.ok(Map.of("recoveredCount", count));
    }

    /**
     * Host selects active deck for a room.
     */
    @PostMapping("/select-room-deck")
    public ResponseEntity<?> selectRoomDeck(
            @RequestParam String roomCode,
            @RequestParam String deckId,
            @RequestParam String deckName,
            @RequestParam String hostId) {

        GameRoom room = roomService.getRoom(roomCode);
        if (room == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "error.room.notfound"));
        }
        if (!room.getHostId().equals(hostId)) {
            return ResponseEntity.badRequest().body(Map.of("error", "error.not_host"));
        }

        room.setSelectedDeckId(deckId);
        room.setSelectedDeckName(deckName);

        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "deckId", deckId,
                "deckName", deckName
        ));
    }
}
