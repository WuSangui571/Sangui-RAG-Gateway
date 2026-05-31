import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  Table, Button, Select, Input, Space, Typography, Alert, Form,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import type { ApiRequestLogVO, RequestLogListParams } from '../../types/request-log'
import { listRequestLogs } from '../../api/request-logs'
import { ApiError } from '../../api/http'
import RequestLogStatusTag from '../../components/domain/RequestLogStatusTag'
import RequestLogDetailDrawer from '../../components/domain/RequestLogDetailDrawer'

const { Title } = Typography

export default function RequestLogListPage() {
  const [appId, setAppId] = useState<string>('')
  const [adminUserId, setAdminUserId] = useState<string>('')
  const [submittedAppId, setSubmittedAppId] = useState<number | null>(null)
  const [submittedAdminUserId, setSubmittedAdminUserId] = useState<number | null>(null)

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
      title: 'Created At',
      dataIndex: 'created_at',
      key: 'created_at',
      width: 180,
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (status: 'success' | 'failure') => <RequestLogStatusTag status={status} />,
    },
    {
      title: 'Error Code',
      dataIndex: 'error_code',
      key: 'error_code',
      width: 140,
      render: (v: string | null) => v ?? '-',
    },
    {
      title: 'Model',
      dataIndex: 'model',
      key: 'model',
      width: 160,
      render: (v: string | null) => v ?? '-',
    },
    {
      title: 'Provider',
      dataIndex: 'provider_name',
      key: 'provider_name',
      width: 130,
      render: (v: string | null) => v ?? '-',
    },
    {
      title: 'Latency',
      dataIndex: 'latency_ms',
      key: 'latency_ms',
      width: 90,
      render: (v: number | null) => (v !== null && v !== undefined ? `${v}ms` : '-'),
    },
    {
      title: 'Total Tokens',
      key: 'total_tokens',
      width: 110,
      render: (_: unknown, record: ApiRequestLogVO) =>
        record.usage?.total_tokens !== null && record.usage?.total_tokens !== undefined
          ? String(record.usage.total_tokens)
          : '-',
    },
    {
      title: 'Question Summary',
      dataIndex: 'question_summary',
      key: 'question_summary',
      ellipsis: true,
      render: (v: string | null) => v ?? '-',
    },
    {
      title: 'Hit Count',
      key: 'hit_count',
      width: 90,
      render: (_: unknown, record: ApiRequestLogVO) => record.hit_chunk_ids.length,
    },
    {
      title: 'Action',
      key: 'action',
      width: 80,
      render: (_: unknown, record: ApiRequestLogVO) => (
        <Button type="link" size="small" onClick={() => openDetail(record.request_id)}>
          Detail
        </Button>
      ),
    },
  ], [])

  if (!canQuery) {
    return (
      <div style={{ maxWidth: 480, margin: '80px auto', padding: 24 }}>
        <Title level={3} style={{ textAlign: 'center', marginBottom: 24 }}>
          Request Logs
        </Title>
        <Form layout="vertical">
          <Form.Item label="App ID" required>
            <Input
              value={appId}
              onChange={e => setAppId(e.target.value)}
              placeholder="Enter app ID"
              type="number"
              onPressEnter={handleConnect}
            />
          </Form.Item>
          <Form.Item label="Admin User ID" required>
            <Input
              value={adminUserId}
              onChange={e => setAdminUserId(e.target.value)}
              placeholder="Enter admin user ID"
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
            Connect
          </Button>
        </Form>
      </div>
    )
  }

  return (
    <div style={{ padding: 24 }}>
      <Title level={3} style={{ marginBottom: 16 }}>
        Request Logs
        <Typography.Text type="secondary" style={{ fontSize: 14, marginLeft: 12 }}>
          App #{submittedAppId}
        </Typography.Text>
      </Title>

      <Space wrap style={{ marginBottom: 16 }}>
        <Select
          value={filters.status || undefined}
          onChange={(v) => handleFilterChange('status', v)}
          placeholder="Status"
          allowClear
          style={{ width: 130 }}
          options={[
            { value: 'success', label: 'Success' },
            { value: 'failure', label: 'Failure' },
          ]}
        />
        <Input
          value={filters.error_code || ''}
          onChange={(e) => handleFilterChange('error_code', e.target.value || undefined)}
          placeholder="Error Code"
          allowClear
          style={{ width: 160 }}
        />
        <Input
          value={filters.start_time || ''}
          onChange={(e) => handleFilterChange('start_time', e.target.value || undefined)}
          placeholder="Start Time (ISO)"
          style={{ width: 220 }}
        />
        <Input
          value={filters.end_time || ''}
          onChange={(e) => handleFilterChange('end_time', e.target.value || undefined)}
          placeholder="End Time (ISO)"
          style={{ width: 220 }}
        />
        <Button onClick={() => {
          setFilters({ page: 1, page_size: 20, status: '', error_code: '', start_time: '', end_time: '' })
        }}>
          Reset Filters
        </Button>
      </Space>

      {error && (
        <Alert
          type="error"
          message="Failed to load request logs"
          description={error}
          closable
          onClose={() => setError(null)}
          style={{ marginBottom: 16 }}
          action={
            <Button size="small" onClick={fetchLogs}>
              Retry
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
          emptyText: error ? ' ' : 'No request logs found',
        }}
        pagination={{
          current: filters.page || 1,
          pageSize: filters.page_size || 20,
          total,
          showSizeChanger: true,
          pageSizeOptions: ['10', '20', '50', '100'],
          showTotal: (t) => `Total ${t} logs`,
          onChange: (page, pageSize) => {
            setFilters(prev => ({ ...prev, page, page_size: pageSize }))
          },
        }}
        scroll={{ x: 1100 }}
      />

      <RequestLogDetailDrawer
        open={detailOpen}
        appId={submittedAppId}
        requestId={detailRequestId}
        adminUserId={submittedAdminUserId}
        onClose={closeDetail}
      />
    </div>
  )
}
