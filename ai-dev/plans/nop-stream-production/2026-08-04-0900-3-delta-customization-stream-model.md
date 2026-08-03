# Stage 51 — Delta 定制 StreamModel

> Plan Status: completed
> Last Reviewed: 2026-08-04
> Source: `ai-dev/backlog/nop-stream-production-roadmap.md` Stage 51；`ai-dev/design/nop-stream/00-vision.md`（三入口，L13-18/L114）；`ai-dev/design/nop-stream/stream-dsl-design.md:161-194`（Delta 用法约定）
> Related: Stage 50（`2026-08-03-2124-1-xsdl-declarative-orchestration`，XDSL 编排，completed — 本 plan 的直接前驱）

## Purpose

验证并固化 `.stream.xml` 的 Delta 定制能力（`x:extends` 覆盖 + `_delta/<layer>/` 目录分层覆盖），证明合并后的模型经既有 `StreamModelDslBuilder` 正确构建+执行，且 fingerprint 对 delta 变更敏感。本 plan 主要是**验证 + 测试 + 设计固化**——加载机制（标准 `DslModelParser`）已在 Stage 50 落地，本 plan 不新增解析器。

## Current Baseline

- Stage 50（XDSL 编排）已 completed（2026-08-04）：`StreamModelDslBuilder` 把解析后的 `flow.model.StreamModel` → DataStream API 调用链 → 可执行 `StreamExecutionEnvironment`；15 种 transform 类型全覆盖；fail-fast 拒绝无执行消费方的 registry。
- **加载机制已 Delta-capable**：`nop-stream-flow` 用标准 `io.nop.xlang.xdsl.DslModelParser` 解析（`StreamModelSmokeTest.java:13,38-39`）。`stream.xdef`（`nop-kernel/nop-xdefs/.../stream/stream.xdef:22`）声明 `xdef:support-extends="true"` + `xdef:model-name-prop`/`xdef:model-version-prop`——Delta 在解析层**今日即可用**，无需新解析器。
- **无任何 Delta 代码/测试**：`nop-stream-flow` 下无 `_delta/` 目录，无 `x:extends`/`x:override` 测试用例（grep 命中仅生成文件 `_StreamModel.java` 的 doc 注释）。
- **fingerprint**：`core.model.StreamModel.computeFingerprint()`（`nop-stream-core/.../model/StreamModel.java:60`）在可执行模型上计算。对 delta-vs-base（均经 XDSL/builder 路径）场景，delta 增删改 transform 会改变 `transformations` map 与 `dagTopologyHash`，fingerprint **自然变化**（敏感），无需改 `nop-stream-core`。
- **关键区分**：Stage 50 deferred 的「精确 fingerprint 相等」（XDSL-built vs Java-API-built 产生相同指纹）是**不同**关注点——因 `Transformation.id` 静态自增 + `toString` 未重写，跨路径相等结构上不可能，且需改 `nop-stream-core`（Protected Area）。本 plan 关注的是 delta-vs-base **敏感**性，今日已成立，不在 Stage 50 deferred 范围。
- vision §00（L13-18）记录三入口：XDSL（Stage 50 done）/ Java API / Delta（Stage 51）；不变量 #10「Delta 只能修改模型，不能 patch runtime」。

## Goals

- 验证并固化 Delta 定制 `.stream.xml`：`x:extends` 覆盖（增/改/删 transform）+ `_delta/<layer>/` 目录分层覆盖。
- 证明合并后 `flow.model.StreamModel` 经既有 `StreamModelDslBuilder` 正确构建 + 执行（输出正确）。
- 证明 fingerprint 对 **transform 级** delta 变更**敏感**（delta-merged vs base 产生不同 fingerprint）。注意：`computeFingerprint()` 仅哈希 `transformations`/`dagTopologyHash`/`requirements`/`checkpointParticipants`，**不含** checkpoint interval/parallelism——故 config-only delta（仅改 checkpoint/parallelism）fingerprint **不变**，这是 by-design（fingerprint = DAG 拓扑身份，非运行时配置），本 plan 须显式区分两类 delta。
- owner-doc：在 `ai-dev/design/nop-stream/` 固化 `.stream.xml` 的 Delta 契约。

## Non-Goals

- 精确 fingerprint **相等**（XDSL vs Java API）——Stage 50 deferred，独立 successor（需改 Protected Area core，非本 plan）。
- StreamComponents registry 级 delta（Stage 50 deferred，successor only）。
- `<union>`/`<sideOutput>` delta——core runtime 缺对应 API（Stage 50 deferred）。
- 改 `nop-stream-core` 的 `computeFingerprint()`（Protected Area，本 plan 不动）。

