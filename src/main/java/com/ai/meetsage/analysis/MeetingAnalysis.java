package com.ai.meetsage.analysis;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * JPA entity storing the structured analysis results from Gemini.
 * JSONB columns are stored as Strings with helper deserialization methods.
 */
@Entity
@Table(name = "meeting_analyses")
@Data
@NoArgsConstructor
public class MeetingAnalysis {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "meeting_id", unique = true, nullable = false)
    private UUID meetingId;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "key_points", columnDefinition = "JSONB")
    @JdbcTypeCode(SqlTypes.JSON)
    private String keyPoints;

    @Column(columnDefinition = "JSONB")
    @JdbcTypeCode(SqlTypes.JSON)
    private String decisions;

    @Column(name = "action_items", columnDefinition = "JSONB")
    @JdbcTypeCode(SqlTypes.JSON)
    private String actionItems;

    @Column(columnDefinition = "JSONB")
    @JdbcTypeCode(SqlTypes.JSON)
    private String topics;

    @Column(name = "sentiment_overall")
    private String sentimentOverall;

    @Column(name = "sentiment_scores", columnDefinition = "JSONB")
    @JdbcTypeCode(SqlTypes.JSON)
    private String sentimentScores;

    @Column(name = "suggested_title")
    private String suggestedTitle;

    @Lob
    @Column(name = "raw_gemini_response")
    private String rawGeminiResponse;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    // ---------------------------------------------------------------
    // Helper methods to deserialize JSONB fields
    // ---------------------------------------------------------------

    @JsonIgnore
    public List<String> getKeyPointsAsList() {
        return fromJson(keyPoints, new TypeReference<>() {});
    }

    @JsonIgnore
    public List<String> getDecisionsAsList() {
        return fromJson(decisions, new TypeReference<>() {});
    }

    @JsonIgnore
    public List<String> getTopicsAsList() {
        return fromJson(topics, new TypeReference<>() {});
    }

    @JsonIgnore
    public List<Map<String, String>> getActionItemsAsList() {
        return fromJson(actionItems, new TypeReference<>() {});
    }

    @JsonIgnore
    public Map<String, Double> getSentimentScoresAsMap() {
        return fromJson(sentimentScores, new TypeReference<>() {});
    }

    private <T> T fromJson(String json, TypeReference<T> ref) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, ref);
        } catch (Exception e) {
            return null;
        }
    }
}
