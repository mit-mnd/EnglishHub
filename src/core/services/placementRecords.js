/** Đọc kết quả bài xếp lớp đã lưu (tách riêng để lộ trình dùng mà không phụ thuộc vòng với placement). */
import * as db from '../storage/db'
import { WEAK_SKILL_THRESHOLD } from '../domain/placement'

export const placementAttempts = () => db.read('attempts').filter((a) => a.kind === 'PLACEMENT')

export const latestFinishedPlacement = () => placementAttempts().filter((a) => a.status !== 'IN_PROGRESS')
  .sort((a, b) => (b.submittedAt ?? '').localeCompare(a.submittedAt ?? ''))[0] ?? null

/** Kết quả xếp lớp gần nhất: level + kỹ năng yếu (< 60%). */
export function latestPlacementSnapshot() {
  const a = latestFinishedPlacement()
  if (!a) return null
  return {
    attemptId: a.id, levelId: a.resultLevelId,
    weakSkills: (a.skillScores ?? []).filter((s) => s.percentage < WEAK_SKILL_THRESHOLD).map((s) => s.skill),
  }
}
