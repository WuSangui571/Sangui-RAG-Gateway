import type { ApiResponse } from '../types/common'
import type { KnowledgeBaseVO, CreateKnowledgeBaseDTO } from '../types/knowledge'
import { apiGet, apiPost } from './http'

export function listKnowledgeBases(
  status: string | undefined,
): Promise<ApiResponse<KnowledgeBaseVO[]>> {
  const params: Record<string, string | number | undefined> = {}
  if (status) params.status = status
  return apiGet<KnowledgeBaseVO[]>('/admin/knowledge-bases', params)
}

export function getKnowledgeBaseDetail(id: number): Promise<ApiResponse<KnowledgeBaseVO>> {
  return apiGet<KnowledgeBaseVO>(`/admin/knowledge-bases/${id}`)
}

export function createKnowledgeBase(dto: CreateKnowledgeBaseDTO): Promise<ApiResponse<KnowledgeBaseVO>> {
  return apiPost<KnowledgeBaseVO>('/admin/knowledge-bases', dto)
}
