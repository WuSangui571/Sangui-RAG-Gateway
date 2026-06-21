import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import SmokeTestPage from '../../pages/smoke/SmokeTestPage'
import { ShellContext } from '../../components/layout/AdminShell'
import type { ShellContextValue } from '../../components/layout/AdminShell'
import UIPreferenceProvider from '../../app/providers/UIPreferenceProvider'

vi.mock('../../api/apps', () => ({
  listApps: vi.fn(),
  getAppReadiness: vi.fn(),
}))

vi.mock('../../api/api-keys', () => ({
  listApiKeys: vi.fn(),
}))

vi.mock('../../api/openai', () => ({
  smokeChatCompletions: vi.fn(),
  smokeStreamingChatCompletions: vi.fn(),
  SmokeApiError: class SmokeApiError extends Error {
    status: number
    errorCode: string | null
    errorType: string | null
    constructor(status: number, message: string, errorCode: string | null, errorType: string | null) {
      super(message)
      this.name = 'SmokeApiError'
      this.status = status
      this.errorCode = errorCode
      this.errorType = errorType
    }
  },
}))

vi.mock('../../api/request-logs', () => ({
  listRequestLogs: vi.fn(),
  getRequestLogDetail: vi.fn(),
  getHitChunks: vi.fn(),
}))

vi.mock('../../api/http', () => ({
  setAuthToken: vi.fn(),
  setUnauthorizedHandler: vi.fn(),
  getAuthToken: vi.fn(() => null),
  ApiError: class ApiError extends Error {
    code: string
    status: number
    constructor(code: string, message: string, status: number) {
      super(message)
      this.name = 'ApiError'
      this.code = code
      this.status = status
    }
  },
  apiGet: vi.fn(),
  apiPost: vi.fn(),
  apiPut: vi.fn(),
  apiUpload: vi.fn(),
  unwrapResponse: vi.fn(),
}))

import { listApps, getAppReadiness } from '../../api/apps'
import { listApiKeys } from '../../api/api-keys'
import { smokeChatCompletions, smokeStreamingChatCompletions, SmokeApiError } from '../../api/openai'
import { listRequestLogs, getRequestLogDetail, getHitChunks } from '../../api/request-logs'

const mockListApps = listApps as ReturnType<typeof vi.fn>
const mockGetAppReadiness = getAppReadiness as ReturnType<typeof vi.fn>
const mockListApiKeys = listApiKeys as ReturnType<typeof vi.fn>
const mockSmokeChatCompletions = smokeChatCompletions as ReturnType<typeof vi.fn>
const mockSmokeStreamingChatCompletions = smokeStreamingChatCompletions as ReturnType<typeof vi.fn>
const mockListRequestLogs = listRequestLogs as ReturnType<typeof vi.fn>
const mockGetRequestLogDetail = getRequestLogDetail as ReturnType<typeof vi.fn>
const mockGetHitChunks = getHitChunks as ReturnType<typeof vi.fn>

function setLocale(locale: string) {
  try { localStorage.setItem('sangui-admin-locale', locale) } catch { /* ignore */ }
}

function createShellValue(overrides: Partial<ShellContextValue> = {}): ShellContextValue {
  return {
    adminUserId: 1,
    currentUser: { id: 1, username: 'admin', status: 'ACTIVE' },
    selectedAppId: null,
    setSelectedAppId: vi.fn(),
    navigateTo: vi.fn(),
    logout: vi.fn(),
    ...overrides,
  }
}

function renderPage(shellOverrides: Partial<ShellContextValue> = {}) {
  return render(
    <UIPreferenceProvider>
      <ShellContext.Provider value={createShellValue(shellOverrides)}>
        <SmokeTestPage />
      </ShellContext.Provider>
    </UIPreferenceProvider>,
  )
}

function mockAppsSuccess(apps: Array<{ id: number; name: string }> = []) {
  mockListApps.mockResolvedValue({
    code: 'OK',
    message: 'ok',
    data: apps.map(a => ({
      ...a, user_id: 1, status: 'ENABLED' as const,
      default_model_config_id: null, default_knowledge_base_id: null,
      request_log_output_capture_enabled: false,
      created_at: '2026-01-01T00:00:00Z', updated_at: '2026-01-01T00:00:00Z',
    })),
  })
}

function mockAppsEmpty() {
  mockListApps.mockResolvedValue({ code: 'OK', message: 'ok', data: [] })
}

