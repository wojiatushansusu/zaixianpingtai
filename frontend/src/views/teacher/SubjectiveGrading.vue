<template>
  <div>
    <el-card class="page-card">
      <template #header>
        <div class="card-header">
          <span>主观题批改 - {{ examTitle }}</span>
          <el-button @click="loadUngraded" :loading="loading">
            <el-icon><Refresh /></el-icon>刷新
          </el-button>
        </div>
      </template>

      <el-alert v-if="ungradedAnswers.length === 0 && !loading" 
        title="所有主观题已批改完成" type="success" :closable="false" show-icon />

      <div v-for="item in ungradedAnswers" :key="item.id"
        class="grading-item" :id="'answer-' + item.id">
        <div class="grading-header">
          <div class="grading-index">答卷ID: {{ item.id }}</div>
          <el-tag size="small">{{ getTypeName(item.questionType) }}</el-tag>
          <span class="grading-score-info">满分: {{ item.questionScore || '-' }}分</span>
        </div>
        <div class="grading-content">
          <div class="question-text">{{ item.questionContent }}</div>
          <div class="answer-text">
            <div class="answer-label">学生答案:</div>
            <div class="answer-value">{{ item.studentAnswer || '（未作答）' }}</div>
          </div>
        </div>
        <div class="grading-action">
          <div class="score-input">
            <span>评分:</span>
            <el-input-number v-model="scores[item.id]" :min="0" :max="item.questionScore || 100" 
              size="small" style="width: 120px;" />
            <span style="color: #909399; font-size: 12px;">/ {{ item.questionScore || '-' }}</span>
          </div>
          <div class="comment-input">
            <el-input v-model="comments[item.id]" placeholder="批注（选填）" size="small"
              style="width: 300px;" />
          </div>
          <el-button type="primary" size="small" @click="submitGrade(item)" :loading="submitting[item.id]">
            提交批改
          </el-button>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getExam } from '@/api/exam'
import { getUngradedAnswers, gradeAnswer } from '@/api/monitor'

const route = useRoute()
const examId = parseInt(route.params.examId)
const loading = ref(false)
const examTitle = ref('')
const ungradedAnswers = ref([])
// 使用answerId作为key，避免splice后index混乱
const scores = reactive({})
const comments = reactive({})
const submitting = reactive({})

onMounted(async () => {
  try {
    const res = await getExam(examId, { silent: true })
    examTitle.value = res.data.title || '未知考试'
  } catch (e) {
    examTitle.value = '未知考试'
  }
  await loadUngraded()
})

async function loadUngraded() {
  loading.value = true
  try {
    const res = await getUngradedAnswers(examId)
    ungradedAnswers.value = res.data || []
    // 以answerId为key初始化评分
    ungradedAnswers.value.forEach(item => {
      if (scores[item.id] === undefined) scores[item.id] = 0
    })
  } catch (e) {
    console.error('加载待批改列表失败:', e)
  } finally {
    loading.value = false
  }
}

async function submitGrade(item) {
  const score = scores[item.id]
  if (score === undefined || score === null) {
    ElMessage.warning('请输入评分')
    return
  }
  submitting[item.id] = true
  try {
    await gradeAnswer(item.id, score, comments[item.id] || null)
    ElMessage.success('批改成功')
    // 从列表中移除已批改的题目（直接按id过滤，不影响其他项）
    ungradedAnswers.value = ungradedAnswers.value.filter(a => a.id !== item.id)
  } catch (e) {
    ElMessage.error(e.message || '批改失败')
  } finally {
    submitting[item.id] = false
  }
}

function getTypeName(type) {
  const map = { 1: '单选题', 2: '多选题', 3: '判断题', 4: '简答题' }
  return map[type] || '未知'
}
</script>

<style scoped>
.grading-item {
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 16px;
}
.grading-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid #f0f2f5;
}
.grading-index {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}
.grading-score-info {
  margin-left: auto;
  font-size: 13px;
  color: #909399;
}
.grading-content {
  margin-bottom: 12px;
}
.question-text {
  font-size: 15px;
  color: #303133;
  margin-bottom: 12px;
  padding: 12px;
  background: #f5f7fa;
  border-radius: 6px;
}
.answer-label {
  font-size: 13px;
  color: #909399;
  margin-bottom: 4px;
}
.answer-value {
  font-size: 14px;
  color: #606266;
  padding: 12px;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  white-space: pre-wrap;
  min-height: 60px;
}
.grading-action {
  display: flex;
  align-items: center;
  gap: 16px;
  padding-top: 12px;
  border-top: 1px solid #f0f2f5;
}
.score-input {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  color: #303133;
}
</style>
