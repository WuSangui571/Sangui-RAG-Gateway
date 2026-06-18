import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import RequestLogListPage from '../../pages/request-logs/RequestLogListPage'
import { ShellContext } from '../../components/layout/AdminShell'
import type { ShellContextValue } from '../../components/layout/AdminShell'
import UIPreferenceProvider from '../../app/providers/UIPreferenceProvider'

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

import { listRequestLogs } from '../../api/request-logs'

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

  describe('no-app guard', () => {
    it('shows app ID input when no app is connected', () => {
      renderPage({ adminUserId: 1 })
      expect(screen.getByPlaceholderText('Enter app ID')).toBeInTheDocument()
      expect(screen.getByRole('button', { name: /connect/i })).toBeInTheDocument()
    })

    it('auto-connects when persistentAppId is provided', async () => {
      mockListRequestLogs.mockResolvedValueOnce({
        code: 'OK',
        message: 'ok',
        data: { items: [], page: 1, page_size: 20, total: 0 },
      })

      renderPage({ adminUserId: 1 }, 42)

      await waitFor(() => {
        expect(mockListRequestLogs).toHaveBeenCalledWith(42, expect.any(Object))
      })
    })
  })

  describe('loading state', () => {
    it('shows loading spinner while fetching', async () => {
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
      mockListRequestLogs.mockResolvedValueOnce({
        code: 'OK',
        message: 'ok',
        data: { items: [], page: 1, page_size: 20, total: 0 },
      })

      renderPage({ adminUserId: 1 }, 42)

      await waitFor(() => {
        expect(screen.getByText('No request logs found')).toBeInTheDocument()
      })
    })
  })

  describe('error state', () => {
    it('shows error alert when API call fails', async () => {
      mockListRequestLogs.mockRejectedValueOnce(new Error('Network error'))

      renderPage({ adminUserId: 1 }, 42)

      await waitFor(() => {
        expect(screen.getByText('Failed to load request logs')).toBeInTheDocument()
      })
    })

    it('shows retry button on error', async () => {
      mockListRequestLogs.mockRejectedValueOnce(new Error('Network error'))

      renderPage({ adminUserId: 1 }, 42)

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /retry/i })).toBeInTheDocument()
      })
    })
  })

  describe('safe rendering', () => {
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

    it('renders safe metadata columns', async () => {
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
