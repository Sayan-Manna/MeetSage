package com.ai.meetsage.analysis.internal;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * DTO matching the structured JSON response from Gemini analysis.
 */
@Data
public class MeetingAnalysisDTO {
    private String summary;
    private List<String> keyPoints;
    private List<String> decisions;
    private List<ActionItemDTO> actionItems;
    private List<String> topics;
    private String sentimentOverall;
    private Map<String, Double> sentimentBreakdown;
    private String suggestedTitle;
}
