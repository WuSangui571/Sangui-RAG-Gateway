import '@testing-library/jest-dom/vitest'

const getComputedStyle = window.getComputedStyle.bind(window)

Object.defineProperty(window, 'getComputedStyle', {
  writable: true,
  value: (elt: Element, _pseudoElt?: string | null) => getComputedStyle(elt),
})

Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: (query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: () => {},
    removeListener: () => {},
    addEventListener: () => {},
    removeEventListener: () => {},
    dispatchEvent: () => false,
  }),
})
