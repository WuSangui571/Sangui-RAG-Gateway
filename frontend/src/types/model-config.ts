export type ModelConfigStatus = 'ENABLED' | 'DISABLED'

export interface ModelConfigVO {
  id: number
  user_id: number
  name: string
  provider_name: string
  base_url: string
  api_key_masked: string | null
  chat_model: string
  embedding_model: string | null
  embedding_dimension: number | null
  status: ModelConfigStatus
  created_at: string
  updated_at: string
}

export interface CreateModelConfigDTO {
  name: string
  provider_name: string
  base_url: string
  api_key: string
  chat_model: string
  embedding_model: string | null
  embedding_dimension: number | null
}

export interface UpdateModelConfigDTO {
  name?: string
  provider_name?: string
  base_url?: string
  api_key?: string
  chat_model?: string
  embedding_model?: string | null
  embedding_dimension?: number | null
}
