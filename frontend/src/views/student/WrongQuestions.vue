<template>
  <div class="wrong-questions">
    <!-- 页面标题 -->
    <div class="page-header">
      <h2>错题本</h2>
      <div style="display: flex; gap: 12px; align-items: center;">
        <el-button @click="loadData" :loading="loading" plain>
          <el-icon><Refresh /></el-icon>刷新
        </el-button>
      </div>
    </div>

    <!-- 错题统计 -->
    <div class="stats-summary" v-if="stats.totalWrong > 0">
      <div class="stat-item">
        <div class="stat-icon" style="background: var(--color-danger-bg); color: var(--color-danger);">
          <el-icon :size="22"><WarningFilled /></el-icon>
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ stats.totalWrong }}</div>
          <div class="stat-label">错题总数</div>
        </div>
      </div>
      <div class="stat-item">
        <div class="stat-icon" style="background: var(--color-primary-bg); color: var(--color-primary);">
          <el-icon :size="22"><List /></el-icon>
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ stats.singleChoiceWrong }}</div>
          <div class="stat-label">单选错题</div>
        </div>
      </div>
      <div class="stat-item">
        <div class="stat-icon" style="background: #fff8e6; color: #f0a020;">
          <el-icon :size="22"><Checked /></el-icon>
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ stats.multiChoiceWrong }}</div>
          <div class="stat-label">多选错题</div>
        </div>
      </div>
      <div class="stat-item">
        <div class="stat-icon" style="background: var(--color-success-bg); color: var(--color-success);">
          <el-icon :size="22"><Select /></el-icon>
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ stats.judgeWrong }}</div>
          <div class="stat-label">判断错题</div>
        </div>
      </div>
    </div>

    <!-- 练习历史 -->
    <div class="history-section" v-if="practiceHistory.length > 0">
      <div class="section-title" @click="showHistory = !showHistory">
        <el-icon><Clock /></el-icon>
        <span>练习历史 ({{ practiceHistory.length }})</span>
        <el-icon class="expand-icon" :class="{ expanded: showHistory }"><ArrowDown /></el-icon>
      </div>
      <div v-show="showHistory" class="history-list">
        <div
          v-for="item in practiceHistory"
          :key="item.id"
          class="history-item"
          @click="$router.push(`/student/practice/${item.id}`)"
        >
          <div class="history-left">
            <span class="history-title">{{ item.title }}</span>
            <span class="history-time">{{ item.startTime }}</span>
          </div>
          <div class="history-right">
            <div class="history-score" :class="item.status === 1 ? 'done' : 'ongoing'">
              {{ item.status === 1 ? `${item.score}/${item.totalScore}` : '未完成' }}
            </div>
            <div v-if="item.status === 1" class="history-accuracy">
              正确率 {{ item.totalCount > 0 ? Math.round(item.correctCount * 100 / item.totalCount) : 0 }}%
            </div>
          </div>
          <el-icon class="history-arrow"><ArrowRight /></el-icon>
        </div>
      </div>
    </div>

    <!-- 考试分组列表 -->
    <div class="wrong-list-section">
      <el-empty v-if="!loading && examGroups.length === 0" description="暂无错题，继续保持！" />

      <div v-else v-loading="loading" class="exam-group-list">
        <div
          v-for="(group, index) in examGroups"
          :key="group.examRecordId"
          class="exam-group-card"
          :style="{ animationDelay: index * 0.04 + 's' }"
        >
          <!-- 考试信息行（点击展开/收起） -->
          <div class="group-header" @click="toggleGroup(group.examRecordId)">
            <div class="group-left">
              <el-icon class="expand-icon" :class="{ expanded: expandedGroup === group.examRecordId }">
                <ArrowDown />
              </el-icon>
              <div class="group-info">
                <div class="group-title">
                  <el-icon><Document /></el-icon>
                  <span>{{ group.examTitle }}</span>
                </div>
                <div class="group-meta">
                  <el-tag type="danger" size="small" effect="plain">
                    {{ group.wrongCount }} 道错题
                  </el-tag>
                  <el-tag :type="group.isPassed ? 'success' : 'danger'" size="small" effect="dark">
                    {{ group.totalScore }} 分
                  </el-tag>
                  <span class="meta-time">{{ group.submitTime }}</span>
                </div>
              </div>
            </div>
            <el-icon class="group-arrow"><ArrowRight /></el-icon>
          </div>

          <!-- 展开的错题列表 -->
          <div v-if="expandedGroup === group.examRecordId" class="group-wrong-list" v-loading="loadingWrongDetail">
            <div
              v-for="(q, qIdx) in getGroupWrongQuestions(group.examRecordId)"
              :key="q.questionId"
              class="wrong-card"
            >
              <div class="wrong-card-body" @click="toggleExpand(q.questionId)">
                <div class="wrong-card-header">
                  <div class="wrong-card-title">
                    <span class="wrong-number">{{ qIdx + 1 }}.</span>
                    <el-tag size="small" :type="getTypeTagType(q.questionType)" effect="plain">
                      {{ q.questionTypeDesc }}
                    </el-tag>
                    <span class="wrong-content">{{ q.content }}</span>
                  </div>
                  <div class="wrong-card-badges">
                    <el-tag :type="getDifficultyType(q.difficulty)" size="small" effect="plain">
                      {{ getDifficultyText(q.difficulty) }}
                    </el-tag>
                    <el-icon class="expand-icon" :class="{ expanded: expandedId === q.questionId }">
                      <ArrowDown />
                    </el-icon>
                  </div>
                </div>
              </div>

              <!-- 展开详情 -->
              <div v-if="expandedId === q.questionId" class="wrong-detail">
                <div class="detail-options" v-if="q.questionType !== 4">
                  <div
                    v-for="opt in getOptions(q)"
                    :key="opt.label"
                    class="detail-option"
                    :class="{
                      'is-correct': opt.label === q.correctAnswer,
                      'is-wrong': opt.label === q.studentAnswer && opt.label !== q.correctAnswer
                    }"
                  >
                    <span class="option-label">{{ opt.label }}.</span>
                    <span class="option-text">{{ opt.text }}</span>
                    <el-icon v-if="opt.label === q.correctAnswer" class="option-icon correct"><CircleCheckFilled /></el-icon>
                    <el-icon v-if="opt.label === q.studentAnswer && opt.label !== q.correctAnswer" class="option-icon wrong"><CircleCloseFilled /></el-icon>
                  </div>
                </div>

                <div class="detail-answers">
                  <div class="answer-item wrong-answer">
                    <span class="answer-label">你的答案：</span>
                    <span class="answer-value">{{ formatAnswer(q.studentAnswer, q.questionType) }}</span>
                  </div>
                  <div class="answer-item correct-answer">
                    <span class="answer-label">正确答案：</span>
                    <span class="answer-value">{{ formatAnswer(q.correctAnswer, q.questionType) }}</span>
                  </div>
                </div>

                <div v-if="q.analysis" class="detail-analysis">
                  <div class="analysis-label">
                    <el-icon><InfoFilled /></el-icon> 解析
                  </div>
                  <div class="analysis-content">{{ q.analysis }}</div>
                </div>

                <div class="detail-ai-btn">
                  <el-button
                    type="primary" plain size="small"
                    @click.stop="handleAiExplain(q)"
                  >
                    <el-icon><MagicStick /></el-icon> AI 深度讲解
                  </el-button>
                </div>
              </div>
            </div>

            <!-- 错题列表底部：创建练习按钮 -->
            <div class="group-actions" v-if="getGroupWrongQuestions(group.examRecordId).length > 0">
              <el-button type="primary" @click.stop="handleCreatePracticeFromGroup(group)">
                <el-icon><EditPen /></el-icon>
                练习该次错题 ({{ getGroupWrongQuestions(group.examRecordId).length }})
              </el-button>
              <el-button type="warning" plain @click.stop="handleAiExplainGroup(group)">
                <el-icon><MagicStick /></el-icon>
                AI讲解全部
              </el-button>
              <el-button
                type="success" plain
                :loading="generatingGroupId === group.examRecordId"
                @click.stop="handleCreateAiPaperFromGroup(group)"
              >
                <el-icon><MagicStick /></el-icon>
                按错题出专项卷
              </el-button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, inject } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getWrongQuestionStats, getWrongQuestionsGrouped, getWrongQuestionsByExamRecord, createPractice, getPracticeHistory } from '@/api/wrongQuestion'
