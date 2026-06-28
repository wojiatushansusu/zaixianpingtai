import request from '@/utils/request'

// 获取题目列表
export function getQuestionList(params) {
  return request.get('/teacher/questions', { params })
}

// 获取所有题目（不分页）
export function getAllQuestions(params) {
  return request.get('/teacher/questions/all', { params })
}

// 获取题目详情
export function getQuestion(id) {
  return request.get(`/teacher/questions/${id}`)
}

// 创建题目
export function createQuestion(data) {
  return request.post('/teacher/questions', data)
}

// 更新题目
export function updateQuestion(id, data) {
  return request.put(`/teacher/questions/${id}`, data)
}

// 删除题目
export function deleteQuestion(id) {
  return request.delete(`/teacher/questions/${id}`)
}

// 批量删除题目
export function batchDeleteQuestions(ids) {
  return request.delete('/teacher/questions/batch', { data: ids })
}

// ========== 阶段二：标签管理 ==========

// 获取所有标签
export function getTagList() {
  return request.get('/teacher/questions/tags')
}

// 创建标签
export function createTag(name) {
  return request.post('/teacher/questions/tags', { name })
}

// 删除标签
export function deleteTag(id) {
  return request.delete(`/teacher/questions/tags/${id}`)
}

// 批量更新题目标签
export function batchUpdateQuestionTags(questionIds, tagIds) {
  return request.put('/teacher/questions/batch-tags', { questionIds, tagIds })
}

// ========== 阶段二：Excel导入 ==========

// 导入题目
export function importQuestions(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/teacher/questions/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000
  })
}

// 导入预检 —— 只校验不写入数据库
export function previewImport(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/teacher/questions/import/preview', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000
  })
}

// 确认导入 —— 带行过滤
export function confirmImport(file, includeRows) {
  const formData = new FormData()
  formData.append('file', file)
  if (includeRows && includeRows.length > 0) {
    formData.append('includeRows', includeRows.join(','))
  }
  return request.post('/teacher/questions/import/confirm', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000
  })
}

// 下载导入模板
export function downloadImportTemplate() {
  return request.get('/teacher/questions/import-template', {
    responseType: 'blob'
  })
}

// ========== AI 智能分类 ==========

// AI分析题目（返回推荐标签+难度）
export function aiClassifyQuestions(questionIds) {
  return request.post('/teacher/questions/ai-classify', { questionIds }, {
    timeout: 360000 // 6分钟，略大于后端HEAVY超时(300s)以容纳网络延迟
  })
}

// 应用AI分类结果到数据库
export function applyAiClassifyResult(items) {
  return request.post('/teacher/questions/ai-classify/apply', { items })
}
