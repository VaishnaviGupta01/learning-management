package com.lms.repository;

import java.time.Instant;
import java.util.List;

import com.lms.entity.AiInteraction;
import com.lms.entity.enums.AiInteractionType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiInteractionRepository extends JpaRepository<AiInteraction, Long> {

    Page<AiInteraction> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    List<AiInteraction> findByUserIdAndType(Long userId, AiInteractionType type);
    long countByType(AiInteractionType type);
    long countByCreatedAtAfter(Instant after);
}
