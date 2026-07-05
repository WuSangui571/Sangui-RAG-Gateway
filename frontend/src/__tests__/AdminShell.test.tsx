import type { ReactNode } from 'react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import AdminShell from '../components/layout/AdminShell'
import type { ShellContextValue, PageKey } from '../components/layout/AdminShell'
import UIPreferenceProvider from '../app/providers/UIPreferenceProvider'

vi.mock('../api/auth', () => ({
  login: vi.fn(),
  getCurrentUser: vi.fn(),
}))

vi.mock('../api/http', () => ({
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

import { login } from '../api/auth'
import { setAuthToken } from '../api/http'

const mockLogin = login as ReturnType<typeof vi.fn>
const mockSetAuthToken = setAuthToken as ReturnType<typeof vi.fn>

function setLocale(locale: string) {
  try { localStorage.setItem('sangui-admin-locale', locale) } catch { /* ignore */ }
}

function renderShell(children: (ctx: ShellContextValue & { currentPage: PageKey }) => ReactNode = () => <div data-testid="page-content">Page</div>) {
  return render(
    <UIPreferenceProvider>
      <AdminShell>{children}</AdminShell>
    </UIPreferenceProvider>,
  )
}

describe('AdminShell', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setLocale('en-US')
  })

  describe('unauthenticated state', () => {
    it('shows login screen when not authenticated', () => {
      renderShell()
      expect(screen.getByTestId('login-wrapper')).toBeInTheDocument()
      expect(screen.getByPlaceholderText('Username')).toBeInTheDocument()
    })

    it('shows app title on login screen', () => {
      renderShell()
      expect(screen.getByText('Sangui RAG Gateway Admin')).toBeInTheDocument()
    })

    it('does not show navigation or page content', () => {
      renderShell()
      expect(screen.queryByText('Model Configs')).not.toBeInTheDocument()
      expect(screen.queryByTestId('page-content')).not.toBeInTheDocument()
    })
  })

  describe('login behavior', () => {
    it('calls login API on submit and transitions to authenticated shell', async () => {
      mockLogin.mockResolvedValueOnce({
        code: 'OK',
        message: 'ok',
        data: {
          access_token: 'test-token',
          token_type: 'Bearer',
          expires_at: new Date(Date.now() + 3600000).toISOString(),
          user: { id: 1, username: 'admin', status: 'ACTIVE' },
        },
      })

      renderShell()

      const user = userEvent.setup()
      await user.type(screen.getByPlaceholderText('Username'), 'admin')
      await user.type(screen.getByPlaceholderText('Password'), 'pass')
      await user.click(screen.getByRole('button', { name: /login/i }))

      await waitFor(() => {
        expect(mockLogin).toHaveBeenCalledWith({ username: 'admin', password: 'pass' })
        expect(mockSetAuthToken).toHaveBeenCalledWith('test-token')
      })

      await waitFor(() => {
        expect(screen.getByText('admin')).toBeInTheDocument()
      })
    })

    it('shows error message on login failure (401)', async () => {
      const err = new Error('Invalid credentials')
      ;(err as unknown as Record<string, unknown>).status = 401
      mockLogin.mockRejectedValueOnce(err)

      renderShell()

      const user = userEvent.setup()
      await user.type(screen.getByPlaceholderText('Username'), 'admin')
      await user.type(screen.getByPlaceholderText('Password'), 'wrong')
      await user.click(screen.getByRole('button', { name: /login/i }))

      await waitFor(() => {
        expect(screen.getByText('Invalid username or password')).toBeInTheDocument()
      })
    })

    it('shows validation error when credentials are empty', async () => {
      renderShell()

      const user = userEvent.setup()
      await user.click(screen.getByPlaceholderText('Username'))
      await user.keyboard('{Enter}')

      await waitFor(() => {
        expect(screen.getByText('Enter username and password')).toBeInTheDocument()
      })
      expect(mockLogin).not.toHaveBeenCalled()
    })
  })

  describe('authenticated navigation', () => {
    beforeEach(() => {
      mockLogin.mockResolvedValueOnce({
        code: 'OK',
        message: 'ok',
        data: {
          access_token: 'test-token',
          token_type: 'Bearer',
          expires_at: new Date(Date.now() + 3600000).toISOString(),
          user: { id: 1, username: 'admin', status: 'ACTIVE' },
        },
      })
    })

    it('shows navigation menu items after login', async () => {
      renderShell()

      const user = userEvent.setup()
      await user.type(screen.getByPlaceholderText('Username'), 'admin')
      await user.type(screen.getByPlaceholderText('Password'), 'pass')
      await user.click(screen.getByRole('button', { name: /login/i }))

      await waitFor(() => {
        const menuItems = screen.getAllByText('Model Configs')
        expect(menuItems.length).toBeGreaterThanOrEqual(1)
        expect(screen.getByText('Knowledge Bases')).toBeInTheDocument()
        expect(screen.getByText('Apps')).toBeInTheDocument()
        expect(screen.getByText('API Keys')).toBeInTheDocument()
        expect(screen.getByText('Test Chat')).toBeInTheDocument()
        expect(screen.getByText('Request Logs')).toBeInTheDocument()
      })
    })

    it('navigates to different pages via menu clicks', async () => {
      const pages: string[] = []
      renderShell((ctx) => {
        pages.push(ctx.currentPage)
        return <div data-testid="page-content">{ctx.currentPage}</div>
      })

      const user = userEvent.setup()
      await user.type(screen.getByPlaceholderText('Username'), 'admin')
      await user.type(screen.getByPlaceholderText('Password'), 'pass')
      await user.click(screen.getByRole('button', { name: /login/i }))

      await waitFor(() => {
        expect(screen.getByText('Apps')).toBeInTheDocument()
      })

      await user.click(screen.getByText('Apps'))
      await waitFor(() => {
        expect(screen.getByTestId('page-content')).toHaveTextContent('apps')
      })

      await user.click(screen.getByText('Request Logs'))
      await waitFor(() => {
        expect(screen.getByTestId('page-content')).toHaveTextContent('request-logs')
      })

      await user.click(screen.getByText('Model Configs'))
      await waitFor(() => {
        expect(screen.getByTestId('page-content')).toHaveTextContent('model-configs')
      })

      await user.click(screen.getByText('API Keys'))
      await waitFor(() => {
        expect(screen.getByTestId('page-content')).toHaveTextContent('api-keys')
      })

      await user.click(screen.getByText('Knowledge Bases'))
      await waitFor(() => {
        expect(screen.getByTestId('page-content')).toHaveTextContent('knowledge')
      })

      await user.click(screen.getByText('Test Chat'))
      await waitFor(() => {
        expect(screen.getByTestId('page-content')).toHaveTextContent('test-chat')
      })
    })
  })
})
