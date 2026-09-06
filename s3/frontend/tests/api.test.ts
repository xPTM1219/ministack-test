import { describe, expect, it } from 'vitest'
import { getQueue, getWeather, ApiError } from '../src/services/api'
import { server } from '../src/mocks/node'
import { mockQueue, mockWeather } from '../src/mocks/handlers'
import { http, HttpResponse } from 'msw'

describe('api service', () => {
  it('fetches weather via MSW', async () => {
    const weather = await getWeather()
    expect(weather.dates).toEqual(mockWeather().dates)
    expect(weather.weather[weather.dates[0]]).toHaveLength(24)
    expect(weather.weather[weather.dates[0]][0]).toEqual({
      hour: '00:00',
      description: 'clear sky',
      temp: 20,
    })
  })

  it('fetches queue via MSW', async () => {
    const queue = await getQueue()
    expect(queue.messages).toEqual(mockQueue().messages)
  })

  it('throws ApiError on non-2xx', async () => {
    server.use(http.get('*/weather', () => new HttpResponse(null, { status: 500 })))
    await expect(getWeather()).rejects.toThrow(ApiError)
  })

  it('throws ApiError on network failure', async () => {
    server.use(http.get('*/queue', () => HttpResponse.error()))
    await expect(getQueue()).rejects.toThrow(ApiError)
  })
})