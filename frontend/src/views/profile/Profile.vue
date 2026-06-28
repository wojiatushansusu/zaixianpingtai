<template>
  <div class="profile-container">
    <el-row :gutter="24">
      <el-col :xs="24" :sm="24" :md="8">
        <el-card class="avatar-card">
          <div class="avatar-section">
            <el-avatar :size="100" :src="avatarUrl" class="main-avatar">
              {{ userInfo.realName?.charAt(0) || userInfo.username?.charAt(0) || 'U' }}
            </el-avatar>
            <el-upload
              class="avatar-uploader"
              :show-file-list="false"
              :before-upload="beforeAvatarUpload"
              :http-request="uploadAvatarRequest"
              accept="image/*"
            >
              <el-button type="primary" size="small" class="upload-btn">
                <el-icon><Upload /></el-icon>更换头像
              </el-button>
            </el-upload>
            <h3 class="user-name">{{ userInfo.realName || userInfo.username }}</h3>
            <el-tag :type="roleTagType" size="default" class="role-tag">
              {{ userInfo.roleName || '未知角色' }}
            </el-tag>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :sm="24" :md="16">
        <el-tabs v-model="activeTab" class="profile-tabs">
          <el-tab-pane label="基本信息" name="basic">
            <el-form
              ref="basicFormRef"
              :model="basicForm"
              :rules="basicRules"
              label-width="100px"
              class="profile-form"
            >
              <el-form-item label="用户名" prop="username">
                <el-input v-model="basicForm.username" disabled />
              </el-form-item>
              <el-form-item label="真实姓名" prop="realName">
                <el-input v-model="basicForm.realName" placeholder="请输入真实姓名" />
              </el-form-item>
              <el-form-item label="邮箱" prop="email">
                <el-input v-model="basicForm.email" placeholder="请输入邮箱" />
              </el-form-item>
              <el-form-item label="手机号" prop="phone">
                <el-input v-model="basicForm.phone" placeholder="请输入手机号" />
              </el-form-item>
              <el-form-item label="性别" prop="gender">
                <el-radio-group v-model="basicForm.gender">
                  <el-radio value="male">男</el-radio>
                  <el-radio value="female">女</el-radio>
                  <el-radio value="unknown">保密</el-radio>
                </el-radio-group>
              </el-form-item>
              <el-form-item>
                <el-button type="primary" :loading="saving" @click="saveBasicInfo">
                  保存修改
                </el-button>
                <el-button @click="resetForm">重置</el-button>
              </el-form-item>
            </el-form>
          </el-tab-pane>

          <el-tab-pane label="修改密码" name="password">
            <el-form
              ref="passwordFormRef"
              :model="passwordForm"
              :rules="passwordRules"
              label-width="120px"
              class="profile-form"
            >
              <el-form-item label="原密码" prop="oldPassword">
                <el-input v-model="passwordForm.oldPassword" type="password" show-password placeholder="请输入原密码" />
              </el-form-item>
              <el-form-item label="新密码" prop="newPassword">
                <el-input v-model="passwordForm.newPassword" type="password" show-password placeholder="请输入新密码" />
              </el-form-item>
              <el-form-item label="确认新密码" prop="confirmPassword">
                <el-input v-model="passwordForm.confirmPassword" type="password" show-password placeholder="请再次输入新密码" />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" :loading="changingPassword" @click="changePassword">
                  修改密码
                </el-button>
              </el-form-item>
            </el-form>
          </el-tab-pane>
        </el-tabs>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Upload } from '@element-plus/icons-vue'
import { getProfile, updateProfile, changePassword as changePasswordApi, uploadAvatar } from '@/api/user'

const userInfo = ref({})
const activeTab = ref('basic')
const saving = ref(false)
const changingPassword = ref(false)
const basicFormRef = ref(null)
const passwordFormRef = ref(null)

const basicForm = reactive({
  username: '',
  realName: '',
  email: '',
  phone: '',
  gender: 'unknown'
})

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const avatarUrl = computed(() => {
  return userInfo.value.avatar ? `/api${userInfo.value.avatar}` : ''
})

const roleTagType = computed(() => {
  const map = { admin: 'danger', teacher: 'warning', student: 'success' }
  return map[userInfo.value.roleKey] || 'info'
})

