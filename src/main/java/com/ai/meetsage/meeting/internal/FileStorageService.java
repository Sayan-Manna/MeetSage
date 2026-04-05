package com.ai.meetsage.meeting.internal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * Handles file storage for uploaded audio and transcript files.
 */
@Service
@Slf4j
public class FileStorageService {

    private final Path uploadDir;

    public FileStorageService(@Value("${meetsage.storage.upload-dir}") String uploadDir) {
        this.uploadDir = Paths.get(uploadDir);
    }

    /**
     * Save an uploaded audio file to disk.
     *
     * @return the absolute path of the saved file
     */
    public String saveAudioFile(MultipartFile file) throws IOException {
        Path audioDir = uploadDir.resolve("audio");
        Files.createDirectories(audioDir);

        String filename = UUID.randomUUID() + "_" + file.getOriginalFilename();
        Path filePath = audioDir.resolve(filename);
        file.transferTo(filePath);

        log.info("Saved audio file: {}", filePath);
        return filePath.toAbsolutePath().toString();
    }

    /**
     * Save an uploaded transcript file to disk.
     *
     * @return the absolute path of the saved file
     */
    public String saveTranscriptFile(MultipartFile file) throws IOException {
        Path transcriptDir = uploadDir.resolve("transcripts");
        Files.createDirectories(transcriptDir);

        String filename = UUID.randomUUID() + "_" + file.getOriginalFilename();
        Path filePath = transcriptDir.resolve(filename);
        file.transferTo(filePath);

        log.info("Saved transcript file: {}", filePath);
        return filePath.toAbsolutePath().toString();
    }
}
