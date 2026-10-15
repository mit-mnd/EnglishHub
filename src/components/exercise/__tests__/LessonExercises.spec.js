import { describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent, h, onMounted } from 'vue'
import LessonExercises from '../LessonExercises.vue'

const list = vi.fn(async () => [{ testId: 7, title: 'Quiz', skills: [], questionCount: 4, passingPercent: 75, attempts: 0 }])
vi.mock('@/api', () => ({ exerciseApi: { list: (...a) => list(...a) } }))

const mounted = vi.fn()
// Trình làm bài giả: chỉ cần phát sự kiện finished và đếm số lần được dựng.
const PlayerStub = defineComponent({
  emits: ['finished'],
  setup(_, { emit }) {
    onMounted(mounted)
    return () => h('button', { id: 'finish', onClick: () => emit('finished', { lessonCompleted: false }) }, 'finish')
  },
})

describe('LessonExercises', () => {
  it('nộp bài xong không dựng lại trình làm bài (không bắt làm lại từ đầu)', async () => {
    const wrapper = mount(LessonExercises, {
      props: { lessonId: 13 },
      global: { stubs: { ExercisePlayer: PlayerStub, ElSkeleton: true, ElTag: true, ElIcon: true, ElButton: { template: '<button class="start" @click="$emit(\'click\')"><slot /></button>' } } },
    })
    await flushPromises()
    await wrapper.find('button.start').trigger('click')
    expect(mounted).toHaveBeenCalledTimes(1)

    await wrapper.find('#finish').trigger('click')
    await flushPromises()

    expect(list).toHaveBeenCalledTimes(2)                 // danh sách được làm mới...
    expect(wrapper.find('#finish').exists()).toBe(true)   // ...nhưng trình làm bài vẫn còn đó
    expect(mounted).toHaveBeenCalledTimes(1)              // và không bị dựng lại
  }, 30_000)
})
