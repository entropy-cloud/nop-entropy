# 2026-07-31-2248-3 P3 残余清理批次（测试隔离 + shell IO + dsl-orm 命名）

> Plan Status: active
> Last Reviewed: 2026-07-31
> Source: `ai-dev/backlog/audit-remediation-roadmap.md` §P2/P3 Deferred Successors；`ai-dev/plans/2026-07-31-1834-1-arm-p2-security-hardening.md` / `1834-2` / `1834-3` Non-Blocking Follow-ups
> Related: `2026-07-31-1834-1-arm-p2-security-hardening.md`、`2026-07-31-1834-2-arm-p2-reliability-observability.md`、`2026-07-31-1834-3-arm-p2-contract-dependency-cleanup.md`
> Mission: audit-remediation
> Work Item: P3 残余清理（MA5.6-AR-4/5/7 + MA5.4-P3-1/3 + P3-MA1-038）

## Purpose

收口多份已 closed 计划 Non-Blocking Follow-ups 中登记的 P3 残余治理项：测试隔离（temp dir deleteOnExit 资源泄漏 ×2、PassThroughModelRouter 单例耦合）、shell IO 设计-代码 drift（IShellInput 接口方法、readAllText 二进制丢弃）、dsl-orm gpt 命名空间（类名层面），全部为低风险、可独立验证的小批量修复。

## Current Baseline

- **MA5.6-AR-4（P3）**：`TestChatLogHelper`、`TestMockAiChatService`（nop-ai-core）、`ShellIOTest`、`ShellConcurrencyEdgeCaseTest`、`ShellCommandExecutorTest`、`CdCommandTest`（nop-ai-shell）使用 `deleteOnExit` 注册临时目录，无显式 `@AfterEach` 清理——CI 长时间运行累积临时文件。
- **MA5.6-AR-7（P3）**：`ShellConcurrencyEdgeCaseTest` 与 AR-4 同模式；`@BeforeEach` 创建 temp dir 后 `LocalToolFileSystem` 写入文件，若 `deleteOnExit` 失败（非空目录）临时文件持久残留。
- **MA5.6-AR-5（P3）**：`PassThroughModelRouter.passThrough()` 返回 static 单例，`TestPassThroughModelRouter.java:94-98` 与 `:102` 断言 `assertSame(a, b)`；单例当前无状态，但未来任何状态添加都会造成测试交叉耦合。`passThrough()` 出现在 17 个文件（42 处 occurrence，含 `PassThroughPermissionMatrix:22` / `PassThroughPostDenialGuard:30` 各自的工厂定义），其中 main 代码对 `PassThroughModelRouter.passThrough()` 的调用点仅 **5 处**（DefaultAgentEngine×3、ReActAgentExecutor×2），均为 static factory 调用、签名不变时零改动；其余为测试引用。另注意 `TestPassThroughPostDenialGuard:58` / `TestPassThroughPermissionMatrix:58` 的 `assertSame` 属于另外两个单例类（非本 finding）。
- **MA5.4-P3-1（P3）**：`IShellInput.java`（nop-ai-shell）仅声明 `read()/close()/isClosed()`，而 design doc `ai-dev/design/nop-ai-shell/02-io-and-pipeline.md` §3.3 声称接口含 `readLine()/readAllText()/lines()/chunks()`；这些方法实际在 `AbstractShellInput` 中。自定义实现不继承 `AbstractShellInput` 将缺失契约方法。
- **MA5.4-P3-3（P3）**：`AbstractShellInput.readAllText()`（:50-59）静默丢弃非文本块（只拼 `isText()` 块），`readLine()`（:44-46）同模式——二进制数据无警告丢失，未来二进制命令实现的正确性陷阱。
- **P3-MA1-038（P3）**：`nop-ai-dsl-orm` 类名使用 `GptOrm*`（GptOrmErrors/GptOrmConstants/GptOrmModelParser/GptOrmSqlType），错误码前缀已由 P2-MA1-034 统一为 `nop.err.ai.dsl-orm.*`（类名仍为 Gpt）；XDEF 文件仍位于 `nop-ai-dsl-orm/src/main/resources/_vfs/nop/schema/gpt/orm.xdef` 且被 `GptOrmConstants:11`（XDEF_GPT_ORM）→ `GptOrmModelParser:35` 引用——gpt 命名空间仍存活于 xdef 资源与类名。类名重命名属公开 API 变更。
- 以上各项均已在各 closed 计划 follow-ups 登记（"后续测试基础设施批次" / "P3，低优先"），非被降级的 live defect。

