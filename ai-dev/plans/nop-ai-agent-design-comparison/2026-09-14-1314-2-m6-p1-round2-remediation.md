---
status: active
mission: nop-ai-agent-design-comparison
work-item: M6-P1
group: "2026-09-14-1314"
verify: [test]
---

# M6-P1 逐项修复：round-2 审计发现的 5 个 P1 缺陷

## Current Baseline

- deep-audit round 2（2026-09-14）登记 5×P1，全部经 live repo 复核仍成立：
  1. **ReActAgentExecutor 失败执行仍发布 EXECUTION_COMPLETED**：`canPublishExecutionCompleted`（ReActAgentExecutor.java:1271-1278）排除 cancelled/forced_stopped/escalated/paused/truncated/waiting 但漏 `failed`；重试耗尽/不可重试分类后 `finalizeLlmCallResult`（LlmCallCoordinator.java:425-435）置 `AgentExecStatus.failed` → 循环 break → `adjudicateTerminal`（ReActAgentExecutor.java:1242）照常发布完成事件并跑 POST_CALL hooks，与 :1254-1260 注释"aborted/suspended 不得发布"契约漂移。
  2. **同实例重复提交删掉运行中执行的 takeover 租约**：`DefaultAgentEngine.java:789-806`（doExecute）与 `AgentSessionLifecycle.java:293-313`（resumeSession）、`:420-440`（wakeSession）、`:655-680`（restoreSession）**四处同构**都是 tryAcquire（同 owner 续租成功，DbSessionTakeoverLock.java:203-224 条件 UPDATE）→ `runningExecutions.putIfAbsent` 失败 → catch 调 `releaseLockQuietly(sessionId, instanceId)` → `DbSessionTakeoverLock.release`（:237-252）`DELETE ... AND LOCK_OWNER=?` 删掉的是**胜出执行**的租约行（同 instanceId）→ 其续租失败被强制 cancel + 第三方可趁机双执行；putIfAbsent 失败分支文案当前已是 "session already executing"（四处一致），需复核 catch 重抛路径不把同实例重复提交呈现成跨实例语义的 "locked by another instance"。
  3. **FileToolBizModel.getProjectDir 沙箱逃逸**：`StringHelper.fileName("..")` 原样返回（lastPart）且 `isValidFileName` 不拒 `..`（只查控制字符与 INVALID_FILE_NAME_CHARS_STR，StringHelper.java:2540-2547），`new File(baseDir, "..")`（FileToolBizModel.java:265-271）把沙箱上移一级（默认 /nop/projects → /nop），readFiles/saveFile/saveFiles/mergeFile/saveDslFile 全部脱沙；对侧 `AiToolsHelper.requireValidSessionId`（utils/AiToolsHelper.java）同类输入 fail-closed，同一抽象两套安全姿态。
  4. **Shell `&>`/`&>>` 合并重定向输出翻倍**：`handleMergeRedirect`（ShellCommandExecutor.java:499-506）用 `new TeeOutput(fileOutput, fileOutput)` 同一实例两次，`TeeOutput.write`（TeeOutput.java:33-37）逐 leg 落同一 buffer → 每次 flush 写双倍内容（`echo stdout &> f` 产出两行）；回归测试（ShellCommandExecutorTest.java:239-253）只断言 `contains("stdout")` 所以 CI 全绿。
  5. **FeishuConnector.isBotMentioned 群聊 @任意成员即触发**：仅检查 mentions 数组含 `"key"` 与 `"open_id"` 两个子串（FeishuConnector.java:586-617），群消息 @了**任何其他人**也通过过滤 → 未 @ bot 的群消息触发 IAgentEngine 执行与回复；类注释自认 "bot open_id 精确匹配 deferred"，测试（TestFeishuConnector.java:356-371）缺 "@了其他用户" 负例。
- 各缺陷归属模块：nop-ai-agent（1、2）、nop-ai-tools（3）、nop-ai-shell（4）、nop-ai-gateway（5）。

## Goals

- 5 个 P1 缺陷逐一修复，每个修复带能验证正确行为的回归测试（不只断言"没报错"）。
- 修复后各模块 `./mvnw test -pl <module> -am` 通过；check-doc-links 0 error。

## Non-Goals

- 不修复 M5 的 4×P1（另一计划）。
- 不修复 round-2 的 P2 项（Follow-up Backlog，非阻塞）。
- 不改变各模块既有 API 签名与配置契约（只改实现语义与防御行为）。
- 不运行 mvn 全量构建。

## Phase 1 — ReActAgentExecutor：failed 状态不得发布 EXECUTION_COMPLETED / 跑 POST_CALL

