<template>
  <el-dialog v-model="visible" title="历史成绩" width="520px">
    <div v-loading="loading">
      <el-empty v-if="!loading && scores.length === 0" description="暂无成绩记录" />
      <div v-else class="scores-list">
        <div v-for="item in scores" :key="item.recordId" class="score-item">
          <div class="score-header">
            <span class="score-attempt">第 {{ item.attemptNo }} 次</span>
            <el-tag :type="item.isPassed ? 'success' : 'danger'" size="small" effect="dark">
              {{ item.isPassed ? '及格' : '未及格' }}
            </el-tag>
          </div>
          <div class="score-body">
            <div class="score-value" :class="{ pass: item.isPassed }">
              {{ item.score }}<span class="score-total">/{{ item.totalScore }}</span>
            </div>
            <div class="score-meta">
              <span>{{ item.statusText }}</span>
              <span>{{ item.submitTime }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
/**
 * 历史成绩弹窗。
 *
 * 从 StudentExams.vue 抽出的第二步：数据加载随组件一起搬走，
 * 父组件只负责「记录当前选中的考试 + 打开弹窗」。
 *
 * 自助考试（有 selfStudyExamId）与普通考试查询的接口不同，这里保留了原来的分支。
 */
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getMyExamRecords } from '@/api/exam'
import { getExamScores } from '@/api/selfStudyExam'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  exam: { type: Object, default: null }
})

const emit = defineEmits(['update:modelValue'])

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
})

const scores = ref([])
const loading = ref(false)

async function loadScores() {
  const exam = props.exam
  if (!exam) return

  loading.value = true
  scores.value = []
  try {
    if (exam.selfStudyExamId) {
      const res = await getExamScores(exam.selfStudyExamId)
      scores.value = res.data || []
    } else {
      const res = await getMyExamRecords(exam.id)
      scores.value = res.data || []
    }
  } catch (e) {
    ElMessage.error('获取成绩记录失败')
  } finally {
    loading.value = false
  }
}

// 同时监听 visible 与 exam：父组件可能「先设 exam 再打开」，
// 也可能「打开后再换 exam」，两种时序都要能正确加载
watch(
  [() => props.modelValue, () => props.exam],
  ([isOpen]) => {
    if (isOpen && props.exam) loadScores()
  }
)
</script>

<style scoped>
.scores-list { display: flex; flex-direction: column; gap: 12px; max-height: 460px; overflow-y: auto; padding-right: 4px; }
.score-item { background: var(--color-bg-page, #f5f7fa); border-radius: var(--radius-md); padding: 14px 16px; }
.score-header { display: flex; align-items: center; gap: 10px; margin-bottom: 10px; }
.score-attempt { font-weight: 600; color: var(--color-text-primary); }
.score-body { display: flex; align-items: center; justify-content: space-between; }
.score-value { font-size: 28px; font-weight: 700; color: var(--color-text-secondary); }
.score-value.pass { color: var(--color-success, #67c23a); }
.score-total { font-size: 16px; font-weight: 400; color: var(--color-text-placeholder); margin-left: 2px; }
.score-meta { display: flex; flex-direction: column; align-items: flex-end; gap: 4px; font-size: 13px; color: var(--color-text-secondary); }
</style>
