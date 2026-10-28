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
        selfStudyExamId: row.selfStudyExamId
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
