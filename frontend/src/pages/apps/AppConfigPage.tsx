import { useCallback, useEffect, useState } from 'react'
import {
  Table, Button, Modal, Form, Input, Select, Space, Typography, Alert,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import type { AppVO, AppStatus, CreateAppDTO } from '../../types/app'
import type { ModelConfigVO } from '../../types/model-config'
import type { KnowledgeBaseVO } from '../../types/knowledge'
import { ApiError } from '../../api/http'
import { listApps, createApp, bindDefaultModelConfig, bindDefaultKnowledgeBase, disableApp, enableApp } from '../../api/apps'
import { listChatCapableConfigs } from '../../api/model-configs'
import { listKnowledgeBases } from '../../api/knowledge'
import { useShell } from '../../components/layout/AdminShell'
import StatusTag from '../../components/domain/StatusTag'
import { useI18n } from '../../app/i18n'

export default function AppConfigPage() {
  const { adminUserId, setSelectedAppId } = useShell()
  const { t } = useI18n()

  const [apps, setApps] = useState<AppVO[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const [createOpen, setCreateOpen] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [form] = Form.useForm<CreateAppDTO>()

  const [bindModelOpen, setBindModelOpen] = useState(false)
  const [bindModelAppId, setBindModelAppId] = useState<number | null>(null)
  const [modelConfigs, setModelConfigs] = useState<ModelConfigVO[]>([])
  const [selectedModelConfigId, setSelectedModelConfigId] = useState<number | null>(null)

  const [bindKbOpen, setBindKbOpen] = useState(false)
  const [bindKbAppId, setBindKbAppId] = useState<number | null>(null)
  const [kbList, setKbList] = useState<KnowledgeBaseVO[]>([])
  const [selectedKbId, setSelectedKbId] = useState<number | null>(null)

  const fetchApps = useCallback(async () => {
    if (adminUserId === null) return
    setLoading(true)
    setError(null)
    try {
      const res = await listApps(undefined, adminUserId)
      if (res.code !== 'OK') {
        setError(res.message)
        setApps([])
      } else {
        setApps(res.data)
      }
    } catch (e: unknown) {
      setError(e instanceof ApiError ? e.message : (e instanceof Error ? e.message : t('apps.networkError')))
      setApps([])
    } finally {
      setLoading(false)
    }
  }, [adminUserId, t])

  useEffect(() => {
    fetchApps()
  }, [fetchApps])

  async function handleCreate() {
    if (adminUserId === null) return
    try {
      const values = await form.validateFields()
      setSubmitting(true)
      setError(null)
      const res = await createApp({ name: values.name }, adminUserId)
      if (res.code !== 'OK') {
        setError(res.message)
      } else {
        setCreateOpen(false)
        form.resetFields()
        fetchApps()
      }
    } catch (e: unknown) {
      if (e instanceof ApiError) setError(e.message)
      else if (e instanceof Error) setError(e.message)
    } finally {
      setSubmitting(false)
    }
  }

  async function loadModelConfigsForBind(appId: number) {
    if (adminUserId === null) return
    setBindModelAppId(appId)
    try {
      const res = await listChatCapableConfigs(adminUserId)
      if (res.code === 'OK') {
        setModelConfigs(res.data)
      } else {
        setModelConfigs([])
        setError(res.message)
      }
    } catch (e: unknown) {
      setModelConfigs([])
      setError(e instanceof ApiError ? e.message : (e instanceof Error ? e.message : t('apps.failedLoadModel')))
    }
    setBindModelOpen(true)
  }

  async function handleBindModel() {
    if (bindModelAppId === null || selectedModelConfigId === null || adminUserId === null) return
    try {
      const res = await bindDefaultModelConfig(bindModelAppId, { model_config_id: selectedModelConfigId }, adminUserId)
      if (res.code !== 'OK') {
        setError(res.message)
      } else {
        setBindModelOpen(false)
        fetchApps()
      }
    } catch (e: unknown) {
      setError(e instanceof ApiError ? e.message : (e instanceof Error ? e.message : t('apps.networkError')))
    }
  }

  async function loadKbsForBind(appId: number) {
    if (adminUserId === null) return
    setBindKbAppId(appId)
    try {
      const res = await listKnowledgeBases(undefined, adminUserId)
      if (res.code === 'OK') {
        setKbList(res.data.filter(kb => kb.status === 'READY'))
      } else {
        setKbList([])
        setError(res.message)
      }
    } catch (e: unknown) {
      setKbList([])
      setError(e instanceof ApiError ? e.message : (e instanceof Error ? e.message : t('apps.failedLoadKb')))
    }
    setBindKbOpen(true)
  }

  async function handleBindKb() {
    if (bindKbAppId === null || selectedKbId === null || adminUserId === null) return
    try {
      const res = await bindDefaultKnowledgeBase(bindKbAppId, { knowledge_base_id: selectedKbId }, adminUserId)
      if (res.code !== 'OK') {
        setError(res.message)
      } else {
        setBindKbOpen(false)
        fetchApps()
      }
    } catch (e: unknown) {
      setError(e instanceof ApiError ? e.message : (e instanceof Error ? e.message : t('apps.networkError')))
    }
  }

  function confirmDisableApp(app: AppVO) {
    if (adminUserId === null) return
    Modal.confirm({
      title: t('apps.disableTitle'),
      content: t('apps.disableContent', { name: app.name }),
      okText: t('apps.disableOk'),
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          const res = await disableApp(app.id, adminUserId)
          if (res.code !== 'OK') {
            setError(res.message)
          } else {
            fetchApps()
          }
        } catch (e: unknown) {
          setError(e instanceof ApiError ? e.message : (e instanceof Error ? e.message : t('apps.networkError')))
        }
      },
    })
  }

  async function handleEnableApp(app: AppVO) {
    if (adminUserId === null) return
    try {
      const res = await enableApp(app.id, adminUserId)
      if (res.code !== 'OK') {
        setError(res.message)
      } else {
        fetchApps()
      }
    } catch (e: unknown) {
      setError(e instanceof ApiError ? e.message : (e instanceof Error ? e.message : t('apps.networkError')))
    }
  }

  const columns: ColumnsType<AppVO> = [
    { title: t('apps.column.id'), dataIndex: 'id', key: 'id', width: 60 },
    { title: t('apps.column.name'), dataIndex: 'name', key: 'name', width: 180 },
    {
      title: t('apps.column.status'), dataIndex: 'status', key: 'status', width: 100,
      render: (s: AppStatus) => <StatusTag status={s} />,
    },
    {
      title: t('apps.column.defaultModel'), key: 'model_config', width: 140,
      render: (_: unknown, record: AppVO) => record.default_model_config_id ?? '-',
    },
    {
      title: t('apps.column.defaultKb'), key: 'kb', width: 140,
      render: (_: unknown, record: AppVO) => record.default_knowledge_base_id ?? '-',
    },
    {
      title: t('apps.column.actions'), key: 'actions', width: 380,
      render: (_: unknown, record: AppVO) => (
        <Space>
          <Button size="small" onClick={() => { setSelectedAppId(record.id) }}>
            {t('apps.selectApp')}
          </Button>
          <Button size="small" onClick={() => loadModelConfigsForBind(record.id)}>
            {t('apps.bindModel')}
          </Button>
          <Button
            size="small"
            onClick={() => loadKbsForBind(record.id)}
          >
            {t('apps.bindKb')}
          </Button>
          {record.status === 'ENABLED' ? (
            <Button size="small" danger onClick={() => confirmDisableApp(record)}>
              {t('apps.disable')}
            </Button>
          ) : (
            <Button size="small" onClick={() => handleEnableApp(record)}>
              {t('apps.enable')}
            </Button>
          )}
        </Space>
      ),
    },
  ]

  return (
    <div>
      <Space style={{ marginBottom: 16 }}>
        <Button type="primary" onClick={() => { setCreateOpen(true); setError(null) }}>
          {t('apps.create')}
        </Button>
        <Button onClick={fetchApps}>{t('apps.refresh')}</Button>
      </Space>

      {error && (
        <Alert type="error" message={t('apps.error')} description={error} closable onClose={() => setError(null)} style={{ marginBottom: 16 }} />
      )}

      <Table
        rowKey="id"
        columns={columns}
        dataSource={apps}
        loading={loading}
        locale={{ emptyText: t('apps.empty') }}
        pagination={false}
        scroll={{ x: 900 }}
      />

      <Modal
        title={t('apps.createTitle')}
        open={createOpen}
        onCancel={() => { setCreateOpen(false); form.resetFields() }}
        onOk={handleCreate}
        confirmLoading={submitting}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item name="name" label={t('apps.column.name')} rules={[{ required: true, message: t('apps.nameRequired') }]}>
            <Input placeholder={t('apps.namePlaceholder')} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={t('apps.bindModelTitle')}
        open={bindModelOpen}
        onCancel={() => { setBindModelOpen(false); setSelectedModelConfigId(null) }}
        onOk={handleBindModel}
        okButtonProps={{ disabled: selectedModelConfigId === null }}
      >
        <Typography.Paragraph type="secondary">
          {t('apps.bindModelHint')}
        </Typography.Paragraph>
        <Select
          value={selectedModelConfigId}
          onChange={(v) => setSelectedModelConfigId(v)}
          placeholder={t('apps.bindModelPlaceholder')}
          style={{ width: '100%' }}
          options={modelConfigs.map(mc => ({
            value: mc.id,
            label: `${mc.name} (${mc.chat_model ?? '-'})`,
          }))}
        />
      </Modal>

      <Modal
        title={t('apps.bindKbTitle')}
        open={bindKbOpen}
        onCancel={() => { setBindKbOpen(false); setSelectedKbId(null) }}
        onOk={handleBindKb}
        okButtonProps={{ disabled: selectedKbId === null }}
      >
        <Typography.Paragraph type="secondary">
          {t('apps.bindKbHint')}
        </Typography.Paragraph>
        <Select
          value={selectedKbId}
          onChange={(v) => setSelectedKbId(v)}
          placeholder={t('apps.bindKbPlaceholder')}
          style={{ width: '100%' }}
          options={kbList.map(kb => ({
            value: kb.id,
            label: `${kb.name} (${kb.embedding_model}, ${kb.embedding_dimension}d)`,
          }))}
        />
      </Modal>
    </div>
  )
}
