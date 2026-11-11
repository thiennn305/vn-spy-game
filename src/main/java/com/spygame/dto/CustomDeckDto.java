package com.spygame.dto;

import java.util.ArrayList;
import java.util.List;

public class CustomDeckDto {
    private String id;
    private String name;
    private String recoveryPin;
    private boolean custom = false;
    private int pairCount = 0;
    private List<WordPairDto> wordPairs = new ArrayList<>();

    public CustomDeckDto() {}

    public CustomDeckDto(String id, String name, boolean custom, List<WordPairDto> wordPairs) {
        this.id = id;
        this.name = name;
        this.custom = custom;
        this.wordPairs = wordPairs != null ? wordPairs : new ArrayList<>();
        this.pairCount = this.wordPairs.size();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getRecoveryPin() { return recoveryPin; }
    public void setRecoveryPin(String recoveryPin) { this.recoveryPin = recoveryPin; }

    public boolean isCustom() { return custom; }
    public void setCustom(boolean custom) { this.custom = custom; }

    public int getPairCount() { return pairCount; }
    public void setPairCount(int pairCount) { this.pairCount = pairCount; }

    public List<WordPairDto> getWordPairs() { return wordPairs; }
    public void setWordPairs(List<WordPairDto> wordPairs) {
        this.wordPairs = wordPairs;
        this.pairCount = wordPairs != null ? wordPairs.size() : 0;
    }
}
