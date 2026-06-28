<template>
  <div class="student-grades">
    <!-- 页面标题 -->
    <div class="page-header">
      <h2>我的成绩</h2>
      <el-button @click="loadData" :loading="loading" plain>
        <el-icon><Refresh /></el-icon>刷新
      </el-button>
    </div>

    <!-- 成绩统计 -->
    <div class="grade-summary" v-if="grades.length > 0">
      <div class="summary-item">
        <div class="summary-icon" style="background: var(--color-primary-bg); color: var(--color-primary);">
          <el-icon :size="22"><Document /></el-icon>
        </div>
        <div class="summary-info">
          <div class="summary-value">{{ grades.length }}</div>
          <div class="summary-label">考试总数</div>
        </div>
      </div>
      <div class="summary-item">
        <div class="summary-icon" style="background: var(--color-success-bg); color: var(--color-success);">
          <el-icon :size="22"><Trophy /></el-icon>
        </div>
        <div class="summary-info">
          <div class="summary-value">{{ passedCount }}</div>
          <div class="summary-label">及格次数</div>
        </div>
      </div>
      <div class="summary-item">
        <div class="summary-icon" style="background: #fff8e6; color: #f0a020;">
          <el-icon :size="22"><TrendCharts /></el-icon>
        </div>
        <div class="summary-info">
          <div class="summary-value">{{ avgScore }}</div>
          <div class="summary-label">平均分</div>
        </div>
      </div>
      <div class="summary-item">
        <div class="summary-icon" style="background: var(--color-danger-bg); color: var(--color-danger);">
          <el-icon :size="22"><Histogram /></el-icon>
        </div>
        <div class="summary-info">
          <div class="summary-value">{{ maxScore }}</div>
          <div class="summary-label">最高分</div>
        </div>
      </div>
    </div>

    <!-- 成绩列表 -->
    <div class="grade-list-section">
      <el-empty v-if="!loading && grades.length === 0" description="暂无成绩记录" />

      <div v-else class="grade-cards" v-loading="loading">
        <div
          v-for="(grade, index) in grades"
          :key="grade.id || index"
          class="grade-card"
          :class="{ passed: grade.isPassed === 1 }"
          :style="{ animationDelay: index * 0.05 + 's' }"
        >
          <div class="grade-card-left">
            <div class="grade-score-circle" :class="grade.isPassed === 1 ? 'pass' : 'fail'">
              <span class="score-number">{{ grade.score }}</span>
              <span class="score-unit">分</span>
            </div>
          </div>
          <div class="grade-card-body">
            <div class="grade-card-top">
              <h3>{{ grade.examTitle }}</h3>
              <el-tag
                :type="grade.isPassed === 1 ? 'success' : 'danger'"
                effect="dark"
                size="small"
              >
                {{ grade.isPassed === 1 ? '及格' : '不及格' }}
              </el-tag>
            </div>
            <div class="grade-card-info">
              <div class="info-item">
                <el-icon><Clock /></el-icon>
                <span>{{ grade.startTime }}</span>
              </div>
              <div class="info-item">
                <el-icon><Check /></el-icon>
                <span>{{ grade.status === 1 ? '手动提交' : '自动提交' }}</span>
              </div>
              <div class="info-item" v-if="grade.switchCount > 0">
                <el-icon><Monitor /></el-icon>
                <span>切屏 {{ grade.switchCount }} 次</span>
              </div>
            </div>
          </div>
          <div class="grade-card-time">
            {{ grade.submitTime }}
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { getMyGrades } from '@/api/exam'

const loading = ref(false)
const grades = ref([])

const passedCount = computed(() => grades.value.filter(g => g.isPassed === 1).length)

const avgScore = computed(() => {
  if (grades.value.length === 0) return 0
  const sum = grades.value.reduce((acc, g) => acc + (g.score || 0), 0)
  return (sum / grades.value.length).toFixed(1)
})

const maxScore = computed(() => {
  if (grades.value.length === 0) return 0
  return Math.max(...grades.value.map(g => g.score || 0))
})

onMounted(() => loadData())

async function loadData() {
  loading.value = true
  try {
    const res = await getMyGrades()
    grades.value = res.data || []
  } finally {
    loading.value = false
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

/* ── 统计卡片 ── */
.grade-summary {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 16px;
  margin-bottom: 24px;
}

.summary-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px 20px;
  background: var(--color-bg-card);
  border-radius: var(--radius-md);
  border: 1px solid var(--color-border-light);
  transition: all var(--transition-normal);
}

.summary-item:hover {
  box-shadow: var(--shadow-card);
  transform: translateY(-1px);
}

.summary-icon {
  width: 44px;
  height: 44px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.summary-value {
  font-size: 22px;
  font-weight: 800;
  color: var(--color-text-primary);
  line-height: 1.2;
}

.summary-label {
  font-size: 12px;
  color: var(--color-text-secondary);
  margin-top: 2px;
}

/* ── 成绩列表 ── */
.grade-list-section {
  background: var(--color-bg-card);
  border-radius: var(--radius-md);
  border: 1px solid var(--color-border-light);
  padding: 24px;
}

.grade-cards {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.grade-card {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 16px 20px;
  border-radius: var(--radius-md);
  border: 1px solid var(--color-border-light);
  background: var(--color-bg-page);
  transition: all var(--transition-normal);
  animation: fadeInUp 0.4s ease backwards;
}

.grade-card:hover {
  border-color: var(--color-primary-light);
  box-shadow: var(--shadow-sm);
  transform: translateX(4px);
}

.grade-card.passed {
  border-left: 3px solid var(--color-success);
}

/* 分数圆圈 */
.grade-card-left {
  flex-shrink: 0;
}

.grade-score-circle {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  transition: all var(--transition-normal);
}

.grade-score-circle.pass {
  background: var(--color-success-bg);
  color: var(--color-success);
  border: 2px solid rgba(6, 214, 160, 0.3);
}

.grade-score-circle.fail {
  background: var(--color-danger-bg);
  color: var(--color-danger);
  border: 2px solid rgba(239, 71, 111, 0.3);
}

.score-number {
  font-size: 20px;
  font-weight: 800;
  line-height: 1;
}

.score-unit {
  font-size: 10px;
  font-weight: 500;
  opacity: 0.8;
}

/* 卡片内容 */
.grade-card-body {
  flex: 1;
  min-width: 0;
}

.grade-card-top {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}

.grade-card-top h3 {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}

.grade-card-info {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
}

.info-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--color-text-secondary);
}

.info-item .el-icon {
  font-size: 14px;
  color: var(--color-text-placeholder);
}

/* 时间 */
.grade-card-time {
  font-size: 12px;
  color: var(--color-text-placeholder);
  flex-shrink: 0;
  white-space: nowrap;
}

/* ── 响应式 ── */
@media (max-width: 768px) {
  .grade-summary {
    grid-template-columns: repeat(2, 1fr);
  }

  .grade-card {
    flex-wrap: wrap;
    gap: 12px;
  }

  .grade-card-time {
    width: 100%;
    text-align: right;
  }
}
</style>
