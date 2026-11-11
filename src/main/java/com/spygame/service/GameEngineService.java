package com.spygame.service;

import com.spygame.model.GameRoom;
import com.spygame.model.Player;
import com.spygame.model.Role;
import com.spygame.model.RoomStatus;
import com.spygame.model.WordPair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.text.Normalizer;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Core Game Engine Service managing the complete game lifecycle:
 * LOBBY -> PLAYING -> VOTING -> LAST_GUESS -> RESULT
 * Conforms to .antigravity/rules.md (Section 2 & 4) and user specifications.
 */
@Service
public class GameEngineService {
    private static final Logger log = LoggerFactory.getLogger(GameEngineService.class);
    private static final Pattern DIACRITICS_PATTERN = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

    private final WordBankService wordBankService;
    private final SecureRandom random = new SecureRandom();

    private static final List<String> PENALTIES = List.of(
            "Kêu meow 3 lần trước mọi người với biểu cảm dễ thương nhất! 🐾",
            "Bắt chước dáng mèo nằm sưởi nắng hoặc duỗi chân mèo trong 10 giây! ☀️",
            "Uống 1 ngụm nước bằng thìa giống như mèo liếm sữa! 🥛",
            "Khen ngợi Chánh Án và bầy mèo còn lại bằng 3 câu thơ con cóc! 🐱",
            "Hát một đoạn bài thiếu nhi bất kỳ nhưng bằng giọng mèo kêu meow~ 🎶",
            "Lấy 2 tay làm tai mèo và chào mọi người theo phong cách Neko! 😸",
            "Đứng dậy xoay tròn 1 vòng rồi giả vờ vồ lấy đuôi của mình! 🌀",
            "Chấp nhận để người bên cạnh đặt cho 1 biệt danh mèo ngáo trong cả buổi chơi! 😼",
            "Giữ nguyên nét mặt ngơ ngác không chớp mắt trong 15 giây! 👁️",
            "Làm điệu bộ mèo rửa mặt bằng hai mu bàn tay cực kỳ nghiêm túc! 🐾"
    );

    public GameEngineService(WordBankService wordBankService) {
        this.wordBankService = wordBankService;
    }

    /**
     * Initializes game with default role distribution.
     */
    public synchronized void startGame(GameRoom room, String creatorToken) {
        startGame(room, creatorToken, null, null, null);
    }

    /**
     * Initializes game with custom role counts configured by Host, assigns roles, picks word pair, and sets speaking order.
     */
    public synchronized void startGame(GameRoom room, String creatorToken, Integer customCivilians, Integer customSpies, Integer customMrWhites) {
        if (!room.canStart()) {
            throw new IllegalStateException("lobby.minplayers.warning");
        }

        // 1. Pick a random WordPair from selected deck
        WordPair pair = wordBankService.getRandomWordPair(room.getSelectedDeckId(), creatorToken);
        room.setCurrentWordPair(pair);

        List<Player> playerList = room.getPlayersList();
        int totalPlayers = playerList.size();

        // 2. Role distribution
        int civCount;
        int spyCount;
        int mrWhiteCount;

        if (customCivilians != null && customSpies != null && customMrWhites != null) {
            if (customCivilians + customSpies + customMrWhites != totalPlayers) {
                throw new IllegalArgumentException("Tổng số vai trò (" + (customCivilians + customSpies + customMrWhites) + ") không khớp với số người chơi (" + totalPlayers + ")!");
            }
            civCount = customCivilians;
            spyCount = customSpies;
            mrWhiteCount = customMrWhites;
        } else {
            // Default distribution
            boolean hasMrWhite = room.isMrWhiteEnabled() && totalPlayers >= 4;
            spyCount = (totalPlayers >= 7) ? 2 : 1;
            mrWhiteCount = hasMrWhite ? 1 : 0;
            civCount = totalPlayers - spyCount - mrWhiteCount;
        }

        List<Player> shuffledPlayers = new ArrayList<>(playerList);
        Collections.shuffle(shuffledPlayers, random);

        int index = 0;
        for (int i = 0; i < mrWhiteCount; i++) {
            Player mrWhite = shuffledPlayers.get(index++);
            mrWhite.setRole(Role.MR_WHITE);
            mrWhite.setSecretWord(null);
        }

        for (int i = 0; i < spyCount; i++) {
            Player spy = shuffledPlayers.get(index++);
            spy.setRole(Role.SPY);
            spy.setSecretWord(pair.getSpyWord());
        }

        for (int i = 0; i < civCount; i++) {
            Player civ = shuffledPlayers.get(index++);
            civ.setRole(Role.CIVILIAN);
            civ.setSecretWord(pair.getCivilianWord());
        }

        // Reset alive & voting state for all players
        for (Player p : playerList) {
            p.setAlive(true);
            p.setHasVoted(false);
            p.setVotedTargetId(null);
        }

        // 3. Speaking order with Round 1 constraint:
        // Mr. White NEVER speaks first in Round 1 if other roles exist!
        List<String> order = playerList.stream().map(Player::getId).collect(Collectors.toList());
        Collections.shuffle(order, random);

        Player firstSpeaker = room.getPlayer(order.get(0));
        if (firstSpeaker != null && firstSpeaker.getRole() == Role.MR_WHITE) {
            // Swap with first non-Mr. White player in the order
            for (int i = 1; i < order.size(); i++) {
                Player candidate = room.getPlayer(order.get(i));
                if (candidate != null && candidate.getRole() != Role.MR_WHITE) {
                    Collections.swap(order, 0, i);
                    break;
                }
            }
        }

        room.getSpeakingOrder().clear();
        room.getSpeakingOrder().addAll(order);
        room.setRoundNumber(1);
        room.setCurrentSpeakerIndex(0);
        room.clearVotes();
        room.setEliminatedPlayerId(null);
        room.setLastGuessWordHint(null);
        room.setLastGuessEndTime(null);
        room.setLastGuessSuccess(null);
        room.setWinnerFaction(null);
        room.setWinReason(null);
        room.setSelectedPenalty(null);

        room.setStatus(RoomStatus.PLAYING);
        log.info("Started game in room {} with {} players ({} Civ, {} Spy, {} MrWhite). WordPair: '{}' vs '{}'",
                room.getRoomCode(), totalPlayers, civCount, spyCount, mrWhiteCount, pair.getCivilianWord(), pair.getSpyWord());
    }

