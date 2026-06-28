<template>
  <!-- 已交卷蒙层 -->
  <div v-if="submitted" class="submitted-overlay">
    <el-result
      icon="success"
      title="交卷成功"
      :sub-title="'得分：' + submittedScore + '分，正在跳转...'"
    />
  </div>

  <!-- 加载中 -->
  <div v-else-if="loading" class="loading-page">
    <el-icon :size="40" class="is-loading"><Loading /></el-icon>
    <p style="margin-top: 16px; color: #909399;">正在加载试卷...</p>
  </div>

  <!-- 错误提示 -->
  <div v-else-if="errorMsg" class="error-page">
    <div class="error-container">
      <div class="error-icon">
        <el-icon :size="48" :color="getErrorIconColor()">
          <component :is="getErrorIcon()" />
        </el-icon>
      </div>
      <div class="error-content">
        <h2 class="error-title">{{ errorMsg }}</h2>
        <p class="error-desc">{{ getErrorDescription() }}</p>
        <div class="error-actions">
          <el-button type="primary" @click="handleErrorAction()">
            {{ getErrorButtonText() }}
          </el-button>
          <el-button type="info" plain @click="retryLoadExam()">
            <el-icon><Refresh /></el-icon>刷新重试
          </el-button>
          <el-button type="default" plain @click="showDebugPanel = !showDebugPanel" v-if="errorType === 'system'">
            <el-icon><Setting /></el-icon>查看详情
          </el-button>
        </div>
      </div>
    </div>
    
    <!-- 调试面板 -->
    <el-collapse-transition>
      <div v-if="showDebugPanel" class="debug-panel">
        <div class="debug-header">
          <span>调试信息</span>
          <el-button size="small" plain @click="copyDebugInfo">复制信息</el-button>
        </div>
        <pre class="debug-content">{{ JSON.stringify(debugInfo, null, 2) }}</pre>
      </div>
    </el-collapse-transition>
  </div>

  <!-- 考试主界面 -->
  <div v-else class="exam-page">
    <!-- 顶部信息栏 -->
    <div class="exam-header">
      <div class="exam-title">{{ examData.examTitle }}</div>
      <div class="exam-info">
        <span :class="['exam-timer', { warning: remainingSeconds < 300 }]">
          <el-icon><Clock /></el-icon>
          {{ formatTime(remainingSeconds) }}
        </span>
        <span class="switch-count" v-if="switchCount > 0 && enableAiMonitor">
          切屏: {{ switchCount }}/{{ maxSwitchCount }}
          <span v-if="switchCount >= maxSwitchCount" style="color: #f56c6c;"> (已达上限)</span>
        </span>
      </div>
      <div style="display: flex; align-items: center; gap: 12px;">
        <CameraMonitor
          v-if="enableAiMonitor"
          ref="cameraMonitorRef"
          :exam-id="examId"
          :active="true"
          @violation-summary="handleViolationSummary"
        />
        <el-button type="danger" @click="handleSubmit">
          <el-icon><CircleCheck /></el-icon>交卷
        </el-button>
      </div>
    </div>

    <!-- 切屏警告 -->
    <el-alert
      v-if="switchCount > 0 && enableAiMonitor"
      :title="`已检测到${switchCount}次切屏，最大允许${maxSwitchCount}次，超过将自动交卷`"
      type="warning"
      show-icon
      :closable="false"
      style="margin-bottom: 16px;"
    />

    <!-- AI检测异常警告 -->
    <el-alert
      v-if="aiWarnings.total > 0 && enableAiMonitor"
      :title="`AI监控已检测到${aiWarnings.total}次异常行为（${buildAiWarningDesc()}），请规范考试行为`"
      type="error"
      show-icon
      :closable="false"
      style="margin-bottom: 16px;"
    />

    <!-- 题目导航 -->
    <div class="question-nav">
      <div class="nav-label">答题卡 ({{ answeredCount }}/{{ questions.length }})</div>
      <div class="nav-grid">
        <div
          v-for="(q, index) in questions"
          :key="q.id"
          :class="['nav-item', {
            answered: isAnswered(q.id),
            current: currentQuestionIndex === index
          }]"
          @click="scrollToQuestion(index)"
        >
          {{ index + 1 }}
        </div>
      </div>
      <div class="nav-legend">
        <span><span class="legend-dot answered-dot"></span>已答</span>
        <span><span class="legend-dot current-dot"></span>当前</span>
        <span><span class="legend-dot"></span>未答</span>
      </div>
    </div>

    <!-- 题目列表 -->
    <div class="exam-paper">
      <div
        v-for="(question, index) in questions"
        :key="question.id"
        :id="'question-' + index"
        :ref="el => questionRefs[index] = el"
        class="question-item"
      >
        <div class="question-header">
          <span class="question-number">{{ index + 1 }}.</span>
          <el-tag size="small" :type="getQuestionTagType(question.type)">
            {{ question.typeDesc }}
          </el-tag>
          <span class="question-score">（{{ question.paperScore }}分）</span>
        </div>
        <div class="question-content">{{ question.content }}</div>

        <!-- 单选题 -->
        <div class="question-options" v-if="question.type === 1">
          <el-radio-group v-model="answers[question.id]" @change="handleAnswerChange(question.id)">
            <el-radio
              v-for="opt in getOptions(question)"
              :key="opt.key"
              :value="opt.key"
              class="option-item"
            >
              <span class="option-label">{{ opt.key }}.</span>
              <span>{{ opt.text }}</span>
            </el-radio>
          </el-radio-group>
        </div>

        <!-- 多选题 -->
        <div class="question-options" v-if="question.type === 2">
          <el-checkbox-group v-model="multiAnswers[question.id]" @change="handleMultiAnswerChange(question.id)">
            <el-checkbox
              v-for="opt in getOptions(question)"
              :key="opt.key"
              :value="opt.key"
              class="option-item"
            >
              <span class="option-label">{{ opt.key }}.</span>
              <span>{{ opt.text }}</span>
            </el-checkbox>
          </el-checkbox-group>
        </div>

        <!-- 判断题 -->
        <div class="question-options" v-if="question.type === 3">
          <el-radio-group v-model="answers[question.id]" @change="handleAnswerChange(question.id)">
            <el-radio value="true" class="option-item">
              <span class="option-label">正确</span>
              <span>{{ question.optionA || '正确' }}</span>
            </el-radio>
            <el-radio value="false" class="option-item">
              <span class="option-label">错误</span>
              <span>{{ question.optionB || '错误' }}</span>
            </el-radio>
          </el-radio-group>
        </div>

        <!-- 简答题 -->
        <div class="question-options" v-if="question.type === 4">
          <el-input
            v-model="answers[question.id]"
            type="textarea"
            :rows="5"
            placeholder="请输入你的答案..."
            @blur="handleAnswerChange(question.id)"
          />
        </div>
      </div>
    </div>

    <!-- 底部交卷按钮 -->
    <div class="exam-footer">
      <el-button type="primary" size="large" @click="handleSubmit" :loading="submitting">
        <el-icon><CircleCheck /></el-icon>交卷
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, ElCollapseTransition } from 'element-plus'
import { startExam, saveAnswer, submitExam, reportSwitchScreen, getDebugInfo } from '@/api/exam'
import CameraMonitor from '@/components/exam/CameraMonitor.vue'

