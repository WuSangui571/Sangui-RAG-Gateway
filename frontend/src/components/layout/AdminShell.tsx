import { createContext, useContext, useMemo, useState, useEffect, useCallback, type ReactNode } from 'react'
import { Layout, Menu, Input, Button, Space, Typography, Select, theme } from 'antd'
import { SunOutlined, MoonOutlined } from '@ant-design/icons'
import type { MenuProps } from 'antd'
import { useI18n } from '../../app/i18n'
import { UIPreferenceContext } from '../../app/providers/UIPreferenceProvider'
import type { I18nKey } from '../../app/i18n/dict'
import { login } from '../../api/auth'
import { setAuthToken, setUnauthorizedHandler } from '../../api/http'
import type { AdminUserVO } from '../../types/auth'

const { Header, Sider, Content } = Layout
const { Text } = Typography

export type PageKey = 'model-configs' | 'knowledge' | 'apps' | 'api-keys' | 'smoke' | 'test-chat' | 'request-logs'

const PAGE_KEY_TO_I18N: Record<PageKey, I18nKey> = {
  'model-configs': 'nav.model-configs',
  'knowledge': 'nav.knowledge',
  'apps': 'nav.apps',
  'api-keys': 'nav.api-keys',
  'smoke': 'nav.smoke',
  'test-chat': 'nav.test-chat',
  'request-logs': 'nav.request-logs',
}

export interface ShellContextValue {
  adminUserId: number | null
  currentUser: AdminUserVO | null
  selectedAppId: number | null
  setSelectedAppId: (id: number | null) => void
  navigateTo: (page: PageKey) => void
  logout: () => void
}

export const ShellContext = createContext<ShellContextValue>({
  adminUserId: null,
  currentUser: null,
  selectedAppId: null,
  setSelectedAppId: () => {},
  navigateTo: () => {},
  logout: () => {},
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

  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [loginError, setLoginError] = useState<string | null>(null)
  const [loggingIn, setLoggingIn] = useState(false)
  const [currentUser, setCurrentUser] = useState<AdminUserVO | null>(null)
  const [currentPage, setCurrentPage] = useState<PageKey>('model-configs')
  const [selectedAppId, setSelectedAppId] = useState<number | null>(null)

  const handleLogin = useCallback(async () => {
    if (!username.trim() || !password) {
      setLoginError(t('app.errorCredentials'))
      return
    }
    setLoginError(null)
    setLoggingIn(true)
    try {
      const res = await login({ username: username.trim(), password })
      const data = res.data
      setAuthToken(data.access_token)
      setCurrentUser(data.user)
    } catch (e: unknown) {
      const err = e as { status?: number; message?: string }
      if (err.status === 401) {
        setLoginError(t('app.errorInvalidCredentials'))
      } else {
        setLoginError(err.message || t('app.errorLoginFailed'))
      }
      setAuthToken(null)
    } finally {
      setLoggingIn(false)
    }
  }, [username, password, t])

  const handleLogout = useCallback(() => {
    setAuthToken(null)
    setCurrentUser(null)
    setSelectedAppId(null)
    setUsername('')
    setPassword('')
    setLoginError(null)
  }, [])

  useEffect(() => {
    setUnauthorizedHandler(handleLogout)
    return () => setUnauthorizedHandler(null)
  }, [handleLogout])

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
    adminUserId: currentUser ? currentUser.id : null,
    currentUser,
    selectedAppId,
    setSelectedAppId,
    navigateTo: (page) => setCurrentPage(page),
    logout: handleLogout,
  }), [currentUser, selectedAppId, handleLogout])

  const shellTitle = t('app.title')

  if (!currentUser) {
    return (
      <div
        data-testid="login-wrapper"
        style={{
        minHeight: '100vh',
        display: 'flex',
        justifyContent: 'center',
        alignItems: 'center',
        background: token.colorBgLayout,
        boxSizing: 'border-box',
        padding: 24,
      }}>
        <div style={{ maxWidth: 400, width: '100%' }}>
          <Typography.Title level={3} style={{ textAlign: 'center', marginBottom: 24 }}>
            {shellTitle}
          </Typography.Title>
          <Space direction="vertical" style={{ width: '100%' }}>
            <Input
              value={username}
              onChange={(e) => { setUsername(e.target.value); setLoginError(null) }}
              placeholder={t('app.placeholderUsername')}
              autoComplete="username"
              onPressEnter={handleLogin}
            />
            <Input.Password
              value={password}
              onChange={(e) => { setPassword(e.target.value); setLoginError(null) }}
              placeholder={t('app.placeholderPassword')}
              autoComplete="current-password"
              onPressEnter={handleLogin}
            />
            {loginError && <Text type="danger">{loginError}</Text>}
            <Button
              type="primary"
              block
              onClick={handleLogin}
              loading={loggingIn}
              disabled={!username.trim() || !password}
            >
              {t('app.login')}
            </Button>
          </Space>
        </div>
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
              {currentUser.username}
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
              <Button size="small" onClick={handleLogout}>
                {t('app.logout')}
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
