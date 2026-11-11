<script setup lang="ts">
import { ref, watch } from 'vue'
import {
  Users,
  CalendarDays,
  Wallet,
  ArrowUpRight,
  ArrowRight,
  CheckCircle2,
  Clock3,
  ReceiptText,
  Activity,
  ShieldCheck as ShieldCheckIcon,
} from 'lucide-vue-next'
import { api } from '../lib/api'
import { won, thisMonth, dateTime, actionLabel } from '../lib/format'
import { useResource } from '../composables/useResource'
import ResourceState from '../components/ResourceState.vue'
import type { Dashboard } from '../types'
const month = ref(thisMonth())
const { data, loading, error, refresh } = useResource(() =>
  api<Dashboard>(`/dashboard?month=${month.value}`),
)
watch(month, refresh)
</script>
<template>
  <div class="page-heading">
    <div>
      <span class="eyebrow">CENTER OVERVIEW</span>
      <h1>운영 대시보드</h1>
      <p>오늘의 돌봄과 이번 달의 운영 현황을 한눈에 확인하세요.</p>
    </div>
    <label class="inline-label"
      >기준 월<input
        v-model="month"
        type="month"
        min="2000-01"
        max="2100-12"
        aria-label="대시보드 기준 월"
        required
    /></label>
  </div>
  <ResourceState :loading="loading" :error="error" @retry="refresh" />
  <template v-if="data && !loading"
    ><div class="welcome-banner">
      <div>
        <span class="banner-kicker">{{ data.today }} · 오늘의 운영</span>
        <h2>
          한 번의 확인이,<br class="mobile-only" />
          더 여유로운 하루를 만듭니다.
        </h2>
        <p>
          오늘 남은 이용 일정은 <strong>{{ data.plannedToday }}건</strong>입니다.
        </p>
      </div>
      <RouterLink to="/schedule" class="button banner-button"
        >일정 확인하기<ArrowRight :size="17"
      /></RouterLink>
      <div class="banner-decoration" aria-hidden="true">
        <Activity :size="110" stroke-width="1" />
      </div>
    </div>
    <div class="stats-grid">
      <article class="stat-card">
        <div class="stat-top">
          <span>등록 수급자</span><span class="stat-icon mint"><Users :size="21" /></span>
        </div>
        <strong>{{ data.recipients }}<small>명</small></strong>
        <p>현재 센터에 등록된 수급자</p>
      </article>
      <article class="stat-card">
        <div class="stat-top">
          <span>오늘 이용 완료</span><span class="stat-icon blue"><CalendarDays :size="21" /></span>
        </div>
        <strong>{{ data.completedToday }}<small>건</small></strong>
        <p>예정 {{ data.plannedToday }}건 · 완료 {{ data.completedToday }}건</p>
      </article>
      <article class="stat-card">
        <div class="stat-top">
          <span>월 본인부담금</span><span class="stat-icon mint"><ReceiptText :size="21" /></span>
        </div>
        <strong class="money-stat">{{ won(data.billing.copayAmount) }}</strong>
        <p>발행된 청구서 {{ data.billing.invoiceCount }}건 기준</p>
      </article>
      <article class="stat-card">
        <div class="stat-top">
          <span>확인할 미수금</span><span class="stat-icon amber"><Wallet :size="21" /></span>
        </div>
        <strong class="money-stat">{{ won(data.billing.outstandingAmount) }}</strong
        ><RouterLink to="/billing" class="stat-link"
          >수납 현황 확인<ArrowUpRight :size="14"
        /></RouterLink>
      </article>
    </div>
    <div class="dashboard-columns">
      <section class="panel">
        <div class="panel-title">
          <div>
            <h2>이번 달 청구 · 수납</h2>
            <p>{{ month }} 발행 청구서의 본인부담금 기준</p>
          </div>
          <ReceiptText :size="21" class="muted" />
        </div>
        <div class="collection-total">
          <span>수납 완료</span><strong>{{ won(data.billing.paidAmount) }}</strong>
        </div>
        <div class="progress-track">
          <span
            :style="{
              width: `${data.billing.copayAmount ? (data.billing.paidAmount / data.billing.copayAmount) * 100 : 0}%`,
            }"
          ></span>
        </div>
        <div class="progress-labels">
          <span
            >수납률
            {{
              data.billing.copayAmount
                ? Math.round((data.billing.paidAmount / data.billing.copayAmount) * 100)
                : 0
            }}%</span
          ><span>청구 {{ won(data.billing.copayAmount) }}</span>
        </div>
        <div class="daily-summary">
          <div>
            <CheckCircle2 :size="18" /><span>이용 완료</span
            ><strong>{{ data.completedToday }}건</strong>
          </div>
          <div>
            <Clock3 :size="18" /><span>오늘 남은 일정</span
            ><strong>{{ data.plannedToday }}건</strong>
          </div>
        </div>
        <RouterLink to="/billing" class="text-link"
          >청구 · 수납 관리로 이동<ArrowRight :size="16"
        /></RouterLink>
      </section>
      <section class="panel">
        <div class="panel-title">
          <div>
            <h2>최근 변경 이력</h2>
            <p>센터의 주요 업무 처리 기록</p>
          </div>
          <RouterLink to="/audit" class="text-link">전체 보기<ArrowRight :size="15" /></RouterLink>
        </div>
        <div v-if="!data.recentActivity.length" class="resource-state">
          아직 변경 이력이 없습니다.
        </div>
        <div v-for="event in data.recentActivity" :key="event.id" class="activity-row">
          <span class="activity-dot"></span>
          <div>
            <strong>{{ actionLabel[event.action] ?? event.action }}</strong>
            <p>{{ event.detail }}</p>
          </div>
          <small>{{ dateTime(event.createdAt) }}</small>
        </div>
      </section>
    </div>
    <div class="info-strip">
      <ShieldCheckIcon /><span
        >청구서 발행 시 해당 수급자의 월 일정이 마감되며, 수납 기록은 변경 이력에 남습니다.</span
      >
    </div>
  </template>
</template>
