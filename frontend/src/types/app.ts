export type AppStatus = 'ENABLED' | 'DISABLED'

export interface AppVO {
  id: number
  user_id: number
  name: string
  status: AppStatus
  default_model_config_id: number | null
  default_knowledge_base_id: number | null
  created_at: string
  updated_at: string
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