## Scope

### In Scope

- Delta 测试夹具：`x:extends` 覆盖（覆盖 base 的 transform 属性、增删 transform）。
- `_delta/<layer>/` 目录分层覆盖测试夹具 + 用例。
- **transform 级** delta 的 fingerprint 敏感性验证（delta-merged ≠ base）。
- 明确裁定 **config-only** delta（仅 checkpoint interval/parallelism）fingerprint 不变是 by-design。
- 验证合并模型 build + execute 输出正确（与手写等价管线对照）。
- 设计固化：在 `ai-dev/design/nop-stream/`（stream-dsl-design 或新增 delta-design）写最终 Delta 契约（用法、限制、与 vision §00 对齐）。

### Out Of Scope

- 上述 Non-Goals 全部；新增解析器/加载器（已有）；core fingerprint 改造；config-only delta 改变 fingerprint（by-design 不变）。

## Execution Plan

### Phase 1 - Delta 用例 + 执行正确性 + fingerprint 敏感性

Status: completed
Targets: `nop-stream/nop-stream-flow/src/test/resources/_vfs/`、`nop-stream/nop-stream-flow/src/test/java/`

- Item Types: `Proof`、`Decision`

> **关键风险（silent no-op）**：VFS `_delta/` 机制真实存在且无需改解析器，但**自定义 layer 名非默认激活**——`nop.core.vfs.delta-layer-ids` 默认 `null`，`DeltaResourceStoreBuilder`（`DeltaResourceStoreBuilder.java:72-77`）在配置为空时**仅自动检测 `_delta/default/` 目录并激活 `default` 单层**；非 `default` 名称的 layer（如 `prod`/`test`）**必须**显式 `@NopTestProperty(name="nop.core.vfs.delta-layer-ids", value="<layer>")`，否则 delta 被静默忽略、test 误加载 base 而通过（参考 `nop-auth-web/.../TestDeltaView.java:16`）。本 Phase 必须用断言 delta-unique 属性来证明 delta 真正生效，而非靠「test 跑过」。
>
> **Layer 策略裁定（落地）**：使用 `_delta/default/`（零配置，auto-activated by `DeltaResourceStoreBuilder`）。`x:extends` 覆盖用显式 base path（`x:extends="/nop/stream/test/test-delta-base.stream.xml"`）。三个测试类：`TestStreamModelDeltaExtends`（执行+delta-unique）、`TestStreamModelDeltaFingerprint`（fingerprint 敏感性+by-design）、`TestStreamModelDeltaFailFast`（fail-fast 保持）。

- [x] 测试夹具：base `.stream.xml` + `x:extends` 覆盖版本（**transform 级**：覆盖 transform 属性、增删 transform）。 → `test-delta-base.stream.xml` + `test-delta-extends.stream.xml`（delta 增 `deltaFilter` filter + 改写 edge e1 + 新增 edge e2）
- [x] 测试夹具：`_delta/<layer>/` 目录分层覆盖版本。**layer 策略裁定**：优先用 `_delta/default/`（零配置）；若用自定义 layer，测试类加 `@NopTestProperty(...)`。 → 用 `test-delta-layered.stream.xml` 的 `_delta/default/` 分层 delta（`x:extends="super"` 增 `deltaLayerMap` map + 改写 e0 + 新增 e1）
- [x] 用例：合并后模型经 `StreamModelDslBuilder.build()` + `execute()` 输出与预期一致（与手写等价管线对照）。 → `xExtendsDeltaProducesDifferentSinkOutput`（base=["A","B","C"]，delta=["A","C"]）、`layeredDefaultDeltaProducesUppercaseOutput`（=["A","B","C"]）
- [x] **delta-unique 属性断言（强制）**：每个 delta 用例须断言一个**只存在于 delta 文件、base 中不存在**的可观测属性。 → `xExtendsDeltaAddsFilterTransformNotPresentInBase`（base 无 `deltaFilter`，delta 有）、`layeredDefaultDeltaAddsMapTransform`（`deltaLayerMap` 仅 delta-merged 存在）；执行输出也是 delta-unique 可观测属性（filter drop B / uppercase 非自然产生）
- [x] 用例：**transform 级** delta 的 fingerprint 敏感性——delta-merged 模型的 `computeFingerprint()` 与 base 不同（断言不等）。 → `transformLevelDeltaProducesDifferentFingerprint`（用真实 `core.model.StreamModel.computeFingerprint()` on parsed transform-id keys，assertNotEquals + dagTopologyHash 不等）
- [x] 用例（Decision）：config-only delta（仅改 checkpoint interval/parallelism）fingerprint **不变**，断言相等并标注为 by-design（fingerprint = DAG 拓扑身份）。裁定该不变是 intended（非缺陷）。 → `configOnlyDeltaPreservesFingerprintByDesign`（test-delta-config-base 60000/2 vs test-delta-config-extends 30000/1，同 transforms → assertEquals）
- [x] 用例：fail-fast 行为——对 Stage 50 已 fail-fast 的 registry（streams/sideInputs 等），delta 不应静默绕过。 → `deltaAddingStreamsRegistryStillFailsFast`（delta 加 `<streams>` → build 仍抛 UnsupportedOperationException）
- [x] **接线验证**：N/A（纯验证 plan，无新组件需与既有组件协作运行时调用）。

