# 2351 WI5 trigger 声明面放行

> Plan Status: completed
> Last Reviewed: 2026-10-09
> Source: `ai-dev/backlog/nop-stream-xdsl-full-coverage-roadmap.md`（WI5 行，M2 窗口控制面）
> Related: plan 2347（WI1 表 B）、plan 2348/2350（L1 规则与 baseline——放行三步法涉改）

## Purpose

放行 strategy/window 节点的 `triggerId` 声明面：接线 core Trigger 家族 + 目录解析 + 组合合法性矩阵 + 判别性时序测试；执行放行三步法（接线 + 删 fail-fast 钉子改放行断言 + owner doc/矩阵迁移）。accumulationMode 半边已由 D10（WI38）交付（ACCUMULATING 已放行，ACCUMULATING_AND_RETRACTING 维持 spec-only 拒绝——本 WI 不改）。

## Current Baseline（2026-10-09 live 核对，侦查 agent 实证）

- **Trigger 家族（core/windowing/triggers，10 类）**：EventTimeTrigger.create()（watermark 过窗端即发，canMerge）/ProcessingTimeTrigger.create()/CountTrigger.of(N)（纯计数不依赖水位，canMerge=false）/ContinuousEventTimeTrigger.of(Duration)/ContinuousProcessingTimeTrigger.of(Duration)（fire 封顶 window.maxTimestamp，canMerge）/DeltaTrigger.of(threshold,DeltaFunction,valueClass)（bean 形态）/ProcessingTimeoutTrigger.of(nested,timeout[,resetOnNew,clearOnTimeout])/PurgingTrigger.of(nested)（FIRE 升级 FIRE_AND_PURGE，canMerge 委托 nested）/EmitUpdateTrigger（每元素 FIRE 不清窗，须 ACCUMULATING，经 emit=UPDATE 接线）。
- **算子消费链**：WindowedStreamImpl.trigger() → WindowOperator.processWindowElement :1094（isFire 发射/isPurge 清窗+清 accumulator+注销窗口）；accumulationMode 差异唯一在 emitWindowContents :1395；allowedLateness 在算子层（cleanup timer = maxTimestamp+lateness，与 trigger 正交）。
- **fail-fast 现状**：strategy triggerId（AdvancedTransforms :257-262）、window 节点 triggerId（:283-288）双处 ERR_STREAM_WINDOW_ATTR_UNSUPPORTED。
- **xdef**：strategy 级 `triggerId="string"` 属性（:49）、window 节点 `<triggerId>string</triggerId>` 子元素（:205）——声明面已在 schema，仅 builder 拒绝。
- **待改钉子**：TestStreamCheckpointAndWindowContract :279/:284（fail-fast 参数化行）、TestWindowEmitDeclaration :146-172（triggerIdOnWindowNodeStillFailsFast）。
- **联动面（放行三步法第 3 步）**：L1 规则 `nop-xdsl-undeclared-consumer`（strategy/window triggerId 分支）+ 其 suite fixture（strategy triggerId="count10"）+ L1 baseline（现 24 条无 triggerId 条目，无需再生）+ WI3 linter R1/R12 的 triggerId 分支 + TestStreamModelLinter 的 faces fixture（strategy triggerId="t1"）+ WI1 矩阵表 B→表 A 迁移。
- **emit=UPDATE 先例**：buildWindow :241-244 强制 EmitUpdateTrigger + ACCUMULATING（UPDATE 与 triggerId 互斥是本 WI 新增校验）。

## Goals

- triggerId 接线：bean 优先 → 目录语法 → 未知 id `ERR_STREAM_REF_UNKNOWN`（镜像 windowFnId 解析序）；节点级覆盖 strategy 级（镜像 allowedLateness 先例）。
- 目录（9 族 × 适用域）：`event-time`/`processing-time`/`count:N`/`continuous-event-time:<dur>`/`continuous-processing-time:<dur>`/`purging:<inner>`（递归）/`processing-timeout:<inner>:<dur>`/Delta 与任意自定义 Trigger 走 bean 名；EmitUpdate 走 emit=UPDATE 不入 triggerId 目录。
- 组合合法性矩阵（构建期 typed 校验）：emit=UPDATE × triggerId 互斥拒绝；canMerge=false 触发器（count 族）× merging assigner（session-event-time）拒绝（Trigger.onMerge 默认抛错的前置化）；ACCUMULATING_AND_RETRACTING 维持拒绝（既有门）。
- 时序判别测试：count 触发器在窗口闭合前发射的端到端序列断言（接线证明，非仅类型断言）。

## Non-Goals

- 不放行 ACCUMULATING_AND_RETRACTING（D10 spec-only 裁定不变）；不改 Evictor 面（WI6）；不改 EmitUpdateTrigger/emit 语义；新增 evictor 组合校验归 WI6（矩阵预留注释）。
- DeltaTrigger 不设目录语法（bean 名形态覆盖，文档记录 valueClass 要求）。

## Scope

### In Scope