const validateConfirmPassword = (rule, value, callback) => {
  if (value !== passwordForm.newPassword) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const basicRules = {
  realName: [
    { required: true, message: '请输入真实姓名', trigger: 'blur' }
  ],
  email: [
    { type: 'email', message: '请输入正确的邮箱地址', trigger: 'blur' }
  ],
  phone: [
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号', trigger: 'blur' }
  ]
}

const passwordRules = {
  oldPassword: [
    { required: true, message: '请输入原密码', trigger: 'blur' }
  ],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度在 6 到 32 个字符', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' }
  ]
}

const loadProfile = async () => {
  try {
    const res = await getProfile()
    if (res.code === 200 && res.data) {
      userInfo.value = res.data
      basicForm.username = res.data.username || ''
      basicForm.realName = res.data.realName || ''
      basicForm.email = res.data.email || ''
      basicForm.phone = res.data.phone || ''
      basicForm.gender = res.data.gender || 'unknown'
      localStorage.setItem('userInfo', JSON.stringify(res.data))
    }
  } catch (e) {
    console.error('加载用户信息失败:', e)
    const localInfo = JSON.parse(localStorage.getItem('userInfo') || '{}')
    userInfo.value = localInfo
    basicForm.username = localInfo.username || ''
    basicForm.realName = localInfo.realName || ''
    basicForm.email = localInfo.email || ''
    basicForm.phone = localInfo.phone || ''
    basicForm.gender = localInfo.gender || 'unknown'
  }
}

const resetForm = () => {
  if (basicFormRef.value) {
    basicFormRef.value.clearValidate()
  }
  loadProfile()
}

const saveBasicInfo = async () => {
  if (!basicFormRef.value) return
  await basicFormRef.value.validate(async (valid) => {
    if (valid) {
      saving.value = true
      try {
        const res = await updateProfile({
          realName: basicForm.realName,
          email: basicForm.email,
          phone: basicForm.phone,
          gender: basicForm.gender
        })
        if (res.code === 200) {
          ElMessage.success('保存成功')
          loadProfile()
        } else {
          ElMessage.error(res.message || '保存失败')
        }
      } catch (e) {
        console.error('保存失败:', e)
        ElMessage.error('保存失败，请稍后重试')
      } finally {
        saving.value = false
      }
    }
  })
}

const changePassword = async () => {
  if (!passwordFormRef.value) return
  await passwordFormRef.value.validate(async (valid) => {
    if (valid) {
      changingPassword.value = true
      try {
        const res = await changePasswordApi({
          oldPassword: passwordForm.oldPassword,
          newPassword: passwordForm.newPassword
        })
        if (res.code === 200) {
          ElMessage.success('密码修改成功')
          passwordForm.oldPassword = ''
          passwordForm.newPassword = ''
          passwordForm.confirmPassword = ''
        } else {
          ElMessage.error(res.message || '密码修改失败')
        }
      } catch (e) {
        console.error('密码修改失败:', e)
        ElMessage.error('密码修改失败，请稍后重试')
      } finally {
        changingPassword.value = false
      }
    }
  })
}

const beforeAvatarUpload = (file) => {
  const isImage = file.type.startsWith('image/')
  const isLt2M = file.size / 1024 / 1024 < 2
  if (!isImage) {
    ElMessage.error('只能上传图片文件!')
    return false
  }
  if (!isLt2M) {
    ElMessage.error('图片大小不能超过 2MB!')
    return false
  }
  return true
}

const uploadAvatarRequest = async (options) => {
  try {
    const res = await uploadAvatar(options.file)
    if (res.code === 200) {
      ElMessage.success('头像上传成功')
      loadProfile()
    } else {
      ElMessage.error(res.message || '头像上传失败')
    }
  } catch (e) {
    console.error('头像上传失败:', e)
    ElMessage.error('头像上传失败，请稍后重试')
  }
}

onMounted(() => {
  loadProfile()
})
</script>

<style scoped>
.profile-container {
  max-width: 1000px;
  margin: 0 auto;
}

.avatar-card {
  text-align: center;
}

.avatar-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 20px 0;
}

.main-avatar {
  margin-bottom: 16px;
  background: linear-gradient(135deg, #4361ee 0%, #6c83f2 100%);
  color: white;
  font-size: 36px;
  font-weight: 500;
}

.upload-btn {
  margin-bottom: 20px;
}

.user-name {
  font-size: 20px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin: 0 0 8px 0;
}

.role-tag {
  margin-bottom: 16px;
}

.profile-tabs {
  width: 100%;
}

.profile-form {
  max-width: 500px;
  padding: 20px 0;
}

.avatar-uploader {
  margin-bottom: 20px;
}
</style>
