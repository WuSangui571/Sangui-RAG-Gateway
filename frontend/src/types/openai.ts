import type { CitationVO } from './request-log'

export interface SmokeChatMessage {
  role: 'user' | 'system' | 'assistant'
  content: string
}

export interface SmokeChatCompletionRequest {
  model: string
  messages: SmokeChatMessage[]
  stream: false
}

export interface SmokeStreamingChatRequest {
  model: string
  messages: SmokeChatMessage[]
  stream: true
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

export interface SmokeStreamingEvidence {
  httpStatus: number
  dataLineCount: number
  chunkCount: number
  donePresent: boolean
}

export interface SmokeOpenAiError {
  error: {
    message: string
    type: string
    code: string
  }
}

export type ChatRole = 'user' | 'system' | 'assistant'

export interface ChatMessage {
  role: ChatRole
  content: string
}

export interface ChatCompletionRequest {
  model: string
  messages: ChatMessage[]
  stream: false
}

export interface ChatChoiceMessage {
  role: string
  content: string
}

export interface ChatChoice {
  index: number
  message: ChatChoiceMessage
  finish_reason: string
}

export interface ChatUsage {
  prompt_tokens: number
  completion_tokens: number
  total_tokens: number
}

export type SanguiCitation = CitationVO

export interface ChatCompletionResponse {
  id: string
  object: string
  created: number
  model: string
  choices: ChatChoice[]
  usage?: ChatUsage | null
  sangui_citations?: SanguiCitation[]
}

export interface OpenAiError {
  error: {
    message: string
    type: string
    code: string
  }
}
