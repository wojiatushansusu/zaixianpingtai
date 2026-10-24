<template>
  <div class="dashboard-container">
    <div class="welcome-section">
      <div class="welcome-card">
        <div class="welcome-text">
          <h2 class="welcome-title">{{ greeting }}，{{ userInfo.realName || userInfo.username }} 👋</h2>
          <p class="welcome-desc">{{ welcomeDesc }}</p>
        </div>
        <div class="welcome-decoration">
          <el-icon :size="80" color="rgba(67, 97, 238, 0.15)"><Reading /></el-icon>
        </div>
      </div>
    </div>

    <div class="stats-section">
      <div class="stat-card primary" @click="navigateTo(studentExamsPath)">
        <div class="stat-icon">
          <el-icon :size="28"><Tickets /></el-icon>
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ stats.examCount }}</div>
          <div class="stat-label">可参加考试</div>
        </div>
        <div class="stat-arrow">
          <el-icon><ArrowRight /></el-icon>
        </div>
      </div>

      <div class="stat-card success" @click="navigateTo(studentGradesPath)">
        <div class="stat-icon">
          <el-icon :size="28"><Medal /></el-icon>
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ stats.completedCount }}</div>
          <div class="stat-label">已完成考试</div>
        </div>
        <div class="stat-arrow">
          <el-icon><ArrowRight /></el-icon>
        </div>
      </div>

      <div class="stat-card warning" @click="navigateTo(wrongQuestionsPath)">
        <div class="stat-icon">
          <el-icon :size="28"><Warning /></el-icon>
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ stats.wrongCount }}</div>
          <div class="stat-label">错题数量</div>
        </div>
        <div class="stat-arrow">
          <el-icon><ArrowRight /></el-icon>
        </div>
      </div>

      <div class="stat-card info" @click="navigateTo(selfStudyPath)">
        <div class="stat-icon">
          <el-icon :size="28"><MagicStick /></el-icon>
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ stats.selfStudyCount }}</div>
          <div class="stat-label">AI 出卷</div>
        </div>
        <div class="stat-arrow">
          <el-icon><ArrowRight /></el-icon>
        </div>
      </div>
    </div>

    <div class="content-section">
      <el-card class="section-card recent-exams" shadow="hover">
        <template #header>
          <div class="card-header">
            <span class="card-title">
              <el-icon class="title-icon"><Clock /></el-icon>
              最近考试
            </span>
            <el-button type="primary" link @click="navigateTo(studentExamsPath)">
              查看全部 <el-icon><ArrowRight /></el-icon>
            </el-button>
          </div>
        </template>
        <el-empty v-if="recentExams.length === 0" description="暂无考试记录" :image-size="100" />
        <div v-else class="exam-list">
          <div v-for="exam in recentExams" :key="exam.id" class="exam-item" @click="goToExam(exam)">
            <div class="exam-dot" :class="getExamStatusClass(exam.status)"></div>
            <div class="exam-info">
              <div class="exam-title">{{ exam.title }}</div>
              <div class="exam-meta">
                <span class="exam-status" :class="getExamStatusClass(exam.status)">
                  {{ getExamStatusText(exam.status) }}
                </span>
                <span class="exam-time" v-if="exam.startTime">
                  <el-icon><Calendar /></el-icon>
                  {{ formatDate(exam.startTime) }}
                </span>
              </div>
            </div>
            <el-icon class="exam-arrow"><ArrowRight /></el-icon>
          </div>
        </div>
      </el-card>

      <el-card class="section-card quick-actions" shadow="hover">
        <template #header>
          <div class="card-header">
            <span class="card-title">
              <el-icon class="title-icon"><Lightning /></el-icon>
              快捷操作
            </span>
          </div>
        </template>
        <div class="action-grid">
          <div class="action-item" @click="navigateTo(studentExamsPath)">
            <div class="action-icon exam">
              <el-icon :size="24"><Tickets /></el-icon>
            </div>
            <span class="action-text">参加考试</span>
          </div>
          <div class="action-item" @click="navigateTo(wrongQuestionsPath)">
            <div class="action-icon wrong">
              <el-icon :size="24"><Notebook /></el-icon>
            </div>
            <span class="action-text">错题练习</span>
          </div>
          <div class="action-item" @click="navigateTo(selfStudyPath)">
            <div class="action-icon ai">
              <el-icon :size="24"><MagicStick /></el-icon>
            </div>
            <span class="action-text">AI组卷</span>
          </div>
          <div class="action-item" @click="navigateTo(studentGradesPath)">
            <div class="action-icon grade">
              <el-icon :size="24"><DataLine /></el-icon>
            </div>
            <span class="action-text">成绩分析</span>
          </div>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import {
  Reading, Tickets, Medal, Warning, MagicStick, ArrowRight,
  Clock, Calendar, Lightning, Notebook, DataLine
} from '@element-plus/icons-vue'

