package com.ai.meetsage.meeting.internal;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request body for pasting transcript text directly.
 */
@Data
public class TextUploadRequest {

    @NotBlank(message = "transcript must not be empty")
    private String transcript;

    private String title;   // optional
}
