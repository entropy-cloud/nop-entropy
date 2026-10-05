# 2310 nop-stream R5-CON-01：能力矩阵钉定测试 HEAD 红灯修复

> Plan Status: completed
> Last Reviewed: 2026-10-05
> Source: ai-dev/audits/2026-10/2026-10-05-0523-deep-audit-nop-stream-quality-r5/03-connectors-security.md（CON-01，P1，HEAD 真实红灯）；summary.md 修复归属 2310
> Related: 2311-2317（R5 修复计划序列）

## Purpose

把 `TestStreamConnectorRegistryDiscovery.testCapabilityMatrixAlignedWithDesignAdjudication` 的 HEAD 红灯收敛为绿，并保持该钉定测试作为「代码级单一事实源」护栏的语义不变：它对齐的是**最新用户裁定**，而不是历史矩阵快照。

## Current Baseline

- HEAD（bd089444f5 之后、审计提交 6ab59d44e6）上 `./mvnw test -pl nop-stream/nop-stream-runtime` 中 `testCapabilityMatrixAlignedWithDesignAdjudication` 真实 FAILURE（R5 审计基线实测，`_tmp/r7-baseline-test-fn.log`）。
- 矛盾三方（03 报告 CON-01，复核确认）：
  - 测试 `nop-stream-runtime/src/test/java/io/nop/stream/runtime/connector/TestStreamConnectorRegistryDiscovery.java:132` 断言 `debezium-cdc` 为 `ConnectorParallelism.SINGLE_INSTANCE`；
  - 工厂 `DebeziumCdcSourceConnectorFactory` 自 1dad44a0c6（2026-10-02，WI4）起声明 `PARALLEL`（B 层确定性表路由）；
  - owner doc `docs-for-ai/03-modules/nop-stream-connectors.md:29` 明言 PARALLEL 为「2026-10-02 用户裁定」。
- git 考古：工厂翻转提交（1dad44a0c6）验证声明只跑了 core+connector 测试面，未含 runtime 宿主；后续 05e81257e6 维护该测试时漏改此行。
- 测试的真实意图（测试注释 §8.4 alignment record）是钉定「能力矩阵与设计裁定一致」，即裁定更新时测试必须跟随更新，而不是冻结旧值。

## Goals

- `debezium-cdc` 的矩阵断言更新为 `PARALLEL`，与 2026-10-02 裁定及工厂声明一致；runtime 模块测试全绿。
- 全仓无其他残留的 `debezium-cdc=SINGLE_INSTANCE` 断言（钉定面收敛）。

## Non-Goals

- 不改工厂/描述符任何生产行为（PARALLEL 是既定裁定）。
- 不处理 connectors 文档的其他漂移（DC-3/DC-6 等，归 2317）。
- 不处理 CON-06（batch-loader 描述符 PARALLEL 与运行期单写入门禁的三方不一致——那是另一个独立裁定问题，本计划只修 debezium-cdc 红灯）。

## Scope

### In Scope

- `TestStreamConnectorRegistryDiscovery` 的 `debezium-cdc` 断言行及注释。
- 全仓 grep 确认无其他 `SINGLE_INSTANCE` 钉定残留指向 debezium-cdc。

### Out Of Scope

- 生产品代码、其余文档、其余连接器矩阵值。

## Execution Plan

### Phase 1 - 钉定测试对齐裁定

Status: completed
Targets: `nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/connector/TestStreamConnectorRegistryDiscovery.java`

- Item Types: `Fix`

- [x] 将 `debezium-cdc` 断言从 `ConnectorParallelism.SINGLE_INSTANCE` 改为 `ConnectorParallelism.PARALLEL`，并在断言处注释登记裁定来源（2026-10-02 用户裁定 + WI4 B 层表路由，owner doc :29）
- [x] 全仓 grep `SINGLE_INSTANCE` 确认 debezium-cdc 相关钉定无其他残留（其他连接器的 SINGLE_INSTANCE 断言如与工厂一致则不动）
- [x] 在 `docs-for-ai/03-modules/nop-stream-connectors.md` 的能力矩阵节追加一条维护规则：**描述符能力值翻转的提交必须同步更新能力矩阵钉定测试**（防复发，审查发现 #1）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `./mvnw test -pl nop-stream/nop-stream-runtime` 全绿（含 `testCapabilityMatrixAlignedWithDesignAdjudication`；前置：上游 SNAPSHOT 已安装，必要时先 `-am` 构建上游）
- [x] grep 证据：`SINGLE_INSTANCE` 在 nop-stream 测试面仅剩与工厂声明一致的断言
- [x] connectors.md 维护规则已追加（防复发）
- [x] No new test required：本计划修改既有钉定测试断言，无新增功能面
- [x] No owner-doc update required（owner doc 已是 PARALLEL，本计划使其守护测试与之一致；connectors.md 仅追加维护规则行）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] in-scope confirmed live defect（CON-01）已修复：HEAD 红灯测试转绿
- [x] 必要 focused verification 已完成（runtime 模块全量测试）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect
- [x] No owner-doc update required（理由如上）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：本计划为纯测试断言对齐，无新增组件/调用链；护栏语义（矩阵↔裁定一致）经测试自身继续生效
- [x] `./mvnw test -pl nop-stream/nop-stream-runtime` 通过
- [x] checkstyle / 代码规范检查通过（无新增生产代码，import 无变化）
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2310-nop-stream-con01-capability-matrix-red.md --strict` 退出码为 0

## Deferred But Adjudicated

（无——本计划单结果面，无延期项）

## Non-Blocking Follow-ups

- CON-06（batch-loader 声明面离群）归 2315 连接器计划统一裁定。

## Closure

Status Note: HEAD 红灯（R5-CON-01）已收敛：钉定断言对齐 2026-10-02 PARALLEL 裁定，runtime 模块 1233 tests 全绿，防复发维护规则落入 connectors.md，钉定面 grep 干净。独立 closure audit APPROVE。
Completed: 2026-10-05

Closure Audit Evidence:

- Reviewer / Agent: 独立 fresh-session closure auditor（general-purpose 子代理，2026-10-05）
- Evidence:
  - Exit Criterion #1 PASS：TestStreamConnectorRegistryDiscovery.java:130-136 PARALLEL + 裁定来源注释，git diff 仅此一处
  - Exit Criterion #2 PASS：nop-stream src 仅剩枚举定义与无关注释两处 SINGLE_INSTANCE（target/ 陈旧产物不计）
  - Exit Criterion #3 PASS：_tmp/plan2310-runtime-full.log——Tests run: 1233, Failures: 0, BUILD SUCCESS
  - Exit Criterion #4/#5 PASS：No new test required / No owner-doc update required（owner doc 本就 PARALLEL）
  - Exit Criterion #6 PASS：ai-dev/logs/2026/10-05.md 已创建含 2310 条目（审计唯一条件，已满足）
  - Closure Gates 全部 PASS；Anti-Hollow：钉定测试经真实 IoC 装配 5 个 connector beans.xml 走 live 描述符，8/8 运行 0 skip，断言数量未减
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2310-nop-stream-con01-capability-matrix-red.md --strict` 退出码 0
  - `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream-runtime --severity high` 退出码 0
  - Deferred 分类检查：CON-06→2315 为 out-of-scope 声明面离群（修复=生产行为变更），非 in-scope 降级

Follow-up:

- CON-06 → 2315