// 图标导入
import {
  Connection, Lock, Document, Calendar, Warning, Refresh, Setting,
  CircleCheck, Loading, Clock
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const examId = parseInt(route.params.examId)
const examToken = route.query.examToken || ''

// ─── 页面状态 ───
const loading = ref(true)
const submitting = ref(false)
const submitted = ref(false)
const submittedScore = ref(0)
const errorMsg = ref('')
const errorType = ref('') // 错误类型: 'network'|'auth'|'data'|'system'|'exam'
const showDebugPanel = ref(false) // 调试面板开关
const debugInfo = ref({}) // 调试信息
const examData = ref(null)
const questions = ref([])
const answers = reactive({})
const multiAnswers = reactive({})
const remainingSeconds = ref(0)
const switchCount = ref(0)
const maxSwitchCount = ref(3)
const enableAiMonitor = ref(true)
const currentQuestionIndex = ref(0)
const questionRefs = ref([])
const cameraMonitorRef = ref(null)
const aiWarnings = ref({
  switch_screen: 0, no_face: 0, multi_face: 0,
  look_away: 0, forbidden_item: 0, keyboard_violation: 0, total: 0
})

let timer = null

// ─── 计算属性 ───
const answeredCount = computed(() => {
  return questions.value.filter(q => isAnswered(q.id)).length
})

function isAnswered(qId) {
  const a = answers[qId]
  return a !== undefined && a !== null && a !== ''
}

// ─── 生命周期 ───
onMounted(async () => {
  // 不在此处注册防作弊！等待考试数据加载成功后再注册，
  // 避免页面初始化期间浏览器触发 visibilitychange/blur 导致误报切屏
  await loadExamData()
})

async function loadExamData() {
  try {
    console.log('[TakeExam] 开始加载考试数据, examId=', examId, 'examToken=', examToken)
    const res = await startExam(examId, examToken)
    console.log('[TakeExam] API响应:', res.data)

    // 数据校验
    const data = res.data
    if (!data) {
      setError('data', '服务器未返回考试数据', { examId, response: res })
      loading.value = false
      return
    }

    // 检查题目列表
    if (!data.questions || data.questions.length === 0) {
      setError('data', '试卷没有题目数据，请联系老师检查试卷设置', { examId, hasQuestions: false })
      loading.value = false
      return
    }

    // 检查关键字段（注意：后端返回 recordId 而非 examId）
    if (!data.recordId || !data.examTitle) {
      console.error('[TakeExam] 关键字段缺失:', {
        hasRecordId: !!data.recordId,
        hasExamTitle: !!data.examTitle,
        dataKeys: Object.keys(data),
        fullData: JSON.stringify(data).substring(0, 500)
      })
      setError('data', '考试信息不完整，请联系管理员', { examId, dataKeys: Object.keys(data) })
      loading.value = false
      return
    }

    // 设置考试数据
    examData.value = data
    questions.value = data.questions
    remainingSeconds.value = data.remainingTime || 0
    switchCount.value = data.switchCount || 0
    maxSwitchCount.value = data.maxSwitchCount || 3
    enableAiMonitor.value = data.enableAiMonitor !== 0

    // 收集调试信息
    debugInfo.value = {
      examId,
      examTitle: data.examTitle,
      examToken,
      questionsCount: data.questions.length,
      remainingTime: data.remainingTime,
      switchCount: data.switchCount,
      maxSwitchCount: data.maxSwitchCount,
      enableAiMonitor: data.enableAiMonitor,
      hasAnswers: !!data.answers,
      answersCount: Object.keys(data.answers || {}).length,
      loadedAt: new Date().toISOString(),
      userAgent: navigator.userAgent,
      screenResolution: `${window.screen.width}x${window.screen.height}`
    }

    // 恢复已保存的答案
    if (data.answers) {
      Object.entries(data.answers).forEach(([qid, answer]) => {
        const q = questions.value.find(q => q.id === parseInt(qid))
        if (q && answer) {
          if (q.type === 2) {
            // 多选：答案格式 "A,C,D" → 数组
            multiAnswers[q.id] = answer.split(',').map(s => s.trim()).filter(Boolean).sort()
            answers[q.id] = multiAnswers[q.id].join(',')
          } else {
            answers[q.id] = answer
          }
        }
      })
    }

    loading.value = false
    console.log('[TakeExam] 加载完成，题目数:', questions.value.length, '已恢复答案数:', Object.keys(data.answers || {}).length)

    // 考试数据加载成功后，再注册防作弊监听（避免初始化期间误报切屏）
    registerAntiCheat()

    // 启动倒计时
    startTimer()

  } catch (e) {
    loading.value = false
    console.error('[TakeExam] 加载失败:', e)
    
    // 错误分类
    const error = e
    if (error.name === 'NetworkError' || !navigator.onLine) {
      setError('network', '网络连接失败，请检查网络后重试', { error: error.message })
    } else if (error.response?.status === 401 || error.response?.status === 403) {
      setError('auth', '身份验证失败，请重新登录后重试', { status: error.response.status })
    } else if (error.response?.status === 400) {
      setError('exam', '考试信息无效或已过期', { status: error.response.status })
    } else if (error.response?.status === 404) {
      setError('exam', '考试不存在或已被删除', { status: error.response.status })
    } else if (error.response?.status >= 500) {
      setError('system', '服务器内部错误，请稍后重试', { status: error.response.status })
      // 服务器错误时自动获取调试信息
      fetchServerDebugInfo()
    } else {
      setError('system', `加载考试数据失败: ${error.message || '未知错误'}`, { error: error.message })
      // 未知错误时也获取调试信息
      fetchServerDebugInfo()
    }
  }
}

onBeforeUnmount(() => {
  stopTimer()
  removeAntiCheat()
})

// ─── 倒计时 ───
function startTimer() {
  stopTimer()
  timer = setInterval(() => {
    if (remainingSeconds.value <= 0 || submitting.value) return
    remainingSeconds.value--
    if (remainingSeconds.value <= 0) {
      stopTimer()
      ElMessage.warning('考试时间到，系统将自动交卷')
      doSubmit(true)
    }
  }, 1000)
}

function stopTimer() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

// ─── 答题逻辑 ───

function getOptions(question) {
  const opts = []
  if (question.optionA) opts.push({ key: 'A', text: question.optionA })
  if (question.optionB) opts.push({ key: 'B', text: question.optionB })
  if (question.optionC) opts.push({ key: 'C', text: question.optionC })
  if (question.optionD) opts.push({ key: 'D', text: question.optionD })
  return opts
}

function getQuestionTagType(type) {
  switch (type) {
    case 1: return ''         // 单选 - 默认蓝色
    case 2: return 'warning'  // 多选 - 橙色
    case 3: return 'success'  // 判断 - 绿色
    case 4: return 'info'     // 简答 - 灰色
    default: return ''
  }
}

async function handleAnswerChange(questionId) {
  await doSaveAnswer(questionId)
}

function handleMultiAnswerChange(questionId) {
  const selected = (multiAnswers[questionId] || []).sort().join(',')
  answers[questionId] = selected
  doSaveAnswer(questionId)
}

async function doSaveAnswer(questionId) {
  try {
    await saveAnswer(examId, questionId, answers[questionId] || '')
  } catch (e) {
    console.warn('[TakeExam] 保存答案失败:', e.message)
  }
}

// ─── 错误处理函数 ───
function setError(type, message, details = {}) {
  errorType.value = type
  errorMsg.value = message
  console.error(`[TakeExam Error] ${type}: ${message}`, details)
  
  // 记录到调试信息
  debugInfo.value.lastError = {
    type,
    message,
    details,
    timestamp: new Date().toISOString()
  }
}

async function doSubmit(isAuto) {
  if (submitting.value) return
  submitting.value = true

  try {
    const answerMap = {}
    questions.value.forEach(q => {
      if (answers[q.id]) {
        answerMap[q.id] = answers[q.id]
      }
    })

    const res = await submitExam(examId, answerMap)
    const record = res.data

    submittedScore.value = record.score || 0
    if (isAuto) {
      ElMessage.warning(`考试已自动提交，得分：${submittedScore.value}分`)
    } else {
      ElMessage.success(`交卷成功，得分：${submittedScore.value}分`)
    }

    // 锁定页面
    submitted.value = true
    stopTimer()
    removeAntiCheat()

    setTimeout(() => {
      // 返回考试列表页（而非成绩页），保持用户操作连贯性
      router.push('/student/exams')
    }, 1500)
  } catch (e) {
    console.error('[TakeExam] 交卷失败:', e)
    if (e.message && !e.message.includes('已提交')) {
      ElMessage.error(e.message || '提交失败')
    }
    submitting.value = false
  }
}

// ─── 交卷 ───

async function handleSubmit() {
  const unansweredCount = questions.value.filter(q => !isAnswered(q.id)).length
  let message = '确定要提交试卷吗？'
  if (unansweredCount > 0) {
    message = `还有 ${unansweredCount} 题未作答，确定要提交试卷吗？`
  }

  try {
    await ElMessageBox.confirm(message, '交卷确认', {
      type: 'warning',
      confirmButtonText: '确定交卷',
      cancelButtonText: '继续答题'
    })
    doSubmit(false)
  } catch { /* 用户取消 */ }
}

function formatTime(seconds) {
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  const s = seconds % 60
  if (h > 0) {
    return `${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`
  }
  return `${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`
}

// ─── 错误处理辅助函数 ───
function getErrorIcon() {
  switch (errorType.value) {
    case 'network': return 'Connection'
    case 'auth': return 'Lock'
    case 'data': return 'Document'
    case 'exam': return 'Calendar'
    case 'system': return 'Warning'
    default: return 'Warning'
  }
}

function getErrorIconColor() {
  switch (errorType.value) {
    case 'network': return '#E6A23C'
    case 'auth': return '#F56C6C'
    case 'data': return '#F56C6C'
    case 'exam': return '#909399'
    case 'system': return '#F56C6C'
    default: return '#F56C6C'
  }
}

function getErrorDescription() {
  switch (errorType.value) {
    case 'network':
      return '请检查您的网络连接，并确保服务器正常访问。如果使用VPN或代理，请检查其配置。'
    case 'auth':
      return '您的登录已失效或权限不足，请重新登录后访问。如果问题持续，请联系管理员。'
    case 'data':
      return '服务器返回的数据格式不正确，可能是考试配置问题或服务器故障。'
    case 'exam':
      return '该考试可能已结束、未开始、被删除或您没有权限参加。请确认考试信息。'
    case 'system':
      return '服务器遇到技术问题，请稍后再试。如果问题持续，请截屏联系技术支持。'
    default:
      return '发生未知错误，请刷新页面或联系技术支持。'
  }
}

function getErrorButtonText() {
  switch (errorType.value) {
    case 'auth':
      return '重新登录'
    case 'exam':
    case 'data':
    case 'network':
      return '返回考试列表'
    case 'system':
      return '联系技术支持'
    default:
      return '返回首页'
  }
}

async function handleErrorAction() {
  switch (errorType.value) {
    case 'auth':
      router.push('/login')
      break
    case 'exam':
    case 'data':
    case 'network':
      router.push('/student/exams')
      break
    case 'system':
      // 可集成联系支持的功能
      ElMessage.info('请发送调试信息给技术支持团队')
      copyDebugInfo()
      break
    default:
      router.push('/')
      break
  }
}

function retryLoadExam() {
  loading.value = true
  errorMsg.value = ''
  errorType.value = ''
  
  // 延迟一小段时间以显示加载状态
  setTimeout(() => {
    loadExamData()
  }, 500)
}

async function copyDebugInfo() {
  const text = `考试页面错误信息：
错误类型：${errorType.value}
错误信息：${errorMsg.value}
时间戳：${new Date().toISOString()}
User-Agent：${navigator.userAgent}
页面URL：${window.location.href}

详细信息：
${JSON.stringify(debugInfo.value, null, 2)}`
  
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('调试信息已复制到剪贴板')
  } catch (e) {
    console.warn('复制失败:', e)
    ElMessage.warning('复制失败，请手动复制控制台信息')
  }
}

