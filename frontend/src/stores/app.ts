import { defineStore } from 'pinia'
import { ref } from 'vue'
import { api, ApiError, errorMessage } from '../lib/api'
import type { Session } from '../types'

export const useApp = defineStore('app', () => {
  const session = ref<Session | null>(null)
  const toast = ref<{ text: string; error: boolean } | null>(null)
  let timer: ReturnType<typeof setTimeout> | undefined
  function notify(text: string, error = false) {
    clearTimeout(timer)
    toast.value = { text, error }
    timer = setTimeout(
      () => {
        toast.value = null
      },
      error ? 12000 : 4500,
    )
  }
  function fail(error: unknown) {
    notify(errorMessage(error), true)
  }
  async function restore() {
    try {
      session.value = await api<Session>('/session')
    } catch (e) {
      session.value = null
      if (!(e instanceof ApiError && e.status === 401)) throw e
    }
  }
  return { session, toast, notify, fail, restore }
})
