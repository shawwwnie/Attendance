<script setup lang="ts">
import { LoaderCircle, CircleAlert, Inbox } from 'lucide-vue-next'
defineProps<{ loading?: boolean; error?: string; empty?: boolean; message?: string }>()
defineEmits<{ retry: [] }>()
</script>
<template>
  <div v-if="loading" class="resource-state" role="status">
    <LoaderCircle class="spin" :size="25" />
    <p>데이터를 불러오고 있습니다.</p>
  </div>
  <div v-else-if="error" class="resource-state" role="alert">
    <CircleAlert :size="26" />
    <p>{{ error }}</p>
    <button class="button secondary" @click="$emit('retry')">다시 불러오기</button>
  </div>
  <div v-else-if="empty" class="resource-state">
    <Inbox :size="30" />
    <p>{{ message ?? '조건에 맞는 데이터가 없습니다.' }}</p>
  </div>
</template>
