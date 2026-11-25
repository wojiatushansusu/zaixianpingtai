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

/**
 * 渲染启动失败提示。
 *
 * 用原生 DOM 而不是 Vue —— 走到这里说明应用可能压根没挂载起来。
 * 背景：登录页是路由分包（动态 import），网络中途断一次，分包就下不来；
 * 此时 HTML 已经加载、标题也正常，body 却是一片空白，看的人完全无从判断。
 */
function renderBootFailure(reason) {
  const el = document.getElementById('app')
  if (!el) return
  el.innerHTML = ''

  const box = document.createElement('div')
  box.setAttribute(
    'style',
    'max-width:520px;margin:15vh auto;padding:0 24px;line-height:1.75;' +
    'font-family:system-ui,-apple-system,"Segoe UI",sans-serif;color:#303133'
  )

  const title = document.createElement('h2')
  title.textContent = '页面资源没能加载完整'
  title.setAttribute('style', 'font-size:18px;margin:0 0 12px')

  const tip = document.createElement('p')
  tip.textContent = '通常是下载脚本时网络中断了——代理、防火墙或线路抖动都会这样。刷新一次通常就好。'
  tip.setAttribute('style', 'margin:0 0 10px;font-size:14px;color:#606266')

  const detail = document.createElement('p')
  detail.textContent = String(reason || '')
  detail.setAttribute('style', 'margin:0 0 18px;font-size:12px;color:#909399;word-break:break-all')

  const button = document.createElement('button')
  button.textContent = '重新加载'
  button.setAttribute(
    'style',
    'padding:8px 20px;border:0;border-radius:6px;background:#409eff;color:#fff;' +
    'font-size:14px;cursor:pointer'
  )
  button.addEventListener('click', () => window.location.reload())

  box.appendChild(title)
  box.appendChild(tip)
  box.appendChild(detail)
  box.appendChild(button)
  el.appendChild(box)
}

// 路由分包加载失败时 Vite 会派发 vite:preloadError。
// 不接住的话就是纯白屏：有标题、无内容，看不出是网络问题还是代码问题。
window.addEventListener('vite:preloadError', (event) => {
  const attempted = sessionStorage.getItem('__chunk_retry__')
  if (!attempted) {
    // 先自动刷新一次：部署后 chunk 哈希变化也会走到这里，刷新即恢复
    sessionStorage.setItem('__chunk_retry__', '1')
    window.location.reload()
    return
  }
  sessionStorage.removeItem('__chunk_retry__')
  const message = event && event.payload && event.payload.message
  renderBootFailure(message || 'chunk load error')
})

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

// 路由首个页面加载成功，说明分包都拿到了，清掉重试标记
router.isReady().then(() => sessionStorage.removeItem('__chunk_retry__'))
