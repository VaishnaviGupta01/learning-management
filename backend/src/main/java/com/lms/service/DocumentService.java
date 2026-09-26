package com.lms.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.lms.dto.document.DocumentChunkResponse;
import com.lms.dto.document.DocumentResponse;
import com.lms.entity.Course;
import com.lms.entity.Document;
import com.lms.entity.DocumentChunk;
import com.lms.entity.Topic;
import com.lms.exception.BadRequestException;
import com.lms.exception.ResourceNotFoundException;
import com.lms.exception.ServiceUnavailableException;
import com.lms.rag.RagClient;
import com.lms.rag.RagClient.IngestResult;
import com.lms.rag.RagServiceException;
import com.lms.repository.CourseRepository;
import com.lms.repository.DocumentRepository;
import com.lms.repository.TopicRepository;
import com.lms.repository.UserRepository;
import com.lms.security.UserPrincipal;

/**
 * Course material upload (Phase 11). The file is stored under {@code app.upload-dir}, a {@link Document} row is
 * created, rag-service extracts/chunks/embeds/indexes it, and the returned chunks are written back as
 * {@link DocumentChunk} rows. If ingestion fails, the row and the file are removed again.
 *
 * <p>Not {@code @Transactional}: ingestion can take a while, so database work runs in short transactions around it.
 */
@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private static final Set<String> ALLOWED_TYPES = Set.of("application/pdf", "text/plain", "text/markdown");

    private final DocumentRepository documentRepository;
    private final CourseRepository courseRepository;
    private final TopicRepository topicRepository;
    private final UserRepository userRepository;
    private final RagClient ragClient;
    private final TransactionTemplate tx;
    private final Path uploadDir;

    public DocumentService(DocumentRepository documentRepository, CourseRepository courseRepository,
                           TopicRepository topicRepository, UserRepository userRepository, RagClient ragClient,
                           PlatformTransactionManager txManager, @Value("${app.upload-dir}") String uploadDir) {
        this.documentRepository = documentRepository;
        this.courseRepository = courseRepository;
        this.topicRepository = topicRepository;
        this.userRepository = userRepository;
        this.ragClient = ragClient;
        this.tx = new TransactionTemplate(txManager);
        this.uploadDir = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    public DocumentResponse upload(Long courseId, Long topicId, String title, MultipartFile file, UserPrincipal user) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File is empty");
        }
        String fileName = safeFileName(file.getOriginalFilename());
        String contentType = contentType(file.getContentType(), fileName);
        String effectiveTitle = title == null || title.isBlank() ? fileName : title.trim();
        byte[] data = bytes(file);

        Path stored = uploadDir.resolve("course_" + courseId).resolve(UUID.randomUUID() + "-" + fileName);
        Long documentId = tx.execute(status -> {
            Course course = courseRepository.findById(courseId)
                    .orElseThrow(() -> new ResourceNotFoundException("Course", courseId));
            CourseAccess.requireManage(course, user);
            Topic topic = null;
            if (topicId != null) {
                topic = topicRepository.findById(topicId).orElseThrow(() -> new ResourceNotFoundException("Topic", topicId));
                if (!topic.getModule().getCourse().getId().equals(courseId)) {
                    throw new BadRequestException("Topic " + topicId + " does not belong to course " + courseId);
                }
            }
            write(stored, data);
            Document d = new Document();
            d.setCourse(course);
            d.setTopic(topic);
            d.setUploadedBy(userRepository.getReferenceById(user.getId()));
            d.setTitle(effectiveTitle);
            d.setFileName(fileName);
            d.setContentType(contentType);
            d.setFileSize((long) data.length);
            d.setStoragePath(uploadDir.relativize(stored).toString().replace('\\', '/'));
            return documentRepository.save(d).getId();
        });

        IngestResult result;
        try {
            result = ragClient.ingest(documentId, courseId, effectiveTitle, fileName, contentType, data);
        } catch (RagServiceException e) {
            log.warn("Ingestion of document {} failed: {}", documentId, e.getMessage());
            tx.executeWithoutResult(status -> documentRepository.deleteById(documentId));
            deleteQuietly(stored);
            if (e.isClientError()) {
                throw new BadRequestException(e.getUserMessage());
            }
            throw new ServiceUnavailableException(e.getUserMessage());
        }

        return tx.execute(status -> {
            Document d = documentRepository.findById(documentId).orElseThrow();
            for (RagClient.IngestedChunk c : result.chunks()) {
                DocumentChunk chunk = new DocumentChunk();
                chunk.setDocument(d);
                chunk.setChunkIndex(c.chunkIndex());
                chunk.setContent(c.content());
                chunk.setTokenCount(c.tokenCount());
                chunk.setPageNumber(c.pageNumber());
                chunk.setEmbeddingId(c.embeddingId());
                d.getChunks().add(chunk);
            }
            d.setChunkCount(result.chunkCount());
            d.setProcessed(true);
            return DocumentResponse.from(d);
        });
    }

    public List<DocumentResponse> list(Long courseId, UserPrincipal user) {
        return tx.execute(status -> {
            Course course = courseRepository.findById(courseId)
                    .orElseThrow(() -> new ResourceNotFoundException("Course", courseId));
            CourseAccess.requireView(course, user);
            return documentRepository.findByCourseId(courseId).stream().map(DocumentResponse::from).toList();
        });
    }

    public List<DocumentChunkResponse> chunks(Long documentId, UserPrincipal user) {
        return tx.execute(status -> {
            Document d = managed(documentId, user);
            return d.getChunks().stream().map(DocumentChunkResponse::from).toList();
        });
    }

    /** Removes the vectors first, so a deleted document can never be retrieved again. */
    public void delete(Long documentId, UserPrincipal user) {
        Document d = tx.execute(status -> managed(documentId, user));
        try {
            ragClient.deleteDocument(d.getCourse().getId(), documentId);
        } catch (RagServiceException e) {
            throw new ServiceUnavailableException(e.getUserMessage());
        }
        tx.executeWithoutResult(status -> documentRepository.deleteById(documentId));
        deleteQuietly(uploadDir.resolve(d.getStoragePath()));
    }

    // ------------------------------------------------------------------ helpers

    private Document managed(Long documentId, UserPrincipal user) {
        Document d = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document", documentId));
        CourseAccess.requireManage(d.getCourse(), user);
        d.getCourse().getId(); // initialise for use after the transaction
        return d;
    }

    static String safeFileName(String original) {
        String name = original == null ? "upload" : Path.of(original.replace('\\', '/')).getFileName().toString();
        name = name.replaceAll("[^A-Za-z0-9._-]", "_");
        return name.isBlank() || name.startsWith(".") ? "upload" + name : name;
    }

    /** Browsers often send octet-stream for .md; trust the extension for the three supported types. */
    static String contentType(String declared, String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (lower.endsWith(".md")) {
            return "text/markdown";
        }
        if (lower.endsWith(".txt")) {
            return "text/plain";
        }
        if (declared != null && ALLOWED_TYPES.contains(declared)) {
            return declared;
        }
        throw new BadRequestException("Unsupported file type; upload a PDF, .txt or .md file");
    }

    private static byte[] bytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void write(Path path, byte[] data) {
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, data);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store upload", e);
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Could not delete {}: {}", path, e.getMessage());
        }
    }
}
