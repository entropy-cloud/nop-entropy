# 283 nop-stream 代码质量修复

> Plan Status: completed
> Last Reviewed: 2026-06-30
> Source: `ai-dev/analysis/2026-06-30-nop-stream-code-audit.md`
> Related: `ai-dev/design/nop-stream/completion-roadmap.md`（Phase 0 包含 Operator State 等功能缺口）

## Purpose

收口 `ai-dev/analysis/2026-06-30-nop-stream-code-audit.md` 中识别出的**可直接修复的代码缺陷**，使 nop-stream 主源码不存在违背契约的代码模式、不违反仓库代码规范。

## Current Baseline

- nop-stream 共 9 个子模块，~356 个源文件，~260 个测试文件，测试密度 0.73
- 审计识别出 4 类问题，经两轮对抗性审查确认其中 3 类可直接修复，1 类（窗口工厂接线）需要架构设计后才能执行

### 确认的 live defects（本计划修复）

1. **`InputGate.java` 从 `Optional<StreamElement>` 方法返回 raw `null`** — 5 处（行 320, 336, 342, 353, 386）。调用方通过 `if (result != null)` 规避，但完全违背 Optional 契约。`grep -c "return null;" InputGate.java` = 5。
2. **主源码 92 个文件使用通配符导入** — `grep -rl "^import .*\*;" nop-stream/*/src/main/java/ | wc -l` = 92。违反 AGENTS.md "Imports: grouped" 规范。

### 确认的 live defects（需架构设计后移入 successor plan）

3. **`WindowedStreamImpl` 默认走已废弃的 `WindowAggregationOperator` 路径** — 设计文档已更新（`core-design.md` §1.2.1 + §2.2.1，`window-design.md` §10.1），确认了 `StreamComponents` 由 `StreamExecutionEnvironment` 持有、通过 SPI 加载 `IWindowOperatorFactory`、工厂缺失时快速失败的设计决策。代码修复涉及：
   - `StreamExecutionEnvironment` 增加 `StreamComponents` 字段 + SPI 加载
   - `KeyedStreamImpl.window()` 传递 `StreamComponents` 到 `WindowedStreamImpl`
   - `WindowedStreamImpl` 工厂缺失时抛异常而非静默回退
   - core 模块 `TestWindowedStreamAggregation`（8 个测试）需迁移到 runtime 或改用 `OperatorTestHarness`
   - 移入 `Deferred But Adjudicated`，指向 successor plan

### 已不存在的缺陷（文档滞后）

4. ~~**runtime → cep 幽灵依赖**~~ — `nop-stream` 下的 `nop-stream-runtime` 模块 pom 中已无 `nop-stream-cep` 依赖，`grep -rn "io.nop.stream.cep"` 在 runtime src 下返回零匹配。`component-roadmap.md` §5 行 300 仍标记"未修复"，属文档滞后。

## Goals

- InputGate 中所有声明返回 `Optional<StreamElement>` 的方法返回 `Optional.empty()` 而非 `null`，调用方使用 `Optional` API 而非 null 检查
- 主源码（`src/main/java`）无通配符导入
- `component-roadmap.md` 同步 runtime→cep 幽灵依赖已消除的事实

## Non-Goals

- **不做窗口工厂接线修复** — 经审查确认需要架构设计（`StreamExecutionEnvironment` 重构 + core/runtime 依赖边界裁定），移入 successor plan
- **不实现 Operator State** — 属于 `completion-roadmap.md` Phase 0.3
- **不重组 execution 包结构** — 属于结构优化，非 defect
- **不删除/合并空壳模块** — 属于 `completion-roadmap.md` Phase 5.6
- **不补充 Operator State / 多 JVM E2E 测试** — 属于 `completion-roadmap.md`
- **不修复测试代码中的通配符导入**（248 个文件）— 测试代码规范改进作为 non-blocking follow-up
- **不做 TimerService 名称冲突重构** — `core.time.TimerService` 是 `@Deprecated` 空接口，不影响运行时行为

## Scope

### In Scope

- `nop-stream-core` 的 `InputGate.java` Optional 修复
- `nop-stream-core` + `nop-stream-runtime` + `nop-stream-cep` + `nop-stream-connector` 的 `src/main/java` 通配符导入清理
- `component-roadmap.md` §5 文档同步

### Out Of Scope

- 窗口工厂接线修复（→ successor plan，需先做架构设计）
- 测试源码（`src/test/java`）的通配符导入（non-blocking follow-up）
- `nop-stream-fraud-example` 的通配符导入（示例代码，non-blocking）
- Operator State 实现
- 空壳模块处理
- execution 包重组

## Execution Plan

### Phase 1 - InputGate Optional 契约修复

