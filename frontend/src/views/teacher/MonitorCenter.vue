<template>
  <div class="monitor-center">
    <!-- 顶部信息 -->
    <div class="monitor-header">
      <div class="header-left">
        <el-icon :size="24"><Monitor /></el-icon>
        <span class="exam-title">监考大屏 - {{ examTitle }}</span>
        <el-tag :type="examStatus === 1 ? 'success' : examStatus === 2 ? 'danger' : 'info'">
          {{ examStatus === 1 ? '进行中' : examStatus === 2 ? '已结束' : '未开始' }}
        </el-tag>
      </div>
      <div class="header-right">
        <el-switch
          v-model="autoRefresh"
          active-text="自动刷新"
          @change="toggleAutoRefresh"
        />
        <el-button @click="loadAllData" :loading="loading">
          <el-icon><Refresh /></el-icon>刷新
        </el-button>
      </div>
    </div>

    <!-- 统计面板 -->
    <el-row :gutter="16" class="stats-row">
      <el-col :span="4">
        <div class="stat-card online">
          <div class="stat-value">{{ onlineStats.online || 0 }}</div>
          <div class="stat-label">在线考生</div>
        </div>
      </el-col>
      <el-col :span="4">
        <div class="stat-card disconnected">
          <div class="stat-value">{{ onlineStats.disconnected || 0 }}</div>
          <div class="stat-label">断网中</div>
        </div>
      </el-col>
      <el-col :span="4">
        <div class="stat-card violation-total">
          <div class="stat-value">{{ stats.totalCount }}</div>
          <div class="stat-label">总违规</div>
        </div>
      </el-col>
      <el-col :span="4">
        <div class="stat-card switch">
          <div class="stat-value">{{ stats.switchCount }}</div>
          <div class="stat-label">切屏</div>
        </div>
      </el-col>
      <el-col :span="4">
        <div class="stat-card noface">
          <div class="stat-value">{{ stats.noFaceCount }}</div>
          <div class="stat-label">人脸消失</div>
        </div>
      </el-col>
      <el-col :span="4">
        <div class="stat-card ai">
          <div class="stat-value">{{ stats.otherCount || 0 }}</div>
          <div class="stat-label">AI检测</div>
        </div>
      </el-col>
    </el-row>

    <!-- 主内容区 -->
    <el-row :gutter="16">
      <!-- 左侧: 在线学生 + 视频预览 -->
      <el-col :span="8">
        <el-card class="panel-card">
          <template #header>
            <span>考生监控 ({{ onlineStudents.length }})</span>
          </template>
          <div class="student-grid">
            <div
              v-for="student in onlineStudents"
              :key="student.studentId"
              :class="['student-card', studentStatusClass(student)]"
              @click="selectStudent(student)"
            >
              <div class="student-avatar">
                <el-icon :size="32"><User /></el-icon>
              </div>
              <div class="student-info">
                <div class="student-name">{{ student.studentName || '未知' }}</div>
                <div class="student-status" :class="statusDotClass(student)">
                  {{ statusText(student) }}
                </div>
              </div>
              <div v-if="student.violationCount > 0" class="violation-badge">
                {{ student.violationCount }}
              </div>
            </div>
            <div v-if="onlineStudents.length === 0" class="empty-hint">
              暂无在线考生
            </div>
          </div>
        </el-card>
      </el-col>

      <!-- 中间: 实时违规列表 -->
      <el-col :span="10">
        <el-card class="panel-card">
          <template #header>
            <div class="card-header-inner">
              <span>实时违规</span>
              <div class="filter-bar">
                <el-input v-model="filterParams.violationType" placeholder="违规类型" 
                  clearable size="small" style="width: 120px" @keyup.enter="loadViolations" />
                <el-button type="primary" size="small" @click="loadViolations">搜索</el-button>
              </div>
            </div>
          </template>
          <div class="violation-list">
            <div
              v-for="v in violationList"
              :key="v.id"
              :class="['violation-item', severityClass(v.severity)]"
              @click="viewViolationDetail(v)"
            >
              <div class="violation-left">
                <el-tag :type="getViolationTagType(v.violationType)" size="small">
                  {{ getViolationTypeName(v.violationType) }}
                </el-tag>
                <span class="violation-student">{{ v.studentName || '未知' }}</span>
              </div>
              <div class="violation-right">
                <span class="violation-desc">{{ v.description }}</span>
                <span class="violation-time">{{ v.occurTime }}</span>
                <div class="violation-actions">
                  <el-button v-if="v.screenshotUrl" type="primary" link size="small" @click.stop="viewScreenshot(v)">
                    截图
                  </el-button>
                  <el-button v-if="v.videoEvidenceUrl" type="success" link size="small" @click.stop="viewVideo(v)">
                    视频
                  </el-button>
                </div>
              </div>
            </div>
            <el-empty v-if="violationList.length === 0" description="暂无违规记录" :image-size="60" />
          </div>
          <div style="margin-top: 12px; text-align: right;">
            <el-pagination
              v-model:current-page="filterParams.pageNum"
              v-model:page-size="filterParams.pageSize"
              :total="violationTotal"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next"
              @size-change="loadViolations"
              @current-change="loadViolations"
              small
            />
          </div>
        </el-card>
      </el-col>

      <!-- 右侧: 操作面板 + 快捷操作 -->
      <el-col :span="6">
        <!-- 快捷操作 -->
        <el-card class="panel-card" style="margin-bottom: 16px;">
          <template #header><span>快捷操作</span></template>
          <div class="action-buttons">
            <el-button type="danger" @click="handleForceSubmit" :disabled="!selectedStudent">
              <el-icon><CircleClose /></el-icon>强制交卷
            </el-button>
            <el-button type="warning" @click="handleSendWarning" :disabled="!selectedStudent">
              <el-icon><Warning /></el-icon>发送警告
            </el-button>
          </div>
          <div v-if="selectedStudent" class="selected-info">
            <p>当前选中: <strong>{{ selectedStudent.studentName }}</strong></p>
          </div>
        </el-card>

        <!-- 考试信息 -->
        <el-card class="panel-card">
          <template #header><span>考试信息</span></template>
          <div class="exam-info-list">
            <div class="info-item">
              <span class="info-label">考试时长</span>
              <span>{{ examInfo.duration }}分钟</span>
            </div>
            <div class="info-item">
              <span class="info-label">切屏限制</span>
              <span>{{ examInfo.maxSwitchCount }}次</span>
            </div>
            <div class="info-item">
              <span class="info-label">人脸核验</span>
              <el-tag :type="examInfo.requireFaceVerify ? 'warning' : 'info'" size="small">
                {{ examInfo.requireFaceVerify ? '已启用' : '未启用' }}
              </el-tag>
            </div>
            <div class="info-item">
              <span class="info-label">WebSocket</span>
              <el-tag :type="wsConnected ? 'success' : 'danger'" size="small">
                {{ wsConnected ? '已连接' : '未连接' }}
              </el-tag>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 截图预览弹窗 -->
    <el-dialog v-model="screenshotVisible" title="违规截图" width="600px">
      <div style="text-align: center;">
        <img :src="screenshotUrl" style="max-width: 100%; border-radius: 8px;" />
      </div>
      <div style="margin-top: 12px; text-align: center; color: #909399;">
        {{ screenshotInfo }}
      </div>
    </el-dialog>

    <!-- 警告发送弹窗 -->
    <el-dialog v-model="warningVisible" title="发送警告" width="400px">
      <el-input v-model="warningMessage" type="textarea" :rows="3" placeholder="请输入警告内容" />
      <template #footer>
        <el-button @click="warningVisible = false">取消</el-button>
        <el-button type="warning" @click="sendWarning">发送</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, computed } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getViolationStats, getViolationList } from '@/api/violation'
