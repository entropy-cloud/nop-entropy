---
status: completed
mission: nop-ai-agent-design-comparison
work-item: WI7
group: "2026-09-12-1121"
verify: [test]
---

# WI7 S4 不同扩展机制之间的协同

## Current Baseline

- 前置依赖 WI4 + WI5 + WI6 交付物（deps: WI4, WI5, WI6）：03-flow-agent-loop.md（流程挂载位置，③ 节段序基准）、04-extension-capability-matrix.md（单点能力）、05-extension-ordering.md（同点顺序与分发语义）；本计划只补"跨机制"维度
- 章节结构契约：WI2 矩阵登记 S4 = ① 结论摘要 ② 三方各自扩展机制面（nop：lifecycle hook × execution middleware × filter chain × DSL 声明扩展；dsh：agent waterfall × tools waterfall × llm/stream × system-prompt；pi：AgentLoopConfig hook × ExtensionAPI event × registerProvider）③ 同一行为穿过多种扩展机制时的叠加顺序 ④ 冲突裁决、veto/bail 的传播边界与终止范围 ⑤ 权威源引用关系（供 dsh-D2 / pi-D2 引用）
- 已知材料（WI4-WI6 累积）：三方流程挂载位置（03 ③ 表）、单点能力（04 ③）、同点顺序（05 ③）；跨机制叠加的典型场景已有线索——nop 一次工具执行穿过 hook/middleware/7-checkpoint/repairer 四机制面、dsh request-error 上 llm-retry×compaction 委托链与 installModelSelection 成对监听、pi beforeToolCall 单槽与 tool_call 事件双通道装配为同一 runner 链
- 基线注记：dsh 基线 c291e7961a（WI6 重钉）；本计划执行时三方 HEAD 当日实测，锚点按各自 HEAD 核对
- 本交付物是跨扩展协同主题的权威源：dsh-D2（WI9）/ pi-D2（WI19）引用本文档只写对比增量；与 WI5/WI6 的边界——本文档裁定机制间叠加与冲突裁决，不重复单点能力（WI5）与同点顺序（WI6）

## Goals

- 产出 Deliverable：ai-dev/analysis/compare-agent-design/06-extension-composition.md
- 三方各一张"机制面 × 行为"叠加图：一次典型行为（LLM 调用/工具执行/轮次收口）穿过各机制面的先后顺序；冲突裁决规则与 veto/bail 传播边界（终止范围：单工具/单步/单轮/整个 run）

## Non-Goals

- 不重复单点能力级别（WI5）与同点多实现顺序（WI6）
- 不产出 D2 维度对比总裁定（WI9/WI19）
- 不修改任何代码；纯分析任务

## Phase 1 — 三方跨机制叠加与冲突裁决核证与文档产出

Status: completed

Targets: ai-dev/analysis/compare-agent-design/06-extension-composition.md（新建文件）

- Item Types: `Proof | Decision | Follow-up`

- [x] `Proof` nop 侧：三条典型行为链的机制面叠加序（①一次 LLM 调用：PRE_CALL middleware 洋葱（core=hook 顺序遍历）→ 治理闸门 → PRE_COMPACT 洋葱 → 压缩（POST_COMPACT 为非 chain 直调 hooks 且先于历史替换）→ PRE_REASONING 洋葱 → 输入护栏 → route → PRE/POST_LLM_ATTEMPT 执行级（pass-through，retry 循环内逐 attempt）→ 响应落账 → POST_REASONING 洋葱 → 输出护栏 → judge；②一次工具执行：repairer → 7-checkpoint → 建批（PRE_TOOL_ATTEMPT）→ join → 逐结果提交（POST_TOOL_ATTEMPT → PRE_ACTING → spill → BEFORE_TOOL_RESULT_PROCESSED（Reenter 替换）→ 回填 → TOOL_EXECUTION checkpoint → POST_ACTING → AFTER（Reenter 标记）→ marker 注入）；③轮次收口：judge/POST_CALL/sustainer），逐点标注机制面归属；冲突裁决矩阵（middleware veto 结构性优先于 hooks（断链即 core 不执行）、三组 cap 独立作用域、治理闸门优先序 cancel>pause>wait>force-stop>goal、DENY_AND_BREAK⟺阈值达标⟺即时 paused、PRE_CALL veto 仅事件 payload 标记无结构化字段、POST_COMPACT hook 注入消息被历史替换丢弃）；veto/bail 终止范围表（四级粒度逐级登记可达/不可达+证据）
- [x] `Proof` dsh 侧：三条典型行为链（①一次模型请求：system-prompt/assemble → agent/pre-step → agent/request → llm/stream → chunk 落账；②一次工具执行：tools/pre-execute → tools/execute → tools/post-execute → tools/result 线性管线；③错误恢复：agent/request-error 上多 listener 委托链 llm-retry×compaction），机制面归属标注；冲突裁决（同机制面拒绝/改写并存时 waterfall 外层裁决；跨机制面：pre-step reject 与 model selection 的交互、installModelSelection 成对监听防 prompt/路由撕裂）；传播边界（扩展点 veto 全部终止于 step/turn 粒度，无 abort-bail；agent.cancel 是唯一 run 级中断且不经扩展点）
- [x] `Proof` pi 侧：三条典型行为链（①一次 LLM 调用：input/before_agent_start → context(transformContext) → before_provider_headers → before_provider_request → after_provider_response；②一次工具执行：tool_call（block/mutate） → 执行 → tool_result → message_end；③run 收口：shouldStopAfterTurn/terminate/auto_retry/overflow compact 的恢复循环），机制面归属标注；冲突裁决（配置级 hook 与扩展事件双通道的装配关系——同一 runner 链；abort 信号与 auto-retry 的交互——退避中被 abort 归一为 aborted）；传播边界（block=单工具、cancel=单操作、shouldStopAfterTurn/terminate 全批=run、compact cancel=单次压缩）
- [x] `Decision` 固化"机制面叠加图"表示法（行为 × 机制面 × 顺序）与冲突裁决判定框架（同面冲突=顺序裁决 / 跨面冲突=流程位置裁决 / 语义冲突=谁的数据被消费），产出三方对照与 veto/bail 终止范围对比表（按单工具/单步/单轮/整个 run 四级逐级登记可达/不可达+证据；一方特有粒度增列附加行，每条裁定带代码证据）
- [x] `Follow-up` 写入 ai-dev/analysis/compare-agent-design/06-extension-composition.md；② 节按 WI5 census 全集归类登记机制面并标注与 S4 契约四类列举的映射（沿用 04 ② "非 hook 表面映射规则"）；未被典型行为链穿过的 census 面显式登记；与 WI4/WI5/WI6 结论冲突时以代码为准回写勘误

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase 标记为完成。

