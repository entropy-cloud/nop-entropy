---
status: completed
mission: nop-ai-agent-design-comparison
work-item: WI6
group: "2026-09-12-1121"
verify: [test]
---

# WI6 S3 同一扩展点上多个触发的顺序

## Current Baseline

- 前置依赖 WI5 交付物 ai-dev/analysis/compare-agent-design/04-extension-capability-matrix.md（deps: WI5）：三方扩展点全量清单与单点能力已裁定；本计划只补"同点多实现共存时"的顺序维度
- 章节结构契约：WI2 矩阵登记 S3 = ① 结论摘要 ② 排序来源（注册顺序 / 优先级字段 / DSL 声明顺序 / 订阅顺序）③ 分发语义（waterfall 逐层包裹 / serial 顺序 await / emit 广播 / 链式）④ 顺序对调用方的可预测性与可配置性 ⑤ 权威源引用关系（供 dsh-D2 / pi-D2 引用）
- 已知线索（WI3-WI5 累积，本计划逐点核证）：nop DefaultHookRegistry session/execution 两层分存 + invokeHooks 顺序遍历（Veto/Bail/Reenter 短路）、middleware DSL 声明序、7-checkpoint 固定序不可配置；dsh cordis 五 mode（waterfall 洋葱/serial/emit）+ scope 层叠注册、无优先级字段；pi AgentLoopConfig 每类单槽无链（多扩展靠 coding-agent 组合函数串链）+ runner emit 逐 handler 顺序 await（扩展加载序→注册序）
- 环境注记：dsh 工作区 HEAD 已前移（WI5 audit 实测 c291e7961a，基线 141eb6fef8 为其祖先）——本计划执行时**三方 HEAD 均当日实测重钉**（记入交付物头部），锚点按各自当日 HEAD 核对
- 本交付物是同点多触发顺序主题的权威源：dsh-D2（WI9）/ pi-D2（WI19）引用本文档只写对比增量；与 WI5 的边界——本文档裁定顺序与分发语义机制，不重复单点能力级别

## Goals

- 产出 Deliverable：ai-dev/analysis/compare-agent-design/05-extension-ordering.md
- 三方每个可多实现共存点：排序来源（代码证据）+ 分发语义 + 短路/透传规则 + 可预测性/可配置性裁定

## Non-Goals

- 不重复单点能力级别裁定（WI5）
- 不裁定跨扩展机制的叠加/冲突裁决（WI7 S4）
- 不产出 D2 维度对比总裁定（WI9/WI19）
- 不修改任何代码；纯分析任务

## Phase 1 — 三方排序与分发语义核证与文档产出

Status: completed

Targets: ai-dev/analysis/compare-agent-design/05-extension-ordering.md（新建文件）

- Item Types: `Proof | Decision | Follow-up`

- [x] `Proof` nop 侧：DefaultHookRegistry 注册序（hook 列表数据结构、session/execution 两层关系——合并还是永不交互、重复注册行为）、invokeHooks 遍历序与短路规则（Veto/Bail/Reenter 命中后同点剩余 hook 还跑吗）、`<hook>` DSL 声明序、middleware 链排序（`<filter-chain>` 声明序及其先于 `<middlewares>` 的注册序 W3-2 + 重名 fail-loud、index 0 与洋葱层次）、7-checkpoint 固定序、事件订阅者顺序（CopyOnWriteArrayList 扇出序）、IContributionRegistry priority 排序、talent/skill/contribution 多注册装配序——**以 WI5 ④ 4.1 census 逐面判定是否可多实现共存，是则入册**
- [x] `Proof` dsh 侧：cordis 注册顺序（同事件多 listener 存储结构与遍历方向）、waterfall 洋葱方向与注册序关系（先注册=外层还是内层——shift 方向）、serial 顺序与 bail 短路、emit 顺序与同步 throw 饿死问题、parallel（session/flush allSettled）、scope 层叠（准入过滤还是排序）、prepend 唯一位置控制、priority 字段有无、重复注册行为、组成行序有无装载语义（fiber 服务可用性驱动）、llm-retry×compaction-basic 在 request-error 上的实际顺序场景、spill-policy prepend 钉位案例
- [x] `Proof` pi 侧：扩展加载顺序的决定链（CLI/settings 声明序/目录 readdir 序/稳定 rank 排序——最终决定因素）、runner handler 收集顺序（加载序×注册调用序）、专用 emit 链式组合方向（前者输出=后者输入？clone 时机）、session_before_* cancel 短路后剩余 handler 行为、首胜短路点（user_bash truthy/project_trust 非 undecided）、AgentLoopConfig 单槽的 coding-agent 组合（emitToolCall 装配 + _installAgentNextTurnRefresh 包装方向）、registerProvider 同名 config 字段级合并 vs native 后覆盖、重名工具先到先得规则
- [x] `Decision` 固化排序来源分类法（注册序/声明序/订阅序/组合序/固定序/单槽无序）+ 可预测性判定标准（顺序是否影响结果、是否对调用方可见/可配置）并产出三方对照
- [x] `Follow-up` 写入 ai-dev/analysis/compare-agent-design/05-extension-ordering.md；与 WI5/WI4 结论冲突时以代码为准回写勘误

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase 标记为完成。

