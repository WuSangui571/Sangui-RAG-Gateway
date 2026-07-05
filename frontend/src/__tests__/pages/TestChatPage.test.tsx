import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import TestChatPage from '../../pages/test-chat/TestChatPage'
import { ShellContext } from '../../components/layout/AdminShell'
import type { ShellContextValue } from '../../components/layout/AdminShell'
import UIPreferenceProvider from '../../app/providers/UIPreferenceProvider'

Element.prototype.scrollIntoView = vi.fn()

vi.mock('../../api/apps', () => ({ listApps: vi.fn(), getAppReadiness: vi.fn() }))
vi.mock('../../api/api-keys', () => ({ listApiKeys: vi.fn() }))
vi.mock('../../api/openai', () => ({
  chatCompletions: vi.fn(),
  smokeChatCompletions: vi.fn(),
  smokeStreamingChatCompletions: vi.fn(),
  SmokeApiError: vi.fn(),
  OpenAiApiError: class MockOpenAiApiError extends Error {
    status: number
    errorCode: string | null
    errorType: string | null

    constructor(status: number, message: string, errorCode: string | null, errorType: string | null) {
      super(message)
      this.name = 'OpenAiApiError'
      this.status = status
      this.errorCode = errorCode
      this.errorType = errorType
    }
  },
}))
vi.mock('../../api/http', () => ({
  setAuthToken: vi.fn(),
  setUnauthorizedHandler: vi.fn(),
  getAuthToken: vi.fn(() => null),
  ApiError: class MockApiError extends Error { code: string; status: number; constructor(c: string, m: string, s: number) { super(m); this.code = c; this.status = s } },
  apiGet: vi.fn(),
  apiPost: vi.fn(),
  apiPut: vi.fn(),
  apiUpload: vi.fn(),
  unwrapResponse: vi.fn(),
}))

import { listApps } from '../../api/apps'
import { listApiKeys } from '../../api/api-keys'
import { chatCompletions, OpenAiApiError } from '../../api/openai'

const mockListApps = listApps as ReturnType<typeof vi.fn>
const mockListApiKeys = listApiKeys as ReturnType<typeof vi.fn>
const mockChatCompletions = chatCompletions as ReturnType<typeof vi.fn>

function setLocale(locale: string) {
  try { localStorage.setItem('sangui-admin-locale', locale) } catch { /* ignore */ }
}

function createShellValue(overrides: Partial<ShellContextValue> = {}): ShellContextValue {
  return {
    adminUserId: 1,
    currentUser: { id: 1, username: 'admin', status: 'ACTIVE' },
    selectedAppId: null, setSelectedAppId: vi.fn(), navigateTo: vi.fn(), logout: vi.fn(),
    ...overrides,
  }
}

function renderPage(shellOverrides: Partial<ShellContextValue> = {}) {
  return render(
    <UIPreferenceProvider>
      <ShellContext.Provider value={createShellValue(shellOverrides)}>
        <TestChatPage />
      </ShellContext.Provider>
    </UIPreferenceProvider>,
  )
}

function okResponse<T>(data: T) { return { code: 'OK', message: 'ok', data } }

function makeApp(id: number, name: string) {
  return { id, user_id: 1, name, status: 'ENABLED' as const, default_model_config_id: null, default_knowledge_base_id: null, retrieval_top_k: null, retrieval_similarity_threshold: null, retrieval_max_context_chunks: null, retrieval_max_context_chars: null, retrieval_max_single_chunk_chars: null, no_hit_policy: null, request_log_output_capture_enabled: false, created_at: '2026-01-01T00:00:00Z', updated_at: '2026-01-01T00:00:00Z' }
}

function makeKey(id: number, name: string, prefix: string, status = 'ACTIVE') {
  return { id, appId: 1, name, key_prefix: prefix, status, expires_at: null, last_used_at: null, created_at: '2026-01-01T00:00:00Z', updated_at: '2026-01-01T00:00:00Z' }
}

async function selectApp(user: ReturnType<typeof userEvent.setup>) {
  const appSelect = screen.getByRole('combobox')
  await user.click(appSelect)
  const option = await screen.findByText('#1 Test App')
  await user.click(option)
  await waitFor(() => { expect(mockListApiKeys).toHaveBeenCalledWith(1) })
}

async function fillKey(user: ReturnType<typeof userEvent.setup>) {
  const inputs = screen.getAllByPlaceholderText('sk-sangui-...')
  await user.type(inputs[0], 'sk-sangui-testkey1234567890abcdef')
}

