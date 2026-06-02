import { useCallback, useEffect, useState } from 'react'
import {
  Table, Button, Modal, Form, Input, Select, Space, Alert, InputNumber,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import type { ModelConfigVO, CreateModelConfigDTO, ModelConfigStatus } from '../../types/model-config'
import { ApiError } from '../../api/http'
import { listModelConfigs, createModelConfig, disableModelConfig, enableModelConfig } from '../../api/model-configs'
import { useShell } from '../../components/layout/AdminShell'
import StatusTag from '../../components/domain/StatusTag'

export default function ModelConfigPage() {
  const { adminUserId } = useShell()

  const [configs, setConfigs] = useState<ModelConfigVO[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [statusFilter, setStatusFilter] = useState<string | undefined>(undefined)

  const [createOpen, setCreateOpen] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [form] = Form.useForm<CreateModelConfigDTO>()

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
      setError(e instanceof ApiError ? e.message : (e instanceof Error ? e.message : 'Network error'))
      setConfigs([])
    } finally {
      setLoading(false)
    }
  }, [adminUserId, statusFilter])

  useEffect(() => {
    fetchConfigs()
  }, [fetchConfigs])

  async function handleCreate() {
    if (adminUserId === null) return
    try {
      const values = await form.validateFields()
      if (values.embedding_model && (!values.embedding_dimension || values.embedding_dimension <= 0)) {
        setError('Embedding dimension must be a positive integer when embedding model is set')
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
      title: 'Disable Model Config',
      content: `Disable "${record.name}"? Apps bound to this config will fail /v1/models and /v1/chat/completions with 409 model_config_not_ready until re-enabled.`,
      okText: 'Disable',
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          const res = await disableModelConfig(record.id, adminUserId)
          if (res.code !== 'OK') setError(res.message)
          else fetchConfigs()
        } catch (e: unknown) {
          setError(e instanceof ApiError ? e.message : (e instanceof Error ? e.message : 'Network error'))
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
      setError(e instanceof ApiError ? e.message : (e instanceof Error ? e.message : 'Network error'))
    }
  }

  const columns: ColumnsType<ModelConfigVO> = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 60 },
    { title: 'Name', dataIndex: 'name', key: 'name', width: 160 },
    { title: 'Provider', dataIndex: 'provider_name', key: 'provider_name', width: 130 },
    { title: 'Chat Model', dataIndex: 'chat_model', key: 'chat_model', width: 150 },
    {
      title: 'Embedding Model',
      dataIndex: 'embedding_model',
      key: 'embedding_model',
      width: 150,
      render: (v: string | null) => v ?? '-',
    },
    {
      title: 'Embedding Dim',
      dataIndex: 'embedding_dimension',
      key: 'embedding_dimension',
      width: 120,
      render: (v: number | null) => v ?? '-',
    },
    {
      title: 'API Key',
      dataIndex: 'api_key_masked',
      key: 'api_key_masked',
      width: 150,
      render: (v: string | null) => v ?? '-',
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (s: ModelConfigStatus) => <StatusTag status={s} />,
    },
    {
      title: 'Action',
      key: 'action',
      width: 100,
      render: (_: unknown, record: ModelConfigVO) => (
        record.status === 'ENABLED' ? (
          <Button size="small" danger onClick={() => handleDisable(record)}>
            Disable
          </Button>
        ) : (
          <Button size="small" onClick={() => handleEnable(record.id)}>
            Enable
          </Button>
        )
      ),
    },
  ]

  return (
    <div>
      <Space style={{ marginBottom: 16 }}>
        <Select
          value={statusFilter}
          onChange={(v) => setStatusFilter(v)}
          placeholder="Status"
          allowClear
          style={{ width: 140 }}
          options={[
            { value: 'ENABLED', label: 'Enabled' },
            { value: 'DISABLED', label: 'Disabled' },
          ]}
        />
        <Button type="primary" onClick={() => { setCreateOpen(true); setError(null) }}>
          Create Config
        </Button>
        <Button onClick={fetchConfigs}>Refresh</Button>
      </Space>

      {error && (
        <Alert
          type="error" message="Error" description={error}
          closable onClose={() => setError(null)}
          style={{ marginBottom: 16 }}
        />
      )}

      <Table
        rowKey="id"
        columns={columns}
        dataSource={configs}
        loading={loading}
        locale={{ emptyText: error ? ' ' : 'No model configs found' }}
        pagination={false}
        scroll={{ x: 1100 }}
      />

      <Modal
        title="Create Model Config"
        open={createOpen}
        onCancel={() => { setCreateOpen(false); form.resetFields() }}
        onOk={handleCreate}
        confirmLoading={submitting}
        destroyOnClose
      >
        <Form form={form} layout="vertical" initialValues={{ provider_name: 'openai-compatible', chat_model: '' }}>
          <Form.Item name="name" label="Name" rules={[{ required: true, message: 'Name is required' }]}>
            <Input placeholder="Display name" />
          </Form.Item>
          <Form.Item name="provider_name" label="Provider" rules={[{ required: true }]}>
            <Input placeholder="openai-compatible" />
          </Form.Item>
          <Form.Item name="base_url" label="Base URL" rules={[{ required: true, message: 'Base URL is required' }]}>
            <Input placeholder="https://api.example.com" />
          </Form.Item>
          <Form.Item name="api_key" label="Upstream API Key" rules={[{ required: true, message: 'API key is required' }]}>
            <Input.Password placeholder="sk-..." />
          </Form.Item>
          <Form.Item name="chat_model" label="Chat Model" rules={[{ required: true, message: 'Chat model is required' }]}>
            <Input placeholder="deepseek-v4-pro" />
          </Form.Item>
          <Form.Item name="embedding_model" label="Embedding Model">
            <Input placeholder="text-embedding-v4" />
          </Form.Item>
          <Form.Item name="embedding_dimension" label="Embedding Dimension">
            <InputNumber min={1} placeholder="1024" style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