    /**
     * Advances speaker to next alive player.
     * Returns true if round completed (wrapped around).
     */
    public synchronized boolean advanceTurn(GameRoom room) {
        if (room.getStatus() != RoomStatus.PLAYING) {
            return false;
        }

        List<String> order = room.getSpeakingOrder();
        if (order.isEmpty()) return false;

        int initialIndex = room.getCurrentSpeakerIndex();
        int nextIndex = (initialIndex + 1) % order.size();
        boolean wrapped = (nextIndex <= initialIndex);

        // Find next alive player
        int attempts = 0;
        while (attempts < order.size()) {
            Player p = room.getPlayer(order.get(nextIndex));
            if (p != null && p.isAlive()) {
                break;
            }
            nextIndex = (nextIndex + 1) % order.size();
            if (nextIndex == 0) wrapped = true;
            attempts++;
        }

        room.setCurrentSpeakerIndex(nextIndex);
        if (wrapped) {
            room.setRoundNumber(room.getRoundNumber() + 1);
        }
        return wrapped;
    }

    /**
     * Starts the voting phase.
     */
    public synchronized void startVoting(GameRoom room) {
        room.clearVotes();
        room.setStatus(RoomStatus.VOTING);
        log.info("Voting phase started in room {}", room.getRoomCode());
    }

    /**
     * Records a vote from voter to target.
     * Returns true if all alive players have voted.
     */
    public synchronized boolean submitVote(GameRoom room, String voterId, String targetId) {
        if (room.getStatus() != RoomStatus.VOTING) {
            throw new IllegalStateException("Phòng chưa ở trạng thái bỏ phiếu!");
        }

        Player voter = room.getPlayer(voterId);
        Player target = room.getPlayer(targetId);

        if (voter == null || !voter.isAlive()) {
            throw new IllegalArgumentException("Người bỏ phiếu không hợp lệ hoặc đã bị loại!");
        }
        if (target == null || !target.isAlive()) {
            throw new IllegalArgumentException("Mục tiêu bỏ phiếu không hợp lệ!");
        }

        room.getVotes().put(voterId, targetId);
        voter.setHasVoted(true);
        voter.setVotedTargetId(targetId);

        return room.hasEveryoneAliveVoted();
    }

