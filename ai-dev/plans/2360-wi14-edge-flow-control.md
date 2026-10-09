# 2360 WI14 — 边级流控声明面处置

> Plan Status: completed
> Last Reviewed: 2026-10-10
> Source: `ai-dev/backlog/nop-stream-xdsl-full-coverage-roadmap.md` WI14（v12）
> Related: plan 2359（WI13 Java-only 裁定先例）、plan 2358（WI12 边属性拒绝面）、WI0 授权面

## Purpose

把 roadmap WI14 收口到：边级 `flowControlPolicy`/`queueCapacity`/`receiveWindow`/`packetSize` 四属性完成「接线 or 维持拒绝 + 显式裁定」处置并落地裁定记录。

## 裁定（对抗性审查后改判：维持拒绝面 + 显式 deferred + 引擎级 successor）

首版 plan 拟定「EdgePlan 加性槽 + provider 读取 + builder 放行」的接线方案，经独立对抗性审查（逐条 live 查证）发现接线在本 roadmap 声明面范畴内**不可达**——三个 Blocker 级前置全部指向引擎级工程：

1. **声明载体缺失（B1）**：core `StreamModel`（`buildPartitionedPlan` 作用域唯一可达的模型载体）只含 StreamComponents + transformMap，**无任何边声明**；四属性仅存在于 flow 模块 `StreamEdgeModel`（仅 builder 可见）。接线需先发明 builder→core 的边配置传递通道（env 侧注册表或 Transformation 打标）——均为未授权的新公共面。
2. **id 空间断裂（B2）**：EdgePlan/JobEdge 键是 `vertex-<id>`（`JobGraphGenerator:448` 取 `firstNode.getId()`——审计订正：Transformation.getId() 是进程级 AtomicInteger 计数器，非 stable hash；跨进程非确定，映射不可重建的结论更强）；模型边 from/to 是 transform id。两空间无既有映射（vertex id 实为 `vertex-<AtomicInteger 计数>`——`JobGraphGenerator:448` 取 `firstNode.getId()`，进程级计数器跨进程非确定，比可逆映射更不可重建），跨空间映射需侵入图生成核心记录链化前的 transformation→vertex 对应。
3. **链化吞边（B3）**：`JobGraphGenerator.identifyChains` 把等并行度+forward 的边融合进单一 JobVertex——XDSL 最常见的线性 pipeline（并行度 1）**所有模型边都是链内边**，零 EdgePlan/JobEdge 载体。对链内边「放行」= 声明被静默丢弃（P1-XDSL-5 与本 plan 无静默跳过门禁止）；禁链改拓扑超范围。
4. **消费端不完备（M1）**：`receiveWindow`/`packetSize` **全仓零行为消费点**（仅一致性比较与错误摘要引用）；remote 模式容量硬编码。接线后它们从「诚实的 typed reject」变「已接受、无行为」——恰是 Anti-Hollow 禁止的空壳。
5. **附加缺陷（M2/M3/M4）**：EdgeConfig 构造器对 int 不兜底（部分声明携 0 会炸多输入一致性门 `sameGateConfig`）；lint 退役面实际涉及 3 文件（rule.yml 头部明文要求同步退役 + fixture + Linter 测试）；「照抄背压先例」指向 remote/DB 门而本地真实观察面是 ResultPartition 有界队列阻塞且前提是声明边为跨 vertex 边。

**裁定**：四属性**维持 typed 拒绝**（现状即诚实行为——声明无载体、两属性无消费）；登记**引擎级 successor 条款**（接线三前置：边声明载体通路、模型 id→vertex id 映射、链化边语义裁定；两属性消费端补齐）；拒绝面回归钉保持（WI27 释放面钉指向该拒绝面）。这与 WI13 的 Java-only 裁定同构（裁定+记录+拒绝面保持），区别仅在理由：WI13 是授权缺，WI14 是引擎级工程前置缺 + 消费端不完备。

roadmap WI14 行注记裁定（改写为裁定推迟 + successor 条款）——roadmap 行内裁定注记有 v3 先例（WI5 行半边由 D10 构成、E3 行状态注记等）。

## Current Baseline

live 核实（2026-10-10，HEAD cb3ca343e3；审查 agent 逐条实地查证）：