function mockReadinessReady(appId: number) {
  mockGetAppReadiness.mockResolvedValue({
    code: 'OK',
    message: 'ok',
    data: {
      app_id: appId,
      user_id: 1,
      overall_status: 'READY' as const,
      checks: [
        { key: 'app', label: 'App Enabled', status: 'READY' as const, message: 'App is enabled', metadata: null },
        { key: 'default_model_config', label: 'Default model config', status: 'READY' as const, message: 'Bound to config #1', metadata: null },
        { key: 'default_knowledge_base', label: 'Default knowledge base', status: 'READY' as const, message: 'Bound to KB #1', metadata: null },
        { key: 'knowledge_base_status', label: 'KB status', status: 'READY' as const, message: 'Documents ready', metadata: null },
        { key: 'active_api_key', label: 'Active API key', status: 'READY' as const, message: '1 active key', metadata: null },
        { key: 'embedding_config', label: 'Embedding config', status: 'READY' as const, message: 'Embedding is ready', metadata: null },
      ],
    },
  })
}

function mockReadinessNotReady(appId: number, failedCheck: { key: string; label: string; status: 'MISSING' | 'DISABLED' | 'NOT_READY'; message: string }) {
  mockGetAppReadiness.mockResolvedValue({
    code: 'OK',
    message: 'ok',
    data: {
      app_id: appId,
      user_id: 1,
      overall_status: 'NOT_READY' as const,
      checks: [
        { key: 'app', label: 'App Enabled', status: 'READY' as const, message: 'App is enabled', metadata: null },
        failedCheck,
        { key: 'default_knowledge_base', label: 'Default knowledge base', status: 'READY' as const, message: 'Bound to KB #1', metadata: null },
        { key: 'knowledge_base_status', label: 'KB status', status: 'READY' as const, message: 'Documents ready', metadata: null },
        { key: 'active_api_key', label: 'Active API key', status: 'READY' as const, message: '1 active key', metadata: null },
        { key: 'embedding_config', label: 'Embedding config', status: 'READY' as const, message: 'Embedding is ready', metadata: null },
      ],
    },
  })
}

function mockApiKeysSuccess() {
  mockListApiKeys.mockResolvedValue({
    code: 'OK',
    message: 'ok',
    data: [{
      id: 1, app_id: 1, user_id: 1, name: 'test-key', key_prefix: 'sk-test',
      status: 'ACTIVE' as const, expires_at: null, last_used_at: null,
      revoked_at: null, created_at: '2026-01-01T00:00:00Z', updated_at: '2026-01-01T00:00:00Z',
    }],
  })
}

const FORBIDDEN_STRINGS = [
  'prompt',
  'messages',
  'storage_path',
  'api_key_encrypted',
  'upstream_api_key',
  'key_hash',
  'authorization',
  'provider_response_body',
  'stack_trace',
  'output_preview',
  'augmented_prompt',
  'full_messages',
  'content',
  'summary',
  'chunk_content',
  'embedding',
  'raw_sse',
]

