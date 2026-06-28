const MAX_ERRORS = 100
const errorLogs = []

export function enableErrorMonitoring() {
  window.addEventListener('error', handleError)
  window.addEventListener('unhandledrejection', handleUnhandledRejection)
  window.onerror = handleOnError
}

export function disableErrorMonitoring() {
  window.removeEventListener('error', handleError)
  window.removeEventListener('unhandledrejection', handleUnhandledRejection)
  window.onerror = null
}

function handleError(event) {
  const errorInfo = {
    type: 'error',
    message: event.message || '未知错误',
    filename: event.filename || '',
    lineno: event.lineno || 0,
    colno: event.colno || 0,
    stack: event.error?.stack || '',
    time: new Date().toISOString(),
    url: window.location.href
  }
  pushError(errorInfo)
}

function handleOnError(message, source, lineno, colno, error) {
  const errorInfo = {
    type: 'onerror',
    message: message || '未知错误',
    filename: source || '',
    lineno: lineno || 0,
    colno: colno || 0,
    stack: error?.stack || '',
    time: new Date().toISOString(),
    url: window.location.href
  }
  pushError(errorInfo)
  return false
}

function handleUnhandledRejection(event) {
  const errorInfo = {
    type: 'unhandledrejection',
    message: event.reason?.message || String(event.reason) || '未处理的Promise异常',
    stack: event.reason?.stack || '',
    time: new Date().toISOString(),
    url: window.location.href
  }
  pushError(errorInfo)
}

function pushError(errorInfo) {
  if (errorLogs.length >= MAX_ERRORS) {
    errorLogs.shift()
  }
  errorLogs.push(errorInfo)
  console.error('[ErrorMonitor]', errorInfo)
}

export function getErrorLogs() {
  return [...errorLogs]
}

export function clearErrorLogs() {
  errorLogs.length = 0
}
