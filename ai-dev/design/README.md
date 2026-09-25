# Design Docs Index

## Purpose

`ai-dev/design/` is the curated home for Nop Entropy's architecture decisions, usage contracts, and requirement specs that serve as the **attractor layer** for AI-driven development.

This subtree carries:

- **governing principles** that define the stable direction of the architecture
- **subsystem design docs** that record architecture decisions, rejected alternatives, usage contracts (meter names, API naming, module boundaries), and requirement specs
- **cross-cutting constraints** that apply across subsystems

This subtree does not carry execution history, migration diaries, rejected alternatives presented as historical narrative, or code-level details. Put those in `ai-dev/analysis/`, `ai-dev/plans/`, `ai-dev/logs/`, or `ai-dev/discussions/`.

Design docs must explain why the current design exists, what constraints it preserves, and what nearby misreadings it rejects. The rule is not "conclusion only". The rule is "current-design rationale only".

## Hierarchy

### 1. Writing Guide

- `00-design-writing-guide.md`

Role: defines what belongs in design docs, what does not, and the pseudocode judgment standard.

### 2. Cross-Cutting Constraints

- `code-quality/checkstyle-configuration.md` — static analysis rules
- `self-contained-design.md` — **自完备设计（平台级）**：核心能力自建，不引入外部大型库/系统作为能力源或架构支柱（禁 jdt.ls 类语言服务器/外部引擎包装；允许适配层内收敛的单一职责工具库与窄桥接数据点）；含架构支柱测试与既有合规证据（nop-treesitter/nop-lint/nop-code/XLang）

### 3. Subsystem Design

Each subsystem directory contains architecture decisions and usage contracts for a bounded area. Subsystems with a `README.md` define their own internal layering and reading order.