import { createFromWrongQuestions, generateSelfStudyExam } from '@/api/selfStudyExam'
import { reportQuality } from '@/utils/qualityNotice'

const router = useRouter()
const openAiAssistant = inject('openAiAssistant')
const loading = ref(false)
const stats = ref({
  totalWrong: 0, singleChoiceWrong: 0, multiChoiceWrong: 0,
  judgeWrong: 0, essayWrong: 0,
  easyWrong: 0, mediumWrong: 0, hardWrong: 0
})
const examGroups = ref([])
const expandedGroup = ref(null)
const expandedId = ref(null)
const practiceHistory = ref([])
const showHistory = ref(false)
// 缓存每个考试分组的错题详情
const groupWrongCache = ref({})
const loadingWrongDetail = ref(false)
const generatingGroupId = ref(null)   // 正在为哪个考试记录生成专项卷

onMounted(() => {
  loadData()
  loadHistory()
})

async function loadData() {
  loading.value = true
  expandedGroup.value = null
  expandedId.value = null
  groupWrongCache.value = {}
  try {
    const [statsRes, groupsRes] = await Promise.all([
      getWrongQuestionStats(),
      getWrongQuestionsGrouped()
    ])
    stats.value = statsRes.data || stats.value
    examGroups.value = (groupsRes.data || []).map(g => ({
      ...g,
      // 格式化时间显示
      submitTimeDisplay: g.submitTime ? g.submitTime.replace('T', ' ').substring(0, 16) : ''
    }))
  } finally {
    loading.value = false
  }
}

