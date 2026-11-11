let csrf: { token: string; headerName: string } | undefined

export class ApiError extends Error {
  constructor(
    public status: number,
    public code: string,
    message: string,
    public requestId = '',
    public fieldErrors: Record<string, string> = {},
  ) {
    super(message)
  }
}

async function token() {
  if (!csrf) {
    const response = await fetch('/api/csrf', { credentials: 'same-origin' })
    if (!response.ok) throw new Error('보안 토큰을 가져오지 못했습니다. 잠시 후 다시 시도해주세요.')
    csrf = await response.json()
  }
  return csrf!
}

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  const method = init.method ?? 'GET'
  if (init.body && !(init.body instanceof URLSearchParams))
    headers.set('Content-Type', 'application/json')
  if (!['GET', 'HEAD'].includes(method)) {
    const security = await token()
    headers.set(security.headerName, security.token)
  }
  const response = await fetch(`/api${path}`, { ...init, headers, credentials: 'same-origin' })
  if (!response.ok) {
    const body = await response.json().catch(() => ({}))
    if (response.status === 401) {
      csrf = undefined
      window.dispatchEvent(new Event('careflow:unauthorized'))
    }
    throw new ApiError(
      response.status,
      body.code ?? 'REQUEST_FAILED',
      body.message ?? '요청을 처리하지 못했습니다.',
      body.requestId ?? response.headers.get('X-Request-ID') ?? '',
      body.fieldErrors ?? {},
    )
  }
  return response.status === 204 ? (undefined as T) : response.json()
}

export async function signIn(username: string, password: string) {
  csrf = undefined
  await api('/login', { method: 'POST', body: new URLSearchParams({ username, password }) })
  csrf = undefined // Spring rotates the CSRF token after authentication.
}

export async function signOut() {
  await api('/logout', { method: 'POST' })
  csrf = undefined
}

export const query = (values: Record<string, string | number | boolean>) =>
  new URLSearchParams(
    Object.fromEntries(Object.entries(values).map(([key, value]) => [key, String(value)])),
  ).toString()

export function errorMessage(error: unknown) {
  if (error instanceof ApiError) {
    const fields = Object.values(error.fieldErrors)
    return `${error.message}${fields.length ? ` ${fields.join(' / ')}` : ''}${error.requestId ? ` (요청 ID: ${error.requestId})` : ''}`
  }
  return error instanceof Error ? error.message : '처리 중 오류가 발생했습니다.'
}
