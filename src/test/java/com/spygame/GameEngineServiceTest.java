package com.spygame;

import com.spygame.model.GameRoom;
import com.spygame.model.Player;
import com.spygame.model.Role;
import com.spygame.model.RoomStatus;
import com.spygame.model.WordPair;
import com.spygame.service.GameEngineService;
import com.spygame.service.WordBankService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GameEngineServiceTest {

    private GameEngineService gameEngineService;

    @BeforeEach
    void setUp() {
        WordBankService stubWordBank = new WordBankService(null, null, null) {
            @Override
            public WordPair getRandomWordPair(String deckId, String creatorToken) {
                return new WordPair("Ẩm Thực", "Cà phê sữa", "Trà sữa trân");
            }
        };
        gameEngineService = new GameEngineService(stubWordBank);
    }

    private GameRoom createSampleRoom(int count, boolean mrWhite) {
        GameRoom room = new GameRoom("TEST1", "p1");
        room.setMrWhiteEnabled(mrWhite);
        room.setCurrentWordPair(new WordPair("Ẩm Thực", "Cà phê sữa", "Trà sữa trân"));

        for (int i = 1; i <= count; i++) {
            room.addPlayer(new Player("p" + i, "Mèo " + i, i, i == 1));
        }
        return room;
    }

    @Test
    void testStartGameRoleDistribution_3Players() {
        GameRoom room = createSampleRoom(3, false);
        gameEngineService.startGame(room, null);

        assertEquals(RoomStatus.PLAYING, room.getStatus());
        assertEquals(3, room.getPlayersList().size());

        long civCount = room.getPlayersList().stream().filter(p -> p.getRole() == Role.CIVILIAN).count();
        long spyCount = room.getPlayersList().stream().filter(p -> p.getRole() == Role.SPY).count();

        assertEquals(2, civCount);
        assertEquals(1, spyCount);

        for (Player p : room.getPlayersList()) {
            if (p.getRole() == Role.CIVILIAN) {
                assertNotNull(p.getSecretWord());
            } else if (p.getRole() == Role.SPY) {
                assertNotNull(p.getSecretWord());
            }
        }
    }

    @Test
    void testStartGameRoleDistribution_4PlayersWithMrWhite() {
        GameRoom room = createSampleRoom(4, true);
        gameEngineService.startGame(room, null);

        assertEquals(RoomStatus.PLAYING, room.getStatus());

        long civCount = room.getPlayersList().stream().filter(p -> p.getRole() == Role.CIVILIAN).count();
        long spyCount = room.getPlayersList().stream().filter(p -> p.getRole() == Role.SPY).count();
        long mrWhiteCount = room.getPlayersList().stream().filter(p -> p.getRole() == Role.MR_WHITE).count();

        assertEquals(2, civCount);
        assertEquals(1, spyCount);
        assertEquals(1, mrWhiteCount);

        Player mrWhite = room.getPlayersList().stream().filter(p -> p.getRole() == Role.MR_WHITE).findFirst().orElseThrow();
        assertNull(mrWhite.getSecretWord(), "Mr. White should have a blank card (null word)");
    }

    @Test
    void testStartGameWithCustomRoleCounts() {
        GameRoom room = createSampleRoom(5, true);
        // Custom roles: 2 civilians, 2 spies, 1 mrWhite
        gameEngineService.startGame(room, null, 2, 2, 1);

        assertEquals(RoomStatus.PLAYING, room.getStatus());

        long civCount = room.getPlayersList().stream().filter(p -> p.getRole() == Role.CIVILIAN).count();
        long spyCount = room.getPlayersList().stream().filter(p -> p.getRole() == Role.SPY).count();
        long mrWhiteCount = room.getPlayersList().stream().filter(p -> p.getRole() == Role.MR_WHITE).count();

        assertEquals(2, civCount);
        assertEquals(2, spyCount);
        assertEquals(1, mrWhiteCount);
    }

    @Test
    void testStartGameWithInvalidRoleSum_ThrowsException() {
        GameRoom room = createSampleRoom(5, true);
        // Sum is 4, but total players is 5 -> should throw IllegalArgumentException
        assertThrows(IllegalArgumentException.class, () -> {
            gameEngineService.startGame(room, null, 2, 1, 1);
        });
    }

    @Test
    void testMrWhiteNeverSpeaksFirstInRound1() {
        // Run 50 iterations to ensure Mr. White never lands on index 0
        for (int i = 0; i < 50; i++) {
            GameRoom room = createSampleRoom(4, true);
            gameEngineService.startGame(room, null);

            String firstSpeakerId = room.getSpeakingOrder().get(0);
            Player firstSpeaker = room.getPlayer(firstSpeakerId);

            assertNotNull(firstSpeaker);
            assertNotEquals(Role.MR_WHITE, firstSpeaker.getRole(),
                    "Mr. White must never be the first speaker in Round 1!");
        }
    }

    @Test
    void testVotingTieResultsInNoElimination() {
        GameRoom room = createSampleRoom(4, false);
        gameEngineService.startGame(room, null);
        gameEngineService.startVoting(room);

        assertEquals(RoomStatus.VOTING, room.getStatus());

        // 2 votes for p1, 2 votes for p2
        gameEngineService.submitVote(room, "p1", "p2");
        gameEngineService.submitVote(room, "p3", "p2");
        gameEngineService.submitVote(room, "p2", "p1");
        gameEngineService.submitVote(room, "p4", "p1");

        GameEngineService.VotingResult result = gameEngineService.concludeVoting(room);

        assertTrue(result.isTie());
        assertNull(result.getEliminatedPlayer());
        assertEquals(RoomStatus.PLAYING, room.getStatus(), "Should return to PLAYING on tie vote");
        assertEquals(4, room.getAlivePlayers().size(), "All players should still be alive");
    }

    @Test
    void testVotingEliminatesLastSpy_TriggersLastGuess() {
        GameRoom room = createSampleRoom(4, false);
        gameEngineService.startGame(room, null);

        // Find the single spy
        Player spy = room.getPlayersList().stream().filter(p -> p.getRole() == Role.SPY).findFirst().orElseThrow();

        gameEngineService.startVoting(room);

        // Everyone votes for spy
        for (Player p : room.getAlivePlayers()) {
            gameEngineService.submitVote(room, p.getId(), spy.getId());
        }

        GameEngineService.VotingResult result = gameEngineService.concludeVoting(room);

        assertFalse(result.isTie());
        assertEquals(spy.getId(), result.getEliminatedPlayer().getId());
        assertFalse(spy.isAlive());

        assertEquals(RoomStatus.LAST_GUESS, room.getStatus(), "Last spy eliminated must trigger LAST_GUESS");
        assertEquals(spy.getId(), room.getEliminatedPlayerId());
        assertNotNull(room.getLastGuessWordHint());
        assertNotNull(room.getLastGuessEndTime());
    }

    @Test
    void testVotingEliminatesMrWhite_AlwaysTriggersLastGuess() {
        GameRoom room = createSampleRoom(4, true);
        gameEngineService.startGame(room, null);

        Player mrWhite = room.getPlayersList().stream().filter(p -> p.getRole() == Role.MR_WHITE).findFirst().orElseThrow();

        gameEngineService.startVoting(room);

        for (Player p : room.getAlivePlayers()) {
            gameEngineService.submitVote(room, p.getId(), mrWhite.getId());
        }

        GameEngineService.VotingResult result = gameEngineService.concludeVoting(room);

        assertEquals(RoomStatus.LAST_GUESS, room.getStatus(), "Mr White eliminated must trigger LAST_GUESS");
        assertEquals(mrWhite.getId(), room.getEliminatedPlayerId());
    }

    @Test
    void testVotingEliminatesCivilian_4Players_NoLastGuess_GameContinues() {
        GameRoom room = createSampleRoom(4, false);
        gameEngineService.startGame(room, null);

        // Find a civilian
        Player civ = room.getPlayersList().stream().filter(p -> p.getRole() == Role.CIVILIAN).findFirst().orElseThrow();

        gameEngineService.startVoting(room);

        for (Player p : room.getAlivePlayers()) {
            gameEngineService.submitVote(room, p.getId(), civ.getId());
        }

        GameEngineService.VotingResult result = gameEngineService.concludeVoting(room);

        assertFalse(civ.isAlive());
        // 3 players alive (2 civ, 1 spy) >= 3 -> advances to ROUND_SUMMARY
        assertEquals(RoomStatus.ROUND_SUMMARY, room.getStatus(), "Civilian eliminated with 3 alive should advance to ROUND_SUMMARY");
        assertNull(room.getLastGuessEndTime(), "Civilian elimination must NOT trigger last guess");

        // Host advances to next round
        gameEngineService.startNextRound(room);
        assertEquals(RoomStatus.PLAYING, room.getStatus());
        assertEquals(2, room.getRoundNumber());
    }

    @Test
    void testVotingEliminatesCivilian_3Players_Leaves2Players_VillainsWin() {
        GameRoom room = createSampleRoom(3, false);
        gameEngineService.startGame(room, null);

        Player civ = room.getPlayersList().stream().filter(p -> p.getRole() == Role.CIVILIAN).findFirst().orElseThrow();

        gameEngineService.startVoting(room);

        for (Player p : room.getAlivePlayers()) {
            gameEngineService.submitVote(room, p.getId(), civ.getId());
        }

        GameEngineService.VotingResult result = gameEngineService.concludeVoting(room);

        assertFalse(civ.isAlive());
        // 2 players remain (1 civ, 1 spy) -> SPY wins
        assertEquals(RoomStatus.RESULT, room.getStatus());
        assertEquals("SPY", room.getWinnerFaction());
    }

    @Test
    void testVotingEliminatesSpy_WithAnotherSpyAlive_NoLastGuess() {
        // 7 players has 2 spies
        GameRoom room = createSampleRoom(7, false);
        gameEngineService.startGame(room, null);

        List<Player> spies = room.getPlayersList().stream().filter(p -> p.getRole() == Role.SPY).toList();
        assertEquals(2, spies.size());

        Player firstSpy = spies.get(0);

        gameEngineService.startVoting(room);

        for (Player p : room.getAlivePlayers()) {
            gameEngineService.submitVote(room, p.getId(), firstSpy.getId());
        }

        GameEngineService.VotingResult result = gameEngineService.concludeVoting(room);

        assertFalse(firstSpy.isAlive());
        // Other spy still alive and total alive is 6 -> advance to ROUND_SUMMARY, NO last guess
        assertEquals(RoomStatus.ROUND_SUMMARY, room.getStatus(), "Eliminating spy when other spy alive should advance to ROUND_SUMMARY");
        assertNull(room.getLastGuessEndTime());
    }

    @Test
    void testNormalizeVietnamese() {
        assertEquals("ca phe sua", gameEngineService.normalizeVietnamese("Cà phê sữa"));
        assertEquals("banh mi kep thit", gameEngineService.normalizeVietnamese("  BÁNH MÌ  kẹp   thịt  "));
        assertEquals("duong pho ha noi", gameEngineService.normalizeVietnamese("Đường phố Hà Nội"));
        assertEquals("", gameEngineService.normalizeVietnamese(""));
    }

    @Test
    void testGenerateWordHint() {
        String hint = gameEngineService.generateWordHint("Bánh mì kẹp");
        assertTrue(hint.startsWith("B"));
        assertTrue(hint.contains("m"));
        assertTrue(hint.contains("k"));
        assertTrue(hint.contains("_"));
    }

    @Test
    void testCheckLastGuess_Success() {
        GameRoom room = createSampleRoom(4, false);
        gameEngineService.startGame(room, null);

        Player spy = room.getPlayersList().stream().filter(p -> p.getRole() == Role.SPY).findFirst().orElseThrow();
        spy.setAlive(false);
        room.setEliminatedPlayerId(spy.getId());
        room.setStatus(RoomStatus.LAST_GUESS);

        // Guess without diacritics
        boolean success = gameEngineService.checkLastGuess(room, "ca phe sua");

        assertTrue(success);
        assertEquals(RoomStatus.RESULT, room.getStatus());
        assertEquals("SPY", room.getWinnerFaction());
        assertNotNull(room.getSelectedPenalty());
    }

    @Test
    void testCheckLastGuess_Failure_AllVillainsDead_CiviliansWin() {
        GameRoom room = createSampleRoom(4, false);
        gameEngineService.startGame(room, null);

        Player spy = room.getPlayersList().stream().filter(p -> p.getRole() == Role.SPY).findFirst().orElseThrow();
        spy.setAlive(false);
        room.setEliminatedPlayerId(spy.getId());
        room.setStatus(RoomStatus.LAST_GUESS);

        // Wrong guess attempt allows further tries while timer is running
        boolean success = gameEngineService.checkLastGuess(room, "tu nay hoan toan sai");
        assertFalse(success);
        assertEquals(RoomStatus.LAST_GUESS, room.getStatus(), "Should remain in LAST_GUESS for another try");

        // Timeout expires without a correct guess
        gameEngineService.handleLastGuessTimeout(room);
        assertEquals(RoomStatus.RESULT, room.getStatus());
        assertEquals("CIVILIAN", room.getWinnerFaction());
        assertNotNull(room.getSelectedPenalty());
    }

    @Test
    void testCheckAndApplyEndGameCondition_CompositionRules() {
        // Test 1: 1 Civ + 1 Mr White remaining
        GameRoom room = new GameRoom("T1", "p1");
        Player civ = new Player("c1", "Civ", 1, false);
        civ.setRole(Role.CIVILIAN);
        Player white = new Player("w1", "White", 2, false);
        white.setRole(Role.MR_WHITE);
        room.addPlayer(civ);
        room.addPlayer(white);

        gameEngineService.checkAndApplyEndGameCondition(room);
        assertEquals(RoomStatus.RESULT, room.getStatus());
        assertEquals("MR_WHITE", room.getWinnerFaction());

        // Test 2: 1 Spy + 1 Mr White remaining -> both win
        GameRoom room2 = new GameRoom("T2", "p1");
        Player spy = new Player("s1", "Spy", 1, false);
        spy.setRole(Role.SPY);
        Player white2 = new Player("w2", "White", 2, false);
        white2.setRole(Role.MR_WHITE);
        room2.addPlayer(spy);
        room2.addPlayer(white2);

        gameEngineService.checkAndApplyEndGameCondition(room2);
        assertEquals(RoomStatus.RESULT, room2.getStatus());
        assertEquals("SPY_AND_MR_WHITE", room2.getWinnerFaction());

        // Test 3: 2 Civilians remaining -> Civilian wins
        GameRoom room3 = new GameRoom("T3", "p1");
        Player civ1 = new Player("c1", "Civ 1", 1, false);
        civ1.setRole(Role.CIVILIAN);
        Player civ2 = new Player("c2", "Civ 2", 2, false);
        civ2.setRole(Role.CIVILIAN);
        room3.addPlayer(civ1);
        room3.addPlayer(civ2);

        gameEngineService.checkAndApplyEndGameCondition(room3);
        assertEquals(RoomStatus.RESULT, room3.getStatus());
        assertEquals("CIVILIAN", room3.getWinnerFaction());
    }

    @Test
    void testResetGameReturnsToLobby() {
        GameRoom room = createSampleRoom(4, true);
        gameEngineService.startGame(room, null);

        assertEquals(RoomStatus.PLAYING, room.getStatus());

        gameEngineService.resetGame(room);

        assertEquals(RoomStatus.LOBBY, room.getStatus());
        assertEquals(4, room.getPlayersList().size());
        for (Player p : room.getPlayersList()) {
            assertTrue(p.isAlive());
            assertNull(p.getRole());
            assertNull(p.getSecretWord());
            assertFalse(p.isHasVoted());
        }
    }
}
