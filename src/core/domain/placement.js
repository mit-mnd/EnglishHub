/**
 * Xếp level từ % điểm bài kiểm tra xếp lớp.
 * Quy tắc: chọn level có placementMinPercent LỚN NHẤT mà vẫn <= % của học viên.
 * Không phụ thuộc placementMaxPercent nên không có "khe hở" do làm tròn giữa các khoảng.
 */
export function resolveLevel(percentage, levels) {
  if (!levels.length) throw new Error('Chưa cấu hình levels')
  const byMin = [...levels].sort((a, b) => a.placementMinPercent - b.placementMinPercent)
  const reached = byMin.filter((l) => l.placementMinPercent <= percentage)
  return reached.length ? reached[reached.length - 1] : byMin[0]
}

/** Ngưỡng % dưới mức này thì coi là kỹ năng yếu (được ưu tiên trong lộ trình). */
export const WEAK_SKILL_THRESHOLD = 60

export const percent = (value, max) => (max === 0 ? 0 : Math.round((value * 10000) / max) / 100)
