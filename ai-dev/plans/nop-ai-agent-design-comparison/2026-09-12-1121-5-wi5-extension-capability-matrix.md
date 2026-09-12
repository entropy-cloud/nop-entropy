---
status: completed
mission: nop-ai-agent-design-comparison
work-item: WI5
group: "2026-09-12-1121"
verify: [test]
---

# WI5 S2 扩展点全量清单与能力语义

## Current Baseline

- 前置依赖 WI4 交付物 ai-dev/analysis/compare-agent-design/03-flow-agent-loop.md（deps: WI4）：三方扩展点挂载位置已测绘；WI3 术语表 T7 已给能力词面映射（并声明"WI5 以代码为准逐点核证"）
- 章节结构契约：WI2 矩阵登记 S2 = ① 结论摘要 ② 能力级别五级定义（observe 仅监听 / transform 修改内容 / veto 否决跳过 / abort-bail 中断轮次 / inject 注入内容）③ 三方每个扩展点一行：触发时机、可扩展/可影响内容、能力级别、同步异步、异常如何传播 ④ 能力级别汇总对比表 ⑤ 权威源引用关系（供 dsh-D2 / pi-D2 引用）
- 硬约束（roadmap Cross-Cutting）：能力级别必须以代码实际行为证明，不以注释、文档或类型名为准
- 已有能力证据（WI3/WI4 调研累积，本计划逐点核证升级为能力裁定）：nop HookResult 四态消费点与 cap（veto cap 3 / bail cap 3 / reenter cap 3，合法点白名单）、ExecutionPoint veto 语义；dsh 各 waterfall 返回类型（PreStepDecision/LlmCallConfig/RequestErrorAction/Pre/PostToolDecision）与 cordis 五 mode 分发语义；pi BeforeToolCallResult/AfterToolCallResult、tool_call 就地 mutate、message_end 同 role 替换、session_before_* cancel
- nop 侧扩展接口全景在 owner doc `ai-dev/design/nop-ai-agent/03-extension-matrix.md`，但该 doc 自身计数口径分裂（§一称 67、§三分类表合计 63、Layer 标题合计 65、实际行数 61），且 doc 日期 2026-06-21 后代码有新增（当前主源码 public interface 实测约 73）——本计划**以 live 代码为准做接口普查**，权威数量以普查结果为准；与 owner doc/矩阵/roadmap 中"66 接口"口径的差异在 daily log 登记 docs bug 线索，必要时回写 00 矩阵与 roadmap
- 本交付物是扩展点能力语义主题的权威源：dsh-D2（WI9）/ pi-D2（WI19）引用本文档只写对比增量；与 S3（顺序）/S4（协同）的边界——本文档裁定单点能力，不裁定多实现顺序（WI6）与跨机制叠加（WI7）

## Goals

- 产出 Deliverable：ai-dev/analysis/compare-agent-design/04-extension-capability-matrix.md
- 五级能力定义固化 + 三方扩展点全量清单逐点裁定能力级别（代码证据）+ 同步异步与异常传播 + 汇总对比表

## Non-Goals

- 不裁定同点多实现的排序来源与分发顺序细节（WI6 S3）
- 不裁定跨扩展机制叠加/冲突裁决（WI7 S4）
- 不产出 D2 维度对比总裁定（WI9/WI19 的工作）
- 不修改任何代码；纯分析任务，不涉及 mvn 构建与测试

## Phase 1 — 三方扩展点能力清单核证与矩阵产出

Status: completed

Targets: ai-dev/analysis/compare-agent-design/04-extension-capability-matrix.md（新建文件）

- Item Types: `Proof | Decision | Follow-up`

