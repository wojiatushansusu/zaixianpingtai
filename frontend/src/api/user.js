import request from '@/utils/request'

// 获取用户列表
export function getUserList(params) {
  return request.get('/admin/users', { params })
}

// 获取用户详情
export function getUser(id) {
  return request.get(`/admin/users/${id}`)
}

// 创建用户
export function createUser(data) {
  return request.post('/admin/users', data)
}

// 更新用户
export function updateUser(id, data) {
  return request.put(`/admin/users/${id}`, data)
}

// 删除用户
export function deleteUser(id) {
  return request.delete(`/admin/users/${id}`)
}

// 获取角色列表
export function getRoles() {
  return request.get('/admin/users/roles')
}

// 修改密码（管理员接口，兼容旧代码）
export function changePassword(data) {
  return request.put('/user/profile/password', data)
}

// 上传头像
export function uploadAvatar(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/files/avatar', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

// ========== 个人中心 ==========

// 获取当前用户信息
export function getProfile() {
  return request.get('/user/profile')
}

// 修改个人信息
export function updateProfile(data) {
  return request.put('/user/profile', data)
}
