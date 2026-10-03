# 2298 WI17 并发安全标记 + 按路径串行队列（refactor roadmap M5）

> Plan Status: completed
> Last Reviewed: 2026-10-03
> Source: `ai-dev/backlog/nop-ai-agent-refactor-roadmap.md` WI17（Deliverable: 代码变更 + 测试；deps: 无）
> Baseline Ruling: `ai-dev/analysis/2026-10/2026-10-02-wi1-dedup-ruling.md`（WI17 吸收，无 autonomous 重叠）
> Design Contract: Owner `ai-dev/design/nop-ai-agent/04-tool-invocation.md`
> Adversarial Review: round 1 子代理 agent_b29f7a4a——1 Blocker（生产并行 fan-out 走 dispatcher 逐个 callTool，executeParallel 零生产调用方——锁必须在 callTool chokepoint）+ 2 Major（Semaphore 叠加语义/重入自死锁登记）已吸收

## Purpose

dsh-D7 建议1：并行工具对同路径文件有竞态风险。现状 `ToolManagerImpl.executeParallel` 只有 in-flight Semaphore 上限（plan 2026-09-15-0818-3，超限排队），无并发安全概念——两个并行 write-file/patch-file 同路径即竞态。本项：工具声明并发安全标记；不安全工具按路径亲和串行（同路径排队、异路径并行），与 Semaphore 正交叠加。

## Current Baseline

（执行时 HEAD `f01a7e2ba1`，2026-10-03）

- `ToolManagerImpl.executeParallel`（nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/manager/ToolManagerImpl.java:71-110）：Semaphore（fair，maxConcurrency>0 时）+ `submitBounded`（:112-130 permit 持有至完成）；结果按提交序 join。
- `loadTool(toolName)`（:165）返回 AiToolModel——WI16 已证 xdef 新元素经 precompile 再生到该模型。
- 文件工具路径参数名：`path`（write-file/patch-file/copy-file/delete-file/move-file/create-dir 等 `<path="!full-path">`），schema XML 声明。
- AiToolCall 输入：`getInput()` JSON 文本（dispatch 侧 setInput(argumentsText)）。

## Goals

- **tool.xdef**：新增可选 `<concurrency-safe>!boolean=false</concurrency-safe>`（默认 false=不安全=参与路径串行；安全工具显式声明 true 豁免）——additive 零改动兼容；生成 `AiToolModel.isConcurrencySafe()`。
- **路径亲和提取**（`ToolPathAffinity` 新类，toolkit）：从 AiToolCall 输入 JSON 提取保留键 `path`/`file_path`/`filePath` 的首个字符串值；畸形输入返回 null（不参与串行、不报错——与 WI16 预算旋钮同故障面纪律）。
- **按路径串行队列**（`ToolManagerImpl.callTool`——全进程唯一 chokepoint：dispatcher fan-out/callTools/CLI/call-agent 子代理全经共享单例走此；executeParallel 无生产调用方，round 1 Blocker 裁定）：不安全工具 && 提取到路径 → per-path 非阻塞未来链队列（`ConcurrentHashMap<String, CompletableFuture<Void>>` + compute + thenCompose 链——链式排队，**不阻塞调用线程**，不影响 WI4/WI5 wait-phase 启动）；链消费前 handle 归一化前驱异常。同路径排队、异路径并行。锁键 manager 实例级全局（跨 session 保守串行——同进程写同一文件本就应互斥）。**与 Semaphore 叠加语义（round 1 Major 2）**：dispatcher 侧无 Semaphore（其在 callTools 面）；callTools 面下同路径排队占 permit 至真实完成——排队不锁死但同路径队列可耗尽 permit 预算推迟异路径调用（吞吐公平性，文档化）。
- **测试**：同路径两并行调用串行完成（时序断言）、异路径并行、safe 标记豁免、无路径不串行、畸形输入容错、Semaphore 叠加不回退、零回归。

## Non-Goals

- 跨进程文件锁；路径规范化（../ 归一——同一路径不同写法视为不同键，如实登记 residual）；WI25 后台模式。

## Scope

### In Scope

- tool.xdef + 生成模型；ToolPathAffinity；ToolManagerImpl 并行路径串行；测试 + owner doc + log。

### Out Of Scope

- 顺序执行路径（executeSequential 天然串行）；dispatcher 层改动。

## Execution Plan

### Phase 1 - 声明 + 提取 + 串行队列

