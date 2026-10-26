import { ElMessage, ElNotification } from 'element-plus'

/**
 * 出卷质量提示。
 *
 * 校验器会剔除结构不合格的题目、答案自检会标记存疑的题目——这些原本只写进后端日志，
 * 使用者只能看到一个"变少了"的题数。把结果摊开说，才谈得上可信。
 *
 * `buildQualityNotice` 是纯函数，不碰任何 UI，便于单测；
 * `reportQuality` 只负责把它交给 Element Plus 的提示组件。
 */

/**
 * 把后端返回的质量报告整理成可展示的结构。
 *
 * @param {Object|null} quality 形如 { generated, accepted, rejected, warnings, suspectQuestions }
 * @returns {{ headline: string, lines: string[] } | null} 无有效数据时返回 null
 */
export function buildQualityNotice(quality) {
  if (!quality || typeof quality.accepted !== 'number') {
    return null
  }

  const accepted = quality.accepted || 0
  const rejected = quality.rejected || 0
  const warnings = Array.isArray(quality.warnings) ? quality.warnings : []
  const suspects = Array.isArray(quality.suspectQuestions) ? quality.suspectQuestions : []

  const lines = []

  if (rejected > 0) {
    lines.push(`已剔除 ${rejected} 道结构不合格的题目（选项缺失或答案格式错误）`)
  }

  if (suspects.length > 0) {
    lines.push(`第 ${suspects.join('、')} 题在答案自检中未能复现，建议作答后核对解析`)
  }

  // 其余提示（缺解析、答案分布异常等）只挑前两条，避免刷屏。
  // 「未能复现」那条已在上方单独处理，这里排除掉避免重复。
  const others = warnings.filter(
    (w) => typeof w === 'string' && !w.includes('未能复现')
  )
  if (others.length > 0) {
    const shown = others.slice(0, 2).join('；')
    lines.push(others.length > 2 ? `${shown} 等 ${others.length} 条` : shown)
  }

  return {
    headline: `AI 试卷生成成功，共 ${accepted} 道题`,
    lines
  }
}

/**
 * 展示出卷结果：一条成功提示 +（必要时）一条质量警示。
 *
 * @param {Object|null} quality 后端返回的质量报告
 * @param {string} fallbackMsg 没有质量数据时的兜底提示
 */
export function reportQuality(quality, fallbackMsg = 'AI 试卷生成成功！') {
  const notice = buildQualityNotice(quality)

  if (!notice) {
    ElMessage.success(fallbackMsg)
    return
  }

  ElMessage.success(notice.headline)

  if (notice.lines.length > 0) {
    ElNotification({
      title: '出卷质量提示',
      type: 'warning',
      duration: 12000,
      message: notice.lines.join('。')
    })
  }
}