- 声明面恒拒：`firstDeclaredFlowControlAttr`（builder 实际 :1622-1636，validateEdgeDeclarations :1437-1489）四属性任一非空 → `ERR_STREAM_EDGE_ATTR_UNSUPPORTED`；lint 镜像（`StreamModelLinter.scanEdge:250-267`）+ nop-lint `nop-xdsl-undeclared-consumer.rule.yml:60-68`（头部明文 release 需同步退役）+ fixture `unconsumed-faces.stream.xml:17`（queueCapacity 载体）+ `TestStreamModelLinter` queueCapacity 模型 + `TestStreamModelEdgeContract` 参数化四钉。
- 消费端现实：`EdgeConfig`（默认 BLOCKING_QUEUE/1024/1024/4096）→ `EdgeAssembly.resolveEdgeConfig`（JobEdge 优先 → DeploymentPlan map）→ `GraphExecutionPlan.resolvePartitionCapacity`（**queueCapacity→ResultPartition 有界队列，真背压**）+ `RecordWriter.validateFlowControlPolicy`（护栏）；`receiveWindow`/`packetSize` 零行为消费点；remote 容量硬编码。
- 两 provider 硬编码 defaultConfig：core `DefaultDeploymentPlanProvider:45-49`、runtime `DeploymentPlanGenerator:83-89`；`PartitionedPlan.EdgePlan` 仅三字段；生产构造点仅 `PartitionedPlanGenerator:60` 与 `JobCoordinatorMain:391`（加性槽本身兼容安全——但载体/映射/链化三前置不可绕过）。
- 零声明路径行为：无 EdgePlan 槽时 provider 仍走 defaultConfig——现状拒绝面不影响任何既有行为。

## Goals

- 裁定记录三处落地：builder/lint 拒绝消息改写（拒绝理由从 "no consumer (roadmap WI14 release face)" 改为裁定措辞：接线三前置 + 两属性消费端缺 + successor 指针）；user guide 裁定段；roadmap WI14 行注记（header v13）。
- 拒绝面回归钉保持（合同账 + WI27 释放面钉 successor=本拒绝面）。
- 交付语映射：roadmap「背压行为测试」以「拒绝面回归钉（四属性声明即拒、消息含裁定指针）+ 既有消费端行为测试引用（ResultPartition 背压既有覆盖）」替代——无接线即无新背压行为可测。

## Non-Goals

- 不动 core 执行计划面（EdgePlan/PartitionedPlanGenerator/JobGraphGenerator/provider）。
- 不为 receiveWindow/packetSize 补消费端。
- 不改链化语义。

## Scope

### In Scope

- builder `firstDeclaredFlowControlAttr` 拒绝消息改写（裁定 + successor 指针；错误码保持 `ERR_STREAM_EDGE_ATTR_UNSUPPORTED`——合同账）。
- lint 三处同步：`StreamModelLinter.scanEdge` 文本、nop-lint rule.yml 尾注、fixture/expect 核对（拒绝面保持，消息 token 断言核对）。
- user guide 裁定段 + 矩阵核对；roadmap WI14 行裁定注记（header v13）。
- 钉子核对：`TestStreamModelEdgeContract` 参数化四钉保持（token 若变则更新断言）。

### Out Of Scope

- core 执行计划面一切改动；消费端补齐；链化语义；测试拓扑构造。

## Execution Plan

### Phase 1 - 裁定落地（单 Phase）

Status: completed
Targets: `StreamModelDslBuilder.java`、`StreamModelLinter.java`、nop-lint rule.yml/fixture 核对、`TestStreamModelEdgeContract.java`、user guide、roadmap、本 plan

- Item Types: `Decision` + `Follow-up`（文档）+ `Proof`

- [x] builder 拒绝消息改写（错误码保持；detail=三前置 + 两属性消费缺 + successor 指针）
- [x] lint 三处同步（scanEdge 文本；rule.yml 尾注；fixture expect 的 messageContains token 核对）
- [x] 钉子核对：TestStreamModelEdgeContract / TestStreamModelLinter 的 token 断言保持或更新
- [x] flow 全量回归 + runtime 全量回归（全量绿；如有失败按 guide 分诊，禁止新增豁免）
- [x] user guide 裁定段 + roadmap WI14 行裁定注记（header v13）+ 交付语映射 + 三工具
- [x] 独立子 agent closure audit + 单次提交——APPROVE（agent_2dff38c9：A-G 全 PASS；条件=B2 描述订正已同批完成）

