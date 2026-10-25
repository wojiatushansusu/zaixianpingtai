<template>
    <!-- ═══════ 人脸核验弹窗（含动作活体检测） ═══════ -->
    <el-dialog 
      v-model="visible" 
      :title="verifyStepTitle" 
      width="520px" 
      :close-on-click-modal="false" 
      :close-on-press-escape="false"
      :show-close="verifyStep >= 4"
    >
      <!-- Step 0: 未上传注册照片 -->
      <template v-if="verifyStep === 0">
        <el-result icon="warning" title="未上传注册照片" sub-title="该考试需要人脸身份核验，请先上传注册照片后再参加考试。">
          <template #extra>
            <el-button type="primary" @click="goToProfile">前往个人中心上传</el-button>
            <el-button @click="visible = false">取消</el-button>
          </template>
        </el-result>
      </template>

      <!-- Step 1: 检测人脸 + 加载AI模型 -->
      <template v-if="verifyStep === 1">
        <el-alert type="info" :closable="false" show-icon style="margin-bottom: 16px;">
          <template #title>第 1 步：请正对摄像头</template>
          请将面部放入框内，保持光线充足
        </el-alert>

        <!-- AI模型加载进度 -->
        <div v-if="verifyCameraActive && !faceApiReady && !opencvReady" class="model-loading-bar">
          <el-progress :percentage="modelLoadProgress" :stroke-width="6" status="" />
          <span>{{ modelLoadingText }}</span>
        </div>

        <div class="verify-area">
          <video ref="verifyVideoRef" autoplay playsinline muted 
                 style="width: 100%; border-radius: 8px; display: block; transform: scaleX(-1);" 
                 v-show="verifyCameraActive" />
          <canvas ref="verifyOverlayRef" style="position: absolute; top: 0; left: 0; pointer-events: none;"></canvas>
          <canvas ref="verifyCanvasRef" style="display: none;"></canvas>
          <div v-show="!verifyCameraActive" class="camera-placeholder">
            <span v-if="!verifyCameraStarting">摄像头未启动</span>
            <span v-else><el-icon class="is-loading" style="margin-right: 4px;" />正在启动...</span>
          </div>

          <!-- 人脸状态提示 -->
          <div v-show="verifyCameraActive" class="face-hint" :class="faceDetectedInPreview ? 'ok' : 'waiting'">
            <el-icon v-if="faceDetectedInPreview"><CircleCheckFilled /></el-icon>
            <el-icon v-else class="pulse-icon"><WarningFilled /></el-icon>
            {{ faceDetectedInPreview ? '已检测到人脸' : (opencvReady ? '请正对摄像头' : 'AI模型加载中，请稍候...') }}
          </div>
        </div>

        <div style="margin-top: 16px; text-align: center;">
          <el-button v-if="!verifyCameraActive && !verifyCameraStarting" @click="startVerifyCamera">打开摄像头</el-button>
          <el-button v-else type="primary" @click="goToStep2" 
                     :disabled="!opencvReady && !faceDetectedInPreview">
            {{ faceApiReady ? '下一步：动作验证' : (faceDetectedInPreview ? '下一步（AI降级模式）' : (modelLoadingText || '正在准备...')) }}
          </el-button>
          <el-button @click="cancelVerify">取消</el-button>
        </div>
      </template>

      <!-- Step 2: 动作活体检测 -->
      <template v-if="verifyStep === 2">
        <!-- 降级模式提示 -->
        <template v-if="!faceApiReady">
          <el-alert type="warning" :closable="false" show-icon style="margin-bottom: 16px;">
            <template #title>第 2 步：动作活体检测（已跳过）</template>
            AI模型未加载成功，已自动跳过动作检测，可直接进入下一步
          </el-alert>
          <div style="margin-top: 16px; text-align: center;">
            <el-button type="primary" size="large" @click="goToStep3">
              下一步：确认身份 → 开始考试
            </el-button>
          </div>
        </template>

        <!-- 正常模式：动作检测 -->
        <template v-else>
          <el-alert type="success" :closable="false" show-icon style="margin-bottom: 16px;">
            <template #title>第 2 步：动作活体检测</template>
            请按照提示完成以下 <strong>{{ requiredActions.length }}</strong> 个动作，证明您是真人
          </el-alert>

          <!-- 动作列表（横向展示当前需要做的动作） -->
          <div class="action-cards">
            <div v-for="act in requiredActions" :key="act.key" 
                 class="action-card" :class="getActionClass(act.key)">
              <div class="action-icon">{{ actionIcons[act.key] }}</div>
              <div class="action-name">{{ actionNames[act.key] }}</div>
              
              <!-- 进度条 -->
              <div v-if="!completedActions.has(act.key)" class="action-progress-wrap">
                <el-progress :percentage="actionProgress[act.key] || 0" :stroke-width="4" 
                             :color="'#409eff'" :show-text="false" />
              </div>
              <el-icon v-else class="action-done"><CircleCheckFilled /></el-icon>
            </div>
          </div>

          <!-- 当前动作提示（大字显示） -->
          <div class="current-action-prompt" v-if="currentActionHint">
            <div class="prompt-bubble">
              {{ currentActionIcon }} {{ currentActionHint }}
            </div>
            <div class="prompt-sub">请在摄像头前完成此动作</div>
          </div>

          <!-- 视频预览（缩小） -->
          <div class="verify-area verify-mini">
            <video ref="actionVideoRef" autoplay playsinline muted 
                   style="width: 100%; border-radius: 8px; display: block; transform: scaleX(-1);" />
          </div>

          <!-- 全部完成后的按钮 -->
          <div style="margin-top: 16px; text-align: center;" v-if="allActionsDone">
            <el-alert type="success" :closable="false" style="margin-bottom: 12px;">
              ✅ 所有动作验证通过！
            </el-alert>
            <el-button type="primary" size="large" @click="goToStep3">
              下一步：确认身份 → 开始考试
            </el-button>
          </div>
          <!-- 未完成：仅降级模式允许跳过，正常模式强制等待 -->
          <div style="margin-top: 16px; text-align: center;" v-if="!allActionsDone">
            <el-button v-if="!faceApiReady" @click="skipLivenessCheck" type="info" plain>跳过活体检测（AI降级模式）</el-button>
            <span v-else style="color:#909399;font-size:13px;line-height:32px;">请完成上方所有动作验证...</span>
          </div>
        </template>
      </template>

      <!-- Step 3: 身份确认 + 拍照上传 -->
      <template v-if="verifyStep === 3">
        <el-alert type="success" :closable="false" show-icon style="margin-bottom: 16px;">
          <template #title>最后一步：确认身份</template>
          正在比对您的面部特征与注册照...
        </el-alert>

        <div class="match-result-area">
          <!-- 左侧：当前画面截图预览 -->
          <div class="match-photo">
            <div class="photo-label">当前拍摄</div>
            <canvas ref="matchCanvasRef" class="photo-img" />
          </div>
          
          <!-- 中间：比对结果 -->
          <div class="match-center">
            <div class="match-arrow">⟷</div>
            <div class="match-info" v-if="matchResult !== null">
              <el-tag :type="matchResult.matched ? 'success' : 'danger'" effect="dark" size="large">
                相似度: {{ (matchResult.similarity * 100).toFixed(0) }}%
              </el-tag>
              <div v-if="matchResult.matched" class="match-ok-text">
                ✓ 身份确认通过
              </div>
              <div v-else class="match-warn-text">
                ⚠ 相似度偏低，但已完成活体验证，允许进入考试
              </div>
            </div>
            <div v-else class="match-loading">
              <el-icon class="is-loading" :size="28"><Loading /></el-icon>
              <span>正在比对...</span>
            </div>
          </div>

          <!-- 右侧：注册照预览 -->
          <div class="match-photo">
            <div class="photo-label">注册照片</div>
            <img v-if="registrationPhotoDataUrl" :src="registrationPhotoDataUrl" class="photo-img" />
            <div v-else class="photo-img photo-placeholder">无注册照</div>
          </div>
        </div>

        <div style="margin-top: 20px; text-align: center;">
          <el-button type="primary" size="large" @click="finishAndStartExam" :loading="verifyLoading" :disabled="matchResult === null">
            🚀 开始考试
          </el-button>
          <el-button @click="goBackToStep2">返回重做动作</el-button>
        </div>
      </template>
    </el-dialog>
