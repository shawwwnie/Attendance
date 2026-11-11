export const won = (value: number) => `${new Intl.NumberFormat('ko-KR').format(value)}원`
export const rate = (basisPoints: number) => `${basisPoints / 100}%`
export const today = () =>
  new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Seoul',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date())
export const thisMonth = () => today().slice(0, 7)
export const dateTime = (value: string) =>
  new Intl.DateTimeFormat('ko-KR', {
    timeZone: 'Asia/Seoul',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(new Date(value))
export const actionLabel: Record<string, string> = {
  RECIPIENT_CREATED: '수급자 등록',
  VISIT_CREATED: '일정 등록',
  VISIT_COMPLETED: '이용 완료',
  VISIT_CANCELLED: '일정 취소',
  INVOICE_ISSUED: '청구서 발행',
  PAYMENT_RECEIVED: '수납 기록',
  DEMO_INITIALIZED: '데모 준비',
}