// 获取服务器端调试信息
async function fetchServerDebugInfo() {
  try {
    const response = await getDebugInfo()
    if (response.data) {
      // 合并到现有调试信息
      debugInfo.value.serverDebugInfo = response.data
      debugInfo.value.serverFetchedAt = new Date().toISOString()
      console.log('[TakeExam] 服务器调试信息已获取:', response.data)
      
      if (showDebugPanel.value) {
        console.log('[TakeExam] 调试面板已更新，点击"查看详情"可查看服务器端信息')
      }
      return true
    }
  } catch (error) {
    console.warn('[TakeExam] 获取服务器调试信息失败:', error.message)
    debugInfo.value.serverDebugError = error.message
  }
  return false
}

function scrollToQuestion(index) {
  currentQuestionIndex.value = index
  const el = questionRefs.value[index]
  if (el) {
    el.scrollIntoView({ behavior: 'smooth', block: 'center' })
  }
}

// ==================== 防作弊 ====================

function handleViolationSummary(summary) {
  aiWarnings.value = { ...summary }
}

function buildAiWarningDesc() {
  const parts = []
  if (aiWarnings.value.no_face > 0) parts.push(`人脸消失${aiWarnings.value.no_face}次`)
  if (aiWarnings.value.multi_face > 0) parts.push(`多人脸${aiWarnings.value.multi_face}次`)
  if (aiWarnings.value.look_away > 0) parts.push(`东张西望${aiWarnings.value.look_away}次`)
  if (aiWarnings.value.forbidden_item > 0) parts.push(`疑似违禁物品${aiWarnings.value.forbidden_item}次`)
  if (aiWarnings.value.keyboard_violation > 0) parts.push(`违规操作${aiWarnings.value.keyboard_violation}次`)
  return parts.join('、')
}