const router = useRouter()

const userInfo = ref({})
const recentExams = ref([])
const loading = ref(false)

const stats = ref({
  examCount: 0,
  completedCount: 0,
  wrongCount: 0,
  selfStudyCount: 0
})

const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 6) return '夜深了'
  if (hour < 9) return '早上好'
  if (hour < 12) return '上午好'
  if (hour < 14) return '中午好'
  if (hour < 18) return '下午好'
  if (hour < 22) return '晚上好'
  return '夜深了'
})

const welcomeDesc = computed(() => {
  const role = userInfo.value.roleKey
  if (role === 'admin') return '管理系统，让考试更高效'
  if (role === 'teacher') return '创建和管理考试，见证学生成长'
  return '学习永不止步，今天也要加油哦'
})

const isStudent = computed(() => userInfo.value.roleKey === 'student' || userInfo.value.roleKey === 'admin')
const isTeacher = computed(() => userInfo.value.roleKey === 'teacher' || userInfo.value.roleKey === 'admin')

const studentExamsPath = '/student/exams'
const studentGradesPath = '/student/grades'
const wrongQuestionsPath = '/student/wrong-questions'
const selfStudyPath = '/student/self-study'

const loadUserInfo = () => {
  try {
    userInfo.value = JSON.parse(localStorage.getItem('userInfo') || '{}')
  } catch {
    userInfo.value = {}
  }
}

const getExamStatusClass = (status) => {
  const map = { 0: 'pending', 1: 'ongoing', 2: 'ended' }
  return map[status] || 'pending'
}

const getExamStatusText = (status) => {
  const map = { 0: '未开始', 1: '进行中', 2: '已结束' }
  return map[status] || '未知'
}