| Directory | Subsystem | README | Status |
|-----------|-----------|--------|--------|
| `nop-ai-gateway/` | AI 网关（dialect 转换 + 账号 failover） | [README](nop-ai-gateway/README.md) | 草案 — 01-architecture（dialect 双向转换委托）+ 02-account-failover-requirement（透明账号切换需求规格） |
| `nop-ai-agent/` | AI Agent DSL + Engine | [README](nop-ai-agent/README.md) | active — 8-layer structure (Vision / Architecture Baseline / Execution Model / DSL / Engine / Semantics / Strategy / Vision) |
| `nop-ai/` | nop-ai 模块组跨模块契约（文件操作抽象 + code-analyzer 边界） | [README](nop-ai/README.md) | active — IFileOperator vs IToolFileSystem 双抽象边界契约（P2-MA1-012 裁定：保持 + forRemoval=true + 迁移前置条件）；nop-ai-code-analyzer 模块职责边界（P3-MA1-014 裁定：不拆模块 + maven 包内部子域 + git 包公共面） |
| `nop-job/` | Job Scheduling | [README](nop-job/README.md) | active — AGE owner-doc (Vision / Architecture Baseline / Execution Strategy / Observability / Cluster) |
| `nop-code/` | Code Indexing & Semantic Analysis | [README](nop-code/README.md) | active — AGE owner-doc (Vision / Architecture Baseline / Query / Analysis / Integration) |
| `nop-refactor/` | 代码修改工具链（AI-First refactor / codemod） | [README](nop-refactor/README.md) | Vision active + Baseline 草案 — AI 一等用户原则 / GraphQL-first 非 LSP / self-verification 反馈载荷 / 读(nop-code)查(nop-lint)改(refactor) 三件套闭环 / 自完备能力源自建 / 复杂度预算不超 nop-lint 量级 + 性价比门（P0 codemod 面、P1 rename、结构变换类 out）；实现未启动，逐操作立项前为模块拓扑与契约权威 |
| `nop-stream/` | Stream Processing | [README](nop-stream/README.md) | active — AGE 8-layer structure (Vision / Architecture Baseline / Core Model / Graph & Execution / Checkpoint / State & Time / Integration / Reference) |
| `nop-wf/` | Workflow Engine | [README](nop-wf/README.md) | active — 审批流核心模式 + 扩展机制（AI 审批 / 调度器 / 离职转办 / 票签策略 / 动态审批） |
| `maker-checker/` | 通用 Maker-Checker（四眼原则）机制 | [README](maker-checker/README.md) | active — AGE owner-doc（Vision / Architecture Baseline / 快照与嵌套数据 / 待审互斥与并发 / 审核页对比契约）；补全平台既有 maker 侧骨架的 checker 侧目标架构，ORM 变更待 plan-first 落地 |
| `nop-core/` | Core Reflection & Type System | [README](nop-core/README.md) | active — record 类型支持设计 |
| `nop-ai-shell/` | AI Shell Command Execution | [README](nop-ai-shell/README.md) | active — AGE owner-doc (Vision / Architecture Baseline / IO & Pipeline / Executor & Async / Bash Syntax). Supports optional fallback to OS shell via `nop-shell` |
| `nop-nosql/` | NoSQL Data Access | [README](nop-nosql/README.md) | active — business-semantic NoSQL abstraction, Redis driver comparison |
| `nop-sys/` | System Infrastructure (`sys-event`, compact ext field) | [README](nop-sys/README.md) | active — AGE owner-doc for `nop-sys` subsystem design; current baseline covers `sys-event` storage split, reliable broadcast, partitioned simple-event consumption, and compact ext field modeling |
| `nop-metadata/` | Metadata Catalog & BI Semantic Layer | [README](nop-metadata/README.md) | draft — 联邦式元数据管理层（模块版本管理 / ORM 拆解 / BI 语义层 / 血缘 / 质量）。ORM 模型已落地（`nop-metadata/model/nop-metadata.orm.xml`，21 实体，8 子模块 BUILD SUCCESS） |
| `xlang-scope-access-design.md` | XLang Scope 变量显式访问 | — | active — `$scope.x` / `$scope.x = expr` 语法决策与局部实现锚点 |
| `xdef-declarative-validation-design.md` | XDef 描述式校验（声明式约束规则） | — | 草案 — 盘点已声明未实现的 `xdef:check-*` / `xdef:def-type` 约束族；两阶段校验架构（XDslValidator 局部校验 + XDefConstraintValidator 文档级约束）；check-ref target 语义修订；P0/P1/P2 落地收敛路径 |
| `nop-gateway/` | Gateway Dynamic Configuration & Hot-Reload | [README](nop-gateway/00-dynamic-configuration-design.md) | active — Hybrid XDSL+Admin API config model, AtomicReference-based hot swap, two-phase update protocol, per-route filter chain reload, provider credential rotation, DB-backed versioning |
| `crud/crud-api-codegen-design.md` | CRUD API 代码生成 | — | 草案 — ICrudApi 泛型接口 + codegen 模板 + Input/Output 决策 |
| `word-editor/` | Online Word Editor Model | *(not yet created)* | active |
| `render-mode-switch-design.md` | 前端渲染模式全局切换（AMIS↔Flux） | — | 草案 — 通过 `web.xlib` 入口代理 + `x:post-extends` 实现 `nop.web.render-mode` 全局开关 |
| `nop-ai-channel-integration-design.md` | 外部信道业务集成抽象（飞书/钉钉等） | — | active — 三层信道模型（传输 `IChannelConnector` / 业务消息 `IChannelMessageService`）；`IMessageService` 复用决策；扫码绑定 `IChannelBindProvider`+`IChannelBindService` / 扫码登录经 `ILoginSpi`；模块归属 |
| `nop-credential/` | 加密凭证库（Credential Vault） | [README](nop-credential/README.md) | active — 独立可复用模块（api/dao/meta/service/web）；复用 `nop-commons` `AESTextCipher`（AES-256-GCM + PBKDF2 + 版本化密文 `v1:`）包装为 `cv1:{keyId}:{v1密文}` 多密钥格式；类型注册表（`*.credential-type.xml`）+ 实例加密存储；`ICredentialProvider` 服务端唯一解密点 SPI（明文边界结构性强制）；配置加密复用既有 `@sec:`（拒绝 `@credential:` resolver，见 baseline §4）。二期设计已定稿并落地（`02-phase2-design.md`：出站 OAuth 客户端引擎 / KMS 材料交付模式 / 归属统一 scope=system\|user（不做租户隔离）/ 凭证级 RBAC 授权）。迁移二期设计已定稿（`03-integration-metadata-migration-design.md`：nop-integration 配置声明 credentialId + 发送期惰性解析 / nop-metadata JSON 内 credentialId 键 + 单点解析 / 横切 fail-closed 契约与迁移工具语义），待 W16-impl 实施 |
| `nop-report/` | 报表引擎 PDF 导出（nop-report-pdf） | — | active — PDF 字体策略（base-14 → VFS `/fonts/` → 字体目录自动发现含 TTC；字形逐字符回退 CJK 字体；`nop.err.report.pdf.font-*` 快败带配置指引）；环境前提：无系统字体的 CI 需安装 CJK 字体包；`PDType0Font` 严禁跨文档缓存。详见 [pdf-font-strategy.md](nop-report/pdf-font-strategy.md) |
| `nop-auth/` | MFA 多因子验证（短信登录 + TOTP） | [README](nop-auth/README.md) | active — 不新增模块，改造 nop-auth 登录流程；一期已落地（两阶段 challenge / loginType=5 短信登录 / TOTP + 恢复码 / store 三实现默认 db）；二期设计已定稿（`02-mfa-phase2-design.md`：操作级 MFA（@MfaRequired + executor 拦截 + 一次性票）/ 角色级强制策略（minMfaLevel + 受限会话）/ 因子扩展（MfaType 保持常量类 + WebAuthn 多 credential + EmailCodeStore）/ 可信设备（请求头指纹 + 固定窗口豁免）），待 W12-W15-impl 实施 |
| `nop-plugin/` | Plugin System Enhancement（吸收 Cordis 思想） | [README](nop-plugin/README.md) | 草案 — 加载/激活两态分离 + revertible effects 系统化（IPluginScope）+ reactive coeffect 条件激活 + HMR；不改 nop-ioc，复用子容器模式 |
| `xlang-execution/` | XLang 执行子系统统一架构（三后端：解释器 / java / truffle） | [README](xlang-execution/README.md) | active — AGE owner-doc (Vision / Architecture Baseline)：三后端分工原则、选择机制与降级链、后端注册 SPI、三后端对拍框架、模块边界与依赖方向 |
| `xlang-java/` | nop-xlang-java 转译器后端（构建期 Executable→Java） | [README](xlang-java/README.md) | active — Architecture Baseline（转译器结构 / 137 节点映射 / SourceLocation 保真 / 生成类优先加载 / `_gen/` 构建任务 / EvalMethod 约定）；Vision 归 xlang-execution |
| `xlang-truffle/` | XLang Truffle 执行后端（JVM 部署形态提速，多线程） | [README](xlang-truffle/README.md) | active — AGE owner-doc (Vision / Architecture Baseline / 知识参考层)：XLangLanguage/Context、帧/slot 映射、Context 池 + SHARED 多线程、两级内联缓存、依赖钉版；01 为外部框架知识速查（不承载决策） |
| `nop-network/` | 网络外围模块（HTTP 客户端 / MQTT） | [README](nop-network/README.md) | active — IHttpClient 跨实现行为等价契约；文件传输（上传双模式 / Range 断点续传 / sha 校验优先级）；MQTT 下行推送 + 订阅路由 + IMessageService 双向桥 |

