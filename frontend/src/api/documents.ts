import type { ApiResponse } from '../types/common'
import type { DocumentVO } from '../types/document'
import { apiGet, apiUpload } from './http'

export function uploadDocument(
  knowledgeBaseId: number,
  file: File,
  adminUserId: number,
): Promise<ApiResponse<DocumentVO>> {
  const formData = new FormData()
  formData.append('file', file)
  return apiUpload<DocumentVO>(`/admin/knowledge-bases/${knowledgeBaseId}/documents`, formData, adminUserId)
}

export function listDocuments(
  knowledgeBaseId: number,
  status: string | undefined,
  adminUserId: number,
): Promise<ApiResponse<DocumentVO[]>> {
  const params: Record<string, string | number | undefined> = {}
  if (status) params.status = status
  return apiGet<DocumentVO[]>(`/admin/knowledge-bases/${knowledgeBaseId}/documents`, params, adminUserId)
}

export function getDocumentDetail(
  documentId: number,
  adminUserId: number,
): Promise<ApiResponse<DocumentVO>> {
  return apiGet<DocumentVO>(`/admin/documents/${documentId}`, undefined, adminUserId)
}
