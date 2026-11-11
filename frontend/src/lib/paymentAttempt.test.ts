import { describe, it, expect } from 'vitest'
import { paymentAttempt, clearPaymentAttempt } from './paymentAttempt'

function storage() {
  const state = new Map<string, string>()
  return {
    getItem: (key: string) => state.get(key) ?? null,
    setItem: (key: string, value: string) => {
      state.set(key, value)
    },
    removeItem: (key: string) => {
      state.delete(key)
    },
  }
}
describe('payment retry identity', () => {
  it('reuses a key after a failed request or page reload', () => {
    const store = storage(),
      draft = { invoiceId: 1, amount: 3000, memo: '입금' }
    expect(paymentAttempt(store, '1:manager', draft, () => 'first-key')).toBe('first-key')
    expect(paymentAttempt(store, '1:manager', { ...draft }, () => 'second-key')).toBe('first-key')
  })
  it('changes the key when the request payload or center identity changes', () => {
    const store = storage(),
      draft = { invoiceId: 1, amount: 3000, memo: '입금' }
    paymentAttempt(store, '1:manager', draft, () => 'first-key')
    expect(
      paymentAttempt(store, '1:manager', { ...draft, amount: 4000 }, () => 'changed-key'),
    ).toBe('changed-key')
    expect(paymentAttempt(store, '2:manager', draft, () => 'other-center')).toBe('other-center')
  })
  it('creates a new attempt only after an acknowledged response is cleared', () => {
    const store = storage(),
      draft = { invoiceId: 1, amount: 3000, memo: '' }
    paymentAttempt(store, '1:manager', draft, () => 'old-key')
    clearPaymentAttempt(store, '1:manager', 1)
    expect(paymentAttempt(store, '1:manager', draft, () => 'new-key')).toBe('new-key')
  })
  it('replaces corrupt stored drafts without treating them as successful payments', () => {
    const store = storage()
    store.setItem('careflow:payment:1:manager:1', 'broken-json')
    expect(
      paymentAttempt(store, '1:manager', { invoiceId: 1, amount: 1, memo: '' }, () => 'safe-key'),
    ).toBe('safe-key')
  })
})
