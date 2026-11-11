package com.spygame.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Represents a player in a game room.
 */
public class Player {
    private String id;
    private String nickname;
    private int avatarIndex; // 1 to 8
    private boolean isHost;
    private Role role;
    private String secretWord;
    private boolean isAlive = true;
    private boolean hasVoted = false;
    private String votedTargetId;
    private Instant joinedAt;

    public Player() {
        this.joinedAt = Instant.now();
    }

    public Player(String id, String nickname, int avatarIndex, boolean isHost) {
        this.id = id;
        this.nickname = nickname;
        this.avatarIndex = avatarIndex;
        this.isHost = isHost;
        this.joinedAt = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public int getAvatarIndex() { return avatarIndex; }
    public void setAvatarIndex(int avatarIndex) { this.avatarIndex = avatarIndex; }

    public boolean isHost() { return isHost; }
    public void setHost(boolean host) { isHost = host; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public String getSecretWord() { return secretWord; }
    public void setSecretWord(String secretWord) { this.secretWord = secretWord; }

    public boolean isAlive() { return isAlive; }
    public void setAlive(boolean alive) { isAlive = alive; }

    public boolean isHasVoted() { return hasVoted; }
    public void setHasVoted(boolean hasVoted) { this.hasVoted = hasVoted; }

    public String getVotedTargetId() { return votedTargetId; }
    public void setVotedTargetId(String votedTargetId) { this.votedTargetId = votedTargetId; }

    public Instant getJoinedAt() { return joinedAt; }
    public void setJoinedAt(Instant joinedAt) { this.joinedAt = joinedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Player player = (Player) o;
        return Objects.equals(id, player.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
