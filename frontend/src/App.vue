<template>
  <router-view />
  <!-- 全局AI助手（仅登录后可用） -->
  <AiAssistant v-if="showAssistant" ref="aiAssistant" />
</template>

<script setup>
import { ref, provide, computed } from 'vue'
import { useRoute } from 'vue-router'
import AiAssistant from '@/components/common/AiAssistant.vue'

const route = useRoute()
const aiAssistant = ref(null)

// 响应式的 token 状态（通过 provide 注入，Login.vue 可写入）
const tokenState = ref(localStorage.getItem('token') || '')

// 当前用户角色
const currentUserRole = computed(() => {
  try {
    const userInfo = JSON.parse(localStorage.getItem('userInfo') || '{}')
    return userInfo.roleKey || 'student'
  } catch {
    return 'student'
  }
})

// 判断是否显示AI助手（考试页面隐藏，防止作弊）
const showAssistant = computed(() => !!tokenState.value && !route.meta?.noAuth && !route.meta?.hideAiAssistant)

// 提供全局方法
provide('openAiAssistant', (questionContext) => {
  if (aiAssistant.value) {
    aiAssistant.value.openDrawer(questionContext)
  }
})

// 注入用户角色给AI助手
provide('userRole', currentUserRole.value)

// 提供登录状态更新方法（Login.vue 调用以触发响应式更新）
provide('setToken', (token) => {
  tokenState.value = token
})
</script>

<style>
/* Element Plus 全局微调已移至 global.css */
</style>