import { getExam } from '@/api/exam'
import { getOnlineStats, getOnlineStudents, getVideoEvidence } from '@/api/monitor'
import examWs from '@/utils/examWebSocket'

const route = useRoute()
const examId = parseInt(route.params.examId)

const loading = ref(false)
const autoRefresh = ref(true)
const examTitle = ref('')
const examStatus = ref(1)
const examInfo = ref({ duration: 60, maxSwitchCount: 3, requireFaceVerify: false })
const wsConnected = ref(false)
const stats = ref({ totalCount: 0, switchCount: 0, faceLostCount: 0, noFaceCount: 0, otherCount: 0 })
const onlineStats = ref({ online: 0, disconnected: 0, total: 0, offline: 0 })
const onlineStudents = ref([])
const violationList = ref([])
const violationTotal = ref(0)
const selectedStudent = ref(null)
const screenshotVisible = ref(false)
const screenshotUrl = ref('')
const screenshotInfo = ref('')
const warningVisible = ref(false)
const warningMessage = ref('')

const filterParams = ref({ pageNum: 1, pageSize: 20, violationType: '' })
let refreshTimer = null

onMounted(async () => {
  await loadExamInfo()
  await loadAllData()
  startAutoRefresh()
  connectWebSocket()
})

onBeforeUnmount(() => {
  stopAutoRefresh()
  examWs.disconnect()
})

