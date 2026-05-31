import type { ApiResponse } from '../types/common'
import type { ApiKeyVO, ApiKeyCreateVO, CreateApiKeyDTO } from '../types/api-key'
import { apiGet, apiPost } from './http'

export function listApiKeys(
  appId: number,
  adminUserId: number,
): Promise<ApiResponse<ApiKeyVO[]>> {
  return apiGet<ApiKeyVO[]>(`/admin/apps/${appId}/api-keys`, undefined, adminUserId)
}

export function createApiKey(
  appId: number,
  dto: CreateApiKeyDTO,
  adminUserId: number,
): Promise<ApiResponse<ApiKeyCreateVO>> {
  return apiPost<ApiKeyCreateVO>(`/admin/apps/${appId}/api-keys`, dto, adminUserId)
}

export function disableApiKey(
  id: number,
  adminUserId: number,
): Promise<ApiResponse<ApiKeyVO>> {
  return apiPost<ApiKeyVO>(`/admin/api-keys/${id}/disable`, undefined, adminUserId)
}

export function revokeApiKey(
  id: number,
  adminUserId: number,
): Promise<ApiResponse<ApiKeyVO>> {
  return apiPost<ApiKeyVO>(`/admin/api-keys/${id}/revoke`, undefined, adminUserId)
}
