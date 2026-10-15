import { Connection, Headset, MagicStick } from '@element-plus/icons-vue'

/** Cấu hình 3 trò chơi từ vựng: khóa route (slug) -> loại trên server, số từ mỗi lượt, mô tả. */
export const GAMES = {
  matching: {
    type: 'MATCHING', count: 6, title: 'Ghép từ - nghĩa', icon: Connection, color: 'color-mix(in srgb, #4f46e5 72%, var(--eh-text))', bg: 'color-mix(in srgb, #4f46e5 14%, transparent)',
    description: 'Nối mỗi từ tiếng Anh với nghĩa đúng trước khi hết giờ. Ghép sai bị trừ điểm.',
  },
  scramble: {
    type: 'SCRAMBLE', count: 8, title: 'Xếp chữ', icon: MagicStick, color: 'color-mix(in srgb, #d97706 72%, var(--eh-text))', bg: 'color-mix(in srgb, #d97706 14%, transparent)',
    description: 'Sắp xếp các chữ cái bị xáo trộn thành từ đúng dựa vào nghĩa gợi ý.',
  },
  dictation: {
    type: 'DICTATION', count: 8, title: 'Nghe & chép', icon: Headset, color: 'color-mix(in srgb, #0891b2 72%, var(--eh-text))', bg: 'color-mix(in srgb, #0891b2 14%, transparent)',
    description: 'Nghe phát âm và gõ lại chính xác từ vừa nghe. Luyện cả nghe lẫn chính tả.',
  },
}

export const gameSlugByType = Object.fromEntries(Object.entries(GAMES).map(([slug, g]) => [g.type, slug]))
