<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Tabulator } from 'tabulator-tables'
import 'tabulator-tables/dist/css/tabulator.min.css'
import type { QueueMessage } from '../types/weather'

const props = defineProps<{
  messages: QueueMessage[]
}>()

const tableRef = ref<HTMLDivElement | null>(null)
let table: Tabulator | null = null

const columns = [
  { title: 'Message ID', field: 'messageId', width: 220 },
  { title: 'Body', field: 'bodyText' },
]

/** Flatten message bodies to display text for Tabulator. */
function toRows(messages: QueueMessage[]): Array<{ messageId: string | null; bodyText: string }> {
  return messages.map((message) => ({
    messageId: message.messageId,
    bodyText:
      typeof message.body === 'string' ? message.body : JSON.stringify(message.body),
  }))
}

onMounted(() => {
  table = new Tabulator(tableRef.value as HTMLElement, {
    columns,
    data: toRows(props.messages),
    layout: 'fitColumns',
  })
})

watch(
  () => props.messages,
  (messages) => {
    void table?.setData(toRows(messages))
  },
)

onBeforeUnmount(() => {
  table?.destroy()
  table = null
})
</script>

<template>
  <div
    ref="tableRef"
    data-testid="queue-table"
  />
</template>