# 1 P2 安全加固批次（MA3.2 + MA6.2 + MA6.5 + MA5.5 + MA6.1 安全类 P2）

> Plan Status: completed
> Last Reviewed: 2026-07-31
> Source: `ai-dev/backlog/audit-remediation-roadmap.md` §P2/P3 Deferred Successors（watch-only residual，按严重度排序另行规划）、`ai-dev/audits/2026-07-31-1550-arm-MA3.2-nop-ai-security.md`、`2026-07-31-arm-MA6.2-nop-ai-agent-security.md`、`2026-07-31-arm-MA6.5-nop-ai-chat-prompt-security.md`、`2026-07-31-arm-MA5.5-nop-ai-sensitive-leak.md`、`2026-07-31-1240-arm-MA6.1-nop-ai-llm-config-security.md`、`2026-07-31-arm-MA5.4-nop-ai-design-drift.md`
> Related: `ai-dev/plans/2026-07-31-1300-5-arm-mr3-fix.md`（MR3 已修复 P1 对应项）、`ai-dev/plans/2026-07-31-1446-2-arm-ma4-p2-code-quality.md`、`ai-dev/plans/2026-07-31-1446-3-arm-ma4-p2-test-quality.md`

## Purpose

修复 MA3.2/MA6.2/MA6.5/MA5.5/MA6.1 审计中遗留的**安全类 P2 finding**（均经 live repo 复核仍成立）：shell 命令过滤默认 no-op、内容安全 guardrail 无生产实现、xbiz 无权限属性、响应缓存无 TTL、forkSession 无消息过滤、凭证样例残留、logMessage 默认开启。这是 roadmap 规则 1 下 P2/P3 deferred successor 的第三批（安全优先），按严重度排序承接 watch-only residual。

## Current Baseline

- MR3（`2026-07-31-1300-5-arm-mr3-fix.md`）已修复全部 P0/P1：SSRF 防御（HttpRequestExecutor/GraphqlQueryExecutor `validateUrl`/`isPrivateIp`）、`LocalToolFileSystem.isPathAllowed()` 接线、`BashExecutor.validateCommand()` + `DESTRUCTIVE_COMMAND`（live 确认在 `BashExecutor.java:34,57,145-149`）、`DefaultPathAccessChecker` 路径遍历防御、引擎层会话鉴权（`SessionIds.requireValidIdentifier`）、`DefaultAiChatExchangePersister` 可选 AES 加密、`DefaultChatLogger` 凭据脱敏、Gemini apiKey 走 `x-api-key` header。
- **P2-MA3-023 / MA6.2-AR-5（live 确认）**：`nop-ai-shell/.../checker/DefaultCommandChecker.java:5-11` `check()` 恒返回 `null`（=全部放行）；`ShellCommandExecutor` 两参构造器 `this(registry, null, null, fileSystem)` 显式传 null checker，`execute()` 中 `if (checker != null)` 跳过整个过滤路径。`TestCommandChecker.testDefaultCommandCheckerAllowsAll` 当前断言 `rm -rf /` 也被放行。
- **MA6.2-AR-6 / MA5.4-P2-2（live 确认）**：`IContentGuardrail` 只有 `NoOpContentGuardrail` 一个实现（`guardrail/NoOpContentGuardrail.java`，恒 Pass）；`DefaultAgentEngine.Builder` 默认 `NoOpContentGuardrail.noOp()`（`DefaultAgentEngine.java:465,630`）。设计文档 `ai-dev/design/nop-ai-agent/nop-ai-agent-security-and-permissions.md` §5.2 声称 4 个预构建 guardrail（PromptInjectionGuardrail 等），代码零实现。
- **MA6.2-AR-7（live 部分确认）**：`DefaultToolAccessChecker` 仅按工具名 deny-list；MR3 已为 Bash/Http/Graphql/文件工具补校验，但 `AskOracleExecutor` 等在无 `ORACLE_ENDPOINT` 时静默返回第一个 option（stub 语义，见 P2-MA1-011 契约计划），`SearchContentExecutor`/`SearchEngineExecutor` 等无输入校验。
- **P2-MA3-026（live 确认）**：42 个 xbiz 文件零 `rights=`/`roles=` 属性、零自定义 action（`NopAiChatResponse.xbiz` 仅 `<actions/>` 空壳）；MR2 已为 4 个自定义 BizModel 补 `@Auth`（`<BizObjName>:<action>`），但 CRUD 继承链 xbiz 层无声明式权限。
- **MA6.5-AR-7（live 确认）**：`nop-ai-core/.../persist/DefaultAiChatResponseCache.java` `loadCachedResponse/saveCachedResponse` 无 TTL、无过期、无时间戳字段。
- **MA6.5-AR-8（live 确认）**：`FileBackedSessionStore.forkSession()`（:261-276）/`InMemorySessionStore.forkSession()`（:78）/`DBSessionStore.forkSession()`（:336）`inheritContext=true` 时无过滤整体复制父会话全部消息。**注意现状**：`ISessionStore.forkSession` 接口默认实现是 `UnsupportedOperationException`（:125-126），三个 store override 提供实际 fork——"默认全量继承"是本计划目标态而非现状，实现 hook 时需保持三 store 现有 fork 行为兼容。
- **MA5.5-AR-6（live 确认）**：`nop-ai-toolkit/src/main/resources/_vfs/nop/ai/tools/http-request.tool.xml:94-95` 示例含 JWT bearer token（`eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...`）。
- **MA5.5-AR-7（live 确认）**：`nop-ai-app/src/main/resources/application.yaml:28-32`、`nop-ai-coder/src/test/resources/application.yaml:4-8`、`nop-ai-skills/nop-ai-translate/src/test/resources/application.yaml:4-8` 存在注释掉的 MySQL 凭据块。
- **MA6.1-AR-7（live 确认）**：`AiCoreConfigs.java:17-18` `CFG_AI_SERVICE_LOG_MESSAGE` 默认 `true`。`_LlmModel.java:94` `_logMessage = true` 的默认值定义在 `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/ai/llm.xdef:11`（`logMessage="!boolean=true"`，xdef 模型默认值，**跨出 nop-ai 模块组**，改动属平台内核变更）。MR3 已给 `DefaultChatLogger` 加凭据脱敏，但全量对话日志仍默认开启。
- **MA6.5-AR-9（live 确认，同 audit P1 发现）**：`nop-ai-core/.../service/ChatLogHelper.java:40-50` `makeSessionId()` 将 caller 提供的 sessionId 直接拼入文件路径，无路径穿越校验（MR3 只修了 nop-ai-agent 侧 `SessionIds.requireValidIdentifier`）。此 finding 未出现在 arm-index P1 表（MV 矩阵缺口），本计划承接修复并在 arm-index 补记。
- **MA5.4-P2-3（live 确认）**：`ISensitivePathProvider` 接口在设计中定义（`nop-ai-agent-security-and-permissions.md` §7.2，:621），代码中不存在；敏感路径硬编码在 `DefaultPathAccessChecker.SENSITIVE_PREFIX_PATTERNS`/`SENSITIVE_FILENAMES`。
- **MA5.4-P2-1 / P2-5（live 确认）**：设计文档声称 DashScope dialect 已实现（llm-layer.md §4.3）、`IApprovalChannel` 已抽象（security-and-permissions.md §6.1），代码中均不存在。
- 全量基线：`./mvnw test -pl nop-ai -am -T 1C` 绿（3444 tests / 0 failures，2026-07-31 记录）。

