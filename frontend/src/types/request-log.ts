export interface RequestLogUsageVO {
  prompt_tokens: number | null
  completion_tokens: number | null
  total_tokens: number | null
}

export interface ApiRequestLogVO {
  id: number
  request_id: string
  app_id: number
  api_key_id: number
  model: string | null
  provider_name: string | null
  status: 'success' | 'failure'
  error_code: string | null
  latency_ms: number | null
  upstream_latency_ms: number | null
  usage: RequestLogUsageVO | null
  messages_count: number | null
  question_summary: string | null
  hit_chunk_ids: number[]
  created_at: string
}

export interface ApiRequestLogDetailVO extends ApiRequestLogVO {
  user_id: number
  updated_at: string
  output_capture_status: OutputCaptureStatus
  completion_length: number | null
  output_preview_available: boolean
  output_preview_truncated: boolean
  output_redacted: boolean
  output_retention_expires_at: string | null
}

export interface ApiRequestLogPageVO<T> {
  items: T[]
  page: number
  page_size: number
  total: number
}

export interface HitChunkSummaryVO {
  chunk_id: number
  document_id: number
  knowledge_base_id: number
  source_filename: string | null
  chunk_index: number
  summary: string | null
}

export interface RequestLogListParams {
  page?: number
  page_size?: number
  status?: 'success' | 'failure' | ''
  error_code?: string
  start_time?: string
  end_time?: string
}

export type OutputCaptureStatus =
  | 'DISABLED'
  | 'CAPTURED'
  | 'EMPTY'
  | 'TRUNCATED_ONLY'
  | 'REDACTED'
  | 'REDACTION_BLOCKED'
  | 'STREAMING_UNSUPPORTED'
  | 'FAILED'
  | 'EXPIRED'

export interface RequestLogOutputPreviewVO {
  request_id: string
  output_capture_status: OutputCaptureStatus
  completion_length: number | null
  output_preview: string | null
  output_preview_truncated: boolean
  output_redacted: boolean
  output_retention_expires_at: string | null
}

export interface RequestLogOutputAccessDTO {
  confirm_access: boolean
  reason?: string
}

export type DiagnosticBoundary =
  | 'auth'
  | 'readiness'
  | 'retrieval'
  | 'embedding'
  | 'upstream'
  | 'streaming'
  | 'request-log'
  | 'unknown'
