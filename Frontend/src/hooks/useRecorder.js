import { useRef, useState } from 'react'

function getSupportedMimeType() {
  const candidates = [
    'audio/webm;codecs=opus',
    'audio/webm',
    'audio/mp4',
    'audio/mpeg',
  ]

  return candidates.find((type) => window.MediaRecorder?.isTypeSupported(type)) || ''
}

export function useRecorder({ chunkSeconds, onChunk, onError }) {
  const [isRecording, setIsRecording] = useState(false)
  const streamRef = useRef(null)
  const recorderRef = useRef(null)

  async function start() {
    if (isRecording) return

    if (!navigator.mediaDevices?.getUserMedia || !window.MediaRecorder) {
      onError?.('This browser does not support microphone recording.')
      return
    }

    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
      const mimeType = getSupportedMimeType()
      const recorder = new MediaRecorder(stream, mimeType ? { mimeType } : undefined)

      recorder.ondataavailable = (event) => {
        if (event.data && event.data.size > 0) {
          onChunk?.(event.data)
        }
      }

      recorder.onerror = () => {
        onError?.('Microphone recording failed.')
      }

      recorder.onstop = () => {
        stream.getTracks().forEach((track) => track.stop())
        streamRef.current = null
        recorderRef.current = null
        setIsRecording(false)
      }

      streamRef.current = stream
      recorderRef.current = recorder
      recorder.start(Math.max(5, chunkSeconds || 30) * 1000)
      setIsRecording(true)
    } catch (error) {
      onError?.(error?.message || 'Could not start microphone recording.')
    }
  }

  function stop() {
    if (recorderRef.current && recorderRef.current.state !== 'inactive') {
      recorderRef.current.stop()
    }

    if (streamRef.current) {
      streamRef.current.getTracks().forEach((track) => track.stop())
    }

    setIsRecording(false)
  }

  function requestChunk() {
    if (recorderRef.current && recorderRef.current.state === 'recording') {
      recorderRef.current.requestData()
      return true
    }

    return false
  }

  return {
    isRecording,
    start,
    stop,
    requestChunk,
  }
}