## Goals

- shell 命令过滤默认启用：`DefaultCommandChecker` 具备默认 deny-list（危险命令/危险 flag 拒绝），`ShellCommandExecutor` 默认装配非 null checker，null checker 时 WARN。
- 内容安全 guardrail 有生产实现：至少落地 `PromptInjectionGuardrail` 并作为可启用默认；NoOp 默认时 WARN（非 INFO）。
- 缓存/会话数据安全：`DefaultAiChatResponseCache` 支持 TTL 过期；`forkSession` 支持消息过滤 hook。
- 权限与配置卫生：xbiz 权限属性问题给出裁定并落地最小实现；JWT 示例 token 与注释凭据清除；`logMessage` 默认裁定。
- 设计文档 drift 收敛：DashScope / IApprovalChannel / guardrail / ISensitivePathProvider 四处设计-代码不一致给出明确裁定（实现或文档修正），消除"声称已实现"误导。

## Non-Goals

- 不修复 P3 finding（MA5.4-P3-1/2/3、MA6.1-AR-8 vault 集成等）——记入 Non-Blocking Follow-ups。
- 不做全量安全重架构（MR3 已裁定 out-of-scope）。
- 不实现 DataAuth 全量（P2-MA3-025 为架构级，本计划只给裁定）。
- 不迁移 legacy `IAiChatService` API 面（P2-MA3-04 属契约清理批次）。
- **不改 `nop-kernel/nop-xdefs/.../ai/llm.xdef` 默认值**（平台内核保护区）；logMessage 默认只改 `AiCoreConfigs` 全局配置默认 + 文档声明 per-model 覆盖。
- 不修 nop-ai-agent 侧会话存储路径校验（MR3 已修，`SessionIds.requireValidIdentifier` 在位）；本计划只补 nop-ai-core 侧 `ChatLogHelper`。

## Scope

### In Scope

