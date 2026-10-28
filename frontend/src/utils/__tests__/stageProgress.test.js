import { describe, it, expect } from 'vitest'
import { mergeStageProgress, describeProgress, progressTagType, STAGE_STATE } from '../stageProgress.js'
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