Status: completed
Targets: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/InputGate.java`

- Item Types: `Fix`

- [x] 修复 `handleBarrierNonRecursive()` 的 `return null` → `return Optional.empty()`（行 320, 336）
- [x] 修复 `handleWatermarkNonRecursive()` 的 `return null` → `return Optional.empty()`（行 342, 353）
- [x] 修复 `checkBarrierAlignmentComplete()` 的 `return null` → `return Optional.empty()`（行 386）
- [x] 修复 `readMultiChannel()` 中调用方的 `if (result != null)` → `if (result.isPresent())`（行 257, 266, 272 附近，3 处调用）
- [x] 全文搜索确认 `InputGate.java` 中不再有 `return null` 在返回类型为 `Optional<` 的方法中
- [x] 运行现有 InputGate 测试确认无回归（`TestInputGate*` 系列 + `TestInputGateBarrierAlignment` + `TestInputGateAlignmentTimeout`）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `grep -c "return null;" InputGate.java` 的输出中，所有匹配行不在任何返回类型为 `Optional<` 的方法体内（可通过代码审查确认）
- [x] `InputGate.java` 中对 `handleBarrierNonRecursive` / `handleWatermarkNonRecursive` / `checkBarrierAlignmentComplete` 返回值的消费使用 `Optional` API（`.isPresent()` / `.isEmpty()`），不使用 `!= null`
- [x] `./mvnw test -pl nop-stream/nop-stream-core -am` 通过，特别是 `TestInputGateBarrierAlignment`、`TestInputGateAlignmentTimeout`、`TestInputGateBarrierForwarding` 测试
- [x] **端到端验证**：`TestGraphModelExecution`（多链管线，keyBy 场景经过 InputGate）或 `TestLocalExecutionBarrierAlignment` 通过，证明 barrier 对齐路径未被破坏
- [x] No owner-doc update required（纯内部实现修复，不改变对外契约）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 通配符导入清理（主源码）

Status: completed
Targets: `nop-stream/*/src/main/java/**/*.java`（92 个文件，已验证：`grep -rl "^import .*\*;" nop-stream/*/src/main/java/ | wc -l` = 92）

- Item Types: `Fix`

- [x] 批量展开 `nop-stream-core/src/main/java` 中所有通配符导入为具体导入（按模块分批 commit，便于回滚）
- [x] 批量展开 `nop-stream-runtime/src/main/java` 中所有通配符导入
- [x] 批量展开 `nop-stream-cep/src/main/java` 中所有通配符导入
- [x] 批量展开 `nop-stream-connector/src/main/java` 中所有通配符导入
- [x] 确认导入分组规范：`java.*` → `jakarta.*` → 第三方 → `io.nop.*`
- [x] 遇到同名类冲突（如 `java.util.List` vs 其他 `List`）时使用全限定名

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `grep -rl "^import .*\*;" nop-stream/*/src/main/java/ | wc -l` 输出为 0
- [x] `./mvnw compile -pl nop-stream -am -T 1C` 通过（确认所有展开的导入正确）
- [x] `./mvnw test -pl nop-stream -am -T 1C` 通过（确认无回归）
- [x] No owner-doc update required（纯代码规范修复）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 文档同步：runtime→cep 幽灵依赖已消除

Status: completed
Targets: `ai-dev/design/nop-stream/component-roadmap.md`, `ai-dev/design/nop-stream/completion-roadmap.md`

- Item Types: `Fix`

**背景**：审查发现 `nop-stream` 下的 `nop-stream-runtime` 模块 pom 中已无 `nop-stream-cep` 依赖，`grep -rn "io.nop.stream.cep"` 在 runtime src 下返回零匹配。但 `component-roadmap.md` §5 行 300 仍标记"未修复"。此 Phase 仅同步文档。

- [x] 将 `component-roadmap.md` §5 已知技术债表中 `runtime 依赖 cep（零代码引用）` 标记为 ✅ 已修复
- [x] 将 `completion-roadmap.md` Phase 0.8 中 runtime→cep 移除标记为 ✅ 已消除
- [x] 将 `component-roadmap.md` §3 C6 中 runtime→cep 幽灵依赖标记为 ✅ 已消除

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `component-roadmap.md` §5 技术债表中 runtime→cep 幽灵依赖标记为 ✅ 已修复
- [x] No code change required（纯文档同步）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。
>
> **纯文档计划**：Phase 3 不涉及代码变更，构建验证条目不适用于 Phase 3。

- [x] InputGate 中无 `Optional<StreamElement>` 方法返回 `null`
- [x] 主源码无通配符导入
- [x] `component-roadmap.md` 已同步 runtime→cep 幽灵依赖消除状态
- [x] `./mvnw clean install -pl nop-stream -am -T 1C` 全量构建通过
- [x] `./mvnw test -pl nop-stream -am -T 1C` 全量测试通过（453 tests, 0 failures）
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/283-nop-stream-code-quality-fixes.md --strict` 退出码为 0
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证 InputGate 的 Optional 修复不改变数据流行为（barrier 对齐/watermark 传播路径未被破坏）

## Deferred But Adjudicated