- `nop-ai-shell`：`DefaultCommandChecker` 默认 deny-list + `ShellCommandExecutor` 默认装配 + WARN 日志。
- `nop-ai-agent`：`PromptInjectionGuardrail` 实现；NoOp 默认 WARN；`forkSession` 消息过滤 hook；`ISensitivePathProvider` 可配置化裁定。
- `nop-ai-core`：`DefaultAiChatResponseCache` TTL；`logMessage` 默认值裁定。
- `nop-ai-toolkit`：`http-request.tool.xml` JWT 示例清除；工具执行器输入校验缺口扫描与修补（AR-7 残余）。
- 配置文件：3 个 application.yaml 注释凭据块清除。
- xbiz 权限：`rights`/`roles` 属性裁定 + 最小落地（自定义 action 面）。
- 设计文档：`ai-dev/design/nop-ai-agent/` 下 4 处 drift 裁定（实现或改文档）。

### Out Of Scope

- 全量 DataAuth 实现（P2-MA3-025）。
- vault/secret-store 集成（MA6.1-AR-8）。
- legacy API 迁移（P2-MA3-03/04/05/06/08，契约批次）。
- 测试隔离 static state（MA5.6-AR-2/3，可靠性批次）。
- 所有 P3 finding。

## Execution Plan

### Phase 1 — Shell 命令过滤默认启用（P2-MA3-023 / MA6.2-AR-5）

Status: completed
Targets: `nop-ai/nop-ai-shell/src/main/java/io/nop/ai/shell/checker/DefaultCommandChecker.java`、`nop-ai/nop-ai-shell/src/main/java/io/nop/ai/shell/executor/ShellCommandExecutor.java`、`nop-ai/nop-ai-shell/src/test/java/io/nop/ai/shell/checker/TestCommandChecker.java`

- Item Types: `Fix | Decision | Proof`

- [x] （Decision）裁定 `DefaultCommandChecker` 默认 deny-list 规则集：危险命令（`rm -rf /`、`mkfs`、`dd`、`mkfs.ext4` 等）、危险 flag、`/dev/sda` 类设备写入；拒绝语义与 `BashExecutor.DESTRUCTIVE_COMMAND` 保持一致风格。
- [x] （Fix）`DefaultCommandChecker.check()` 实现默认 deny-list：命中返回拒绝消息，未命中返回 null（保持接口契约：null=放行，String=拒绝原因）。
- [x] （Fix）`ShellCommandExecutor` 两参构造器默认装配 `new DefaultCommandChecker()`（不再传 null）；`checker == null` 分支保留但构造后 WARN。
- [x] （Fix）`TestCommandChecker.testDefaultCommandCheckerAllowsAll` 重写为 deny-list 行为断言（危险命令拒绝、普通命令放行），并补充 `ShellCommandExecutor` 默认装配的接线测试（危险命令返回 126 + 拒绝消息）。
- [x] （Proof）兼容面核验：`ShellCommandExecutorTest`（4 处两参构造器：:59/:290/:305/:350）与 `ShellConcurrencyEdgeCaseTest`（**21 处**：9 处共享 registry :74/:205/:259/:341/:349/:378/:510/:520/:521 + 12 处 localRegistry :113/:144/:166/:195/:229/:249/:286/:315/:368/:421/:463/:492）现有命令（echo/ls/failcmd/管道）不被 deny-list 误伤，测试全绿。
- [x] 全量 build + test 验证。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `DefaultCommandChecker` 拒绝危险命令（测试断言），普通命令放行（测试断言）
- [x] `ShellCommandExecutor(registry, fileSystem)` 默认装配非 null checker；危险命令经默认装配 executor 执行返回 126 + 拒绝原因（**端到端验证**：`execute()` 入口 → `checkAst` → `ExecutionResult(126,...)` 完整路径）
- [x] **接线验证**：默认构造器确实把 checker 传入 `execute()` 路径（测试覆盖），非仅类型存在
- [x] **无静默跳过**：deny-list 未覆盖的已知危险模式不允许静默放行——每次新增 deny 规则必须伴随测试
- [x] `No owner-doc update required`（`DefaultCommandChecker` 语义为 audit 已声明语义的实现，不改变 nop-ai-shell 文档契约；如裁定改变设计文档则同步）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 — 内容安全 guardrail 生产实现（MA6.2-AR-6 / MA5.4-P2-2）

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/guardrail/`、`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/DefaultAgentEngine.java`、`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/ReActAgentExecutor.java`

- Item Types: `Fix | Decision | Proof`

- [x] （Decision）裁定 `PromptInjectionGuardrail` 检测规则集（至少覆盖：已知指令注入模式如 "ignore previous instructions"、敏感命令模式、restricted path 模式）与执行语义。**注意 live 现状**：`GuardrailMode`（OFF/REPORT/ENFORCE）枚举已存在但零消费者；`IContentGuardrail.check(direction, content, ctx)` 无 mode 参数；`GuardrailResult` 只有 Pass/Block/Modify，REPORT（记录但放行）语义无表达方式。裁定必须回答：mode 如何传递到 guardrail（构造器/配置/ctx）、REPORT 如何映射到 GuardrailResult（例如 Modify+记录或 Pass+副作用），并与设计文档 §5.2 对齐或显式修订。
- [x] （Fix）实现 `PromptInjectionGuardrail implements IContentGuardrail`：按裁定规则集对 `GuardrailDirection.INPUT/OUTPUT` 内容检测，命中返回 Block 结果。
- [x] （Proof）核验 NoOp 默认 WARN 已在位：`DefaultAgentEngine.java:787-792` 已有 `LOG.warn("DefaultAgentEngine constructed with NoOpContentGuardrail...")`，`TestSecureByDefault.java:371-395` 已识别/容忍该 WARN——**无需新增实现**，仅核验记录；若核验发现缺失再补。
- [x] （Fix）为 `PromptInjectionGuardrail` 编写单元测试（命中/未命中/方向区分/enforce 语义），并在 `TestSecureByDefault` 或等价引擎测试中验证 guardrail 在 ReAct loop 中被调用（**接线验证**）。
- [x] （Decision）更新设计文档 `nop-ai-agent-security-and-permissions.md` §5.2：4 个 guardrail 逐一声明实现状态（实现/P2 已落地/未来），消除"预构建"误导（同时收敛 MA5.4-P2-2 裁定）。

Exit Criteria:

- [x] `PromptInjectionGuardrail` 存在且有测试：注入模式命中 Block、普通内容 Pass、INPUT/OUTPUT 方向行为正确
- [x] **接线验证**：ReAct loop 运行时确实调用 guardrail（引擎级测试断言 guardrail 方法被调用）
- [x] **无静默跳过**：NoOp 默认 WARN 在 `DefaultAgentEngine.java:787-792`（核验记录）；命中 guardrail 的消息不触发后续 LLM 调用/工具调用（INPUT 拦截点在 LLM 调用前，见 `ReActAgentExecutor.java:1506-1522`）
- [x] GuardrailMode/REPORT 语义裁定落盘（模式传递机制 + REPORT 映射）；**若裁定要求代码落地（如 REPORT=Modify+副作用），该语义必须有对应测试**——"裁定落盘"与"裁定要求代码则必测"两者都满足
- [x] 设计文档 §5.2 逐项声明 4 guardrail 实现状态
- [x] `ai-dev/design/nop-ai-agent/nop-ai-agent-security-and-permissions.md` 已更新（本 Phase 改变设计基线）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 — 缓存 TTL + forkSession 消息过滤（MA6.5-AR-7 / AR-8）

Status: completed
Targets: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/persist/DefaultAiChatResponseCache.java`、`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/session/FileBackedSessionStore.java`、`InMemorySessionStore.java`、`DBSessionStore.java`、`ISessionStore.java`

