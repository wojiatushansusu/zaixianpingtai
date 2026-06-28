<template>
  <!-- 悬浮按钮 -->
  <div class="ai-fab" @click="openDrawer">
    <el-icon :size="24"><MagicStick /></el-icon>
    <!-- 教师管理角标 -->
    <span v-if="isTeacherOrAdmin" class="fab-badge">AI+</span>
  </div>

  <!-- 抽屉 -->
  <el-drawer
    v-model="visible"
    title=""
    :size="480"
    :show-close="false"
    direction="rtl"
    class="ai-assistant-drawer"
  >
    <template #header>
      <div class="drawer-header">
        <div class="header-left">
          <div class="header-icon">
            <el-icon :size="20"><MagicStick /></el-icon>
          </div>
          <div>
            <div class="header-title">
              {{ isStudent ? 'AI 学习助手' : 'AI 智能助手' }}
              <el-tag v-if="isTeacherOrAdmin" size="small" type="warning" effect="dark" style="margin-left:6px;font-size:10px;">指令模式</el-tag>
            </div>
            <div class="header-subtitle">{{ statusText }}</div>
          </div>
        </div>
        <div class="header-actions">
          <el-button text size="small" @click="clearChat" :disabled="chatMessages.length <= 1">
            <el-icon><Delete /></el-icon> 清空
          </el-button>
          <el-button text size="small" @click="visible = false">
            <el-icon><Close /></el-icon>
          </el-button>
        </div>
      </div>
    </template>

    <div class="drawer-body">
      <!-- 消息列表 -->
      <div class="chat-messages" ref="messagesRef">
        <div v-if="chatMessages.length === 0" class="chat-empty">
          <el-icon :size="48" color="var(--color-text-placeholder)"><ChatDotRound /></el-icon>
          <p>{{ isStudent ? '你好，我是 AI 学习助手 👋' : '你好，我是 AI 智能助手 👋' }}</p>
          <p class="empty-hint">
            {{ isStudent 
              ? '可以问我任何学习问题，或让我帮你讲解错题' 
              : '可以用自然语言让我帮您查询数据、管理考试平台' }}
          </p>
        </div>

        <div
          v-for="(msg, idx) in chatMessages"
          :key="idx"
          class="chat-message"
          :class="msg.role"
        >
          <div class="msg-avatar">
            <el-icon v-if="msg.role === 'user'" :size="16"><User /></el-icon>
            <el-icon v-else :size="16"><MagicStick /></el-icon>
          </div>
          <div class="msg-bubble">
            <div class="msg-content" v-html="formatContent(msg.content)"></div>
            <!-- 指令结果卡片 -->
            <div v-if="msg.commandResult" class="command-result-card" @click="navigateTo(msg.commandResult.route)">
              <div class="card-header-row">
                <span class="card-title">{{ msg.commandResult.title }}</span>
                <el-button type="primary" link size="small">查看详情 →</el-button>
              </div>
              <div class="card-body" v-html="formatContent(msg.commandResult.summary)"></div>
              <div v-if="msg.commandResult.dataList && msg.commandResult.dataList.length > 0" class="data-preview">
                <div v-for="(item, i) in msg.commandResult.dataList.slice(0, 5)" :key="i" class="preview-item">
                  {{ item }}
                </div>
                <div v-if="msg.commandResult.dataList.length > 5" class="preview-more">
                  ...等 {{ msg.commandResult.dataList.length }} 条数据
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 加载中 -->
        <div v-if="loading" class="chat-message assistant">
          <div class="msg-avatar">
            <el-icon :size="16"><MagicStick /></el-icon>
          </div>
          <div class="msg-bubble">
            <div v-if="commandLoading" class="cmd-loading">
              <el-icon class="is-loading" :size="18"><Loading /></el-icon>
              <span>正在执行指令...</span>
            </div>
            <div v-else class="msg-loading">
              <span class="dot"></span><span class="dot"></span><span class="dot"></span>
            </div>
          </div>
        </div>
      </div>

      <!-- 快捷问题（无题目上下文时显示） -->
      <div v-if="chatMessages.length <= 1 && !loading" class="quick-questions">
        <div class="quick-label">{{ isStudent ? '试试问我：' : '试试这样对我说：' }}</div>
        <div class="quick-list">
          <el-button
            v-for="q in quickQuestions"
            :key="q"
            size="small"
            round
            plain
            @click="sendQuickQuestion(q)"
          >{{ q }}</el-button>
        </div>
      </div>
    </div>

    <template #footer>
      <div class="drawer-footer">
        <el-input
          v-model="inputText"
          :placeholder="isStudent ? '输入你的问题...' : '描述你想要执行的操作...'"
          :disabled="loading"
          @keyup.enter="sendMessage"
          size="large"
          class="chat-input"
        >
          <template #append>
            <el-button
              :icon="Promotion"
              @click="sendMessage"
              :disabled="!inputText.trim() || loading"
              :loading="loading"
              type="primary"
            />
          </template>
        </el-input>
        <div v-if="isTeacherOrAdmin" class="mode-hint">
          <el-tag size="small" type="info" effect="plain">💡 支持自然语言操作：查题库 / 创建试卷 / 创建考试 / 查成绩 / 查用户 等</el-tag>
        </div>
      </div>
    </template>
  </el-drawer>
