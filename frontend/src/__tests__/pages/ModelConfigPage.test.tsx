import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import ModelConfigPage from '../../pages/model-configs/ModelConfigPage'
import { ShellContext } from '../../components/layout/AdminShell'
import type { ShellContextValue } from '../../components/layout/AdminShell'
import UIPreferenceProvider from '../../app/providers/UIPreferenceProvider'

vi.mock('../../api/model-configs', () => ({
  listModelConfigs: vi.fn(),
  createModelConfig: vi.fn(),
  updateModelConfig: vi.fn(),
  disableModelConfig: vi.fn(),
  enableModelConfig: vi.fn(),
  checkUnsavedModelConfig: vi.fn(),
  checkSavedModelConfig: vi.fn(),
  listChatCapableConfigs: vi.fn(),
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

import { listModelConfigs } from '../../api/model-configs'

const mockListModelConfigs = listModelConfigs as ReturnType<typeof vi.fn>

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
        <ModelConfigPage />
      </ShellContext.Provider>
    </UIPreferenceProvider>,
  )
}

describe('ModelConfigPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setLocale('en-US')
  })

  describe('loading state', () => {
    it('shows loading while fetching configs', async () => {
      mockListModelConfigs.mockImplementation(
        () => new Promise((resolve) => setTimeout(() => resolve({ code: 'OK', message: 'ok', data: [] }), 500)),
      )

      renderPage()

      await waitFor(() => {
        expect(screen.getByRole('table')).toBeInTheDocument()
      })
    })
  })

  describe('empty state', () => {
    it('shows empty message when no configs exist', async () => {
      mockListModelConfigs.mockResolvedValueOnce({
        code: 'OK',
        message: 'ok',
        data: [],
      })

      renderPage()

      await waitFor(() => {
        expect(screen.getByText('No model configs found')).toBeInTheDocument()
      })
    })

    it('shows create button even when empty', async () => {
      mockListModelConfigs.mockResolvedValueOnce({
        code: 'OK',
        message: 'ok',
        data: [],
      })

      renderPage()

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /create config/i })).toBeInTheDocument()
      })
    })
  })

  describe('error state', () => {
    it('shows error alert when API call fails', async () => {
      mockListModelConfigs.mockRejectedValueOnce(new Error('Network error'))

      renderPage()

      await waitFor(() => {
        expect(screen.getByText('Error')).toBeInTheDocument()
      })
    })
  })

  describe('data rendering', () => {
    const sampleConfig = {
      id: 1,
      user_id: 1,
      capability: 'CHAT',
      name: 'Test Config',
      provider_name: 'openai-compatible',
      base_url: 'https://api.example.com',
      api_key_masked: 'sk-****',
      chat_model: 'deepseek-v4-pro',
      embedding_model: null,
      embedding_dimension: null,
      status: 'ENABLED' as const,
      created_at: '2026-06-18T10:00:00Z',
      updated_at: '2026-06-18T10:00:00Z',
    }

    it('renders config data with typed API results', async () => {
      mockListModelConfigs.mockResolvedValueOnce({
        code: 'OK',
        message: 'ok',
        data: [sampleConfig],
      })

      renderPage()

      await waitFor(() => {
        expect(screen.getByText('Test Config')).toBeInTheDocument()
        expect(screen.getByText('openai-compatible')).toBeInTheDocument()
        expect(screen.getByText('deepseek-v4-pro')).toBeInTheDocument()
        expect(screen.getByText('CHAT')).toBeInTheDocument()
      })
    })

    it('shows masked key, not plaintext', async () => {
      mockListModelConfigs.mockResolvedValueOnce({
        code: 'OK',
        message: 'ok',
        data: [sampleConfig],
      })

      renderPage()

      await waitFor(() => {
        expect(screen.getByText('sk-****')).toBeInTheDocument()
      })
    })
  })
})
