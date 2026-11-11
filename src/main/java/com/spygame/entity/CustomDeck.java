package com.spygame.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Persistent Custom Deck created by users.
 * Identified by creatorToken (UUID) and optional recoveryPin.
 * Conforms to .antigravity/rules.md (Section 1 & 3).
 */
@Entity
@Table(name = "custom_decks", indexes = {
    @Index(name = "idx_deck_creator", columnList = "creatorToken"),
    @Index(name = "idx_deck_pin", columnList = "recoveryPin")
})
public class CustomDeck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String creatorToken;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 20)
    private String recoveryPin;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "deck", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CustomWordPair> wordPairs = new ArrayList<>();

    public CustomDeck() {}

    public CustomDeck(String creatorToken, String name, String recoveryPin) {
        this.creatorToken = creatorToken;
        this.name = name;
        this.recoveryPin = recoveryPin;
        this.createdAt = Instant.now();
    }

    public void addWordPair(CustomWordPair pair) {
        wordPairs.add(pair);
        pair.setDeck(this);
    }

    public void removeWordPair(CustomWordPair pair) {
        wordPairs.remove(pair);
        pair.setDeck(null);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCreatorToken() { return creatorToken; }
    public void setCreatorToken(String creatorToken) { this.creatorToken = creatorToken; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getRecoveryPin() { return recoveryPin; }
    public void setRecoveryPin(String recoveryPin) { this.recoveryPin = recoveryPin; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public List<CustomWordPair> getWordPairs() { return wordPairs; }
    public void setWordPairs(List<CustomWordPair> wordPairs) { this.wordPairs = wordPairs; }
}
