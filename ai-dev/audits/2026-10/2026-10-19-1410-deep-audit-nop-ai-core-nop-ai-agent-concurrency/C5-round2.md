# C5 深挖第 2 轮 — nop-ai-core-nop-ai-agent（WI 3.1）

## 深挖范围与方法

- **深挖面（任务指派五项，逐一处置）**：① usage 统计累积；② talent/skill 装配物化；③ message 压缩物化峰值；④ quota 记账精度漂移（long 溢出面）；⑤ fencing token 空间。
- **方法**：`usage/` 6 文件全文（DbUsageRecorder/UsageRecord/NopAiChatResponseTable/SimpleUsageRecorder 等）；`skill/` 14 文件（SkillResolver/LLMCurator/FileSystemSkillProvider/SkillAssemblyResult 全文）+ `talent/` 2 文件（ITalent/NoOpTalent）；`compact/` 14 文件复读（PipelineCompactor/Layer2TurnPruningStrategy/Layer3FullSummaryStrategy/MicroCompressionCompactor/ReferenceCompactionStrategy/ToolResultTruncator/ToolOutputBudgetResolver/InMemorySpillStore/双 archive）；`quota/` 6 文件（DefaultResourceGuard/QuotaConfig）+ `budget/` 3 文件（BudgetSnapshot/IBudgetProvider）+ `engine/AgentExecutionContext`（tokensUsed 字段类型 :31,174-178）与 `AbstractLlmDialect.parseUsage`（:612-678，Integer 路径）；`fencing/` 5 文件（DefaultFencingTokenService/NoOp/FencingToken 值对象）。
- **盲审纪律**：仅读第 1 轮五文件 + 本文件；未读其他 audits/plans/bugs/lessons/backlog；未查 git。行号已在审计工作树核验。

## 新增发现

本轮无新增 C5 立案。五项深挖面处置如下。

### 深挖面处置表（五面 × 复核证据 × 判定）

| 深挖面 | 本轮复读证据 | 判定 |
|---|---|---|
| ① usage 统计累积 | `DbUsageRecorder.java`：无共享可变状态（第 1 轮清单 E12 通过）；**表行累积面已由第 1 轮 CL-051 登记**（INSERT-only、无 TTL/归档，nop-ai-core 侧 `nop_ai_chat_response` 表）；本轮新增证据仅为**每 record 一次无缓存 `resolveModelId` SELECT**（:184-208）——属热路径 CPU/延迟面而非内存界，已并入 `C3-round2.md` C3-029 一并立案（同一站点的异常传导 + 延迟耦合），不在 C5 重复计数。`UsageRecord` 为 per-call 值对象，无累积 | 通过（CL-051 已盖 + C3-029 承接新证据） |
| ② talent/skill 装配物化 | `SkillResolver.resolve:60-108`：per-resolve 新建 registry map + builder（栈封闭，随 execute 结束 GC）；`FileSystemSkillProvider.getSkills` DCL 缓存后只读（第 1 轮清单 E2 通过）——装配物化 = 每执行一次 O(registry) 轻量拷贝，registry 为配置面有界集合；`LLMCurator.curate:76-89`：分批物化 prompt（`buildRequest:168-195` StringBuilder 有界于 batchSize）、结果 assessments 有界于批大小；`talent/` 两文件为接口 + NoOp，无状态无容器 | 通过（全部有界/栈封闭） |
| ③ message 压缩物化峰值 | 复读 `PipelineCompactor.compact:56-146`：`current` 列表（:81/:116）与每层 `rebuildContext`（:158-175）均为栈内瞬态副本，层数 ≤3，峰值 ≤3× 上下文尺寸——第 1 轮 C5-010 失效面 ③ 已按该口径判定「瞬态 1× 拷贝不单列」；`Layer2TurnPruningStrategy`（:89/:116-126）与 `MicroCompressionCompactor:47,97` 的中间列表同为栈内新建；`Layer3FullSummaryStrategy` 的 subList/分段（:149-150,:201,:242）为瞬态视图/副本；持久化累积面（spill/双 archive/摘要归档）已由 C5-010/C5-011/C7-031/CL-070 四条覆盖 | 通过（瞬态有界；持久面已立案） |
| ④ quota 记账精度漂移（long 溢出面） | `DefaultResourceGuard.java`：guard 本体无状态（第 1 轮清单 E11 通过，计数外置于执法点）；计数源均为集合 size()/快照计数（int），无 long 累加面；`AgentExecutionContext.tokensUsed` 为 **long**（:31，`setTokensUsed(long)`:177-178），累加式 `ctx.getTokensUsed() + promptTokens + completionTokens`（ReActAgentExecutor:1073）——溢出需单执行 2^63 token，物理不可达；`BudgetSnapshot`（budget/BudgetSnapshot.java:19-69）：不可变值对象，cost 用 `BigDecimal.compareTo`（无精度漂移），`exceeded` 在构造器内强制重算（:50-56，契约自洽）；`AbstractLlmDialect.parseUsage:629-665`：tokens 走 `getIntByPath`（Integer），provider 单响应 token 量级远低于 int 上界，无截断面 | 通过（无溢出/漂移面） |
| ⑤ fencing token 空间 | `DefaultFencingTokenService.java:58-89`：`issueCounters`/`recordedHighWater` 双 CHM<String,AtomicLong>，`incrementAndGet` 溢出需 2^63 次发行/actor——物理不可达；validate 的 only-if-greater CAS 无回绕比较缺陷（单调递增前提下 `currentValue.get() < token` 比较对 2^63 内全序成立）；生命周期面（无删除/复位 API、休眠零接线）已由第 1 轮 C1-027 完整立案（含跨会话高水位残留语义风险），本轮复读维持 | 通过（C1-027 已盖） |