## Precedence Model

1. **Nop platform principles** (reversible computation, model-first, delta customization) take top-level precedence. These are codified in `docs-for-ai/00-start-here/ai-defaults.md` and the platform's theoretical foundations.
2. **Subsystem design docs** keep local precedence inside their own subject area. For example, `nop-job/invoker-design.md` owns the routing contract for job invokers.
3. When two subsystem designs make conflicting claims about a shared boundary, the conflict must be resolved by updating the docs — the older or less precise doc yields.
4. `docs-for-ai/` owns platform usage knowledge (API, conventions, development patterns). `ai-dev/design/` owns architecture decisions for platform development. When both cover the same topic, `docs-for-ai/` is the usage-facing source of truth; `ai-dev/design/` is the decision-facing source of truth.

## `ai-dev/archived/` 目录结构

历史计划归档在 `ai-dev/archived/` 下按月分片（如 `2026-06`），按 `Completed` 日期确定月份目录，文件名（`NN-描述.md`）保持不变。详见 `ai-dev/plans/00-plan-authoring-and-execution-guide.md` 规则 #28。

## Relationship to `docs-for-ai/`

| `docs-for-ai/` | `ai-dev/design/` |
|----------------|-------------------|
| Platform **usage** knowledge: how to use Nop to build apps | Platform **development** decisions: how and why Nop is built this way |
| API, conventions, development patterns, runbooks | Architecture decisions, usage contracts, requirement specs |
| Source of truth for app developers | Source of truth for platform developers |
| Normative — describes what works today | Normative — describes why it works this way and what it must preserve |

## Relationship to `docs/`

`docs/` is human-written historical documentation (tutorials, theory, API docs). It is read-only for AI development purposes. `ai-dev/design/` does not replace or duplicate `docs/`; it records architecture decisions that `docs/` was never designed to capture.
