import { useEffect, useRef, useState } from 'react'
import { Alert, Button, Divider, Input, Modal, Space, Typography } from 'antd'

const { Text, Paragraph } = Typography

interface ApiKeyOneTimeSecretProps {
  open: boolean
  plaintextKey: string | null
  onClose: () => void
  onGoToSmokeTest?: () => void
}

export default function ApiKeyOneTimeSecret({ open, plaintextKey, onClose, onGoToSmokeTest }: ApiKeyOneTimeSecretProps) {
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
      title="API Key Created"
      open={open}
      onCancel={onClose}
      closable={false}
      keyboard={false}
      maskClosable={false}
      footer={
        <Space>
          <Button onClick={handleCopy} type="primary">
            Copy Key
          </Button>
          {onGoToSmokeTest && (
            <Button onClick={handleGoToSmokeTest} type="default">
              Go to Smoke Test
            </Button>
          )}
          <Button onClick={onClose}>
            I have saved this key
          </Button>
        </Space>
      }
      destroyOnClose
    >
      <Paragraph style={{ marginBottom: 12 }}>
        <Text type="warning">This key will only be shown once. Copy it now.</Text>
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
        <Alert type="success" showIcon message="Copied. Save it somewhere secure before closing this dialog." />
      ) : null}
      {copyStatus === 'failed' ? (
        <Alert
          type="warning"
          showIcon
          message="Browser clipboard access failed. The key is selected above; press Ctrl+C to copy it manually."
        />
      ) : null}

      <Divider style={{ margin: '16px 0 12px' }} />

      <Paragraph style={{ marginBottom: 8 }}>
        <Text strong>Next step:</Text>{' '}
        <Text type="secondary">
          Use this key as <Text code>Authorization: Bearer &lt;key&gt;</Text> with the gateway base URL.
        </Text>
      </Paragraph>
      <Space style={{ marginBottom: 8 }}>
        <Text code style={{ fontSize: 13 }}>{gatewayBaseUrl}</Text>
        <Button size="small" onClick={handleCopyBaseUrl}>
          {baseUrlCopyStatus === 'success' ? 'Copied' : 'Copy Base URL'}
        </Button>
      </Space>
      {baseUrlCopyStatus === 'failed' && (
        <Paragraph>
          <Text type="warning" style={{ fontSize: 12 }}>Failed to copy base URL. Please copy it manually.</Text>
        </Paragraph>
      )}
      {onGoToSmokeTest && (
        <Paragraph style={{ marginBottom: 0 }}>
          <Text type="secondary" style={{ fontSize: 12 }}>
            You can also click "Go to Smoke Test" to validate this key immediately.
          </Text>
        </Paragraph>
      )}
    </Modal>
  )
}
