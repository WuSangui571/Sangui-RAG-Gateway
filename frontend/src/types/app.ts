export type AppStatus = 'ENABLED' | 'DISABLED'

export type ReadinessStatus = 'READY' | 'MISSING' | 'DISABLED' | 'NOT_READY'

export interface AppVO {
  id: number
  user_id: number
  name: string
  status: AppStatus
  default_model_config_id: number | null
  default_knowledge_base_id: number | null
  retrieval_top_k: number | null
  retrieval_similarity_threshold: number | null
  retrieval_max_context_chunks: number | null
  retrieval_max_context_chars: number | null
  retrieval_max_single_chunk_chars: number | null
  no_hit_policy: 'STRICT_RAG' | string | null
  request_log_output_capture_enabled: boolean
  created_at: string
  updated_at: string
}

export interface AppReadinessCheckVO {
  key: string
  label: string
  status: ReadinessStatus
  message: string
  metadata: Record<string, unknown> | null
}

export interface AppReadinessVO {
  app_id: number
  user_id: number
  overall_status: ReadinessStatus
  checks: AppReadinessCheckVO[]
}

export interface CreateAppDTO {
  name: string
}

export interface BindAppDefaultModelConfigDTO {
  model_config_id: number
}

export interface BindAppDefaultModelConfigVO {
  app_id: number
  user_id: number
  default_model_config_id: number
}

export interface BindAppDefaultKnowledgeBaseDTO {
  knowledge_base_id: number
}

export interface BindAppDefaultKnowledgeBaseVO {
  app_id: number
  user_id: number
  default_knowledge_base_id: number
}

export interface UpdateAppOutputCaptureDTO {
  request_log_output_capture_enabled: boolean
}
