<template>
  <div>
    <el-card class="page-card">
      <template #header>
        <div class="card-header">
          <span>试卷管理</span>
          <el-button type="primary" @click="openDialog()">
            <el-icon><Plus /></el-icon>新建试卷
          </el-button>
        </div>
      </template>

      <!-- 搜索栏 -->
      <div class="search-bar">
        <el-input v-model="searchParams.title" placeholder="试卷名称" clearable style="width: 200px" @keyup.enter="loadData" />
        <el-button type="primary" @click="loadData">搜索</el-button>
        <el-button @click="resetSearch">重置</el-button>
      </div>

      <!-- 表格 -->
      <el-table :data="tableData" border stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="title" label="试卷名称" width="180" />
        <el-table-column prop="description" label="描述" show-overflow-tooltip />
        <el-table-column prop="totalScore" label="总分" width="80" />
        <el-table-column prop="passScore" label="及格分" width="80" />
        <el-table-column prop="questionCount" label="题目数" width="80" />
        <el-table-column prop="creatorName" label="创建者" width="100" />
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="250" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="viewQuestions(row)">查看题目</el-button>
            <el-button type="warning" link @click="openDialog(row)">编辑</el-button>
            <el-popconfirm title="确定删除该试卷？" @confirm="handleDelete(row.id)">
              <template #reference>
                <el-button type="danger" link>删除</el-button>
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

    <!-- 新建/编辑试卷弹窗 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑试卷' : '新建试卷'" width="850px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="试卷名称" prop="title">
          <el-input v-model="form.title" placeholder="请输入试卷名称" />
        </el-form-item>
        <el-form-item label="试卷描述">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="请输入试卷描述" />
        </el-form-item>
        <el-form-item label="及格分" prop="passScore">
          <el-input-number v-model="form.passScore" :min="0" :max="999" />
        </el-form-item>

        <!-- 题目选择区 -->
        <el-form-item label="选择题目" required>
          <div style="width: 100%">
            <div style="margin-bottom: 12px; display: flex; gap: 8px; align-items: center;">
              <el-input v-model="questionKeyword" placeholder="搜索题目" clearable style="width: 200px" size="small" @keyup.enter="loadAllQuestions" />
              <el-select v-model="questionTypeFilter" placeholder="类型" clearable style="width: 120px" size="small" @change="loadAllQuestions">
                <el-option label="单选题" :value="1" />
                <el-option label="多选题" :value="2" />
                <el-option label="判断题" :value="3" />
              </el-select>
              <el-button type="primary" size="small" @click="loadAllQuestions">搜索</el-button>
            </div>

            <!-- 已选题目列表 -->
            <el-table :data="form.questions" border size="small" max-height="300" v-if="form.questions.length > 0">
              <el-table-column label="序号" width="60" type="index" />
              <el-table-column prop="content" label="题干" show-overflow-tooltip />
              <el-table-column label="类型" width="80">
                <template #default="{ row }">
                  <el-tag size="small" :type="row.type === 1 ? '' : row.type === 2 ? 'warning' : 'success'">
                    {{ row.type === 1 ? '单选' : row.type === 2 ? '多选' : '判断' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="分值" width="100">
                <template #default="{ row }">
                  <el-input-number v-model="row.score" :min="1" :max="100" size="small" style="width: 80px" />
                </template>
              </el-table-column>
              <el-table-column label="操作" width="80">
                <template #default="{ $index }">
                  <el-button type="danger" link size="small" @click="removeQuestion($index)">移除</el-button>
                </template>
              </el-table-column>
            </el-table>

            <!-- 添加题目 -->
            <el-divider v-if="form.questions.length > 0">添加更多题目</el-divider>
            <el-table
              :data="availableQuestions"
              border
              size="small"
              max-height="250"
              v-loading="questionsLoading"
              v-if="showQuestionPicker"
            >
              <el-table-column prop="content" label="题干" show-overflow-tooltip />
              <el-table-column label="类型" width="80">
                <template #default="{ row }">
                  <el-tag size="small" :type="row.type === 1 ? '' : row.type === 2 ? 'warning' : 'success'">
                    {{ row.type === 1 ? '单选' : row.type === 2 ? '多选' : '判断' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="score" label="默认分值" width="90" />
              <el-table-column label="操作" width="80">
                <template #default="{ row }">
                  <el-button type="primary" link size="small" @click="addQuestion(row)">添加</el-button>
                </template>
              </el-table-column>
            </el-table>

            <div style="margin-top: 8px;">
              <el-button size="small" @click="showQuestionPicker = !showQuestionPicker">
                {{ showQuestionPicker ? '收起题目列表' : '展开题目列表' }}
              </el-button>
              <span style="color: #909399; font-size: 12px; margin-left: 8px;">
                已选 {{ form.questions.length }} 题，总分 {{ totalSelectedScore }} 分
              </span>
            </div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 查看题目弹窗 -->
    <el-dialog v-model="viewDialogVisible" title="试卷题目" width="700px">
      <el-table :data="viewQuestionsList" border>
        <el-table-column label="序号" width="60" type="index" />
        <el-table-column prop="content" label="题干" show-overflow-tooltip />
        <el-table-column label="类型" width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="row.type === 1 ? '' : row.type === 2 ? 'warning' : 'success'">
              {{ row.typeDesc }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="paperScore" label="分值" width="70" />
        <el-table-column prop="answer" label="答案" width="100" />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getPaperList, createPaper, updatePaper, deletePaper, getPaperQuestions } from '@/api/paper'
import { getAllQuestions } from '@/api/question'

const loading = ref(false)
const submitLoading = ref(false)
const questionsLoading = ref(false)
const tableData = ref([])
const total = ref(0)
const dialogVisible = ref(false)
const viewDialogVisible = ref(false)
const viewQuestionsList = ref([])
const isEdit = ref(false)
const formRef = ref()
const availableQuestions = ref([])
const showQuestionPicker = ref(false)
const questionKeyword = ref('')
const questionTypeFilter = ref(null)

const searchParams = ref({ pageNum: 1, pageSize: 10, title: '' })
const form = ref({
  title: '',
  description: '',
  passScore: 60,
  questions: []
})

const rules = {
  title: [{ required: true, message: '请输入试卷名称', trigger: 'blur' }],
  passScore: [{ required: true, message: '请设置及格分', trigger: 'change' }]
}

const totalSelectedScore = computed(() => {
  return form.value.questions.reduce((sum, q) => sum + (q.score || 0), 0)
})

onMounted(() => loadData())

async function loadData() {
  loading.value = true
  try {
    const res = await getPaperList(searchParams.value)
    tableData.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

function resetSearch() {
  searchParams.value = { pageNum: 1, pageSize: 10, title: '' }
  loadData()
}

async function openDialog(row) {
  isEdit.value = !!row
  showQuestionPicker.value = false
  questionKeyword.value = ''
  questionTypeFilter.value = null

  if (row) {
    // 编辑模式 - 加载已有题目
    const res = await getPaperQuestions(row.id)
    form.value = {
      _paperId: row.id,
      title: row.title,
      description: row.description,
      passScore: row.passScore,
      questions: res.data.map(q => ({
        questionId: q.id,
        content: q.content,
        type: q.type,
        score: q.paperScore,
        answer: q.answer
      }))
    }
  } else {
    form.value = { _paperId: null, title: '', description: '', passScore: 60, questions: [] }
  }
  dialogVisible.value = true
  loadAllQuestions()
}

async function loadAllQuestions() {
  questionsLoading.value = true
  try {
    const params = {}
    if (questionKeyword.value) params.keyword = questionKeyword.value
    if (questionTypeFilter.value) params.type = questionTypeFilter.value
    const res = await getAllQuestions(params)
    // 过滤已选题目
    const selectedIds = form.value.questions.map(q => q.questionId)
    availableQuestions.value = res.data.filter(q => !selectedIds.includes(q.id))
  } finally {
    questionsLoading.value = false
  }
}

function addQuestion(row) {
  if (form.value.questions.some(q => q.questionId === row.id)) {
    ElMessage.warning('该题目已添加')
    return
  }
  form.value.questions.push({
    questionId: row.id,
    content: row.content,
    type: row.type,
    score: row.score || 5,
    answer: row.answer
  })
  // 从可选列表移除
  availableQuestions.value = availableQuestions.value.filter(q => q.id !== row.id)
}

function removeQuestion(index) {
  form.value.questions.splice(index, 1)
  loadAllQuestions()
}

async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  if (form.value.questions.length === 0) {
    ElMessage.warning('请至少选择一道题目')
    return
  }

  submitLoading.value = true
  try {
    const data = {
      title: form.value.title,
      description: form.value.description,
      passScore: form.value.passScore,
      questions: form.value.questions.map(q => ({
        questionId: q.questionId,
        score: q.score
      }))
    }

    if (isEdit.value && form.value._paperId) {
      await updatePaper(form.value._paperId, data)
      ElMessage.success('更新成功')
    } else {
      await createPaper(data)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    loadData()
  } finally {
    submitLoading.value = false
  }
}

async function handleDelete(id) {
  await deletePaper(id)
  ElMessage.success('删除成功')
  loadData()
}

async function viewQuestions(row) {
  const res = await getPaperQuestions(row.id)
  viewQuestionsList.value = res.data
  viewDialogVisible.value = true
}
</script>