// ── 切屏检测 ──
let lastSwitchTime = 0
let isReportingSwitch = false
const SWITCH_THROTTLE_MS = 1000
let switchEventSource = ''
let antiCheatRegisteredAt = 0  // 防作弊注册时间戳

function registerAntiCheat() {
  // 记录注册时间，用于初始化冷却期（防止注册后短时间内浏览器事件误报）
  antiCheatRegisteredAt = Date.now()
  document.addEventListener('visibilitychange', handleVisibilityChange)
  window.addEventListener('blur', handleWindowBlur)
  window.addEventListener('focus', handleWindowFocus)
  document.addEventListener('fullscreenchange', handleFullscreenChange)
  document.addEventListener('webkitfullscreenchange', handleFullscreenChange)
  document.addEventListener('keydown', handleKeyDown)
  document.addEventListener('contextmenu', preventDefault)
  document.addEventListener('copy', handleCopyBlock)
  document.addEventListener('cut', preventDefault)
  document.addEventListener('paste', handlePasteBlock)
  startZoomDetection()
  startDevToolsDetection()
}

function removeAntiCheat() {
  document.removeEventListener('visibilitychange', handleVisibilityChange)
  window.removeEventListener('blur', handleWindowBlur)
  window.removeEventListener('focus', handleWindowFocus)
  document.removeEventListener('fullscreenchange', handleFullscreenChange)
  document.removeEventListener('webkitfullscreenchange', handleFullscreenChange)
  document.removeEventListener('keydown', handleKeyDown)
  document.removeEventListener('contextmenu', preventDefault)
  document.removeEventListener('copy', handleCopyBlock)
  document.removeEventListener('cut', preventDefault)
  document.removeEventListener('paste', handlePasteBlock)
  stopZoomDetection()
  stopDevToolsDetection()
}

