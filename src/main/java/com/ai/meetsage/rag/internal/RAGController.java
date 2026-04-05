package com.ai.meetsage.rag.internal;

import com.ai.meetsage.rag.RAGResponseDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for RAG-based question answering and semantic search.
 */
@RestController
@RequestMapping("/api/rag")
@RequiredArgsConstructor
public class RAGController {

    private final RAGService ragService;

    /**
     * Ask a question across all meeting transcripts.
     */
    @PostMapping("/query")
    public ResponseEntity<RAGResponseDTO> query(@RequestBody @Valid RAGQueryRequest request) {
        return ResponseEntity.ok(ragService.query(request.getQuestion()));
    }

    /**
     * Semantic search across all meeting transcripts.
     */
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> search(@RequestParam String q) {
        return ResponseEntity.ok(Map.of("results", ragService.search(q)));
    }
}
