<template>
  <div>
    <el-card class="page-card">
      <template #header>
        <div class="card-header">
          <span>考试管理</span>
          <el-button type="primary" @click="openDialog()">
            <el-icon><Plus /></el-icon>发布考试
          </el-button>
        </div>
      </template>

      <!-- 搜索栏 -->
      <div class="search-bar">
        <el-input v-model="searchParams.title" placeholder="考试名称" clearable style="width: 200px" @keyup.enter="loadData" />
        <el-select v-model="searchParams.status" placeholder="状态" clearable style="width: 140px">
          <el-option label="未开始" :value="0" />
          <el-option label="进行中" :value="1" />
          <el-option label="已结束" :value="2" />
        </el-select>
        <el-button type="primary" @click="loadData">搜索</el-button>
        <el-button @click="resetSearch">重置</el-button>
      </div>

      <!-- 表格 -->
      <el-table :data="tableData" border stripe v-loading="loading">
        <el-table-column label="ID" width="60">
          <template #default="{ $index }">
            {{ (searchParams.pageNum - 1) * searchParams.pageSize + $index + 1 }}
          </template>
        </el-table-column>
        <el-table-column prop="title" label="考试名称" width="180" />
        <el-table-column prop="paperTitle" label="试卷" width="140" />
        <el-table-column label="时间" width="320">
          <template #default="{ row }">
            <div style="font-size: 12px;">
              <div>{{ row.startTime }} ~ {{ row.endTime }}</div>
              <div>时长: {{ row.duration }}分钟</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 0 ? 'info' : row.status === 1 ? 'success' : 'danger'">
              {{ row.status === 0 ? '未开始' : row.status === 1 ? '进行中' : '已结束' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="whitelistCount" label="考生数" width="80" />
        <el-table-column prop="submittedCount" label="已提交" width="80" />
        <el-table-column prop="maxSwitchCount" label="切屏限制" width="80" />
        <el-table-column label="身份核验" width="80">
          <template #default="{ row }">
            <el-tag :type="row.requireFaceVerify === 1 ? 'warning' : 'info'" size="small">
              {{ row.requireFaceVerify === 1 ? '需要' : '不需要' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="AI监考" width="80">
          <template #default="{ row }">
            <el-tag :type="row.enableAiMonitor !== 0 ? 'success' : 'info'" size="small">
              {{ row.enableAiMonitor !== 0 ? '启用' : '关闭' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="440" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openWhitelist(row)">白名单</el-button>
            <el-button type="success" link @click="viewGrades(row)">成绩</el-button>
            <el-button link @click="viewMonitor(row)" v-if="row.status === 1">监考</el-button>
            <el-button type="warning" link @click="viewGrading(row)" v-if="row.status === 2">批改</el-button>
            <el-button type="warning" link @click="openDialog(row)" v-if="row.status === 0">编辑</el-button>
            <el-popconfirm title="确定删除该考试？" @confirm="handleDelete(row.id)" v-if="row.status === 0">
              <template #reference>
                <el-button type="danger" link>删除</el-button>
              </template>
            </el-popconfirm>
            <el-popconfirm
              v-if="row.status !== 0"
              title="确定删除该考试的所有记录？此操作不可恢复，将同时清除答题记录、违规记录等所有关联数据。"
              confirm-button-text="确定删除"
              cancel-button-text="取消"
              confirm-button-type="danger"
              @confirm="handleDeleteRecords(row)"
            >
              <template #reference>
                <el-button type="danger" link>删除记录</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <div style="margin-top: 20px; text-align: right;">
        <el-pagination
          v-model:current-page="searchParams.pageNum"
          v-model:page-size="searchParams.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </el-card>

    <!-- 新建/编辑考试弹窗 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑考试' : '发布考试'" width="650px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="考试名称" prop="title">
          <el-input v-model="form.title" placeholder="请输入考试名称" />
        </el-form-item>
        <el-form-item label="考试描述">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="选择试卷" prop="paperId">
          <el-select v-model="form.paperId" placeholder="请选择试卷" style="width: 100%">
            <el-option v-for="p in papers" :key="p.id" :label="`${p.title} (总分${p.totalScore})`" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="考试时间" prop="timeRange">
          <el-date-picker
            v-model="form.timeRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            format="YYYY-MM-DD HH:mm"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="考试时长" prop="duration">
          <el-input-number v-model="form.duration" :min="1" :max="600" />
          <span style="margin-left: 8px; color: #909399;">分钟</span>
        </el-form-item>
        <el-form-item label="切屏次数限制">
          <el-input-number v-model="form.maxSwitchCount" :min="1" :max="20" />
          <span style="margin-left: 8px; color: #909399;">超过将自动交卷</span>
        </el-form-item>
        <el-form-item label="人脸核验">
          <el-switch v-model="form.requireFaceVerify" :active-value="1" :inactive-value="0"
            active-text="需要考前人脸核验" inactive-text="不需要" />
        </el-form-item>
        <el-form-item label="AI监考">
          <el-switch v-model="form.enableAiMonitor" :active-value="1" :inactive-value="0"
            active-text="启用摄像头监控+AI检测" inactive-text="关闭(自律考试模式)" />
          <div style="color: #909399; font-size: 12px; margin-top: 4px;">
            关闭后将不启用摄像头、不检测切屏，适合自律型考试
          </div>
        </el-form-item>
        <el-form-item label="考试须知">
          <el-input v-model="form.notice" type="textarea" :rows="3" placeholder="请输入考试注意事项" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 白名单管理弹窗 -->
    <el-dialog v-model="whitelistDialogVisible" title="考生白名单管理" width="700px" destroy-on-close>
      <div style="margin-bottom: 12px;">
        <el-button type="primary" @click="openAddStudent">
          <el-icon><Plus /></el-icon>添加考生
        </el-button>
      </div>
      <el-table :data="whitelistData" border v-loading="whitelistLoading">
        <el-table-column prop="studentId" label="学生ID" width="80" />
        <el-table-column prop="studentName" label="姓名" width="120" />
        <el-table-column prop="studentUsername" label="用户名" width="120" />
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-popconfirm title="确定移除该考生？" @confirm="removeWhitelist(row)">
              <template #reference>
                <el-button type="danger" link>移除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <!-- 添加考生子弹窗 -->
      <el-dialog v-model="addStudentDialogVisible" title="选择考生" width="500px" append-to-body>
        <el-table :data="availableStudents" border v-loading="availableLoading" max-height="400">
          <el-table-column prop="id" label="ID" width="60" />
          <el-table-column prop="username" label="用户名" width="120" />
          <el-table-column prop="realName" label="姓名" />
          <el-table-column label="操作" width="80">
            <template #default="{ row }">
              <el-button type="primary" link @click="addStudentToWhitelist(row)">添加</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-dialog>
    </el-dialog>

  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  getExamList, getExam, createExam, updateExam, deleteExam,
  getExamWhitelist, getAvailableStudents, deleteExamWithRecords
} from '@/api/exam'
import { getAllPapers } from '@/api/paper'

const router = useRouter()
const loading = ref(false)
const submitLoading = ref(false)
const tableData = ref([])
const total = ref(0)
const papers = ref([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref()

// 白名单相关
const whitelistDialogVisible = ref(false)
const whitelistLoading = ref(false)
const whitelistData = ref([])
const currentExamId = ref(null)
const addStudentDialogVisible = ref(false)
const availableStudents = ref([])
const availableLoading = ref(false)

const searchParams = ref({ pageNum: 1, pageSize: 10, title: '', status: null })
const form = ref({
  title: '',
  description: '',
  paperId: null,
  timeRange: null,
  duration: 60,
  maxSwitchCount: 3,
  requireFaceVerify: 0,
  enableAiMonitor: 1,
  notice: '',
  _examId: null
})

const rules = {
  title: [{ required: true, message: '请输入考试名称', trigger: 'blur' }],
  paperId: [{ required: true, message: '请选择试卷', trigger: 'change' }],
  timeRange: [{ required: true, message: '请设置考试时间', trigger: 'change' }],
  duration: [{ required: true, message: '请设置考试时长', trigger: 'change' }]
}

onMounted(() => {
  loadData()
  loadPapers()
})

async function loadData() {
  loading.value = true
  try {
    const res = await getExamList(searchParams.value)
    tableData.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

async function loadPapers() {
  const res = await getAllPapers()
  papers.value = res.data
}

function resetSearch() {
  searchParams.value = { pageNum: 1, pageSize: 10, title: '', status: null }
  loadData()
}

function openDialog(row) {
  isEdit.value = !!row
  if (row) {
    form.value = {
      _examId: row.id,
      title: row.title,
      description: row.description,
      paperId: row.paperId,
      timeRange: [row.startTime, row.endTime],
      duration: row.duration,
      maxSwitchCount: row.maxSwitchCount,
      requireFaceVerify: row.requireFaceVerify || 0,
      enableAiMonitor: row.enableAiMonitor != null ? row.enableAiMonitor : 1,
      notice: row.notice
    }
  } else {
    form.value = {
      _examId: null, title: '', description: '', paperId: null,
      timeRange: null, duration: 60, maxSwitchCount: 3, requireFaceVerify: 0, enableAiMonitor: 1, notice: ''
    }
  }
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitLoading.value = true
  try {
    const data = {
      title: form.value.title,
      description: form.value.description,
      paperId: form.value.paperId,
      startTime: form.value.timeRange[0],
      endTime: form.value.timeRange[1],
      duration: form.value.duration,
      maxSwitchCount: form.value.maxSwitchCount,
      requireFaceVerify: form.value.requireFaceVerify,
      enableAiMonitor: form.value.enableAiMonitor,
      notice: form.value.notice
    }

    if (isEdit.value && form.value._examId) {
      await updateExam(form.value._examId, data)
      ElMessage.success('更新成功')
    } else {
      await createExam(data)
      ElMessage.success('发布成功')
    }
    dialogVisible.value = false
    loadData()
  } finally {
    submitLoading.value = false
  }
}

async function handleDelete(id) {
  await deleteExam(id)
  ElMessage.success('删除成功')
  loadData()
}

async function handleDeleteRecords(row) {
  try {
    await deleteExamWithRecords(row.id)
    ElMessage.success('考试记录已删除')
    loadData()
  } catch (e) {
    ElMessage.error(e.message || '删除失败')
  }
}

function viewGrades(row) {
  router.push(`/teacher/exams/${row.id}/grades`)
}

function viewMonitor(row) {
  router.push(`/teacher/monitor/${row.id}`)
}

function viewGrading(row) {
  router.push(`/teacher/exams/${row.id}/grading`)
}

// 白名单管理
async function openWhitelist(row) {
  currentExamId.value = row.id
  whitelistDialogVisible.value = true
  loadWhitelist()
}

async function loadWhitelist() {
  whitelistLoading.value = true
  try {
    const res = await getExamWhitelist(currentExamId.value)
    whitelistData.value = res.data
  } finally {
    whitelistLoading.value = false
  }
}

async function openAddStudent() {
  addStudentDialogVisible.value = true
  availableLoading.value = true
  try {
    const res = await getAvailableStudents(currentExamId.value)
    availableStudents.value = res.data
  } finally {
    availableLoading.value = false
  }
}

async function addStudentToWhitelist(student) {
  try {
    const wlRes = await getExamWhitelist(currentExamId.value)
    const existingIds = wlRes.data.map(w => w.studentId)
    if (existingIds.includes(student.id)) {
      ElMessage.warning('该学生已在白名单中')
      return
    }
    existingIds.push(student.id)

    // 直接获取当前考试信息，用于更新
    const examRes = await getExam(currentExamId.value, { silent: true })
    const exam = examRes.data
    await updateExam(currentExamId.value, {
      title: exam.title,
      description: exam.description,
      paperId: exam.paperId,
      startTime: exam.startTime,
      endTime: exam.endTime,
      duration: exam.duration,
      maxSwitchCount: exam.maxSwitchCount,
      notice: exam.notice,
      studentIds: existingIds
    })
    ElMessage.success('添加成功')
    loadWhitelist()
    availableStudents.value = availableStudents.value.filter(s => s.id !== student.id)
  } catch (e) {
    ElMessage.error(e.message || '添加失败')
  }
}

async function removeWhitelist(row) {
  try {
    const wlRes = await getExamWhitelist(currentExamId.value)
    const existingIds = wlRes.data.filter(w => w.studentId !== row.studentId).map(w => w.studentId)

    // 直接获取当前考试信息，用于更新
    const examRes = await getExam(currentExamId.value, { silent: true })
    const exam = examRes.data
    await updateExam(currentExamId.value, {
      title: exam.title,
      description: exam.description,
      paperId: exam.paperId,
      startTime: exam.startTime,
      endTime: exam.endTime,
      duration: exam.duration,
      maxSwitchCount: exam.maxSwitchCount,
      notice: exam.notice,
      studentIds: existingIds
    })
    ElMessage.success('移除成功')
    loadWhitelist()
  } catch (e) {
    ElMessage.error(e.message || '移除失败')
  }
}

</script>