Exit Criteria:

- [x] 裁定落地且双侧（builder/lint/nop-lint）一致
- [x] 合同账不破（错误码/token 核对）+ 全量回归绿
- [x] audit APPROVE，evidence 写入 Closure 段
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] Phase 1 completed
- [x] 裁定表与 live 一致（双侧消息 + 错误码 + nop-lint 套件绿）
- [x] 无 in-scope live defect 被降级
- [x] owner docs 同步
- [x] 独立子 agent closure audit APPROVE，evidence 写入 Closure 段
- [x] **Anti-Hollow Check**：不适用新组件（纯裁定+消息改写），核对为「无双面不一致 + 无静默放行」
- [x] `./mvnw test`（涉及模块）全绿
- [x] 三个工具退出码 0

## Deferred But Adjudicated

### 边级流控四属性声明面接线

- Classification: `out-of-scope improvement`（引擎级 successor）
- Why Not Blocking Closure: 接线三前置（边声明载体通路/模型 id→vertex id 映射/链化边语义裁定）+ 两属性消费端补齐均为 core 执行计划面引擎级工程，超出本 roadmap 声明面范畴；现状 typed 拒绝是诚实行为（声明无载体即拒），不构成功能缺失或静默降级。
- Successor Required: `yes`
- Successor Path: roadmap 级引擎工程（执行计划边配置通路），登记于 roadmap WI14 行裁定注记。

## Non-Blocking Follow-ups

（暂无）

## Closure

Status Note: 边级流控四属性裁定（维持拒绝+引擎级 successor）双侧落地并有独立审计背书：裁定依据经审计亲证（B1 core StreamModel 无边声明字段；B3 链化融合等并行度 forward 边——线性拓扑全为链内边零载体；M1 receiveWindow/packetSize 零行为消费点），B2 机制描述经审计订正（vertex id 为 AtomicInteger 计数非 stable hash——结论更强，裁定不变，四处文档同批订正）。拒绝面消息三处（builder/linter/nop-lint rule.yml）一致改写，错误码与属性名 token 合同账不破（EdgeContract 11 + Linter 11 + NopRuleSuites 73 + ProductionRuleCount 1 亲跑全绿，flow 284 回归绿）。Deferred 分类诚实（out-of-scope improvement + successor，非缺陷降级——现状拒绝是 by-design 合同）。
Completed: 2026-10-10
Completed: （待填）

Closure Audit Evidence:

- Reviewer / Agent: agent_2dff38c9（独立 fresh-session closure auditor）
- Audit Session: agent_2dff38c9-9336-4d10-aed3-6ddb7b058df3
- Evidence:
  - 裁定双侧：builder :1482-1498（detail 三前置+successor、错误码/属性名保持）、linter :250-267 四条、rule.yml :60-68 属性名 token 保持
  - 钉子（audit 亲跑）：EdgeContract 11/0、Linter 11/0、NopRuleSuites 73/0、ProductionRuleCount 1/0
  - 依据亲证：B1（core StreamModel 仅 Components+transformMap）、B3（canChain :343-403 等并行度+forward+单入单出）、M1（getReceiveWindow/getPacketSize 仅一致性比较引用）
  - B2 订正：:448 实为 firstNode.getId()（AtomicInteger 计数）——四处文档同批订正（plan/log/roadmap/user guide）
  - 文档/工具：user guide :178 裁定段+:281 矩阵行、roadmap v13+:142 裁定推迟注记、doc-links 0、checklist 0（回填后终验）、scan-hollow 0 findings
  - Deferred 分类检查：PASS（out-of-scope improvement + successor；现状拒绝是 by-design 合同非缺陷降级）
- `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2360-wi14-edge-flow-control.md --strict` 退出码 0（Closure Evidence 回填后复跑确认）

Follow-up:

- no remaining plan-owned work（引擎级 successor 已登记 roadmap WI14 行）
