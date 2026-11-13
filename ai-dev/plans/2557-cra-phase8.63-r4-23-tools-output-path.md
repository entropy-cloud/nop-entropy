# 2557 CRA Phase 8.63 — R4-23 3.2 工具输出/路径束（10 工单）

> Plan Status: completed
> Last Reviewed: 2026-11-11
> Source: roadmap 工作项 8.63；批次定义 = batch-proposal.md §二 :138 R4-23 行；成员发现 = `ai-dev/audits/2026-10/2026-10-20-0829-deep-audit-nop-ai-toolkit-tools-shell-concurrency/`
> Related: plan 2501（同单元兄弟批，ToolManagerImpl/BashExecutionTracker 锚点漂移源）；设计文档 = `../design/concurrency/ai-tools-output-path-batch.md`（裁定 A-J）
> **PA**：无（10 项全 Java 层）
> **特殊约束**：① C6-009 落地形态 = cancel 条目标记+延迟清理，不改 remove 顺序外部契约（宽口径兜底留 7.1）；② C8-010+C8-012 同批协调（C10-008 一张保护面）；③ C1-005 跨 toolkit/tools 两模块；④ 2501 锚点漂移（ToolManagerImpl :37-38/:157-170；BashExecutionTracker +2 行）
> 成员取证（fresh session @ 2026-11-11）：10/10 原样

## Purpose

关闭 R4-23 的 **10 个 P2 工单**：账本生命周期族（MA-07 pathQueues 驱逐、C1-004 todoLists 原子化、C6-007 ThoughtStorage 删除+TTL）、一致性族（C1-005 原子写四站点、C4-007 submit→run 回滚、C6-009 cancel 可重试）、输出上界族（C8-007 响应体帽、C8-008 七失效面 clamp、C8-010/012 shell IO 字节帽）。

## Baseline（live 亲读 @ 2026-11-11，取证 agent 独立核验；10/10 原样）

- `ToolManagerImpl.java:37-38,157-170`（pathQueues 唯一写点 compute、全文件无 remove/evict）；`UpdateTodosExecutor.java:25,73,95,100,103`（getOrDefault→put 丢单更新窗）。
- `DeltaVirtualFileSystem.java:164-167,190-191`（TRUNCATE_EXISTING/REPLACE_EXISTING 非原子写）+ 扩展面 `DslToolImpl.java:81`/`AiFileTool`（ResourceHelper.writeText 就地写）。
- `BashExecutor.java:209,220` + `IBackgroundTaskRegistry.java:42-47`（run UOE → RUNNING 永久滞留）。
- `ThoughtStorage.java:97,139-146`（clearHistory 只覆写空内容）；`BashExecutionTracker.java:84-105`（remove-before-act + destroy 无守卫；2501 safeClose 补偿 :92-95）。
- `HttpRequestExecutor.java:246` + `GraphqlQueryExecutor.java:156,158`（getBodyAsString 无上界）。
- 七失效面：`SearchContentExecutor.java:40-41`/`SearchFilesExecutor`/`ReadFileExecutor.java:39-53`/`PatchFileExecutor.java:53`/`FileToolBizModel.java:64,71`（ApplyDelta 帽在位无测）。
- `FileShellOutput.java:26-48`（无界 buffer+整体 flush）+ `AbstractShellInput.java:28,57,65`/`FileShellInput.java:27`/`BlockingQueueShellOutput.write`（字节无界）。
- 基线命令：`./mvnw -o test -pl nop-ai/nop-ai-toolkit,nop-ai/nop-ai-tools,nop-ai/nop-ai-shell,nop-ai/nop-ai-agent -am`（C6-009 回归面含 nop-ai-agent cancel 钉族[R1-Major-2]；纯 in-JVM 无 docker）

## Goals

- 10 工单全部落地且载体先红后绿或变异论证（≥3 链）。
- 2501 兄弟工单回归零回退（TestSandboxReaderExitCompensation_C6_001 等）。
- owner doc `docs-for-ai/03-modules/nop-ai.md` 落账。

