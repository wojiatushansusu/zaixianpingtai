import request from '@/utils/request'

// ========== AI自助考试 ==========

// 创建AI自助考试（先创建记录）
export function createSelfStudyExam(data) {
  return request.post('/student/self-study/create', data)
}

// 复习路径各阶段的测验结果
export function getStageProgress() {
  return request.get('/student/self-study/stage-progress')
}

// 依据某次考试的错题创建专项巩固卷（只建记录，生成另调 generate）
export function createFromWrongQuestions(data) {
  return request.post('/student/self-study/from-wrong-questions', data)
}

// 触发AI生成题目（同步调用，可能耗时较长）
export function generateSelfStudyExam(examId) {
  return request.post(`/student/self-study/${examId}/generate`, {}, {
    timeout: 300000 // AI生成可能需要5分钟
  })
}

// 获取生成状态（轮询用）
export function getGenerateStatus(examId) {
  return request.get(`/student/self-study/${examId}/status`)
}

// 获取我参与的自助考试列表
export function getMySelfStudyExams() {
  return request.get('/student/self-study/list')
}

// 获取自助考试详情
export function getSelfStudyExamDetail(examId) {
  return request.get(`/student/self-study/${examId}`)
}

// 获取考试码
export function getExamCode(examId) {
  return request.get(`/student/self-study/${examId}/exam-code`)
}

// 发布考试
export function publishSelfStudyExam(examId) {
  return request.post(`/student/self-study/${examId}/publish`)
}

// 通过考试码预览
export function previewByExamCode(examCode) {
  return request.get('/student/self-study/preview', { params: { examCode } })
}

// 通过考试码加入考试
export function joinByExamCode(examCode) {
  return request.post('/student/self-study/join', { examCode })
}

// 自助重考
export function selfRetake(examId) {
  return request.post(`/student/self-study/${examId}/retake`)
}

// 获取历史成绩
export function getExamScores(examId) {
  return request.get(`/student/self-study/${examId}/scores`)
}

// 查看试卷内容（不含答案）
export function getExamPaper(examId) {
  return request.get(`/student/self-study/${examId}/paper`)
}

// 查看某次答卷详情（含学生答案、正确答案、解析）
export function getExamPaperWithAnswers(examId, recordId) {
  return request.get(`/student/self-study/${examId}/paper/${recordId}`)
}

// 重新生成试卷（修改prompt/duration后清除旧数据重新调用AI生成）
export function regenerateSelfStudyExam(examId, data) {
  return request.post(`/student/self-study/${examId}/regenerate`, data, {
    timeout: 300000 // AI生成可能需要5分钟
  })
}

