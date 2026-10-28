<template>
  <div class="review-path">
    <div class="page-header">
      <div>
        <h2>复习路径</h2>
        <p class="subtitle">安徽专升本《高等数学》· 七个阶段，从初中地基到真题冲刺</p>
      </div>
    </div>

    <el-tabs v-model="activeTab" class="guide-tabs">
      <!-- ═════════ 路线图 ═════════ -->
      <el-tab-pane label="路线图" name="path">
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

              <p v-if="describeWeakTopics(stage.progress.weakTopics)" class="weak-topics">
                <el-icon><Warning /></el-icon>
                {{ describeWeakTopics(stage.progress.weakTopics) }}
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
      </el-tab-pane>

      <!-- ═════════ 卷面结构 ═════════ -->
      <el-tab-pane label="卷面结构" name="paper">
        <p class="lead">{{ paperStructure.intro }}</p>

        <div class="stat-row">
          <div v-for="stat in paperStructure.stats" :key="stat.label" class="stat">
            <b>{{ stat.value }}</b>
            <span>{{ stat.label }}</span>
            <small>{{ stat.note }}</small>
          </div>
        </div>

        <p class="lead">{{ paperStructure.blocksTitle }}</p>
        <table class="data-table">
          <thead>
            <tr><th>板块</th><th class="num">分值</th><th>难度</th><th>特点</th></tr>
          </thead>
          <tbody>
            <tr v-for="block in paperStructure.blocks" :key="block.name">
              <td><b>{{ block.name }}</b></td>
              <td class="num">{{ block.score }}</td>
              <td>{{ block.difficulty }}</td>
              <td class="muted">{{ block.note }}</td>
            </tr>
          </tbody>
        </table>

        <p class="lead">{{ paperStructure.conclusionsTitle }}</p>
        <ol class="conclusions">
          <li v-for="(item, i) in paperStructure.conclusions" :key="i">
            <b>{{ item.title }}</b>
            <span>{{ item.text }}</span>
          </li>
        </ol>
      </el-tab-pane>

      <!-- ═════════ 考纲减法 ═════════ -->
      <el-tab-pane label="考纲减法" name="cut">
        <p class="lead">{{ syllabusCut.intro }}</p>

        <div class="two-col">
          <div class="col col-must">
            <h4>{{ syllabusCut.mustTitle }}</h4>
            <ul>
              <li v-for="item in syllabusCut.must" :key="item">{{ item }}</li>
            </ul>
          </div>
          <div class="col col-skip">
            <h4>{{ syllabusCut.skipTitle }}</h4>
            <ul>
              <li v-for="item in syllabusCut.skip" :key="item">{{ item }}</li>
            </ul>
          </div>
        </div>

        <div class="callout">{{ syllabusCut.keyPoint }}</div>

        <h4 class="section-title">{{ syllabusCut.levelsTitle }}</h4>
        <table class="data-table">
          <thead>
            <tr><th>学科</th><th>保留（对高数有直接用处）</th><th>可以整章跳过</th></tr>
          </thead>
          <tbody>
            <tr v-for="level in syllabusCut.levels" :key="level.subject">
              <td><b>{{ level.subject }}</b></td>
              <td>{{ level.keep }}</td>
              <td class="muted">{{ level.drop }}</td>
            </tr>
          </tbody>
        </table>
      </el-tab-pane>

      <!-- ═════════ 时间安排 ═════════ -->
      <el-tab-pane label="时间安排" name="time">
        <p class="lead">{{ timePlans.intro }}</p>

        <table class="data-table">
          <thead>
            <tr><th>阶段</th><th class="num">标准档</th><th class="num">压缩档</th><th>压缩档怎么砍</th></tr>
          </thead>
          <tbody>
            <tr v-for="row in timePlans.rows" :key="row.stage">
              <td><b>{{ row.stage }}</b></td>
              <td class="num">{{ row.standard }}</td>
              <td class="num">{{ row.compressed }}</td>
              <td class="muted">{{ row.how }}</td>
            </tr>
            <tr class="total">
              <td><b>{{ timePlans.total.stage }}</b></td>
              <td class="num"><b>{{ timePlans.total.standard }}</b></td>
              <td class="num"><b>{{ timePlans.total.compressed }}</b></td>
              <td class="muted">{{ timePlans.total.how }}</td>
            </tr>
          </tbody>
        </table>

        <div class="callout">{{ timePlans.advice }}</div>

        <h4 class="section-title">一周怎么排</h4>
        <p class="lead">{{ weeklyPlan.intro }}</p>

        <div class="week-grid">
          <div v-for="block in weeklyPlan.blocks" :key="block.title" class="week-card">
            <div class="week-head">
              <b>{{ block.title }}</b>
              <span>{{ block.total }}</span>
            </div>
            <ul v-if="block.items.length">
              <li v-for="item in block.items" :key="item.min">
                <em>{{ item.min }}</em>{{ item.text }}
              </li>
            </ul>
            <p v-if="block.note" class="week-note">{{ block.note }}</p>
          </div>
        </div>

        <div class="callout callout-warn">{{ weeklyPlan.hardRule }}</div>
      </el-tab-pane>

      <!-- ═════════ 资源与坑 ═════════ -->
      <el-tab-pane label="资源与坑" name="more">
        <h4 class="section-title">资源清单</h4>
        <table class="data-table">
          <thead>
            <tr><th>阶段</th><th>平台</th><th>具体内容</th><th>用法要点</th></tr>
          </thead>
          <tbody>
            <tr v-for="row in resources.rows" :key="row.stage">
              <td><b>{{ row.stage }}</b></td>
              <td>{{ row.platform }}</td>
              <td>{{ row.content }}</td>
              <td class="muted">{{ row.tip }}</td>
            </tr>
          </tbody>
        </table>
        <p class="lead small">{{ resources.note }}</p>

        <h4 class="section-title">八个最容易踩的坑</h4>
        <div class="pitfalls">
          <div v-for="item in pitfalls.items" :key="item.no" class="pitfall">
            <span class="pitfall-no">{{ item.no }}</span>
            <div class="pitfall-body">
              <b>{{ item.title }}</b>
              <p>{{ item.text }}</p>
            </div>
          </div>
        </div>

        <div class="footnotes">
          <p v-for="(text, i) in pitfalls.footnotes" :key="i">{{ text }}</p>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
