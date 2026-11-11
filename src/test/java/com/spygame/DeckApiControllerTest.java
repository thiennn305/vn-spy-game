package com.spygame;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spygame.dto.CustomDeckDto;
import com.spygame.dto.WordPairDto;
import com.spygame.model.GameRoom;
import com.spygame.service.RoomService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class DeckApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RoomService roomService;

    @Test
    void testGetDecksApi() throws Exception {
        mockMvc.perform(get("/api/decks").param("lang", "vi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].name").isNotEmpty());
    }

    @Test
    void testSaveCustomDeckApi() throws Exception {
        CustomDeckDto dto = new CustomDeckDto();
        dto.setName("Bộ Từ Game Thủ");
        dto.setWordPairs(List.of(
                new WordPairDto("Game", "Dota", "Liên Minh"),
                new WordPairDto("Game", "Mario", "Sonic"),
                new WordPairDto("Game", "PUBG", "Free Fire")
        ));

        mockMvc.perform(post("/api/decks")
                        .param("creatorToken", "token_mock_test_1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(startsWith("custom_")))
                .andExpect(jsonPath("$.name").value("Bộ Từ Game Thủ"))
                .andExpect(jsonPath("$.custom").value(true));
    }

    @Test
    void testSelectRoomDeckApi() throws Exception {
        GameRoom room = roomService.createRoom("ChiefCat");
        String code = room.getRoomCode();
        String hostId = room.getHostId();

        mockMvc.perform(post("/api/decks/select-room-deck")
                        .param("roomCode", code)
                        .param("deckId", "vi_pets_animals")
                        .param("deckName", "Thế Giới Động Vật")
                        .param("hostId", hostId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.deckId").value("vi_pets_animals"));
    }
}