- Item Types: `Fix | Decision | Proof`

- [x] （Decision）裁定缓存 TTL 配置键与默认值（如 `nop.ai.service.cache-ttl`，默认 0=不过期保持兼容，或 24h）与过期语义（**读取时惰性过期，基于文件 mtime，不写入缓存文件内容**——避免破坏缓存文件格式兼容）。
- [x] （Fix）`DefaultAiChatResponseCache` 增加 TTL：`loadCachedResponse` 检查缓存条目 mtime，超 TTL 视为 miss（删除或跳过）；保存时不改动文件格式。
- [x] （Decision）裁定 forkSession 过滤 hook 的接口形态：类型（`Predicate<AiMessage>`？`BiFunction<...>`？）、注入方式（`ISessionStore` 接口新增 overload 默认方法保持兼容 vs store setter）、`DefaultAgentEngine.forkSession` → store 的传递路径（engine 必须能传 hook 到 store，否则"端到端"不成立）。
- [x] （Fix）按裁定实现 hook：`ISessionStore.forkSession` 扩展（默认全量继承保持兼容）+ 三个 store 接入 + engine 传递路径。
- [x] （Fix）测试：缓存 TTL 过期后 miss（时间可控）、未过期命中、fork 过滤生效/默认全量兼容（3 个 store 至少各 1 条）。

Exit Criteria:

- [x] 缓存 TTL：过期条目不再返回（测试），未过期返回（测试），配置默认值裁定落盘
- [x] forkSession：默认行为兼容（全量继承测试不变），注入过滤后仅继承过滤子集（**端到端验证**：engine.forkSession → store.forkSession → 子会话消息断言）
- [x] **接线验证**：过滤 hook 在 engine→store 传递路径被实际调用（引擎级测试覆盖 1 个 store 全路径 + 其余 store 单元测试）
- [x] **无静默跳过**：TTL 配置缺失时行为明确（默认值语义文档化），不静默不过期也不静默误删
- [x] `docs-for-ai/03-modules/nop-ai.md` 配置节同步新增 `nop.ai.service.cache-ttl` 键（本 Phase 新增配置键，属 owner-doc 变更）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 — 权限与配置卫生（P2-MA3-026 + MA5.5-AR-6/7 + MA6.1-AR-7）