</template>

<script setup>
/**
 * 人脸核验弹窗（含动作活体检测）。
 *
 * 从 StudentExams.vue 抽出的第三步，也是最大的一块。除下述两处接口适配外，
 * **内部逻辑一行未改**（包括大量 console.log 与调试导出，均原样保留）：
 *
 * 1. 可见性：原来用组件内 ref，现在由 v-model 控制（visible 是 computed，setter 抛事件）
 * 2. 考生 ID：原来用组件内 pendingExamId，现在由父组件通过 examId 传入
 *
 * 打开时自己完成「获取注册照 → 决定起始 step → 启动摄像头」的准备流程，
 * 父组件只负责「把 examId 传进来并打开」。
 */
import { ref, computed, watch, nextTick, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { verifyFace, getRegistrationPhoto } from '@/api/face'
import openCVHelper from '@/utils/openCVHelper'
import faceApiHelper from '@/utils/faceApiHelper'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  examId: { type: [Number, String], default: null }
})

const emit = defineEmits(['update:modelValue'])

const router = useRouter()

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
})

// ─── 人脸核验状态机 ───
// verifyStep: 0=无注册照, 1=检测人脸+加载模型, 2=动作活体检测, 3=身份确认, 4=完成
const verifyStep = ref(0)
const verifyLoading = ref(false)
const verifyCameraActive = ref(false)
const verifyCameraStarting = ref(false)

// 错误信息和降级状态
const photoCheckErrorInfo = ref(null)
const cameraErrorInfo = ref(null)
const modelLoadErrorInfo = ref(null)
const faceVerifyDebugInfo = ref({
  registrationPhotoSuccess: false,
  cameraAccessSuccess: false,
  openCVLoaded: false,
  faceApiLoaded: false,
  actionDetectionReady: false,
  errors: [],
  warnings: []
})

// DOM refs
const verifyVideoRef = ref(null)
const actionVideoRef = ref(null)
const verifyCanvasRef = ref(null)
const matchCanvasRef = ref(null)
const verifyOverlayRef = ref(null)
const hasRegistrationPhoto = ref(false)
let verifyStream = null

// OpenCV.js 检测状态
const opencvReady = ref(false)
const faceDetectedInPreview = ref(false)
let verifyDetectTimer = null

// face-api.js 活体检测状态
const faceApiReady = ref(false)
const modelLoadingText = ref('正在加载AI模型...')
const modelLoadProgress = ref(0)

// 动作检测相关
const requiredActions = ref([])
const completedActions = ref(new Set())
const actionProgress = ref({})   // { blink: 50, mouthOpen: 0, ... }
const currentActionHint = ref('')
const currentActionIcon = ref('')
let actionDetector = null

// 动作配置
const actionNames = {
  blink: '眨眼',
  mouthOpen: '张嘴',
  turnLeft: '向左转头',
  turnRight: '向右转头',
  turnUp: '抬头',
  turnDown: '低头'
}
const actionIcons = {
  blink: '👁️',
  mouthOpen: '😮',
  turnLeft: '↩️',
  turnRight: '↪️',
  turnUp: '🫠',
  turnDown: '👇'
}

// 身份匹配结果
const matchResult = ref(null)
const registrationPhotoDataUrl = ref(null)

// 计算属性
const verifyStepTitle = computed(() => {
  const titles = { 0: '人脸身份核验', 1: '人脸身份核验 - 第1步/共3步', 2: '动作活体检测 - 第2步/共3步', 3: '身份确认 - 最后一步' }
  return titles[verifyStep.value] || '核验中...'
})

const allActionsDone = computed(() => {
  return requiredActions.value.length > 0 && requiredActions.value.every(a => completedActions.value.has(a.key))
})

