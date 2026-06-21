import { useCallback, useEffect, useMemo, useState } from 'react'
import type { CSSProperties, ReactNode } from 'react'
import {
  Button, Select, Space, Typography, Card, Spin, Descriptions, Tag, Input, Divider, Alert,
} from 'antd'
import type { AppVO, AppReadinessVO, ReadinessStatus } from '../../types/app'
import type { ApiKeyVO } from '../../types/api-key'
import type { SmokeChatCompletionResponse, SmokeStreamingEvidence } from '../../types/openai'
import type { ApiRequestLogVO, ApiRequestLogDetailVO, HitChunkSummaryVO, DiagnosticBoundary } from '../../types/request-log'
import { listApps, getAppReadiness } from '../../api/apps'
import { listApiKeys } from '../../api/api-keys'
import { ApiError } from '../../api/http'
import { smokeChatCompletions, smokeStreamingChatCompletions, SmokeApiError } from '../../api/openai'
import { listRequestLogs, getRequestLogDetail, getHitChunks } from '../../api/request-logs'
import { useShell } from '../../components/layout/AdminShell'
import { useI18n } from '../../app/i18n'
import type { I18nKey } from '../../app/i18n/dict'
import { BOUNDARY_LABEL_KEYS } from '../../components/domain/requestDiagnostics'

const { Text } = Typography

type StepStatus = 'idle' | 'running' | 'pass' | 'fail' | 'skip'

interface NonStreamingStepState {
  status: StepStatus
  evidence: {
    id: string
    object: string
    model: string
    finishReason: string
    contentLength: number
    promptTokens: number
    completionTokens: number
    totalTokens: number
  } | null
  error: { status: number; message: string; code: string | null } | null
}

interface StreamingStepState {
  status: StepStatus
  evidence: SmokeStreamingEvidence | null
  error: { status: number; message: string; code: string | null } | null
}

interface RequestLogStepState {
  status: StepStatus
  listRow: ApiRequestLogVO | null
  detail: ApiRequestLogDetailVO | null
  hitChunks: HitChunkSummaryVO[] | null
  error: string | null
  subStep: 'idle' | 'list' | 'detail' | 'hit-chunks' | 'done'
}

interface RevokedKeyStepState {
  status: StepStatus
  error: { status: number; message: string; code: string | null } | null
  enabled: boolean
  keyValue: string | null
}

const READINESS_HINT_MAP: Record<string, I18nKey> = {
  app: 'smoke.precondition.hint.app' as I18nKey,
  default_model_config: 'smoke.precondition.hint.default_model_config' as I18nKey,
  default_knowledge_base: 'smoke.precondition.hint.default_knowledge_base' as I18nKey,
  knowledge_base_status: 'smoke.precondition.hint.knowledge_base_status' as I18nKey,
  active_api_key: 'smoke.precondition.hint.active_api_key' as I18nKey,
  embedding_config: 'smoke.precondition.hint.embedding_config' as I18nKey,
}

const ERROR_CODE_BOUNDARY_MAP: Record<string, DiagnosticBoundary> = {
  model_config_not_ready: 'readiness',
  knowledge_base_not_ready: 'retrieval',
  embedding_failed: 'embedding',
  upstream_timeout: 'upstream',
  upstream_error: 'upstream',
  invalid_api_key: 'auth',
}

const BOUNDARY_COLORS: Record<DiagnosticBoundary, string> = {
  auth: 'red',
  readiness: 'orange',
  retrieval: 'blue',
  embedding: 'purple',
  upstream: 'red',
  streaming: 'cyan',
  'request-log': 'gold',
  unknown: 'default',
}

const FAILURE_HINT_MAP: Record<DiagnosticBoundary, I18nKey> = {
  auth: 'smoke.failure.auth' as I18nKey,
  readiness: 'smoke.failure.readiness' as I18nKey,
  retrieval: 'smoke.failure.retrieval' as I18nKey,
  embedding: 'smoke.failure.embedding' as I18nKey,
  upstream: 'smoke.failure.upstream' as I18nKey,
  streaming: 'smoke.failure.streaming' as I18nKey,
  'request-log': 'smoke.failure.request-log' as I18nKey,
  unknown: 'smoke.failure.unknown' as I18nKey,
}

const FRAMED_SECTION_STYLE: CSSProperties = {
  border: '1px solid rgba(140, 140, 140, 0.35)',
  borderRadius: 6,
  padding: 12,
}

const FRAMED_SECTION_BORDER: Record<'success' | 'error', string> = {
  success: '#52c41a',
  error: '#ff4d4f',
}

function FramedSection({
  title,
  tone,
  children,
}: {
  title?: ReactNode
  tone?: 'success' | 'error'
  children: ReactNode
}) {
  const style = tone
    ? { ...FRAMED_SECTION_STYLE, borderColor: FRAMED_SECTION_BORDER[tone] }
    : FRAMED_SECTION_STYLE

  return (
    <div style={style}>
      {title && (
        <Text strong style={{ display: 'block', marginBottom: 8 }}>{title}</Text>
      )}
      {children}
    </div>
  )
}

