import request from '@/utils/request'

// 获取试卷列表
export function getPaperList(params) {
  return request.get('/teacher/papers', { params })
}

// 获取所有试卷
export function getAllPapers() {
  return request.get('/teacher/papers/all')
}

// 获取试卷详情
export function getPaper(id) {
  return request.get(`/teacher/papers/${id}`)
}

// 获取试卷题目
export function getPaperQuestions(id) {
  return request.get(`/teacher/papers/${id}/questions`)
}

// 创建试卷（手工组卷）
export function createPaper(data) {
  return request.post('/teacher/papers', data)
}

// 更新试卷
export function updatePaper(id, data) {
  return request.put(`/teacher/papers/${id}`, data)
}

// 删除试卷
export function deletePaper(id) {
  return request.delete(`/teacher/papers/${id}`)
}

// ========== 阶段二：智能组卷 ==========

// 智能组卷预览
export function autoGeneratePreview(data) {
  return request.post('/teacher/papers/auto-generate/preview', data)
}

// 智能组卷确认保存
export function autoGenerateConfirm(data) {
  return request.post('/teacher/papers/auto-generate/confirm', data)
}
