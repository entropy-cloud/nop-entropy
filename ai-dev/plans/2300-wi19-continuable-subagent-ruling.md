# 2300 WI19 continuable 子代理 + 传输抽象裁定（refactor roadmap M5，Decision）

> Plan Status: completed
> Last Reviewed: 2026-10-03
> Source: `ai-dev/backlog/nop-ai-agent-refactor-roadmap.md` WI19（Deliverable: 裁定记录落 `ai-dev/design/nop-ai-agent/nop-ai-agent-multi-agent.md` 裁定小节；deps: 无；Item Type: Decision）
> Baseline Ruling: `ai-dev/analysis/2026-10/2026-10-02-wi1-dedup-ruling.md`（WI19 吸收，纯裁定项；来源 dsh-D10 ⑥-建议1/2 + pi-D10 ⑥-建议1）
> Design Contract: Owner `ai-dev/design/nop-ai-agent/nop-ai-agent-multi-agent.md`

## Purpose

dsh-D10 建议1（call-agent continuable 形态：子执行驻留 waiting/settled + 稍后 followup/取回）与建议2（CallAgentExecutor 抽象 SubagentProvider 传输接口：in-process/进程外/ACP 协议适配）+ pi-D10 建议1（进程外子代理最小协议参照）是否补强 nop——D10 总裁定 dsh 侧为"等价"（D10-3 nop 编排全栈显著领先），裁定吸收/移出。

## Ruling（Deliverable ①）

**结论：建议1 部分满足（差距如实登记，不吸收实施）；建议2 与 pi-D10 建议1 移出（重启条件登记）。**（round 1 审查否证初版"已满足"判定后修正——初版把驻留归因于 plan 224 async pathway，被 plan 224 自身契约（Non-Goals 排除异步 handler）与 live code（父同步等响应、waiting 呈 failure）双重否证。）

依据（执行时 HEAD `46464624dd`，2026-10-03）：

1. **建议1 部分满足（round 1 修正）**：满足面 = session 跨 turn 持续 + send-message/team followup（plan 168/225）+ 引擎级 WAIT_FOR/wakeSession 驻留状态机。差距面 = call-agent 无 continuable 工具形态——plan 224 async pathway 是异步管道+父同步等响应（AgentCallDelegate.java:92-93），子执行 waiting 态对父呈 failure（CallAgentExecutor.java:494、AgentCallDelegate.java:98-102），无 LLM 可达 wake/取回工具。差距属工具面便利性缺口（引擎机制在位），无生产需求证据，不吸收实施。原论据文本：：`CallAgentExecutor` 双路径（CallAgentExecutor.java:46-48）——async mailbox pathway（有 functional messenger 时默认）将子执行路由到**驻留的目标 session**（REQUEST 信封经 mailbox、异步请求-响应、超时显式报错 :333），父/子可继续经 send-message（plan 168）与 team 通信工具（plan 225）followup——"子执行驻留 + 稍后 followup"正是 dsh Activation 对位的既有形态。同步路径（engine.execute）服务即时等待场景。两层形态并存，建议1 的增量收益不成立。
2. **传输抽象（SubagentProvider）移出**：nop 子代理全在进程内（Actor runtime + 嵌套 engine 调用 + team 编排），无外部 agent 运行时接入需求；抽象接缝在无第二实现时是猜测性设计（违反本仓 anti-hollow 纪律——无消费者的接口即空壳）。pi-D10 建议1 同为条件式（"若 nop 未来支持进程外子代理"），且其参照价值（JSON 结构化输出捕获）到时可直接取用。
3. **维度总裁定佐证**：dsh-D10 总裁定"等价"——D10-3 nop 编排全栈显著领先（team.flow fan-out+AllMustSucceed 归约、深度钳制、Actor 通信栈）；pi-D10 总裁定"nop 领先"（pi 三方最弱、零内置）。两报告的增量均为条件式/柔性项，不改刚性面。
4. **深度记账（dsh 建议4，非本项清单但相关）**：登记为 owner doc residual 注记（delegationDepth 钳制已安全，显式深度审计字段为 optimization candidate，不在本裁定范围）。

