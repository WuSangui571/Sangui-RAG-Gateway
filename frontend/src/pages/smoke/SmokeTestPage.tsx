import { useCallback, useEffect, useState } from 'react'
import {
  Button, Select, Space, Typography, Card, Spin, Descriptions, Tag, Input, Divider,
} from 'antd'
import type { AppVO } from '../../types/app'
import type { ApiKeyVO } from '../../types/api-key'
import type { SmokeChatCompletionResponse, SmokeStreamingEvidence } from '../../types/openai'
import type { ApiRequestLogVO, ApiRequestLogDetailVO, HitChunkSummaryVO } from '../../types/request-log'
import { listApps } from '../../api/apps'
import { listApiKeys } from '../../api/api-keys'
import { ApiError } from '../../api/http'
import { smokeChatCompletions, smokeStreamingChatCompletions, SmokeApiError } from '../../api/openai'
import { listRequestLogs, getRequestLogDetail, getHitChunks } from '../../api/request-logs'
import { useShell } from '../../components/layout/AdminShell'

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

function StepStatusTag({ status }: { status: StepStatus }) {
  if (status === 'idle') return <Tag>IDLE</Tag>
  if (status === 'running') return <Tag color="processing">RUNNING</Tag>
  if (status === 'pass') return <Tag color="success">PASS</Tag>
  if (status === 'fail') return <Tag color="error">FAIL</Tag>
  if (status === 'skip') return <Tag color="default">SKIP</Tag>
  return null
}

