# nop-auth 设计文档（MFA 多因子验证子系统）

> Status: active
> Created: 2026-08-10
> Updated: 2026-08-10（第五轮审查修订，已达成共识）

本目录按 AGE（Attractor-Guided Engineering）owner-doc 模式组织，承载 `nop-auth` 子系统的架构决策。当前覆盖 **MFA（多因子验证）** 设计：短信验证码登录与多因子验证，后续可继续纳入登录安全、会话治理等专题。

## 文档结构与阅读顺序

### 必读路径

1. `00-vision.md`
   - MFA 子系统的产品定位、成功标准、显式 non-goals、设计收敛路径。回答"MFA 做什么、不做什么、凭什么判断成功"。

2. `01-architecture-baseline.md`
   - 登录流程改造（两阶段 challenge，覆盖 loginAsync 与 createSessionForUserAsync）、短信验证码登录、TOTP 验证器、数据模型、API 契约、配置项、错误码、关键设计决策。回答"MFA 如何分层、对象间如何协作、需要改哪些既有代码"。

### 按需深入

- `docs-for-ai/03-modules/nop-auth.md` — 认证模块现有能力（用户/角色/资源/会话/外部登录）
- `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/login/LoginServiceImpl.java` — 现有登录核心（改造主战场）
- `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/biz/LoginApiBizModel.java` — 登录 GraphQL/REST 入口（新增接口落点）
- `nop-service-framework/nop-biz-auth-core/src/main/java/io/nop/auth/core/login/IUserContextCache.java` — 现有验证码/失败计数缓存（**仅 Local 实现、无 TTL 参数，MFA 不复用它**，见 Architecture §3.3）
- `nop-service-framework/nop-biz-auth-core/src/main/java/io/nop/auth/core/login/ISessionBootstrap.java` — SSO/信道登录引导接口（MFA 拦截第二落点）
- `nop-integration/nop-integration-api/src/main/java/io/nop/integration/api/sms/ISmsSender.java` — 短信发送通道（已存在）
- `nop-service-framework/nop-biz-auth-api/src/main/java/io/nop/auth/api/AuthApiConstants.java` — loginType 编码（需新增 5）
- `nop-service-framework/nop-biz-auth-core/src/main/resources/_vfs/dict/auth/login-type.dict.yaml` — 登录类型字典（需同步修复 SSO 4/10 不一致 + 补 2/3/5）
- `nop-auth/model/nop-auth.orm.xml` — ORM 模型（新增 MFA 实体）

## 职责边界

- `00-vision.md` 回答"MFA 的边界是什么"。
- `01-architecture-baseline.md` 回答"MFA 如何改造登录流程、核心对象职责、数据模型、API 契约、与既有机制的关系"。
- 本目录不记录实现过程、迁移日志、测试结果；这些进入 `ai-dev/logs/`、`ai-dev/plans/` 或 `ai-dev/analysis/`。

## 阅读顺序建议

新读者按 00 → 01 顺序阅读；只关心"短信验证码登录怎么加"的读者直接读 `01-architecture-baseline.md` 的"短信验证码登录与存储设计"一节；只关心"二次验证怎么加"的读者读"MFA 登录流程"一节。