function StepStatusTagLocalized({ status }: { status: StepStatus }) {
  const { t } = useI18n()
  const upper = status.toUpperCase()
  const label = t(`status.smoke.${upper}` as I18nKey)
  if (status === 'idle') return <Tag>{label}</Tag>
  if (status === 'running') return <Tag color="processing">{label}</Tag>
  if (status === 'pass') return <Tag color="success">{label}</Tag>
  if (status === 'fail') return <Tag color="error">{label}</Tag>
  if (status === 'skip') return <Tag color="default">{label}</Tag>
  return null
}

function ReadinessStatusTagLocalized({ status }: { status: ReadinessStatus | string }) {
  const { t } = useI18n()
  if (status === 'READY') return <Tag color="success">{t('status.readiness.READY')}</Tag>
  if (status === 'MISSING') return <Tag color="error">{t('status.readiness.MISSING')}</Tag>
  if (status === 'DISABLED') return <Tag color="warning">{t('status.readiness.DISABLED')}</Tag>
  if (status === 'NOT_READY') return <Tag color="default">{t('status.readiness.NOT_READY')}</Tag>
  if (!status) return <Tag color="default">{t('status.readiness.UNKNOWN')}</Tag>
  const upper = status.toUpperCase()
  return <Tag color="default">{upper}</Tag>
}

