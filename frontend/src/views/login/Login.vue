<template>
  <div class="login-container">
    <div class="login-bg">
      <div class="bg-circle c1"></div>
      <div class="bg-circle c2"></div>
      <div class="bg-circle c3"></div>
    </div>

    <div class="login-wrapper">
      <div class="login-left">
        <div class="brand-section">
          <div class="logo-icon">
            <el-icon :size="48"><Reading /></el-icon>
          </div>
          <h1 class="brand-title">在线考试平台</h1>
          <p class="brand-desc">智能组卷 · AI监考 · 数据分析</p>
        </div>
        <div class="feature-list">
          <div class="feature-item">
            <el-icon :size="20" color="#6c83f2"><MagicStick /></el-icon>
            <span>AI智能组卷，一键生成高质量试卷</span>
          </div>
          <div class="feature-item">
            <el-icon :size="20" color="#6c83f2"><VideoCamera /></el-icon>
            <span>人脸识别监考，全方位防作弊</span>
          </div>
          <div class="feature-item">
            <el-icon :size="20" color="#6c83f2"><DataLine /></el-icon>
            <span>详细成绩分析，精准定位薄弱点</span>
          </div>
        </div>
      </div>

      <div class="login-right">
        <div class="login-card">
          <h2 class="card-title">欢迎回来</h2>
          <p class="card-subtitle">请登录您的账号</p>

          <el-tabs v-model="activeTab" class="login-tabs" @tab-change="handleTabChange">
            <el-tab-pane label="登录" name="login">
              <el-form
                ref="loginFormRef"
                :model="loginForm"
                :rules="loginRules"
                class="login-form"
                @keyup.enter="handleLogin"
              >
                <el-form-item prop="username">
                  <el-input
                    v-model="loginForm.username"
                    placeholder="请输入用户名"
                    size="large"
                    :prefix-icon="User"
                  />
                </el-form-item>
                <el-form-item prop="password">
                  <el-input
                    v-model="loginForm.password"
                    type="password"
                    placeholder="请输入密码"
                    size="large"
                    :prefix-icon="Lock"
                    show-password
                  />
                </el-form-item>
                <div class="form-options">
                  <el-checkbox v-model="loginForm.remember">记住密码</el-checkbox>
                  <el-button type="text" class="forget-btn" @click="showResetDialog = true">
                    忘记密码？
                  </el-button>
                </div>
                <el-button
                  type="primary"
                  size="large"
                  class="submit-btn"
                  :loading="loading"
                  @click="handleLogin"
                >
                  登 录
                </el-button>
              </el-form>
            </el-tab-pane>

            <el-tab-pane label="注册" name="register">
              <el-form
                ref="registerFormRef"
                :model="registerForm"
                :rules="registerRules"
                class="login-form"
                @keyup.enter="handleRegister"
              >
                <el-form-item prop="username">
                  <el-input
                    v-model="registerForm.username"
                    placeholder="请设置用户名"
                    size="large"
                    :prefix-icon="User"
                  />
                </el-form-item>
                <el-form-item prop="realName">
                  <el-input
                    v-model="registerForm.realName"
                    placeholder="请输入真实姓名"
                    size="large"
                    :prefix-icon="UserFilled"
                  />
                </el-form-item>
                <el-form-item prop="password">
                  <el-input
                    v-model="registerForm.password"
                    type="password"
                    placeholder="请设置密码（6-20位）"
                    size="large"
                    :prefix-icon="Lock"
                    show-password
                  />
                </el-form-item>
                <el-form-item prop="confirmPassword">
                  <el-input
                    v-model="registerForm.confirmPassword"
                    type="password"
                    placeholder="请再次输入密码"
                    size="large"
                    :prefix-icon="Lock"
                    show-password
                  />
                </el-form-item>
                <el-button
                  type="primary"
                  size="large"
                  class="submit-btn"
                  :loading="registerLoading"
                  @click="handleRegister"
                >
                  注 册
                </el-button>
              </el-form>
            </el-tab-pane>
          </el-tabs>

          <!-- 演示账号提示只在开发环境渲染，生产构建里不会出现，避免把测试凭据暴露给线上用户 -->
          <div v-if="isDev" class="login-tips">
            <el-icon><InfoFilled /></el-icon>
            <span>演示账号：admin / teacher1 / student1，密码见 README</span>
          </div>
        </div>
      </div>
    </div>

    <el-dialog v-model="showResetDialog" title="重置密码" width="420px" destroy-on-close>
      <el-steps :active="resetStep" finish-status="success" class="reset-steps">
        <el-step title="验证账号" />
        <el-step title="设置新密码" />
      </el-steps>

      <div v-if="resetStep === 0" class="reset-form">
        <el-form :model="resetForm" :rules="resetStep1Rules" ref="resetStep1Ref">
          <el-form-item prop="username" label="用户名">
            <el-input v-model="resetForm.username" placeholder="请输入用户名" />
          </el-form-item>
          <el-button type="primary" @click="sendResetCode" :loading="sendingCode">
            {{ countdown > 0 ? countdown + 's 后重试' : '获取验证码' }}
          </el-button>
          <el-form-item prop="code" label="验证码" v-if="resetCodeSent">
            <el-input v-model="resetForm.code" placeholder="请输入验证码" />
          </el-form-item>
        </el-form>
      </div>

      <div v-if="resetStep === 1" class="reset-form">
        <el-form :model="resetForm" :rules="resetStep2Rules" ref="resetStep2Ref">
          <el-form-item prop="newPassword" label="新密码">
            <el-input v-model="resetForm.newPassword" type="password" show-password placeholder="请输入新密码" />
          </el-form-item>
          <el-form-item prop="confirmPassword" label="确认密码">
            <el-input v-model="resetForm.confirmPassword" type="password" show-password placeholder="请再次输入新密码" />
          </el-form-item>
        </el-form>
      </div>

      <template #footer>
        <el-button @click="showResetDialog = false">取消</el-button>
        <el-button v-if="resetStep === 0" type="primary" @click="verifyResetCode" :loading="verifyingCode">
          下一步
        </el-button>
        <el-button v-if="resetStep === 1" type="primary" @click="confirmResetPassword" :loading="resettingPassword">
          确认重置
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, inject, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  User, Lock, UserFilled, Reading, MagicStick, VideoCamera, DataLine, InfoFilled
} from '@element-plus/icons-vue'
import { login, register, requestResetPassword, resetPassword } from '@/api/auth'

