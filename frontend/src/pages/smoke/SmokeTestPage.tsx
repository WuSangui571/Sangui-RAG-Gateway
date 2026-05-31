import { useCallback, useEffect, useState } from 'react'
import {
  Button, Select, Space, Typography, Card, Spin, Descriptions, Tag, Input,
} from 'antd'
import type { AppVO } from '../../types/app'
import type { ApiKeyVO } from '../../types/api-key'
import type { SmokeChatCompletionResponse } from '../../types/openai'
import { listApps } from '../../api/apps'
import { listApiKeys } from '../../api/api-keys'
import { ApiError } from '../../api/http'
import { smokeChatCompletions, SmokeApiError } from '../../api/openai'
import { useShell } from '../../components/layout/AdminShell'

export default function SmokeTestPage() {
  const { adminUserId, selectedAppId, setSelectedAppId, navigateTo } = useShell()

  const [apps, setApps] = useState<AppVO[]>([])
  const [keys, setKeys] = useState<ApiKeyVO[]>([])
  const [activeAppId, setActiveAppId] = useState<number | null>(selectedAppId)
  const [selectedKeyPrefix, setSelectedKeyPrefix] = useState<string | null>(null)
  const [selectedKeyValue, setSelectedKeyValue] = useState<string | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)

  const [running, setRunning] = useState(false)
  const [result, setResult] = useState<SmokeChatCompletionResponse | null>(null)
  const [smokeError, setSmokeError] = useState<{ status: number; message: string; code: string | null } | null>(null)
  const [userInput, setUserInput] = useState('Answer using the uploaded knowledge base.')

  const fetchApps = useCallback(async () => {
    if (adminUserId === null) return
    try {
      const res = await listApps(undefined, adminUserId)
      if (res.code === 'OK') {
        setApps(res.data)
        setLoadError(null)
      } else {
        setApps([])
        setLoadError(res.message)
      }
    } catch (e: unknown) {
      setApps([])
      setLoadError(e instanceof ApiError ? e.message : (e instanceof Error ? e.message : 'Failed to load apps'))
    }
  }, [adminUserId])

  useEffect(() => {
    fetchApps()
  }, [fetchApps])

  useEffect(() => {
    if (selectedAppId !== null) {
      setActiveAppId(selectedAppId)
      setSelectedKeyValue(null)
    }
  }, [selectedAppId])

  const fetchKeys = useCallback(async () => {
    if (activeAppId === null || adminUserId === null) return
    try {
      const res = await listApiKeys(activeAppId, adminUserId)
      if (res.code === 'OK') {
        setKeys(res.data.filter(k => k.status === 'ACTIVE'))
        setLoadError(null)
      } else {
        setKeys([])
        setLoadError(res.message)
      }
    } catch (e: unknown) {
      setKeys([])
      setLoadError(e instanceof ApiError ? e.message : (e instanceof Error ? e.message : 'Failed to load API keys'))
    }
  }, [activeAppId, adminUserId])

  useEffect(() => {
    setSelectedKeyPrefix(null)
    setSelectedKeyValue(null)
    setResult(null)
    setSmokeError(null)
    fetchKeys()
  }, [activeAppId, fetchKeys])

  function handleAppSelect(appId: number) {
    setActiveAppId(appId)
    setSelectedAppId(appId)
    setResult(null)
    setSmokeError(null)
    setSelectedKeyPrefix(null)
    setSelectedKeyValue(null)
  }

  async function handleSmoke() {
    if (selectedKeyValue === null) return
    setRunning(true)
    setResult(null)
    setSmokeError(null)
    try {
      const response = await smokeChatCompletions({
        model: 'ignored-by-gateway',
        messages: [{ role: 'user', content: userInput }],
        stream: false,
      }, selectedKeyValue)
      setResult(response)
    } catch (e: unknown) {
      if (e instanceof SmokeApiError) {
        setSmokeError({ status: e.status, message: e.message, code: e.errorCode })
      } else {
        setSmokeError({ status: 0, message: e instanceof Error ? e.message : 'Unknown error', code: null })
      }
    } finally {
      setRunning(false)
    }
  }

  function handleGoToRequestLogs() {
    navigateTo('request-logs')
  }

  const canRun = activeAppId !== null && selectedKeyValue !== null && userInput.trim().length > 0

  return (
    <div>
      <Space direction="vertical" style={{ width: '100%' }} size="middle">
        {loadError && (
          <Typography.Text type="danger">{loadError}</Typography.Text>
        )}

        <Space>
          <div>
            <Typography.Text type="secondary">App:</Typography.Text>
            <Select
              value={activeAppId}
              onChange={(v) => handleAppSelect(v)}
              placeholder="Select app"
              style={{ width: 240, marginLeft: 8 }}
              options={apps.map(a => ({ value: a.id, label: `#${a.id} ${a.name}` }))}
            />
          </div>
          <div>
            <Typography.Text type="secondary">Active Key:</Typography.Text>
            <Select
              value={selectedKeyPrefix}
              onChange={(v) => setSelectedKeyPrefix(v)}
              placeholder="Select active key (for reference)"
              style={{ width: 260, marginLeft: 8 }}
              options={keys.map(k => ({
                value: `${k.name} (${k.key_prefix})`,
                label: `${k.name} (${k.key_prefix})`,
              }))}
            />
          </div>
        </Space>

        <Card size="small" title="Enter the API key plaintext (from key creation)">
          <Input.Password
            value={selectedKeyValue ?? ''}
            onChange={(e) => setSelectedKeyValue(e.target.value || null)}
            placeholder="sk-sangui-..."
          />
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            Paste the full plaintext key generated from the API Keys page.
          </Typography.Text>
        </Card>

        <Card size="small" title="User Message">
          <Input.TextArea
            value={userInput}
            onChange={(e) => setUserInput(e.target.value)}
            rows={2}
            placeholder="Enter the user message for the chat completion..."
          />
        </Card>

        <Space>
          <Button
            type="primary"
            onClick={handleSmoke}
            loading={running}
            disabled={!canRun}
          >
            Send Smoke Request
          </Button>
          <Button onClick={handleGoToRequestLogs} disabled={activeAppId === null}>
            View Request Logs
          </Button>
        </Space>

        {running && <Spin tip="Waiting for gateway response..." style={{ display: 'block', margin: '16px 0' }}>
          <div style={{ height: 40 }} />
        </Spin>}

        {result && (
          <Card size="small" title="Success Response" style={{ borderColor: '#52c41a' }}>
            <Descriptions column={1} size="small" bordered>
              <Descriptions.Item label="ID">{result.id}</Descriptions.Item>
              <Descriptions.Item label="Model">{result.model}</Descriptions.Item>
              <Descriptions.Item label="Object">{result.object}</Descriptions.Item>
              <Descriptions.Item label="Finish Reason">{result.choices[0]?.finish_reason}</Descriptions.Item>
              <Descriptions.Item label="Content">{result.choices[0]?.message.content}</Descriptions.Item>
              <Descriptions.Item label="Tokens">
                {result.usage.prompt_tokens} prompt / {result.usage.completion_tokens} completion / {result.usage.total_tokens} total
              </Descriptions.Item>
            </Descriptions>
          </Card>
        )}

        {smokeError && (
          <Card size="small" title="Error Response" style={{ borderColor: '#ff4d4f' }}>
            <Descriptions column={1} size="small" bordered>
              <Descriptions.Item label="HTTP Status">{smokeError.status || 'N/A'}</Descriptions.Item>
              <Descriptions.Item label="Error Code">
                <Tag color="red">{smokeError.code || 'N/A'}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="Message">{smokeError.message}</Descriptions.Item>
            </Descriptions>
          </Card>
        )}
      </Space>
    </div>
  )
}
