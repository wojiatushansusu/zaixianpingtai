<template>
  <div>
    <el-page-header @back="$router.push('/teacher/exams')" title="返回考试管理" style="margin-bottom: 20px;">
      <template #content>
        <span>成绩查看 - {{ examTitle }}</span>
      </template>
    </el-page-header>

    <!-- 统计卡片 -->
    <el-row :gutter="16" style="margin-bottom: 20px;" v-if="statistics">
      <el-col :span="4">
        <div class="stat-card">
          <div class="stat-value">{{ statistics.totalStudents }}</div>
          <div class="stat-label">参考人数</div>
        </div>
      </el-col>
      <el-col :span="4">
        <div class="stat-card">
          <div class="stat-value">{{ statistics.avgScore }}</div>
          <div class="stat-label">平均分</div>
        </div>
      </el-col>
      <el-col :span="4">
        <div class="stat-card">
          <div class="stat-value">{{ statistics.maxScore }}</div>
          <div class="stat-label">最高分</div>
        </div>
      </el-col>
      <el-col :span="4">
        <div class="stat-card">
          <div class="stat-value">{{ statistics.minScore }}</div>
          <div class="stat-label">最低分</div>
        </div>
      </el-col>
      <el-col :span="4">
        <div class="stat-card">
          <div class="stat-value" style="color: #67c23a;">{{ statistics.passCount }}</div>
          <div class="stat-label">及格人数</div>
        </div>
      </el-col>
      <el-col :span="4">
        <div class="stat-card">
          <div class="stat-value" style="color: #409eff;">{{ statistics.passRate }}%</div>
          <div class="stat-label">及格率</div>
        </div>
      </el-col>
    </el-row>

    <!-- 成绩列表 -->
    <el-card>
      <template #header>
        <div class="card-header">
          <span>成绩列表</span>
          <el-button type="primary" @click="loadData" :loading="loading">刷新</el-button>
        </div>
      </template>

      <el-table :data="grades" border stripe v-loading="loading">
        <el-table-column prop="studentId" label="学生ID" width="80" />
        <el-table-column prop="studentName" label="姓名" width="120" />
        <el-table-column prop="studentUsername" label="用户名" width="120" />
        <el-table-column prop="score" label="得分" width="80" sortable>
          <template #default="{ row }">
            <span :style="{ color: row.isPassed === 1 ? '#67c23a' : '#f56c6c', fontWeight: 'bold' }">
              {{ row.score }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="是否及格" width="90">
          <template #default="{ row }">
            <el-tag :type="row.isPassed === 1 ? 'success' : 'danger'">
              {{ row.isPassed === 1 ? '及格' : '不及格' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="switchCount" label="切屏次数" width="90" />
        <el-table-column label="提交状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'warning'" size="small">
              {{ row.status === 1 ? '手动提交' : '自动提交' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="submitTime" label="提交时间" width="170" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getExamGrades, getExamStatistics, getExam } from '@/api/exam'

const route = useRoute()
const loading = ref(false)
const examTitle = ref('')
const grades = ref([])
const statistics = ref(null)
const examDuration = ref(60)

onMounted(() => loadData())

async function loadData() {
  const examId = route.params.id
  loading.value = true
  try {
    const [gradesRes, statsRes, examRes] = await Promise.all([
      getExamGrades(examId),
      getExamStatistics(examId),
      getExam(examId)
    ])
    grades.value = gradesRes.data
    statistics.value = statsRes.data
    examTitle.value = examRes.data.title
    examDuration.value = examRes.data.duration || 60
  } finally {
    loading.value = false
  }
}
</script>
