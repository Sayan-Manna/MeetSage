package com.ai.meetsage.analysis.internal;

import com.ai.meetsage.meeting.MeetingEvents;
import com.ai.meetsage.meeting.MeetingService;
import com.ai.meetsage.meeting.MeetingStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;

import java.nio.file.Path;
import java.util.UUID;

/**
 * Handles audio-to-text transcription using Gemini's multimodal capabilities.
 * Listens to AudioUploaded events from the meeting module.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TranscriptionService {

    private final org.springframework.ai.chat.client.ChatClient.Builder chatClientBuilder;
    private final MeetingService meetingService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Triggered when an audio file is uploaded.
     * Sends audio to Gemini for transcription, then publishes TranscriptReady event.
     */
    @Async
    @ApplicationModuleListener
    public void onAudioUploaded(MeetingEvents.AudioUploaded event) {
        UUID meetingId = event.meetingId();
        String audioPath = event.audioPath();

        try {
            meetingService.updateStatus(meetingId, MeetingStatus.TRANSCRIBING);

            Resource audioResource = new FileSystemResource(Path.of(audioPath));
            String mimeType = detectMimeType(audioPath);

            var chatClient = chatClientBuilder.build();

            String transcript = chatClient.prompt()
                    .user(u -> u
                            .text("""
                                    Transcribe the following audio recording accurately.
                                    Return ONLY the spoken words as plain text.
                                    Do not add timestamps, speaker labels, or annotations.
                                    If a word is unclear, write your best guess.
                                    If nothing intelligible was spoken, return an empty string.
                                    """)
                            .media(MimeTypeUtils.parseMimeType(mimeType), audioResource))
                    .call()
                    .content();

            // Save transcript and update status
            meetingService.updateTranscript(meetingId, transcript);
            meetingService.updateStatus(meetingId, MeetingStatus.ANALYSING);

            log.info("Transcription done for meeting {}. Publishing TranscriptReady event.", meetingId);
            eventPublisher.publishEvent(new MeetingEvents.TranscriptReady(meetingId, transcript));

        } catch (Exception e) {
            log.error("Transcription failed for meeting {}", meetingId, e);
            meetingService.markFailed(meetingId, "Transcription failed: " + e.getMessage());
        }
    }

    private String detectMimeType(String path) {
        String lower = path.toLowerCase();
        if (lower.endsWith(".mp3")) return "audio/mpeg";
        if (lower.endsWith(".wav")) return "audio/wav";
        if (lower.endsWith(".webm")) return "audio/webm";
        if (lower.endsWith(".m4a")) return "audio/mp4";
        if (lower.endsWith(".ogg")) return "audio/ogg";
        return "audio/mpeg"; // default fallback
    }
}