async function loadHistory() {
  try {
    const res = await getPracticeHistory({ params: {}, silent: true })
    practiceHistory.value = (res.data || []).slice(0, 10)
  } catch { /* ignore */ }
}

async function toggleGroup(examRecordId) {
  if (expandedGroup.value === examRecordId) {
    expandedGroup.value = null
    return
  }
  expandedGroup.value = examRecordId
  expandedId.value = null

  // 加载该分组的错题详情（带缓存）
  if (!groupWrongCache.value[examRecordId]) {
    loadingWrongDetail.value = true
    try {
      const res = await getWrongQuestionsByExamRecord(examRecordId)
      groupWrongCache.value[examRecordId] = res.data || []
    } catch (e) {
      ElMessage.error('加载错题详情失败')
      groupWrongCache.value[examRecordId] = []
    } finally {
      loadingWrongDetail.value = false
    }
  }
}

function getGroupWrongQuestions(examRecordId) {
  return groupWrongCache.value[examRecordId] || []
}

function toggleExpand(questionId) {
  expandedId.value = expandedId.value === questionId ? null : questionId
}

function getTypeTagType(type) {
  if (type === 1) return ''
  if (type === 2) return 'warning'
  if (type === 3) return 'success'
  return 'info'
}

function getDifficultyType(difficulty) {
  if (difficulty === 1) return 'success'
  if (difficulty === 2) return 'warning'
  if (difficulty === 3) return 'danger'
  return 'info'
}

function getDifficultyText(difficulty) {
  if (difficulty === 1) return '简单'
  if (difficulty === 2) return '中等'
  if (difficulty === 3) return '困难'
  return '未知'
}

function getOptions(q) {
  const options = []
  if (q.optionA) options.push({ label: 'A', text: q.optionA })
  if (q.optionB) options.push({ label: 'B', text: q.optionB })
  if (q.optionC) options.push({ label: 'C', text: q.optionC })
  if (q.optionD) options.push({ label: 'D', text: q.optionD })
  return options
}

function formatAnswer(answer, type) {
  if (!answer) return '未作答'
  if (type === 3) return answer === 'true' ? '正确' : '错误'
  if (type === 2) return answer.split(',').map(a => a.trim()).join('、')
  return answer
}

function handleAiExplain(q) {
  openAiAssistant({
    content: q.content,
    optionA: q.optionA,
    optionB: q.optionB,
    optionC: q.optionC,
    optionD: q.optionD,
    correctAnswer: q.correctAnswer,
    studentAnswer: q.studentAnswer,
    analysis: q.analysis,
    questionTypeDesc: q.questionTypeDesc
  })
}

function handleAiExplainGroup(group) {
  const questions = getGroupWrongQuestions(group.examRecordId)
  if (questions.length === 0) return
  openAiAssistant(questions.map(q => ({
    content: q.content,
    optionA: q.optionA,
    optionB: q.optionB,
    optionC: q.optionC,
    optionD: q.optionD,
    correctAnswer: q.correctAnswer,
    studentAnswer: q.studentAnswer,
    analysis: q.analysis,
    questionTypeDesc: q.questionTypeDesc
  })))
}

/**
 * 以这组错题为考点，让 AI 出一份新的专项卷。
 *
 * 与「练习该次错题」的区别：练习是把原题再做一遍，这里是换一批新题考同样的考点。
 * 前者治"记不住"，后者治"没真懂"。
 */
