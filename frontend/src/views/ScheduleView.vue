<script setup lang="ts">
import { ref, watch } from 'vue'
import { Plus, CalendarDays } from 'lucide-vue-next'
import { api, query } from '../lib/api'
import { thisMonth, today } from '../lib/format'
import { useApp } from '../stores/app'
import { useResource } from '../composables/useResource'
import ResourceState from '../components/ResourceState.vue'
import Pagination from '../components/Pagination.vue'
import Modal from '../components/Modal.vue'
import type { PageResult, Recipient, Visit, VisitStatus } from '../types'
const app = useApp()
const month = ref(thisMonth()),
  status = ref(''),
  name = ref(''),
  page = ref(0)
const showForm = ref(false),
  busy = ref(false),
  actionId = ref<number | null>(null)
const people = ref<Recipient[]>([]),
  peopleSearch = ref('')
const input = ref({ recipientId: '', serviceDate: today(), startTime: '09:00', endTime: '17:00' })
const { data, loading, error, refresh } = useResource(() =>
  api<PageResult<Visit>>(
    `/visits?${query({ month: month.value, name: name.value, page: page.value, size: 10, ...(status.value ? { status: status.value } : {}) })}`,
  ),
)
watch([month, status], () => {
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
async function open() {
  await findPeople()
  showForm.value = true
}
async function save() {
  busy.value = true
  try {
    await api('/visits', {
      method: 'POST',
      body: JSON.stringify({ ...input.value, recipientId: Number(input.value.recipientId) }),
    })
    showForm.value = false
    app.notify('이용 일정을 등록했습니다.')
    await refresh()
  } catch (e) {
    app.fail(e)
  } finally {
    busy.value = false
  }
}
async function transition(visit: Visit, next: VisitStatus) {
  actionId.value = visit.id
  try {
    await api(`/visits/${visit.id}`, { method: 'PATCH', body: JSON.stringify({ status: next }) })
    app.notify(next === 'COMPLETED' ? '이용 완료를 기록했습니다.' : '일정을 취소했습니다.')
    await refresh()
  } catch (e) {
    app.fail(e)
  } finally {
    actionId.value = null
  }
}
function searchVisits() {
  if (page.value !== 0) page.value = 0
  else void refresh()
}
const labels: Record<VisitStatus, string> = {
  PLANNED: '이용 예정',
  COMPLETED: '이용 완료',
  CANCELLED: '취소',
}
</script>
<template>
  <div class="page-heading">
    <div>
      <span class="eyebrow">DAILY CARE SCHEDULE</span>
      <h1>이용 일정</h1>
      <p>센터의 이용 일정을 확인하고, 완료된 이용 내역을 청구로 연결합니다.</p>
    </div>
    <button v-if="app.session?.canWrite" class="button primary" @click="open">
      <Plus :size="18" />일정 등록
    </button>
  </div>
  <div class="filter-bar">
    <label class="inline-label"
      >기준 월<input
        v-model="month"
        type="month"
        min="2000-01"
        max="2100-12"
        aria-label="일정 기준 월"
        required /></label
    ><label class="inline-label"
      >상태<select v-model="status" aria-label="일정 상태">
        <option value="">전체 상태</option>
        <option value="PLANNED">이용 예정</option>
        <option value="COMPLETED">이용 완료</option>
        <option value="CANCELLED">취소</option>
      </select></label
    >
    <form class="inline-search" @submit.prevent="searchVisits">
      <input
        v-model="name"
        placeholder="수급자 이름 검색"
        aria-label="일정 수급자 검색"
        maxlength="40"
      /><button class="button secondary">검색</button>
    </form>
  </div>
  <section class="panel table-panel">
    <div class="table-toolbar">
      <div class="section-label">
        <CalendarDays :size="19" />
        <h2>월 이용 일정</h2>
        <span class="count-badge">{{ data?.total ?? 0 }}</span>
      </div>
      <span class="muted small-text">완료·취소한 일정은 되돌릴 수 없습니다.</span>
    </div>
    <ResourceState
      :loading="loading"
      :error="error"
      :empty="!data?.items.length"
      message="해당 조건의 이용 일정이 없습니다."
      @retry="refresh"
    />
    <div v-if="data?.items.length && !loading" class="table-scroll">
      <table>
        <thead>
          <tr>
            <th>이용일</th>
            <th>수급자</th>
            <th>이용 시간</th>
            <th>상태</th>
            <th v-if="app.session?.canWrite" class="numeric">업무 처리</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="visit in data.items" :key="visit.id">
            <td class="mono">{{ visit.serviceDate }}</td>
            <td>
              <strong>{{ visit.recipientName }}</strong>
            </td>
            <td class="muted">
              {{ visit.startTime.slice(0, 5) }} – {{ visit.endTime.slice(0, 5) }}
            </td>
            <td>
              <span :class="['badge', visit.status.toLowerCase()]">{{ labels[visit.status] }}</span>
            </td>
            <td v-if="app.session?.canWrite" class="numeric">
              <div v-if="visit.status === 'PLANNED'" class="row-actions">
                <button
                  class="button small secondary"
                  :disabled="actionId !== null || visit.serviceDate > today()"
                  @click="transition(visit, 'COMPLETED')"
                >
                  이용 완료</button
                ><button
                  class="button small quiet"
                  :disabled="actionId !== null"
                  @click="transition(visit, 'CANCELLED')"
                >
                  취소
                </button>
              </div>
              <span v-else class="muted">처리 완료</span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <Pagination v-if="data" :page="page" :size="10" :total="data.total" @change="page = $event" />
  </section>
  <Modal v-if="showForm" title="이용 일정 등록" @close="!busy && (showForm = false)"
    ><form @submit.prevent="save">
      <p class="modal-description">수급자별 하루 한 건의 이용 일정을 등록할 수 있습니다.</p>
      <div class="lookup-search">
        <input
          v-model="peopleSearch"
          placeholder="수급자 이름으로 찾기"
          aria-label="등록할 수급자 검색"
          maxlength="40"
        /><button type="button" class="button secondary" @click="findPeople">찾기</button>
      </div>
      <label
        >수급자<select v-model="input.recipientId" required>
          <option value="" disabled>수급자 선택 (검색 결과 최대 100명)</option>
          <option v-for="person in people" :key="person.id" :value="person.id">
            {{ person.name }} · {{ person.careGrade }}등급
          </option>
        </select></label
      ><label
        >이용일<input
          v-model="input.serviceDate"
          type="date"
          required
          min="2000-01-01"
          max="2100-12-31"
      /></label>
      <div class="form-grid">
        <label>시작 시간<input v-model="input.startTime" type="time" required /></label
        ><label>종료 시간<input v-model="input.endTime" type="time" required /></label>
      </div>
      <div class="modal-actions">
        <button type="button" class="button secondary" :disabled="busy" @click="showForm = false">
          취소</button
        ><button class="button primary" :disabled="busy">
          {{ busy ? '등록 중…' : '일정 등록' }}
        </button>
      </div>
    </form></Modal
  >
</template>
