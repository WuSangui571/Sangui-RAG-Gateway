import type { SmokeChatCompletionRequest, SmokeChatCompletionResponse, SmokeOpenAiError } from '../types/openai'

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
    let body: SmokeOpenAiError | null = null
    try {
      body = await response.json() as SmokeOpenAiError
    } catch {
      // ignore parse failure
    }
    throw new SmokeApiError(
      response.status,
      body?.error.message || `Gateway returned ${response.status}`,
      body?.error.code || null,
      body?.error.type || null,
    )
  }

  return response.json() as Promise<SmokeChatCompletionResponse>
}
