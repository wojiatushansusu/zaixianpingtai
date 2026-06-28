<template>
  <div class="practice-page" v-if="practiceData">
    <!-- 顶部信息栏 -->
    <div class="practice-header">
      <div class="practice-title">{{ practiceData.title }}</div>
      <div class="practice-info">
        <span class="question-count">
          <el-icon><Document /></el-icon>
          {{ practiceData.questions.length }} 题
        </span>
        <span class="answered-count">
          已答 {{ answeredCount }}/{{ practiceData.questions.length }}
        </span>
      </div>
      <div style="display: flex; align-items: center; gap: 12px;">
        <el-button type="primary" @click="handleSubmit" :loading="submitting" size="large">
          <el-icon><CircleCheck /></el-icon>提交练习
        </el-button>
      </div>
    </div>

    <!-- 进度条 -->
    <div class="progress-bar">
      <div class="progress-fill" :style="{ width: progressPercent + '%' }"></div>
    </div>

    <!-- 题目导航 -->
    <div class="question-nav">
      <div class="nav-label">答题卡</div>
      <div class="nav-grid">
        <div
          v-for="(q, index) in practiceData.questions"
          :key="q.id"
          :class="['nav-item', {
            answered: answers[q.id],
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
    <div class="practice-paper">
      <div
        v-for="(question, index) in practiceData.questions"
        :key="question.id"
        :id="'question-' + index"
        :ref="el => questionRefs[index] = el"
        class="question-item"
      >
        <div class="question-header">
          <span class="question-number">{{ index + 1 }}.</span>
          <el-tag size="small" :type="question.type === 1 ? '' : question.type === 2 ? 'warning' : question.type === 3 ? 'success' : 'info'">
            {{ question.typeDesc }}
          </el-tag>
          <span class="question-score">（{{ question.paperScore }}分）</span>
        </div>
        <div class="question-content">{{ question.content }}</div>

        <!-- 单选题 -->
        <div class="question-options" v-if="question.type === 1">
          <el-radio-group v-model="answers[question.id]" @change="handleAnswerChange(question.id)">
            <el-radio v-for="opt in ['A', 'B', 'C', 'D']" :key="opt" :value="opt" class="option-item">
              <span class="option-label">{{ opt }}.</span>
              <span>{{ question['option' + opt] }}</span>
            </el-radio>
          </el-radio-group>
        </div>

        <!-- 多选题 -->
        <div class="question-options" v-if="question.type === 2">
          <el-checkbox-group v-model="multiAnswers[question.id]" @change="handleMultiAnswerChange(question.id)">
            <el-checkbox v-for="opt in ['A', 'B', 'C', 'D']" :key="opt" :value="opt" class="option-item">
              <span class="option-label">{{ opt }}.</span>
              <span>{{ question['option' + opt] }}</span>
            </el-checkbox>
          </el-checkbox-group>
        </div>

        <!-- 判断题 -->
        <div class="question-options" v-if="question.type === 3">
          <el-radio-group v-model="answers[question.id]" @change="handleAnswerChange(question.id)">
            <el-radio value="true" class="option-item">
              <span class="option-label">正确</span>
              <span>{{ question.optionA }}</span>
            </el-radio>
            <el-radio value="false" class="option-item">
              <span class="option-label">错误</span>
              <span>{{ question.optionB }}</span>
            </el-radio>
          </el-radio-group>
        </div>

        <!-- 简答题 -->
        <div class="question-options" v-if="question.type === 4">
          <el-input
            v-model="answers[question.id]"
            type="textarea"
            :rows="4"
            placeholder="请输入你的答案..."
            @blur="handleAnswerChange(question.id)"
          />
        </div>
      </div>
    </div>

    <!-- 底部提交按钮 -->
    <div class="practice-footer">
      <el-button type="primary" size="large" @click="handleSubmit" :loading="submitting">
        <el-icon><CircleCheck /></el-icon>提交练习
      </el-button>
    </div>
  </div>

  <!-- 已完成的练习结果 -->
  <div class="result-page" v-else-if="resultData">
    <!-- 成绩概览 -->
    <div class="result-overview">
      <div class="score-display">
        <div class="score-circle" :class="resultData.accuracy >= 60 ? 'pass' : 'fail'">
          <span class="score-number">{{ resultData.score }}</span>
          <span class="score-unit">/{{ resultData.totalScore }}</span>
        </div>
      </div>
      <div class="result-stats">
        <div class="result-stat-item">
          <span class="stat-value" style="color: var(--color-success);">{{ resultData.correctCount }}</span>
          <span class="stat-label">答对</span>
        </div>
        <div class="result-stat-item">
          <span class="stat-value" style="color: var(--color-danger);">{{ resultData.totalCount - resultData.correctCount }}</span>
          <span class="stat-label">答错</span>
        </div>
        <div class="result-stat-item">
          <span class="stat-value" style="color: var(--color-primary);">{{ resultData.accuracy }}%</span>
          <span class="stat-label">正确率</span>
        </div>
      </div>
      <div class="result-actions">
        <el-button @click="$router.push('/student/wrong-questions')">
          <el-icon><Back /></el-icon>返回错题本
        </el-button>
      </div>
    </div>

    <!-- 每题详情 -->
    <div class="result-details">
      <h3 style="margin-bottom: 16px; font-size: 16px; font-weight: 600;">答题详情</h3>
      <div
        v-for="(detail, index) in resultData.details"
        :key="detail.questionId"
        class="result-card"
        :class="{ correct: detail.isCorrect === 1, wrong: detail.isCorrect === 0 }"
        :style="{ animationDelay: index * 0.04 + 's' }"
      >
        <div class="result-card-header">
          <div class="result-card-title">
            <span class="result-number">{{ index + 1 }}.</span>
            <el-tag size="small" :type="detail.questionType === 1 ? '' : detail.questionType === 2 ? 'warning' : detail.questionType === 3 ? 'success' : 'info'">
              {{ detail.questionTypeDesc }}
            </el-tag>
            <span>{{ detail.content }}</span>
          </div>
          <el-tag :type="detail.isCorrect === 1 ? 'success' : 'danger'" effect="dark" size="small">
            {{ detail.isCorrect === 1 ? '正确' : '错误' }}
            {{ detail.score > 0 ? `+${detail.score}分` : '+0分' }}
          </el-tag>
        </div>

        <!-- 选项（客观题） -->
        <div class="result-options" v-if="detail.questionType !== 4">
          <div
            v-for="opt in getResultOptions(detail)"
            :key="opt.label"
            class="result-option"
            :class="{
              'is-correct': opt.label === detail.correctAnswer,
              'is-wrong': opt.label === detail.studentAnswer && opt.label !== detail.correctAnswer
            }"
          >
            <span class="option-label">{{ opt.label }}.</span>
            <span class="option-text">{{ opt.text }}</span>
            <el-icon v-if="opt.label === detail.correctAnswer" class="option-icon correct"><CircleCheckFilled /></el-icon>
            <el-icon v-if="opt.label === detail.studentAnswer && opt.label !== detail.correctAnswer" class="option-icon wrong"><CircleCloseFilled /></el-icon>
          </div>
        </div>

        <!-- 简答题答案 -->
        <div v-if="detail.questionType === 4" class="result-essay">
          <div class="essay-item">
            <span class="essay-label">你的答案：</span>
            <div class="essay-content">{{ detail.studentAnswer || '未作答' }}</div>
          </div>
          <div class="essay-item">
            <span class="essay-label">参考答案：</span>
            <div class="essay-content">{{ detail.correctAnswer }}</div>
          </div>
        </div>

        <!-- 答案对比 -->
        <div class="result-answers" v-if="detail.questionType !== 4">
          <div class="answer-item">
            <span class="answer-label">你的答案：</span>
            <span class="answer-value" :class="detail.isCorrect === 1 ? 'correct' : 'wrong'">
              {{ formatAnswer(detail.studentAnswer, detail.questionType) }}
            </span>
          </div>
          <div class="answer-item">
            <span class="answer-label">正确答案：</span>
            <span class="answer-value correct">{{ formatAnswer(detail.correctAnswer, detail.questionType) }}</span>
          </div>
        </div>

        <!-- 解析 -->
        <div v-if="detail.analysis" class="result-analysis">
          <div class="analysis-label">
            <el-icon><InfoFilled /></el-icon> 解析
          </div>
          <div class="analysis-content">{{ detail.analysis }}</div>
        </div>

        <!-- AI讲解按钮 -->
        <div class="result-ai-btn">
          <el-button
            type="primary"
            plain
            size="small"
            @click="handleAiExplain(detail)"
          >
            <el-icon><MagicStick /></el-icon> AI 深度讲解
          </el-button>
        </div>
      </div>
    </div>
  </div>

  <!-- 加载中 -->
  <div v-else-if="loading" style="text-align: center; padding: 100px 0;">
    <el-icon :size="40" class="is-loading"><Loading /></el-icon>
    <p style="margin-top: 16px; color: #909399;">正在加载练习...</p>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, nextTick, inject } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getPracticeDetail, savePracticeAnswer, submitPractice, getPracticeResult } from '@/api/wrongQuestion'

