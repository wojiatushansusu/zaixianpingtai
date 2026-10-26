import { describe, it, expect } from 'vitest'
import { reviewStages, buildStagePrompt } from '../reviewPath.js'

/**
 * 复习路径数据与提示词拼装测试。
 *
 * 这里最要紧的一条是「不要串阶段」：阶段测验的意义就是只考这一段，
 * 一旦提示词里混进了后面阶段的内容，学生做完也没法判断自己过没过。
 */

describe('reviewStages 数据完整性', () => {
  it('共 7 个阶段，key 唯一且按顺序编号', () => {
    expect(reviewStages).toHaveLength(7)
    const keys = reviewStages.map((s) => s.key)
    expect(new Set(keys).size).toBe(7)
    expect(keys).toEqual(['stage-0', 'stage-1', 'stage-2', 'stage-3', 'stage-4', 'stage-5', 'stage-6'])
  })

  it('每个阶段都有标题、时长、模块与验收标准', () => {
    for (const stage of reviewStages) {
      expect(stage.tag).toMatch(/^阶段 \d$/)
      expect(stage.title.length).toBeGreaterThan(2)
      expect(stage.weeks).toMatch(/周/)
      expect(stage.items.length).toBeGreaterThan(0)
      expect(stage.acceptance.length).toBeGreaterThan(0)
      // 模块必须有名字，否则提示词里会出现空考点
      for (const item of stage.items) {
        expect(item.name.trim().length).toBeGreaterThan(0)
      }
      // 验收标准里不能残留前导标点（解析时的坑）
      for (const text of stage.acceptance) {
        expect(text).not.toMatch(/^[：:；;，,、\s]/)
        expect(text.trim().length).toBeGreaterThan(2)
      }
    }
  })
})

describe('buildStagePrompt', () => {
  const stage2 = reviewStages[2]

  it('包含阶段标识与题量', () => {
    const prompt = buildStagePrompt(stage2, 8)
    expect(prompt).toContain('阶段 2')
    expect(prompt).toContain('一元微积分')
    expect(prompt).toContain('共 8 道客观题')
  })

  it('列出本阶段的全部考点', () => {
    const prompt = buildStagePrompt(stage2)
    for (const item of stage2.items) {
      expect(prompt).toContain(item.name)
    }
  })

  it('不串阶段：不包含其它阶段的模块名', () => {
    const prompt = buildStagePrompt(stage2)
    const ownNames = new Set(stage2.items.map((i) => i.name))
    for (const other of reviewStages) {
      if (other.key === stage2.key) continue
      for (const item of other.items) {
        if (ownNames.has(item.name)) continue
        expect(prompt).not.toContain(item.name)
      }
    }
  })

  it('带上验收标准，让题目能检验真实能力', () => {
    const prompt = buildStagePrompt(stage2)
    expect(prompt).toContain('验收标准')
    expect(prompt).toContain(stage2.acceptance[0])
  })

  it('明确要求客观题与解析', () => {
    const prompt = buildStagePrompt(stage2)
    expect(prompt).toContain('客观题')
    expect(prompt).toContain('不要出简答题')
    expect(prompt).toContain('解析')
  })

  it('题量参数生效，默认 10 道', () => {
    expect(buildStagePrompt(stage2)).toContain('共 10 道客观题')
    expect(buildStagePrompt(stage2, 20)).toContain('共 20 道客观题')
  })
})
