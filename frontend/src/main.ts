import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import { router } from './router'
import { useApp } from './stores/app'
import './style.css'

async function start() {
  const app = createApp(App)
  app.use(createPinia())
  const store = useApp()
  try {
    await store.restore()
  } catch (e) {
    store.fail(e)
  }
  window.addEventListener('careflow:unauthorized', () => {
    if (store.session) {
      store.session = null
      void router.push('/login')
      store.notify('세션이 만료되었습니다. 다시 로그인해주세요.', true)
    }
  })
  app.use(router)
  app.mount('#app')
}
void start()
