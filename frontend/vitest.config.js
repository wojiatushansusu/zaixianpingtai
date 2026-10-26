import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import path from 'path'

/**
 * 单测专用配置。
 *
 * 单独一份而不是复用 vite.config.js，原因有两个：
 * 1. 只跑 `src/**\/__tests__/**\/*.test.js`，避免把将来的 e2e 文件误当作单测
 * 2. 把缓存目录固定在项目内——默认会写到系统临时目录，
 *    在某些受限环境（CI 容器、沙箱）里会因 EPERM 直接中断测试
 */
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
    include: ['src/**/__tests__/**/*.test.js'],
    reporters: 'default'
  }
})
