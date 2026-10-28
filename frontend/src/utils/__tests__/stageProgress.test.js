import { describe, it, expect } from 'vitest'
import {
  mergeStageProgress,
  describeProgress,
  progressTagType,
  applyPrerequisites,
  describePrerequisite,
  describeWeakTopics,
  UNLABELED_TOPIC,
  STAGE_STATE
} from '../stageProgress.js'
import { reviewStages } from '@/data/reviewPath.js'

/**
 * 阶段进度合并测试。
 *
 * 这里最容易出错的是「分数为 null」——考卷生成了但没作答，
 * 若当成 0 分就会显示「未通过」，把人吓一跳。
 */

const stage = (key) => ({ key, tag: key, title: key, items: [], acceptance: [] })

describe('mergeStageProgress', () => {
  it('没有记录时标记为未测', () => {
    const result = mergeStageProgress([stage('stage-0')], [])
    expect(result[0].progress.state).toBe(STAGE_STATE.NOT_TESTED)
  })

  it('通过时带上分数', () => {
    const result = mergeStageProgress(
      [stage('stage-0')],
      [{ stageKey: 'stage-0', score: 82, passed: true, passScore: 60, examId: 5 }]
    )
    expect(result[0].progress.state).toBe(STAGE_STATE.PASSED)
    expect(result[0].progress.score).toBe(82)
    expect(result[0].progress.examId).toBe(5)
  })

  it('未通过时也带分数，不做美化', () => {
    const result = mergeStageProgress(
      [stage('stage-0')],
      [{ stageKey: 'stage-0', score: 41, passed: false, passScore: 60 }]
    )
    expect(result[0].progress.state).toBe(STAGE_STATE.FAILED)
    expect(result[0].progress.score).toBe(41)
  })

  it('分数为 null 视为已出卷待作答，而不是 0 分', () => {
    const result = mergeStageProgress(
      [stage('stage-0')],
      [{ stageKey: 'stage-0', score: null, passed: false, examId: 9 }]
    )
    expect(result[0].progress.state).toBe(STAGE_STATE.IN_PROGRESS)
    expect(result[0].progress.score).toBeUndefined()
  })

  it('只映射已有记录的阶段，其余保持未测', () => {
    const result = mergeStageProgress(
      [stage('stage-0'), stage('stage-1'), stage('stage-2')],
      [{ stageKey: 'stage-1', score: 90, passed: true }]
    )
    expect(result.map((r) => r.progress.state)).toEqual([
      STAGE_STATE.NOT_TESTED,
      STAGE_STATE.PASSED,
      STAGE_STATE.NOT_TESTED
    ])
  })

  it('忽略缺少 stageKey 的脏数据，不串到别的阶段', () => {
    const result = mergeStageProgress(
      [stage('stage-0')],
      [{ score: 99, passed: true }, null, undefined]
    )
    expect(result[0].progress.state).toBe(STAGE_STATE.NOT_TESTED)
  })

  it('不修改原始阶段对象', () => {
    const original = [stage('stage-0')]
    mergeStageProgress(original, [{ stageKey: 'stage-0', score: 70, passed: true }])
    expect(original[0].progress).toBeUndefined()
  })

  it('能直接吃真实的阶段数据', () => {
    const result = mergeStageProgress(reviewStages, [
      { stageKey: 'stage-2', score: 75, passed: true }
    ])
    expect(result).toHaveLength(reviewStages.length)
    const s2 = result.find((r) => r.key === 'stage-2')
    expect(s2.progress.state).toBe(STAGE_STATE.PASSED)
    expect(s2.title).toBe('高数主体 · 一元微积分')
  })
})

describe('describeProgress', () => {
  it('各状态给出可读文案', () => {
    expect(describeProgress({ state: STAGE_STATE.PASSED, score: 88 })).toBe('已通过 · 88 分')
    expect(describeProgress({ state: STAGE_STATE.FAILED, score: 42 })).toBe('未通过 · 42 分')
    expect(describeProgress({ state: STAGE_STATE.IN_PROGRESS })).toBe('已出卷 · 待作答')
    expect(describeProgress({ state: STAGE_STATE.NOT_TESTED })).toBe('未测')
    expect(describeProgress(undefined)).toBe('未测')
  })
})

describe('progressTagType', () => {
  it('通过绿、未通过红、待作答橙、未测灰', () => {
    expect(progressTagType({ state: STAGE_STATE.PASSED })).toBe('success')
    expect(progressTagType({ state: STAGE_STATE.FAILED })).toBe('danger')
    expect(progressTagType({ state: STAGE_STATE.IN_PROGRESS })).toBe('warning')
    expect(progressTagType({ state: STAGE_STATE.NOT_TESTED })).toBe('info')
  })
})

