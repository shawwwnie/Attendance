/** Retain an unconfirmed request key across network failures and page reloads. */
export interface PaymentDraft {
  invoiceId: number
  amount: number
  memo: string
}
export function paymentAttempt(
  storage: Pick<Storage, 'getItem' | 'setItem'>,
  owner: string,
  draft: PaymentDraft,
  newKey: () => string = () => crypto.randomUUID(),
): string {
  const storageKey = `careflow:payment:${owner}:${draft.invoiceId}`
  const fingerprint = JSON.stringify(draft)
  try {
    const prior = JSON.parse(storage.getItem(storageKey) ?? 'null')
    if (prior?.fingerprint === fingerprint && typeof prior.key === 'string') return prior.key
  } catch {
    /* A damaged draft is replaced; no payment is inferred from local state. */
  }
  const key = newKey()
  storage.setItem(storageKey, JSON.stringify({ fingerprint, key }))
  return key
}
export function clearPaymentAttempt(
  storage: Pick<Storage, 'removeItem'>,
  owner: string,
  invoiceId: number,
) {
  storage.removeItem(`careflow:payment:${owner}:${invoiceId}`)
}
