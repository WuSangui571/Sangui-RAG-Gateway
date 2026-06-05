import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from './App'
import UIPreferenceProvider from './app/providers/UIPreferenceProvider'
import './styles/index.css'

const rootElement = document.getElementById('root')

if (!rootElement) {
  throw new Error('Root element #root was not found')
}

createRoot(rootElement).render(
  <StrictMode>
    <UIPreferenceProvider>
      <App />
    </UIPreferenceProvider>
  </StrictMode>,
)
