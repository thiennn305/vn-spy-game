package com.spygame.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Server-Sent Events (SSE) Service managing SseEmitters per GameRoom.
 * Dispatches real-time events to HTMX (hx-ext="sse").
 * Conforms to .antigravity/rules.md (Section 1).
 */
@Service
public class SseService {
    private static final Logger log = LoggerFactory.getLogger(SseService.class);
    private static final Long DEFAULT_TIMEOUT = 300_000L; // 5 minutes

    private final Map<String, List<SseEmitter>> roomEmitters = new ConcurrentHashMap<>();

    /**
     * Registers a new SSE Emitter for the specified room.
     */
    public SseEmitter createEmitter(String roomCode) {
        String key = roomCode.toUpperCase();
        SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT);

        roomEmitters.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(key, emitter));
        emitter.onTimeout(() -> removeEmitter(key, emitter));
        emitter.onError(e -> removeEmitter(key, emitter));

        // Send initial connect handshake
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data("🐾 Welcome to Room " + key));
        } catch (IOException e) {
            removeEmitter(key, emitter);
        }

        log.debug("New SSE Emitter connected for room {}. Active: {}", key, getEmitterCount(key));
        return emitter;
    }

    /**
     * Broadcasts an event with data to all clients connected to a room.
     */
    public void broadcast(String roomCode, String eventName, Object data) {
        String key = roomCode.toUpperCase();
        List<SseEmitter> emitters = roomEmitters.get(key);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        List<SseEmitter> deadEmitters = new CopyOnWriteArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name(eventName)
                        .data(data));
            } catch (Exception ex) {
                deadEmitters.add(emitter);
            }
        }

        if (!deadEmitters.isEmpty()) {
            emitters.removeAll(deadEmitters);
            log.debug("Removed {} dead SSE emitters for room {}", deadEmitters.size(), key);
        }
    }

    /**
     * Notifies all players that lobby state has changed (player joined/left).
     */
    public void broadcastLobbyUpdate(String roomCode) {
        broadcast(roomCode, "lobby-updated", System.currentTimeMillis());
    }

    /**
     * Notifies players that game has started.
     */
    public void broadcastGameStarted(String roomCode) {
        broadcast(roomCode, "game-started", System.currentTimeMillis());
    }

    /**
     * Notifies players that speaker turn has updated.
     */
    public void broadcastTurnUpdated(String roomCode) {
        broadcast(roomCode, "turn-updated", System.currentTimeMillis());
    }

    /**
     * Notifies players that voting phase has started.
     */
    public void broadcastVotingStarted(String roomCode) {
        broadcast(roomCode, "voting-started", System.currentTimeMillis());
    }

    /**
     * Notifies players that a vote was cast.
     */
    public void broadcastVoteUpdated(String roomCode) {
        broadcast(roomCode, "vote-updated", System.currentTimeMillis());
    }

    /**
     * Notifies players that 20s Last Guess phase has started.
     */
    public void broadcastLastGuessStarted(String roomCode) {
        broadcast(roomCode, "last-guess-started", System.currentTimeMillis());
    }

    /**
     * Notifies players that the game has concluded and results are ready.
     */
    public void broadcastGameEnded(String roomCode) {
        broadcast(roomCode, "game-ended", System.currentTimeMillis());
    }

    /**
     * Notifies players that the host restarted the game back to lobby.
     */
    public void broadcastGameRestarted(String roomCode) {
        broadcast(roomCode, "game-restarted", System.currentTimeMillis());
    }

    /**
     * Notifies all players that room was disbanded / closed by Host.
     */
    public void broadcastRoomClosed(String roomCode) {
        broadcast(roomCode, "room-closed", System.currentTimeMillis());
        // Clean up emitters for this closed room
        String key = roomCode.toUpperCase();
        List<SseEmitter> emitters = roomEmitters.remove(key);
        if (emitters != null) {
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.complete();
                } catch (Exception ignored) {}
            }
        }
    }

    private void removeEmitter(String key, SseEmitter emitter) {
        List<SseEmitter> emitters = roomEmitters.get(key);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                roomEmitters.remove(key);
            }
        }
    }

    public int getEmitterCount(String roomCode) {
        List<SseEmitter> list = roomEmitters.get(roomCode.toUpperCase());
        return list != null ? list.size() : 0;
    }
}