Status: completed
Targets: nop-xdefs/tool.xdef、nop-ai-toolkit（ToolManagerImpl、ToolPathAffinity、生成模型）

- Item Types: `Feature`

- [x] xdef `<concurrency-safe>` + 再生；`ToolPathAffinity.extract(call)`；ToolManagerImpl.callTool chokepoint per-path 非阻塞未来链（**执行体惰性**——executor 调用在 thenCompose 内，排队调用不提前启动；执行中修复：初版链只串回调未串执行，测试时序断言当场暴露）
- [x] 测试：TestToolPathQueue 4 个（同路径 chokepoint 串行时序 latch 确定性/异路径并行/safe 豁免 loadTool seam/畸形与无路径容错）+ ToolManagerImplParallelTest 5 零回退

Exit Criteria:

- [x] 测试全绿；`./mvnw test -pl nop-ai/nop-ai-agent -am` 通过（3514 全绿，含 toolkit）
- [x] **接线验证**：dispatcher fan-out（AgentToolDispatcher.java:676）→ callTool → pathQueues → 惰性执行链连通（同路径串行时序断言即证明）
- [x] **无静默跳过**：提取失败=文档化降级（不串行），无空方法体
- [x] `ai-dev/logs/` 条目更新（owner doc 归 Phase 2 显式归属）

### Phase 2 - owner doc 与收口

Status: completed
Targets: `ai-dev/design/nop-ai-agent/04-tool-invocation.md`、log、roadmap

- Item Types: `Proof`

- [x] owner doc"并发安全与路径串行"节（标记语义、保留键、锁粒度裁定、residual）；doc-links 0 error；roadmap 勾选+锚点；closure audit + 工具门 + commit

Exit Criteria:

- [x] owner doc 在位；全部验证门通过

## Closure Gates

- [x] 路径串行端到端连通（并行 fan-out→提取→锁→串行化）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 通过（3514 全绿）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-agent --severity high` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2298-wi17-concurrency-safety-path-queue.md --strict` 退出码 0
- [x] checkstyle：commit 钩子承担（No separate checkstyle gate required）
- [x] 受影响 owner docs 已同步
- [x] git commit 完成（排除无关杂散改动）

## Known Residuals

- 路径键不做规范化（a/b.txt 与 ./a/b.txt 视为不同键）——同写法竞态已封、异写法同文件仍可能并发（optimization candidate）。
- **重入自死锁**：带 path 参数且同步等待嵌套同路径 callTool 的自定义工具会自死锁（未来链 prev.thenCompose 形式同样等 prev）——现网工具无此形态（call-agent 无 path 键），登记为自定义工具契约限制（round 1 Major 3）。
- **数据源声明**：保留键提取以 `AiToolCall.getInput()` JSON 为准（dispatch 路径唯一数据源；executor 内 node attr 读取为另一数据源，不在本项范围）。
- 同路径队列排队期间占用的 dispatcher 无 timeout 语义不变；callTools 面的 permit 持有窗口覆盖排队期（吞吐公平性，文档化）。
- **pathQueues 无清理机制**（audit Low#1）：per-path tail 完成后驻留，长生命周期进程 + 海量不同路径键为 per-key 有界、per-key-count 无界增长（optimization candidate：弱引用或 LRU 清理）。

## Closure

Status Note: round 1 Blocker（锁位置——生产 fan-out 走 callTool 非 executeParallel）与执行中修（执行体惰性化）均以 live 代码结构证实并有确定性时序测试；xdef 声明/提取器/chokepoint 队列/文档全链落地；audit PASS（7 PASS + 1 合规中间态），Low 项已处置（pathQueues 无界登记 residual、log 措辞修正）——WI17 交付全部落地。
Completed: 2026-10-03

Closure Audit Evidence:

- Reviewer / Agent: 独立子代理 agent_f6a1cb4c-9c7a-4021-8b8f-f50a03f3f0d0（fresh session）
- Evidence:
  - 8 验证点 7 PASS + 1 合规中间态（惰性执行结构 :70-77/:96、归一化 :93-95、保守 false :112-122）
  - TestToolPathQueue 4/4 + ToolManagerImplParallelTest 5/5；全量 3514 全绿
  - hollow/doc-links/checklist exit 0；Anti-Hollow 链 dispatcher:676→callTool→pathQueues→惰性执行
  - Low 处置：pathQueues 无界登记 Known Residuals；"无裸 sleep"表述修正（负断言宽限期 sleep 为正当手法）

Follow-up:

- 见 Known Residuals
