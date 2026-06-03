import type {
  SmokeChatCompletionRequest,
  SmokeChatCompletionResponse,
  SmokeStreamingChatRequest,
  SmokeStreamingEvidence,
} from '../types/openai'

const V1_BASE = '/v1'

export class SmokeApiError extends Error {
  status: number
  errorCode: string | null
  errorType: string | null

  constructor(status: number, message: string, errorCode: string | null, errorType: string | null) {
    super(message)
    this.name = 'SmokeApiError'
    this.status = status
    this.errorCode = errorCode
    this.errorType = errorType
  }
}

function getOpenAiErrorField(body: unknown, field: 'message' | 'code' | 'type'): string | null {
  if (typeof body !== 'object' || body === null || !('error' in body)) {
    return null
  }
  const error = (body as { error?: unknown }).error
  if (typeof error !== 'object' || error === null || !(field in error)) {
    return null
  }
  const value = (error as Record<typeof field, unknown>)[field]
  return typeof value === 'string' && value.trim().length > 0 ? value : null
}

async function toSmokeApiError(response: Response): Promise<SmokeApiError> {
  let body: unknown = null
  try {
    body = await response.json()
  } catch {
    // Non-JSON gateway/proxy errors still fail visibly with HTTP status evidence.
  }

  return new SmokeApiError(
    response.status,
    getOpenAiErrorField(body, 'message') || `Gateway returned ${response.status}`,
    getOpenAiErrorField(body, 'code'),
    getOpenAiErrorField(body, 'type'),
  )
}

export async function smokeChatCompletions(
  request: SmokeChatCompletionRequest,
  apiKey: string,
): Promise<SmokeChatCompletionResponse> {
  const response = await fetch(`${V1_BASE}/chat/completions`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${apiKey}`,
    },
    body: JSON.stringify(request),
  })

  if (!response.ok) {
    throw await toSmokeApiError(response)
  }

  return response.json() as Promise<SmokeChatCompletionResponse>
}

export async function smokeStreamingChatCompletions(
  request: SmokeStreamingChatRequest,
  apiKey: string,
): Promise<SmokeStreamingEvidence> {
  const response = await fetch(`${V1_BASE}/chat/completions`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${apiKey}`,
    },
    body: JSON.stringify(request),
  })

  if (!response.ok) {
    throw await toSmokeApiError(response)
  }

  const reader = response.body?.getReader()
  if (!reader) {
    throw new SmokeApiError(0, 'Response body is not readable', null, null)
  }

  const decoder = new TextDecoder()
  let dataLineCount = 0
  let chunkCount = 0
  let donePresent = false
  let buffer = ''

  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''

      for (const line of lines) {
        const trimmed = line.trim()
        if (trimmed.startsWith('data:')) {
          dataLineCount++
          const data = trimmed.slice(5).trim()
          if (data === '[DONE]') {
            donePresent = true
          } else {
            chunkCount++
          }
        }
      }
    }

    const remaining = buffer.trim()
    if (remaining.startsWith('data:')) {
      dataLineCount++
      const data = remaining.slice(5).trim()
      if (data === '[DONE]') {
        donePresent = true
      } else {
        chunkCount++
      }
    }
  } finally {
    reader.releaseLock()
  }

  return {
    httpStatus: response.status,
    dataLineCount,
    chunkCount,
    donePresent,
  }
}