const route = useRoute()
const router = useRouter()
const openAiAssistant = inject('openAiAssistant')
const practiceId = parseInt(route.params.practiceId)

const loading = ref(true)
const submitting = ref(false)
const practiceData = ref(null)
const resultData = ref(null)
const answers = reactive({})
const multiAnswers = reactive({})
const currentQuestionIndex = ref(0)
const questionRefs = ref([])

const answeredCount = computed(() => {
  return Object.values(answers).filter(a => a !== undefined && a !== null && a !== '').length
})

const progressPercent = computed(() => {
  if (!practiceData.value) return 0
  return practiceData.value.questions.length > 0
    ? Math.round(answeredCount.value / practiceData.value.questions.length * 100)
    : 0
})

onMounted(async () => {
  try {
    // 先获取练习详情
    const detailRes = await getPracticeDetail(practiceId)
    const detail = detailRes.data

    if (detail.status === 1) {
      // 已完成，获取结果
      const resultRes = await getPracticeResult(practiceId)
      resultData.value = resultRes.data
    } else {
      // 进行中
      practiceData.value = detail

      // 恢复已保存的答案
      if (detail.answers) {
        Object.entries(detail.answers).forEach(([qid, answer]) => {
          const q = detail.questions.find(q => q.id === parseInt(qid))
          if (q) {
            if (q.type === 2) {
              multiAnswers[q.id] = answer.split(',').sort()
              answers[q.id] = answer
            } else {
              answers[q.id] = answer
            }
          }
        })
      }

      loading.value = false
    }
  } catch (e) {
    loading.value = false
    ElMessage.error(e.message || '加载失败')
    router.push('/student/wrong-questions')
  }
  loading.value = false
})