## Non-Goals

- C6-009 宽口径兜底（语义变更决策留 7.1）；C6-007 headerless 放大面（依赖认证配置，7.1 注记）；CL-010/CL-020 关联线索（同批核对不并入）。

## Execution Plan

### Phase 1 - 设计与取证

Status: completed（设计裁定 A-J + fresh-session 取证 10/10 原样 + 2501 锚点漂移核验）

- [x] 设计文档（裁定 A-J + 10 载体表 + 回归接缝）
- [x] 成员取证（fresh session：10/10 原样）
- [x] 独立 draft review（R1 must-fix-first → R2 ready-to-execute；记录见 Review 记录）

### Phase 2 - 10 工单实现 + 回归测试

Status: completed（10 工单全落盘 @ 2026-11-11；四腿红证 `_tmp/red-green-ledger-2557.md`；中断恢复续跑完成——披露于 ledger）

- Item Types: `Fix`（10）

**实施顺序**

- 组 1（nop-ai-toolkit）：MA-07+A → C1-004+B（仅原子性）→ C4-007+D → C6-009+F → C8-007+G → C8-008+H → C1-005+C（mavenvfs 面）
- 组 2（nop-ai-tools + nop-ai-shell）：C1-005+C（扩展面）→ C6-007+E → C8-008+H（FileToolBizModel/ApplyDelta 回归）→ C8-010/012+I/J

Exit Criteria:

- [x] 10 工单全部实施
- [x] 载体表逐条存在且断言目标行为
- [x] 关键项先红后绿或变异论证（≥3：MA-07 驱逐、C6-009 可重试[分层诚实登记]、C8-010 分块落盘）
- [x] **C6-009 形态约束**：remove 顺序外部契约零变化（结构钉）
- [x] **2501 回归零回退**：TestSandboxReaderExitCompensation_C6_001 等全绿
- [x] E1 全量编译门禁 BUILD SUCCESS（排除 nop-cli 已知面；`_tmp/e1-2557-full-build-excli.log`）
- [x] E3：三模块逐模块归因绿（toolkit 344/tools 62/shell 314[1 pre-existing error=ShellConcurrencyEdgeCaseTest#testCloseIdempotent，HEAD stash 验证零 diff 豁免]）+ nop-ai-agent cancel 钉族 15/15 绿
- [x] owner doc 落账：`docs-for-ai/03-modules/nop-ai.md`（工具执行与输出包络契约节）
- [x] 工具门禁：check-doc-links --strict EXIT=0；scan-hollow 三叶子模块全 EXIT=0
- [x] `ai-dev/logs/` 已更新

## Closure Gates

- [x] 所有 in-scope confirmed live defects 已修复（10 工单）
- [x] 所有 in-scope confirmed contract drifts 已收敛
- [x] 行为/契约结果已达成
- [x] 必要 focused verification 已完成
- [x] 不存在被静默降级到 deferred 的 in-scope live defect
- [x] 受影响 owner docs 已同步
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] Anti-Hollow Check：closure audit 验证调用链连通与无空壳
- [x] `./mvnw compile`（三模块）
- [x] `./mvnw test`（三模块逐模块）
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### C6-009 宽口径兜底 / C6-007 headerless 放大面
- Classification: `watch-only residual`
- Why Not Blocking Closure: 均为语义变更决策（前者）或认证配置依赖面（后者），裁定表明示留 7.1；本批收敛主体缺陷（可重试取消/删除+TTL）。closure audit Minor-3（root 销毁失败路径 safeClose 补偿跳过）随 7.1 一并裁定。
- Successor Required: `no`

### C1-004 会话条目清理面
- Classification: `watch-only residual`（R1-Major-1：toolkit 无 session-lifecycle seam，cross-module agent 侧 seam 属 7.1 域）
- Why Not Blocking Closure: 本批收敛原子性主体缺陷（compute 单次决策）；条目缓增面与 MA-07 驱逐同族，随未来 session-lifecycle seam 批落地。
- Successor Required: `no`

