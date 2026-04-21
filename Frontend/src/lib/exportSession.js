export function exportSession({ transcript, suggestionBatches, chatMessages, settings }) {
  const exportedAt = new Date().toISOString()
  const safeSettings = {
    liveContextWindow: settings.liveContextWindow,
    chatContextWindow: settings.chatContextWindow,
    refreshIntervalSeconds: settings.refreshIntervalSeconds,
    audioChunkSeconds: settings.audioChunkSeconds,
    hasApiKey: Boolean(settings.apiKey?.trim()),
    customPrompts: {
      liveSuggestionPrompt: Boolean(settings.liveSuggestionPrompt?.trim()),
      detailedAnswerPrompt: Boolean(settings.detailedAnswerPrompt?.trim()),
      chatPrompt: Boolean(settings.chatPrompt?.trim()),
    },
  }

  const payload = {
    exportedAt,
    transcript,
    suggestionBatches,
    chatHistory: chatMessages,
    settings: safeSettings,
  }

  const blob = new Blob([JSON.stringify(payload, null, 2)], {
    type: 'application/json',
  })
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = `twinmind-session-${exportedAt.replace(/[:.]/g, '-')}.json`
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  URL.revokeObjectURL(url)
}
