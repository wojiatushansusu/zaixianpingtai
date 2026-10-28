<template>
  <div class="review-path">
    <div class="page-header">
      <div>
        <h2>复习路径</h2>
        <p class="subtitle">安徽专升本《高等数学》· 七个阶段，从初中地基到真题冲刺</p>
      </div>
    </div>

    <el-alert type="info" :closable="false" class="intro">
      每个阶段末尾都有验收标准。<strong>过不了验收就别进下一阶段</strong>——尤其阶段 1 到阶段 2 那道坎。
      拿不准的话，点「测这一阶段」让 AI 只针对本阶段考点出一套题，做完再决定往哪走。
    </el-alert>

    <div class="stage-list">
      <div
        v-for="(stage, index) in stages"
        :key="stage.key"
        class="stage"
        :class="`tone-${stage.color || 'default'}`"
      >
        <div class="rail">
          <span class="dot">{{ index }}</span>
          <span class="line" v-if="index < stages.length - 1"></span>
        </div>

        <div class="card">
          <div class="card-head">
            <span class="tag">{{ stage.tag }}</span>
            <h3>{{ stage.title }}</h3>
            <el-tag :type="progressTagType(stage.progress)" size="small" effect="light" class="status-tag">
              {{ describeProgress(stage.progress) }}
            </el-tag>
            <span class="weeks">{{ stage.weeks }}</span>
          </div>

          <p class="src">
            <b>平台</b> {{ stage.source }}
            <template v-if="stage.method">　<b>方式</b> {{ stage.method }}</template>
          </p>

          <p v-if="stage.prerequisite.unmet" class="gate-hint">
            <el-icon><InfoFilled /></el-icon>
            {{ describePrerequisite(stage.prerequisite) }}
          </p>

          <ul class="items">
            <li v-for="item in stage.items" :key="item.name">
              <b>{{ item.name }}</b>
              <span v-if="item.span" class="span">{{ item.span }}</span>
              <span class="note">{{ item.note }}</span>
            </li>
          </ul>

          <div v-if="stage.acceptance.length" class="acceptance">
            <div class="acceptance-title">验收标准</div>
            <ol>
              <li v-for="(text, ai) in stage.acceptance" :key="ai">{{ text }}</li>
            </ol>
          </div>

          <div class="actions">
            <el-button
              type="primary"
              :loading="generating === stage.key"
              @click="handleTestStage(stage)"
            >
              <el-icon><MagicStick /></el-icon>
              {{ stage.progress.state === STAGE_STATE.NOT_TESTED ? '测这一阶段' : '再测一次' }}
            </el-button>
            <span class="hint">只考本阶段考点，10 道客观题，约 1~2 分钟生成</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
/**
 * 复习路径页。
 *
 * 内容来自《安徽专升本〈高等数学〉从初中到高数的完整复习路径》的阶段路线，
 * 但本页不是把它静态搬过来——每个阶段都能「就地出一套只考本阶段的题」。
 *
 * 为什么这么做：路径本身不稀奇，网上到处都是。这里独有的一点是，
 * 平台能真的出题、真的判分，所以「你过没过这个阶段」可以被验证，而不是靠自我感觉。
 *
 * 当前版本只做了「按阶段出卷」。把考试结果回填成阶段状态（通过/未通过）
 * 需要把 exam 与 stage 关联起来，属于下一步。
 */
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { reviewStages, buildStagePrompt } from '@/data/reviewPath'
import { createSelfStudyExam, generateSelfStudyExam, getStageProgress } from '@/api/selfStudyExam'
import { reportQuality } from '@/utils/qualityNotice'
import {
  mergeStageProgress,
  describeProgress,
  progressTagType,
  applyPrerequisites,
  describePrerequisite,
  STAGE_STATE
} from '@/utils/stageProgress'

const router = useRouter()

const progressRows = ref([])
const generating = ref(null)

// 把后端返回的测验结果合并进阶段数据：未测 / 已出卷待作答 / 已通过 / 未通过
const stages = computed(() =>
  applyPrerequisites(mergeStageProgress(reviewStages, progressRows.value))
)

async function loadProgress() {
  try {
    const res = await getStageProgress()
    progressRows.value = res.data || []
  } catch {
    // 进度拿不到不影响出卷，页面照常可用
    progressRows.value = []
  }
}

onMounted(loadProgress)

