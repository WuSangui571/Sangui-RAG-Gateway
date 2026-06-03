import type { ApiResponse } from '../types/common'
import type {
  AppVO, CreateAppDTO, AppReadinessVO,
  BindAppDefaultModelConfigDTO, BindAppDefaultModelConfigVO,
  BindAppDefaultKnowledgeBaseDTO, BindAppDefaultKnowledgeBaseVO,
} from '../types/app'
import { apiGet, apiPost, apiPut } from './http'

export function listApps(
  status: string | undefined,
  adminUserId: number,
): Promise<ApiResponse<AppVO[]>> {
  const params: Record<string, string | number | undefined> = {}
  if (status) params.status = status
  return apiGet<AppVO[]>('/admin/apps', params, adminUserId)
}

export function getAppDetail(
  id: number,
  adminUserId: number,
): Promise<ApiResponse<AppVO>> {
  return apiGet<AppVO>(`/admin/apps/${id}`, undefined, adminUserId)
}

export function getAppReadiness(
  appId: number,
  adminUserId: number,
): Promise<ApiResponse<AppReadinessVO>> {
  return apiGet<AppReadinessVO>(`/admin/apps/${appId}/readiness`, undefined, adminUserId)
}

export function createApp(
  dto: CreateAppDTO,
  adminUserId: number,
): Promise<ApiResponse<AppVO>> {
  return apiPost<AppVO>('/admin/apps', dto, adminUserId)
}

export function bindDefaultModelConfig(
  appId: number,
  dto: BindAppDefaultModelConfigDTO,
  adminUserId: number,
): Promise<ApiResponse<BindAppDefaultModelConfigVO>> {
  return apiPut<BindAppDefaultModelConfigVO>(`/admin/apps/${appId}/default-model-config`, dto, adminUserId)
}

export function bindDefaultKnowledgeBase(
  appId: number,
  dto: BindAppDefaultKnowledgeBaseDTO,
  adminUserId: number,
): Promise<ApiResponse<BindAppDefaultKnowledgeBaseVO>> {
  return apiPut<BindAppDefaultKnowledgeBaseVO>(`/admin/apps/${appId}/knowledge-base`, dto, adminUserId)
}

export function disableApp(
  id: number,
  adminUserId: number,
): Promise<ApiResponse<AppVO>> {
  return apiPost<AppVO>(`/admin/apps/${id}/disable`, undefined, adminUserId)
}

export function enableApp(
  id: number,
  adminUserId: number,
): Promise<ApiResponse<AppVO>> {
  return apiPost<AppVO>(`/admin/apps/${id}/enable`, undefined, adminUserId)
}
