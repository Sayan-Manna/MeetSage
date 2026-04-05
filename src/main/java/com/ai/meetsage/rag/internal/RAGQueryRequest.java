package com.ai.meetsage.rag.internal;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request body for RAG query endpoint.
 */
@Data
public class RAGQueryRequest {

    @NotBlank(message = "question must not be empty")
    private String question;
}
