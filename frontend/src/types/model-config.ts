export type ModelConfigStatus = 'ENABLED' | 'DISABLED'
export type ModelConfigCapability = 'CHAT' | 'EMBEDDING'

export interface ModelConfigVO {
  id: number
  user_id: number
  capability: string
  name: string
  provider_name: string
  base_url: string
  api_key_masked: string | null
  chat_model: string | null
  embedding_model: string | null
  embedding_dimension: number | null
  status: ModelConfigStatus
  created_at: string
  updated_at: string
}

export interface CreateModelConfigDTO {
  capability: ModelConfigCapability
  name: string
  provider_name: string
  base_url: string
  api_key: string
  chat_model?: string | null
  embedding_model?: string | null
  embedding_dimension?: number | null
}

export interface UpdateModelConfigDTO {
  capability?: ModelConfigCapability
  name?: string
  provider_name?: string
  base_url?: string
  api_key?: string
  chat_model?: string | null
  embedding_model?: string | null
  embedding_dimension?: number | null
}

export type CheckStatus = 'SUCCESS' | 'FAILED' | 'PARTIAL'

export interface ChatCheckResult {
  status: CheckStatus
  model: string
  message: string
}

export interface EmbeddingCheckResult {
  status: CheckStatus
  model: string
  actual_dimension: number | null
  configured_dimension: number | null
  message: string
}

export interface ModelConfigCheckRequest {
  capability?: ModelConfigCapability
  provider_name?: string
  base_url?: string
  api_key?: string
  chat_model?: string
  embedding_model?: string
  embedding_dimension?: number
}

export interface ModelConfigCheckResult {
  capability: ModelConfigCapability
  overall_status: CheckStatus
  base_url_checked: boolean
  chat: ChatCheckResult | null
  embedding: EmbeddingCheckResult | null
}
