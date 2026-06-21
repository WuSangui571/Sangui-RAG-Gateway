import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import ApiKeyOneTimeSecret from '../../components/domain/ApiKeyOneTimeSecret'
import UIPreferenceProvider from '../../app/providers/UIPreferenceProvider'

function setLocale(locale: string) {
  try { localStorage.setItem('sangui-admin-locale', locale) } catch { /* ignore */ }
}

function renderComponent(props: {
  open?: boolean
  plaintextKey?: string | null
  onClose?: () => void
  onGoToSmokeTest?: () => void
  origin?: string
} = {}) {
  return render(
    <UIPreferenceProvider>
      <ApiKeyOneTimeSecret
        open={props.open ?? true}
        plaintextKey={props.plaintextKey ?? 'sk-sangui-test-key-12345'}
        onClose={props.onClose ?? vi.fn()}
        onGoToSmokeTest={props.onGoToSmokeTest}
        origin={props.origin}
      />
    </UIPreferenceProvider>,
  )
}

describe('ApiKeyOneTimeSecret', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setLocale('en-US')
  })

  it('shows SDK base URL and chat completions endpoint for localhost origin', () => {
    renderComponent({ origin: 'http://localhost:3000' })

    expect(screen.getByText('SDK Base URL')).toBeInTheDocument()
    expect(screen.getByText('http://localhost:3000/v1')).toBeInTheDocument()
    expect(screen.getByText('Chat Completions Endpoint')).toBeInTheDocument()
    expect(screen.getByText('http://localhost:3000/v1/chat/completions')).toBeInTheDocument()
  })

  it('shows correct URLs for non-localhost origin', () => {
    renderComponent({ origin: 'https://rag.example.com' })

    expect(screen.getByText('SDK Base URL')).toBeInTheDocument()
    expect(screen.getByText('https://rag.example.com/v1')).toBeInTheDocument()
    expect(screen.getByText('Chat Completions Endpoint')).toBeInTheDocument()
    expect(screen.getByText('https://rag.example.com/v1/chat/completions')).toBeInTheDocument()
  })

  it('SDK base URL contains /v1 but not /chat/completions', () => {
    renderComponent({ origin: 'http://localhost:3000' })

    const sdkUrl = screen.getByText('http://localhost:3000/v1')
    expect(sdkUrl).toBeInTheDocument()
    expect(sdkUrl.textContent).not.toContain('/chat/completions')
    expect(sdkUrl.textContent).toContain('/v1')
  })

  it('full endpoint contains /v1/chat/completions', () => {
    renderComponent({ origin: 'https://rag.example.com' })

    const endpoint = screen.getByText('https://rag.example.com/v1/chat/completions')
    expect(endpoint).toBeInTheDocument()
    expect(endpoint.textContent).toContain('/v1/chat/completions')
  })

  it('does not show dialog when open is false', () => {
    renderComponent({ open: false })

    expect(screen.queryByText('API Key Created')).not.toBeInTheDocument()
  })

  it('shows plaintext key when provided', () => {
    renderComponent({ plaintextKey: 'sk-sangui-my-secret-key' })

    expect(screen.getByText('sk-sangui-my-secret-key')).toBeInTheDocument()
  })

  it('shows one-time warning text', () => {
    renderComponent()

    expect(screen.getByText('API Key Created')).toBeInTheDocument()
    expect(screen.getByText('This key will only be shown once. Copy it now.')).toBeInTheDocument()
  })

  it('calls onClose when saved button is clicked', async () => {
    const onClose = vi.fn()
    renderComponent({ onClose })

    const user = userEvent.setup()
    const savedBtn = screen.getByRole('button', { name: /i have saved this key/i })
    await user.click(savedBtn)

    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it('renders integration labels in Chinese when locale is zh-CN', () => {
    setLocale('zh-CN')
    renderComponent({ origin: 'http://localhost:3000' })

    expect(screen.getByText('SDK Base URL')).toBeInTheDocument()
    expect(screen.getByText('Chat Completions 接口地址')).toBeInTheDocument()
    expect(screen.getByText('使用以下信息配置你的 OpenAI-compatible SDK 或 HTTP 客户端：')).toBeInTheDocument()
    expect(screen.getByText('此密钥仅显示一次，请立即复制保存。')).toBeInTheDocument()
  })

  it('renders integration labels in English when locale is en-US', () => {
    setLocale('en-US')
    renderComponent({ origin: 'http://localhost:3000' })

    expect(screen.getByText('SDK Base URL')).toBeInTheDocument()
    expect(screen.getByText('Chat Completions Endpoint')).toBeInTheDocument()
    expect(screen.getByText('Configure your OpenAI-compatible SDK or HTTP client with:')).toBeInTheDocument()
    expect(screen.getByText('This key will only be shown once. Copy it now.')).toBeInTheDocument()
  })

  it('uses browser runtime origin when origin override is not provided', () => {
    renderComponent()

    expect(screen.getByText('SDK Base URL')).toBeInTheDocument()
    expect(screen.getByText(`${window.location.origin}/v1`)).toBeInTheDocument()
    expect(screen.getByText(`${window.location.origin}/v1/chat/completions`)).toBeInTheDocument()
  })

  it('has copy buttons for both URLs', () => {
    renderComponent({ origin: 'http://localhost:3000' })

    const copyButtons = screen.getAllByRole('button', { name: /copy/i })
    // One copy button for the key, one for SDK URL, one for endpoint = at least 3
    expect(copyButtons.length).toBeGreaterThanOrEqual(3)
  })
})