// ==================== 答题逻辑 ====================

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
    await savePracticeAnswer(practiceId, questionId, answers[questionId] || '')
  } catch { /* silent */ }
}

// ==================== 提交 ====================

async function handleSubmit() {
  const unanswered = practiceData.value.questions.filter(q => !answers[q.id] || answers[q.id] === '').length
  let message = '确定要提交练习吗？'
  if (unanswered > 0) {
    message = `还有 ${unanswered} 题未作答，确定要提交练习吗？`
  }

  try {
    await ElMessageBox.confirm(message, '提交练习', {
      type: 'info',
      confirmButtonText: '确定提交',
      cancelButtonText: '继续答题'
    })

    submitting.value = true
    const answerMap = {}
    practiceData.value.questions.forEach(q => {
      if (answers[q.id]) {
        answerMap[q.id] = answers[q.id]
      }
    })

    try {
      await submitPractice(practiceId, answerMap)

      // 提交成功，加载结果
      const resultRes = await getPracticeResult(practiceId)
      resultData.value = resultRes.data
      practiceData.value = null
      ElMessage.success('练习提交成功')
    } catch (e) {
      ElMessage.error(e.message || '提交失败，请重试')
    } finally {
      submitting.value = false
    }
  } catch { /* cancel */ }
}

// ==================== 辅助方法 ====================