function resetVerifyState() {
  verifyStep.value = 1
  verifyLoading.value = false
  opencvReady.value = false
  faceDetectedInPreview.value = false
  faceApiReady.value = false
  modelLoadingText.value = '正在加载AI模型...'
  modelLoadProgress.value = 0
  completedActions.value = new Set()
  actionProgress.value = {}
  currentActionHint.value = ''
  currentActionIcon.value = ''
  matchResult.value = null
  stopActionDetector()
  
  // 随机选择2个动作（从5个可用动作中）
  const allActionKeys = ['blink', 'mouthOpen', 'turnLeft', 'turnRight']
  // Fisher-Yates 洗牌取前2个
  const shuffled = [...allActionKeys].sort(() => Math.random() - 0.5)
  requiredActions.value = shuffled.slice(0, 2).map(key => ({ key }))
  
  // 设置第一个为默认提示
  if (requiredActions.value.length > 0) {
    currentActionHint.value = `请${actionNames[requiredActions.value[0].key]}`
    currentActionIcon.value = actionIcons[requiredActions.value[0].key]
  }
}

function getActionClass(actionKey) {
  if (completedActions.value.has(actionKey)) return 'done'
  const progress = actionProgress.value[actionKey] || 0
  if (progress > 0) return 'in-progress'
  return 'pending'
}

function goToProfile() { visible.value = false; router.push('/profile') }

function cancelVerify() {
  visible.value = false
  stopVerifyCamera()
  stopActionDetector()
}

// ────────────── Step 1: 人脸检测 + 模型加载 ──────────────

async function startVerifyCamera(retryCount = 0) {
  if (verifyCameraActive.value || verifyCameraStarting.value) return
  
  // 最大重试次数限制
  if (retryCount > 2) {
    ElMessage.warning('摄像头访问多次失败，已进入降级模式')
    faceVerifyDebugInfo.value.cameraAccessSuccess = false
    faceVerifyDebugInfo.value.errors.push({
      type: 'CAMERA_MAX_RETRY_FAILED',
      message: `摄像头访问失败，重试次数：${retryCount}`,
      timestamp: new Date().toISOString()
    })
    
    // 直接进入降级模式的模型加载
    setTimeout(() => {
      if (visible.value && verifyStep.value === 1) {
        startModelLoading(true) // fallbackMode = true
      }
    }, 500)
    return
  }

  verifyCameraStarting.value = true
  cameraErrorInfo.value = null

  try {
    await nextTick()
    
    // 尝试获取摄像头权限
    verifyStream = await navigator.mediaDevices.getUserMedia({
      video: { facingMode: 'user', width: 480, height: 360 },
      audio: false
    })
    
    await nextTick()
    
    faceVerifyDebugInfo.value.cameraAccessSuccess = true
    cameraErrorInfo.value = null // 清除之前的错误信息
    
    // 同时设置两个视频源（step1 和 step2 共用）
    const setVideo = (refVal) => {
      if (refVal) {
        refVal.srcObject = verifyStream
        verifyCameraActive.value = true
        return true
      }
      return false
    }

    const ok1 = setVideo(verifyVideoRef.value)
    
    if (ok1) {
      startModelLoading(false) // fallbackMode = false
      startVerifyFaceDetection()
    } else {
      setTimeout(() => {
        if (verifyVideoRef.value && verifyStream) {
          verifyVideoRef.value.srcObject = verifyStream
          verifyCameraActive.value = true
          startModelLoading(false)
          startVerifyFaceDetection()
        }
      }, 200)
    }
  } catch (e) {
    console.error('[FaceVerify] 摄像头访问失败:', e)
    cameraErrorInfo.value = {
      name: e.name,
      message: e.message,
      code: e.code,
      retryCount,
      timestamp: new Date().toISOString()
    }

    faceVerifyDebugInfo.value.cameraAccessSuccess = false
    faceVerifyDebugInfo.value.errors.push({
      type: 'CAMERA_ERROR',
      name: e.name,
      message: e.message,
      retryCount,
      timestamp: new Date().toISOString()
    })

    // 根据错误类型提供不同的用户提示
    let userMessage = '摄像头访问失败'
    let messageType = 'error'
    let showRetry = false
    
    if (e.name === 'NotAllowedError') {
      userMessage = '摄像头访问被拒绝，请在浏览器权限设置中允许使用摄像头'
      messageType = 'warning'
    } else if (e.name === 'NotFoundError') {
      userMessage = '未检测到可用摄像头，请确认摄像头已连接'
      messageType = 'error'
    } else if (e.name === 'NotReadableError' || e.name === 'TrackStartError') {
      userMessage = '摄像头正被其他应用占用，请关闭其他应用后重试'
      messageType = 'warning'
      showRetry = true
    } else if (e.name === 'OverconstrainedError') {
      userMessage = '摄像头不支持所需的分辨率，将尝试其他设置'
      messageType = 'info'
      showRetry = true
    } else {
      userMessage = `摄像头访问失败：${e.message || '未知错误'}`
    }

    ElMessage({
      message: userMessage,
      type: messageType,
      duration: 5000
    })

    // 如果需要重试，延迟后自动重试
    if (showRetry && retryCount < 2) {
      setTimeout(() => {
        if (visible.value && !verifyCameraActive.value) {
          ElMessage.info(`摄像头访问失败，${3 - retryCount}秒后重试...`)
          setTimeout(() => startVerifyCamera(retryCount + 1), 3000)
        }
      }, 1000)
    }
  } finally {
    verifyCameraStarting.value = false
  }
}

/**
 * 并行加载 OpenCV.js + face-api.js 模型（支持降级模式）
 */
