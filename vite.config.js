import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

// Ứng dụng chạy hoàn toàn trên trình duyệt: không có backend, không cần proxy.
// - Nội dung học (khóa học, từ vựng, mẫu câu, TOEIC...) là file JSON trong /data, import qua alias @data.
// - Hồ sơ & tiến độ của người học lưu trong localStorage (xem src/core/storage).
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
      '@data': fileURLToPath(new URL('./data', import.meta.url)),
    },
  },
  server: { port: 5173 },
  build: {
    // Element Plus import toàn bộ (~280 kB gzip). Tối ưu sau bằng import theo nhu cầu (unplugin-vue-components).
    chunkSizeWarningLimit: 1000,
  },
  test: {
    environment: 'jsdom',
  },
})
