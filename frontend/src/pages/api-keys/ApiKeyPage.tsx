import { useCallback, useEffect, useState } from 'react'
import {
  Table, Button, Modal, Form, Input, Select, Space, Typography, Alert, DatePicker,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import type { ApiKeyVO, ApiKeyStatus, CreateApiKeyDTO } from '../../types/api-key'
import type { AppVO } from '../../types/app'
import { ApiError } from '../../api/http'
import { listApps } from '../../api/apps'
import { listApiKeys, createApiKey, disableApiKey, revokeApiKey } from '../../api/api-keys'
import { useShell } from '../../components/layout/AdminShell'
import StatusTag from '../../components/domain/StatusTag'
import ApiKeyOneTimeSecret from '../../components/domain/ApiKeyOneTimeSecret'

export default function ApiKeyPage() {
  const { adminUserId, selectedAppId } = useShell()

  const [apps, setApps] = useState<AppVO[]>([])
  const [activeAppId, setActiveAppId] = useState<number | null>(selectedAppId)
  const [keys, setKeys] = useState<ApiKeyVO[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const [createOpen, setCreateOpen] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [form] = Form.useForm<CreateApiKeyDTO>()
  const [expiresAtIso, setExpiresAtIso] = useState<string | null>(null)

  const [secretOpen, setSecretOpen] = useState(false)
  const [plaintextKey, setPlaintextKey] = useState<string | null>(null)

  const fetchApps = useCallback(async () => {
    if (adminUserId === null) return
    try {
      const res = await listApps(undefined, adminUserId)
      if (res.code === 'OK') {
        setApps(res.data)
      } else {
        setApps([])
        setError(res.message)
      }
    } catch (e: unknown) {
      setError(e instanceof ApiError ? e.message : (e instanceof Error ? e.message : 'Failed to load apps'))
    }
  }, [adminUserId])

  useEffect(() => {
    fetchApps()
  }, [fetchApps])

  useEffect(() => {
    if (selectedAppId !== null) {
      setActiveAppId(selectedAppId)
      handleSecretClose()
    }
  }, [selectedAppId])

  const fetchKeys = useCallback(async () => {
    if (activeAppId === null || adminUserId === null) return
    setLoading(true)
    setError(null)
    try {
      const res = await listApiKeys(activeAppId, adminUserId)
      if (res.code !== 'OK') {
        setError(res.message)
        setKeys([])
      } else {
        setKeys(res.data)
      }
    } catch (e: unknown) {
      setError(e instanceof ApiError ? e.message : (e instanceof Error ? e.message : 'Network error'))
      setKeys([])
    } finally {
      setLoading(false)
    }
  }, [activeAppId, adminUserId])

  useEffect(() => {
    fetchKeys()
  }, [fetchKeys])

  function handleAppSelect(appId: number) {
    setActiveAppId(appId)
    setError(null)
    handleSecretClose()
  }

  async function handleCreate() {
    if (activeAppId === null || adminUserId === null) return
    try {
      const values = await form.validateFields()
      setSubmitting(true)
      setError(null)
      const res = await createApiKey(activeAppId, { name: values.name, expires_at: expiresAtIso }, adminUserId)
      if (res.code !== 'OK') {
        setError(res.message)
      } else {
        setCreateOpen(false)
        form.resetFields()
        setPlaintextKey(res.data.key)
        setSecretOpen(true)
        fetchKeys()
      }
    } catch (e: unknown) {
      if (e instanceof ApiError) setError(e.message)
      else if (e instanceof Error) setError(e.message)
    } finally {
      setSubmitting(false)
    }
  }

  async function handleDisable(id: number) {
    if (adminUserId === null) return
    try {
      const res = await disableApiKey(id, adminUserId)
      if (res.code !== 'OK') setError(res.message)
      else fetchKeys()
    } catch (e: unknown) {
      setError(e instanceof ApiError ? e.message : (e instanceof Error ? e.message : 'Network error'))
    }
  }

  async function handleRevoke(id: number) {
    if (adminUserId === null) return
    try {
      const res = await revokeApiKey(id, adminUserId)
      if (res.code !== 'OK') setError(res.message)
      else fetchKeys()
    } catch (e: unknown) {
      setError(e instanceof ApiError ? e.message : (e instanceof Error ? e.message : 'Network error'))
    }
  }

  function handleSecretClose() {
    setSecretOpen(false)
    setPlaintextKey(null)
  }

  const columns: ColumnsType<ApiKeyVO> = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 60 },
    { title: 'Name', dataIndex: 'name', key: 'name', width: 140 },
    { title: 'Prefix', dataIndex: 'key_prefix', key: 'key_prefix', width: 180 },
    {
      title: 'Status', dataIndex: 'status', key: 'status', width: 100,
      render: (s: ApiKeyStatus) => <StatusTag status={s} />,
    },
    {
      title: 'Expires', dataIndex: 'expires_at', key: 'expires_at', width: 180,
      render: (v: string | null) => v ?? 'Never',
    },
    {
      title: 'Last Used', dataIndex: 'last_used_at', key: 'last_used_at', width: 180,
      render: (v: string | null) => v ?? '-',
    },
    {
      title: 'Actions', key: 'actions', width: 180,
      render: (_: unknown, record: ApiKeyVO) => (
        <Space>
          {record.status === 'ACTIVE' || record.status === 'DISABLED' ? (
            <Button size="small" danger onClick={() => handleDisable(record.id)}>
              Disable
            </Button>
          ) : null}
          {record.status !== 'REVOKED' ? (
            <Button size="small" danger onClick={() => handleRevoke(record.id)}>
              Revoke
            </Button>
          ) : null}
        </Space>
      ),
    },
  ]

  return (
    <div>
      <Space style={{ marginBottom: 16 }} align="start">
        <div>
          <Typography.Text type="secondary">Select App:</Typography.Text>
          <Select
            value={activeAppId}
            onChange={(v) => handleAppSelect(v)}
            placeholder="Select an app"
            style={{ width: 240, marginLeft: 8 }}
            options={apps.map(app => ({ value: app.id, label: `#${app.id} ${app.name}` }))}
          />
        </div>
        <Button
          type="primary"
          disabled={activeAppId === null}
          onClick={() => { setCreateOpen(true); setError(null); setExpiresAtIso(null) }}
        >
          Create API Key
        </Button>
        <Button onClick={fetchKeys} disabled={activeAppId === null}>Refresh</Button>
      </Space>

      {error && (
        <Alert type="error" message="Error" description={error} closable onClose={() => setError(null)} style={{ marginBottom: 16 }} />
      )}

      <Table
        rowKey="id"
        columns={columns}
        dataSource={keys}
        loading={loading}
        locale={{ emptyText: activeAppId === null ? 'Select an app to view keys' : 'No API keys found' }}
        pagination={false}
        scroll={{ x: 900 }}
      />

      <Modal
        title="Create API Key"
        open={createOpen}
        onCancel={() => { setCreateOpen(false); form.resetFields(); setExpiresAtIso(null) }}
        onOk={handleCreate}
        confirmLoading={submitting}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="Name" rules={[{ required: true, message: 'Name is required' }]}>
            <Input placeholder="Key name" />
          </Form.Item>
          <Form.Item name="expires_at" label="Expires At">
            <DatePicker
              showTime
              format="YYYY-MM-DDTHH:mm:ss"
              style={{ width: '100%' }}
              placeholder="Optional expiry"
              onChange={(_date, dateStr) => {
                const str = typeof dateStr === 'string' ? dateStr.trim() : ''
                setExpiresAtIso(str.length > 0 ? str : null)
              }}
            />
          </Form.Item>
        </Form>
      </Modal>

      <ApiKeyOneTimeSecret
        open={secretOpen}
        plaintextKey={plaintextKey}
        onClose={handleSecretClose}
      />
    </div>
  )
}
