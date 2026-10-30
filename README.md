# 在线考试平台

[![CI](https://github.com/wojiatushansusu/zaixianpingtai/actions/workflows/ci.yml/badge.svg)](https://github.com/wojiatushansusu/zaixianpingtai/actions/workflows/ci.yml)

面向高校与培训场景的在线考试系统。学生用一句自然语言就能出卷，生成考试码拉同学来考；教师侧有组卷、监考、批改的完整闭环。

## 它解决什么

传统在线考试系统的瓶颈不在「做题」，在两个端头：

- **出题端**：教师手工录入一道题的耗时，远高于它被使用的价值。
- **复盘端**：考完就结束了，学生知道自己错了哪几道，但不知道接下来该练什么。

这个项目在这两端各加了一环：出题交给 LLM（说清「高数，求导，中等难度，10 道选择题」即可生成），复盘交给错题本（自动收录 → 按题型筛选 → 一键生成练习 → 提交后自动评分并展示解析）。

## 三个核心能力

**1. AI 出卷**
自然语言描述考试需求 → 大模型生成试卷 → 产出 6 位考试码 → 同学凭码加入。支持自助重考，同一份卷子可以反复练。

生成结果不会直接入库，中间有两道关卡：

- **结构校验**（`AiQuestionValidator`）：选项缺失或重复、答案字母越界、单选多答、判断题答案不在 A/B、题干占位符与重复，一律剔除；剔除比例过高时自动带约束提示重试一次。
- **答案自检**（`AnswerSelfCheckService`）：把题目隐去答案再让模型做一遍，与它先前声明的答案比对，不一致的题标记为「存疑」。

这两道关卡只能降低出错概率，**不能证明答案正确**。刻意保留人工判断入口，而不是假装 AI 永远是对的。

校验结果不会只写进日志：出卷完成后界面会明确告诉你「共 8 道题，已剔除 2 道结构不合格的题目，第 3、7 题答案存疑」。被悄悄减掉的题数，比错误本身更让人不信任。

**2. 监考与防作弊**
分层落地：前端切屏检测与复制限制、`face-api.js` 人脸核验、多人脸与头部姿态告警、后端切屏次数原子更新与超限自动交卷、Redis 分布式锁防重复提交、WebSocket 实时帧传输 + 监控大屏 + 视频证据留存。

**3. 错题闭环**
错题自动入库并按题型归类，可勾选生成专项练习，提交即评分并逐题展示解析；配卷 AI 助手可针对当前错题追问。

此外教师侧包含题库 CRUD（EasyExcel 批量导入，兼容 30+ 列名别名）、贪心算法智能组卷（按题型比例 / 难度分布 / 知识点约束）、主观题逐题批注、成绩分析（分布图、题目区分度、Cronbach's α 信度）。

## 快速开始

### 方式一：Docker（推荐）

只需 Docker，不必装 JDK / Node / MySQL / Redis：

```bash
git clone https://github.com/wojiatushansusu/zaixianpingtai.git
cd zaixianpingtai
cp .env.example .env        # 填好 DB_PASSWORD 与 JWT_SECRET
docker compose up -d --build
```

首次启动会自动建表并写入演示数据。打开 http://localhost:8088 即可。

> 首次构建要拉取镜像并编译前后端，耗时数分钟属正常。
> `DB_PASSWORD` / `JWT_SECRET` 未填时 compose 会直接报错退出——和本地启动一样，不许用弱密钥跑起来。

### 方式二：本地运行

**环境要求**：JDK 21+、Maven 3.8+、Node.js 18+、MySQL 8.0+、Redis 6.0+

```bash
# 1. 初始化数据库（24 张表 + 演示数据）
mysql -u root -p < backend/src/main/resources/db/schema.sql
```

**2. 配置环境变量（必需）**

复制 `.env.example` 为 `.env` 并填入真实值，然后 `set -a; source .env; set +a`。

```bash
export DB_PASSWORD=你的数据库密码
export JWT_SECRET=$(openssl rand -base64 48)   # 至少 32 字节
```

> ⚠️ **这两项缺失时后端会拒绝启动**——这是刻意设计。宁可启动失败，也不要用公开的弱密钥把一个「看起来能用」的服务跑起来。

```bash
# 3. 启动
cd backend  && mvn spring-boot:run                 # :8080/api
cd frontend && npm install && npm run dev          # :5173
```

打开 http://localhost:5173 即可。

## 环境变量

| 变量 | 必需 | 说明 |
|---|---|---|
| `DB_HOST` / `DB_PORT` / `DB_NAME` | 否 | 默认 `localhost` / `3306` / `exam_platform` |
| `DB_USERNAME` / `DB_PASSWORD` | **是** | 数据库账号 |
| `JWT_SECRET` | **是** | ≥32 字节随机串，用于签发 token |
| `CORS_ALLOWED_ORIGINS` | 否 | 逗号分隔，默认仅放行 localhost |
| `AI_ENABLED` | 否 | 默认 `false`。**必须显式设为 `true` 才会启用 AI**，即使填了 key |
| `AI_API_URL` / `AI_API_KEY` | 否 | OpenAI 兼容接口。也支持本地 Ollama |
| `REDIS_HOST` / `REDIS_PORT` | 否 | 默认 `localhost` / `6379`；compose 下已设为 `redis` |

## 部署到服务器

### 1. 准备 .env

```bash
cp .env.example .env
```

两件事必须做，否则要么起不来、要么核心功能是哑的：

```bash
# ① JWT 密钥（留空直接拒绝启动）
JWT_SECRET=$(openssl rand -base64 48)

# ② 打开 AI —— 不设这一项，出卷 / 错题讲解 / 知识点补标全部不可用
AI_ENABLED=true
AI_API_KEY=sk-xxxxxxxx
```

> `AI_ENABLED` 与 `AI_API_KEY` 缺一不可：只填 key 不开开关，什么都没变；
> 只开开关没 key，调用会失败。这一项最容易漏，因为漏了不报错，只是功能静默不可用。

### 2. 起服务

```bash
docker compose up -d --build
```

首次启动会自动建表并写入演示数据（24 张表 + 若干账号）。**演示账号密码都是 `123456`，
对公网开放前必须先改掉或删除**，见下方「演示账号」。

### 3. 接域名

最简单的做法是让宿主机的 80/443 反代到 `127.0.0.1:8088`：

```nginx
server {
    listen 443 ssl;
    server_name exam.example.com;
    client_max_body_size 20m;

    location / {
        proxy_pass http://127.0.0.1:8088;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        # AI 出卷是同步长请求，超时要够长
        proxy_read_timeout 300s;
    }
}
```

前端容器里的 nginx 已经把 `/api/` 转给后端（含 WebSocket 升级），外层只需要整体转发。

> **CORS 不用配**：前后端同域。只有在前后端分属不同域名时才需要设 `CORS_ALLOWED_ORIGINS`。

### 4. 复用旧数据库时

`docker-compose.yml` 只在**首次**创建数据库时执行 `schema.sql`，已存在的库不会自动升级。
若你导入的是旧数据，按顺序补这三个迁移：

```bash
for f in migration-schema-sync migration-stage-key migration-knowledge-point; do
  docker compose exec -T mysql mysql -uroot -p"$DB_PASSWORD" exam_platform \
    < backend/src/main/resources/db/$f.sql
done
```

> `migration-schema-sync` 补的是 `answer_record` 的 `create_time/update_time/deleted`
> 和 `exam_online_student` 的两个断网列。**缺 `deleted` 会让交卷、批改、成绩、错题本全部失败**——
> MyBatis-Plus 的全局逻辑删除会给每次查询补 `WHERE deleted = 0`。
>
> 这三处是部署时才发现的老账。现在 CI 里有一道 `scripts/check_schema_consistency.py`，
> 会比对实体字段与 `schema.sql` 的列，不一致直接失败，同类问题不会再漏。

### 5. 部署后 5 分钟自查

按顺序跑，哪一步不对就停在那里查，别急着点页面：

```bash
BASE=https://你的域名

# ① 服务活着（返回 400 = 参数校验生效，说明请求链路通了）
curl -s -o /dev/null -w "%{http_code}\n" -X POST $BASE/api/auth/login \
  -H 'Content-Type: application/json' -d '{}'

# ② 登录拿 token
TOKEN=$(curl -s -X POST $BASE/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"123456"}' | python -c 'import sys,json;print(json.load(sys.stdin)["data"]["token"])')

# ③ 知识点词表（能返回 35 项，说明词表加载 + 管理员权限都正常）
curl -s $BASE/api/admin/questions/topic-catalog -H "Authorization: Bearer $TOKEN" | head -c 200

# ④ 真实调一次模型（这一步才验证得出 AI 配好了没有）
curl -s -X POST "$BASE/api/admin/questions/backfill-topics?limit=3&dryRun=true" \
  -H "Authorization: Bearer $TOKEN"
```

第 ④ 步的返回就是答案：

| 返回 | 说明 |
|---|---|
| `tagged` > 0，`samples` 里的题目和知识点对得上 | AI 通了，可以继续 |
| `tagged: 0`、`failed: 3`，日志写「AI 未启用」 | `AI_ENABLED` 没生效 |
| `tagged: 0`、`failed: 3`，日志是一条 4xx | `AI_API_KEY` 无效或余额不足 |
| 接口 502 / 超时 | nginx 的 `proxy_read_timeout` 太短 |

最后一步是人工判断：拿真实账号在「复习路径 → 测这一阶段」点一次。
**AI 出的题质量只能靠人看**，任何自动化检查都替代不了。

## 工程护栏

CI 里有两道静态检查，都不需要数据库、秒级完成。它们拦的是**开发期完全静默、
只在别人部署时炸**的那一类问题：

| 检查 | 拦什么 | 真实案例 |
|---|---|---|
| `scripts/check_schema_consistency.py` | 实体字段与 `schema.sql` 的列不一致 | `answer_record` 缺 `deleted`，导致交卷全链路失败 |
| `scripts/check_env_contract.py` | 应用引用的环境变量与 compose 传的不一致 | `AI_ENABLED` 没有环境变量入口，填了 key 功能也不生效 |

两道检查都验证过「能红」：手工删一列 / 少传一个变量，退出码立刻变 1 并点名具体位置。
**一道永远不会失败的检查，比没有检查更糟。**

本地也可以直接跑：

```bash
python3 scripts/check_schema_consistency.py
python3 scripts/check_env_contract.py
```

## 技术栈

| 层级 | 技术 |
|---|---|
| 前端 | Vue 3 + Element Plus + Vite + Pinia |
| 后端 | Spring Boot 3.2.5 + Java 21 + MyBatis-Plus |
| 存储 | MySQL 8.0（24 张表）+ Redis |
| 认证 | Spring Security + JWT（无状态） |
| 实时 | WebSocket 双通道（通知 / 考试监控，支持二进制帧） |
| AI | LLM 走 OpenAI 兼容接口；人脸检测前端 `face-api.js` + `OpenCV.js` |

## 项目结构

```
├── backend/                  Spring Boot
│   ├── controller/           Auth / Question / Paper / Exam / Monitor / Admin ...
│   ├── service/              业务层（含智能组卷、LLM 路由）
│   ├── websocket/            通知 + 考试监控双通道
│   ├── security/             JWT 过滤器与鉴权
│   └── resources/db/         schema.sql（建表 + 演示数据）
└── frontend/                 Vue 3
    ├── views/{student,teacher,admin,layout,dashboard}
    ├── components/           AI 助手、摄像头监控
    └── assets/styles/        global.css（CSS 变量 + Element Plus 主题桥接层）
```

## 演示账号

数据库初始化后自带四个账号，密码均为 `123456`：

| 账号 | 角色 |
|---|---|
| `admin` | 管理员 |
| `teacher1` | 教师 |
| `student1` / `student2` | 学生 |

> 登录页只在开发环境显示这组提示，生产构建不会渲染。
> **任何对公网开放的部署都必须先重置这些密码或删除演示账号。**

## 人脸核验的前端模型

浏览器侧用 `face-api.js`，模型**先找本地 `/models/`，找不到再回落到 jsdelivr CDN**。

- 默认走 CDN，仓库里不带权重（约 12 MB）
- 网络访问 CDN 不稳时，把权重放进 `frontend/public/models/` 即可离线运行，
  具体命令见该目录下的 `README.md`
- 加载失败会降级（`_doLoad` 返回 false），不会阻塞考试主流程
- 后端 OpenCV 检测与前端是两套：后端缺原生库时同样降级，不影响其他功能

## 已知限制

诚实列出，比藏着好：

- **自动化测试仍不完整**：后端有 51 个用例（判分规则 `ScoringRules`、题目校验 `AiQuestionValidator`、答案自检 `AnswerSelfCheckService`），前端有 31 个用例（`examStatus` 状态判定、`qualityNotice` 质量文案）。但**组件渲染与接口层仍无测试**——页面行为靠真实浏览器手工验证，这部分没有被 CI 覆盖。

> 本地跑前端单测：`cd frontend && npm test`（CI 里也会执行）。

> **存量题目补知识点**（管理员）：`POST /admin/questions/backfill-topics?limit=10&dryRun=true`
> 每道题都要调一次模型，所以故意限制单批最多 50 题——跑一批、看一眼 `samples`、再决定继续。
> `dryRun=true` 只判定不落库。词表见 `backend/src/main/resources/knowledge-topics.json`，
> 与前端复习路径的模块名由一条单测守卫，两边不会漂移。

> **已有数据库升级**：复习路径相关改动加了两列——
> `self_study_exam.stage_key`（阶段测验归属）与 `question.knowledge_point`（题目知识点）。
> 老库依次执行 `db/migration-stage-key.sql` 与 `db/migration-knowledge-point.sql`；
> 新建库由 `schema.sql` 直接建出，无需执行。
- **单机部署**：未做水平扩展，`uploads/` 与视频证据存本地磁盘，多实例需改造为对象存储。
- **前端单文件偏大**：`StudentExams.vue` 等页面尚未拆分组件，改动时注意影响面。
- **无 API 文档**：未集成 springdoc，接口以 controller 代码为准。

## 说明

个人项目，用于学习与作品展示，未做生产级安全审计。若要用于真实场景，请先补齐鉴权边界、限流、日志脱敏与前述测试。
