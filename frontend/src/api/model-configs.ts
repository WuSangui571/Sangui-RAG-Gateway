import type { ApiResponse } from '../types/common'
import type { ModelConfigVO, CreateModelConfigDTO, UpdateModelConfigDTO } from '../types/model-config'
import { apiGet, apiPost, apiPut } from './http'

export function listModelConfigs(
  status: string | undefined,
  adminUserId: number,
): Promise<ApiResponse<ModelConfigVO[]>> {
  const params: Record<string, string | number | undefined> = {}
  if (status) params.status = status
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
