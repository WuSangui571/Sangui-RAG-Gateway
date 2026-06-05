import { useCallback, useEffect, useState } from 'react'
import {
  Button, Select, Space, Typography, Card, Spin, Descriptions, Tag, Input, Divider,
} from 'antd'
import type { AppVO, AppReadinessVO, ReadinessStatus } from '../../types/app'
import type { ApiKeyVO } from '../../types/api-key'
import type { SmokeChatCompletionResponse, SmokeStreamingEvidence } from '../../types/openai'
import type { ApiRequestLogVO, ApiRequestLogDetailVO, HitChunkSummaryVO } from '../../types/request-log'
import { listApps, getAppReadiness } from '../../api/apps'
import { listApiKeys } from '../../api/api-keys'
import { ApiError } from '../../api/http'
import { smokeChatCompletions, smokeStreamingChatCompletions, SmokeApiError } from '../../api/openai'
import { listRequestLogs, getRequestLogDetail, getHitChunks } from '../../api/request-logs'
import { useShell } from '../../components/layout/AdminShell'
import { useI18n } from '../../app/i18n'
import type { I18nKey } from '../../app/i18n/dict'

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
  const upper = status.toUpperCase()
  if (status === 'READY') return <Tag color="success">{t('status.readiness.READY')}</Tag>
  if (status === 'MISSING') return <Tag color="error">{t('status.readiness.MISSING')}</Tag>
  if (status === 'DISABLED') return <Tag color="warning">{t('status.readiness.DISABLED')}</Tag>
  if (status === 'NOT_READY') return <Tag color="default">{t('status.readiness.NOT_READY')}</Tag>
  if (!status) return <Tag color="default">{t('status.readiness.UNKNOWN')}</Tag>
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

  const fetchApps = useCallback(async () => {
    if (adminUserId === null) return
    try {
      const res = await listApps(undefined, adminUserId)
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
      const res = await listApiKeys(activeAppId, adminUserId)
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
      const res = await getAppReadiness(activeAppId, adminUserId)
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

  async function handleNonStreamingSmoke() {
    if (selectedKeyValue === null) return
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
      const listRes = await listRequestLogs(activeAppId, { page: 1, page_size: 5, status: 'success' }, adminUserId)
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

      const detailRes = await getRequestLogDetail(activeAppId, matchedLog.request_id, adminUserId)
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

      const chunksRes = await getHitChunks(activeAppId, matchedLog.request_id, adminUserId)
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

  const canRun = activeAppId !== null && selectedKeyValue !== null && userInput.trim().length > 0
  const canRunRequestLog = activeAppId !== null && adminUserId !== null && nonStreaming.status === 'pass'

  return (
    <div>
      <Space direction="vertical" style={{ width: '100%' }} size="middle">
        {loadError && (
          <Text type="danger">{loadError}</Text>
        )}

        <Space>
          <div>
            <Text type="secondary">{t('smoke.app')}</Text>
            <Select
              value={activeAppId}
              onChange={(v) => handleAppSelect(v)}
              placeholder={t('smoke.appPlaceholder')}
              style={{ width: 240, marginLeft: 8 }}
              options={apps.map(a => ({ value: a.id, label: `#${a.id} ${a.name}` }))}
            />
          </div>
          <div>
            <Text type="secondary">{t('smoke.activeKey')}</Text>
            <Select
              value={selectedKeyPrefix}
              onChange={(v) => setSelectedKeyPrefix(v)}
              placeholder={t('smoke.keyPlaceholder')}
              style={{ width: 260, marginLeft: 8 }}
              options={keys.map(k => ({
                value: `${k.name} (${k.key_prefix})`,
                label: `${k.name} (${k.key_prefix})`,
              }))}
            />
          </div>
        </Space>

        {activeAppId !== null && (
          <Card size="small" title={
            <Space>
              <span>{t('smoke.preflightTitle')}</span>
              {readinessLoading && <Spin size="small" />}
              {readiness && (
                <ReadinessStatusTagLocalized status={readiness.overall_status} />
              )}
            </Space>
          }>
            {readinessLoading && !readiness && (
              <Spin tip={t('smoke.preflightLoading')} style={{ display: 'block', margin: '16px 0' }}>
                <div style={{ height: 40 }} />
              </Spin>
            )}
            {readinessError && (
              <Text type="danger">{t('smoke.preflightError')} {readinessError}</Text>
            )}
            {readiness && readiness.checks.length > 0 && (
              <Descriptions column={1} size="small" bordered>
                {readiness.checks.map(check => (
                  <Descriptions.Item key={check.key} label={
                    <Space size={4}>
                      <span>{check.label}</span>
                      <ReadinessStatusTagLocalized status={check.status} />
                    </Space>
                  }>
                    <Space direction="vertical" size={2}>
                      <Text>{check.message}</Text>
                      {check.metadata && Object.keys(check.metadata).length > 0 && (
                        <Text type="secondary" style={{ fontSize: 12 }}>
                          {Object.entries(check.metadata)
                            .map(([k, v]) => `${k}: ${v}`)
                            .join(', ')}
                        </Text>
                      )}
                    </Space>
                  </Descriptions.Item>
                ))}
              </Descriptions>
            )}
            {readiness && readiness.overall_status !== 'READY' && (
              <Text type="warning" style={{ display: 'block', marginTop: 8 }}>
                {t('smoke.preflightWarning')}
              </Text>
            )}
            {readiness && readiness.overall_status === 'READY' && (
              <Text type="success" style={{ display: 'block', marginTop: 8 }}>
                {t('smoke.preflightReady')}
              </Text>
            )}
          </Card>
        )}

        <Card size="small" title={t('smoke.keyCardTitle')}>
          <Space.Compact style={{ width: '100%' }}>
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
          <Text type="secondary" style={{ fontSize: 12 }}>
            {t('smoke.keyHint')}
          </Text>
        </Card>

        <Card size="small" title={t('smoke.userMessageTitle')}>
          <Input.TextArea
            value={userInput}
            onChange={(e) => handleUserInputChange(e.target.value)}
            rows={2}
            placeholder={t('smoke.userMessagePlaceholder')}
          />
        </Card>

        <Divider orientation="left" plain>{t('smoke.step1')}</Divider>

        <Space>
          <Button
            type="primary"
            onClick={handleNonStreamingSmoke}
            loading={nonStreaming.status === 'running'}
            disabled={!canRun}
          >
            {t('smoke.step1Send')}
          </Button>
          <StepStatusTagLocalized status={nonStreaming.status} />
        </Space>

        {nonStreaming.status === 'running' && (
          <Spin tip={t('smoke.step1Waiting')} style={{ display: 'block', margin: '16px 0' }}>
            <div style={{ height: 40 }} />
          </Spin>
        )}

        {nonStreaming.evidence && (
          <Card size="small" title={t('smoke.step1Evidence')} style={{ borderColor: '#52c41a' }}>
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
          </Card>
        )}

        {nonStreaming.error && (
          <Card size="small" title={t('smoke.step1Error')} style={{ borderColor: '#ff4d4f' }}>
            <Descriptions column={1} size="small" bordered>
              <Descriptions.Item label={t('evidence.httpStatus')}>{nonStreaming.error.status || 'N/A'}</Descriptions.Item>
              <Descriptions.Item label={t('evidence.errorCode')}>
                <Tag color="red">{nonStreaming.error.code || 'N/A'}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label={t('evidence.message')}>{nonStreaming.error.message}</Descriptions.Item>
            </Descriptions>
          </Card>
        )}

        <Divider orientation="left" plain>{t('smoke.step2')}</Divider>

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

        {streaming.status === 'running' && (
          <Spin tip={t('smoke.step2Waiting')} style={{ display: 'block', margin: '16px 0' }}>
            <div style={{ height: 40 }} />
          </Spin>
        )}

        {streaming.evidence && (
          <Card size="small" title={t('smoke.step2Evidence')} style={{ borderColor: streaming.status === 'pass' ? '#52c41a' : '#ff4d4f' }}>
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
          </Card>
        )}

        {streaming.error && (
          <Card size="small" title={t('smoke.step2Error')} style={{ borderColor: '#ff4d4f' }}>
            <Descriptions column={1} size="small" bordered>
              <Descriptions.Item label={t('evidence.httpStatus')}>{streaming.error.status || 'N/A'}</Descriptions.Item>
              <Descriptions.Item label={t('evidence.errorCode')}>
                <Tag color="red">{streaming.error.code || 'N/A'}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label={t('evidence.message')}>{streaming.error.message}</Descriptions.Item>
            </Descriptions>
          </Card>
        )}

        <Divider orientation="left" plain>{t('smoke.step3')}</Divider>

        <Space>
          <Button
            onClick={handleRequestLogValidation}
            loading={requestLog.status === 'running'}
            disabled={!canRunRequestLog}
          >
            {t('smoke.step3Validate')}
          </Button>
          <StepStatusTagLocalized status={requestLog.status} />
          {!canRunRequestLog && (
            <Text type="secondary">{t('smoke.step3Hint')}</Text>
          )}
        </Space>

        {requestLog.status === 'running' && (
          <Spin tip={t('smoke.step3Validating', { step: requestLog.subStep })} style={{ display: 'block', margin: '16px 0' }}>
            <div style={{ height: 40 }} />
          </Spin>
        )}

        {requestLog.listRow && (
          <Card size="small" title={t('smoke.step3ListEvidence')} style={{ borderColor: requestLog.status === 'pass' ? '#52c41a' : requestLog.status === 'fail' ? '#ff4d4f' : undefined }}>
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
          </Card>
        )}

        {requestLog.detail && (
          <Card size="small" title={t('smoke.step3DetailEvidence')}>
            <Descriptions column={1} size="small" bordered>
              <Descriptions.Item label={t('evidence.requestId')}>{requestLog.detail.request_id}</Descriptions.Item>
              <Descriptions.Item label={t('evidence.userId')}>{requestLog.detail.user_id}</Descriptions.Item>
              <Descriptions.Item label={t('evidence.updatedAt')}>{requestLog.detail.updated_at}</Descriptions.Item>
              <Descriptions.Item label={t('evidence.messagesCount')}>{requestLog.detail.messages_count ?? '-'}</Descriptions.Item>
            </Descriptions>
          </Card>
        )}

        {requestLog.hitChunks && requestLog.hitChunks.length > 0 && (
          <Card size="small" title={t('smoke.step3ChunkTitle', { count: requestLog.hitChunks.length })}>
            <Descriptions column={1} size="small" bordered>
              {requestLog.hitChunks.map((chunk, idx) => (
                <Descriptions.Item key={idx} label={t('evidence.chunkNumber', { number: idx + 1 })}>
                  chunk_id={chunk.chunk_id}, document_id={chunk.document_id}, kb_id={chunk.knowledge_base_id}, file={chunk.source_filename ?? '-'}, chunk_idx={chunk.chunk_index}
                </Descriptions.Item>
              ))}
            </Descriptions>
          </Card>
        )}

        {requestLog.error && (
          <Card size="small" title={t('smoke.step3Error')} style={{ borderColor: '#ff4d4f' }}>
            <Text type="danger">{requestLog.error}</Text>
          </Card>
        )}

        <Divider orientation="left" plain>{t('smoke.step4')}</Divider>

        <Space direction="vertical" style={{ width: '100%' }}>
          <Space>
            <Button
              size="small"
              type={revokedKey.enabled ? 'primary' : 'default'}
              onClick={() => setRevokedKey(prev => ({ ...prev, enabled: !prev.enabled, status: 'idle', error: null, keyValue: null }))}
            >
              {revokedKey.enabled ? t('smoke.step4Enabled') : t('smoke.step4Disabled')}
            </Button>
            <Text type="secondary">{t('smoke.step4Hint')}</Text>
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
            <Text type="secondary">{t('smoke.step4DisabledHint')}</Text>
          )}
        </Space>

        {revokedKey.error && (
          <Card size="small" title={t('smoke.step4Error')} style={{ borderColor: '#ff4d4f' }}>
            <Descriptions column={1} size="small" bordered>
              <Descriptions.Item label={t('evidence.httpStatus')}>{revokedKey.error.status || 'N/A'}</Descriptions.Item>
              <Descriptions.Item label={t('evidence.errorCode')}>
                <Tag color="red">{revokedKey.error.code || 'N/A'}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label={t('evidence.message')}>{revokedKey.error.message}</Descriptions.Item>
            </Descriptions>
          </Card>
        )}

        {revokedKey.status === 'pass' && (
          <Card size="small" title={t('smoke.step4Evidence')} style={{ borderColor: '#52c41a' }}>
            <Text>{t('smoke.step4EvidenceText')}</Text>
          </Card>
        )}
      </Space>
    </div>
  )
}
