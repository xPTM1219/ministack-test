<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import type { WeatherResponse } from '../types/weather'
import { getWeather, ApiError } from '../services/api'
import WeatherTable from '../components/WeatherTable.vue'

const data = ref<WeatherResponse | null>(null)
const selectedDate = ref('')
const error = ref('')
const loading = ref(true)

const dates = computed(() => data.value?.dates ?? [])

onMounted(async () => {
  try {
    data.value = await getWeather()
    selectedDate.value = dates.value[0] ?? ''
  } catch (cause) {
    error.value = cause instanceof ApiError ? cause.message : String(cause)
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <section>
    <h1>Weather</h1>
    <p v-if="loading">
      Loading…
    </p>
    <p
      v-else-if="error"
      class="error"
    >
      {{ error }}
    </p>
    <template v-else>
      <label>
        Date:
        <select
          v-model="selectedDate"
          data-testid="date-select"
        >
          <option
            v-for="date in dates"
            :key="date"
            :value="date"
          >
            {{ date }}
          </option>
        </select>
      </label>
      <WeatherTable
        :date="selectedDate"
        :samples="data?.weather[selectedDate] ?? []"
      />
    </template>
  </section>
</template>

<style>
.error {
  color: crimson;
}
</style>