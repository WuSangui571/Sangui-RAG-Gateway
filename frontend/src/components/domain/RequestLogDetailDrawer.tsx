import { useEffect, useState } from 'react'
import type { ApiRequestLogDetailVO, HitChunkSummaryVO } from '../../types/request-log'
import { getRequestLogDetail, getHitChunks } from '../../api/request-logs'
import RequestLogStatusTag from './RequestLogStatusTag'
import HitChunksPanel from './HitChunksPanel'
import { Drawer, Descriptions, Typography, Button, Space, Alert, Spin, Tag } from 'antd'

const { Text } = Typography

interface RequestLogDetailDrawerProps {
  open: boolean
  appId: number
  requestId: string | null
  adminUserId: number
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
  adminUserId,
  onClose,
}: RequestLogDetailDrawerProps) {
  const [detail, setDetail] = useState<ApiRequestLogDetailVO | null>(null)
  const [chunks, setChunks] = useState<HitChunkSummaryVO[]>([])
  const [loading, setLoading] = useState(false)
  const [detailError, setDetailError] = useState<string | null>(null)
  const [chunksLoading, setChunksLoading] = useState(false)
  const [chunksError, setChunksError] = useState<string | null>(null)

  useEffect(() => {
    if (!open || !requestId) {
      setDetail(null)
      setChunks([])
      setDetailError(null)
      setChunksError(null)
      return
    }

    let cancelled = false

    setLoading(true)
    setDetailError(null)
    setChunksError(null)
    setChunks([])

    getRequestLogDetail(appId, requestId, adminUserId)
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

    loadHitChunks(appId, requestId, adminUserId, () => cancelled, setChunks, setChunksLoading, setChunksError)

    return () => {
      cancelled = true
    }
  }, [open, appId, requestId, adminUserId])

  function handleRetryHitChunks() {
    if (!requestId) return
    setChunksError(null)
    setChunksLoading(true)
    loadHitChunks(appId, requestId, adminUserId, () => false, setChunks, setChunksLoading, setChunksError)
  }

  return (
    <Drawer
      title="Request Log Detail"
      open={open}
      onClose={onClose}
      width={640}
      destroyOnClose
    >
      {loading && !detailError && (
        <Spin tip="Loading..." style={{ display: 'block', textAlign: 'center', padding: 48 }}>
          <div style={{ height: 80 }} />
        </Spin>
      )}

      {detailError && (
        <Alert
          type="error"
          message="Failed to load request log detail"
          description={detailError}
          action={
            <Space direction="vertical">
              <Button size="small" onClick={onClose} type="primary">
                Close
              </Button>
            </Space>
          }
          style={{ marginBottom: 16 }}
        />
      )}

      {detail && (
        <>
          <Descriptions column={2} size="small" bordered style={{ marginBottom: 16 }}>
            <Descriptions.Item label="Request ID">
              <Text copyable={{ text: detail.request_id }} style={{ fontSize: 12 }}>
                {detail.request_id}
              </Text>
            </Descriptions.Item>
            <Descriptions.Item label="Status">
              <RequestLogStatusTag status={detail.status} />
            </Descriptions.Item>
            <Descriptions.Item label="Error Code">
              {detail.error_code ? <Tag>{detail.error_code}</Tag> : '-'}
            </Descriptions.Item>
            <Descriptions.Item label="Created At">{detail.created_at}</Descriptions.Item>
            <Descriptions.Item label="Model">{detail.model ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="Provider">{detail.provider_name ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="Latency">{formatMs(detail.latency_ms)}</Descriptions.Item>
            <Descriptions.Item label="Upstream Latency">{formatMs(detail.upstream_latency_ms)}</Descriptions.Item>
            <Descriptions.Item label="Messages Count">{detail.messages_count ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="User ID">{detail.user_id}</Descriptions.Item>
          </Descriptions>

          <Descriptions column={1} size="small" bordered style={{ marginBottom: 16 }}>
            <Descriptions.Item label="Prompt Tokens">
              {formatTokens(detail.usage?.prompt_tokens ?? null)}
            </Descriptions.Item>
            <Descriptions.Item label="Completion Tokens">
              {formatTokens(detail.usage?.completion_tokens ?? null)}
            </Descriptions.Item>
            <Descriptions.Item label="Total Tokens">
              {formatTokens(detail.usage?.total_tokens ?? null)}
            </Descriptions.Item>
          </Descriptions>

          <Descriptions column={1} size="small" bordered style={{ marginBottom: 16 }}>
            <Descriptions.Item label="Question Summary">
              {detail.question_summary ? (
                <Text ellipsis={{ tooltip: detail.question_summary }} style={{ maxWidth: 560, display: 'block' }}>
                  {detail.question_summary}
                </Text>
              ) : (
                '-'
              )}
            </Descriptions.Item>
            <Descriptions.Item label="Hit Chunk IDs">
              {detail.hit_chunk_ids.length > 0 ? detail.hit_chunk_ids.join(', ') : '-'}
            </Descriptions.Item>
          </Descriptions>

          <Typography.Title level={5}>Hit Chunks</Typography.Title>
          <HitChunksPanel
            chunks={chunks}
            loading={chunksLoading}
            error={chunksError}
            onRetry={handleRetryHitChunks}
          />
        </>
      )}
    </Drawer>
  )
}

async function loadHitChunks(
  appId: number,
  requestId: string,
  adminUserId: number,
  isCancelled: () => boolean,
  setChunks: (c: HitChunkSummaryVO[]) => void,
  setChunksLoading: (v: boolean) => void,
  setChunksError: (e: string | null) => void,
) {
  setChunksLoading(true)
  try {
    const res = await getHitChunks(appId, requestId, adminUserId)
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
