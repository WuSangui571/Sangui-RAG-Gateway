import type { ApiResponse } from '../types/common'
import type { DocumentVO } from '../types/document'
import { apiGet, apiPost, apiUpload } from './http'

export function uploadDocument(
  knowledgeBaseId: number,
  file: File,
): Promise<ApiResponse<DocumentVO>> {
  const formData = new FormData()
  formData.append('file', file)
  return apiUpload<DocumentVO>(`/admin/knowledge-bases/${knowledgeBaseId}/documents`, formData)
}

export function listDocuments(
  knowledgeBaseId: number,
  status: string | undefined,
): Promise<ApiResponse<DocumentVO[]>> {
  const params: Record<string, string | number | undefined> = {}
  if (status) params.status = status
  return apiGet<DocumentVO[]>(`/admin/knowledge-bases/${knowledgeBaseId}/documents`, params)
}

export function getDocumentDetail(documentId: number): Promise<ApiResponse<DocumentVO>> {
  return apiGet<DocumentVO>(`/admin/documents/${documentId}`)
}

export function retryDocument(documentId: number): Promise<ApiResponse<DocumentVO>> {
  return apiPost<DocumentVO>(`/admin/documents/${documentId}/processing-task/retry`, {})
}
