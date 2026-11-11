package com.spygame.repository;

import com.spygame.entity.CustomDeck;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomDeckRepository extends JpaRepository<CustomDeck, Long> {

    List<CustomDeck> findByCreatorTokenOrderByCreatedAtDesc(String creatorToken);

    Optional<CustomDeck> findByIdAndCreatorToken(Long id, String creatorToken);

    List<CustomDeck> findByRecoveryPin(String recoveryPin);
}
