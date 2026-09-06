<script setup lang="ts">
import { ref } from 'vue'
import type { QueueMessage } from '../types/weather'
import { getQueue, ApiError } from '../services/api'
import QueueTable from '../components/QueueTable.vue'

const messages = ref<QueueMessage[]>([])
const error = ref('')
const loading = ref(false)

async function refresh(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    messages.value = (await getQueue()).messages
  } catch (cause) {
    error.value = cause instanceof ApiError ? cause.message : String(cause)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section>
    <h1>Queue</h1>
    <button
      type="button"
      data-testid="load-queue"
      @click="refresh"
    >
      {{ loading ? 'Loading…' : 'Load messages' }}
    </button>
    <p
      v-if="error"
      class="error"
    >
      {{ error }}
    </p>
    <QueueTable
      v-else
      :messages="messages"
    />
  </section>
</template>

<style>
.error {
  color: crimson;
}
</style>