async function loadExamInfo() {
  try {
    const res = await getExam(examId, { silent: true })
    const exam = res.data
    examTitle.value = exam.title || '未知考试'
    examStatus.value = exam.status
    examInfo.value = {
      duration: exam.duration,
      maxSwitchCount: exam.maxSwitchCount,
      requireFaceVerify: exam.requireFaceVerify === 1
    }
  } catch (e) {
    console.warn('加载考试信息失败:', e.message)
  }
}

async function loadAllData() {
  loading.value = true
  try {
    await Promise.all([
      loadStats(),
      loadOnlineStats(),
      loadOnlineStudents(),
      loadViolations()
    ])
  } finally {
    loading.value = false
  }
}

async function loadStats() {
  try {
    const res = await getViolationStats(examId)
    stats.value = res.data || { totalCount: 0, switchCount: 0, noFaceCount: 0, otherCount: 0 }
  } catch (e) {
    console.warn('加载违规统计失败:', e.message)
  }
}

async function loadOnlineStats() {
  try {
    const res = await getOnlineStats(examId)
    onlineStats.value = res.data || { online: 0, disconnected: 0, total: 0, offline: 0 }
  } catch (e) {
    console.warn('加载在线统计失败:', e.message)
  }
}

async function loadOnlineStudents() {
  try {
    const res = await getOnlineStudents(examId)
    onlineStudents.value = res.data || []
  } catch (e) {
    console.warn('加载在线学生列表失败:', e.message)
  }
}

async function loadViolations() {
  try {
    const res = await getViolationList({ ...filterParams.value, examId })
    violationList.value = res.data.records || []
    violationTotal.value = res.data.total || 0
  } catch (e) {
    console.warn('加载违规列表失败:', e.message)
  }
}

function connectWebSocket() {
  // 教师以teacher角色加入
  const userId = parseInt(localStorage.getItem('userId') || '0')
  examWs.connect(examId, userId, 'teacher')

  examWs.on('connected', () => {
    wsConnected.value = true
    console.log('监考大屏WebSocket已连接')
  })

  examWs.on('disconnected', () => {
    wsConnected.value = false
  })

  // 监听实时违规
  examWs.on('ai_violation', (data) => {
    loadStats()
    loadViolations()
    // 可选: 播放提示音
  })

  // 监听学生上下线
  examWs.on('student_online', (data) => {
    loadOnlineStats()
    loadOnlineStudents()
  })

  examWs.on('student_offline', (data) => {
    loadOnlineStats()
    loadOnlineStudents()
  })

  examWs.on('student_disconnect', (data) => {
    loadOnlineStats()
    loadOnlineStudents()
    ElMessage.warning(`${data.studentName || '考生'} 已断网`)
  })

  examWs.on('violation_alert', (data) => {
    loadStats()
    loadViolations()
  })

  // 监听视频帧（可选：在侧边栏显示缩略图）
  examWs.on('frame', (arrayBuffer) => {
    // 视频帧处理 - 可以显示在学生卡片上
  })
}

