/**
 * Kho nội dung học (chỉ đọc) - nạp từ các file JSON trong thư mục /data.
 *
 * - Nội dung cốt lõi (level, khóa học, bài test, từ vựng) được đóng gói sẵn (eager).
 * - Mẫu câu và đề luyện TOEIC lớn hơn nên chỉ tải khi người học mở trang tương ứng (lazy).
 * - Thêm file từ vựng mới: đặt vào data/vocabulary với tên *-words.json / *-sets.json là tự được nạp.
 * - Mở rộng bộ từ có sẵn mà không sửa file gốc: thêm mục { "id": <id bộ>, "wordIds": [...] } (không có "name") vào bất kỳ file *-sets.json;
 *   các wordIds được nối vào bộ cùng id.
 */
import levelsJson from '../../../data/levels.json'
import coursesJson from '../../../data/courses.json'
import placementJson from '../../../data/tests/placement.json'
import exercisesJson from '../../../data/tests/lesson-exercises.json'

const wordFiles = import.meta.glob('../../../data/vocabulary/*words.json', { eager: true, import: 'default' })
const setFiles = import.meta.glob('../../../data/vocabulary/*sets.json', { eager: true, import: 'default' })
const lazySentences = import.meta.glob('../../../data/sentences/*.json', { import: 'default' })
const lazyToeic = import.meta.glob('../../../data/toeic/part*.json', { import: 'default' })

// ------------------------------------------------------------------ levels

export const levels = [...levelsJson].sort((a, b) => a.sortOrder - b.sortOrder)
const levelById = new Map(levels.map((l) => [l.id, l]))
export const getLevel = (id) => (id == null ? null : levelById.get(Number(id)) ?? null)

// ------------------------------------------------------------------ khóa học & bài học

export const courses = [...coursesJson].sort((a, b) =>
  (getLevel(a.levelId)?.sortOrder ?? 0) - (getLevel(b.levelId)?.sortOrder ?? 0) || (a.sortOrder ?? 0) - (b.sortOrder ?? 0) || a.id - b.id)
const courseById = new Map(courses.map((c) => [c.id, c]))

/** Bài học phẳng, mỗi bài biết khóa học chứa nó; thứ tự = thứ tự trong khóa. */
export const lessons = courses.flatMap((c) => c.lessons.map((l, index) => ({
  ...l,
  courseId: c.id,
  courseTitle: c.title,
  levelId: l.levelId ?? c.levelId,
  sortOrder: l.sortOrder ?? index + 1,
  transcript: l.transcript ?? [],
})))
const lessonById = new Map(lessons.map((l) => [l.id, l]))

export const getCourse = (id) => courseById.get(Number(id)) ?? null
export const getLesson = (id) => lessonById.get(Number(id)) ?? null
export const lessonsOfCourse = (courseId) => lessons.filter((l) => l.courseId === Number(courseId))

// ------------------------------------------------------------------ bài test

export const placementTest = placementJson
export const exercises = exercisesJson
const testById = new Map([placementJson, ...exercisesJson].map((t) => [t.id, t]))
export const getTest = (id) => testById.get(Number(id)) ?? null
export const exercisesOfLesson = (lessonId) => exercisesJson.filter((t) => t.lessonId === Number(lessonId))

/** Câu hỏi luyện nói (dùng khi ghi âm theo questionId). */
const questionById = new Map([placementJson, ...exercisesJson].flatMap((t) => t.questions).map((q) => [q.id, q]))
export const getQuestion = (id) => questionById.get(Number(id)) ?? null

// ------------------------------------------------------------------ từ vựng

const byId = (a, b) => a.id - b.id
export const words = Object.values(wordFiles).flat().sort(byId)
const wordById = new Map(words.map((w) => [w.id, w]))
export const getWord = (id) => wordById.get(Number(id)) ?? null

const wordsByHeadword = new Map()
for (const w of words) {
  const key = w.headword.toLowerCase()
  if (!wordsByHeadword.has(key)) wordsByHeadword.set(key, [])
  wordsByHeadword.get(key).push(w)
}
/** Tra theo headword (không phân biệt hoa thường). */
export const findWordsByHeadword = (headword) => wordsByHeadword.get((headword ?? '').toLowerCase()) ?? []

/** Mọi mục bộ từ trước khi gộp (dùng cho kiểm tra dữ liệu). */
export const rawVocabularySets = Object.values(setFiles).flat()

/** Gộp phần mở rộng vào bộ gốc (bộ gốc = mục có "name"); từ trùng được bỏ, thứ tự giữ nguyên. */
function mergeSets(entries) {
  const bases = new Map(entries.filter((s) => s.name).map((s) => [s.id, { ...s, wordIds: [...s.wordIds] }]))
  for (const ext of entries.filter((s) => !s.name)) {
    const base = bases.get(ext.id)
    if (!base) continue
    const have = new Set(base.wordIds)
    for (const id of ext.wordIds) if (!have.has(id)) { have.add(id); base.wordIds.push(id) }
  }
  return [...bases.values()]
}

export const vocabularySets = mergeSets(rawVocabularySets)
  .sort((a, b) => (a.levelId ?? 0) - (b.levelId ?? 0) || a.name.localeCompare(b.name, 'vi'))
export const getVocabularySet = (id) => vocabularySets.find((s) => s.id === Number(id)) ?? null

// ------------------------------------------------------------------ nội dung tải khi cần

const cache = new Map()
async function loadLazy(loaders, file) {
  const key = Object.keys(loaders).find((k) => k.endsWith(`/${file}`))
  if (!key) return null
  if (!cache.has(key)) cache.set(key, loaders[key]())
  return cache.get(key)
}

/** Danh sách chủ đề mẫu câu ([] nếu chưa có dữ liệu). */
export async function loadSentenceTopics() {
  return (await loadLazy(lazySentences, 'patterns.json')) ?? []
}

/** Số Part của file TOEIC: "part5.json", "part5-b.json"... -> 5. */
const toeicPartOf = (key) => Number(key.match(/\/part(\d)(?:-[\w-]+)?\.json$/)?.[1]) || null

/** Các phần TOEIC có dữ liệu (2..7). */
export const toeicParts = [...new Set(Object.keys(lazyToeic).map(toeicPartOf).filter(Boolean))].sort()

/**
 * Dữ liệu một Part: gộp mọi file của Part đó (part5.json + part5-b.json + ...), giữ tiêu đề / mô tả của file chính.
 * Thêm bộ đề mới chỉ cần thả file "partN-<tên>.json" vào data/toeic.
 */
export async function loadToeicPart(part) {
  const keys = Object.keys(lazyToeic).filter((k) => toeicPartOf(k) === Number(part)).sort()
  if (!keys.length) return null
  const cacheKey = `toeic:${part}`
  if (!cache.has(cacheKey)) {
    cache.set(cacheKey, Promise.all(keys.map((k) => lazyToeic[k]())).then((files) => {
      const main = files[keys.findIndex((k) => k.endsWith(`/part${part}.json`))] ?? files[0]
      return { ...main, items: files.flatMap((f) => f.items) }
    }))
  }
  return cache.get(cacheKey)
}
