// 应用入口。创建 Vue 实例，挂上 Pinia（状态管理）、router（路由），
// 引入全局样式（tokens/base/utilities）。
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { initTheme } from './composables/useTheme'
// 霞鹜文楷屏幕阅读版（简体字形）：按 unicode-range 切片，浏览器只下载页面实际用到的字符分片。
import 'lxgw-wenkai-screen-webfont/lxgwwenkaigbscreen.css'
import './styles/index.css'

initTheme()

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.mount('#app')
