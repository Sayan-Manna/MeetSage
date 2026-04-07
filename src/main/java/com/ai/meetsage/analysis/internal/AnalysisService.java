package com.ai.meetsage.analysis.internal;

import com.ai.meetsage.analysis.MeetingAnalysis;
import com.ai.meetsage.analysis.MeetingAnalysisRepository;
import com.ai.meetsage.meeting.MeetingEvents;
import com.ai.meetsage.meeting.MeetingService;
import com.ai.meetsage.meeting.MeetingStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Analyses meeting transcripts using Gemini and persists structured results.
 * Listens to TranscriptReady events from the meeting module.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AnalysisService {

    private final org.springframework.ai.chat.client.ChatClient.Builder chatClientBuilder;
    private final MeetingService meetingService;
    private final MeetingAnalysisRepository analysisRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @Value("${meetsage.analysis.chunk-size:1000}")
    private int chunkSize;

    @Value("${meetsage.analysis.chunk-overlap:150}")
    private int chunkOverlap;

    /**
     * Triggered when a transcript is ready for analysis.
     * Calls Gemini for structured analysis → persists result → publishes AnalysisCompleted.
     */
    @Async
    @ApplicationModuleListener
    public void onTranscriptReady(MeetingEvents.TranscriptReady event) {
        UUID meetingId = event.meetingId();
        String transcript = event.transcript();

        try {
            meetingService.updateStatus(meetingId, MeetingStatus.ANALYSING);

            // For very long transcripts, summarise in windows first
            String effectiveTranscript = transcript.length() > 50000
                    ? summariseLongTranscript(transcript)
                    : transcript;

            // Call Gemini for structured analysis
            String rawJson = callGeminiAnalysis(effectiveTranscript);

            // Parse the response
            MeetingAnalysisDTO dto = parseGeminiResponse(rawJson);

            // Persist analysis
            MeetingAnalysis analysis = new MeetingAnalysis();
            analysis.setMeetingId(meetingId);
            analysis.setSummary(dto.getSummary());
            analysis.setKeyPoints(toJson(dto.getKeyPoints()));
            analysis.setDecisions(toJson(dto.getDecisions()));
            analysis.setActionItems(toJson(dto.getActionItems()));
            analysis.setTopics(toJson(dto.getTopics()));
            analysis.setSentimentOverall(dto.getSentimentOverall());
            analysis.setSentimentScores(toJson(dto.getSentimentBreakdown()));
            analysis.setSuggestedTitle(dto.getSuggestedTitle());
            analysis.setRawGeminiResponse(rawJson);
            analysisRepository.save(analysis);

            // Update meeting title if not already set
            meetingService.updateTitleIfAbsent(meetingId, dto.getSuggestedTitle());

            // Mark as done
            meetingService.updateStatus(meetingId, MeetingStatus.DONE);

            // Publish event for RAG indexing
            eventPublisher.publishEvent(new MeetingEvents.AnalysisCompleted(meetingId, transcript));

            log.info("Analysis complete for meeting {}", meetingId);

        } catch (Exception e) {
            log.error("Analysis failed for meeting {}", meetingId, e);
            meetingService.markFailed(meetingId, "Analysis failed: " + e.getMessage());
        }
    }

    private String callGeminiAnalysis(String transcript) {
        var chatClient = chatClientBuilder.build();

        return chatClient.prompt()
                .user("""
                        You are an expert meeting analyst. Analyse the transcript below and return
                        a valid JSON object. Return ONLY raw JSON — no markdown fences, no extra text.

                        Use this exact structure:
                        {
                          "summary": "3-5 sentence plain English overview of the meeting",
                          "keyPoints": ["key point 1", "key point 2", "key point 3"],
                          "decisions": ["decision 1", "decision 2"],
                          "actionItems": [
                            {
                              "task": "what needs to be done",
                              "assignee": "person's name or null",
                              "deadline": "natural language deadline or null"
                            }
                          ],
                          "topics": ["topic1", "topic2", "topic3"],
                          "sentimentOverall": "positive",
                          "sentimentBreakdown": {
                            "positive": 0.65,
                            "neutral": 0.30,
                            "negative": 0.05
                          },
                          "suggestedTitle": "Short Meeting Title (max 7 words)"
                        }

                        Rules:
                        - sentimentOverall must be exactly one of: positive, neutral, negative
                        - sentimentBreakdown values must sum to 1.0
                        - If something has no value (no decisions, no action items), use empty array []
                        - assignee and deadline inside actionItems should be null if not explicitly mentioned

                        TRANSCRIPT:
                        """ + transcript)
                .call()
                .content();
    }

    private MeetingAnalysisDTO parseGeminiResponse(String raw) throws Exception {
        // Gemini sometimes wraps in ```json ... ``` even when told not to. Strip it.
        String cleaned = raw
                .replaceAll("(?s)```json\\s*", "")
                .replaceAll("```", "")
                .trim();
        return objectMapper.readValue(cleaned, MeetingAnalysisDTO.class);
    }

    /**
     * For transcripts > 50k chars: summarise in 10k-char windows first,
     * then analyse the combined window summaries.
     */
    private String summariseLongTranscript(String transcript) {
        var chatClient = chatClientBuilder.build();

        int windowSize = 10000;
        List<String> windows = new ArrayList<>();
        for (int i = 0; i < transcript.length(); i += windowSize) {
            windows.add(transcript.substring(i, Math.min(i + windowSize, transcript.length())));
        }

        List<String> windowSummaries = windows.stream()
                .map(window -> chatClient.prompt()
                        .user("Summarise the key points from this meeting excerpt in 3-5 sentences:\n\n" + window)
                        .call()
                        .content())
                .toList();

        String combined = String.join("\n\n", windowSummaries);

        return chatClient.prompt()
                .user("""
                        The following is a combination of summaries from meeting transcript windows. Analyse this combined summary as if it were the full transcript, and return a concise summary of the overall meeting:
                        
                        """ + combined)
                .call()
                .content();


    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "[]";
        }
    }
}
