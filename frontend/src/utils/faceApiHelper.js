/**
 * face-api.js 封装工具
 * 
 * 功能：
 * 1. 懒加载 face-api.js + 模型（CDN动态加载，浏览器缓存）
 * 2. 实时面部关键点检测（68点landmark）
 * 3. 动作活体检测：眨眼 / 张嘴 / 转头
 * 4. 人脸特征提取与匹配比对
 * 
 * 设计原则：
 * - 所有检测在浏览器本地完成，不依赖后端AI
 * - 模型从CDN加载，首次约9MB，之后浏览器缓存
 * - 加载失败时优雅降级（跳过活体检测，直接进入拍照流程）
 */

// ─── 状态 ───
let _loaded = false
let _loading = false
let _loadPromise = null
let _error = null

// 面部检测器与人脸识别器引用
let _optionsSSDMobileNet = null
// 低置信度备选检测器（用于远距离/小脸场景）
let _optionsLowConfidence = null

// ─── 配置常量 ───
const CONFIG = {
  // 模型加载地址（jsdelivr CDN，国内可访问，有缓存）
  MODEL_URL: 'https://cdn.jsdelivr.net/npm/@vladmandic/face-api@1.7.12/model',
  
  // 输入尺寸（越大精度越高但速度更慢，320→416提升远距离小脸检测率）
  INPUT_SIZE: 416,
  
  // 检测置信度阈值（0.5→0.3，大幅降低门槛，减少"有人脸但检测不到"的情况）
  MIN_DETECTION_CONFIDENCE: 0.3,
  
  // ── 动作检测阈值（基于大量实测调优）──
  
  // 眨眼：EAR(Eye Aspect Ratio) < 此值视为闭眼
  // 正常睁眼 EAR ≈ 0.25~0.35, 闭眼 EAR ≈ 0.01~0.10
  BLINK_EAR_THRESHOLD: 0.18,
  // 连续多少帧闭眼才算一次有效眨眼（防误检）
  BLINK_CONSECUTIVE_FRAMES: 2,
  
  // 张嘴：MAR(Mouth Aspect Ratio) > 此值视为张嘴
  // 正常闭嘴 MAR ≈ 0.30~0.45, 张嘴 MAR ≈ 0.60~0.90
  MOUTH_MAR_THRESHOLD: 0.55,
  // 连续多少帧张嘴才算有效
  MOUTH_CONSECUTIVE_FRAMES: 3,
  
  // 转头：Yaw角度（度）超过此值视为转头
  TURN_YAW_THRESHOLD: 15,
  // 连续多少帧转向才算有效
  TURN_CONSECUTIVE_FRAMES: 3,
  
  // 动作超时（毫秒）
  ACTION_TIMEOUT_MS: 10000,
  
  // 人脸匹配距离阈值（欧氏距离，越小越相似）
  // < 0.6: 同一人;  0.6~0.8: 可能同一人;  > 0.8: 不同人
  MATCH_DISTANCE_THRESHOLD: 0.6
}

// ─── 公共 API ───

/**
 * 等待 face-api.js 和模型加载完成
 * @returns {Promise<boolean>} 是否加载成功
 */
async function waitForLoad() {
  if (_loaded) return true
  if (_error) return false
  if (_loading) return _loadPromise
  
  _loading = true
  _loadPromise = _doLoad()
  try {
    const ok = await _loadPromise
    _loaded = ok
    return ok
  } catch (e) {
    _error = e
    _loading = false
    return false
  }
}

/** 是否已加载完成 */
function isLoaded() {
  return _loaded
}

/**
 * 从视频/图片元素检测人脸并返回完整结果
 * 支持多尺度检测：默认检测失败时自动降低置信度阈值重试
 * @param {HTMLVideoElement|HTMLImageElement|HTMLCanvasElement} sourceEl 
 * @param {boolean} withLandmarks 是否计算68点landmark（默认true）
 * @param {boolean} withDescriptors 是否提取128维特征向量（用于身份比对，默认false）
 * @returns {Promise<FaceDetectionResult>}
 */
