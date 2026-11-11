export interface PageResult<T> {
  items: T[]
  total: number
  page: number
  size: number
}
export interface Session {
  username: string
  centerId: number
  centerName: string
  canWrite: boolean
}
export interface Recipient {
  id: number
  name: string
  careGrade: number
  dailyFee: number
  copayBasisPoints: number
}
export type VisitStatus = 'PLANNED' | 'COMPLETED' | 'CANCELLED'
export interface Visit {
  id: number
  recipientId: number
  recipientName: string
  serviceDate: string
  startTime: string
  endTime: string
  status: VisitStatus
}
export interface Invoice {
  id: number
  recipientId: number
  recipientName: string
  billingMonth: string
  totalAmount: number
  copayAmount: number
  paidAmount: number
  outstandingAmount: number
}
export interface InvoiceLine {
  visitId: number
  serviceDate: string
  dailyFee: number
  copayBasisPoints: number
  copayAmount: number
}
export interface Payment {
  id: number
  amount: number
  memo: string
  createdAt: string
}
export interface InvoiceDetail {
  invoice: Invoice
  lines: InvoiceLine[]
  payments: Payment[]
}
export interface MutationResult<T> {
  data: T
  replayed: boolean
}
export interface AuditEvent {
  id: number
  actor: string
  action: string
  entityType: string
  entityId: number
  detail: string
  createdAt: string
}
export interface BillingSummary {
  invoiceCount: number
  copayAmount: number
  paidAmount: number
  outstandingAmount: number
}
export interface Dashboard {
  today: string
  month: string
  recipients: number
  plannedToday: number
  completedToday: number
  billing: BillingSummary
  recentActivity: AuditEvent[]
}
