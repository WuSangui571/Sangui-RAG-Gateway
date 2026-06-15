import type { ApiResponse } from '../types/common'
import type {
  AppVO, CreateAppDTO, AppReadinessVO,
  BindAppDefaultModelConfigDTO, BindAppDefaultModelConfigVO,
  BindAppDefaultKnowledgeBaseDTO, BindAppDefaultKnowledgeBaseVO,
  UpdateAppOutputCaptureDTO,
} from '../types/app'
import { apiGet, apiPost, apiPut } from './http'

export function listApps(
  status: string | undefined,
): Promise<ApiResponse<AppVO[]>> {
  const params: Record<string, string | number | undefined> = {}
  if (status) params.status = status
  return apiGet<AppVO[]>('/admin/apps', params)
}

export function getAppDetail(id: number): Promise<ApiResponse<AppVO>> {
  return apiGet<AppVO>(`/admin/apps/${id}`)
}

export function getAppReadiness(appId: number): Promise<ApiResponse<AppReadinessVO>> {
  return apiGet<AppReadinessVO>(`/admin/apps/${appId}/readiness`)
}

export function createApp(dto: CreateAppDTO): Promise<ApiResponse<AppVO>> {
  return apiPost<AppVO>('/admin/apps', dto)
}

export function bindDefaultModelConfig(
  appId: number,
  dto: BindAppDefaultModelConfigDTO,
): Promise<ApiResponse<BindAppDefaultModelConfigVO>> {
  return apiPut<BindAppDefaultModelConfigVO>(`/admin/apps/${appId}/default-model-config`, dto)
}

export function bindDefaultKnowledgeBase(
  appId: number,
  dto: BindAppDefaultKnowledgeBaseDTO,
): Promise<ApiResponse<BindAppDefaultKnowledgeBaseVO>> {
  return apiPut<BindAppDefaultKnowledgeBaseVO>(`/admin/apps/${appId}/knowledge-base`, dto)
}

export function disableApp(id: number): Promise<ApiResponse<AppVO>> {
  return apiPost<AppVO>(`/admin/apps/${id}/disable`)
}

export function enableApp(id: number): Promise<ApiResponse<AppVO>> {
  return apiPost<AppVO>(`/admin/apps/${id}/enable`)
}

export function updateAppOutputCapture(
  appId: number,
  dto: UpdateAppOutputCaptureDTO,
): Promise<ApiResponse<AppVO>> {
  return apiPut<AppVO>(`/admin/apps/${appId}/request-log-output-capture`, dto)
}