async function startModelLoading(fallbackMode = false) {
  let progress = 0
  modelLoadErrorInfo.value = null
  
  faceVerifyDebugInfo.value.openCVLoaded = false
  faceVerifyDebugInfo.value.faceApiLoaded = false
  
  const updateProgress = (val, text, warning = false) => {
    progress = val
    modelLoadProgress.value = val
    modelLoadingText.value = text
    
    if (warning) {
      faceVerifyDebugInfo.value.warnings.push({
        type: 'MODEL_LOAD_WARNING',
        message: text,
        progress: val,
        timestamp: new Date().toISOString()
      })
    }
  }

  updateProgress(5, '正在加载人脸检测模块...')

  // 1. OpenCV.js 加载（用于基础人脸检测）
  const opencvPromise = openCVHelper.waitForLoad()
    .then(ok => {
      opencvReady.value = ok
      faceVerifyDebugInfo.value.openCVLoaded = ok
      
      if (ok) {
        updateProgress(30, 'OpenCV就绪，加载高级AI模型...')
      } else {
        updateProgress(30, 'OpenCV加载失败，使用降级模式...', true)
        faceDetectedInPreview.value = true // 在降级模式下假设人脸已检测
        
        // OpenCV失败时不阻止流程，但记录警告
        faceVerifyDebugInfo.value.errors.push({
          type: 'OPENCV_LOAD_FAILED',
          message: 'OpenCV.js 加载失败，人脸检测功能受限',
          fallbackMode: fallbackMode,
          timestamp: new Date().toISOString()
        })
        
        if (!fallbackMode) {
          ElMessage.warning('基础人脸检测功能受限，将尝试使用AI模型进行高级检测')
        }
      }
    })
    .catch(err => {
      console.error('[OpenCV] 加载异常:', err)
      opencvReady.value = false
      faceDetectedInPreview.value = true // 降级模式下假设通过
      
      modelLoadErrorInfo.value = {
        name: 'OPENCV_ERROR',
        message: err.message,
        timestamp: new Date().toISOString()
      }
      
      updateProgress(30, 'OpenCV加载异常，启用降级模式...', true)
    })

  // 2. face-api.js 加载（用于活体检测+特征匹配）
  const faceapiPromise = faceApiHelper.waitForLoad()
    .then(ok => {
      faceApiReady.value = ok
      faceVerifyDebugInfo.value.faceApiLoaded = ok
      
      if (ok) {
        updateProgress(70, 'AI模型就绪，准备人脸识别...')
        faceVerifyDebugInfo.value.actionDetectionReady = true
      } else {
        updateProgress(70, 'AI模型加载失败，活体检测不可用...', true)
        
        // 记录AI模型失败
        faceVerifyDebugInfo.value.errors.push({
          type: 'FACEAPI_LOAD_FAILED',
          message: 'face-api.js 加载失败，活体检测功能不可用',
          fallbackMode: fallbackMode,
          timestamp: new Date().toISOString()
        })
        
        if (!fallbackMode) {
          ElMessage.warning('活体检测功能不可用，将直接进入身份确认步骤')
        }
      }
    })
    .catch(err => {
      console.error('[FaceAPI] 加载异常:', err)
      faceApiReady.value = false
      
      modelLoadErrorInfo.value = {
        name: 'FACEAPI_ERROR',
        message: err.message,
        timestamp: new Date().toISOString()
      }
      
      updateProgress(70, 'AI模型加载异常，使用基础检测...', true)
    })

  // 并行执行，使用更宽松的等待策略
  try {
    const maxWaitTime = fallbackMode ? 15000 : 30000 // 降级模式等待更短时间
    const timeoutPromise = new Promise((_, reject) => {
      setTimeout(() => reject(new Error('AI模型加载超时')), maxWaitTime)
    })
    
    await Promise.race([
      Promise.allSettled([opencvPromise, faceapiPromise]),
      timeoutPromise
    ])
  } catch (timeoutErr) {
    console.warn('[ModelLoad] 加载超时:', timeoutErr)
    
    // 超时时尝试强制设置降级状态
    faceVerifyDebugInfo.value.errors.push({
      type: 'MODEL_LOAD_TIMEOUT',
      message: `AI模型加载超时（${fallbackMode ? '降级' : '正常'}模式）`,
      timestamp: new Date().toISOString()
    })
    
    if (!opencvReady.value) {
      opencvReady.value = false
      faceDetectedInPreview.value = true // 降级模式
    }
    
    if (!faceApiReady.value) {
      faceApiReady.value = false
    }
    
    updateProgress(100, '加载超时，使用基础模式...', true)
    
    ElMessage.warning('AI模型加载较慢，部分高级功能可能受限')
    return
  }

  // 加载完成后检查状态
  const allModelsFailed = !opencvReady.value && !faceApiReady.value
  const someModelsFailed = !opencvReady.value || !faceApiReady.value
  
  if (allModelsFailed) {
    updateProgress(100, '所有AI模型加载失败，使用极简模式 ⚠️', true)
    ElMessage.warning('AI模型全部加载失败，将使用极简验证模式')
    
    // 紧急情况：如果没有模型可用，直接进入下一步
    setTimeout(() => {
      if (visible.value && verifyStep.value === 1 && !faceCameraActive.value) {
        goToStep2()
      }
    }, 1500)
  } else if (someModelsFailed) {
    const loadedFeatures = []
    if (opencvReady.value) loadedFeatures.push('基础检测')
    if (faceApiReady.value) loadedFeatures.push('活体检测')
    
    updateProgress(100, `部分就绪: ${loadedFeatures.join('+')}`, true)
    ElMessage.info(`部分AI功能可用: ${loadedFeatures.join(', ')}`)
  } else {
    updateProgress(100, '全部就绪 ✅')
    ElMessage.success('AI模型加载完成，准备开始人脸核验')
  }
}

function stopVerifyCamera() {
  stopActionDetector()
  if (verifyDetectTimer) { clearInterval(verifyDetectTimer); verifyDetectTimer = null }
  if (verifyStream) {
    verifyStream.getTracks().forEach(track => track.stop())
    verifyStream = null
  }
  verifyCameraActive.value = false
  faceDetectedInPreview.value = false
  opencvReady.value = false
  faceApiReady.value = false
}

/** OpenCV.js 实时人脸检测（Step 1 预览用） */
async function startVerifyFaceDetection() {
  const loaded = await openCVHelper.waitForLoad()
  opencvReady.value = loaded
  if (!loaded) { faceDetectedInPreview.value = true; return }

  verifyDetectTimer = setInterval(async () => {
    if (!verifyVideoRef.value || !verifyCameraActive.value) return
    try {
      const result = openCVHelper.detectFaces(verifyVideoRef.value)
      faceDetectedInPreview.value = result.detected
      if (verifyOverlayRef.value && result.faces.length > 0) {
        drawVerifyOverlay(result.faces, verifyOverlayRef.value,
          verifyVideoRef.value.videoWidth || 480,
          verifyVideoRef.value.videoHeight || 360)
      } else if (verifyOverlayRef.value) {
        const ctx = verifyOverlayRef.value.getContext('2d')
        ctx.clearRect(0, 0, verifyOverlayRef.value.width, verifyOverlayRef.value.height)
      }
    } catch (e) { /* ignore */ }
  }, 500)
}