</template>

<script setup>
import { ref, reactive, computed, onMounted, inject } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { MagicStick, Close, Delete, User, ChatDotRound, Promotion, Loading } from '@element-plus/icons-vue'
import { aiChat, getAiStatus, aiCommand } from '@/api/aiAssistant'

// 引入业务API - 用于执行指令后获取实际数据
import { getQuestionList, getTagList as getQTagList } from '@/api/question'
import { getPaperList, getAllPapers, createPaper } from '@/api/paper'
import { getExamList, getExam, getExamGrades, getExamStatistics, createExam } from '@/api/exam'
import { getUserList, getRoles } from '@/api/user'

const emit = defineEmits(['open'])
const router = useRouter()

// 状态
const visible = ref(false)
const loading = ref(false)
const commandLoading = ref(false)
const inputText = ref('')
const messagesRef = ref(null)
const chatMessages = reactive([])

// 角色信息
const userRole = inject('userRole', 'student')
const isStudent = computed(() => userRole === 'student')
const isTeacherOrAdmin = computed(() => !isStudent.value)

// 快捷问题 - 根据角色动态展示
const quickQuestions = computed(() => {
  if (isStudent.value) {
    return ['帮我制定一个学习计划', '如何提高选择题的正确率？', '简答题有什么答题技巧？']
  }
  return [
    '帮我看看题库里有多少道题',
    '查询当前进行中的考试',
    '查看最近一次考试的成绩分析',
    '显示所有试卷列表',
    '帮我创建试卷《Java基础测试》',
    '帮我创建考试《期末考试》，试卷ID 1，时长60分钟',
    '统计一下用户情况'
  ]
})

const statusText = ref('加载中...')

onMounted(async () => {
  try {
    const res = await getAiStatus()
    statusText.value = res.data?.enabled
      ? (res.data?.provider || 'AI') + (isTeacherOrAdmin.value ? ' 智能助手' : '') + ' 已就绪'
      : '未启用（后端未配置AI）'
  } catch (e) {
    if (e.code === 'ERR_NETWORK' || !e.response) {
      statusText.value = '后端服务未连接'
    } else {
      statusText.value = '服务异常'
    }
  }
})

// 暴露给父组件的方法 —— 传入单个题目或题目数组，自动讲解
function openDrawer(questionContext) {
  visible.value = true
  // 过滤掉非题目参数（如 MouseEvent、无参点击）
  if (!questionContext || typeof questionContext !== 'object') return

  const isSingle = 'content' in questionContext && !Array.isArray(questionContext)
  const isArray = Array.isArray(questionContext) && questionContext.length > 0 && 'content' in questionContext[0]

  if (!isSingle && !isArray) return

  const questions = isSingle ? [questionContext] : questionContext

  chatMessages.splice(0, chatMessages.length)
  if (questions.length > 0) {
    explainQuestions(questions)
  }
}

