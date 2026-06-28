# Debug Session: spring-boot-startup-failure

**Status**: [OPEN]
**Created**: 2026-05-30
**Issue**: Spring Boot 应用启动失败，exit code 1

---

## Hypotheses

1. **环境变量缺失** - `DB_PASSWORD`, `JWT_SECRET`, `AI_API_KEY` 等必需环境变量未设置
2. **配置文件格式错误** - application.yml 修改后格式有问题
3. **MySQL 连接失败** - 数据库服务未启动或连接信息错误
4. **Redis 连接失败** - Redis 服务未启动

---

## Evidence Collection

[To be filled after running diagnostic commands]
