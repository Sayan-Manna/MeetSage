package com.ai.meetsage.meeting.internal;

import com.ai.meetsage.analysis.MeetingAnalysis;
import com.ai.meetsage.analysis.MeetingAnalysisRepository;
import com.ai.meetsage.meeting.Meeting;
import com.ai.meetsage.meeting.MeetingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * REST controller for meeting management: upload, status, list, delete.
 */
@RestController
@RequestMapping("/api/meetings")
@Slf4j
@RequiredArgsConstructor
public class MeetingController {

    private final MeetingService meetingService;
    private final MeetingAnalysisRepository analysisRepository;
    private final FileStorageService fileStorageService;

    // ---------------------------------------------------------------
    // Upload endpoints
    // ---------------------------------------------------------------

    /**
     * Upload an audio file (mp3, wav, webm, m4a, ogg).
     */
    @PostMapping("/upload/audio")
    public ResponseEntity<Map<String, Object>> uploadAudio(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title) throws IOException {

        validateAudioFile(file);
        String savedPath = fileStorageService.saveAudioFile(file);
        Meeting meeting = meetingService.createFromAudio(title, savedPath); // Create meeting domain and publish event

        return ResponseEntity.accepted().body(Map.of(
                "meetingId", meeting.getId(),
                "status", "TRANSCRIBING",
                "message", "Audio received. Transcription started."));
    }

    /**
     * Upload a .txt or .md transcript file.
     */
    @PostMapping("/upload/transcript")
    public ResponseEntity<Map<String, Object>> uploadTranscriptFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title) throws IOException {

        String transcript = new String(file.getBytes(), StandardCharsets.UTF_8);
        String savedPath = fileStorageService.saveTranscriptFile(file);
        Meeting meeting = meetingService.createFromTranscriptFile(title, transcript, savedPath);

        return ResponseEntity.accepted().body(Map.of(
                "meetingId", meeting.getId(),
                "status", "ANALYSING",
                "message", "Transcript received. Analysis started."));
    }

    /**
     * Paste transcript text directly in request body.
     */
    @PostMapping("/upload/text")
    public ResponseEntity<Map<String, Object>> uploadText(
            @RequestBody @Valid TextUploadRequest request) {

        Meeting meeting = meetingService.createFromText(request.getTitle(), request.getTranscript());

        return ResponseEntity.accepted().body(Map.of(
                "meetingId", meeting.getId(),
                "status", "ANALYSING",
                "message", "Transcript received. Analysis started."));
    }

    // ---------------------------------------------------------------
    // Query endpoints
    // ---------------------------------------------------------------

    /**
     * Get meeting processing status.
     */
    @GetMapping("/{id}/status")
    public ResponseEntity<Map<String, Object>> getStatus(@PathVariable UUID id) {
        Meeting m = findMeetingOrThrow(id);
        return ResponseEntity.ok(Map.of(
                "meetingId", m.getId(),
                "title", Optional.ofNullable(m.getTitle()).orElse("Untitled"),
                "status", m.getStatus().name(),
                "sourceType", m.getSourceType().name(),
                "createdAt", m.getCreatedAt()));
    }

    /**
     * Get full analysis for a meeting.
     */
    @GetMapping("/{id}/analysis")
    public ResponseEntity<?> getAnalysis(@PathVariable UUID id) {
        Meeting meeting = findMeetingOrThrow(id);

        if (meeting.getStatus() != com.ai.meetsage.meeting.MeetingStatus.DONE) {
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(Map.of(
                            "status", meeting.getStatus().name(),
                            "message", "Analysis not ready yet."));
        }

        MeetingAnalysis analysis = analysisRepository.findByMeetingId(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Analysis not found for meeting: " + id));

        return ResponseEntity.ok(buildAnalysisResponse(meeting, analysis));
    }

    /**
     * Get raw transcript.
     */
    @GetMapping("/{id}/transcript")
    public ResponseEntity<Map<String, Object>> getTranscript(@PathVariable UUID id) {
        Meeting m = findMeetingOrThrow(id);
        return ResponseEntity.ok(Map.of(
                "meetingId", m.getId(),
                "transcript", Optional.ofNullable(m.getRawTranscript()).orElse("")));
    }

    /**
     * List all meetings with pagination.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> listMeetings(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<Meeting> meetings = meetingService.findAll(pageable);

        List<Map<String, Object>> summaries = meetings.getContent().stream()
                .map(this::toSummary)
                .toList();

        return ResponseEntity.ok(Map.of(
                "meetings", summaries,
                "total", meetings.getTotalElements(),
                "page", page,
                "size", size));
    }

    /**
     * Delete a meeting and all associated data.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        meetingService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ---------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------

    private Meeting findMeetingOrThrow(UUID id) {
        return meetingService.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Meeting not found: " + id));
    }

    private void validateAudioFile(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No filename provided");
        }
        String lower = name.toLowerCase();
        if (!lower.endsWith(".mp3") && !lower.endsWith(".wav")
                && !lower.endsWith(".webm") && !lower.endsWith(".m4a")
                && !lower.endsWith(".ogg")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unsupported audio format. Use mp3, wav, webm, m4a, or ogg.");
        }
    }

    private Map<String, Object> toSummary(Meeting m) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("meetingId", m.getId());
        summary.put("title", Optional.ofNullable(m.getTitle()).orElse("Untitled"));
        summary.put("status", m.getStatus().name());
        summary.put("sourceType", m.getSourceType().name());
        summary.put("createdAt", m.getCreatedAt());
        return summary;
    }

    private Map<String, Object> buildAnalysisResponse(Meeting m, MeetingAnalysis a) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("meetingId", m.getId());
        response.put("title", Optional.ofNullable(m.getTitle()).orElse("Untitled"));
        response.put("summary", a.getSummary());
        response.put("keyPoints", a.getKeyPointsAsList());
        response.put("decisions", a.getDecisionsAsList());
        response.put("actionItems", a.getActionItemsAsList());
        response.put("topics", a.getTopicsAsList());
        response.put("sentiment", Map.of(
                "overall", Optional.ofNullable(a.getSentimentOverall()).orElse("unknown"),
                "scores", Optional.ofNullable(a.getSentimentScoresAsMap()).orElse(Map.of())));
        response.put("transcript", Optional.ofNullable(m.getRawTranscript()).orElse(""));
        response.put("createdAt", a.getCreatedAt());
        return response;
    }
}
