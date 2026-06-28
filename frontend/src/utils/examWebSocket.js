/**
 * WebSocket 连接管理工具
 * 支持文本消息和二进制帧传输
 */
class ExamWebSocket {
  constructor() {
    this.ws = null
    this.heartbeatTimer = null
    this.reconnectTimer = null
    this.reconnectAttempts = 0
    this.maxReconnectAttempts = 10
    this.listeners = new Map()
    this.isConnecting = false
  }

  /**
   * 建立连接
   */
  connect(examId, userId, role) {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) return

    this.examId = examId
    this.userId = userId
    this.role = role
    this.isConnecting = true
    this.reconnectAttempts = 0 // 重置重连计数，确保新连接可以重试

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const host = window.location.host
    const token = localStorage.getItem('token')
    if (!token) {
      console.error('无法连接考试监控WS: 未找到登录Token')
      this.isConnecting = false
      return
    }
    const url = `${protocol}//${host}/api/ws/exam-monitor?token=${encodeURIComponent(token)}`

    try {
      this.ws = new WebSocket(url)
      this.ws.binaryType = 'arraybuffer'

      this.ws.onopen = () => {
        console.log('考试监控WS已连接')
        this.isConnecting = false
        this.reconnectAttempts = 0

        // 发送加入考试房间消息
        this.send({
          type: 'join_exam',
          examId,
          userId,
          role
        })

        // 启动心跳
        this.startHeartbeat()
        this.emit('connected')
      }

      this.ws.onmessage = (event) => {
        if (typeof event.data === 'string') {
          // 文本消息
          try {
            const data = JSON.parse(event.data)
            this.emit('message', data)
            if (data.type) {
              this.emit(data.type, data.content || data)
            }
          } catch (e) {
            console.warn('WS消息解析失败:', e)
          }
        } else if (event.data instanceof ArrayBuffer) {
          // 二进制帧（视频帧）
          this.emit('frame', event.data)
        }
      }

      this.ws.onclose = (event) => {
        console.log('考试监控WS已断开:', event.code, event.reason)
        this.isConnecting = false
        this.stopHeartbeat()
        this.emit('disconnected', event)

        // 自动重连
        if (this.reconnectAttempts < this.maxReconnectAttempts) {
          this.scheduleReconnect()
        }
      }

      this.ws.onerror = (error) => {
        console.error('考试监控WS错误:', error)
        this.isConnecting = false
        this.emit('error', error)
      }
    } catch (e) {
      console.error('WS连接创建失败:', e)
      this.isConnecting = false
      this.scheduleReconnect()
    }
  }

  /**
   * 发送文本消息
   */
  send(data) {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      this.ws.send(typeof data === 'string' ? data : JSON.stringify(data))
    }
  }

  /**
   * 发送视频帧（二进制）
   * 格式: [4字节examId(BigEndian)] + [4字节userId(BigEndian)] + [JPEG数据]
   */
  sendFrame(jpegBlob) {
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN) return

    const reader = new FileReader()
    reader.onload = () => {
      const jpegData = new Uint8Array(reader.result)
      const header = new ArrayBuffer(8)
      const view = new DataView(header)
      view.setInt32(0, this.examId)  // examId
      view.setInt32(4, this.userId)  // userId

      const frame = new Uint8Array(8 + jpegData.length)
      frame.set(new Uint8Array(header), 0)
      frame.set(jpegData, 8)

      this.ws.send(frame.buffer)
    }
    reader.readAsArrayBuffer(jpegBlob)
  }

  /**
   * 启动心跳
   */
  startHeartbeat() {
    this.stopHeartbeat()
    this.heartbeatTimer = setInterval(() => {
      this.send({
        type: 'heartbeat',
        examId: this.examId,
        userId: this.userId
      })
    }, 20000) // 每20秒发送一次心跳
  }

  /**
   * 停止心跳
   */
  stopHeartbeat() {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer)
      this.heartbeatTimer = null
    }
  }

  /**
   * 安排重连
   */
  scheduleReconnect() {
    if (this.reconnectTimer) return
    this.reconnectAttempts++
    const delay = Math.min(1000 * Math.pow(2, this.reconnectAttempts - 1), 30000)
    console.log(`WS将在${delay}ms后重连(第${this.reconnectAttempts}次)`)

    this.emit('reconnecting', { attempt: this.reconnectAttempts, delay })

    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null
      this.connect(this.examId, this.userId, this.role)
    }, delay)
  }

  /**
   * 注册事件监听
   */
  on(event, callback) {
    if (!this.listeners.has(event)) {
      this.listeners.set(event, new Set())
    }
    this.listeners.get(event).add(callback)

    // 返回取消监听函数
    return () => {
      const callbacks = this.listeners.get(event)
      if (callbacks) callbacks.delete(callback)
    }
  }

  /**
   * 触发事件
   */
  emit(event, data) {
    const callbacks = this.listeners.get(event)
    if (callbacks) {
      callbacks.forEach(cb => {
        try { cb(data) } catch (e) { console.error(`事件处理器错误[${event}]:`, e) }
      })
    }
  }

  /**
   * 断开连接
   */
  disconnect() {
    this.stopHeartbeat()
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer)
      this.reconnectTimer = null
    }
    this.reconnectAttempts = this.maxReconnectAttempts // 阻止自动重连
    if (this.ws) {
      this.ws.close(1000, '用户主动断开')
      this.ws = null
    }
    this.listeners.clear()
  }

  get isConnected() {
    return this.ws && this.ws.readyState === WebSocket.OPEN
  }
}

// 单例导出
const examWs = new ExamWebSocket()
export default examWs
