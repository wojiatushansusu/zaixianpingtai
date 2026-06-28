import request from '@/utils/request'

// 登录
export function login(data) {
  return request.post('/auth/login', data)
}

// 注册
export function register(data) {
  return request.post('/auth/register', data)
}

// 获取当前用户信息
export function getUserInfo() {
  return request.get('/auth/info')
}

// 退出登录（调用后端将Token加入黑名单）
export function logout() {
  return request.post('/auth/logout')
}

// ====== 密码重置 ======

// 发送重置密码验证码
export function requestResetPassword(data) {
  return request.post('/auth/reset-password/request', data, { silent: true })
}

// 验证码校验 + 重置密码
export function resetPassword(data) {
  return request.post('/auth/reset-password/reset', data, { silent: true })
}