    /**
     * Tally votes and handles elimination according to refined Phase 2 rules:
     * - Tie: No elimination, returns to PLAYING.
     * - Single target eliminated:
     *   * If Mr. White -> Trigger LAST_GUESS (20s).
     *   * If Spy and is LAST surviving Spy -> Trigger LAST_GUESS (20s).
     *   * If Spy but other Spies still alive -> NO last guess, check win condition.
     *   * If Civilian -> NO last guess, check win condition.
     */
    public synchronized VotingResult concludeVoting(GameRoom room) {
        if (room.getStatus() != RoomStatus.VOTING) {
            throw new IllegalStateException("Phòng chưa ở trạng thái bỏ phiếu!");
        }

        Map<String, String> votes = room.getVotes();
        if (votes.isEmpty()) {
            // No votes cast -> advance to ROUND_SUMMARY without elimination
            room.setEliminatedPlayerId(null);
            room.setStatus(RoomStatus.ROUND_SUMMARY);
            room.clearVotes();
            return new VotingResult(true, null, "Không có phiếu bầu nào được ghi nhận!");
        }

        // Count votes per target among currently alive players
        Map<String, Long> countMap = votes.values().stream()
                .filter(tid -> {
                    Player p = room.getPlayer(tid);
                    return p != null && p.isAlive();
                })
                .collect(Collectors.groupingBy(tid -> tid, Collectors.counting()));

        if (countMap.isEmpty()) {
            room.setEliminatedPlayerId(null);
            room.setStatus(RoomStatus.ROUND_SUMMARY);
            room.clearVotes();
            return new VotingResult(true, null, "Không có phiếu hợp lệ!");
        }

        long maxVotes = Collections.max(countMap.values());
        List<String> topTargets = countMap.entrySet().stream()
                .filter(e -> e.getValue() == maxVotes)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        // Check tie
        if (topTargets.size() > 1) {
            room.setEliminatedPlayerId(null);
            room.clearVotes();
            room.setStatus(RoomStatus.ROUND_SUMMARY);
            log.info("Tie vote in room {} between {} players ({} votes each). Advancing to ROUND_SUMMARY.",
                    room.getRoomCode(), topTargets.size(), maxVotes);
            return new VotingResult(true, null, "Hòa phiếu bầu! Chưa có mèo nào bị kết tội.");
        }

        // Single most-voted player
        String eliminatedId = topTargets.get(0);
        Player eliminated = room.getPlayer(eliminatedId);
        if (eliminated == null) {
            room.setEliminatedPlayerId(null);
            room.setStatus(RoomStatus.ROUND_SUMMARY);
            room.clearVotes();
            return new VotingResult(true, null, "Lỗi xác định mèo bị loại!");
        }

        eliminated.setAlive(false);
        room.setEliminatedPlayerId(eliminatedId);
        log.info("Player '{}' ({}) was voted out in room {}. Role: {}",
                eliminated.getNickname(), eliminatedId, room.getRoomCode(), eliminated.getRole());

        // Rule Check:
        // 1. If Mr. White -> Trigger 20s LAST_GUESS
        if (eliminated.getRole() == Role.MR_WHITE) {
            triggerLastGuess(room);
            return new VotingResult(false, eliminated,
                    "Mr. White đã bị phát hiện! Kích hoạt 20 giây lật kèo!");
        }

        // 2. If Spy -> Check if this is the LAST alive Spy
        if (eliminated.getRole() == Role.SPY) {
            long remainingAliveSpies = room.getAlivePlayers().stream()
                    .filter(p -> p.getRole() == Role.SPY)
                    .count();

            if (remainingAliveSpies == 0) {
                // Last Spy eliminated -> Trigger LAST_GUESS!
                triggerLastGuess(room);
                return new VotingResult(false, eliminated,
                        "Gián Điệp cuối cùng đã bị bắt! Kích hoạt 20 giây lật kèo!");
            } else {
                // Other spies still alive -> Do NOT trigger last guess; keep role secret & check win condition
                boolean gameEnded = checkAndApplyEndGameCondition(room);
                if (gameEnded) {
                    return new VotingResult(false, eliminated, "Phiên tòa đã có phán quyết chung cuộc!");
                } else {
                    room.clearVotes();
                    return new VotingResult(false, eliminated,
                            "Một con mèo đã bị trục xuất khỏi phiên tòa! Phiên tòa tiếp tục tranh luận.");
                }
            }
        }

        // 3. If Civilian -> Keep role secret, NO last guess, check win condition directly
        boolean gameEnded = checkAndApplyEndGameCondition(room);
        if (gameEnded) {
            return new VotingResult(false, eliminated, "Phiên tòa đã có phán quyết chung cuộc!");
        } else {
            room.clearVotes();
            return new VotingResult(false, eliminated,
                    "Một con mèo đã bị trục xuất khỏi phiên tòa! Phiên tòa tiếp tục tranh luận.");
        }
    }