Status: completed
Targets: `nop-ai/nop-ai-service/src/main/resources/_vfs/nop/ai/model/**/*.xbiz`、`nop-ai/nop-ai-toolkit/src/main/resources/_vfs/nop/ai/tools/http-request.tool.xml`、`nop-ai/nop-ai-app/src/main/resources/application.yaml`、`nop-ai/nop-ai-coder/src/test/resources/application.yaml`、`nop-ai/nop-ai-skills/nop-ai-translate/src/test/resources/application.yaml`、`nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/AiCoreConfigs.java`、`nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/service/ChatLogHelper.java`

- Item Types: `Fix | Decision | Proof`

- [x] （Decision）裁定 xbiz 权限策略，**两条路线任选其一并给出可操作规格**：
  - 路线 A（补属性）：42 个 xbiz 当前自定义 action 面为 0（全部继承 CrudBizModel），若裁定给 CRUD 继承链显式声明 `rights`，必须定义命名规范（`<BizObjName>:<action>`）、批改方式（脚本/手改）与回归验证（schema 校验 + 权限测试），预计 42 文件 × ~9 action；
  - 路线 B（文档化）：nop-ai 为框架模块组，声明式 CRUD 权限归属调用方应用层，写入 owner doc（如 `docs-for-ai/03-modules/nop-ai.md` 或 design），并注明 MR2 已落的 `@Auth`（`<BizObjName>:<action>`）为自定义方法面的现状基线。
- [x] （Fix）`http-request.tool.xml` 示例 JWT token 替换为占位符（`YOUR_BEARER_TOKEN_HERE`），保留 auth 结构说明。
- [x] （Fix）3 个 application.yaml 删除注释掉的 MySQL 凭据块。
- [x] （Decision）`logMessage` 默认值裁定：`AiCoreConfigs` 默认 `true` → `false`。**注意**：`_LlmModel.java`（`nop-ai-core/src/main/java/io/nop/ai/core/model/_gen/_LlmModel.java`，xdef 模型生成类）的 `logMessage` 默认值定义在 `nop-kernel/nop-xdefs/.../ai/llm.xdef:11`——**不改平台内核**；只改 `AiCoreConfigs` 全局默认并在文档声明 per-model 覆盖（`_LlmModel` 字段经 `setLogMessage` 可覆盖）。
- [x] （Fix）按裁定落地 logMessage 默认值变更 + 对应测试/文档同步。
- [x] （Fix）**MA6.5-AR-9（同 audit P1）**：`ChatLogHelper.makeSessionId()` 对 caller 提供的 sessionId 做路径穿越校验（复用 nop-ai-agent `SessionIds.requireValidIdentifier` 的白名单模式，`^[A-Za-z0-9_-]+$`），拒绝非法值；补测试 + 在 arm-index 补记该 finding 的修复归属。
- [x] 全量 build + test 验证。

Exit Criteria:

- [x] xbiz 权限裁定落盘（`arm-index.md` 新 §P2 追踪 或 owner doc 记录裁定与理由）；若选路线 A，42 文件按命名规范落地 `rights` 且有 schema/权限测试验证；若选路线 B，文档声明已更新
- [x] `http-request.tool.xml` 无真实/示例 JWT token（grep 验证），仅占位符
- [x] 3 个 application.yaml 无 MySQL 凭据残留（grep `jdbc:mysql` 0 命中——覆盖 driver/url/username/password 四行整块删除验证）
- [x] logMessage 全局默认按裁定改为 `false`（`AiCoreConfigs.java:18` 值正确）；`_LlmModel`/`llm.xdef` 未被手改（git diff 验证）；`docs-for-ai/03-modules/nop-ai.md` 已声明 per-model 覆盖
- [x] **MA6.5-AR-9**：`ChatLogHelper.makeSessionId` 拒绝非法 sessionId（测试断言），合法值不受影响；arm-index 补记该 finding 修复
- [x] 全量 `./mvnw test -pl nop-ai -am -T 1C` 绿
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 5 — 设计文档 drift 收敛 + 可配置敏感路径（MA5.4-P2-1/3/5 残余 + 执行器输入校验缺口）

Status: completed
Targets: `ai-dev/design/nop-ai-agent/nop-ai-agent-llm-layer.md`、`ai-dev/design/nop-ai-agent/nop-ai-agent-security-and-permissions.md`、`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/security/DefaultPathAccessChecker.java`、`nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/`（执行器输入校验缺口）

