<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref } from 'vue'
import { X } from 'lucide-vue-next'
import { useApp } from '../stores/app'
defineProps<{ title: string; wide?: boolean }>()
const emit = defineEmits<{ close: [] }>()
const dialog = ref<HTMLDialogElement>()
const app = useApp()
const titleId = `modal-${crypto.randomUUID()}`
onMounted(() => {
  app.toast = null
  dialog.value?.showModal()
})
onBeforeUnmount(() => dialog.value?.close())
</script>
<template>
  <dialog
    ref="dialog"
    :class="['modal', { wide }]"
    :aria-labelledby="titleId"
    @cancel.prevent="emit('close')"
  >
    <div class="modal-heading">
      <h2 :id="titleId">{{ title }}</h2>
      <button class="icon-button" aria-label="닫기" @click="emit('close')"><X :size="20" /></button>
    </div>
    <slot />
    <p v-if="app.toast?.error" class="form-error" role="alert">{{ app.toast.text }}</p>
  </dialog>
</template>