function startAutoRefresh() {
  if (refreshTimer) clearInterval(refreshTimer)
  if (autoRefresh.value) {
    refreshTimer = setInterval(() => {
      loadStats()
      loadOnlineStats()
    }, 10000)
  }
}

function stopAutoRefresh() {
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
}

function toggleAutoRefresh(val) {
  if (val) startAutoRefresh()
  else stopAutoRefresh()
}

function selectStudent(student) {
  selectedStudent.value = student
}

function studentStatusClass(student) {
  if (student.onlineStatus === 1) return 'status-online'
  if (student.onlineStatus === 2) return 'status-disconnected'
  return 'status-offline'
}

function statusDotClass(student) {
  if (student.onlineStatus === 1) return 'dot-online'
  if (student.onlineStatus === 2) return 'dot-disconnected'
  return 'dot-offline'
}

function statusText(student) {
  if (student.onlineStatus === 1) return '在线'
  if (student.onlineStatus === 2) return '断网中'
  return '离线'
}

function getViolationTypeName(type) {
  const map = {
    switch_screen: '切屏', no_face: '无人脸', face_lost: '人脸消失',
    leave_page: '离开页面', multi_face: '多人脸', look_away: '东张西望',
    forbidden_item: '违禁物品', other: '其他'
  }
  return map[type] || type || '未知'
}

function getViolationTagType(type) {
  if (type === 'switch_screen') return 'warning'
  if (type === 'no_face' || type === 'face_lost' || type === 'multi_face') return 'danger'
  if (type === 'forbidden_item') return 'danger'
  if (type === 'look_away') return 'warning'
  return 'info'
}

function severityClass(severity) {
  if (severity === 3) return 'severity-high'
  if (severity === 2) return 'severity-medium'
  return 'severity-low'
}

function viewScreenshot(row) {
  screenshotUrl.value = '/api/' + row.screenshotUrl
  screenshotInfo.value = `${row.studentName || '未知'} - ${getViolationTypeName(row.violationType)} - ${row.occurTime}`
  screenshotVisible.value = true
}

function viewVideo(row) {
  ElMessage.info('视频证据回放功能')
}

function viewViolationDetail(v) {
  if (v.screenshotUrl) viewScreenshot(v)
}

async function handleForceSubmit() {
  if (!selectedStudent.value) return
  try {
    await ElMessageBox.confirm(
      `确定强制交卷 ${selectedStudent.value.studentName}？此操作不可撤销。`,
      '强制交卷确认', { type: 'warning' }
    )
    examWs.send({
      type: 'force_submit',
      targetUserId: selectedStudent.value.studentId,
      reason: '监考教师强制交卷'
    })
    ElMessage.success('已发送强制交卷指令')
  } catch { /* 取消 */ }
}

function handleSendWarning() {
  if (!selectedStudent.value) return
  warningMessage.value = ''
  warningVisible.value = true
}

function sendWarning() {
  if (!warningMessage.value.trim()) {
    ElMessage.warning('请输入警告内容')
    return
  }
  examWs.send({
    type: 'warning',
    targetUserId: selectedStudent.value.studentId,
    message: warningMessage.value
  })
  warningVisible.value = false
  ElMessage.success('警告已发送')
}
</script>

<style scoped>
.monitor-center {
  padding: 16px;
}

