import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import {
  Button, Select, Space, Typography, Card, Input, Alert, Spin, Tag, Empty,
} from 'antd'
import { SendOutlined, DeleteOutlined } from '@ant-design/icons'
import type { AppVO } from '../../types/app'
import type { ApiKeyVO } from '../../types/api-key'
import type { ChatMessage, SanguiCitation } from '../../types/openai'
import { listApps } from '../../api/apps'
import { listApiKeys } from '../../api/api-keys'
import { ApiError } from '../../api/http'
import { chatCompletions, OpenAiApiError } from '../../api/openai'
import { useShell } from '../../components/layout/AdminShell'
import { useI18n } from '../../app/i18n'
import type { I18nKey } from '../../app/i18n/dict'
import SourceCitationList from '../../components/domain/SourceCitationList'

const { Text } = Typography

interface ChatResult {
  latencyMs: number
  usage: { promptTokens: number; completionTokens: number; totalTokens: number }
  model: string
  citations: SanguiCitation[] | null
}

interface ChatErrorResult {
  httpStatus: number
  errorCode: string | null
  message: string
}

interface MessageEntry {
  role: 'user' | 'assistant'
  content: string
  result?: ChatResult
  error?: ChatErrorResult
}

const SK_KEY_PREFIX = 'sk-sangui-'

function isValidKey(value: string): boolean {
  return value.startsWith(SK_KEY_PREFIX) && value.length > SK_KEY_PREFIX.length
}

function formatLatency(ms: number): string {
  if (ms < 1000) return `${ms}ms`
  return `${(ms / 1000).toFixed(1)}s`
}

function MessageBubble({
  entry,
  t,
}: {
  entry: MessageEntry
  t: (key: I18nKey, params?: Record<string, string | number>) => string
}) {
  const isUser = entry.role === 'user'
  const bubbleStyle: React.CSSProperties = {
    maxWidth: '80%',
    padding: '10px 14px',
    borderRadius: 12,
    background: isUser ? 'var(--ant-color-primary)' : 'var(--ant-color-fill-alter)',
    color: isUser ? '#fff' : undefined,
    alignSelf: isUser ? 'flex-end' : 'flex-start',
    whiteSpace: 'pre-wrap',
    wordBreak: 'break-word',
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 4, marginBottom: 12, alignItems: isUser ? 'flex-end' : 'flex-start' }}>
      <Text style={{ marginBottom: 2, opacity: 0.7 }}>
        {isUser ? t('test-chat.role.user') : t('test-chat.role.assistant')}
      </Text>
      <div style={bubbleStyle}>
        <Text style={{ color: isUser ? '#fff' : undefined }}>{entry.content}</Text>
        {entry.result && (
          <div style={{ marginTop: 8, borderTop: isUser ? '1px solid rgba(255,255,255,0.25)' : '1px solid var(--ant-color-border-secondary)', paddingTop: 6 }}>
            <Space size="small" wrap>
              <Tag>{t('test-chat.latency')}: {formatLatency(entry.result.latencyMs)}</Tag>
              {entry.result.usage.totalTokens > 0 && (
                <Tag>{t('test-chat.usage')}: {entry.result.usage.promptTokens}/{entry.result.usage.completionTokens}/{entry.result.usage.totalTokens}</Tag>
              )}
              {entry.result.model && (
                <Tag>{t('test-chat.model')}: {entry.result.model}</Tag>
              )}
            </Space>
          </div>
        )}
        {entry.result?.citations && entry.result.citations.length > 0 && (
          <div style={{ marginTop: 6, borderTop: '1px solid var(--ant-color-border-secondary)', paddingTop: 6 }}>
            <Text strong style={{ fontSize: 12 }}>{t('test-chat.citationsTitle')}:</Text>
            <SourceCitationList citations={entry.result.citations} />
          </div>
        )}
        {entry.error && (
          <div style={{ marginTop: 8, borderTop: '1px solid var(--ant-color-error)', paddingTop: 6 }}>
            <Tag color="red">{t('test-chat.httpStatus')}: {entry.error.httpStatus || 'N/A'}</Tag>
            {entry.error.errorCode && (
              <Tag color="red">{t('test-chat.errorCode')}: {entry.error.errorCode}</Tag>
            )}
            <Text type="danger" style={{ display: 'block', marginTop: 4 }}>{entry.error.message}</Text>
          </div>
        )}
      </div>
    </div>
  )
}

