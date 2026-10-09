# 2358 WI12 — 五注册表族逐族处置

> Plan Status: completed
> Last Reviewed: 2026-10-10
> Source: `ai-dev/backlog/nop-stream-xdsl-full-coverage-roadmap.md` WI12（v10）
> Related: plan 2357（WI11 同族放行先例）、multi-engine WI18（Avro format，已闭环）、plan 2346（WI0 授权面）

## Purpose

把 roadmap WI12 收口到：`<streams>`/`<environments>`/`<requirements>`/`<checkpointParticipants>`/`<coders>` 五注册表族逐族完成「接线 or 移除 schema 面 or 显式记为 Java-only」三选一裁定并落地，交付逐族裁定表 + 落地改动 + schema 面与消费方一致性回归。

## Current Baseline

live 核实（2026-10-10，HEAD fbe4edded0；审查 agent 实地复核）：

- **现状恒拒**：`failFastOnUnsupportedRegistries`（`StreamModelDslBuilder.java:451-491`）对五族非空声明全部 `ERR_STREAM_NOT_IMPLEMENTED`；`StreamModelLinter.scanRegistries:270-298` 五族 lint warning 分支俱全（**放行面必须双侧同步**）。
- **WI1 合同账钉**：`TestStreamModelDslBuilderFailFast` 五族各一钉 + 共享 helper；`TestStreamErrorCodeContract.notYetImplementedRegistryCarriesErrorCode:52` 用 `model.setStreams(...)` 作 NOT_IMPLEMENTED 载体；nop-lint 套件以 coders 为 invalid 载体（只看 valid/invalid 分类）。
- **引擎侧消费事实**（裁定依据，审查复核后表述）：
  - `DeploymentMode`（LOCAL/DISTRIBUTED）：`env.setDeploymentMode`（`StreamExecutionEnvironment.java:263-269`，null 拒绝）；**消费有条件**——`execute()` 中 checkpoint 引擎路径（:406-408）**先于** DISTRIBUTED 分支（:411-419），checkpoint 引擎（`GraphModelCheckpointExecutor`）不读 deploymentMode：**声明 `<checkpoint>`（xdef 默认 enabled=true）+ DISTRIBUTED 环境 → deploymentMode 静默惰性**。无 dispatcher 的 DISTRIBUTED execute 走 :415-419 typed reject。
  - `StreamRequirement`：引擎聚合在**图生成期**（`detectRequirements` 由 `populateStreamModel` 于 execute/buildJobGraph 内调用，builder 期不可达）；2PC 判定基准=SinkFunction bean `instanceof TwoPhaseCommitSinkFunction`（`StreamGraphGenerator:196`），而 builder `buildSink:1598` 已解析**同一 bean 实例**——构建期可判定且语义同源。`StreamComponents` 仅在 execute/buildJobGraph 内构造，builder 无句柄（declareRegistry 落 declarativeRegistries 独立 map，不进 requirements）。
  - `CheckpointParticipant`：参与性由**函数 bean 是否实现 CheckpointParticipant** 决定（RunningTask:315-321 operator/UDF instanceof；检测点全集中 xpl-fn sink 的 `XplSinkFunction` 恒不参与）——与 DSL transform 类型无映射，**类型表方案不可用**；bean 实例可经 `beanResolver().resolve(beanAttr, ...)` 在构建期拿到。
  - `<coders>`：无 DSL 消费点；Avro format（multi-engine WI18）走连接器 wire-format 层不经过 coders。
  - `<streams>`：唯一注释语义「命名流跨算子引用」，无任何消费属性。
- **streams 移除的既有破坏面（审查盘点，6 处必改）**：`TestStreamModelDslBuilderFailFast.streamsRegistryFailsFast:86-88`、`TestStreamErrorCodeContract.notYetImplementedRegistryCarriesErrorCode:52`（编译失败——setStreams 消失）、`TestStreamModelDeltaFailFast.deltaAddingStreamsRegistryStillFailsFast:46-61` + fixture `test-delta-failfast-extends.stream.xml`、`TestXdslCorpusLintGate:108` R12 存量行（双向门：streams 载体消失后该行更新为现存 fail-fast 载体（`<onStart>` 族或 coders），非删除——门对 UNWHITELISTED/STALE 双向翻红）、`TestStreamModelLinter:89/:109/:126` streams lint 钉。`TestStreamFlowAuditFixes` 不受影响。

## 逐族裁定表（审查后定稿）

