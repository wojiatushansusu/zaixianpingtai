import request from '@/utils/request'

// 上传注册照片（所有已登录用户可用）
export function uploadRegistrationPhoto(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/user/face/register', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

// 获取注册照片状态（所有已登录用户可用）
export function getRegistrationPhoto() {
  return request.get('/user/face/register', { silent: true })
}

// 考前人脸核验（学生）
export function verifyFace(examId, file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post(`/student/exams/${examId}/face-verify`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 30000
  })
}

// 验证考试凭证
export function verifyExamToken(examId, examToken) {
  return request.get(`/student/exams/${examId}/verify-token`, {
    params: { examToken }
  })
}