export default function SmokeTestPage() {
  const { adminUserId, selectedAppId, setSelectedAppId } = useShell()

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
      setLoadError(e instanceof ApiError ? e.message : (e instanceof Error ? e.message : 'Failed to load apps'))
    }
  }, [adminUserId])

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
      setLoadError(e instanceof ApiError ? e.message : (e instanceof Error ? e.message : 'Failed to load API keys'))
    }
  }, [activeAppId, adminUserId])

  useEffect(() => {
    setSelectedKeyPrefix(null)
    setSelectedKeyValue(null)
    resetAllSteps()
    fetchKeys()
  }, [activeAppId, fetchKeys])

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
          error: { status: 0, message: e instanceof Error ? e.message : 'Unknown error', code: null },
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
          error: { status: 0, message: e instanceof Error ? e.message : 'Unknown error', code: null },
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
          error: { status: 0, message: e instanceof Error ? e.message : 'Unknown error', code: null },
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
            <Text type="secondary">App:</Text>
            <Select
              value={activeAppId}
              onChange={(v) => handleAppSelect(v)}
              placeholder="Select app"
              style={{ width: 240, marginLeft: 8 }}
              options={apps.map(a => ({ value: a.id, label: `#${a.id} ${a.name}` }))}
            />
          </div>
          <div>
            <Text type="secondary">Active Key:</Text>
            <Select
              value={selectedKeyPrefix}
              onChange={(v) => setSelectedKeyPrefix(v)}
              placeholder="Select active key (for reference)"
              style={{ width: 260, marginLeft: 8 }}
              options={keys.map(k => ({
                value: `${k.name} (${k.key_prefix})`,
                label: `${k.name} (${k.key_prefix})`,
              }))}
            />
          </div>
        </Space>

        <Card size="small" title="Temporary API Key (not stored)">
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
              Clear
            </Button>
          </Space.Compact>
          <Text type="secondary" style={{ fontSize: 12 }}>
            Paste the full plaintext key from the API Keys creation dialog. This value is only held in memory for this session and is cleared when you switch apps, clear manually, or reload the page.
          </Text>
        </Card>

        <Card size="small" title="User Message">
          <Input.TextArea
            value={userInput}
            onChange={(e) => handleUserInputChange(e.target.value)}
            rows={2}
            placeholder="Enter the user message for the chat completion..."
          />
        </Card>

        <Divider orientation="left" plain>Step 1: Non-Streaming Smoke</Divider>

        <Space>
          <Button
            type="primary"
            onClick={handleNonStreamingSmoke}
            loading={nonStreaming.status === 'running'}
            disabled={!canRun}
          >
            Send Non-Streaming Request
          </Button>
          <StepStatusTag status={nonStreaming.status} />
        </Space>

        {nonStreaming.status === 'running' && (
          <Spin tip="Waiting for gateway response..." style={{ display: 'block', margin: '16px 0' }}>
            <div style={{ height: 40 }} />
          </Spin>
        )}

        {nonStreaming.evidence && (
          <Card size="small" title="Non-Streaming Evidence" style={{ borderColor: '#52c41a' }}>
            <Descriptions column={1} size="small" bordered>
              <Descriptions.Item label="ID">{nonStreaming.evidence.id}</Descriptions.Item>
              <Descriptions.Item label="Object">{nonStreaming.evidence.object}</Descriptions.Item>
              <Descriptions.Item label="Model">{nonStreaming.evidence.model}</Descriptions.Item>
              <Descriptions.Item label="Finish Reason">{nonStreaming.evidence.finishReason}</Descriptions.Item>
              <Descriptions.Item label="Content Length">{nonStreaming.evidence.contentLength} chars</Descriptions.Item>
              <Descriptions.Item label="Tokens">
                {nonStreaming.evidence.promptTokens} prompt / {nonStreaming.evidence.completionTokens} completion / {nonStreaming.evidence.totalTokens} total
              </Descriptions.Item>
            </Descriptions>
          </Card>
        )}

        {nonStreaming.error && (
          <Card size="small" title="Non-Streaming Error" style={{ borderColor: '#ff4d4f' }}>
            <Descriptions column={1} size="small" bordered>
              <Descriptions.Item label="HTTP Status">{nonStreaming.error.status || 'N/A'}</Descriptions.Item>
              <Descriptions.Item label="Error Code">
                <Tag color="red">{nonStreaming.error.code || 'N/A'}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="Message">{nonStreaming.error.message}</Descriptions.Item>
            </Descriptions>
          </Card>
        )}

        <Divider orientation="left" plain>Step 2: Streaming Smoke</Divider>

        <Space>
          <Button
            onClick={handleStreamingSmoke}
            loading={streaming.status === 'running'}
            disabled={!canRun}
          >
            Send Streaming Request
          </Button>
          <StepStatusTag status={streaming.status} />
        </Space>

        {streaming.status === 'running' && (
          <Spin tip="Reading SSE stream..." style={{ display: 'block', margin: '16px 0' }}>
            <div style={{ height: 40 }} />
          </Spin>
        )}

        {streaming.evidence && (
          <Card size="small" title="Streaming Evidence" style={{ borderColor: streaming.status === 'pass' ? '#52c41a' : '#ff4d4f' }}>
            <Descriptions column={1} size="small" bordered>
              <Descriptions.Item label="HTTP Status">{streaming.evidence.httpStatus}</Descriptions.Item>
              <Descriptions.Item label="Data Lines">{streaming.evidence.dataLineCount}</Descriptions.Item>
              <Descriptions.Item label="Chunk Count">{streaming.evidence.chunkCount}</Descriptions.Item>
              <Descriptions.Item label="[DONE] Present">
                <Tag color={streaming.evidence.donePresent ? 'success' : 'error'}>
                  {streaming.evidence.donePresent ? 'YES' : 'NO'}
                </Tag>
              </Descriptions.Item>
            </Descriptions>
          </Card>
        )}

        {streaming.error && (
          <Card size="small" title="Streaming Error" style={{ borderColor: '#ff4d4f' }}>
            <Descriptions column={1} size="small" bordered>
              <Descriptions.Item label="HTTP Status">{streaming.error.status || 'N/A'}</Descriptions.Item>
              <Descriptions.Item label="Error Code">
                <Tag color="red">{streaming.error.code || 'N/A'}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="Message">{streaming.error.message}</Descriptions.Item>
            </Descriptions>
          </Card>
        )}

        <Divider orientation="left" plain>Step 3: Request-Log Validation</Divider>

        <Space>
          <Button
            onClick={handleRequestLogValidation}
            loading={requestLog.status === 'running'}
            disabled={!canRunRequestLog}
          >
            Validate Request Logs
          </Button>
          <StepStatusTag status={requestLog.status} />
          {!canRunRequestLog && (
            <Text type="secondary">Requires app selection, admin user ID, and a passing non-streaming smoke run</Text>
          )}
        </Space>

        {requestLog.status === 'running' && (
          <Spin tip={`Validating request logs (${requestLog.subStep})...`} style={{ display: 'block', margin: '16px 0' }}>
            <div style={{ height: 40 }} />
          </Spin>
        )}

        {requestLog.listRow && (
          <Card size="small" title="Request-Log List Evidence" style={{ borderColor: requestLog.status === 'pass' ? '#52c41a' : requestLog.status === 'fail' ? '#ff4d4f' : undefined }}>
            <Descriptions column={1} size="small" bordered>
              <Descriptions.Item label="Request ID">{requestLog.listRow.request_id}</Descriptions.Item>
              <Descriptions.Item label="Model">{requestLog.listRow.model ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="Provider">{requestLog.listRow.provider_name ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="Status">{requestLog.listRow.status}</Descriptions.Item>
              <Descriptions.Item label="Latency">{requestLog.listRow.latency_ms !== null ? `${requestLog.listRow.latency_ms}ms` : '-'}</Descriptions.Item>
              <Descriptions.Item label="Messages Count">{requestLog.listRow.messages_count ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="Hit Chunk IDs">
                [{requestLog.listRow.hit_chunk_ids.join(', ')}] (count={requestLog.listRow.hit_chunk_ids.length})
              </Descriptions.Item>
            </Descriptions>
          </Card>
        )}

        {requestLog.detail && (
          <Card size="small" title="Request-Log Detail Evidence">
            <Descriptions column={1} size="small" bordered>
              <Descriptions.Item label="Request ID">{requestLog.detail.request_id}</Descriptions.Item>
              <Descriptions.Item label="User ID">{requestLog.detail.user_id}</Descriptions.Item>
              <Descriptions.Item label="Updated At">{requestLog.detail.updated_at}</Descriptions.Item>
              <Descriptions.Item label="Messages Count">{requestLog.detail.messages_count ?? '-'}</Descriptions.Item>
            </Descriptions>
          </Card>
        )}

        {requestLog.hitChunks && requestLog.hitChunks.length > 0 && (
          <Card size="small" title={`Hit-Chunk Metadata (count=${requestLog.hitChunks.length})`}>
            <Descriptions column={1} size="small" bordered>
              {requestLog.hitChunks.map((chunk, idx) => (
                <Descriptions.Item key={idx} label={`Chunk #${idx + 1}`}>
                  chunk_id={chunk.chunk_id}, document_id={chunk.document_id}, kb_id={chunk.knowledge_base_id}, file={chunk.source_filename ?? '-'}, chunk_idx={chunk.chunk_index}
                </Descriptions.Item>
              ))}
            </Descriptions>
          </Card>
        )}

        {requestLog.error && (
          <Card size="small" title="Request-Log Error" style={{ borderColor: '#ff4d4f' }}>
            <Text type="danger">{requestLog.error}</Text>
          </Card>
        )}

        <Divider orientation="left" plain>Step 4: Revoked-Key Auth (Optional)</Divider>

        <Space direction="vertical" style={{ width: '100%' }}>
          <Space>
            <Button
              size="small"
              type={revokedKey.enabled ? 'primary' : 'default'}
              onClick={() => setRevokedKey(prev => ({ ...prev, enabled: !prev.enabled, status: 'idle', error: null, keyValue: null }))}
            >
              {revokedKey.enabled ? 'Enabled' : 'Disabled'}
            </Button>
            <Text type="secondary">Enable to verify revoked key returns 401 invalid_api_key</Text>
          </Space>

          {revokedKey.enabled && (
            <Space.Compact style={{ width: '100%', maxWidth: 480 }}>
              <Input.Password
                value={revokedKey.keyValue ?? ''}
                onChange={(e) => setRevokedKey(prev => ({ ...prev, keyValue: e.target.value || null, status: 'idle', error: null }))}
                placeholder="Paste revoked sk-sangui-... key"
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
                Verify Revoked Key
              </Button>
              <StepStatusTag status={revokedKey.status} />
            </Space>
          )}

          {!revokedKey.enabled && revokedKey.status === 'skip' && (
            <Text type="secondary">Revoked-key check disabled.</Text>
          )}
        </Space>

        {revokedKey.error && (
          <Card size="small" title="Revoked-Key Error" style={{ borderColor: '#ff4d4f' }}>
            <Descriptions column={1} size="small" bordered>
              <Descriptions.Item label="HTTP Status">{revokedKey.error.status || 'N/A'}</Descriptions.Item>
              <Descriptions.Item label="Error Code">
                <Tag color="red">{revokedKey.error.code || 'N/A'}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="Message">{revokedKey.error.message}</Descriptions.Item>
            </Descriptions>
          </Card>
        )}

        {revokedKey.status === 'pass' && (
          <Card size="small" title="Revoked-Key Evidence" style={{ borderColor: '#52c41a' }}>
            <Text>HTTP 401, error.code=invalid_api_key</Text>
          </Card>
        )}
      </Space>
    </div>
  )
}
