export type KnowledgeBaseStatus = 'EMPTY' | 'PROCESSING' | 'READY' | 'FAILED'

export interface KnowledgeBaseVO {
  id: number
  user_id: number
  name: string
  embedding_model: string
  embedding_dimension: number
  status: KnowledgeBaseStatus
  created_at: string
  updated_at: string
}

export interface CreateKnowledgeBaseDTO {
  name: string
  embedding_model: string
  embedding_dimension: number
}
