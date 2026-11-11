package com.spygame.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-Memory Game Room state.
 * Conforms to .antigravity/rules.md (Section 1 & 2).
 */
public class GameRoom {
    private final String roomCode;
    private String hostId;
    private RoomStatus status = RoomStatus.LOBBY;
    private final Map<String, Player> players = new ConcurrentHashMap<>();

    private boolean mrWhiteEnabled = false;
    private int timerSeconds = 45;
    private String selectedDeckId = "vi_street_food";
    private String selectedDeckName = "Ẩm Thực Đường Phố";
    private WordPair currentWordPair;

    // === Phase 2 Lifecycle State Fields ===
    private int roundNumber = 1;
    private int currentSpeakerIndex = 0;
    private final List<String> speakingOrder = new ArrayList<>();
    private final Map<String, String> votes = new ConcurrentHashMap<>(); // voterId -> targetId
    private String eliminatedPlayerId;
    private String lastGuessWordHint;
    private Instant lastGuessEndTime;
    private Boolean lastGuessSuccess;
    private String winnerFaction; // "CIVILIAN", "SPY", "MR_WHITE"
    private String winReason;
    private String selectedPenalty;

    private final Instant createdAt;
    private Instant lastActivityTime;

    public GameRoom(String roomCode, String hostId) {
        this.roomCode = roomCode;
        this.hostId = hostId;
        this.createdAt = Instant.now();
        this.lastActivityTime = Instant.now();
    }

    public synchronized void addPlayer(Player player) {
        players.put(player.getId(), player);
        touch();
    }

    public synchronized Player removePlayer(String playerId) {
        if (playerId == null) {
            return null;
        }
        Player removed = players.remove(playerId);
        touch();
        // If host leaves, assign next available player as host
        if (removed != null && removed.isHost() && !players.isEmpty()) {
            Player nextHost = players.values().stream()
                    .min(Comparator.comparing(Player::getJoinedAt))
                    .orElse(null);
            if (nextHost != null) {
                nextHost.setHost(true);
                this.hostId = nextHost.getId();
            }
        }
        return removed;
    }

    public Player getPlayer(String playerId) {
        if (playerId == null) {
            return null;
        }
        return players.get(playerId);
    }

    public Player getHost() {
        if (hostId == null) {
            return null;
        }
        return players.get(hostId);
    }

    public List<Player> getPlayersList() {
        List<Player> list = new ArrayList<>(players.values());
        list.sort(Comparator.comparing(Player::getJoinedAt));
        return list;
    }

    public int getPlayerCount() {
        return players.size();
    }

    public boolean canStart() {
        return players.size() >= 3;
    }

    public void touch() {
        this.lastActivityTime = Instant.now();
    }

    public String getRoomCode() { return roomCode; }
    public String getHostId() { return hostId; }
    public void setHostId(String hostId) { this.hostId = hostId; }

    public RoomStatus getStatus() { return status; }
    public void setStatus(RoomStatus status) {
        this.status = status;
        touch();
    }

    public Map<String, Player> getPlayers() { return players; }

    public boolean isMrWhiteEnabled() { return mrWhiteEnabled; }
    public void setMrWhiteEnabled(boolean mrWhiteEnabled) {
        this.mrWhiteEnabled = mrWhiteEnabled;
        touch();
    }

    public int getTimerSeconds() { return timerSeconds; }
    public void setTimerSeconds(int timerSeconds) {
        this.timerSeconds = timerSeconds;
        touch();
    }

    public String getSelectedDeckId() { return selectedDeckId; }
    public void setSelectedDeckId(String selectedDeckId) {
        this.selectedDeckId = selectedDeckId;
        touch();
    }

    public String getSelectedDeckName() { return selectedDeckName; }
    public void setSelectedDeckName(String selectedDeckName) {
        this.selectedDeckName = selectedDeckName;
        touch();
    }

    public WordPair getCurrentWordPair() { return currentWordPair; }
    public void setCurrentWordPair(WordPair currentWordPair) {
        this.currentWordPair = currentWordPair;
        touch();
    }

    // === Phase 2 Game Helpers ===

    public List<Player> getAlivePlayers() {
        List<Player> alive = new ArrayList<>();
        for (Player p : players.values()) {
            if (p.isAlive()) {
                alive.add(p);
            }
        }
        alive.sort(Comparator.comparing(Player::getJoinedAt));
        return alive;
    }

    public long getAliveCiviliansCount() {
        return players.values().stream()
                .filter(p -> p.isAlive() && p.getRole() == Role.CIVILIAN)
                .count();
    }

