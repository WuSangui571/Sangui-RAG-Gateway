import { Tag } from 'antd'
import type { ModelConfigStatus } from '../../types/model-config'
import type { AppStatus } from '../../types/app'
import type { KnowledgeBaseStatus } from '../../types/knowledge'
import type { DocumentStatus } from '../../types/document'
import type { ApiKeyStatus } from '../../types/api-key'

export type StatusType = ModelConfigStatus | AppStatus | KnowledgeBaseStatus | DocumentStatus | ApiKeyStatus | 'success' | 'failure'

const STATUS_COLORS: Record<string, string> = {
  ENABLED: 'green',
  DISABLED: 'warning',
  ACTIVE: 'green',
  REVOKED: 'red',
  EXPIRED: 'orange',
  EMPTY: 'default',
  PROCESSING: 'blue',
  READY: 'green',
  FAILED: 'red',
  UPLOADED: 'blue',
  PARSING: 'blue',
  PARSED: 'blue',
  EMBEDDING: 'blue',
  success: 'green',
  failure: 'red',
}

interface StatusTagProps {
  status: StatusType
}

export default function StatusTag({ status }: StatusTagProps) {
  const color = STATUS_COLORS[status] || 'default'
  return <Tag color={color}>{status}</Tag>
}
