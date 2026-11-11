<script setup lang="ts">
import { ref, watch } from 'vue'
import { Plus, ReceiptText, ArrowRight, CheckCircle2 } from 'lucide-vue-next'
import { api, query } from '../lib/api'
import { won, rate, thisMonth, dateTime } from '../lib/format'
import { paymentAttempt, clearPaymentAttempt } from '../lib/paymentAttempt'
import { useApp } from '../stores/app'
import { useResource } from '../composables/useResource'
import ResourceState from '../components/ResourceState.vue'
import Pagination from '../components/Pagination.vue'
import Modal from '../components/Modal.vue'
import type {
  PageResult,
  Recipient,
  Invoice,
  InvoiceDetail,
  Payment,
  MutationResult,
} from '../types'
const app = useApp()
const month = ref(thisMonth()),
  unpaidOnly = ref(false),
  page = ref(0)
const showForm = ref(false),
  busy = ref(false),
  people = ref<Recipient[]>([]),
  peopleSearch = ref('')
const recipientId = ref(''),
  detail = ref<InvoiceDetail | null>(null),
  detailBusy = ref(false)
const amount = ref<number | ''>(''),
  memo = ref('')
const { data, loading, error, refresh } = useResource(() =>
  api<PageResult<Invoice>>(
    `/invoices?${query({ month: month.value, unpaidOnly: unpaidOnly.value, page: page.value, size: 10 })}`,
  ),
)
watch([month, unpaidOnly], () => {
  page.value = 0
  void refresh()
})
watch(page, refresh)
async function findPeople() {
  try {
    people.value = (
      await api<PageResult<Recipient>>(
        `/recipients?${query({ name: peopleSearch.value, size: 100 })}`,
      )
    ).items
  } catch (e) {
    app.fail(e)
  }
}
async function openIssue() {
  await findPeople()
  showForm.value = true
}
async function issue() {
  busy.value = true
  try {
    const result = await api<MutationResult<Invoice>>('/invoices', {
      method: 'POST',
      body: JSON.stringify({ recipientId: Number(recipientId.value), month: month.value }),
    })
    showForm.value = false
    app.notify(
      result.replayed
        ? '이미 발행된 청구서를 불러왔습니다.'
        : '청구서를 발행했습니다. 해당 수급자의 월 일정이 마감되었습니다.',
    )
    await refresh()
    await openDetail(result.data.id)
  } catch (e) {
    app.fail(e)
  } finally {
    busy.value = false
  }
}
async function openDetail(id: number) {
  detailBusy.value = true
  try {
    detail.value = await api<InvoiceDetail>(`/invoices/${id}`)
    amount.value = ''
    memo.value = ''
  } catch (e) {
    detail.value = null
    app.fail(e)
  } finally {
    detailBusy.value = false
  }
}
async function pay() {
  if (!detail.value || amount.value === '') return
  busy.value = true
  const id = detail.value.invoice.id
  const owner = `${app.session?.centerId}:${app.session?.username}`
  try {
    const draft = { invoiceId: id, amount: Number(amount.value), memo: memo.value.trim() }
    const key = paymentAttempt(sessionStorage, owner, draft)
    const result = await api<MutationResult<Payment>>(`/invoices/${id}/payments`, {
      method: 'POST',
      headers: { 'Idempotency-Key': key },
      body: JSON.stringify({ amount: draft.amount, memo: draft.memo }),
    })
    clearPaymentAttempt(sessionStorage, owner, id)
    app.notify(
      result.replayed
        ? '이미 기록된 수납입니다. 중복 반영하지 않았습니다.'
        : '수납 내역을 기록했습니다.',
    )
    await openDetail(id)
    await refresh()
  } catch (e) {
    app.fail(e)
  } finally {
    busy.value = false
  }
}
</script>
<template>
  <div class="page-heading">
    <div>
      <span class="eyebrow">BILLING & COLLECTIONS</span>
      <h1>청구 · 수납</h1>
      <p>완료된 이용 내역으로 청구서를 발행하고 본인부담금 수납을 기록합니다.</p>
    </div>
    <button v-if="app.session?.canWrite" class="button primary" @click="openIssue">
      <Plus :size="18" />청구서 발행
    </button>
  </div>
  <div class="filter-bar">
    <label class="inline-label"
      >청구 월<input
        v-model="month"
        type="month"
        required
        min="2000-01"
        max="2100-12"
        aria-label="청구 기준 월" /></label
    ><label class="checkbox-label"
      ><input v-model="unpaidOnly" type="checkbox" />미수금 있는 청구서만</label
    ><span class="filter-note">발행 전에 해당 월의 예정 일정을 모두 처리해주세요.</span>
  </div>
  <section class="panel table-panel">
    <div class="table-toolbar">
      <div class="section-label">
        <ReceiptText :size="19" />
        <h2>월 청구서</h2>
        <span class="count-badge">{{ data?.total ?? 0 }}</span>
      </div>
    </div>
    <ResourceState
      :loading="loading"
      :error="error"
      :empty="!data?.items.length"
      message="해당 월에 발행된 청구서가 없습니다."
      @retry="refresh"
    />
    <div v-if="data?.items.length && !loading" class="table-scroll">
      <table>
        <thead>
          <tr>
            <th>청구 번호 · 수급자</th>
            <th class="numeric">총 이용료</th>
            <th class="numeric">본인부담금</th>
            <th class="numeric">수납액</th>
            <th class="numeric">미수금</th>
            <th>상태</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="invoice in data.items" :key="invoice.id">
            <td>
              <strong>{{ invoice.recipientName }}</strong
              ><small class="cell-subtitle mono"
                >INV-{{ String(invoice.id).padStart(4, '0') }}</small
              >
            </td>
            <td class="numeric muted">{{ won(invoice.totalAmount) }}</td>
            <td class="numeric">{{ won(invoice.copayAmount) }}</td>
            <td class="numeric">{{ won(invoice.paidAmount) }}</td>
            <td class="numeric" :class="{ 'amount-due': invoice.outstandingAmount > 0 }">
              {{ won(invoice.outstandingAmount) }}
            </td>
            <td>
              <span :class="['badge', invoice.outstandingAmount === 0 ? 'completed' : 'planned']">{{
                invoice.outstandingAmount === 0 ? '수납 완료' : '미수금'
              }}</span>
            </td>
            <td>
              <button
                class="button small quiet"
                :disabled="detailBusy"
                @click="openDetail(invoice.id)"
              >
                상세<ArrowRight :size="15" />
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <Pagination v-if="data" :page="page" :size="10" :total="data.total" @change="page = $event" />
  </section>
  <p class="footnote">
    학습용 요금으로 계산하며, 공단 청구·은행 이체·실제 결제 기능은 포함하지 않습니다.
  </p>
  <Modal v-if="showForm" title="월 청구서 발행" @close="!busy && (showForm = false)"
    ><form @submit.prevent="issue">
      <p class="modal-description">
        {{ month }}의 완료된 이용 일정을 모아 청구합니다. 발행 후 해당 수급자의 월 일정은
        마감됩니다.
      </p>
      <div class="lookup-search">
        <input
          v-model="peopleSearch"
          placeholder="수급자 이름으로 찾기"
          aria-label="청구할 수급자 검색"
          maxlength="40"
        /><button type="button" class="button secondary" @click="findPeople">찾기</button>
      </div>
      <label
        >수급자<select v-model="recipientId" required>
          <option value="" disabled>수급자 선택 (검색 결과 최대 100명)</option>
          <option v-for="person in people" :key="person.id" :value="person.id">
            {{ person.name }}
          </option>
        </select></label
      >
      <div class="notice">
        같은 수급자·같은 월의 청구서를 다시 요청하면 기존 청구서를 반환합니다.
      </div>
      <div class="modal-actions">
        <button type="button" class="button secondary" :disabled="busy" @click="showForm = false">
          취소</button
        ><button class="button primary" :disabled="busy">
          {{ busy ? '발행 중…' : '청구서 발행' }}
        </button>
      </div>
    </form></Modal
  >
  <Modal
    v-if="detail"
    :title="`${detail.invoice.recipientName} · ${detail.invoice.billingMonth} 청구서`"
    wide
    @close="!busy && (detail = null)"
    ><div class="invoice-totals">
      <div>
        <span>본인부담금</span><strong>{{ won(detail.invoice.copayAmount) }}</strong>
      </div>
      <div>
        <span>수납 완료</span><strong>{{ won(detail.invoice.paidAmount) }}</strong>
      </div>
      <div>
        <span>남은 미수금</span
        ><strong class="amount-due">{{ won(detail.invoice.outstandingAmount) }}</strong>
      </div>
    </div>
    <h3 class="detail-title">이용 내역 · 발행 당시 요금</h3>
    <div class="table-scroll">
      <table class="compact-table">
        <thead>
          <tr>
            <th>이용일</th>
            <th class="numeric">일 이용료</th>
            <th class="numeric">부담률</th>
            <th class="numeric">본인부담금</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="line in detail.lines" :key="line.visitId">
            <td>{{ line.serviceDate }}</td>
            <td class="numeric">{{ won(line.dailyFee) }}</td>
            <td class="numeric">{{ rate(line.copayBasisPoints) }}</td>
            <td class="numeric">{{ won(line.copayAmount) }}</td>
          </tr>
        </tbody>
      </table>
    </div>
    <h3 class="detail-title">수납 이력</h3>
    <p v-if="!detail.payments.length" class="muted">아직 수납 기록이 없습니다.</p>
    <div v-for="payment in detail.payments" :key="payment.id" class="payment-history">
      <CheckCircle2 :size="17" /><strong>{{ won(payment.amount) }}</strong
      ><span>{{ payment.memo || '메모 없음' }}</span
      ><small>{{ dateTime(payment.createdAt) }}</small>
    </div>
    <form
      v-if="app.session?.canWrite && detail.invoice.outstandingAmount > 0"
      class="payment-form"
      @submit.prevent="pay"
    >
      <h3>수납 기록 추가</h3>
      <div class="form-grid">
        <label
          >수납액 (원)<input
            v-model.number="amount"
            type="number"
            min="1"
            :max="detail.invoice.outstandingAmount"
            step="1"
            required
            placeholder="수납한 금액" /></label
        ><label
          >수납 메모<input v-model="memo" maxlength="100" placeholder="예: 계좌 입금 확인"
        /></label>
      </div>
      <p class="small-text muted">
        네트워크 오류로 다시 요청해도 같은 내용의 수납은 중복 기록하지 않습니다.
      </p>
      <div class="modal-actions">
        <button class="button primary" :disabled="busy">
          {{ busy ? '기록 중…' : '수납 기록' }}
        </button>
      </div>
    </form></Modal
  >
</template>
