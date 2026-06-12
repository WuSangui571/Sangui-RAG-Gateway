import type { ApiResponse } from '../types/common'
import type {
  ModelConfigVO,
  CreateModelConfigDTO,
  UpdateModelConfigDTO,
  ModelConfigCheckRequest,
  ModelConfigCheckResult,
  ModelConfigCapability,
  ModelConfigStatus,
} from '../types/model-config'
import { apiGet, apiPost, apiPut } from './http'

type ModelConfigCapabilityFilter = ModelConfigCapability

export function listModelConfigs(
  status: ModelConfigStatus | undefined,
  adminUserId: number,
  capability?: ModelConfigCapabilityFilter,
): Promise<ApiResponse<ModelConfigVO[]>> {
  const params: Record<string, string | number | undefined> = {}
  if (status) params.status = status
  if (capability) params.capability = capability
  return apiGet<ModelConfigVO[]>('/admin/model-configs', params, adminUserId)
}

export function getModelConfigDetail(
  id: number,
  adminUserId: number,
): Promise<ApiResponse<ModelConfigVO>> {
  return apiGet<ModelConfigVO>(`/admin/model-configs/${id}`, undefined, adminUserId)
}

export function createModelConfig(
  dto: CreateModelConfigDTO,
  adminUserId: number,
): Promise<ApiResponse<ModelConfigVO>> {
  return apiPost<ModelConfigVO>('/admin/model-configs', dto, adminUserId)
}

export function updateModelConfig(
  id: number,
  dto: UpdateModelConfigDTO,
  adminUserId: number,
): Promise<ApiResponse<ModelConfigVO>> {
  return apiPut<ModelConfigVO>(`/admin/model-configs/${id}`, dto, adminUserId)
}

export function disableModelConfig(
  id: number,
  adminUserId: number,
): Promise<ApiResponse<ModelConfigVO>> {
  return apiPost<ModelConfigVO>(`/admin/model-configs/${id}/disable`, undefined, adminUserId)
}

export function enableModelConfig(
  id: number,
  adminUserId: number,
): Promise<ApiResponse<ModelConfigVO>> {
  return apiPost<ModelConfigVO>(`/admin/model-configs/${id}/enable`, undefined, adminUserId)
}

export function checkUnsavedModelConfig(
  request: ModelConfigCheckRequest,
  adminUserId: number,
): Promise<ApiResponse<ModelConfigCheckResult>> {
  return apiPost<ModelConfigCheckResult>('/admin/model-configs/check', request, adminUserId)
}

export function checkSavedModelConfig(
  id: number,
  request: ModelConfigCheckRequest,
  adminUserId: number,
): Promise<ApiResponse<ModelConfigCheckResult>> {
  return apiPost<ModelConfigCheckResult>(`/admin/model-configs/${id}/check`, request, adminUserId)
}

export function listChatCapableConfigs(
  adminUserId: number,
): Promise<ApiResponse<ModelConfigVO[]>> {
  return apiGet<ModelConfigVO[]>('/admin/model-configs/chat-capable', undefined, adminUserId)
}
