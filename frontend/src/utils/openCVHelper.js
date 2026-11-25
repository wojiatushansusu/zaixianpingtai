/**
 * OpenCV.js 工具类（精简版）
 * 
 * 职责划分：
 * - 人脸核验（StudentExams.vue）: 前端OpenCV.js本地人脸检测
 * - 考试中AI监考: 后端JavaCV AiAnalysisService处理
 * 
 * 使用方式:
 * 1. await OpenCVHelper.waitForLoad()  // 用到时才加载，不占首屏
 * 2. 调用 detectFaces() 进行人脸检测
 *
 * 加载策略：只从本地 /opencv/opencv.js 加载，**不回落到任何 CDN**。
 * 原先靠 index.html 里的 docs.opencv.org 全局脚本（10MB、境外），
 * 国内网络经常不通，而且每次打开页面都会白拉——即使根本不用人脸核验。
 * 现在没有本地文件就直接降级，把「一次下载 10MB」变成「一个 404」。
 */
class OpenCVHelper {
  constructor() {
    this.loaded = false
    this.faceClassifier = null
    this.loading = false
  }

  /**
   * 等待OpenCV.js加载完成
   */
  async waitForLoad() {
    if (this.loaded) return this.loaded
    // 多处同时调用时只加载一次，避免重复注入 10MB 脚本
    if (!this._loadPromise) {
      this._loadPromise = this.doLoad()
    }
    return this._loadPromise
  }

  async doLoad() {
    if (typeof cv === 'undefined') {
      const ok = await this.injectLocalScript()
      if (!ok) {
        console.warn('OpenCV.js 未自托管（/opencv/opencv.js 缺失），人脸检测降级')
        this.loaded = false
        return false
      }
    }

    // 脚本 load 事件不代表 wasm 运行时已初始化，还要等到 cv 上出现可用的构造函数
    const ready = await this.waitUntil(() => typeof cv !== 'undefined' && !!cv.CascadeClassifier, 10000)
    if (!ready) {
      console.warn('OpenCV.js 运行时初始化超时，人脸检测降级')
      this.loaded = false
      return false
    }

    try {
      await this.initFaceClassifier()
    } catch (e) {
      console.warn('OpenCV 分类器初始化失败:', e)
    }

    this.loaded = true
    return true
  }

  /** 注入本地脚本；404 会立即触发 onerror，不会像 CDN 那样干等 */
  injectLocalScript() {
    return new Promise((resolve) => {
      const script = document.createElement('script')
      script.src = '/opencv/opencv.js'
      script.async = true

      let settled = false
      const finish = (ok) => {
        if (settled) return
        settled = true
        resolve(ok)
      }

      script.onload = () => finish(true)
      script.onerror = () => finish(false)
      document.head.appendChild(script)

      // 兜底：本地文件很大时加载可能很慢，给 20 秒
      setTimeout(() => finish(false), 20000)
    })
  }

  waitUntil(check, timeoutMs) {
    return new Promise((resolve) => {
      const start = Date.now()
      const timer = setInterval(() => {
        if (check()) {
          clearInterval(timer)
          resolve(true)
        } else if (Date.now() - start > timeoutMs) {
          clearInterval(timer)
          resolve(false)
        }
      }, 100)
    })
  }

  /**
   * 初始化人脸分类器
   */
  async initFaceClassifier() {
    try {
      this.faceClassifier = new cv.CascadeClassifier()

      const faceCascadeUrls = [
        '/haarcascade_frontalface_alt2.xml',
        'https://gitee.com/mirrors/opencv/raw/4.x/data/haarcascades/haarcascade_frontalface_alt2.xml',
        'https://raw.githubusercontent.com/opencv/opencv/master/data/haarcascades/haarcascade_frontalface_alt2.xml'
      ]

      for (const url of faceCascadeUrls) {
        try {
          const faceFile = await this.fetchCascadeFile(url)
          if (faceFile) {
            this.faceClassifier.load(faceFile)
            if (!this.faceClassifier.empty()) {
              console.log('人脸分类器加载成功:', url)
              break
            }
          }
        } catch (e) { /* try next */ }
      }

      if (this.faceClassifier.empty()) {
        console.warn('人脸分类器加载失败')
      }
    } catch (e) {
      console.warn('OpenCV分类器初始化失败:', e)
    }
  }

