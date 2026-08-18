# nop-auth 设计文档（MFA 多因子验证子系统）

> Status: active
> Created: 2026-08-10
> Updated: 2026-08-14（二期设计 `02-mfa-phase2-design.md` 定稿，阅读顺序补条目）

本目录按 AGE（Attractor-Guided Engineering）owner-doc 模式组织，承载 `nop-auth` 子系统的架构决策。当前覆盖 **MFA（多因子验证）** 设计：一期已落地（短信验证码登录 + TOTP + 登录级两阶段 MFA），二期设计定稿（操作级 MFA / 角色级强制策略 / 因子扩展（WebAuthn/邮件码）/ 可信设备，待 W12-W15-impl 实施）；后续可继续纳入登录安全、会话治理等专题。

## 文档结构与阅读顺序

### 必读路径

1. `00-vision.md`
   - MFA 子系统的产品定位、成功标准、显式 non-goals、设计收敛路径。回答"MFA 做什么、不做什么、凭什么判断成功"。

2. `01-architecture-baseline.md`
   - 登录流程改造（两阶段 challenge，覆盖 loginAsync 与 createSessionForUserAsync）、短信验证码登录、TOTP 验证器、数据模型、API 契约、配置项、错误码、关键设计决策。回答"MFA 如何分层、对象间如何协作、需要改哪些既有代码"。**一期基线（W4-W8 已落地），二期各主题的兼容性锚点。**

3. `02-mfa-phase2-design.md`
   - MFA 二期四主题设计：操作级 MFA（请求级钩子 + 敏感操作声明模型）、角色级强制策略（策略模型/三层判定矩阵/受限会话引导）、因子扩展（MfaType 形态裁决 + 白名单校验点基线清单 + WebAuthn/FIDO2 + 邮件验证码 + 外部服务评估）、可信设备（设备指纹/豁免语义/撤销条件）。回答"二期做什么、怎么与一期契约共存"。W12-impl ~ W15-impl 的直接设计输入。

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
- `01-architecture-baseline.md` 回答"MFA 如何改造登录流程、核心对象职责、数据模型、API 契约、与既有机制的关系"（一期基线）。
- `02-mfa-phase2-design.md` 回答"二期四主题（操作级/角色策略/因子扩展/可信设备）如何设计与一期契约共存"（二期设计，未实施）。
- 本目录不记录实现过程、迁移日志、测试结果；这些进入 `ai-dev/logs/`、`ai-dev/plans/` 或 `ai-dev/analysis/`。

## 阅读顺序建议

新读者按 00 → 01 → 02 顺序阅读；只关心"短信验证码登录怎么加"的读者直接读 `01-architecture-baseline.md` 的"短信验证码登录与存储设计"一节；只关心"二次验证怎么加"的读者读"MFA 登录流程"一节；关心二期主题（操作级 MFA/角色策略/WebAuthn/邮件码/可信设备）的读者读 `02-mfa-phase2-design.md` 对应主题小节（各小节五段结构自包含）。