function drawVerifyOverlay(faces, canvas, frameWidth, frameHeight) {
  canvas.width = frameWidth
  canvas.height = frameHeight
  const ctx = canvas.getContext('2d')
  ctx.clearRect(0, 0, canvas.width, canvas.height)
  faces.forEach((face) => {
    ctx.strokeStyle = '#67c23a'
    ctx.lineWidth = 2
    const x = canvas.width - face.x - face.width
    ctx.strokeRect(x, face.y, face.width, face.height)
  })
}

// ────────────── Step 切换 ──────────────

function goToStep2() {
  verifyStep.value = 2
  // 将视频流切换到 step2 的 video 元素
  nextTick(() => {
    if (actionVideoRef.value && verifyStream) {
      actionVideoRef.value.srcObject = verifyStream
    }
    // 启动动作检测
    if (faceApiReady.value) {
      startActionDetection()
    }
  })
}

function goBackToStep2() {
  verifyStep.value = 2
  matchResult.value = null
  // 重新选择动作（重新随机）
  const allActionKeys = ['blink', 'mouthOpen', 'turnLeft', 'turnRight']
  const shuffled = [...allActionKeys].sort(() => Math.random() - 0.5)
  requiredActions.value = shuffled.slice(0, 2).map(key => ({ key }))
  completedActions.value = new Set()
  actionProgress.value = {}
  if (requiredActions.value.length > 0) {
    currentActionHint.value = `请${actionNames[requiredActions.value[0].key]}`
    currentActionIcon.value = actionIcons[requiredActions.value[0].key]
  }
  nextTick(() => startActionDetection())
}

async function goToStep3() {
  verifyStep.value = 3
  stopActionDetector()

  // 截取当前帧作为匹配用的照片
  await nextTick()
  const video = verifyVideoRef.value || actionVideoRef.value
  if (video && matchCanvasRef.value) {
    const cv = matchCanvasRef.value
    cv.width = video.videoWidth || 480
    cv.height = video.videoHeight || 360
    cv.getContext('2d').drawImage(video, 0, 0, cv.width, cv.height)
  }

  // 执行特征匹配
  await doFaceMatch()
}

function skipLivenessCheck() {
  // 跳过活体检测直接到身份确认步骤
  goToStep3()
}

// ────────────── Step 2: 动作活体检测 ──────────────

function startActionDetection() {
  if (!faceApiReady.value || !actionVideoRef.value) return

  stopActionDetector()

  const videoEl = actionVideoRef.value
  const actionKeys = requiredActions.value.map(a => a.key)

  actionDetector = faceApiHelper.createActionDetector(videoEl, { intervalMs: 250 })

  // 进度回调
  actionDetector.onProgress = (action, progress) => {
    actionProgress.value[action] = progress
    // 更新当前提示文字（第一个未完成的动作）
    const firstPending = requiredActions.value.find(a => !completedActions.value.has(a.key))
    if (firstPending && firstPending.key === action && progress < 80) {
      currentActionHint.value = `请${actionNames[action]}`
      currentActionIcon.value = actionIcons[action]
    }
  }

  // 完成回调
  actionDetector.onCompleted = (action, data) => {
    completedActions.value.add(action)
    actionProgress.value[action] = 100
    ElMessage.success(`${actionNames[action]} ✓`)
    
    // 切换到下一个未完成的动作提示
    const nextPending = requiredActions.value.find(a => !completedActions.value.has(a.key))
    if (nextPending) {
      currentActionHint.value = `请${actionNames[nextPending.key]}`
      currentActionIcon.value = actionIcons[nextPending.key]
    } else {
      currentActionHint.value = ''
      currentActionIcon.value = ''
    }
  }

  // 启动检测
  actionDetector.start(actionKeys).then(completed => {
    console.log('[Liveness] Actions completed:', [...completed])
  }).catch(err => {
    console.warn('[Liveness] Detection error:', err)
  })
}

function stopActionDetector() {
  if (actionDetector) {
    actionDetector.stop()
    actionDetector = null
  }
}

// ────────────── Step 3: 身份确认 ──────────────

async function doFaceMatch() {
  matchResult.value = null

  try {
    if (!registrationPhotoDataUrl.value) {
      // 没有数据库中的照片数据，尝试用URL
      matchResult.value = { matched: true, similarity: 1.0, distance: 0, reason: 'no_photo_data' }
      return
    }

    const videoEl = verifyVideoRef.value || actionVideoRef.value
    if (!videoEl) {
      matchResult.value = { matched: true, similarity: 0.9, distance: 0.3, reason: 'no_video' }
      return
    }

    // 使用 face-api.js 的特征向量匹配
    const result = await faceApiHelper.matchFace(registrationPhotoDataUrl.value, videoEl)
    matchResult.value = result

    if (!result.error) {
      console.log(`[FaceMatch] distance=${result.distance}, similarity=${(result.similarity * 100).toFixed(0)}%, matched=${result.matched}`)
    }
  } catch (e) {
    console.error('[FaceMatch] Error:', e)
    // 匹配失败时也允许通过（已完成活体验证就是真人了）
    matchResult.value = { matched: false, similarity: 0, distance: Infinity, error: e.message }
  }
}

