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
  const { adminUserId, selectedAppId, setSelectedAppId, navigateTo } = useShell()

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

  const [disableConfirmId, setDisableConfirmId] = useState<number | null>(null)
  const [revokeConfirmId, setRevokeConfirmId] = useState<number | null>(null)

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
    } finally {
      setDisableConfirmId(null)
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
    } finally {
      setRevokeConfirmId(null)
    }
  }

  function handleSecretClose() {
    setSecretOpen(false)
    setPlaintextKey(null)
  }

  function handleGoToSmokeTest() {
    setSelectedAppId(activeAppId)
    navigateTo('smoke')
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
      title: 'Created', dataIndex: 'created_at', key: 'created_at', width: 170,
      render: (v: string | null) => v ?? '-',
    },
    {
      title: 'Expires', dataIndex: 'expires_at', key: 'expires_at', width: 170,
      render: (v: string | null) => v ?? 'Never',
    },
    {
      title: 'Last Used', dataIndex: 'last_used_at', key: 'last_used_at', width: 170,
      render: (v: string | null) => v ?? 'Never',
    },
    {
      title: 'Revoked', dataIndex: 'revoked_at', key: 'revoked_at', width: 170,
      render: (v: string | null, record: ApiKeyVO) => {
        if (record.status !== 'REVOKED') return '-'
        return v ?? '-'
      },
    },
    {
      title: 'Actions', key: 'actions', width: 180,
      render: (_: unknown, record: ApiKeyVO) => {
        if (record.status === 'REVOKED') return null
        return (
          <Space>
            {record.status === 'ACTIVE' ? (
              <Button size="small" onClick={() => setDisableConfirmId(record.id)}>
                Disable
              </Button>
            ) : null}
            <Button size="small" danger onClick={() => setRevokeConfirmId(record.id)}>
              Revoke
            </Button>
          </Space>
        )
      },
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
        scroll={{ x: 1200 }}
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
        onGoToSmokeTest={handleGoToSmokeTest}
      />

      <Modal
        title="Disable API Key"
        open={disableConfirmId !== null}
        onCancel={() => setDisableConfirmId(null)}
        onOk={() => disableConfirmId !== null && handleDisable(disableConfirmId)}
        okText="Disable"
        okButtonProps={{ danger: false }}
        cancelText="Cancel"
      >
        <Alert
          type="warning"
          showIcon
          message="This key will stop authenticating public /v1/* requests immediately."
          description="Use this for a key that should stop working now but is not known to be leaked. If the key has been leaked, revoke it permanently instead."
          style={{ marginBottom: 12 }}
        />
        <Typography.Text type="secondary">
          The key prefix will remain visible in the list. Revocation is the terminal action for leaked keys.
        </Typography.Text>
      </Modal>

      <Modal
        title="Revoke API Key"
        open={revokeConfirmId !== null}
        onCancel={() => setRevokeConfirmId(null)}
        onOk={() => revokeConfirmId !== null && handleRevoke(revokeConfirmId)}
        okText="Revoke"
        okButtonProps={{ danger: true }}
        cancelText="Cancel"
      >
        <Alert
          type="error"
          showIcon
          message="This operation is irreversible."
          description="The key will be permanently revoked and must fail all public /v1/* calls with 401 invalid_api_key. You will need to create a new key and update any clients that were using this key."
          style={{ marginBottom: 12 }}
        />
        <Typography.Text type="secondary">
          After revocation, verify the old key is rejected, then create a fresh key and update your clients.
        </Typography.Text>
      </Modal>
    </div>
  )
}
