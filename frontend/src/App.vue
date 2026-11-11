<script setup lang="ts">
import {
  HeartPulse,
  LayoutDashboard,
  Users,
  CalendarDays,
  ReceiptText,
  ClipboardList,
  LogOut,
  ShieldCheck,
  Sprout,
} from 'lucide-vue-next'
import { useApp } from './stores/app'
import { useRouter } from 'vue-router'
import { signOut } from './lib/api'
const app = useApp()
const router = useRouter()
const nav = [
  { to: '/', title: '운영 대시보드', icon: LayoutDashboard },
  { to: '/recipients', title: '수급자 관리', icon: Users },
  { to: '/schedule', title: '이용 일정', icon: CalendarDays },
  { to: '/billing', title: '청구 · 수납', icon: ReceiptText },
  { to: '/audit', title: '변경 이력', icon: ClipboardList },
]
async function logout() {
  try {
    await signOut()
    app.session = null
    await router.push('/login')
  } catch (e) {
    app.fail(e)
  }
}
</script>
<template>
  <div v-if="app.session" class="app-shell">
    <aside class="sidebar">
      <RouterLink to="/" class="brand"
        ><span class="brand-mark"><HeartPulse :size="25" /></span
        ><span>CareFlow<small>센터 운영의 흐름</small></span></RouterLink
      >
      <div class="center-box">
        <span class="center-icon"><Sprout :size="21" /></span>
        <div>
          <small>MY CENTER</small><strong>{{ app.session.centerName }}</strong>
        </div>
      </div>
      <p class="nav-caption">WORKSPACE</p>
      <nav aria-label="주 메뉴">
        <RouterLink v-for="item in nav" :key="item.to" :to="item.to" :exact-active-class="'active'"
          ><component :is="item.icon" :size="20" /><span>{{ item.title }}</span></RouterLink
        >
      </nav>
      <div class="sidebar-note">
        <ShieldCheck :size="21" /><strong>기록은 정확하게,<br />돌봄은 여유롭게.</strong>
        <p>일정에서 수납까지<br />하나로 이어지는 센터 업무</p>
      </div>
      <div class="sidebar-bottom">
        <span class="online-dot"></span> CareFlow ERP <span>v1.0</span>
      </div>
    </aside>
    <div class="workspace">
      <header class="topbar">
        <div class="breadcrumb">
          WORKSPACE <span>/</span> {{ nav.find((n) => n.to === $route.path)?.title }}
        </div>
        <div class="account">
          <span class="demo-label">DEMO</span
          ><span class="avatar">{{ app.session.canWrite ? '운' : '조' }}</span>
          <div>
            <strong>{{ app.session.username }}</strong
            ><small>{{ app.session.canWrite ? '운영 매니저' : '조회 전용' }}</small>
          </div>
          <button class="icon-button" aria-label="로그아웃" @click="logout">
            <LogOut :size="19" />
          </button>
        </div>
      </header>
      <main id="main-content"><RouterView /></main>
      <footer class="app-footer">
        <span>CareFlow · 주야간보호센터 운영 워크스페이스</span
        ><span>가상 데이터 · 학습용 요금 정책</span>
      </footer>
    </div>
  </div>
  <RouterView v-else />
  <div
    v-if="app.toast"
    :class="['toast', { 'toast-error': app.toast.error }]"
    :role="app.toast.error ? 'alert' : 'status'"
  >
    {{ app.toast.text }}<button aria-label="알림 닫기" @click="app.toast = null">×</button>
  </div>
</template>