### 窗口工厂接线修复（WindowedStreamImpl 默认走废弃路径）

- Classification: `moved to explicit successor ownership`
- Why Not Blocking Closure: 设计决策已在 `core-design.md` §1.2.1 + §2.2.1 和 `window-design.md` §10.1 中确定（`StreamComponents` 由 environment 持有、SPI 加载工厂、缺失时快速失败）。代码实现涉及 `StreamExecutionEnvironment` 重构 + `KeyedStreamImpl` 传递 + core 测试迁移，属于架构级变更，应在独立 successor plan 中执行
- Successor Required: yes
- Successor Path: 基于已确定的设计决策创建 successor plan 执行接线重构（无需再写 design 文档）

### 测试代码通配符导入（248 个文件）

- Classification: `optimization candidate`
- Why Not Blocking Closure: 测试代码不影响生产行为，通配符导入在测试中不构成 defect
- Successor Required: no
- Successor Path: non-blocking follow-up

### execution 包重组（26 文件 → 拆分）

- Classification: `optimization candidate`
- Why Not Blocking Closure: 包结构不影响正确性，是可读性优化
- Successor Required: no
- Successor Path: `completion-roadmap.md` Phase 0.8

### TimerService 名称冲突（core.time vs cep.time）

- Classification: `watch-only residual`
- Why Not Blocking Closure: `core.time.TimerService` 是 `@Deprecated` 空接口，不影响运行时行为；CEP 的 `cep.time.TimerService` 是活跃接口，两者不产生实际冲突
- Successor Required: no
- Successor Path: 无

### 4 个空壳模块（api/checkpoint/flink/flow）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 空壳模块不影响现有功能，属于 `completion-roadmap.md` Phase 5.6 的规划
- Successor Required: yes
- Successor Path: `completion-roadmap.md` Phase 5.6

### Operator State 实现

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 属于功能新增，不是代码质量缺陷。已在 `completion-roadmap.md` Phase 0.3 规划
- Successor Required: yes
- Successor Path: `completion-roadmap.md` Phase 0.3

### 多 JVM 分布式 E2E 测试

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 属于测试基建新增，不是代码质量缺陷。已在 `completion-roadmap.md` Phase 3.6 规划
- Successor Required: yes
- Successor Path: `completion-roadmap.md` Phase 3.6

### `WindowAggregationOperator` 类的删除

- Classification: `optimization candidate`
- Why Not Blocking Closure: 该类被 7+ 个现有测试直接实例化。删除属于后续代码清理，依赖窗口工厂接线 successor plan 先完成
- Successor Required: yes
- Successor Path: 窗口工厂接线 successor plan 完成后

## Non-Blocking Follow-ups

- 测试代码通配符导入清理（248 个文件）— 可在后续代码规范治理中批量处理
- `InputGate` 硬编码的 `parkNanos(10_000_000)` 轮询间隔可配置化
- `JdbcCheckpointStorage` / `JdbcClusterRegistry` 中 100+ 处 `return null` 的 null 安全审计

## Closure

Status Note: 三项代码质量缺陷已全部修复——InputGate Optional 契约修复（5 处 return null → Optional.empty()，3 处调用方 != null → .isPresent()），92 个主源码文件的通配符导入全部展开为具体导入，component-roadmap.md 文档同步完成。窗口工厂接线问题经审查确认为架构级变更，设计决策已记录在 core-design.md §1.2.1 + §2.2.1 和 window-design.md §10.1，移入 successor plan。

Completed: 2026-06-30

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（task ses_0e7ef5442ffegnGzFIx2EU1C06）
- Audit Session: ses_0e7ef5442ffegnGzFIx2EU1C06
- Evidence:
  - InputGate Optional fix: PASS — `grep -c "return null;" InputGate.java` = 0；所有 Optional<StreamElement> 方法使用 Optional.empty()；调用方使用 .isPresent()；5 处 != null 匹配均为 pendingBarrier 字段（非 Optional 类型），合法
  - Wildcard imports: PASS — `grep -rl "^import .*\*;" nop-stream/*/src/main/java/ | wc -l` = 0
  - Document sync: PASS — component-roadmap.md:300 标记 ✅ 已修复
  - Anti-Hollow check: PASS — Optional 修复语义等价（null→Optional.empty()，非 null→Optional.of(x)），barrier 对齐逻辑不变
  - Plan checkbox state: PASS — 所有 Phase Exit Criteria = [x]
  - `./mvnw test -pl nop-stream -am -T 1C`: 453 tests, 0 failures, 0 errors
  - `node ai-dev/tools/check-plan-checklist.mjs --strict`: 退出码 0

Follow-up:

- 窗口工厂接线修复 → successor plan（设计决策已在 core-design.md §1.2.1 + §2.2.1 确定）
- 测试代码通配符导入清理（248 文件）→ non-blocking follow-up
- WindowAggregationOperator 废弃类删除 → 窗口工厂接线 successor plan 完成后
- no remaining plan-owned work