Status: planned

Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/ReActAgentExecutor.java`、对应测试

- Item Types: `Fix | Proof`

- [x] `Fix` `canPublishExecutionCompleted`（:1271-1278）增加 `failed` 排除：`ctx.getStatus() != AgentExecStatus.failed`，使重试耗尽/不可重试分类的失败执行不再发布 EXECUTION_COMPLETED、不再跑 POST_CALL hooks。
- [x] `Fix` 同步核对 `publishExecutionCompleted` 调用链（adjudicateTerminal :1242），确认 failed 时错误路径仍走既有 `EXECUTION_FAILED` 事件（finalizeLlmCallResult / catch 分支）而不被吞。
- [x] `Proof` 复核 `AgentExecStatus` 全枚举（cancelled/forced_stopped/escalated/paused/truncated/waiting/failed/completed/running 等）逐值比对 `canPublishExecutionCompleted` 的排除面，确认无第二个遗漏状态；必要时补注释说明每个排除状态的理由。
- [x] `Fix` 新增回归测试：构造 failed 终态的执行（如不可重试错误耗尽重试），断言未发布 EXECUTION_COMPLETED 事件、未执行 POST_CALL hooks，且 EXECUTION_FAILED 事件正常发布；同时保留"completed 正常发布"正例。

Exit Criteria:

- [x] failed 终态执行不再发布 EXECUTION_COMPLETED、不再跑 POST_CALL hooks（测试断言计数/事件捕获为证）。
- [x] completed 正常执行仍发布完成事件（正例测试通过）。
- [x] **端到端验证**：从 execute() 入口到事件发布出口的完整路径覆盖（失败路径与成功路径各一条）。
- [x] **接线验证**：新增排除逻辑被 adjudicateTerminal 运行时调用（经执行流触发而非直接调私有方法）。
- [x] **无静默跳过**：failed 的失败通知（EXECUTION_FAILED）未被吞掉。
- [x] `No owner-doc update required`：行为修复不改公开契约（排除面与既有注释意图一致，属注释承诺的落地）。
- [x] `ai-dev/logs/2026/09-14.md` 对应条目已更新。

## Phase 2 — DefaultAgentEngine：同实例重复提交不得删胜出执行租约

Status: planned

Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/DefaultAgentEngine.java`、`AgentSessionLifecycle.java`、`DbSessionTakeoverLock.java`、`TestDbSessionTakeoverLock*.java`、`TestDefaultAgentEngineConcurrencyGuard.java`

- Item Types: `Fix | Proof`

- [x] `Fix` 修复 catch 路径的误释放：putIfAbsent 失败（同实例已在执行）时**不得**调用 `releaseLockQuietly` 删除租约行——此时租约归胜出执行所有；仅当 tryAcquire 本身失败（租约被他方持有）才允许走"无锁可释放"路径（此时 release 因 `LOCK_OWNER=instanceId` 匹配不到行，天然无害）。改为：区分 `tryAcquire` 失败与 `putIfAbsent` 失败两个分支，后者只抛 "already executing" 错误、不碰租约（对全部 4 处同构调用点同一修复：doExecute :789-806、resumeSession :293-313、wakeSession :420-440、restoreSession :655-680）。
- [x] `Fix` 复核报错文案：确认 4 处调用点 putIfAbsent 失败分支的错误消息均为准确的 "session already executing"（当前已是），且 catch 重抛/包装路径不把同实例重复提交呈现为跨实例语义的 "locked by another instance"（该文案仅保留给 tryAcquire 真实失败场景）。
- [x] `Proof` 复核 `DefaultAgentEngine.java:789-806` 与 `AgentSessionLifecycle.java:293-313`（resumeSession）、`:420-440`（wakeSession）、`:655-680`（restoreSession）全部 4 处同构代码，确认每处都已修（resumeSession/wakeSession/restoreSession 路径同一缺陷）。
- [x] `Fix` 新增回归测试（沿用 TestDbSessionTakeoverLock/TestDefaultAgentEngineConcurrencyGuard 的 in-memory DB 与双实例 E2E 基建）：
  - 同实例同一 session 连续两次提交：第二次报 "already executing"，且**租约行仍存在**（胜出执行可继续续租，不被取消）；
  - 异实例重复提交：仍 fail-fast 且不误删他方租约；
  - 正常执行结束释放：租约行被正确删除。

Exit Criteria:

