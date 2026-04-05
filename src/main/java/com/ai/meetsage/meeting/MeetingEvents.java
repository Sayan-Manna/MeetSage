package com.ai.meetsage.meeting;

import java.util.UUID;

/**
 * Application events published by the meeting module for cross-module communication.
 * Other modules (analysis, rag) listen to these events without direct coupling.
 */
public final class MeetingEvents {

    private MeetingEvents() {
        // Utility class — no instantiation
    }

    /**
     * Published when an audio file has been uploaded and saved.
     * The analysis module listens to this to start transcription.
     */
    public record AudioUploaded(UUID meetingId, String audioPath) {}

    /**
     * Published when a transcript is available for analysis.
     * This can happen after:
     *   - A user pastes text directly
     *   - A user uploads a transcript file
     *   - Audio transcription completes
     */
    public record TranscriptReady(UUID meetingId, String transcript) {}

    /**
     * Published when analysis is complete for a meeting.
     * The RAG module listens to this to index the transcript.
     */
    public record AnalysisCompleted(UUID meetingId, String transcript) {}
}
