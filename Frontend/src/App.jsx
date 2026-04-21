import { useEffect, useMemo, useRef, useState } from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import './App.css'
import { generateSuggestions, sendChatMessage } from './lib/api'
import { useRecorder } from './hooks/useRecorder'
import { transcribeAudio } from './lib/api'
import { exportSession } from './lib/exportSession'

const initialTranscript = []

const initialSuggestionBatches = []

const initialChatMessages = [
  {
    id: 'chat-1',
    role: 'assistant',
    text: 'Click a suggestion or ask a question to get a detailed answer grounded in the live transcript.',
    createdAt: new Date().toISOString(),
  },
]

function toBackendTranscript(transcript) {
  return transcript.map((chunk) => ({
    id: chunk.id,
    text: chunk.text,
    endedAt: chunk.endedAt || new Date().toISOString(),
  }))
}

function toBackendChatHistory(messages) {
  return messages.map((message) => ({
    role: message.role,
    content: message.text,
    createdAt: message.createdAt || new Date().toISOString(),
  }))
}

const defaultSettings = {
  apiKey: '',
  liveSuggestionPrompt: '',
  detailedAnswerPrompt: '',
  chatPrompt: '',
  liveContextWindow: 8,
  chatContextWindow: 20,
  refreshIntervalSeconds: 30,
  audioChunkSeconds: 30,
}

function loadSettings() {
  try {
    const saved = window.localStorage.getItem('twinmind-settings')
    return saved ? { ...defaultSettings, ...JSON.parse(saved) } : defaultSettings
  } catch {
    return defaultSettings
  }
}

