# 2361 WI15 — 端点属性消费面处置

> Plan Status: completed
> Last Reviewed: 2026-10-10
> Source: `ai-dev/backlog/nop-stream-xdsl-full-coverage-roadmap.md` WI15（v13）
> Related: plan 2358（WI12 requirements 同源校验先例）、plan 2360（WI14 裁定推迟先例）

## Purpose

把 roadmap WI15 收口到：source/sink 的 `consistencyCapability`/`maxParallelism`/`params`/`outputType`·`inputType` 与 `<custom>` 的 `params`/`<source>` 体逐项完成「接线 or 维持拒绝（裁定措辞）」处置，交付能力一致性断言。

## Current Baseline

live 核实（2026-10-10，HEAD 576221b54f）：

- **恒拒现状**：`failFastOnUnsupportedSourceConfig`（builder :1728-1756：params/maxParallelism/outputType/consistencyCapability 四钉）与 `failFastOnUnsupportedSinkConfig`（:1843-1871 镜像）；`<custom>` params/source 双钉（`AdvancedTransforms.buildCustom:1021-1046`，R5-AR-02）。合同账：`TestStreamFlowAuditFixes` 8 钉。
- **consistencyCapability 引擎消费链（接线依据）**：bean 实例 `SourceFunction.getSourceConsistency()`（默认 BEST_EFFORT）/`SinkFunction.getSinkConsistency()`（默认 AT_LEAST_ONCE）→ `StreamModel.getSourceCapabilities()/getSinkCapabilities()`（transformations 遍历提取，core :103-131）→ `execute()` 的 `StreamRequirementValidator.validateConnectorConsistency(checkpointConfig.getProcessingGuarantee(), sourceCapabilities, sinkCapabilities)`。声明面同源校验可行：buildSource/buildSink 已解析 bean 实例（`resolvedSinkBeans` WI12 先例），instanceof + capability 提取与引擎同源同实例。
- **maxParallelism**：引擎消费=keyBy 的 graph 时 KR-3 冻结（keyed maxParallelism drift 校验 env :448-473）；端点级 maxParallelism 无消费点（sink xdef 默认 0=未声明）。
- **params**：bean 构造器/IoC 装配即配置通道；multi-engine WI4 参数规则化在 connector factory 层（`ConnectorParamDescriptor`），不经过 DSL params。roadmap 明示「参数规则化归外部前置 multi-engine WI4，本 WI 只做端点属性接线」。
- **outputType/inputType**：类型体系为 UnknownTypeInformation 兜底，类型声明无消费点。
- **xdef 形态**：source 有 params/maxParallelism/outputType/consistencyCapability（`SourceConsistencyCapability` 枚举）；sink 有 params/maxParallelism/inputType/consistencyCapability（`SinkConsistencyCapability` 枚举，默认 AT_LEAST_ONCE）。

## 逐属性裁定表

| 属性 | 裁定 | 落地语义 |
|---|---|---|
| source/sink `consistencyCapability` | **接线为声明一致性校验（按值豁免）** | **豁免语义=按值判定**（B1 修正）：xdef 默认 AT_LEAST_ONCE 经 parse 强制注入（`DslBeanModelParser:109-117`），模型值恒非 null——「显式 vs 缺省」不可区分，故 `声明值 == AT_LEAST_ONCE`（含注入形态）一律豁免；`声明值 != AT_LEAST_ONCE` 时与 bean 实际 `getSourceConsistency()/getSinkConsistency()` 比对，不符 → `ERR_STREAM_INVALID_ARG`（声明失真——引擎按 bean 实际参与 validateConnectorConsistency，声明不改变行为）。**保真缺口裁定（接受为 residual）**：source 侧 xdef 默认 AT_LEAST_ONCE ≠ SourceFunction 默认 BEST_EFFORT，「声明 AT_LEAST_ONCE 的 BEST_EFFORT bean」结构性豁免（最常见失真不可查）；改 xdef 默认属 schema 语义变更超本 WI。**xpl 形态端点**：wrapper 不覆写 capability 恒默认值，非默认声明必拒——消息指引「非默认 capability 请用 bean 连接器」。校验时机=buildSource/buildSink 解析 bean 后；豁免钉走 XML parse 路径 |
| `maxParallelism`（source/sink） | **维持拒绝**（消息统一裁定措辞） | 端点级无消费点（引擎 maxParallelism 消费=keyBy KR-3 冻结）；重启触发=端点级并行上限语义进入引擎 |
| `params`（source/sink/custom） | **维持拒绝**（消息统一） | bean 构造器/IoC 装配即配置通道；参数规则化归 connector factory 层（multi-engine WI4 前置） |
| `outputType`/`inputType` | **维持拒绝**（消息统一） | UnknownTypeInformation 体系下无消费点；重启触发=显式类型声明进入类型解析 |
| `<custom>` params/`<source>` 体 | **维持拒绝**（既有钉保持，消息按裁定口径核对） | R5-AR-02 既有裁定：customType bean 直收上游输入；消息已含指引 |

