import { spawnSync } from 'node:child_process'
import path from 'node:path'
import fs from 'node:fs'
import { fileURLToPath } from 'node:url'

/**
 * 测试入口包装。
 *
 * 为什么需要这一层：vitest 会把 SSR 模块缓存写到 `os.tmpdir()`，
 * 而受限环境（沙箱、部分容器）对该目录的写入会 EPERM，导致整个测试进程
 * 以退出码 1 结束——更麻烦的是它**每次跑到的文件数不一样**，
 * 少跑一个文件时输出看起来仍然是「passed」，很容易被当成通过。
 *
 * 在 vitest.config.js 里设置 process.env 已经来不及（部分路径在配置求值前就确定了），
 * 所以改成在**启动子进程之前**把临时目录指向项目内，worker 会继承这个环境。
 */

const here = path.dirname(fileURLToPath(import.meta.url))
const root = path.resolve(here, '..')
const tmpDir = path.join(root, 'node_modules', '.vitest-tmp')

fs.mkdirSync(tmpDir, { recursive: true })

const vitestBin = path.join(root, 'node_modules', 'vitest', 'vitest.mjs')

const extra = process.argv.slice(2)
// watch 模式不能带 `run` 子命令，否则 vitest 会当作一次性执行后直接退出
const args = extra.includes('--watch') ? [vitestBin, ...extra] : [vitestBin, 'run', ...extra]

const result = spawnSync(process.execPath, args, {
  cwd: root,
  stdio: 'inherit',
  env: {
    ...process.env,
    TMPDIR: tmpDir,
    TMP: tmpDir,
    TEMP: tmpDir
  }
})

process.exit(result.status === null ? 1 : result.status)
