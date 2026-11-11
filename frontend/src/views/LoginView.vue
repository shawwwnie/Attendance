<script setup lang="ts">
import { ref } from 'vue'
import { HeartPulse, ArrowRight, CalendarCheck, ReceiptText, ShieldCheck } from 'lucide-vue-next'
import { useRouter } from 'vue-router'
import { useApp } from '../stores/app'
import { signIn, errorMessage } from '../lib/api'
const router = useRouter()
const app = useApp()
const username = ref('manager')
const password = ref('careflow-demo')
const busy = ref(false)
const error = ref('')
async function login() {
  busy.value = true
  error.value = ''
  try {
    await signIn(username.value, password.value)
    await app.restore()
    await router.push('/')
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    busy.value = false
  }
}
</script>
<template>
  <div class="login-layout">
    <section class="login-story">
      <div class="brand">
        <span class="brand-mark"><HeartPulse :size="28" /></span
        ><span>CareFlow<small>센터 운영의 흐름</small></span>
      </div>
      <div class="login-copy">
        <span class="eyebrow light">A BETTER DAY, TOGETHER</span>
        <h1>돌봄에 집중할 수 있도록,<br /><em>센터의 하루를 잇다.</em></h1>
        <p>
          이용 일정부터 청구와 수납까지.<br />흩어져 있던 센터 업무를 하나의 흐름으로 관리하세요.
        </p>
        <div class="login-features">
          <span><CalendarCheck :size="21" /> 하루의 이용 일정</span
          ><span><ReceiptText :size="21" /> 정확한 청구와 수납</span
          ><span><ShieldCheck :size="21" /> 투명한 변경 이력</span>
        </div>
      </div>
      <div class="login-caption">DAYCARE OPERATIONS, THOUGHTFULLY CONNECTED.</div>
    </section>
    <section class="login-form-area">
      <form class="login-card" @submit.prevent="login">
        <span class="eyebrow">WELCOME TO CAREFLOW</span>
        <h2>오늘의 센터 업무를<br />시작해볼까요?</h2>
        <p class="muted">데모 계정으로 모든 업무 흐름을 체험할 수 있습니다.</p>
        <label>아이디<input v-model="username" autocomplete="username" required /></label
        ><label
          >비밀번호<input
            v-model="password"
            type="password"
            autocomplete="current-password"
            required
        /></label>
        <p v-if="error" class="form-error" role="alert">{{ error }}</p>
        <button class="button primary login-submit" :disabled="busy">
          {{ busy ? '로그인 중…' : '워크스페이스 입장' }}<ArrowRight :size="19" />
        </button>
        <div class="demo-accounts">
          <strong>체험 계정 선택</strong>
          <div>
            <button
              type="button"
              :class="{ selected: username === 'manager' }"
              @click="username = 'manager'"
            >
              운영 매니저</button
            ><button
              type="button"
              :class="{ selected: username === 'viewer' }"
              @click="username = 'viewer'"
            >
              조회 전용</button
            ><button
              type="button"
              :class="{ selected: username === 'other-manager' }"
              @click="username = 'other-manager'"
            >
              다른 센터
            </button>
          </div>
          <small>기본 데모 비밀번호: careflow-demo</small>
        </div>
        <p class="demo-disclaimer">
          포트폴리오용 서비스입니다. 모든 수급자와 요금은 가상이며 실제 공단 청구·결제는 수행하지
          않습니다.
        </p>
      </form>
    </section>
  </div>
</template>
