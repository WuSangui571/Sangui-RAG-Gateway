import { useCallback, useEffect, useState } from 'react'
import {
  Table, Button, Modal, Form, Input, Select, Space, Typography, Alert, DatePicker,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import type { ApiKeyVO, ApiKeyStatus, CreateApiKeyDTO } from '../../types/api-key'
import type { AppVO } from '../../types/app'
import { ApiError } from '../../api/http'
import { listApps } from '../../api/apps'
import { listApiKeys, createApiKey, disableApiKey, enableApiKey, revokeApiKey } from '../../api/api-keys'
import { useShell } from '../../components/layout/AdminShell'
import StatusTag from '../../components/domain/StatusTag'
import ApiKeyOneTimeSecret from '../../components/domain/ApiKeyOneTimeSecret'
import { useI18n } from '../../app/i18n'

export default function ApiKeyPage() {
  const { adminUserId, selectedAppId, setSelectedAppId, navigateTo } = useShell()
  const { t } = useI18n()

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
      setError(e instanceof ApiError ? e.message : (e instanceof Error ? e.message : t('api-keys.failedLoadApps')))
    }
  }, [adminUserId, t])

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
      setError(e instanceof ApiError ? e.message : (e instanceof Error ? e.message : t('api-keys.networkError')))
      setKeys([])
    } finally {
      setLoading(false)
    }
  }, [activeAppId, adminUserId, t])

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
      setError(e instanceof ApiError ? e.message : (e instanceof Error ? e.message : t('api-keys.networkError')))
    } finally {
      setDisableConfirmId(null)
    }
  }

  async function handleEnable(id: number) {
    if (adminUserId === null) return
    try {
      const res = await enableApiKey(id, adminUserId)
      if (res.code !== 'OK') setError(res.message)
      else fetchKeys()
    } catch (e: unknown) {
      setError(e instanceof ApiError ? e.message : (e instanceof Error ? e.message : t('api-keys.networkError')))
    }
  }

  async function handleRevoke(id: number) {
    if (adminUserId === null) return
    try {
      const res = await revokeApiKey(id, adminUserId)
      if (res.code !== 'OK') setError(res.message)
      else fetchKeys()
    } catch (e: unknown) {
      setError(e instanceof ApiError ? e.message : (e instanceof Error ? e.message : t('api-keys.networkError')))
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
    { title: t('api-keys.column.id'), dataIndex: 'id', key: 'id', width: 60 },
    { title: t('api-keys.column.name'), dataIndex: 'name', key: 'name', width: 140 },
    { title: t('api-keys.column.prefix'), dataIndex: 'key_prefix', key: 'key_prefix', width: 180 },
    {
      title: t('api-keys.column.status'), dataIndex: 'status', key: 'status', width: 100,
      render: (s: ApiKeyStatus) => <StatusTag status={s} />,
    },
    {
      title: t('api-keys.column.created'), dataIndex: 'created_at', key: 'created_at', width: 170,
      render: (v: string | null) => v ?? '-',
    },
    {
      title: t('api-keys.column.expires'), dataIndex: 'expires_at', key: 'expires_at', width: 170,
      render: (v: string | null) => v ?? t('api-keys.never'),
    },
    {
      title: t('api-keys.column.lastUsed'), dataIndex: 'last_used_at', key: 'last_used_at', width: 170,
      render: (v: string | null) => v ?? t('api-keys.never'),
    },
    {
      title: t('api-keys.column.revoked'), dataIndex: 'revoked_at', key: 'revoked_at', width: 170,
      render: (v: string | null, record: ApiKeyVO) => {
        if (record.status !== 'REVOKED') return '-'
        return v ?? '-'
      },
    },
    {
      title: t('api-keys.column.actions'), key: 'actions', width: 180,
      render: (_: unknown, record: ApiKeyVO) => {
        if (record.status === 'REVOKED') {
          return '-'
        }

        return (
          <Space size={4}>
            {record.status === 'ACTIVE' ? (
              <Button size="small" onClick={() => setDisableConfirmId(record.id)}>
                {t('api-keys.disable')}
              </Button>
            ) : null}
            {record.status === 'DISABLED' ? (
              <Button size="small" onClick={() => handleEnable(record.id)}>
                {t('api-keys.enable')}
              </Button>
            ) : null}
            <Button size="small" danger onClick={() => setRevokeConfirmId(record.id)}>
              {t('api-keys.revoke')}
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
          <Typography.Text type="secondary">{t('api-keys.selectApp')}</Typography.Text>
          <Select
            value={activeAppId}
            onChange={(v) => handleAppSelect(v)}
            placeholder={t('api-keys.selectPlaceholder')}
            style={{ width: 240, marginLeft: 8 }}
            options={apps.map(app => ({ value: app.id, label: `#${app.id} ${app.name}` }))}
          />
        </div>
        <Button
          type="primary"
          disabled={activeAppId === null}
          onClick={() => { setCreateOpen(true); setError(null); setExpiresAtIso(null) }}
        >
          {t('api-keys.create')}
        </Button>
        <Button onClick={fetchKeys} disabled={activeAppId === null}>{t('api-keys.refresh')}</Button>
      </Space>

      {error && (
        <Alert type="error" message={t('api-keys.error')} description={error} closable onClose={() => setError(null)} style={{ marginBottom: 16 }} />
      )}

      <Table
        rowKey="id"
        columns={columns}
        dataSource={keys}
        loading={loading}
        locale={{ emptyText: activeAppId === null ? t('api-keys.emptyNoApp') : t('api-keys.empty') }}
        pagination={false}
        scroll={{ x: 1200 }}
      />

      <Modal
        title={t('api-keys.createTitle')}
        open={createOpen}
        onCancel={() => { setCreateOpen(false); form.resetFields(); setExpiresAtIso(null) }}
        onOk={handleCreate}
        confirmLoading={submitting}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item name="name" label={t('api-keys.column.name')} rules={[{ required: true, message: t('api-keys.nameRequired') }]}>
            <Input placeholder={t('api-keys.namePlaceholder')} />
          </Form.Item>
          <Form.Item name="expires_at" label={t('api-keys.expiresLabel')}>
            <DatePicker
              showTime
              format="YYYY-MM-DDTHH:mm:ss"
              style={{ width: '100%' }}
              placeholder={t('api-keys.expiresPlaceholder')}
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
        title={t('api-keys.disableTitle')}
        open={disableConfirmId !== null}
        onCancel={() => setDisableConfirmId(null)}
        onOk={() => disableConfirmId !== null && handleDisable(disableConfirmId)}
        okText={t('api-keys.disableOk')}
        okButtonProps={{ danger: false }}
        cancelText={t('api-keys.cancel')}
      >
        <Alert
          type="warning"
          showIcon
          message={t('api-keys.disableWarning')}
          description={t('api-keys.disableDesc')}
          style={{ marginBottom: 12 }}
        />
        <Typography.Text type="secondary">
          {t('api-keys.disableHint')}
        </Typography.Text>
      </Modal>

      <Modal
        title={t('api-keys.revokeTitle')}
        open={revokeConfirmId !== null}
        onCancel={() => setRevokeConfirmId(null)}
        onOk={() => revokeConfirmId !== null && handleRevoke(revokeConfirmId)}
        okText={t('api-keys.revokeOk')}
        okButtonProps={{ danger: true }}
        cancelText={t('api-keys.cancel')}
      >
        <Alert
          type="error"
          showIcon
          message={t('api-keys.revokeWarning')}
          description={t('api-keys.revokeDesc')}
          style={{ marginBottom: 12 }}
        />
        <Typography.Text type="secondary">
          {t('api-keys.revokeHint')}
        </Typography.Text>
      </Modal>
    </div>
  )
}
