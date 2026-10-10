# Plan 2376: U3 限速泛化 XDSL 声明面（`<source rateLimitRowsPerSecond/rateLimitBytesPerSecond>`）

- Status: completed
- Item Type: Feature
- Roadmap: `ai-dev/backlog/nop-stream-deferred-unlock-roadmap.md` U3
- Related: WI21/plan 2367 Deferred 段（「限速声明面 Follow-up」的解除落地）；multi-engine WI5 `SourceRateLimiter`（引擎面）；`TestRateLimitValidationFace`（connector 参数面）
- Owner Direction: 2026-10-10「容易实现的部分都写计划自动完成」

## Purpose

把 multi-engine WI5 交付的引擎限速面（`SourceRateLimiter` 令牌桶）接到**作业 DSL** 上：任意 `<source>`（bean 或 xpl，不限 connector）可声明 `rateLimitRowsPerSecond`/`rateLimitBytesPerSecond`，builder 泛化包装。消除「connector 专属参数」与「bean 内自限速」二选一的限制（E1 场景的降级形态升级为声明面直达）。

## Background（代码事实锚点）

- `SourceRateLimiter`（nop-stream-core/source，:34-84）：令牌桶，`acquire(rows, bytes)` 阻塞获取（`MAX_SLEEP_SLICE_MS=100` 切片保证 cancel 延迟有界）；kafka/file connector 经 connector 参数消费（`KafkaSourceConnectorFactory.createSourceFunction` :93-99 `.withRateLimit(...)`）。
- `<source>` xdef（stream.xdef :160-169）：`maxParallelism`/`consistencyCapability` 属性 + `outputType`/`source`/`params` 子元素；无限速属性。
- `buildSource`（StreamModelDslBuilder :1707）：`resolveFunctionOrXpl(t, t.getBean(), t.getSource(), SourceFunction.class, XplSourceFunction::new)` 解析出 `SourceFunction<T>` 后直接 `env.addSource`——泛化包装点。
- connector 参数面是 connector 专属（conf 校验+工厂消费），不覆盖 bean 源。

## Scope

### In Scope

1. **core**：新增 `RateLimitedSourceFunction<T>`（`implements SourceFunction<T>`，包装委托 + 包装 `SourceContext`：`collect`/`collectWithTimestamp` 前 `acquire(1, payloadBytes)`；`cancel` 透传；serializable）。单位测试（token 消费计数）。
2. **xdef + 再生成**：`<source>` 增两属性 `rateLimitRowsPerSecond="long=0"`、`rateLimitBytesPerSecond="long=0"`（两步再生成；预期恰好 `_StreamSourceModel` 一处 _gen 变更）。
3. **builder**：`buildSource` 解析后，若 rows>0 或 bytes>0 → 包装 `new RateLimitedSourceFunction<>(fn, new SourceRateLimiter(rows, bytes))`；负值 typed reject（`nop.err.stream.invalid-arg`，沿用 WI17 限速负值语义）。
4. **测试**：
   - core：`TestRateLimitedSourceFunction`（token 消费计数、cancel 中断传播、isOff 零开销直通）。
   - flow：`TestSourceRateLimitDeclaration`（决策函数 `applyRateLimitDeclaration` 直钉：包装实例断言、负值双属性 typed reject、双零声明不包装（assertSame 委托）；build→addSource 全链覆盖由 runtime E2E 承担）。
   - runtime：`TestSourceRateLimitE2E`（限速源全量记录仍到达 sink——限速不丢数据；非吞吐时序断言，避免 flake）。
5. **文档**：user guide 端点属性裁定段后补限速声明形态（与 connector 参数面/bean 自限速三分关系）+ 支持矩阵行 + E1 场景注记升级；WI21/plan 2367 Deferred 段勘误注记（声明面已交付）；daily log。

### Out Of Scope

- connector 参数面变更（kafka/file 的 conf 参数保持原样，与 DSL 属性面独立）。
- `StreamConfValidator` 规则扩展（connector conf 校验面不动）。
- 反压语义文档化（`SourceRateLimiter` 已有 javadoc；引用即可）。

## Phases

### Phase 0: core 包装器
- [x] `RateLimitedSourceFunction` + 单元测试绿（5/0，含 1 rps 节流下界钉）
Status: completed

### Phase 1: xdef 属性 + 再生成
- [x] 两属性声明 + 再生成（恰 1 处 _StreamSourceModel 变更）
- [x] flow 编译通过
Status: completed

