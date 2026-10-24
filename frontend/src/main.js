import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'
import { createPinia } from 'pinia'
// 顺序要求：global.css 必须在 element-plus/dist/index.css 之后引入。
// global.css 里的 :root 主题桥接与 Element Plus 默认 :root 选择器同级，
// 靠的就是后引入取胜。若调换顺序，主题会静默失效。
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
