import { test, expect, type Page } from '@playwright/test'

const DARK_BG_RGB = 'rgb(30, 20, 20)'
const DARK_LAYOUT_RGB = 'rgb(0, 0, 0)'
const LIGHT_BG_RGB = 'rgb(245, 245, 245)'

async function setThemeAndReload(page: Page, theme: 'dark' | 'light') {
  await page.evaluate((t) => {
    localStorage.setItem('sangui-admin-theme', t)
  }, theme)
  await page.reload()
  await page.waitForLoadState('networkidle')
}

async function assertDarkBackgrounds(page: Page) {
  await expect(page.locator('body')).toHaveCSS('background-color', DARK_BG_RGB)
  await expect(page.locator('#root')).toHaveCSS('background-color', DARK_BG_RGB)
  await expect(page.locator('[data-testid="app-frame"]')).toHaveCSS('background-color', DARK_BG_RGB)
  await expect(page.locator('[data-testid="login-wrapper"]')).toHaveCSS('background-color', DARK_LAYOUT_RGB)
}

async function assertLightBackgrounds(page: Page) {
  await expect(page.locator('body')).toHaveCSS('background-color', LIGHT_BG_RGB)
  await expect(page.locator('#root')).toHaveCSS('background-color', LIGHT_BG_RGB)
  await expect(page.locator('[data-testid="app-frame"]')).toHaveCSS('background-color', LIGHT_BG_RGB)
  await expect(page.locator('[data-testid="login-wrapper"]')).toHaveCSS('background-color', LIGHT_BG_RGB)
}

async function assertViewportEdgesNotWhite(page: Page) {
  const viewport = page.viewportSize()
  if (!viewport) {
    throw new Error('Viewport size is not available')
  }
  const { width, height } = viewport
  const corners: [number, number][] = [
    [0, 0],
    [width - 1, 0],
    [0, height - 1],
    [width - 1, height - 1],
  ]

  for (const [x, y] of corners) {
    const bgColor = await page.evaluate(({ x, y }) => {
      const el = document.elementFromPoint(x, y)
      if (!el) return null
      return window.getComputedStyle(el).backgroundColor
    }, { x, y })
    expect(bgColor, `Viewport corner (${x}, ${y}) should not be white`).not.toBe('rgb(255, 255, 255)')
  }
}

test.describe('Admin login theme visual smoke', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/')
    await page.waitForSelector('[data-testid="login-wrapper"]')
  })

  test('default dark theme has correct backgrounds and no white viewport edges', async ({ page }) => {
    await assertDarkBackgrounds(page)
    await assertViewportEdgesNotWhite(page)
  })

  test('light theme after localStorage toggle has correct backgrounds', async ({ page }) => {
    await setThemeAndReload(page, 'light')
    await page.waitForSelector('[data-testid="login-wrapper"]')
    await assertLightBackgrounds(page)
  })

  test('dark theme after second localStorage toggle has correct backgrounds and no white edges', async ({ page }) => {
    await setThemeAndReload(page, 'light')
    await page.waitForSelector('[data-testid="login-wrapper"]')
    await setThemeAndReload(page, 'dark')
    await page.waitForSelector('[data-testid="login-wrapper"]')
    await assertDarkBackgrounds(page)
    await assertViewportEdgesNotWhite(page)
  })
})