### Phase 2: builder 接线 + 校验
- [x] `buildSource` 包装 + 负值 typed reject
- [x] `TestSourceRateLimitDeclaration` 绿（5/0）+ runtime 接线节流钉（1 rps 墙钟下界）；变异探针：接线断开→25ms 红→恢复→2.6s 绿
Status: completed

### Phase 3: runtime E2E + 回归
- [x] `TestSourceRateLimitE2E` 绿（2/0：高速率透明送达 + 低速率节流钉）
- [x] core 1759/0 + flow 312/0 + sql 165/0 全量零回归（fork+JDK25）
Status: completed

### Phase 4: 文档与收口
- [x] user guide 限速声明段+矩阵行 + plan 2367 Deferred 收口注记 + daily log
- [x] doc-links --strict 退出码 0
- [x] roadmap U3 勾选 + 单次提交（独立 audit APPROVE，agent_0ae8e678）
Status: completed

## Exit Criteria

- [x] 任意 `<source>` 声明限速→构建期包装生效（flow 断言包装实例——决策函数 `applyRateLimitDeclaration` 直钉；全链覆盖由 runtime E2E 承担 + runtime E2E 数据完整）
- [x] 负值 typed reject 有断言
- [x] core/flow 测试绿（core 5/0、flow 定向 5/0）；flow 全量 312/0 零回归
- [x] 文档三处同步 + 两工具退出码 0

## Closure

Status Note: U3 交付完成——`<source rateLimitRowsPerSecond/rateLimitBytesPerSecond>` 声明面（xdef+再生成）、builder `applyRateLimitDeclaration` 泛化包装（负值 typed reject、0/0 不包装）、core `RateLimitedSourceFunction`（SourceContext 包装，watermark/idle/钟直通）、flow 决策钉 5/0 + runtime E2E 2/0（含 1 rps 接线节流钉）、文档三处同步。独立 audit APPROVE 后收口。**提交分裂说明（audit Major-1 裁定处置）**：builder 34 行接线已被无关 chore 提交 1fa609e31c 提前卷入（该提交同时引用了未提交的 RateLimitedSourceFunction，HEAD 曾短暂不可编译）；因并行会话共享工作树，不改写历史，由本收口提交补齐其余实现并在此显式记录分裂。
Completed: 2026-10-10

Closure Audit Evidence:

- Reviewer / Agent: agent_0ae8e678-d007-4488-9e4a-9400a5e399ad（独立 fresh-session Explore 审计）
- Audit Session: agent_0ae8e678-d007-4488-9e4a-9400a5e399ad
- Evidence:
  - Exit Criterion 1（声明→包装生效）PASS：`StreamModelDslBuilder.buildSource` :2035 调用 `applyRateLimitDeclaration`（def :2050-2068，一致性保真校验之后）；flow `TestSourceRateLimitDeclaration.declaredRateWrapsResolvedSource` 断言包装实例+limiter rows=100；runtime `TestSourceRateLimitE2E.declaredRateLimitSourceDeliversEveryRecord` 10/10 有序送达。
  - Exit Criterion 2（负值 typed reject）PASS：`negativeRowsFailsFast`/`negativeBytesFailsFast` 断言 `nop.err.stream.invalid-arg`。
  - Exit Criterion 3（测试绿）PASS：surefire 亲核 core 5/0、flow 5/0、runtime 2/0；全量合计 core 1759/0、flow 312/0、sql 165/0。本会话复跑三 focused 套件均绿。
  - Exit Criterion 4（文档三处+工具）PASS：user guide :218 限速段+ :316 矩阵行；plan 2367 :66 Deferred 收口注记；`check-plan-checklist --strict` 退出码 0（audit 复跑）；`check-doc-links --strict` 退出码 0（实现侧运行，audit Minor-3 因工具写报告文件无法只读复跑——提交前已由实现会话确认）。
  - Anti-Hollow PASS：xdef :163-172 → `_StreamSourceModel` :185-209 → buildSource :2035 → `RateLimitedSourceFunction` :86-95 collect/collectWithTimestamp 前 acquire（≤100ms 切片、中断响应、无空方法体）；安装构件 jar 内含接线符号（grep=1）。
  - 审计发现处置：Major-1 提交分裂→本 plan Status Note 显式记录，收口提交补齐；Major-2 过时计数→Exit Criteria 已修（5/0、312/0）；Minor-3→实现侧退出码 0 记录在案；Nit-2 项（E2E 顺序断言由首用例承担/byte 估算已文档化）无需动作。
- `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2376-u3-source-rate-limit-declaration.md --strict` 退出码 0（关闭前复跑确认）

Follow-up:

- no remaining plan-owned work（byte 估算精度仅 byte[]/String 计价，已 javadoc+user guide 文档化，watch-only）
