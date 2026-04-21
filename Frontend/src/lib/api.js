const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://127.0.0.1:8081'

function audioFilename(audioBlob) {
  const type = audioBlob?.type || ''
  if (type.includes('mp4')) return 'meeting-audio.mp4'
  if (type.includes('mpeg')) return 'meeting-audio.mp3'
  if (type.includes('ogg')) return 'meeting-audio.ogg'
  if (type.includes('wav')) return 'meeting-audio.wav'
  return 'meeting-audio.webm'
}

async function parseResponse(response) {
  const contentType = response.headers.get('content-type') || ''
  const body = contentType.includes('application/json')
    ? await response.json()
    : await response.text()

  if (!response.ok) {
    const message = typeof body === 'string'
      ? body
      : body.details || body.error || 'Request failed'
    throw new Error(message)
  }

  return body
}

function authHeaders(apiKey) {
  const key = apiKey?.trim()

  if (!key) {
    throw new Error('Paste your Groq API key in Settings first.')
  }

  if (!key.startsWith('gsk_')) {
    throw new Error('Groq API key should start with gsk_. Paste the full key from Groq, not the masked dashboard preview.')
  }

  if (key.includes('…') || key.includes('...')) {
    throw new Error('Paste the full Groq API key. The masked key preview with dots or ellipsis will not work.')
  }

  if (!/^[\x20-\x7E]+$/.test(key)) {
    throw new Error('Groq API key contains an invalid hidden character. Delete it and paste the raw key again.')
  }

  return {
    Authorization: `Bearer ${key}`,
  }
}

export async function transcribeAudio({ audioBlob, apiKey }) {
  const formData = new FormData()
  formData.append('audio', audioBlob, audioFilename(audioBlob))

  const response = await fetch(`${API_BASE_URL}/api/transcribe`, {
    method: 'POST',
    headers: authHeaders(apiKey),
    body: formData,
  })

  return parseResponse(response)
}

export async function generateSuggestions({
  apiKey,
  transcript,
  previousSuggestions,
  liveSuggestionPrompt,
  contextWindow,
}) {
  const response = await fetch(`${API_BASE_URL}/api/suggestions`, {
    method: 'POST',
    headers: {
      ...authHeaders(apiKey),
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      transcript,
      previousSuggestions,
      liveSuggestionPrompt,
      contextWindow,
    }),
  })

  return parseResponse(response)
}

export async function sendChatMessage({
  apiKey,
  message,
  selectedSuggestion,
  transcript,
  chatHistory,
  chatPrompt,
  detailedAnswerPrompt,
  contextWindow,
}) {
  const response = await fetch(`${API_BASE_URL}/api/chat`, {
    method: 'POST',
    headers: {
      ...authHeaders(apiKey),
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      message,
      selectedSuggestion,
      transcript,
      chatHistory,
      chatPrompt,
      detailedAnswerPrompt,
      contextWindow,
    }),
  })

  return parseResponse(response)
}