function preventDefault(e) {
  e.preventDefault()
  return false
}

function handleKeyDown(e) {
  const key = e.key.toUpperCase()
  if (key === 'F12' ||
      (e.ctrlKey && e.shiftKey && ['I', 'J', 'C'].includes(key)) ||
      (e.ctrlKey && key === 'U') ||
      (e.ctrlKey && key === 'S') ||
      (e.ctrlKey && key === 'P')) {
    e.preventDefault()
    uploadKeyboardViolate(e, '禁止的快捷键: ' + formatKeyCombo(e))
    return
  }
  if (key === 'F11') {
    e.preventDefault()
    uploadKeyboardViolate(e, '尝试F11全屏操作')
    return
  }
  if (key === 'ESCAPE' && enableAiMonitor.value) {
    uploadKeyboardViolate(e, 'Escape键(可能退出全屏)')
  }
  if (e.altKey && key === 'F4') {
    e.preventDefault()
  }
}

function formatKeyCombo(e) {
  const parts = []
  if (e.ctrlKey) parts.push('Ctrl')
  if (e.shiftKey) parts.push('Shift')
  if (e.altKey) parts.push('Alt')
  parts.push(e.key)
  return parts.join('+')
}

function handleCopyBlock(e) {
  e.preventDefault()
  uploadKeyboardViolate(e, '复制操作(疑似抄袭)')
  ElMessage.warning('考试期间禁止复制内容')
}

