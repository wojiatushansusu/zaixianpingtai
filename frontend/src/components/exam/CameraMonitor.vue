<template>
  <div class="camera-monitor">
    <div class="camera-container" :class="{ active: isActive, error: cameraError }">
      <video
        ref="videoRef"
        class="camera-video"
        autoplay
        muted
        playsinline
        v-show="!cameraError && isActive"
      ></video>
      <canvas ref="canvasRef" class="camera-canvas" style="display: none;"></canvas>

      <div v-if="!isActive" class="camera-placeholder">
        <el-icon :size="32"><VideoCamera /></el-icon>
        <span>监控未启用</span>
      </div>

      <div v-if="cameraError" class="camera-error">
        <el-icon :size="32" color="#f56c6c"><Warning /></el-icon>
        <span>摄像头不可用</span>
      </div>

      <div v-if="isActive && !cameraError" class="monitor-status">
        <span class="status-dot" :class="{ pulse: detecting }"></span>
        <span class="status-text">{{ detecting ? 'AI检测中' : '监控中' }}</span>
      </div>
    </div>

    <div v-if="isActive && !cameraError && violationSummary.total > 0" class="violation-badge">
      <el-icon><Warning /></el-icon>
      <span>{{ violationSummary.total }}次异常</span>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, watch, onMounted, onBeforeUnmount } from 'vue'
import { VideoCamera, Warning } from '@element-plus/icons-vue'

const props = defineProps({
  examId: { type: Number, required: true },
  active: { type: Boolean, default: false }
})

const emit = defineEmits(['violation-summary'])

const videoRef = ref(null)
const canvasRef = ref(null)
const isActive = ref(false)
const cameraError = ref(false)
const detecting = ref(false)

const violationSummary = reactive({
  switch_screen: 0,
  no_face: 0,
  multi_face: 0,
  look_away: 0,
  forbidden_item: 0,
  keyboard_violation: 0,
  total: 0
})

let stream = null
let detectInterval = null
let ws = null

watch(() => props.active, (val) => {
  isActive.value = val
  if (val) {
    startCamera()
  } else {
    stopCamera()
  }
}, { immediate: true })

watch(violationSummary, () => {
  emit('violation-summary', { ...violationSummary })
}, { deep: true })

async function startCamera() {
  try {
    stream = await navigator.mediaDevices.getUserMedia({
      video: { width: 320, height: 240, facingMode: 'user' },
      audio: false
    })
    if (videoRef.value) {
      videoRef.value.srcObject = stream
      cameraError.value = false
      startDetection()
      connectWebSocket()
    }
  } catch (err) {
    console.warn('摄像头访问失败:', err)
    cameraError.value = true
  }
}

function stopCamera() {
  if (stream) {
    stream.getTracks().forEach(track => track.stop())
    stream = null
  }
  if (detectInterval) {
    clearInterval(detectInterval)
    detectInterval = null
  }
  if (ws) {
    ws.close()
    ws = null
  }
  detecting.value = false
}

function startDetection() {
  detectInterval = setInterval(() => {
    if (!videoRef.value || !canvasRef.value) return
    detecting.value = true
    setTimeout(() => { detecting.value = false }, 200)
  }, 3000)
}

function connectWebSocket() {
  try {
    const token = localStorage.getItem('token')
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const wsUrl = `${protocol}//${window.location.host}/api/ws/monitor?role=student&examId=${props.examId}&token=${token}`
    ws = new WebSocket(wsUrl)

    ws.onopen = () => {
      console.log('监控WS连接成功')
      startFrameSend()
    }

    ws.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data)
        if (data.type === 'violation') {
          addViolation(data.violationType)
        } else if (data.type === 'warning') {
          addViolation('warning')
        } else if (data.type === 'force_submit') {
          console.warn('收到强制交卷指令')
        }
      } catch (e) {
        console.warn('解析WS消息失败:', e)
      }
    }

    ws.onclose = () => {
      console.warn('监控WS连接断开')
    }

    ws.onerror = (err) => {
      console.warn('监控WS错误:', err)
    }
  } catch (e) {
    console.warn('创建WS失败:', e)
  }
}

function startFrameSend() {
  setInterval(() => {
    if (ws && ws.readyState === WebSocket.OPEN && videoRef.value && canvasRef.value) {
      const canvas = canvasRef.value
      const ctx = canvas.getContext('2d')
      canvas.width = 160
      canvas.height = 120
      ctx.drawImage(videoRef.value, 0, 0, 160, 120)
      const imageData = canvas.toDataURL('image/jpeg', 0.6)
      try {
        ws.send(imageData)
      } catch (e) {
        // 忽略发送错误
      }
    }
  }, 3000)
}

function addViolation(type) {
  const key = type === 'no_face' ? 'no_face' :
    type === 'multi_face' ? 'multi_face' :
    type === 'look_away' ? 'look_away' :
    type === 'forbidden_item' ? 'forbidden_item' :
    type === 'keyboard' ? 'keyboard_violation' : null
  if (key && violationSummary[key] !== undefined) {
    violationSummary[key]++
    violationSummary.total++
  }
}

onMounted(() => {
  if (props.active) {
    startCamera()
  }
})

onBeforeUnmount(() => {
  stopCamera()
})
</script>

<style scoped>
.camera-monitor {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

.camera-container {
  position: relative;
  width: 140px;
  height: 105px;
  border-radius: 10px;
  overflow: hidden;
  background: #1a1d23;
  border: 2px solid transparent;
  transition: border-color 0.3s ease;
}

.camera-container.active {
  border-color: #67c23a;
}

.camera-container.error {
  border-color: #f56c6c;
}

.camera-video {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transform: scaleX(-1);
}

.camera-placeholder,
.camera-error {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: rgba(255, 255, 255, 0.5);
  font-size: 12px;
}

.camera-error {
  color: #f56c6c;
}

.monitor-status {
  position: absolute;
  bottom: 6px;
  left: 6px;
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 3px 8px;
  background: rgba(0, 0, 0, 0.6);
  border-radius: 10px;
  font-size: 11px;
  color: white;
}

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #67c23a;
}

.status-dot.pulse {
  animation: pulse 1s infinite;
  background: #f56c6c;
}

@keyframes pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.5; transform: scale(1.3); }
}

.status-text {
  font-size: 10px;
}

.violation-badge {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  background: #fef0f0;
  color: #f56c6c;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 500;
  animation: shake 0.5s ease-in-out;
}

@keyframes shake {
  0%, 100% { transform: translateX(0); }
  25% { transform: translateX(-2px); }
  75% { transform: translateX(2px); }
}

@media (max-width: 768px) {
  .camera-container {
    width: 100px;
    height: 75px;
  }
  .monitor-status {
    display: none;
  }
}
</style>
