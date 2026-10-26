import { describe, it, expect, vi } from 'vitest'
import {
  parseExamTime,
  isSubmitted,
  getStatusText,
  getStatusClass,
  getStatusTagType,
  canStartExam,
  getCannotStartReason,
  getButtonPrimaryType,
  getButtonText
} from '../examStatus.js'

/**
 * 考试状态判定测试。
 *
 * 这些规则管的是「学生能不能进考场」——错了不会抛异常，只会让人点不进去，
 * 或者不该进的人进去了。所以边界（时间窗口、提交状态、非法时间）必须逐个钉住。
 *
 * 注意：这些函数面向的是 `Exam`，其 `submitStatus` 在 Java 侧是 **String**
 * （见 Exam.java:79），JSON 里也是字符串 '0' / '1' / '2'。
 * 若把 `SelfStudyExam`（mySubmitStatus 为 Integer）传进来，判定会错，
 * 因此下面专门有一条用例锁住「数字 0/1」的行为，避免有人误用。
 */

// 被测算的函数里带诊断日志（排查线上问题用的），测试时静音，避免几十条用例刷满输出
vi.spyOn(console, 'log').mockImplementation(() => {})
vi.spyOn(console, 'warn').mockImplementation(() => {})

const pad = (n) => String(n).padStart(2, '0')
const fmt = (d) =>
  `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ` +
  `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`

// 以「现在」为锚点造时间，避开正好落在窗口边界上的毫秒抖动
const at = (offsetMin) => new Date(Date.now() + offsetMin * 60000)

/** 默认造一场「时间窗口内、未提交」的考试 */
function makeExam(overrides = {}) {
  return {
    startTime: fmt(at(-30)),
    endTime: fmt(at(30)),
    submitStatus: '0',
    ...overrides
  }
}

describe('parseExamTime', () => {
  it('解析后端常见的空格分隔格式', () => {
    const d = parseExamTime('2026-04-07 19:00:00')
    expect(d).toBeInstanceOf(Date)
    expect(d.getFullYear()).toBe(2026)
    expect(d.getMonth()).toBe(3) // 4 月
  })

  it('解析 ISO 格式', () => {
    expect(parseExamTime('2026-04-07T19:00:00')).toBeInstanceOf(Date)
  })

  it('Date 对象原样返回', () => {
    const now = new Date()
    expect(parseExamTime(now)).toBe(now)
  })

  it('空值与无效字符串返回 null', () => {
    expect(parseExamTime(null)).toBeNull()
    expect(parseExamTime(undefined)).toBeNull()
    expect(parseExamTime('')).toBeNull()
    expect(parseExamTime('not-a-date')).toBeNull()
  })
})

describe('进行中的考试', () => {
  const exam = makeExam()

  it('状态文案与样式', () => {
    expect(getStatusText(exam)).toBe('进行中')
    expect(getStatusClass(exam)).toBe('active')
    expect(getStatusTagType(exam)).toBe('success')
  })

  it('可以开始，且没有拦截原因', () => {
    expect(canStartExam(exam)).toBe(true)
    expect(getCannotStartReason(exam)).toBeNull()
  })

  it('按钮为主色、文案「开始考试」', () => {
    expect(getButtonText(exam)).toBe('开始考试')
    expect(getButtonPrimaryType(exam)).toBe('primary')
  })

  it('未提交', () => {
    expect(isSubmitted(exam)).toBe(false)
  })
})

describe('未开始的考试', () => {
  const exam = makeExam({ startTime: fmt(at(30)), endTime: fmt(at(90)) })

  it('状态为「未开始」，配色 info', () => {
    expect(getStatusText(exam)).toBe('未开始')
    expect(getStatusClass(exam)).toBe('upcoming')
    expect(getStatusTagType(exam)).toBe('info')
  })

  it('不能开始，并提示还需等待多久', () => {
    expect(canStartExam(exam)).toBe(false)
    const reason = getCannotStartReason(exam)
    expect(reason).toContain('尚未开始')
    expect(reason).toMatch(/\d+ 分钟/)
  })

  it('按钮文案「未开始」、无主色', () => {
    expect(getButtonText(exam)).toBe('未开始')
    expect(getButtonPrimaryType(exam)).toBe('')
  })
})

describe('已结束的考试', () => {
  // 注意：未提交但已过结束时间，按钮文案仍是「开始考试」，
  // 靠 disabled + danger 配色来表达不可用。这是既有行为，此处锁住防止被无意改动。
  const exam = makeExam({ startTime: fmt(at(-90)), endTime: fmt(at(-30)) })

  it('状态为「已结束」，配色 danger', () => {
    expect(getStatusText(exam)).toBe('已结束')
    expect(getStatusClass(exam)).toBe('ended')
    expect(getStatusTagType(exam)).toBe('danger')
  })

  it('不能开始，原因写明已结束', () => {
    expect(canStartExam(exam)).toBe(false)
    expect(getCannotStartReason(exam)).toContain('考试已结束')
  })

  it('按钮为 danger 但不改文案', () => {
    expect(getButtonPrimaryType(exam)).toBe('danger')
    expect(getButtonText(exam)).toBe('开始考试')
  })
})

describe('已交卷的考试', () => {
  it('手动提交：状态「已提交」', () => {
    const exam = makeExam({ submitStatus: '1' })
    expect(getStatusText(exam)).toBe('已提交')
    expect(getStatusClass(exam)).toBe('submitted')
    expect(getStatusTagType(exam)).toBe('warning')
    expect(isSubmitted(exam)).toBe(true)
  })

  it('自动交卷：状态「自动提交」', () => {
    const exam = makeExam({ submitStatus: '2', endTime: fmt(at(-10)) })
    expect(getStatusText(exam)).toBe('自动提交')
    expect(getStatusClass(exam)).toBe('auto')
    expect(getStatusTagType(exam)).toBe('warning')
  })

  it('已交卷的按钮为 info、「已交卷」', () => {
    const exam = makeExam({ submitStatus: '1' })
    expect(getButtonText(exam)).toBe('已交卷')
    expect(getButtonPrimaryType(exam)).toBe('info')
    expect(canStartExam(exam)).toBe(false)
  })

  it('自动交卷给出对应原因', () => {
    const exam = makeExam({ submitStatus: '2' })
    expect(getCannotStartReason(exam)).toContain('自动交卷')
  })
})

describe('异常数据', () => {
  it('时间为空时不可开始，并提示联系管理员', () => {
    const exam = makeExam({ startTime: null, endTime: null })
    expect(canStartExam(exam)).toBe(false)
    expect(getCannotStartReason(exam)).toContain('时间信息异常')
  })

  it('时间非法时不可开始', () => {
    const exam = makeExam({ startTime: 'bad', endTime: 'worse' })
    expect(canStartExam(exam)).toBe(false)
    expect(getCannotStartReason(exam)).toContain('时间信息异常')
  })

  it('时间传 Date 对象也能判定', () => {
    const exam = makeExam({ startTime: at(-10), endTime: at(10) })
    expect(canStartExam(exam)).toBe(true)
  })

  it('提交状态是数字 0 时仍视为未提交（防御性兼容）', () => {
    // canStartExam 同时接受 '0' 与 0；但状态文案只认字符串，
    // 因此这里只断言「能不能开始」，不断言文案——数字传入属于误用，文案会失真
    const exam = makeExam({ submitStatus: 0 })
    expect(canStartExam(exam)).toBe(true)
  })

  it('提交状态是数字 1 时视为已提交', () => {
    const exam = makeExam({ submitStatus: 1 })
    expect(canStartExam(exam)).toBe(false)
  })
})
