import { Tag } from 'antd'
import { useI18n } from '../../app/i18n'
import type { I18nKey } from '../../app/i18n/dict'

interface RequestLogStatusTagProps {
  status: 'success' | 'failure'
}

export default function RequestLogStatusTag({ status }: RequestLogStatusTagProps) {
  const { t } = useI18n()
  const i18nKey = `status.${status}` as I18nKey
  const label = t(i18nKey)
  return (
    <Tag color={status === 'success' ? 'green' : 'red'}>
      {label}
    </Tag>
  )
}
