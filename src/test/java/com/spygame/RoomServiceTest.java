package com.spygame;

import com.spygame.model.GameRoom;
import com.spygame.model.Player;
import com.spygame.model.WordPair;
import com.spygame.service.RoomService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RoomServiceTest {

    private RoomService roomService;

    @BeforeEach
    void setUp() {
        roomService = new RoomService();
    }

    @Test
    void testCreateRoom() {
        GameRoom room = roomService.createRoom("Chúa Tể Mèo");

        assertNotNull(room);
        assertNotNull(room.getRoomCode());
        assertEquals(5, room.getRoomCode().length());
        assertEquals(1, room.getPlayerCount());

        Player host = room.getHost();
        assertNotNull(host);
        assertEquals("Chúa Tể Mèo", host.getNickname());
        assertTrue(host.isHost());
        assertEquals(1, host.getAvatarIndex());
    }

    @Test
    void testJoinRoomAndAvatarAssignment() {
        GameRoom room = roomService.createRoom("HostCat");
        String code = room.getRoomCode();

        Player p2 = roomService.joinRoom(code, "PlayerTwo");
        assertEquals(2, p2.getAvatarIndex());
        assertFalse(p2.isHost());
        assertEquals(2, room.getPlayerCount());
        assertFalse(room.canStart()); // < 3 players

        Player p3 = roomService.joinRoom(code, "PlayerThree");
        assertEquals(3, p3.getAvatarIndex());
        assertEquals(3, room.getPlayerCount());
        assertTrue(room.canStart()); // >= 3 players
    }

    @Test
    void testLeaveRoomAndHostTransfer() {
        GameRoom room = roomService.createRoom("InitialHost");
        String code = room.getRoomCode();
        String hostId = room.getHostId();

        Player p2 = roomService.joinRoom(code, "MemberCat");

        // Host leaves
        roomService.leaveRoom(code, hostId);

        assertEquals(1, room.getPlayerCount());
        assertEquals(p2.getId(), room.getHostId());
        assertTrue(p2.isHost());
    }

    @Test
    void testWordCountParity() {
        // Valid pairs
        WordPair valid1 = new WordPair("Đồ uống", "Cà phê", "Trà đá");
        assertTrue(valid1.hasWordCountParity());

        WordPair valid2 = new WordPair("Động vật", "Mèo", "Chó");
        assertTrue(valid2.hasWordCountParity());

        // Invalid pairs
        WordPair invalid1 = new WordPair("Đồ uống", "Cà phê sữa đá", "Trà đá");
        assertFalse(invalid1.hasWordCountParity());

        WordPair invalid2 = new WordPair("Đồ uống", "Nước", "Trà chanh sả");
        assertFalse(invalid2.hasWordCountParity());
    }
}