| 族 | 裁定 | 落地语义 |
|---|---|---|
| `<streams>` | **移除 schema 面** | xdef 删块 + `StreamDefinitionModel` 删除 + `_gen` 再生成 + fail-fast/lint 分支删 + 上述 6 处既有钉子/fixture/corpus 按新契约改写（Exit Criteria 明确为「清单内钉子按裁定改写后全绿」而非「零改动」）；移除族的正钉只有「parse 层 xdef 拒绝」单一负钉 |
| `<environments>` | **接线（不加新 schema face）** | deploymentMode → `env.setDeploymentMode`（null 守卫：属性缺省=保持 LOCAL 默认）；**恰一环境直接应用；多环境（>1）无选择机制 → typed reject**（消息提示收敛为单环境档；不加 activeEnvironment 根属性，零授权扩张）；零环境=现行为。**DISTRIBUTED + 活动 checkpoint 组合 typed reject**（谓词镜像 `enableCheckpointing` 触发条件：`enabled && interval>0`——`enabled="false"` 块 + DISTRIBUTED 合法且 mode 生效；当前引擎 checkpoint 路径先于 DISTRIBUTED 分支且不读 deploymentMode，活动组合下声明惰性；重启触发=checkpoint 引擎支持分布式模式） |
| `<requirements>` | **接线（仅可静态判定子集）** | 声明面只接受可校验需求：`TWO_PHASE_COMMIT_SINK` 声明 ↔ buildSink 解析的 sink bean `instanceof TwoPhaseCommitSinkFunction`（与引擎 `detectRequirements` 同源同实例）双向一致性校验（声明了但图内无 2PC sink → 声明失真 reject；图内有但未声明 → 不拒绝（声明是需求非普查））；`DISTRIBUTED_EXECUTION` 恒真（引擎恒聚合）放行；**其余枚举值 typed reject**（无静态判定器，声明面不接受——无静默跳过）。校验时机=buildTransforms 之后（sink bean 已解析）。不改 StreamComponents（builder 无句柄，引擎逻辑不动） |
| `<checkpointParticipants>` | **接线（拼写 + bean 级实校验）** | 参与性=函数 bean `instanceof CheckpointParticipant`（引擎判定集），与 DSL transform 类型无映射——类型表不可用。落地：participant id ∈ transforms（REF_UNKNOWN 防拼写）+ 目标须为**函数携带型 transform**（source/sink/map/filter/flatMap/process/custom/aggregate/reduce——从 resolveFunctionOrXpl 调用点导出；keyBy/union/sideOutput/window/cep/timestampsAndWatermarks 等无函数消费语义型 typed reject）+ 函数携带型中 **xpl 形态 typed reject**（`XplSinkFunction` 等恒不参与——语法可检测的静默失真禁令）+ **bean 形态** resolve 后 `instanceof CheckpointParticipant` 实校验（构建期，与 Supplier 先例同构；resolve 异常经 resolveBean :1668-1677 锚定到声明 transform location，错误归属已核） |
| `<coders>` | **显式记为 Java-only** | xdef 保留 + fail-fast **保留**（错误码保持 `ERR_STREAM_NOT_IMPLEMENTED` 不变——WI1 合同账/nop-lint invalid 载体依赖此码；「DSL 面未实现」语义准确）+ builder 消息与 lint 文本同步改写（"coders is a Java-only surface: wire formats attach at the connector layer; the declarative registry has no consumer"）+ 文档裁定记录（重启触发=wire format DSL 接入诉求出现） |

## Goals

- 五族裁定全部落地：三个接线族有构建期校验与真实消费语义；移除族 schema 面删除且破坏面清单内钉子按新契约改写；Java-only 族消息与文档明确且合同账不破。
- 逐族裁定表落 user guide（含每族依据与重启触发条件）。
- 一致性回归：接线面各有 fixture 钉子（合法 + 非法）；破坏面清单外既有测试零回归。

## Non-Goals

- 不加 `activeEnvironment` 或任何新 schema 属性（多环境选择等真实诉求出现再议）。
- 不为 `<coders>` 发明 DSL 消费语义；不改 coders 错误码。
- 不改引擎侧逻辑（detectRequirements/RunningTask/StreamComponents 构造时机全部不动）。
- 不触碰 `<schemas>`/`<windowingStrategies>` 等已放行族。

## Scope

### In Scope