/**
 * 复习路径页。
 *
 * 内容来自《安徽专升本〈高等数学〉从初中到高数的完整复习路径》。分五个标签：
 *
 * - 路线图：七阶段，每个阶段能就地出一套只考本阶段的题，结果会回填成状态
 * - 卷面结构 / 考纲减法 / 时间安排 / 资源与坑：陈述性内容，原样呈现
 *
 * 为什么后者也要放进来：它们回答的是"什么不用学""时间怎么分"，
 * 而路线图只回答"按什么顺序学"。缺了前者，学生很可能在不会考的内容上耗掉一半时间。
 * 这些内容无法被平台数据检验，所以只做结构化呈现，不做加工。
 */
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { reviewStages, buildStagePrompt } from '@/data/reviewPath'
import { paperStructure, syllabusCut, timePlans, weeklyPlan, resources, pitfalls } from '@/data/reviewGuide'
import { createSelfStudyExam, generateSelfStudyExam, getStageProgress } from '@/api/selfStudyExam'
import { reportQuality } from '@/utils/qualityNotice'
import {
  mergeStageProgress,
  describeProgress,
  progressTagType,
  applyPrerequisites,
  describePrerequisite,
  describeWeakTopics,
  STAGE_STATE
} from '@/utils/stageProgress'

const router = useRouter()

const activeTab = ref('path')
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
.page-header { margin-bottom: 8px; }
.page-header h2 { margin: 0; font-size: 22px; font-weight: 700; color: var(--color-text-primary); }
.subtitle { margin: 6px 0 0; font-size: 13px; color: var(--color-text-secondary); }

.guide-tabs { margin-top: 4px; }

.intro { margin-bottom: 24px; border-radius: var(--radius-md, 8px); line-height: 1.7; }

/* ── 阶段时间线 ── */
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