const router = useRouter()
const setToken = inject('setToken')

// 演示账号提示仅在开发环境显示（vite build 时该分支会被静态替换为 false）
const isDev = import.meta.env.DEV

const activeTab = ref('login')
const loading = ref(false)
const registerLoading = ref(false)
const loginFormRef = ref(null)
const registerFormRef = ref(null)

const loginForm = reactive({
  username: '',
  password: '',
  remember: false
})

const registerForm = reactive({
  username: '',
  realName: '',
  password: '',
  confirmPassword: ''
})

const loginRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const validateConfirmPassword = (rule, value, callback) => {
  if (value !== registerForm.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const registerRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度为3-20个字符', trigger: 'blur' }
  ],
  realName: [{ required: true, message: '请输入真实姓名', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度为6-20个字符', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' }
  ]
}

const showResetDialog = ref(false)
const resetStep = ref(0)
const resetCodeSent = ref(false)
const sendingCode = ref(false)
const verifyingCode = ref(false)
const resettingPassword = ref(false)
const countdown = ref(0)
const resetStep1Ref = ref(null)
const resetStep2Ref = ref(null)

const resetForm = reactive({
  username: '',
  code: '',
  newPassword: '',
  confirmPassword: ''
})

const resetStep1Rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  code: [{ required: true, message: '请输入验证码', trigger: 'blur' }]
}

