package com.lms.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.lms.entity.DocumentChunk;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {

    List<DocumentChunk> findByDocumentIdOrderByChunkIndexAsc(Long documentId);
    Optional<DocumentChunk> findByEmbeddingId(String embeddingId);
    List<DocumentChunk> findByEmbeddingIdIn(Collection<String> embeddingIds);
    long countByDocumentId(Long documentId);
    void deleteByDocumentId(Long documentId);
}
