<template>
  <div class="student-exams">
    <!-- 页面标题 -->
    <div class="page-header">
      <h2>我的考试</h2>
      <el-button @click="loadData" :loading="loading" plain>
        <el-icon><Refresh /></el-icon>刷新
      </el-button>
    </div>

    <!-- Tab筛选 -->
    <div class="exam-tabs">
      <div
        v-for="tab in tabs"
        :key="tab.key"
        class="exam-tab"
        :class="{ active: activeTab === tab.key }"
        @click="activeTab = tab.key; loadData()"
      >
        {{ tab.label }}
        <span v-if="tab.count !== undefined" class="tab-count">{{ tab.count }}</span>
      </div>
    </div>

    <!-- 空状态 -->
    <div v-if="!loading && filteredExams.length === 0" class="empty-state">
      <el-icon :size="64" color="#dcdfe6"><Document /></el-icon>
      <p>暂无考试</p>
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
            <el-tag :type="getStatusTagType(exam)" effect="dark" size="small" class="status-tag">
              {{ getStatusText(exam) }}
            </el-tag>
          </div>

          <div class="card-info">
            <div class="info-row">
              <el-icon><Document /></el-icon>
              <span>{{ exam.paperTitle || '未分配试卷' }}</span>
            </div>
            <div class="info-row">
              <el-icon><Clock /></el-icon>
              <span>{{ exam.startTime }} ~ {{ exam.endTime }}</span>
            </div>
            <div class="info-row">
              <el-icon><Timer /></el-icon>
              <span>{{ exam.duration }} 分钟</span>
            </div>
            <div v-if="exam.requireFaceVerify === 1" class="info-row face-row">
              <el-icon><UserFilled /></el-icon>
              <span>需要人脸核验</span>
            </div>
          </div>

          <div class="card-action">
            <el-tooltip
              :content="canStartExam(exam) ? '点击开始考试' : getCannotStartReason(exam) || '当前不可用'"
              :placement="'bottom'"
              :disabled="canStartExam(exam)"
            >
            <el-button
              :type="getButtonPrimaryType(exam)"
              :disabled="!canStartExam(exam)"
              @click="handleStartExam(exam)"
              class="action-btn"
            >
              <el-icon><Right /></el-icon>
              {{ getButtonText(exam) }}
            </el-button>
            </el-tooltip>
            <!-- 已交卷后的操作按钮 -->
            <div v-if="isSubmitted(exam)" class="sub-actions">
              <el-button
                v-if="exam.selfStudyExamId || exam.recordCount > 1"
                text type="info" size="small"
                @click="handleViewScores(exam)"
              >
                <el-icon><Trophy /></el-icon>历史成绩
              </el-button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 人脸核验弹窗（已抽为独立组件 FaceVerifyDialog） -->
    <FaceVerifyDialog v-model="showFaceVerify" :exam-id="faceVerifyExamId" />

    <!-- ===== 历史成绩对话框（已抽为独立组件） ===== -->
    <ExamScoresDialog v-model="showScoresDialog" :exam="currentScoreExam" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMyExams } from '@/api/exam'
import ExamScoresDialog from '@/components/exam/ExamScoresDialog.vue'
import FaceVerifyDialog from '@/components/exam/FaceVerifyDialog.vue'
import { isSubmitted, getStatusClass, getStatusText, getStatusTagType, canStartExam, getCannotStartReason, getButtonPrimaryType, getButtonText } from '@/utils/examStatus'

const router = useRouter()
const loading = ref(false)
const activeTab = ref('all')
const exams = ref([])
const filteredExams = ref([])

// Tab配置
const tabs = ref([
  { key: 'all', label: '全部' },
  { key: 'active', label: '进行中' },
  { key: 'ended', label: '已结束' }
])

// ─── 人脸核验（实现已移入 FaceVerifyDialog，这里只控制开关与考生 ID）───
const showFaceVerify = ref(false)
const faceVerifyExamId = ref(null)

// 历史成绩（数据加载已移入 ExamScoresDialog，此处只记录选中的考试）
const showScoresDialog = ref(false)
const currentScoreExam = ref(null)

onMounted(() => loadData())

async function loadData() {
  loading.value = true
  try {
    const res = await getMyExams()
    exams.value = res.data || []
    filterExams()
    updateTabCounts()
  } finally {
    loading.value = false
  }
}

function filterExams() {
  if (activeTab.value === 'all') {
    filteredExams.value = exams.value
  } else if (activeTab.value === 'active') {
    filteredExams.value = exams.value.filter(e => {
      const now = new Date()
      const start = new Date(e.startTime)
      const end = new Date(e.endTime)
      return now >= start && now <= end && e.submitStatus === '0'
    })
  } else if (activeTab.value === 'ended') {
    filteredExams.value = exams.value.filter(e => {
      const now = new Date()
      const end = new Date(e.endTime)
      return now > end || e.submitStatus !== '0'
    })
  }
}