## Goals

- MA5.6-AR-4/AR-7：6 个测试类由 `deleteOnExit` 改为显式 `@AfterEach` 清理（`Files.walk`/`deleteRecursively` 或 Nop 工具方法），临时目录生命周期与测试同步。
- MA5.6-AR-5：消除单例耦合——`passThrough()` 每次返回新实例（或保留单例但加防御性注释 + 测试改为不依赖身份），main 代码 5 处调用点签名不变零改动，仅同步 `TestPassThroughModelRouter` 断言（scope 限定 PassThroughModelRouter，不动 TestPassThroughPostDenialGuard/TestPassThroughPermissionMatrix 的其他单例断言）。
- MA5.4-P3-1：二选一裁定——在 `IShellInput` 接口加 `default` 方法（基于 `read()` 的通用实现，非委托 AbstractShellInput，因其依赖实例私有状态），或更新 design doc §3.3 与代码一致。
- MA5.4-P3-3：`readAllText()` 遇 `BinaryChunk` 时记录 WARN 日志（不静默丢弃）或更新 design doc 明确限制；两者兼做更优（行为 + 文档）。
- P3-MA1-038：类名 `GptOrm*` 裁定（重命名属公开 API 破坏性变更，默认倾向保留类名 + javadoc 记录 gpt 历史命名理由 + 错误码前缀已统一），裁定结论落盘 design 文档或 arm-index。

## Non-Goals

- 不处理 MA5.6-AR-1（P1，已 closed）、AR-2/AR-3（P2，已修复）、AR-6（P3 observation，无需动作）。
- 不迁移 `IShellInput` 实现结构（AbstractShellInput 保留），只收敛接口契约或文档。
- 不做 GptOrm* 类名重命名（除非裁定明确要求且评估破坏性后执行）。
- 不处理 MA5.4-P3-2（readBudgeted UOE——已由 `2026-07-31-2248-2-arm-hollow-baseline-clearance.md` 覆盖，InMemoryAiMemoryStore 已有真实实现）。

## Scope

### In Scope

- 6 个测试类 temp dir 清理改造
- PassThroughModelRouter 单例解除 + 调用点/测试同步
- IShellInput 接口方法或 design doc 收敛
- readAllText 二进制丢弃警告/文档
- GptOrm* 命名裁定 + 落盘

### Out Of Scope

- 其他测试基础设施改造（如 AR-1 生命周期 race）
- IShellInput 实现重构
- GptOrm* 类重命名执行（除非裁定要求）

## Execution Plan

### Phase 1 - 测试隔离：temp dir 清理（MA5.6-AR-4/AR-7）

Status: planned
Targets: `nop-ai/nop-ai-core/src/test/java/io/nop/ai/core/service/TestChatLogHelper.java`、`TestMockAiChatService.java`、`nop-ai/nop-ai-shell/src/test/java/io/nop/ai/shell/io/ShellIOTest.java`、`.../executor/ShellConcurrencyEdgeCaseTest.java`、`ShellCommandExecutorTest.java`、`.../commands/impl/CdCommandTest.java`

- Item Types: `Fix | Proof`