async function detectFaces(sourceEl, { withLandmarks = true, withDescriptors = false } = {}) {
  if (!_loaded || !_optionsSSDMobileNet) {
    return { detected: false, faces: [], error: 'not_loaded' }
  }

  try {
    // 尝试1: 使用标准检测器
    let detections = await _detectWithOptions(sourceEl, _optionsSSDMobileNet, withLandmarks, withDescriptors)

    // 尝试2: 如果没检测到，使用低置信度检测器重试（应对远距离/侧脸/光线差）
    if ((!detections || detections.length === 0) && _optionsLowConfidence) {
      detections = await _detectWithOptions(sourceEl, _optionsLowConfidence, withLandmarks, withDescriptors)
    }

    if (!detections || detections.length === 0) {
      return { detected: false, faces: [] }
    }

    // 选择最大的人脸作为主要人脸（考试场景通常只有一个人）
    const primary = detections.reduce((a, b) => 
      (a.box.width * a.box.height) > (b.box.width * b.box.height) ? a : b
    )

    const faces = detections.map(d => ({
      x: Math.round(d.box.x),
      y: Math.round(d.box.y),
      width: Math.round(d.box.width),
      height: Math.round(d.box.height),
      confidence: d.score,
      landmarks: d.landmarks ? extractLandmarkPoints(d.landmarks) : null,
      descriptor: d.descriptor ? Array.from(d.descriptor) : null,
      angles: d.angle ? { pitch: d.angle.pitch, yaw: d.angle.yaw, roll: d.angle.roll } : null
    }))

    return {
      detected: true,
      faces,
      primaryFace: faces.find(f => f === faces[faces.indexOf(primary)]) || faces[0],
      rawDetections: detections // 保留原始检测结果供动作检测用
    }
  } catch (e) {
    return { detected: false, faces: [], error: e.message }
  }
}

/**
 * 使用指定的检测选项执行检测
 */
async function _detectWithOptions(sourceEl, options, withLandmarks, withDescriptors) {
  let detections
  if (withDescriptors) {
    detections = await window.faceapi
      .detectAllFaces(sourceEl, options)
      .withFaceLandmarks()
      .withFaceDescriptors()
  } else if (withLandmarks) {
    detections = await window.faceapi
      .detectAllFaces(sourceEl, options)
      .withFaceLandmarks()
  } else {
    detections = await window.faceapi
      .detectAllFaces(sourceEl, options)
  }
  return detections
}

/**
 * 检测人脸+关键点+特征向量（用于身份匹配场景）
 */
async function _detectAllWithLandmarksAndDescriptors(source, options) {
  const opts = options || _optionsSSDMobileNet
  return window.faceapi
    .detectAllFaces(source, opts)
    .withFaceLandmarks()
    .withFaceDescriptors()
}

/**
 * 提取68个关键点的坐标数组
 */
function extractLandmarkPoints(landmarks) {
  if (!landmarks) return null
  const positions = landmarks.positions || landmarks.getPositions?.()
  if (!positions) return null
  return Array.from(positions).map(p => ({ x: p.x, y: p.y }))
}

// ─── 动作活体检测 API ───

/**
 * 创建一个动作检测控制器
 * 用于在视频流上持续监测特定动作是否完成
 * 
 * @param {HTMLVideoElement} videoEl 视频元素
 * @param {{ intervalMs?: number }} options 检测间隔（默认200ms）
 * @returns {ActionDetector}
 */
function createActionDetector(videoEl, { intervalMs = 200 } = {}) {
  return new ActionDetector(videoEl, intervalMs)
}

/**
 * 动作检测器类
 * 在视频帧上持续运行，检测眨眼/张嘴/转头动作
 */
class ActionDetector {
  constructor(videoEl, intervalMs) {
    this._video = videoEl
    this._intervalMs = intervalMs
    this._timer = null
    this._running = false
    // 各动作的连续计数器
    this._counters = { blink: 0, mouthOpen: 0, turnLeft: 0, turnRight: 0, turnUp: 0, turnDown: 0 }
    // 回调
    this.onProgress = null   // (action, progress) => void  进度回调
    this.onCompleted = null  // (action, data) => void     完成回调
    this.onError = null      // (err) => void              错误回调
    // 当前正在检测的动作列表
    this._activeActions = new Set()
  }

