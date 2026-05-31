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

function buildUrl(path: string): string {
  const url = new URL(path, window.location.origin)
  url.pathname = BASE_URL + path
  return url.toString()
}

function buildHeaders(adminUserId?: number, extra?: Record<string, string>): Record<string, string> {
  const headers: Record<string, string> = { ...extra }
  if (adminUserId !== undefined) {
    headers['X-Admin-User-Id'] = String(adminUserId)
  }
  return headers
}

async function handleResponse<T>(response: Response): Promise<ApiResponse<T>> {
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

export async function apiGet<T>(
  path: string,
  params?: Record<string, string | number | undefined>,
  adminUserId?: number,
): Promise<ApiResponse<T>> {
  const url = new URL(buildUrl(path))
  if (params) {
    Object.entries(params).forEach(([key, value]) => {
      if (value !== undefined && value !== '') {
        url.searchParams.set(key, String(value))
      }
    })
  }
  const response = await fetch(url.toString(), {
    headers: buildHeaders(adminUserId),
  })
  return handleResponse<T>(response)
}

export async function apiPost<T>(
  path: string,
  body?: unknown,
  adminUserId?: number,
): Promise<ApiResponse<T>> {
  const response = await fetch(buildUrl(path), {
    method: 'POST',
    headers: buildHeaders(adminUserId, { 'Content-Type': 'application/json' }),
    body: body !== undefined ? JSON.stringify(body) : undefined,
  })
  return handleResponse<T>(response)
}

export async function apiPut<T>(
  path: string,
  body?: unknown,
  adminUserId?: number,
): Promise<ApiResponse<T>> {
  const response = await fetch(buildUrl(path), {
    method: 'PUT',
    headers: buildHeaders(adminUserId, { 'Content-Type': 'application/json' }),
    body: body !== undefined ? JSON.stringify(body) : undefined,
  })
  return handleResponse<T>(response)
}

export async function apiUpload<T>(
  path: string,
  formData: FormData,
  adminUserId?: number,
): Promise<ApiResponse<T>> {
  const response = await fetch(buildUrl(path), {
    method: 'POST',
    headers: buildHeaders(adminUserId),
    body: formData,
  })
  return handleResponse<T>(response)
}

export function unwrapResponse<T>(response: ApiResponse<T>): T {
  if (response.code !== 'OK') {
    throw new ApiError(response.code, response.message, 200)
  }
  return response.data
}