- [x] ai-dev/analysis/compare-agent-design/05-extension-ordering.md 存在，章节 = S3 契约五节，无缺节；头部记录三方 HEAD（dsh 重钉）与分析日期
- [x] ② 节排序来源分类覆盖三方全部可多实现共存点，每点带注册/遍历代码锚点
- [x] ③ 节分发语义覆盖：nop hook 顺序遍历+短路规则、dsh waterfall/serial/emit/parallel 四 mode + scope 准入过滤 + inner 位置、pi 顺序 await + cancel/block/handled/首胜短路 + 链式组合方向 + 单槽包装链；每 mode 带实现锚点
- [x] ④ 节可预测性/可配置性裁定：每方回答"顺序影响结果吗/调用方能控制顺序吗/有优先级机制吗"三问，证据化
- [x] ⑤ 节声明权威源关系（dsh-D2/pi-D2 引用，只写对比增量）
- [x] 端到端验证（不适用）：纯文档分析任务
- [x] 接线验证（不适用）：无新组件
- [x] 无静默跳过（不适用）：无代码变更
- [x] No owner-doc update required（分析任务）
- [x] node ai-dev/tools/check-doc-links.mjs --strict 退出码为 0
- [x] ai-dev/logs/ 对应日期条目已更新

## Draft Review Record

- 对抗性审查：独立子代理 agent_0ee09cd6-2643-45cf-b4bf-b7886da1709c（fresh session），2026-09-12
- 结论：无 Blocker，3 处措辞级修订全部已修复——P1 nop Proof 补 `<filter-chain>` 注册序（W3-2，排序证据最硬处）；P2 Proof 增补 census 基线句（以 WI5 ④ 4.1 逐面判定可多实现共存则入册）；P3 dsh 补 parallel mode（session/flush）+ EC③ 四 mode；P4 HEAD 重钉措辞改三方

## Verification

- `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（0 errors；9 warnings 全部为存量：2258-xlang plan 8 处 + WI5 plan 1 处，非本次产物）
- `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-ai-agent-design-comparison/2026-09-12-1121-6-wi6-extension-ordering.md --strict` 退出码 0（16/16 checkbox 全勾，Closure Evidence 已写入）
- `roadmap-check.mjs`（AGE 模板）`passed: true`
- 纯文档分析任务，无代码变更：mvn 构建与测试不适用（Non-Goals 已声明）

## Closure

Status Note: 交付物 05-extension-ordering.md 已产出并通过独立子代理 closure audit（11 条 Exit Criteria 全部 PASS，19 项锚点实测零偏差，K1 dsh 先注册=最外层 shift 方向、K2 nop session/execution 永不交互、K3 与 04 无冲突全部核验成立）。
Completed: 2026-09-12

Closure Audit Evidence:

- Reviewer / Agent: 独立子代理 agent_e7deffc7-5492-449e-ad0c-4a78192cac99（fresh session，非实现 session）
- Evidence:
  - E1–E7 全部 PASS：五节齐备；三方 HEAD 实测（nop=800baf32da 经 `git diff --stat 800baf32da c585459f83 -- nop-ai` 为空实证；dsh 重钉 c291e7961a 实测吻合；pi=c49906ec7）；② 节 21 面排序来源全带锚点（census 抽查超额）；③ 节四 mode + 三方短路规则全覆盖；④ 三问裁定表证据化；⑤ 权威源+分工+重钉注记齐备；daily log 条目在位；check-doc-links 退出码 0
  - 锚点核对 19/19 零偏差：nop（DefaultHookRegistry:32 / AgentHookInvoker:154-177 / MiddlewareChain:55-61 / SecurityConsultation:276-284 / InMemoryContributionRegistry:139-153 + 加验 IHookRegistry:50-52、AgentExecutorResolver:151-156,280-294、KeyedList）；dsh（events.ts waterfall shift :234-243 / serial :204-209 / prepend :254-260 / parallel :183-187 / emit :194-196 / unregister :269-275 / spill-policy prepend :185-204 / dispatch contained / cordis.patch 行序 / scope 准入）；pi（runner emit cancel :801-833 / emitToolCall :932-953 / resourcePrecedenceRank :167-183 / model-runtime 合并 :742-778 / loader push :260-265 / NextTurnRefresh 包装 :540-561）
  - K1 PASS：events.ts:234-243 `shift()` 从注册序数组头部取——先注册=最外层=最先执行，与 05 结论一致
  - K2 PASS：IHookRegistry.java:50-52 "the two scopes never interact" + 三个独立 EnumMap 无合并路径
  - K3 PASS：05 与 04 能力/顺序分工干净，重叠行为描述一致，无冲突
  - 文本一致性：Phase 1 Status=completed、frontmatter status=completed、16/16 checkbox 全勾

Follow-up:

- no remaining plan-owned work
