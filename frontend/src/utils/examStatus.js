/**
 * 考试状态判定与按钮文案（纯函数）。
 *
 * 这些规则原先散在 StudentExams.vue 里，和摄像头、人脸核验的状态混在同一个文件，
 * 无法单独验证。抽出来后：
 * - 判定逻辑只有一处，改「什么时候能开始考试」不必翻 1800 行
 * - 都只读入参、不碰组件状态，可以直接用单测覆盖（后续引入前端测试时）
 *
 * 注意：`getStatusText` 直接用 `new Date(...)` 解析时间，而 `canStartExam` 走
 * `parseExamTime`（会做无效日期校验）。这个不一致是历史行为，本次只做搬运、
 * 未做统一——两处的容错能力不同，贸然合并会改变边界情况下的判定结果。
 */

/**
 * 安全解析考试时间，兼容后端返回的字符串格式与前端 Date 对象。
 *
 * @param {string|Date|null|undefined} timeValue
 * @returns {Date|null} 解析失败返回 null
 */
export function parseExamTime(timeValue) {
  if (!timeValue) return null
  if (timeValue instanceof Date) return timeValue
  // 后端 Jackson 序列化格式: "2026-04-07 19:00:00" 或 "2026-04-07T19:00:00"
  const parsed = new Date(timeValue)
  if (isNaN(parsed.getTime())) return null
  return parsed
}

/** 是否已交卷（含自动交卷） */
export function isSubmitted(exam) {
  return exam.submitStatus !== '0'
}

/** 状态文案：已提交 / 自动提交 / 未开始 / 已结束 / 进行中 */
export function getStatusText(exam) {
  const now = new Date()
  const start = new Date(exam.startTime)
  const end = new Date(exam.endTime)
  if (exam.submitStatus !== '0') return exam.submitStatus === '1' ? '已提交' : '自动提交'
  if (now < start) return '未开始'
  if (now > end) return '已结束'
  return '进行中'
}

/** 卡片顶部色条对应的 class 后缀 */
export function getStatusClass(exam) {
  const text = getStatusText(exam)
  if (text === '进行中') return 'active'
  if (text === '未开始') return 'upcoming'
  if (text === '已提交') return 'submitted'
  if (text === '自动提交') return 'auto'
  return 'ended'
}

/** el-tag 的 type */
export function getStatusTagType(exam) {
  const text = getStatusText(exam)
  if (text === '进行中') return 'success'
  if (text === '未开始') return 'info'
  if (text === '已提交' || text === '自动提交') return 'warning'
  return 'danger'
}

/**
 * 当前是否可以进入考试：既要在考试时间窗口内，又要尚未交卷。
 */
export function canStartExam(exam) {
  const now = new Date()
  const startTime = parseExamTime(exam.startTime)
  const endTime = parseExamTime(exam.endTime)

  if (!startTime || !endTime) {
    console.warn('[canStartExam] 时间解析失败:', { startTime: exam.startTime, endTime: exam.endTime })
    return false
  }

  const timeOk = now >= startTime && now <= endTime
  const statusOk = exam.submitStatus === '0' || exam.submitStatus === 0

  if (timeOk && !statusOk) {
    console.log('[canStartExam] 时间满足但已提交:', { submitStatus: exam.submitStatus })
  }
  if (statusOk && !timeOk) {
    console.log('[canStartExam] 状态满足但不在考试时间内:', {
      now: now.toISOString(),
      start: startTime.toISOString(),
      end: endTime.toISOString()
    })
  }
  if (!timeOk && !statusOk) {
    console.log('[canStartExam] 不满足开始条件:', {
      submitStatus: exam.submitStatus,
      now: now.toISOString(),
      start: startTime.toISOString(),
      end: endTime.toISOString()
    })
  }

  return timeOk && statusOk
}

/**
 * 无法开始考试的具体原因，用于按钮禁用时的提示。
 * @returns {string|null} 可开始时返回 null
 */
export function getCannotStartReason(exam) {
  const now = new Date()
  const startTime = parseExamTime(exam.startTime)
  const endTime = parseExamTime(exam.endTime)

  if (exam.submitStatus !== '0' && exam.submitStatus !== 0) {
    return exam.submitStatus === '2' ? '该考试已自动交卷，无法重新开始' : '您已提交该考试，无法重复作答'
  }

  if (startTime && now < startTime) {
    const diffMinutes = Math.ceil((startTime - now) / 60000)
    if (diffMinutes <= 60) {
      return `考试尚未开始，请等待 ${diffMinutes} 分钟后再试`
    }
    return `考试开始时间：${exam.startTime}，请耐心等待`
  }

  if (endTime && now > endTime) {
    return `考试已结束（结束时间：${exam.endTime}），无法进入`
  }

  if (!startTime || !endTime) {
    return '考试时间信息异常，请联系管理员或刷新页面重试'
  }

  return null
}

/** 主按钮的 type，用颜色区分「可开始 / 已交卷 / 未开始 / 已结束」 */
export function getButtonPrimaryType(exam) {
  if (canStartExam(exam)) return 'primary'

  const now = new Date()
  const startTime = parseExamTime(exam.startTime)
  const endTime = parseExamTime(exam.endTime)

  if (exam.submitStatus !== '0' && exam.submitStatus !== 0) return 'info'
  if (startTime && now < startTime) return ''
  if (endTime && now > endTime) return 'danger'
  return ''
}

/** 主按钮文案 */
export function getButtonText(exam) {
  if (exam.submitStatus !== '0') return '已交卷'
  const now = new Date()
  const start = new Date(exam.startTime)
  if (now < start) return '未开始'
  return '开始考试'
}
