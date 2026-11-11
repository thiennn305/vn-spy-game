package com.spygame.controller;

import com.spygame.model.GameRoom;
import com.spygame.model.Player;
import com.spygame.model.RoomStatus;
import com.spygame.service.GameEngineService;
import com.spygame.service.RoomService;
import com.spygame.service.SseService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * REST & HTMX API Controller for Room lifecycle and real-time SSE.
 */
@Controller
@RequestMapping("/api/rooms")
public class RoomApiController {

    private final RoomService roomService;
    private final SseService sseService;
    private final GameEngineService gameEngineService;

    public RoomApiController(RoomService roomService, SseService sseService, GameEngineService gameEngineService) {
        this.roomService = roomService;
        this.sseService = sseService;
        this.gameEngineService = gameEngineService;
    }

    /**
     * Create Room endpoint.
     */
    @PostMapping("/create")
    @ResponseBody
    public ResponseEntity<?> createRoom(@RequestParam String nickname) {
        if (nickname == null || nickname.trim().length() < 2 || nickname.trim().length() > 15) {
            return ResponseEntity.badRequest().body(Map.of("error", "validation.nickname.length"));
        }

        GameRoom room = roomService.createRoom(nickname.trim());
        Player host = room.getHost();

        return ResponseEntity.ok(Map.of(
                "roomCode", room.getRoomCode(),
                "playerId", host.getId(),
                "nickname", host.getNickname(),
                "isHost", true
        ));
    }

    /**
     * Join Room endpoint.
     */
    @PostMapping("/join")
    @ResponseBody
    public ResponseEntity<?> joinRoom(@RequestParam String roomCode, @RequestParam String nickname) {
        if (roomCode == null || roomCode.trim().length() != 5) {
            return ResponseEntity.badRequest().body(Map.of("error", "validation.roomcode.format"));
        }
        if (nickname == null || nickname.trim().length() < 2 || nickname.trim().length() > 15) {
            return ResponseEntity.badRequest().body(Map.of("error", "validation.nickname.length"));
        }

        try {
            Player player = roomService.joinRoom(roomCode.trim().toUpperCase(), nickname.trim());
            // Broadcast real-time lobby update to all connected players
            sseService.broadcastLobbyUpdate(roomCode.trim().toUpperCase());

            return ResponseEntity.ok(Map.of(
                    "roomCode", roomCode.trim().toUpperCase(),
                    "playerId", player.getId(),
                    "nickname", player.getNickname(),
                    "avatarIndex", player.getAvatarIndex(),
                    "isHost", false
            ));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Leave Room endpoint. If Host leaves, the room is disbanded and all players are kicked out.
     */
    @PostMapping("/{roomCode}/leave")
    @ResponseBody
    public ResponseEntity<?> leaveRoom(@PathVariable String roomCode, @RequestParam String playerId) {
        String code = roomCode.trim().toUpperCase();
        GameRoom room = roomService.getRoom(code);
        if (room == null) {
            return ResponseEntity.ok(Map.of("status", "already_closed"));
        }

        boolean isHost = room.getHostId() != null && room.getHostId().equals(playerId);
        // During active gameplay, only Host can leave (which disbands the room)
        if (room.getStatus() != RoomStatus.LOBBY && !isHost) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "error.cannot_leave_during_game",
                    "message", "Phiên tòa đang diễn ra! Chỉ Chánh Án mới có quyền kết thúc hoặc giải tán phiên tòa meow~ 😾"
            ));
        }

        Player removed = roomService.leaveRoom(code, playerId);

        if (isHost) {
            // Disband the room and kick all players
            roomService.deleteRoom(code);
            sseService.broadcastRoomClosed(code);
        } else if (removed != null) {
            // Member left: notify remaining players to update lobby count
            sseService.broadcastLobbyUpdate(code);
        }
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    /**
     * SSE Stream endpoint for real-time room events.
     */
    @GetMapping(value = "/{roomCode}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ResponseBody
    public SseEmitter subscribeEvents(@PathVariable String roomCode) {
        return sseService.createEmitter(roomCode);
    }

    /**
     * Lobby player list fragment endpoint returned for HTMX SSE swaps.
     */
    @GetMapping("/{roomCode}/lobby-players")
    public String getLobbyPlayersFragment(@PathVariable String roomCode, Model model) {
        GameRoom room = roomService.getRoom(roomCode);
        if (room == null) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Room not found");
        }
        model.addAttribute("room", room);
        model.addAttribute("players", room.getPlayersList());
        model.addAttribute("canStart", room.canStart());
        return "fragments/lobby-fragments :: player-list";
    }