- xdef：`<streams>` 块删除。
- `StreamModelDslBuilder`：fail-fast 分支改写（environments/requirements/checkpointParticipants 接线校验；coders 消息改写）；三族校验方法（requirements/participants 的 bean 级校验挂 buildTransforms 之后）。
- `StreamModelLinter`：三接线族 warning 分支删 + coders 文本同步。
- `StreamDefinitionModel` 删除 + `_gen` 再生成。
- 破坏面 6 处既有测试/fixture/corpus 改写。
- 新增五族钉子测试；文档裁定表段 + source-anchors 核对。

### Out Of Scope

- 引擎侧逻辑改动；coders wire-format DSL 面；多环境运行时切换。

## Execution Plan

### Phase 1 - 裁定落地（五族）+ 放行验证测试

Status: completed
Targets: `stream.xdef`、`StreamModelDslBuilder.java`、`StreamModelLinter.java`、`StreamDefinitionModel.java`（删）、`TestStreamModelDslBuilderFailFast.java`、`TestStreamErrorCodeContract.java`、`TestStreamModelDeltaFailFast.java` + fixture、`TestXdslCorpusLintGate.java`、`TestStreamModelLinter.java`、新测试类 `TestWi12RegistryFamilies.java`

- Item Types: `Feature` + `Decision` + `Fix`

- [x] `<streams>` 移除：xdef 删块、模型类+`_gen/_StreamDefinitionModel` 删、fail-fast/lint 分支删、两步再生成（漂移恰 2 文件：`_StreamModel.java` + 手删 `_StreamDefinitionModel.java`）、6 处破坏面按新契约改写（FailFast 钉改 parse 层拒绝；ErrorCodeContract 载体换 coders；DeltaFailFast 载体换仍 fail-fast 的族；CorpusLintGate R12 行更新为现存载体；Linter 两处 streams 钉换面）
- [x] `<environments>` 接线：恰一直接应用（deploymentMode null 守卫=保持 LOCAL 默认）；多环境 typed reject；DISTRIBUTED + checkpoint 声明组合 typed reject（detail 记录路径优先事实与重启条件）
- [x] `<requirements>` 接线：TWO_PHASE_COMMIT_SINK ↔ resolvedSinkBeans（buildSink 记录）instanceof 双向一致性（buildTransforms 后）；DISTRIBUTED_EXECUTION 放行；其余枚举值 typed reject
- [x] `<checkpointParticipants>` 接线：id ∈ transforms + 函数携带型谓词（isParticipantCapable 九类型）+ xpl 形态 typed reject + bean 形态 resolve 后 `instanceof CheckpointParticipant`
- [x] `<coders>` Java-only：builder 消息 + lint 文本改写（Java-only surface 措辞）；错误码保持 NOT_IMPLEMENTED
- [x] 五族钉子测试：environments（合法 LOCAL/合法 DISTRIBUTED with fake dispatcher 经 preBuildEnvironmentConfigurer 或无 dispatcher execute 拒绝钉/多环境/活动组合拒绝/enabled=false+DISTRIBUTED 合法钉）、requirements（2PC 声明一致/声明失真/未声明不拒/DISTRIBUTED 放行/其余值拒绝）、participants（拼写/bean 参与合法/xpl 形态/bean 不参与）、streams（parse 层拒绝）、coders（消息改写后仍 NOT_IMPLEMENTED）
- [x] owner-doc 裁定：裁定表与文档更新集中 Phase 2，本 Phase 无独立 owner-doc 更新

Exit Criteria:

- [x] 裁定表五族全部落地且与 live 行为一致（TestWi12RegistryFamilies 14 用例全绿）
- [x] **接线验证**：environments 的 deploymentMode 真实到达 env（getDeploymentMode 状态断言）；requirements 2PC 一致性双向（合法+失真+未声明不拒三钉）；participants 的 bean instanceof 校验双向（合法+不实现拒绝）
- [x] **无静默跳过**：多环境/DISTRIBUTED×活动 checkpoint/失真需求/其余枚举值/xpl 参与者/无函数型目标/不实现 bean 全部 typed reject
- [x] 破坏面 6 处改写后全绿 + 清单外既有测试零回归（FailFast 7 + ErrorCodeContract 4 + DeltaFailFast 1 + CorpusLintGate 1 + Linter 11）
- [x] `./mvnw test -pl nop-stream/nop-stream-flow -am` 全绿（全量 284 绿）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 回归与文档

Status: completed
Targets: runtime 回归、`docs-for-ai/03-modules/nop-stream-user-guide.md`、`docs-for-ai/04-reference/source-anchors.md`、roadmap、本 plan

