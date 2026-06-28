import request from '@/utils/request'

// ========== 错题本 ==========

// 获取错题列表
export function getWrongQuestions(questionType) {
  return request.get('/student/wrong-questions', {
    params: questionType ? { questionType } : {}
  })
}

// 获取错题统计
export function getWrongQuestionStats() {
  return request.get('/student/wrong-questions/stats')
}

// 获取错题按考试分组列表
export function getWrongQuestionsGrouped() {
  return request.get('/student/wrong-questions/grouped')
}

// 获取某次考试的错题详情
export function getWrongQuestionsByExamRecord(examRecordId) {
  return request.get(`/student/wrong-questions/exam-record/${examRecordId}`)
}

// ========== 错题练习 ==========

// 创建错题练习
export function createPractice(data) {
  return request.post('/student/wrong-questions/practice', data)
}

// 获取练习详情
export function getPracticeDetail(practiceId) {
  return request.get(`/student/wrong-questions/practice/${practiceId}`)
}

// 保存练习答案
export function savePracticeAnswer(practiceId, questionId, answer) {
  return request.post(`/student/wrong-questions/practice/${practiceId}/save-answer`, {
    questionId,
    answer
  })
}

// 提交练习
export function submitPractice(practiceId, answers) {
  return request.post(`/student/wrong-questions/practice/${practiceId}/submit`, answers)
}

// 获取练习结果详情
export function getPracticeResult(practiceId) {
  return request.get(`/student/wrong-questions/practice/${practiceId}/result`)
}

// 获取练习历史记录
export function getPracticeHistory(config) {
  return request.get('/student/wrong-questions/practice/history', config)
}

// ========== AI讲解 ==========

// 获取AI深度讲解
export function getAiExplanation(questionData) {
  return request.post('/student/wrong-questions/ai-explain', questionData, {
    timeout: 35000 // 30秒后端超时 + 5秒余量
  })
}
