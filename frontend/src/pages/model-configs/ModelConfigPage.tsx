import { useCallback, useEffect, useState } from 'react'
import {
  Table, Button, Modal, Form, Input, Select, Space, Alert, InputNumber, Radio, Typography, Tag, Descriptions,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import type {
  ModelConfigVO, CreateModelConfigDTO, UpdateModelConfigDTO, ModelConfigStatus,
  ModelConfigCapability, ModelConfigCheckRequest, ModelConfigCheckResult,
} from '../../types/model-config'
import { ApiError } from '../../api/http'
import {
  listModelConfigs, createModelConfig, updateModelConfig, disableModelConfig, enableModelConfig,
  checkUnsavedModelConfig, checkSavedModelConfig,
} from '../../api/model-configs'
import { useShell } from '../../components/layout/AdminShell'
import StatusTag from '../../components/domain/StatusTag'
import { useI18n } from '../../app/i18n'

type EditModelConfigFormValues = Omit<UpdateModelConfigDTO, 'api_key'> & {
  api_key?: string
}

const CAPABILITY_OPTIONS: { value: ModelConfigCapability; label: string }[] = [
  { value: 'CHAT', label: 'CHAT' },
  { value: 'EMBEDDING', label: 'EMBEDDING' },
]

export default function ModelConfigPage() {
  const { adminUserId } = useShell()
  const { t } = useI18n()

  const [configs, setConfigs] = useState<ModelConfigVO[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [statusFilter, setStatusFilter] = useState<ModelConfigStatus | undefined>(undefined)
  const [capabilityFilter, setCapabilityFilter] = useState<ModelConfigCapability | undefined>(undefined)

  const [createOpen, setCreateOpen] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [form] = Form.useForm<CreateModelConfigDTO>()
  const [createCapability, setCreateCapability] = useState<ModelConfigCapability>('CHAT')

  const [editOpen, setEditOpen] = useState(false)
  const [editSubmitting, setEditSubmitting] = useState(false)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [editForm] = Form.useForm<EditModelConfigFormValues>()
  const [editCapability, setEditCapability] = useState<ModelConfigCapability>('CHAT')

  const [checkOpen, setCheckOpen] = useState(false)
  const [checking, setChecking] = useState(false)
  const [checkResult, setCheckResult] = useState<ModelConfigCheckResult | null>(null)
  const [checkConfigId, setCheckConfigId] = useState<number | null>(null)
  const [checkForm] = Form.useForm<ModelConfigCheckRequest>()
  const checkCapability = Form.useWatch('capability', checkForm)

  const needsChatModel = (cap: ModelConfigCapability) => cap === 'CHAT'
  const needsEmbeddingModel = (cap: ModelConfigCapability) => cap === 'EMBEDDING'
  const capabilityOptions = CAPABILITY_OPTIONS.map(option => ({
    value: option.value,
    label: t(`model-config.capability${option.value}`),
  }))

  const fetchConfigs = useCallback(async () => {
    if (adminUserId === null) return
    setLoading(true)
    setError(null)
    try {
      const res = await listModelConfigs(statusFilter, adminUserId, capabilityFilter)
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
  }, [adminUserId, statusFilter, capabilityFilter, t])

  useEffect(() => {
    fetchConfigs()
  }, [fetchConfigs])

  async function handleCreate() {
    if (adminUserId === null) return
    try {
      const values = await form.validateFields()
      setSubmitting(true)
      setError(null)
      const dto: CreateModelConfigDTO = {
        capability: createCapability,
        name: values.name,
        provider_name: values.provider_name,
        base_url: values.base_url,
        api_key: values.api_key,
        chat_model: needsChatModel(createCapability) ? (values.chat_model || '') : null,
        embedding_model: needsEmbeddingModel(createCapability) ? (values.embedding_model || '') : null,
        embedding_dimension: needsEmbeddingModel(createCapability) ? (values.embedding_dimension ?? null) : null,
      }
      const res = await createModelConfig(dto, adminUserId)
      if (res.code !== 'OK') {
        setError(res.message)
      } else {
        setCreateOpen(false)
        form.resetFields()
        setCreateCapability('CHAT')
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
      chat_model: record.chat_model ?? '',
      embedding_model: record.embedding_model ?? '',
      embedding_dimension: record.embedding_dimension ?? null,
      api_key: '',
    })
    setEditCapability(record.capability === 'EMBEDDING' ? 'EMBEDDING' : 'CHAT')
    setEditOpen(true)
    setError(null)
  }

  async function handleEditSubmit() {
    if (adminUserId === null || editingId === null) return
    try {
      const values = await editForm.validateFields()
      const apiKeyRaw: string = values.api_key ?? ''
      const apiKeyTrimmed = apiKeyRaw.trim()

      const dto: UpdateModelConfigDTO = {
        capability: editCapability,
        name: values.name,
        provider_name: values.provider_name,
        base_url: values.base_url,
        chat_model: needsChatModel(editCapability) ? (values.chat_model ?? '') : null,
        embedding_model: needsEmbeddingModel(editCapability) ? (values.embedding_model ?? '') : null,
        embedding_dimension: needsEmbeddingModel(editCapability) ? (values.embedding_dimension ?? null) : null,
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

  function handleOpenCheck(record: ModelConfigVO | null) {
    const inheritedCapability: ModelConfigCapability =
      record?.capability === 'EMBEDDING'
        || (record?.capability === 'CHAT_EMBEDDING' && record.embedding_model)
        ? 'EMBEDDING'
        : 'CHAT'
    setCheckConfigId(record?.id ?? null)
    setCheckResult(null)
    checkForm.resetFields()
    checkForm.setFieldsValue({ capability: inheritedCapability })
    setCheckOpen(true)
  }

  async function handleRunCheck() {
    if (adminUserId === null) return
    try {
      const values = await checkForm.validateFields()
      const selectedCapability = values.capability
      setChecking(true)
      setError(null)
      const request: ModelConfigCheckRequest = {
        capability: selectedCapability,
        provider_name: values.provider_name || undefined,
        base_url: values.base_url || undefined,
        api_key: values.api_key || undefined,
        chat_model: selectedCapability === 'CHAT' ? (values.chat_model || undefined) : undefined,
        embedding_model: selectedCapability === 'EMBEDDING' ? (values.embedding_model || undefined) : undefined,
        embedding_dimension: selectedCapability === 'EMBEDDING' ? values.embedding_dimension : undefined,
      }
      let res
      if (checkConfigId !== null) {
        res = await checkSavedModelConfig(checkConfigId, request, adminUserId)
      } else {
        if (!request.capability || !request.base_url || !request.api_key) {
          setError(t('model-config.checkMissingRequired'))
          setChecking(false)
          return
        }
        res = await checkUnsavedModelConfig(request, adminUserId)
      }
      if (res.code !== 'OK') {
        setError(res.message)
        setCheckResult(null)
      } else {
        setCheckResult(res.data)
      }
    } catch (e: unknown) {
      if (e instanceof ApiError) setError(e.message)
      else if (e instanceof Error) setError(e.message)
      setCheckResult(null)
    } finally {
      setChecking(false)
    }
  }

  function handleFillDimension(dimension: number) {
    editForm.setFieldsValue({ embedding_dimension: dimension })
    form.setFieldsValue({ embedding_dimension: dimension })
  }

  function handleFillDimensionFromCheckResult() {
    const dimension = checkResult?.embedding?.actual_dimension
    if (dimension != null) {
      handleFillDimension(dimension)
    }
  }

  const columns: ColumnsType<ModelConfigVO> = [
    { title: t('model-config.column.id'), dataIndex: 'id', key: 'id', width: 60 },
    { title: t('model-config.column.name'), dataIndex: 'name', key: 'name', width: 140 },
    {
      title: t('model-config.capabilityLabel'),
      dataIndex: 'capability',
      key: 'capability',
      width: 130,
      render: (v: string) => (
        <Tag color={v === 'CHAT' ? 'blue' : v === 'EMBEDDING' ? 'green' : 'default'}>{v}</Tag>
      ),
    },
    { title: t('model-config.column.provider'), dataIndex: 'provider_name', key: 'provider_name', width: 110 },
    {
      title: t('model-config.column.chatModel'),
      dataIndex: 'chat_model',
      key: 'chat_model',
      width: 130,
      render: (v: string | null) => v ?? '-',
    },
    {
      title: t('model-config.column.embeddingModel'),
      dataIndex: 'embedding_model',
      key: 'embedding_model',
      width: 130,
      render: (v: string | null) => v ?? '-',
    },
    {
      title: t('model-config.column.embeddingDim'),
      dataIndex: 'embedding_dimension',
      key: 'embedding_dimension',
      width: 100,
      render: (v: number | null) => v ?? '-',
    },
    {
      title: t('model-config.column.apiKey'),
      dataIndex: 'api_key_masked',
      key: 'api_key_masked',
      width: 130,
      render: (v: string | null) => v ?? '-',
    },
    {
      title: t('model-config.column.status'),
      dataIndex: 'status',
      key: 'status',
      width: 90,
      render: (s: ModelConfigStatus) => <StatusTag status={s} />,
    },
    {
      title: t('model-config.column.action'),
      key: 'action',
      width: 200,
      render: (_: unknown, record: ModelConfigVO) => (
        <Space size="small">
          <Button size="small" onClick={() => handleEdit(record)}>
            {t('model-config.edit')}
          </Button>
          <Button size="small" onClick={() => handleOpenCheck(record)}>
            {t('model-config.checkButton')}
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
      <Space style={{ marginBottom: 16 }} wrap>
        <Select
          value={statusFilter}
          onChange={(v) => setStatusFilter(v)}
          placeholder={t('model-config.statusFilter')}
          allowClear
          style={{ width: 130 }}
          options={[
            { value: 'ENABLED', label: t('model-config.statusEnabled') },
            { value: 'DISABLED', label: t('model-config.statusDisabled') },
          ]}
        />
        <Select
          value={capabilityFilter}
          onChange={(v) => setCapabilityFilter(v)}
          placeholder={t('model-config.capabilityFilter')}
          allowClear
          style={{ width: 140 }}
          options={[
            { value: 'CHAT', label: 'CHAT' },
            { value: 'EMBEDDING', label: 'EMBEDDING' },
          ]}
        />
        <Button type="primary" onClick={() => { setCreateOpen(true); setError(null) }}>
          {t('model-config.create')}
        </Button>
        <Button onClick={() => handleOpenCheck(null)}>{t('model-config.checkUnsaved')}</Button>
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
        scroll={{ x: 1300 }}
      />

      <Modal
        title={t('model-config.createTitle')}
        open={createOpen}
        onCancel={() => { setCreateOpen(false); form.resetFields(); setCreateCapability('CHAT') }}
        onOk={handleCreate}
        confirmLoading={submitting}
        destroyOnClose
        width={560}
      >
        <Form form={form} layout="vertical" initialValues={{ provider_name: 'openai-compatible' }}>
          <Form.Item label={t('model-config.capabilityLabel')} required>
            <Radio.Group
              optionType="button"
              value={createCapability}
              onChange={(e) => setCreateCapability(e.target.value)}
              options={capabilityOptions}
            />
          </Form.Item>
          <Form.Item name="name" label={t('model-config.column.name')} rules={[{ required: true, message: t('model-config.nameRequired') }]}>
            <Input placeholder={t('model-config.namePlaceholder')} />
          </Form.Item>
          <Form.Item name="provider_name" label={t('model-config.provider')} rules={[{ required: true }]}>
            <Input placeholder="openai-compatible" />
          </Form.Item>
          <Form.Item name="base_url" label={t('model-config.baseUrlLabel')} rules={[{ required: true, message: t('model-config.baseUrlRequired') }]}>
            <Input placeholder={t('model-config.baseUrlPlaceholder')} />
          </Form.Item>
          <Form.Item name="api_key" label={t('model-config.upstreamKeyLabel')} rules={[{ required: true, message: t('model-config.upstreamKeyRequired') }]}>
            <Input.Password placeholder={t('model-config.upstreamKeyPlaceholder')} />
          </Form.Item>
          {needsChatModel(createCapability) && (
            <Form.Item name="chat_model" label={t('model-config.column.chatModel')} rules={[{ required: true, message: t('model-config.chatModelRequired') }]}>
              <Input placeholder={t('model-config.chatModelPlaceholder')} />
            </Form.Item>
          )}
          {needsEmbeddingModel(createCapability) && (
            <>
              <Form.Item name="embedding_model" label={t('model-config.embeddingModelLabel')} rules={[{ required: true, message: t('model-config.embeddingModelRequired') }]}>
                <Input placeholder={t('model-config.embeddingModelPlaceholder')} />
              </Form.Item>
              <Form.Item name="embedding_dimension" label={t('model-config.embeddingDimLabel')}>
                <InputNumber min={1} placeholder={t('model-config.embeddingDimPlaceholder')} style={{ width: '100%' }} />
              </Form.Item>
            </>
          )}
        </Form>
      </Modal>

      <Modal
        title={t('model-config.editTitle')}
        open={editOpen}
        onCancel={() => { setEditOpen(false); editForm.resetFields(); setEditingId(null) }}
        onOk={handleEditSubmit}
        confirmLoading={editSubmitting}
        destroyOnClose
        width={560}
      >
        <Form form={editForm} layout="vertical">
          <Form.Item label={t('model-config.capabilityLabel')}>
            <Radio.Group
              optionType="button"
              value={editCapability}
              onChange={(e) => setEditCapability(e.target.value)}
              options={capabilityOptions}
            />
          </Form.Item>
          <Form.Item name="name" label={t('model-config.column.name')} rules={[{ required: true, message: t('model-config.nameRequired') }]}>
            <Input placeholder={t('model-config.namePlaceholder')} />
          </Form.Item>
          <Form.Item name="provider_name" label={t('model-config.provider')} rules={[{ required: true }]}>
            <Input placeholder="openai-compatible" />
          </Form.Item>
          <Form.Item name="base_url" label={t('model-config.baseUrlLabel')} rules={[{ required: true, message: t('model-config.baseUrlRequired') }]}>
            <Input placeholder={t('model-config.baseUrlPlaceholder')} />
          </Form.Item>
          <Form.Item name="api_key" label={t('model-config.upstreamKeyLabel')} extra={t('model-config.editKeyHint')}>
            <Input.Password placeholder={t('model-config.editKeyPlaceholder')} />
          </Form.Item>
          {needsChatModel(editCapability) && (
            <Form.Item name="chat_model" label={t('model-config.column.chatModel')} rules={[{ required: true, message: t('model-config.chatModelRequired') }]}>
              <Input placeholder={t('model-config.chatModelPlaceholder')} />
            </Form.Item>
          )}
          {needsEmbeddingModel(editCapability) && (
            <>
              <Form.Item name="embedding_model" label={t('model-config.embeddingModelLabel')} rules={[{ required: true, message: t('model-config.embeddingModelRequired') }]}>
                <Input placeholder={t('model-config.embeddingModelPlaceholder')} />
              </Form.Item>
              <Form.Item name="embedding_dimension" label={t('model-config.embeddingDimLabel')}>
                <InputNumber min={1} placeholder={t('model-config.embeddingDimPlaceholder')} style={{ width: '100%' }} />
              </Form.Item>
            </>
          )}
        </Form>
      </Modal>

      <Modal
        title={t('model-config.checkTitle')}
        open={checkOpen}
        onCancel={() => { setCheckOpen(false); setCheckResult(null) }}
        onOk={handleRunCheck}
        confirmLoading={checking}
        destroyOnClose
        width={640}
        okText={t('model-config.checkRun')}
      >
        <Form form={checkForm} layout="vertical">
          <Form.Item
            name="capability"
            label={t('model-config.capabilityLabel')}
            rules={[{ required: true, message: t('model-config.checkMissingRequired') }]}
          >
            <Select
              placeholder={t('model-config.capabilityLabel')}
              options={capabilityOptions}
            />
          </Form.Item>
          {checkConfigId === null && (
            <>
              <Form.Item name="base_url" label={t('model-config.baseUrlLabel')} rules={[{ required: true, message: t('model-config.checkUnsavedBaseUrlRequired') }]}>
                <Input placeholder="https://api.example.com/v1" />
              </Form.Item>
              <Form.Item name="api_key" label={t('model-config.column.apiKey')} rules={[{ required: true, message: t('model-config.checkUnsavedApiKeyRequired') }]}>
                <Input.Password placeholder="sk-..." />
              </Form.Item>
            </>
          )}
          {checkCapability === 'CHAT' && (
            <Form.Item name="chat_model" label={t('model-config.column.chatModel')}>
              <Input placeholder={checkConfigId === null ? 'deepseek-v4-pro' : 'Override chat model'} />
            </Form.Item>
          )}
          {checkCapability === 'EMBEDDING' && (
            <>
              <Form.Item name="embedding_model" label={t('model-config.column.embeddingModel')}>
                <Input placeholder={checkConfigId === null ? 'text-embedding-v4' : 'Override embedding model'} />
              </Form.Item>
              <Form.Item name="embedding_dimension" label={t('model-config.checkConfiguredDim')}>
                <InputNumber min={1} style={{ width: '100%' }} />
              </Form.Item>
            </>
          )}
        </Form>

        {checkResult && (
          <div style={{ marginTop: 16 }}>
            <Typography.Title level={5}>
              {t('model-config.checkOverallStatus')} <Tag color={
                checkResult.overall_status === 'SUCCESS' ? 'green' :
                checkResult.overall_status === 'PARTIAL' ? 'orange' : 'red'
              }>{checkResult.overall_status}</Tag>
            </Typography.Title>
            <Typography.Text type="secondary">
              {t('model-config.checkBaseUrlChecked')} {checkResult.base_url_checked ? t('evidence.yes') : t('evidence.no')}
            </Typography.Text>

            {checkResult.chat && (
              <Descriptions title={t('model-config.checkChatTitle')} bordered size="small" column={1} style={{ marginTop: 12 }}>
                <Descriptions.Item label={t('evidence.status')}>
                  <Tag color={checkResult.chat.status === 'SUCCESS' ? 'green' : 'red'}>{checkResult.chat.status}</Tag>
                </Descriptions.Item>
                <Descriptions.Item label={t('evidence.model')}>{checkResult.chat.model}</Descriptions.Item>
                <Descriptions.Item label={t('model-config.checkMessage')}>{checkResult.chat.message}</Descriptions.Item>
              </Descriptions>
            )}

            {checkResult.embedding && (
              <Descriptions title={t('model-config.checkEmbeddingTitle')} bordered size="small" column={1} style={{ marginTop: 12 }}>
                <Descriptions.Item label={t('evidence.status')}>
                  <Tag color={checkResult.embedding.status === 'SUCCESS' ? 'green' : 'red'}>{checkResult.embedding.status}</Tag>
                </Descriptions.Item>
                <Descriptions.Item label={t('evidence.model')}>{checkResult.embedding.model}</Descriptions.Item>
                <Descriptions.Item label={t('model-config.checkActualDim')}>
                  {checkResult.embedding.actual_dimension != null ? (
                    <Space>
                      <Typography.Text strong>{checkResult.embedding.actual_dimension}</Typography.Text>
                      <Button size="small" onClick={handleFillDimensionFromCheckResult}>
                        {t('model-config.checkFillDimension')}
                      </Button>
                    </Space>
                  ) : '-'}
                </Descriptions.Item>
                <Descriptions.Item label={t('model-config.checkConfiguredDim')}>
                  {checkResult.embedding.configured_dimension ?? '-'}
                </Descriptions.Item>
                <Descriptions.Item label={t('model-config.checkMessage')}>{checkResult.embedding.message}</Descriptions.Item>
              </Descriptions>
            )}
          </div>
        )}
      </Modal>
    </div>
  )
}
