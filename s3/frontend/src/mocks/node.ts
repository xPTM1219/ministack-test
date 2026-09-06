/**
 * Node-side MSW server for Vitest (intercepts `fetch` in jsdom/node).
 */

import { setupServer } from 'msw/node'
import { handlers } from './handlers'

/** Shared MSW server instance used by the test setup file. */
export const server = setupServer(...handlers)