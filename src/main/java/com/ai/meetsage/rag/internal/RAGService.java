package com.ai.meetsage.rag.internal;

import com.ai.meetsage.meeting.Meeting;
import com.ai.meetsage.meeting.MeetingEvents;
import com.ai.meetsage.meeting.MeetingService;
import com.ai.meetsage.rag.RAGResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Handles transcript embedding, semantic search, and question answering.
 * Listens to AnalysisCompleted events to index new meeting transcripts.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RAGService {

    private final VectorStore vectorStore;
    private final org.springframework.ai.chat.client.ChatClient.Builder chatClientBuilder;
    private final MeetingService meetingService;

    @Value("${meetsage.analysis.chunk-size:1000}")
    private int chunkSize;

    @Value("${meetsage.analysis.chunk-overlap:150}")
    private int chunkOverlap;

    @Value("${meetsage.analysis.rag-top-k:5}")
    private int topK;

    @Value("${meetsage.analysis.rag-similarity-threshold:0.0}")
    private double similarityThreshold;

    /**
     * Triggered when analysis for a meeting is complete.
     * Splits the transcript into overlapping chunks, embeds each, and stores in
     * pgvector.
     */
    @Async
    @ApplicationModuleListener
    public void onAnalysisCompleted(MeetingEvents.AnalysisCompleted event) {
        UUID meetingId = event.meetingId();
        String transcript = event.transcript();

        try {
            indexMeeting(meetingId, transcript);
        } catch (Exception e) {
            log.error("Failed to index meeting {} for RAG", meetingId, e);
        }
    }

    /**
     * Split transcript into overlapping chunks, embed each, store in pgvector.
     */
    public void indexMeeting(UUID meetingId, String transcript) {
        Optional<Meeting> meetingOpt = meetingService.findById(meetingId);
        if (meetingOpt.isEmpty()) {
            log.warn("Meeting {} not found for RAG indexing", meetingId);
            return;
        }
        Meeting meeting = meetingOpt.get();

        List<String> chunks = splitIntoChunks(transcript, chunkSize, chunkOverlap);

        List<Document> docs = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            Document doc = new Document(
                    chunks.get(i),
                    Map.of(
                            "meetingId", meetingId.toString(),
                            "meetingTitle", Optional.ofNullable(meeting.getTitle()).orElse("Untitled"),
                            "meetingDate", meeting.getCreatedAt().toString(),
                            "chunkIndex", String.valueOf(i)));
            docs.add(doc);
        }

        vectorStore.add(docs);
        log.info("Indexed {} chunks for meeting {}", docs.size(), meetingId);
    }

    /**
     * Answer a question using relevant chunks from all meetings.
     */
    public RAGResponseDTO query(String question) {
        log.info("RAG query start: '{}' | topK={} | threshold={}", question, topK, similarityThreshold);

        List<Document> relevant = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(question)
                        .topK(topK)
                        .similarityThreshold(similarityThreshold)
                        .build());

        // Log actual scores so you can tune the threshold correctly
        if (relevant.isEmpty()) {
            log.warn("RAG: zero results returned for query '{}' with threshold={}", question, similarityThreshold);
            return new RAGResponseDTO(
                    "I couldn't find relevant content in your meeting records.",
                    List.of());
        }
        log.info("RAG: {} chunks matched for query '{}'", relevant.size(), question);

        // 2. Build context string from retrieved chunks
        String context = relevant.stream()
                .map(d -> "[%s — %s]\n%s".formatted(
                        d.getMetadata().get("meetingTitle"),
                        d.getMetadata().get("meetingDate"),
                        d.getText()))
                .collect(Collectors.joining("\n\n---\n\n"));

        // 3. Ask Gemini with context
        var chatClient = chatClientBuilder.build();

        String answer = chatClient.prompt()
                .system("""
                        You are a helpful meeting assistant.
                        Answer questions based ONLY on the meeting transcript excerpts provided.
                        If the answer is not in the provided context, say:
                        "I couldn't find that in your meeting records."
                        Always mention which meeting and date the information came from.
                        Be concise — 2-4 sentences unless more detail is needed.
                        """)
                .user("Context:\n%s\n\nQuestion: %s".formatted(context, question))
                .call()
                .content();

        // 4. Build source list
        List<RAGResponseDTO.Source> sources = relevant.stream()
                .map(d -> new RAGResponseDTO.Source(
                        (String) d.getMetadata().get("meetingId"),
                        (String) d.getMetadata().get("meetingTitle"),
                        (String) d.getMetadata().get("meetingDate"),
                        d.getText().substring(0, Math.min(200, d.getText().length())) + "..."))
                .toList();

        return new RAGResponseDTO(answer, sources);
    }

    /**
     * Semantic search — returns meetings that contain relevant content.
     */
    public List<RAGResponseDTO.Source> search(String query) {
        List<Document> results = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(10)
                        .similarityThreshold(0.0)   // permissive — return all semantic matches
                        .build());

        return results.stream()
                .map(d -> new RAGResponseDTO.Source(
                        (String) d.getMetadata().get("meetingId"),
                        (String) d.getMetadata().get("meetingTitle"),
                        (String) d.getMetadata().get("meetingDate"),
                        d.getText().substring(0, Math.min(200, d.getText().length())) + "..."))
                .distinct()
                .toList();
    }

    /**
     * Split text into overlapping chunks for better RAG recall.
     */
    private List<String> splitIntoChunks(String text, int size, int overlap) {
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + size, text.length());
            chunks.add(text.substring(start, end));
            start += size - overlap;
        }
        return chunks;
    }
}
