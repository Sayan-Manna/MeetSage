-- V2: Initial MeetSage schema
-- This migration creates the full base schema for new installations.
-- Existing databases are baselined at V1 and will skip this script.

CREATE EXTENSION IF NOT EXISTS vector;

-- ============================================================
-- Meetings table
-- ============================================================
CREATE TABLE IF NOT EXISTS meetings (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title          VARCHAR(255),
    source_type    VARCHAR(20)  NOT NULL,       -- AUDIO | TRANSCRIPT_FILE | TRANSCRIPT_TEXT
    source_path    VARCHAR(500),                -- path to uploaded file (null for pasted text)
    raw_transcript TEXT,                        -- full transcript text
    status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
                                                -- PENDING | TRANSCRIBING | ANALYSING | DONE | FAILED
    error_message  TEXT,
    created_at     TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ============================================================
-- Meeting analyses table
-- ============================================================
CREATE TABLE IF NOT EXISTS meeting_analyses (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    meeting_id          UUID UNIQUE NOT NULL REFERENCES meetings(id) ON DELETE CASCADE,
    summary             TEXT,
    key_points          JSONB,                  -- ["point 1", "point 2"]
    decisions           JSONB,                  -- ["decision 1"]
    action_items        JSONB,                  -- [{"task":"...","assignee":"...","deadline":"..."}]
    topics              JSONB,                  -- ["topic 1", "topic 2"]
    sentiment_overall   VARCHAR(20),            -- POSITIVE | NEUTRAL | NEGATIVE
    sentiment_scores    JSONB,                  -- {"positive":0.7,"neutral":0.2,"negative":0.1}
    suggested_title     VARCHAR(255),
    raw_gemini_response TEXT,                   -- store raw for debugging bad parses
    created_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ============================================================
-- Indexes
-- ============================================================
CREATE INDEX IF NOT EXISTS idx_meetings_status      ON meetings(status);
CREATE INDEX IF NOT EXISTS idx_meetings_created_at  ON meetings(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_analyses_meeting_id  ON meeting_analyses(meeting_id);

-- Note: Spring AI PgVectorStore auto-creates its own vector_store table.
-- Note: Spring Modulith auto-creates its own event_publication table.
