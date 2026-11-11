package com.spygame.model;

import java.util.Objects;

/**
 * Word Pair consisting of a Civilian Word and a Spy Word with strict Word-Count Parity.
 * Conforms to .antigravity/rules.md (Section 2 & 4).
 */
public class WordPair {
    private String topic;
    private String civilianWord;
    private String spyWord;

    public WordPair() {}

    public WordPair(String topic, String civilianWord, String spyWord) {
        this.topic = topic;
        this.civilianWord = civilianWord;
        this.spyWord = spyWord;
    }

    public static int countWords(String text) {
        if (text == null || text.trim().isEmpty()) {
            return 0;
        }
        return text.trim().split("\\s+").length;
    }

    /**
     * Word-Count Parity Rule: civilianWord and spyWord MUST have the exact same word count!
     */
    public boolean hasWordCountParity() {
        if (civilianWord == null || spyWord == null) return false;
        int count1 = countWords(civilianWord);
        int count2 = countWords(spyWord);
        return count1 > 0 && count1 == count2;
    }

    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }

    public String getCivilianWord() { return civilianWord; }
    public void setCivilianWord(String civilianWord) { this.civilianWord = civilianWord; }

    public String getSpyWord() { return spyWord; }
    public void setSpyWord(String spyWord) { this.spyWord = spyWord; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WordPair wordPair = (WordPair) o;
        return Objects.equals(topic, wordPair.topic) &&
               Objects.equals(civilianWord, wordPair.civilianWord) &&
               Objects.equals(spyWord, wordPair.spyWord);
    }

    @Override
    public int hashCode() {
        return Objects.hash(topic, civilianWord, spyWord);
    }
}
