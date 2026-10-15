import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import vi from 'element-plus/es/locale/lang/vi'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import './styles/main.css'

import App from './App.vue'
import router from './router'
import { initTheme } from './composables/useTheme'

initTheme()

// Ứng dụng chạy hoàn toàn trên trình duyệt: không có server, không đăng nhập.
// Hồ sơ người học được khai báo ở lần đầu mở ứng dụng (trang /welcome) và lưu trong localStorage.
const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: vi })
app.mount('#app')