describe('applyPrerequisites', () => {
  const merged = (states) =>
    states.map((state, i) => ({
      key: `stage-${i}`,
      tag: `阶段 ${i}`,
      title: `标题${i}`,
      progress: { state }
    }))

  it('第一个阶段没有门槛', () => {
    const result = applyPrerequisites(merged([STAGE_STATE.NOT_TESTED]))
    expect(result[0].prerequisite.unmet).toBe(false)
  })

  it('上一阶段已通过时不提示', () => {
    const result = applyPrerequisites(merged([STAGE_STATE.PASSED, STAGE_STATE.NOT_TESTED]))
    expect(result[1].prerequisite.unmet).toBe(false)
  })

  it('上一阶段未通过、未测、待作答都算未满足', () => {
    for (const state of [STAGE_STATE.FAILED, STAGE_STATE.NOT_TESTED, STAGE_STATE.IN_PROGRESS]) {
      const result = applyPrerequisites(merged([state, STAGE_STATE.NOT_TESTED]))
      expect(result[1].prerequisite.unmet).toBe(true)
      expect(result[1].prerequisite.prevState).toBe(state)
    }
  })

  it('指的是紧邻的上一阶段，不是更早的阶段', () => {
    const result = applyPrerequisites(
      merged([STAGE_STATE.PASSED, STAGE_STATE.FAILED, STAGE_STATE.NOT_TESTED])
    )
    expect(result[2].prerequisite.prevTag).toBe('阶段 1')
    expect(result[2].prerequisite.unmet).toBe(true)
  })

  it('不修改入参', () => {
    const input = merged([STAGE_STATE.PASSED, STAGE_STATE.NOT_TESTED])
    applyPrerequisites(input)
    expect(input[1].prerequisite).toBeUndefined()
  })
})

describe('describePrerequisite', () => {
  it('满足时返回空串', () => {
    expect(describePrerequisite({ unmet: false })).toBe('')
    expect(describePrerequisite(null)).toBe('')
  })

  it('未满足时点名上一阶段', () => {
    expect(describePrerequisite({ unmet: true, prevTag: '阶段 1' }))
      .toBe('建议先通过「阶段 1」再测这一段')
  })
})

describe('describeWeakTopics', () => {
  it('没有数据时返回空串', () => {
    expect(describeWeakTopics([])).toBe('')
    expect(describeWeakTopics(null)).toBe('')
    expect(describeWeakTopics(undefined)).toBe('')
  })

  it('列出错得最多的几个模块', () => {
    const text = describeWeakTopics([
      { topic: '不定积分', wrongCount: 3 },
      { topic: '定积分', wrongCount: 2 },
      { topic: '导数与微分', wrongCount: 1 }
    ])
    expect(text).toBe('错在：不定积分 ×3、定积分 ×2、导数与微分 ×1')
  })

  it('超过上限时截断并说明总数', () => {
    const text = describeWeakTopics([
      { topic: '不定积分', wrongCount: 3 },
      { topic: '定积分', wrongCount: 2 },
      { topic: '导数与微分', wrongCount: 1 },
      { topic: '函数与极限', wrongCount: 1 }
    ], 2)
    expect(text).toContain('不定积分 ×3')
    expect(text).toContain('定积分 ×2')
    expect(text).not.toContain('导数与微分')
    expect(text).toContain('等 4 个知识点')
  })

  it('未标注的题单独说明，不混进任何模块', () => {
    const text = describeWeakTopics([
      { topic: '不定积分', wrongCount: 2 },
      { topic: UNLABELED_TOPIC, wrongCount: 1 }
    ])
    expect(text).toBe('错在：不定积分 ×2、另有 1 题未标注')
  })

  it('全部未标注时如实说明，而不是编一个模块名', () => {
    const text = describeWeakTopics([{ topic: UNLABELED_TOPIC, wrongCount: 4 }])
    expect(text).toBe('错题尚未标注知识点（4 题）')
  })
})

describe('mergeStageProgress 带上 weakTopics', () => {
  it('把错题分布透传到 progress', () => {
    const result = mergeStageProgress(
      [{ key: 'stage-2', tag: '阶段 2', title: 't', items: [], acceptance: [] }],
      [{ stageKey: 'stage-2', score: 45, passed: false, weakTopics: [{ topic: '不定积分', wrongCount: 2 }] }]
    )
    expect(result[0].progress.weakTopics).toEqual([{ topic: '不定积分', wrongCount: 2 }])
    expect(describeWeakTopics(result[0].progress.weakTopics)).toBe('错在：不定积分 ×2')
  })

  it('后端没给该字段时兜底为空数组', () => {
    const result = mergeStageProgress(
      [{ key: 'stage-2', tag: '阶段 2', title: 't', items: [], acceptance: [] }],
      [{ stageKey: 'stage-2', score: 88, passed: true }]
    )
    expect(result[0].progress.weakTopics).toEqual([])
  })
})
