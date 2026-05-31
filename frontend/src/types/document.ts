export type DocumentStatus = 'UPLOADED' | 'PARSING' | 'PARSED' | 'EMBEDDING' | 'READY' | 'FAILED'

export const TERMINAL_DOCUMENT_STATUSES: ReadonlySet<DocumentStatus> = new Set(['READY', 'FAILED'])

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
}
