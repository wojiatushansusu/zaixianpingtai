<template>
  <div>
    <el-card class="page-card">
      <template #header>
        <div class="card-header">
          <span>题库管理</span>
          <div>
            <el-button @click="handleDownloadTemplate" plain>
              <el-icon><Download /></el-icon>导入模板
            </el-button>
            <el-upload
              :auto-upload="false"
              :show-file-list="false"
              accept=".xlsx,.xls"
              :on-change="handleImportFile"
              style="display: inline-block; margin-left: 8px;"
            >
              <el-button type="success">
                <el-icon><Upload /></el-icon>Excel导入
              </el-button>
            </el-upload>
            <el-button type="primary" @click="openDialog()" style="margin-left: 8px;">
              <el-icon><Plus /></el-icon>新增题目
            </el-button>
          </div>
        </div>
      </template>

      <!-- 搜索栏 -->
      <div class="search-bar">
        <el-input v-model="searchParams.keyword" placeholder="搜索题目内容" clearable style="width: 200px" @keyup.enter="loadData" />
        <el-select v-model="searchParams.type" placeholder="题目类型" clearable style="width: 120px">
          <el-option label="单选题" :value="1" />
          <el-option label="多选题" :value="2" />
          <el-option label="判断题" :value="3" />
          <el-option label="简答题" :value="4" />
        </el-select>
        <el-select v-model="searchParams.difficulty" placeholder="难度" clearable style="width: 100px">
          <el-option label="简单" :value="1" />
          <el-option label="中等" :value="2" />
          <el-option label="困难" :value="3" />
        </el-select>
        <el-select v-model="searchParams.tagId" placeholder="知识点标签" clearable style="width: 140px">
          <el-option v-for="tag in tagList" :key="tag.id" :label="tag.name" :value="tag.id" />
        </el-select>
        <el-button type="primary" @click="loadData">搜索</el-button>
        <el-button @click="resetSearch">重置</el-button>
        <el-button
          type="danger"
          :disabled="selectedRows.length === 0"
          :loading="batchDeleteLoading"
          @click="handleBatchDelete"
        >
          批量删除{{ selectedRows.length > 0 ? ` (${selectedRows.length})` : '' }}
        </el-button>
        <el-button
          type="warning"
          :disabled="selectedRows.length === 0"
          @click="openBatchTagDialog"
        >
          批量标签{{ selectedRows.length > 0 ? ` (${selectedRows.length})` : '' }}
        </el-button>
        <el-button
          type="primary"
          :disabled="selectedRows.length === 0"
          @click="handleAiClassify"
          plain
        >
          <el-icon><MagicStick /></el-icon>
          AI分类{{ selectedRows.length > 0 ? ` (${selectedRows.length})` : '' }}
        </el-button>
      </div>

      <!-- 表格 -->
      <el-table :data="tableData" border stripe v-loading="loading" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="45" align="center" />
        <el-table-column label="序号" width="60" align="center">
          <template #default="{ $index }">
            {{ (searchParams.pageNum - 1) * searchParams.pageSize + $index + 1 }}
          </template>
        </el-table-column>
        <el-table-column prop="typeDesc" label="类型" width="90">
          <template #default="{ row }">
            <el-tag :type="row.type === 1 ? '' : row.type === 2 ? 'warning' : row.type === 3 ? 'success' : 'danger'" size="small">
              {{ row.typeDesc }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="content" label="题干" show-overflow-tooltip />
        <el-table-column prop="difficultyDesc" label="难度" width="70">
          <template #default="{ row }">
            <el-tag :type="row.difficulty === 1 ? 'success' : row.difficulty === 3 ? 'danger' : 'info'" size="small">
              {{ row.difficultyDesc }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="标签" width="180">
          <template #default="{ row }">
            <el-tag v-for="tag in (row.tagNames || [])" :key="tag" size="small" style="margin-right: 4px;">
              {{ tag }}
            </el-tag>
            <span v-if="!row.tagNames || row.tagNames.length === 0" style="color: #909399; font-size: 13px;">无</span>
          </template>
        </el-table-column>
        <el-table-column prop="answer" label="答案" width="120" />
        <el-table-column prop="score" label="分值" width="60" />
        <el-table-column prop="creatorName" label="创建者" width="90" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openDialog(row)">编辑</el-button>
            <el-popconfirm title="确定删除该题目？" @confirm="handleDelete(row.id)">
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

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑题目' : '新增题目'" width="700px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="题目类型" prop="type">
          <el-radio-group v-model="form.type" @change="onTypeChange">
            <el-radio :value="1">单选题</el-radio>
            <el-radio :value="2">多选题</el-radio>
            <el-radio :value="3">判断题</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="题干" prop="content">
          <el-input v-model="form.content" type="textarea" :rows="3" />
        </el-form-item>

        <template v-if="form.type !== 3">
          <el-form-item label="选项A" prop="optionA">
            <el-input v-model="form.optionA" />
          </el-form-item>
          <el-form-item label="选项B" prop="optionB">
            <el-input v-model="form.optionB" />
          </el-form-item>
          <el-form-item label="选项C">
            <el-input v-model="form.optionC" />
          </el-form-item>
          <el-form-item label="选项D">
            <el-input v-model="form.optionD" />
          </el-form-item>
        </template>

        <template v-if="form.type === 3">
          <el-form-item label="正确答案" prop="answer">
            <el-radio-group v-model="form.answer">
              <el-radio value="true">正确</el-radio>
              <el-radio value="false">错误</el-radio>
            </el-radio-group>
          </el-form-item>
        </template>

        <template v-if="form.type === 1">
          <el-form-item label="正确答案" prop="answer">
            <el-radio-group v-model="form.answer">
              <el-radio value="A">A</el-radio>
              <el-radio value="B">B</el-radio>
              <el-radio value="C">C</el-radio>
              <el-radio value="D">D</el-radio>
            </el-radio-group>
          </el-form-item>
        </template>
        <template v-if="form.type === 2">
          <el-form-item label="正确答案" prop="answer">
            <el-checkbox-group v-model="selectedAnswers" @change="onMultiAnswerChange">
              <el-checkbox value="A">A</el-checkbox>
              <el-checkbox value="B">B</el-checkbox>
              <el-checkbox value="C">C</el-checkbox>
              <el-checkbox value="D">D</el-checkbox>
            </el-checkbox-group>
          </el-form-item>
        </template>

        <el-form-item label="解析">
          <el-input v-model="form.analysis" type="textarea" :rows="2" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="分值" prop="score">
              <el-input-number v-model="form.score" :min="1" :max="100" style="width: 100%;" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="难度">
              <el-select v-model="form.difficulty" style="width: 100%;">
                <el-option label="简单" :value="1" />
                <el-option label="中等" :value="2" />
                <el-option label="困难" :value="3" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="知识点标签">
          <el-select
            v-model="form.tagIds"
            multiple
            filterable
            allow-create
            default-first-option
            placeholder="输入标签名搜索或直接创建新标签"
            style="width: 100%;"
          >
            <el-option v-for="tag in tagList" :key="tag.id" :label="tag.name" :value="tag.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- ====== 智能导入预检弹窗 ====== -->
    <el-dialog
      v-model="previewVisible"
      title="导入预检"
      width="900px"
      destroy-on-close
      :close-on-click-modal="false"
    >
      <!-- 预检加载中 -->
      <div v-if="previewLoading" style="text-align: center; padding: 40px;">
        <el-icon class="is-loading" :size="32" style="color: #409EFF;"><Loading /></el-icon>
        <p style="margin-top: 12px; color: #909399;">正在分析文件内容...</p>
      </div>

      <!-- 预检结果 -->
      <template v-else-if="previewData">
        <!-- 统计摘要 -->
        <el-alert
          :type="previewData.errorCount > 0 ? 'error' : previewData.warningCount > 0 ? 'warning' : 'success'"
          :closable="false"
          style="margin-bottom: 16px;"
        >
          <template #title>
            <span style="font-weight: bold;">
              共 {{ previewData.totalCount }} 道题目：
              <el-tag type="success" size="small" style="margin: 0 4px;">{{ previewData.okCount }} 通过</el-tag>
              <el-tag type="warning" size="small" style="margin: 0 4px;">{{ previewData.warningCount }} 警告</el-tag>
              <el-tag type="danger" size="small" style="margin: 0 4px;">{{ previewData.errorCount }} 错误</el-tag>
            </span>
          </template>
          <template v-if="previewData.unknownHeaders && previewData.unknownHeaders.length > 0" #default>
            <div style="margin-top: 4px; font-size: 13px;">
              未识别的表头列: <el-tag v-for="h in previewData.unknownHeaders" :key="h" size="small" type="info" style="margin: 2px;">{{ h }}</el-tag>
            </div>
          </template>
        </el-alert>

        <!-- 筛选工具栏 -->
        <div style="display: flex; align-items: center; gap: 12px; margin-bottom: 12px;">
          <span style="font-size: 14px; color: #606266;">筛选：</span>
          <el-radio-group v-model="previewFilter" size="small">
            <el-radio-button value="all">全部 ({{ previewData.totalCount }})</el-radio-button>
            <el-radio-button value="error">
              错误 ({{ previewData.errorCount }})
              <span v-if="previewData.errorCount > 0" style="color: #f56c6c;">*</span>
            </el-radio-button>
            <el-radio-button value="warning">警告 ({{ previewData.warningCount }})</el-radio-button>
            <el-radio-button value="ok">通过 ({{ previewData.okCount }})</el-radio-button>
          </el-radio-group>
          <div style="flex: 1;"></div>
          <el-checkbox v-model="excludeErrors" :disabled="previewData.errorCount === 0" size="small">
            排除错误行
          </el-checkbox>
        </div>

        <!-- 预检表格 -->
        <el-table
          :data="filteredPreviewRows"
          border
          size="small"
          max-height="400"
          style="width: 100%;"
          :row-class-name="previewRowClassName"
        >
          <el-table-column label="行" width="50" align="center">
            <template #default="{ row }">
              <span style="font-weight: bold;">{{ row.rowIndex }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="70" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 'ok' ? 'success' : row.status === 'warning' ? 'warning' : 'danger'" size="small">
                {{ row.status === 'ok' ? '通过' : row.status === 'warning' ? '警告' : '错误' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="typeStr" label="类型" width="80" />
          <el-table-column prop="content" label="题干" show-overflow-tooltip min-width="180" />
          <el-table-column prop="answer" label="答案" width="100" />
          <el-table-column label="问题" min-width="200">
            <template #default="{ row }">
              <div v-if="row.messages && row.messages.length > 0">
                <div v-for="(msg, idx) in row.messages" :key="idx"
                  :style="{ color: row.status === 'error' ? '#f56c6c' : '#e6a23c', fontSize: '12px', lineHeight: '1.6' }">
                  {{ msg }}
                </div>
              </div>
              <span v-else style="color: #67c23a; font-size: 12px;">-</span>
            </template>
          </el-table-column>
        </el-table>
      </template>

      <template #footer>
        <el-button @click="previewVisible = false">取消</el-button>
        <el-button type="primary" :loading="confirmLoading" @click="handleConfirmImport"
          :disabled="!canConfirmImport">
          确认导入 {{ importSummary }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 导入结果弹窗 -->
    <el-dialog v-model="importResultVisible" title="导入结果" width="500px">
      <el-result
        :icon="importResult.errorCount === 0 ? 'success' : 'warning'"
        :title="`成功导入 ${importResult.successCount} 道题目`"
        :sub-title="importResult.errorCount > 0 ? `失败 ${importResult.errorCount} 条` : ''"
      >
        <template #extra>
          <div v-if="importResult.skippedCount > 0" style="margin-bottom: 12px;">
            <el-alert :title="`已跳过 ${importResult.skippedCount} 条被排除的记录`" type="info" :closable="false" />
          </div>
          <div v-if="importResult.errors && importResult.errors.length > 0" style="max-height: 200px; overflow-y: auto; text-align: left; padding: 0 20px;">
            <el-alert v-for="(err, idx) in importResult.errors.slice(0, 10)" :key="idx" :title="err" type="error" :closable="false" style="margin-bottom: 8px;" />
            <div v-if="importResult.errors.length > 10" style="color: #909399; text-align: center;">
              还有 {{ importResult.errors.length - 10 }} 条错误...
            </div>
          </div>
          <el-button type="primary" @click="importResultVisible = false">确定</el-button>
        </template>
      </el-result>
    </el-dialog>

    <!-- ====== 标签快速编辑弹窗 ====== -->
    <el-dialog
      v-model="tagDialogVisible"
      :title="tagDialogTitle"
      width="500px"
      destroy-on-close
    >
      <div style="margin-bottom: 12px; color: #606266; font-size: 13px;">
        选择已有标签或输入新标签名后按回车创建
      </div>
      <el-select
        v-model="tagDialogIds"
        multiple
        filterable
        allow-create
        default-first-option
        placeholder="输入标签名搜索或直接创建"
        style="width: 100%;"
      >
        <el-option v-for="tag in tagList" :key="tag.id" :label="tag.name" :value="tag.id" />
      </el-select>

      <!-- 批量标签模式提示 -->
      <div v-if="tagBatchMode && tagBatchRows.length > 1" style="margin-top: 12px;">
        <el-alert type="info" :closable="false">
          将为选中的 {{ tagBatchRows.length }} 道题目统一设置以下标签
        </el-alert>
      </div>

      <template #footer>
        <el-button @click="tagDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="tagDialogLoading" @click="handleTagSubmit">保存</el-button>
      </template>
    </el-dialog>

    <!-- ====== AI 分类结果弹窗 ====== -->
    <el-dialog
      v-model="aiClassifyVisible"
      title="AI 智能分类结果"
      width="750px"
      destroy-on-close
      :close-on-click-modal="false"
    >
      <!-- 加载中 -->
      <div v-if="aiClassifyLoading" style="text-align: center; padding: 40px;">
        <el-icon class="is-loading" :size="32" style="color: #409EFF;"><Loading /></el-icon>
        <p style="margin-top: 12px; color: #909399;">AI 正在分析题目，请稍候...</p>
      </div>

      <!-- 结果展示 -->
      <template v-else-if="aiClassifyData">
        <el-alert
          :type="aiClassifyData.failCount > 0 ? 'warning' : 'success'"
          :closable="false"
          style="margin-bottom: 16px;"
        >
          <template #title>
            <span style="font-weight: bold;">
              分析完成：{{ aiClassifyData.successCount }} 道成功，{{ aiClassifyData.failCount }} 道失败
            </span>
          </template>
        </el-alert>

        <el-table :data="aiClassifyData.items" border size="small" max-height="400">
          <el-table-column label="状态" width="65" align="center">
            <template #default="{ row }">
              <el-tag :type="row.success ? 'success' : 'danger'" size="small">
                {{ row.success ? '成功' : '失败' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="questionId" label="ID" width="60" align="center" />
          <el-table-column label="题干" min-width="180">
            <template #default="{ row }">
              {{ getQuestionContent(row.questionId) }}
            </template>
          </el-table-column>
          <el-table-column label="推荐标签" width="180">
            <template #default="{ row }">
              <template v-if="row.suggestedTags && row.suggestedTags.length > 0">
                <el-tag v-for="tag in row.suggestedTags" :key="tag" size="small" style="margin: 2px;">
                  {{ tag }}
                </el-tag>
              </template>
              <span v-else style="color: #909399;">-</span>
            </template>
          </el-table-column>
          <el-table-column label="推荐难度" width="80" align="center">
            <template #default="{ row }">
              <el-tag
                v-if="row.suggestedDifficulty"
                :type="row.suggestedDifficulty === 1 ? 'success' : row.suggestedDifficulty === 3 ? 'danger' : 'info'"
                size="small"
              >
                {{ row.suggestedDifficultyDesc }}
              </el-tag>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column label="问题" width="120">
            <template #default="{ row }">
              <span v-if="row.error" style="color: #f56c6c; font-size: 12px;">{{ row.error }}</span>
              <span v-else>-</span>
            </template>
          </el-table-column>
        </el-table>
      </template>

      <template #footer>
        <el-button @click="aiClassifyVisible = false">取消</el-button>
        <el-button
          type="primary"
          :disabled="!aiClassifyData || aiClassifyData.successCount === 0 || aiClassifyApplying"
          :loading="aiClassifyApplying"
          @click="handleApplyAiClassify"
        >
          确认应用 ({{ aiClassifyData ? aiClassifyData.successCount : 0 }} 道)
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getQuestionList, createQuestion, updateQuestion, deleteQuestion, batchDeleteQuestions, getTagList, createTag, batchUpdateQuestionTags, previewImport, confirmImport, downloadImportTemplate, aiClassifyQuestions, applyAiClassifyResult } from '@/api/question'

const loading = ref(false)
const submitLoading = ref(false)
const tableData = ref([])
const total = ref(0)
const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref()
const selectedAnswers = ref([])
const tagList = ref([])
const importResultVisible = ref(false)
const importResult = ref({ successCount: 0, errorCount: 0, skippedCount: 0, errors: [] })

// 批量删除相关
const selectedRows = ref([])
const batchDeleteLoading = ref(false)

// ====== 预检相关状态 ======
const previewVisible = ref(false)
const previewLoading = ref(false)
const previewData = ref(null)
const previewFilter = ref('all')
const excludeErrors = ref(true)
const confirmLoading = ref(false)
const pendingFile = ref(null) // 保存待导入的文件对象

const searchParams = ref({ pageNum: 1, pageSize: 10, keyword: '', type: null, difficulty: null, tagId: null })
const form = ref({
  type: 1, content: '', optionA: '', optionB: '', optionC: '', optionD: '',
  answer: '', analysis: '', score: 5, difficulty: 2, tagIds: []
})

const rules = {
  type: [{ required: true, message: '请选择题目类型', trigger: 'change' }],
  content: [{ required: true, message: '请输入题干', trigger: 'blur' }],
  answer: [{ required: true, message: '请设置答案', trigger: 'change' }],
  score: [{ required: true, message: '请设置分值', trigger: 'change' }]
}

onMounted(async () => {
  await loadTags()
  loadData()
})

async function loadTags() {
  try {
    const res = await getTagList()
    tagList.value = res.data || []
  } catch (e) { /* ignore */ }
}

async function loadData() {
  loading.value = true
  try {
    const res = await getQuestionList(searchParams.value)
    tableData.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

function resetSearch() {
  searchParams.value = { pageNum: 1, pageSize: 10, keyword: '', type: null, difficulty: null, tagId: null }
  loadData()
}

function onTypeChange() {
  form.value.answer = ''
  selectedAnswers.value = []
  if (form.value.type === 3) {
    form.value.optionA = '正确'
    form.value.optionB = '错误'
    form.value.optionC = null
    form.value.optionD = null
  }
}

function onMultiAnswerChange(val) {
  form.value.answer = val.sort().join(',')
}

function openDialog(row) {
  isEdit.value = !!row
  if (row) {
    form.value = { ...row, tagIds: row.tagIds || [] }
    if (row.type === 2 && row.answer) {
      selectedAnswers.value = row.answer.split(',')
    }
  } else {
    form.value = { type: 1, content: '', optionA: '', optionB: '', optionC: '', optionD: '', answer: '', analysis: '', score: 5, difficulty: 2, tagIds: [] }
    selectedAnswers.value = []
  }
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitLoading.value = true
  try {
    // 处理用户新建标签的情况：tagIds 中混有字符串（新标签名）和数字（已有标签ID）
    const tagIds = form.value.tagIds || []
    const newTagNames = tagIds.filter(id => typeof id === 'string')
    const existingTagIds = tagIds.filter(id => typeof id === 'number')

    // 批量创建新标签
    for (const name of newTagNames) {
      try {
        const res = await createTag(name)
        // 后端返回 Result.success(tag)，data 中有完整的标签对象
        const newTag = res.data
        if (newTag && newTag.id) {
          existingTagIds.push(newTag.id)
          // 立即加入本地标签列表，避免重复创建
          if (!tagList.value.find(t => t.id === newTag.id)) {
            tagList.value.push(newTag)
          }
        }
      } catch (e) {
        const existing = tagList.value.find(t => t.name === name)
        if (existing) {
          existingTagIds.push(existing.id)
        } else {
          ElMessage.warning(`标签「${name}」创建失败`)
        }
      }
    }

    // 用纯数字ID提交
    const submitData = { ...form.value, tagIds: existingTagIds }

    if (isEdit.value) {
      await updateQuestion(form.value.id, submitData)
      ElMessage.success('更新成功')
    } else {
      await createQuestion(submitData)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    await loadTags() // 刷新标签列表
    loadData()
  } finally {
    submitLoading.value = false
  }
}

async function handleDelete(id) {
  await deleteQuestion(id)
  ElMessage.success('删除成功')
  loadData()
}

function handleSelectionChange(rows) {
  selectedRows.value = rows
}

async function handleBatchDelete() {
  if (selectedRows.value.length === 0) {
    ElMessage.warning('请先选择要删除的题目')
    return
  }

  try {
    await ElMessageBox.confirm(
      `确定要删除选中的 ${selectedRows.value.length} 道题目吗？此操作不可撤销。`,
      '批量删除确认',
      { confirmButtonText: '确定删除', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return // 用户取消
  }

  const ids = selectedRows.value.map(row => row.id)
  batchDeleteLoading.value = true
  try {
    await batchDeleteQuestions(ids)
    ElMessage.success(`成功删除 ${ids.length} 道题目`)
    loadData()
  } catch (e) {
    ElMessage.error(e.message || '批量删除失败')
  } finally {
    batchDeleteLoading.value = false
  }
}

// ====== 智能导入预检流程 ======

/**
 * 步骤1：选择文件后，先发起预检请求
 */
async function handleImportFile(file) {
  pendingFile.value = file.raw
  previewFilter.value = 'all'
  excludeErrors.value = true
  previewData.value = null
  previewLoading.value = true
  previewVisible.value = true

  try {
    const res = await previewImport(file.raw)
    previewData.value = res.data

    // 如果有错误，自动切换到"错误"筛选
    if (res.data.errorCount > 0) {
      previewFilter.value = 'error'
    }
  } catch (e) {
    ElMessage.error(e.message || '文件分析失败，请检查文件格式')
    previewVisible.value = false
  } finally {
    previewLoading.value = false
  }
}

/**
 * 根据筛选条件过滤预检行
 */
const filteredPreviewRows = computed(() => {
  if (!previewData.value || !previewData.value.rows) return []

  let rows = previewData.value.rows
  if (previewFilter.value !== 'all') {
    rows = rows.filter(r => r.status === previewFilter.value)
  }
  return rows
})

/**
 * 预检表格行的样式
 */
function previewRowClassName({ row }) {
  if (row.status === 'error') return 'preview-row-error'
  if (row.status === 'warning') return 'preview-row-warning'
  return ''
}

/**
 * 计算将要导入的行数
 */
const importSummary = computed(() => {
  if (!previewData.value) return ''
  if (excludeErrors.value && previewData.value.errorCount > 0) {
    return `${previewData.value.okCount + previewData.value.warningCount} / ${previewData.value.totalCount} 条（排除错误行）`
  }
  return `${previewData.value.totalCount} 条`
})

/**
 * 是否可以确认导入
 */
const canConfirmImport = computed(() => {
  if (!previewData.value) return false
  if (excludeErrors.value && previewData.value.errorCount === previewData.value.totalCount) {
    return false // 全是错误行，没有可导入的数据
  }
  return true
})

/**
 * 步骤2：确认导入
 */
async function handleConfirmImport() {
  if (!pendingFile.value) return

  // 计算要包含的行号列表
  let includeRows = []
  if (excludeErrors.value && previewData.value.errorCount > 0) {
    // 只导入非错误行
    includeRows = previewData.value.rows
      .filter(r => r.status !== 'error')
      .map(r => r.rowIndex)
  }

  confirmLoading.value = true
  try {
    const res = await confirmImport(pendingFile.value, includeRows)
    previewVisible.value = false
    importResult.value = res.data
    importResultVisible.value = true
    loadData()
    ElMessage.success(`导入完成！成功 ${res.data.successCount} 道`)
  } catch (e) {
    ElMessage.error(e.message || '导入失败')
  } finally {
    confirmLoading.value = false
    pendingFile.value = null
  }
}

async function handleDownloadTemplate() {
  try {
    const res = await downloadImportTemplate()
    const blob = new Blob([res], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = '题目导入模板.xlsx'
    link.click()
    window.URL.revokeObjectURL(url)
  } catch (e) {
    ElMessage.error('下载模板失败')
  }
}

// ====== 标签快速编辑 ======

const tagDialogVisible = ref(false)
const tagDialogLoading = ref(false)
const tagDialogIds = ref([]) // 当前选中的标签（数字ID和字符串混用）
const tagDialogRow = ref(null) // 单个编辑模式的目标题目
const tagBatchMode = ref(false)
const tagBatchRows = ref([])

const tagDialogTitle = computed(() => {
  if (tagBatchMode.value) {
    return `批量设置标签（${tagBatchRows.value.length} 道题目）`
  }
  if (tagDialogRow.value) {
    const title = tagDialogRow.value.content || ''
    return `设置标签 - ${title.length > 20 ? title.substring(0, 20) + '...' : title}`
  }
  return '设置标签'
})

/**
 * 打开单题目标签编辑弹窗
 */
function openTagDialog(row) {
  tagBatchMode.value = false
  tagBatchRows.value = [row]
  tagDialogRow.value = row
  tagDialogIds.value = [...(row.tagIds || [])]
  tagDialogVisible.value = true
}

/**
 * 打开批量标签编辑弹窗（可后续扩展为按钮触发）
 */
function openBatchTagDialog() {
  if (selectedRows.value.length === 0) {
    ElMessage.warning('请先选择要设置标签的题目')
    return
  }
  tagBatchMode.value = true
  tagBatchRows.value = [...selectedRows.value]
  tagDialogRow.value = null
  tagDialogIds.value = []
  tagDialogVisible.value = true
}

/**
 * 提交标签修改
 */
async function handleTagSubmit() {
  tagDialogLoading.value = true
  try {
    const ids = tagDialogIds.value
    const newTagNames = ids.filter(id => typeof id === 'string')
    const existingIds = ids.filter(id => typeof id === 'number')

    // 创建新标签
    const createdNames = []
    const existedNames = []
    for (const name of newTagNames) {
      try {
        const res = await createTag(name)
        const newTag = res.data
        if (newTag && newTag.id) {
          existingIds.push(newTag.id)
          // 立即加入本地标签列表，避免重复创建
          if (!tagList.value.find(t => t.id === newTag.id)) {
            tagList.value.push(newTag)
          }
          createdNames.push(name)
        }
      } catch (e) {
        const existing = tagList.value.find(t => t.name === name)
        if (existing) {
          existingIds.push(existing.id)
          existedNames.push(name)
        }
      }
    }

    if (tagBatchMode.value) {
      // 批量模式：调用专用批量标签接口
      const questionIds = tagBatchRows.value.map(r => r.id)
      await batchUpdateQuestionTags(questionIds, existingIds)
      let msg = `已更新 ${questionIds.length} 道题目的标签`
      if (createdNames.length > 0) msg += `，新建标签「${createdNames.join('、')}」`
      if (existedNames.length > 0) msg += `，已有标签「${existedNames.join('、')}」已复用`
      ElMessage.success(msg)
    } else {
      // 单题模式：调用完整更新接口
      const row = tagDialogRow.value
      if (row) {
        await updateQuestion(row.id, { tagIds: existingIds })
        let msg = '标签已更新'
        if (createdNames.length > 0) msg += `，新建标签「${createdNames.join('、')}」`
        if (existedNames.length > 0) msg += `，已有标签「${existedNames.join('、')}」已复用`
        ElMessage.success(msg)
      }
    }

    tagDialogVisible.value = false
    await loadTags()
    loadData()
  } catch (e) {
    ElMessage.error(e.message || '标签更新失败')
  } finally {
    tagDialogLoading.value = false
  }
}

// ====== AI 智能分类 ======

const aiClassifyVisible = ref(false)
const aiClassifyLoading = ref(false)
const aiClassifyApplying = ref(false)
const aiClassifyData = ref(null)

function getQuestionContent(questionId) {
  const row = selectedRows.value.find(r => r.id === questionId) || tableData.value.find(r => r.id === questionId)
  if (!row) return `ID:${questionId}`
  const content = row.content || ''
  return content.length > 30 ? content.substring(0, 30) + '...' : content
}

async function handleAiClassify() {
  if (selectedRows.value.length === 0) {
    ElMessage.warning('请先选择要分析的题目')
    return
  }

  try {
    await ElMessageBox.confirm(
      `将使用 AI 分析选中的 ${selectedRows.value.length} 道题目，自动推荐知识点标签和难度等级。\n分析完成后可确认应用或取消。`,
      'AI 智能分类',
      { confirmButtonText: '开始分析', cancelButtonText: '取消', type: 'info' }
    )
  } catch {
    return
  }

  aiClassifyLoading.value = true
  aiClassifyData.value = null
  aiClassifyVisible.value = true

  try {
    const ids = selectedRows.value.map(r => r.id)
    const res = await aiClassifyQuestions(ids)
    aiClassifyData.value = res.data
  } catch (e) {
    ElMessage.error(e.response?.data?.message || 'AI分类失败，请检查AI服务是否可用')
    aiClassifyVisible.value = false
  } finally {
    aiClassifyLoading.value = false
  }
}

async function handleApplyAiClassify() {
  if (!aiClassifyData.value || aiClassifyData.value.successCount === 0) return

  aiClassifyApplying.value = true
  try {
    // 只应用成功的项
    const successItems = aiClassifyData.value.items.filter(item => item.success)
    await applyAiClassifyResult(successItems)
    ElMessage.success(`已成功应用 AI 分类结果（${successItems.length} 道题目）`)
    aiClassifyVisible.value = false
    await loadTags()
    loadData()
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '应用分类结果失败')
  } finally {
    aiClassifyApplying.value = false
  }
}
</script>

<style scoped>
:deep(.preview-row-error) {
  background-color: #fef0f0 !important;
}
:deep(.preview-row-warning) {
  background-color: #fdf6ec !important;
}
</style>