export default function TestChatPage() {
  const { adminUserId, selectedAppId, setSelectedAppId } = useShell()
  const { t, tCommon } = useI18n()

  const [apps, setApps] = useState<AppVO[]>([])
  const [keys, setKeys] = useState<ApiKeyVO[]>([])
  const [activeAppId, setActiveAppId] = useState<number | null>(selectedAppId)
  const [appLoadError, setAppLoadError] = useState<string | null>(null)
  const [appLoading, setAppLoading] = useState(false)
  const [keyLoadError, setKeyLoadError] = useState<string | null>(null)

  const [selectedKeyPrefix, setSelectedKeyPrefix] = useState<string | null>(null)
  const [plaintextKey, setPlaintextKey] = useState('')
  const [userInput, setUserInput] = useState('')
  const [messages, setMessages] = useState<MessageEntry[]>([])
  const [sending, setSending] = useState(false)
  const [sendError, setSendError] = useState<string | null>(null)
  const [keyValidationError, setKeyValidationError] = useState<string | null>(null)

  const messagesEndRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (selectedAppId !== null) {
      setActiveAppId(selectedAppId)
      setPlaintextKey('')
      setKeyValidationError(null)
      setMessages([])
      setSendError(null)
    }
  }, [selectedAppId])

  const fetchApps = useCallback(async () => {
    if (adminUserId === null) return
    setAppLoading(true)
    setAppLoadError(null)
    try {
      const res = await listApps(undefined)
      if (res.code === 'OK') {
        setApps(res.data)
      } else {
        setApps([])
        setAppLoadError(res.message)
      }
    } catch (e: unknown) {
      setApps([])
      setAppLoadError(e instanceof ApiError ? e.message : (e instanceof Error ? e.message : tCommon('Failed to load apps')))
    } finally {
      setAppLoading(false)
    }
  }, [adminUserId, tCommon])

  useEffect(() => {
    fetchApps()
  }, [fetchApps])

  const fetchKeys = useCallback(async () => {
    if (activeAppId === null || adminUserId === null) return
    setKeyLoadError(null)
    try {
      const res = await listApiKeys(activeAppId)
      if (res.code === 'OK') {
        setKeys(res.data.filter(k => k.status === 'ACTIVE'))
      } else {
        setKeys([])
        setKeyLoadError(res.message)
      }
    } catch (e: unknown) {
      setKeys([])
      setKeyLoadError(e instanceof ApiError ? e.message : (e instanceof Error ? e.message : tCommon('Failed to load API keys')))
    }
  }, [activeAppId, adminUserId, tCommon])

  useEffect(() => {
    setSelectedKeyPrefix(null)
    fetchKeys()
  }, [activeAppId, fetchKeys])

  function handleAppSelect(appId: number) {
    setActiveAppId(appId)
    setSelectedAppId(appId)
    setSelectedKeyPrefix(null)
    setPlaintextKey('')
    setKeyValidationError(null)
    setMessages([])
    setSendError(null)
  }

  function handleKeyChange(value: string) {
    setPlaintextKey(value)
    setSendError(null)
    if (value.trim().length > 0 && !isValidKey(value.trim())) {
      setKeyValidationError(t('test-chat.invalidKey'))
    } else {
      setKeyValidationError(null)
    }
  }

  function handleClearKey() {
    setPlaintextKey('')
    setKeyValidationError(null)
    setSelectedKeyPrefix(null)
  }

  function handleClearConversation() {
    setMessages([])
    setSendError(null)
  }

  const canSend = useMemo(() => {
    if (sending) return false
    if (activeAppId === null) return false
    if (!plaintextKey.trim() || !isValidKey(plaintextKey.trim())) return false
    if (userInput.trim().length === 0) return false
    return true
  }, [sending, activeAppId, plaintextKey, userInput])

  const disabledReason = useMemo((): string | null => {
    if (activeAppId === null) return t('test-chat.disabledNoApp')
    if (!plaintextKey.trim()) return t('test-chat.disabledNoKey')
    if (!isValidKey(plaintextKey.trim())) return t('test-chat.invalidKey')
    if (userInput.trim().length === 0) return t('test-chat.disabledNoMessage')
    if (sending) return t('test-chat.disabledSending')
    return null
  }, [activeAppId, plaintextKey, userInput, sending, t])

  async function handleSend() {
    if (!canSend) return
    const key = plaintextKey.trim()
    const input = userInput.trim()

    const userMsg: MessageEntry = { role: 'user', content: input }
    setMessages(prev => [...prev, userMsg])
    setUserInput('')
    setSending(true)
    setSendError(null)

    const startTime = performance.now()
    try {
      const chatMessages: ChatMessage[] = messages
        .filter(m => (m.role === 'user' || m.role === 'assistant') && !m.error)
        .concat([{ role: 'user' as const, content: input }])
        .map(m => ({ role: m.role, content: m.content }))

      const response = await chatCompletions(
        {
          model: 'ignored-by-gateway',
          messages: chatMessages,
          stream: false,
        },
        key,
        { returnCitations: true },
      )

      const latencyMs = Math.round(performance.now() - startTime)
      const choice = response.choices[0]
      const assistantContent = choice?.message?.content ?? ''

      const assistantMsg: MessageEntry = {
        role: 'assistant',
        content: assistantContent,
        result: {
          latencyMs,
          usage: {
            promptTokens: response.usage?.prompt_tokens ?? 0,
            completionTokens: response.usage?.completion_tokens ?? 0,
            totalTokens: response.usage?.total_tokens ?? 0,
          },
          model: response.model,
          citations: response.sangui_citations ?? null,
        },
      }

      setMessages(prev => [...prev, assistantMsg])
    } catch (e: unknown) {
      if (e instanceof OpenAiApiError) {
        setSendError(`${e.status}: ${e.errorCode ?? 'unknown'} - ${e.message}`)
        setMessages(prev => [...prev, {
          role: 'assistant',
          content: e.message,
          error: { httpStatus: e.status, errorCode: e.errorCode, message: e.message },
        }])
      } else {
        const msg = e instanceof Error ? e.message : tCommon('Unknown error')
        setSendError(msg)
        setMessages(prev => [...prev, {
          role: 'assistant',
          content: msg,
          error: { httpStatus: 0, errorCode: null, message: msg },
        }])
      }
    } finally {
      setSending(false)
    }
  }

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView?.({ behavior: 'smooth' })
  }, [messages])

  const activeKeyCount = keys.length
  const showKeyInput = activeAppId !== null

  return (
    <div>
      <Space direction="vertical" style={{ width: '100%' }} size="middle">
        {(appLoadError || keyLoadError) && (
          <Alert
            type="error"
            message={appLoadError || keyLoadError}
            showIcon
            closable
          />
        )}

        <Card size="small">
          <Space direction="vertical" style={{ width: '100%' }} size="small">
            <div>
              <Text type="secondary" style={{ fontSize: 12 }}>{t('test-chat.app')}</Text>
              <Select
                value={activeAppId}
                onChange={(v) => handleAppSelect(v)}
                placeholder={t('test-chat.appPlaceholder')}
                style={{ width: 280, marginTop: 4 }}
                loading={appLoading}
                options={apps.map(a => ({ value: a.id, label: `#${a.id} ${a.name}` }))}
                notFoundContent={
                  appLoading
                    ? <Spin size="small" />
                    : <Empty description={t('test-chat.noApps')} image={Empty.PRESENTED_IMAGE_SIMPLE} />
                }
              />
              {activeAppId === null && apps.length > 0 && (
                <Text type="secondary" style={{ display: 'block', marginTop: 4 }}>{t('test-chat.noAppSelected')}</Text>
              )}
            </div>

            {activeAppId !== null && (
              <Space size="small" wrap>
                {activeKeyCount > 0 && (
                  <Select
                    value={selectedKeyPrefix}
                    onChange={(v) => setSelectedKeyPrefix(v)}
                    placeholder={t('test-chat.keyPlaceholder')}
                    style={{ width: 260 }}
                    options={keys.map(k => ({
                      value: `${k.name} (${k.key_prefix})`,
                      label: `${k.name} (${k.key_prefix})`,
                    }))}
                  />
                )}
                {activeKeyCount === 0 && (
                  <Text type="warning" style={{ fontSize: 12 }}>
                    {t('test-chat.noActiveApiKey')}
                  </Text>
                )}
              </Space>
            )}
          </Space>
        </Card>

        {showKeyInput && (
          <Card size="small">
            <Space direction="vertical" style={{ width: '100%' }} size="small">
              <Text type="secondary" style={{ fontSize: 12 }}>{t('test-chat.keyCardTitle')}</Text>
              <Space.Compact style={{ width: '100%' }}>
                <Input.Password
                  value={plaintextKey}
                  onChange={(e) => handleKeyChange(e.target.value)}
                  placeholder="sk-sangui-..."
                  style={{ flex: 1 }}
                />
                <Button
                  onClick={handleClearKey}
                  disabled={!plaintextKey}
                >
                  {t('test-chat.clear')}
                </Button>
              </Space.Compact>
              <Text type="secondary" style={{ fontSize: 11 }}>{t('test-chat.keyHint')}</Text>
              {keyValidationError && (
                <Text type="danger" style={{ fontSize: 12 }}>{keyValidationError}</Text>
              )}
            </Space>
          </Card>
        )}

        <Card
          size="small"
          title={t('test-chat.title')}
          extra={
            <Button
              size="small"
              icon={<DeleteOutlined />}
              onClick={handleClearConversation}
              disabled={messages.length === 0}
            >
              {t('test-chat.clearConversation')}
            </Button>
          }
        >
          <div style={{ display: 'flex', flexDirection: 'column', minHeight: 200 }}>
            {messages.length === 0 && (
              <div style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', padding: 40 }}>
                <Empty
                  description={
                    activeAppId === null
                      ? <Text type="secondary">{t('test-chat.disabledNoApp')}</Text>
                      : <Text type="secondary">{t('test-chat.emptyConversationHint')}</Text>
                  }
                />
              </div>
            )}

            {messages.length > 0 && (
              <div style={{ flex: 1, overflowY: 'auto', padding: '0 4px', maxHeight: 400 }}>
                {messages.map((msg, idx) => (
                  <MessageBubble key={idx} entry={msg} t={t} />
                ))}
                <div ref={messagesEndRef} />
              </div>
            )}
          </div>

          {sendError && (
            <Alert
              type="error"
              message={sendError}
              showIcon
              closable
              onClose={() => setSendError(null)}
              style={{ marginTop: 8 }}
            />
          )}

          <Space.Compact style={{ width: '100%', marginTop: 12 }}>
            <Input
              value={userInput}
              onChange={(e) => setUserInput(e.target.value)}
              placeholder={t('test-chat.userMessagePlaceholder')}
              onPressEnter={handleSend}
              disabled={sending}
            />
            <Button
              type="primary"
              icon={<SendOutlined />}
              onClick={handleSend}
              loading={sending}
              disabled={!canSend}
              title={disabledReason ?? undefined}
            >
              {sending ? t('test-chat.sending') : t('test-chat.send')}
            </Button>
          </Space.Compact>

          {disabledReason && (
            <Text type="secondary" style={{ display: 'block', marginTop: 4, fontSize: 12 }}>{disabledReason}</Text>
          )}
        </Card>

        <Text type="secondary" style={{ fontSize: 11, textAlign: 'center', display: 'block' }}>
          {t('test-chat.securityNote')}
        </Text>
      </Space>
    </div>
  )
}
