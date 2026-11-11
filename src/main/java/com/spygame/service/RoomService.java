package com.spygame.service;

import com.spygame.model.GameRoom;
import com.spygame.model.Player;
import com.spygame.model.RoomStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-Memory Room Service managing active GameRooms.
 * Uses ConcurrentHashMap for real-time performance and thread-safety.
 * Automatically cleans up inactive rooms after 2 hours.
 * Conforms to .antigravity/rules.md (Section 1).
 */
@Service
public class RoomService {
    private static final Logger log = LoggerFactory.getLogger(RoomService.class);

    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // Removed ambiguous chars (I, O, 0, 1)
    private static final int CODE_LENGTH = 5;
    private static final int MAX_PLAYERS_PER_ROOM = 16;
    private static final Duration ROOM_TTL = Duration.ofHours(2);

    private final Map<String, GameRoom> rooms = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    /**
     * Creates a new game room with a unique 5-character code and registers the host.
     */
    public GameRoom createRoom(String hostNickname) {
        String roomCode = generateUniqueRoomCode();
        String hostId = UUID.randomUUID().toString();

        Player host = new Player(hostId, hostNickname.trim(), 1, true);
        GameRoom room = new GameRoom(roomCode, hostId);
        room.addPlayer(host);

        rooms.put(roomCode, room);
        log.info("Created game room {} by host '{}' ({})", roomCode, hostNickname, hostId);
        return room;
    }

    /**
     * Adds a new player to an existing room.
     */
    public Player joinRoom(String roomCode, String nickname) {
        GameRoom room = getRoom(roomCode);
        if (room == null) {
            throw new IllegalArgumentException("error.room.notfound");
        }
        if (room.getStatus() != RoomStatus.LOBBY) {
            throw new IllegalStateException("error.room.not_in_lobby");
        }
        if (room.getPlayerCount() >= MAX_PLAYERS_PER_ROOM) {
            throw new IllegalStateException("error.room.full");
        }

        String playerId = UUID.randomUUID().toString();
        // Assign avatar 1-8 cyclically
        int avatarIndex = (room.getPlayerCount() % 8) + 1;
        Player player = new Player(playerId, nickname.trim(), avatarIndex, false);

        room.addPlayer(player);
        log.info("Player '{}' ({}) joined room {}", nickname, playerId, roomCode);
        return player;
    }

    /**
     * Retrieves an active room by its 5-character code.
     */
    public GameRoom getRoom(String roomCode) {
        if (roomCode == null) return null;
        return rooms.get(roomCode.trim().toUpperCase());
    }

    /**
     * Removes a player from a room. Deletes the room if all players leave.
     */
    public Player leaveRoom(String roomCode, String playerId) {
        GameRoom room = getRoom(roomCode);
        if (room == null) return null;

        Player removed = room.removePlayer(playerId);
        if (room.getPlayerCount() == 0) {
            rooms.remove(room.getRoomCode());
            log.info("Room {} is empty and has been removed.", roomCode);
        } else {
            log.info("Player {} left room {}. Remaining players: {}", playerId, roomCode, room.getPlayerCount());
        }
        return removed;
    }

    /**
     * Removes a room manually.
     */
    public void deleteRoom(String roomCode) {
        if (roomCode != null) {
            rooms.remove(roomCode.trim().toUpperCase());
        }
    }

    /**
     * Generates an unambiguous 5-character alphanumeric room code.
     */
    private String generateUniqueRoomCode() {
        String code;
        int attempts = 0;
        do {
            StringBuilder sb = new StringBuilder(CODE_LENGTH);
            for (int i = 0; i < CODE_LENGTH; i++) {
                sb.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
            }
            code = sb.toString();
            attempts++;
            if (attempts > 500) {
                throw new IllegalStateException("Failed to generate unique room code. System is saturated.");
            }
        } while (rooms.containsKey(code));
        return code;
    }

    /**
     * Scheduled cleanup: Automatically clears rooms inactive for more than 2 hours.
     * Runs every 10 minutes.
     */
    @Scheduled(fixedRate = 600_000)
    public void cleanupInactiveRooms() {
        Instant threshold = Instant.now().minus(ROOM_TTL);
        rooms.entrySet().removeIf(entry -> {
            boolean expired = entry.getValue().getLastActivityTime().isBefore(threshold);
            if (expired) {
                log.info("Cleaning up inactive room {} (inactive since {})",
                        entry.getKey(), entry.getValue().getLastActivityTime());
            }
            return expired;
        });
    }

    public int getActiveRoomCount() {
        return rooms.size();
    }
}