// ==================== AI讲解 ====================

function handleAiExplain(detail) {
  openAiAssistant({
    content: detail.content,
    optionA: detail.optionA,
    optionB: detail.optionB,
    optionC: detail.optionC,
    optionD: detail.optionD,
    correctAnswer: detail.correctAnswer,
    studentAnswer: detail.studentAnswer,
    analysis: detail.analysis,
    questionTypeDesc: detail.questionTypeDesc
  })
}

function getResultOptions(detail) {
  const options = []
  if (detail.optionA) options.push({ label: 'A', text: detail.optionA })
  if (detail.optionB) options.push({ label: 'B', text: detail.optionB })
  if (detail.optionC) options.push({ label: 'C', text: detail.optionC })
  if (detail.optionD) options.push({ label: 'D', text: detail.optionD })
  return options
}

function formatAnswer(answer, type) {
  if (!answer) return '未作答'
  if (type === 3) return answer === 'true' ? '正确' : '错误'
  if (type === 2) return answer.split(',').map(a => a.trim()).join('、')
  return answer
}

function scrollToQuestion(index) {
  currentQuestionIndex.value = index
  const el = questionRefs.value[index]
  if (el) {
    el.scrollIntoView({ behavior: 'smooth', block: 'center' })
  }
}
</script>

<style scoped>
/* ── 练习页头部 ── */
.practice-page {
  max-width: 960px;
  margin: 0 auto;
  padding: 0 16px;
}
.practice-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  margin-bottom: 12px;
  position: sticky;
  top: 0;
  z-index: 100;
  flex-wrap: wrap;
  gap: 12px;
}
.practice-title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}
.practice-info {
  display: flex;
  align-items: center;
  gap: 16px;
  font-size: 14px;
  color: #606266;
}

/* ── 进度条 ── */
.progress-bar {
  height: 4px;
  background: #e4e7ed;
  border-radius: 2px;
  margin-bottom: 16px;
  overflow: hidden;
}
.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, var(--color-primary), #7c8cf8);
  border-radius: 2px;
  transition: width 0.4s ease;
}

