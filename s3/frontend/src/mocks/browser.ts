/**
 * Browser-side MSW worker setup, used only when the dev server is started
 * with `VITE_ENABLE_MSW=1`.
 */

import { setupWorker } from 'msw/browser'
import { handlers } from './handlers'

export const worker = setupWorker(...handlers)

/**
 * Start the MSW browser worker if `VITE_ENABLE_MSW=1`; no-op otherwise.
 *
 * @returns promise resolving once the worker (if enabled) is ready
 */
export async function startMockWorker(): Promise<void> {
  if (import.meta.env.VITE_ENABLE_MSW === '1') {
    await worker.start({ onUnhandledRequest: 'bypass' })
  }
}