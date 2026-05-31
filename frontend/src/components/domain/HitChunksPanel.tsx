import type { HitChunkSummaryVO } from '../../types/request-log'
import { List, Typography, Empty } from 'antd'

const { Text } = Typography

interface HitChunksPanelProps {
  chunks: HitChunkSummaryVO[]
  loading: boolean
  error: string | null
  onRetry: () => void
}

export default function HitChunksPanel({ chunks, loading, error, onRetry }: HitChunksPanelProps) {
  if (loading) {
    return <Text type="secondary">Loading hit chunks...</Text>
  }

  if (error) {
    return (
      <Text type="danger">
        Failed to load hit chunks: {error}
        <a onClick={onRetry} style={{ marginLeft: 8 }}>Retry</a>
      </Text>
    )
  }

  if (chunks.length === 0) {
    return <Empty description="No hit chunks" image={Empty.PRESENTED_IMAGE_SIMPLE} />
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
                {chunk.source_filename ?? `Document #${chunk.document_id}`}
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
                <Text type="secondary">No summary available</Text>
              )
            }
          />
        </List.Item>
      )}
    />
  )
}
