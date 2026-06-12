import type { DiagnosticBoundary } from '../../types/request-log'
import type { DiagnosticResult } from './requestDiagnostics'
import type { AppReadinessVO } from '../../types/app'
import { BOUNDARY_LABEL_KEYS } from './requestDiagnostics'
import { Tag, Typography, List, Alert, Spin } from 'antd'
import { useI18n } from '../../app/i18n'

const { Text, Paragraph } = Typography

const BOUNDARY_COLORS: Record<DiagnosticBoundary, string> = {
  auth: 'red',
  readiness: 'orange',
  retrieval: 'blue',
  embedding: 'purple',
  upstream: 'red',
  streaming: 'cyan',
  'request-log': 'gold',
  unknown: 'default',
}

interface RequestDiagnosticsPanelProps {
  diagnostic: DiagnosticResult | null
  readiness: AppReadinessVO | null
  readinessLoading: boolean
  readinessError: string | null
}

export default function RequestDiagnosticsPanel({
  diagnostic,
  readiness,
  readinessLoading,
  readinessError,
}: RequestDiagnosticsPanelProps) {
  const { t } = useI18n()

  if (!diagnostic) {
    return null
  }

  return (
    <div style={{ marginTop: 16 }}>
      <Typography.Title level={5}>{t('diag.title')}</Typography.Title>

      <div style={{ marginBottom: 12 }}>
        <Tag color={BOUNDARY_COLORS[diagnostic.boundary] ?? 'default'}>
          {t(BOUNDARY_LABEL_KEYS[diagnostic.boundary])}
        </Tag>
      </div>

      <Paragraph style={{ marginBottom: 8 }}>
        <Text>{t(diagnostic.safe_summary_key)}</Text>
      </Paragraph>

      <Paragraph style={{ marginBottom: 16 }}>
        <Text strong>{t('diag.nextSteps')}:</Text>
      </Paragraph>
      <List
        size="small"
        dataSource={diagnostic.safe_next_step_keys}
        renderItem={(key) => (
          <List.Item style={{ paddingLeft: 0 }}>
            <Text>{t(key)}</Text>
          </List.Item>
        )}
      />

      {readinessLoading && (
        <div style={{ marginTop: 16 }}>
          <Spin size="small" />{' '}
          <Text type="secondary">{t('diag.readinessLoading')}</Text>
        </div>
      )}

      {readinessError && (
        <Alert
          type="warning"
          message={t('diag.readinessUnavailable')}
          description={readinessError}
          style={{ marginTop: 16 }}
        />
      )}

      {!readinessLoading && !readinessError && readiness && readiness.checks.length > 0 && (
        <div style={{ marginTop: 16 }}>
          <Typography.Title level={5}>{t('diag.readinessEvidence')}</Typography.Title>
          <List
            size="small"
            dataSource={readiness.checks}
            renderItem={(check) => (
              <List.Item>
                <List.Item.Meta
                  title={
                    <span>
                      <Tag
                        color={
                          check.status === 'READY' ? 'green' :
                          check.status === 'MISSING' ? 'red' :
                          check.status === 'DISABLED' ? 'orange' :
                          check.status === 'NOT_READY' ? 'gold' : 'default'
                        }
                        style={{ marginRight: 8 }}
                      >
                        {t(`status.readiness.${check.status}`)}
                      </Tag>
                      <Text>{check.label ?? check.key}</Text>
                    </span>
                  }
                  description={check.message ? <Text type="secondary">{check.message}</Text> : null}
                />
              </List.Item>
            )}
          />
        </div>
      )}
    </div>
  )
}
