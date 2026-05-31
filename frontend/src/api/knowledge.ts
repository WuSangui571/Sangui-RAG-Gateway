import type { ApiResponse } from '../types/common'
import type { KnowledgeBaseVO, CreateKnowledgeBaseDTO } from '../types/knowledge'
import { apiGet, apiPost } from './http'

export function listKnowledgeBases(
  status: string | undefined,
  adminUserId: number,
): Promise<ApiResponse<KnowledgeBaseVO[]>> {
  const params: Record<string, string | number | undefined> = {}
  if (status) params.status = status
  return apiGet<KnowledgeBaseVO[]>('/admin/knowledge-bases', params, adminUserId)
}

export function getKnowledgeBaseDetail(
  id: number,
  adminUserId: number,
): Promise<ApiResponse<KnowledgeBaseVO>> {
  return apiGet<KnowledgeBaseVO>(`/admin/knowledge-bases/${id}`, undefined, adminUserId)
}

export function createKnowledgeBase(
  dto: CreateKnowledgeBaseDTO,
  adminUserId: number,
): Promise<ApiResponse<KnowledgeBaseVO>> {
  return apiPost<KnowledgeBaseVO>('/admin/knowledge-bases', dto, adminUserId)
}
