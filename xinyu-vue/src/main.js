// 应用入口。创建 Vue 实例，挂上 Pinia（状态管理）、router（路由），
// 引入全局样式（tokens/base/utilities）。
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import './styles/index.css'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.mount('#app')
