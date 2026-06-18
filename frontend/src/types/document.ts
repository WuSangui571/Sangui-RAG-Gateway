export type DocumentStatus = 'UPLOADED' | 'PARSING' | 'PARSED' | 'EMBEDDING' | 'READY' | 'FAILED'

export type DocumentProcessingTaskStatus =
  | 'PENDING'
  | 'PROCESSING'
  | 'SUCCEEDED'
  | 'RETRYABLE'
  | 'FAILED'
  | 'CANCELED'

export const TERMINAL_DOCUMENT_STATUSES: ReadonlySet<DocumentStatus> = new Set(['READY', 'FAILED'])

export const TASK_NON_TERMINAL_STATUSES: ReadonlySet<DocumentProcessingTaskStatus> = new Set([
  'PENDING',
  'PROCESSING',
  'RETRYABLE',
])

export interface DocumentVO {
  id: number
  user_id: number
  knowledge_base_id: number
  original_filename: string
  content_type: string | null
  file_size: number
  status: DocumentStatus
  chunk_count: number
  error_message: string | null
  created_at: string
  updated_at: string
  processing_task_id: number | null
  processing_task_status: DocumentProcessingTaskStatus | null
  processing_attempt_count: number | null
  processing_next_attempt_at: string | null
  processing_started_at: string | null
  processing_finished_at: string | null
}