export default function SmokeTestPage() {
  const { adminUserId, selectedAppId, setSelectedAppId } = useShell()
  const { t, tCommon } = useI18n()

  const [apps, setApps] = useState<AppVO[]>([])
  const [keys, setKeys] = useState<ApiKeyVO[]>([])
  const [activeAppId, setActiveAppId] = useState<number | null>(selectedAppId)
  const [selectedKeyPrefix, setSelectedKeyPrefix] = useState<string | null>(null)
  const [selectedKeyValue, setSelectedKeyValue] = useState<string | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [userInput, setUserInput] = useState('Answer using the uploaded knowledge base.')

  const [nonStreaming, setNonStreaming] = useState<NonStreamingStepState>({
    status: 'idle', evidence: null, error: null,
  })
  const [streaming, setStreaming] = useState<StreamingStepState>({
    status: 'idle', evidence: null, error: null,
  })
  const [requestLog, setRequestLog] = useState<RequestLogStepState>({
    status: 'idle', listRow: null, detail: null, hitChunks: null, error: null, subStep: 'idle',
  })
  const [revokedKey, setRevokedKey] = useState<RevokedKeyStepState>({
    status: 'idle', error: null, enabled: false, keyValue: null,
  })

  const [readiness, setReadiness] = useState<AppReadinessVO | null>(null)
  const [readinessLoading, setReadinessLoading] = useState(false)
  const [readinessError, setReadinessError] = useState<string | null>(null)

  const [runAllActive, setRunAllActive] = useState(false)

  const fetchApps = useCallback(async () => {
    if (adminUserId === null) return
    try {
      const res = await listApps(undefined)
      if (res.code === 'OK') {
        setApps(res.data)
        setLoadError(null)
      } else {
        setApps([])
        setLoadError(res.message)
      }
    } catch (e: unknown) {
      setApps([])
      setLoadError(e instanceof ApiError ? e.message : (e instanceof Error ? e.message : tCommon('Failed to load apps')))
    }
  }, [adminUserId, tCommon])

  useEffect(() => {
    fetchApps()
  }, [fetchApps])

  useEffect(() => {
    if (selectedAppId !== null) {
      setActiveAppId(selectedAppId)
      setSelectedKeyValue(null)
    }
  }, [selectedAppId])

  const fetchKeys = useCallback(async () => {
    if (activeAppId === null || adminUserId === null) return
    try {
      const res = await listApiKeys(activeAppId)
      if (res.code === 'OK') {
        setKeys(res.data.filter(k => k.status === 'ACTIVE'))
        setLoadError(null)
      } else {
        setKeys([])
        setLoadError(res.message)
      }
    } catch (e: unknown) {
      setKeys([])
      setLoadError(e instanceof ApiError ? e.message : (e instanceof Error ? e.message : tCommon('Failed to load API keys')))
    }
  }, [activeAppId, adminUserId, tCommon])

  const fetchReadiness = useCallback(async () => {
    if (activeAppId === null || adminUserId === null) {
      setReadiness(null)
      setReadinessError(null)
      return
    }
    setReadinessLoading(true)
    setReadinessError(null)
    try {
      const res = await getAppReadiness(activeAppId)
      if (res.code === 'OK') {
        setReadiness(res.data)
      } else {
        setReadiness(null)
        setReadinessError(res.message)
      }
    } catch (e: unknown) {
      setReadiness(null)
      setReadinessError(e instanceof ApiError ? e.message : (e instanceof Error ? e.message : tCommon('Failed to load readiness')))
    } finally {
      setReadinessLoading(false)
    }
  }, [activeAppId, adminUserId, tCommon])

  useEffect(() => {
    setSelectedKeyPrefix(null)
    setSelectedKeyValue(null)
    resetAllSteps()
    setReadiness(null)
    setReadinessError(null)
    fetchKeys()
    fetchReadiness()
  }, [activeAppId, fetchKeys, fetchReadiness])

  function resetAllSteps() {
    setNonStreaming({ status: 'idle', evidence: null, error: null })
    setStreaming({ status: 'idle', evidence: null, error: null })
    setRequestLog({ status: 'idle', listRow: null, detail: null, hitChunks: null, error: null, subStep: 'idle' })
    setRevokedKey(prev => ({ ...prev, status: 'idle', error: null }))
    setRunAllActive(false)
  }

  function handleAppSelect(appId: number) {
    setActiveAppId(appId)
    setSelectedAppId(appId)
    setSelectedKeyPrefix(null)
    setSelectedKeyValue(null)
    resetAllSteps()
    setReadiness(null)
    setReadinessError(null)
  }

  function handleApiKeyChange(value: string) {
    setSelectedKeyValue(value || null)
    resetAllSteps()
  }

  function handleUserInputChange(value: string) {
    setUserInput(value)
    resetAllSteps()
  }

  async function handleNonStreamingSmoke(): Promise<boolean> {
    if (selectedKeyValue === null) return false
    setNonStreaming({ status: 'running', evidence: null, error: null })
    try {
      const response: SmokeChatCompletionResponse = await smokeChatCompletions({
        model: 'ignored-by-gateway',
        messages: [{ role: 'user', content: userInput }],
        stream: false,
      }, selectedKeyValue)
      const content = response.choices[0]?.message.content
      setNonStreaming({
        status: 'pass',
        evidence: {
          id: response.id,
          object: response.object,
          model: response.model,
          finishReason: response.choices[0]?.finish_reason || 'N/A',
          contentLength: content ? content.length : 0,
          promptTokens: response.usage.prompt_tokens,
          completionTokens: response.usage.completion_tokens,
          totalTokens: response.usage.total_tokens,
        },
        error: null,
      })
      return true
    } catch (e: unknown) {
      if (e instanceof SmokeApiError) {
        setNonStreaming({
          status: 'fail',
          evidence: null,
          error: { status: e.status, message: e.message, code: e.errorCode },
        })
      } else {
        setNonStreaming({
          status: 'fail',
          evidence: null,
          error: { status: 0, message: e instanceof Error ? e.message : tCommon('Unknown error'), code: null },
        })
      }
      return false
    }
  }

  async function handleStreamingSmoke() {
    if (selectedKeyValue === null) return
    setStreaming({ status: 'running', evidence: null, error: null })
    try {
      const evidence = await smokeStreamingChatCompletions({
        model: 'ignored-by-gateway',
        messages: [{ role: 'user', content: userInput }],
        stream: true,
      }, selectedKeyValue)
      setStreaming({
        status: evidence.donePresent ? 'pass' : 'fail',
        evidence,
        error: evidence.donePresent ? null : {
          status: evidence.httpStatus,
          message: 'SSE chunks received but [DONE] missing; stream may have been truncated',
          code: 'upstream_error',
        },
      })
    } catch (e: unknown) {
      if (e instanceof SmokeApiError) {
        setStreaming({
          status: 'fail',
          evidence: null,
          error: { status: e.status, message: e.message, code: e.errorCode },
        })
      } else {
        setStreaming({
          status: 'fail',
          evidence: null,
          error: { status: 0, message: e instanceof Error ? e.message : tCommon('Unknown error'), code: null },
        })
      }
    }
  }

  async function handleRequestLogValidation() {
    if (activeAppId === null || adminUserId === null) {
      setRequestLog({ status: 'skip', listRow: null, detail: null, hitChunks: null, error: null, subStep: 'idle' })
      return
    }
    setRequestLog({ status: 'running', listRow: null, detail: null, hitChunks: null, error: null, subStep: 'list' })

    try {
      const listRes = await listRequestLogs(activeAppId, { page: 1, page_size: 5, status: 'success' })
      if (listRes.code !== 'OK') {
        setRequestLog({ status: 'fail', listRow: null, detail: null, hitChunks: null, error: `List: ${listRes.message}`, subStep: 'list' })
        return
      }

      const items = listRes.data.items
      if (items.length === 0) {
        setRequestLog({ status: 'fail', listRow: null, detail: null, hitChunks: null, error: 'No success request log found for this app', subStep: 'list' })
        return
      }

      const matchPrefix = userInput.length > 30 ? userInput.substring(0, 30) : userInput
      const matchedLog = items.find(item =>
        item.question_summary && item.question_summary.startsWith(matchPrefix)
      )

      if (!matchedLog) {
        setRequestLog({ status: 'fail', listRow: null, detail: null, hitChunks: null, error: `No recent success log with question_summary matching '${matchPrefix}'`, subStep: 'list' })
        return
      }

      const listErrors: string[] = []
      if (matchedLog.status !== 'success') listErrors.push(`status=${matchedLog.status}, expected success`)
      if (!matchedLog.model || matchedLog.model.trim() === '') listErrors.push('model is blank')
      if (!matchedLog.provider_name || matchedLog.provider_name.trim() === '') listErrors.push('provider_name is blank')
      if (matchedLog.latency_ms === null || matchedLog.latency_ms < 0) listErrors.push('latency_ms is null or negative')
      if (!matchedLog.question_summary || matchedLog.question_summary.trim() === '') listErrors.push('question_summary is blank')
      if (!matchedLog.hit_chunk_ids || matchedLog.hit_chunk_ids.length === 0) listErrors.push('hit_chunk_ids is empty')

      if (listErrors.length > 0) {
        setRequestLog({ status: 'fail', listRow: matchedLog, detail: null, hitChunks: null, error: `List validation: ${listErrors.join('; ')}`, subStep: 'list' })
        return
      }

      setRequestLog(prev => ({ ...prev, listRow: matchedLog, subStep: 'detail' }))

      const detailRes = await getRequestLogDetail(activeAppId, matchedLog.request_id)
      if (detailRes.code !== 'OK') {
        setRequestLog(prev => ({ ...prev, status: 'fail', error: `Detail: ${detailRes.message}`, subStep: 'detail' }))
        return
      }

      const detail = detailRes.data
      const detailErrors: string[] = []
      if (detail.request_id !== matchedLog.request_id) detailErrors.push(`detail request_id=${detail.request_id} differs from list row`)
      if (!('user_id' in detail)) detailErrors.push('detail missing user_id')
      if (!('updated_at' in detail)) detailErrors.push('detail missing updated_at')
      if (!detail.model) detailErrors.push('detail missing model')
      if (!detail.provider_name) detailErrors.push('detail missing provider_name')
      if (detail.latency_ms === null) detailErrors.push('detail missing latency_ms')
      if (detail.messages_count === null) detailErrors.push('detail missing messages_count')

      if (detailErrors.length > 0) {
        setRequestLog(prev => ({ ...prev, detail, status: 'fail', error: `Detail validation: ${detailErrors.join('; ')}`, subStep: 'detail' }))
        return
      }

      setRequestLog(prev => ({ ...prev, detail, subStep: 'hit-chunks' }))

      const chunksRes = await getHitChunks(activeAppId, matchedLog.request_id)
      if (chunksRes.code !== 'OK') {
        setRequestLog(prev => ({ ...prev, status: 'fail', error: `Hit-chunks: ${chunksRes.message}`, subStep: 'hit-chunks' }))
        return
      }

      const chunks = chunksRes.data
      if (chunks.length === 0) {
        setRequestLog(prev => ({ ...prev, hitChunks: chunks, status: 'fail', error: 'Hit-chunks returned empty list but hit_chunk_ids was non-empty', subStep: 'hit-chunks' }))
        return
      }

      const chunkErrors: string[] = []
      for (const chunk of chunks) {
        if (chunk.chunk_id === null || chunk.chunk_id === undefined) chunkErrors.push('chunk_id is null')
        if (chunk.document_id === null || chunk.document_id === undefined) chunkErrors.push('document_id is null')
        if (chunk.knowledge_base_id === null || chunk.knowledge_base_id === undefined) chunkErrors.push('knowledge_base_id is null')
        if (chunk.chunk_index === null || chunk.chunk_index === undefined) chunkErrors.push('chunk_index is null')
      }

      if (chunkErrors.length > 0) {
        const unique = [...new Set(chunkErrors)]
        setRequestLog(prev => ({ ...prev, hitChunks: chunks, status: 'fail', error: `Hit-chunks validation: ${unique.join('; ')}`, subStep: 'hit-chunks' }))
        return
      }

      setRequestLog({
        status: 'pass',
        listRow: matchedLog,
        detail,
        hitChunks: chunks,
        error: null,
        subStep: 'done',
      })
    } catch (e: unknown) {
      const msg = e instanceof ApiError ? e.message : (e instanceof Error ? e.message : 'Unknown error')
      setRequestLog(prev => ({ ...prev, status: 'fail', error: msg }))
    }
  }

  async function handleRevokedKeyCheck() {
    if (!revokedKey.enabled || !revokedKey.keyValue) {
      setRevokedKey(prev => ({ ...prev, status: 'skip', error: null }))
      return
    }
    setRevokedKey(prev => ({ ...prev, status: 'running', error: null }))
    try {
      await smokeChatCompletions({
        model: 'ignored-by-gateway',
        messages: [{ role: 'user', content: 'test' }],
        stream: false,
      }, revokedKey.keyValue)
      setRevokedKey(prev => ({
        ...prev,
        status: 'fail',
        error: { status: 200, message: 'Revoked key succeeded, expected 401', code: null },
      }))
    } catch (e: unknown) {
      if (e instanceof SmokeApiError) {
        if (e.status === 401 && e.errorCode === 'invalid_api_key') {
          setRevokedKey(prev => ({ ...prev, status: 'pass', error: null }))
        } else {
          setRevokedKey(prev => ({
            ...prev,
            status: 'fail',
            error: { status: e.status, message: e.message, code: e.errorCode },
          }))
        }
      } else {
        setRevokedKey(prev => ({
          ...prev,
          status: 'fail',
          error: { status: 0, message: e instanceof Error ? e.message : tCommon('Unknown error'), code: null },
        }))
      }
    }
  }

  async function handleRunAllSmokes() {
    if (!canRun) return
    resetAllSteps()
    setRunAllActive(true)

    const nsPassed = await handleNonStreamingSmoke()
    await handleStreamingSmoke()

    if (nsPassed && activeAppId !== null && adminUserId !== null) {
      await handleRequestLogValidation()
    }

    setRunAllActive(false)
  }

  const readinessReady = readiness !== null && readiness.overall_status === 'READY'
  const readinessNotReady = readiness !== null && readiness.overall_status !== 'READY'
  const canRun = activeAppId !== null
    && readinessReady
    && readinessError === null
    && selectedKeyValue !== null
    && userInput.trim().length > 0
  const canRunRequestLog = activeAppId !== null && adminUserId !== null && nonStreaming.status === 'pass'

  const disabledReason = useMemo((): string | null => {
    if (activeAppId === null) return t('smoke.disabledSelectApp')
    if (readinessLoading || readinessError !== null || !readinessReady) return t('smoke.disabledNotReady')
    if (selectedKeyValue === null) return t('smoke.disabledNoKey')
    if (userInput.trim().length === 0) return t('smoke.disabledNoMessage')
    return null
  }, [activeAppId, readinessLoading, readinessError, readinessReady, selectedKeyValue, userInput, t])

  const failureBoundary = useMemo((): DiagnosticBoundary | null => {
    if (readinessError !== null) return 'readiness'

    if (readinessNotReady) {
      if (readiness?.checks) {
        for (const check of readiness.checks) {
          if (check.status !== 'READY') {
            if (check.key === 'app' || check.key === 'default_model_config') return 'readiness'
            if (check.key === 'default_knowledge_base' || check.key === 'knowledge_base_status') return 'retrieval'
            if (check.key === 'embedding_config') return 'embedding'
            if (check.key === 'active_api_key') return 'auth'
          }
        }
      }
      return 'readiness'
    }

    if (nonStreaming.status === 'fail' && nonStreaming.error?.code) {
      const mapped = ERROR_CODE_BOUNDARY_MAP[nonStreaming.error.code]
      if (mapped) return mapped
    }
    if (streaming.status === 'fail') {
      if (streaming.evidence && !streaming.evidence.donePresent) return 'streaming'
      if (streaming.error?.code) {
        const mapped = ERROR_CODE_BOUNDARY_MAP[streaming.error.code]
        if (mapped) return mapped
      }
    }
    if (requestLog.status === 'fail') return 'request-log'
    if (revokedKey.status === 'fail') return 'auth'
    if (nonStreaming.status === 'fail' || streaming.status === 'fail') return 'unknown'
    return null
  }, [readinessError, readinessNotReady, readiness, nonStreaming, streaming, requestLog, revokedKey])

  const hasEvidence = nonStreaming.status === 'pass' || nonStreaming.status === 'fail'
    || streaming.status === 'pass' || streaming.status === 'fail'
    || requestLog.status === 'pass' || requestLog.status === 'fail'
    || revokedKey.status === 'pass' || revokedKey.status === 'fail'

  return (
    <div>
      <Space direction="vertical" style={{ width: '100%' }} size="middle">
        {loadError && (
          <Alert type="error" message={loadError} showIcon closable />
        )}

        {/* Region 1: Select App */}
        <Card size="small" title={t('smoke.app')}>
          <Space direction="vertical" style={{ width: '100%' }}>
            <Select
              value={activeAppId}
              onChange={(v) => handleAppSelect(v)}
              placeholder={t('smoke.appPlaceholder')}
              style={{ width: 280 }}
              options={apps.map(a => ({ value: a.id, label: `#${a.id} ${a.name}` }))}
            />
            {apps.length === 0 && !loadError && (
              <Text type="secondary">{t('smoke.noApps')}</Text>
            )}
            {activeAppId === null && apps.length > 0 && (
              <Text type="secondary">{t('smoke.selectAppHint')}</Text>
            )}
            {activeAppId !== null && (
              <Space size="small">
                {keys.length > 0 && (
                  <Select
                    value={selectedKeyPrefix}
                    onChange={(v) => setSelectedKeyPrefix(v)}
                    placeholder={t('smoke.keyPlaceholder')}
                    style={{ width: 240 }}
                    options={keys.map(k => ({
                      value: `${k.name} (${k.key_prefix})`,
                      label: `${k.name} (${k.key_prefix})`,
                    }))}
                  />
                )}
                {keys.length === 0 && (
                  <Text type="warning" style={{ fontSize: 12 }}>
                    {t('smoke.noActiveApiKey')}
                  </Text>
                )}
              </Space>
            )}
          </Space>
        </Card>

        {/* Region 2: Preconditions */}
        {activeAppId !== null && (
          <Card
            size="small"
            title={t('smoke.preconditionsTitle')}
            extra={
              readiness && !readinessLoading
                ? <ReadinessStatusTagLocalized status={readiness.overall_status} />
                : null
            }
          >
            {readinessLoading && (
              <Spin tip={t('smoke.preflightLoading')} style={{ display: 'block', margin: '16px 0' }}>
                <div style={{ height: 40 }} />
              </Spin>
            )}
            {readinessError && (
              <Alert
                type="warning"
                message={`${t('smoke.preflightError')} ${readinessError}`}
                action={<Button size="small" onClick={fetchReadiness}>{t('smoke.retryReadiness')}</Button>}
                style={{ marginBottom: 8 }}
              />
            )}
            {readiness && readiness.checks.length > 0 && (
              <Space direction="vertical" style={{ width: '100%' }} size="small">
                {readiness.checks.map(check => {
                  const hintKey = check.status !== 'READY'
                    ? READINESS_HINT_MAP[check.key]
                    : undefined
                  return (
                    <div key={check.key} style={{ display: 'flex', alignItems: 'flex-start', gap: 8 }}>
                      <ReadinessStatusTagLocalized status={check.status} />
                      <div style={{ flex: 1 }}>
                        <Text strong style={{ fontSize: 13 }}>{check.label || check.key}</Text>
                        {check.message && (
                          <Text type="secondary" style={{ display: 'block', fontSize: 12 }}>{check.message}</Text>
                        )}
                        {hintKey && (
                          <Text type="warning" style={{ display: 'block', fontSize: 12 }}>
                            {t(hintKey)}
                          </Text>
                        )}
                      </div>
                    </div>
                  )
                })}
              </Space>
            )}
            {readiness && readiness.checks.length === 0 && (
              <Text type="secondary">{t('smoke.precondition.noChecks')}</Text>
            )}
            {readinessNotReady && (
              <Alert
                type="warning"
                message={t('smoke.preflightWarning')}
                style={{ marginTop: 8 }}
                showIcon
              />
            )}
          </Card>
        )}

        {/* Region 3: Execute Smoke */}
        {activeAppId !== null && (
          <Card size="small" title={t('smoke.executeTitle')}>
            <Space direction="vertical" style={{ width: '100%' }} size="middle">
              <FramedSection>
                <Space direction="vertical" style={{ width: '100%' }} size="small">
                  <div>
                    <Text type="secondary" style={{ fontSize: 12 }}>{t('smoke.keyCardTitle')}</Text>
                    <Space.Compact style={{ width: '100%', marginTop: 4 }}>
                      <Input.Password
                        value={selectedKeyValue ?? ''}
                        onChange={(e) => handleApiKeyChange(e.target.value)}
                        placeholder="sk-sangui-..."
                        style={{ flex: 1 }}
                      />
                      <Button
                        onClick={() => { setSelectedKeyValue(null); resetAllSteps() }}
                        disabled={selectedKeyValue === null}
                      >
                        {t('smoke.clear')}
                      </Button>
                    </Space.Compact>
                    <Text type="secondary" style={{ fontSize: 11 }}>{t('smoke.keyHint')}</Text>
                  </div>
                  <div>
                    <Text type="secondary" style={{ fontSize: 12 }}>{t('smoke.userMessageTitle')}</Text>
                    <Input.TextArea
                      value={userInput}
                      onChange={(e) => handleUserInputChange(e.target.value)}
                      rows={2}
                      placeholder={t('smoke.userMessagePlaceholder')}
                      style={{ marginTop: 4 }}
                    />
                  </div>
                </Space>
              </FramedSection>

              {/* Primary Run All */}
              <Space>
                <Button
                  type="primary"
                  onClick={handleRunAllSmokes}
                  loading={runAllActive}
                  disabled={!canRun}
                >
                  {t('smoke.runAllSmokes')}
                </Button>
                {disabledReason && (
                  <Text type="secondary">{disabledReason}</Text>
                )}
                {runAllActive && (
                  <Spin size="small" style={{ marginLeft: 8 }} />
                )}
              </Space>

              <Divider orientation="left" plain style={{ fontSize: 13, margin: '8px 0' }}>{t('smoke.step1')}</Divider>

              <Space>
                <Button
                  onClick={() => { handleNonStreamingSmoke() }}
                  loading={nonStreaming.status === 'running'}
                  disabled={!canRun}
                >
                  {t('smoke.step1Send')}
                </Button>
                <StepStatusTagLocalized status={nonStreaming.status} />
              </Space>

              <Divider orientation="left" plain style={{ fontSize: 13, margin: '8px 0' }}>{t('smoke.step2')}</Divider>

              <Space>
                <Button
                  onClick={handleStreamingSmoke}
                  loading={streaming.status === 'running'}
                  disabled={!canRun}
                >
                  {t('smoke.step2Send')}
                </Button>
                <StepStatusTagLocalized status={streaming.status} />
              </Space>

              <Divider orientation="left" plain style={{ fontSize: 13, margin: '8px 0' }}>{t('smoke.step3')}</Divider>

              <Space>
                <Button
                  onClick={handleRequestLogValidation}
                  loading={requestLog.status === 'running'}
                  disabled={!canRunRequestLog}
                >
                  {t('smoke.step3Validate')}
                </Button>
                <StepStatusTagLocalized status={requestLog.status} />
                {!canRunRequestLog && nonStreaming.status !== 'pass' && nonStreaming.status !== 'idle' && (
                  <Text type="secondary" style={{ fontSize: 12 }}>{t('smoke.nonStreamingPassHint')}</Text>
                )}
              </Space>

              <Divider orientation="left" plain style={{ fontSize: 13, margin: '8px 0' }}>{t('smoke.step4')}</Divider>

              <Space direction="vertical" style={{ width: '100%' }} size="small">
                <Space>
                  <Button
                    size="small"
                    type={revokedKey.enabled ? 'primary' : 'default'}
                    onClick={() => setRevokedKey(prev => ({ ...prev, enabled: !prev.enabled, status: 'idle', error: null, keyValue: null }))}
                  >
                    {revokedKey.enabled ? t('smoke.step4Enabled') : t('smoke.step4Disabled')}
                  </Button>
                  <Text type="secondary" style={{ fontSize: 12 }}>{t('smoke.step4Hint')}</Text>
                </Space>

                {revokedKey.enabled && (
                  <Space.Compact style={{ width: '100%', maxWidth: 480 }}>
                    <Input.Password
                      value={revokedKey.keyValue ?? ''}
                      onChange={(e) => setRevokedKey(prev => ({ ...prev, keyValue: e.target.value || null, status: 'idle', error: null }))}
                      placeholder={t('smoke.step4Placeholder')}
                      style={{ flex: 1 }}
                    />
                  </Space.Compact>
                )}

                {revokedKey.enabled && (
                  <Space>
                    <Button
                      onClick={handleRevokedKeyCheck}
                      loading={revokedKey.status === 'running'}
                      disabled={!revokedKey.keyValue}
                    >
                      {t('smoke.step4Verify')}
                    </Button>
                    <StepStatusTagLocalized status={revokedKey.status} />
                  </Space>
                )}

                {!revokedKey.enabled && revokedKey.status === 'skip' && (
                  <Text type="secondary" style={{ fontSize: 12 }}>{t('smoke.step4DisabledHint')}</Text>
                )}
              </Space>
            </Space>
          </Card>
        )}

        {/* Region 4: Evidence */}
        {hasEvidence && (
          <Card size="small" title={t('smoke.evidenceTitle')}>
            <Space direction="vertical" style={{ width: '100%' }} size="small">

              {nonStreaming.evidence && (
                <FramedSection title={t('smoke.evidence.gatewayResponse')} tone="success">
                  <Descriptions column={1} size="small" bordered>
                    <Descriptions.Item label={t('evidence.id')}>{nonStreaming.evidence.id}</Descriptions.Item>
                    <Descriptions.Item label={t('evidence.object')}>{nonStreaming.evidence.object}</Descriptions.Item>
                    <Descriptions.Item label={t('evidence.model')}>{nonStreaming.evidence.model}</Descriptions.Item>
                    <Descriptions.Item label={t('evidence.finishReason')}>{nonStreaming.evidence.finishReason}</Descriptions.Item>
                    <Descriptions.Item label={t('evidence.contentLength')}>{nonStreaming.evidence.contentLength} {t('evidence.chars')}</Descriptions.Item>
                    <Descriptions.Item label={t('evidence.tokens')}>
                      {t('evidence.tokenSummary', {
                        prompt: nonStreaming.evidence.promptTokens,
                        completion: nonStreaming.evidence.completionTokens,
                        total: nonStreaming.evidence.totalTokens,
                      })}
                    </Descriptions.Item>
                  </Descriptions>
                </FramedSection>
              )}

              {nonStreaming.error && (
                <FramedSection title={t('smoke.evidence.gatewayResponse')} tone="error">
                  <Descriptions column={1} size="small" bordered>
                    <Descriptions.Item label={t('evidence.httpStatus')}>{nonStreaming.error.status || 'N/A'}</Descriptions.Item>
                    <Descriptions.Item label={t('evidence.errorCode')}>
                      <Tag color="red">{nonStreaming.error.code || 'N/A'}</Tag>
                    </Descriptions.Item>
                    <Descriptions.Item label={t('evidence.message')}>{nonStreaming.error.message}</Descriptions.Item>
                  </Descriptions>
                </FramedSection>
              )}

              {streaming.evidence && (
                <FramedSection title={t('smoke.evidence.streaming')} tone={streaming.status === 'pass' ? 'success' : 'error'}>
                  <Descriptions column={1} size="small" bordered>
                    <Descriptions.Item label={t('evidence.httpStatus')}>{streaming.evidence.httpStatus}</Descriptions.Item>
                    <Descriptions.Item label={t('evidence.dataLines')}>{streaming.evidence.dataLineCount}</Descriptions.Item>
                    <Descriptions.Item label={t('evidence.chunkCount')}>{streaming.evidence.chunkCount}</Descriptions.Item>
                    <Descriptions.Item label={t('evidence.donePresent')}>
                      <Tag color={streaming.evidence.donePresent ? 'success' : 'error'}>
                        {streaming.evidence.donePresent ? t('evidence.yes') : t('evidence.no')}
                      </Tag>
                    </Descriptions.Item>
                  </Descriptions>
                </FramedSection>
              )}

              {streaming.error && (
                <FramedSection title={t('smoke.evidence.streaming')} tone="error">
                  <Descriptions column={1} size="small" bordered>
                    <Descriptions.Item label={t('evidence.httpStatus')}>{streaming.error.status || 'N/A'}</Descriptions.Item>
                    <Descriptions.Item label={t('evidence.errorCode')}>
                      <Tag color="red">{streaming.error.code || 'N/A'}</Tag>
                    </Descriptions.Item>
                    <Descriptions.Item label={t('evidence.message')}>{streaming.error.message}</Descriptions.Item>
                  </Descriptions>
                </FramedSection>
              )}

              {requestLog.listRow && (
                <FramedSection title={t('smoke.evidence.requestLog')}>
                  <Descriptions column={1} size="small" bordered>
                    <Descriptions.Item label={t('evidence.requestId')}>{requestLog.listRow.request_id}</Descriptions.Item>
                    <Descriptions.Item label={t('evidence.model')}>{requestLog.listRow.model ?? '-'}</Descriptions.Item>
                    <Descriptions.Item label={t('evidence.provider')}>{requestLog.listRow.provider_name ?? '-'}</Descriptions.Item>
                    <Descriptions.Item label={t('evidence.status')}>{requestLog.listRow.status}</Descriptions.Item>
                    <Descriptions.Item label={t('evidence.latency')}>{requestLog.listRow.latency_ms !== null ? `${requestLog.listRow.latency_ms}ms` : '-'}</Descriptions.Item>
                    <Descriptions.Item label={t('evidence.messagesCount')}>{requestLog.listRow.messages_count ?? '-'}</Descriptions.Item>
                    <Descriptions.Item label={t('evidence.hitChunkIds')}>
                      {t('evidence.hitChunkIdSummary', {
                        ids: requestLog.listRow.hit_chunk_ids.join(', '),
                        count: requestLog.listRow.hit_chunk_ids.length,
                      })}
                    </Descriptions.Item>
                  </Descriptions>
                  {requestLog.detail && (
                    <div style={{ marginTop: 8 }}>
                      <Descriptions column={1} size="small" bordered>
                        <Descriptions.Item label={t('evidence.userId')}>{requestLog.detail.user_id}</Descriptions.Item>
                        <Descriptions.Item label={t('evidence.updatedAt')}>{requestLog.detail.updated_at}</Descriptions.Item>
                        <Descriptions.Item label={t('evidence.messagesCount')}>{requestLog.detail.messages_count ?? '-'}</Descriptions.Item>
                      </Descriptions>
                    </div>
                  )}
                </FramedSection>
              )}

              {requestLog.error && (
                <FramedSection title={t('smoke.evidence.requestLog')} tone="error">
                  <Text type="danger">{requestLog.error}</Text>
                </FramedSection>
              )}

              {requestLog.hitChunks && requestLog.hitChunks.length > 0 && (
                <FramedSection title={t('smoke.evidence.hitChunks')}>
                  <Descriptions column={1} size="small" bordered>
                    {requestLog.hitChunks.map((chunk, idx) => (
                      <Descriptions.Item key={idx} label={t('evidence.chunkNumber', { number: idx + 1 })}>
                        chunk_id={chunk.chunk_id}, document_id={chunk.document_id}, kb_id={chunk.knowledge_base_id}, file={chunk.source_filename ?? '-'}, chunk_idx={chunk.chunk_index}
                      </Descriptions.Item>
                    ))}
                  </Descriptions>
                </FramedSection>
              )}

              {revokedKey.status === 'pass' && (
                <FramedSection title={t('smoke.evidence.revokedKey')} tone="success">
                  <Text>{t('smoke.step4EvidenceText')}</Text>
                </FramedSection>
              )}

              {revokedKey.error && (
                <FramedSection title={t('smoke.evidence.revokedKey')} tone="error">
                  <Descriptions column={1} size="small" bordered>
                    <Descriptions.Item label={t('evidence.httpStatus')}>{revokedKey.error.status || 'N/A'}</Descriptions.Item>
                    <Descriptions.Item label={t('evidence.errorCode')}>
                      <Tag color="red">{revokedKey.error.code || 'N/A'}</Tag>
                    </Descriptions.Item>
                    <Descriptions.Item label={t('evidence.message')}>{revokedKey.error.message}</Descriptions.Item>
                  </Descriptions>
                </FramedSection>
              )}

              <Text type="secondary" style={{ fontSize: 11, display: 'block' }}>
                {t('smoke.securityBoundary')}
              </Text>
            </Space>
          </Card>
        )}

        {/* Region 5: Failure Next Step */}
        {failureBoundary && (
          <Card size="small" title={t('smoke.failureTitle')} style={{ borderColor: '#ff4d4f' }}>
            <Space direction="vertical" size="small" style={{ width: '100%' }}>
              <Tag color={BOUNDARY_COLORS[failureBoundary]}>
                {t(BOUNDARY_LABEL_KEYS[failureBoundary])}
              </Tag>
              <Text>{t(FAILURE_HINT_MAP[failureBoundary])}</Text>
            </Space>
          </Card>
        )}
      </Space>
    </div>
  )
}