// 批量讲解题目
async function explainQuestions(questions) {
  for (let i = 0; i < questions.length; i++) {
    const q = questions[i]
    loading.value = true
    try {
      const res = await aiChat({
        messages: [],
        questionContext: {
          content: q.content,
          options: buildOptions(q),
          correctAnswer: q.correctAnswer,
          studentAnswer: q.studentAnswer,
          analysis: q.analysis || '',
          questionTypeDesc: q.questionTypeDesc || ''
        }
      })
      const reply = res.data?.content || '抱歉，暂时无法回答'
      chatMessages.push({ role: 'user', content: `请帮我讲解这道错题：\n${q.content}` })
      chatMessages.push({ role: 'assistant', content: reply })
      scrollToBottom()
    } catch (e) {
      ElMessage.error(`第 ${i + 1} 题讲解失败，请稍后重试`)
      break
    } finally {
      loading.value = false
    }
  }
}

function buildOptions(q) {
  const opts = []
  if (q.optionA) opts.push(`A. ${q.optionA}`)
  if (q.optionB) opts.push(`B. ${q.optionB}`)
  if (q.optionC) opts.push(`C. ${q.optionC}`)
  if (q.optionD) opts.push(`D. ${q.optionD}`)
  return opts
}

/**
 * 核心消息处理 - 区分学生模式和教师管理模式
 */
async function sendMessage() {
  const text = inputText.value.trim()
  if (!text || loading.value) return

  chatMessages.push({ role: 'user', content: text })
  inputText.value = ''
  scrollToBottom()

  // 教师/管理员走指令解析流程
  if (isTeacherOrAdmin.value) {
    await handleCommandFlow(text)
  } else {
    // 学生走原有对话流程
    await handleChatFlow(text)
  }
}

/**
 * 教师/管理员指令流程：先让AI识别意图，再调用对应API获取数据展示
 */
async function handleCommandFlow(text) {
  loading.value = true
  commandLoading.value = true

  try {
    // Step 1: 调用后端AI接口解析意图
    const cmdRes = await aiCommand({
      message: text,
      role: userRole
    })

    const isCmd = cmdRes.data?.isCommand
    const intent = cmdRes.data?.intent || ''
    const params = cmdRes.data?.params || {}
    const reply = cmdRes.data?.reply || ''

    // 如果不是有效指令，当作普通对话处理
    if (!isCmd || intent === 'unknown') {
      chatMessages.push({
        role: 'assistant',
        content: reply || '抱歉，我无法理解这个指令。您可以尝试说："查题库"、"看考试"、"成绩分析"等。'
      })
      scrollToBottom()
      return
    }

    // 帮助指令直接返回帮助信息
    if (intent === 'help') {
      chatMessages.push({ role: 'assistant', content: reply })
      scrollToBottom()
      return
    }

    // Step 2: 根据意图调用对应的业务API获取真实数据
    const resultData = await executeIntent(intent, params)

    // Step 3: 构建结果卡片消息
    chatMessages.push({
      role: 'assistant',
      content: reply,
      commandResult: resultData
    })

    scrollToBottom()
  } catch (e) {
    console.error('指令执行失败:', e)
    ElMessage.error(e.response?.data?.message || '指令执行失败')
    chatMessages.pop() // 移除失败的用户消息
  } finally {
    loading.value = false
    commandLoading.value = false
  }
}

/**
 * 根据意图执行对应业务API，返回结构化结果
 */
