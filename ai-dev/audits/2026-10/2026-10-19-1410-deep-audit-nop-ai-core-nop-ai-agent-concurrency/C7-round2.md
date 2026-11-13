# C7 深挖第 2 轮 — nop-ai-core-nop-ai-agent（WI 3.1）

## 深挖范围与方法

- **深挖面（任务指派五项，逐一处置）**：① team flow 的编排死锁组合面；② guardrule 校验热循环退化；③ router 故障切换震荡；④ conflict 裁决等待；⑤ repair 循环收敛性。
- **方法**：`team/flow/MemberFanOutDispatcher.java`（全文 443 行——BOUND/SPAWN 双路径 future 构建、allOf→reduce→complete 链、两层 orTimeout 防护逐行核对）；`team/flow/TeamTaskFlowOrchestrator` 池供给与 javadoc 警示（:189-198/:406）复读；`team/scheduler/TaskDispatchCoordinator` 派发链复读；`engine/LlmCallCoordinator.java:600-990` 全段（doProviderFailover/doModelTierFallback/checkFallbackStepCap/resolveCircuitAware/三重上限常量 :941-970/sleepBackoff）；`engine/AgentLoopGuard.java`（全文，denial-pause/goal-stuck/forced-stop 链）；`engine/CalibratedTokenEstimator` + `dialect/ILlmDialect.estimateTokensDefault`（:42-59，O(n) 无分配 length-sum 复核）；`guardrail/`（PromptInjectionGuardrail 静态预编译先例）+ CL-011 三站点判定复读；`conflict/FailFastStrategy.java`（全文 79 行）+ `conflict/InMemoryWriteIntentRegistry` 判定复读；`repair/` 8 文件（ChainRepairer 单遍流水、ArgumentValueCoercionStage/ArgumentCleanupStage 的 resolveParamTypes 异常降级面）；`skill/LLMCurator` 批式调用链（扩展 CL-012 证据，登记于 C3-round2）。
- **盲审纪律**：仅读第 1 轮五文件 + 本文件；未读其他 audits/plans/bugs/lessons/backlog；未查 git。行号已在审计工作树核验。

## 新增发现

本轮无新增 C7 立案。五项深挖面处置如下。

### 深挖面处置表（五面 × 复核证据 × 判定）

| 深挖面 | 本轮复读证据 | 判定 |
|---|---|---|
| ① team flow 编排死锁组合面 | `MemberFanOutDispatcher.dispatch:160-295`：BOUND 目标 `agentEngine.execute(...).orTimeout(memberExecTimeoutMs)`（:321-322，异步非阻塞构建）；SPAWN 目标 `supplyAsync(..., spawnExecutor)` + dispatcher 层 orTimeout（:374-383）；reduce 链 `allOf→thenApply(f.join())→thenCompose`（:243-289）——join 仅消费**已 settle**（orTimeout 界定）的 future，:366 注释自证「this is what makes dispatch's reduce f.join() bounded」；cross-wait 组合仅存于「spawn worker 同步 get 引擎 future」（DefaultMemberSpawner:188，有界 get）且两侧池分离（spawn 池 vs agentExecutor）——共享池前提下的停摆形态已被第 1 轮 CL-025（AgentDispatchExecutor→commonPool）与 TeamTaskFlowOrchestrator:189-198 javadoc 警示 + C4-006（同池嵌套）+ C4-014/C4-015（取消不可达面）+ C7-022/C7-023/C7-024（僵尸族）覆盖 | 通过（既有立案已覆盖全部可达组合；无新死锁环） |
| ② guardrule 校验热循环退化 | 每内容检查重复 `Pattern.compile` ×3 站点 + 坏 pattern 静默失效已由第 1 轮 CL-011 完整立案（含 `PromptInjectionGuardrail:37-61` static 预编译正确先例对照）；本轮复读 `RuleGraphGuardrail.check:83-127` 与 `RuleResultAggregator` 维持原判定；无新增站点（同族第四面 dialect 错误映射路径已登记于 `C1-round2.md` CL-076，归 C9 流水） | 通过（CL-011 已盖 + CL-076 并流） |
| ③ router 故障切换震荡 | `LlmCallCoordinator` 三重上限逐行核验：`MAX_FALLBACK_SCAN=64`（:941，circuit-aware 扫描硬帽）、`MAX_FALLBACK_STEPS=16`（:970，全部 FALLBACK 通道共享步数帽，:697-704 checkFallbackStepCap 每次 switch 计数、超帽 BREAK fail-loud :679-686）、`MAX_EXECUTION_VETOES=3`（:954）；`SmartModelRouter.getFallback:126-156` 为纯函数（tierSequences 构建后不可变，批次 E 清单 E8 通过），无状态游走器、无记忆效应——A→B→A 循环由 16 步帽兜底并 fail-loud（:684-685 错误消息明示该场景）；`resolveCircuitAware:888-930` 每迭代重判，OPEN 状态切换有 LOG.warn 可观测（:913-915） | 通过（上限守卫齐备，无震荡发散面） |
| ④ conflict 裁决等待 | `FailFastStrategy.resolve:37-63`：纯函数、同步判定，**无任何等待/阻塞原语**（fail-fast 即 deny）；registry 侧 per-bucket monitor 仅覆盖桶内增删（第 1 轮 C1-026/C5-019 已盖生命周期与假阳性面）；等待形态在本模块不存在（无 lockInterruptibly/wait/poll 等待点新增——第 1 轮挂死点清单 13 项已全量盘点） | 通过（无等待面；C5-019 假阳性已立案） |
| ⑤ repair 循环收敛性 | `ChainRepairer.repair:74-80`：**单遍**四 stage 固定顺序流水，无循环、无重试、无 stage 间回边（threading 输出单向）；各 stage 输入输出均为 `ChatToolCall` 值对象（copy 语义，:75），无收敛性问题；`resolveParamTypes` 异常降级为 emptyMap（ArgumentValueCoercionStage:80-91 catch-all）——单 tool call 粒度的 fail-soft，无状态残留；repair 触发点为每 tool call 一次（ReActAgentExecutor:1585/:1714），次数受 ReAct 迭代预算约束 | 通过（单遍有界，无循环） |