## 维度集外线索（overlay）

本轮无新增集外线索（CL 流水无追加；本轮发现的唯一新站点证据——DbUsageRecorder.resolveModelId 每 record 一次 SELECT——已并入 C3-029 立案，不另立 CL）。

## 继续判定

**终止信号**。判定依据：新增立案 0 条；五项深挖面全部复核收口——usage 面的持久累积已由 CL-051 承接、talent/skill 装配物化全部栈封闭或有界、压缩物化峰值全部瞬态有界（持久面四条已立案）、quota/预算链无 long 溢出与精度漂移面（long 字段 + BigDecimal 比较逐点核验）、fencing 空间无回绕缺陷（C1-027 已盖生命周期）。第 1 轮 22 条（C5-001…C5-022）+ 14 条 CL（CL-041…CL-054）+ 本轮五面复读共同构成 C5 维度收敛证据：随输入增长容器清单三批次收官（20+20+26 项人工复现）+ 深挖面无新无界站点。

---

## 独立复核结论（WI 3.1 Phase 4，2026-10-20）

- **对本文件的抽查意见**：**零新增立案成立**。五项深挖面处置表的证据与定性经独立回码抽查未见反证——①usage 面表行累积归 CL-051、每 record 无缓存 SELECT 归 C3-029 的分账口径正确（不重复计数）；③压缩物化瞬态有界判定与第 1 轮 C5-010 失效面③口径一致，且持久面四条（C5-010/C5-011/C7-031/CL-070）覆盖清单经与本维最终保留项核对无遗漏；④quota/预算链无溢出面、⑤fencing 生命周期归 C1-027 的引用边界均正确（不重复立案）。
- **对第 1 轮的复核联动**：22 条第 1 轮发现已在本轮 Phase 4 全量回码复核（保留 19 / 降级 1 / 驳回 1，详见 `C5-memory-bounds.md` 维度复核结论节）——本文件「持久化累积面已由四条覆盖」的引用对象中，C5-019（若涉 writeIntent）不在保留清单，其余引用全部有效；CL-070 为 C7 维度登记，跨维引用边界正确。
- **结论**：本文件维持终止信号有效；C5 维度以 21 条最终保留项（P1×4/P2×11/P3×6）+ 14 条 CL 收口，进入 Phase 5（C10 测试保护力）。