async function executeIntent(intent, params) {
  switch (intent) {
    case 'query_questions': {
      const query = {}
      if (params.keyword) query.keyword = params.keyword
      if (params.type != null) query.type = Number(params.type)
      if (params.difficulty != null) query.difficulty = Number(params.difficulty)
      query.pageNum = 1
      query.pageSize = 10
      const res = await getQuestionList(query)
      const records = res.data?.records || []
      const total = res.data?.total || 0
      return {
        title: `📋 题目列表 (共${total}道)`,
        summary: records.length > 0 
          ? `已为您找到 **${total}** 道符合条件的题目，以下是最新的${Math.min(records.length, total)}条：`
          : '暂未找到匹配的题目，请尝试调整搜索条件。',
        dataList: records.map(q => `${q.typeDesc || ''} | ${q.content?.substring(0, 60)}... | 难度:${['','简单','中等','困难'][q.difficulty||0]} | 分值:${q.score}`),
        route: '/teacher/questions'
      }
    }

    case 'stats_questions': {
      const [listRes] = await Promise.all([
        getQuestionList({ pageNum: 1, pageSize: 1 }),
        getQTagList().catch(() => ({ data: [] }))
      ])
      const total = listRes.data?.total || 0
      const tags = listRes.data || []
      return {
        title: '📊 题库统计',
        summary: `**总题数**: ${total} 道\n\n**标签数量**: ${tags.length} 个`,
        dataList: tags.map(t => `🏷️ ${t.name}`).slice(0, 8),
        route: '/teacher/questions'
      }
    }

    case 'query_papers': {
      const query = {}
      if (params.keyword) query.title = params.keyword
      query.pageNum = 1
      query.pageSize = 10
      const res = await getPaperList(query)
      const records = res.data?.records || []
      const total = res.data?.total || 0
      return {
        title: `📝 试卷列表 (共${total}份)`,
        summary: records.length > 0 
          ? `共 **${total}** 份试卷：`
          : '暂无试卷，您可以前往试卷管理创建新试卷。',
        dataList: records.map(p => `${p.title} | 总分:${p.totalScore} 及格:${p.passScore}分 | ${p.questionCount||'?'}题`),
        route: '/teacher/papers'
      }
    }

    case 'create_paper': {
      const title = params.title
      if (!title) {
        return { title: '📝 创建试卷', summary: '请提供试卷标题，例如：帮我创建试卷《Java基础测试》', route: '/teacher/papers' }
      }
      try {
        const paperData = {
          title: title,
          description: params.description || '',
          passScore: params.passScore || 60
        }
        const res = await createPaper(paperData)
        const paperId = res.data?.id || res.data
        return {
          title: `📝 试卷创建成功`,
          summary: `试卷 **${title}** 已创建成功！(ID: ${paperId})\n\n下一步您可以：\n1. 前往试卷管理添加题目\n2. 继续说"创建考试《xxx》，试卷ID ${paperId}"来发布考试`,
          dataList: [],
          route: '/teacher/papers'
        }
      } catch (e) {
        return { title: '📝 创建试卷失败', summary: `创建失败：${e.response?.data?.message || e.message}\n\n请检查试卷标题是否重复，或前往试卷管理手动创建。`, route: '/teacher/papers' }
      }
    }

    case 'query_exams': {
      const query = {}
      if (params.title) query.title = params.title
      if (params.status != null) query.status = Number(params.status)
      query.pageNum = 1
      query.pageSize = 10
      const res = await getExamList(query)
      const records = res.data?.records || []
      const total = res.data?.total || 0
      return {
        title: `📋 考试列表 (共${total}场)`,
        summary: records.length > 0 
         ? `共 **${total}** 场考试：`
          : '暂无考试数据。',
        dataList: records.map(e => {
          const statusMap = { 0: '⏳ 未开始', 1: '🟢 进行中', 2: '✅ 已结束' }
          return `${statusMap[e.status]||''} ${e.title} | 考生:${e.whitelistCount||0}人 | ${e.startTime||''}~${e.endTime||''}`
        }),
        route: '/teacher/exams'
      }
    }

    case 'create_exam': {
      const title = params.title
      const paperId = params.paperId
      if (!title) {
        return { title: '📋 创建考试', summary: '请提供考试标题，例如：帮我创建考试《期末考试》，试卷ID 1', route: '/teacher/exams' }
      }
      if (!paperId) {
        return { title: '📋 创建考试', summary: `请提供试卷ID。您可以先说"查看试卷列表"获取试卷ID，然后说"创建考试《${title}》，试卷ID 1"`, route: '/teacher/exams' }
      }
      try {
        const now = new Date()
        const startStr = params.startTime || `${now.getFullYear()}-${String(now.getMonth()+1).padStart(2,'0')}-${String(now.getDate()).padStart(2,'0')} ${String(now.getHours()).padStart(2,'0')}:${String(now.getMinutes()).padStart(2,'0')}`
        // 默认结束时间为开始时间+duration分钟
        let endStr = params.endTime
        const duration = Number(params.duration) || 60
        if (!endStr) {
          const endTime = new Date(startStr.replace(' ', 'T'))
          endTime.setMinutes(endTime.getMinutes() + duration)
          endStr = `${endTime.getFullYear()}-${String(endTime.getMonth()+1).padStart(2,'0')}-${String(endTime.getDate()).padStart(2,'0')} ${String(endTime.getHours()).padStart(2,'0')}:${String(endTime.getMinutes()).padStart(2,'0')}`
        }
        const examData = {
          title: title,
          paperId: Number(paperId),
          duration: duration,
          startTime: startStr,
          endTime: endStr,
          passScore: Number(params.passScore) || 60,
          maxSwitchCount: Number(params.maxSwitchCount) || 3,
          requireFaceVerify: Number(params.requireFaceVerify) || 0,
          enableAiMonitor: params.enableAiMonitor != null ? Number(params.enableAiMonitor) : 1
        }
        const res = await createExam(examData)
        const examId = res.data?.id || res.data
        return {
          title: `📋 考试创建成功`,
          summary: `考试 **${title}** 已创建成功！(ID: ${examId})\n\n配置：\n- 试卷ID: ${paperId}\n- 时长: ${duration}分钟\n- 时间: ${startStr} ~ ${endStr}\n- 切屏限制: ${examData.maxSwitchCount}次\n- 人脸核验: ${examData.requireFaceVerify ? '是' : '否'}\n- AI监考: ${examData.enableAiMonitor ? '启用' : '关闭'}\n\n下一步：前往考试管理添加考生白名单。`,
          dataList: [],
          route: '/teacher/exams'
        }
      } catch (e) {
        return { title: '📋 创建考试失败', summary: `创建失败：${e.response?.data?.message || e.message}\n\n常见原因：\n1. 试卷ID不存在\n2. 考试标题重复\n3. 时间格式有误\n\n请前往考试管理手动创建。`, route: '/teacher/exams' }
      }
    }

    case 'query_exam_detail': {
      let examId = params.examId
      // 处理 "latest" 等模糊标识
      if (!examId || examId === 'latest') {
        const listRes = await getExamList({ pageNum: 1, pageSize: 5 })
        const records = listRes.data?.records || []
        if (records.length > 0) examId = records[0].id
        else return { title: '📋 考试详情', summary: '没有找到任何考试记录。', route: '/teacher/exams' }
      }
      const res = await getExam(Number(examId), { silent: true })
      const exam = res.data || {}
      const statusMap = { 0: '未开始', 1: '进行中', 2: '已结束' }
      return {
        title: `📋 考试详情: ${exam.title || '未知'}`,
        summary: `**状态**: ${statusMap[exam.status]||'未知'}\n` +
                 `**试卷**: ${exam.paperTitle||'未指定'}\n` +
                 `**时间**: ${exam.startTime||''} ~ ${exam.endTime||''}\n` +
                 `**时长**: ${exam.duration||0}分钟\n` +
                 `**考生数**: ${exam.whitelistCount||0}人\n` +
                 `**已提交**: ${exam.submittedCount||0}人\n` +
                 `**切屏限制**: ${exam.maxSwitchCount||0}次\n` +
                 `**人脸核验**: ${exam.requireFaceVerify?'需要':'不需要'}\n` +
                 `**AI监考**: ${exam.enableAiMonitor!==0?'启用':'关闭'}`,
        route: `/teacher/exams/${examId}/grades`
      }
    }

    case 'query_exam_stats': {
      let examId = params.examId
      if (!examId || examId === 'latest') {
        const listRes = await getExamList({ pageNum: 1, pageSize: 3 })
        const records = listRes.data?.records || []
        if (records.length > 0) {
          // 找一个已结束有成绩的考试
          const finished = records.find(e => e.status === 2) || records[0]
          examId = finished.id
        } else {
          return { title: '📊 考试统计', summary: '没有可统计的考试。', route: '/teacher/exams' }
        }
      }
      try {
        const [statsRes, gradesRes] = await Promise.all([
          getExamStatistics(Number(examId)),
          getExamGrades(Number(examId))
        ])
        const stats = statsRes.data || {}
        const grades = gradesRes.data || []
        return {
          title: `📊 考试统计: ${stats.examTitle||''}`,
          summary: `**参考人数**: ${stats.totalStudents||0}人\n` +
                   `**平均分**: ${stats.avgScore||0}\n` +
                   `**最高分**: ${stats.maxScore||0}\n` +
                   `**最低分**: ${stats.minScore||0}\n` +
                   `**及格人数**: ${stats.passCount||0}人\n` +
                   `**及格率**: ${stats.passRate||0}%`,
          dataList: grades.slice(0, 8).map(g =>
            `${g.studentName||g.studentUsername||'?'} | 得分:${g.score} | ${g.isPassed===1?'✅及格':'❌不及格'} | 提交:${g.submitTime||'-'}`
          ),
          route: `/teacher/exams/${examId}/grades`
        }
      } catch (e) {
        return { title: '📊 考试统计', summary: '该考试暂无统计数据（可能还未有人交卷）。', route: '/teacher/exams' }
      }
    }

    case 'query_grades': {
      let examId = params.examId
      if (!examId || examId === 'latest') {
        const listRes = await getExamList({ pageNum: 1, pageSize: 3 })
        const records = listRes.data?.records || []
        if (records.length > 0) examId = records.find(e => e.status === 2)?.id || records[0].id
        else return { title: '📊 成绩列表', summary: '没有可查看成绩的考试。', route: '/teacher/exams' }
      }
      try {
        const [gradesRes, examRes] = await Promise.all([
          getExamGrades(Number(examId)),
          getExam(Number(examId), { silent: true })
        ])
        const grades = gradesRes.data || []
        const examTitle = examRes.data?.title || ''
        return {
          title: `📊 成绩列表: ${examTitle}`,
          summary: `共 **${grades.length}** 条成绩记录：`,
          dataList: grades.map(g =>
            `${g.studentName||g.studentUsername||'?'} | 得分:**${g.score}**分 | ${g.isPassed===1?'✅及格':'❌不及格'} | 切屏:${g.switchCount||0}次 | ${g.submitTime||''}`
          ),
          route: `/teacher/exams/${examId}/grades`
        }
      } catch (e) {
        return { title: '📊 成绩列表', summary: '该考试暂无成绩数据。', route: '/teacher/exams' }
      }
    }

    case 'query_analysis': {
      return { title: '📈 成绩分析', summary: '成绩分析功能已下线，您可以通过"成绩"按钮查看考试分数和排名。', route: '/teacher/exams' }
    }

    case 'query_users': {
      const query = {}
      if (params.username) query.username = params.username
      if (params.roleId) query.roleId = Number(params.roleId)
      if (params.status != null) query.status = Number(params.status)
      query.pageNum = 1
      query.pageSize = 10
      const res = await getUserList(query)
      const records = res.data?.records || []
      const total = res.data?.total || 0
      return {
        title: `👥 用户列表 (共${total}人)`,
        summary: records.length > 0 ? `共 **${total}** 位用户：` : '暂无匹配用户。',
        dataList: records.map(u => {
          const roleTag = { admin: '🔴管理员', teacher: '🟠教师', student: '🟢学生' }[u.roleKey] || u.roleName
          const statusTag = u.status === 1 ? '✅正常' : '❌禁用'
          return `${u.username}(${u.realName||'-'}) | ${roleTag} | ${statusTag} | ${u.email||'-'}`
        }),
        route: '/admin/users'
      }
    }

    case 'user_stats': {
      const [usersRes, rolesRes] = await Promise.all([
        getUserList({ pageNum: 1, pageSize: 1 }),
        getRoles()
      ])
      const total = usersRes.data?.total || 0
      const roles = rolesRes.data || []
      return {
        title: '👥 用户统计',
        summary: `**总用户数**: ${total} 人\n\n**角色分布**:\n${
          roles.map(r => `  ${r.roleName}: -`).join('\n')
        }\n\n💡 可使用 "查询所有管理员" 或 "查找用户张三" 等指令筛选具体用户。`,
        route: '/admin/users'
      }
    }

    default:
      return {
        title: '⚙️ 指令执行',
        summary: '指令 **' + intent + '** 已识别，但尚未实现对应的数据查询逻辑。',
        route: '/dashboard'
      }
  }
}

