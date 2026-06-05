import { useCallback, useEffect, useState } from 'react'
import {
  Table, Button, Modal, Form, Input, Select, Space, Alert, InputNumber,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import type { ModelConfigVO, CreateModelConfigDTO, UpdateModelConfigDTO, ModelConfigStatus } from '../../types/model-config'
import { ApiError } from '../../api/http'
import { listModelConfigs, createModelConfig, updateModelConfig, disableModelConfig, enableModelConfig } from '../../api/model-configs'
import { useShell } from '../../components/layout/AdminShell'
import StatusTag from '../../components/domain/StatusTag'
import { useI18n } from '../../app/i18n'

type EditModelConfigFormValues = Omit<UpdateModelConfigDTO, 'api_key'> & {
  api_key?: string
}

export default function ModelConfigPage() {
  const { adminUserId } = useShell()
  const { t } = useI18n()

  const [configs, setConfigs] = useState<ModelConfigVO[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [statusFilter, setStatusFilter] = useState<string | undefined>(undefined)

  const [createOpen, setCreateOpen] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [form] = Form.useForm<CreateModelConfigDTO>()

  const [editOpen, setEditOpen] = useState(false)
  const [editSubmitting, setEditSubmitting] = useState(false)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [editForm] = Form.useForm<EditModelConfigFormValues>()

  function validateEmbeddingFields(embeddingModel: string | null | undefined, embeddingDimension: number | null | undefined): string | null {
    const hasEmbeddingModel = typeof embeddingModel === 'string' && embeddingModel.trim().length > 0
    const hasEmbeddingDimension = embeddingDimension !== null && embeddingDimension !== undefined

    if (hasEmbeddingDimension && embeddingDimension <= 0) {
      return t('model-config.embeddingDimPositive')
    }
    if (hasEmbeddingModel && !hasEmbeddingDimension) {
      return t('model-config.embeddingDimRequired')
    }
    if (!hasEmbeddingModel && hasEmbeddingDimension) {
      return t('model-config.embeddingModelRequired')
    }
    return null
  }

  const fetchConfigs = useCallback(async () => {
    if (adminUserId === null) return
    setLoading(true)
    setError(null)
    try {
      const res = await listModelConfigs(statusFilter, adminUserId)
      if (res.code !== 'OK') {
        setError(res.message)
        setConfigs([])
      } else {
        setConfigs(res.data)
      }
    } catch (e: unknown) {
      setError(e instanceof ApiError ? e.message : (e instanceof Error ? e.message : t('model-config.networkError')))
      setConfigs([])
    } finally {
      setLoading(false)
    }
  }, [adminUserId, statusFilter, t])

  useEffect(() => {
    fetchConfigs()
  }, [fetchConfigs])

  async function handleCreate() {
    if (adminUserId === null) return
    try {
      const values = await form.validateFields()
      const embeddingError = validateEmbeddingFields(values.embedding_model, values.embedding_dimension)
      if (embeddingError) {
        setError(embeddingError)
        return
      }
      setSubmitting(true)
      setError(null)
      const dto: CreateModelConfigDTO = {
        name: values.name,
        provider_name: values.provider_name,
        base_url: values.base_url,
        api_key: values.api_key,
        chat_model: values.chat_model,
        embedding_model: values.embedding_model || null,
        embedding_dimension: values.embedding_dimension || null,
      }
      const res = await createModelConfig(dto, adminUserId)
      if (res.code !== 'OK') {
        setError(res.message)
      } else {
        setCreateOpen(false)
        form.resetFields()
        fetchConfigs()
      }
    } catch (e: unknown) {
      if (e instanceof ApiError) setError(e.message)
      else if (e instanceof Error) setError(e.message)
    } finally {
      setSubmitting(false)
    }
  }

  function handleDisable(record: ModelConfigVO) {
    if (adminUserId === null) return
    Modal.confirm({
      title: t('model-config.disableTitle'),
      content: t('model-config.disableContent', { name: record.name }),
      okText: t('model-config.disableOk'),
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          const res = await disableModelConfig(record.id, adminUserId)
          if (res.code !== 'OK') setError(res.message)
          else fetchConfigs()
        } catch (e: unknown) {
          setError(e instanceof ApiError ? e.message : (e instanceof Error ? e.message : t('model-config.networkError')))
        }
      },
    })
  }

  async function handleEnable(id: number) {
    if (adminUserId === null) return
    try {
      const res = await enableModelConfig(id, adminUserId)
      if (res.code !== 'OK') setError(res.message)
      else fetchConfigs()
    } catch (e: unknown) {
      setError(e instanceof ApiError ? e.message : (e instanceof Error ? e.message : t('model-config.networkError')))
    }
  }

  function handleEdit(record: ModelConfigVO) {
    setEditingId(record.id)
    editForm.setFieldsValue({
      name: record.name,
      provider_name: record.provider_name,
      base_url: record.base_url,
      chat_model: record.chat_model,
      embedding_model: record.embedding_model ?? '',
      embedding_dimension: record.embedding_dimension ?? null,
      api_key: '',
    })
    setEditOpen(true)
    setError(null)
  }

  async function handleEditSubmit() {
    if (adminUserId === null || editingId === null) return
    try {
      const values = await editForm.validateFields()
      const apiKeyRaw: string = values.api_key ?? ''
      const apiKeyTrimmed = apiKeyRaw.trim()

      const embeddingError = validateEmbeddingFields(values.embedding_model, values.embedding_dimension)
      if (embeddingError) {
        setError(embeddingError)
        return
      }

      const dto: UpdateModelConfigDTO = {
        name: values.name,
        provider_name: values.provider_name,
        base_url: values.base_url,
        chat_model: values.chat_model,
        embedding_model: values.embedding_model || null,
        embedding_dimension: values.embedding_dimension || null,
      }
      if (apiKeyTrimmed.length > 0) {
        dto.api_key = apiKeyTrimmed
      }

      setEditSubmitting(true)
      setError(null)
      const res = await updateModelConfig(editingId, dto, adminUserId)
      if (res.code !== 'OK') {
        setError(res.message)
      } else {
        setEditOpen(false)
        editForm.resetFields()
        setEditingId(null)
        fetchConfigs()
      }
    } catch (e: unknown) {
      if (e instanceof ApiError) setError(e.message)
      else if (e instanceof Error) setError(e.message)
    } finally {
      setEditSubmitting(false)
    }
  }

  const columns: ColumnsType<ModelConfigVO> = [
    { title: t('model-config.column.id'), dataIndex: 'id', key: 'id', width: 60 },
    { title: t('model-config.column.name'), dataIndex: 'name', key: 'name', width: 160 },
    { title: t('model-config.column.provider'), dataIndex: 'provider_name', key: 'provider_name', width: 130 },
    { title: t('model-config.column.chatModel'), dataIndex: 'chat_model', key: 'chat_model', width: 150 },
    {
      title: t('model-config.column.embeddingModel'),
      dataIndex: 'embedding_model',
      key: 'embedding_model',
      width: 150,
      render: (v: string | null) => v ?? '-',
    },
    {
      title: t('model-config.column.embeddingDim'),
      dataIndex: 'embedding_dimension',
      key: 'embedding_dimension',
      width: 120,
      render: (v: number | null) => v ?? '-',
    },
    {
      title: t('model-config.column.apiKey'),
      dataIndex: 'api_key_masked',
      key: 'api_key_masked',
      width: 150,
      render: (v: string | null) => v ?? '-',
    },
    {
      title: t('model-config.column.status'),
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (s: ModelConfigStatus) => <StatusTag status={s} />,
    },
    {
      title: t('model-config.column.action'),
      key: 'action',
      width: 160,
      render: (_: unknown, record: ModelConfigVO) => (
        <Space size="small">
          <Button size="small" onClick={() => handleEdit(record)}>
            {t('model-config.edit')}
          </Button>
          {record.status === 'ENABLED' ? (
            <Button size="small" danger onClick={() => handleDisable(record)}>
              {t('model-config.disable')}
            </Button>
          ) : (
            <Button size="small" onClick={() => handleEnable(record.id)}>
              {t('model-config.enable')}
            </Button>
          )}
        </Space>
      ),
    },
  ]

  return (
    <div>
      <Space style={{ marginBottom: 16 }}>
        <Select
          value={statusFilter}
          onChange={(v) => setStatusFilter(v)}
          placeholder={t('model-config.statusFilter')}
          allowClear
          style={{ width: 140 }}
          options={[
            { value: 'ENABLED', label: t('model-config.statusEnabled') },
            { value: 'DISABLED', label: t('model-config.statusDisabled') },
          ]}
        />
        <Button type="primary" onClick={() => { setCreateOpen(true); setError(null) }}>
          {t('model-config.create')}
        </Button>
        <Button onClick={fetchConfigs}>{t('model-config.refresh')}</Button>
      </Space>

      {error && (
        <Alert
          type="error" message={t('model-config.error')} description={error}
          closable onClose={() => setError(null)}
          style={{ marginBottom: 16 }}
        />
      )}

      <Table
        rowKey="id"
        columns={columns}
        dataSource={configs}
        loading={loading}
        locale={{ emptyText: error ? ' ' : t('model-config.empty') }}
        pagination={false}
        scroll={{ x: 1100 }}
      />

      <Modal
        title={t('model-config.createTitle')}
        open={createOpen}
        onCancel={() => { setCreateOpen(false); form.resetFields() }}
        onOk={handleCreate}
        confirmLoading={submitting}
        destroyOnClose
      >
        <Form form={form} layout="vertical" initialValues={{ provider_name: 'openai-compatible', chat_model: '' }}>
          <Form.Item name="name" label={t('model-config.column.name')} rules={[{ required: true, message: t('model-config.nameRequired') }]}>
            <Input placeholder={t('model-config.namePlaceholder')} />
          </Form.Item>
          <Form.Item name="provider_name" label={t('model-config.provider')} rules={[{ required: true }]}>
            <Input placeholder="openai-compatible" />
          </Form.Item>
          <Form.Item name="base_url" label="Base URL" rules={[{ required: true, message: t('model-config.baseUrlRequired') }]}>
            <Input placeholder={t('model-config.baseUrlPlaceholder')} />
          </Form.Item>
          <Form.Item name="api_key" label={t('model-config.upstreamKeyLabel')} rules={[{ required: true, message: t('model-config.upstreamKeyRequired') }]}>
            <Input.Password placeholder={t('model-config.upstreamKeyPlaceholder')} />
          </Form.Item>
          <Form.Item name="chat_model" label={t('model-config.column.chatModel')} rules={[{ required: true, message: t('model-config.chatModelRequired') }]}>
            <Input placeholder={t('model-config.chatModelPlaceholder')} />
          </Form.Item>
          <Form.Item name="embedding_model" label={t('model-config.embeddingModelLabel')}>
            <Input placeholder={t('model-config.embeddingModelPlaceholder')} />
          </Form.Item>
          <Form.Item name="embedding_dimension" label={t('model-config.embeddingDimLabel')}>
            <InputNumber min={1} placeholder={t('model-config.embeddingDimPlaceholder')} style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={t('model-config.editTitle')}
        open={editOpen}
        onCancel={() => { setEditOpen(false); editForm.resetFields(); setEditingId(null) }}
        onOk={handleEditSubmit}
        confirmLoading={editSubmitting}
        destroyOnClose
      >
        <Form form={editForm} layout="vertical">
          <Form.Item name="name" label={t('model-config.column.name')} rules={[{ required: true, message: t('model-config.nameRequired') }]}>
            <Input placeholder={t('model-config.namePlaceholder')} />
          </Form.Item>
          <Form.Item name="provider_name" label={t('model-config.provider')} rules={[{ required: true }]}>
            <Input placeholder="openai-compatible" />
          </Form.Item>
          <Form.Item name="base_url" label="Base URL" rules={[{ required: true, message: t('model-config.baseUrlRequired') }]}>
            <Input placeholder={t('model-config.baseUrlPlaceholder')} />
          </Form.Item>
          <Form.Item name="api_key" label={t('model-config.upstreamKeyLabel')} extra={t('model-config.editKeyHint')}>
            <Input.Password placeholder={t('model-config.editKeyPlaceholder')} />
          </Form.Item>
          <Form.Item name="chat_model" label={t('model-config.column.chatModel')} rules={[{ required: true, message: t('model-config.chatModelRequired') }]}>
            <Input placeholder={t('model-config.chatModelPlaceholder')} />
          </Form.Item>
          <Form.Item name="embedding_model" label={t('model-config.embeddingModelLabel')}>
            <Input placeholder={t('model-config.embeddingModelPlaceholder')} />
          </Form.Item>
          <Form.Item name="embedding_dimension" label={t('model-config.embeddingDimLabel')}>
            <InputNumber min={1} placeholder={t('model-config.embeddingDimPlaceholder')} style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
