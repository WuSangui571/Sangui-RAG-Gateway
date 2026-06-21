import { useEffect, useRef, useState } from 'react'
import { Alert, Button, Divider, Input, Modal, Space, Typography } from 'antd'
import { useI18n } from '../../app/i18n'

const { Text, Paragraph } = Typography

interface ApiKeyOneTimeSecretProps {
  open: boolean
  plaintextKey: string | null
  onClose: () => void
  onGoToSmokeTest?: () => void
  origin?: string
}

export default function ApiKeyOneTimeSecret({ open, plaintextKey, onClose, onGoToSmokeTest, origin: originOverride }: ApiKeyOneTimeSecretProps) {
  const { t } = useI18n()
  const inputRef = useRef<HTMLTextAreaElement>(null)
  const [copyStatus, setCopyStatus] = useState<'idle' | 'success' | 'failed'>('idle')
  const [sdkUrlCopyStatus, setSdkUrlCopyStatus] = useState<'idle' | 'success' | 'failed'>('idle')
  const [endpointCopyStatus, setEndpointCopyStatus] = useState<'idle' | 'success' | 'failed'>('idle')

  const origin = originOverride ?? (typeof window !== 'undefined' ? window.location.origin : '')
  const sdkBaseUrl = origin ? `${origin}/v1` : ''
  const chatCompletionsEndpoint = origin ? `${origin}/v1/chat/completions` : ''

  useEffect(() => {
    if (!open) {
      setCopyStatus('idle')
      setSdkUrlCopyStatus('idle')
      setEndpointCopyStatus('idle')
      return
    }
    window.setTimeout(() => {
      try {
        inputRef.current?.focus()
        inputRef.current?.select()
      } catch { /* input selection is best-effort */ }
    }, 0)
  }, [open, plaintextKey])

  async function handleCopy() {
    if (!plaintextKey) return

    try {
      if (navigator.clipboard && window.isSecureContext) {
        await navigator.clipboard.writeText(plaintextKey)
      } else {
        inputRef.current?.focus()
        inputRef.current?.select()
        const copied = document.execCommand('copy')
        if (!copied) {
          throw new Error('copy command failed')
        }
      }
      setCopyStatus('success')
    } catch {
      inputRef.current?.focus()
      inputRef.current?.select()
      setCopyStatus('failed')
    }
  }

  async function handleCopyUrl(url: string, setStatus: (s: 'idle' | 'success' | 'failed') => void) {
    if (!url) return

    try {
      if (navigator.clipboard && window.isSecureContext) {
        await navigator.clipboard.writeText(url)
      } else {
        throw new Error('clipboard not available')
      }
      setStatus('success')
    } catch {
      setStatus('failed')
    }
  }

  function handleGoToSmokeTest() {
    onClose()
    onGoToSmokeTest?.()
  }

  return (
    <Modal
      title={t('secret.title')}
      open={open}
      onCancel={onClose}
      closable={false}
      keyboard={false}
      maskClosable={false}
      footer={
        <Space>
          <Button onClick={handleCopy} type="primary">
            {t('secret.copy')}
          </Button>
          {onGoToSmokeTest && (
            <Button onClick={handleGoToSmokeTest} type="default">
              {t('secret.goToSmoke')}
            </Button>
          )}
          <Button onClick={onClose}>
            {t('secret.saved')}
          </Button>
        </Space>
      }
      destroyOnHidden
    >
      <Paragraph style={{ marginBottom: 12 }}>
        <Text type="warning">{t('secret.warning')}</Text>
      </Paragraph>
      <Input.TextArea
        ref={inputRef}
        readOnly
        value={plaintextKey || ''}
        autoSize={{ minRows: 2, maxRows: 4 }}
        onFocus={(event) => event.target.select()}
        style={{ fontFamily: 'monospace', wordBreak: 'break-all', marginBottom: 12 }}
      />
      {copyStatus === 'success' ? (
        <Alert type="success" showIcon message={t('secret.copied')} />
      ) : null}
      {copyStatus === 'failed' ? (
        <Alert type="warning" showIcon message={t('secret.copyFailed')} />
      ) : null}

      <Divider style={{ margin: '16px 0 12px' }} />

      <Paragraph style={{ marginBottom: 8 }}>
        <Text type="secondary">{t('secret.integrationHint')}</Text>
      </Paragraph>

      <Paragraph style={{ marginBottom: 4 }}>
        <Text strong>{t('secret.sdkBaseUrlLabel')}</Text>
      </Paragraph>
      <Space style={{ marginBottom: 8 }}>
        <Text code style={{ fontSize: 13 }}>{sdkBaseUrl}</Text>
        <Button size="small" onClick={() => handleCopyUrl(sdkBaseUrl, setSdkUrlCopyStatus)}>
          {sdkUrlCopyStatus === 'success' ? t('secret.copiedUrl') : t('secret.copyUrl')}
        </Button>
      </Space>
      {sdkUrlCopyStatus === 'failed' && (
        <Paragraph>
          <Text type="warning" style={{ fontSize: 12 }}>{t('secret.copySdkUrlFailed')}</Text>
        </Paragraph>
      )}

      <Paragraph style={{ marginBottom: 4 }}>
        <Text strong>{t('secret.chatEndpointLabel')}</Text>
      </Paragraph>
      <Space style={{ marginBottom: 8 }}>
        <Text code style={{ fontSize: 13 }}>{chatCompletionsEndpoint}</Text>
        <Button size="small" onClick={() => handleCopyUrl(chatCompletionsEndpoint, setEndpointCopyStatus)}>
          {endpointCopyStatus === 'success' ? t('secret.copiedUrl') : t('secret.copyUrl')}
        </Button>
      </Space>
      {endpointCopyStatus === 'failed' && (
        <Paragraph>
          <Text type="warning" style={{ fontSize: 12 }}>{t('secret.copyEndpointFailed')}</Text>
        </Paragraph>
      )}
      {onGoToSmokeTest && (
        <Paragraph style={{ marginBottom: 0 }}>
          <Text type="secondary" style={{ fontSize: 12 }}>
            {t('secret.smokeHint')}
          </Text>
        </Paragraph>
      )}
    </Modal>
  )
}
