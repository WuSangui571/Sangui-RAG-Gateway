import { useCallback, useEffect, useRef, useState } from 'react'
import {
  Table, Button, Modal, Form, Input, InputNumber, Space, Typography, Alert, Upload,
} from 'antd'
import { UploadOutlined } from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import type { KnowledgeBaseVO, KnowledgeBaseStatus, CreateKnowledgeBaseDTO } from '../../types/knowledge'
import type { DocumentVO, DocumentStatus } from '../../types/document'
import { TERMINAL_DOCUMENT_STATUSES } from '../../types/document'
import { ApiError } from '../../api/http'
import { listKnowledgeBases, createKnowledgeBase } from '../../api/knowledge'
import { uploadDocument, listDocuments } from '../../api/documents'
import { useShell } from '../../components/layout/AdminShell'
import StatusTag from '../../components/domain/StatusTag'
import { useI18n } from '../../app/i18n'

const ALLOWED_EXTENSIONS = ['.txt', '.md', '.markdown']

function isNonTerminal(status: DocumentStatus): boolean {
  return !TERMINAL_DOCUMENT_STATUSES.has(status)
}

export default function KnowledgeBasePage() {
  const { adminUserId } = useShell()
  const { t } = useI18n()

  const [kbs, setKbs] = useState<KnowledgeBaseVO[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const [createOpen, setCreateOpen] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [form] = Form.useForm<CreateKnowledgeBaseDTO>()

  const [selectedKbId, setSelectedKbId] = useState<number | null>(null)
  const [documents, setDocuments] = useState<DocumentVO[]>([])
  const [docsLoading, setDocsLoading] = useState(false)
  const [docsError, setDocsError] = useState<string | null>(null)
  const pollTimerRef = useRef<number | null>(null)

  const fetchKbs = useCallback(async () => {
    if (adminUserId === null) return
    setLoading(true)
    setError(null)
    try {
      const res = await listKnowledgeBases(undefined, adminUserId)
      if (res.code !== 'OK') {
        setError(res.message)
        setKbs([])
      } else {
        setKbs(res.data)
      }
    } catch (e: unknown) {
      setError(e instanceof ApiError ? e.message : (e instanceof Error ? e.message : t('knowledge.networkError')))
      setKbs([])
    } finally {
      setLoading(false)
    }
  }, [adminUserId, t])

  useEffect(() => {
    fetchKbs()
  }, [fetchKbs])

  const fetchDocuments = useCallback(async () => {
    if (selectedKbId === null || adminUserId === null) return
    setDocsLoading(true)
    setDocsError(null)
    try {
      const res = await listDocuments(selectedKbId, undefined, adminUserId)
      if (res.code !== 'OK') {
        setDocsError(res.message)
        setDocuments([])
      } else {
        setDocuments(res.data)
      }
    } catch (e: unknown) {
      setDocsError(e instanceof ApiError ? e.message : (e instanceof Error ? e.message : t('knowledge.networkError')))
      setDocuments([])
    } finally {
      setDocsLoading(false)
    }
  }, [selectedKbId, adminUserId, t])

  useEffect(() => {
    if (pollTimerRef.current !== null) {
      clearTimeout(pollTimerRef.current)
      pollTimerRef.current = null
    }
    if (selectedKbId === null) {
      setDocuments([])
      return
    }
    fetchDocuments()
  }, [selectedKbId, fetchDocuments])

  useEffect(() => {
    const hasPending = documents.some(d => isNonTerminal(d.status))
    if (!hasPending) {
      if (pollTimerRef.current !== null) {
        clearTimeout(pollTimerRef.current)
        pollTimerRef.current = null
      }
      return
    }

    pollTimerRef.current = window.setTimeout(() => {
      fetchDocuments()
    }, 3000)

    return () => {
      if (pollTimerRef.current !== null) {
        clearTimeout(pollTimerRef.current)
        pollTimerRef.current = null
      }
    }
  }, [documents, fetchDocuments])

  async function handleCreate() {
    if (adminUserId === null) return
    try {
      const values = await form.validateFields()
      setSubmitting(true)
      setError(null)
      const res = await createKnowledgeBase({
        name: values.name,
        embedding_model: values.embedding_model,
        embedding_dimension: values.embedding_dimension,
      }, adminUserId)
      if (res.code !== 'OK') {
        setError(res.message)
      } else {
        setCreateOpen(false)
        form.resetFields()
        fetchKbs()
      }
    } catch (e: unknown) {
      if (e instanceof ApiError) setError(e.message)
      else if (e instanceof Error) setError(e.message)
    } finally {
      setSubmitting(false)
    }
  }

  async function handleUpload(file: File) {
    if (selectedKbId === null || adminUserId === null) return
    setDocsError(null)
    try {
      const res = await uploadDocument(selectedKbId, file, adminUserId)
      if (res.code !== 'OK') {
        setDocsError(t('knowledge.uploadFailed', { message: res.message }))
      } else {
        fetchDocuments()
        fetchKbs()
      }
    } catch (e: unknown) {
      setDocsError(e instanceof ApiError ? e.message : (e instanceof Error ? e.message : 'Upload failed'))
    }
  }

  function selectKb(kb: KnowledgeBaseVO) {
    setSelectedKbId(kb.id)
    setDocsError(null)
  }

  const kbColumns: ColumnsType<KnowledgeBaseVO> = [
    { title: t('knowledge.column.id'), dataIndex: 'id', key: 'id', width: 60 },
    { title: t('knowledge.column.name'), dataIndex: 'name', key: 'name', width: 180, render: (name: string, record: KnowledgeBaseVO) => (
      <Button type="link" size="small" onClick={() => selectKb(record)}>{name}</Button>
    )},
    { title: t('knowledge.column.embeddingModel'), dataIndex: 'embedding_model', key: 'embedding_model', width: 160 },
    { title: t('knowledge.column.dim'), dataIndex: 'embedding_dimension', key: 'embedding_dimension', width: 80 },
    {
      title: t('knowledge.column.status'), dataIndex: 'status', key: 'status', width: 120,
      render: (s: KnowledgeBaseStatus) => <StatusTag status={s} />,
    },
  ]

  const docColumns: ColumnsType<DocumentVO> = [
    { title: t('knowledge.column.id'), dataIndex: 'id', key: 'id', width: 60 },
    {
      title: t('knowledge.column.filename'),
      dataIndex: 'original_filename',
      key: 'original_filename',
      width: 220,
      ellipsis: true,
    },
    { title: t('knowledge.column.size'), dataIndex: 'file_size', key: 'file_size', width: 90, render: (v: number) => `${(v / 1024).toFixed(1)}KB` },
    { title: t('knowledge.column.status'), dataIndex: 'status', key: 'status', width: 110, render: (s: DocumentStatus) => <StatusTag status={s} /> },
    { title: t('knowledge.column.chunks'), dataIndex: 'chunk_count', key: 'chunk_count', width: 80 },
    {
      title: t('knowledge.column.error'),
      dataIndex: 'error_message',
      key: 'error_message',
      ellipsis: true,
      render: (v: string | null) => v ?? '-',
    },
  ]

  function beforeUpload(file: File): boolean {
    const ext = '.' + file.name.split('.').pop()?.toLowerCase()
    if (!ALLOWED_EXTENSIONS.includes(ext)) {
      const exts = ALLOWED_EXTENSIONS.join(', ')
      setDocsError(t('knowledge.unsupportedFile', { ext: ext || '', allowed: exts }))
      return false
    }
    handleUpload(file)
    return false
  }

  return (
    <div>
      {error && (
        <Alert type="error" message={t('knowledge.error')} description={error} closable onClose={() => setError(null)} style={{ marginBottom: 16 }} />
      )}

      <Space style={{ marginBottom: 16 }}>
        <Button type="primary" onClick={() => { setCreateOpen(true); setError(null) }}>
          {t('knowledge.create')}
        </Button>
        <Button onClick={fetchKbs}>{t('knowledge.refresh')}</Button>
      </Space>

      <Table
        rowKey="id"
        columns={kbColumns}
        dataSource={kbs}
        loading={loading}
        locale={{ emptyText: t('knowledge.empty') }}
        pagination={false}
      />

      {selectedKbId !== null && (
        <div style={{ marginTop: 24 }}>
          <Typography.Title level={5}>
            {t('knowledge.documentsIn', { id: selectedKbId })}
          </Typography.Title>

          {docsError && (
            <Alert type="error" message={t('knowledge.docError')} description={docsError} closable onClose={() => setDocsError(null)} style={{ marginBottom: 8 }} />
          )}

          <Space style={{ marginBottom: 12 }}>
            <Upload beforeUpload={beforeUpload} showUploadList={false} accept=".txt,.md,.markdown">
              <Button icon={<UploadOutlined />} disabled={selectedKbId === null}>
                {t('knowledge.upload')}
              </Button>
            </Upload>
            <Button onClick={fetchDocuments} disabled={selectedKbId === null}>{t('knowledge.refreshDocs')}</Button>
          </Space>

          <Table
            rowKey="id"
            columns={docColumns}
            dataSource={documents}
            loading={docsLoading}
            locale={{ emptyText: t('knowledge.docEmpty') }}
            pagination={false}
          />
        </div>
      )}

      <Modal
        title={t('knowledge.createTitle')}
        open={createOpen}
        onCancel={() => { setCreateOpen(false); form.resetFields() }}
        onOk={handleCreate}
        confirmLoading={submitting}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item name="name" label={t('knowledge.column.name')} rules={[{ required: true, message: t('knowledge.nameRequired') }]}>
            <Input placeholder={t('knowledge.namePlaceholder')} />
          </Form.Item>
          <Form.Item name="embedding_model" label={t('knowledge.column.embeddingModel')} rules={[{ required: true, message: t('knowledge.embeddingModelRequired') }]}>
            <Input placeholder="text-embedding-v4" />
          </Form.Item>
          <Form.Item name="embedding_dimension" label={t('knowledge.column.dim')} rules={[{ required: true, message: t('knowledge.dimRequired'), type: 'number', min: 1 }]}>
            <InputNumber min={1} placeholder="1024" style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
