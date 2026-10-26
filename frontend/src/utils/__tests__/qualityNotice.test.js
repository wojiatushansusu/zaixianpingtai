import { describe, it, expect, vi } from 'vitest'

// 这个模块顶层 import 了 element-plus（用于 reportQuality），
// 单测只验证纯函数 buildQualityNotice，因此把 UI 依赖整体 mock 掉，
// 既不启动组件库，也不让测试依赖真实 DOM。
vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn() },
  ElNotification: vi.fn()
}))

const { buildQualityNotice } = await import('../qualityNotice.js')

/**
 * 出卷质量提示的文案拼装测试。
 *
 * 这段逻辑决定用户能不能看懂「为什么题变少了」。
 * 拼错了不会崩，只会让人困惑或者误以为一切正常——所以逐条钉住。
 */

describe('buildQualityNotice', () => {
  it('没有质量数据时返回 null（由调用方走兜底提示）', () => {
    expect(buildQualityNotice(null)).toBeNull()
    expect(buildQualityNotice(undefined)).toBeNull()
    expect(buildQualityNotice({})).toBeNull()
    expect(buildQualityNotice({ rejected: 2 })).toBeNull() // 缺 accepted 视为无效
  })

  it('全部通过时不产生任何警示行', () => {
    const notice = buildQualityNotice({
      generated: 10,
      accepted: 10,
      rejected: 0,
      warnings: [],
      suspectQuestions: []
    })
    expect(notice.headline).toBe('AI 试卷生成成功，共 10 道题')
    expect(notice.lines).toEqual([])
  })

  it('有剔除时说明剔除了几道、原因是什么', () => {
    const notice = buildQualityNotice({
      generated: 10,
      accepted: 8,
      rejected: 2,
      warnings: [],
      suspectQuestions: []
    })
    expect(notice.headline).toContain('共 8 道题')
    expect(notice.lines).toHaveLength(1)
    expect(notice.lines[0]).toContain('剔除 2 道')
    expect(notice.lines[0]).toContain('结构不合格')
  })

  it('有存疑题时列出题号', () => {
    const notice = buildQualityNotice({
      accepted: 8,
      rejected: 0,
      warnings: [],
      suspectQuestions: [3, 7]
    })
    expect(notice.lines[0]).toContain('第 3、7 题')
    expect(notice.lines[0]).toContain('未能复现')
  })

  it('剔除与存疑同时存在时各占一行', () => {
    const notice = buildQualityNotice({
      accepted: 8,
      rejected: 2,
      warnings: [],
      suspectQuestions: [3, 7]
    })
    expect(notice.lines).toHaveLength(2)
    expect(notice.lines[0]).toContain('剔除 2 道')
    expect(notice.lines[1]).toContain('第 3、7 题')
  })

  it('「未能复现」那条不重复出现在其他提示里', () => {
    const notice = buildQualityNotice({
      accepted: 8,
      rejected: 0,
      warnings: ['第 3、7 题的答案在自检中未能复现，建议人工核验', '第 3 题缺少解析'],
      suspectQuestions: [3, 7]
    })
    // 第一行来自 suspectQuestions，第二行来自 warnings（且只剩「缺少解析」那条）
    expect(notice.lines).toHaveLength(2)
    expect(notice.lines[1]).toBe('第 3 题缺少解析')
  })

  it('其他提示超过两条时只显示两条并标注总数', () => {
    const notice = buildQualityNotice({
      accepted: 8,
      rejected: 0,
      warnings: ['警告一', '警告二', '警告三', '警告四'],
      suspectQuestions: []
    })
    expect(notice.lines).toHaveLength(1)
    expect(notice.lines[0]).toContain('警告一')
    expect(notice.lines[0]).toContain('警告二')
    expect(notice.lines[0]).not.toContain('警告三')
    expect(notice.lines[0]).toContain('等 4 条')
  })

  it('容忍字段缺失与类型异常，不抛异常', () => {
    const notice = buildQualityNotice({ accepted: 5 })
    expect(notice.lines).toEqual([])

    const weird = buildQualityNotice({
      accepted: 5,
      warnings: [null, 123, '有效提示'],
      suspectQuestions: 'not-an-array'
    })
    expect(weird.lines).toEqual(['有效提示'])
  })
})
