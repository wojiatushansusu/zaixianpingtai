import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'

const request = axios.create({
  baseURL: '/api',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
})

// 请求拦截器
request.interceptors.request.use(
  config => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers['Authorization'] = 'Bearer ' + token
    }
    return config
  },
  error => {
    return Promise.reject(error)
  }
)

// 响应拦截器
let lastErrorMsg = ''
let lastErrorTime = 0
request.interceptors.response.use(
  response => {
    const res = response.data
    if (res.code !== 200) {
      // 未认证 - 跳转登录（仅对非401的code显示错误消息，401单独处理）
      if (res.code === 401) {
        clearAuthAndRedirect(res.message || '登录已过期，请重新登录')
      } else if (!response.config.silent) {
        showErrorMsg(res.message || '操作失败')
      }
      return Promise.reject(new Error(res.message || '操作失败'))
    }
    return res
  },
  error => {
    // silent 模式不弹错误提示（用于辅助信息加载，如考试标题）
    if (error.config?.silent) {
      return Promise.reject(error)
    }
    if (error.response) {
      const status = error.response.status
      if (status === 401) {
        clearAuthAndRedirect('登录已过期，请重新登录')
      } else if (status === 403) {
        showErrorMsg('权限不足')
      } else {
        showErrorMsg(error.response.data?.message || '请求失败')
      }
    } else {
      showErrorMsg('网络连接失败')
    }
    return Promise.reject(error)
  }
)

// 防止短时间内弹出相同的错误提示
function showErrorMsg(msg) {
  const now = Date.now()
  if (msg === lastErrorMsg && now - lastErrorTime < 1000) {
    return
  }
  lastErrorMsg = msg
  lastErrorTime = now
  ElMessage.error(msg)
}

// 清除认证信息并跳转登录页
let isRedirecting = false
function clearAuthAndRedirect(message) {
  if (isRedirecting) return
  isRedirecting = true
  localStorage.removeItem('token')
  localStorage.removeItem('userInfo')
  ElMessage.error(message)
  // 如果当前已在登录页，不重复跳转
  if (window.location.hash === '#/login' || window.location.pathname === '/login') {
    setTimeout(() => { isRedirecting = false }, 2000)
    return
  }
  router.push('/login').finally(() => {
    setTimeout(() => { isRedirecting = false }, 2000)
  })
}

export default request
