import { Tag } from 'antd'
import type { ModelConfigStatus } from '../../types/model-config'
import type { AppStatus } from '../../types/app'
import type { KnowledgeBaseStatus } from '../../types/knowledge'
import type { DocumentStatus, DocumentProcessingTaskStatus } from '../../types/document'
import type { ApiKeyStatus } from '../../types/api-key'
import { useI18n } from '../../app/i18n'
import type { I18nKey } from '../../app/i18n/dict'

export type StatusType = ModelConfigStatus | AppStatus | KnowledgeBaseStatus | DocumentStatus | DocumentProcessingTaskStatus | ApiKeyStatus | 'success' | 'failure'

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
  PENDING: 'blue',
  SUCCEEDED: 'green',
  RETRYABLE: 'orange',
  CANCELED: 'default',
  success: 'green',
  failure: 'red',
}

interface StatusTagProps {
  status: StatusType
}

export default function StatusTag({ status }: StatusTagProps) {
  const { t } = useI18n()
  const i18nKey = `status.${status}` as I18nKey
  const label = t(i18nKey)
  const color = STATUS_COLORS[status] || 'default'
  return <Tag color={color}>{label}</Tag>
}
