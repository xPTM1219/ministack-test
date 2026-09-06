/**
 * MSW request handlers mocking the API Gateway endpoints.
 *
 * Used by browser MSW (dev server behind `VITE_ENABLE_MSW=1`) and by Vitest
 * through the node server.
 */

import { http, HttpResponse } from 'msw'
import type { QueueResponse, WeatherResponse } from '../types/weather'

export const MOCK_DATES = ['2026-01-12', '2026-01-11', '2026-01-10']

/** Build a deterministic 3-day, hourly mock weather payload. */
export function mockWeather(): WeatherResponse {
  const weather: WeatherResponse['weather'] = {}
  MOCK_DATES.forEach((date, dayIndex) => {
    weather[date] = Array.from({ length: 24 }, (_, hour) => ({
      hour: `${String(hour).padStart(2, '0')}:00`,
      description: hour % 2 === 0 ? 'clear sky' : 'scattered clouds',
      temp: 20 + dayIndex + hour * 0.1,
    }))
  })
  return { dates: [...MOCK_DATES], weather }
}

/** Build a mock queue payload with two sample messages. */
export function mockQueue(): QueueResponse {
  return {
    messages: [
      { messageId: 'mock-1', body: { sender: 'sensor-1', temp: '38.75' } },
      { messageId: 'mock-2', body: 'plain text message' },
    ],
  }
}

/** MSW handlers for `GET /weather` and `GET /queue` on any host. */
export const handlers = [
  http.get('*/weather', () => HttpResponse.json(mockWeather())),
  http.get('*/queue', () => HttpResponse.json(mockQueue())),
]