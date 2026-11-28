import { onMounted, onUnmounted, ref } from 'vue'

/**
 * 是否小屏（默认 ≤768px）。
 *
 * 用途：表格列需要按屏幕决定「渲不渲染」，而不是靠 CSS 隐藏。
 *
 * 为什么要用 v-if 而不是 display:none —— el-table 会在挂载时按列算好 colgroup 宽度，
 * `display:none` 只藏了单元格，**列宽仍占着**。实测题库表隐藏 4 列后，
 * 表格内容宽仍有 1075px，手机上照样要横向拖。
 * 用 v-if 让列根本不进表格，宽度自然回收。
 */
export function useIsMobile(query = '(max-width: 768px)') {
  const isMobile = ref(false)
  let mql = null

  const onChange = (e) => {
    isMobile.value = e.matches
  }

  onMounted(() => {
    if (typeof window === 'undefined' || !window.matchMedia) {
      return
    }
    mql = window.matchMedia(query)
    isMobile.value = mql.matches
    // Safari < 14 只有 addListener
    if (mql.addEventListener) {
      mql.addEventListener('change', onChange)
    } else if (mql.addListener) {
      mql.addListener(onChange)
    }
  })

  onUnmounted(() => {
    if (!mql) {
      return
    }
    if (mql.removeEventListener) {
      mql.removeEventListener('change', onChange)
    } else if (mql.removeListener) {
      mql.removeListener(onChange)
    }
  })

  return { isMobile }
}
