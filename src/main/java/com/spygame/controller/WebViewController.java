package com.spygame.controller;

import com.spygame.dto.CustomDeckDto;
import com.spygame.model.GameRoom;
import com.spygame.model.Player;
import com.spygame.service.RoomService;
import com.spygame.service.WordBankService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Locale;

/**
 * Controller serving Thymeleaf web views for the Undercover Cat Party Game.
 */
@Controller
public class WebViewController {

    private final RoomService roomService;
    private final WordBankService wordBankService;

    public WebViewController(RoomService roomService, WordBankService wordBankService) {
        this.roomService = roomService;
        this.wordBankService = wordBankService;
    }

    @GetMapping("/")
    public String index(@RequestParam(required = false) String error, Model model) {
        if (error != null) {
            model.addAttribute("errorMessageKey", error);
        }
        return "index";
    }

    @GetMapping("/room/{roomCode}")
    public String room(@PathVariable String roomCode,
                       @RequestParam(required = false) String playerId,
                       @RequestParam(defaultValue = "false") boolean host,
                       @CookieValue(value = "cat_creator_token", required = false) String creatorToken,
                       Locale locale,
                       Model model) {
        String code = roomCode.trim().toUpperCase();
        GameRoom room = roomService.getRoom(code);

        if (room == null) {
            return "redirect:/?error=error.room.notfound";
        }

        Player current = (playerId != null) ? room.getPlayer(playerId) : null;
        boolean isActuallyHost = (current != null && current.isHost()) || host || (room.getHostId().equals(playerId));

        String lang = (locale != null && "en".equalsIgnoreCase(locale.getLanguage())) ? "en" : "vi";
        List<CustomDeckDto> availableDecks = wordBankService.getAvailableDecks(lang, creatorToken);

        model.addAttribute("roomCode", code);
        model.addAttribute("room", room);
        model.addAttribute("players", room.getPlayersList());
        model.addAttribute("isHost", isActuallyHost);
        model.addAttribute("currentPlayer", current);
        model.addAttribute("playerId", playerId != null ? playerId : "");
        model.addAttribute("nickname", current != null ? current.getNickname() : "Hoàng Thượng");
        model.addAttribute("canStart", room.canStart());
        model.addAttribute("secretWord", (current != null && current.getSecretWord() != null) ? current.getSecretWord() : "");
        model.addAttribute("availableDecks", availableDecks);
        model.addAttribute("selectedDeckId", room.getSelectedDeckId() != null ? room.getSelectedDeckId() : (lang.equals("en") ? "en_daily_life" : "vi_street_food"));
        model.addAttribute("alivePlayers", room.getAlivePlayers());
        model.addAttribute("currentSpeaker", room.getCurrentSpeaker());
        model.addAttribute("eliminatedPlayer", room.getEliminatedPlayerId() != null ? room.getPlayer(room.getEliminatedPlayerId()) : null);
        model.addAttribute("status", room.getStatus().name());

        return "room";
    }
}
