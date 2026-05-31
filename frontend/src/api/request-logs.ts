import type { ApiResponse } from '../types/common'
import type {
  ApiRequestLogDetailVO,
  ApiRequestLogPageVO,
  ApiRequestLogVO,
  HitChunkSummaryVO,
  RequestLogListParams,
} from '../types/request-log'
import { apiGet } from './http'

export function listRequestLogs(
  appId: number,
  params: RequestLogListParams,
  adminUserId: number,
): Promise<ApiResponse<ApiRequestLogPageVO<ApiRequestLogVO>>> {
  const queryParams: Record<string, string | number | undefined> = {
    page: params.page,
    page_size: params.page_size,
    status: params.status || undefined,
    error_code: params.error_code || undefined,
    start_time: params.start_time || undefined,
    end_time: params.end_time || undefined,
  }
  return apiGet<ApiRequestLogPageVO<ApiRequestLogVO>>(
    `/admin/apps/${appId}/request-logs`,
    queryParams,
    adminUserId,
  )
}

export function getRequestLogDetail(
  appId: number,
  requestId: string,
  adminUserId: number,
): Promise<ApiResponse<ApiRequestLogDetailVO>> {
  return apiGet<ApiRequestLogDetailVO>(
    `/admin/apps/${appId}/request-logs/${requestId}`,
    undefined,
    adminUserId,
  )
}

export function getHitChunks(
  appId: number,
  requestId: string,
  adminUserId: number,
): Promise<ApiResponse<HitChunkSummaryVO[]>> {
  return apiGet<HitChunkSummaryVO[]>(
    `/admin/apps/${appId}/request-logs/${requestId}/hit-chunks`,
    undefined,
    adminUserId,
  )
}