### 复核中的否定性结论（防止误报，记录备查）

- **每迭代 token 估算扫描**：`AgentLoopGuard.shouldForceStop:92` + `recordLlmTurnUsage→tokenEstimator.record:1088` 每 ReAct 迭代对全量消息各做一次 `estimateTokensDefault`——经核验为 O(n) **无分配 length-sum**（`ILlmDialect.java:42-59`：`content.length()/4` 累加，无序列化无装箱），单次成本微秒级，与 CL-068（每 save 全前缀 JSON 序列化指纹，分配+反射重负载）不同量级；无量化收益依据，按 C9 口径「无量化依据的不报告」不予立案。
- **team flow reduce 链租户窗口**：`MemberFanOutDispatcher:268/:286` 的 set(capturedTenant)/clear() 无保存-恢复已由第 1 轮 C3-026 立案（主面），本轮复读确认 spawn 侧（:376-381/:233-237）自包含窗口豁免判定维持，无新面。

## 维度集外线索（overlay）

本轮无新增集外线索（CL 流水无追加；`LLMCurator` 无超时批式调用为既有 CL-012 的第三兄弟扩展，已按「扩展既有发现」登记于 `C3-round2.md`）。

## 继续判定

**终止信号**。判定依据：新增立案 0 条；五项深挖面全部复核收口——team flow 死锁组合面的可达集合被第 1 轮 6 条立案 + 2 条 CL 完整覆盖（orTimeout 两层防护 + 池分离 + 16/64/3 三重上限逐行核验）、guardrule 热循环由 CL-011+CL-076 承接、router 无震荡发散、conflict 无等待原语、repair 单遍收敛。第 1 轮 32 条（C7-001…C7-032）+ 20 条 CL（CL-055…CL-074）+ 本轮五面复读共同构成 C7 维度收敛证据：单调增长源清单 18 项、挂死点清单 13 项均已双轮对照，无新增增长源或挂死点。

---

## 复核结论（WI 3.1 C7 独立复核）

- **零新增立案维持**。五项深挖面处置表经独立回码抽查未见反证：① MemberFanOutDispatcher 双层 orTimeout 防护与「join 仅消费已 settle future」论证成立（:243-289/:321-337/:374-383 直核）；② CL-011/CL-076 承接 guardrule 热循环判定维持；③ router 三重上限（64/16/3）逐行核验成立、无震荡发散面；④ FailFastStrategy 纯函数无等待原语成立；⑤ ChainRepairer 单遍四 stage 无回边成立。两处否定性结论（token 估算 O(n) 无分配、fan-out 租户窗口自包含豁免）证据复核通过。
- 补充登记：本轮处置表 ① 所引「C7-022/C7-023/C7-024（僵尸族）」在第 1 轮主文件的独立复核中已确认成立（C7-022/023 P1 保留、与同站 C3-025/C4-014 的判级分叉留 7.1；C7-024 P2 保留）——本轮「既有立案已覆盖」的判定与主文件复核结论一致。
- 主文件 32 条复核结果与最终保留分布见 `C7-long-running.md`「维度复核结论」节：保留 31（P1×8/P2×19/P3×4）+ 降级 1（C7-013 P1→P2 条件化）+ 驳回 0。
