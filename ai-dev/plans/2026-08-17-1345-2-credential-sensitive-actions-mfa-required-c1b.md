# C1b 凭证库敏感动作 @MfaRequired 标注（操作级 MFA 接入凭证库）

> Plan Status: active
> Mission: nop-credential-mfa
> Work Item: C1-hardening Part B（@MfaRequired 标注小 plan），A1-audit successor 收口（roadmap 建议拆分 C1a/C1b 中的 C1b）
> Last Reviewed: 2026-08-17（draft review 两轮达成共识：首轮 1 Major（owner doc 既有章节改写目标）+ 7 Minor 全部处置，复审确认全部落地且无新缺陷，裁定可执行；非阻塞建议（logs 钩子/受限会话措辞/Item Types/负例跨文件注记）已顺手纳入。审查 session：ses_ff1bada09ffeZ0ONiJJQQzCaq0 / ses_ff1aca46affeoXruyjEa7rYhE2）
> Source: `ai-dev/audits/2026-08/2026-08-17-0518-deep-audit-nop-credential/adjudication.md` §二#4（@MfaRequired 路由终局裁定 + 标注清单缩窄裁决 + 依赖边结论）；`ai-dev/backlog/nop-credential-mfa-roadmap.md` C1-hardening 条目
> Related: W12-impl `2026-08-16-2321-2-mfa-operation-level-stepup.md`（@MfaRequired 机制交付方）；C1a `2026-08-17-1345-1-credential-phase2-audit-hardening-c1a.md`

## Purpose

按 A1-audit 终局裁定，为凭证库四个敏感管理动作标注操作级 MFA（`reencryptAll`/`delete`/`NopCredentialAuth.grant`/`revoke`），打通 `nop-credential-service → nop-biz-auth-api` 的 API-only 依赖边，使部署侧启用操作级 MFA 时凭证库敏感动作纳入会话内二次验证；未启用时零介入零破坏。

## Current Baseline

（2026-08-17 live repo 核对）

- **操作级 MFA 机制已交付（W12-impl done）**：`@MfaRequired` 注解（`io.nop.auth.api.mfa.MfaRequired`，METHOD 级/RUNTIME 保留）与 `IOperationMfaChecker` SPI 位于 `nop-service-framework/nop-biz-auth-api`（该模块仅依赖 `nop-api-core`——**API-only 干净边**，A1 adjudication §二#4 已核实裁定可加）。executor 检查点在 `nop-graphql-core` `GraphQLExecutor`（checker 为 null 或 `nop.auth.operation-mfa.enabled=false` 时零介入）；元数据经 `ReflectionBizModelBuilder` 读取注解并做构建期约束（`@BizSubscription` 组合拒绝、`publicAccess=true` 组合拒绝，fail-fast）。
- **标注清单已缩窄裁定（A1 §二#4）**：应标注 = `NopCredentialBizModel.reencryptAll`（全量敏感）、`NopCredentialBizModel.delete`（数据级不可逆）、`NopCredentialAuthBizModel.grant`/`revoke`（权限变更）；**不标注** = `saveCredential`（高频用户操作，强制 MFA 损害 UX；明文写入口已有写分级 + 归属校验两层）、`beginOAuthFlow`（state 一次性绑定已防劫持，动作仅返回 URL 不泄密）。
- **目标方法 live 锚点**：`nop-credential-service/.../entity/NopCredentialBizModel.java`（`reencryptAll` :571、`delete` :668）、`.../entity/NopCredentialAuthBizModel.java`（`grant` :103、`revoke` :137）。四方法均为 `@BizMutation` 且非 `publicAccess`（构建期约束可满足）。（行号为 2026-08-17 快照，执行时以符号定位为准——C1a 先行会改动 NopCredentialBizModel 行号。）
- **owner doc 现状（关键）**：`docs-for-ai/03-modules/nop-credential.md` **已存在**"敏感操作标注（@MfaRequired，W12 机制可用）"章节（约 :333-347），当前声明"本模块动作尚未标注…deferred 至 A1-audit"——本计划落地后该章节必须**改写**（非新增并列章节），否则构成 owner-doc drift。`docs-for-ai/03-modules/nop-auth.md` 操作级 MFA 章节的"首批标注"段含跨模块清单句"凭证库模块标注 deferred（owner 裁定链，见 roadmap）"，落地后同样**必须更新**（无条件项，非可选）。
- **依赖边现状**：`nop-credential/nop-credential-service/pom.xml` 现直接依赖零 nop-auth 系构件；`nop-biz-auth-api` 经 `nop-biz → nop-graphql-core → nop-biz-auth-api`（全 compile）**已在 classpath 传递可达**——本次 pom 变更语义是"将既有传递边显式化"（Maven 依赖卫生，声明直接依赖而非隐式传递），非新增运行时耦合；版本经 nop-bom dependencyManagement 以 version-less 声明。
- **前置语义**：生效需部署侧 `nop.auth.operation-mfa.enabled=true` + 用户 MFA 启用 + checker SPI bean 装配（`auth-service.beans.xml` 注册 `OperationMfaCheckerImpl`，enabled 开关判定在 checker 实现内部；executor 侧只判 checker 是否装配）。三者任一不满足时四动作行为与当前完全一致（W12 可选语义）。
- **先例测试**：`TestMfaRequiredMetadata`/`TestOperationMfaExecutorWiring` 位于 `nop-graphql-core` 的 **test 树**（不可 import，只能复制模式）；`nop-credential-service` 经 compile 传递已具备 `ReflectionBizModelBuilder`/`GraphQLEngine`/`MfaRequiredMeta` 类可见性，且本模块自有容器级先例 `TestNopCredentialAuthBizModel`（@NopTestConfig + @Inject IGraphQLEngine）可直接承载元数据断言与零介入回归——**元数据断言优先容器级（经 xmeta/merge 管线后仍存活），纯反射级不足以证明接线**。
- **测试基线**：`./mvnw test -pl nop-credential -am` 全绿（service 171 / kms-vault 32 / web 1）。