    /**
     * Activates 20-second LAST_GUESS phase for the eliminated villain.
     */
    private void triggerLastGuess(GameRoom room) {
        room.setStatus(RoomStatus.LAST_GUESS);
        room.setLastGuessEndTime(Instant.now().plusSeconds(20));
        String civilianWord = (room.getCurrentWordPair() != null)
                ? room.getCurrentWordPair().getCivilianWord()
                : "";
        room.setLastGuessWordHint(generateWordHint(civilianWord));
        room.clearVotes();
    }

    /**
     * Checks the guess submitted during the LAST_GUESS phase.
     * Uses Vietnamese Normalizer to strip diacritics and match case-insensitively.
     */
    public synchronized boolean checkLastGuess(GameRoom room, String guess) {
        if (room.getStatus() != RoomStatus.LAST_GUESS) {
            return false;
        }

        String civilianWord = (room.getCurrentWordPair() != null)
                ? room.getCurrentWordPair().getCivilianWord()
                : "";

        String normalizedGuess = normalizeVietnamese(guess);
        String normalizedTarget = normalizeVietnamese(civilianWord);

        boolean isCorrect = !normalizedGuess.isEmpty() && normalizedGuess.equals(normalizedTarget);

        if (isCorrect) {
            // Last Guess success! Villain steals victory!
            room.setLastGuessSuccess(true);
            room.setStatus(RoomStatus.RESULT);

            Player eliminated = room.getPlayer(room.getEliminatedPlayerId());
            if (eliminated != null && eliminated.getRole() == Role.MR_WHITE) {
                room.setWinnerFaction("MR_WHITE");
                room.setWinReason("result.mrwhite_last_guess_win");
            } else {
                room.setWinnerFaction("SPY");
                room.setWinReason("result.spy_last_guess_win");
            }
            room.setSelectedPenalty(pickRandomPenalty());
            log.info("Room {}: Last Guess SUCCESS by {}! Faction {} wins!",
                    room.getRoomCode(), room.getEliminatedPlayerId(), room.getWinnerFaction());
            return true;
        } else {
            // Guess is incorrect, but allow further attempts while timer is running
            log.info("Room {}: Last Guess attempt '{}' was incorrect.", room.getRoomCode(), guess);
            return false;
        }
    }

    /**
     * Handles timeout when 20s expires during LAST_GUESS without a correct answer.
     */
    public synchronized void handleLastGuessTimeout(GameRoom room) {
        if (room.getStatus() != RoomStatus.LAST_GUESS) {
            return;
        }
        room.setLastGuessSuccess(false);
        checkAndApplyEndGameCondition(room);
        log.info("Room {}: Last Guess timed out. Concluded to status: {}",
                room.getRoomCode(), room.getStatus());
    }

    /**
     * Starts the next discussion round (advancing from ROUND_SUMMARY to PLAYING).
     */
    public synchronized void startNextRound(GameRoom room) {
        room.startNextRound();
        log.info("Room {}: Advanced to Round {} (status: PLAYING)",
                room.getRoomCode(), room.getRoundNumber());
    }