function handlePasteBlock(e) {
  e.preventDefault()
  ElMessage.warning('考试期间禁止粘贴内容')
}

function handleVisibilityChange() {
  if (!enableAiMonitor.value || submitted.value) return
  if (document.hidden) {
    switchEventSource = 'visibility'
    reportSwitchEnhanced('页面隐藏(Tab切换或最小化)')
  }
}

function handleWindowBlur() {
  if (!enableAiMonitor.value || submitted.value) return
  if (switchEventSource === 'visibility' && Date.now() - lastSwitchTime < 500) return
  switchEventSource = 'blur'
  reportSwitchEnhanced('窗口失焦')
}

function handleWindowFocus() {
  switchEventSource = ''
}

function handleFullscreenChange() {
  if (!enableAiMonitor.value || submitted.value) return
  if (!document.fullscreenElement && !document.webkitFullscreenElement) {
    uploadKeyboardViolate(null, '退出全屏模式')
    reportSwitchEnhanced('退出全屏')
  }
}

function reportSwitchEnhanced(reason) {
  if (isReportingSwitch) return
  const now = Date.now()
  // 初始化冷却期：防作弊注册后3秒内的事件全部忽略（浏览器初始化可能触发误报）
  if (now - antiCheatRegisteredAt < 3000) return
  if (now - lastSwitchTime < SWITCH_THROTTLE_MS) return
  lastSwitchTime = now
  isReportingSwitch = true

  if (cameraMonitorRef.value) {
    cameraMonitorRef.value.forceCapture('switch_screen', reason)
  }

  reportSwitchScreen(examId).then(res => {
    if (res.data) {
      switchCount.value = res.data.switchCount
      if (res.data.forceSubmit) {
        ElMessage.error('切屏次数已达上限，系统将自动交卷！')
        doSubmit(true)
      }
    }
  }).catch(() => {
  }).finally(() => {
    setTimeout(() => { isReportingSwitch = false }, 500)
  })
}