/* ── 题目导航 ── */
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
.nav-item:hover { border-color: #409eff; color: #409eff; }
.nav-item.answered { background: #409eff; border-color: #409eff; color: #fff; }
.nav-item.current { border-color: #e6a23c; box-shadow: 0 0 0 2px rgba(230, 162, 60, 0.3); }
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
.legend-dot.answered-dot { background: #409eff; border-color: #409eff; }
.legend-dot.current-dot { border-color: #e6a23c; box-shadow: 0 0 0 1px rgba(230, 162, 60, 0.3); }

/* ── 题目 ── */
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
.question-number { font-size: 16px; font-weight: 600; color: #303133; }
.question-score { font-size: 13px; color: #909399; }
.question-content { font-size: 15px; margin-bottom: 20px; line-height: 1.6; color: #303133; }
.question-options { padding-left: 28px; }
.option-item {
  display: flex;
  align-items: flex-start;
  margin-bottom: 12px;
  padding: 10px 16px;
  border-radius: 6px;
  transition: background 0.2s;
}
.option-item:hover { background: #f5f7fa; }
.option-label { font-weight: 500; margin-right: 8px; min-width: 20px; }
.practice-footer { text-align: center; padding: 24px 0 40px; }

/* ── 结果页 ── */
.result-page {
  max-width: 960px;
  margin: 0 auto;
  padding: 0 16px;
}
.result-overview {
  background: var(--color-bg-card);
  border-radius: var(--radius-md);
  border: 1px solid var(--color-border-light);
  padding: 40px;
  text-align: center;
  margin-bottom: 24px;
  animation: fadeInUp 0.4s ease;
}
.score-display { margin-bottom: 24px; }
.score-circle {
  width: 120px;
  height: 120px;
  border-radius: 50%;
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  margin: 0 auto;
}
.score-circle.pass {
  background: var(--color-success-bg);
  color: var(--color-success);
  border: 3px solid rgba(6, 214, 160, 0.3);
}
.score-circle.fail {
  background: var(--color-danger-bg);
  color: var(--color-danger);
  border: 3px solid rgba(239, 71, 111, 0.3);
}
.score-circle .score-number { font-size: 32px; font-weight: 800; line-height: 1; }
.score-circle .score-unit { font-size: 14px; opacity: 0.7; }
.result-stats {
  display: flex;
  justify-content: center;
  gap: 40px;
  margin-bottom: 24px;
}
.result-stat-item { text-align: center; }
.result-stat-item .stat-value { font-size: 24px; font-weight: 700; display: block; }
.result-stat-item .stat-label { font-size: 13px; color: #909399; }
.result-actions { margin-top: 8px; }

/* ── 结果详情卡片 ── */
.result-details {
  background: var(--color-bg-card);
  border-radius: var(--radius-md);
  border: 1px solid var(--color-border-light);
  padding: 24px;
}
.result-card {
  padding: 16px;
  border-radius: var(--radius-md);
  border: 1px solid var(--color-border-light);
  background: var(--color-bg-page);
  margin-bottom: 12px;
  animation: fadeInUp 0.4s ease backwards;
}
.result-card.correct { border-left: 3px solid var(--color-success); }
.result-card.wrong { border-left: 3px solid var(--color-danger); }
.result-card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 10px;
}
.result-card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  font-size: 14px;
}
.result-number { font-weight: 700; flex-shrink: 0; }
.result-options {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 12px;
}
.result-option {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--color-border-light);
  background: #fff;
}
.result-option.is-correct { border-color: var(--color-success); background: rgba(6, 214, 160, 0.06); }
.result-option.is-wrong { border-color: var(--color-danger); background: rgba(239, 71, 111, 0.06); }
.option-icon { font-size: 18px; flex-shrink: 0; }
.option-icon.correct { color: var(--color-success); }
.option-icon.wrong { color: var(--color-danger); }

.result-answers {
  display: flex;
  gap: 20px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.answer-item { font-size: 14px; display: flex; align-items: center; gap: 6px; }
.answer-label { font-weight: 500; }
.answer-value { font-weight: 600; }
.answer-value.correct { color: var(--color-success); }
.answer-value.wrong { color: var(--color-danger); }

.result-essay {
  margin-bottom: 12px;
}
.essay-item {
  margin-bottom: 10px;
}
.essay-label { font-size: 13px; font-weight: 600; color: var(--color-text-secondary); margin-bottom: 4px; }
.essay-content {
  font-size: 14px;
  padding: 10px 14px;
  border-radius: var(--radius-sm);
  background: #fff;
  border: 1px solid var(--color-border-light);
  line-height: 1.6;
  color: var(--color-text-regular);
}

.result-analysis {
  padding: 12px 16px;
  border-radius: var(--radius-sm);
  background: rgba(67, 97, 238, 0.04);
  border: 1px solid rgba(67, 97, 238, 0.1);
}
.analysis-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-primary);
  margin-bottom: 6px;
  display: flex;
  align-items: center;
  gap: 4px;
}
.analysis-content { font-size: 14px; color: var(--color-text-regular); line-height: 1.6; }

.result-ai-btn {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed var(--color-border-light);
}

/* ── 响应式 ── */
@media (max-width: 768px) {
  .practice-header { flex-direction: column; align-items: flex-start; }
  .result-stats { gap: 20px; }
}
</style>
