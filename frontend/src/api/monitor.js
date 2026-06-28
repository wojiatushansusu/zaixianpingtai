import request from '@/utils/request'

// 获取在线学生统计
export function getOnlineStats(examId) {
  return request.get(`/teacher/monitor/online-stats/${examId}`, { silent: true })
}

// 获取在线学生列表
export function getOnlineStudents(examId) {
  return request.get(`/teacher/monitor/online-students/${examId}`, { silent: true })
}

// 获取视频证据列表
export function getVideoEvidence(examId, studentId) {
  return request.get(`/teacher/monitor/evidence/${examId}`, {
    params: studentId ? { studentId } : {}
  })
}

// 获取待批改列表
export function getUngradedAnswers(examId) {
  return request.get(`/teacher/analysis/ungraded/${examId}`)
}

// 批改主观题
export function gradeAnswer(answerId, score, comment) {
  return request.post('/teacher/analysis/grade', { answerId, score, comment })
}

// ========== 学生端断网续传 ==========

// 上传离线缓存的答题记录
export function uploadOfflineAnswers(examId, answers) {
  return request.post(`/student/exams/${examId}/offline-sync`, { answers })
}

// 上报断网状态
export function reportDisconnect(examId) {
  return request.post(`/student/exams/${examId}/disconnect`)
}

// 上报重连
export function reportReconnect(examId) {
  return request.post(`/student/exams/${examId}/reconnect`)
}