  /**
   * 获取级联分类器文件
   */
  async fetchCascadeFile(url) {
    try {
      const response = await fetch(url)
      if (!response.ok) return null
      const buf = await response.arrayBuffer()
      const data = new Uint8Array(buf)
      if (data.length < 1000) return null
      return new cv.File(data)
    } catch (e) {
      return null
    }
  }

  /**
   * 人脸检测（从video/canvas元素）
   * 支持多尺度检测：如果标准尺度未检测到人脸，自动降低最小尺寸重试
   * @returns {DetectionResult} { detected, faceCount, faces[], primaryFace }
   */
  detectFaces(source, width, height) {
    if (!this.loaded || typeof cv === 'undefined') {
      return { detected: false, faceCount: 0, faces: [], primaryFace: null }
    }

    try {
      let src
      if (source instanceof HTMLVideoElement || source instanceof HTMLCanvasElement) {
        src = cv.imread(source)
      } else if (source instanceof ImageData) {
        src = cv.matFromImageData(source)
      } else {
        return { detected: false, faceCount: 0, faces: [], primaryFace: null }
      }

      const gray = new cv.Mat()
      cv.cvtColor(src, gray, cv.COLOR_RGBA2GRAY)
      cv.equalizeHist(gray, gray)

      // 多尺度检测策略：
      // 第1次：标准尺度 (minSize=30)，覆盖远距离小脸
      // 第2次：更小尺度 (minSize=15)，覆盖极远距离/侧脸
      const scales = [
        { minSize: new cv.Size(30, 30), scaleFactor: 1.05, neighbors: 3 },
        { minSize: new cv.Size(15, 15), scaleFactor: 1.05, neighbors: 2 }
      ]

      let bestFaces = new cv.RectVector()

      if (this.faceClassifier && !this.faceClassifier.empty()) {
        for (let i = 0; i < scales.length; i++) {
          const scale = scales[i]
          const faces = new cv.RectVector()
          this.faceClassifier.detectMultiScale(gray, faces, scale.scaleFactor, scale.neighbors, 0, scale.minSize, new cv.Size(0, 0))

          // 第1次检测到了就直接用，否则尝试更小尺度
          if (faces.size() > 0) {
            bestFaces = faces
            break
          } else {
            faces.delete()
          }
        }
      }

      const result = {
        detected: bestFaces.size() > 0,
        faceCount: bestFaces.size(),
        faces: [],
        primaryFace: null
      }

      const frameWidth = src.cols
      const centerX = frameWidth / 2

      for (let i = 0; i < bestFaces.size(); i++) {
        const face = bestFaces.get(i)
        const faceObj = {
          x: face.x,
          y: face.y,
          width: face.width,
          height: face.height,
          centerX: face.x + face.width / 2,
          centerY: face.y + face.height / 2
        }
        result.faces.push(faceObj)

        if (!result.primaryFace ||
            Math.abs(faceObj.centerX - centerX) < Math.abs(result.primaryFace.centerX - centerX)) {
          result.primaryFace = faceObj
        }
      }

      gray.delete()
      bestFaces.delete()
      src.delete()

      return result
    } catch (e) {
      console.warn('人脸检测异常:', e)
      return { detected: false, faceCount: 0, faces: [], primaryFace: null }
    }
  }

  isLoaded() {
    return this.loaded && typeof cv !== 'undefined'
  }
}

// 单例导出
const openCVHelper = new OpenCVHelper()
export default openCVHelper