    /**
     * Host action to start the game.
     */
    @PostMapping("/{roomCode}/start")
    @ResponseBody
    public ResponseEntity<?> startGame(@PathVariable String roomCode,
                                       @RequestParam String hostId,
                                       @RequestParam(required = false) String deckId,
                                       @RequestParam(required = false) Boolean mrWhite,
                                       @RequestParam(required = false) Integer timer,
                                       @RequestParam(required = false) Integer civilians,
                                       @RequestParam(required = false) Integer spies,
                                       @RequestParam(required = false) Integer mrWhites,
                                       @CookieValue(value = "cat_creator_token", required = false) String creatorToken) {
        String code = roomCode.trim().toUpperCase();
        GameRoom room = roomService.getRoom(code);
        if (room == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "error.room.notfound", "message", "Phòng không tồn tại hoặc đã bị hủy!"));
        }
        if (!room.getHostId().equals(hostId)) {
            return ResponseEntity.badRequest().body(Map.of("error", "error.not_host", "message", "Chỉ Chánh Án mới có quyền khai mạc phiên tòa!"));
        }
        if (!room.canStart()) {
            return ResponseEntity.badRequest().body(Map.of("error", "lobby.minplayers.warning", "message", "Cần ít nhất 3 con mèo để mở phiên tòa xử án! 🐾"));
        }

        if (deckId != null && !deckId.isBlank()) {
            room.setSelectedDeckId(deckId.trim());
        }
        if (mrWhite != null) {
            room.setMrWhiteEnabled(mrWhite);
        }
        if (timer != null && timer > 0) {
            room.setTimerSeconds(timer);
        }

        try {
            gameEngineService.startGame(room, creatorToken, civilians, spies, mrWhites);
            sseService.broadcastGameStarted(code);
            return ResponseEntity.ok(Map.of("status", "started", "roomStatus", room.getStatus()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "error.start_failed", "message", e.getMessage() != null ? e.getMessage() : "Không thể khởi động ván chơi!"));
        }
    }

    /**
     * Host action to advance speaker turn.
     */
    @PostMapping("/{roomCode}/next-speaker")
    @ResponseBody
    public ResponseEntity<?> nextSpeaker(@PathVariable String roomCode, @RequestParam String hostId) {
        String code = roomCode.trim().toUpperCase();
        GameRoom room = roomService.getRoom(code);
        if (room == null) return ResponseEntity.badRequest().body(Map.of("error", "error.room.notfound"));
        if (!room.getHostId().equals(hostId)) return ResponseEntity.badRequest().body(Map.of("error", "error.not_host"));

        boolean roundEnded = gameEngineService.advanceTurn(room);
        sseService.broadcastTurnUpdated(code);
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "roundEnded", roundEnded,
                "roundNumber", room.getRoundNumber(),
                "currentSpeakerIndex", room.getCurrentSpeakerIndex()
        ));
    }

    /**
     * Host action to initiate voting phase.
     */
    @PostMapping("/{roomCode}/start-voting")
    @ResponseBody
    public ResponseEntity<?> startVoting(@PathVariable String roomCode, @RequestParam String hostId) {
        String code = roomCode.trim().toUpperCase();
        GameRoom room = roomService.getRoom(code);
        if (room == null) return ResponseEntity.badRequest().body(Map.of("error", "error.room.notfound"));
        if (!room.getHostId().equals(hostId)) return ResponseEntity.badRequest().body(Map.of("error", "error.not_host"));

        gameEngineService.startVoting(room);
        sseService.broadcastVotingStarted(code);
        return ResponseEntity.ok(Map.of("status", "voting"));
    }

    /**
     * Cast vote endpoint.
     */
    @PostMapping("/{roomCode}/vote")
    @ResponseBody
    public ResponseEntity<?> submitVote(@PathVariable String roomCode,
                                        @RequestParam String voterId,
                                        @RequestParam String targetId) {
        String code = roomCode.trim().toUpperCase();
        GameRoom room = roomService.getRoom(code);
        if (room == null) return ResponseEntity.badRequest().body(Map.of("error", "error.room.notfound"));

        try {
            boolean allVoted = gameEngineService.submitVote(room, voterId, targetId);

            if (allVoted) {
                concludeVotingInternal(room);
            } else {
                sseService.broadcastVoteUpdated(code);
            }

            return ResponseEntity.ok(Map.of("status", "ok", "allVoted", allVoted, "roomStatus", room.getStatus()));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Conclude voting endpoint (Manual Host button or auto trigger).
     */
    @PostMapping("/{roomCode}/conclude-vote")
    @ResponseBody
    public ResponseEntity<?> concludeVote(@PathVariable String roomCode, @RequestParam String hostId) {
        String code = roomCode.trim().toUpperCase();
        GameRoom room = roomService.getRoom(code);
        if (room == null) return ResponseEntity.badRequest().body(Map.of("error", "error.room.notfound"));
        if (!room.getHostId().equals(hostId)) return ResponseEntity.badRequest().body(Map.of("error", "error.not_host"));

        concludeVotingInternal(room);
        return ResponseEntity.ok(Map.of("status", "ok", "roomStatus", room.getStatus()));
    }

    /**
     * Last Guess submission endpoint for eliminated Spy / Mr. White.
     */
    @PostMapping("/{roomCode}/last-guess")
    @ResponseBody
    public ResponseEntity<?> submitLastGuess(@PathVariable String roomCode,
                                             @RequestParam String playerId,
                                             @RequestParam String guess) {
        String code = roomCode.trim().toUpperCase();
        GameRoom room = roomService.getRoom(code);
        if (room == null) return ResponseEntity.badRequest().body(Map.of("error", "error.room.notfound"));
        if (!playerId.equals(room.getEliminatedPlayerId())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Chỉ mèo bị loại mới được quyền đoán từ!"));
        }

        boolean success = gameEngineService.checkLastGuess(room, guess);
        if (success) {
            sseService.broadcastGameEnded(code);
            return ResponseEntity.ok(Map.of("status", "ok", "success", true, "roomStatus", room.getStatus()));
        } else {
            return ResponseEntity.ok(Map.of("status", "ok", "success", false, "message", "Mật lệnh chưa chính xác! Hãy thử lại nhanh!"));
        }
    }

    /**
     * Last Guess timeout endpoint (triggered when 20s expires without a correct guess).
     */
    @PostMapping("/{roomCode}/last-guess-timeout")
    @ResponseBody
    public ResponseEntity<?> lastGuessTimeout(@PathVariable String roomCode) {
        String code = roomCode.trim().toUpperCase();
        GameRoom room = roomService.getRoom(code);
        if (room == null || room.getStatus() != RoomStatus.LAST_GUESS) {
            return ResponseEntity.ok(Map.of("status", "ignored"));
        }

        gameEngineService.handleLastGuessTimeout(room);
        if (room.getStatus() == RoomStatus.RESULT) {
            sseService.broadcastGameEnded(code);
        } else {
            sseService.broadcastTurnUpdated(code);
        }
        return ResponseEntity.ok(Map.of("status", "ok", "roomStatus", room.getStatus()));
    }

    /**
     * Host action to start the next round from ROUND_SUMMARY.
     */
    @PostMapping("/{roomCode}/next-round")
    @ResponseBody
    public ResponseEntity<?> nextRound(@PathVariable String roomCode, @RequestParam String hostId) {
        String code = roomCode.trim().toUpperCase();
        GameRoom room = roomService.getRoom(code);
        if (room == null) return ResponseEntity.badRequest().body(Map.of("error", "error.room.notfound"));
        if (!room.getHostId().equals(hostId)) return ResponseEntity.badRequest().body(Map.of("error", "error.not_host"));

        gameEngineService.startNextRound(room);
        sseService.broadcastTurnUpdated(code);
        return ResponseEntity.ok(Map.of("status", "ok", "roundNumber", room.getRoundNumber(), "roomStatus", room.getStatus()));
    }

    /**
     * Host action to spin a new penalty on the result screen.
     */
    @PostMapping("/{roomCode}/spin-penalty")
    @ResponseBody
    public ResponseEntity<?> spinPenalty(@PathVariable String roomCode, @RequestParam String hostId) {
        String code = roomCode.trim().toUpperCase();
        GameRoom room = roomService.getRoom(code);
        if (room == null) return ResponseEntity.badRequest().body(Map.of("error", "error.room.notfound"));
        if (!room.getHostId().equals(hostId)) return ResponseEntity.badRequest().body(Map.of("error", "error.not_host"));

        String newPenalty = gameEngineService.spinNewPenalty(room);
        sseService.broadcastPenaltyUpdated(code, newPenalty);
        return ResponseEntity.ok(Map.of("status", "ok", "penalty", newPenalty));
    }

    /**
     * Restart game back to lobby.
     */
    @PostMapping("/{roomCode}/restart")
    @ResponseBody
    public ResponseEntity<?> restartGame(@PathVariable String roomCode, @RequestParam String hostId) {
        String code = roomCode.trim().toUpperCase();
        GameRoom room = roomService.getRoom(code);
        if (room == null) return ResponseEntity.badRequest().body(Map.of("error", "error.room.notfound"));
        if (!room.getHostId().equals(hostId)) return ResponseEntity.badRequest().body(Map.of("error", "error.not_host"));

        gameEngineService.resetGame(room);
        sseService.broadcastGameRestarted(code);
        return ResponseEntity.ok(Map.of("status", "restarted"));
    }

    /**
     * Dynamic fragment endpoint returning current game view.
     */
    @GetMapping("/{roomCode}/game-view")
    public String getGameView(@PathVariable String roomCode,
                              @RequestParam(required = false) String playerId,
                              Model model) {
        String code = roomCode.trim().toUpperCase();
        GameRoom room = roomService.getRoom(code);
        if (room == null) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Room not found");
        }

        // Auto-resolve expired LAST_GUESS on server-side if 20s has elapsed
        if (room.getStatus() == RoomStatus.LAST_GUESS && room.getLastGuessEndTime() != null) {
            if (java.time.Instant.now().isAfter(room.getLastGuessEndTime())) {
                gameEngineService.handleLastGuessTimeout(room);
                if (room.getStatus() == RoomStatus.RESULT) {
                    sseService.broadcastGameEnded(code);
                } else {
                    sseService.broadcastTurnUpdated(code);
                }
            }
        }

        Player current = (playerId != null) ? room.getPlayer(playerId) : null;
        boolean isHost = current != null && current.isHost();

        model.addAttribute("room", room);
        model.addAttribute("roomCode", code);
        model.addAttribute("currentPlayer", current);
        model.addAttribute("isHost", isHost);
        model.addAttribute("players", room.getPlayersList());
        model.addAttribute("alivePlayers", room.getAlivePlayers());
        model.addAttribute("currentSpeaker", room.getCurrentSpeaker());
        model.addAttribute("eliminatedPlayer", room.getEliminatedPlayerId() != null ? room.getPlayer(room.getEliminatedPlayerId()) : null);
        model.addAttribute("status", room.getStatus().name());
        return "fragments/game-fragments :: game-container";
    }

    private void concludeVotingInternal(GameRoom room) {
        String code = room.getRoomCode();
        gameEngineService.concludeVoting(room);
        if (room.getStatus() == RoomStatus.LAST_GUESS) {
            sseService.broadcastLastGuessStarted(code);
        } else if (room.getStatus() == RoomStatus.RESULT) {
            sseService.broadcastGameEnded(code);
        } else {
            sseService.broadcastTurnUpdated(code);
        }
    }
}
