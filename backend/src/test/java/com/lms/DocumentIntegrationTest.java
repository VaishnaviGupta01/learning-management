package com.lms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.lms.repository.DocumentRepository;

/** Phase 11 backend side: upload -> rag ingestion -> Document/DocumentChunk rows, and grounded tutor replies. */
class DocumentIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private FakeRagClient rag;

    @Autowired
    private DocumentRepository documentRepository;

    @AfterEach
    void reset() {
        rag.reset();
    }

    @Test
    void uploadWritesDocumentAndChunksAndGroundsTheTutor() throws Exception {
        String instructor = registerAs("INSTRUCTOR");
        String student = registerAs("STUDENT");
        long courseId = createCourse(instructor, true);
        long topicId = createTopic(instructor, createModule(instructor, courseId, "M"), "Heaps");

        JsonNode doc = upload(instructor, courseId, topicId, "Heap notes", "heaps.md",
                "A heap is a complete binary tree.\n\nInsert is O(log n).\n", 201);
        assertThat(doc.get("title").asText()).isEqualTo("Heap notes");
        assertThat(doc.get("contentType").asText()).isEqualTo("text/markdown");
        assertThat(doc.get("processed").asBoolean()).isTrue();
        assertThat(doc.get("chunkCount").asInt()).isEqualTo(2);
        assertThat(doc.get("topicId").asLong()).isEqualTo(topicId);
        long docId = doc.get("id").asLong();

        JsonNode chunks = get_("/api/documents/" + docId + "/chunks", instructor, 200);
        assertThat(chunks).hasSize(2);
        assertThat(chunks.at("/1/content").asText()).isEqualTo("Insert is O(log n).");
        assertThat(chunks.at("/0/embeddingId").asText()).startsWith(courseId + ":");
        assertThat(get_("/api/courses/" + courseId + "/documents", student, 200)).hasSize(1);
        get_("/api/documents/" + docId + "/chunks", student, 403);

        // the tutor now sends the course id and gets a grounded answer with sources
        JsonNode reply = post_("/api/tutor/chat", student, Map.of("message", "What is a heap?", "courseId", courseId), 200);
        assertThat(rag.lastRequest.context().courseId()).isEqualTo(courseId);
        assertThat(reply.get("grounded").asBoolean()).isTrue();
        assertThat(reply.at("/sources/0/documentId").asLong()).isEqualTo(docId);

        delete_("/api/documents/" + docId, instructor, 204);
        assertThat(documentRepository.findById(docId)).isEmpty();
        assertThat(rag.indexed.get(courseId)).isEmpty();
        assertThat(post_("/api/tutor/chat", student, Map.of("message", "What is a heap?", "courseId", courseId), 200)
                .get("grounded").asBoolean()).isFalse();
    }

    @Test
    void failedIngestionLeavesNoDocumentBehind() throws Exception {
        String instructor = registerAs("INSTRUCTOR");
        long courseId = createCourse(instructor, true);
        long before = documentRepository.count();

        rag.failWith = "No extractable text found (scanned PDFs need OCR before upload)";
        rag.failStatus = 422;
        JsonNode error = upload(instructor, courseId, null, null, "scan.pdf", "%PDF-1.7 ...", 400);
        assertThat(error.get("message").asText()).contains("OCR");

        rag.failStatus = 0; // unreachable
        upload(instructor, courseId, null, null, "notes.txt", "text", 503);
        assertThat(documentRepository.count()).isEqualTo(before);
    }

    @Test
    void uploadRulesAndPermissions() throws Exception {
        String owner = registerAs("INSTRUCTOR");
        String other = registerAs("INSTRUCTOR");
        String student = registerAs("STUDENT");
        long courseId = createCourse(owner, true);
        long otherCourseTopic = createTopic(owner, createModule(owner, createCourse(owner, true), "M"), "T");

        upload(student, courseId, null, null, "a.txt", "x", 403);
        upload(other, courseId, null, null, "a.txt", "x", 403);
        upload(owner, courseId, null, null, "diagram.png", "x", 400);
        upload(owner, courseId, otherCourseTopic, null, "a.txt", "x", 400);
        upload(owner, 999_999L, null, null, "a.txt", "x", 404);
        JsonNode doc = upload(owner, courseId, null, null, "../../etc/passwd.txt", "line", 201);
        assertThat(doc.get("fileName").asText()).isEqualTo("passwd.txt"); // path stripped
    }

    private JsonNode upload(String token, long courseId, Long topicId, String title, String fileName, String content,
                            int status) throws Exception {
        var request = multipart("/api/documents/upload")
                .file(new MockMultipartFile("file", fileName, "application/octet-stream",
                        content.getBytes(StandardCharsets.UTF_8)))
                .param("courseId", String.valueOf(courseId));
        if (topicId != null) {
            request.param("topicId", String.valueOf(topicId));
        }
        if (title != null) {
            request.param("title", title);
        }
        return call(request, token, null, status);
    }
}
