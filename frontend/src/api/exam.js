import request from '@/utils/request'

// ========== 教师端 ==========

// 获取考试列表
export function getExamList(params) {
  return request.get('/teacher/exams', { params })
}

// 获取考试详情
export function getExam(id, config) {
  return request.get(`/teacher/exams/${id}`, config)
}

// 创建考试
export function createExam(data) {
  return request.post('/teacher/exams', data)
}

// 更新考试
export function updateExam(id, data) {
  return request.put(`/teacher/exams/${id}`, data)
}

// 删除考试
export function deleteExam(id) {
  return request.delete(`/teacher/exams/${id}`)
}

// 删除考试记录（含所有关联数据）
export function deleteExamWithRecords(id) {
  return request.delete(`/teacher/exams/${id}/records`)
}

// 获取考试白名单
export function getExamWhitelist(id) {
  return request.get(`/teacher/exams/${id}/whitelist`)
}

// 获取可添加的学生
export function getAvailableStudents(id) {
  return request.get(`/teacher/exams/${id}/available-students`)
}

// 获取考试成绩列表
export function getExamGrades(id) {
  return request.get(`/teacher/exams/${id}/grades`)
}

// 获取考试成绩统计
export function getExamStatistics(id) {
  return request.get(`/teacher/exams/${id}/statistics`)
}


// ========== 学生端 ==========

// 获取我的考试列表
export function getMyExams() {
  return request.get('/student/exams')
}

// 获取考试详情
export function getExamDetail(examId) {
  return request.get(`/student/exams/${examId}`)
}

// 开始考试
export function startExam(examId, examToken) {
  return request.post(`/student/exams/${examId}/start`, null, {
    params: examToken ? { examToken } : {}
  })
}

// 保存答案
export function saveAnswer(examId, questionId, answer) {
  return request.post(`/student/exams/${examId}/save-answer`, { questionId, answer })
}

// 提交考试
export function submitExam(examId, answers) {
  return request.post(`/student/exams/${examId}/submit`, { answers })
}

// 上报切屏
export function reportSwitchScreen(examId) {
  return request.post(`/student/exams/${examId}/switch-screen`)
}

// 上传违规截图
export function uploadViolation(examId, file, type, description, createRecord = true) {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('type', type)
  if (description) formData.append('description', description)
  formData.append('createRecord', String(createRecord))
  return request.post(`/student/exams/${examId}/violation`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

// 获取我的成绩
export function getMyGrades() {
  return request.get('/student/grades')
}

// 查看试卷详情（已交卷后查看，不含答案）
export function getExamPaper(examId) {
  return request.get(`/student/exams/${examId}/paper`)
}


// 获取学生在某考试的所有历史答卷记录（历史成绩弹窗用）
export function getMyExamRecords(examId) {
  return request.get(`/student/exams/${examId}/records`)
}

// 获取调试信息（排查考试相关问题）
export function getDebugInfo(repair = false) {
  const params = {}
  if (repair) params.repair = 'true'
  return request.get('/student/exams/debug-info', { params })
}
