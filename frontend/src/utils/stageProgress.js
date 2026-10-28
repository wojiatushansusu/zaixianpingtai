/**
 * 把后端返回的阶段测验结果合并到复习路径上。
 *
 * 纯函数，不碰 UI 也不发请求——「哪个阶段通过了」这种判断最怕悄悄算错，
 * 抽出来就能单独验证。
 */

/** 阶段状态 */
export const STAGE_STATE = {
  NOT_TESTED: 'not-tested',
  IN_PROGRESS: 'in-progress',
  PASSED: 'passed',
  FAILED: 'failed'
}

/**
 * @param {Array<Object>} stages reviewStages
 * @param {Array<Object>} rows   后端 /stage-progress 返回的记录
 * @returns {Array<Object>} 每个阶段附加 progress 字段
 */
export function mergeStageProgress(stages, rows) {
  const byKey = new Map()
  for (const row of rows || []) {
    if (row && row.stageKey) {
      byKey.set(row.stageKey, row)
    }
  }

  return (stages || []).map((stage) => {
    const row = byKey.get(stage.key)

    if (!row) {
      return { ...stage, progress: { state: STAGE_STATE.NOT_TESTED } }
    }

    // 考卷已生成但还没作答：分数为空，不能当成 0 分
    if (row.score === null || row.score === undefined) {
      return {
        ...stage,
        progress: {
          state: STAGE_STATE.IN_PROGRESS,
          examId: row.examId,
          selfStudyExamId: row.selfStudyExamId
        }
      }
    }

    return {
      ...stage,
      progress: {
        state: row.passed ? STAGE_STATE.PASSED : STAGE_STATE.FAILED,
        score: row.score,
        passed: !!row.passed,
        passScore: row.passScore,
        submitTime: row.submitTime,
        examId: row.examId,
        selfStudyExamId: row.selfStudyExamId,
        weakTopics: Array.isArray(row.weakTopics) ? row.weakTopics : []
      }
    }
  })
}

/**
 * 阶段状态的中文描述（用于页面上的状态标签）。
 */
export function describeProgress(progress) {
  switch (progress?.state) {
    case STAGE_STATE.PASSED:
      return `已通过 · ${progress.score} 分`
    case STAGE_STATE.FAILED:
      return `未通过 · ${progress.score} 分`
    case STAGE_STATE.IN_PROGRESS:
      return '已出卷 · 待作答'
    default:
      return '未测'
  }
}

/**
 * 阶段状态对应的标签配色（Element Plus tag type）。
 */
export function progressTagType(progress) {
  switch (progress?.state) {
    case STAGE_STATE.PASSED:
      return 'success'
    case STAGE_STATE.FAILED:
      return 'danger'
    case STAGE_STATE.IN_PROGRESS:
      return 'warning'
    default:
      return 'info'
  }
}

/**
 * 给每个阶段标注「上一阶段是否已通过」。
 *
 * 这是**软门槛**：不禁止你去测后面的阶段，只提示一句。
 * 理由很实际——阶段划分是建议，不是考纲。真有人基础好，从阶段 3 起步也说不定；
 * 强行锁死只会让人去找别的工具。但「你在原地跳段」这件事必须让他自己知道。
 *
 * @param {Array<Object>} stages 已经过 mergeStageProgress 的阶段
 * @returns {Array<Object>} 每项附加 prerequisite 字段
 */
export function applyPrerequisites(stages) {
  return (stages || []).map((stage, index) => {
    if (index === 0) {
      return { ...stage, prerequisite: { unmet: false } }
    }

    const prev = stages[index - 1]
    const prevState = prev.progress?.state || STAGE_STATE.NOT_TESTED

    return {
      ...stage,
      prerequisite: {
        unmet: prevState !== STAGE_STATE.PASSED,
        prevKey: prev.key,
        prevTag: prev.tag,
        prevTitle: prev.title,
        prevState
      }
    }
  })
}

/**
 * 门槛提示文案。未满足时给出可读的一句话，满足则返回空串。
 */
export function describePrerequisite(prerequisite) {
  if (!prerequisite || !prerequisite.unmet) {
    return ''
  }
  return `建议先通过「${prerequisite.prevTag}」再测这一段`
}

/** 后端对「题目没有知识点」的统一叫法，前后端各有一处，改的时候要一起改 */
export const UNLABELED_TOPIC = '未标注知识点'

/**
 * 把错题的知识点分布整理成一句人话。
 *
 * 分数只说明"这一阶段没过"，这行才说明"该回看哪一段"。
 * 未标注的题目单独提一句，不混进任何模块——瞎归类比不归类更害人。
 *
 * @param {Array<{topic: string, wrongCount: number}>} weakTopics
 * @param {number} limit 最多列几个模块
 * @returns {string} 没有可用信息时返回空串
 */
export function describeWeakTopics(weakTopics, limit = 3) {
  if (!Array.isArray(weakTopics) || weakTopics.length === 0) {
    return ''
  }

  const labeled = weakTopics.filter((item) => item && item.topic && item.topic !== UNLABELED_TOPIC)
  const unlabeled = weakTopics.find((item) => item && item.topic === UNLABELED_TOPIC)

  if (labeled.length === 0) {
    return unlabeled ? `错题尚未标注知识点（${unlabeled.wrongCount} 题）` : ''
  }

  const parts = labeled
    .slice(0, limit)
    .map((item) => `${item.topic} ×${item.wrongCount}`)

  if (labeled.length > limit) {
    parts.push(`等 ${labeled.length} 个知识点`)
  }
  if (unlabeled) {
    parts.push(`另有 ${unlabeled.wrongCount} 题未标注`)
  }

  return `错在：${parts.join('、')}`
}

/**
 * 取出可以拿去出题的薄弱考点名（去掉「未标注知识点」这类非考点项）。
 *
 * @param {Array<{topic: string, wrongCount: number}>} weakTopics
 * @param {number} limit 最多取几个，避免提示词里考点过多导致焦点分散
 */
export function weakTopicNames(weakTopics, limit = 4) {
  if (!Array.isArray(weakTopics)) {
    return []
  }
  return weakTopics
    .filter((item) => item && item.topic && item.topic !== UNLABELED_TOPIC)
    .slice(0, limit)
    .map((item) => item.topic)
}
