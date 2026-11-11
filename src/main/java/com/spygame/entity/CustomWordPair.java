package com.spygame.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

/**
 * Word Pair within a Persistent Custom Deck.
 */
@Entity
@Table(name = "custom_word_pairs")
public class CustomWordPair {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String topic;

    @Column(nullable = false, length = 100)
    private String civilianWord;

    @Column(nullable = false, length = 100)
    private String spyWord;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deck_id", nullable = false)
    @JsonIgnore
    private CustomDeck deck;

    public CustomWordPair() {}

    public CustomWordPair(String topic, String civilianWord, String spyWord) {
        this.topic = topic;
        this.civilianWord = civilianWord;
        this.spyWord = spyWord;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }

    public String getCivilianWord() { return civilianWord; }
    public void setCivilianWord(String civilianWord) { this.civilianWord = civilianWord; }

    public String getSpyWord() { return spyWord; }
    public void setSpyWord(String spyWord) { this.spyWord = spyWord; }

    public CustomDeck getDeck() { return deck; }
    public void setDeck(CustomDeck deck) { this.deck = deck; }
}
