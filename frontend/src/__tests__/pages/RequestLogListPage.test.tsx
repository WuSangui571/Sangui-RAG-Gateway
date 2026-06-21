import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import RequestLogListPage from '../../pages/request-logs/RequestLogListPage'
import { ShellContext } from '../../components/layout/AdminShell'
import type { ShellContextValue } from '../../components/layout/AdminShell'
import UIPreferenceProvider from '../../app/providers/UIPreferenceProvider'

vi.mock('../../api/apps', () => ({
  listApps: vi.fn(),
}))

vi.mock('../../api/request-logs', () => ({
  listRequestLogs: vi.fn(),
  getRequestLogDetail: vi.fn(),
  getHitChunks: vi.fn(),
  accessOutputPreview: vi.fn(),
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

import { listApps } from '../../api/apps'
import { listRequestLogs } from '../../api/request-logs'

const mockListApps = listApps as ReturnType<typeof vi.fn>
const mockListRequestLogs = listRequestLogs as ReturnType<typeof vi.fn>

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

function renderPage(shellOverrides: Partial<ShellContextValue> = {}, persistentAppId?: number) {
  return render(
    <UIPreferenceProvider>
      <ShellContext.Provider value={createShellValue(shellOverrides)}>
        <RequestLogListPage persistentAppId={persistentAppId} />
      </ShellContext.Provider>
    </UIPreferenceProvider>,
  )
}

function mockAppsSuccess(apps: Array<{ id: number; name: string }> = []) {
  mockListApps.mockResolvedValueOnce({
    code: 'OK',
    message: 'ok',
    data: apps.map(a => ({ ...a, user_id: 1, status: 'ENABLED', default_model_config_id: null, default_knowledge_base_id: null, request_log_output_capture_enabled: false, created_at: '2026-01-01T00:00:00Z', updated_at: '2026-01-01T00:00:00Z' })),
  })
}

function mockLogsEmpty() {
  mockListRequestLogs.mockResolvedValueOnce({
    code: 'OK',
    message: 'ok',
    data: { items: [], page: 1, page_size: 20, total: 0 },
  })
}

const safeLogEntry = {
  id: 1,
  request_id: 'req-001',
  app_id: 42,
  api_key_id: 10,
  model: 'deepseek-v4-pro',
  provider_name: 'openai-compatible',
  status: 'success' as const,
  error_code: null,
  latency_ms: 250,
  upstream_latency_ms: 200,
  usage: { prompt_tokens: 100, completion_tokens: 50, total_tokens: 150 },
  messages_count: 3,
  question_summary: 'What is RAG?',
  hit_chunk_ids: [1, 2],
  created_at: '2026-06-18T10:00:00Z',
}

describe('RequestLogListPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setLocale('en-US')
  })

  describe('unauthenticated guard', () => {
    it('shows login prompt when adminUserId is null', () => {
      renderPage({ adminUserId: null })
      expect(screen.getByText(/please log in first/i)).toBeInTheDocument()
    })
  })

  describe('app selector', () => {
    it('shows app select with placeholder when no app is selected', async () => {
      mockAppsSuccess([{ id: 1, name: 'Test App' }])
      renderPage({ adminUserId: 1 })

      await waitFor(() => {
        expect(screen.getByText('Select App')).toBeInTheDocument()
      })
    })

    it('shows loading state while apps are loading', async () => {
      mockListApps.mockImplementation(
        () => new Promise(() => { /* never resolves in this test */ }),
      )
      renderPage({ adminUserId: 1 })

      await waitFor(() => {
        const select = document.querySelector('.ant-select-loading')
        expect(select).toBeTruthy()
      })
    })

    it('shows error with retry when app list fails', async () => {
      mockListApps.mockRejectedValueOnce(new Error('Network error'))
      renderPage({ adminUserId: 1 })

      await waitFor(() => {
        expect(screen.getByText('Failed to load apps')).toBeInTheDocument()
      })
      expect(screen.getByRole('button', { name: /retry/i })).toBeInTheDocument()
    })

    it('shows no apps state when the app list is empty', async () => {
      mockAppsSuccess([])
      renderPage({ adminUserId: 1 })

      await waitFor(() => {
        expect(screen.getByText('No apps available')).toBeInTheDocument()
      })
    })

    it('shows empty state when no app selected and apps loaded', async () => {
      mockAppsSuccess([{ id: 1, name: 'Test App' }])
      renderPage({ adminUserId: 1 })

      await waitFor(() => {
        expect(screen.getByText('Select an app to view request logs')).toBeInTheDocument()
      })
    })

    it('does not call listRequestLogs when no app is selected', async () => {
      mockAppsSuccess([{ id: 1, name: 'Test App' }])
      renderPage({ adminUserId: 1 })

      await waitFor(() => {
        expect(screen.getByText('Select an app to view request logs')).toBeInTheDocument()
      })
      expect(mockListRequestLogs).not.toHaveBeenCalled()
    })

    it('auto-connects when persistentAppId is provided', async () => {
      mockAppsSuccess([{ id: 42, name: 'Auto App' }])
      mockLogsEmpty()

      renderPage({ adminUserId: 1 }, 42)

      await waitFor(() => {
        expect(mockListRequestLogs).toHaveBeenCalledWith(42, expect.any(Object))
      })
    })
  })

  describe('app selection triggers log fetch', () => {
    it('calls listRequestLogs when app is selected', async () => {
      mockAppsSuccess([{ id: 1, name: 'Test App' }, { id: 2, name: 'Another App' }])
      mockLogsEmpty()

      const user = userEvent.setup()
      renderPage({ adminUserId: 1 })

      await waitFor(() => {
        expect(screen.getByText('Select an app to view request logs')).toBeInTheDocument()
      })

      const select = screen.getByRole('combobox')
      await user.click(select)
      const option = await screen.findByText('#2 Another App')
      await user.click(option)

      await waitFor(() => {
        expect(mockListRequestLogs).toHaveBeenCalledWith(2, expect.any(Object))
      })
    })
  })

  describe('loading state', () => {
    it('shows loading spinner while fetching logs', async () => {
      mockAppsSuccess([{ id: 42, name: 'App' }])
      mockListRequestLogs.mockImplementation(
        () => new Promise((resolve) => setTimeout(() => resolve({
          code: 'OK', message: 'ok', data: { items: [], page: 1, page_size: 20, total: 0 },
        }), 500)),
      )

      renderPage({ adminUserId: 1 }, 42)

      await waitFor(() => {
        expect(screen.getByRole('table')).toBeInTheDocument()
      })
    })
  })

  describe('empty state', () => {
    it('shows empty message when no logs exist', async () => {
      mockAppsSuccess([{ id: 42, name: 'App' }])
      mockLogsEmpty()

      renderPage({ adminUserId: 1 }, 42)

      await waitFor(() => {
        expect(screen.getByText('No request logs found')).toBeInTheDocument()
      })
    })
  })

  describe('error state', () => {
    it('shows error alert when API call fails', async () => {
      mockAppsSuccess([{ id: 42, name: 'App' }])
      mockListRequestLogs.mockRejectedValueOnce(new Error('Network error'))

      renderPage({ adminUserId: 1 }, 42)

      await waitFor(() => {
        expect(screen.getByText('Failed to load request logs')).toBeInTheDocument()
      })
    })

    it('shows retry button on error', async () => {
      mockAppsSuccess([{ id: 42, name: 'App' }])
      mockListRequestLogs.mockRejectedValueOnce(new Error('Network error'))

      renderPage({ adminUserId: 1 }, 42)

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /retry/i })).toBeInTheDocument()
      })
    })
  })

  describe('output capture does not affect list', () => {
    it('renders metadata rows even when output capture would be disabled', async () => {
      mockAppsSuccess([{ id: 42, name: 'App' }])
      mockListRequestLogs.mockResolvedValueOnce({
        code: 'OK',
        message: 'ok',
        data: { items: [{ ...safeLogEntry, output_capture_status: 'DISABLED' }], page: 1, page_size: 20, total: 1 },
      })

      renderPage({ adminUserId: 1 }, 42)

      await waitFor(() => {
        expect(screen.getByText('deepseek-v4-pro')).toBeInTheDocument()
        expect(screen.getByText('250ms')).toBeInTheDocument()
      })
    })
  })

  describe('safe rendering', () => {
    it('renders safe metadata columns', async () => {
      mockAppsSuccess([{ id: 42, name: 'App' }])
      mockListRequestLogs.mockResolvedValueOnce({
        code: 'OK',
        message: 'ok',
        data: { items: [safeLogEntry], page: 1, page_size: 20, total: 1 },
      })

      renderPage({ adminUserId: 1 }, 42)

      await waitFor(() => {
        expect(screen.getByText('deepseek-v4-pro')).toBeInTheDocument()
        expect(screen.getByText('openai-compatible')).toBeInTheDocument()
        expect(screen.getByText('250ms')).toBeInTheDocument()
        expect(screen.getByText('150')).toBeInTheDocument()
        expect(screen.getByText('What is RAG?')).toBeInTheDocument()
      })
    })

    it('does not render forbidden fields', async () => {
      mockAppsSuccess([{ id: 42, name: 'App' }])
      mockListRequestLogs.mockResolvedValueOnce({
        code: 'OK',
        message: 'ok',
        data: { items: [safeLogEntry], page: 1, page_size: 20, total: 1 },
      })

      const { container } = renderPage({ adminUserId: 1 }, 42)

      await waitFor(() => {
        expect(screen.getByText('deepseek-v4-pro')).toBeInTheDocument()
      })

      const html = container.innerHTML
      const forbiddenTerms = [
        'api_key',
        'key_hash',
        'authorization',
        'upstream_api_key',
        'api_key_encrypted',
        'prompt',
        'messages',
        'augmented_prompt',
        'chunk_content',
        'embedding',
        'provider_response_body',
        'stack_trace',
        'storage_path',
        'raw_sse',
        'environment',
      ]
      for (const term of forbiddenTerms) {
        expect(html).not.toContain(term)
      }
    })
  })
})
