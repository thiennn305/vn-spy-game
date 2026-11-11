package com.spygame.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spygame.dto.CustomDeckDto;
import com.spygame.dto.WordPairDto;
import com.spygame.entity.CustomDeck;
import com.spygame.entity.CustomWordPair;
import com.spygame.model.WordPair;
import com.spygame.repository.CustomDeckRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Hybrid Word Bank Service managing Default System Decks (JSON)
 * and User-created Custom Decks (JPA/H2 Database).
 * Conforms to .antigravity/rules.md (Section 1 & 3).
 */
@Service
public class WordBankService {
    private static final Logger log = LoggerFactory.getLogger(WordBankService.class);

    private final CustomDeckRepository customDeckRepository;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;

    // In-memory cache of default system decks: Map<Language, List<CustomDeckDto>>
    private final Map<String, List<CustomDeckDto>> systemDecks = new ConcurrentHashMap<>();

    public WordBankService(CustomDeckRepository customDeckRepository,
                           ResourceLoader resourceLoader,
                           ObjectMapper objectMapper) {
        this.customDeckRepository = customDeckRepository;
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void loadDefaultWordBanks() {
        loadLanguageFile("vi", "classpath:data/game-data-vi.json");
        loadLanguageFile("en", "classpath:data/game-data-en.json");
    }

    private void loadLanguageFile(String lang, String path) {
        try {
            Resource resource = resourceLoader.getResource(path);
            if (!resource.exists()) {
                log.warn("Default word bank file not found: {}", path);
                return;
            }
            try (InputStream is = resource.getInputStream()) {
                List<Map<String, Object>> list = objectMapper.readValue(is, new TypeReference<>() {});
                List<CustomDeckDto> decks = new ArrayList<>();

                for (Map<String, Object> map : list) {
                    String id = (String) map.get("id");
                    String name = (String) map.get("name");
                    @SuppressWarnings("unchecked")
                    List<Map<String, String>> rawPairs = (List<Map<String, String>>) map.get("pairs");

                    List<WordPairDto> pairs = new ArrayList<>();
                    if (rawPairs != null) {
                        for (Map<String, String> p : rawPairs) {
                            pairs.add(new WordPairDto(p.get("topic"), p.get("civilianWord"), p.get("spyWord")));
                        }
                    }
                    decks.add(new CustomDeckDto(id, name, false, pairs));
                }
                systemDecks.put(lang, decks);
                log.info("Loaded {} default system decks for language '{}'", decks.size(), lang);
            }
        } catch (Exception e) {
            log.error("Failed to load default word bank from {}: {}", path, e.getMessage(), e);
        }
    }

    /**
     * Retrieves all available decks for the given language and creatorToken.
     * System decks + Personal custom decks.
     */
    @Transactional(readOnly = true)
    public List<CustomDeckDto> getAvailableDecks(String lang, String creatorToken) {
        String key = (lang != null && lang.equalsIgnoreCase("en")) ? "en" : "vi";
        List<CustomDeckDto> result = new ArrayList<>(systemDecks.getOrDefault(key, Collections.emptyList()));

        if (creatorToken != null && !creatorToken.trim().isEmpty()) {
            List<CustomDeck> customDecks = customDeckRepository.findByCreatorTokenOrderByCreatedAtDesc(creatorToken.trim());
            for (CustomDeck cd : customDecks) {
                result.add(mapEntityToDto(cd));
            }
        }
        return result;
    }

    /**
     * Retrieves word pairs of a specific deck by ID.
     */
    @Transactional(readOnly = true)
    public List<WordPair> getDeckWordPairs(String deckId, String creatorToken) {
        if (deckId == null || deckId.trim().isEmpty()) {
            return getDefaultWordPairs();
        }

        // 1. Check custom deck from DB
        if (deckId.startsWith("custom_")) {
            try {
                Long id = Long.parseLong(deckId.substring("custom_".length()));
                Optional<CustomDeck> optional = (creatorToken != null)
                        ? customDeckRepository.findByIdAndCreatorToken(id, creatorToken)
                        : customDeckRepository.findById(id);

                if (optional.isPresent()) {
                    return optional.get().getWordPairs().stream()
                            .map(p -> new WordPair(p.getTopic(), p.getCivilianWord(), p.getSpyWord()))
                            .collect(Collectors.toList());
                }
            } catch (NumberFormatException ignored) {}
        }

        // 2. Check system decks
        for (List<CustomDeckDto> list : systemDecks.values()) {
            for (CustomDeckDto d : list) {
                if (d.getId().equalsIgnoreCase(deckId)) {
                    return d.getWordPairs().stream()
                            .map(p -> new WordPair(p.getTopic(), p.getCivilianWord(), p.getSpyWord()))
                            .collect(Collectors.toList());
                }
            }
        }

        return getDefaultWordPairs();
    }

    /**
     * Picks a random WordPair from the specified deck ID.
     */
    @Transactional(readOnly = true)
    public WordPair getRandomWordPair(String deckId, String creatorToken) {
        List<WordPair> pairs = getDeckWordPairs(deckId, creatorToken);
        if (pairs == null || pairs.isEmpty()) {
            return new WordPair("Ẩm Thực", "Cà phê sữa", "Trà đào cam");
        }
        return pairs.get(new java.security.SecureRandom().nextInt(pairs.size()));
    }

    /**
     * Saves or updates a user-owned Custom Deck.
     */
    @Transactional
    public CustomDeckDto saveCustomDeck(String creatorToken, CustomDeckDto dto) {
        if (creatorToken == null || creatorToken.trim().isEmpty()) {
            throw new IllegalArgumentException("creatorToken is required");
        }
        if (dto.getName() == null || dto.getName().trim().length() < 3 || dto.getName().trim().length() > 30) {
            throw new IllegalArgumentException("validation.deckname.length");
        }
        if (dto.getWordPairs() == null || dto.getWordPairs().size() < 3) {
            throw new IllegalArgumentException("validation.wordpair.min");
        }

        CustomDeck deck;
        if (dto.getId() != null && dto.getId().startsWith("custom_")) {
            Long id = Long.parseLong(dto.getId().substring("custom_".length()));
            deck = customDeckRepository.findByIdAndCreatorToken(id, creatorToken)
                    .orElse(new CustomDeck(creatorToken, dto.getName().trim(), dto.getRecoveryPin()));
            deck.setName(dto.getName().trim());
            deck.setRecoveryPin(dto.getRecoveryPin());
            deck.getWordPairs().clear();
        } else {
            deck = new CustomDeck(creatorToken, dto.getName().trim(), dto.getRecoveryPin());
        }

        for (WordPairDto p : dto.getWordPairs()) {
            if (p.getCivilianWord() != null && !p.getCivilianWord().trim().isEmpty() &&
                p.getSpyWord() != null && !p.getSpyWord().trim().isEmpty()) {
                deck.addWordPair(new CustomWordPair(
                        p.getTopic() != null ? p.getTopic().trim() : "Tự do",
                        p.getCivilianWord().trim(),
                        p.getSpyWord().trim()
                ));
            }
        }

        CustomDeck saved = customDeckRepository.save(deck);
        log.info("Saved custom deck '{}' ({}) with {} pairs", saved.getName(), saved.getId(), saved.getWordPairs().size());
        return mapEntityToDto(saved);
    }

    /**
     * Deletes a custom deck owned by creatorToken.
     */
    @Transactional
    public boolean deleteCustomDeck(String creatorToken, Long deckId) {
        Optional<CustomDeck> optional = customDeckRepository.findByIdAndCreatorToken(deckId, creatorToken);
        if (optional.isPresent()) {
            customDeckRepository.delete(optional.get());
            log.info("Deleted custom deck id {}", deckId);
            return true;
        }
        return false;
    }

    /**
     * Recovers custom decks using a recoveryPin and assigns them to the new creatorToken.
     */
    @Transactional
    public int recoverDecks(String newCreatorToken, String recoveryPin) {
        if (recoveryPin == null || recoveryPin.trim().length() < 4) {
            return 0;
        }
        List<CustomDeck> decks = customDeckRepository.findByRecoveryPin(recoveryPin.trim());
        for (CustomDeck d : decks) {
            d.setCreatorToken(newCreatorToken);
        }
        customDeckRepository.saveAll(decks);
        log.info("Recovered {} decks using PIN for new token {}", decks.size(), newCreatorToken);
        return decks.size();
    }

    private CustomDeckDto mapEntityToDto(CustomDeck cd) {
        List<WordPairDto> pairs = cd.getWordPairs().stream()
                .map(p -> new WordPairDto(p.getTopic(), p.getCivilianWord(), p.getSpyWord()))
                .collect(Collectors.toList());
        CustomDeckDto dto = new CustomDeckDto("custom_" + cd.getId(), cd.getName(), true, pairs);
        dto.setRecoveryPin(cd.getRecoveryPin());
        return dto;
    }

    private List<WordPair> getDefaultWordPairs() {
        List<CustomDeckDto> vi = systemDecks.getOrDefault("vi", Collections.emptyList());
        if (!vi.isEmpty()) {
            return vi.get(0).getWordPairs().stream()
                    .map(p -> new WordPair(p.getTopic(), p.getCivilianWord(), p.getSpyWord()))
                    .collect(Collectors.toList());
        }
        return List.of(new WordPair("Ẩm thực", "Cà phê", "Trà đá"));
    }
}