- `AdvancedTransforms`（fail-fast 删除 + resolveTrigger + 组合校验）。
- 钉子改造（2 个测试类）+ L1 规则/suite/baseline 校核 + WI3 linter/测试联动 + 矩阵迁移。
- 新测试：目录解析族测、precedence、互斥/merge 合法性、count 触发时序 E2E。

### Out Of Scope

- core Trigger 实现改动；xdef 变更（triggerId 声明已存在）；evictor（WI6）。

## Execution Plan

### Phase 1 - 接线与组合校验

Status: completed
Targets: `nop-stream-flow` builder

- Item Types: `Feature`

- [x] `resolveTrigger(owner, t, strategy, nodeTriggerId)`：null 直接返回不覆盖默认 trigger；解析序 bean → 目录 → `ERR_STREAM_REF_UNKNOWN`（复用 `parseDurationMillis` 解析 `<dur>`；count:N 非正整数拒绝；purging/processing-timeout 递归且嵌套深度超限拒绝）；节点级非空覆盖 strategy 级
- [x] buildWindow 消费：**互斥校验置于 resolveTrigger 之前**（emit==UPDATE 且任一级 triggerId 非空 → `ERR_STREAM_INVALID_ARG`，错误优先级确定且不构建弃用对象）；windowed.trigger(resolved)；组合矩阵校验：count 族（含 purging:count）× session-event-time → 拒绝（canMerge 契约前置化）；ACCUMULATING_AND_RETRACTING 拒绝保留
- [x] 删两处 fail-fast 分支（:257-262/:283-288）并同步更新类/方法 javadoc 的「triggerId remains rejected (D11)」表述（:101-105 等三处）

Exit Criteria:

- [x] `./mvnw compile -pl nop-stream/nop-stream-flow -am` 通过
- [x] 无静默跳过：未知 triggerId/互斥/merge 不兼容均 typed 拒绝
- [x] `ai-dev/logs/` 条目已更新

### Phase 2 - 钉子改造与联动面（放行三步法）

Status: completed
Targets: 测试类×2、L1 规则/suite、linter、矩阵

- Item Types: `Feature` + `Proof`

- [x] TestStreamCheckpointAndWindowContract：删 :279/:284 两行 fail-fast 参数（A&R 行保留）；TestWindowEmitDeclaration：triggerIdOnWindowNodeStillFailsFast 改造为 triggerId 接线断言（node 覆盖 strategy + UPDATE 互斥）
- [x] L1 `nop-xdsl-undeclared-consumer.rule.yml`：删 strategy/window triggerId 分支（保留其余）→ suite fixture 的 `triggerId="count10"` 删除或改注释（suite expect 同步）→ 门禁复核（suite 绿 + `--baseline-check` 仍 exit 0——现存 baseline 无 triggerId 条目，理论不需再生，实测确证）
- [x] WI3 linter：删 strategy/window triggerId 分支；TestStreamModelLinter 的 faces fixture 将 `triggerId="t1"` 换为 `accumulationMode="ACCUMULATING_AND_RETRACTING"`——比原稿更强：保住 >=4 断言与 `contains("strategy s1")`（strategy 级 lint 覆盖与「四面一次报齐」性质不丢；audit Minor-3 按实回写）
- [x] WI1 矩阵迁移：表 B 的 strategy/节点 triggerId 行迁表 A（目录语法 + 适用域 + 锚点）；**表 B 的 A&R 行放行归属改「保持拒绝（D10 spec-only，WI38）」**（原误标 WI5）
- [x] 矩阵适用域注记：ProcessingTimeoutTrigger 无 canMerge 覆写（继承 false）→ × session 一律拒绝；DeltaTrigger（bean 形态）canMerge=false → × session 拒绝（E2 GlobalWindows 场景不受影响）；EmitUpdateTrigger canMerge=true（既有 emit=UPDATE × session 不受影响）
- [x] L1 valid fixture（all-consumed.stream.xml）加 triggerId 正控（放行后 L1 接受 triggerId 的正例覆盖）
- [x] **A&R 一致性裁定登记**：roadmap WI5 行「非 DISCARDING accumulationMode 放行」的 A&R 半边已由 D10（WI38）改判 spec-only 维持拒绝——本 plan closure 时在 roadmap header 版本注记该事实（WI5 勾选含 D10 既有交付，不重开 A&R）；Deferred 节增设 A&R residual 条目

Exit Criteria:

- [x] 全部涉及测试绿；L1 suite 绿 + baseline-check exit 0；linter 测试绿
- [x] 矩阵两表与 live 一致
- [x] `ai-dev/logs/` 条目已更新

### Phase 3 - 目录/时序测试与收口

Status: completed
Targets: nop-stream-flow 测试

- Item Types: `Proof`

