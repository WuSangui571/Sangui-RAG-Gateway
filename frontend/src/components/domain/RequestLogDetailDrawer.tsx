import { useEffect, useMemo, useState } from 'react'
import type { ApiRequestLogDetailVO, HitChunkSummaryVO } from '../../types/request-log'
import type { AppReadinessVO } from '../../types/app'
import { getRequestLogDetail, getHitChunks } from '../../api/request-logs'
import { getAppReadiness } from '../../api/apps'
import { classifyDiagnostic } from './requestDiagnostics'
import RequestLogStatusTag from './RequestLogStatusTag'
import HitChunksPanel from './HitChunksPanel'
import RequestDiagnosticsPanel from './RequestDiagnosticsPanel'
import OutputPreviewModal from './OutputPreviewModal'
import { Drawer, Descriptions, Typography, Button, Space, Alert, Spin, Tag } from 'antd'
import { useI18n } from '../../app/i18n'

const { Text } = Typography

interface RequestLogDetailDrawerProps {
  open: boolean
  appId: number
  requestId: string | null
  onClose: () => void
}

function formatMs(ms: number | null): string {
  if (ms === null || ms === undefined) return '-'
  return `${ms} ms`
}

function formatTokens(n: number | null): string {
  if (n === null || n === undefined) return '-'
  return String(n)
}