**重启条件**（登记）：(a) 建议2/pi 建议1——真实外部 agent 运行时接入需求出现时 successor 立项 SubagentProvider 接缝；(b) 建议1 差距——生产场景出现 call-agent continuable 工具形态需求时 successor 立项（草图：fire-and-forget 变体返回子 sessionId + retrieve/wake 工具复用 wakeSession 机制）。

## Current Baseline

- `CallAgentExecutor`（nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/tool/CallAgentExecutor.java:84）双路径：sync engine.execute / async mailbox（plan 224 **completed**）。
- 既有配套：send-message（plan 168）、team 通信工具（plan 225）、IMailbox deferred-ack（plan 216）、WAIT_FOR/wakeSession（M1 WI4/WI5 面）、team.flow 编排（fan-out+归约）。
- pi-D10 总裁定"nop 领先"（零内置 pi 最弱）；dsh-D10 总裁定"等价"（D10-3 nop 显著领先）。

## Goals

- owner doc `nop-ai-agent-multi-agent.md` 新增"continuable 子代理与传输抽象裁定"小节。
- log + roadmap WI19 勾选 + 锚点。

## Non-Goals

- 任何代码变更；delegationDepth 深度字段实施。

## Execution Plan

### Phase 1 - 裁定落档与收口

Status: completed
Targets: `ai-dev/design/nop-ai-agent/nop-ai-agent-multi-agent.md`、log、roadmap

- Item Types: `Decision`

- [x] owner doc 裁定小节（No new test required: 纯裁定无代码变更——Rule #25 注记；round 1 审查修正后重写）
- [x] 独立子代理对抗审查（agent_b2b7b08f-7f92-45ce-9ec2-3e7a037ec950：否证初版"已满足"论据——plan 224 契约+live code 双重否证；裁定修正为部分满足，其余维持）
- [x] doc-links 0 error；roadmap WI19 勾选 + 锚点；log；commit

Exit Criteria:

- [x] 裁定在 owner doc 在位且论据与 live 一致；全部验证门通过

## Closure Gates

- [x] 无代码变更（纯裁定，构建验证门不适用——per guide 纯文档计划条款）
- [x] 独立子 agent closure-audit 已完成并记录证据（对抗审查 agent_b2b7b08f 兼 closure audit——其否证直接构成裁定修正依据，纯裁定项审查即验收）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2300-wi19-continuable-subagent-ruling.md --strict` 退出码 0
- [x] 受影响 owner docs 已同步
- [x] git commit 完成

## Closure

Status Note: 建议1 经独立对抗审查否证初版"已满足"判定后修正为部分满足（plan 224 契约与 live code 双重否证），差距与重启条件（含实施拆分草图）如实登记；建议2/pi 建议1 移出维持（无外部运行时需求 + anti-hollow）。无代码变更、无构建门（纯文档豁免）——WI19 交付（裁定记录）全部落地。
Completed: 2026-10-03

Closure Audit Evidence:

- Reviewer / Agent: 独立子代理 agent_b2b7b08f-7f92-45ce-9ec2-3e7a037ec950（对抗性审查兼 closure audit）
- Evidence:
  - 5 审查点：1 FAIL（初版论据1 否证——plan 224 自身契约与 AgentCallDelegate/CallAgentExecutor live code）→ 裁定修正；2 PASS+保留（followup 在位但无 wake-on-message，如实登记）；3 PASS（无 ACP 需求登记）；4 PASS（总裁定引用准确）；5 PASS（guide 合规）
  - 修正后 owner doc 与 plan Ruling 同步重写；doc-links exit 0
  - Follow-up: 重启条件见 Ruling (a)(b)；dsh 建议4 深度审计字段为 optimization candidate