  /**
   * 开始检测指定动作
   * @param {string[]} actions 要检测的动作列表, 如 ['blink', 'mouthOpen', 'turnLeft']
   * @returns {Promise<Map<string, boolean>>} 各动作是否完成的 Map
   */
  async start(actions) {
    if (!_loaded) throw new Error('face-api not loaded')
    
    this._activeActions = new Set(actions)
    this._resetCounters()
    this._running = true
    
    return new Promise((resolve) => {
      this._resolve = resolve
      this._completedActions = new Set()
      
      // 启动定时检测
      this._timer = setInterval(() => this._tick(), this._intervalMs)
      
      // 超时保护
      this._timeoutTimer = setTimeout(() => {
        this.stop()
        resolve(this._completedActions)
      }, CONFIG.ACTION_TIMEOUT_MS * actions.length + 5000)
    })
  }

  /** 停止检测 */
  stop() {
    this._running = false
    if (this._timer) { clearInterval(this._timer); this._timer = null }
    if (this._timeoutTimer) { clearTimeout(this._timeoutTimer); this._timeoutTimer = null }
  }

  _resetCounters() {
    this._counters = { blink: 0, mouthOpen: 0, turnLeft: 0, turnRight: 0, turnUp: 0, turnDown: 0 }
  }

  async _tick() {
    if (!this._running || !this._video) return

    try {
      const result = await detectFaces(this._video, { withLandmarks: true })
      if (!result.detected || !result.rawDetections || result.rawDetections.length === 0) {
        this._resetCounters()
        return
      }

      const detection = result.primaryFace 
        ? result.rawDetections.find(d => 
            Math.round(d.box.x) === result.primaryFace.x && Math.round(d.box.y) === result.primaryFace.y)
        : result.rawDetections[0]

      if (!detection || !detection.landmarks) return

      const landmarks = detection.landmarks
      const angles = detection.angle || {}

      // ── 逐个检测活跃动作 ──
      
      // 1. 眨眼检测
      if (this._activeActions.has('blink')) {
        const ear = calcEAR(landmarks)
        const isBlinking = ear < CONFIG.BLINK_EAR_THRESHOLD
        
        if (isBlinking) {
          this._counters.blink++
          const progress = Math.min(100, (this._counters.blink / CONFIG.BLINK_CONSECUTIVE_FRAMES) * 100)
          this.onProgress?.('blink', progress)
          
          if (this._counters.blink >= CONFIG.BLINK_CONSECUTIVE_FRAMES && !this._completedActions.has('blink')) {
            this._completedActions.add('blink')
            this.onCompleted?.('blink', { ear })
          }
        } else {
          this._counters.blink = Math.max(0, this._counters.blink - 1)
        }
      }

      // 2. 张嘴检测
      if (this._activeActions.has('mouthOpen')) {
        const mar = calcMAR(landmarks)
        const isMouthOpen = mar > CONFIG.MOUTH_MAR_THRESHOLD
        
        if (isMouthOpen) {
          this._counters.mouthOpen++
          const progress = Math.min(100, (this._counters.mouthOpen / CONFIG.MOUTH_CONSECUTIVE_FRAMES) * 100)
          this.onProgress?.('mouthOpen', progress)
          
          if (this._counters.mouthOpen >= CONFIG.MOUTH_CONSECUTIVE_FRAMES && !this._completedActions.has('mouthOpen')) {
            this._completedActions.add('mouthOpen')
            this.onCompleted?.('mouthOpen', { mar })
          }
        } else {
          this._counters.mouthOpen = Math.max(0, this._counters.mouthOpen - 1)
        }
      }

      // 3. 转头检测（左/右/上/下）
      for (const dir of ['turnLeft', 'turnRight', 'turnUp', 'turnDown']) {
        if (!this._activeActions.has(dir)) continue
        
        let triggered = false
        if (dir === 'turnLeft' && angles.yaw !== undefined && angles.yaw < -CONFIG.TURN_YAW_THRESHOLD) triggered = true
        if (dir === 'turnRight' && angles.yaw !== undefined && angles.yaw > CONFIG.TURN_YAW_THRESHOLD) triggered = true
        if (dir === 'turnUp' && angles.pitch !== undefined && angles.pitch < -CONFIG.TURN_YAW_THRESHOLD) triggered = true
        if (dir === 'turnDown' && angles.pitch !== undefined && angles.pitch > CONFIG.TURN_YAW_THRESHOLD) triggered = true
        
        if (triggered) {
          this._counters[dir]++
          const progress = Math.min(100, (this._counters[dir] / CONFIG.TURN_CONSECUTIVE_FRAMES) * 100)
          this.onProgress?.(dir, progress)
          
          if (this._counters[dir] >= CONFIG.TURN_CONSECUTIVE_FRAMES && !this._completedActions.has(dir)) {
            this._completedActions.add(dir)
            this.onCompleted?.(dir, { yaw: angles.yaw, pitch: angles.pitch })
          }
        } else {
          this._counters[dir] = Math.max(0, this._counters[dir] - 1)
        }
      }

      // 检查是否所有动作都已完成
      if (this._completedActions.size >= this._activeActions.size) {
        this.stop()
        this._resolve?.(this._completedActions)
      }

    } catch (e) {
      this.onError?.(e)
    }
  }
}

