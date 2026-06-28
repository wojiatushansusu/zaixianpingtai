import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'
import { createPinia } from 'pinia'
import './assets/styles/global.css'

// 导入错误监控工具
import { enableErrorMonitoring } from './utils/errorMonitor'

const app = createApp(App)

// 注册所有 Element Plus 图标
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(ElementPlus, { locale: zhCn })
app.use(createPinia())
app.use(router)

// 在生产环境启用错误监控
if (import.meta.env.PROD) {
  enableErrorMonitoring()
  console.info('[ErrorMonitor] 前端错误监控已启用')
}

app.mount('#app')