- Item Types: `Proof` + `Follow-up`

- [x] runtime 全量回归（1352/0 绿，零豁免触发）
- [x] user guide 裁定表段（五族依据 + 重启触发条件）+ source-anchors.md STRM-002 描述去 streams（矩阵面：streams 无独立行，五族裁定集中专段）
- [x] roadmap WI12 勾选（日期/plan 号/audit agent），header 升 v11
- [x] `check-doc-links --strict`（0 errors）/ `check-plan-checklist --strict` / `scan-hollow`（直接路径形式）退出码 0
- [x] 独立子 agent closure audit（fresh session）——**APPROVE**（agent_16f976c3：A1-A4/B1-B4/C/D/E/F/G 全 PASS；亲跑定向 38/0、flow 284/0、runtime 抽查 1/0）
- [x] 单次提交

Exit Criteria:

- [x] audit APPROVE，evidence 写入 Closure 段
- [x] roadmap WI12 已勾选且与 plan/日志一致

## Closure Gates

- [x] Phase 1/2 全部 completed
- [x] 五族裁定表与 live 一致
- [x] 破坏面清单内改写完成、清单外零回归
- [x] 无 in-scope live defect 被降级
- [x] owner docs 同步（user guide + 矩阵 + source-anchors + roadmap）
- [x] 独立子 agent closure audit APPROVE，evidence 写入 Closure 段
- [x] **Anti-Hollow Check**：调用链连通 + 无空壳/静默跳过
- [x] `./mvnw test`（涉及模块）全绿
- [x] 三个工具退出码 0

## Deferred But Adjudicated

（暂无）

## Non-Blocking Follow-ups

（暂无）

## Closure

Status Note: 五族裁定全链路落地并有独立审计背书：`<streams>` 移除（schema/模型/双面分支删除 + 6 处破坏面按新契约改写 + parse 层拒绝钉）；三接线族（environments 状态面断言 + 组合惰性拒；requirements bean 同源双向一致性——resolvedSinkBeans 与引擎 detectRequirements 同实例；participants 拼写+谓词+bean instanceof 三层校验）；`<coders>` Java-only 消息双侧同步且合同账码保持。独立 closure audit（agent_16f976c3，fresh session）A-G 全 PASS/APPROVE：定向 38/0、flow 284/0、runtime 抽查 1/0 亲跑；scan-hollow 0 findings；doc-links 0。执行期事实修正（实测依据并记录）：xdef 删除 registry 后 parse 层 undefined-child-node 先行（移除族正钉=parse 层负钉）；NopException 断言走 errorCode/toString（getMessage 空串）；测试桩按 TwoPhaseCommitSinkFunction/CheckpointParticipant 真实抽象方法重写。
Completed: 2026-10-10

Closure Audit Evidence:

- Reviewer / Agent: agent_16f976c3（独立 fresh-session closure auditor）
- Audit Session: agent_16f976c3-7ce5-4fef-8b56-bc2633076d4b
- Evidence:
  - 移除面：A1-A4 PASS（xdef :136 注记、双模型删、builder :657-690/linter :270-292 无残留分支、parse 层钉 + 6 处破坏面逐一核对含 nop-lint invalid fixture 载体核对）
  - 接线面：B1-B4 PASS（validateEnvironments :275-322 谓词镜像 applyCheckpointConfig:602/execute():406-419 实测；validateDeclaredRequirements :330-365 与 detectRequirements:166/:196 同源；validateCheckpointParticipants :385-446 与运行时同谓词；coders :683-687/:284-286 双侧同步）
  - 测试（audit 亲跑）：定向 38/0（14+7+4+1+1+11 与 plan 逐一吻合）、flow 全量 284/0、TestDimLookupPipelineE2E 1/0
  - Anti-Hollow：三链代码追踪连通 + 全部非法形态 typed reject 有钉 + 双面无假阳性 + scan-hollow 0
  - 文档/工具：user guide :178、roadmap v11 + WI12 [x] + 历史行完整、source-anchors STRM-002、doc-links 0、checklist exit 0（Closure 回填后终验）
  - Deferred 分类检查：G PASS（暂无 deferred；R2 三项 execution-minor 全部折入实现实证）
- `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2358-wi12-registry-families-adjudication.md --strict` 退出码 0（Closure Evidence 回填后复跑确认）

Follow-up:

- no remaining plan-owned work