## Goals

- consistencyCapability 一致性校验接线（source+sink）+ 能力一致性断言（按值豁免/合法一致 build 通过/声明失真/xpl 拒绝四钉，source+sink 双面）。
- 四项维持拒绝的钉子保持（token 核对；消息改写为裁定口径后断言如受影响则更新）。
- 裁定记录落 user guide + roadmap（header v14）。

## Non-Goals

- 不做 params → connector factory 的参数规则化 DSL（multi-engine WI4 前置面）。
- 不动引擎一致性校验逻辑（validateConnectorConsistency）。
- 不改 UnknownTypeInformation 类型体系。

## Execution Plan

### Phase 1 - 处置落地（单 Phase：接线 + 拒绝面统一 + 文档 + audit）

Status: completed
Targets: `StreamModelDslBuilder.java`（buildSource/buildSink/双 fail-fast）、`AdvancedTransforms.buildCustom`（消息核对）、`TestStreamFlowAuditFixes.java`、新测试 `TestWi15EndpointAttributes.java`、user guide、roadmap、本 plan

- Item Types: `Feature` + `Decision` + `Proof`

- [x] consistencyCapability 接线：删除 failFast 双方法旧 capability 分支（拒绝任何 ≠AT_LEAST_ONCE——不删则 bean 匹配的合法声明不可达，feature 死胎）+ javadoc 改写（「not plumbed/tracked as follow-up」表述失效）；新校验=按值豁免 + bean 同源比对（不符 `ERR_STREAM_INVALID_ARG`，detail 双值）；**两条合同钉错误码迁移**：TestStreamFlowAuditFixes:136-146/:219-229 的 not-implemented → invalid-arg（token "consistencyCapability" 保留）
- [x] 四项维持拒绝的消息统一裁定口径（params→bean 装配指引；maxParallelism→端点级无消费+重启触发；outputType/inputType→类型体系；错误码按合同账保持）——**含 StreamModelLinter 平行面**（:193-248 复刻全部 endpoint 钉 + 「(roadmap WI15 release face)」标记退役 + R6 skip :163-171 文案改写为**显式裁定退役**：「endpoint capability 静态预检 retired——引擎 validateConnectorConsistency 为最终门（processingGuarantee 充分性维度），builder 新增的是声明失真维度（声明 vs bean 实际），两维度不同不互代」）；linter 无 bean 访问能力，capability 规则**删除**（WI12 streams 移除先例），TestStreamConfValidator 回归
- [x] 钉子核对：TestStreamFlowAuditFixes 8 钉（6 保持 + 2 capability 迁移）；custom 测试钉实为一条（TestAdvancedTransforms:353-375 source 钉，params 无钉——核对非幻影）
- [x] 新测试四钉：①按值豁免（XML parse 路径——AT_LEAST_ONCE 注入豁免 build 通过）②**非默认声明+bean 匹配→build 通过（合法路径钉，防退化实现的核心 Anti-Hollow 钉）**③非默认声明失真（拒绝）④xpl 形态非默认拒绝——source+sink 双面 + 拒绝面抽查
- [x] flow 全量回归（290/0）+ runtime 全量回归（1351/1352，TestE2ETwoPhaseCommitSink 白名单抖动隔离复跑 3/0 绿核实）
- [x] user guide 裁定段 + 矩阵核对 + roadmap WI15 勾选（header v14）+ 三工具（doc-links 0）
- [x] 独立子 agent closure audit + 单次提交——APPROVE（agent_a4f38f3f：1-9 全 PASS；条件 M1 sink javadoc/m2 imports/m3/m4 tidy 与回填已同批完成）

Exit Criteria:

- [x] capability 校验真实拦截面（声明失真 reject + **非默认一致 build 通过**双面数据断言——合法路径钉防「全拒绝退化实现」）
- [x] 合同账不破 + 全量回归绿
- [x] audit APPROVE，evidence 写入 Closure 段
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] Phase 1 completed
- [x] 裁定表与 live 一致
- [x] 无 in-scope live defect 被降级
- [x] owner docs 同步
- [x] 独立子 agent closure audit APPROVE，evidence 写入 Closure 段
- [x] **Anti-Hollow Check**：capability 校验调用链连通 + 无静默跳过
- [x] `./mvnw test`（涉及模块）全绿
- [x] 三个工具退出码 0

## Deferred But Adjudicated

### source 侧 capability 默认不对称保真缺口

- Classification: `watch-only residual`
- Why Not Blocking Closure: source xdef 默认（AT_LEAST_ONCE）≠ SourceFunction 默认（BEST_EFFORT），「声明 AT_LEAST_ONCE 的 BEST_EFFORT bean」结构性豁免（最常见失真不可查）——改 xdef source 默认属 schema 语义变更（影响全部既有模型的能力面），超出本 WI；按值豁免语义下不产生错误放行（AT_LEAST_ONCE 声明本身即引擎缺省语义）。
- Successor Required: `yes`
- Successor Path: schema 语义变更 WI（xdef source 默认对齐 SourceFunction.BEST_EFFORT 或引入显式 source capability 声明语义），登记 roadmap WI15 行。

## Non-Blocking Follow-ups

（暂无）

## Closure

Status Note: 端点属性处置全链路落地并有独立审计背书：consistencyCapability 声明保真度校验（按值豁免——xdef 默认 parse 注入故按值非存在性；声明 vs bean 实际不符 invalid-arg detail 双值；xpl 恒默认裁定；**合法匹配 Anti-退化钉**——变异探针证明退化实现必红）；四属性维持拒绝消息双侧裁定口径；linter 平行 capability 规则删除 + R6 静态预检显式退役（双维度不互代）；两条合同钉错误码迁移（not-implemented→invalid-arg+实际值 token）。独立 closure audit（agent_a4f38f3f，fresh session）1-9 全 PASS/APPROVE：定向 6+13+11 亲跑、flow 290/0 独立复核、runtime 1351/1352（TestE2ETwoPhaseCommitSink 白名单抖动隔离复跑 3/0 绿）；条件项（sink javadoc 镜像/悬空 import/文档对称/回填）已同批完成。保真缺口 residual 正式登记（watch-only，successor=schema 默认对齐 WI）。
Completed: 2026-10-10

Closure Audit Evidence:

- Reviewer / Agent: agent_a4f38f3f（独立 fresh-session closure auditor）
- Audit Session: agent_a4f38f3f-60af-4c9b-a246-e67369ad47b9
- Evidence:
  - 接线双侧：buildSource :1718-1730 / buildSink :1844-1856（按值豁免+detail 双值+xpl 指引）；failFast 双方法分支删除+双 javadoc WI15 revision（audit M1 条件于提交前完成）
  - linter 三处：capability 规则删除+R6 显式退役（双维度不互代）+维持拒绝消息裁定口径；"WI15 release face" 全仓零残留
  - 合同账：两钉迁移 invalid-arg+实际值 token（BEST_EFFORT/AT_LEAST_ONCE）；其余 6 钉保持；custom 双钉不动
  - 测试（audit 亲跑）：TestWi15EndpointAttributes 6/0（sinkNonDefaultMatchingDeclarationBuilds Anti-退化钉绿）、FlowAuditFixes 13/0、Linter 11/0；flow 全量 290/0 独立复核
  - 变异探针：daily log 记录（退化实现必红/恢复 probe=0 复绿）
  - 文档/工具：user guide 裁定段+矩阵拆分、roadmap v14+WI15 [x]、doc-links 0、checklist 0（回填后终验）、scan-hollow 0 findings
  - Deferred：source 保真缺口 residual 正式段（watch-only+successor 登记 roadmap WI15 行）；runtime 抖动核实记录（TestE2ETwoPhaseCommitSink 隔离复跑 3/0 绿）
- `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2361-wi15-endpoint-attributes.md --strict` 退出码 0（Closure Evidence 回填后复跑确认）

Follow-up:

- no remaining plan-owned work（保真缺口 residual successor 已登记 roadmap WI15 行）
