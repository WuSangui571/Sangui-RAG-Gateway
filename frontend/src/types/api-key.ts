export type ApiKeyStatus = 'ACTIVE' | 'DISABLED' | 'EXPIRED' | 'REVOKED'

export interface ApiKeyVO {
  id: number
  app_id: number
  user_id: number
  name: string
  key_prefix: string
  status: ApiKeyStatus
  expires_at: string | null
  last_used_at: string | null
  revoked_at: string | null
  created_at: string
  updated_at: string
}

export interface ApiKeyCreateVO extends ApiKeyVO {
  key: string
}

export interface CreateApiKeyDTO {
  name: string
  expires_at: string | null
}
