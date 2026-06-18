import type { CitationVO } from '../../types/request-log'
import { List, Typography, Empty, Tag } from 'antd'
import { useI18n } from '../../app/i18n'

const { Text } = Typography

interface SourceCitationListProps {
  citations: CitationVO[]
}

function formatSimilarity(value: number | null): string {
  if (value === null || value === undefined) return '-'
  return value.toFixed(3)
}

export default function SourceCitationList({ citations }: SourceCitationListProps) {
  const { t } = useI18n()

  if (citations.length === 0) {
    return <Empty description={t('citations.empty')} image={Empty.PRESENTED_IMAGE_SIMPLE} />
  }

  return (
    <List
      size="small"
      dataSource={citations}
      renderItem={(citation) => (
        <List.Item>
          <List.Item.Meta
            title={
              <Text>
                <Tag color="blue">{citation.citation_id}</Tag>
                {citation.source_filename ?? t('citations.unknownSource')}
                <Text type="secondary" style={{ marginLeft: 8 }}>
                  #{citation.chunk_index ?? '-'}
                </Text>
              </Text>
            }
            description={
              <Text type="secondary" style={{ fontSize: 12 }}>
                {t('citations.chunkId')}: {citation.chunk_id} · {t('citations.documentId')}: {citation.document_id} ·{' '}
                {t('citations.kbId')}: {citation.knowledge_base_id} · {t('citations.similarity')}: {formatSimilarity(citation.similarity)} ·{' '}
                {t('citations.injectedChars')}: {citation.injected_chars ?? '-'}
              </Text>
            }
          />
        </List.Item>
      )}
    />
  )
}