const resetStep2Rules = {
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度为6-20个字符', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
    { validator: (rule, value, callback) => {
      if (value !== resetForm.newPassword) {
        callback(new Error('两次输入的密码不一致'))
      } else {
        callback()
      }
    }, trigger: 'blur' }
  ]
}

onMounted(() => {
  const savedUsername = localStorage.getItem('remembered_username')
  if (savedUsername) {
    loginForm.username = savedUsername
    loginForm.remember = true
  }
})

const handleTabChange = () => {
  loginFormRef.value?.clearValidate()
  registerFormRef.value?.clearValidate()
}

const handleLogin = async () => {
  if (!loginFormRef.value) return
  await loginFormRef.value.validate()
  loading.value = true
  try {
    const res = await login({
      username: loginForm.username,
      password: loginForm.password
    })
    const { token, ...userInfo } = res.data
    localStorage.setItem('token', token)
    localStorage.setItem('userInfo', JSON.stringify(userInfo))
    setToken && setToken(token)

    if (loginForm.remember) {
      localStorage.setItem('remembered_username', loginForm.username)
    } else {
      localStorage.removeItem('remembered_username')
    }

    ElMessage.success('登录成功')
    router.push('/dashboard')
  } catch (e) {
    console.error('登录失败:', e)
  } finally {
    loading.value = false
  }
}

const handleRegister = async () => {
  if (!registerFormRef.value) return
  await registerFormRef.value.validate()
  registerLoading.value = true
  try {
    await register({
      username: registerForm.username,
      realName: registerForm.realName,
      password: registerForm.password
    })
    ElMessage.success('注册成功，请登录')
    activeTab.value = 'login'
    loginForm.username = registerForm.username
  } catch (e) {
    console.error('注册失败:', e)
  } finally {
    registerLoading.value = false
  }
}

const sendResetCode = async () => {
  if (!resetForm.username) {
    ElMessage.warning('请先输入用户名')
    return
  }
  sendingCode.value = true
  try {
    await requestResetPassword({ username: resetForm.username })
    resetCodeSent.value = true
    ElMessage.success('验证码已发送（开发环境请查看后端日志）')
    startCountdown()
  } catch (e) {
    console.error('发送验证码失败:', e)
  } finally {
    sendingCode.value = false
  }
}

const startCountdown = () => {
  countdown.value = 60
  const timer = setInterval(() => {
    countdown.value--
    if (countdown.value <= 0) {
      clearInterval(timer)
    }
  }, 1000)
}

const verifyResetCode = async () => {
  if (!resetStep1Ref.value) return
  await resetStep1Ref.value.validate()
  verifyingCode.value = true
  try {
    resetStep.value = 1
    ElMessage.success('验证成功')
  } catch (e) {
    console.error('验证失败:', e)
  } finally {
    verifyingCode.value = false
  }
}

const confirmResetPassword = async () => {
  if (!resetStep2Ref.value) return
  await resetStep2Ref.value.validate()
  resettingPassword.value = true
  try {
    await resetPassword({
      username: resetForm.username,
      code: resetForm.code,
      newPassword: resetForm.newPassword
    })
    ElMessage.success('密码重置成功，请用新密码登录')
    showResetDialog.value = false
    activeTab.value = 'login'
    loginForm.username = resetForm.username
  } catch (e) {
    console.error('重置密码失败:', e)
  } finally {
    resettingPassword.value = false
  }
}
</script>