- Item Types: `Fix | Decision | Proof`

- [x] （Decision）DashScope（MA5.4-P2-1）：**默认倾向文档修正**（从 Known Provider 表移除并标注未来添加）；实现新 dialect 属完整功能（半天+工作量），仅在明确必要时才选实现。
- [x] （Decision）`IApprovalChannel`（MA5.4-P2-5）：**默认倾向文档标注 deferred**（设计文档注明"接口为未来功能化审批流设计，当前无代码实现"）；创建空接口占位需同步 `DefaultApprovalGate`/`AutoApproveGate` 现状说明，仅在明确必要时才选。
- [x] （Decision）`ISensitivePathProvider`（MA5.4-P2-3）：**默认倾向最小配置注入**——`DefaultPathAccessChecker` 增加可注入的敏感路径扩展（构造器/配置注入追加 pattern 集，不改现有硬编码默认），配 1 个测试；完整 XDSL/Delta 外部配置化仅在明确必要时才选。
- [x] （Fix）按裁定落地：文档修正即时完成；若裁定实现，实现 + 测试。
- [x] （Fix）执行器输入校验缺口扫描（MA6.2-AR-7 残余）：对 `SearchContentExecutor`/`SearchFilesExecutor`/`SkillExecutor`/`AskOracleExecutor`（除 P2-MA1-011 stub 裁定外）确认输入路径/参数校验；缺口补最小校验或文档化扩展点。
- [x] （Proof）核验 MA5.4-P2-4（pipeline 复杂命令）已由 MR3 文档收敛（`04-tool-invocation.md` 已无 pipeline 段落）——仅核验记录，不重复处理。
- [x] 全量 build + test 验证。

Exit Criteria:

- [x] 4 处设计-代码 drift（DashScope/Guardrail/ISensitivePathProvider/IApprovalChannel）逐项有裁定记录（落盘于设计文档或 `arm-index.md` 新 §P2 追踪），无"声称已实现但不存在"残留；裁定均含倾向性理由
- [x] 若裁定实现敏感路径可配置：`DefaultPathAccessChecker` 支持外部配置且测试验证（**接线验证**：配置路径进入实际检查路径）
- [x] 执行器输入校验缺口逐项确认（校验存在或文档化扩展点），无静默 stub 语义新增
- [x] MA5.4-P2-4 核验记录（MR3 已收敛，非本 plan 遗漏）
- [x] `ai-dev/design/nop-ai-agent/` 对应文档已更新
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] 所有 in-scope 安全 P2 finding（P2-MA3-023、MA6.2-AR-5/6/7 残余、MA6.5-AR-7/8、MA5.5-AR-6/7、MA6.1-AR-7、P2-MA3-026、MA5.4-P2-1/2/3/5、MA6.5-AR-9）已修复或裁定落盘
- [x] 无 in-scope live defect 被静默降级到 deferred / follow-up（AR-9 为同 audit P1 缺口，已承接修复）
- [x] 关键行为（shell deny-list、guardrail 拦截、缓存 TTL、fork 过滤）均有 focused 测试 + 端到端/接线验证
- [x] 不存在空方法体/静默跳过/no-op 作为正常实现（Anti-Hollow）
- [x] 受影响 owner docs（`ai-dev/design/nop-ai-agent/`、`docs-for-ai/03-modules/nop-ai.md`、`arm-index.md` 新 §P2 追踪）已同步到 live baseline
- [x] 独立子 agent closure audit 已完成并记录证据（含每条 Exit Criterion 验证）
- [x] `./mvnw compile -pl nop-ai -am`
- [x] `./mvnw test -pl nop-ai -am -T 1C`
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai --severity high`：**基线退出码为 1（24 项既有 high findings，均为历史 pass-through/SPI 设计，不在本 plan scope）**——closure 判定为增量式：**本 plan 引入/触及的文件不新增 high 项**（对比执行前基线记录）；其余 24 项已登记于本计划 `Deferred But Adjudicated` 节，执行前落盘基线清单至 `arm-index.md`，不构成 closure 阻塞
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2026-07-31-1834-1-arm-p2-security-hardening.md --strict` 退出码 0

## Deferred But Adjudicated

### P2-MA3-025 零 DataAuth 使用

- Classification: `watch-only residual`
- Why Not Blocking Closure: 架构级能力；nop-ai 为框架模块组，数据级访问控制属应用层部署责任（与 nop-code/nop-auth 模块一致，二者 DataAuth 配置也在应用层）。本计划 Phase 4 xbiz 权限裁定覆盖声明式 action 权限面；DataAuth 全量实现超出 P2 批量修复定位。
- Successor Required: `no`

### MA6.1-AR-8 vault/secret-store 集成

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: MR3 已将密钥走 header/密文存储/脱敏，明文暴露面已收敛；vault 集成是部署基础设施演进，非 live defect。
- Successor Required: `no`