## Goals

- 四个敏感动作携带 `@MfaRequired` 元数据且经 GraphQL 元数据构建可观察（mfaRequiredMeta 存在）。
- 依赖边 `nop-credential-service → nop-biz-auth-api` 落地且不引入 nop-auth-service 运行时耦合（pom 层面 API-only）。
- 未部署 checker / 未启用配置时零介入：容器可启动、四动作行为不变（可选语义回归锚点）。
- owner doc 声明标注清单、部署前置与缩窄裁定理由。

## Non-Goals

- 不实现新的 MFA 因子或 checker 逻辑（W12-impl 已交付，本计划只做标注消费）。
- 不标注 `saveCredential`/`beginOAuthFlow`（缩窄裁定，防止 UX 损害与无增益挑战）。
- 不改四方法签名、错误码、action-auth roles（与 C1a Phase 1 D4-02 的 roles 对齐互不冲突——C1a 先行时以 C1a 结果为基线）。
- 不做 nop-ai 侧或 nop-integration 侧动作标注（无审计裁定输入，如需扩展另立 plan）。

## Scope

### In Scope

- `nop-credential/nop-credential-service/pom.xml`（新增 nop-biz-auth-api 依赖）
- `NopCredentialBizModel`（reencryptAll/delete 标注）、`NopCredentialAuthBizModel`（grant/revoke 标注）
- `nop-credential-service` 测试目录（元数据传播 + 零介入回归用例）
- `docs-for-ai/03-modules/nop-credential.md`（**改写**既有"敏感操作标注"章节）、`docs-for-ai/03-modules/nop-auth.md`（操作级 MFA 跨模块清单句更新）

### Out Of Scope

- C1a 全部标的（代码加固）
- executor/checker 机制本身、`nop.auth.operation-mfa.enabled` 配置语义
- Web 页面交互变化（挑战失败的前端提示走 W12 既定错误码通道）

## Execution Plan

### Phase 1 - 依赖边 + 四动作标注 + 元数据验证

Status: planned
Targets: `nop-credential/nop-credential-service/pom.xml`、`.../entity/NopCredentialBizModel.java`、`.../entity/NopCredentialAuthBizModel.java`、测试目录

- Item Types: `Fix | Proof`

