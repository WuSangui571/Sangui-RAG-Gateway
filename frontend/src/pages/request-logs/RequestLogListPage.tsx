import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import {
  Table, Button, Select, Input, Space, Typography, Alert, Form,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import type { ApiRequestLogVO, RequestLogListParams } from '../../types/request-log'
import { listRequestLogs } from '../../api/request-logs'
import { ApiError } from '../../api/http'
import RequestLogStatusTag from '../../components/domain/RequestLogStatusTag'
import RequestLogDetailDrawer from '../../components/domain/RequestLogDetailDrawer'
import { useI18n } from '../../app/i18n'

const { Title } = Typography

interface RequestLogListPageProps {
  persistentAppId?: number
  persistentAdminUserId?: number
}

export default function RequestLogListPage({ persistentAppId, persistentAdminUserId }: RequestLogListPageProps) {
  const { t } = useI18n()
  const [appId, setAppId] = useState<string>('')
  const [adminUserId, setAdminUserId] = useState<string>('')
  const [submittedAppId, setSubmittedAppId] = useState<number | null>(null)
  const [submittedAdminUserId, setSubmittedAdminUserId] = useState<number | null>(null)
  const autoConnectDone = useRef(false)

  const [filters, setFilters] = useState<RequestLogListParams>({
    page: 1,
    page_size: 20,
    status: '',
    error_code: '',
    start_time: '',
    end_time: '',
  })

  const [data, setData] = useState<ApiRequestLogVO[]>([])
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const [detailRequestId, setDetailRequestId] = useState<string | null>(null)
  const [detailOpen, setDetailOpen] = useState(false)

  const canQuery = submittedAppId !== null && submittedAdminUserId !== null

  const fetchLogs = useCallback(async () => {
    if (submittedAppId === null || submittedAdminUserId === null) return
    setLoading(true)
    setError(null)
    try {
      const res = await listRequestLogs(submittedAppId, filters, submittedAdminUserId)
      if (res.code !== 'OK') {
        setError(res.message)
        setData([])
        setTotal(0)
      } else {
        setData(res.data.items)
        setTotal(res.data.total)
      }
    } catch (e: unknown) {
      const msg = e instanceof ApiError ? e.message : (e instanceof Error ? e.message : 'Network error')
      setError(msg)
      setData([])
      setTotal(0)
    } finally {
      setLoading(false)
    }
  }, [canQuery, submittedAppId, submittedAdminUserId, filters])

  useEffect(() => {
    if (autoConnectDone.current) return
    if (persistentAppId !== undefined && persistentAdminUserId !== undefined
      && persistentAppId > 0 && persistentAdminUserId > 0) {
      autoConnectDone.current = true
      setSubmittedAppId(persistentAppId)
      setSubmittedAdminUserId(persistentAdminUserId)
    }
  }, [persistentAppId, persistentAdminUserId])

  useEffect(() => {
    fetchLogs()
  }, [fetchLogs])

  function handleConnect() {
    const appIdNum = Number(appId)
    const adminUserIdNum = Number(adminUserId)
    if (!appId || !adminUserId || !Number.isFinite(appIdNum) || !Number.isFinite(adminUserIdNum)
      || appIdNum <= 0 || adminUserIdNum <= 0) {
      return
    }
    setSubmittedAppId(appIdNum)
    setSubmittedAdminUserId(adminUserIdNum)
    setFilters(prev => ({ ...prev, page: 1 }))
    setError(null)
  }

  function handleFilterChange(
    key: keyof RequestLogListParams,
    value: string | number | undefined,
  ) {
    setFilters(prev => {
      const next = { ...prev, [key]: value }
      if (key !== 'page' && key !== 'page_size') {
        next.page = 1
      }
      return next
    })
  }

  function openDetail(requestId: string) {
    setDetailRequestId(requestId)
    setDetailOpen(true)
  }

  function closeDetail() {
    setDetailOpen(false)
    setDetailRequestId(null)
  }

  const columns: ColumnsType<ApiRequestLogVO> = useMemo(() => [
    {
      title: t('request-log.column.createdAt'),
      dataIndex: 'created_at',
      key: 'created_at',
      width: 180,
    },
    {
      title: t('request-log.column.status'),
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (status: 'success' | 'failure') => <RequestLogStatusTag status={status} />,
    },
    {
      title: t('request-log.column.errorCode'),
      dataIndex: 'error_code',
      key: 'error_code',
      width: 140,
      render: (v: string | null) => v ?? '-',
    },
    {
      title: t('request-log.column.model'),
      dataIndex: 'model',
      key: 'model',
      width: 160,
      render: (v: string | null) => v ?? '-',
    },
    {
      title: t('request-log.column.provider'),
      dataIndex: 'provider_name',
      key: 'provider_name',
      width: 130,
      render: (v: string | null) => v ?? '-',
    },
    {
      title: t('request-log.column.latency'),
      dataIndex: 'latency_ms',
      key: 'latency_ms',
      width: 90,
      render: (v: number | null) => (v !== null && v !== undefined ? `${v}ms` : '-'),
    },
    {
      title: t('request-log.column.totalTokens'),
      key: 'total_tokens',
      width: 110,
      render: (_: unknown, record: ApiRequestLogVO) =>
        record.usage?.total_tokens !== null && record.usage?.total_tokens !== undefined
          ? String(record.usage.total_tokens)
          : '-',
    },
    {
      title: t('request-log.column.questionSummary'),
      dataIndex: 'question_summary',
      key: 'question_summary',
      ellipsis: true,
      render: (v: string | null) => v ?? '-',
    },
    {
      title: t('request-log.column.hitCount'),
      key: 'hit_count',
      width: 90,
      render: (_: unknown, record: ApiRequestLogVO) => record.hit_chunk_ids.length,
    },
    {
      title: t('request-log.column.action'),
      key: 'action',
      width: 80,
      render: (_: unknown, record: ApiRequestLogVO) => (
        <Button type="link" size="small" onClick={() => openDetail(record.request_id)}>
          {t('request-log.detail')}
        </Button>
      ),
    },
  ], [t])

  if (!canQuery) {
    return (
      <div style={{ maxWidth: 480, margin: '80px auto', padding: 24 }}>
        <Title level={3} style={{ textAlign: 'center', marginBottom: 24 }}>
          {t('request-log.title')}
        </Title>
        <Form layout="vertical">
          <Form.Item label={t('request-log.appId')} required>
            <Input
              value={appId}
              onChange={e => setAppId(e.target.value)}
              placeholder={t('request-log.enterAppId')}
              type="number"
              onPressEnter={handleConnect}
            />
          </Form.Item>
          <Form.Item label={t('request-log.adminUserId')} required>
            <Input
              value={adminUserId}
              onChange={e => setAdminUserId(e.target.value)}
              placeholder={t('request-log.enterUserId')}
              type="number"
              onPressEnter={handleConnect}
            />
          </Form.Item>
          <Button
            type="primary"
            block
            onClick={handleConnect}
            disabled={!appId || !adminUserId
              || !Number.isFinite(Number(appId)) || !Number.isFinite(Number(adminUserId))
              || Number(appId) <= 0 || Number(adminUserId) <= 0}
          >
            {t('request-log.connect')}
          </Button>
        </Form>
      </div>
    )
  }

  return (
    <div style={{ padding: 24 }}>
      <Title level={3} style={{ marginBottom: 16 }}>
        {t('request-log.title')}
        <Typography.Text type="secondary" style={{ fontSize: 14, marginLeft: 12 }}>
          App #{submittedAppId}
        </Typography.Text>
      </Title>

      <Space wrap style={{ marginBottom: 16 }}>
        <Select
          value={filters.status || undefined}
          onChange={(v) => handleFilterChange('status', v)}
          placeholder={t('request-log.statusFilter')}
          allowClear
          style={{ width: 130 }}
          options={[
            { value: 'success', label: t('request-log.success') },
            { value: 'failure', label: t('request-log.failure') },
          ]}
        />
        <Input
          value={filters.error_code || ''}
          onChange={(e) => handleFilterChange('error_code', e.target.value || undefined)}
          placeholder={t('request-log.errorCode')}
          allowClear
          style={{ width: 160 }}
        />
        <Input
          value={filters.start_time || ''}
          onChange={(e) => handleFilterChange('start_time', e.target.value || undefined)}
          placeholder={t('request-log.startTime')}
          style={{ width: 220 }}
        />
        <Input
          value={filters.end_time || ''}
          onChange={(e) => handleFilterChange('end_time', e.target.value || undefined)}
          placeholder={t('request-log.endTime')}
          style={{ width: 220 }}
        />
        <Button onClick={() => {
          setFilters({ page: 1, page_size: 20, status: '', error_code: '', start_time: '', end_time: '' })
        }}>
          {t('request-log.resetFilters')}
        </Button>
      </Space>

      {error && (
        <Alert
          type="error"
          message={t('request-log.loadError')}
          description={error}
          closable
          onClose={() => setError(null)}
          style={{ marginBottom: 16 }}
          action={
            <Button size="small" onClick={fetchLogs}>
              {t('request-log.retry')}
            </Button>
          }
        />
      )}

      <Table
        rowKey="id"
        columns={columns}
        dataSource={data}
        loading={loading}
        locale={{
          emptyText: error ? ' ' : t('request-log.empty'),
        }}
        pagination={{
          current: filters.page || 1,
          pageSize: filters.page_size || 20,
          total,
          showSizeChanger: true,
          pageSizeOptions: ['10', '20', '50', '100'],
          showTotal: (totalCount) => t('request-log.pagination', { total: totalCount }),
          onChange: (page, pageSize) => {
            setFilters(prev => ({ ...prev, page, page_size: pageSize }))
          },
        }}
        scroll={{ x: 1100 }}
      />

      {submittedAppId !== null && submittedAdminUserId !== null && (
        <RequestLogDetailDrawer
          open={detailOpen}
          appId={submittedAppId}
          requestId={detailRequestId}
          adminUserId={submittedAdminUserId}
          onClose={closeDetail}
        />
      )}
    </div>
  )
}
