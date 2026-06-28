import request from '@/utils/request'

// 获取违规记录列表（教师）
export function getViolationList(params) {
  return request.get('/teacher/violations', { params, silent: true })
}

// 获取考试违规统计（教师监考大屏）
export function getViolationStats(examId) {
  return request.get(`/teacher/violations/stats/${examId}`, { silent: true })
}