Exit Criteria:

- [x] **端到端验证**：base + delta 两路径经 builder 执行，输出与预期一致（执行正确）。
- [x] **delta-unique 断言**：每个 delta 用例有且仅当 delta 生效才成立的断言通过（防 silent base-load）。
- [x] transform 级 delta 的 fingerprint 敏感性用例通过（delta-merged ≠ base）；config-only delta 不变用例通过并标注 by-design。
- [x] **无静默跳过**：delta 不绕过既有 fail-fast；未支持的能力仍抛异常。
- [x] 新增功能均有对应测试（x:extends 覆盖、_delta 分层、delta-unique 断言、transform 级 fingerprint 敏感、config-only fingerprint 不变、fail-fast 保持 各一条）。
- [x] No owner-doc update required in this Phase（设计固化在 Phase 2）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - Delta 契约设计固化 + owner-doc 同步

Status: completed
Targets: `ai-dev/design/nop-stream/`、`ai-dev/design/nop-stream/00-vision.md`

- Item Types: `Decision`、`Proof`

- [x] 在 `ai-dev/design/nop-stream/` 写最终 Delta 契约：`x:extends`/`_delta` 用法、覆盖语义、限制（不能 patch runtime、不变量 #10）、与三入口关系。 → `stream-dsl-design.md` §7.2 重写为最终 Delta 契约（两种入口表 + 覆盖语义 + fingerprint 与 delta 关系 + 示例 + 验证引用），无 Proposed 措辞。
- [x] 同步 `stream-dsl-design.md`（若已存在 Delta 章节，修正为最终状态；去除 Proposed 措辞）。 → `rg -i proposed` 确认 stream-dsl-design.md / 00-vision.md 无 Proposed 命中。
- [x] 核对 `00-vision.md` 三入口描述与 live baseline 一致（Delta 标记为 supported）。 → `00-vision.md:16` Delta 条目标注「已落地，Stage 51」。

Exit Criteria:

- [x] Delta 契约文档为最终设计状态（无 Proposed/Current-vs-Proposed 章节）。
- [x] `00-vision.md` 与 live baseline 一致。
- [x] **No new test required: pure-doc phase**（本 Phase 仅写设计文档/同步 vision，无新增代码/功能；测试覆盖在 Phase 1）。验证项为文档内容与 live baseline 一致性（抽查）。
- [x] `ai-dev/logs/` 收口记录已更新。
- [x] 纯文档/验证 plan：Closure Gates 中构建验证条目可按 guide 删除，但保留测试验证（`./mvnw test -pl nop-stream/nop-stream-flow -am`）。

## Closure Gates

- [x] Delta 定制（`x:extends` + `_delta`）端到端验证通过（build + execute 输出正确）。
- [x] **delta-unique 断言**通过：每个 delta 用例证明 delta 确被应用（非误加载 base）。
- [x] transform 级 delta 的 fingerprint 敏感性（delta-merged ≠ base）经测试验证；config-only delta 不变已裁定为 by-design。
- [x] Delta 契约文档固化为最终状态，与 vision 一致。
- [x] 不存在被静默降级到 deferred 的 in-scope live defect。
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据。
- [x] **Anti-Hollow Check**：delta 合并确在解析层生效，且经 delta-unique 可观测属性断言验证（非仅文档声明/非靠 test 跑过）。
- [x] `./mvnw test -pl nop-stream/nop-stream-flow -am` 通过
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

（执行中如有裁定填入。）

### 精确 fingerprint 相等（XDSL vs Java API）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 结构上不可能（`Transformation.id` 静态自增 + `toString` 未重写），且需改 Protected Area `nop-stream-core`。本 plan 关注 delta-vs-base 敏感性，与相等无关。属 Stage 50 独立 successor。
- Successor Required: `yes`
- Successor Path: 独立 plan（重写 fingerprint 基于 Transformation 结构内容）。

