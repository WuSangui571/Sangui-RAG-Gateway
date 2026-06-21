import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import {
  Table, Button, Select, Input, Space, Typography, Alert,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import type { AppVO } from '../../types/app'
import type { ApiRequestLogVO, RequestLogListParams } from '../../types/request-log'
import { listRequestLogs } from '../../api/request-logs'
import { listApps } from '../../api/apps'
import { ApiError } from '../../api/http'
import RequestLogStatusTag from '../../components/domain/RequestLogStatusTag'
import RequestLogDetailDrawer from '../../components/domain/RequestLogDetailDrawer'
import { useShell } from '../../components/layout/AdminShell'
import { useI18n } from '../../app/i18n'

const { Title, Text } = Typography

interface RequestLogListPageProps {
  persistentAppId?: number
}

export default function RequestLogListPage({ persistentAppId }: RequestLogListPageProps) {
  const { t } = useI18n()
  const { adminUserId, selectedAppId, setSelectedAppId } = useShell()

  const [apps, setApps] = useState<AppVO[]>([])
  const [activeAppId, setActiveAppId] = useState<number | null>(null)
  const [appsLoading, setAppsLoading] = useState(false)
  const [appsError, setAppsError] = useState<string | null>(null)
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

  const canQuery = activeAppId !== null && adminUserId !== null
  const noAppsAvailable = apps.length === 0

  const fetchApps = useCallback(async () => {
    if (adminUserId === null) return
    setAppsLoading(true)
    setAppsError(null)
    try {
      const res = await listApps(undefined)
      if (res.code === 'OK') {
        setApps(res.data)
      } else {
        setAppsError(res.message)
        setApps([])
      }
    } catch (e: unknown) {
      setAppsError(e instanceof ApiError ? e.message : (e instanceof Error ? e.message : 'Network error'))
      setApps([])
    } finally {
      setAppsLoading(false)
    }
  }, [adminUserId])

  const fetchLogs = useCallback(async () => {
    if (activeAppId === null || adminUserId === null) return
    setLoading(true)
    setError(null)
    try {
      const res = await listRequestLogs(activeAppId, filters)
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
  }, [activeAppId, adminUserId, filters])

  useEffect(() => {
    fetchApps()
  }, [fetchApps])

  useEffect(() => {
    if (autoConnectDone.current) return
    if (persistentAppId !== undefined && persistentAppId > 0) {
      autoConnectDone.current = true
      setActiveAppId(persistentAppId)
    }
  }, [persistentAppId])

  useEffect(() => {
    if (selectedAppId !== null) {
      setActiveAppId(selectedAppId)
    }
  }, [selectedAppId])

  useEffect(() => {
    fetchLogs()
  }, [fetchLogs])

  function handleAppSelect(appId: number) {
    setActiveAppId(appId)
    setSelectedAppId(appId)
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
      render: (status: 'success' | 'failure' | 'cancelled') => <RequestLogStatusTag status={status} />,
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

  if (adminUserId === null) {
    return (
      <div style={{ maxWidth: 480, margin: '80px auto', padding: 24 }}>
        <Text type="secondary">{t('request-log.title')} — please log in first.</Text>
      </div>
    )
  }

  return (
    <div style={{ padding: 24 }}>
      <Title level={3} style={{ marginBottom: 16 }}>
        {t('request-log.title')}
        {activeAppId !== null && (
          <Typography.Text type="secondary" style={{ fontSize: 14, marginLeft: 12 }}>
            App #{activeAppId}
          </Typography.Text>
        )}
      </Title>

      <Space style={{ marginBottom: 16 }}>
        <Typography.Text type="secondary">{t('request-log.selectApp')}</Typography.Text>
        <Select
          value={activeAppId}
          onChange={(v) => handleAppSelect(v)}
          placeholder={t('request-log.selectPlaceholder')}
          loading={appsLoading}
          disabled={appsLoading}
          style={{ width: 280 }}
          options={apps.map(app => ({ value: app.id, label: `#${app.id} ${app.name}` }))}
          notFoundContent={appsLoading ? t('request-log.loadingApps') : t('request-log.noApps')}
        />
      </Space>

      {appsError && (
        <Alert
          type="error"
          message={t('request-log.loadAppsError')}
          description={appsError}
          closable
          onClose={() => setAppsError(null)}
          style={{ marginBottom: 16 }}
          action={
            <Button size="small" onClick={fetchApps}>
              {t('request-log.retry')}
            </Button>
          }
        />
      )}

      {!canQuery && !appsLoading && !appsError && (
        <div style={{ textAlign: 'center', padding: 48, color: '#888' }}>
          <Text type="secondary">
            {noAppsAvailable ? t('request-log.noApps') : t('request-log.emptyNoApp')}
          </Text>
        </div>
      )}

      {canQuery && (
        <>
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
                { value: 'cancelled', label: t('request-log.cancelled') },
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

          <RequestLogDetailDrawer
            open={detailOpen}
            appId={activeAppId}
            requestId={detailRequestId}
            onClose={closeDetail}
          />
        </>
      )}
    </div>
  )
}
