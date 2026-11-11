package com.spygame.dto;

import com.spygame.model.WordPair;

public class WordPairDto {
    private String topic;
    private String civilianWord;
    private String spyWord;
    private boolean parity = true;

    public WordPairDto() {}

    public WordPairDto(String topic, String civilianWord, String spyWord) {
        this.topic = topic != null ? topic.trim() : "";
        this.civilianWord = civilianWord != null ? civilianWord.trim() : "";
        this.spyWord = spyWord != null ? spyWord.trim() : "";
        int c1 = WordPair.countWords(this.civilianWord);
        int c2 = WordPair.countWords(this.spyWord);
        this.parity = (c1 > 0 && c1 == c2);
    }

    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }

    public String getCivilianWord() { return civilianWord; }
    public void setCivilianWord(String civilianWord) { this.civilianWord = civilianWord; }

    public String getSpyWord() { return spyWord; }
    public void setSpyWord(String spyWord) { this.spyWord = spyWord; }

    public boolean isParity() { return parity; }
    public void setParity(boolean parity) { this.parity = parity; }
}