### MA5.4-P2-1 DashScope dialect 实现 / P2-5 IApprovalChannel 占位接口（若裁定为实现路线）

- Classification: `optimization candidate`
- Why Not Blocking Closure: 两个均属完整功能实现（新 dialect 类 + SPI 注册、新接口 + 消费者），非 live defect；本计划默认倾向文档修正（见 Phase 5 裁定倾向），实现路径仅在明确必要时才进入 scope，届时按此分类记录。
- Successor Required: `no`

### llm.xdef `logMessage` per-model 默认值（平台内核）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: `nop-kernel/nop-xdefs/.../ai/llm.xdef:11` 属平台内核保护区（plan-first），改动影响全平台生成产物；本计划只改 `AiCoreConfigs` 全局默认 + 文档声明 per-model 覆盖，暴露面已收敛。
- Successor Required: `no`

### scan-hollow 基线 24 项既有 high findings

- Classification: `watch-only residual`
- Why Not Blocking Closure: `scan-hollow-implementations.mjs --module nop-ai --severity high` 基线退出码 1（24 项），全部为历史 pass-through/SPI 设计（IAiMemoryStore×4、ISessionStore UOE defaults、IAgentEngine×4、NoOpHookRegistry、NoOpFencingTokenService、AlwaysClosed/NoOpGoalTracker/NoOpSustainer、DefaultAiChatService:620、PrintStreamShellOutput/ShellChunk/TeeOutput、DefaultAgentEngine:3268 plan-mode UOE 等），非本 plan scope。执行前落盘基线清单，closure 以增量判定（本 plan 触及文件不新增 high 项）。
- Successor Required: `no`

## Non-Blocking Follow-ups

- MA5.4-P3-1/2/3（IShellInput 接口方法、readBudgeted UOE、readAllText 二进制丢弃）：P3，低优先。
- MA6.1-AR-6（LlmConfigHelper static state）：测试隔离类，由可靠性批次（`2026-07-31-1834-2-arm-p2-reliability-observability.md`）承接。
- P2-MA3-03/04/05/06/08（legacy API 迁移/契约）：由契约批次（`2026-07-31-1834-3-arm-p2-contract-dependency-cleanup.md`）承接。

## Closure

