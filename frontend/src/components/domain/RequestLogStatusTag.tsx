import { Tag } from 'antd'

interface RequestLogStatusTagProps {
  status: 'success' | 'failure'
}

export default function RequestLogStatusTag({ status }: RequestLogStatusTagProps) {
  return (
    <Tag color={status === 'success' ? 'green' : 'red'}>
      {status === 'success' ? 'SUCCESS' : 'FAILURE'}
    </Tag>
  )
}