// ─── 人脸特征匹配 API ───

/**
 * 计算两个人脸描述符之间的欧氏距离
 * @param {number[]|Float32Array} desc1 描述符1
 * @param {number[]|Float32Array} desc2 描述符2
 * @returns {number} 欧氏距离 (0=完全相同, >1=不同人)
 */
function descriptorDistance(desc1, desc2) {
  if (!desc1 || !desc2 || desc1.length !== desc2.length) return Infinity
  let sum = 0
  for (let i = 0; i < desc1.length; i++) {
    const diff = desc1[i] - desc2[i]
    sum += diff * diff
  }
  return Math.sqrt(sum)
}

/**
 * 匹配两张照片的人脸相似度
 * @param {string|HTMLImageElement} photo1UrlOrImg 注册照片URL或Image元素
 * @param {HTMLVideoElement|HTMLImageElement} currentSource 当前摄像头画面
 * @returns {Promise<{ matched: boolean, distance: number, similarity: number }>}
 */
async function matchFace(photo1UrlOrImg, currentSource) {
  if (!_loaded) return { matched: false, distance: Infinity, similarity: 0, error: 'not_loaded' }

  try {
    // 加载注册照
    let img1
    if (typeof photo1UrlOrImg === 'string') {
      img1 = await faceapi.bufferToImage(await (await fetch(photo1UrlOrImg)).arrayBuffer())
    } else {
      img1 = photo1UrlOrImg
    }

    // 检测两张图的人脸+特征（先标准检测，失败用低置信度检测器）
    let [det1, det2] = await Promise.all([
      _detectAllWithLandmarksAndDescriptors(img1),
      _detectAllWithLandmarksAndDescriptors(currentSource)
    ])

    // 如果任一图没检测到人脸，用低置信度检测器重试
    if ((!det1.length || !det2.length) && _optionsLowConfidence) {
      const [lowDet1, lowDet2] = await Promise.all([
        det1.length ? Promise.resolve(det1) : _detectAllWithLandmarksAndDescriptors(img1, _optionsLowConfidence),
        det2.length ? Promise.resolve(det2) : _detectAllWithLandmarksAndDescriptors(currentSource, _optionsLowConfidence)
      ])
      if (lowDet1.length) det1 = lowDet1
      if (lowDet2.length) det2 = lowDet2
    }

    if (!det1.length || !det2.length) {
      return { matched: false, distance: Infinity, similarity: 0, error: 'no_face' }
    }

    // 取各自最大的脸
    const face1 = det1.reduce((a, b) => (a.box.area > b.box.area) ? a : b)
    const face2 = det2.reduce((a, b) => (a.box.area > b.box.area) ? a : b)

    const dist = descriptorDistance(face1.descriptor, face2.descriptor)
    // 将距离转换为相似度百分比 (0.0~1.0)
    const similarity = Math.max(0, 1 - dist)

    return {
      matched: dist <= CONFIG.MATCH_DISTANCE_THRESHOLD,
      distance: parseFloat(dist.toFixed(4)),
      similarity: parseFloat(similarity.toFixed(4))
    }
  } catch (e) {
    return { matched: false, distance: Infinity, similarity: 0, error: e.message }
  }
}

// ─── 内部：模型加载 ───

