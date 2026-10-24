<template>
  <div class="self-study-exam">
    <!-- 页面标题 -->
    <div class="page-header">
      <h2>AI 出卷</h2>
      <div class="header-actions">
        <el-button @click="showJoinDialog = true" plain>
          <el-icon><Connection /></el-icon>通过考试码加入
        </el-button>
        <el-button type="primary" @click="showCreateDialog = true">
          <el-icon><MagicStick /></el-icon>AI创建考试
        </el-button>
      </div>
    </div>

    <!-- Tab筛选 -->
    <div class="exam-tabs">
      <div
        v-for="tab in tabs"
        :key="tab.key"
        class="exam-tab"
        :class="{ active: activeTab === tab.key }"
        @click="activeTab = tab.key"
      >
        {{ tab.label }}
        <span v-if="tab.count" class="tab-count">{{ tab.count }}</span>
      </div>
    </div>

    <!-- 空状态 -->
    <div v-if="!loading && filteredExams.length === 0" class="empty-state">
      <div class="empty-icon">
        <el-icon :size="64"><MagicStick /></el-icon>
      </div>
      <p v-if="activeTab === 'all'">还没有出过卷，点击右上角让 AI 为你生成一份</p>
      <p v-else>该分类下暂无考试</p>
    </div>

    <!-- 考试卡片列表 -->
    <div class="exam-grid" v-else>
      <div
        v-for="(exam, index) in filteredExams"
        :key="exam.id"
        class="exam-card"
        :style="{ animationDelay: index * 0.05 + 's' }"
      >
        <div class="card-status-bar" :class="'status-' + getStatusClass(exam)"></div>
        <div class="card-body">
          <div class="card-top">
            <h3 class="card-title">{{ exam.title }}</h3>
            <div class="card-badges">
              <el-tag v-if="exam.isCreator" type="warning" effect="plain" size="small">创建者</el-tag>
              <el-tag :type="getStatusTagType(exam)" effect="dark" size="small">
                {{ getStatusText(exam) }}
              </el-tag>
            </div>
          </div>

          <div class="card-info">
            <div class="info-row" v-if="exam.description && exam.status === 1">
              <el-icon><ChatLineRound /></el-icon>
              <span class="prompt-text">{{ exam.description }}</span>
            </div>
            <div class="info-row" v-if="exam.paperTitle">
              <el-icon><Document /></el-icon>
              <span>{{ exam.paperTitle }}</span>
            </div>
            <div class="info-row">
              <el-icon><Edit /></el-icon>
              <span>{{ exam.questionCount || 0 }} 道题</span>
            </div>
            <div class="info-row">
              <el-icon><Timer /></el-icon>
              <span>{{ exam.duration }} 分钟</span>
            </div>
            <div class="info-row">
              <el-icon><User /></el-icon>
              <span>{{ exam.studentCount || 0 }} 人参与</span>
            </div>
          </div>

          <div class="card-action">
            <!-- 生成中状态 -->
            <template v-if="exam.status === 0">
              <div class="generating-info">
                <el-icon class="spin-icon"><Loading /></el-icon>
                <span>AI正在生成试卷，请稍候...</span>
              </div>
            </template>

            <!-- 生成失败 -->
            <template v-else-if="exam.status === 3">
              <el-button type="danger" plain disabled class="action-btn">
                <el-icon><CircleClose /></el-icon>生成失败
              </el-button>
            </template>

            <!-- 待考试/进行中 - 创建者 -->
            <template v-else-if="exam.isCreator && (exam.status === 1 || exam.status === 2)">
              <div class="creator-actions">
                <el-button type="primary" @click="handleStartExam(exam)" class="action-btn" :disabled="exam.mySubmitStatus === 1">
                  <el-icon><Right /></el-icon>
                  {{ exam.mySubmitStatus === 1 ? '已完成' : '开始考试' }}
                </el-button>
                <div class="sub-actions">
                  <el-button text type="primary" size="small" @click="showExamCode(exam)">
                    <el-icon><Ticket /></el-icon>分享考试码
                  </el-button>
                  <el-button text type="success" size="small" @click="handleViewPaper(exam)">
                    <el-icon><Document /></el-icon>查看试卷
                  </el-button>
                  <el-button text type="warning" size="small" @click="handleOpenRegenerate(exam)">
                    <el-icon><Refresh /></el-icon>重新生成
                  </el-button>
                  <el-button text type="warning" size="small" @click="handleRetake(exam)" v-if="exam.mySubmitStatus === 1">
                    <el-icon><RefreshRight /></el-icon>重新考试
                  </el-button>
                </div>
              </div>
            </template>

            <!-- 待考试/进行中 - 非创建者 -->
            <template v-else-if="!exam.isCreator && (exam.status === 1 || exam.status === 2)">
              <el-button type="primary" @click="handleStartExam(exam)" class="action-btn" :disabled="exam.mySubmitStatus === 1">
                <el-icon><Right /></el-icon>
                {{ exam.mySubmitStatus === 1 ? '已完成' : '开始考试' }}
              </el-button>
              <div class="sub-actions">
                <el-button text type="success" size="small" @click="handleViewPaper(exam)">
                  <el-icon><Document /></el-icon>查看试卷
                </el-button>
                <el-button text type="warning" size="small" @click="handleRetake(exam)" v-if="exam.mySubmitStatus === 1">
                  <el-icon><RefreshRight /></el-icon>重新考试
                </el-button>
              </div>
            </template>

            <!-- 其他状态（如已过期）：不显示操作按钮 -->
          </div>
        </div>
      </div>
    </div>

    <!-- ===== AI创建考试对话框 ===== -->
    <el-dialog v-model="showCreateDialog" title="AI智能生成试卷" width="580px" :close-on-click-modal="false">
      <div class="create-form">
        <div class="form-tip">
          <el-icon><InfoFilled /></el-icon>
          <span>告诉AI你想考什么，它会自动生成试卷。例如：</span>
        </div>
        <div class="prompt-examples">
          <el-tag
            v-for="example in promptExamples"
            :key="example"
            class="example-tag"
            effect="plain"
            @click="form.prompt = example"
          >
            {{ example }}
          </el-tag>
        </div>
        <el-form :model="form" label-position="top" style="margin-top: 20px;">
          <el-form-item label="考试要求" required>
            <el-input
              v-model="form.prompt"
              type="textarea"
              :rows="3"
              placeholder="请描述你想考什么内容、多少道题、什么题型等要求..."
              maxlength="500"
              show-word-limit
            />
          </el-form-item>
          <el-form-item label="考试时长（分钟）">
            <el-slider v-model="form.duration" :min="10" :max="180" :step="5" show-input :show-input-controls="false" />
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" @click="handleCreate" :loading="creating" :disabled="!form.prompt.trim()">
          <el-icon><MagicStick /></el-icon>
          {{ creating ? '创建中...' : '确认创建' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- ===== 通过考试码加入对话框 ===== -->
    <el-dialog v-model="showJoinDialog" title="通过考试码加入" width="460px">
      <div class="join-form">
        <div class="form-tip">
          <el-icon><InfoFilled /></el-icon>
          <span>输入同学分享的 6 位考试码，加入这场考试</span>
        </div>
        <el-input
          v-model="joinCode"
          placeholder="请输入6位考试码"
          maxlength="6"
          size="large"
          clearable
          style="margin-top: 20px; letter-spacing: 8px; font-size: 20px; text-align: center; font-weight: 700;"
          @keyup.enter="handlePreviewJoin"
        />
      </div>
      <template #footer>
        <el-button @click="showJoinDialog = false">取消</el-button>
        <el-button type="primary" @click="handlePreviewJoin" :disabled="joinCode.length !== 6">
          查看考试信息
        </el-button>
      </template>
    </el-dialog>

    <!-- ===== 考试预览对话框（加入前确认） ===== -->
    <el-dialog v-model="showPreviewDialog" title="考试信息" width="480px">
      <div v-if="previewInfo" class="preview-content">
        <div class="preview-header">
          <el-icon :size="40" color="#4361ee"><Document /></el-icon>
          <div>
            <h3>{{ previewInfo.title }}</h3>
            <p v-if="previewInfo.description">{{ previewInfo.description }}</p>
          </div>
        </div>
        <div class="preview-details">
          <div class="detail-item">
            <span class="detail-label">题目数量</span>
            <span class="detail-value">{{ previewInfo.questionCount }} 道</span>
          </div>
          <div class="detail-item">
            <span class="detail-label">考试时长</span>
            <span class="detail-value">{{ previewInfo.duration }} 分钟</span>
          </div>
          <div class="detail-item">
            <span class="detail-label">考试总分</span>
            <span class="detail-value">{{ previewInfo.totalScore }} 分</span>
          </div>
          <div class="detail-item">
            <span class="detail-label">已参与人数</span>
            <span class="detail-value">{{ previewInfo.studentCount }} 人</span>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="showPreviewDialog = false">取消</el-button>
        <el-button type="primary" @click="handleJoin" :loading="joining">
          <el-icon><CircleCheck /></el-icon>加入考试
        </el-button>
      </template>
    </el-dialog>

    <!-- ===== 考试码分享对话框 ===== -->
    <el-dialog v-model="showCodeDialog" title="分享考试码" width="420px">
      <div class="code-share">
        <p class="code-tip">将以下考试码分享给同学，同学可在「通过考试码加入」中输入此码参加考试。</p>
        <div class="code-display" @click="copyCode">
          <span class="code-text">{{ displayExamCode }}</span>
          <el-icon class="copy-icon"><CopyDocument /></el-icon>
        </div>
        <p class="code-hint">点击考试码可复制到剪贴板</p>
      </div>
      <template #footer>
        <el-button @click="showCodeDialog = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- ===== 查看试卷对话框（纯题目，不含答案和答题记录） ===== -->
    <el-dialog v-model="showPaperDialog" width="750px" top="3vh">
      <template #header>
        <span>试卷内容</span>
      </template>
      <div v-loading="loadingPaper" class="paper-dialog-body">
        <div v-if="paperData" class="paper-view">
          <div class="paper-header">
            <h3>{{ paperData.title }}</h3>
            <div class="paper-meta">
              <span>{{ paperData.questionCount }} 道题</span>
              <span>总分 {{ paperData.totalScore }} 分</span>
              <span>时长 {{ paperData.duration }} 分钟</span>
            </div>
          </div>
          <div class="paper-questions">
            <div v-for="(q, idx) in paperData.questions" :key="q.questionId" class="paper-question">
              <div class="pq-header">
                <span class="pq-number">{{ idx + 1 }}.</span>
                <el-tag size="small" :type="q.type === 1 ? '' : q.type === 2 ? 'warning' : q.type === 3 ? 'success' : 'info'" effect="plain">
                  {{ q.typeDesc }}
                </el-tag>
                <span class="pq-content">{{ q.content }}</span>
                <span class="pq-score">（{{ q.score }}分）</span>
              </div>
              <!-- 选择/判断题选项（纯展示，不高亮答案） -->
              <div v-if="q.type !== 4" class="pq-options">
                <div v-for="opt in ['A','B','C','D']" :key="opt" class="pq-option">
                  <strong v-if="q['option' + opt]">{{ opt }}.</strong> {{ q['option' + opt] || '' }}
                </div>
              </div>
              <!-- 简答题：无答案展示 -->
              <div v-if="q.type === 4" class="pq-essay-placeholder">
                <span class="pq-answer-label">（简答题，考试时填写）</span>
              </div>
            </div>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="showPaperDialog = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- ===== 重新生成对话框 ===== -->
    <el-dialog v-model="showRegenerateDialog" title="重新生成试卷" width="580px" :close-on-click-modal="false">
      <div class="create-form">
        <el-alert type="warning" :closable="false" style="margin-bottom: 16px;">
          <template #default>
            重新生成将<strong>清除当前试卷</strong>（含题目、答题记录、成绩），生成全新试卷，操作不可撤销。
          </template>
        </el-alert>
        <div class="prompt-examples">
          <el-tag
            v-for="example in promptExamples"
            :key="example"
            class="example-tag"
            effect="plain"
            @click="regenForm.prompt = example"
          >
            {{ example }}
          </el-tag>
        </div>
        <el-form :model="regenForm" label-position="top" style="margin-top: 16px;">
          <el-form-item label="考试要求" required>
            <el-input
              v-model="regenForm.prompt"
              type="textarea"
              :rows="3"
              placeholder="修改或保留原来的考试要求..."
              maxlength="500"
              show-word-limit
            />
          </el-form-item>
          <el-form-item label="考试时长（分钟）">
            <el-slider v-model="regenForm.duration" :min="10" :max="180" :step="5" show-input :show-input-controls="false" />
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <el-button @click="showRegenerateDialog = false">取消</el-button>
        <el-button type="danger" @click="handleRegenerate" :loading="regenerating" :disabled="!regenForm.prompt.trim()">
          <el-icon><Refresh /></el-icon>
          {{ regenerating ? '生成中...' : '确认重新生成' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getMySelfStudyExams,
  createSelfStudyExam,
  generateSelfStudyExam,
  getGenerateStatus,
  previewByExamCode,
  joinByExamCode,
  getExamCode,
  selfRetake,
  getExamPaper,
  regenerateSelfStudyExam
} from '@/api/selfStudyExam'
import { startExam } from '@/api/exam'

const router = useRouter()
const loading = ref(false)
const activeTab = ref('all')
const exams = ref([])
let pollTimer = null

// Tab配置
const tabs = ref([
  { key: 'all', label: '全部', count: 0 },
  { key: 'active', label: '可参与', count: 0 },
  { key: 'failed', label: '生成失败', count: 0 }
])

// 创建相关
const showCreateDialog = ref(false)
const creating = ref(false)
const form = ref({
  prompt: '',
  duration: 60
})

// 重新生成相关
const showRegenerateDialog = ref(false)
const regenerating = ref(false)
const regenForm = ref({ prompt: '', duration: 60 })
const regenTargetExam = ref(null) // 当前要重新生成的考试

// 加入相关
const showJoinDialog = ref(false)
const joinCode = ref('')
const showPreviewDialog = ref(false)
const previewInfo = ref(null)
const joining = ref(false)

// 考试码分享
const showCodeDialog = ref(false)
const displayExamCode = ref('')

// 历史成绩（已移至"我的考试"页面）
// 查看试卷
const showPaperDialog = ref(false)
const paperData = ref(null)
const loadingPaper = ref(false)

// 示例prompt
const promptExamples = [
  '生成10道Java基础单选题',
  '5道Vue.js多选题和5道判断题',
  '8道数据库SQL相关的单选题',
  '10道Python编程基础题（含判断和单选）',
  '15道计算机网络基础题，包含单选和多选'
]

// 生命周期
onMounted(() => {
  loadData()
})

onBeforeUnmount(() => {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
})

// ====== 数据加载 ======

async function loadData() {
  loading.value = true
  try {
    const res = await getMySelfStudyExams()
    exams.value = (res.data || []).map(exam => ({
      ...exam,
      // 从后端participant信息判断是否是创建者
      isCreator: exam.creatorId == getCurrentUserId()
    }))
    updateTabCounts()
    startPollingIfGenerating()
  } finally {
    loading.value = false
  }
}

function getCurrentUserId() {
  const userInfo = JSON.parse(localStorage.getItem('userInfo') || '{}')
  return userInfo.id || userInfo.userId
}

function updateTabCounts() {
  tabs.value[0].count = exams.value.length
  tabs.value[1].count = exams.value.filter(e => e.status === 1 || e.status === 2).length
  tabs.value[2].count = exams.value.filter(e => e.status === 3).length
}

// 如果有正在生成的考试，启动轮询
function startPollingIfGenerating() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
  const generatingExams = exams.value.filter(e => e.status === 0)
  if (generatingExams.length > 0) {
    pollTimer = setInterval(async () => {
      try {
        const res = await getMySelfStudyExams()
        const updated = (res.data || []).map(exam => ({
          ...exam,
          isCreator: exam.creatorId == getCurrentUserId()
        }))

        // 检查是否有状态变化
        let changed = false
        for (const old of exams.value) {
          if (old.status === 0) {
            const updatedExam = updated.find(e => e.id === old.id)
            if (updatedExam && updatedExam.status !== 0) {
              changed = true
              if (updatedExam.status === 1) {
                ElMessage.success(`考试「${updatedExam.title}」生成成功！`)
              } else if (updatedExam.status === 3) {
                ElMessage.error(`考试「${updatedExam.title}」生成失败`)
              }
            }
          }
        }

        exams.value = updated
        updateTabCounts()

        // 如果没有正在生成的了，停止轮询
        if (!updated.some(e => e.status === 0)) {
          clearInterval(pollTimer)
          pollTimer = null
        }
      } catch (e) {
        // 轮询失败静默处理
      }
    }, 3000)
  }
}

// ====== 筛选 ======

const filteredExams = computed(() => {
  if (activeTab.value === 'all') return exams.value
  if (activeTab.value === 'active') return exams.value.filter(e => e.status === 1 || e.status === 2)
  return exams.value.filter(e => e.status === 3)
})

// ====== 状态工具 ======

function getStatusText(exam) {
  const map = { 0: '生成中', 1: '待考试', 2: '进行中', 3: '生成失败' }
  return map[exam.status] || '未知'
}

function getStatusClass(exam) {
  const map = { 0: 'generating', 1: 'ready', 2: 'active', 3: 'ended' }
  return map[exam.status] || 'default'
}

function getStatusTagType(exam) {
  const map = { 0: 'info', 1: 'success', 2: '', 3: 'danger' }
  return map[exam.status] || 'info'
}

// ====== 创建考试 ======

async function handleCreate() {
  if (!form.value.prompt.trim()) {
    ElMessage.warning('请输入考试要求')
    return
  }

  creating.value = true
  try {
    // 1. 创建自助考试记录
    const createRes = await createSelfStudyExam({
      prompt: form.value.prompt.trim(),
      duration: form.value.duration
    })

    const newExam = createRes.data
    ElMessage.info('考试记录已创建，正在调用AI生成试卷，请耐心等待...')

    showCreateDialog.value = false

    // 重新加载列表
    await loadData()

    // 2. 触发AI生成（同步调用，可能耗时较长）
    try {
      await generateSelfStudyExam(newExam.id)
      ElMessage.success('AI试卷生成成功！')
    } catch (genErr) {
      // 生成失败，通过轮询自动感知状态变化
      ElMessage.error(genErr.message || 'AI生成失败，可稍后重试')
    }

    // 重新加载列表以获取最新状态
    await loadData()
  } catch (e) {
    ElMessage.error(e.message || '创建失败')
  } finally {
    creating.value = false
    form.value.prompt = ''
    form.value.duration = 60
  }
}

// ====== 重新生成试卷 ======

function handleOpenRegenerate(exam) {
  regenTargetExam.value = exam
  regenForm.value.prompt = exam.description || ''
  regenForm.value.duration = exam.duration || 60
  showRegenerateDialog.value = true
}

async function handleRegenerate() {
  if (!regenForm.value.prompt.trim()) {
    ElMessage.warning('请输入考试要求')
    return
  }
  try {
    await ElMessageBox.confirm(
      '重新生成将清除当前试卷的所有题目、答题记录和成绩，确定要继续吗？',
      '确认重新生成',
      { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' }
    )
  } catch { return }

  regenerating.value = true
  showRegenerateDialog.value = false
  ElMessage.info('正在重新生成试卷，请耐心等待...')

  try {
    await regenerateSelfStudyExam(regenTargetExam.value.id, {
      prompt: regenForm.value.prompt.trim(),
      duration: regenForm.value.duration
    })
    ElMessage.success('试卷已重新生成成功！')
    await loadData()
  } catch (e) {
    ElMessage.error(e.message || '重新生成失败，请稍后重试')
    await loadData()
  } finally {
    regenerating.value = false
    regenTargetExam.value = null
  }
}

// ====== 开始考试 ======

async function handleStartExam(exam) {
  // 自助考试使用关联的examId来开始
  const targetExamId = exam.examId || exam.id

  if (!targetExamId) {
    ElMessage.error('考试ID无效，请联系创建者')
    return
  }

  try {
    await ElMessageBox.confirm(
      `即将开始考试「${exam.title}」，考试时长${exam.duration}分钟。请确保网络稳定，准备好后点击确定。`,
      '开始考试',
      { type: 'warning', confirmButtonText: '确定开始', cancelButtonText: '取消' }
    )

    await startExam(targetExamId)
    // 跳转到考试页面（使用关联的正式examId）
    router.push(`/student/exam/${targetExamId}`)
  } catch (e) {
    // 用户取消
  }
}

// ====== 重考 ======

async function handleRetake(exam) {
  try {
    await ElMessageBox.confirm(
      '重考将清除本次的答题记录和成绩，确定要重新考试吗？',
      '确认重考',
      { type: 'warning', confirmButtonText: '确定重考', cancelButtonText: '取消' }
    )

    await selfRetake(exam.id)
    ElMessage.success('已重置，可以重新考试')
    await loadData()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(e.message || '重考失败')
    }
  }
}

// ====== 考试码相关 ======

async function showExamCode(exam) {
  try {
    const res = await getExamCode(exam.id)
    displayExamCode.value = res.data.examCode
    showCodeDialog.value = true
  } catch (e) {
    ElMessage.error(e.message || '获取考试码失败')
  }
}

function copyCode() {
  navigator.clipboard.writeText(displayExamCode.value).then(() => {
    ElMessage.success('考试码已复制到剪贴板')
  }).catch(() => {
    // fallback
    const textarea = document.createElement('textarea')
    textarea.value = displayExamCode.value
    document.body.appendChild(textarea)
    textarea.select()
    document.execCommand('copy')
    document.body.removeChild(textarea)
    ElMessage.success('考试码已复制到剪贴板')
  })
}

// ====== 通过考试码加入 ======

async function handlePreviewJoin() {
  if (joinCode.value.length !== 6) {
    ElMessage.warning('请输入6位考试码')
    return
  }

  try {
    const res = await previewByExamCode(joinCode.value)
    previewInfo.value = res.data
    showJoinDialog.value = false
    showPreviewDialog.value = true
  } catch (e) {
    ElMessage.error(e.message || '查询失败，请检查考试码是否正确')
  }
}

async function handleJoin() {
  joining.value = true
  try {
    await joinByExamCode(joinCode.value)
    ElMessage.success('加入成功！')
    showPreviewDialog.value = false
    joinCode.value = ''
    previewInfo.value = null
    await loadData()
  } catch (e) {
    ElMessage.error(e.message || '加入失败')
  } finally {
    joining.value = false
  }
}

// ====== 查看历史成绩 ======
// （已移至"我的考试"页面，此处删除）

// ====== 查看试卷内容（纯题目，不含答案和答题记录） ======

async function handleViewPaper(exam) {
  showPaperDialog.value = true
  loadingPaper.value = true
  paperData.value = null
  try {
    // 始终调用纯题目接口，不展示答案和答题情况
    const res = await getExamPaper(exam.id)
    paperData.value = res.data
  } catch (e) {
    ElMessage.error(e.message || '获取试卷失败')
    showPaperDialog.value = false
  } finally {
    loadingPaper.value = false
  }
}
</script>

<style scoped>
/* ── 页面标题 ── */
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.page-header h2 {
  font-size: 20px;
  font-weight: 700;
  color: var(--color-text-primary);
}

.header-actions {
  display: flex;
  gap: 10px;
}

/* ── Tab切换 ── */
.exam-tabs {
  display: flex;
  gap: 4px;
  margin-bottom: 24px;
  background: var(--color-bg-card);
  padding: 4px;
  border-radius: var(--radius-md);
  border: 1px solid var(--color-border-light);
  width: fit-content;
}

.exam-tab {
  padding: 8px 20px;
  border-radius: var(--radius-sm);
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text-secondary);
  cursor: pointer;
  transition: all var(--transition-fast);
  display: flex;
  align-items: center;
  gap: 6px;
}

.exam-tab:hover {
  color: var(--color-text-primary);
  background: var(--color-bg-page);
}

.exam-tab.active {
  background: var(--color-primary);
  color: #fff;
  box-shadow: 0 2px 8px rgba(67, 97, 238, 0.3);
}

.tab-count {
  font-size: 11px;
  font-weight: 600;
  padding: 1px 6px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.2);
}

