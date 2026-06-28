import request from '@/utils/request'

// ========== AI助手 ==========

// 检查AI助手状态
export function getAiStatus() {
  return request.get('/ai-assistant/status', { silent: true })
}

// AI对话
export function aiChat(data) {
  return request.post('/ai-assistant/chat', data, {
    timeout: 200000 // 本地模型可能较慢
  })
}

// AI指令解析（教师/管理员专用）
// 将自然语言转换为结构化操作指令
export function aiCommand(data) {
  return request.post('/ai-assistant/command', data, {
    timeout: 60000 // AI意图识别可能需要较长时间
  })
}
