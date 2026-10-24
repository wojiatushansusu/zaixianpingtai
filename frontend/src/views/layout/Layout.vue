<template>
  <div class="layout-container">
    <aside class="sidebar" :class="{ collapsed }">
      <div class="sidebar-header">
        <div class="logo">
          <div class="logo-icon">
            <el-icon :size="24"><Reading /></el-icon>
          </div>
          <span v-show="!collapsed" class="logo-text">在线考试平台</span>
        </div>
      </div>

      <el-menu
        :default-active="activeMenu"
        :collapse="collapsed"
        :collapse-transition="false"
        router
        class="sidebar-menu"
        background-color="transparent"
        text-color="rgba(255,255,255,0.75)"
        active-text-color="#ffffff"
      >
        <el-menu-item index="/dashboard">
          <el-icon><HomeFilled /></el-icon>
          <template #title>首页</template>
        </el-menu-item>

        <!--
          AI 出卷独立成一级入口：这是平台最有辨识度的能力（自然语言出题 → 生成考试码 → 同学来考），
          原先折叠在「学生中心」三级菜单里，等于把最强的故事点藏起来了。
        -->
        <el-menu-item v-if="isStudent || isAdmin" index="/student/self-study" class="highlight-entry">
          <el-icon><MagicStick /></el-icon>
          <template #title>AI 出卷</template>
        </el-menu-item>

        <template v-if="isStudent || isAdmin">
          <el-sub-menu index="student-menu">
            <template #title>
              <el-icon><Tickets /></el-icon>
              <span>学生中心</span>
            </template>
            <el-menu-item index="/student/exams">我的考试</el-menu-item>
            <el-menu-item index="/student/grades">我的成绩</el-menu-item>
            <el-menu-item index="/student/wrong-questions">错题本</el-menu-item>
          </el-sub-menu>
        </template>

        <template v-if="isTeacher || isAdmin">
          <el-sub-menu index="teacher-menu">
            <template #title>
              <el-icon><Document /></el-icon>
              <span>教师中心</span>
            </template>
            <el-menu-item index="/teacher/questions">题库管理</el-menu-item>
            <el-menu-item index="/teacher/papers">试卷管理</el-menu-item>
            <el-menu-item index="/teacher/exams">考试管理</el-menu-item>
          </el-sub-menu>
        </template>

        <template v-if="isAdmin">
          <el-menu-item index="/admin/users">
            <el-icon><UserFilled /></el-icon>
            <template #title>用户管理</template>
          </el-menu-item>
        </template>
      </el-menu>
    </aside>

    <!-- 移动端：侧栏展开时盖一层遮罩，点击空白处收起 -->
    <div
      v-if="isMobile && !collapsed"
      class="sidebar-mask"
      @click="collapsed = true"
    ></div>

    <div class="main-container">
      <header class="header">
        <div class="header-left">
          <el-button text @click="toggleCollapse" class="collapse-btn">
            <el-icon :size="20">
              <component :is="collapsed ? 'Expand' : 'Fold'" />
            </el-icon>
          </el-button>
          <div class="breadcrumb-wrapper">
            <el-breadcrumb separator="/">
              <el-breadcrumb-item v-for="(item, idx) in breadcrumbs" :key="idx">
                {{ item }}
              </el-breadcrumb-item>
            </el-breadcrumb>
          </div>
        </div>

        <div class="header-right">
          <el-tooltip content="全屏" placement="bottom">
            <el-button text class="header-icon-btn" @click="toggleFullscreen">
              <el-icon :size="18"><FullScreen /></el-icon>
            </el-button>
          </el-tooltip>

          <el-dropdown @command="handleUserCommand" trigger="click">
            <div class="user-info">
              <el-avatar :size="32" :src="userAvatar" class="user-avatar">
                {{ userInfo.realName?.charAt(0) || userInfo.username?.charAt(0) || 'U' }}
              </el-avatar>
              <span class="user-name">{{ userInfo.realName || userInfo.username }}</span>
              <el-tag v-if="userInfo.roleName" :type="roleTagType" size="small" class="role-tag">
                {{ userInfo.roleName }}
              </el-tag>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">
                  <el-icon><User /></el-icon>个人中心
                </el-dropdown-item>
                <el-dropdown-item command="logout" divided>
                  <el-icon><SwitchButton /></el-icon>退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <main class="main-content">
        <router-view v-slot="{ Component }">
          <transition name="fade-transform" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </main>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Reading, HomeFilled, Tickets, Document, UserFilled, User,
  SwitchButton, Fold, Expand, FullScreen
} from '@element-plus/icons-vue'
import { logout } from '@/api/auth'