async function handleCreateAiPaperFromGroup(group) {
  const questions = getGroupWrongQuestions(group.examRecordId)
  if (questions.length === 0) return

  try {
    await ElMessageBox.confirm(
      `将以「${group.examTitle}」中的 ${questions.length} 道错题为考点，生成一份新的专项卷。\n出题与质量校验需要一两分钟，页面会保持等待。`,
      'AI 生成专项巩固卷',
      { type: 'info', confirmButtonText: '开始生成', cancelButtonText: '取消' }
    )
  } catch {
    return
  }

  generatingGroupId.value = group.examRecordId
  try {
    // 与「AI创建考试」同一套流程：先建记录，再触发生成
    const createRes = await createFromWrongQuestions({ examRecordId: group.examRecordId })
    const newExam = createRes.data
    ElMessage.info('专项卷已创建，正在生成题目，请耐心等待...')

    const genRes = await generateSelfStudyExam(newExam.id)
    reportQuality(genRes?.data?.quality, '专项卷生成成功！')

    router.push('/student/self-study')
  } catch (e) {
    ElMessage.error(e.message || '生成失败，请稍后重试')
  } finally {
    generatingGroupId.value = null
  }
}

async function handleCreatePracticeFromGroup(group) {
  const questions = getGroupWrongQuestions(group.examRecordId)
  if (questions.length === 0) return
  try {
    await ElMessageBox.confirm(
      `将使用「${group.examTitle}」中的 ${questions.length} 道错题创建练习，确定吗？`,
      '创建错题练习',
      { type: 'info', confirmButtonText: '开始练习', cancelButtonText: '取消' }
    )
    const res = await createPractice({
      title: `${group.examTitle} - 错题练习`,
      questionIds: questions.map(q => q.questionId)
    })
    ElMessage.success('练习创建成功')
    router.push(`/student/practice/${res.data.id}`)
  } catch { /* cancel */ }
}
</script>

<style scoped>
/* ── 页面标题 ── */
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  flex-wrap: wrap;
  gap: 12px;
}
.page-header h2 {
  font-size: 20px;
  font-weight: 700;
  color: var(--color-text-primary);
}

/* ── 统计卡片 ── */
.stats-summary {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  gap: 14px;
  margin-bottom: 20px;
}
.stat-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 18px;
  background: var(--color-bg-card);
  border-radius: var(--radius-md);
  border: 1px solid var(--color-border-light);
  transition: all var(--transition-normal);
}
.stat-item:hover {
  box-shadow: var(--shadow-card);
  transform: translateY(-1px);
}
.stat-icon {
  width: 42px;
  height: 42px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.stat-value {
  font-size: 22px;
  font-weight: 800;
  color: var(--color-text-primary);
  line-height: 1.2;
}
.stat-label {
  font-size: 12px;
  color: var(--color-text-secondary);
  margin-top: 2px;
}

/* ── 练习历史 ── */
.history-section {
  background: var(--color-bg-card);
  border-radius: var(--radius-md);
  border: 1px solid var(--color-border-light);
  margin-bottom: 20px;
  overflow: hidden;
}
.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 20px;
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-primary);
  cursor: pointer;
  user-select: none;
  transition: background var(--transition-fast);
}
.section-title:hover { background: var(--color-bg-page); }
.section-title .expand-icon {
  margin-left: auto;
  transition: transform 0.3s;
}
.section-title .expand-icon.expanded { transform: rotate(180deg); }
.history-list { border-top: 1px solid var(--color-border-light); }
.history-item {
  display: flex;
  align-items: center;
  padding: 12px 20px;
  border-bottom: 1px solid var(--color-border-light);
  cursor: pointer;
  transition: background var(--transition-fast);
}
.history-item:last-child { border-bottom: none; }
.history-item:hover { background: var(--color-bg-page); }
.history-left { flex: 1; min-width: 0; }
.history-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text-primary);
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.history-time { font-size: 12px; color: var(--color-text-placeholder); }
.history-right { display: flex; align-items: center; gap: 12px; margin: 0 12px; }
.history-score { font-size: 14px; font-weight: 700; }
.history-score.done { color: var(--color-primary); }
.history-score.ongoing { color: var(--color-text-secondary); }
.history-accuracy { font-size: 12px; color: var(--color-text-secondary); }
.history-arrow { color: var(--color-text-placeholder); font-size: 14px; }

/* ── 考试分组列表 ── */
.wrong-list-section {
  background: var(--color-bg-card);
  border-radius: var(--radius-md);
  border: 1px solid var(--color-border-light);
  padding: 20px;
}
.exam-group-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

