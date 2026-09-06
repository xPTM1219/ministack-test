<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Tabulator } from 'tabulator-tables'
import 'tabulator-tables/dist/css/tabulator.min.css'
import type { HourSample } from '../types/weather'

const props = defineProps<{
  date: string
  samples: HourSample[]
}>()

const tableRef = ref<HTMLDivElement | null>(null)
let table: Tabulator | null = null

const columns = [
  { title: 'Hour', field: 'hour', width: 100 },
  { title: 'Description', field: 'description' },
  { title: 'Temp °F', field: 'temp', hozAlign: 'right' as const },
]

onMounted(() => {
  table = new Tabulator(tableRef.value as HTMLElement, {
    columns,
    data: props.samples,
    layout: 'fitColumns',
  })
})

watch(
  () => props.samples,
  (samples) => {
    void table?.setData(samples)
  },
)

onBeforeUnmount(() => {
  table?.destroy()
  table = null
})
</script>

<template>
  <div>
    <h2 v-if="date">
      {{ date }}
    </h2>
    <div
      ref="tableRef"
      data-testid="weather-table"
    />
  </div>
</template>