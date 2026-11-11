import { createRouter, createWebHistory } from 'vue-router'
import { useApp } from './stores/app'
import LoginView from './views/LoginView.vue'
import DashboardView from './views/DashboardView.vue'
import RecipientsView from './views/RecipientsView.vue'
import ScheduleView from './views/ScheduleView.vue'
import BillingView from './views/BillingView.vue'
import AuditView from './views/AuditView.vue'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: LoginView },
    { path: '/', component: DashboardView },
    { path: '/recipients', component: RecipientsView },
    { path: '/schedule', component: ScheduleView },
    { path: '/billing', component: BillingView },
    { path: '/audit', component: AuditView },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

router.beforeEach((to) => {
  const app = useApp()
  if (!app.session && to.path !== '/login') return '/login'
  if (app.session && to.path === '/login') return '/'
})
