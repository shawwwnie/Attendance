<script setup lang="ts">
import { ChevronLeft, ChevronRight } from 'lucide-vue-next'
defineProps<{ page: number; size: number; total: number }>()
defineEmits<{ change: [page: number] }>()
</script>
<template>
  <div class="pagination">
    <span
      >전체 {{ total }}건 · {{ total ? page * size + 1 : 0 }}–{{
        Math.min((page + 1) * size, total)
      }}건</span
    >
    <div>
      <button
        class="icon-button"
        aria-label="이전 페이지"
        :disabled="page === 0"
        @click="$emit('change', page - 1)"
      >
        <ChevronLeft :size="18" />
      </button>
      <span class="page-number">{{ page + 1 }}</span>
      <button
        class="icon-button"
        aria-label="다음 페이지"
        :disabled="(page + 1) * size >= total"
        @click="$emit('change', page + 1)"
      >
        <ChevronRight :size="18" />
      </button>
    </div>
  </div>
</template>