## Review 记录

**Draft review R1（fresh session，2026-11-11，must-fix-first）**：Major-1 裁定 B session-hook 引用不存在的 seam（toolkit grep 零命中）→ F4 清理面移出 scope+Deferred 登记（选项 a）；Major-2 裁定 F 返回值语义未定+回归面缺 nop-ai-agent → rethrow 形态（putIfAbsent 回填+保留 boolean 契约）+基线命令补 agent 钉族；Major-3 裁定 I append 陷阱未述 → 首 flush append=false 后续切 true+超帽=命令失败 fail-loud；Major-4 裁定 J truncated 标记无通道 → 分片可观测契约+readAllText 抛 NopException；m5 QueueOverflowPolicy.ERROR 勘正/计数域、m6 锚点、m7 virtual-resource 分支、m8 TTL 宿主=saveSession。R2 ready-to-execute（4 处措辞同步完成）。

## Closure

Status Note: 8.63/R4-23 全 10 工单落地（10 Fix），28+19 载体钉全绿，四腿红证（3 变异红 + C1-005 失败保旧钉），2501 钉族零回退，owner doc 落账，E1 排除形态 + E3 四模块绿（shell 1 pre-existing error HEAD-stash 豁免）。首轮 closure audit 判 REJECTED——唯一 Blocker 为 plan 文件遭误写覆盖（与 docs 页逐字节相同，untracked 无法 git 恢复）；代码实质审计为全 PASS（10/10 机制、独立复跑精确一致、Anti-Hollow 三链、豁免独立确认）。plan 文件重建后本 evidence 即位，按审计指示无需重审代码直接转 APPROVED。
Completed: 2026-11-11

Closure Audit Evidence:

- Reviewer / Agent: 独立 fresh-session closure audit 子 agent（2026-11-11；首轮 REJECTED[唯一 Blocker=plan 文件覆盖]，代码实质 10/10 PASS）
- Evidence:
  - 10/10 机制 live 取证命中（逐一 file:line：settlePathQueue 两参驱逐+计数+ERR_AI_TOOLKIT_PATH_QUEUE_OVERFLOW+pathQueueSize/compute 单次决策/AtomicMoveOutputStream+writeLocalOrVfs FileResource 分支/UOE→FAILED 回滚/destroy 守卫+putIfAbsent 回填+rethrow+safeClose 降级共存/clearHistory deleteIfExists+TTL saveSession 节流/capBody 截断+graphql 双路径/七面 clamp+ApplyDelta 常量+回归钉/64K 分块+append 陷阱正确+2MiB fail-loud/write 拆分+分片拼接+readAllText NopException+FileShellInput 分片）
  - 独立复跑：toolkit 344 / tools 62 / shell 314（唯一 error pre-existing）+ agent 六钉族 15/15 全 BUILD SUCCESS
  - Anti-Hollow 三链 PASS（pathQueues 链/cancel 可重试链/FileShellOutput 阈值链）；scan-hollow 三模块 EXIT=0
  - Deferred 诚实性 PASS（三条 seam-missing/范围理由）；中断恢复披露核验（ToolManagerImpl 终态与裁定 A 逐点吻合，无孤儿脚手架）
  - 预存豁免独立确认（auditor 自行 stash push -u 验证 ShellConcurrencyEdgeCaseTest HEAD 同失败，stash pop 干净）
  - Blocker 修正：plan 文件本体重建（含全部状态/勾选/evidence）——修正后转 APPROVED
  - `check-plan-checklist.mjs --strict` EXIT=0（重建后非空转通过确认）

Follow-up:

- BashExecutionTracker destroy-failure 分支注释重复行清理（cosmetic）
- GraphqlQueryExecutor 错误路径 getBodyAsString 双调用（依赖 body 可重复读，无缺陷证据；观察项）
- UpdateTodosExecutor compute mapping 内 changesSummary StringBuilder 副作用（争用重试理论可重复追加呈现文本；建议移出 compute 域）
