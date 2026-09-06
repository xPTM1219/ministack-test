import { describe, expect, it } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import WeatherView from '../src/views/WeatherView.vue'
import AboutView from '../src/views/AboutView.vue'
import { mockWeather } from '../src/mocks/handlers'

describe('WeatherView', () => {
  it('renders rows and switches dates via dropdown', async () => {
    const wrapper = mount(WeatherView, {
      global: {
        stubs: {
          WeatherTable: {
            props: ['date', 'samples'],
            template: '<div data-testid="stub-table">{{ date }}:{{ samples.length }}</div>',
          },
        },
      },
    })
    await flushPromises()

    const select = wrapper.get('[data-testid="date-select"]')
    const dates = mockWeather().dates

    // First date selected by default.
    const stub = wrapper.get('[data-testid="stub-table"]')
    expect(stub.text()).toBe(`${dates[0]}:24`)

    // Switching the dropdown updates the selected date.
    select.setValue(dates[2])
    await flushPromises()
    expect((stub.element as HTMLElement).textContent).toBe(`${dates[2]}:24`)
  })
})

describe('AboutView', () => {
  it('shows name and email', () => {
    const wrapper = mount(AboutView)
    expect(wrapper.text()).toContain('Marcial')
    const link = wrapper.get('[data-testid="email-link"]')
    expect(link.attributes('href')).toBe('mailto:patoloqsea@yopmail.com')
  })
})