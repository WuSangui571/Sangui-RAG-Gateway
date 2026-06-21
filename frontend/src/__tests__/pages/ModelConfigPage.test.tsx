import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, fireEvent } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
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

import { listModelConfigs, checkUnsavedModelConfig, checkSavedModelConfig } from '../../api/model-configs'

const mockListModelConfigs = listModelConfigs as ReturnType<typeof vi.fn>
const mockCheckUnsaved = checkUnsavedModelConfig as ReturnType<typeof vi.fn>
const mockCheckSaved = checkSavedModelConfig as ReturnType<typeof vi.fn>

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

  describe('check button semantics', () => {
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

    beforeEach(() => {
      mockListModelConfigs.mockResolvedValue({
        code: 'OK',
        message: 'ok',
        data: [sampleConfig],
      })
    })

    it('shows draft check button with correct en-US label', async () => {
      renderPage()
      await waitFor(() => {
        expect(screen.getByText('Test Config')).toBeInTheDocument()
      })
      expect(screen.getByRole('button', { name: 'Check Draft Config' })).toBeInTheDocument()
    })

    it('shows saved check button with correct en-US label', async () => {
      renderPage()
      await waitFor(() => {
        expect(screen.getByText('Test Config')).toBeInTheDocument()
      })
      expect(screen.getByRole('button', { name: 'Check Saved Config' })).toBeInTheDocument()
    })

    it('shows zh-CN labels when locale is zh-CN', async () => {
      setLocale('zh-CN')
      renderPage()
      await waitFor(() => {
        expect(screen.getByText('Test Config')).toBeInTheDocument()
      })
      expect(screen.getByRole('button', { name: '检查草稿配置' })).toBeInTheDocument()
      expect(screen.getByRole('button', { name: '检查已保存配置' })).toBeInTheDocument()
    })

    it('opens draft check modal with title and description', async () => {
      const user = userEvent.setup()
      renderPage()
      await waitFor(() => {
        expect(screen.getByText('Test Config')).toBeInTheDocument()
      })

      await user.click(screen.getByRole('button', { name: 'Check Draft Config' }))

      await waitFor(() => {
        expect(screen.getByText('Draft Config Check')).toBeInTheDocument()
        expect(screen.getByText(/This check uses only the fields entered in this form/)).toBeInTheDocument()
      })
    })

    it('draft check fills modal fields and calls checkUnsavedModelConfig with expected payload', async () => {
      mockCheckUnsaved.mockResolvedValueOnce({
        code: 'OK',
        message: 'ok',
        data: {
          capability: 'CHAT',
          overall_status: 'SUCCESS',
          base_url_checked: true,
          chat: { status: 'SUCCESS', model: 'deepseek-v4-pro', message: 'ok' },
          embedding: null,
        },
      })

      const user = userEvent.setup()
      renderPage()
      await waitFor(() => {
        expect(screen.getByText('Test Config')).toBeInTheDocument()
      })

      await user.click(screen.getByRole('button', { name: 'Check Draft Config' }))
      await waitFor(() => {
        expect(screen.getByText('Draft Config Check')).toBeInTheDocument()
      })

      const baseUrlInput = screen.getByPlaceholderText('https://api.example.com/v1')
      await user.type(baseUrlInput, 'https://api.openai.com/v1')
      const apiKeyInput = screen.getByPlaceholderText('sk-...')
      await user.type(apiKeyInput, 'sk-test-key')
      const chatModelInput = screen.getByPlaceholderText('deepseek-v4-pro')
      await user.clear(chatModelInput)
      await user.type(chatModelInput, 'gpt-4')

      await user.click(screen.getByRole('button', { name: 'Run Check' }))

      await waitFor(() => {
        expect(mockCheckUnsaved).toHaveBeenCalledTimes(1)
        expect(mockCheckUnsaved).toHaveBeenCalledWith({
          capability: 'CHAT',
          base_url: 'https://api.openai.com/v1',
          api_key: 'sk-test-key',
          chat_model: 'gpt-4',
          embedding_model: undefined,
          embedding_dimension: undefined,
        })
      })
    })

    it('draft check does not call checkSavedModelConfig', async () => {
      mockCheckUnsaved.mockResolvedValueOnce({
        code: 'OK',
        message: 'ok',
        data: {
          capability: 'CHAT',
          overall_status: 'SUCCESS',
          base_url_checked: true,
          chat: { status: 'SUCCESS', model: 'deepseek-v4-pro', message: 'ok' },
          embedding: null,
        },
      })

      const user = userEvent.setup()
      renderPage()
      await waitFor(() => {
        expect(screen.getByText('Test Config')).toBeInTheDocument()
      })

      await user.click(screen.getByRole('button', { name: 'Check Draft Config' }))
      await waitFor(() => {
        expect(screen.getByText('Draft Config Check')).toBeInTheDocument()
      })

      const baseUrlInput = screen.getByPlaceholderText('https://api.example.com/v1')
      await user.type(baseUrlInput, 'https://api.openai.com/v1')
      const apiKeyInput = screen.getByPlaceholderText('sk-...')
      await user.type(apiKeyInput, 'sk-test-key')
      const chatModelInput = screen.getByPlaceholderText('deepseek-v4-pro')
      await user.clear(chatModelInput)
      await user.type(chatModelInput, 'gpt-4')

      await user.click(screen.getByRole('button', { name: 'Run Check' }))

      await waitFor(() => {
        expect(mockCheckUnsaved).toHaveBeenCalledTimes(1)
        expect(mockCheckSaved).not.toHaveBeenCalled()
      })
    })

    it('saved row check calls checkSavedModelConfig(record.id, {})', async () => {
      mockCheckSaved.mockResolvedValueOnce({
        code: 'OK',
        message: 'ok',
        data: {
          capability: 'CHAT',
          overall_status: 'SUCCESS',
          base_url_checked: true,
          chat: { status: 'SUCCESS', model: 'deepseek-v4-pro', message: 'ok' },
          embedding: null,
        },
      })

      const user = userEvent.setup()
      renderPage()
      await waitFor(() => {
        expect(screen.getByText('Test Config')).toBeInTheDocument()
      })

      await user.click(screen.getByRole('button', { name: 'Check Saved Config' }))

      await waitFor(() => {
        expect(mockCheckSaved).toHaveBeenCalledTimes(1)
        expect(mockCheckSaved).toHaveBeenCalledWith(1, {})
      })
    })

    it('saved check result modal shows row name context', async () => {
      mockCheckSaved.mockResolvedValueOnce({
        code: 'OK',
        message: 'ok',
        data: {
          capability: 'CHAT',
          overall_status: 'SUCCESS',
          base_url_checked: true,
          chat: { status: 'SUCCESS', model: 'deepseek-v4-pro', message: 'ok' },
          embedding: null,
        },
      })

      const user = userEvent.setup()
      renderPage()
      await waitFor(() => {
        expect(screen.getByText('Test Config')).toBeInTheDocument()
      })

      await user.click(screen.getByRole('button', { name: 'Check Saved Config' }))

      await waitFor(() => {
        expect(screen.getByText('Check Result')).toBeInTheDocument()
        expect(screen.getByText('#1 Test Config')).toBeInTheDocument()
      })
    })

    it('non-OK draft check shows error and does not open result modal', async () => {
      mockCheckUnsaved.mockResolvedValueOnce({
        code: 'ERROR',
        message: 'Provider unavailable',
        data: null,
      })

      const user = userEvent.setup()
      renderPage()
      await waitFor(() => {
        expect(screen.getByText('Test Config')).toBeInTheDocument()
      })

      await user.click(screen.getByRole('button', { name: 'Check Draft Config' }))
      await waitFor(() => {
        expect(screen.getByText('Draft Config Check')).toBeInTheDocument()
      })

      const baseUrlInput = screen.getByPlaceholderText('https://api.example.com/v1')
      fireEvent.change(baseUrlInput, { target: { value: 'https://api.example.com/v1' } })
      const apiKeyInput = screen.getByPlaceholderText('sk-...')
      fireEvent.change(apiKeyInput, { target: { value: 'sk-test-key' } })
      const chatModelInput = screen.getByPlaceholderText('deepseek-v4-pro')
      fireEvent.change(chatModelInput, { target: { value: 'gpt-4' } })

      await user.click(screen.getByRole('button', { name: 'Run Check' }))

      await waitFor(() => {
        expect(mockCheckUnsaved).toHaveBeenCalledTimes(1)
      })

      await waitFor(() => {
        expect(screen.getByText('Provider unavailable')).toBeInTheDocument()
      })
      expect(screen.queryByText('Check Result')).toBeNull()
    })

    it('rejected draft check shows error and does not open result modal', async () => {
      mockCheckUnsaved.mockRejectedValueOnce(new Error('Network failure'))

      const user = userEvent.setup()
      renderPage()
      await waitFor(() => {
        expect(screen.getByText('Test Config')).toBeInTheDocument()
      })

      await user.click(screen.getByRole('button', { name: 'Check Draft Config' }))
      await waitFor(() => {
        expect(screen.getByText('Draft Config Check')).toBeInTheDocument()
      })

      const baseUrlInput = screen.getByPlaceholderText('https://api.example.com/v1')
      await user.type(baseUrlInput, 'https://api.example.com/v1')
      const apiKeyInput = screen.getByPlaceholderText('sk-...')
      await user.type(apiKeyInput, 'sk-test-key')
      const chatModelInput = screen.getByPlaceholderText('deepseek-v4-pro')
      await user.type(chatModelInput, 'gpt-4')

      await user.click(screen.getByRole('button', { name: 'Run Check' }))

      await waitFor(() => {
        expect(screen.getByText('Error')).toBeInTheDocument()
      })
      expect(screen.queryByText('Check Result')).toBeNull()
    })

    it('non-OK saved check shows error and does not open stale result modal', async () => {
      mockCheckSaved.mockResolvedValueOnce({
        code: 'ERROR',
        message: 'Config not found',
        data: null,
      })

      const user = userEvent.setup()
      renderPage()
      await waitFor(() => {
        expect(screen.getByText('Test Config')).toBeInTheDocument()
      })

      await user.click(screen.getByRole('button', { name: 'Check Saved Config' }))

      await waitFor(() => {
        expect(screen.getByText('Error')).toBeInTheDocument()
        expect(screen.getByText('Config not found')).toBeInTheDocument()
      })
      expect(screen.queryByText('Check Result')).toBeNull()
    })

    it('disabled state prevents double-submit while saved check is running', async () => {
      let resolveCheck: (value: unknown) => void = () => {
        throw new Error('saved check promise was not initialized')
      }
      mockCheckSaved.mockImplementationOnce(
        () => new Promise((resolve) => { resolveCheck = resolve }),
      )

      const user = userEvent.setup()
      renderPage()
      await waitFor(() => {
        expect(screen.getByText('Test Config')).toBeInTheDocument()
      })

      const savedButton = screen.getByRole('button', { name: 'Check Saved Config' })
      await user.click(savedButton)

      await waitFor(() => {
        expect(savedButton).toBeDisabled()
      })

      resolveCheck({
        code: 'OK',
        message: 'ok',
        data: {
          capability: 'CHAT',
          overall_status: 'SUCCESS',
          base_url_checked: true,
          chat: { status: 'SUCCESS', model: 'deepseek-v4-pro', message: 'ok' },
          embedding: null,
        },
      })

      await waitFor(() => {
        expect(savedButton).not.toBeDisabled()
      })
    })

    it('row check button shows checking text while running', async () => {
      let resolveCheck: (value: unknown) => void = () => {
        throw new Error('saved check promise was not initialized')
      }
      mockCheckSaved.mockImplementationOnce(
        () => new Promise((resolve) => { resolveCheck = resolve }),
      )

      const user = userEvent.setup()
      renderPage()
      await waitFor(() => {
        expect(screen.getByText('Test Config')).toBeInTheDocument()
      })

      await user.click(screen.getByRole('button', { name: 'Check Saved Config' }))

      await waitFor(() => {
        expect(screen.getByText('Checking...')).toBeInTheDocument()
      })

      resolveCheck({
        code: 'OK',
        message: 'ok',
        data: {
          capability: 'CHAT',
          overall_status: 'SUCCESS',
          base_url_checked: true,
          chat: { status: 'SUCCESS', model: 'deepseek-v4-pro', message: 'ok' },
          embedding: null,
        },
      })

      await waitFor(() => {
        expect(screen.getByRole('button', { name: 'Check Saved Config' })).toBeInTheDocument()
      })
    })
  })
})