export default function RequestLogDetailDrawer({
  open,
  appId,
  requestId,
  onClose,
}: RequestLogDetailDrawerProps) {
  const { t } = useI18n()
  const [detail, setDetail] = useState<ApiRequestLogDetailVO | null>(null)
  const [chunks, setChunks] = useState<HitChunkSummaryVO[]>([])
  const [loading, setLoading] = useState(false)
  const [detailError, setDetailError] = useState<string | null>(null)
  const [chunksLoading, setChunksLoading] = useState(false)
  const [chunksError, setChunksError] = useState<string | null>(null)

  const [readiness, setReadiness] = useState<AppReadinessVO | null>(null)
  const [readinessLoading, setReadinessLoading] = useState(false)
  const [readinessError, setReadinessError] = useState<string | null>(null)

  const [previewModalOpen, setPreviewModalOpen] = useState(false)

  useEffect(() => {
    if (!open || !requestId) {
      setDetail(null)
      setChunks([])
      setDetailError(null)
      setChunksError(null)
      setReadiness(null)
      setReadinessError(null)
      return
    }

    let cancelled = false

    setLoading(true)
    setDetailError(null)
    setChunksError(null)
    setChunks([])
    setReadiness(null)
    setReadinessError(null)
    setReadinessLoading(true)

    getRequestLogDetail(appId, requestId)
      .then((res) => {
        if (cancelled) return
        if (res.code !== 'OK') {
          setDetailError(res.message)
        } else {
          setDetail(res.data)
        }
      })
      .catch((e: Error) => {
        if (cancelled) return
        setDetailError(e.message)
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })

    getAppReadiness(appId)
      .then((res) => {
        if (cancelled) return
        if (res.code !== 'OK') {
          setReadinessError(res.message)
        } else {
          setReadiness(res.data)
        }
      })
      .catch((e: Error) => {
        if (cancelled) return
        setReadinessError(e.message)
      })
      .finally(() => {
        if (!cancelled) setReadinessLoading(false)
      })

    loadHitChunks(appId, requestId, () => cancelled, setChunks, setChunksLoading, setChunksError)

    return () => {
      cancelled = true
    }
  }, [open, appId, requestId])

  function handleRetryHitChunks() {
    if (!requestId) return
    setChunksError(null)
    setChunksLoading(true)
    loadHitChunks(appId, requestId, () => false, setChunks, setChunksLoading, setChunksError)
  }

  const diagnostic = useMemo(() => {
    if (!detail) return null
    return classifyDiagnostic(
      {
        status: detail.status,
        error_code: detail.error_code,
        hit_chunk_ids: detail.hit_chunk_ids,
      },
      readiness,
    )
  }, [detail, readiness])

  return (
    <Drawer
      title={t('rl-drawer.title')}
      open={open}
      onClose={onClose}
      width={640}
      destroyOnClose
    >
      {loading && !detailError && (
        <Spin tip={t('rl-drawer.loading')} style={{ display: 'block', textAlign: 'center', padding: 48 }}>
          <div style={{ height: 80 }} />
        </Spin>
      )}

      {detailError && (
        <Alert
          type="error"
          message={t('rl-drawer.loadError')}
          description={detailError}
          action={
            <Space direction="vertical">
              <Button size="small" onClick={onClose} type="primary">
                {t('rl-drawer.close')}
              </Button>
            </Space>
          }
          style={{ marginBottom: 16 }}
        />
      )}

      {detail && (
        <>
          <Descriptions column={2} size="small" bordered style={{ marginBottom: 16 }}>
            <Descriptions.Item label={t('rl-drawer.requestId')}>
              <Text copyable={{ text: detail.request_id }} style={{ fontSize: 12 }}>
                {detail.request_id}
              </Text>
            </Descriptions.Item>
            <Descriptions.Item label={t('rl-drawer.status')}>
              <RequestLogStatusTag status={detail.status} />
            </Descriptions.Item>
            <Descriptions.Item label={t('rl-drawer.errorCode')}>
              {detail.error_code ? <Tag>{detail.error_code}</Tag> : '-'}
            </Descriptions.Item>
            <Descriptions.Item label={t('rl-drawer.createdAt')}>{detail.created_at}</Descriptions.Item>
            <Descriptions.Item label={t('rl-drawer.model')}>{detail.model ?? '-'}</Descriptions.Item>
            <Descriptions.Item label={t('rl-drawer.provider')}>{detail.provider_name ?? '-'}</Descriptions.Item>
            <Descriptions.Item label={t('rl-drawer.latency')}>{formatMs(detail.latency_ms)}</Descriptions.Item>
            <Descriptions.Item label={t('rl-drawer.upstreamLatency')}>{formatMs(detail.upstream_latency_ms)}</Descriptions.Item>
            <Descriptions.Item label={t('rl-drawer.messagesCount')}>{detail.messages_count ?? '-'}</Descriptions.Item>
            <Descriptions.Item label={t('rl-drawer.userId')}>{detail.user_id}</Descriptions.Item>
          </Descriptions>

          <Descriptions column={1} size="small" bordered style={{ marginBottom: 16 }}>
            <Descriptions.Item label={t('rl-drawer.promptTokens')}>
              {formatTokens(detail.usage?.prompt_tokens ?? null)}
            </Descriptions.Item>
            <Descriptions.Item label={t('rl-drawer.completionTokens')}>
              {formatTokens(detail.usage?.completion_tokens ?? null)}
            </Descriptions.Item>
            <Descriptions.Item label={t('rl-drawer.totalTokens')}>
              {formatTokens(detail.usage?.total_tokens ?? null)}
            </Descriptions.Item>
          </Descriptions>

          <Descriptions column={1} size="small" bordered style={{ marginBottom: 16 }}>
            <Descriptions.Item label={t('rl-drawer.questionSummary')}>
              {detail.question_summary ? (
                <Text ellipsis={{ tooltip: detail.question_summary }} style={{ maxWidth: 560, display: 'block' }}>
                  {detail.question_summary}
                </Text>
              ) : (
                '-'
              )}
            </Descriptions.Item>
            <Descriptions.Item label={t('rl-drawer.hitChunkIds')}>
              {detail.hit_chunk_ids.length > 0 ? detail.hit_chunk_ids.join(', ') : '-'}
            </Descriptions.Item>
          </Descriptions>

          <Descriptions column={2} size="small" bordered style={{ marginBottom: 16 }}>
            <Descriptions.Item label={t('rl-drawer.outputCaptureStatus')}>
              <Tag>{detail.output_capture_status}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label={t('rl-drawer.completionLength')}>
              {detail.completion_length !== null && detail.completion_length !== undefined
                ? String(detail.completion_length) : '-'}
            </Descriptions.Item>
            <Descriptions.Item label={t('rl-drawer.outputPreviewAvailable')}>
              {detail.output_preview_available ? t('rl-drawer.yes') : t('rl-drawer.no')}
            </Descriptions.Item>
            <Descriptions.Item label={t('rl-drawer.outputPreviewTruncated')}>
              {detail.output_preview_truncated ? t('rl-drawer.yes') : t('rl-drawer.no')}
            </Descriptions.Item>
            <Descriptions.Item label={t('rl-drawer.outputRedacted')}>
              {detail.output_redacted ? t('rl-drawer.yes') : t('rl-drawer.no')}
            </Descriptions.Item>
            <Descriptions.Item label={t('rl-drawer.outputRetentionExpiresAt')}>
              {detail.output_retention_expires_at ?? '-'}
            </Descriptions.Item>
          </Descriptions>

          {detail.output_preview_available && (
            <div style={{ marginBottom: 16 }}>
              <Button
                type="primary"
                onClick={() => setPreviewModalOpen(true)}
              >
                {t('rl-drawer.viewOutputPreview')}
              </Button>
            </div>
          )}

          <Typography.Title level={5}>{t('rl-drawer.hitChunks')}</Typography.Title>
          <HitChunksPanel
            chunks={chunks}
            loading={chunksLoading}
            error={chunksError}
            onRetry={handleRetryHitChunks}
          />

          <RequestDiagnosticsPanel
            diagnostic={diagnostic}
            readiness={readiness}
            readinessLoading={readinessLoading}
            readinessError={readinessError}
          />

          {appId && requestId && (
            <OutputPreviewModal
              open={previewModalOpen}
              appId={appId}
              requestId={requestId}
              onClose={() => setPreviewModalOpen(false)}
            />
          )}
        </>
      )}
    </Drawer>
  )
}

async function loadHitChunks(
  appId: number,
  requestId: string,
  isCancelled: () => boolean,
  setChunks: (c: HitChunkSummaryVO[]) => void,
  setChunksLoading: (v: boolean) => void,
  setChunksError: (e: string | null) => void,
) {
  setChunksLoading(true)
  try {
    const res = await getHitChunks(appId, requestId)
    if (isCancelled()) return
    if (res.code !== 'OK') {
      setChunksError(res.message)
    } else {
      setChunks(res.data)
    }
  } catch (e: unknown) {
    if (isCancelled()) return
    setChunksError(e instanceof Error ? e.message : 'Unknown error')
  } finally {
    if (!isCancelled()) setChunksLoading(false)
  }
}
