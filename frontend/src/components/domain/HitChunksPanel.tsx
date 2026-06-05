import type { HitChunkSummaryVO } from '../../types/request-log'
import { List, Typography, Empty } from 'antd'
import { useI18n } from '../../app/i18n'

const { Text } = Typography

interface HitChunksPanelProps {
  chunks: HitChunkSummaryVO[]
  loading: boolean
  error: string | null
  onRetry: () => void
}

export default function HitChunksPanel({ chunks, loading, error, onRetry }: HitChunksPanelProps) {
  const { t } = useI18n()

  if (loading) {
    return <Text type="secondary">{t('chunks.loading')}</Text>
  }

  if (error) {
    return (
      <Text type="danger">
        {t('chunks.error', { error })}
        <a onClick={onRetry} style={{ marginLeft: 8 }}>{t('chunks.retry')}</a>
      </Text>
    )
  }

  if (chunks.length === 0) {
    return <Empty description={t('chunks.empty')} image={Empty.PRESENTED_IMAGE_SIMPLE} />
  }

  return (
    <List
      size="small"
      dataSource={chunks}
      renderItem={(chunk) => (
        <List.Item>
          <List.Item.Meta
            title={
              <Text>
                {chunk.source_filename ?? t('chunks.document', { id: chunk.document_id })}
                <Text type="secondary" style={{ marginLeft: 8 }}>
                  #{chunk.chunk_index}
                </Text>
              </Text>
            }
            description={
              chunk.summary ? (
                <Text
                  type="secondary"
                  ellipsis={{ tooltip: chunk.summary }}
                  style={{ maxWidth: 480, display: 'block' }}
                >
                  {chunk.summary}
                </Text>
              ) : (
                <Text type="secondary">{t('chunks.noSummary')}</Text>
              )
            }
          />
        </List.Item>
      )}
    />
  )
}