async function finishAndStartExam() {
  if (!props.examId) return
  verifyLoading.value = true

  try {
    // 在摄像头仍活跃时截图并上传后端核验（一次性操作，后端记录日志+颁发ExamToken）
    const video = verifyVideoRef.value || actionVideoRef.value
    let examToken = ''
    if (video && verifyCanvasRef.value) {
      const cv = verifyCanvasRef.value
      cv.width = video.videoWidth || 480
      cv.height = video.videoHeight || 360
      cv.getContext('2d').drawImage(video, 0, 0, cv.width, cv.height)
      const blob = await new Promise(resolve => cv.toBlob(resolve, 'image/jpeg', 0.9))

      const res = await verifyFace(props.examId, blob)
      const data = res.data
      examToken = data.examToken || ''

      if (data.passed && examToken) {
        ElMessage.success(`核验通过！相似度 ${((data.similarity || matchResult.value?.similarity || 1) * 100).toFixed(0)}%`)
      } else if (data.passed && !examToken) {
        // 核验通过但未获取到凭证，记录警告但仍允许（可能是requireFaceVerify未开启）
        ElMessage.warning('核验通过但未获取到考试凭证，尝试直接进入...')
      } else {
        // 后端比对不通过，阻止进入考试
        ElMessage.error(`人脸核验未通过：${data.failReason || '相似度不足(' + ((data.similarity || 0) * 100).toFixed(0) + '%)'}，请重新验证或联系管理员`)
        verifyLoading.value = false
        return
      }
    }

    // 关闭弹窗并停摄像头
    visible.value = false
    stopVerifyCamera()

    // 跳转考试（带上examToken）
    const query = examToken ? { examToken } : {}
    router.push({ path: `/student/exam/${props.examId}`, query })

  } catch (e) {
    ElMessage.error(e.response?.data?.message || e.message || '核验失败')
  } finally {
    verifyLoading.value = false
  }
}

// =============== 人脸核验辅助函数 ===============

/**
 * 添加人脸核验错误记录
 */
function addFaceVerifyError(type, details, recoverable = true) {
  const errorRecord = {
    type,
    details,
    recoverable,
    timestamp: new Date().toISOString(),
    step: verifyStep.value,
    debugInfo: { ...faceVerifyDebugInfo.value }
  }
  
  faceVerifyDebugInfo.value.errors.push(errorRecord)
  
  // 根据错误类型确定用户提示等级
  let message = details.message || '人脸核验过程中发生错误'
  let messageType = 'error'
  let duration = 4000
  
  const errorClassifications = {
    // 可恢复的错误 - 警告级别
    'CAMERA_ACCESS_WARNING': { type: 'warning', duration: 5000 },
    'MODEL_LOAD_WARNING': { type: 'warning', duration: 5000 },
    'NETWORK_RETRY': { type: 'info', duration: 3000 },
    
    // 部分可恢复的错误 - 错误级别但有解决方案建议
    'OPENCV_LOAD_FAILED': { type: 'warning', duration: 6000 },
    'FACEAPI_LOAD_FAILED': { type: 'warning', duration: 6000 },
    
    // 严重错误 - 需要用户干预
    'CAMERA_PERMISSION_DENIED': { type: 'error', duration: 8000 },
    'NO_CAMERA_DEVICE': { type: 'error', duration: 8000 },
    'NO_REGISTRATION_PHOTO': { type: 'warning', duration: 6000 },
    
    // 致命错误 - 可能需要跳过核验
    'ALL_MODELS_FAILED': { type: 'error', duration: 10000, allowSkip: true },
    'MAX_RETRY_EXCEEDED': { type: 'error', duration: 10000, allowSkip: true }
  }
  
  const classification = errorClassifications[type]
  if (classification) {
    messageType = classification.type
    duration = classification.duration || 4000
    
    // 添加具体错误消息
    if (classification.allowSkip) {
      message = `${message}，如果问题持续，可以尝试跳过人脸核验`
    }
  }
  
  // 显示用户提示
  ElMessage({
    message: `[人脸核验] ${message}`,
    type: messageType,
    duration,
    showClose: true
  })
  
  return errorRecord
}

/**
 * 获取可跳过的错误类型
 */
function isErrorSkippable(errorType) {
  const skippableErrors = [
    'CAMERA_ACCESS_WARNING',
    'MODEL_LOAD_WARNING',
    'OPENCV_LOAD_FAILED',
    'FACEAPI_LOAD_FAILED',
    'ALL_MODELS_FAILED',
    'MAX_RETRY_EXCEEDED'
  ]
  
  return skippableErrors.includes(errorType)
}

/**
 * 检查是否可以继续人脸核验（降级模式）
 */
function canContinueWithFallback() {
  // 如果摄像头不可用，但非必需，可以继续
  if (!faceVerifyDebugInfo.value.cameraAccessSuccess && !cameraErrorInfo.value) {
    return true
  }
  
  // 如果有可跳过的错误
  const hasSkippableError = faceVerifyDebugInfo.value.errors.some(err => 
    isErrorSkippable(err.type)
  )
  
  return hasSkippableError
}

/**
 * 获取人脸核验降级模式建议
 */
function getFallbackSuggestion() {
  const suggestions = []
  
  if (!faceVerifyDebugInfo.value.cameraAccessSuccess) {
    suggestions.push('• 摄像头访问失败，将跳过人脸检测，使用其他身份验证方式')
  }
  
  if (!faceVerifyDebugInfo.value.openCVLoaded) {
    suggestions.push('• 基础人脸检测不可用，将依赖后台比对')
  }
  
  if (!faceVerifyDebugInfo.value.faceApiLoaded) {
    suggestions.push('• 活体检测不可用，将跳过动作验证环节')
  }
  
  if (faceVerifyDebugInfo.value.errors.length > 3) {
    suggestions.push('• 检测到多个错误，建议联系技术支持或尝试刷新页面')
  }
  
  return suggestions.length > 0 ? suggestions : ['所有功能正常，无需降级']
}

/**
 * 导出debug信息供技术支持使用
 */
