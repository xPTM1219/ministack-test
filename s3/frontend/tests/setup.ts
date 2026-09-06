import { beforeAll, afterAll } from 'vitest'
import { server } from '../src/mocks/node'

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterAll(() => server.close())