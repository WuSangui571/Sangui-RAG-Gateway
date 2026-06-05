import { useCallback, useContext } from 'react'
import { I18nContext } from '../providers/UIPreferenceProvider'
import dict from './dict'
import type { I18nKey } from './dict'

export function useI18n() {
  const { locale } = useContext(I18nContext)
  const d = dict[locale]

  const t = useCallback((key: I18nKey, params?: Record<string, string | number>): string => {
    let value: string = (d as Record<string, string>)[key] ?? key
    if (params) {
      for (const [k, v] of Object.entries(params)) {
        value = value.replace(`{${k}}`, String(v))
      }
    }
    return value
  }, [d])

  const tCommon = useCallback((errorMessage: string): string => {
    return (d as Record<string, string>)[`common.${errorMessage}`] ?? errorMessage
  }, [d])

  return { t, tCommon, locale }
}
