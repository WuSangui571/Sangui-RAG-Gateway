import AdminShell from './components/layout/AdminShell'
import type { ShellContextValue, PageKey } from './components/layout/AdminShell'
import ModelConfigPage from './pages/model-configs/ModelConfigPage'
import KnowledgeBasePage from './pages/knowledge/KnowledgeBasePage'
import AppConfigPage from './pages/apps/AppConfigPage'
import ApiKeyPage from './pages/api-keys/ApiKeyPage'
import SmokeTestPage from './pages/smoke/SmokeTestPage'
import RequestLogListPage from './pages/request-logs/RequestLogListPage'

interface PageContext extends ShellContextValue {
  currentPage: PageKey
}

export default function App() {
  return (
    <AdminShell>
      {(ctx: PageContext) => {
        switch (ctx.currentPage) {
          case 'model-configs':
            return <ModelConfigPage />
          case 'knowledge':
            return <KnowledgeBasePage />
          case 'apps':
            return <AppConfigPage />
          case 'api-keys':
            return <ApiKeyPage />
          case 'smoke':
            return <SmokeTestPage />
          case 'request-logs':
            return <RequestLogListPage
              persistentAppId={ctx.selectedAppId ?? undefined}
            />
          default:
            return <ModelConfigPage />
        }
      }}
    </AdminShell>
  )
}
