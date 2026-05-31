import type { ApiResponse } from '../types/common'

const BASE_URL = '/api'

export class ApiError extends Error {
  code: string
  status: number

  constructor(code: string, message: string, status: number) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.status = status
  }
}

export async function apiGet<T>(
  path: string,
  params?: Record<string, string | number | undefined>,
  adminUserId?: number,
): Promise<ApiResponse<T>> {
  const url = new URL(path, window.location.origin)
  url.pathname = BASE_URL + path

  if (params) {
    Object.entries(params).forEach(([key, value]) => {
      if (value !== undefined && value !== '') {
        url.searchParams.set(key, String(value))
      }
    })
  }

  const headers: Record<string, string> = {}
  if (adminUserId !== undefined) {
    headers['X-Admin-User-Id'] = String(adminUserId)
  }

  const response = await fetch(url.toString(), { headers })

  if (!response.ok) {
    let body: ApiResponse<unknown> | null = null
    try {
      body = await response.json() as ApiResponse<unknown>
    } catch {
      // ignore parse failure
    }
    throw new ApiError(
      body?.code || 'NETWORK_ERROR',
      body?.message || `HTTP ${response.status}`,
      response.status,
    )
  }

  return response.json() as Promise<ApiResponse<T>>
}

export function unwrapResponse<T>(response: ApiResponse<T>): T {
  if (response.code !== 'OK') {
    throw new ApiError(response.code, response.message, 200)
  }
  return response.data
}
