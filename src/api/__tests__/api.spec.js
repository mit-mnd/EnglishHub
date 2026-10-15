import { beforeEach, describe, expect, it } from 'vitest'
import { reactive } from 'vue'
import * as db from '@/core/storage/db'
import { placementApi, profileApi } from '@/api'

beforeEach(() => db.clearAll())

describe('api facade', () => {
  it('nhận object reactive của Vue (Proxy) như dữ liệu thuần', async () => {
    await profileApi.create(reactive({ fullName: 'An', learningGoal: 'GENERAL', dailyGoalMinutes: 15 }))
    const attempt = await placementApi.start()
    const q = attempt.questions[0]
    const answers = reactive({ [q.id]: { selectedOptionIds: [q.options[0].id] } })
    await expect(placementApi.saveAnswer(attempt.attemptId, { questionId: q.id, ...answers[q.id] })).resolves.toBeUndefined()
  })

  it('kết quả là bản sao: sửa không ảnh hưởng dữ liệu đã lưu', async () => {
    const p = await profileApi.create({ fullName: 'An', learningGoal: 'GENERAL', dailyGoalMinutes: 15 })
    p.fullName = 'Đã sửa'
    expect((await profileApi.get()).fullName).toBe('An')
  })
})