- [x] `Proof` nop 侧：核对 owner doc 66 接口清单与 live 代码；对 agent loop 直接挂载面逐点核证能力级别——12 AgentLifecyclePoint（HookResult 四态消费点/合法点白名单/cap）、4 ExecutionPoint（veto→retry 决策）、7-checkpoint 安全链（ALLOW/DENY/DENY_AND_BREAK）、guardrail（BLOCK/MODIFY）、IToolCallRepairer（transform）、IModelRouter/ICompletionJudge/ISustainer/IWaitCoordinator/IDenialLedger 等策略面、talent/skill/contribution 注册面
- [x] `Proof` dsh 侧：逐扩展点核证能力级别——封闭清单：agent/* 3 waterfall（pre-step/request/request-error）+ 1 serial（turn-stopping）+ 8 emit（status/error/session-start/created/disposed/inbox inserted·claimed·discarded）+ tools/* 4 waterfall（pre-execute/execute/post-execute/code-dispatch-log）+ 2 emit（result/change）+ llm/stream waterfall + system-prompt/assemble waterfall + session/* 4（event/flush/created/disposed）+ scope 注册面（systemPrompt.section/context、tools 注册）；waterfall 不调 next() 的否决语义、emit 异常包含、serial bail 值的代码证据
- [x] `Proof` pi 侧：AgentLoopConfig 9 个 hook 函数逐点核证（transformContext/beforeToolCall/afterToolCall/prepareNextTurn/shouldStopAfterTurn/convertToLlm/getApiKey/getSteeringMessages/getFollowUpMessages；toolExecution 是调度配置枚举非 hook，单独注明）+ ExtensionAPI 34 个 type 标签**逐个**定级（能力相同的可归组但每组列全成员及各自级别；34 = pi.on 重载/type 标签数，不是 25 联合成员或 48 具体事件接口）+ register* 注册面；hook 抛异常传播路径（error 工具结果 / runner 的 errorListeners 上报，确切表面以代码为准）
- [x] `Decision` 固化五级能力定义（每级判定标准：什么代码证据支撑哪一级）并补三条映射规则：(1) 策略对象（可替换组件非监听者）按"对主循环的影响面"标注并加"非 hook 策略对象"注记——nop 侧封闭点名：IModelRouter/ICompletionJudge/ISustainer/IWaitCoordinator/IDenialLedger/ICircuitBreaker/IRetryPolicy/IBudgetProvider/IContextCompactor；(2) 注册面（talent/skill/contribution、dsh scope、pi register*）固定标注"注册面，非运行时能力点"（其注入效果可标 inject）；(3) 预登记灰区裁定：nop DENY_AND_BREAK（断工具批+经 pause 通路终止轮次）→ abort-bail；nop 执行级 veto（合成失败进 retry 决策）→ veto；pi terminate:true 全批软终止 → abort-bail（优雅非错误）；pi hook 抛异常降级为 error 结果 → 按该点主能力 + 异常传播栏说明；dsh waterfall 不调 next() → 技术性跳过内层，语义由消费端类型裁定
- [x] `Decision` 产出三方逐点能力矩阵（③ 节）与汇总对比表（④ 节，含每方 census 计数表：按 hook/waterfall/serial/emit/事件/注册面/策略对象分类计数，计数单位 = 可注册监听面/事件类型）
- [x] `Follow-up` 写入 ai-dev/analysis/compare-agent-design/04-extension-capability-matrix.md；覆盖边界声明：非 loop 挂载的 owner doc 接口（Layer 4 平台扩展约 40 个）在 ⑤ 或附录登记普查结论，不逐点分级；与 02 术语表 T7 / 03 流程文档 ③ 冲突时以代码为准回写勘误；WI5 仅记录判定能力级别/异常传播所必需的分发行为后果（每点一行内），模式级分发语义的完整机制描述归 WI6

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase 标记为完成。

- [x] ai-dev/analysis/compare-agent-design/04-extension-capability-matrix.md 存在，章节 = S2 契约五节，无缺节；头部记录三方 HEAD 与分析日期
- [x] 五级能力定义含可操作的判定标准（什么代码证据支撑哪一级）
- [x] ③ 节三方逐点行覆盖（计数单位 = 可注册监听面/事件类型，④ 节附 census 表）：nop = 12 lifecycle + 4 execution + 7 checkpoint + 9 策略对象（封闭点名见 Decision 项）+ 注册面（talent/skill/contribution×7 类/DSL/事件订阅）+ 护栏 + repairer；dsh = agent/* 12 + tools/* 6 + llm/stream + system-prompt/assemble + session/* 4 + scope 注册面；pi = 9 hook 函数（toolExecution 注明为调度配置）+ 34 个 type 标签逐个定级 + register* 注册面；每行含触发时机/可影响内容/能力级别/同步异步/异常传播五要素
- [x] 能力级别全部以代码行为为证据（每行至少一个锚点），无"以注释/类型名为准"的裁定
- [x] ④ 汇总对比表按五级 × 三方汇总量化，能直接支撑 WI9/WI19 的 D2 对比增量
- [x] 端到端验证（不适用）：纯文档分析任务，无运行时路径
- [x] 接线验证（不适用）：无新组件与已有组件协作
- [x] 无静默跳过（不适用）：无代码变更
- [x] No owner-doc update required（分析任务，不改变 live baseline；若发现 owner doc 03-extension-matrix.md 与代码不符，按 docs bug 在 daily log 登记而非本计划内修改）
- [x] node ai-dev/tools/check-doc-links.mjs --strict 退出码为 0
- [x] ai-dev/logs/ 对应日期条目已更新

## Draft Review Record

- 对抗性审查（含想象性分析）：独立子代理 agent_3944c2bc-77ab-4d00-a4d7-310fbcefd6b2（fresh session），2026-09-12
- 结论：无 Blocker，4 Major 必须先修，全部已修复——F1 "66 接口"基数错误（owner doc 自身 65/67/61/68 口径分裂，live 约 73 interface）→ 改为以代码为准普查+差异登记；F2 dsh 清单错误（turn-stopping 是 serial 非 waterfall；补 created/disposed/inbox emits）→ 封闭枚举；F3 EC 量词不可数 → 策略面封闭点名 + census 表 + 计数单位定义；F4 五级定义未覆盖非 hook 表面与灰区 → 补三条映射规则 + 灰区预登记。Minor F5-F8（覆盖边界声明、WI6 分发语义边界、34 事件逐个定级口径、ExtensionError 措辞）已并入相应条款

## Verification

- `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（0 errors / 8 warnings，warnings 全部为 `ai-dev/plans/2258-xlang-try-switch-fix.md` 同源存量问题）
- `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-ai-agent-design-comparison/2026-09-12-1121-5-wi5-extension-capability-matrix.md --strict` 退出码 0（17/17 checkbox 全勾，Closure Evidence 已写入）
- `roadmap-check.mjs`（AGE 模板）`passed: true`
- 纯文档分析任务，无代码变更：mvn 构建与测试不适用（Non-Goals 已声明）

## Closure

Status Note: 交付物 04-extension-capability-matrix.md 已产出并通过独立子代理 closure audit（11 条 Exit Criteria 全部 PASS，15+ 锚点实测命中，K1 pi 34 事件逐个定级完整性、K2 与 03/02 无未登记冲突、K3 owner doc 勘误线索抽验属实）；audit 的 polish 项（HookContext 目录前缀）已修正。
Completed: 2026-09-12

Closure Audit Evidence:

- Reviewer / Agent: 独立子代理 agent_0c8235e6-ba6a-46e6-83ae-15b8272cf2fc（fresh session，非实现 session）
- Evidence:
  - E1–E7 全部 PASS：五节齐备（① 6 bullet ≤10 行）；头部三方 HEAD 实测（nop=800baf32da 与 c585459f83 diff 为空声明实测成立）；三方逐点覆盖对照封闭清单逐项核数通过（nop 12+4+7+9+注册面 / dsh 16 行 / pi 9 hook+34 事件+9 注册面）；能力裁定全部带消费点锚点；census 表可复算（pi 53 精确、dsh 25 精确）；daily log WI5 条目 + 6 项 owner doc 勘误线索登记；check-doc-links 退出码 0
  - 高风险裁定抽验 15+ 行全部命中：nop PRE_CALL veto→completed :410-416、PRE_ACTING 丢弃 :304、AttemptContext 只读、execution veto cap 3（:766 MAX_EXECUTION_VETOES=3）、POST_REASONING bail cap 3（:148）、IContextCompactor 吞异常、dsh pre-execute 禁改参数（:588-591）、turn-stopping 返回值丢弃（:295-299）、waterfall 不调 next 语义（cordis:234-243）、pi beforeToolCall 无 args（:61-69）、tool_call mutate 无再校验（:914-928）、user_bash 替代执行（IM:6596-6631）、tool_call 异常降级 veto（R:941-947）
  - K1 PASS：pi.on 重载实测 34 个（types.ts:1219-1252），04 分组 8+6+1+19=34 无遗漏重复
  - K2 PASS：与 03 ③ 表逐行一致，两处良性口径差已登记（pi hook 计数 10 vs 9+配置；REASONING_CHUNK 观察被死点裁定取代）
  - K3 PASS：PromptInjectionGuardrail implements IContentGuardrail 实证；HookToMiddlewareAdapter 零外部引用实证
  - Minor 已修复：HookContext 路径 engine/→hook/（04 文档）
  - 环境注记：dsh 工作区 HEAD 已前移至 c291e7961a（04 基线 141eb6fef8 为其祖先，锚点验证有效）；后续实测 dsh 的 WI 须重钉基线
  - 文本一致性：Phase 1 Status=completed、frontmatter status=completed、17/17 checkbox 全勾

Follow-up:

- owner doc 03-extension-matrix.md 的 6 项勘误线索修订（已在 04 ⑤ 节与 daily log 登记，属独立的 docs-bug 修复任务，非本 plan 范围）
- no remaining plan-owned work