.exam-tab:not(.active) .tab-count {
  background: var(--color-border);
  color: var(--color-text-secondary);
}

/* ── 空状态 ── */
.empty-state {
  text-align: center;
  padding: 80px 0;
}

.empty-icon {
  color: #dcdfe6;
  margin-bottom: 16px;
}

.empty-state p {
  color: var(--color-text-placeholder);
  font-size: 15px;
}

/* ── 考试卡片网格 ── */
.exam-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(360px, 1fr));
  gap: 20px;
}

.exam-card {
  background: var(--color-bg-card);
  border-radius: var(--radius-md);
  border: 1px solid var(--color-border-light);
  overflow: hidden;
  transition: all var(--transition-normal);
  animation: fadeInUp 0.4s ease backwards;
}

.exam-card:hover {
  transform: translateY(-3px);
  box-shadow: var(--shadow-card-hover);
  border-color: transparent;
}

/* 状态条 */
.card-status-bar {
  height: 3px;
}

.card-status-bar.status-generating {
  background: linear-gradient(90deg, var(--color-info), var(--color-primary));
  animation: shimmer 2s infinite;
}

.card-status-bar.status-ready {
  background: linear-gradient(90deg, var(--color-success), #38e8b0);
}

.card-status-bar.status-active {
  background: linear-gradient(90deg, var(--color-primary), #5a7cf7);
}

.card-status-bar.status-ended {
  background: linear-gradient(90deg, var(--color-text-placeholder), #b0b0b0);
}

/* 卡片内容 */
.card-body {
  padding: 20px;
}

.card-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 14px;
  gap: 12px;
}

.card-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--color-text-primary);
  line-height: 1.4;
  flex: 1;
}