- [x] 同实例重复提交后胜出执行的租约行仍在（SQL 查询/续租成功为证），不再出现"删除胜出租约→强制 cancel"。
- [x] 错误消息准确（"already executing"），不再误导为跨实例锁定。
- [x] **端到端验证**：从 engine 提交入口到租约表状态的完整路径（提交→续租→结束释放）覆盖。
- [x] **接线验证**：修复后的释放/不释放决策被 doExecute、resumeSession、wakeSession、restoreSession 运行时调用（经执行流触发）。
- [x] **无静默跳过**：第二次提交 fail-fast 抛异常，不静默成功。
- [x] `No owner-doc update required`：行为契约（幂等失败语义）不变。
- [x] `ai-dev/logs/2026/09-14.md` 对应条目已更新。

## Phase 3 — FileToolBizModel.getProjectDir：拒绝 `..` / 路径分隔符（fail-closed）

Status: planned

Targets: `nop-ai/nop-ai-tools/src/main/java/io/nop/ai/tools/file/FileToolBizModel.java`、`LocalFileOperator` 相关测试

- Item Types: `Fix | Proof`

- [x] `Fix` `getProjectDir`（:265-271）增加 fail-closed 校验：raw projectName 不得含 `/`/`\` 路径分隔符（`StringHelper.fileName` 会按 `/` 截断成 lastPart，逃逸输入须在截断前被拒）；`StringHelper.fileName` 结果必须非空、不等于 `.`/`..`（与 `AiToolsHelper.requireValidSessionId` 的防御姿态对齐）；违规抛 `ERR_AI_TOOLS_INVALID_PROJECT_NAME` 并带 `ARG_VALUE`。
- [x] `Fix` 复核所有经 `getFileOperator(projectName)` 的入口（readFiles/saveFile/saveFiles/mergeFile/saveDslFile）共用同一校验路径，确认无旁路。
- [x] `Fix` 新增回归测试：`projectName=".."`、`projectName="."`、`projectName="a/b"`、`projectName="a\\b"` 均被拒；正常 projectName 可读写文件且文件落在 baseDir 内（断言 `new File(baseDir, name)` 的 canonical path 前缀）。
- [x] `Proof` 检查 `LocalFileOperator.java:76-88` 的 `resolveFile`：其已含 `..`/`:` 拒绝与 canonical 前缀校验（非同构逃逸），确认逃逸输入经 `getProjectDir` 前置校验拦截、内层路径校验无其它旁路；有残留缺口则同步补校验或登记为同族缺陷。

Exit Criteria:

- [x] `..`/`.`/含分隔符的 projectName 一律被拒（测试断言异常与错误码）。
- [x] 正常 projectName 的文件读写仍工作且不脱沙（canonical path 断言）。
- [x] **端到端验证**：从工具入口（readFiles/saveFile 等）经 getProjectDir 到文件系统的完整路径覆盖逃逸输入与正常输入。
- [x] **接线验证**：校验逻辑被全部 5 个文件操作入口运行时共享调用。
- [x] **无静默跳过**：逃逸输入抛异常，不返回脱沙的文件操作结果。
- [x] `No owner-doc update required`（工具输入契约收严，属防御语义）。
- [x] `ai-dev/logs/2026/09-14.md` 对应条目已更新。

## Phase 4 — ShellCommandExecutor：`&>` 合并重定向输出不得翻倍

Status: planned

Targets: `nop-ai/nop-ai-shell/src/main/java/io/nop/ai/shell/executor/ShellCommandExecutor.java`、`nop-ai/nop-ai-shell/src/main/java/io/nop/ai/shell/io/TeeOutput.java`、`nop-ai/nop-ai-shell/src/test/java/io/nop/ai/shell/executor/ShellCommandExecutorTest.java`

- Item Types: `Fix | Proof`

- [x] `Fix` `handleMergeRedirect`（:499-506）不再 `new TeeOutput(fileOutput, fileOutput)` 同实例两次；改为 stdout/stderr 两个流共享**一个**输出目标（直接同时指向 fileOutput，或 TeeOutput 去重同实例），保证每次 flush 每个字节只落一次。
- [x] `Proof` 复核 `TeeOutput.write`/`flush`（:33-37 及 flush 遍历）在去重后行为正确（stdout 与 stderr 都进同一文件、无丢失、无翻倍）。
- [x] `Fix` 强化回归测试（ShellCommandExecutorTest.java:239-253）：`echo stdout &> f` 与 `echo out; echo err >&2 &>> f` 等用例断言**精确内容**（恰好一行 stdout / stdout+stderr 各一行且顺序正确），替换仅 `contains("stdout")` 的弱断言。
- [x] `Fix` 补 `&>>`（追加合并）用例：两次执行合并文件内容不重复叠加。

Exit Criteria:

- [x] `&>` 合并重定向输出恰好一份（测试断言精确行数/内容，CI 不再对翻倍全绿）。
- [x] `&>>` 追加合并行为正确。
- [x] **端到端验证**：从 shell 命令入口到合并输出文件的完整路径（stdout+stderr 合并）覆盖。
- [x] **接线验证**：修复后的合并逻辑被命令执行器运行时调用（测试经 executor.execute 触发）。
- [x] **无静默跳过**：无空实现/吞错。
- [x] `No owner-doc update required`（shell 行为修复，语义不变）。
- [x] `ai-dev/logs/2026/09-14.md` 对应条目已更新。

## Phase 5 — FeishuConnector.isBotMentioned：@任意成员不得触发 agent

Status: planned

Targets: `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/channel/feishu/FeishuConnector.java`、`TestFeishuConnector.java`

- Item Types: `Fix | Proof`

- [x] `Fix` `isBotMentioned`（:586-617）改为精确匹配 bot 自身身份：优先从已解析的 `FeishuCredentials`/ChannelConfig（:619-641 resolveCredentials 产物）取 bot open_id（或 app_id 对应的 open_id），仅当 mentions 中存在该 open_id 才判定为 @bot；无法获得 bot 身份时 fail-closed（不触发），并更新类注释中 "bot open_id 精确匹配 deferred" 的旧声明。
- [x] `Fix` 对不可解析的 mentions 形态（缺 open_id 结构）保持现有"未 @"语义（fail-closed），合并为一个统一判定函数，删除纯子串双条件检查。
- [x] `Fix` 新增回归测试（TestFeishuConnector.java:356-371 扩展）：
  - 负例：群消息 @了**其他用户**（mentions 含他人 open_id、含 "key"/"open_id" 标记但不含 bot open_id）→ 不触发 engine.execute；
  - 正例：@bot（含 bot open_id）→ 触发；
  - 单聊消息不受影响（非群聊路径不进 isBotMentioned）。
- [x] `Proof` 复核 onMessage 群聊分支（:248）确认过滤后无旁路入口。

Exit Criteria:

- [x] 群消息 @其他成员不触发 IAgentEngine 执行（engine.executeCount 不变为证）。
- [x] @bot 仍正常触发；单聊行为不变。
- [x] **端到端验证**：从 Feishu 入站消息到 engine.execute 的完整路径覆盖三个场景（@bot / @他人 / 单聊）。
- [x] **接线验证**：精确匹配逻辑被 onMessage 群聊分支运行时调用。
- [x] **无静默跳过**：bot 身份缺失时 fail-closed 且有日志说明，不静默放行。
- [x] owner doc 同步：`docs-for-ai/03-modules/nop-ai-gateway.md` 已核实无 Feishu 触发语义内容 → `No owner-doc update required`；若执行中发现该文档含相关语义，则补一行精确匹配说明。
- [x] `ai-dev/logs/2026/09-14.md` 对应条目已更新。

## Draft Review Record

（待独立 reviewer 填写；drafter 不自行 dispatch）
- dispatch review #review-2026-09-14-110620-2026-09-14-1314-2-m6-p1-round2-remediation-1-3261252e to opencode-pid-31925
- 2026-09-14：iteration 1，共识 approved #review-2026-09-14-110620-2026-09-14-1314-2-m6-p1-round2-remediation-1-3261252e

## Verification

- pass test 20260914-1540 exit=0

## Closure

- dispatch audit #audit-2026-09-14-110620-2026-09-14-1314-2-m6-p1-round2-remediation-1-14fe4b21 to opencode-closure-audit-53654 models={exec:opencode-go/deepseek-v4-flash,aud:opencode-go/deepseek-v4-flash}
- accepted #audit-2026-09-14-110620-2026-09-14-1314-2-m6-p1-round2-remediation-1-14fe4b21：5×P1 修复经独立 closure audit 全部在 live repo 落地且运行时接线连通（ReActAgentExecutor.java:1290-1298 排除 failed、DefaultAgentEngine.java:811 + AgentSessionLifecycle 3 处 slotRegistered 守卫、FileToolBizModel.java:273-281 fail-closed、ShellCommandExecutor.java:506-507 单一输出目标、FeishuConnector.java:611-656 bot open_id 精确匹配），回归测试全绿（TestReActAgentExecutor 11、TestSessionTakeoverLockEngineWiring 7、TestFileToolBizModelProjectDir 3、ShellCommandExecutorTest 25、TestFeishuConnector 19 + TestFeishuConversationE2E 5，0 失败）；check-doc-links 0 error；plan-check --strict 55/55 通过