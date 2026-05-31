export interface SmokeChatMessage {
  role: 'user' | 'system' | 'assistant'
  content: string
}

export interface SmokeChatCompletionRequest {
  model: string
  messages: SmokeChatMessage[]
  stream: false
}

export interface SmokeChatChoiceMessage {
  role: string
  content: string
}

export interface SmokeChatChoice {
  index: number
  message: SmokeChatChoiceMessage
  finish_reason: string
}

export interface SmokeChatUsage {
  prompt_tokens: number
  completion_tokens: number
  total_tokens: number
}

export interface SmokeChatCompletionResponse {
  id: string
  object: string
  created: number
  model: string
  choices: SmokeChatChoice[]
  usage: SmokeChatUsage
}

export interface SmokeOpenAiError {
  error: {
    message: string
    type: string
    code: string
  }
}
