# 2353 WI7 窗口类型参数化与 session 合并接线（剩余面收口）

> Plan Status: completed
> Last Reviewed: 2026-10-09
> Source: `ai-dev/backlog/nop-stream-xdsl-full-coverage-roadmap.md`（WI7 行，M2 窗口控制面）
> Related: plan 2351/2352（trigger/evictor 声明面）

## Purpose

WI7 的参数化主体已由前置交付（sql roadmap WI26 交付 `sliding-event-time`/`session-event-time` 参数化 id + slide<=size 守卫；session 合并由 `MergingWindowSet` 在 WindowOperator 完整实现并有 4+ 算子级测试类）。本 plan 收口剩余面：**XDSL 声明路径**的 session 合并判别 E2E、session×allowedLateness 交互声明钉子、legacy 白名单/global 面显式化与口径对齐文档。

## Current Baseline（2026-10-09 live 核对）

- **已交付**：`resolveWindowAssigner` 参数化 `tumbling-event-time`+duration / `sliding-event-time`+duration+slide（slide<=size 守卫，sql+DSL 双守卫）/ `session-event-time`+duration；legacy 白名单 `global`/`tumbling-global`/`tumbling-event-time-1s`/`-5s`；bean 优先（`AdvancedTransforms.resolveWindowAssigner :465-542`）；session 合并 `MergingWindowSet` 消费（WindowOperator :749/:949/:968-976）+ `TestSessionWindowAdvancedMerge`/`TestMergingWindowSet`/`TestWindowOperatorMergingCleanupInvariant` 算子级测试；session×allowedLateness 算子级覆盖（`TestWindowOperatorPerWindowStateCleanup` 等）。
- **缺口（本 plan）**：无任何 runtime 测试资源或 E2E 走 **XDSL 声明的 `session-event-time`**（`session-event-time` 仅出现在 flow 的 builder 分支测试与 sql 测试）；legacy 白名单的兼容性承诺（不移除）未成文；duration 外部口径对照（HOP=sliding(size,slide)、SESSION=gap）未在 DSL 矩阵行标注。

## Goals

- XDSL 声明路径 session 合并判别 E2E：`session-event-time` + duration 经 .stream.xml 声明→execute→合并断言（两条相交窗口的记录合并为一个窗口发射——判别不合并的 tumble 行为）。
- session×allowedLateness 交互声明钉子：同一 fixture 变体声明 allowedLateness，断言合并窗 late 区间的行为（lateness 传递到算子——`windowed.allowedLateness` 统一消费点已存在，本钉证明声明路径直通）。
- legacy 白名单与 global 面显式化：兼容性承诺文档句（白名单 id 冻结不移除、`global` 无 duration 语义——其参数化形态是 countWindow 组合即 GlobalWindows+evictor+trigger）+ 矩阵行口径对齐注记（HOP/SESSION 外部口径对照）。

## Non-Goals

- 不新增 windowFnId（参数化面已齐）；不改 MergingWindowSet/算子实现；不做 global+duration 语义（GlobalWindows 无界，参数化形态 = countWindow 组合）。

## Scope

### In Scope

- runtime 测试：`TestXdslSessionMergeE2E`（声明路径合并判别 + lateness 交互钉）。
- user-guide 矩阵行口径注记 + legacy 兼容承诺句。

### Out Of Scope

- builder/xdef/core 变更；sql 文档（其 D4 口径已存在）。

## Execution Plan

### Phase 1 - 声明路径 session 合并 E2E 与钉子

Status: completed
Targets: `nop-stream-runtime/src/test/java/io/nop/stream/runtime/integration/`

- Item Types: `Proof`

- [x] `TestXdslSessionMergeE2E`（fixture 镜像 TestTriggerCountE2E：`session-event-time` duration="50ms" accumulationMode="ACCUMULATING"（xdef 默认 DISCARDING 须显式声明）+ 逐事件水位 watermarkInterval="0" + keyExpr="1" + reduce(sum) + 收集 sink；timestampAssigner=event）：
  - **测试 1 合并判别**：输入 ts=[0,30,55,200]——session 窗 [ts,ts+gap)：[0,50)+[30,80)→[0,80)+[55,105) 合并为单窗 [0,105)（cover 语义，窗端=最后元素 ts+gap，fire timer 随合并重注册 49→79→104）；wm 200 触发 timer 104 → 发射 **85**（=sum(0+30+55)，window maxTimestamp 104）；流终止 MAX_WATERMARK flush 尾窗 [200,250) → 发射 **200**。断言 `SINK == [85L, 200L]`（85 先于 200 证明 timer 随合并重注册非 flush 才出数；反事实：合并坏掉 → flush 全 fire 出 [0,30,55,200] 4 条——写死在断言消息）
  - **测试 2 lateness 钉**：strategy 加 `allowedLateness="10000"`（**硬约束：须 >96ms**——merge guard `ERR_STREAM_WINDOW_MERGE_INVALID_WATERMARK` 要求 mergeResult.maxTimestamp+lateness > currentWatermark=200，WindowOperator :984-990）；输入 ts=[0,30,55,**200**,40]（**迟到 40 必须排在推 wm 过 104 的 200 之后**——否则 40 只是普通 merge，钉子静默失效）：85（同测试 1）→ 迟到 40 合入在飞窗 [0,105)，EventTimeTrigger 见 maxTimestamp 104 ≤ wm 200 **立即重 fire 全量 contents** → **125**（=sum(0+30+55+40)；skipPurgeForLateness 使 DISCARDING 也不 purge，同值）→ flush 尾窗 **200**。断言 `SINK == [85L, 125L, 200L]`（只发 40 或丢弃均为错误形态）
  - 可选对照注释：超 cleanup 丢弃路径（ts=10500 推过 104+10000 后再发 40 → 窗已清、新窗被 isWindowLate 拒收、isElementLate 成立丢弃）——以断言消息注记说明两极语义，不另立测试
