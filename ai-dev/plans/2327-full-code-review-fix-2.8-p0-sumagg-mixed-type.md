# 2327 full-code-review 修复：StreamSqlAggregation.SumAgg 混型累加（P0-2.8-001 + 连带 dim10-10）

> Plan Status: draft
> Last Reviewed: 2026-10-05
> Source: 审计报告 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/2.8-stream-sql-first-audit.md` P0 即时通道（异步注入）；roadmap 规则 #6
> Related: plan 2326（2.8 首审，P0 发现来源）；prior art：自身 roadmap WI9（同型钉子——修复须扩钉）、WI8c（参数类型 fail-fast——复合路径复用本修复）
> Item Types: `Fix`（审计确认的 live defect——复核探针三例错值一例塌缩实测，不得降级）
> Draft Review: agent_f0b8c4b8 首轮 1 Blocker（**merge 路径完全遗漏**——SumAgg.merge :252-259 经 SqlRowCompositeFunction:103 委托是 live 契约，改累加器形态不改 merge=同类静默缺陷）+ 3 Major（探针七行实为 6 行枚举、index 锚点失配、两处契约分叉未强制裁定）+ 4 Minor 全修（推荐三通道精确累加器裁定落地、设计记录落 ai-dev/design、行号勘正、静态契约不变量、序列化钉）

## Purpose

修复 StreamSqlAggregation.SumAgg 混型累加静默错值（P0-2.8-001：整数部分丢弃+类型塌缩）并连带修复 dim10-10（BigDecimal 全流截断/BigInteger 回绕——复核裁定与 P0 必须一并设计：截断发生在 add 期 longValue() 调用点，getResult 侧修复无效）。修复以「聚合数值语义不因输入类型分布改变」为验收契约。

## Current Baseline

- 缺陷机制（复核探针实测，`_tmp/probe/` 可复现）：`StreamSqlAggregation.java:207-260` SumAgg（add :226-249/getResult/merge :252-259/createAccumulator :221-223 全触面） 内部累加：遇 Integer/Long 用 long 累加、遇 Double/Float 切换 double 累加——**已累的整数部分在切 double 时被丢弃**（sum(1,2,3.5)→3.5 应 6.5）；浮点部分和恰为 0.0 时 getResult 返回 Long（类型塌缩，1.5−1.5+2→2 [Long]）。非 Double/Float 类型走 `toNumber().longValue()`（dim10-10：BigDecimal 1.5+2.25→3 逐条截断、BigInteger 2^70→0 回绕——无需混型即触发）。
- 影响面：窗口与持续两路径共用 SumAgg（SqlRowCompositeFunction.buildComposite 复用）；记录载体是无型 Map（schema 类型纯建议性），混型为预期输入。
- 既有覆盖缺口：TestStreamSqlAggregations:67-93 sum 仅两支同型钉+序列化单值钉——混型/BigDecimal/BigInteger 零覆盖（WI9 缺口）。
- 复合路径（dim10-02）在 resolver 提前 return 跳过类型 fail-fast——本修复只改累加器语义，复合路径类型校验（dim10-02）不在范围。
- 绿色锚：nop-stream-sql 79/79 全绿（2026-10-05 排除两个预存失败后实测；预存失败=TestBatchTaskCheckpoint flaky+TestStreamConnectorRegistryDiscovery，均与本修复无关）。

## Goals

- SumAgg 混型累加语义正确：sum(1,2,3.5)=6.5 [Double]、sum(1.5,-1.5,2)=2.0 [Double]、全 BigDecimal 流 sum(1.5,2.25)=3.75 [BigDecimal 或 double 语义明确]、BigInteger 不回绕。
- 类型契约稳定：getResult 返回类型由「是否出现过浮点/高精度输入」决定，一经提升不回落（消除 0.0 塌缩）。
- 修复不破坏同型行为（现有 1,2→3L、1.5,2.5→4.0 钉保持）。
- 补齐 WI9 缺口：混型/BigDecimal/BigInteger 回归钉。

## Non-Goals

- MinMaxAgg 混型 CCE（dim10-09，P2 独立条目——若修复机制顺带覆盖可一并，须单独验证）；复合路径参数类型校验（dim10-02）；除法等价（dim10-03）。
- Avg/Count 聚合（复核实测混型干净）。
- SQL 语义层（NULL 处理已正确——null 跳过逻辑保持）。

## Scope

### In Scope

- `nop-stream/nop-stream-sql/src/main/java/io/nop/stream/sql/eval/StreamSqlAggregation.java`（SumAgg 及其类型提升机制；如 SqlRowAggregateSpec/AggCall 需携带类型信息属最小协作面）
- `nop-stream/nop-stream-sql/src/test/.../TestStreamSqlAggregations.java`（扩钉）

### Out Of Scope

- MinMaxAgg/Avg/Count（除非顺带且单独验证）；dim10-02/03；xpl 打印面。

## Execution Plan

### Phase 1 - 修复实现

Status: planned
Targets: StreamSqlAggregation.SumAgg

- Item Types: `Fix`

- [ ] 实施前基线：本 plan 已提交、`git status` 干净、nop-stream-sql 79 测试全绿
- [ ] 设计并实现累加器类型提升：**推荐三通道精确累加器 {long, double, BigDecimal} + sawFloating/sawDecimal 双单调旗标**（对抗审查裁定：全 BigDecimal 通道打破 4 枚既有钉最危险、纯双轨把截断换成舍入/Infinity 窄化替代）；getResult 阶梯=sawDecimal→BigDecimal / else sawFloating→Double / else Long / else null；**merge 同步设计（Blocker 修复）：三槽各自相加+双旗标 OR——SumAgg.merge（:252-259）经 SqlRowCompositeFunction:103 委托是 live 契约，改累加器形态不改 merge = 留下与 P0 同类的静默缺陷**；设计权衡记录落 `ai-dev/design/nop-stream/`（既有 sql-subset-and-semantics.md owner 追加聚合类型提升契约——guide 规则 #14；_tmp 只留过程探针），**强制裁定两点契约分叉并各补回归钉：①BigDecimal/BigInteger 超 double 域（≥1e308）行为（禁止静默 Infinity）②long+BigDecimal 混流输出类型**
- [ ] dim10-10 一并修复：BigDecimal/BigInteger 输入进 BigDecimal 通道不再走 longValue() 截断
- [ ] 静态契约不变量：StreamSqlExprCompiler.aggregateResultType:173-203 与 resultTypesFollowD7StyleRules:135-141 零改动（schema DECIMAL 绑定在子集外，BigDecimal 只经无型 Map 到达——显式钉住防实现者顺手改 promote）
- [ ] Owner doc 裁定：聚合数值语义修复属行为对齐，`No owner-doc update required`（或如 service-layer/流文档有 sum 语义描述则同步）

Exit Criteria:

- [ ] 复核探针七行实测表全部输出期望值（**显式清单**——复核会话权威口径，仓内此前无完整枚举）：
      ① sum(1,2,3.5)：旧 3.5→期望 6.5；② sum(0.5,1,2)：旧 0.5→期望 3.5；③ sum(3,4,5,-6.0)：旧 -6.0→期望 6.0；④ sum(1.5,-1.0,2)：旧 0.5→期望 2.5；⑤ sum(1.5,-1.5,2)：旧 2 [Long]→期望 2.0 [Double]（塌缩消除）；⑥ sum(BigDecimal 1.5,2.25)：旧 3→期望 3.75；⑦ sum(BigInteger 2^70,…)：旧 0（回绕）→期望精确值
- [ ] 无静默跳过；null 跳过/DISTINCT fail-fast 等既有语义保持
- [ ] `ai-dev/logs/` 对应执行日 log 已追加 Phase 1 事实

### Phase 2 - 回归测试与验证

Status: planned
Targets: TestStreamSqlAggregations

- Item Types: `Fix` + `Proof`

- [ ] 新增回归钉：混型（int+double 三序）、BigDecimal 全流、BigInteger 越界、0.0 塌缩序列——断言值+类型（WI9 缺口闭合）
- [ ] merge 回归钉：混型两半聚合 merge（三槽+旗标 OR 验证）——Blocker 修复验收
- [ ] 序列化钉扩展：既有 serializationPreservesAggregation 加一条混型/BigDecimal 断言（checkpoint 通道兼容验证）
- [ ] 既有同型钉零回退
- [ ] `./mvnw test -pl nop-stream/nop-stream-sql,nop-stream/nop-stream-core,nop-stream/nop-stream-runtime -am -Dtest='!TestBatchTaskCheckpoint,!TestStreamConnectorRegistryDiscovery' -Dsurefire.failIfNoSpecifiedTests=false` 全绿（预存失败排除口径同 2.8 报告登记）

Exit Criteria:

- [ ] 新增回归 ≥6 条（混型三序+BigDecimal+BigInteger+塌缩序列）
- [ ] 既有测试零回退
- [ ] 三模块定向测试全绿（排除两个预存失败）
- [ ] `ai-dev/logs/` 对应执行日 log 已追加 Phase 2 事实

### Phase 3 - Closure audit 与收口

Status: planned
Targets: 本 plan + index + roadmap

- Item Types: `Proof`

- [ ] 独立子 agent closure audit（验证：探针七行全过、回归钉对齐 WI9 缺口、同型零回退、修复设计文档在案）
- [ ] index 回填：P0-2.8-001 行（2.8 收口时已新增为 open）状态→fixed；dim10-10 并账标注；log 收口
- [ ] 双 --strict；commit

Exit Criteria:

- [ ] closure audit PASS 证据写入本 plan Closure 段
- [ ] index「P0 发现追踪」P0-2.8-001→fixed
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`check-plan-checklist.mjs <本文件> --strict` 退出码 0

## Closure Gates

- [ ] P0-2.8-001 与 dim10-10 全部修复且语义正确（探针七行+回归钉双证据）
- [ ] 同型行为零回退；既有测试零回退
- [ ] 三模块定向测试全绿
- [ ] 独立 closure audit PASS 并留痕
- [ ] index/log 一致；双 --strict 退出码 0
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream-sql --severity high` 退出码 0
- [ ] 不存在被静默降级的 in-scope 项

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

- dim10-09 MinMaxAgg 混型 CCE（P2）独立条目——若本修复的数值通道顺带覆盖须单独验证后并账，否则留 R1.x。

## Closure

Status Note: （待 closure audit 后填写）
Completed: （待填）

Closure Audit Evidence:

- Reviewer / Agent: （待独立子 agent 填写）
- Evidence: （待填）

Follow-up:

- （待填）