    public long getAliveSpiesAndMrWhiteCount() {
        return players.values().stream()
                .filter(p -> p.isAlive() && (p.getRole() == Role.SPY || p.getRole() == Role.MR_WHITE))
                .count();
    }

    public Player getCurrentSpeaker() {
        if (speakingOrder.isEmpty()) return null;
        if (currentSpeakerIndex < 0 || currentSpeakerIndex >= speakingOrder.size()) {
            currentSpeakerIndex = 0;
        }
        String speakerId = speakingOrder.get(currentSpeakerIndex);
        return getPlayer(speakerId);
    }

    public synchronized boolean advanceSpeaker() {
        if (speakingOrder.isEmpty()) return false;
        currentSpeakerIndex++;
        if (currentSpeakerIndex >= speakingOrder.size()) {
            // Completed current speaking round
            currentSpeakerIndex = 0;
            roundNumber++;
            touch();
            return true; // Round ended
        }
        touch();
        return false; // Still within round
    }

    public synchronized void clearVotes() {
        votes.clear();
        for (Player p : players.values()) {
            p.setHasVoted(false);
            p.setVotedTargetId(null);
        }
        touch();
    }

    public synchronized void startNextRound() {
        this.roundNumber++;
        this.clearVotes();
        // Point currentSpeakerIndex to the first ALIVE player in speakingOrder
        int idx = 0;
        while (idx < speakingOrder.size()) {
            Player p = getPlayer(speakingOrder.get(idx));
            if (p != null && p.isAlive()) {
                break;
            }
            idx++;
        }
        this.currentSpeakerIndex = (idx < speakingOrder.size()) ? idx : 0;
        this.status = RoomStatus.PLAYING;
        touch();
    }

    public boolean hasEveryoneAliveVoted() {
        List<Player> alive = getAlivePlayers();
        if (alive.isEmpty()) return false;
        return alive.stream().allMatch(Player::isHasVoted);
    }

    public synchronized void resetForNewGame() {
        this.status = RoomStatus.LOBBY;
        this.roundNumber = 1;
        this.currentSpeakerIndex = 0;
        this.speakingOrder.clear();
        this.votes.clear();
        this.eliminatedPlayerId = null;
        this.lastGuessWordHint = null;
        this.lastGuessEndTime = null;
        this.lastGuessSuccess = null;
        this.winnerFaction = null;
        this.winReason = null;
        this.selectedPenalty = null;
        this.currentWordPair = null;

        for (Player p : players.values()) {
            p.setAlive(true);
            p.setHasVoted(false);
            p.setVotedTargetId(null);
            p.setRole(null);
            p.setSecretWord(null);
        }
        touch();
    }

    // === Phase 2 Getters and Setters ===

    public int getRoundNumber() { return roundNumber; }
    public void setRoundNumber(int roundNumber) { this.roundNumber = roundNumber; touch(); }

    public int getCurrentSpeakerIndex() { return currentSpeakerIndex; }
    public void setCurrentSpeakerIndex(int currentSpeakerIndex) { this.currentSpeakerIndex = currentSpeakerIndex; touch(); }

    public List<String> getSpeakingOrder() { return speakingOrder; }
    public Map<String, String> getVotes() { return votes; }

    public String getEliminatedPlayerId() { return eliminatedPlayerId; }
    public void setEliminatedPlayerId(String eliminatedPlayerId) { this.eliminatedPlayerId = eliminatedPlayerId; touch(); }

    public String getLastGuessWordHint() { return lastGuessWordHint; }
    public void setLastGuessWordHint(String lastGuessWordHint) { this.lastGuessWordHint = lastGuessWordHint; touch(); }

    public Instant getLastGuessEndTime() { return lastGuessEndTime; }
    public void setLastGuessEndTime(Instant lastGuessEndTime) { this.lastGuessEndTime = lastGuessEndTime; touch(); }

    public Boolean getLastGuessSuccess() { return lastGuessSuccess; }
    public void setLastGuessSuccess(Boolean lastGuessSuccess) { this.lastGuessSuccess = lastGuessSuccess; touch(); }

    public String getWinnerFaction() { return winnerFaction; }
    public void setWinnerFaction(String winnerFaction) { this.winnerFaction = winnerFaction; touch(); }

    public String getWinReason() { return winReason; }
    public void setWinReason(String winReason) { this.winReason = winReason; touch(); }

    public String getSelectedPenalty() { return selectedPenalty; }
    public void setSelectedPenalty(String selectedPenalty) { this.selectedPenalty = selectedPenalty; touch(); }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastActivityTime() { return lastActivityTime; }
}
