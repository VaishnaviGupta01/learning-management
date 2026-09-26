package com.lms.repository;

import java.util.List;

import com.lms.entity.Document;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    List<Document> findByCourseId(Long courseId);
    List<Document> findByTopicId(Long topicId);
    List<Document> findByProcessedFalse();
    List<Document> findByUploadedById(Long uploadedById);
}
