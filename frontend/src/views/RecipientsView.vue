<script setup lang="ts">
import { ref, watch } from 'vue'
import { Plus, Search, Users } from 'lucide-vue-next'
import { api, query } from '../lib/api'
import { won, rate } from '../lib/format'
import { useApp } from '../stores/app'
import { useResource } from '../composables/useResource'
import ResourceState from '../components/ResourceState.vue'
import Pagination from '../components/Pagination.vue'
import Modal from '../components/Modal.vue'
import type { PageResult, Recipient } from '../types'
const app = useApp()
const search = ref(''),
  name = ref(''),
  page = ref(0),
  showForm = ref(false),
  busy = ref(false)
const input = ref({ name: '', careGrade: 3, dailyFee: 60000, copayBasisPoints: 1500 })
const { data, loading, error, refresh } = useResource(() =>
  api<PageResult<Recipient>>(
    `/recipients?${query({ name: name.value, page: page.value, size: 10 })}`,
  ),
)
watch([name, page], refresh)
function find() {
  page.value = 0
  name.value = search.value
  void refresh()
}
async function save() {
  busy.value = true
  try {
    await api('/recipients', { method: 'POST', body: JSON.stringify(input.value) })
    showForm.value = false
    input.value = { name: '', careGrade: 3, dailyFee: 60000, copayBasisPoints: 1500 }
    app.notify('수급자를 등록했습니다.')
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
      <span class="eyebrow">RECIPIENT DIRECTORY</span>
      <h1>수급자 관리</h1>
      <p>수급자의 이용 정보와 학습용 요금 정책을 관리합니다.</p>
    </div>
    <button v-if="app.session?.canWrite" class="button primary" @click="showForm = true">
      <Plus :size="18" />수급자 등록
    </button>
  </div>
  <section class="panel table-panel">
    <div class="table-toolbar">
      <div class="section-label">
        <Users :size="19" />
        <h2>수급자 목록</h2>
        <span class="count-badge">{{ data?.total ?? 0 }}</span>
      </div>
      <form class="search-form" @submit.prevent="find">
        <Search :size="17" /><input
          v-model="search"
          placeholder="수급자 이름 검색"
          aria-label="수급자 이름 검색"
          maxlength="40"
        /><button class="button small secondary">검색</button>
      </form>
    </div>
    <ResourceState
      :loading="loading"
      :error="error"
      :empty="!data?.items.length"
      message="등록된 수급자가 없습니다. 수급자를 먼저 등록해주세요."
      @retry="refresh"
    />
    <div v-if="data?.items.length && !loading" class="table-scroll">
      <table>
        <thead>
          <tr>
            <th>수급자</th>
            <th>장기요양 등급</th>
            <th class="numeric">일 이용료</th>
            <th class="numeric">본인부담률</th>
            <th>관리 번호</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="person in data.items" :key="person.id">
            <td>
              <div class="person-cell">
                <span class="person-avatar">{{ person.name.slice(0, 1) }}</span
                ><strong>{{ person.name }}</strong>
              </div>
            </td>
            <td>
              <span class="badge neutral">{{ person.careGrade }}등급</span>
            </td>
            <td class="numeric">{{ won(person.dailyFee) }}</td>
            <td class="numeric">{{ rate(person.copayBasisPoints) }}</td>
            <td class="muted mono">CF-{{ String(person.id).padStart(4, '0') }}</td>
          </tr>
        </tbody>
      </table>
    </div>
    <Pagination v-if="data" :page="page" :size="10" :total="data.total" @change="page = $event" />
  </section>
  <p class="footnote">
    학습용 일 이용료와 본인부담률입니다. 실제 장기요양 수가·감경 규칙을 적용하지 않습니다.
  </p>
  <Modal v-if="showForm" title="수급자 등록" @close="!busy && (showForm = false)"
    ><form @submit.prevent="save">
      <p class="modal-description">시연에는 가상 이름만 사용해주세요.</p>
      <label
        >수급자 이름<input v-model="input.name" required maxlength="40" placeholder="예: 김하늘"
      /></label>
      <div class="form-grid">
        <label
          >장기요양 등급<select v-model.number="input.careGrade">
            <option v-for="n in 5" :key="n" :value="n">{{ n }}등급</option>
          </select></label
        ><label
          >일 이용료 (원)<input
            v-model.number="input.dailyFee"
            type="number"
            min="1000"
            max="1000000"
            step="1"
            required
        /></label>
      </div>
      <label
        >본인부담률<select v-model.number="input.copayBasisPoints">
          <option :value="1500">15%</option>
          <option :value="900">9%</option>
          <option :value="600">6%</option>
          <option :value="0">0%</option>
        </select></label
      >
      <div class="modal-actions">
        <button type="button" class="button secondary" :disabled="busy" @click="showForm = false">
          취소</button
        ><button class="button primary" :disabled="busy">
          {{ busy ? '등록 중…' : '수급자 등록' }}
        </button>
      </div>
    </form></Modal
  >
</template>
