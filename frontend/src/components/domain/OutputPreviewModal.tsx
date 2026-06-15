import { useState } from 'react'
import type { RequestLogOutputPreviewVO } from '../../types/request-log'
import { accessOutputPreview } from '../../api/request-logs'
import { Modal, Alert, Spin, Typography, Input, Space, Tag, Button } from 'antd'
import { useI18n } from '../../app/i18n'

const { Text, Paragraph } = Typography
const { TextArea } = Input

interface OutputPreviewModalProps {
  open: boolean
  appId: number
  requestId: string
  adminUserId: number
  onClose: () => void
}

export default function OutputPreviewModal({
  open,
  appId,
  requestId,
  adminUserId,
  onClose,
}: OutputPreviewModalProps) {
  const { t } = useI18n()
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [preview, setPreview] = useState<RequestLogOutputPreviewVO | null>(null)
  const [reason, setReason] = useState('')
  const [requested, setRequested] = useState(false)

  function handleClose() {
    setPreview(null)
    setError(null)
    setReason('')
    setRequested(false)
    onClose()
  }

  function handleAccess() {
    setLoading(true)
    setError(null)
    accessOutputPreview(appId, requestId, adminUserId, {
      confirm_access: true,
      reason: reason.trim() || undefined,
    })
      .then((res) => {
        if (res.code !== 'OK') {
          setError(res.message)
        } else {
          setPreview(res.data)
        }
        setRequested(true)
      })
      .catch((e: Error) => {
        setError(e.message)
        setRequested(true)
      })
      .finally(() => setLoading(false))
  }

  function renderStatusTag(status: string | null) {
    if (!status) return '-'
    let color = 'default'
    if (status === 'DISABLED') color = 'default'
    else if (status === 'CAPTURED') color = 'green'
    else if (status === 'EMPTY') color = 'orange'
    else if (status === 'REDACTED') color = 'blue'
    else if (status === 'REDACTION_BLOCKED') color = 'red'
    else if (status === 'STREAMING_UNSUPPORTED') color = 'purple'
    else if (status === 'FAILED') color = 'red'
    else if (status === 'EXPIRED') color = 'default'
    return <Tag color={color}>{status}</Tag>
  }

  return (
    <Modal
      title={t('rl-preview.title')}
      open={open}
      onCancel={handleClose}
      footer={null}
      width={640}
      destroyOnClose
    >
      {!requested && !loading && (
        <>
          <Alert
            type="warning"
            message={t('rl-preview.confirmWarning')}
            description={t('rl-preview.confirmDescription')}
            style={{ marginBottom: 16 }}
          />
          <Space direction="vertical" style={{ width: '100%', marginBottom: 16 }}>
            <Text>{t('rl-preview.reasonLabel')}</Text>
            <TextArea
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              placeholder={t('rl-preview.reasonPlaceholder')}
              maxLength={256}
              rows={2}
            />
          </Space>
          <Space>
            <Button type="primary" onClick={handleAccess}>
              {t('rl-preview.confirmButton')}
            </Button>
            <Button onClick={handleClose}>
              {t('rl-preview.cancel')}
            </Button>
          </Space>
        </>
      )}

      {loading && (
        <Spin tip={t('rl-preview.loading')} style={{ display: 'block', textAlign: 'center', padding: 48 }}>
          <div style={{ height: 80 }} />
        </Spin>
      )}

      {error && (
        <Alert
          type="error"
          message={t('rl-preview.error')}
          description={error}
          style={{ marginBottom: 16 }}
        />
      )}

      {requested && preview && (
        <>
          <Space direction="vertical" style={{ width: '100%', marginBottom: 16 }}>
            <Text strong>{t('rl-preview.status')}:</Text>
            {renderStatusTag(preview.output_capture_status)}
          </Space>
          <Space direction="vertical" style={{ width: '100%', marginBottom: 16 }}>
            <Text strong>{t('rl-preview.completionLength')}: {preview.completion_length ?? '-'}</Text>
          </Space>
          {preview.output_preview && (
            <Space direction="vertical" style={{ width: '100%', marginBottom: 16 }}>
              <Text strong>{t('rl-preview.preview')}:</Text>
              <Paragraph
                style={{
                  background: '#f5f5f5',
                  padding: 12,
                  borderRadius: 6,
                  maxHeight: 300,
                  overflow: 'auto',
                  whiteSpace: 'pre-wrap',
                  wordBreak: 'break-word',
                  fontSize: 13,
                  fontFamily: 'monospace',
                  userSelect: 'none',
                }}
              >
                {preview.output_preview}
              </Paragraph>
            </Space>
          )}
          {!preview.output_preview && (
            <Alert
              type="info"
              message={t('rl-preview.noPreviewAvailable')}
              description={t('rl-preview.statusReason', { status: preview.output_capture_status })}
              style={{ marginBottom: 16 }}
            />
          )}
          {preview.output_preview_truncated && (
            <Alert
              type="warning"
              message={t('rl-preview.truncatedWarning')}
              style={{ marginBottom: 16 }}
            />
          )}
          {preview.output_redacted && (
            <Alert
              type="info"
              message={t('rl-preview.redactedNotice')}
              style={{ marginBottom: 16 }}
            />
          )}
          <Button onClick={handleClose}>
            {t('rl-preview.close')}
          </Button>
        </>
      )}
    </Modal>
  )
}
