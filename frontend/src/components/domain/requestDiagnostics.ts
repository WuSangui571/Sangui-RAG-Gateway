import type { DiagnosticBoundary } from '../../types/request-log'
import type { AppReadinessVO } from '../../types/app'
import type { I18nKey } from '../../app/i18n'

export interface DiagnosticResult {
  boundary: DiagnosticBoundary
  safe_summary_key: I18nKey
  safe_next_step_keys: I18nKey[]
}

const ERROR_CODE_BOUNDARY_MAP: Record<string, DiagnosticBoundary> = {
  model_config_not_ready: 'readiness',
  knowledge_base_not_ready: 'retrieval',
  embedding_failed: 'embedding',
  upstream_timeout: 'upstream',
  upstream_error: 'upstream',
  invalid_request: 'request-log',
}

const SUMMARY_KEYS: Record<DiagnosticBoundary, I18nKey> = {
  auth: 'diag.summary.auth',
  readiness: 'diag.summary.readiness',
  retrieval: 'diag.summary.retrieval',
  embedding: 'diag.summary.embedding',
  upstream: 'diag.summary.upstream',
  streaming: 'diag.summary.streaming',
  'request-log': 'diag.summary.request-log',
  unknown: 'diag.summary.unknown',
}

export const BOUNDARY_LABEL_KEYS: Record<DiagnosticBoundary, I18nKey> = {
  auth: 'diag.boundary.auth',
  readiness: 'diag.boundary.readiness',
  retrieval: 'diag.boundary.retrieval',
  embedding: 'diag.boundary.embedding',
  upstream: 'diag.boundary.upstream',
  streaming: 'diag.boundary.streaming',
  'request-log': 'diag.boundary.request-log',
  unknown: 'diag.boundary.unknown',
}

const NEXT_STEP_KEYS: Record<DiagnosticBoundary, I18nKey[]> = {
  auth: ['diag.step.auth'],
  readiness: ['diag.step.readiness'],
  retrieval: ['diag.step.retrieval'],
  embedding: ['diag.step.embedding'],
  upstream: ['diag.step.upstream'],
  streaming: ['diag.step.streaming'],
  'request-log': ['diag.step.request-log'],
  unknown: ['diag.step.unknown'],
}

interface LogSnapshot {
  status: string
  error_code: string | null
  hit_chunk_ids: number[]
}

export function classifyDiagnostic(
  log: LogSnapshot,
  readiness: AppReadinessVO | null,
): DiagnosticResult | null {
  if (log.status === 'success' && log.hit_chunk_ids.length > 0) {
    return null
  }

  if (log.status === 'success' && log.hit_chunk_ids.length === 0) {
    return buildResult('retrieval')
  }

  const boundary = resolveBoundary(log.error_code, readiness)
  return buildResult(boundary)
}

function resolveBoundary(
  errorCode: string | null,
  readiness: AppReadinessVO | null,
): DiagnosticBoundary {
  if (errorCode && errorCode in ERROR_CODE_BOUNDARY_MAP) {
    return ERROR_CODE_BOUNDARY_MAP[errorCode]
  }

  if (readiness && readiness.checks) {
    for (const check of readiness.checks) {
      if (check.status !== 'READY') {
        if (check.key === 'default_model_config' || check.key === 'app') {
          return 'readiness'
        }
        if (check.key === 'default_knowledge_base' || check.key === 'knowledge_base_status') {
          return 'retrieval'
        }
        if (check.key === 'embedding_config') {
          return 'embedding'
        }
      }
    }
  }

  return 'unknown'
}

function buildResult(boundary: DiagnosticBoundary): DiagnosticResult {
  return {
    boundary,
    safe_summary_key: SUMMARY_KEYS[boundary],
    safe_next_step_keys: NEXT_STEP_KEYS[boundary],
  }
}