- [x] `TestTriggerDeclaration`（镜像 TestWindowAssignerBranches harness）：9 族目录逐条解析断言（getTrigger() 类型/参数）、bean 名优先、节点覆盖 strategy、未知 id fail、UPDATE 互斥、session×count 拒绝、purging 嵌套、processing-timeout 嵌套
- [x] **时序判别 E2E（落点 nop-stream-runtime 测试——flow 无 runtime 依赖，WindowedStreamImpl.aggregate 无 IWindowOperatorFactory 即抛 ERR_STREAM_UNSUPPORTED；先例 TestSideOutputPipelineE2E 的 DslModelParser+execute+有序 sink 断言）**：tumbling 窗口 + `count:2` 触发器 + ACCUMULATING——**4 条记录、恰 2 次发射：第 2 条后 [a,b]、第 4 条后 [a,b,c,d]**（emitWindowContents 发全量内容，第 2 次发射内容即 ACCUMULATING/DISCARDING 判别点；count 触发器 onEventTime 恒 CONTINUE，无「窗端终值发射」——cleanup 只清状态发 CLOSE 事件）——证明算子真实消费声明 trigger（接线验证 #22/#23）
- [x] 模块回归 + 独立 closure audit

Exit Criteria:

- [x] 目录族测全绿 + 时序 E2E 断言中间发射序列
- [x] 回归全绿
- [x] closure audit APPROVE 且证据入 plan

## Closure Gates

- [x] triggerId 双处放行且接线（类型断言 + 时序 E2E 双证）
- [x] 目录 9 族落位（Delta/自定义 = bean 形态文档化）
- [x] 组合矩阵校验落地（UPDATE 互斥 / merge 不兼容 / A&R 维持）
- [x] 放行三步法完成（钉子改放行断言 + L1/L2/矩阵联动 + owner doc）
- [x] 受影响 owner docs 已同步（user-guide 矩阵 + 触发语义映射表如涉）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] `./mvnw test -pl nop-stream/nop-stream-flow,nop-stream/nop-stream-runtime -am` 通过（E2E 落 runtime）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2351-wi5-trigger-declaration.md --strict` 退出码 0
- [x] roadmap Work Item Status 的 WI5 checkbox 已勾选（audit 通过后）

## Deferred But Adjudicated

### ACCUMULATING_AND_RETRACTING 维持拒绝（D10 spec-only）

- Classification: `watch-only residual`（既有裁定维持，非本 plan 新裁定）
- Why Not Blocking Closure: D10（WI38，2026-10-08）已改判 A&R 为 spec-only 拒绝——retract 语义未实现且无场景依赖；roadmap WI5 行的「非 DISCARDING 放行」半边由 D10 交付（ACCUMULATING）+ D10 拒绝面共同构成
- Successor Required: no（重启须 owner 裁定）

### trigger × evictor 组合校验

- Classification: `watch-only residual`（激活点 WI6）
- Why Not Blocking Closure: evictor 声明面是 WI6 交付物，其与 trigger 的组合矩阵随 WI6 落地；本 WI 矩阵留注释位
- Successor Required: yes
- Successor Path: WI6（plan 内组合校验扩展）

## Non-Blocking Follow-ups

（无）

## Closure

Status Note: triggerId 双处放行并接线（bean→目录→ref-unknown 解析序、节点覆盖 strategy、UPDATE 互斥前置、canMerge×session 构建期拒绝）；目录 9 族落位；放行三步法完成（钉子改造 + L1 规则/suite/baseline 实测零 stale + linter 联动 + 矩阵迁移）；时序 E2E 判别性验证 [3,10]。独立 closure audit APPROVE（0B/0M/3 Minor 注释与文本项已随收口修正；A&R 一致性裁定经 roadmap v5 版本注记落账）。
Completed: 2026-10-09

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent agent_204dac58-7d61-4c8a-9352-e2c6459e67ca（fresh session，未参与实现）
- Audit Session: agent_204dac58-7d61-4c8a-9352-e2c6459e67ca
- Evidence:
  - 放行核验：双处 fail-fast 删除 + resolveTrigger 全语法（审计逐条读码）+ UPDATE 互斥前置（:249-256）+ canMerge×Merging 拒绝（:261-272）+ A&R 三处保留（builder/L1/L2）
  - 测试：TestTriggerDeclaration 11/11 + TestTriggerCountE2E 1/1（audit 自行运行）；E2E 判别性成立——默认触发器零发射、DISCARDING [3,7]、ACCUMULATING [3,10]，三种错误形态均无法过断言
  - 放行三步法：钉子改造、L1 规则/fixture（expect 3 条 + valid 正控）、baseline --baseline-check exit 0（24 条零 triggerId 项零 stale，audit 实测）、linter 联动、矩阵两表迁移
  - 回归：flow 240/0 + runtime 1345/0+10 skipped（audit 独立复跑）
  - 门禁：doc-links 0；plan-checklist 0；L1 suite 73/73
  - diff 干净：无 xdef 变更、无 core Trigger 实现变更
  - Minor 3 项（DISCARDING 注释算术 [3,7]、类 javadoc D11 残留、plan 按实回写）已随收口修正
  - Deferred 项分类检查：A&R spec-only（D10 维持）与 trigger×evictor（WI6 successor）均为显式裁定，非静默降级

Follow-up:

- no remaining plan-owned work（trigger×evictor 组合校验随 WI6 落地）