describe('SmokeTestPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setLocale('zh-CN')
  })

  describe('empty / no-app state', () => {
    it('shows no-app hint when app list is empty', async () => {
      mockAppsEmpty()
      renderPage()
      await waitFor(() => {
        expect(screen.getByText('暂无可用应用，请先创建并配置。')).toBeInTheDocument()
      })
      expect(mockListApps).toHaveBeenCalledWith(undefined)
    })

    it('shows select-app placeholder when apps exist but none selected', async () => {
      mockAppsSuccess([{ id: 1, name: 'Test App' }])
      renderPage()
      await waitFor(() => {
        expect(screen.getByText('选择一个应用以继续。')).toBeInTheDocument()
      })
    })

    it('does not render preconditions or execute sections when no app selected', async () => {
      mockAppsEmpty()
      renderPage()
      await waitFor(() => {
        expect(screen.queryByText('前置条件')).not.toBeInTheDocument()
        expect(screen.queryByText('执行冒烟测试')).not.toBeInTheDocument()
      })
    })
  })

  describe('app selected with readiness NOT_READY', () => {
    it('renders failed precondition and action hint when model config is missing', async () => {
      mockAppsSuccess([{ id: 1, name: 'Test App' }])
      mockApiKeysSuccess()
      mockReadinessNotReady(1, {
        key: 'default_model_config',
        label: 'Default model config',
        status: 'MISSING',
        message: 'No default model config',
      })
      renderPage({ selectedAppId: 1 })
      await waitFor(() => {
        expect(screen.getByText('前置条件')).toBeInTheDocument()
      })
      await waitFor(() => {
        expect(screen.getByText('请绑定或修复对话模型配置。')).toBeInTheDocument()
      })
    })

    it('shows readiness warning when overall status is not READY', async () => {
      mockAppsSuccess([{ id: 1, name: 'Test App' }])
      mockApiKeysSuccess()
      mockReadinessNotReady(1, {
        key: 'default_model_config',
        label: 'Default model config',
        status: 'MISSING',
        message: 'No default model config',
      })
      renderPage({ selectedAppId: 1 })
      await waitFor(() => {
        expect(screen.getByText('请在运行冒烟测试之前解决以上问题。')).toBeInTheDocument()
      })
    })

    it('disables Run All button when readiness is not ready', async () => {
      mockAppsSuccess([{ id: 1, name: 'Test App' }])
      mockApiKeysSuccess()
      mockReadinessNotReady(1, {
        key: 'default_model_config',
        label: 'Default model config',
        status: 'MISSING',
        message: 'No default model config',
      })
      renderPage({ selectedAppId: 1 })
      await waitFor(() => {
        expect(screen.getByText('前置条件未满足')).toBeInTheDocument()
      })
      const passInputs = screen.getAllByPlaceholderText('sk-sangui-...')
      await userEvent.type(passInputs[0], 'sk-sangui-test-key-12345')
      const runAllBtn = screen.getByText('运行全部')
      expect(runAllBtn.closest('button')).toBeDisabled()
    })

    it('shows readiness boundary when readiness API fails', async () => {
      mockAppsSuccess([{ id: 1, name: 'Test App' }])
      mockApiKeysSuccess()
      mockGetAppReadiness.mockRejectedValue(new Error('readiness down'))
      renderPage({ selectedAppId: 1 })
      await waitFor(() => {
        expect(screen.getByText(/加载就绪状态失败：/)).toBeInTheDocument()
      })
      expect(screen.getByText('下一步')).toBeInTheDocument()
      expect(screen.getByText('就绪状态')).toBeInTheDocument()
    })
  })

  describe('READY readiness with pasted key', () => {
    it('enables Run All button when readiness is READY and key is provided', async () => {
      mockAppsSuccess([{ id: 1, name: 'Test App' }])
      mockApiKeysSuccess()
      mockReadinessReady(1)
      renderPage({ selectedAppId: 1 })
      await waitFor(() => {
        expect(screen.getByText('前置条件')).toBeInTheDocument()
      })
      const passInputs = screen.getAllByPlaceholderText('sk-sangui-...')
      const keyInput = passInputs[0]
      await userEvent.type(keyInput, 'sk-sangui-test-key-12345')
      await waitFor(() => {
        const runAllBtn = screen.getByText('运行全部')
        expect(runAllBtn.closest('button')).not.toBeDisabled()
      })
    })
  })

  describe('successful non-streaming smoke evidence', () => {
    it('renders response metadata and content length, not answer text', async () => {
      mockAppsSuccess([{ id: 1, name: 'Test App' }])
      mockApiKeysSuccess()
      mockReadinessReady(1)
      mockSmokeChatCompletions.mockResolvedValue({
        id: 'chatcmpl-001',
        object: 'chat.completion',
        created: 1700000000,
        model: 'deepseek-v4-pro',
        choices: [{ index: 0, message: { role: 'assistant', content: 'The answer is 42.' }, finish_reason: 'stop' }],
        usage: { prompt_tokens: 10, completion_tokens: 5, total_tokens: 15 },
      })
      renderPage({ selectedAppId: 1 })
      await waitFor(() => {
        expect(screen.getByText('前置条件')).toBeInTheDocument()
      })
      const passInputs = screen.getAllByPlaceholderText('sk-sangui-...')
      await userEvent.type(passInputs[0], 'sk-sangui-test-key-12345')
      const sendBtns = screen.getAllByText('发送非流式请求')
      await userEvent.click(sendBtns[0])
      await waitFor(() => {
        expect(screen.getByText('chatcmpl-001')).toBeInTheDocument()
      })
      expect(screen.getByText('deepseek-v4-pro')).toBeInTheDocument()
      expect(screen.getByText('stop')).toBeInTheDocument()
      expect(screen.queryByText('The answer is 42.')).not.toBeInTheDocument()
    })
  })

  describe('request-log validation flow', () => {
    it('disables request-log button before non-streaming success', async () => {
      mockAppsSuccess([{ id: 1, name: 'Test App' }])
      mockApiKeysSuccess()
      mockReadinessReady(1)
      renderPage({ selectedAppId: 1 })
      await waitFor(() => {
        expect(screen.getByText('执行冒烟测试')).toBeInTheDocument()
      })
      const validateBtns = screen.getAllByText('验证请求日志')
      expect(validateBtns.length).toBeGreaterThan(0)
      expect(validateBtns[0].closest('button')).toBeDisabled()
    })

    it('renders request-log evidence with safe metadata fields', async () => {
      mockAppsSuccess([{ id: 1, name: 'Test App' }])
      mockApiKeysSuccess()
      mockReadinessReady(1)
      mockSmokeChatCompletions.mockResolvedValue({
        id: 'chatcmpl-002',
        object: 'chat.completion',
        created: 1700000000,
        model: 'deepseek-v4-pro',
        choices: [{ index: 0, message: { role: 'assistant', content: 'OK' }, finish_reason: 'stop' }],
        usage: { prompt_tokens: 10, completion_tokens: 5, total_tokens: 15 },
      })
      mockListRequestLogs.mockResolvedValue({
        code: 'OK',
        message: 'ok',
        data: {
          items: [{
            id: 1, request_id: 'req-002', app_id: 1, api_key_id: 1,
            model: 'deepseek-v4-pro', provider_name: 'openai',
            status: 'success' as const, error_code: null,
            latency_ms: 250, upstream_latency_ms: 200,
            usage: { prompt_tokens: 10, completion_tokens: 5, total_tokens: 15 },
            messages_count: 1, question_summary: 'Answer using the uploaded knowledge base.',
            hit_chunk_ids: [1, 2, 3], created_at: '2026-01-01T00:00:00Z',
          }],
          page: 1, page_size: 5, total: 1,
        },
      })
      mockGetRequestLogDetail.mockResolvedValue({
        code: 'OK',
        message: 'ok',
        data: {
          id: 1, request_id: 'req-002', app_id: 1, api_key_id: 1,
          user_id: 1, updated_at: '2026-01-01T00:00:01Z',
          model: 'deepseek-v4-pro', provider_name: 'openai',
          status: 'success' as const, error_code: null,
          latency_ms: 250, upstream_latency_ms: 200,
          usage: { prompt_tokens: 10, completion_tokens: 5, total_tokens: 15 },
          messages_count: 1, question_summary: 'Answer using the uploaded knowledge base.',
          hit_chunk_ids: [1, 2, 3], created_at: '2026-01-01T00:00:00Z',
          output_capture_status: 'DISABLED' as const, completion_length: null,
          output_preview_available: false, output_preview_truncated: false,
          output_redacted: false, output_retention_expires_at: null,
          retrieval_evidence: null,
        },
      })
      mockGetHitChunks.mockResolvedValue({
        code: 'OK',
        message: 'ok',
        data: [{
          chunk_id: 1, document_id: 10, knowledge_base_id: 5,
          source_filename: 'doc.pdf', chunk_index: 0, summary: 'some summary',
        }],
      })
      renderPage({ selectedAppId: 1 })
      await waitFor(() => {
        expect(screen.getByText('前置条件')).toBeInTheDocument()
      })
      const passInputs = screen.getAllByPlaceholderText('sk-sangui-...')
      await userEvent.type(passInputs[0], 'sk-sangui-test-key-12345')
      const sendBtns = screen.getAllByText('发送非流式请求')
      await userEvent.click(sendBtns[0])
      await waitFor(() => {
        const validateBtns = screen.getAllByText('验证请求日志')
        expect(validateBtns[0].closest('button')).not.toBeDisabled()
      })
      const validateBtnsAfter = screen.getAllByText('验证请求日志')
      await userEvent.click(validateBtnsAfter[0])
      await waitFor(() => {
        expect(screen.getByText('req-002')).toBeInTheDocument()
      })
      expect(screen.getAllByText('deepseek-v4-pro').length).toBeGreaterThan(0)
      expect(screen.getByText('openai')).toBeInTheDocument()
      expect(screen.getByText('250ms')).toBeInTheDocument()
      await waitFor(() => {
        expect(screen.getByText(/chunk_id=1/)).toBeInTheDocument()
      })
      const domText = document.body.textContent || ''
      expect(domText).not.toContain('some summary')
    })
  })

  describe('forbidden fields', () => {
    it('does not render forbidden strings in DOM after smoke execution', async () => {
      mockAppsSuccess([{ id: 1, name: 'Test App' }])
      mockApiKeysSuccess()
      mockReadinessReady(1)
      mockSmokeChatCompletions.mockResolvedValue({
        id: 'chatcmpl-003',
        object: 'chat.completion',
        created: 1700000000,
        model: 'deepseek-v4-pro',
        choices: [{ index: 0, message: { role: 'assistant', content: 'Answer text that must not appear' }, finish_reason: 'stop' }],
        usage: { prompt_tokens: 10, completion_tokens: 5, total_tokens: 15 },
      })
      renderPage({ selectedAppId: 1 })
      await waitFor(() => {
        expect(screen.getByText('前置条件')).toBeInTheDocument()
      })
      const passInputs = screen.getAllByPlaceholderText('sk-sangui-...')
      await userEvent.type(passInputs[0], 'sk-sangui-test-key-12345')
      const sendBtns = screen.getAllByText('发送非流式请求')
      await userEvent.click(sendBtns[0])
      await waitFor(() => {
        expect(screen.getByText('chatcmpl-003')).toBeInTheDocument()
      })
      const domText = document.body.textContent || ''
      for (const forbidden of FORBIDDEN_STRINGS) {
        expect(domText).not.toContain(forbidden)
      }
      expect(domText).not.toContain('Answer text that must not appear')
    })
  })

  describe('streaming missing [DONE]', () => {
    it('shows streaming failure and next-step when [DONE] is missing', async () => {
      mockAppsSuccess([{ id: 1, name: 'Test App' }])
      mockApiKeysSuccess()
      mockReadinessReady(1)
      mockSmokeStreamingChatCompletions.mockResolvedValue({
        httpStatus: 200,
        dataLineCount: 3,
        chunkCount: 3,
        donePresent: false,
      })
      renderPage({ selectedAppId: 1 })
      await waitFor(() => {
        expect(screen.getByText('前置条件')).toBeInTheDocument()
      })
      const passInputs = screen.getAllByPlaceholderText('sk-sangui-...')
      await userEvent.type(passInputs[0], 'sk-sangui-test-key-12345')
      const streamBtns = screen.getAllByText('发送流式请求')
      await userEvent.click(streamBtns[0])
      await waitFor(() => {
        const failTags = screen.getAllByText('失败')
        expect(failTags.length).toBeGreaterThan(0)
      })
      await waitFor(() => {
        expect(screen.getByText('下一步')).toBeInTheDocument()
      })
    })
  })

  describe('revoked-key optional check', () => {
    it('shows disabled state for revoked-key check by default', async () => {
      mockAppsSuccess([{ id: 1, name: 'Test App' }])
      mockApiKeysSuccess()
      mockReadinessReady(1)
      renderPage({ selectedAppId: 1 })
      await waitFor(() => {
        expect(screen.getByText('已禁用')).toBeInTheDocument()
      })
    })

    it('handles revoked-key 401 invalid_api_key as pass', async () => {
      mockAppsSuccess([{ id: 1, name: 'Test App' }])
      mockApiKeysSuccess()
      mockReadinessReady(1)
      const smokeError = new SmokeApiError(401, 'Invalid API key', 'invalid_api_key', 'invalid_request_error')
      mockSmokeChatCompletions.mockRejectedValue(smokeError)
      renderPage({ selectedAppId: 1 })
      await waitFor(() => {
        expect(screen.getByText('前置条件')).toBeInTheDocument()
      })
      const enableBtn = screen.getByText('已禁用')
      await userEvent.click(enableBtn)
      await waitFor(() => {
        expect(screen.getByPlaceholderText('粘贴已撤销的 sk-sangui-... 密钥')).toBeInTheDocument()
      })
      const revokeInput = screen.getByPlaceholderText('粘贴已撤销的 sk-sangui-... 密钥')
      await userEvent.type(revokeInput, 'sk-sangui-revoked-123')
      const verifyBtns = screen.getAllByText('验证已撤销密钥')
      await userEvent.click(verifyBtns[0])
      await waitFor(() => {
        expect(screen.getByText('HTTP 401, error.code=invalid_api_key')).toBeInTheDocument()
      })
    })
  })

  describe('i18n dictionary parity', () => {
    it('renders smoke page in en-US with app selected', async () => {
      setLocale('en-US')
      mockAppsSuccess([{ id: 1, name: 'Test App' }])
      mockApiKeysSuccess()
      mockReadinessReady(1)
      renderPage({ selectedAppId: 1 })
      await waitFor(() => {
        expect(screen.getByText('Preconditions')).toBeInTheDocument()
      })
      expect(screen.getByText('Execute Smoke Tests')).toBeInTheDocument()
      expect(screen.getByText('Run All')).toBeInTheDocument()
    })
  })
})
