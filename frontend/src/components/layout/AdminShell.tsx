import { createContext, useContext, useMemo, useState, type ReactNode } from 'react'
import { Layout, Menu, Input, Button, Space, Typography } from 'antd'
import type { MenuProps } from 'antd'

const { Header, Sider, Content } = Layout
const { Text } = Typography

export type PageKey = 'model-configs' | 'knowledge' | 'apps' | 'api-keys' | 'smoke' | 'request-logs'

const PAGE_LABELS: Record<PageKey, string> = {
  'model-configs': 'Model Configs',
  'knowledge': 'Knowledge Bases',
  'apps': 'Apps',
  'api-keys': 'API Keys',
  'smoke': 'Smoke Test',
  'request-logs': 'Request Logs',
}

export interface ShellContextValue {
  adminUserId: number | null
  selectedAppId: number | null
  setSelectedAppId: (id: number | null) => void
  navigateTo: (page: PageKey) => void
}

export const ShellContext = createContext<ShellContextValue>({
  adminUserId: null,
  selectedAppId: null,
  setSelectedAppId: () => {},
  navigateTo: () => {},
})

export function useShell(): ShellContextValue {
  return useContext(ShellContext)
}

interface AdminShellProps {
  children: (shell: ShellContextValue & { currentPage: PageKey }) => ReactNode
}

export default function AdminShell({ children }: AdminShellProps) {
  const [adminUserIdInput, setAdminUserIdInput] = useState('')
  const [adminUserId, setAdminUserId] = useState<number | null>(null)
  const [connectError, setConnectError] = useState<string | null>(null)
  const [currentPage, setCurrentPage] = useState<PageKey>('model-configs')
  const [selectedAppId, setSelectedAppId] = useState<number | null>(null)

  function handleConnect() {
    const num = Number(adminUserIdInput)
    if (!adminUserIdInput || !Number.isFinite(num) || num <= 0) {
      setConnectError('Enter a positive number')
      return
    }
    setConnectError(null)
    setAdminUserId(num)
  }

  const menuItems: MenuProps['items'] = useMemo(() => {
    const entries = Object.entries(PAGE_LABELS) as [PageKey, string][]
    return entries.map(([key, label]) => ({
      key,
      label,
    }))
  }, [])

  function handleMenuClick(info: { key: string }) {
    setCurrentPage(info.key as PageKey)
  }

  const shellValue = useMemo<ShellContextValue>(() => ({
    adminUserId,
    selectedAppId,
    setSelectedAppId,
    navigateTo: (page) => setCurrentPage(page),
  }), [adminUserId, selectedAppId])

  if (adminUserId === null) {
    return (
      <div style={{ maxWidth: 400, margin: '120px auto', padding: 24 }}>
        <Typography.Title level={3} style={{ textAlign: 'center', marginBottom: 24 }}>
          Sangui RAG Gateway Admin
        </Typography.Title>
        <Space direction="vertical" style={{ width: '100%' }}>
          <Text>Enter Admin User ID to continue</Text>
          <Input
            value={adminUserIdInput}
            onChange={(e) => { setAdminUserIdInput(e.target.value); setConnectError(null) }}
            placeholder="Positive integer"
            type="number"
            status={connectError ? 'error' : undefined}
            onPressEnter={handleConnect}
          />
          {connectError && <Text type="danger">{connectError}</Text>}
          <Button
            type="primary"
            block
            onClick={handleConnect}
            disabled={!adminUserIdInput || !Number.isFinite(Number(adminUserIdInput)) || Number(adminUserIdInput) <= 0}
          >
            Connect
          </Button>
        </Space>
      </div>
    )
  }

  return (
    <ShellContext.Provider value={shellValue}>
      <Layout style={{ minHeight: '100vh' }}>
        <Sider width={220} theme="light" breakpoint="lg" collapsedWidth={0}>
          <div style={{ padding: '16px 16px 8px', fontWeight: 600, fontSize: 16 }}>
            RAG Gateway Admin
          </div>
          <div style={{ padding: '0 16px 8px' }}>
            <Text type="secondary" style={{ fontSize: 12 }}>
              User #{adminUserId}
            </Text>
          </div>
          <Menu
            mode="inline"
            selectedKeys={[currentPage]}
            items={menuItems}
            onClick={handleMenuClick}
          />
        </Sider>
        <Layout>
          <Header style={{ background: '#fff', padding: '0 24px', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
            <Text strong>{PAGE_LABELS[currentPage]}</Text>
            <Space>
              {selectedAppId !== null && (
                <Text type="secondary">App #{selectedAppId}</Text>
              )}
              <Button size="small" onClick={() => setAdminUserId(null)}>
                Switch User
              </Button>
            </Space>
          </Header>
          <Content style={{ margin: 16, padding: 24, background: '#fff', borderRadius: 8, minHeight: 360 }}>
            {children({ ...shellValue, currentPage })}
          </Content>
        </Layout>
      </Layout>
    </ShellContext.Provider>
  )
}
