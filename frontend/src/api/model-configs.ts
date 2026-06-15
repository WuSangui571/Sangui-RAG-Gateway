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
  capability?: ModelConfigCapabilityFilter,
): Promise<ApiResponse<ModelConfigVO[]>> {
  const params: Record<string, string | number | undefined> = {}
  if (status) params.status = status
  if (capability) params.capability = capability
  return apiGet<ModelConfigVO[]>('/admin/model-configs', params)
}

export function getModelConfigDetail(id: number): Promise<ApiResponse<ModelConfigVO>> {
  return apiGet<ModelConfigVO>(`/admin/model-configs/${id}`)
}

export function createModelConfig(dto: CreateModelConfigDTO): Promise<ApiResponse<ModelConfigVO>> {
  return apiPost<ModelConfigVO>('/admin/model-configs', dto)
}

export function updateModelConfig(
  id: number,
  dto: UpdateModelConfigDTO,
): Promise<ApiResponse<ModelConfigVO>> {
  return apiPut<ModelConfigVO>(`/admin/model-configs/${id}`, dto)
}

export function disableModelConfig(id: number): Promise<ApiResponse<ModelConfigVO>> {
  return apiPost<ModelConfigVO>(`/admin/model-configs/${id}/disable`)
}

export function enableModelConfig(id: number): Promise<ApiResponse<ModelConfigVO>> {
  return apiPost<ModelConfigVO>(`/admin/model-configs/${id}/enable`)
}

export function checkUnsavedModelConfig(
  request: ModelConfigCheckRequest,
): Promise<ApiResponse<ModelConfigCheckResult>> {
  return apiPost<ModelConfigCheckResult>('/admin/model-configs/check', request)
}

export function checkSavedModelConfig(
  id: number,
  request: ModelConfigCheckRequest,
): Promise<ApiResponse<ModelConfigCheckResult>> {
  return apiPost<ModelConfigCheckResult>(`/admin/model-configs/${id}/check`, request)
}

export function listChatCapableConfigs(): Promise<ApiResponse<ModelConfigVO[]>> {
  return apiGet<ModelConfigVO[]>('/admin/model-configs/chat-capable')
}
