import { createApp } from 'vue'
import App from './App.vue'
import { router } from './router'
import { startMockWorker } from './mocks/browser'

async function bootstrap(): Promise<void> {
  await startMockWorker()
  createApp(App).use(router).mount('#app')
}

void bootstrap()