- [x] 断言全部经声明面（无 Java API 设窗）；`resolveWindowAssigner` 行号锚点更正为 :465-543（原 plan :420-481 漂移）

Exit Criteria:

- [x] E2E 绿且合并判别成立（不合并反事实被排除）
- [x] `ai-dev/logs/` 条目已更新

### Phase 2 - 文档显式化与收口

Status: completed
Targets: user-guide

- Item Types: `Proof`

- [x] 矩阵「窗口 assigner 参数化」行补：外部口径对照（HOP(t,slide,size)=sliding-event-time duration+slide；SESSION(t,gap)=session-event-time duration）+ legacy 白名单兼容承诺（冻结不移除）+ `global` 面说明（无 duration 语义；参数化形态=GlobalWindows+evictorId+triggerId 组合即 countWindow 等价物）
- [x] flow+runtime 回归 + 独立 closure audit

Exit Criteria:

- [x] 文档句落地
- [x] 回归全绿
- [x] closure audit APPROVE 且证据入 plan

## Closure Gates

- [x] XDSL 声明路径 session 合并 E2E 判别成立
- [x] session×allowedLateness 交互钉落地
- [x] legacy/global 面显式化文档落地
- [x] 受影响 owner docs 已同步（矩阵行注记）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] `./mvnw test -pl nop-stream/nop-stream-flow,nop-stream/nop-stream-runtime -am` 通过
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2353-wi7-session-merge-remaining.md --strict` 退出码 0
- [x] roadmap Work Item Status 的 WI7 checkbox 已勾选（audit 通过后）

## Deferred But Adjudicated

### global + duration 语义

- Classification: `watch-only residual`
- Why Not Blocking Closure: GlobalWindows 无界（无窗端），duration 无语义挂点；其参数化形态 = countWindow 组合（GlobalWindows+evictor+trigger，`KeyedStreamImpl.countWindow :192-195` 官方入口），E2 场景（GlobalWindows+DeltaTrigger+TimeEvictor）不依赖 duration
- Successor Required: no（E2 套件落地时按需复核）

## Non-Blocking Follow-ups

（无）

## Closure

Status Note: XDSL 声明路径 session 合并判别 E2E（[85,200] 合并单窗 + flush 尾窗；反事实 4 发射写死断言消息）+ lateness 交互钉（[85,125,200]，迟到顺序与 guard 下限两硬约束落实）+ legacy/global 面显式化文档全部落地；零产品代码变更。WI7 三子项经审计独立判定全覆盖（参数化=sql WI26 前置、合并消费=既有算子链+算子级 suite、显式化=本 plan）。
Completed: 2026-10-09

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent agent_d9eecc3f-51d6-4789-aa62-3aacac8cd6e3（fresh session，未参与实现）
- Audit Session: agent_d9eecc3f-51d6-4789-aa62-3aacac8cd6e3
- Evidence:
  - 合并数学独立推演 + 实测日志双重确认（Merging [0,50)+[30,80)→[0,80)、+{55,105}→{0,105}；merge guard :968-990 实核）；TestXdslSessionMergeE2E 2/2 绿（audit 自行运行）
  - lateness 钉：顺序硬约束（40 在 200 后）与 guard 下限（L>96ms）落实；skipPurgeForLateness :1394 实核
  - 文档行三要素齐备且表格结构/锚点约定未破坏
  - 回归：runtime 1348/0（audit 实测）+ BUILD SUCCESS
  - 门禁：doc-links 0；plan-checklist 0
  - WI7 三子项覆盖独立判定：全覆盖；唯一 residual（global+duration）已归类 watch-only
  - Minor 5 项（注释过度声明/锚点并存漂移/2352 的 log 链接路径）已修或记档（2352 链接修复归其 owner 收尾）

Follow-up:

- no remaining plan-owned work（plan 2352 的 daily-log 链接路径 nit 由其 owner 顺手修）