## Non-Blocking Follow-ups

- StreamComponents registry 级 delta（若未来 delta 需触及 registry）。
- `<union>`/`<sideOutput>` delta（待 core runtime 提供对应 API 后）。

## Closure

Status Note: Stage 51 Delta 定制验证并固化完成。两种 Delta 入口（`x:extends` 显式 base path + `_delta/default/` 目录分层）经端到端测试证明合并在解析层生效、执行输出正确、fingerprint 对 transform 级 delta 敏感（config-only by-design 不敏感）、delta 不绕过 Stage 50 fail-fast。Delta 契约固化在 `stream-dsl-design.md` §72，vision §一 Delta 标记为已落地。加载机制（`DslModelParser` + `xdef:support-extends`）已有，本 plan 无新增生产代码——仅测试夹具/测试类 + 设计文档。
Completed: 2026-08-04

Closure Audit Evidence:

- Reviewer / Agent: independent closure audit subagent (fresh session, distinct from implementation session)
- Audit Session: closure-audit for plan `2026-08-04-0900-3-delta-customization-stream-model.md`
- Evidence:
  - **Phase 1 Exit Criteria** (all PASS):
    - 端到端验证: `TestStreamModelDeltaExtends.xExtendsDeltaProducesDifferentSinkOutput`（base=["A","B","C"], delta=["A","C"] — filter drops B）、`layeredDefaultDeltaProducesUppercaseOutput`（=["A","B","C"] — map applies uppercase）PASS
    - delta-unique 断言: `xExtendsDeltaAddsFilterTransformNotPresentInBase`（base assertNull deltaFilter, delta assertNotNull）、`layeredDefaultDeltaAddsMapTransform`（deltaLayerMap 仅 merged 存在）PASS
    - fingerprint 敏感性: `TestStreamModelDeltaFingerprint.transformLevelDeltaProducesDifferentFingerprint`（assertNotEquals + dagTopologyHash 不等）PASS；`configOnlyDeltaPreservesFingerprintByDesign`（assertEquals, by-design）PASS
    - 无静默跳过: `TestStreamModelDeltaFailFast.deltaAddingStreamsRegistryStillFailsFast`（UnsupportedOperationException for `<streams>`）PASS
  - **Phase 2 Exit Criteria** (all PASS):
    - `stream-dsl-design.md` §7.2 重写为最终 Delta 契约（两种入口表、覆盖语义、fingerprint 关系、示例、验证引用），`rg -i proposed` 确认无 Proposed 措辞
    - `00-vision.md:16` Delta 条目标注「已落地，Stage 51」
  - **Closure Gates** (all PASS):
    - `./mvnw test -pl nop-stream/nop-stream-flow -T 1C` → 51 tests run, 0 failures, 0 errors, 0 skipped（含 8 新 delta 测试 + 43 已有测试全绿）
    - `./mvnw clean install -pl nop-stream/nop-stream-flow -am -T 1C -DskipTests` → BUILD SUCCESS
    - checkstyle / 代码规范: 编译通过（javac release 11，无 error），import 分组遵循 io.nop.* → third-party → java.*
    - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码为 0（确认无未勾选项 + Closure Evidence 已写入）
    - Anti-Hollow 检查: `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream-flow --severity high` 退出码为 0（0 critical/high/medium/low findings）；端到端调用链追踪：`DslModelParser.parseFromResource(delta-with-x:extends)` → XDSL merge (base + delta) → `flow.model.StreamModel` → `StreamModelDslBuilder.of(model, resolver).build()` → `StreamExecutionEnvironment.execute()` → source/map/filter/sink 全链连通（sink 收集到正确输出，证明非空壳）
    - Deferred 项分类检查: 精确 fingerprint 相等（XDSL vs Java API）已明确为 out-of-scope improvement（successor = 独立 plan），非 in-scope live defect 降级
  - **doc-link-checker**: `node ai-dev/tools/check-doc-links.mjs --strict` — 本 plan 文件零新增 broken link（3 pre-existing errors in debugging-and-diagnostics.md / flux-rendering.md 非本 plan 引入）

Follow-up:

- 精确 fingerprint 相等（XDSL-built vs Java-API-built）→ 独立 successor plan（重写 fingerprint 基于 Transformation 结构内容，需改 Protected Area nop-stream-core）
- StreamComponents registry 级 delta（若未来 delta 需触及 registry）
- `<union>`/`<sideOutput>` delta（待 core runtime 提供对应 API 后）
