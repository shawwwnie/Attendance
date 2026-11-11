import { test, expect, type Page } from '@playwright/test'

async function login(page: Page, username = 'manager') {
  await page.goto('/login')
  await page.getByLabel('아이디', { exact: true }).fill(username)
  await page.getByLabel('비밀번호', { exact: true }).fill('careflow-demo')
  await page.getByRole('button', { name: '워크스페이스 입장' }).click()
  await expect(page.getByRole('heading', { name: '운영 대시보드', exact: true })).toBeVisible()
  await expect(page.getByText('등록 수급자', { exact: true })).toBeVisible()
}

test('real workflow: create recipient, complete visit, issue invoice and record payment', async ({
  page,
}) => {
  const name = `시연${Date.now()}`
  await login(page)
  await page.getByRole('link', { name: '수급자 관리', exact: true }).click()
  await page.getByRole('button', { name: '수급자 등록', exact: true }).click()
  let dialog = page.getByRole('dialog')
  await dialog.getByLabel('수급자 이름', { exact: true }).fill(name)
  await dialog.getByRole('button', { name: '수급자 등록', exact: true }).click()
  await expect(dialog).not.toBeVisible()
  await expect(page.getByText(name, { exact: true })).toBeVisible()

  await page.getByRole('link', { name: '이용 일정', exact: true }).click()
  await page.getByRole('button', { name: '일정 등록', exact: true }).click()
  dialog = page.getByRole('dialog')
  await dialog.getByRole('combobox', { name: '수급자', exact: true }).selectOption({ label: `${name} · 3등급` })
  await dialog.getByRole('button', { name: '일정 등록', exact: true }).click()
  await expect(dialog).not.toBeVisible()
  await page.getByLabel('일정 수급자 검색').fill(name)
  await page.getByRole('button', { name: '검색', exact: true }).click()
  await page.getByRole('button', { name: '이용 완료', exact: true }).click()
  await expect(page.getByRole('cell', { name: '이용 완료', exact: true })).toBeVisible()

  await page.getByRole('link', { name: '청구 · 수납', exact: true }).click()
  await page.getByRole('button', { name: '청구서 발행', exact: true }).click()
  dialog = page.getByRole('dialog')
  await dialog.getByRole('combobox', { name: '수급자', exact: true }).selectOption({ label: name })
  await dialog.getByRole('button', { name: '청구서 발행', exact: true }).click()
  await expect(page.getByRole('heading', { name: new RegExp(`${name}.*청구서`) })).toBeVisible()
  dialog = page.getByRole('dialog')
  await expect(dialog.getByText('9,000원', { exact: true }).first()).toBeVisible()
  await dialog.getByLabel('수납액 (원)', { exact: true }).fill('1000')
  await dialog.getByLabel('수납 메모', { exact: true }).fill('브라우저 시연 입금 확인')
  await dialog.getByRole('button', { name: '수납 기록', exact: true }).click()
  await expect(dialog.getByText('8,000원', { exact: true })).toBeVisible()
  await expect(dialog.getByText('브라우저 시연 입금 확인', { exact: true })).toBeVisible()
  await dialog.getByRole('button', { name: '닫기', exact: true }).click()
  await page.getByRole('link', { name: '변경 이력', exact: true }).click()
  await expect(page.getByRole('cell', { name: '수납 기록', exact: true }).first()).toBeVisible()
})

test('viewer can read but sees no mutation controls', async ({ page }) => {
  await login(page, 'viewer')
  await page.getByRole('link', { name: '수급자 관리', exact: true }).click()
  await expect(page.getByRole('heading', { name: '수급자 관리', exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '수급자 등록', exact: true })).toHaveCount(0)
  await page.getByRole('link', { name: '청구 · 수납', exact: true }).click()
  await expect(page.getByRole('button', { name: '청구서 발행', exact: true })).toHaveCount(0)
})

test('another center sees only its own recipient directory', async ({ page }) => {
  await login(page, 'other-manager')
  await expect(page.getByText('다온 주야간보호센터', { exact: true })).toBeVisible()
  await page.getByRole('link', { name: '수급자 관리', exact: true }).click()
  await expect(page.getByRole('cell', { name: '타센터 샘플' })).toBeVisible()
  await expect(page.getByRole('cell', { name: '김하늘' })).toHaveCount(0)
})

test('failed list request provides a working retry action', async ({ page }) => {
  await login(page)
  await page.route('**/api/visits?**', (route) => route.abort('failed'))
  await page.getByRole('link', { name: '이용 일정', exact: true }).click()
  await expect(page.getByRole('button', { name: '다시 불러오기' })).toBeVisible()
  await page.unroute('**/api/visits?**')
  await page.getByRole('button', { name: '다시 불러오기' }).click()
  await expect(page.getByRole('table')).toBeVisible()
})

test('invalid login displays an actionable message', async ({ page }) => {
  await page.goto('/login')
  await page.getByLabel('비밀번호', { exact: true }).fill('incorrect-password')
  await page.getByRole('button', { name: '워크스페이스 입장' }).click()
  await expect(page.getByRole('alert')).toContainText('아이디 또는 비밀번호')
})

test('billing business errors remain visible inside the active modal', async ({ page }) => {
  await login(page)
  await page.getByRole('link', { name: '청구 · 수납', exact: true }).click()
  await page.getByRole('button', { name: '청구서 발행', exact: true }).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByRole('combobox', { name: '수급자', exact: true }).selectOption({ label: '김하늘' })
  await dialog.getByRole('button', { name: '청구서 발행', exact: true }).click()
  await expect(dialog.getByRole('alert')).toContainText('예정 일정을 완료 또는 취소')
})

test('mobile dashboard fits the viewport and navigation works', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await login(page)
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390)
  await page.getByRole('link', { name: '청구 · 수납', exact: true }).click()
  await expect(page.getByRole('heading', { name: '청구 · 수납', exact: true })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390)
})