/**
 * 学生对话流程（保持原逻辑不变）
 */
async function handleChatFlow(text) {
  loading.value = true
  try {
    const res = await aiChat({
      messages: chatMessages.map(m => ({ role: m.role, content: m.content })),
      systemPrompt: '你是一位耐心的学习助手。用简洁清晰的语言回答学生的问题，如果是学科知识问题，给出准确的解释和例子。回答控制在200字以内。'
    })
    const reply = res.data?.content || '抱歉，暂时无法回答'
    chatMessages.push({ role: 'assistant', content: reply })
  } catch (e) {
    ElMessage.error(e.response?.data?.message || 'AI回复失败')
    chatMessages.pop()
  } finally {
    loading.value = false
    scrollToBottom()
  }
}

function sendQuickQuestion(q) {
  inputText.value = q
  sendMessage()
}

function clearChat() {
  chatMessages.splice(0, chatMessages.length)
}

function scrollToBottom() {
  setTimeout(() => {
    if (messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight
    }
  }, 50)
}

function formatContent(text) {
  if (!text) return ''
  return text
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    .replace(/\n/g, '<br>')
}

function navigateTo(routePath) {
  if (routePath) {
    visible.value = false
    router.push(routePath)
  }
}

defineExpose({ openDrawer })
</script>

