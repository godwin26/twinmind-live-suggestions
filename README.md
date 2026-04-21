# TwinMind Live Suggestions Assignment

Web app for the TwinMind live suggestions assignment. The app listens to microphone audio, transcribes it with Groq Whisper, generates exactly 3 live suggestions from the transcript, and supports a transcript-aware chat panel.

## Deliverables

- Deployed app URL: TODO
- GitHub repository: TODO

## Stack

- Backend: Java 21, Spring Boot, Maven
- Frontend: React + Vite + JavaScript
- AI provider: Groq
- Transcription model: `whisper-large-v3`
- Suggestions/chat model: `openai/gpt-oss-120b`

## Backend

Run the backend:

```bash
cd Backend
mvn spring-boot:run
```

The backend runs on:

```txt
http://127.0.0.1:8081
```

Health check:

```bash
curl http://127.0.0.1:8081/api/health
```

Expected response:

```json
{"status":"ok"}
```

Compile/check backend:

```bash
cd Backend
mvn test
```

## Frontend

Install dependencies:

```bash
cd Frontend
npm install
```

Run the frontend:

```bash
cd Frontend
npm run dev
```

The frontend runs on:

```txt
http://localhost:5173
```

Build/check frontend:

```bash
cd Frontend
npm run build
```

The frontend calls the backend at `http://127.0.0.1:8081` by default. For deployment, set:

```txt
VITE_API_BASE_URL=<deployed_backend_url>
```

## Deployment

This project is split into two deployable apps:

- Backend: Spring Boot API in `Backend/`
- Frontend: Vite static app in `Frontend/`

Recommended simple deployment path:

1. Deploy the backend to Render, Railway, or another Java/Spring host.
2. Deploy the frontend to Vercel or Netlify.
3. Set the frontend environment variable:

```txt
VITE_API_BASE_URL=https://your-backend-url.example.com
```

4. Set the backend environment variable so browser requests from the deployed frontend are allowed:

```txt
APP_CORS_ALLOWED_ORIGINS=https://your-frontend-url.example.com
```

For local development, the backend defaults to port `8081` and allows `http://localhost:5173` plus `http://127.0.0.1:5173`.

Backend deploy commands:

```bash
cd Backend
mvn clean package
java -jar target/live-suggestions-backend-0.0.1-SNAPSHOT.jar
```

The backend also includes a `Dockerfile` for platforms such as Render. The container uses Java 21 and respects the platform-provided `PORT` environment variable through Spring Boot configuration.

Frontend deploy commands:

```bash
cd Frontend
npm install
npm run build
```

Deploy `Frontend/dist` as the static output.

## Local End-to-End Run

Open two terminals.

Terminal 1:

```bash
cd Backend
mvn spring-boot:run
```

Terminal 2:

```bash
cd Frontend
npm run dev
```

Then open:

```txt
http://localhost:5173
```

In the app:

1. Open Settings with the gear button.
2. Paste a Groq API key.
3. Keep `Audio chunk seconds` at `30` for assignment-like behavior, or reduce it briefly for faster local testing.
4. Click `Start mic`.
5. Speak until a chunk is transcribed.
6. Click `Refresh` or wait for automatic suggestion refresh.
7. Click a suggestion to expand it in chat.
8. Ask a direct chat question.
9. Click `Export` to download the session JSON.

## Backend Endpoints

```txt
GET  /api/health
POST /api/transcribe
POST /api/suggestions
POST /api/chat
```

All Groq-powered endpoints expect the user-provided API key in this header:

```txt
Authorization: Bearer <groq_api_key>
```

No API key is hardcoded or committed.

## Prompt Strategy

The backend uses separate default prompts for:

- Live suggestions
- Detailed answer when a suggestion is clicked
- Direct chat questions

The live suggestion prompt requires exactly 3 JSON suggestions and pushes the model toward specific, timely, non-generic suggestions grounded in recent transcript context.

Live suggestions use a small recent transcript window by default so the middle column updates quickly and stays focused on what is being discussed right now. When a suggestion is clicked, the detailed answer endpoint receives the full transcript so the longer answer can use the broader session context. Direct typed chat uses a configurable chat context window to balance relevance and latency.

## Settings

The app includes editable settings for:

- Groq API key
- Live suggestion prompt
- Detailed answer prompt
- Chat prompt
- Live suggestion context window
- Chat/detail answer context window
- Refresh interval
- Audio chunk duration

The Groq API key is stored only in browser localStorage for convenience and is sent to the backend in the `Authorization` header. It is not hardcoded and is not exported in the session JSON.

## Export

The `Export` button downloads a JSON file containing:

- Export timestamp
- Transcript chunks
- Every suggestion batch
- Full chat history
- Non-secret settings summary

The exported JSON does not include the raw Groq API key.

## Tradeoffs

- The app stores session data in React state only. Reloading the page clears transcript, suggestions, and chat, which matches the assignment requirement.
- The browser records audio with `MediaRecorder` and sends chunks to the backend. The default chunk duration is 30 seconds to match the assignment, but it can be lowered in Settings for testing.
- The backend keeps Groq calls centralized in `GroqService` and uses separate prompts for live suggestions, clicked-suggestion details, and direct chat.
- The backend port and allowed CORS origins are environment-configurable for deployment while keeping local defaults.
- Streaming chat responses are not implemented yet; responses return after the backend receives the full Groq completion.

## Assignment Checklist

- [x] Spring Boot backend skeleton
- [x] Health endpoint
- [x] Groq service
- [x] Transcription endpoint
- [x] Live suggestions endpoint
- [x] Chat endpoint
- [x] React frontend
- [x] Microphone recording and chunking
- [x] Settings screen
- [x] Export session
- [x] Three-column assignment layout
- [x] Manual suggestion refresh
- [x] Automatic suggestion refresh while recording
- [x] Click suggestion to open detailed chat answer
- [ ] Deployment URL added above
- [ ] GitHub repository URL added above
- [ ] Final real Groq API key test on deployed app