<style scoped>
.login-container {
  position: relative;
  width: 100%;
  height: 100vh;
  overflow: hidden;
  background: linear-gradient(135deg, #eef1ff 0%, #f3f4f8 50%, #fff8e6 100%);
}

.login-bg {
  position: absolute;
  inset: 0;
  overflow: hidden;
  pointer-events: none;
}

.bg-circle {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  opacity: 0.4;
}

.c1 {
  width: 500px;
  height: 500px;
  background: #4361ee;
  top: -200px;
  left: -150px;
  animation: float 8s ease-in-out infinite;
}

.c2 {
  width: 400px;
  height: 400px;
  background: #06d6a0;
  bottom: -100px;
  right: -100px;
  animation: float 10s ease-in-out infinite reverse;
}

.c3 {
  width: 300px;
  height: 300px;
  background: #ffd166;
  top: 50%;
  left: 60%;
  animation: float 12s ease-in-out infinite;
}

@keyframes float {
  0%, 100% { transform: translate(0, 0); }
  50% { transform: translate(30px, -30px); }
}

.login-wrapper {
  position: relative;
  z-index: 1;
  display: flex;
  width: 100%;
  height: 100%;
  max-width: 1200px;
  margin: 0 auto;
  padding: 40px;
}

.login-left {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding-right: 60px;
}

.brand-section {
  margin-bottom: 48px;
}

.logo-icon {
  width: 80px;
  height: 80px;
  background: linear-gradient(135deg, #4361ee 0%, #6c83f2 100%);
  border-radius: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  margin-bottom: 24px;
  box-shadow: 0 12px 40px rgba(67, 97, 238, 0.3);
}

.brand-title {
  font-size: 42px;
  font-weight: 700;
  color: #1a1d23;
  margin-bottom: 12px;
  background: linear-gradient(135deg, #4361ee 0%, #6c83f2 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.brand-desc {
  font-size: 18px;
  color: #8b8f99;
  margin: 0;
}

.feature-list {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.feature-item {
  display: flex;
  align-items: center;
  gap: 14px;
  font-size: 16px;
  color: #4a4d55;
  padding: 14px 20px;
  background: rgba(255, 255, 255, 0.6);
  backdrop-filter: blur(10px);
  border-radius: 12px;
  border: 1px solid rgba(255, 255, 255, 0.8);
  transition: all 0.3s ease;
}

.feature-item:hover {
  transform: translateX(8px);
  box-shadow: 0 8px 24px rgba(67, 97, 238, 0.1);
}

.login-right {
  flex: 0 0 480px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.login-card {
  width: 100%;
  padding: 48px 40px;
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(20px);
  border-radius: 24px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.9);
}

.card-title {
  font-size: 28px;
  font-weight: 600;
  color: #1a1d23;
  margin: 0 0 8px 0;
}

.card-subtitle {
  font-size: 14px;
  color: #8b8f99;
  margin: 0 0 32px 0;
}

.login-tabs {
  margin-bottom: 24px;
}

.login-tabs :deep(.el-tabs__nav) {
  width: 100%;
}

.login-tabs :deep(.el-tabs__item) {
  flex: 1;
  text-align: center;
  font-size: 16px;
}

.submit-btn {
  width: 100%;
  margin-top: 8px;
  height: 48px;
  font-size: 16px;
  letter-spacing: 4px;
  background: linear-gradient(135deg, #4361ee 0%, #6c83f2 100%);
  border: none;
  box-shadow: 0 8px 24px rgba(67, 97, 238, 0.3);
  transition: all 0.3s ease;
}

.submit-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 12px 32px rgba(67, 97, 238, 0.4);
}

.form-options {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.forget-btn {
  padding: 0;
  font-size: 13px;
}

.login-tips {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  margin-top: 24px;
  padding: 12px 16px;
  background: #eef1ff;
  border-radius: 10px;
  font-size: 13px;
  color: #4361ee;
}

.reset-steps {
  margin-bottom: 32px;
}

.reset-form {
  padding: 20px 0;
}

@media (max-width: 960px) {
  .login-wrapper {
    flex-direction: column;
    padding: 20px;
  }
  .login-left {
    display: none;
  }
  .login-right {
    flex: 1;
  }
  .login-card {
    padding: 32px 24px;
  }
}
</style>
