
<img width="1440" height="1006" alt="image" src="https://github.com/user-attachments/assets/b4e37f60-0dcc-4d42-9a1b-d4185d0b16fe" />



## What MeetSage Does

You give it a meeting (audio file or text transcript). It uses Google Gemini AI to understand and analyse it, then lets you ask questions about it later.

---

## The Big Picture — 3 Steps

```
1. You upload a meeting
       ↓
2. Gemini analyses it
       ↓
3. You query it later
```

---

## Step-by-Step Flow

### 1️⃣ You Upload a Meeting

You have 3 ways to do this:

| What you send | Endpoint | What happens |
|---|---|---|
| Audio file (.mp3, .wav…) | `POST /upload/audio` | File saved to disk first |
| Transcript file (.txt) | `POST /upload/transcript` | Text extracted from file |
| Paste text directly | `POST /upload/text` | Text taken from request body |

The app **immediately** saves a Meeting record to the database with status `PENDING` and sends you back a `meetingId`. Processing happens **in the background** — you don't wait for it.

---

### 2️⃣ Gemini Processes It (Background)

This is where the 3 modules kick in, **one after the other, triggered by events**:

```
Audio path? → TranscriptionService fires
                   [calls Gemini multimodal API]
                   [Gemini listens to audio, returns text]
                       ↓
                 Transcript ready → AnalysisService fires
                                         [calls Gemini again]
                                         [Gemini returns structured JSON]:
                                           - summary
                                           - key points
                                           - decisions
                                           - action items (who does what by when)
                                           - topics
                                           - sentiment (positive/neutral/negative)
                                           - suggested title
                                              ↓
                                        Saved to database
                                        Status → DONE
                                              ↓
                                        RAGService fires
                                        [transcript split into 1000-char chunks]
                                        [each chunk embedded via text-embedding-004]
                                        [vectors stored in pgvector]
```

If you uploaded text/transcript directly, the first step (transcription) is skipped — it goes straight to analysis.

---

### 3️⃣ You Query It

**Get the analysis:**
```
GET /api/meetings/{id}/analysis
→ Returns: summary, key points, action items, sentiment, etc.
```

**Ask a question in plain English (RAG):**
```
POST /api/rag/query
{ "question": "What did we decide about the database?" }
→ Gemini searches the vector database for relevant chunks
→ Returns an answer + which meeting it came from
```

**Semantic search:**
```
GET /api/rag/search?q=authentication
→ Returns meetings that discussed authentication
```

---

