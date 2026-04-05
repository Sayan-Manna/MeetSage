package com.ai.meetsage.analysis.internal;

import lombok.Data;

/**
 * DTO for individual action items within a meeting analysis.
 */
@Data
public class ActionItemDTO {
    private String task;
    private String assignee;   // nullable
    private String deadline;   // nullable
}
