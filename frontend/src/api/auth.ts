import type { ApiResponse } from '../types/common'
import type { AdminLoginDTO, AdminLoginVO, AdminUserVO } from '../types/auth'
import { apiGet, apiPost } from './http'

export function login(dto: AdminLoginDTO): Promise<ApiResponse<AdminLoginVO>> {
  return apiPost<AdminLoginVO>('/admin/auth/login', dto)
}

export function getCurrentUser(): Promise<ApiResponse<AdminUserVO>> {
  return apiGet<AdminUserVO>('/admin/auth/me')
}
