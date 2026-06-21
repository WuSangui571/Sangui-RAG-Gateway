import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, fireEvent } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import KnowledgeBasePage from '../../pages/knowledge/KnowledgeBasePage'
import { ShellContext } from '../../components/layout/AdminShell'
import type { ShellContextValue } from '../../components/layout/AdminShell'
import UIPreferenceProvider from '../../app/providers/UIPreferenceProvider'

vi.mock('../../api/knowledge', () => ({
  listKnowledgeBases: vi.fn(),
  createKnowledgeBase: vi.fn(),
}))

vi.mock('../../api/documents', () => ({
  uploadDocument: vi.fn(),
  listDocuments: vi.fn(),
  retryDocument: vi.fn(),
}))

vi.mock('../../api/model-configs', () => ({
  listModelConfigs: vi.fn(),
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

import { listKnowledgeBases } from '../../api/knowledge'
import { listDocuments, uploadDocument } from '../../api/documents'

const mockListKnowledgeBases = listKnowledgeBases as ReturnType<typeof vi.fn>
const mockListDocuments = listDocuments as ReturnType<typeof vi.fn>
const mockUploadDocument = uploadDocument as ReturnType<typeof vi.fn>

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
        <KnowledgeBasePage />
      </ShellContext.Provider>
    </UIPreferenceProvider>,
  )
}

function kbFactory(overrides: Record<string, unknown> = {}) {
  return {
    id: 1,
    user_id: 1,
    name: 'Test KB',
    embedding_model: 'text-embedding-v4',
    embedding_dimension: 1024,
    status: 'EMPTY' as const,
    created_at: '2026-01-01T00:00:00Z',
    updated_at: '2026-01-01T00:00:00Z',
    ...overrides,
  }
}

function docFactory(overrides: Record<string, unknown> = {}) {
  return {
    id: 1,
    user_id: 1,
    knowledge_base_id: 1,
    original_filename: 'test.md',
    content_type: 'text/markdown',
    file_size: 1024,
    status: 'READY' as const,
    chunk_count: 5,
    error_message: null,
    created_at: '2026-01-01T00:00:00Z',
    updated_at: '2026-01-01T00:00:00Z',
    processing_task_id: 1,
    processing_task_status: 'SUCCEEDED' as const,
    processing_attempt_count: 1,
    processing_next_attempt_at: null,
    processing_started_at: '2026-01-01T00:00:00Z',
    processing_finished_at: '2026-01-01T00:01:00Z',
    ...overrides,
  }
}

describe('KnowledgeBasePage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setLocale('en-US')
  })

  describe('unauthenticated state', () => {
    it('does not call listKnowledgeBases when adminUserId is null', () => {
      renderPage({ adminUserId: null })
      expect(mockListKnowledgeBases).not.toHaveBeenCalled()
    })
  })

  describe('KB list empty state', () => {
    it('shows actionable empty hint when no KBs exist', async () => {
      mockListKnowledgeBases.mockResolvedValueOnce({
        code: 'OK',
        message: 'ok',
        data: [],
      })

      renderPage()

      await waitFor(() => {
        expect(screen.getByText('Create a knowledge base to get started.')).toBeInTheDocument()
      })
    })

    it('shows create button in empty state', async () => {
      mockListKnowledgeBases.mockResolvedValueOnce({
        code: 'OK',
        message: 'ok',
        data: [],
      })

      renderPage()

      await waitFor(() => {
        expect(screen.getByText('Create Knowledge Base')).toBeInTheDocument()
      })
    })

    it('does not show empty hint when KBs exist', async () => {
      mockListKnowledgeBases.mockResolvedValueOnce({
        code: 'OK',
        message: 'ok',
        data: [kbFactory()],
      })

      renderPage()

      await waitFor(() => {
        expect(screen.getByText('Test KB')).toBeInTheDocument()
      })
      expect(screen.queryByText('Create a knowledge base to get started.')).not.toBeInTheDocument()
    })
  })

  describe('KB list with items', () => {
    it('shows KB names in table', async () => {
      mockListKnowledgeBases.mockResolvedValueOnce({
        code: 'OK',
        message: 'ok',
        data: [kbFactory({ id: 1, name: 'Alpha' }), kbFactory({ id: 2, name: 'Beta' })],
      })

      renderPage()

      await waitFor(() => {
        expect(screen.getByText('Alpha')).toBeInTheDocument()
        expect(screen.getByText('Beta')).toBeInTheDocument()
      })
    })
  })

  describe('KB selection and document fetching', () => {
    it('does not call listDocuments when no KB is selected', async () => {
      mockListKnowledgeBases.mockResolvedValueOnce({
        code: 'OK',
        message: 'ok',
        data: [kbFactory()],
      })

      renderPage()

      await waitFor(() => {
        expect(screen.getByText('Test KB')).toBeInTheDocument()
      })
      expect(mockListDocuments).not.toHaveBeenCalled()
    })

    it('calls listDocuments when a KB is selected', async () => {
      mockListKnowledgeBases.mockResolvedValueOnce({
        code: 'OK',
        message: 'ok',
        data: [kbFactory()],
      })
      mockListDocuments.mockResolvedValueOnce({
        code: 'OK',
        message: 'ok',
        data: [],
      })

      const user = userEvent.setup()
      renderPage()

      await waitFor(() => {
        expect(screen.getByText('Test KB')).toBeInTheDocument()
      })

      await user.click(screen.getByText('Test KB'))

      await waitFor(() => {
        expect(mockListDocuments).toHaveBeenCalledWith(1, undefined)
      })
    })
  })

  describe('document empty state', () => {
    it('shows empty document hint with upload button when KB is EMPTY and no documents', async () => {
      mockListKnowledgeBases.mockResolvedValueOnce({
        code: 'OK',
        message: 'ok',
        data: [kbFactory({ status: 'EMPTY' })],
      })
      mockListDocuments.mockResolvedValueOnce({
        code: 'OK',
        message: 'ok',
        data: [],
      })

      const user = userEvent.setup()
      renderPage()

      await waitFor(() => {
        expect(screen.getByText('Test KB')).toBeInTheDocument()
      })
      await user.click(screen.getByText('Test KB'))

      await waitFor(() => {
        expect(screen.getByText('This knowledge base has no searchable documents yet.')).toBeInTheDocument()
        expect(screen.getAllByText('Upload Document').length).toBeGreaterThanOrEqual(1)
      })
    })

    it('does not show document empty state upload CTA when documents exist', async () => {
      mockListKnowledgeBases.mockResolvedValueOnce({
        code: 'OK',
        message: 'ok',
        data: [kbFactory({ status: 'READY' })],
      })
      mockListDocuments.mockResolvedValueOnce({
        code: 'OK',
        message: 'ok',
        data: [docFactory({ original_filename: 'doc.md' })],
      })

      const user = userEvent.setup()
      renderPage()

      await waitFor(() => {
        expect(screen.getByText('Test KB')).toBeInTheDocument()
      })
      await user.click(screen.getByText('Test KB'))

      await waitFor(() => {
        expect(screen.getByText('doc.md')).toBeInTheDocument()
      })
      expect(screen.queryByText('This knowledge base has no searchable documents yet.')).not.toBeInTheDocument()
    })
  })

  describe('KB status hints', () => {
    it('shows EMPTY status hint when KB status is EMPTY', async () => {
      mockListKnowledgeBases.mockResolvedValueOnce({
        code: 'OK',
        message: 'ok',
        data: [kbFactory({ status: 'EMPTY' })],
      })
      mockListDocuments.mockResolvedValueOnce({
        code: 'OK',
        message: 'ok',
        data: [],
      })

      const user = userEvent.setup()
      renderPage()

      await waitFor(() => { expect(screen.getByText('Test KB')).toBeInTheDocument() })
      await user.click(screen.getByText('Test KB'))

      await waitFor(() => {
        expect(screen.getByText('Knowledge base has no searchable documents. Upload documents to start parsing and embedding.')).toBeInTheDocument()
      })
    })

    it('shows PROCESSING status hint when KB status is PROCESSING', async () => {
      mockListKnowledgeBases.mockResolvedValueOnce({
        code: 'OK',
        message: 'ok',
        data: [kbFactory({ status: 'PROCESSING' })],
      })
      mockListDocuments.mockResolvedValueOnce({
        code: 'OK',
        message: 'ok',
        data: [],
      })

      const user = userEvent.setup()
      renderPage()

      await waitFor(() => { expect(screen.getByText('Test KB')).toBeInTheDocument() })
      await user.click(screen.getByText('Test KB'))

      await waitFor(() => {
        expect(screen.getByText('Documents are being parsed and embedded. Status will refresh automatically.')).toBeInTheDocument()
      })
    })

    it('shows FAILED status hint when KB status is FAILED', async () => {
      mockListKnowledgeBases.mockResolvedValueOnce({
        code: 'OK',
        message: 'ok',
        data: [kbFactory({ status: 'FAILED' })],
      })
      mockListDocuments.mockResolvedValueOnce({
        code: 'OK',
        message: 'ok',
        data: [],
      })

      const user = userEvent.setup()
      renderPage()

      await waitFor(() => { expect(screen.getByText('Test KB')).toBeInTheDocument() })
      await user.click(screen.getByText('Test KB'))

      await waitFor(() => {
        expect(screen.getByText('Recent processing failed. Check document error details and retry when ready.')).toBeInTheDocument()
      })
    })

    it('does not show status hint for READY KB', async () => {
      mockListKnowledgeBases.mockResolvedValueOnce({
        code: 'OK',
        message: 'ok',
        data: [kbFactory({ status: 'READY' })],
      })
      mockListDocuments.mockResolvedValueOnce({
        code: 'OK',
        message: 'ok',
        data: [docFactory()],
      })

      const user = userEvent.setup()
      renderPage()

      await waitFor(() => { expect(screen.getByText('Test KB')).toBeInTheDocument() })
      await user.click(screen.getByText('Test KB'))

      await waitFor(() => {
        expect(screen.getByText('test.md')).toBeInTheDocument()
      })
      expect(screen.queryByText('Knowledge base has no searchable documents. Upload documents to start parsing and embedding.')).not.toBeInTheDocument()
      expect(screen.queryByText('Documents are being parsed and embedded. Status will refresh automatically.')).not.toBeInTheDocument()
      expect(screen.queryByText('Recent processing failed. Check document error details and retry when ready.')).not.toBeInTheDocument()
    })
  })

  describe('upload error handling', () => {
    it('shows unsupported file type error when file extension is not allowed', async () => {
      mockListKnowledgeBases.mockResolvedValueOnce({
        code: 'OK',
        message: 'ok',
        data: [kbFactory()],
      })
      mockListDocuments.mockResolvedValueOnce({
        code: 'OK',
        message: 'ok',
        data: [],
      })

      const user = userEvent.setup()
      const { container } = renderPage()

      await waitFor(() => { expect(screen.getByText('Test KB')).toBeInTheDocument() })
      await user.click(screen.getByText('Test KB'))

      await waitFor(() => {
        expect(screen.getByText('This knowledge base has no searchable documents yet.')).toBeInTheDocument()
      })

      const fileInput = container.querySelector('input[type="file"]') as HTMLInputElement
      const unsupportedFile = new File(['content'], 'test.exe', { type: 'application/octet-stream' })
      fireEvent.change(fileInput, { target: { files: [unsupportedFile] } })

      await waitFor(() => {
        expect(screen.getByText(/Unsupported file type/i)).toBeInTheDocument()
      })
      expect(mockUploadDocument).not.toHaveBeenCalled()
    })

    it('shows error when uploadDocument API returns non-OK', async () => {
      mockListKnowledgeBases.mockResolvedValueOnce({
        code: 'OK',
        message: 'ok',
        data: [kbFactory()],
      })
      mockListDocuments.mockResolvedValueOnce({
        code: 'OK',
        message: 'ok',
        data: [],
      })
      mockUploadDocument.mockResolvedValueOnce({
        code: 'ERROR',
        message: 'Upload rejected',
        data: null,
      })

      const user = userEvent.setup()
      const { container } = renderPage()

      await waitFor(() => { expect(screen.getByText('Test KB')).toBeInTheDocument() })
      await user.click(screen.getByText('Test KB'))

      await waitFor(() => {
        expect(screen.getByText('This knowledge base has no searchable documents yet.')).toBeInTheDocument()
      })

      const fileInput = container.querySelector('input[type="file"]') as HTMLInputElement
      const validFile = new File(['content'], 'test.md', { type: 'text/markdown' })
      fireEvent.change(fileInput, { target: { files: [validFile] } })

      await waitFor(() => {
        expect(screen.getByText(/Upload rejected/)).toBeInTheDocument()
      })
    })

    it('shows error when uploadDocument API rejects', async () => {
      mockListKnowledgeBases.mockResolvedValueOnce({
        code: 'OK',
        message: 'ok',
        data: [kbFactory()],
      })
      mockListDocuments.mockResolvedValueOnce({
        code: 'OK',
        message: 'ok',
        data: [],
      })
      mockUploadDocument.mockRejectedValueOnce(new Error('Network error'))

      const user = userEvent.setup()
      const { container } = renderPage()

      await waitFor(() => { expect(screen.getByText('Test KB')).toBeInTheDocument() })
      await user.click(screen.getByText('Test KB'))

      await waitFor(() => {
        expect(screen.getByText('This knowledge base has no searchable documents yet.')).toBeInTheDocument()
      })

      const fileInput = container.querySelector('input[type="file"]') as HTMLInputElement
      const validFile = new File(['content'], 'test.txt', { type: 'text/plain' })
      fireEvent.change(fileInput, { target: { files: [validFile] } })

      await waitFor(() => {
        expect(screen.getByText('Network error')).toBeInTheDocument()
      })
    })
  })

  describe('safe rendering', () => {
    it('renders document metadata in table', async () => {
      mockListKnowledgeBases.mockResolvedValueOnce({
        code: 'OK',
        message: 'ok',
        data: [kbFactory({ status: 'READY' })],
      })
      mockListDocuments.mockResolvedValueOnce({
        code: 'OK',
        message: 'ok',
        data: [docFactory({ original_filename: 'readme.md', file_size: 2048, chunk_count: 10 })],
      })

      const user = userEvent.setup()
      renderPage()

      await waitFor(() => { expect(screen.getByText('Test KB')).toBeInTheDocument() })
      await user.click(screen.getByText('Test KB'))

      await waitFor(() => {
        expect(screen.getByText('readme.md')).toBeInTheDocument()
        expect(screen.getByText('2.0KB')).toBeInTheDocument()
        expect(screen.getByText('10')).toBeInTheDocument()
      })
    })

    it('does not render forbidden fields in DOM', async () => {
      mockListKnowledgeBases.mockResolvedValueOnce({
        code: 'OK',
        message: 'ok',
        data: [kbFactory({ status: 'READY' })],
      })
      mockListDocuments.mockResolvedValueOnce({
        code: 'OK',
        message: 'ok',
        data: [docFactory()],
      })

      const user = userEvent.setup()
      const { container } = renderPage()

      await waitFor(() => { expect(screen.getByText('Test KB')).toBeInTheDocument() })
      await user.click(screen.getByText('Test KB'))

      await waitFor(() => {
        expect(screen.getByText('test.md')).toBeInTheDocument()
      })

      const html = container.innerHTML
      const forbiddenTerms = [
        'storage_path',
        'chunk_content',
        'prompt',
        'api_key',
        'key_hash',
        'authorization',
        'upstream_api_key',
        'api_key_encrypted',
        'augmented_prompt',
        'messages',
        'provider_response_body',
        'stack_trace',
        'raw_sse',
        'environment',
      ]
      for (const term of forbiddenTerms) {
        expect(html).not.toContain(term)
      }
    })
  })
})