function exportFaceVerifyDebugInfo() {
  const debugData = {
    timestamp: new Date().toISOString(),
    examId: props.examId,
    userAgent: navigator.userAgent,
    debugInfo: faceVerifyDebugInfo.value,
    cameraError: cameraErrorInfo.value,
    modelLoadError: modelLoadErrorInfo.value,
    photoCheckError: photoCheckErrorInfo.value,
    currentStep: verifyStep.value,
    systemInfo: {
      screenSize: `${window.screen.width}x${window.screen.height}`,
      connectionType: navigator.connection ? navigator.connection.effectiveType : 'unknown',
      browserVersion: {
        chrome: navigator.userAgent.match(/Chrome\/([0-9.]+)/)?.[1],
        firefox: navigator.userAgent.match(/Firefox\/([0-9.]+)/)?.[1],
        safari: navigator.userAgent.match(/Version\/([0-9.]+)/)?.[1]
      }
    }
  }
  
  const jsonStr = JSON.stringify(debugData, null, 2)
  const blob = new Blob([jsonStr], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `face-verify-debug-${new Date().getTime()}.json`
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
  
  ElMessage.success('Debug信息已导出，请将此文件提供给技术支持人员')
}

// =============== 流程中断恢复机制 ===============

/**
 * 保存人脸核验状态到sessionStorage
 */
function saveFaceVerifyState() {
  try {
    const state = {
      examId: props.examId,
      step: verifyStep.value,
      timestamp: Date.now(),
      cameraActive: verifyCameraActive.value,
      cameraStarted: verifyCameraStarting.value,
      opencvReady: opencvReady.value,
      faceApiReady: faceApiReady.value,
      modelLoadProgress: modelLoadProgress.value,
      registrationPhotoExists: hasRegistrationPhoto.value,
      errorsCount: faceVerifyDebugInfo.value.errors.length,
      warningsCount: faceVerifyDebugInfo.value.warnings.length
    }
    
    sessionStorage.setItem('faceVerifyState', JSON.stringify(state))
    
    // 保存详细的debug信息（限制大小）
    const debugInfo = {
      errors: faceVerifyDebugInfo.value.errors.slice(-5), // 只保留最近5个错误
      warnings: faceVerifyDebugInfo.value.warnings.slice(-3), // 只保留最近3个警告
      cameraAccess: faceVerifyDebugInfo.value.cameraAccessSuccess,
      openCVLoaded: faceVerifyDebugInfo.value.openCVLoaded
    }
    sessionStorage.setItem('faceVerifyDebug', JSON.stringify(debugInfo))
    
    return true
  } catch (e) {
    console.warn('[FaceVerify] 状态保存失败:', e)
    return false
  }
}

/**
 * 从sessionStorage恢复人脸核验状态
 */
function restoreFaceVerifyState() {
  try {
    const stateStr = sessionStorage.getItem('faceVerifyState')
    if (!stateStr) return null
    
    const state = JSON.parse(stateStr)
    
    // 检查状态是否过期（超过5分钟）
    const isExpired = Date.now() - state.timestamp > 5 * 60 * 1000
    if (isExpired) {
      sessionStorage.removeItem('faceVerifyState')
      sessionStorage.removeItem('faceVerifyDebug')
      return null
    }
    
    // 检查是否是同一个考试
    if (state.examId !== props.examId) {
      return null
    }
    
    return {
      step: state.step,
      cameraActive: state.cameraActive,
      opencvReady: state.opencvReady,
      faceApiReady: state.faceApiReady,
      modelLoadProgress: state.modelLoadProgress,
      registrationPhotoExists: state.registrationPhotoExists
    }
  } catch (e) {
    console.warn('[FaceVerify] 状态恢复失败:', e)
    sessionStorage.removeItem('faceVerifyState')
    sessionStorage.removeItem('faceVerifyDebug')
    return null
  }
}

/**
 * 提供状态恢复建议
 */
function getRecoverySuggestion(savedState) {
  if (!savedState) return null
  
  const suggestions = []
  
  if (savedState.step > 0 && savedState.step < verifyStep.value) {
    suggestions.push('检测到未完成的人脸核验流程，是否从上次中断的地方继续？')
  }
  
  if (savedState.opencvReady && !opencvReady.value) {
    suggestions.push('之前已成功加载OpenCV，建议重新加载模型')
  }
  
  if (savedState.faceApiReady && !faceApiReady.value) {
    suggestions.push('之前已成功加载AI模型，建议重新启动人脸检测')
  }
  
  if (savedState.cameraActive && !verifyCameraActive.value) {
    suggestions.push('之前摄像头已经启动，是否尝试重新连接？')
  }
  
  return suggestions.length > 0 ? suggestions : null
}

/**
 * 应用恢复的状态
 */
function applyRecoveredState(savedState) {
  if (!savedState) return false
  
  // 根据保存的状态恢复流程
  if (savedState.step > 0 && savedState.step < 4) {
    verifyStep.value = savedState.step
    verifyCameraActive.value = savedState.cameraActive
    opencvReady.value = savedState.opencvReady
    faceApiReady.value = savedState.faceApiReady
    modelLoadProgress.value = savedState.modelLoadProgress
    
    // 根据恢复的状态进行相应的操作
    if (verifyStep.value === 1 && !verifyCameraActive.value) {
      setTimeout(() => startVerifyCamera(), 1000)
    }
    
    ElMessage.info(`已从步骤 ${savedState.step} 恢复人脸核验流程`)
    return true
  }
  
  return false
}

/**
 * 定期保存状态（每30秒）
 */
let stateSaveTimer = null
function startStateSaving() {
  if (stateSaveTimer) clearInterval(stateSaveTimer)
  
  stateSaveTimer = setInterval(() => {
    if (visible.value) {
      saveFaceVerifyState()
    } else {
      clearInterval(stateSaveTimer)
      stateSaveTimer = null
    }
  }, 30000) // 每30秒保存一次
}

/**
 * 停止状态保存
 */
function stopStateSaving() {
  if (stateSaveTimer) {
    clearInterval(stateSaveTimer)
    stateSaveTimer = null
  }
  
  // 清空保存的状态
  sessionStorage.removeItem('faceVerifyState')
  sessionStorage.removeItem('faceVerifyDebug')
}

/**
 * 页面可见性变化处理（防止标签页切换导致流程中断）
 */
function setupVisibilityHandlers() {
  if (!document.hidden) return
  
  document.addEventListener('visibilitychange', () => {
    if (visible.value) {
      if (document.hidden) {
        // 页面隐藏时暂停检测
        if (verifyCameraActive.value) {
          ElMessage.warning('页面切换可能导致人脸检测暂停，返回后会自动恢复')
        }
      } else {
        // 页面重新显示时尝试恢复
        setTimeout(() => {
          if (visible.value && verifyStep.value === 1 && !verifyCameraActive.value) {
            ElMessage.info('正在尝试恢复摄像头连接...')
            startVerifyCamera()
          }
        }, 1000)
      }
    }
  })
}

// 页面卸载时清理
window.addEventListener('beforeunload', () => {
  if (visible.value) {
    stopStateSaving()
    stopVerifyCamera()
  }
})

// ────────────── 打开弹窗时的准备流程 ──────────────
// 原实现在父组件 handleStartExam 里：先取注册照，再决定起始 step，再启动摄像头。
// 搬进来后由 visible 变化触发，顺序与原先一致。
async function bootVerify() {
  resetVerifyState()

  let photoCheckError = null
  try {
    const photoRes = await getRegistrationPhoto()
    hasRegistrationPhoto.value = photoRes.data.hasPhoto === true
    if (hasRegistrationPhoto.value) {
      registrationPhotoDataUrl.value = photoRes.data.photoData || null
    }
  } catch (e) {
    hasRegistrationPhoto.value = false
    photoCheckError = e
    console.error('[FaceVerify] 获取注册照片失败:', e)

    if (e.response?.status === 401) {
      ElMessage.error('身份验证已过期，请重新登录后再试')
    } else if (e.response?.status === 404) {
      ElMessage.info('未找到注册照片，请前往个人中心上传人脸照片')
    } else if (e.code === 'NETWORK_ERROR' || !e.response) {
      ElMessage.warning('网络异常，将尝试使用离线模式进行人脸核验')
    } else {
      ElMessage.error('获取注册照片失败，错误代码：' + (e.response?.data?.code || '未知'))
    }
  }

  // 即使注册照片获取失败，也尝试使用摄像头识别，但显示相应提示
  if (photoCheckError) {
    verifyStep.value = 1
    photoCheckErrorInfo.value = {
      code: photoCheckError.response?.status || 'NETWORK',
      message: photoCheckError.message || '照片获取失败',
      timestamp: new Date().toISOString()
    }
  } else {
    verifyStep.value = hasRegistrationPhoto.value ? 1 : 0
  }

  if (verifyStep.value === 1) {
    // 必须等弹窗内容渲染出来，video/canvas 的 ref 才有值
    await nextTick()
    try {
      await startVerifyCamera()
    } catch (cameraErr) {
      console.error('[FaceVerify] startVerifyCamera 失败:', cameraErr)
      ElMessage.error('摄像头启动失败：' + (cameraErr.message || '无法访问摄像头'))
    }
  }
}

// flush: 'post' 确保 DOM 已渲染；内部再 await nextTick() 兜一层
watch(() => props.modelValue, (open) => {
  if (open) bootVerify()
}, { flush: 'post' })

// 清理随组件搬走：漏了这里，离开页面后摄像头指示灯不会灭
onBeforeUnmount(() => {
  stopVerifyCamera()
  stopStateSaving()
  stopActionDetector()
})

</script>

<style scoped>
/* ── 人脸核验 ── */
.verify-area {
  position: relative;
  background: #000;
  border-radius: 8px;
  overflow: hidden;
}
.verify-mini { max-height: 240px; overflow: hidden; }
.camera-placeholder {
  padding: 40px;
  text-align: center;
  background: #1a1a1a;
  border-radius: 8px;
  color: #909399;
}

/* 人脸检测提示 */
.face-hint {
  position: absolute;
  top: 10px;
  left: 50%;
  transform: translateX(-50%);
  padding: 5px 14px;
  border-radius: 20px;
  font-size: 12px;
  color: #fff;
  z-index: 5;
  display: flex;
  align-items: center;
  gap: 4px;
  white-space: nowrap;
}
.face-hint.ok { background: rgba(103, 194, 58, 0.85); }
.face-hint.waiting { background: rgba(230, 162, 60, 0.85); animation: pulse 1.5s infinite; }
.pulse-icon { animation: pulse 1.5s infinite; }

/* 模型加载条 */
.model-loading-bar {
  text-align: center;
  margin-bottom: 10px;
}
.model-loading-bar span {
  font-size: 12px;
  color: #909399;
  margin-left: 8px;
}

/* ── 动作卡片 ── */
.action-cards {
  display: flex;
  justify-content: center;
  gap: 16px;
  margin-bottom: 18px;
}
.action-card {
  width: 120px;
  padding: 14px 10px;
  border-radius: 12px;
  text-align: center;
  border: 2px solid #e4e7ed;
  background: #fafafa;
  transition: all 0.3s;
}
.action-card.pending { border-color: #e4e7ed; opacity: 0.85; }
.action-card.in-progress { border-color: #409eff; background: #ecf5ff; }
.action-card.done { border-color: #67c23a; background: #f0f9eb; }
.action-icon { font-size: 32px; margin-bottom: 6px; }
.action-name { font-size: 13px; font-weight: 600; color: var(--color-text-primary); }
.action-progress-wrap { margin-top: 8px; }
.action-done { color: #67c23a; font-size: 22px; margin-top: 4px; }

/* 当前动作提示 */
.current-action-prompt { text-align: center; margin-bottom: 14px; }
.prompt-bubble {
  display: inline-block;
  padding: 12px 28px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border-radius: 25px;
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 1px;
  box-shadow: 0 4px 15px rgba(102, 126, 234, 0.4);
  animation: breathe 2s ease-in-out infinite;
}
.prompt-sub { margin-top: 8px; font-size: 13px; color: #909399; }

@keyframes breathe {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.03); }
}

/* ── 身份匹配区域 ── */
.match-result-area {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 20px;
  padding: 20px 0;
}
.match-photo { text-align: center; }
.photo-label { font-size: 12px; color: #909399; margin-bottom: 6px; }
.photo-img {
  width: 140px;
  height: 140px;
  object-fit: cover;
  border-radius: 10px;
  border: 2px solid #e4e7ed;
  background: #f5f7fa;
}
.photo-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #c0c4cc;
  font-size: 13px;
}
.match-center { text-align: center; min-width: 130px; }
.match-arrow { font-size: 28px; color: #409eff; margin-bottom: 8px; }
.match-info .el-tag { font-size: 15px; padding: 8px 16px; }
.match-ok-text { margin-top: 8px; color: #67c23a; font-weight: 600; font-size: 13px; }
.match-warn-text { margin-top: 8px; color: #e6a23c; font-size: 12px; max-width: 120px; line-height: 1.4; }
.match-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  color: #909399;
  font-size: 13px;
}

/* ── 响应式（仅本组件内的元素） ── */
@media (max-width: 768px) {
  .match-result-area { flex-direction: column; }
  .action-cards { flex-wrap: wrap; }
  .action-card { width: calc(50% - 10px); }
}
</style>
