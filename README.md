# 在线考试平台

基于 Vue 3 + Spring Boot 3 的全功能在线考试系统，支持 AI 组卷、智能防作弊、错题练习、AI 自助考试等特性。

## 技术栈

| 层级 | 技术 |
|------|------|
| **前端** | Vue 3 + Element Plus + Vite + Pinia + Axios |
| **后端** | Spring Boot 3.2.5 + Java 21 + MyBatis-Plus |
| **安全** | Spring Security + JWT (无状态认证) |
| **数据库** | MySQL 8.0 + Redis |
| **AI** | OpenCV.js (前端) + JavaCV (后端) + 本地 LLM (Ollama) |
| **通信** | WebSocket (通知 + 考试监控) |

## 环境要求

- JDK 21+
- Maven 3.8+
- Node.js 18+
- MySQL 8.0+
- Redis 6.0+

## 快速开始

### 1. 初始化数据库

```sql
-- 方式一：直接执行完整脚本（推荐）
SOURCE backend/src/main/resources/db/schema.sql;
```

```powershell
# 方式二：命令行执行
mysql -u root -p < backend/src/main/resources/db/schema.sql
```

> 💡 **脚本说明**：
> - `schema.sql` — 完整数据库脚本（24张表 + 初始数据），全新环境直接执行即可
> - `integrity-check.sql` — 数据完整性检查与修复脚本（可选，用于排查问题）
> - 兼容 MySQL 8.0+ 和 MySQL 9.x

### 2. 修改配置

**方式一：环境变量（推荐用于生产环境）**

```bash
# 数据库配置 (必需)
export DB_HOST=localhost
export DB_PORT=3306
export DB_NAME=exam_platform
export DB_USERNAME=root
export DB_PASSWORD=your_secure_password

# JWT密钥 (必需) - 至少32位随机字符串
export JWT_SECRET=your_secure_jwt_secret_at_least_32_characters

# AI服务配置 (可选，默认关闭)
export AI_API_KEY=your_ai_api_key
export AI_API_URL=https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions

# CORS配置 (可选，默认仅允许本地开发)
export CORS_ALLOWED_ORIGINS=http://localhost:5173,http://127.0.0.1:5173
```

**方式二：配置文件**

编辑 `backend/src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/exam_platform?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root
    password: 123456  # 开发环境默认密码，生产环境请修改
  data:
    redis:
      host: localhost
      port: 6379

# JWT配置
jwt:
  secret: your-very-long-secret-key-here-for-jwt-signing  # 开发环境默认值，生产环境请修改

# AI相关配置（默认关闭，需启用请改enabled: true）
app:
  ai:
    enabled: false
    default-api-key: ${AI_API_KEY:}
    default-model: qwen
    default-api-url: ${AI_API_URL:https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions}
```

### 3. 启动服务

```
# 手动启动
cd backend && mvn spring-boot:run   # 后端 :8080/api
cd frontend && npm install && npm run dev  # 前端 :5173
```

### 4. 访问地址

| 服务 | 地址 |
|------|------|
| 前端 | http://localhost:5173 |
| 后端 API | http://localhost:8080/api |
| API 文档 (Swagger) | http://localhost:8080/api/swagger-ui.html |

## 测试账号

> ⚠️ **重要**: 请在数据库初始化后设置管理员密码。创建用户时密码为必填项（至少6位）。

| 角色 | 用户名 | 密码 |
|------|--------|------|
| 管理员 | admin | (初始化时设置) |
| 教师 | teacher1 | (初始化时设置) |
| 学生 | student1 | (初始化时设置) |
| 学生 | student2 | (初始化时设置) |

**初始账号设置方式**:
1. 通过管理后台创建用户
2. 或通过 API: `POST /admin/users` 传入密码字段

## 功能概览

### 👨‍💼 管理员
- 用户管理（创建 / 编辑 / 删除 / 角色分配）
- 操作日志查看