.weak-topics {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0 0 12px;
  padding: 8px 12px;
  font-size: 12.5px;
  line-height: 1.5;
  color: var(--color-danger, #f56c6c);
  background: rgba(245, 108, 108, 0.07);
  border-radius: 6px;
}

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

/* ── 通用内容排版 ── */
.lead { font-size: 13.5px; line-height: 1.8; color: var(--color-text-regular); margin: 0 0 14px; }
.lead.small { font-size: 12.5px; color: var(--color-text-secondary); }
.section-title {
  margin: 26px 0 12px;
  font-size: 15px;
  font-weight: 700;
  color: var(--color-text-primary);
}
.muted { color: var(--color-text-secondary); }
.num { white-space: nowrap; font-variant-numeric: tabular-nums; }

.data-table {
  width: 100%;
  border-collapse: collapse;
  margin-bottom: 16px;
  font-size: 13px;
}
.data-table th {
  text-align: left;
  padding: 10px 12px;
  background: var(--color-bg-page, #f5f7fa);
  color: var(--color-text-secondary);
  font-weight: 600;
  border-bottom: 1px solid var(--color-border-light);
  white-space: nowrap;
}
.data-table td {
  padding: 10px 12px;
  border-bottom: 1px solid var(--color-border-light);
  color: var(--color-text-regular);
  line-height: 1.7;
  vertical-align: top;
}
.data-table tr:last-child td { border-bottom: none; }
.data-table tr.total td { background: rgba(64, 158, 255, 0.05); }

/* 卷面结构的关键数字 */
.stat-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  gap: 12px;
  margin-bottom: 20px;
}
.stat {
  background: var(--color-bg-page, #f5f7fa);
  border-radius: var(--radius-md, 8px);
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.stat b { font-size: 20px; font-weight: 700; color: var(--color-primary, #409eff); }
.stat span { font-size: 13px; color: var(--color-text-primary); }
.stat small { font-size: 12px; color: var(--color-text-secondary); }

.conclusions { margin: 0 0 8px; padding-left: 20px; }
.conclusions li { margin-bottom: 12px; font-size: 13.5px; line-height: 1.75; }
.conclusions b { display: block; color: var(--color-text-primary); margin-bottom: 2px; }
.conclusions span { color: var(--color-text-secondary); }

/* 考纲减法：必须学 / 直接跳过 */
.two-col { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 16px; }
.col {
  border-radius: var(--radius-md, 8px);
  padding: 14px 18px;
  border: 1px solid var(--color-border-light);
}
.col h4 { margin: 0 0 10px; font-size: 13.5px; font-weight: 700; }
.col ul { margin: 0; padding-left: 18px; }
.col li { font-size: 13px; line-height: 1.8; color: var(--color-text-regular); }
.col-must { background: rgba(103, 194, 58, 0.06); border-color: rgba(103, 194, 58, 0.25); }
.col-must h4 { color: #4e9a2f; }
.col-skip { background: rgba(144, 147, 153, 0.06); }
.col-skip h4 { color: var(--color-text-secondary); }
.col-skip li { color: var(--color-text-placeholder); }

.callout {
  background: rgba(64, 158, 255, 0.06);
  border-left: 3px solid var(--color-primary, #409eff);
  border-radius: 6px;
  padding: 12px 16px;
  margin-bottom: 8px;
  font-size: 13px;
  line-height: 1.8;
  color: var(--color-text-regular);
}
.callout-warn {
  background: rgba(230, 162, 60, 0.08);
  border-left-color: var(--color-warning, #e6a23c);
}

/* 一周怎么排 */
.week-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 12px;
  margin-bottom: 14px;
}
.week-card {
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-md, 8px);
  padding: 14px 16px;
}
.week-head { display: flex; justify-content: space-between; align-items: baseline; margin-bottom: 10px; }
.week-head b { font-size: 13.5px; color: var(--color-text-primary); }
.week-head span { font-size: 12px; color: var(--color-text-secondary); }
.week-card ul { margin: 0; padding: 0; list-style: none; }
.week-card li { font-size: 12.5px; line-height: 1.7; color: var(--color-text-regular); }
.week-card li em {
  display: inline-block;
  min-width: 56px;
  margin-right: 6px;
  font-style: normal;
  color: var(--color-primary, #409eff);
  font-weight: 600;
}
.week-note { margin: 0; font-size: 12.5px; line-height: 1.7; color: var(--color-text-secondary); }

/* 八个坑 */
.pitfalls { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.pitfall {
  display: flex;
  gap: 12px;
  padding: 14px 16px;
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-md, 8px);
}
.pitfall-no {
  font-size: 18px;
  font-weight: 700;
  color: rgba(64, 158, 255, 0.35);
  line-height: 1.2;
}
.pitfall-body b { font-size: 13.5px; color: var(--color-text-primary); }
.pitfall-body p { margin: 4px 0 0; font-size: 12.5px; line-height: 1.7; color: var(--color-text-secondary); }

.footnotes { margin-top: 20px; padding-top: 14px; border-top: 1px dashed var(--color-border-light); }
.footnotes p { margin: 0 0 8px; font-size: 12px; line-height: 1.7; color: var(--color-text-placeholder); }

@media (max-width: 768px) {
  .stage { gap: 10px; }
  .rail { flex: 0 0 24px; }
  .dot { width: 24px; height: 24px; font-size: 12px; }
  .card { padding: 14px; }
  .card-head h3 { font-size: 15px; }
  .actions .hint { display: none; }

  .two-col { grid-template-columns: 1fr; }
  .pitfalls { grid-template-columns: 1fr; }
  .stat-row { grid-template-columns: 1fr 1fr; }
  .data-table { font-size: 12.5px; }
  .data-table th, .data-table td { padding: 8px; }
}
</style>