function App() {
  const [transcript, setTranscript] = useState(initialTranscript)
  const [suggestionBatches, setSuggestionBatches] = useState(initialSuggestionBatches)
  const [chatMessages, setChatMessages] = useState(initialChatMessages)
  const [settings, setSettings] = useState(loadSettings)
  const [isSettingsOpen, setIsSettingsOpen] = useState(false)
  const [isTranscribing, setIsTranscribing] = useState(false)
  const [isRefreshingSuggestions, setIsRefreshingSuggestions] = useState(false)
  const [isSendingChat, setIsSendingChat] = useState(false)
  const [error, setError] = useState('')
  const [chatInput, setChatInput] = useState('')
  const transcriptEndRef = useRef(null)
  const chatEndRef = useRef(null)

  async function handleAudioChunk(audioBlob) {
    if (!audioBlob || audioBlob.size < 4096) {
      return
    }

    setError('')
    setIsTranscribing(true)

    try {
      const response = await transcribeAudio({
        audioBlob,
        apiKey: settings.apiKey,
      })

      const text = response.text?.trim()
      if (text) {
        const now = new Date()
        const newChunk = {
          id: `chunk-${crypto.randomUUID()}`,
          time: now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
          text,
          endedAt: response.createdAt || now.toISOString(),
        }
        const nextTranscript = [...transcript, newChunk]

        setTranscript(nextTranscript)
        handleRefreshSuggestions({ silent: true, transcriptOverride: nextTranscript })
      }
    } catch (caughtError) {
      const message = caughtError.message || ''
      if (!message.includes('could not process file') && !message.includes('invalid_request_error')) {
        setError(message)
      }
    } finally {
      setIsTranscribing(false)
    }
  }

  const recorder = useRecorder({
    chunkSeconds: settings.audioChunkSeconds,
    onChunk: handleAudioChunk,
    onError: setError,
  })

  const statusLabel = useMemo(() => {
    if (isTranscribing) return 'Transcribing'
    if (recorder.isRecording) return 'Recording'
    return 'Idle'
  }, [recorder.isRecording, isTranscribing])

  const hasApiKey = settings.apiKey.trim().length > 0

  useEffect(() => {
    window.localStorage.setItem('twinmind-settings', JSON.stringify(settings))
  }, [settings])

  useEffect(() => {
    transcriptEndRef.current?.scrollIntoView({ block: 'end' })
  }, [transcript])

  useEffect(() => {
    chatEndRef.current?.scrollIntoView({ block: 'end' })
  }, [chatMessages])

  useEffect(() => {
    if (!recorder.isRecording || isRefreshingSuggestions || transcript.length === 0) {
      return undefined
    }

    const intervalMs = Math.max(5, settings.refreshIntervalSeconds || 30) * 1000
    const intervalId = window.setInterval(() => {
      handleRefreshSuggestions({ silent: true })
    }, intervalMs)

    return () => window.clearInterval(intervalId)
  }, [
    recorder.isRecording,
    isRefreshingSuggestions,
    settings.refreshIntervalSeconds,
    transcript.length,
    settings.apiKey,
    settings.liveSuggestionPrompt,
    settings.liveContextWindow,
  ])

  function updateSetting(field, value) {
    setSettings((current) => ({
      ...current,
      [field]: value,
    }))
  }

  function toggleRecording() {
    setError('')
    if (recorder.isRecording) {
      recorder.stop()
    } else {
      recorder.start()
    }
  }

  function handleManualRefresh() {
    setError('')

    if (recorder.isRecording) {
      const requested = recorder.requestChunk()
      if (requested) {
        return
      }
    }

    handleRefreshSuggestions()
  }

  async function handleRefreshSuggestions(options = {}) {
    if (isRefreshingSuggestions) return
    if (!options.silent) {
      setError('')
    }
    setIsRefreshingSuggestions(true)

    try {
      const previousSuggestions = suggestionBatches.flatMap((batch) => batch.suggestions)
      const transcriptForRequest = options.transcriptOverride || transcript
      const response = await generateSuggestions({
        apiKey: settings.apiKey,
        transcript: toBackendTranscript(transcriptForRequest),
        previousSuggestions,
        liveSuggestionPrompt: settings.liveSuggestionPrompt,
        contextWindow: settings.liveContextWindow,
      })

      setSuggestionBatches((current) => [
        {
          id: `batch-${crypto.randomUUID()}`,
          time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
          createdAt: new Date().toISOString(),
          suggestions: response.suggestions,
        },
        ...current,
      ])
    } catch (caughtError) {
      if (!options.silent) {
        setError(caughtError.message)
      }
    } finally {
      setIsRefreshingSuggestions(false)
    }
  }

  async function handleSendChat(event) {
    event.preventDefault()
    const trimmedMessage = chatInput.trim()
    if (!trimmedMessage) return

    const userMessage = {
      id: `chat-${crypto.randomUUID()}`,
      role: 'user',
      text: trimmedMessage,
      createdAt: new Date().toISOString(),
    }

    setError('')
    setIsSendingChat(true)
    setChatMessages((current) => [...current, userMessage])
    setChatInput('')

    try {
      const response = await sendChatMessage({
        apiKey: settings.apiKey,
        message: trimmedMessage,
        transcript: toBackendTranscript(transcript),
        chatHistory: toBackendChatHistory([...chatMessages, userMessage]),
        chatPrompt: settings.chatPrompt,
        contextWindow: settings.chatContextWindow,
      })

      setChatMessages((current) => [
        ...current,
        {
          id: `chat-${crypto.randomUUID()}`,
          role: response.message.role,
          text: response.message.content,
          createdAt: response.message.createdAt,
        },
      ])
    } catch (caughtError) {
      setError(caughtError.message)
    } finally {
      setIsSendingChat(false)
    }
  }

  async function handleSuggestionClick(suggestion) {
    setError('')
    setIsSendingChat(true)

    const userMessage = {
      id: `chat-${crypto.randomUUID()}`,
      role: 'user',
      text: `Clicked suggestion: ${suggestion.title}\n\n${suggestion.preview}`,
      createdAt: new Date().toISOString(),
    }

    setChatMessages((current) => [...current, userMessage])

    try {
      const response = await sendChatMessage({
        apiKey: settings.apiKey,
        message: suggestion.preview,
        selectedSuggestion: suggestion,
        transcript: toBackendTranscript(transcript),
        chatHistory: toBackendChatHistory([...chatMessages, userMessage]),
        detailedAnswerPrompt: settings.detailedAnswerPrompt,
        contextWindow: settings.chatContextWindow,
      })

      setChatMessages((current) => [
        ...current,
        {
          id: `chat-${crypto.randomUUID()}`,
          role: response.message.role,
          text: response.message.content,
          createdAt: response.message.createdAt,
        },
      ])
    } catch (caughtError) {
      setError(caughtError.message)
    } finally {
      setIsSendingChat(false)
    }
  }

  function handleExport() {
    exportSession({
      transcript,
      suggestionBatches,
      chatMessages,
      settings,
    })
  }

  return (
    <main className="app-shell">
      <header className="topbar">
        <div>
          <p className="eyebrow">TwinMind assignment</p>
          <h1>Live Suggestions</h1>
        </div>
        <div className="topbar-actions">
          {!hasApiKey && <span className="key-warning">API key needed</span>}
          <button
            className="icon-button"
            type="button"
            aria-label="Open settings"
            title="Settings"
            onClick={() => setIsSettingsOpen(true)}
          >
            ⚙
          </button>
          <button className="secondary-button" type="button" onClick={handleExport}>
            Export
          </button>
        </div>
      </header>

      <section className="workspace" aria-label="Live meeting workspace">
        {error && (
          <div className="error-banner" role="alert">
            <span>{error}</span>
            <button type="button" onClick={() => setError('')} aria-label="Dismiss error">
              ×
            </button>
          </div>
        )}

        <aside className="panel transcript-panel">
          <div className="panel-header">
            <div>
              <p className="eyebrow">Left column</p>
              <h2>Transcript</h2>
            </div>
            <span className={`status-pill ${recorder.isRecording ? 'active' : ''}`}>{statusLabel}</span>
          </div>

          <button className="primary-button mic-button" type="button" onClick={toggleRecording}>
            {recorder.isRecording ? 'Stop mic' : 'Start mic'}
          </button>

          <div className="scroll-area transcript-list">
            {transcript.length === 0 ? (
              <p className="empty-state">Transcript chunks will appear here while recording.</p>
            ) : (
              transcript.map((chunk) => (
                <article className="transcript-item" key={chunk.id}>
                  <time>{chunk.time}</time>
                  <p>{chunk.text}</p>
                </article>
              ))
            )}
            <div ref={transcriptEndRef} />
          </div>
        </aside>

        <section className="panel suggestions-panel">
          <div className="panel-header">
            <div>
              <p className="eyebrow">Middle column</p>
              <h2>Live suggestions</h2>
            </div>
            <button
              className="secondary-button"
              type="button"
              onClick={handleManualRefresh}
              disabled={isRefreshingSuggestions || isTranscribing || (!recorder.isRecording && transcript.length === 0)}
            >
              {isTranscribing ? 'Updating transcript' : isRefreshingSuggestions ? 'Refreshing' : 'Refresh'}
            </button>
          </div>

          <div className="scroll-area suggestion-batches">
            {suggestionBatches.length === 0 ? (
              <p className="empty-state">Three live suggestions will appear here after transcript context is available.</p>
            ) : (
              suggestionBatches.map((batch) => (
                <section className="suggestion-batch" key={batch.id}>
                  <div className="batch-meta">
                    <span>3 suggestions</span>
                    <time>{batch.time}</time>
                  </div>

                  {batch.suggestions.map((suggestion) => (
                    <button
                      className="suggestion-card"
                      type="button"
                      key={suggestion.id}
                      onClick={() => handleSuggestionClick(suggestion)}
                      disabled={isSendingChat}
                    >
                      <span className="suggestion-type">{suggestion.type}</span>
                      <strong>{suggestion.title}</strong>
                      <span>{suggestion.preview}</span>
                    </button>
                  ))}
                </section>
              ))
            )}
          </div>
        </section>

        <aside className="panel chat-panel">
          <div className="panel-header">
            <div>
              <p className="eyebrow">Right column</p>
              <h2>Chat</h2>
            </div>
          </div>

          <div className="scroll-area chat-history">
            {chatMessages.map((message, index) => (
              <article className={`chat-message ${message.role}`} key={`${message.role}-${index}`}>
                <span>{message.role}</span>
                <div className="markdown-content">
                  <ReactMarkdown remarkPlugins={[remarkGfm]}>{message.text}</ReactMarkdown>
                </div>
              </article>
            ))}
            {isSendingChat && (
              <article className="chat-message assistant">
                <span>assistant</span>
                <div className="markdown-content">
                  <p>Thinking...</p>
                </div>
              </article>
            )}
            <div ref={chatEndRef} />
          </div>

          <form className="chat-input" onSubmit={handleSendChat}>
            <input
              type="text"
              placeholder="Ask about this meeting..."
              value={chatInput}
              onChange={(event) => setChatInput(event.target.value)}
            />
            <button className="primary-button" type="submit" disabled={isSendingChat || !chatInput.trim()}>
              {isSendingChat ? 'Sending' : 'Send'}
            </button>
          </form>
        </aside>
      </section>

      {isSettingsOpen && (
        <div className="modal-backdrop" role="presentation">
          <section className="settings-modal" role="dialog" aria-modal="true" aria-labelledby="settings-title">
            <div className="modal-header">
              <div>
                <p className="eyebrow">Session settings</p>
                <h2 id="settings-title">Settings</h2>
              </div>
              <button
                className="icon-button"
                type="button"
                aria-label="Close settings"
                title="Close"
                onClick={() => setIsSettingsOpen(false)}
              >
                ×
              </button>
            </div>

            <div className="settings-grid">
              <label className="field full-width">
                <span>Groq API key</span>
                <input
                  type="password"
                  value={settings.apiKey}
                  placeholder="gsk_..."
                  onChange={(event) => updateSetting('apiKey', event.target.value)}
                />
              </label>

              <label className="field">
                <span>Live context window</span>
                <input
                  type="number"
                  min="1"
                  value={settings.liveContextWindow}
                  onChange={(event) => updateSetting('liveContextWindow', Number(event.target.value))}
                />
              </label>

              <label className="field">
                <span>Chat context window</span>
                <input
                  type="number"
                  min="1"
                  value={settings.chatContextWindow}
                  onChange={(event) => updateSetting('chatContextWindow', Number(event.target.value))}
                />
              </label>

              <label className="field">
                <span>Refresh interval seconds</span>
                <input
                  type="number"
                  min="5"
                  value={settings.refreshIntervalSeconds}
                  onChange={(event) => updateSetting('refreshIntervalSeconds', Number(event.target.value))}
                />
              </label>

              <label className="field">
                <span>Audio chunk seconds</span>
                <input
                  type="number"
                  min="5"
                  value={settings.audioChunkSeconds}
                  onChange={(event) => updateSetting('audioChunkSeconds', Number(event.target.value))}
                />
              </label>

              <label className="field full-width">
                <span>Live suggestion prompt</span>
                <textarea
                  value={settings.liveSuggestionPrompt}
                  placeholder="Leave blank to use the backend default prompt."
                  onChange={(event) => updateSetting('liveSuggestionPrompt', event.target.value)}
                />
              </label>

              <label className="field full-width">
                <span>Detailed answer prompt</span>
                <textarea
                  value={settings.detailedAnswerPrompt}
                  placeholder="Leave blank to use the backend default prompt."
                  onChange={(event) => updateSetting('detailedAnswerPrompt', event.target.value)}
                />
              </label>

              <label className="field full-width">
                <span>Chat prompt</span>
                <textarea
                  value={settings.chatPrompt}
                  placeholder="Leave blank to use the backend default prompt."
                  onChange={(event) => updateSetting('chatPrompt', event.target.value)}
                />
              </label>
            </div>

            <div className="modal-footer">
              <button className="secondary-button" type="button" onClick={() => setSettings(defaultSettings)}>
                Reset defaults
              </button>
              <button className="primary-button" type="button" onClick={() => setIsSettingsOpen(false)}>
                Done
              </button>
            </div>
          </section>
        </div>
      )}
    </main>
  )
}

export default App