function uploadKeyboardViolate(e, description) {
  if (!enableAiMonitor.value) return
  if (cameraMonitorRef.value) {
    cameraMonitorRef.value.forceCapture('keyboard_violation', description)
  }
  aiWarnings.value.total++
  if (aiWarnings.value.keyboard_violation === undefined) aiWarnings.value.keyboard_violation = 0
  aiWarnings.value.keyboard_violation++
}

// ── 缩放检测 ──
let zoomCheckTimer = null
let lastZoomLevel = 100

function startZoomDetection() {
  zoomCheckTimer = setInterval(() => {
    if (submitted.value || !enableAiMonitor.value) return
    const zoom = detectZoomLevel()
    if (Math.abs(zoom - lastZoomLevel) > 5) {
      lastZoomLevel = zoom
      if (zoom !== 100) {
        uploadKeyboardViolate(null, '浏览器缩放至' + Math.round(zoom) + '%(疑似查看远处屏幕)')
      }
    }
  }, 3000)
}

function stopZoomDetection() {
  if (zoomCheckTimer) clearInterval(zoomCheckTimer)
}

function detectZoomLevel() {
  let ratio = window.devicePixelRatio || 1
  if (window.outerWidth) {
    ratio = window.outerWidth / window.innerWidth * (window.devicePixelRatio || 1)
  }
  return Math.round(ratio * 100)
}

// ── DevTools 检测 ──
let devtoolsCheckTimer = null
let devtoolsWarned = false

function startDevToolsDetection() {
  devtoolsCheckTimer = setInterval(() => {
    if (submitted.value || !enableAiMonitor.value) return
    const threshold = 160
    if (window.outerWidth - window.innerWidth > threshold ||
        window.outerHeight - window.innerHeight > threshold) {
      if (!devtoolsWarned) {
        devtoolsWarned = true
        uploadKeyboardViolate(null, '疑似打开开发者工具')
        ElMessage.error('检测到异常操作，请规范考试行为！')
      }
    } else {
      devtoolsWarned = false
    }
  }, 2000)
}