### 👩‍🏫 教师
- **题库管理**：单选 / 多选 / 判断 / 简答题 CRUD，支持 Excel 批量导入（30+ 列名别名），题目标签化 + 难度分级
- **智能组卷**：贪心算法自动选题，按题型比例 / 难度分布 / 知识点标签约束生成试卷
- **试卷管理**：手工组卷 + 智能组卷，选择题目设定分值
- **考试管理**：发布考试，设置时间 / 时长 / 白名单，支持人脸核验开关
- **监考大屏**：实时在线统计、WebSocket 视频流、违规告警、视频证据回放
- **主观题批改**：逐题批注评分
- **成绩分析**：成绩分布图、题目区分度、Cronbach's Alpha 信度分析

### 🎓 学生
- **我的考试**：查看可参加考试，人脸核验（可选），在线答题
- **AI 自助考试**：输入考试需求，AI 自动生成试卷，生成考试码分享给其他同学
- **错题本**：自动收录错题，按题型筛选，查看解析
- **错题练习**：选择错题一键生成练习，提交后自动评分并展示解析
- **AI 学习助手**：右下角悬浮按钮，支持错题讲解 + 自由对话
- **我的成绩**：查看考试成绩详情

## 防作弊机制

| 层级 | 措施 |
|------|------|
| **前端基础** | 切屏检测 (visibilitychange)、禁止复制粘贴、禁用 F12、禁用右键菜单 |
| **前端 AI** | OpenCV.js 人脸检测、多人脸告警、头部姿态估计（偏转检测）、违禁品检测 |
| **后端** | 切屏次数原子更新、超限自动交卷、Redis 分布式提交锁 |
| **核验** | 人脸核验（考前拍照比对注册照）、一次性 ExamToken |
| **监控** | WebSocket 视频帧实时传输、帧缓存 + FFmpeg 视频证据合成 |
| **在线检测** | 心跳检测、断网标记、重连恢复 |

## 开发历程

### 阶段一 — 基础考试平台 MVP
用户管理、题库 CRUD（单选/多选/判断）、手工组卷、考试发布与在线答题、成绩查看、基础防作弊。

### 阶段二 — 智能组卷与监考
贪心算法组卷、Excel 批量导入、题目标签化与难度分级、人脸核验、CameraMonitor 摄像头监控、切屏截图、MonitorCenter 监考大屏。

### 阶段三 — AI 防作弊与成绩分析
AI 分析引擎（人脸/姿态/违禁品）、WebSocket 二进制帧传输、在线状态与断网续传、视频证据留存、主观题批改、考试分析（正态分布/区分度/信度）。

### 阶段四 — 错题本与 AI 助手
错题自动收录、错题练习考试（自动评分+解析展示）、AI 学习助手（全局抽屉对话 + 错题上下文讲解）、前端 UI 设计系统（CSS 变量主题、动画、响应式）。

### 阶段五 — AI 自助考试
学生用自然语言描述考试需求 → AI (本地 LLM) 自动生成试卷 → 生成 6 位考试码 → 其他同学通过考试码参加考试 → 支持自助重考。

## 数据库表结构

共 **24 张表**，按功能模块划分：

| 模块 | 表名 | 说明 |
|------|------|------|
| **用户权限** | `role` | 角色表 |
| | `user` | 用户表 |
| **题库管理** | `question` | 题目表（含标签/难度） |
| | `knowledge_tag` | 知识点标签表 |
| | `question_tag` | 题目-标签关联表 |
| **试卷管理** | `paper` | 试卷表 |
| | `paper_question` | 试卷-题目关联表 |
| **考试管理** | `exam` | 考试表 |
| | `exam_whitelist` | 考试白名单表 |
| | `exam_token` | 人脸核验一次性凭证 |
| | `user_face` | 用户人脸数据 |
| | `face_verify_record` | 人脸核验记录 |
| **答题记录** | `exam_record` | 考试记录表 |
| | `answer_record` | 答题记录表（含教师评分） |
| **防作弊监控** | `violation_record` | 违规记录表 |
| | `exam_online_student` | 考生在线状态表 |
| | `video_evidence` | 视频证据表 |
| | `frame_analysis_log` | 帧分析日志表 |
| **错题练习** | `practice_record` | 错题练习记录表 |
| | `practice_answer` | 错题练习答案表 |
| **AI自助考试** | `self_study_exam` | AI自助考试表 |
| | `self_study_participant` | 自助考试参与者表 |
| **系统** | `operation_log` | 操作日志表 |
| | `system_config` | 系统配置表 |

