import { createContext, useCallback, useMemo, useState, type CSSProperties, type ReactNode } from 'react'
import { ConfigProvider, theme } from 'antd'
import type { Locale } from '../i18n/dict'

const THEME_KEY = 'sangui-admin-theme'
const LOCALE_KEY = 'sangui-admin-locale'
const VALID_THEMES = ['dark', 'light'] as const

type ThemeMode = typeof VALID_THEMES[number]

function isValidTheme(v: string | null): v is ThemeMode {
  return v === 'dark' || v === 'light'
}

function isValidLocale(v: string | null): v is Locale {
  return v === 'zh-CN' || v === 'en-US'
}

function loadTheme(): ThemeMode {
  try {
    const stored = localStorage.getItem(THEME_KEY)
    if (isValidTheme(stored)) return stored
  } catch { /* localStorage unavailable */ }
  return 'dark'
}

function loadLocale(): Locale {
  try {
    const stored = localStorage.getItem(LOCALE_KEY)
    if (isValidLocale(stored)) return stored
  } catch { /* localStorage unavailable */ }
  return 'zh-CN'
}

export interface I18nContextValue {
  locale: Locale
}

export const I18nContext = createContext<I18nContextValue>({ locale: 'zh-CN' })

export interface UIPreferenceContextValue {
  themeMode: ThemeMode
  setThemeMode: (mode: ThemeMode) => void
  locale: Locale
  setLocale: (locale: Locale) => void
}

export const UIPreferenceContext = createContext<UIPreferenceContextValue>({
  themeMode: 'dark',
  setThemeMode: () => {},
  locale: 'zh-CN',
  setLocale: () => {},
})

interface UIPreferenceProviderProps {
  children: ReactNode
}

export default function UIPreferenceProvider({ children }: UIPreferenceProviderProps) {
  const [themeMode, setThemeModeState] = useState<ThemeMode>(loadTheme)
  const [locale, setLocaleState] = useState<Locale>(loadLocale)

  const setThemeMode = useCallback((mode: ThemeMode) => {
    setThemeModeState(mode)
    try { localStorage.setItem(THEME_KEY, mode) } catch { /* ignore */ }
  }, [])

  const setLocale = useCallback((l: Locale) => {
    setLocaleState(l)
    try { localStorage.setItem(LOCALE_KEY, l) } catch { /* ignore */ }
  }, [])

  const antdTheme = useMemo(() => ({
    algorithm: themeMode === 'dark' ? theme.darkAlgorithm : theme.defaultAlgorithm,
  }), [themeMode])

  const appFrameStyle = useMemo<CSSProperties>(() => ({
    minHeight: '100vh',
    background: themeMode === 'dark' ? '#141414' : '#f5f5f5',
  }), [themeMode])

  const i18nValue = useMemo<I18nContextValue>(() => ({ locale }), [locale])

  const preferenceValue = useMemo<UIPreferenceContextValue>(() => ({
    themeMode,
    setThemeMode,
    locale,
    setLocale,
  }), [themeMode, setThemeMode, locale, setLocale])

  return (
    <UIPreferenceContext.Provider value={preferenceValue}>
      <I18nContext.Provider value={i18nValue}>
        <ConfigProvider theme={antdTheme}>
          <div style={appFrameStyle}>
            {children}
          </div>
        </ConfigProvider>
      </I18nContext.Provider>
    </UIPreferenceContext.Provider>
  )
}