function stopDevToolsDetection() {
  if (devtoolsCheckTimer) clearInterval(devtoolsCheckTimer)
}
</script>

<style scoped>
.submitted-overlay {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: #f5f7fa;
}

.loading-page {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: #f5f7fa;
}

.error-page {
  min-height: 100vh;
  background: #f5f7fa;
  padding: 60px 20px;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.error-container {
  max-width: 600px;
  background: #fff;
  border-radius: 16px;
  padding: 48px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.08);
  text-align: center;
  margin-bottom: 24px;
}

.error-icon {
  margin-bottom: 32px;
}

.error-content {
  text-align: center;
}

.error-title {
  font-size: 20px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 16px;
  line-height: 1.5;
}

.error-desc {
  font-size: 15px;
  color: #606266;
  margin-bottom: 32px;
  line-height: 1.6;
}

.error-actions {
  display: flex;
  gap: 12px;
  justify-content: center;
  flex-wrap: wrap;
}

.debug-panel {
  max-width: 800px;
  background: #fff;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.debug-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  background: #f5f7fa;
  border-bottom: 1px solid #e4e7ed;
  font-weight: 600;
  color: #303133;
}

.debug-content {
  padding: 20px;
  margin: 0;
  font-family: 'Monaco', 'Menlo', 'Consolas', monospace;
  font-size: 12px;
  line-height: 1.5;
  color: #333;
  background: #fafafa;
  max-height: 400px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
}

.exam-page {
  max-width: 960px;
  margin: 0 auto;
  padding: 0 16px;
}

.exam-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  margin-bottom: 16px;
  position: sticky;
  top: 0;
  z-index: 100;
}

.exam-title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.exam-info {
  display: flex;
  align-items: center;
  gap: 16px;
}

.exam-timer {
  font-size: 20px;
  font-weight: bold;
  color: #409eff;
  display: flex;
  align-items: center;
  gap: 4px;
}

.exam-timer.warning {
  color: #f56c6c;
  animation: blink 1s infinite;
}

.switch-count {
  font-size: 13px;
  color: #e6a23c;
}

.question-nav {
  background: #fff;
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.nav-label {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 12px;
  color: #606266;
}

.nav-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.nav-item {
  width: 36px;
  height: 36px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  font-size: 13px;
  color: #606266;
  transition: all 0.2s;
}

.nav-item:hover {
  border-color: #409eff;
  color: #409eff;
}

.nav-item.answered {
  background: #409eff;
  border-color: #409eff;
  color: #fff;
}

.nav-item.current {
  border-color: #e6a23c;
  box-shadow: 0 0 0 2px rgba(230, 162, 60, 0.3);
}

.nav-legend {
  display: flex;
  gap: 16px;
  font-size: 12px;
  color: #909399;
}

.legend-dot {
  display: inline-block;
  width: 12px;
  height: 12px;
  border: 1px solid #dcdfe6;
  border-radius: 2px;
  margin-right: 4px;
  vertical-align: middle;
}

.legend-dot.answered-dot {
  background: #409eff;
  border-color: #409eff;
}

.legend-dot.current-dot {
  border-color: #e6a23c;
  box-shadow: 0 0 0 1px rgba(230, 162, 60, 0.3);
}

.question-item {
  background: #fff;
  border-radius: 8px;
  padding: 24px;
  margin-bottom: 16px;
  border: 1px solid #e4e7ed;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
}

.question-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.question-number {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.question-score {
  font-size: 13px;
  color: #909399;
}

.question-content {
  font-size: 15px;
  margin-bottom: 20px;
  line-height: 1.6;
  color: #303133;
}

.question-options {
  padding-left: 28px;
}

.option-item {
  display: flex;
  align-items: flex-start;
  margin-bottom: 12px;
  padding: 10px 16px;
  border-radius: 6px;
  transition: background 0.2s;
}

.option-item:hover {
  background: #f5f7fa;
}

.option-label {
  font-weight: 500;
  margin-right: 8px;
  min-width: 20px;
}

.exam-footer {
  text-align: center;
  padding: 24px 0 40px;
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}
</style>