Status Note: 全部 5 个 Phase 完成（Exit Criteria 全勾选），Closure Gates 全勾选，独立子 agent closure audit APPROVE（26 项逐条验证 PASS，2 个 non-blocking 记录精度问题已修正）。安全类 P2 finding 全部修复或裁定落盘，无 in-scope live defect 静默降级。
Completed: 2026-07-31

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure audit 子 agent（task `ses_047f8f204ffe3Y9aNzy9u05VOW`，fresh session）
- Audit Session: ses_047f8f204ffe3Y9aNzy9u05VOW
- Evidence:
  - Phase 1（shell deny-list）：`DefaultCommandChecker.java` deny-list 规则（BLOCKED_COMMANDS/MKFS/存储设备/rm-root 三元判定/chmod-chown 根级/sudo 包装/裸 shell）PASS；`ShellCommandExecutor.java:66-68` 两参构造器默认装配、`:58-60` null checker WARN、`:78-83` execute()→checkAst→checker.check 调用链连通（Anti-Hollow 端到端）；`TestCommandChecker` 13 例 + `ShellCommandExecutorTest.testDefaultExecutorAssemblesDefaultCommandChecker`（126+拒绝消息）PASS
  - Phase 2（guardrail）：`PromptInjectionGuardrail.java` 四类威胁正则 + OFF/REPORT/ENFORCE 语义（构造器传 mode，REPORT=WARN+Pass，ENFORCE=BlockResult）PASS；`ReActAgentExecutor.java:1506` INPUT 拦截（LLM 调用前）、`:1728` OUTPUT 拦截，`TestContentGuardrailInReActLoop.promptInjectionGuardrailBlocksInjectedInputInLoop`（真实 guardrail 引擎级接线，LLM 零调用）PASS；NoOp WARN 核验（`DefaultAgentEngine.java:795-800`，plan 引 787-792 行号漂移为 cosmetic）；设计文档 §5.2 实现状态表 PASS
  - Phase 3（TTL+fork 过滤）：`DefaultAiChatResponseCache` 惰性 mtime 过期+删除（`isExpired`，保存格式不变）PASS；`nop.ai.service.cache-ttl` 配置键 + `ai-defaults.beans.xml` 接线 PASS；`ISessionStore` 4 参 overload（null 委托 3 参，非 null 不支持→UOE fail-fast）+ 三 store 实现 PASS；`DefaultAgentEngine.forkMessageFilter`（字段/setter/Builder/`:2156` 传递）PASS；`TestSessionStoreForkMessageFilter` 7 例（含引擎级端到端）+ `TestAiChatResponseCacheTtl` 3 例 PASS
  - Phase 4（配置卫生）：`http-request.tool.xml:95` 占位符（nop-ai 组内 grep `eyJhbGci` 0 命中；全仓 2 处既有命中在 nop-auth SSO/e2e 测试夹具，arm-index 措辞已修正）PASS；3 个 application.yaml `jdbc:mysql` 0 命中 PASS；`AiCoreConfigs.java` log-message 默认 false + `ChatServiceImpl` `全局 && per-model isLogMessage()` 三处接线 PASS；`llm.xdef`/`_LlmModel` git diff 0 改动 PASS；`ChatLogHelper` 白名单校验 + `TestChatLogHelper` 3 新例（11/11）PASS；arm-index §P2 安全批次表 PASS
  - Phase 5（drift 收敛）：llm-layer.md §4.3 DashScope 未实现注记 PASS；security-and-permissions.md §6.1 IApprovalChannel deferred / §5.2 guardrail 状态 / §7.2 真实实现状态 PASS；`DefaultPathAccessChecker` 构造器注入 + `TestPathAccessCheckerSensitivePaths` 5 例（接线验证 matchedRule 断言）PASS；`04-tool-invocation.md` pipeline 0 命中（MA5.4-P2-4 核验）PASS
  - Anti-Hollow：3 条调用链（checker→execute、guardrail→ReAct loop、forkMessageFilter→store）均以引擎级/端到端测试证实运行时连通，非仅类型存在；新/改文件无空方法体/静默跳过（ISessionStore 4 参 default 的 UOE 为显式 fail-fast 设计，null 委托保持兼容）
  - `node ai-dev/tools/check-plan-checklist.mjs <plan> --strict` 退出码 0（Closure Evidence 已写入，全部 checklist 勾选）
  - `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai --severity high` 退出码 1 = 基线 24 项既有 high findings（IAiMemoryStore×4、ISessionStore UOE defaults、IAgentEngine×4、NoOpHookRegistry、NoOpFencingTokenService、AlwaysClosed/NoOpGoalTracker/NoOpSustainer、DefaultAiChatService:620、PrintStreamShellOutput/ShellChunk/TeeOutput、DefaultAgentEngine:3268 等，均预登记于 Deferred But Adjudicated），本 plan 触及文件 0 新增（增量判定 PASS）
  - Deferred 项分类检查：P2-MA3-025（架构级 DataAuth，路线 B 裁定）、MA6.1-AR-8（vault，out-of-scope improvement）、DashScope/IApprovalChannel（文档修正路线，非降级）、llm.xdef 内核默认（预声明）、scan-hollow 24 项基线（预声明）——无 in-scope live defect 被降级
  - 构建验证：`./mvnw install -DskipTests -pl nop-ai -am -T 1C` BUILD SUCCESS；`./mvnw test -pl nop-ai` BUILD SUCCESS（nop-ai-agent 2856 / nop-ai-shell 269 等，3 模块独立审计运行 3245 tests 0 failures）；上游模块全量顺序 run 已核绿（19:15），因并行会话 xview.xdef WIP 干扰后续批次跳过上游测试（环境备注见 `ai-dev/logs/2026/07-31.md`）；`check-doc-links.mjs --strict` exit 0

Follow-up:

- MA5.4-P3-1/2/3、MA6.1-AR-8（vault）、P2-MA3-03/04/05/06/08（契约批次）——均按 Non-Blocking Follow-ups 登记，无本 plan-owned 剩余工作
- 审计记录精度修正（closure audit 指出）：arm-index JWT grep 措辞已精确化（nop-ai 组内 0 命中 vs 全仓 2 处测试夹具命中）；NoOp WARN 行号漂移（787-792 → 795-800）为 cosmetic，不影响验证结论

## Optional Sections

## Risks And Rollback

- `DefaultCommandChecker` deny-list 误伤正常命令：deny-list 采用保守集（仅明确危险模式），全部规则带测试；误伤可经配置关闭或回滚单 commit。
- `logMessage` 默认改 `false`：行为变更，`_LlmModel`（`nop-ai-core/.../model/_gen/`，xdef 模型生成类）默认值定义在 `nop-kernel/nop-xdefs/.../ai/llm.xdef:11`——平台内核保护区，**不改**；只改 `AiCoreConfigs` 全局默认并在文档声明 per-model 经 `setLogMessage` 覆盖。若评估发现全局默认改动影响过大（如依赖日志的既有测试），回退为仅文档声明，裁定记录理由。
- `forkSession` 过滤 hook 默认全量兼容，不破坏现有 fork 行为；风险面收敛。
- 缓存 TTL 默认 0（不过期）保持兼容，配置开启后惰性过期，无主动清扫线程。