.card-badges {
  display: flex;
  gap: 6px;
  flex-shrink: 0;
}

.card-info {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 16px;
}

.info-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--color-text-regular);
}

.info-row .el-icon {
  color: var(--color-text-placeholder);
  font-size: 16px;
  flex-shrink: 0;
}

.prompt-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 300px;
}

.card-action {
  padding-top: 14px;
  border-top: 1px solid var(--color-border-light);
}

.action-btn {
  width: 100%;
  border-radius: var(--radius-sm);
  font-weight: 600;
}

.sub-actions {
  display: flex;
  justify-content: center;
  gap: 12px;
  margin-top: 8px;
}

.creator-actions {
  display: flex;
  flex-direction: column;
}

.generating-info {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 12px;
  color: var(--color-primary);
  font-size: 14px;
  font-weight: 500;
}

.spin-icon {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

/* ── 创建对话框 ── */
.create-form {
  padding: 4px;
}

.form-tip {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--color-text-secondary);
  margin-bottom: 12px;
}

.form-tip .el-icon {
  color: var(--color-primary);
}

.prompt-examples {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.example-tag {
  cursor: pointer;
  transition: all var(--transition-fast);
}

.example-tag:hover {
  color: var(--color-primary);
  border-color: var(--color-primary);
}

/* ── 加入对话框 ── */
.join-form {
  text-align: center;
  padding: 10px 0;
}

.code-share {
  text-align: center;
}

.code-tip {
  font-size: 14px;
  color: var(--color-text-secondary);
  margin-bottom: 20px;
}

.code-display {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 20px;
  background: var(--color-bg-page);
  border: 2px dashed var(--color-border);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.code-display:hover {
  border-color: var(--color-primary);
  background: var(--color-primary-bg);
}

.code-text {
  font-size: 36px;
  font-weight: 800;
  letter-spacing: 12px;
  color: var(--color-primary);
}

.copy-icon {
  font-size: 20px;
  color: var(--color-text-placeholder);
}

.code-hint {
  margin-top: 10px;
  font-size: 12px;
  color: var(--color-text-placeholder);
}

/* ── 预览对话框 ── */
.preview-content {
  padding: 8px;
}

.preview-header {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  margin-bottom: 24px;
}

.preview-header h3 {
  font-size: 16px;
  font-weight: 700;
  color: var(--color-text-primary);
  margin-bottom: 6px;
}

.preview-header p {
  font-size: 13px;
  color: var(--color-text-secondary);
}

.preview-details {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.detail-item {
  display: flex;
  justify-content: space-between;
  padding: 12px 16px;
  background: var(--color-bg-page);
  border-radius: var(--radius-sm);
}

.detail-label {
  font-size: 13px;
  color: var(--color-text-secondary);
}

.detail-value {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-primary);
}

/* ── 响应式 ── */
@media (max-width: 768px) {
  .page-header {
    flex-direction: column;
    gap: 12px;
    align-items: flex-start;
  }

  .header-actions {
    width: 100%;
  }

  .header-actions .el-button {
    flex: 1;
  }

  .exam-grid {
    grid-template-columns: 1fr;
  }

  .exam-tabs {
    width: 100%;
    overflow-x: auto;
  }

  .code-text {
    font-size: 28px;
    letter-spacing: 8px;
  }
}

/* ── 历史成绩对话框 ── */
.scores-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.score-item {
  padding: 14px 18px;
  background: var(--color-bg-page);
  border-radius: var(--radius-sm);
  border: 1px solid var(--color-border-light);
}
.score-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.score-attempt {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-primary);
}
.score-body {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.score-value {
  font-size: 28px;
  font-weight: 800;
  color: var(--color-text-primary);
}
.score-value.pass { color: var(--color-success); }
.score-total {
  font-size: 16px;
  font-weight: 400;
  color: var(--color-text-placeholder);
}
.score-meta {
  display: flex;
  gap: 12px;
  font-size: 12px;
  color: var(--color-text-placeholder);
}

/* ── 查看试卷对话框 ── */
.paper-dialog-header {
  display: flex;
  align-items: center;
}
.paper-dialog-body {
  max-height: 70vh;
  overflow-y: auto;
}
.paper-score-banner {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  background: var(--color-bg-page);
  border-radius: var(--radius-sm);
  margin-bottom: 20px;
}
.psb-score {
  font-size: 32px;
  font-weight: 800;
  color: var(--color-danger);
}
.psb-score.pass { color: var(--color-success); }
.psb-total {
  font-size: 16px;
  font-weight: 400;
  color: var(--color-text-placeholder);
}
.psb-info {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
}
.psb-time {
  font-size: 13px;
  color: var(--color-text-secondary);
}
.paper-view {
  padding: 4px;
}
.paper-header {
  margin-bottom: 20px;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--color-border-light);
}
.paper-header h3 {
  font-size: 17px;
  font-weight: 700;
  color: var(--color-text-primary);
  margin-bottom: 8px;
}
.paper-meta {
  display: flex;
  gap: 16px;
  font-size: 13px;
  color: var(--color-text-secondary);
}
.paper-questions {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.paper-question {
  padding: 14px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--color-border-light);
  background: var(--color-bg-page);
  transition: border-color 0.2s;
}
.paper-question.pq-correct { border-left: 3px solid var(--color-success); }
.paper-question.pq-wrong { border-left: 3px solid var(--color-danger); }
.pq-header {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-bottom: 10px;
  flex-wrap: wrap;
}
.pq-number {
  font-size: 14px;
  font-weight: 700;
  color: var(--color-text-primary);
  flex-shrink: 0;
}
.pq-content {
  font-size: 14px;
  color: var(--color-text-primary);
  flex: 1;
  line-height: 1.5;
}
.pq-score {
  font-size: 12px;
  color: var(--color-text-placeholder);
  flex-shrink: 0;
}
.pq-result-tag {
  flex-shrink: 0;
}
.pq-options {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding-left: 28px;
}
.pq-option {
  font-size: 14px;
  color: var(--color-text-regular);
  line-height: 1.5;
  padding: 4px 8px;
  border-radius: 4px;
  display: flex;
  align-items: center;
  gap: 4px;
}
.pq-option-selected { background: #ecf5ff; }
.pq-option-selected.pq-option-wrong { background: #fef0f0; color: var(--color-danger); }
.pq-option-correct:not(.pq-option-wrong) { background: #f0f9eb; color: var(--color-success); }
.pq-option-mark {
  font-size: 11px;
  color: var(--color-primary);
  background: rgba(67, 97, 238, 0.08);
  padding: 1px 6px;
  border-radius: 3px;
  margin-left: 8px;
}
.pq-option-wrong .pq-option-mark { color: var(--color-danger); background: rgba(245, 108, 108, 0.08); }
.pq-option-mark-correct { color: var(--color-success) !important; background: rgba(103, 194, 58, 0.08) !important; }
/* 简答题答案 */
.pq-essay-answer {
  padding: 10px 14px;
  margin-left: 28px;
  background: #f8f9fa;
  border-radius: var(--radius-sm);
  font-size: 14px;
  line-height: 1.6;
}
.pq-student-answer { margin-bottom: 8px; padding-bottom: 8px; border-bottom: 1px dashed var(--color-border-light); }
.pq-answer-label { color: var(--color-text-secondary); font-weight: 600; }
.pq-correct-answer { color: var(--color-success); }
.pq-analysis {
  margin-top: 10px;
  margin-left: 28px;
  padding: 10px 14px;
  background: #fffbf0;
  border-radius: var(--radius-sm);
  font-size: 13px;
  color: var(--color-text-secondary);
  line-height: 1.6;
}
.pq-analysis-label { color: #e6a23c; font-weight: 600; }
</style>