<style scoped>
/* ── 悬浮按钮 ── */
.ai-fab {
  position: fixed;
  right: 28px;
  bottom: 28px;
  width: 52px;
  height: 52px;
  border-radius: 16px;
  background: linear-gradient(135deg, var(--color-primary, #4361ee), #7c8cf8);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 4px 16px rgba(67, 97, 238, 0.35);
  transition: all 0.3s;
  z-index: 999;
}
.ai-fab:hover {
  transform: translateY(-2px) scale(1.05);
  box-shadow: 0 6px 24px rgba(67, 97, 238, 0.45);
}
.fab-badge {
  position: absolute;
  top: -4px;
  right: -4px;
  background: #ff9800;
  color: #fff;
  font-size: 9px;
  padding: 1px 5px;
  border-radius: 8px;
  font-weight: 700;
  line-height: 14px;
}

/* ── 抽屉头部 ── */
.drawer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding-right: 4px;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.header-icon {
  width: 38px;
  height: 38px;
  border-radius: 10px;
  background: linear-gradient(135deg, var(--color-primary, #4361ee), #7c8cf8);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
}
.header-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--color-text-primary, #1a1a2e);
  display: flex;
  align-items: center;
  gap: 4px;
}
.header-subtitle {
  font-size: 12px;
  color: var(--color-text-secondary, #8892a4);
  margin-top: 1px;
}
.header-actions {
  display: flex;
  gap: 4px;
}

/* ── 抽屉内容 ── */
.drawer-body {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}

/* ── 消息列表 ── */
.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 4px 0;
}
.chat-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--color-text-secondary, #8892a4);
  text-align: center;
  gap: 8px;
}
.chat-empty p { font-size: 15px; margin: 0; }
.empty-hint { font-size: 13px !important; color: var(--color-text-placeholder, #b0b8c8) !important; }

.chat-message {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
  animation: fadeIn 0.3s ease;
}
.chat-message.user {
  flex-direction: row-reverse;
}
.msg-avatar {
  width: 30px;
  height: 30px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.chat-message.user .msg-avatar {
  background: var(--color-primary, #4361ee);
  color: #fff;
}
.chat-message.assistant .msg-avatar {
  background: rgba(67, 97, 238, 0.1);
  color: var(--color-primary, #4361ee);
}
.msg-bubble {
  max-width: 90%;
}
.chat-message.user .msg-bubble {
  background: var(--color-primary, #4361ee);
  color: #fff;
  border-radius: 12px;
  border-top-right-radius: 4px;
  padding: 10px 14px;
  font-size: 14px;
  line-height: 1.6;
  word-break: break-word;
}
.chat-message.assistant .msg-bubble {
  background: transparent;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

/* 助手文字气泡 */
.chat-message.assistant .msg-content {
  background: var(--color-bg-page, #f5f7fa);
  color: var(--color-text-primary, #1a1a2e);
  border: 1px solid var(--color-border-light, #e8ecf1);
  border-radius: 12px;
  border-top-left-radius: 4px;
  padding: 10px 14px;
  font-size: 14px;
  line-height: 1.6;
  word-break: break-word;
}
.msg-content :deep(strong) {
  color: var(--color-primary, #4361ee);
  font-weight: 600;
}

/* ── 指令结果卡片 ── */
.command-result-card {
  background: linear-gradient(135deg, #f8faff, #f0f4ff);
  border: 1px solid #d4dff0;
  border-radius: 12px;
  overflow: hidden;
  transition: all 0.25s ease;
  cursor: pointer;
}
.command-result-card:hover {
  border-color: var(--color-primary, #4361ee);
  box-shadow: 0 4px 16px rgba(67, 97, 238, 0.15);
  transform: translateY(-1px);
}
.card-header-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 14px 8px;
}
.card-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--color-primary, #4361ee);
}
.card-body {
  padding: 0 14px 12px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--color-text-primary, #1a1a2e);
  white-space: pre-line;
  word-break: break-word;
}
.data-preview {
  margin-top: 8px;
  padding: 8px 12px;
  background: rgba(255,255,255,0.7);
  border-radius: 8px;
  max-height: 180px;
  overflow-y: auto;
}
.preview-item {
  font-size: 12px;
  color: #555;
  padding: 4px 0;
  border-bottom: 1px dashed #eee;
  line-height: 1.5;
}
.preview-item:last-child {
  border-bottom: none;
}
.preview-more {
  font-size: 12px;
  color: var(--color-primary, #4361ee);
  padding: 6px 0 2px;
  font-style: italic;
}

/* 加载动画 */
.msg-loading {
  display: flex;
  gap: 4px;
  padding: 4px 0;
}
.cmd-loading {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  background: var(--color-bg-page, #f5f7fa);
  border: 1px solid var(--color-border-light, #e8ecf1);
  border-radius: 12px;
  border-top-left-radius: 4px;
  font-size: 13px;
  color: var(--color-text-secondary, #8892a4);
}
.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--color-text-placeholder, #b0b8c8);
  animation: dot-bounce 1.2s ease infinite;
}
.dot:nth-child(2) { animation-delay: 0.2s; }
.dot:nth-child(3) { animation-delay: 0.4s; }
@keyframes dot-bounce {
  0%, 80%, 100% { transform: scale(0.8); opacity: 0.5; }
  40% { transform: scale(1.1); opacity: 1; }
}

/* ── 快捷问题 ── */
.quick-questions {
  flex-shrink: 0;
  padding: 12px 0 8px;
  border-top: 1px solid var(--color-border-light, #e8ecf1);
}
.quick-label {
  font-size: 12px;
  color: var(--color-text-secondary, #8892a4);
  margin-bottom: 8px;
}
.quick-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.quick-list .el-button {
  font-size: 12px;
}

/* ── 底部输入 ── */
.drawer-footer {
  padding: 0;
}
.chat-input :deep(.el-input-group__append) {
  padding: 0;
}
.chat-input :deep(.el-input-group__append .el-button) {
  margin: 0;
  border-radius: 0;
}
.mode-hint {
  margin-top: 6px;
  text-align: center;
}

/* ── 动画 ── */
@keyframes fadeIn {
  from { opacity: 0; transform: translateY(6px); }
  to { opacity: 1; transform: translateY(0); }
}

/* ── 响应式 ── */
@media (max-width: 768px) {
  .ai-assistant-drawer .el-drawer {
    max-width: 100%;
  }
}
</style scoped>

<style>
/* 全局覆盖 - 抽屉宽度 */
.ai-assistant-drawer .el-drawer {
  max-width: 480px;
}
@media (max-width: 768px) {
  .ai-assistant-drawer .el-drawer {
    max-width: 100%;
    width: 100% !important;
  }
}
</style>
