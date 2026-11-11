<script setup lang="ts">
import { ref, watch } from 'vue'
import { ClipboardList } from 'lucide-vue-next'
import { api, query } from '../lib/api'
import { dateTime, actionLabel } from '../lib/format'
import { useResource } from '../composables/useResource'
import ResourceState from '../components/ResourceState.vue'
import Pagination from '../components/Pagination.vue'
import type { PageResult, AuditEvent } from '../types'
const page = ref(0)
const { data, loading, error, refresh } = useResource(() =>
  api<PageResult<AuditEvent>>(`/audit-events?${query({ page: page.value, size: 15 })}`),
)
watch(page, refresh)
</script>
<template>
  <div class="page-heading">
    <div>
      <span class="eyebrow">OPERATIONAL AUDIT TRAIL</span>
      <h1>변경 이력</h1>
      <p>누가, 언제, 어떤 업무를 처리했는지 확인하세요.</p>
    </div>
    <span class="subtle-pill"><ClipboardList :size="16" />주요 변경 자동 기록</span>
  </div>
  <section class="panel table-panel">
    <div class="table-toolbar">
      <div class="section-label">
        <ClipboardList :size="19" />
        <h2>업무 처리 기록</h2>
        <span class="count-badge">{{ data?.total ?? 0 }}</span>
      </div>
      <button class="button small secondary" @click="refresh">새로고침</button>
    </div>
    <ResourceState
      :loading="loading"
      :error="error"
      :empty="!data?.items.length"
      message="아직 변경 이력이 없습니다."
      @retry="refresh"
    />
    <div v-if="data?.items.length && !loading" class="table-scroll">
      <table>
        <thead>
          <tr>
            <th>처리 시각 (한국)</th>
            <th>업무 구분</th>
            <th>처리 내용</th>
            <th>담당자</th>
            <th>대상</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="event in data.items" :key="event.id">
            <td class="nowrap">{{ dateTime(event.createdAt) }}</td>
            <td>
              <span class="badge neutral">{{ actionLabel[event.action] ?? event.action }}</span>
            </td>
            <td>{{ event.detail }}</td>
            <td class="mono">{{ event.actor }}</td>
            <td class="muted mono">{{ event.entityType }} #{{ event.entityId }}</td>
          </tr>
        </tbody>
      </table>
    </div>
    <Pagination v-if="data" :page="page" :size="15" :total="data.total" @change="page = $event" />
  </section>
  <p class="footnote">
    업무 변경과 이력 저장은 같은 트랜잭션에서 처리됩니다. 실패한 요청은 성공 이력으로 남지 않습니다.
  </p>
</template>
