import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import AppConfigPage from '../../pages/apps/AppConfigPage'
import { ShellContext } from '../../components/layout/AdminShell'
import type { ShellContextValue } from '../../components/layout/AdminShell'
import UIPreferenceProvider from '../../app/providers/UIPreferenceProvider'

vi.mock('../../api/apps', () => ({
  listApps: vi.fn(),
  createApp: vi.fn(),
  bindDefaultModelConfig: vi.fn(),
  bindDefaultKnowledgeBase: vi.fn(),
  disableApp: vi.fn(),
  enableApp: vi.fn(),
  updateAppOutputCapture: vi.fn(),
}))
vi.mock('../../api/model-configs', () => ({
  listChatCapableConfigs: vi.fn(),
  listModelConfigs: vi.fn(),
}))
vi.mock('../../api/knowledge', () => ({
  listKnowledgeBases: vi.fn(),
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

import { listApps, updateAppOutputCapture } from '../../api/apps'

const mockListApps = listApps as ReturnType<typeof vi.fn>
const mockUpdateAppOutputCapture = updateAppOutputCapture as ReturnType<typeof vi.fn>

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
        <AppConfigPage />
      </ShellContext.Provider>
    </UIPreferenceProvider>,
  )
}

describe('AppConfigPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setLocale('en-US')
  })

  describe('loading state', () => {
    it('shows loading while fetching apps', async () => {
      mockListApps.mockImplementation(
        () => new Promise((resolve) => setTimeout(() => resolve({ code: 'OK', message: 'ok', data: [] }), 500)),
      )

      renderPage()

      await waitFor(() => {
        expect(screen.getByRole('table')).toBeInTheDocument()
      })
    })
  })

  describe('empty state', () => {
    it('shows empty message when no apps exist', async () => {
      mockListApps.mockResolvedValueOnce({
        code: 'OK',
        message: 'ok',
        data: [],
      })

      renderPage()

      await waitFor(() => {
        expect(screen.getByText('No apps found')).toBeInTheDocument()
      })
    })
  })

  describe('error state', () => {
    it('shows error alert when API call fails', async () => {
      mockListApps.mockRejectedValueOnce(new Error('Network error'))

      renderPage()

      await waitFor(() => {
        expect(screen.getByText('Error')).toBeInTheDocument()
      })
    })
  })

  describe('output capture behavior', () => {
    const sampleApp = {
      id: 1,
      user_id: 1,
      name: 'Test App',
      status: 'ENABLED' as const,
      default_model_config_id: null,
      default_knowledge_base_id: null,
      request_log_output_capture_enabled: false,
      created_at: '2026-06-18T10:00:00Z',
      updated_at: '2026-06-18T10:00:00Z',
    }

    it('shows confirmation modal when enabling output capture', async () => {
      mockListApps.mockResolvedValueOnce({
        code: 'OK',
        message: 'ok',
        data: [sampleApp],
      })

      renderPage()

      await waitFor(() => {
        expect(screen.getByText('Test App')).toBeInTheDocument()
      })

      const switches = screen.getAllByRole('switch')
      const captureSwitch = switches[0]
      await userEvent.click(captureSwitch)

      await waitFor(() => {
        expect(screen.getByText('Enable Output Capture')).toBeInTheDocument()
        expect(screen.getByText(/Enable output capture for "Test App"/)).toBeInTheDocument()
      })
    })

    it('calls API directly when disabling output capture', async () => {
      const enabledApp = { ...sampleApp, request_log_output_capture_enabled: true }
      mockListApps.mockResolvedValueOnce({
        code: 'OK',
        message: 'ok',
        data: [enabledApp],
      })
      mockUpdateAppOutputCapture.mockResolvedValueOnce({
        code: 'OK',
        message: 'ok',
        data: { ...enabledApp, request_log_output_capture_enabled: false },
      })

      renderPage()

      await waitFor(() => {
        expect(screen.getByText('Test App')).toBeInTheDocument()
      })

      const switches = screen.getAllByRole('switch')
      const captureSwitch = switches[0]
      await userEvent.click(captureSwitch)

      await waitFor(() => {
        expect(mockUpdateAppOutputCapture).toHaveBeenCalledWith(1, { request_log_output_capture_enabled: false })
      })
    })

    it('calls confirm enable API when modal is confirmed', async () => {
      mockListApps.mockResolvedValueOnce({
        code: 'OK',
        message: 'ok',
        data: [sampleApp],
      })
      mockUpdateAppOutputCapture.mockResolvedValueOnce({
        code: 'OK',
        message: 'ok',
        data: { ...sampleApp, request_log_output_capture_enabled: true },
      })
      mockListApps.mockResolvedValueOnce({
        code: 'OK',
        message: 'ok',
        data: [{ ...sampleApp, request_log_output_capture_enabled: true }],
      })

      renderPage()

      await waitFor(() => {
        expect(screen.getByText('Test App')).toBeInTheDocument()
      })

      const switches = screen.getAllByRole('switch')
      await userEvent.click(switches[0])

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /enable/i })).toBeInTheDocument()
      })

      await userEvent.click(screen.getByRole('button', { name: /enable/i }))

      await waitFor(() => {
        expect(mockUpdateAppOutputCapture).toHaveBeenCalledWith(1, { request_log_output_capture_enabled: true })
      })
    })
  })
})
