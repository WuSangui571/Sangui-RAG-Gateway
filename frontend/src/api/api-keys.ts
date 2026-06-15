import type { ApiResponse } from '../types/common'
import type { ApiKeyVO, ApiKeyCreateVO, CreateApiKeyDTO } from '../types/api-key'
import { apiGet, apiPost } from './http'

export function listApiKeys(appId: number): Promise<ApiResponse<ApiKeyVO[]>> {
  return apiGet<ApiKeyVO[]>(`/admin/apps/${appId}/api-keys`)
}

export function createApiKey(
  appId: number,
  dto: CreateApiKeyDTO,
): Promise<ApiResponse<ApiKeyCreateVO>> {
  return apiPost<ApiKeyCreateVO>(`/admin/apps/${appId}/api-keys`, dto)
}

export function disableApiKey(id: number): Promise<ApiResponse<ApiKeyVO>> {
  return apiPost<ApiKeyVO>(`/admin/api-keys/${id}/disable`)
}

export function enableApiKey(id: number): Promise<ApiResponse<ApiKeyVO>> {
  return apiPost<ApiKeyVO>(`/admin/api-keys/${id}/enable`)
}

export function revokeApiKey(id: number): Promise<ApiResponse<ApiKeyVO>> {
  return apiPost<ApiKeyVO>(`/admin/api-keys/${id}/revoke`)
}