async function typeMessage(user: ReturnType<typeof userEvent.setup>, text: string) {
  const inputs = screen.getAllByPlaceholderText('Type a message...')
  await user.type(inputs[0], text)
}

const FORBIDDEN_STRINGS = ['key_hash', 'api_key', 'plaintextKey', 'Authorization', 'upstream_api_key', 'api_key_encrypted', 'provider_response_body', 'stack_trace', 'storage_path']

describe('TestChatPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setLocale('en-US')
    mockListApps.mockResolvedValue(okResponse([]))
    mockListApiKeys.mockResolvedValue(okResponse([]))
  })

  it('shows page title and app selector', async () => {
    mockListApps.mockResolvedValue(okResponse([makeApp(1, 'Test App')]))
    renderPage()
    await waitFor(() => {
      expect(screen.getByText('Test Chat')).toBeInTheDocument()
      expect(screen.getByText('App')).toBeInTheDocument()
    })
  })

  it('shows send button disabled when no app selected', async () => {
    renderPage()
    await waitFor(() => {
      expect(screen.getByRole('button', { name: /send/i })).toBeDisabled()
    })
  })

  it('shows noAppSelected hint', async () => {
    renderPage()
    await waitFor(() => {
      expect(screen.getByRole('button', { name: /clear conversation/i })).toBeInTheDocument()
      expect(screen.getByRole('button', { name: /send/i })).toBeDisabled()
    })
  })

  it('blocks send without plaintext key after app selection', async () => {
    mockListApps.mockResolvedValue(okResponse([makeApp(1, 'Test App')]))
    mockListApiKeys.mockResolvedValue(okResponse([makeKey(1, 'Key1', 'sk-sangui-p1')]))
    renderPage()
    const user = userEvent.setup()
    await selectApp(user)
    expect(screen.getByRole('button', { name: /send/i })).toBeDisabled()
  })

  it('shows invalid key error for bad key format', async () => {
    mockListApps.mockResolvedValue(okResponse([makeApp(1, 'Test App')]))
    mockListApiKeys.mockResolvedValue(okResponse([makeKey(1, 'Key1', 'sk-sangui-p1')]))
    renderPage()
    const user = userEvent.setup()
    await selectApp(user)
    await fillKey(user) // first type a valid key
    await user.clear(screen.getAllByPlaceholderText('sk-sangui-...')[0])
    await user.type(screen.getAllByPlaceholderText('sk-sangui-...')[0], 'bad-key')
    await user.tab()
    await waitFor(() => {
      const errEls = screen.getAllByText('Enter a valid sk-sangui-* key')
      expect(errEls.length).toBeGreaterThanOrEqual(1)
    })
  })

  it('sends message and shows assistant response', async () => {
    mockListApps.mockResolvedValue(okResponse([makeApp(1, 'Test App')]))
    mockListApiKeys.mockResolvedValue(okResponse([makeKey(1, 'Key1', 'sk-sangui-p1')]))
    mockChatCompletions.mockResolvedValue({
      id: 'chat-1', object: 'chat.completion', created: 1234567890, model: 'test-model',
      choices: [{ index: 0, message: { role: 'assistant', content: 'Hello! How can I help?' }, finish_reason: 'stop' }],
      usage: { prompt_tokens: 50, completion_tokens: 30, total_tokens: 80 },
    })
    renderPage()
    const user = userEvent.setup()
    await selectApp(user)
    await fillKey(user)
    await typeMessage(user, 'Hello')
    await user.click(screen.getByRole('button', { name: /send/i }))
    await waitFor(() => {
      expect(screen.getByText('Hello')).toBeInTheDocument()
      expect(screen.getByText('Hello! How can I help?')).toBeInTheDocument()
    })
  })

  it('sends visible successful message history on follow-up', async () => {
    mockListApps.mockResolvedValue(okResponse([makeApp(1, 'Test App')]))
    mockListApiKeys.mockResolvedValue(okResponse([makeKey(1, 'Key1', 'sk-sangui-p1')]))
    mockChatCompletions
      .mockResolvedValueOnce({
        id: 'chat-1', object: 'chat.completion', created: 1234567890, model: 'test-model',
        choices: [{ index: 0, message: { role: 'assistant', content: 'First reply' }, finish_reason: 'stop' }],
        usage: { prompt_tokens: 10, completion_tokens: 5, total_tokens: 15 },
      })
      .mockResolvedValueOnce({
        id: 'chat-2', object: 'chat.completion', created: 1234567891, model: 'test-model',
        choices: [{ index: 0, message: { role: 'assistant', content: 'Second reply' }, finish_reason: 'stop' }],
        usage: null,
      })
    renderPage()
    const user = userEvent.setup()
    await selectApp(user)
    await fillKey(user)
    await typeMessage(user, 'First question')
    await user.click(screen.getByRole('button', { name: /send/i }))
    await waitFor(() => { expect(screen.getByText('First reply')).toBeInTheDocument() })
    await typeMessage(user, 'Follow up')
    await user.click(screen.getByRole('button', { name: /send/i }))
    await waitFor(() => { expect(screen.getByText('Second reply')).toBeInTheDocument() })
    expect(mockChatCompletions).toHaveBeenLastCalledWith(
      {
        model: 'ignored-by-gateway',
        messages: [
          { role: 'user', content: 'First question' },
          { role: 'assistant', content: 'First reply' },
          { role: 'user', content: 'Follow up' },
        ],
        stream: false,
      },
      'sk-sangui-testkey1234567890abcdef',
      { returnCitations: true },
    )
  })

  it('shows OpenAI-compatible errors without fabricating success', async () => {
    mockListApps.mockResolvedValue(okResponse([makeApp(1, 'Test App')]))
    mockListApiKeys.mockResolvedValue(okResponse([makeKey(1, 'Key1', 'sk-sangui-p1')]))
    mockChatCompletions.mockRejectedValue(new OpenAiApiError(
      401,
      'Invalid API key.',
      'invalid_api_key',
      'invalid_request_error',
    ))
    renderPage()
    const user = userEvent.setup()
    await selectApp(user)
    await fillKey(user)
    await typeMessage(user, 'Hello')
    await user.click(screen.getByRole('button', { name: /send/i }))
    await waitFor(() => {
      expect(screen.getByText('HTTP Status: 401')).toBeInTheDocument()
      expect(screen.getByText('Error Code: invalid_api_key')).toBeInTheDocument()
      expect(screen.getAllByText('Invalid API key.').length).toBeGreaterThanOrEqual(1)
    })
    expect(screen.queryByText('Hello! How can I help?')).not.toBeInTheDocument()
  })

  it('clears conversation on button click', async () => {
    mockListApps.mockResolvedValue(okResponse([makeApp(1, 'Test App')]))
    mockListApiKeys.mockResolvedValue(okResponse([makeKey(1, 'Key1', 'sk-sangui-p1')]))
    mockChatCompletions.mockResolvedValue({
      id: 'chat-1', object: 'chat.completion', created: 1234567890, model: 'test-model',
      choices: [{ index: 0, message: { role: 'assistant', content: 'Reply' }, finish_reason: 'stop' }],
      usage: { prompt_tokens: 10, completion_tokens: 5, total_tokens: 15 },
    })
    renderPage()
    const user = userEvent.setup()
    await selectApp(user)
    await fillKey(user)
    await typeMessage(user, 'Q')
    await user.click(screen.getByRole('button', { name: /send/i }))
    await waitFor(() => { expect(screen.getByText('Reply')).toBeInTheDocument() })
    await user.click(screen.getByRole('button', { name: /clear conversation/i }))
    await waitFor(() => {
      expect(screen.queryByText('User')).not.toBeInTheDocument()
      expect(screen.queryByText('Reply')).not.toBeInTheDocument()
    })
  })

  it('does not render forbidden fields in DOM', async () => {
    mockListApps.mockResolvedValue(okResponse([makeApp(1, 'Test App')]))
    mockListApiKeys.mockResolvedValue(okResponse([makeKey(1, 'Key1', 'sk-sangui-p1')]))
    mockChatCompletions.mockResolvedValue({
      id: 'chat-1', object: 'chat.completion', created: 1234567890, model: 'test-model',
      choices: [{ index: 0, message: { role: 'assistant', content: 'Safe reply' }, finish_reason: 'stop' }],
      usage: { prompt_tokens: 10, completion_tokens: 5, total_tokens: 15 },
      sangui_citations: [{ citation_id: 'S1', chunk_id: 1, document_id: 1, knowledge_base_id: 1, source_filename: 'doc.txt', chunk_index: 0, similarity: 0.95, metadata: null, content_chars: 100, injected_chars: 80 }],
    })
    renderPage()
    const user = userEvent.setup()
    await selectApp(user)
    await fillKey(user)
    await typeMessage(user, 'Q')
    await user.click(screen.getByRole('button', { name: /send/i }))
    await waitFor(() => { expect(screen.getByText('Safe reply')).toBeInTheDocument() })
    const html = document.body.innerHTML
    for (const forbidden of FORBIDDEN_STRINGS) {
      expect(html).not.toContain(forbidden)
    }
  })
})