async function handleTestStage(stage) {
  try {
    await ElMessageBox.confirm(
      `将为「${stage.tag} · ${stage.title}」生成一套 10 道客观题，只考本阶段考点。\n出题与质量校验需要一两分钟，页面会保持等待。`,
      '测这一阶段',
      { type: 'info', confirmButtonText: '开始生成', cancelButtonText: '取消' }
    )
  } catch {
    return
  }

  generating.value = stage.key
  try {
    // 复用「AI创建考试」的流程：先建记录，再触发生成
    const prompt = buildStagePrompt(stage, 10)
    const createRes = await createSelfStudyExam({ prompt, duration: 30, stageKey: stage.key })
    const exam = createRes.data
    ElMessage.info('阶段测验已创建，正在生成题目，请耐心等待...')

    const genRes = await generateSelfStudyExam(exam.id)
    reportQuality(genRes?.data?.quality, '阶段测验生成成功！')

    router.push('/student/self-study')
  } catch (e) {
    ElMessage.error(e.message || '生成失败，请稍后重试')
  } finally {
    generating.value = null
  }
}
</script>

<style scoped>
/* 底部留白：避免最后一张卡被右下角的 AI 助手悬浮按钮压住 */
.review-path { padding-bottom: 80px; }
.page-header { margin-bottom: 16px; }
.page-header h2 { margin: 0; font-size: 22px; font-weight: 700; color: var(--color-text-primary); }
.subtitle { margin: 6px 0 0; font-size: 13px; color: var(--color-text-secondary); }

.intro { margin-bottom: 24px; border-radius: var(--radius-md, 8px); line-height: 1.7; }

.stage-list { display: flex; flex-direction: column; }

.stage { display: flex; gap: 16px; }

.rail {
  position: relative;
  flex: 0 0 32px;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.dot {
  width: 32px; height: 32px;
  border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  font-weight: 700; font-size: 14px;
  color: #fff;
  background: var(--color-primary, #409eff);
  flex-shrink: 0;
  z-index: 1;
}
.line { flex: 1; width: 2px; background: var(--color-border-light); margin: 6px 0; }

.card {
  flex: 1;
  background: var(--color-bg-card, #fff);
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-md, 8px);
  padding: 20px;
  margin-bottom: 20px;
}

.card-head { display: flex; align-items: baseline; gap: 10px; flex-wrap: wrap; }
.card-head h3 { margin: 0; font-size: 16px; font-weight: 700; color: var(--color-text-primary); flex: 1; }
.tag { font-size: 12px; font-weight: 700; color: var(--color-primary, #409eff); letter-spacing: .5px; }
.weeks { font-size: 13px; color: var(--color-text-secondary); white-space: nowrap; }
.status-tag { flex-shrink: 0; }

.gate-hint {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0 0 12px;
  padding: 8px 12px;
  font-size: 12.5px;
  line-height: 1.5;
  color: var(--color-warning, #b88230);
  background: rgba(230, 162, 60, 0.08);
  border-radius: 6px;
}

.src { margin: 10px 0 14px; font-size: 13px; color: var(--color-text-secondary); line-height: 1.6; }
.src b { color: var(--color-text-regular); font-weight: 600; }

.items { margin: 0 0 14px; padding-left: 18px; }
.items li { font-size: 13.5px; line-height: 1.85; color: var(--color-text-regular); }
.items b { color: var(--color-text-primary); font-weight: 600; }
.items .span { color: var(--color-text-placeholder); margin: 0 6px; font-size: 12.5px; }
.items .note { color: var(--color-text-secondary); }

.acceptance {
  background: var(--color-bg-page, #f5f7fa);
  border-left: 3px solid var(--color-warning, #e6a23c);
  border-radius: 6px;
  padding: 12px 16px;
  margin-bottom: 16px;
}
.acceptance-title { font-size: 13px; font-weight: 700; color: var(--color-text-primary); margin-bottom: 6px; }
.acceptance ol { margin: 0; padding-left: 20px; }
.acceptance li { font-size: 13px; line-height: 1.8; color: var(--color-text-regular); }

.actions { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.hint { font-size: 12.5px; color: var(--color-text-placeholder); }

/* 阶段配色沿用原文的色标 */
.stage.tone-g .dot { background: #4caf50; }
.stage.tone-v .dot { background: #7c6cf0; }
.stage.tone-a .dot { background: #f0a020; }

@media (max-width: 768px) {
  .stage { gap: 10px; }
  .rail { flex: 0 0 24px; }
  .dot { width: 24px; height: 24px; font-size: 12px; }
  .card { padding: 14px; }
  .card-head h3 { font-size: 15px; }
  .actions .hint { display: none; }
}
</style>
