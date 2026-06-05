import { useEffect, useRef, useState } from 'react'
import { Alert, Button, Divider, Input, Modal, Space, Typography } from 'antd'
import { useI18n } from '../../app/i18n'

const { Text, Paragraph } = Typography

interface ApiKeyOneTimeSecretProps {
  open: boolean
  plaintextKey: string | null
  onClose: () => void
  onGoToSmokeTest?: () => void
}

export default function ApiKeyOneTimeSecret({ open, plaintextKey, onClose, onGoToSmokeTest }: ApiKeyOneTimeSecretProps) {
  const { t } = useI18n()
  const inputRef = useRef<HTMLTextAreaElement>(null)
  const [copyStatus, setCopyStatus] = useState<'idle' | 'success' | 'failed'>('idle')
  const [baseUrlCopyStatus, setBaseUrlCopyStatus] = useState<'idle' | 'success' | 'failed'>('idle')

  const gatewayBaseUrl = typeof window !== 'undefined' ? window.location.origin : ''

  useEffect(() => {
    if (!open) {
      setCopyStatus('idle')
      setBaseUrlCopyStatus('idle')
      return
    }
    window.setTimeout(() => {
      inputRef.current?.focus()
      inputRef.current?.select()
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

  async function handleCopyBaseUrl() {
    if (!gatewayBaseUrl) return

    try {
      if (navigator.clipboard && window.isSecureContext) {
        await navigator.clipboard.writeText(gatewayBaseUrl)
      } else {
        throw new Error('clipboard not available')
      }
      setBaseUrlCopyStatus('success')
    } catch {
      setBaseUrlCopyStatus('failed')
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
      destroyOnClose
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
        <Text strong>{t('secret.nextStep')}</Text>{' '}
        <Text type="secondary">
          {t('secret.usage')}{' '}
          <Text code>Authorization: Bearer {'<key>'}</Text>{' '}
          {t('secret.usageAuth')}
        </Text>
      </Paragraph>
      <Space style={{ marginBottom: 8 }}>
        <Text code style={{ fontSize: 13 }}>{gatewayBaseUrl}</Text>
        <Button size="small" onClick={handleCopyBaseUrl}>
          {baseUrlCopyStatus === 'success' ? t('secret.copiedUrl') : t('secret.copyBaseUrl')}
        </Button>
      </Space>
      {baseUrlCopyStatus === 'failed' && (
        <Paragraph>
          <Text type="warning" style={{ fontSize: 12 }}>{t('secret.copyUrlFailed')}</Text>
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
