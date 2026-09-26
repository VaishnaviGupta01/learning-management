package com.lms.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.lms.dto.document.DocumentChunkResponse;
import com.lms.dto.document.DocumentResponse;
import com.lms.security.UserPrincipal;
import com.lms.service.DocumentService;

@RestController
@RequestMapping("/api")
public class DocumentController {

    private static final String MANAGERS = "hasAnyRole('INSTRUCTOR','ADMIN')";

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    /** multipart/form-data: file (PDF, .txt or .md), courseId, optional topicId and title. */
    @PostMapping(value = "/documents/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(MANAGERS)
    public DocumentResponse upload(@RequestPart("file") MultipartFile file,
                                   @RequestParam Long courseId,
                                   @RequestParam(required = false) Long topicId,
                                   @RequestParam(required = false) String title,
                                   @AuthenticationPrincipal UserPrincipal user) {
        return documentService.upload(courseId, topicId, title, file, user);
    }

    @GetMapping("/courses/{courseId}/documents")
    public List<DocumentResponse> list(@PathVariable Long courseId, @AuthenticationPrincipal UserPrincipal user) {
        return documentService.list(courseId, user);
    }

    @GetMapping("/documents/{documentId}/chunks")
    @PreAuthorize(MANAGERS)
    public List<DocumentChunkResponse> chunks(@PathVariable Long documentId, @AuthenticationPrincipal UserPrincipal user) {
        return documentService.chunks(documentId, user);
    }

    @DeleteMapping("/documents/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(MANAGERS)
    public void delete(@PathVariable Long documentId, @AuthenticationPrincipal UserPrincipal user) {
        documentService.delete(documentId, user);
    }
}