- [ ] pom 新增 `nop-biz-auth-api` 依赖（compile scope，版本走父 pom dependencyManagement 或显式 2.0.0-SNAPSHOT——对齐仓库既有惯例）
- [ ] 四方法标注 `@MfaRequired`（javadoc 注记来源 A1-audit §二#4 缩窄裁定与生效前置）
- [ ] 新增元数据级测试（复制 `TestMfaRequiredMetadata` 模式，优先容器级——对齐本模块 `TestNopCredentialAuthBizModel` 的 @NopTestConfig + IGraphQLEngine 先例，确保元数据经 xmeta/merge 管线后仍存活）：四方法 mfaRequired 元数据经 BizModel 构建传播可观察；未标注方法零元数据负例至少三个——`saveCredential`/`maskList`（NopCredentialBizModel）+ `beginOAuthFlow`（位于 `CredentialOAuthApiBizModel`，负例断言跨两个 BizModel 文件）
- [ ] 新增零介入回归测试（对齐 `TestOperationMfaExecutorWiring` 先例或容器级）：无 checker 装配时容器启动成功且四动作调用路径不触发操作级检查（可选语义不破坏）
- [ ] 文档裁定：No owner-doc update required（本 Phase 不改文档，统一 Phase 2 回写）

Exit Criteria:

- [ ] 元数据断言用例覆盖四标注方法 + 至少三个未标注方法（负例）
- [ ] **接线验证**：元数据并非仅注解存在——经 GraphQL/BizObject 构建管线（含 xmeta merge）后 `mfaRequiredMeta` 可观察（容器级断言，纯反射级不满足本条）
- [ ] **无静默跳过**：未装配 checker 时行为差异为零（回归锚点断言），启用路径不在本模块内自造 stub
- [ ] 构建期约束不触发（四方法无 publicAccess/subscription 组合，容器/元数据构建成功即证明）
- [ ] `./mvnw test -pl nop-credential -am` 全绿
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - owner doc 同步 + 收口

Status: planned
Targets: `docs-for-ai/03-modules/nop-credential.md`、`docs-for-ai/03-modules/nop-auth.md`、roadmap

- Item Types: `Fix | Follow-up`

- [ ] **改写** `nop-credential.md` 既有"敏感操作标注（@MfaRequired，W12 机制可用）"章节（非新增并列章节）：四动作清单、部署前置（enabled 配置 + 用户 MFA 启用 + checker 装配）、缩窄裁定理由（saveCredential/beginOAuthFlow 不标注）、依赖边说明（显式化既有传递边，API-only）。**措辞注意**：零介入表述须对齐 `nop-auth.md` 受限会话分层语义（受限会话拦截不受操作级 enabled 开关门控），不得照抄"enabled=false 零介入"单一措辞
- [ ] 更新 `nop-auth.md` 操作级 MFA 章节"首批标注"段的跨模块清单句（"凭证库模块标注 deferred"已过期，改为指向四动作落地）
- [ ] `./mvnw test -pl nop-credential -am` 复跑全绿；`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-credential --severity high` 退出码 0
- [ ] 独立 closure audit（fresh subagent）+ Closure Evidence 写入

Exit Criteria:

- [ ] 文档内容与 live 行为一致（清单/前置/语义可对照代码验证）
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [ ] 独立 closure audit 通过且证据写入本 plan Closure 段
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [ ] 四动作标注落地且元数据可观察（非仅源码注解存在）
- [ ] 依赖边显式化且 API-only（pom 直接声明 version-less；依赖树核实无 nop-auth-service 传递引入——既有 nop-biz 传递链不含该工件）
- [ ] 零介入语义回归通过（未启用时四动作行为与标注前一致）
- [ ] 缩窄裁定不被执行期静默放宽（saveCredential/beginOAuthFlow 保持未标注）
- [ ] owner docs 已同步
- [ ] 独立子 agent closure audit 已完成并记录证据
- [ ] `./mvnw test -pl nop-credential -am` 通过
- [ ] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

（起草时空缺——执行中产生的延期项按 Anti-Slacking 规则填充。）

## Non-Blocking Follow-ups

- 凭证库 Web 管理页对 `ERR_AUTH_MFA_OPERATION_REQUIRED` 的前端引导体验（W12 既定错误码通道，前端增强按需求另起）
- nop-ai / nop-integration 侧敏感动作标注扩展（无审计裁定输入，需求出现时另立 plan）

## Closure

Status Note: （关闭时填写）
Completed: （关闭时填写）

Closure Audit Evidence:

- Reviewer / Agent: （关闭时填写）
- Evidence: （关闭时填写）

Follow-up:

- （关闭时填写；confirmed live defect 不得出现在这里）
