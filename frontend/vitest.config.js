import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import path from 'path'
import fs from 'fs'

/**
 * 单测专用配置。
 *
 * 单独一份而不是复用 vite.config.js，原因有三个：
 * 1. 只跑 `src/**\/__tests__/**\/*.test.js`，避免把将来的 e2e 文件误当作单测
 * 2. 缓存目录固定在项目内——默认写系统临时目录，在受限环境（沙箱、容器）里
 *    会因 EPERM 直接让整个测试进程以退出码 1 结束
 * 3. 同理把 OS 临时目录也重定向到项目内：vite-node 的 ssr 缓存由 os.tmpdir() 决定，
 *    不受 cacheDir 控制，只改后者仍然会失败
 */

const localTmp = path.resolve(__dirname, 'node_modules/.vitest-tmp')
fs.mkdirSync(localTmp, { recursive: true })

// worker 子进程会继承这些环境变量
process.env.TMPDIR = localTmp
process.env.TMP = localTmp
process.env.TEMP = localTmp

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src')
    }
  },
  cacheDir: 'node_modules/.vite-test',
  test: {
    environment: 'node',
    cache: { dir: 'node_modules/.vitest-cache' },
    include: ['src/**/__tests__/**/*.test.js'],
    reporters: 'default'
  }
})