- [ ] 核验 6 个测试类中 `deleteOnExit` 的具体用法（`Files.createTempDirectory` + `toFile().deleteOnExit()` 或等效），确认清理目标与测试生命周期
- [ ] 为每个测试类添加 `@AfterEach` 显式递归删除临时目录（使用 `io.nop.commons.util.FileHelper` 或 `Files.walk` 逆序删除；删除失败记录 WARN 不掩盖测试结果）
- [ ] 移除 `deleteOnExit` 调用（或保留为兜底但以显式清理为主——裁定：显式清理为准，deleteOnExit 移除避免双路径）
- [ ] 核验测试在临时目录删除后无遗留断言依赖（无测试读取已删除目录）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] grep 确认 6 个目标测试类 `deleteOnExit` 0 残留（或核验记录说明保留理由）
- [ ] 6 个测试类均含显式 `@AfterEach` 清理方法
- [ ] `./mvnw test -pl nop-ai/nop-ai-core,nop-ai/nop-ai-shell` 相关测试类全绿（0 failures）
- [ ] 测试后临时目录不残留（抽查：单测运行后 `<java.io.tmpdir>` 下无新增本计划创建的目录，或核验记录）
- [ ] No owner-doc update required（测试内部改造）
- [ ] `ai-dev/logs/2026/07-31.md` 对应条目已更新

### Phase 2 - PassThroughModelRouter 单例解除（MA5.6-AR-5）

