package com.ai.meetsage.meeting;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Public service for the meeting module.
 * Handles meeting creation, retrieval, and lifecycle management.
 * Publishes events for cross-module communication.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MeetingService {

    private final MeetingRepository meetingRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Create a meeting obj from an uploaded audio file.
     * Publishes AudioUploaded event for the analysis module to pick up.
     */
    @Transactional
    public Meeting createFromAudio(String title, String audioPath) {
        Meeting meeting = new Meeting();
        meeting.setTitle(title);
        meeting.setSourceType(SourceType.AUDIO);
        meeting.setSourcePath(audioPath);
        meeting.setStatus(MeetingStatus.PENDING);
        meeting = meetingRepository.save(meeting);

        log.info("Created meeting {} from audio: {}", meeting.getId(), audioPath);
        eventPublisher.publishEvent(new MeetingEvents.AudioUploaded(meeting.getId(), audioPath));

        return meeting;
    }

    /**
     * Create a meeting from an uploaded transcript file.
     * Publishes TranscriptReady event for the analysis module.
     */
    @Transactional
    public Meeting createFromTranscriptFile(String title, String transcript, String sourcePath) {
        Meeting meeting = new Meeting();
        meeting.setTitle(title);
        meeting.setSourceType(SourceType.TRANSCRIPT_FILE);
        meeting.setSourcePath(sourcePath);
        meeting.setRawTranscript(transcript);
        meeting.setStatus(MeetingStatus.ANALYSING);
        meeting = meetingRepository.save(meeting);

        log.info("Created meeting {} from transcript file: {}", meeting.getId(), sourcePath);
        eventPublisher.publishEvent(new MeetingEvents.TranscriptReady(meeting.getId(), transcript));

        return meeting;
    }

    /**
     * Create a meeting from pasted transcript text.
     * Publishes TranscriptReady event for the analysis module.
     */
    @Transactional
    public Meeting createFromText(String title, String transcript) {
        Meeting meeting = new Meeting();
        meeting.setTitle(title);
        meeting.setSourceType(SourceType.TRANSCRIPT_TEXT);
        meeting.setRawTranscript(transcript);
        meeting.setStatus(MeetingStatus.ANALYSING);
        meeting = meetingRepository.save(meeting);

        log.info("Created meeting {} from pasted text", meeting.getId());
        eventPublisher.publishEvent(new MeetingEvents.TranscriptReady(meeting.getId(), transcript));

        return meeting;
    }

    /**
     * Update meeting status (used by analysis module).
     */
    @Transactional
    public void updateStatus(UUID meetingId, MeetingStatus status) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("Meeting not found: " + meetingId));
        meeting.setStatus(status);
        meetingRepository.save(meeting);
    }

    /**
     * Mark meeting as failed with an error message.
     */
    @Transactional
    public void markFailed(UUID meetingId, String errorMessage) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("Meeting not found: " + meetingId));
        meeting.setStatus(MeetingStatus.FAILED);
        meeting.setErrorMessage(errorMessage);
        meetingRepository.save(meeting);
    }

    /**
     * Update the meeting's transcript (used after audio transcription).
     */
    @Transactional
    public void updateTranscript(UUID meetingId, String transcript) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("Meeting not found: " + meetingId));
        meeting.setRawTranscript(transcript);
        meetingRepository.save(meeting);
    }

    /**
     * Update the meeting title if not already set.
     */
    @Transactional
    public void updateTitleIfAbsent(UUID meetingId, String suggestedTitle) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("Meeting not found: " + meetingId));
        if (meeting.getTitle() == null && suggestedTitle != null) {
            meeting.setTitle(suggestedTitle);
            meetingRepository.save(meeting);
        }
    }

    public Optional<Meeting> findById(UUID id) {
        return meetingRepository.findById(id);
    }

    public Page<Meeting> findAll(Pageable pageable) {
        return meetingRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    @Transactional
    public void delete(UUID id) {
        meetingRepository.deleteById(id);
        log.info("Deleted meeting {}", id);
    }
}
