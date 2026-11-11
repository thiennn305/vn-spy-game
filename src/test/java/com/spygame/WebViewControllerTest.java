package com.spygame;

import com.spygame.model.GameRoom;
import com.spygame.service.RoomService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class WebViewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RoomService roomService;

    @Autowired
    private org.springframework.context.MessageSource messageSource;

    @Test
    void testVietnameseMessageEncoding() {
        String title = messageSource.getMessage("app.title", null, new java.util.Locale("vi"));
        org.junit.jupiter.api.Assertions.assertEquals("🐾 Tòa Án Mèo Đêm", title);

        String welcome = messageSource.getMessage("home.welcome", null, new java.util.Locale("vi"));
        org.junit.jupiter.api.Assertions.assertTrue(welcome.contains("Chào mừng Hoàng thượng đến Tòa Án Mèo Đêm!"));
    }

    @Test
    void testIndexPageLoads() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("cat-audio.js")))
                .andExpect(content().string(containsString("tab-create")))
                .andExpect(content().string(containsString("tab-join")));
    }

    @Test
    void testRoomPageLoadsWhenRoomExists() throws Exception {
        GameRoom room = roomService.createRoom("HostCat");
        String code = room.getRoomCode();

        mockMvc.perform(get("/room/" + code + "?host=true&playerId=" + room.getHostId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(code)))
                .andExpect(content().string(containsString("secret-flip-card")))
                .andExpect(content().string(containsString("CatAudio.setupSmartAudio")));
    }

    @Test
    void testRoomRedirectsWhenNotFound() throws Exception {
        mockMvc.perform(get("/room/NONEX"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/?error=error.room.notfound"));
    }

    @Test
    void testCreateRoomApi() throws Exception {
        mockMvc.perform(post("/api/rooms/create").param("nickname", "MeoThamTu"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomCode").isNotEmpty())
                .andExpect(jsonPath("$.isHost").value(true));
    }
}
