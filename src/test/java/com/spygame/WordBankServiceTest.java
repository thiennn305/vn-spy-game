package com.spygame;

import com.spygame.dto.CustomDeckDto;
import com.spygame.dto.WordPairDto;
import com.spygame.model.WordPair;
import com.spygame.service.WordBankService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class WordBankServiceTest {

    @Autowired
    private WordBankService wordBankService;

    @Test
    void testDefaultSystemDecksLoaded() {
        List<CustomDeckDto> viDecks = wordBankService.getAvailableDecks("vi", null);
        assertNotNull(viDecks);
        assertFalse(viDecks.isEmpty());

        // Street food deck should be present
        CustomDeckDto streetFood = viDecks.stream()
                .filter(d -> d.getId().equals("vi_street_food"))
                .findFirst()
                .orElse(null);

        assertNotNull(streetFood);
        assertEquals("Ẩm Thực Đường Phố", streetFood.getName());
        assertTrue(streetFood.getPairCount() >= 5);

        // Verify word pairs parity
        for (WordPairDto p : streetFood.getWordPairs()) {
            assertTrue(p.isParity(), "Pair should have parity: " + p.getCivilianWord() + " vs " + p.getSpyWord());
        }
    }

    @Test
    void testSaveAndGetCustomDeck() {
        String token = "test_creator_123";

        CustomDeckDto newDeck = new CustomDeckDto();
        newDeck.setName("Bộ Từ Lập Trình");
        newDeck.setRecoveryPin("1234");
        newDeck.setWordPairs(List.of(
                new WordPairDto("Code", "Java", "Kotlin"),
                new WordPairDto("Web", "Spring Boot", "Quarkus IO"),
                new WordPairDto("Database", "MySQL", "Postgres")
        ));

        CustomDeckDto saved = wordBankService.saveCustomDeck(token, newDeck);
        assertNotNull(saved);
        assertNotNull(saved.getId());
        assertTrue(saved.isCustom());
        assertEquals("Bộ Từ Lập Trình", saved.getName());
        assertEquals(3, saved.getPairCount());

        // Query available decks for this token
        List<CustomDeckDto> userDecks = wordBankService.getAvailableDecks("vi", token);
        boolean found = userDecks.stream().anyMatch(d -> d.getName().equals("Bộ Từ Lập Trình"));
        assertTrue(found);

        // Query pairs
        List<WordPair> pairs = wordBankService.getDeckWordPairs(saved.getId(), token);
        assertEquals(3, pairs.size());
    }

    @Test
    void testDeleteCustomDeck() {
        String token = "test_creator_456";
        CustomDeckDto newDeck = new CustomDeckDto();
        newDeck.setName("Bộ Từ Cần Xóa");
        newDeck.setWordPairs(List.of(
                new WordPairDto("Test", "A", "B"),
                new WordPairDto("Test", "C", "D"),
                new WordPairDto("Test", "E", "F")
        ));

        CustomDeckDto saved = wordBankService.saveCustomDeck(token, newDeck);
        Long rawId = Long.parseLong(saved.getId().replace("custom_", ""));

        boolean deleted = wordBankService.deleteCustomDeck(token, rawId);
        assertTrue(deleted);

        // Verify it is gone
        List<CustomDeckDto> remaining = wordBankService.getAvailableDecks("vi", token);
        assertFalse(remaining.stream().anyMatch(d -> d.getName().equals("Bộ Từ Cần Xóa")));
    }
}