/* 考试分组卡片 */
.exam-group-card {
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-md);
  background: var(--color-bg-page);
  overflow: hidden;
  transition: all var(--transition-normal);
  animation: fadeInUp 0.4s ease backwards;
}
.exam-group-card:hover {
  border-color: var(--color-border);
  box-shadow: var(--shadow-sm);
}
.group-header {
  display: flex;
  align-items: center;
  padding: 16px 20px;
  cursor: pointer;
  transition: background var(--transition-fast);
  user-select: none;
}
.group-header:hover { background: rgba(67, 97, 238, 0.03); }
.group-left {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}
.group-left .expand-icon {
  font-size: 16px;
  color: var(--color-text-placeholder);
  transition: transform 0.3s;
  flex-shrink: 0;
}
.group-left .expand-icon.expanded { transform: rotate(180deg); }
.group-info { flex: 1; min-width: 0; }
.group-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin-bottom: 6px;
}
.group-title .el-icon { color: var(--color-primary); flex-shrink: 0; }
.group-title span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.group-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
}
.meta-time {
  color: var(--color-text-placeholder);
  margin-left: 4px;
}
.group-arrow {
  color: var(--color-text-placeholder);
  font-size: 14px;
  flex-shrink: 0;
}

/* 展开的错题列表 */
.group-wrong-list {
  border-top: 1px solid var(--color-border-light);
  padding: 12px 16px 16px;
  animation: fadeIn 0.3s ease;
}

/* 错题卡片 */
.wrong-card {
  display: flex;
  flex-direction: column;
  padding: 14px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--color-border-light);
  background: #fff;
  margin-bottom: 10px;
  transition: all var(--transition-fast);
}
.wrong-card:last-child { margin-bottom: 0; }
.wrong-card:hover { border-color: var(--color-border); }
.wrong-card-body { cursor: pointer; }
.wrong-card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}
.wrong-card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 0;
}
.wrong-number {
  font-size: 14px;
  font-weight: 700;
  color: var(--color-text-primary);
  flex-shrink: 0;
}
.wrong-content {
  font-size: 14px;
  color: var(--color-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.wrong-card-badges {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}
.wrong-card-badges .expand-icon {
  color: var(--color-text-placeholder);
  transition: transform 0.3s;
  cursor: pointer;
}
.wrong-card-badges .expand-icon.expanded { transform: rotate(180deg); }

/* 展开详情 */
.wrong-detail {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed var(--color-border);
  animation: fadeIn 0.3s ease;
}
.detail-options {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 12px;
}
.detail-option {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--color-border-light);
  background: #fff;
}
.detail-option.is-correct {
  border-color: var(--color-success);
  background: rgba(6, 214, 160, 0.06);
}
.detail-option.is-wrong {
  border-color: var(--color-danger);
  background: rgba(239, 71, 111, 0.06);
}
.option-label {
  font-weight: 600;
  font-size: 14px;
  min-width: 20px;
  color: var(--color-text-primary);
}
.option-text {
  flex: 1;
  font-size: 14px;
  color: var(--color-text-regular);
}
.option-icon { font-size: 18px; flex-shrink: 0; }
.option-icon.correct { color: var(--color-success); }
.option-icon.wrong { color: var(--color-danger); }
.detail-answers {
  display: flex;
  gap: 20px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.answer-item { font-size: 14px; display: flex; align-items: center; gap: 6px; }
.answer-label { font-weight: 500; }
.wrong-answer .answer-value { color: var(--color-danger); font-weight: 600; }
.correct-answer .answer-value { color: var(--color-success); font-weight: 600; }
.detail-analysis {
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
.analysis-content {
  font-size: 14px;
  color: var(--color-text-regular);
  line-height: 1.6;
}
.detail-ai-btn {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed var(--color-border-light);
}

/* 分组底部操作按钮 */
.group-actions {
  display: flex;
  gap: 12px;
  justify-content: center;
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px solid var(--color-border-light);
}

/* ── 响应式 ── */
@media (max-width: 768px) {
  .stats-summary { grid-template-columns: repeat(2, 1fr); }
  .page-header { flex-direction: column; align-items: flex-start; }
  .wrong-card-header { flex-direction: column; }
  .wrong-card-badges { width: 100%; justify-content: flex-end; }
  .detail-answers { flex-direction: column; gap: 8px; }
  .group-actions { flex-direction: column; }
  .group-meta { flex-wrap: wrap; }
}
</style>
