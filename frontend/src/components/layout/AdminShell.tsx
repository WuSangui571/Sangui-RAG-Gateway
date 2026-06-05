import { createContext, useContext, useMemo, useState, type ReactNode } from 'react'
import { Layout, Menu, Input, Button, Space, Typography, Select, theme } from 'antd'
import { SunOutlined, MoonOutlined } from '@ant-design/icons'
import type { MenuProps } from 'antd'
import { useI18n } from '../../app/i18n'
import { UIPreferenceContext } from '../../app/providers/UIPreferenceProvider'
import type { I18nKey } from '../../app/i18n/dict'

const { Header, Sider, Content } = Layout
const { Text } = Typography

export type PageKey = 'model-configs' | 'knowledge' | 'apps' | 'api-keys' | 'smoke' | 'request-logs'

const PAGE_KEY_TO_I18N: Record<PageKey, I18nKey> = {
  'model-configs': 'nav.model-configs',
  'knowledge': 'nav.knowledge',
  'apps': 'nav.apps',
  'api-keys': 'nav.api-keys',
  'smoke': 'nav.smoke',
  'request-logs': 'nav.request-logs',
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
  const { t, locale } = useI18n()
  const { themeMode, setThemeMode, setLocale } = useContext(UIPreferenceContext)
  const { token } = theme.useToken()

  const [adminUserIdInput, setAdminUserIdInput] = useState('')
  const [adminUserId, setAdminUserId] = useState<number | null>(null)
  const [connectError, setConnectError] = useState<string | null>(null)
  const [currentPage, setCurrentPage] = useState<PageKey>('model-configs')
  const [selectedAppId, setSelectedAppId] = useState<number | null>(null)

  function handleConnect() {
    const num = Number(adminUserIdInput)
    if (!adminUserIdInput || !Number.isFinite(num) || num <= 0) {
      setConnectError(t('app.errorUserId'))
      return
    }
    setConnectError(null)
    setAdminUserId(num)
  }

  const menuItems: MenuProps['items'] = useMemo(() => {
    const entries = Object.entries(PAGE_KEY_TO_I18N) as [PageKey, I18nKey][]
    return entries.map(([key, labelKey]) => ({
      key,
      label: t(labelKey),
    }))
  }, [t])

  function handleMenuClick(info: { key: string }) {
    setCurrentPage(info.key as PageKey)
  }

  const shellValue = useMemo<ShellContextValue>(() => ({
    adminUserId,
    selectedAppId,
    setSelectedAppId,
    navigateTo: (page) => setCurrentPage(page),
  }), [adminUserId, selectedAppId])

  const shellTitle = t('app.title')

  if (adminUserId === null) {
    return (
      <div style={{ maxWidth: 400, margin: '120px auto', padding: 24 }}>
        <Typography.Title level={3} style={{ textAlign: 'center', marginBottom: 24 }}>
          {shellTitle}
        </Typography.Title>
        <Space direction="vertical" style={{ width: '100%' }}>
          <Text>{t('app.enterUserId')}</Text>
          <Input
            value={adminUserIdInput}
            onChange={(e) => { setAdminUserIdInput(e.target.value); setConnectError(null) }}
            placeholder={t('app.placeholderUserId')}
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
            {t('app.connect')}
          </Button>
        </Space>
      </div>
    )
  }

  return (
    <ShellContext.Provider value={shellValue}>
      <Layout style={{ minHeight: '100vh' }}>
        <Sider width={220} theme={themeMode} breakpoint="lg" collapsedWidth={0}>
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
          <Header style={{ background: token.colorBgContainer, padding: '0 24px', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
            <Text strong>{t(PAGE_KEY_TO_I18N[currentPage])}</Text>
            <Space>
              <Select
                size="small"
                value={locale}
                onChange={(v) => setLocale(v)}
                style={{ width: 100 }}
                options={[
                  { value: 'zh-CN', label: t('lang.zhCN') },
                  { value: 'en-US', label: t('lang.enUS') },
                ]}
              />
              <Button
                size="small"
                icon={themeMode === 'dark' ? <SunOutlined /> : <MoonOutlined />}
                onClick={() => setThemeMode(themeMode === 'dark' ? 'light' : 'dark')}
              >
                {themeMode === 'dark' ? t('theme.light') : t('theme.dark')}
              </Button>
              {selectedAppId !== null && (
                <Text type="secondary">App #{selectedAppId}</Text>
              )}
              <Button size="small" onClick={() => setAdminUserId(null)}>
                {t('app.switchUser')}
              </Button>
            </Space>
          </Header>
          <Content style={{ margin: 16, padding: 24, background: token.colorBgContainer, borderRadius: 8, minHeight: 360 }}>
            {children({ ...shellValue, currentPage })}
          </Content>
        </Layout>
      </Layout>
    </ShellContext.Provider>
  )
}