    /**
     * Evaluates end-game conditions per refined Phase 2 specifications:
     * 1. All Spies and Mr. White eliminated -> CIVILIAN wins.
     * 2. Total alive players <= 2 -> RESULT screen:
     *    - 2 Civilians -> CIVILIAN wins.
     *    - 1 Civilian & 1 Spy -> SPY wins.
     *    - 1 Civilian & 1 Mr. White -> MR_WHITE wins.
     *    - 1 Spy & 1 Mr. White -> SPY_AND_MR_WHITE (both win).
     *    - 2 Spies -> SPY wins.
     *    - 2 Mr. White -> MR_WHITE wins.
     * 3. Total alive >= 3 and at least 1 villain remains -> Continue PLAYING.
     *
     * Returns true if game concluded (status is RESULT), false if continuing PLAYING.
     */
    public synchronized boolean checkAndApplyEndGameCondition(GameRoom room) {
        List<Player> alivePlayers = room.getAlivePlayers();
        long aliveCivilians = alivePlayers.stream().filter(p -> p.getRole() == Role.CIVILIAN).count();
        long aliveSpies = alivePlayers.stream().filter(p -> p.getRole() == Role.SPY).count();
        long aliveMrWhites = alivePlayers.stream().filter(p -> p.getRole() == Role.MR_WHITE).count();
        int totalAlive = alivePlayers.size();

        // 1. If all Spies and Mr. White have been eliminated -> Civilians win
        if (aliveSpies == 0 && aliveMrWhites == 0) {
            room.setStatus(RoomStatus.RESULT);
            room.setWinnerFaction("CIVILIAN");
            room.setWinReason("result.civilians_eliminated_all");
            room.setSelectedPenalty(pickRandomPenalty());
            log.info("Room {}: Civilians WIN by eliminating all villains!", room.getRoomCode());
            return true;
        }

        // 2. If total alive players <= 2 -> Evaluate by final composition
        if (totalAlive <= 2) {
            room.setStatus(RoomStatus.RESULT);
            room.setSelectedPenalty(pickRandomPenalty());

            if (aliveCivilians == 2) {
                room.setWinnerFaction("CIVILIAN");
                room.setWinReason("result.civilians_majority");
            } else if (aliveCivilians == 1 && aliveSpies == 1) {
                room.setWinnerFaction("SPY");
                room.setWinReason("result.villains_majority");
            } else if (aliveCivilians == 1 && aliveMrWhites == 1) {
                room.setWinnerFaction("MR_WHITE");
                room.setWinReason("result.mrwhite_majority");
            } else if (aliveSpies >= 1 && aliveMrWhites >= 1) {
                // 1 Spy and 1 Mr. White remaining
                room.setWinnerFaction("SPY_AND_MR_WHITE");
                room.setWinReason("result.both_villains_win");
            } else if (aliveSpies == 2) {
                room.setWinnerFaction("SPY");
                room.setWinReason("result.villains_majority");
            } else if (aliveMrWhites == 2) {
                room.setWinnerFaction("MR_WHITE");
                room.setWinReason("result.mrwhite_majority");
            } else {
                room.setWinnerFaction("SPY");
                room.setWinReason("result.villains_majority");
            }

            log.info("Room {}: Concluded with <= 2 players alive. Winner Faction: {}",
                    room.getRoomCode(), room.getWinnerFaction());
            return true;
        }

        // 3. Otherwise: >= 3 players remain and at least one villain is still alive -> Show ROUND_SUMMARY
        room.setStatus(RoomStatus.ROUND_SUMMARY);
        room.clearVotes();
        log.info("Room {}: {} players remain ({} Civ, {} Spy, {} MrWhite). Advancing to ROUND_SUMMARY.",
                room.getRoomCode(), totalAlive, aliveCivilians, aliveSpies, aliveMrWhites);
        return false;
    }

    /**
     * Generates a structural letter hint (e.g. "Bánh mì kẹp" -> "B _ _ _   m _   k _ _").
     */
    public String generateWordHint(String word) {
        if (word == null || word.trim().isEmpty()) return "";
        String[] parts = word.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i];
            if (!p.isEmpty()) {
                sb.append(p.charAt(0));
                for (int j = 1; j < p.length(); j++) {
                    sb.append(" _");
                }
            }
            if (i < parts.length - 1) {
                sb.append("   "); // Triple space between words
            }
        }
        return sb.toString();
    }

    /**
     * Normalizes text by removing Vietnamese accents, diacritics, and symbols.
     * Example: "Cà phê sữa" -> "ca phe sua"
     */
    public String normalizeVietnamese(String text) {
        if (text == null) return "";
        String nfd = Normalizer.normalize(text.trim(), Normalizer.Form.NFD);
        String withoutDiacritics = DIACRITICS_PATTERN.matcher(nfd).replaceAll("");
        return withoutDiacritics
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .toLowerCase()
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * Picks a random funny cat-themed penalty for the losing team.
     */
    public String pickRandomPenalty() {
        return PENALTIES.get(random.nextInt(PENALTIES.size()));
    }

    /**
     * Spins and sets a new random cat-themed penalty different from the current one.
     */
    public synchronized String spinNewPenalty(GameRoom room) {
        String current = room.getSelectedPenalty();
        List<String> pool = PENALTIES.stream()
                .filter(p -> !p.equals(current))
                .collect(Collectors.toList());
        String newPenalty = pool.isEmpty() ? PENALTIES.get(0) : pool.get(random.nextInt(pool.size()));
        room.setSelectedPenalty(newPenalty);
        log.info("Room {}: Host spun new penalty: {}", room.getRoomCode(), newPenalty);
        return newPenalty;
    }

    /**
     * Resets room back to LOBBY state.
     */
    public synchronized void resetGame(GameRoom room) {
        room.resetForNewGame();
        log.info("Room {} reset to LOBBY", room.getRoomCode());
    }

    // === Helper DTO ===
    public static class VotingResult {
        private final boolean tie;
        private final Player eliminatedPlayer;
        private final String message;

        public VotingResult(boolean tie, Player eliminatedPlayer, String message) {
            this.tie = tie;
            this.eliminatedPlayer = eliminatedPlayer;
            this.message = message;
        }

        public boolean isTie() { return tie; }
        public Player getEliminatedPlayer() { return eliminatedPlayer; }
        public String getMessage() { return message; }
    }
}
