package com.ai.meetsage.rag;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for RAG query results.
 * Public API of the rag module — used by controllers and external consumers.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RAGResponseDTO {

    private String answer;
    private List<Source> sources;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Source {
        private String meetingId;
        private String meetingTitle;
        private String meetingDate;
        private String excerpt;
    }
}