## 项目结构

```
├── backend/                         # Spring Boot 后端
│   ├── src/main/java/com/exam/
│   │   ├── common/                  # 通用组件
│   │   │   ├── enums/               # 枚举（ResultCodeEnum、DifficultyEnum、QuestionTypeEnum）
│   │   │   ├── exception/           # 异常（BusinessException、GlobalExceptionHandler）
│   │   │   └── result/              # 统一响应结果（Result）
│   │   ├── config/                  # 配置类（Security、WebSocket、Async、MyBatis）
│   │   ├── controller/              # 控制器（Auth、StudentExam、Teacher、Admin、Monitor...）
│   │   ├── dto/                     # 数据传输对象
│   │   ├── listener/                # 监听器（Excel导入监听器）
│   │   ├── mapper/                  # MyBatis Mapper
│   │   ├── pojo/                    # 实体类
│   │   ├── security/                # Spring Security（JWT过滤器、认证入口、登出处理）
│   │   ├── service/                 # 业务服务层（含LlmRouter AI路由）
│   │   ├── util/                    # 工具类（JwtUtil等）
│   │   └── websocket/               # WebSocket 处理器
│   │       ├── NotificationWebSocketHandler.java
│   │       └── ExamMonitorWebSocketHandler.java
│   └── src/main/resources/
│       ├── application.yml          # 应用配置
│       └── db/                      # 数据库脚本（按阶段）
│           ├── init.sql             # 阶段一（基础表）
│           ├── phase2-init.sql      # 阶段二（标签/人脸/白名单）
│           ├── phase3-init.sql      # 阶段三（在线状态/视频证据）
│           ├── phase4-init.sql      # 阶段四（错题练习）
│           └── phase5-init.sql      # 阶段五（AI自助考试）
├── frontend/                        # Vue 3 前端
│   ├── src/
│   │   ├── api/                     # API 接口封装
│   │   ├── assets/styles/           # 全局样式（CSS变量主题系统）
│   │   ├── components/              # 公共组件
│   │   │   ├── common/AiAssistant.vue
│   │   │   └── exam/CameraMonitor.vue
│   │   ├── router/                  # 路由配置（角色守卫）
│   │   ├── utils/                   # 工具函数（request、errorMonitor）
│   │   └── views/                   # 页面组件
│   │       ├── dashboard/           # 首页 Dashboard
│   │       ├── layout/              # 布局（侧边栏 + 顶栏）
│   │       ├── login/               # 登录/注册页
│   │       ├── student/             # 学生端页面
│   │       ├── teacher/             # 教师端页面
│   │       └── admin/               # 管理员页面
│   └── package.json
└── README.md
```

## 架构要点

- **认证**：Spring Security + JWT 无状态认证，角色分为 admin / teacher / student
- **数据删除**：MyBatis-Plus 逻辑删除（`deleted` 字段），唯一索引冲突场景使用物理删除
- **定时任务**：每分钟更新考试状态，自动提交超时答卷
- **WebSocket**：双通道设计 — `/ws/notification`（基础通知）+ `/ws/exam-monitor`（考试监控，支持二进制帧）
- **并发控制**：Redis 分布式锁防止重复提交，切屏次数原子更新
- **AI 集成**：支持阿里云百炼 API / 本地 Ollama，OpenAI 兼容格式 API 调用
- **安全加固**：
  - 敏感配置（数据库密码、JWT密钥、AI API密钥）通过环境变量注入
  - CORS 仅允许配置的域名访问
  - 用户密码强制要求至少6位