Status: planned
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/router/PassThroughModelRouter.java`、`TestPassThroughModelRouter.java`

- Item Types: `Fix | Proof`

- [ ] 裁定：`passThrough()` 改为每次返回新实例（无状态类，代价最低；main 代码调用点 5 处——DefaultAgentEngine×3、ReActAgentExecutor×2——均为 static factory 调用，签名不变**零改动**；`PassThroughPermissionMatrix:22`/`PassThroughPostDenialGuard:30` 的 `passThrough()` 是各自类的工厂定义，不调用 PassThroughModelRouter，不涉及），或保留单例 + 类 javadoc 防御性说明 + 测试去掉身份断言。默认倾向：改为新实例（彻底解除耦合）
- [ ] 按裁定执行：更新 `PassThroughModelRouter`（如选新实例，仅改方法体返回 `new PassThroughModelRouter()`）；**main 代码 5 处调用点无需修改**（核验确认，不产生无谓 diff）
- [ ] 同步测试断言：`TestPassThroughModelRouter.java:94-98` 与 `:102` 的 `assertSame(a, b)` 单例断言 → `assertNotSame` 或删除；**注意**：`TestPassThroughPostDenialGuard:58`、`TestPassThroughPermissionMatrix:58` 的 `assertSame` 断言的是另外两个类（PassThroughPostDenialGuard/PassThroughPermissionMatrix）的单例，**不在本 finding scope 内，不改**
- [ ] 核验无测试/代码依赖 PassThroughModelRouter 单例身份（grep `assertSame` + `PassThroughModelRouter` 限定 scope）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] `PassThroughModelRouter.passThrough()` 调用点全部编译通过（main 代码 5 处，零改动）
- [ ] `TestPassThroughModelRouter` 无 `assertSame(a, b)` 单例断言残留（:94-98 与 :102 已同步）；`TestPassThroughPostDenialGuard:58` / `TestPassThroughPermissionMatrix:58` 保持原样（非本 finding scope）
- [ ] `./mvnw test -pl nop-ai/nop-ai-agent` 全绿（router 相关测试）
- [ ] No owner-doc update required（内部实现，无契约变化；如 javadoc 措辞改动则核验 design doc `nop-ai-agent-reliability.md` 无矛盾）
- [ ] `ai-dev/logs/2026/07-31.md` 对应条目已更新

### Phase 3 - shell IO 契约收敛（MA5.4-P3-1/P3-3）

Status: planned
Targets: `nop-ai/nop-ai-shell/src/main/java/io/nop/ai/shell/io/IShellInput.java`、`AbstractShellInput.java`、`ai-dev/design/nop-ai-shell/02-io-and-pipeline.md`

- Item Types: `Decision | Fix`

- [ ] P3-1 裁定：`IShellInput` 增加 `default` 方法 `readLine()/readAllText()/lines()/chunks()`（基于 `read()` 的通用实现，供不继承 `AbstractShellInput` 的自定义实现使用；`AbstractShellInput` 保留其基于实例状态 buffer/eofSeen 的高效覆写——审计建议的"委托 AbstractShellInput 实现"不可行，因为其方法依赖实例私有状态，interface default 无法委托；通用 read()-based 实现与 AbstractShellInput 形成双份逻辑，需 javadoc 说明二者关系），或更新 design doc §3.3 使契约与代码一致（声明 4 方法为 `AbstractShellInput` 便利方法而非接口契约）。默认倾向：接口 default 方法（契约收敛，custom 实现自动获得能力）+ design doc 同步
- [ ] 按裁定执行：接口 default 方法落地（含 javadoc 说明与 AbstractShellInput 覆写的关系），`AbstractShellInput` 覆写保持现有行为；或 design doc 修订
- [ ] P3-3：`AbstractShellInput.readAllText()`（:50-59）与 `readLine()`（:44-46）遇非文本块时 `log.warn("...skipping non-text chunk...")`（不静默丢弃），并在 javadoc 明确"text-only 读取，非文本块被跳过并告警"
- [ ] 更新 design doc §3.3 与代码一致（无论 P3-1 走接口 default 还是 doc 路径）
- [ ] 补充/更新 `ShellIOTest` 覆盖：接口 default 方法对纯 `IShellInput` 实现（不继承 AbstractShellInput）可用；binary chunk 跳过产生 WARN 日志路径（用 `ShellChunk.binary(byte[])` factory 构造含 BinaryChunk 的输入）；**注意**：`ShellIOTest` 同时是计划 `2026-07-31-2248-2` 的 Phase 1 目标文件（:360 断言 PrintStreamShellOutput UOE 需先由计划 2 转换），本计划按文件名顺序在计划 2 之后执行，改动基于计划 2 后的新状态

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] `IShellInput` 与 design doc §3.3 一致（两者之一已收敛，非双轨 drift）
- [ ] `readAllText()`/`readLine()` 非文本块不再静默丢弃（WARN 日志 + javadoc 明确）
- [ ] `ShellIOTest` 新增用例通过（default 方法对纯 IShellInput 实现可用 + binary 跳过 WARN 路径）
- [ ] **端到端验证**（如适用）：shell 命令（如 `cat`/`echo` 经 ShellCommandExecutor）走 IShellInput → readAllText 完整路径测试通过
- [ ] owner docs 已同步：`ai-dev/design/nop-ai-shell/02-io-and-pipeline.md` §3.3 与代码一致
- [ ] `ai-dev/logs/2026/07-31.md` 对应条目已更新

### Phase 4 - GptOrm* 命名裁定（P3-MA1-038）

Status: planned
Targets: `nop-ai/nop-ai-dsl-orm/src/main/java/io/nop/ai/dsl/orm/GptOrmErrors.java`、`GptOrmConstants.java`、`GptOrmModelParser.java`、`consts/GptOrmSqlType.java`、`arm-index.md`

- Item Types: `Decision | Proof`

- [ ] 裁定（Decision）：类名 `GptOrm*` 重命名（→ `AiDslOrm*`）属公开 API 破坏性变更（nop-ai-dsl-orm 是独立发布模块，GptOrmErrors 错误码常量/类名可能被外部引用）；默认倾向：保留类名，类级 javadoc 记录 gpt 历史命名缘由 + 错误码前缀已统一（P2-MA1-034 已做）。XDef 资源路径 `/nop/schema/gpt/orm.xdef`（被 GptOrmConstants:11 → GptOrmModelParser:35 引用）与类名同源，一并纳入裁定范围（保留或迁移二选一，迁移需同步 xdef 路径 + 常量 + parser 引用 + `_vfs` 资源，破坏面更大）；如裁定重命名则评估破坏面并执行迁移
- [ ] 按裁定落盘：arm-index 新增 P3-MA1-038 行（当前 arm-index 无该行，为新增落盘；裁定结论 + 证据）；如需要，GptOrmErrors javadoc 补注

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 裁定结论写入 arm-index（新增行：保留类名 + 理由，或重命名 + 迁移证据）
- [ ] 如裁定保留：类 javadoc 含历史命名说明（GptOrmErrors/GptOrmConstants 至少一处），xdef 路径保留并在 javadoc 注明
- [ ] 如裁定重命名：`./mvnw compile -pl nop-ai/nop-ai-dsl-orm -am` + 相关测试全绿，旧类名/xdef 路径引用 0 残留（含 GptOrmConstants:11 → GptOrmModelParser:35 链）
- [ ] No owner-doc update required（或 design 文档同步核验记录）
- [ ] `ai-dev/logs/2026/07-31.md` 对应条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见本 guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [ ] MA5.6-AR-4/AR-7 已修复（显式清理 + 无 deleteOnExit 残留 + 测试绿）
- [ ] MA5.6-AR-5 已修复（单例耦合解除或防御性裁定落地 + PassThroughModelRouter 断言同步，scope 限定 PassThroughModelRouter 不含其他单例类）
- [ ] MA5.4-P3-1/P3-3 已收敛（接口契约与 doc 一致 + 非文本块不静默丢弃 + 测试）
- [ ] P3-MA1-038 已裁定落盘（arm-index 可追溯）
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [ ] 受影响的 owner docs 已同步到 live baseline，或明确写明 No owner-doc update required
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）IShellInput default 方法在 shell 命令路径运行时可达（如有接线），（b）无空方法体/静默跳过/no-op 作为正常实现（readAllText/readLine WARN 为显式行为）
- [ ] `./mvnw compile -pl nop-ai -am`
- [ ] `./mvnw test -pl nop-ai -am -T 1C`
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <本plan文件> --strict` 退出码 0
- [ ] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### GptOrm* 类名/xdef 路径迁移执行（若裁定保留）

