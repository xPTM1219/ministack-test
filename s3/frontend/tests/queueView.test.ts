import { describe, expect, it } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import QueueView from '../src/views/QueueView.vue'
import { mockQueue } from '../src/mocks/handlers'

describe('QueueView', () => {
  it('renders queue messages after clicking load', async () => {
    const wrapper = mount(QueueView, {
      global: {
        stubs: {
          QueueTable: {
            props: ['messages'],
            template: '<div data-testid="stub-table">{{ messages.length }}</div>',
          },
        },
      },
    })
    await wrapper.get('[data-testid="load-queue"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-testid="stub-table"]').text()).toBe(
      String(mockQueue().messages.length),
    )
  })
})