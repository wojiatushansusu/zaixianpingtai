/**
 * OpenCV.js 工具类（精简版）
 * 
 * 职责划分：
 * - 人脸核验（StudentExams.vue）: 前端OpenCV.js本地人脸检测
 * - 考试中AI监考: 后端JavaCV AiAnalysisService处理
 * 
 * 使用方式:
 * 1. index.html 中已配置 <script async src="https://docs.opencv.org/4.9.0/opencv.js">
 * 2. 等待OpenCV加载完成: await OpenCVHelper.waitForLoad()
 * 3. 调用 detectFaces() 进行人脸检测
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
    if (this.loaded) return true

    return new Promise((resolve) => {
      if (typeof cv !== 'undefined') {
        this.initFaceClassifier().then(() => {
          this.loaded = true
          resolve(true)
        }).catch(() => {
          this.loaded = true
          resolve(true)
        })
        return
      }

      let attempts = 0
      const maxAttempts = 150 // 15秒超时
      const interval = setInterval(() => {
        attempts++
        if (typeof cv !== 'undefined') {
          clearInterval(interval)
          this.initFaceClassifier().then(() => {
            this.loaded = true
            resolve(true)
          })
        } else if (attempts >= maxAttempts) {
          clearInterval(interval)
          console.warn('OpenCV.js 加载超时，使用降级模式')
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