- Classification: `watch-only residual`
- Why Not Blocking Closure: 类名与 xdef 路径 `/nop/schema/gpt/orm.xdef` 属公开 API 表面，错误码前缀（`nop.err.ai.dsl-orm.*`）已统一；历史命名由 javadoc 记录，迁移破坏面评估为 low-value/high-risk，非 live defect。
- Successor Required: `no`

## Non-Blocking Follow-ups

- MA5.6-AR-1（CoreInitialization lifecycle race）：P1 已由 MR2 处理（closed），无新增
- MA5.4-P3-2（readBudgeted UOE）：由 `2026-07-31-2248-2-arm-hollow-baseline-clearance.md` 承接

## Closure

Status Note: 待执行
Completed: （待填）

Closure Audit Evidence:

- Reviewer / Agent: （待填）

Follow-up:

- （待填）

## Optional Sections

## Risks And Rollback

- temp dir 清理改造：显式删除可能在 Windows 上失败（文件句柄未释放）——删除失败只 WARN 不影响测试结论；单 commit 可回滚。
- PassThroughModelRouter 新实例化：无状态类，实例化成本可忽略；main 代码调用点 5 处零改动，仅测试断言变更。
- IShellInput default 方法：新增接口方法为 default，不破坏现有实现；AbstractShellInput 覆写保持行为，零回归；若接口 default 与 AbstractShellInput 双份逻辑在测试中发现不一致，回退路径 = 改 design doc（doc-remediation 路线）。
- GptOrm* 裁定保留类名：零风险。
