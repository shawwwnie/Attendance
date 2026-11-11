import { ref, shallowRef, onMounted } from 'vue'
import { errorMessage } from '../lib/api'

export function useResource<T>(loader: () => Promise<T>) {
  const data = shallowRef<T>()
  const loading = ref(true)
  const error = ref('')
  let generation = 0
  async function refresh() {
    const current = ++generation
    loading.value = true
    error.value = ''
    try {
      const result = await loader()
      if (current === generation) data.value = result
    } catch (cause) {
      if (current === generation) {
        error.value = errorMessage(cause)
        data.value = undefined
      }
    } finally {
      if (current === generation) loading.value = false
    }
  }
  onMounted(refresh)
  return { data, loading, error, refresh }
}
