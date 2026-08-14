# nop-credential 设计文档

> Status: active
> Created: 2026-08-10
> Updated: 2026-08-14（二期设计文档条目纳入阅读顺序）

本目录按 AGE（Attractor-Guided Engineering）owner-doc 模式组织，承载 `nop-credential` 子系统的架构决策：加密凭证库的模块边界、加密方案、数据模型与使用契约。

## 文档结构与阅读顺序

### 必读路径

1. `00-vision.md`
   - 凭证库的产品定位、成功标准、显式 non-goals、设计收敛路径。回答"凭证库做什么、不做什么、凭什么判断成功"。

2. `01-architecture-baseline.md`
   - 系统分层（复用 nop-commons 密码学原语 + `nop-credential` 凭证库层）、核心对象职责契约、模块边界、加密方案、数据模型、API 契约、关键设计决策。回答"凭证库如何组成、对象间如何协作"。

3. `02-phase2-design.md`
   - 二期四主题设计（OAuth 流程引擎（出站客户端）/ 外部 KMS/HSM 集成 / 凭证归属统一（scope=system|user，不做租户隔离）/ RBAC 细粒度授权），每主题独立小节（设计结论/背景与动机/核心设计/拒绝了什么/与一期契约兼容性五段），含跨主题 deferred 裁定与设计→impl 映射。回答"二期四个方向各怎么做、边界在哪、一期契约如何保持"。

### 按需深入

- `docs-for-ai/03-modules/nop-sys.md` — 系统管理模块既有能力（凭证库与字典/配置的边界）
- `nop-kernel/nop-commons/src/main/java/io/nop/commons/crypto/impl/AESTextCipher.java` — 平台现有对称加密原语（v1: 版本化格式，凭证库复用其算法参数）
- `nop-core-framework/nop-config/src/main/java/io/nop/config/enhancer/DefaultConfigValueEnhancer.java` — 平台现有配置增强器（`@sec:` 配置值解密，凭证库与其互操作）
- `nop-ai/model/nop-ai.orm.xml` — `NopAiModel.apiKey` 明文列现状（凭证库迁移消费方）
- `nop-metadata/model/nop-metadata.orm.xml` — `NopMetaDataSource.connectionConfig`（`tagSet="sensitive"` 标记现状）

## 职责边界

- `00-vision.md` 回答"凭证库的边界是什么"。
- `01-architecture-baseline.md` 回答"凭证库如何分层、核心对象各自职责、依赖方向、加密与存储策略"。
- `02-phase2-design.md` 回答"二期四主题（OAuth/KMS/归属统一/RBAC）的设计裁决与一期契约兼容性"。
- 本目录不记录实现过程、迁移日志、测试结果；这些进入 `ai-dev/logs/`、`ai-dev/plans/` 或 `ai-dev/analysis/`。

## 阅读顺序建议

新读者按 00 → 01 顺序阅读；只关心"能不能用、怎么用"的读者直接读 `01-architecture-baseline.md` 的 API 契约一节。