const formatDate = (dateStr) => {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  const pad = n => n.toString().padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

const navigateTo = (path) => {
  router.push(path)
}

const goToExam = (exam) => {
  if (exam.status === 1 || exam.status === 0) {
    router.push(`/student/exam/${exam.id}`)
  } else {
    router.push(`/student/grades`)
  }
}

onMounted(() => {
  loadUserInfo()
})
</script>

<style scoped>
.dashboard-container {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.welcome-section {
  width: 100%;
}

.welcome-card {
  position: relative;
  background: linear-gradient(135deg, #4361ee 0%, #6c83f2 50%, #8b9ff5 100%);
  border-radius: var(--radius-xl);
  padding: 36px 40px;
  color: white;
  overflow: hidden;
  box-shadow: 0 12px 40px rgba(67, 97, 238, 0.3);
}

.welcome-text {
  position: relative;
  z-index: 1;
}

.welcome-title {
  font-size: 28px;
  font-weight: 600;
  margin: 0 0 10px 0;
}

.welcome-desc {
  font-size: 15px;
  opacity: 0.9;
  margin: 0;
}

.welcome-decoration {
  position: absolute;
  right: 40px;
  top: 50%;
  transform: translateY(-50%);
}

.stats-section {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
}

.stat-card {
  position: relative;
  background: var(--color-bg-card);
  border-radius: var(--radius-lg);
  padding: 24px;
  display: flex;
  align-items: center;
  gap: 16px;
  cursor: pointer;
  transition: all var(--transition-normal);
  box-shadow: var(--shadow-card);
  overflow: hidden;
}

.stat-card:hover {
  transform: translateY(-4px);
  box-shadow: var(--shadow-card-hover);
}

.stat-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  width: 4px;
  height: 100%;
}

.stat-card.primary::before { background: var(--color-primary); }
.stat-card.success::before { background: var(--color-success); }
.stat-card.warning::before { background: var(--color-warning); }
.stat-card.info::before { background: var(--color-info); }

.stat-icon {
  width: 56px;
  height: 56px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.stat-card.primary .stat-icon {
  background: var(--color-primary-bg);
  color: var(--color-primary);
}
.stat-card.success .stat-icon {
  background: var(--color-success-bg);
  color: var(--color-success);
}
.stat-card.warning .stat-icon {
  background: var(--color-warning-bg);
  color: #e6b450;
}
.stat-card.info .stat-icon {
  background: var(--color-info-bg);
  color: var(--color-info);
}

.stat-info {
  flex: 1;
  min-width: 0;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: var(--color-text-primary);
  line-height: 1.2;
  margin-bottom: 6px;
}

.stat-label {
  font-size: 14px;
  color: var(--color-text-secondary);
}

.stat-arrow {
  color: var(--color-text-placeholder);
  transition: transform var(--transition-fast);
}

.stat-card:hover .stat-arrow {
  transform: translateX(4px);
  color: var(--color-primary);
}

.content-section {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 24px;
}

.section-card {
  border-radius: var(--radius-lg);
  border: none;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text-primary);
}

.title-icon {
  color: var(--color-primary);
}

.exam-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.exam-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 16px;
  border-radius: 12px;
  background: var(--color-bg-page);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.exam-item:hover {
  background: var(--color-primary-bg);
}

.exam-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex-shrink: 0;
}

.exam-dot.pending { background: var(--color-warning); }
.exam-dot.ongoing { background: var(--color-success); }
.exam-dot.ended { background: var(--color-text-placeholder); }

.exam-info {
  flex: 1;
  min-width: 0;
}

.exam-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text-primary);
  margin-bottom: 6px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.exam-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 12px;
  color: var(--color-text-secondary);
}

.exam-status {
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
}

.exam-status.pending {
  background: var(--color-warning-bg);
  color: #e6b450;
}
.exam-status.ongoing {
  background: var(--color-success-bg);
  color: var(--color-success);
}
.exam-status.ended {
  background: var(--color-border-light);
  color: var(--color-text-secondary);
}

.exam-time {
  display: flex;
  align-items: center;
  gap: 4px;
}

.exam-arrow {
  color: var(--color-text-placeholder);
  flex-shrink: 0;
}

.action-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}

.action-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 24px 16px;
  border-radius: 14px;
  background: var(--color-bg-page);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.action-item:hover {
  transform: translateY(-2px);
  background: var(--color-primary-bg);
}

.action-icon {
  width: 52px;
  height: 52px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.action-icon.exam {
  background: var(--color-primary-bg);
  color: var(--color-primary);
}
.action-icon.wrong {
  background: var(--color-danger-bg);
  color: var(--color-danger);
}
.action-icon.ai {
  background: linear-gradient(135deg, #eef1ff 0%, #fff8e6 100%);
  color: var(--color-primary);
}
.action-icon.grade {
  background: var(--color-success-bg);
  color: var(--color-success);
}

.action-text {
  font-size: 13px;
  color: var(--color-text-regular);
  font-weight: 500;
}

@media (max-width: 1200px) {
  .stats-section {
    grid-template-columns: repeat(2, 1fr);
  }
  .content-section {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .stats-section {
    grid-template-columns: 1fr;
  }
  .welcome-card {
    padding: 24px;
  }
  .welcome-title {
    font-size: 22px;
  }
  .welcome-decoration {
    display: none;
  }
}
</style>
