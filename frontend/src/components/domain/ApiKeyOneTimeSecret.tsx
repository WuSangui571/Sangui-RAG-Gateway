import { Modal, Typography, Button, Space } from 'antd'

const { Text, Paragraph } = Typography

interface ApiKeyOneTimeSecretProps {
  open: boolean
  plaintextKey: string | null
  onClose: () => void
}

export default function ApiKeyOneTimeSecret({ open, plaintextKey, onClose }: ApiKeyOneTimeSecretProps) {
  function handleCopy() {
    if (plaintextKey) {
      navigator.clipboard.writeText(plaintextKey).catch(() => {
        // Clipboard API may fail; the key remains selectable for manual copy.
      })
    }
  }

  return (
    <Modal
      title="API Key Created"
      open={open}
      onCancel={onClose}
      footer={
        <Button onClick={onClose} type="primary">
          I have saved this key
        </Button>
      }
      destroyOnClose
    >
      <Paragraph style={{ marginBottom: 12 }}>
        <Text type="warning">This key will only be shown once. Copy it now.</Text>
      </Paragraph>
      <Paragraph
        copyable={{ text: plaintextKey || '' }}
        style={{
          background: '#f5f5f5',
          padding: 12,
          borderRadius: 4,
          fontFamily: 'monospace',
          wordBreak: 'break-all',
        }}
      >
        {plaintextKey || '-'}
      </Paragraph>
      <Space>
        <Button size="small" onClick={handleCopy}>
          Copy to Clipboard
        </Button>
      </Space>
    </Modal>
  )
}