- [x] ai-dev/analysis/compare-agent-design/06-extension-composition.md 存在，章节 = S4 契约五节，无缺节；头部记录三方 HEAD 与分析日期
- [x] ② 节三方机制面清单与 WI5 census 一致（不新增不遗漏机制面）
- [x] ③ 节三方各有 ≥3 条典型行为链的机制面叠加图，每段标注机制面归属与锚点，段序与 03 流程文档一致
- [x] ④ 节冲突裁决 + veto/bail 传播边界与终止范围表：按 单工具/单步/单轮/整个 run 四级逐级登记可达/不可达 + 一方特有粒度附加行，每条裁定带代码证据
- [x] ⑤ 节声明权威源关系（dsh-D2/pi-D2 引用，只写对比增量）
- [x] 端到端验证（不适用）：纯文档分析任务
- [x] 接线验证（不适用）：无新组件
- [x] 无静默跳过（不适用）：无代码变更
- [x] No owner-doc update required（分析任务）
- [x] node ai-dev/tools/check-doc-links.mjs --strict 退出码为 0
- [x] ai-dev/logs/ 对应日期条目已更新

## Draft Review Record

- 对抗性审查：独立子代理 agent_63685d7e-bf86-4b7f-86fe-7dc01b62fa39（fresh session），2026-09-12
- 结论：可执行，3 处措辞修订全部已修复——P1 "PRE_CALL hook洋葱"改为 middleware 洋葱（core=hook 顺序遍历，洋葱为 middleware 专属语义）；P2 ② 节 census 双约束收敛规则（按 census 全集归类+标注契约映射）；P4 终止范围表四级登记可达/不可达+附加行、去中英混排；deps 补 WI4（03 流程文档为事实读取依赖）

## Verification

- `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（0 errors；9 warnings 全部为存量，非本次产物）
- `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-ai-agent-design-comparison/2026-09-12-1121-7-wi7-extension-composition.md --strict` 退出码 0（16/16 checkbox 全勾，Closure Evidence 已写入）
- `roadmap-check.mjs`（AGE 模板）`passed: true`
- 纯文档分析任务，无代码变更：mvn 构建与测试不适用（Non-Goals 已声明）

## Closure

Status Note: 交付物 06-extension-composition.md 已产出并通过独立子代理 closure audit（E1-E7 + K1-K3 全部 PASS，14 锚点实测全对，文本一致性达标）；M1 专项深挖里程碑（WI4-WI7）全部完成。
Completed: 2026-09-12

Closure Audit Evidence:

- Reviewer / Agent: 独立子代理 agent_3fed5ef9-7700-4d79-aae9-eb8b30ec381b（fresh session，非实现 session）
- Evidence:
  - E1–E7 全部 PASS：五节齐备且与矩阵 S4 契约逐一对应；头部三方 HEAD 实测（nop 800baf32da 与 HEAD diff 为空、dsh c291e7961a、pi c49906ec7）；② census 归类+映射与 04 交叉核对全一致、未穿过面显式登记；③ 三方各 3 链+机制面归属+勘误声明；④ 16 行冲突裁决带锚点+四级终止范围表+附加粒度行；⑤ 权威源+与 03 精化关系声明；daily log 条目在位；check-doc-links 退出码 0
  - K1 PASS：06 对 03 的两处顺序精化与代码一致（nop BEFORE :323 先于回填 :349 与 checkpoint；dsh agent/request :530 先于 user/message 落账 :373-377，runtime-types.ts:334-336 注释证实）
  - K2 PASS：终止范围总结与 04 已裁定结论零矛盾（nop PRE_CALL run 级 / dsh 无 / pi shouldStopAfterTurn+terminate）
  - K3 PASS：handleRunFailure 仅 loop 体未捕获异常可达（agent.ts:502-527），工具钩子异常降级单工具级（agent-loop.ts:661-667）
  - 锚点核对 14/14：nop 9 + dsh 6 + pi 5（部分跨核验共用），全部行号精确
  - 两处轻微观察（非阻塞）：nop 链 3 / pi 链 1 无行内锚点行、4.2 综合表无行内锚点——相关裁定已在 04/③ 获代码级锚定且本次独立补证
  - 文本一致性：Phase 1 Status=completed、frontmatter status=completed、16/16 checkbox 全勾

Follow-up:

- no remaining plan-owned work