.monitor-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding: 12px 20px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.exam-title {
  font-size: 18px;
  font-weight: 600;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.stats-row {
  margin-bottom: 16px;
}

.stat-card {
  padding: 16px;
  border-radius: 8px;
  text-align: center;
  color: #fff;
}

.stat-card.online { background: linear-gradient(135deg, #67c23a, #529b2e); }
.stat-card.disconnected { background: linear-gradient(135deg, #e6a23c, #d4912a); }
.stat-card.violation-total { background: linear-gradient(135deg, #f56c6c, #d9534f); }
.stat-card.switch { background: linear-gradient(135deg, #909399, #73767a); }
.stat-card.noface { background: linear-gradient(135deg, #e6a23c, #cf9236); }
.stat-card.ai { background: linear-gradient(135deg, #409eff, #337ecc); }

.stat-value {
  font-size: 28px;
  font-weight: bold;
}

.stat-label {
  font-size: 12px;
  opacity: 0.9;
  margin-top: 4px;
}

.panel-card {
  height: 100%;
}

.panel-card :deep(.el-card__header) {
  padding: 12px 16px;
  font-weight: 600;
}

.card-header-inner {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.filter-bar {
  display: flex;
  gap: 8px;
}

.student-grid {
  max-height: 480px;
  overflow-y: auto;
}

.student-card {
  display: flex;
  align-items: center;
  padding: 10px 12px;
  border-bottom: 1px solid #f0f0f0;
  cursor: pointer;
  transition: background 0.2s;
  position: relative;
}

.student-card:hover {
  background: #f5f7fa;
}

.student-card.selected {
  background: #ecf5ff;
  border-left: 3px solid #409eff;
}

.student-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: #e4e7ed;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #909399;
  flex-shrink: 0;
}

.student-info {
  margin-left: 12px;
  flex: 1;
}

.student-name {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
}

.student-status {
  font-size: 12px;
  margin-top: 2px;
  display: flex;
  align-items: center;
  gap: 4px;
}

.student-status::before {
  content: '';
  width: 6px;
  height: 6px;
  border-radius: 50%;
}

.dot-online::before { background: #67c23a; }
.dot-disconnected::before { background: #e6a23c; animation: blink 1s infinite; }
.dot-offline::before { background: #909399; }

.status-online { border-left: 3px solid #67c23a; }
.status-disconnected { border-left: 3px solid #e6a23c; }
.status-offline { border-left: 3px solid #909399; }

.violation-badge {
  position: absolute;
  top: 8px;
  right: 8px;
  min-width: 20px;
  height: 20px;
  border-radius: 10px;
  background: #f56c6c;
  color: #fff;
  font-size: 11px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.violation-list {
  max-height: 400px;
  overflow-y: auto;
}

.violation-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 0;
  border-bottom: 1px solid #f5f5f5;
  cursor: pointer;
  transition: background 0.2s;
}

.violation-item:hover {
  background: #fafafa;
}

.violation-left {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.violation-student {
  font-size: 13px;
  color: #303133;
  min-width: 60px;
}

.violation-right {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
  justify-content: flex-end;
}

.violation-desc {
  font-size: 12px;
  color: #606266;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.violation-time {
  font-size: 11px;
  color: #909399;
  white-space: nowrap;
}

.severity-high { border-left: 3px solid #f56c6c; }
.severity-medium { border-left: 3px solid #e6a23c; }
.severity-low { border-left: 3px solid #909399; }

.action-buttons {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.action-buttons .el-button {
  width: 100%;
}

.selected-info {
  margin-top: 12px;
  padding: 8px 12px;
  background: #ecf5ff;
  border-radius: 4px;
  font-size: 13px;
}

.exam-info-list {
  font-size: 13px;
}

.info-item {
  display: flex;
  justify-content: space-between;
  padding: 8px 0;
  border-bottom: 1px solid #f5f5f5;
}

.info-label {
  color: #909399;
}

.empty-hint {
  text-align: center;
  color: #909399;
  padding: 40px 0;
  font-size: 13px;
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.3; }
}
</style>