function updateTabCounts() {
  const now = new Date()
  const activeCount = exams.value.filter(e => {
    const start = new Date(e.startTime)
    const end = new Date(e.endTime)
    return now >= start && now <= end && e.submitStatus === '0'
  }).length
  tabs.value[1].count = activeCount
}

async function handleStartExam(exam) {
  console.log('[handleStartExam] 被点击, examId=', exam.id, 'title=', exam.title,
    'submitStatus=', exam.submitStatus, 'startTime=', exam.startTime, 'endTime=', exam.endTime)

  if (!canStartExam(exam)) {
    // 给出明确的原因提示，不再静默失败
    const reason = getCannotStartReason(exam)
    ElMessage.warning(reason || '当前无法开始考试，请检查考试时间和状态')
    console.warn('[handleStartExam] canStartExam 返回 false, 原因:', reason)
    return
  }

  try {
    await ElMessageBox.confirm(
      `即将开始考试「${exam.title}」，考试时长${exam.duration}分钟。请确保网络稳定，准备好后点击确定。`,
      '开始考试',
      { type: 'warning', confirmButtonText: '确定开始', cancelButtonText: '取消' }
    )
    console.log('[handleStartExam] 用户确认开始考试')

    if (exam.requireFaceVerify === 1) {
      console.log('[handleStartExam] 需要人脸核验, 打开核验弹窗, examId=', exam.id)
      faceVerifyExamId.value = exam.id
      showFaceVerify.value = true
    } else {
      console.log('[handleStartExam] 无需人脸核验, 直接跳转考试页面, examId=', exam.id)
      router.push(`/student/exam/${exam.id}`)
    }
  } catch (e) {
    // 区分"用户取消确认框"和真正的异常
    if (e === 'cancel' || e?.message === 'cancel') {
      console.log('[handleStartExam] 用户取消了确认弹窗')
    } else {
      console.error('[handleStartExam] 发生异常:', e)
      ElMessage.error('操作失败：' + (e?.message || '未知错误'))
    }
  }
}

/** 打开历史成绩弹窗（数据由 ExamScoresDialog 自己加载） */
function handleViewScores(exam) {
  currentScoreExam.value = exam
  showScoresDialog.value = true
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
.exam-tab:hover { color: var(--color-text-primary); background: var(--color-bg-page); }
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
.empty-state { text-align: center; padding: 60px 0; }
.empty-state p { margin-top: 16px; color: var(--color-text-placeholder); font-size: 15px; }

/* ── 考试卡片网格 ── */
.exam-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(340px, 1fr)); gap: 20px; }
.exam-card {
  background: var(--color-bg-card);
  border-radius: var(--radius-md);
  border: 1px solid var(--color-border-light);
  overflow: hidden;
  transition: all var(--transition-normal);
  animation: fadeInUp 0.4s ease backwards;
}
.exam-card:hover { transform: translateY(-3px); box-shadow: var(--shadow-card-hover); border-color: transparent; }

.card-status-bar { height: 3px; transition: background var(--transition-fast); }
.status-active { background: linear-gradient(90deg, var(--color-success), #38e8b0); }
.status-upcoming { background: linear-gradient(90deg, var(--color-info), #4db8d8); }
.status-submitted { background: linear-gradient(90deg, var(--color-warning), #ffe299); }
.status-auto { background: linear-gradient(90deg, #f0a020, var(--color-warning)); }
.status-ended { background: linear-gradient(90deg, var(--color-danger), #f47a97); }

.card-body { padding: 20px; }
.card-top { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 16px; gap: 12px; }
.card-title { font-size: 16px; font-weight: 700; color: var(--color-text-primary); line-height: 1.4; flex: 1; }
.status-tag { flex-shrink: 0; }
.card-info { display: flex; flex-direction: column; gap: 8px; margin-bottom: 16px; }
.info-row { display: flex; align-items: center; gap: 8px; font-size: 13px; color: var(--color-text-regular); }
.info-row .el-icon { color: var(--color-text-placeholder); font-size: 16px; flex-shrink: 0; }
.face-row { color: #f0a020; }
.face-row .el-icon { color: #f0a020; }
.card-action { padding-top: 16px; border-top: 1px solid var(--color-border-light); }
.action-btn { width: 100%; border-radius: var(--radius-sm); font-weight: 600; }
.sub-actions { display: flex; justify-content: center; gap: 12px; margin-top: 10px; }

/* ── 响应式 ── */
@media (max-width: 768px) {
  .exam-grid { grid-template-columns: 1fr; }
  .exam-tabs { width: 100%; overflow-x: auto; }
}
</style>