const route = useRoute()
const router = useRouter()

const collapsed = ref(false)
const userInfo = ref({})

// ≤768px 时侧栏是浮层（position: fixed），若不默认收起会一进页面就盖住正文。
const MOBILE_BREAKPOINT = 768
const isMobile = ref(false)

const syncViewport = () => {
  const mobile = window.innerWidth <= MOBILE_BREAKPOINT
  // 只在真正跨越断点时重置状态，否则用户手动展开后，任何一次 resize 都会把它强制收起
  if (mobile === isMobile.value) return
  isMobile.value = mobile
  collapsed.value = mobile
}

onMounted(() => {
  isMobile.value = window.innerWidth <= MOBILE_BREAKPOINT
  collapsed.value = isMobile.value
  window.addEventListener('resize', syncViewport)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', syncViewport)
})

const isStudent = computed(() => userInfo.value.roleKey === 'student')
const isTeacher = computed(() => userInfo.value.roleKey === 'teacher')
const isAdmin = computed(() => userInfo.value.roleKey === 'admin')

const roleTagType = computed(() => {
  const map = { admin: 'danger', teacher: 'warning', student: 'success' }
  return map[userInfo.value.roleKey] || 'info'
})

const activeMenu = computed(() => route.path)

const userAvatar = computed(() => {
  return userInfo.value.avatar ? `/api${userInfo.value.avatar}` : ''
})

const breadcrumbs = computed(() => {
  const matched = route.matched.filter(r => r.meta?.title && !r.meta?.hidden)
  return matched.map(r => r.meta.title)
})

const loadUserInfo = () => {
  try {
    const info = JSON.parse(localStorage.getItem('userInfo') || '{}')
    userInfo.value = info
  } catch {
    userInfo.value = {}
  }
}

loadUserInfo()

watch(() => route.fullPath, () => {
  loadUserInfo()
  // 移动端点完菜单后自动收起浮层，否则目标页会被侧栏挡住
  if (isMobile.value) collapsed.value = true
})

const toggleCollapse = () => {
  collapsed.value = !collapsed.value
}

const toggleFullscreen = () => {
  if (!document.fullscreenElement) {
    document.documentElement.requestFullscreen()
  } else {
    document.exitFullscreen()
  }
}

const handleUserCommand = async (command) => {
  if (command === 'profile') {
    router.push('/profile')
  } else if (command === 'logout') {
    ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }).then(async () => {
      try {
        await logout()
      } catch (e) {
        console.warn('登出接口调用失败:', e)
      }
      localStorage.removeItem('token')
      localStorage.removeItem('userInfo')
      ElMessage.success('已退出登录')
      router.push('/login')
    }).catch(() => {})
  }
}
</script>

<style scoped>
.layout-container {
  display: flex;
  width: 100%;
  height: 100vh;
  overflow: hidden;
}

.sidebar {
  width: var(--sidebar-width);
  background: var(--sidebar-bg);
  display: flex;
  flex-direction: column;
  transition: width var(--transition-normal);
  flex-shrink: 0;
}

.sidebar.collapsed {
  width: var(--sidebar-collapsed-width);
}

.sidebar-header {
  height: var(--header-height);
  display: flex;
  align-items: center;
  justify-content: center;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  flex-shrink: 0;
}

.logo {
  display: flex;
  align-items: center;
  gap: 12px;
  color: white;
}

