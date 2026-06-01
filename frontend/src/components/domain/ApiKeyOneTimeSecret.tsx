import { useEffect, useRef, useState } from 'react'
import { Alert, Button, Input, Modal, Space, Typography } from 'antd'

const { Text, Paragraph } = Typography

interface ApiKeyOneTimeSecretProps {
  open: boolean
  plaintextKey: string | null
  onClose: () => void
}

export default function ApiKeyOneTimeSecret({ open, plaintextKey, onClose }: ApiKeyOneTimeSecretProps) {
  const inputRef = useRef<HTMLTextAreaElement>(null)
  const [copyStatus, setCopyStatus] = useState<'idle' | 'success' | 'failed'>('idle')

  useEffect(() => {
    if (!open) {
      setCopyStatus('idle')
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
    </Modal>
  )
}
