/**
 * "Cơ sở dữ liệu" cục bộ: mỗi bộ sưu tập (collection) là một khóa JSON trong localStorage.
 *
 * - Khóa có tiền tố + phiên bản schema ("eh:v1:<collection>") để sau này nâng cấp cấu trúc an toàn.
 * - Giữ bản sao trong bộ nhớ: đọc nhanh, và vẫn chạy được khi localStorage bị chặn (chế độ ẩn danh...).
 * - Toàn bộ dữ liệu có thể xuất ra / nhập lại bằng một file JSON (sao lưu, chuyển máy).
 */

export const SCHEMA_VERSION = 1
const PREFIX = `eh:v${SCHEMA_VERSION}:`

/** Danh sách collection và giá trị mặc định. */
export const COLLECTIONS = {
  profile: null,          // hồ sơ người học (null = chưa khai báo)
  meta: {},               // bộ đếm id tự tăng
  lessonProgress: {},     // lessonId -> tiến độ bài học
  daily: {},              // 'YYYY-MM-DD' -> số liệu học trong ngày
  streak: { current: 0, longest: 0, lastActivityDate: null },
  attempts: [],           // lượt làm bài xếp lớp / bài tập
  path: null,             // lộ trình đang dùng
  notebook: [],           // sổ từ + trạng thái lặp lại ngắt quãng
  reviews: [],            // nhật ký ôn flashcard (giữ gần nhất)
  customWords: [],        // từ tra được từ từ điển ngoài (không có trong kho nội dung)
  speaking: [],           // lịch sử luyện nói
  writing: [],            // lịch sử luyện viết
  games: [],              // kết quả trò chơi
  toeic: [],              // kết quả luyện TOEIC
  sentences: {},          // patternId -> { saved, practicedAt }
  notifications: [],
}

/** Giới hạn số bản ghi giữ lại để localStorage (~5 MB) không đầy. */
export const LIMITS = { reviews: 3000, speaking: 100, writing: 100, games: 300, toeic: 300, notifications: 100, attempts: 300 }

const memory = new Map()

function backend() {
  try {
    return typeof localStorage === 'undefined' ? null : localStorage
  } catch {
    return null
  }
}

const clone = (v) => (v == null ? v : structuredClone(v))

/** Đọc một collection (trả về bản sao, sửa thoải mái không ảnh hưởng dữ liệu đã lưu). */
export function read(name) {
  if (!(name in COLLECTIONS)) throw new Error(`Collection không tồn tại: ${name}`)
  if (!memory.has(name)) {
    let value = clone(COLLECTIONS[name])
    const raw = backend()?.getItem(PREFIX + name)
    if (raw != null) {
      try {
        value = JSON.parse(raw)
      } catch {
        console.warn(`[db] Dữ liệu "${name}" hỏng, dùng giá trị mặc định`)
      }
    }
    memory.set(name, value)
  }
  return clone(memory.get(name))
}

export function write(name, value) {
  if (!(name in COLLECTIONS)) throw new Error(`Collection không tồn tại: ${name}`)
  let stored = value
  if (Array.isArray(value) && LIMITS[name] && value.length > LIMITS[name]) {
    stored = value.slice(value.length - LIMITS[name])   // bỏ bản ghi cũ nhất
  }
  memory.set(name, clone(stored))
  try {
    backend()?.setItem(PREFIX + name, JSON.stringify(stored))
  } catch (e) {
    console.warn(`[db] Không ghi được "${name}" vào localStorage:`, e.message)
  }
  return clone(stored)
}

/** Đọc - sửa - ghi trong một bước. fn nhận bản sao và có thể sửa trực tiếp hoặc trả về giá trị mới. */
export function update(name, fn) {
  const current = read(name)
  const result = fn(current)
  return write(name, result === undefined ? current : result)
}

/** Id tự tăng theo từng loại bản ghi. */
export function nextId(sequence) {
  let id
  update('meta', (meta) => {
    id = (meta[sequence] ?? 0) + 1
    meta[sequence] = id
  })
  return id
}

/** Toàn bộ dữ liệu người học dưới dạng object (để xuất file sao lưu). */
export function exportAll() {
  return {
    app: 'EnglishHub',
    schemaVersion: SCHEMA_VERSION,
    exportedAt: new Date().toISOString(),
    data: Object.fromEntries(Object.keys(COLLECTIONS).map((name) => [name, read(name)])),
  }
}

/** Nhập dữ liệu từ file sao lưu (ghi đè toàn bộ). */
export function importAll(backup) {
  if (!backup || backup.app !== 'EnglishHub' || typeof backup.data !== 'object') {
    throw new Error('File không phải bản sao lưu EnglishHub')
  }
  if (backup.schemaVersion > SCHEMA_VERSION) {
    throw new Error('Bản sao lưu được tạo từ phiên bản mới hơn của ứng dụng')
  }
  clearAll()
  for (const name of Object.keys(COLLECTIONS)) {
    if (name in backup.data) write(name, backup.data[name])
  }
}

/** Xóa toàn bộ dữ liệu người học trên máy này. */
export function clearAll() {
  memory.clear()
  const store = backend()
  if (!store) return
  for (const name of Object.keys(COLLECTIONS)) store.removeItem(PREFIX + name)
}

/** Ước lượng dung lượng đang dùng (byte) - hiển thị trong trang Tài khoản. */
export function usageBytes() {
  return Object.keys(COLLECTIONS).reduce((sum, name) => sum + (JSON.stringify(read(name))?.length ?? 0) * 2, 0)
}