.logo-icon {
  width: 40px;
  height: 40px;
  background: linear-gradient(135deg, #4361ee 0%, #6c83f2 100%);
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.logo-text {
  font-size: 18px;
  font-weight: 600;
  white-space: nowrap;
}

.sidebar-menu {
  flex: 1;
  border-right: none;
  padding: 12px 0;
  overflow-y: auto;
}

.sidebar-menu:not(.el-menu--collapse) {
  width: 100%;
}

.sidebar-menu :deep(.el-menu-item),
.sidebar-menu :deep(.el-sub-menu__title) {
  height: 48px;
  line-height: 48px;
  margin: 4px 12px;
  border-radius: 10px;
  width: auto;
}

.sidebar-menu :deep(.el-menu-item:hover),
.sidebar-menu :deep(.el-sub-menu__title:hover) {
  background-color: rgba(255, 255, 255, 0.08);
}

.sidebar-menu :deep(.el-menu-item.is-active) {
  background: linear-gradient(135deg, #4361ee 0%, #6c83f2 100%);
  color: white;
}

/* AI 出卷：一级入口，非激活态也给一层极淡的主色底，让它在深色侧栏里被一眼看到 */
.sidebar-menu :deep(.el-menu-item.highlight-entry) {
  background-color: rgba(67, 97, 238, 0.14);
}

.sidebar-menu :deep(.el-menu-item.highlight-entry:hover) {
  background-color: rgba(67, 97, 238, 0.26);
}

/* 激活时用统一的渐变，不再叠加淡色底 */
.sidebar-menu :deep(.el-menu-item.highlight-entry.is-active) {
  background: linear-gradient(135deg, #4361ee 0%, #6c83f2 100%);
}

.sidebar-menu :deep(.el-sub-menu .el-menu-item) {
  min-width: auto;
  padding-left: 48px !important;
  font-size: 14px;
}

.sidebar-menu::-webkit-scrollbar {
  width: 4px;
}
.sidebar-menu::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.2);
  border-radius: 2px;
}

.main-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-width: 0;
}

.header {
  height: var(--header-height);
  background: var(--color-bg-card);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  border-bottom: 1px solid var(--color-border-light);
  box-shadow: var(--shadow-sm);
  flex-shrink: 0;
  z-index: 10;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.collapse-btn {
  width: 40px;
  height: 40px;
  border-radius: 10px;
}

.collapse-btn:hover {
  background: var(--color-primary-bg);
  color: var(--color-primary);
}

.breadcrumb-wrapper {
  padding-left: 8px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-icon-btn {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  color: var(--color-text-regular);
}

.header-icon-btn:hover {
  background: var(--color-primary-bg);
  color: var(--color-primary);
}

.user-info {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 12px 6px 6px;
  border-radius: 12px;
  cursor: pointer;
  transition: background var(--transition-fast);
}

.user-info:hover {
  background: var(--color-bg-page);
}

.user-avatar {
  background: linear-gradient(135deg, #4361ee 0%, #6c83f2 100%);
  color: white;
  font-weight: 500;
}

.user-name {
  font-size: 14px;
  color: var(--color-text-primary);
  font-weight: 500;
  max-width: 100px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.role-tag {
  margin-left: 4px;
}

.main-content {
  flex: 1;
  overflow: auto;
  padding: 24px;
  background: var(--color-bg-page);
}

.fade-transform-enter-active,
.fade-transform-leave-active {
  transition: all var(--transition-normal);
}

.fade-transform-enter-from {
  opacity: 0;
  transform: translateY(10px);
}

.fade-transform-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}

/* ── 移动端侧栏遮罩 ── */
.sidebar-mask {
  position: fixed;
  inset: 0;
  z-index: 99;
  background: rgba(15, 18, 26, 0.45);
  animation: maskFade 0.2s ease;
}

@keyframes maskFade {
  from { opacity: 0; }
  to { opacity: 1; }
}

@media (max-width: 768px) {
  .sidebar {
    position: fixed;
    left: 0;
    top: 0;
    bottom: 0;
    z-index: 100;
    transform: translateX(0);
    transition: transform var(--transition-normal);
    box-shadow: var(--shadow-lg);
  }
  .sidebar.collapsed {
    transform: translateX(-100%);
    width: var(--sidebar-width);
  }
  .user-name, .role-tag {
    display: none;
  }
}
</style>