async function _doLoad() {
  try {
    // 动态导入 face-api.js
    if (!window.faceapi) {
      // 尝试从 node_modules 导入
      const faceapiModule = await import('face-api.js')
      if (faceapiModule.default || faceapiModule.faceapi) {
        window.faceapi = faceapiModule.default || faceapiModule.faceapi || faceapiModule
      }
      
      if (!window.faceapi) {
        throw new Error('face-api.js module loaded but no global object found')
      }
    }

    // 使用 jsdelivr CDN（国内友好）
    const modelPath = CONFIG.MODEL_URL
    
    await Promise.all([
      window.faceapi.nets.ssdMobilenetv1.loadFromUri(modelPath),
      window.faceapi.nets.faceLandmark68Net.loadFromUri(modelPath),
      window.faceapi.nets.faceRecognitionNet.loadFromUri(modelPath)
    ])

    // 创建检测选项（标准+低置信度备选）
    _optionsSSDMobileNet = new window.faceapi.SsdMobilenetv1Options({
      minConfidence: CONFIG.MIN_DETECTION_CONFIDENCE,
      inputSize: CONFIG.INPUT_SIZE
    })

    // 低置信度备选检测器：阈值降到0.15，输入尺寸增大到608（提升远距离小脸检测）
    _optionsLowConfidence = new window.faceapi.SsdMobilenetv1Options({
      minConfidence: 0.15,
      inputSize: 608
    })

    return true

  } catch (e) {
    return false
  }
}

// ─── 几何计算函数 ───

/**
 * 计算 Eye Aspect Ratio (眼睛长宽比)
 * 基于眼睛的6个关键点，EAR值越小表示眼睛闭合程度越高
 * 参考: Soukupová & Čech, "Real-Time Eye Blink Detection using Facial Landmarks", 2016
 * 
 * face-api.js 的 68 点索引:
 *   左眼: 37-42 (37=左角, 38=上1, 39=上2, 40=右角, 41=下2, 42=下1)
 *   右眼: 43-48 (43=左角, 44=上1, 45=上2, 46=右角, 47=下2, 48=下1)
 */
function calcEAR(landmarks) {
  try {
    const positions = landmarks.positions || landmarks.getPositions?.()
    if (!positions || positions.length < 49) return 0.3 // 默认睁眼值

    function eyeEAR(startIdx) {
      // 垂直方向：上下两点之间的距离取两对平均
      const p = positions
      const v1 = Math.sqrt(Math.pow(p[startIdx + 1].x - p[startIdx + 5].x, 2) + Math.pow(p[startIdx + 1].y - p[startIdx + 5].y, 2))
      const v2 = Math.sqrt(Math.pow(p[startIdx + 2].x - p[startIdx + 4].x, 2) + Math.pow(p[startIdx + 2].y - p[startIdx + 4].y, 2))
      // 水平方向：左右两点的距离
      const h = Math.sqrt(Math.pow(p[startIdx].x - p[startIdx + 3].x, 2) + Math.pow(p[startIdx].y - p[startIdx + 3].y, 2))
      
      if (h === 0) return 0.3
      return (v1 + v2) / (2.0 * h)
    }

    // 左眼(起始点37) + 右眼(起始点43)，取平均值
    const leftEAR = eyeEAR(37)
    const rightEAR = eyeEAR(43)
    
    return (leftEAR + rightEAR) / 2
  } catch (e) {
    return 0.3
  }
}

/**
 * 计算 Mouth Aspect Ratio (嘴巴长宽比)
 * 基于12个嘴唇关键点，MAR越大表示嘴巴张开程度越高
 * 
 * face-api.js 68点索引:
 *   外嘴唇: 48-59 (顺时针), 内嘴唇: 60-67
 */
function calcMAR(landmarks) {
  try {
    const positions = landmarks.positions || landmarks.getPositions?.()
    if (!positions || positions.length < 68) return 0.35 // 默认闭嘴值

    const p = positions
    
    // 垂直距离：嘴唇中心上下两点 (内嘴唇上沿62, 下沿66)
    const v1 = Math.sqrt(Math.pow(p[62].x - p[66].x, 2) + Math.pow(p[62].y - p[66].y, 2))
    const v2 = Math.sqrt(Math.pow(p[60].x - p[64].x, 2) + Math.pow(p[60].y - p[64].y, 2))
    
    // 水平距离：嘴唇左右两点 (内嘴唇左61, 右63)
    const h = Math.sqrt(Math.pow(p[61].x - p[63].x, 2) + Math.pow(p[61].y - p[63].y, 2))
    
    if (h === 0) return 0.35
    return (v1 + v2) / (2.0 * h)
  } catch (e) {
    return 0.35
  }
}

// ─── 导出 ───
export default {
  waitForLoad,
  isLoaded,
  detectFaces,
  createActionDetector,
  matchFace,
  descriptorDistance,
  // 暴露配置供外部调整（可选）
  CONFIG
}
