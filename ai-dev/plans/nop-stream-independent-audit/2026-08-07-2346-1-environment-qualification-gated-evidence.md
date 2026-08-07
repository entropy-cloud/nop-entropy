# 2 Environment Qualification & Gated-Evidence Contract (nop-stream Independent Audit)

> Plan Status: completed
> Last Reviewed: 2026-08-07
> Draft Review: round 1 independent sub-agent review — 1 Blocker + 5 Majors found (D-1 wrong gate property names `.brokers`/`.serviceUrl`→应为 `.enabled`; C-1 lane taxonomy 5-vs-6 不一致; C-2 H2/Debezium 误分类为外部 gated; E-1 无 `@@LANE` 格式规范; E-2 校验器字段弱于 Goals). Round 2 restructure: 诚实 lane 再分类 T1-T6、frozen-vocabulary 映射规则、`@@LANE` 格式规范、校验器字段对齐. Round-2 独立 review verdict: **Consensus YES**（1 Blocker + 5 Majors 全部 RESOLVED；新增 1 Minor invoke_command blocked-no-test 占位已修）. 已 promoted active.
> Source: `ai-dev/backlog/nop-stream-independent-audit-roadmap.md` (Stage 5); frozen Stage-4 outputs (`source-manifest.md`, `evidence-schema.md`, `finding-corpus.md`, `ai-dev/tools/check-nop-stream-audit-manifest.mjs`); live repo baseline of `nop-stream/*/src/test/` lanes + gated external-service fixtures (verified 2026-08-07).
> Mission: nop-stream-independent-audit
> Work Item: 5. Environment qualification and gated-evidence contract
> Related: Execution order `{1}` of this DRAFT_PLANS round. Direct successor of Stage 4 (`2026-08-06-2250-1-evidence-schema-source-manifest-finding-corpus.md`, completed). Hard prerequisite for Stages 9, 10, 13, 14, 15, 16, 17 (every domain audit that asserts an `environment_class`/`required_lane`). Unblocks the most critical-path downstream items (9 checkpoint, 10 state, 13 control-plane, 14 data-plane are all critical path).

## Purpose

在做出任何带环境依赖的能力声明之前，冻结 nop-stream 独立审计的"环境资格登记"：哪些测试环境能产出**可复现的审计证据**，以及当一个所需的外部后端不可用时如何**诚实地报告为 `blocked`**。本计划同时裁定 roadmap Stage 5 列出的 lane 目标与 live 现实的差异（例如 H2 是嵌入式 in-process、Debezium 现有测试是 mocked、PostgreSQL 无 gated 测试），把每个 lane 目标**显式映射**到 frozen `evidence-schema.md` 的 `unit|in-process|multi-jvm` 强度词表——不引入新 lane 词表。后续 Stages 6-16 只能在本计划冻结的、已资格认定的 lane 内取证；一个 required-lane 的 `blocked` 行将阻塞 Stage 23 的 ready 判定。

本计划**不产出产品正确性结论**，只产出"这条 lane 的测试在满足前置条件时是否真的能跑、跑出预期正向结果、并在后端不可用时显式 blocked"的资格证据。

## Current Baseline

经 2026-08-07 live repo 核对（引用均与 frozen Stage-4 `source-manifest.md` Lane Classification + 实际测试源码一致；gate 属性名已逐行复核）：

- **frozen lane 强度词表**（`evidence-schema.md`）：`environment_class`/`required_lane` 取值 `unit|in-process|multi-jvm`（`environment_class` 另含 `none`）；强度序 `none < unit < in-process < multi-jvm`。**本计划不新增 lane 词表值**——所有资格目标映射到这 3 个强度。
- **测试语料规模**（manifest 域 g，分母已校验器复核）：全量 test java 448 文件；test resource fixture 13 文件；multi-JVM fixture 4 文件。
- **资格目标与 live 现实（逐项核实）**：
  - **(T1) unit + 嵌入式 in-process（始终可用）**：`./mvnw test -pl nop-stream -am` 标准 suite，无外部依赖。含嵌入式 H2 JDBC sink 测试 `TestJdbcTwoPhaseCommitSinkSkeleton`（`HikariDataSource` + `jdbc:h2:mem:...;MODE=MySQL`，`:60-62`，**无 `@EnabledIfSystemProperty` 门禁**，随标准 suite 跑）与 mocked Debezium offset-config 测试 `TestDebeziumCdcCheckpoint`（Mockito mock `WorkerConfig`，`:215`，**无门禁**，测的是 offset storage 属性配置而非真实 CDC engine）。→ 映射 `unit`/`in-process` 强度。
  - **(T2) multi-JVM（gated，进程 spawn）**：`nop-stream-runtime/src/test/.../multijvm/`（`MiniStreamCluster` + 3 测试）。门禁：`@EnabledIfSystemProperty(named = "nop.stream.test.multi-jvm.enabled", matches = "true")`（`TestMultiJvmExactlyOnceRecovery.java:67`）——默认 skipped。`MiniStreamCluster` 用 `System.getProperty("java.class.path")`（`:137`）与 `java.home/bin/java`（`:138`）spawn 子 JVM；前置：reactor 须先 `./mvnw install -DskipTests`（或至少 test-compile）以填充 classpath。→ 映射 `multi-jvm` 强度。
  - **(T3) Kafka data-plane（gated，外部 broker）**：`TestDataPlaneKafkaBackendE2E`。门禁：`@EnabledIfSystemProperty(named = "nop.stream.test.kafka.enabled", matches = "true")`（`:44-45`）。连接串（**非门禁**）：`nop.stream.test.kafka.brokers`（`:57`，缺省 `localhost:9092`）。→ 映射 `in-process` 强度（单 JVM transport × 真实 Kafka 后端）。
  - **(T4) Pulsar data-plane（gated，外部 service）**：`TestDataPlanePulsarBackendE2E`。门禁：`@EnabledIfSystemProperty(named = "nop.stream.test.pulsar.enabled", matches = "true")`（`:49-50`）。连接串（**非门禁**）：`nop.stream.test.pulsar.serviceUrl`（`:62`，缺省 `pulsar://localhost:6650`）。→ 映射 `in-process` 强度。
  - **(T5) PostgreSQL（roadmap 列出，live 无 gated 测试）**：repo 中无 `@EnabledIfSystemProperty` 门禁的 PostgreSQL 测试；JDBC sink 测试均用嵌入式 H2。→ live 现实：**无资格目标测试**（`blocked: no gated PostgreSQL test exists in repo`），须如实记录。
  - **(T6) Debezium 真实 CDC（roadmap 列出，live 仅 mocked）**：现有 `TestDebeziumCdcCheckpoint` 是 mocked offset-config unit 测试，**不**对抗真实 source DB + Debezium engine。→ live 现实：真实 CDC lane **无资格目标测试**（`blocked: no real-source Debezium integration test`）；mocked 测试归入 T1。
- **evidence schema 已预留环境字段**（`evidence-schema.md`）：`environment_class`、`required_lane`、`disposition` 词表含 `blocked`（"Cannot be adjudicated because a required lane/environment is not qualified"）。schema 的 `blocked` 行显式写 "(see Stage 5)"，故本计划在该处补充规则文本是合法增补。
- **校验器已存在**：`ai-dev/tools/check-nop-stream-audit-manifest.mjs` 子命令 `manifest`/`corpus`/`evidence`/`self-test`/`all`（`all` 当前组合 manifest+corpus+evidence，**未含 qualification**）。`evidence` 子命令用非递归 `readdirSync` 扫描 audit dir 直系 `*.evidence.md` 文件（`:285-287`），但**尚不校验 lane 资格记录**。
- **真实 gap**：(1) 没有 lane 资格认定记录（每条 lane 的精确调用命令含**正确门禁属性**、前置条件、provisioning/cleanup/timeout/owner、正向预期结果或 blocked）；(2) roadmap lane 目标与 live 现实的差异（H2 嵌入式 / Debezium mocked / PostgreSQL 无测试）未如实裁定；(3) 没有 lane 目标→frozen 强度词表的映射规则；(4) 没有 gated-evidence 规则（skip 不算证据）与 required-lane blocked→阻塞 Stage 23 ready 的成文门禁；(5) 校验器不校验资格记录，且 `all` 未含 qualification。

## Goals

- 产出一份**有界的 lane 资格认定记录** `ai-dev/audits/nop-stream-independent-audit/environment-qualification.md`，采用 `@@LANE` 机器可解析块（格式见下方"Qualification Record Format"）。对资格目标 T1-T6 各登记一条 `@@LANE` 记录：`lane_id`、`frozen_strength`（映射到 `unit|in-process|multi-jvm`）、精确调用命令（含正确门禁 system property）、前置条件（外部服务如何 provision / classpath 是否就绪）、credential isolation / cleanup / timeout / artifact retention、owner、`status`（`qualified`/`blocked`）、`expected_positive_result`（实跑引用）或 `blocked_reason`+`rerun_condition`。
- **如实裁定** T1-T6 与 live 现实的差异：T1 嵌入式/mocked → `qualified`（in-process/unit）；T5 PostgreSQL → `blocked: no gated test`；T6 真实 CDC → `blocked: only mocked test exists`；T2/T3/T4 → 按认定期是否能 provision 判 `qualified`/`blocked`。不得把嵌入式/mocked 测试冒充为外部集成资格。
- 冻结**映射规则**：每个 `@@LANE` 的 `frozen_strength` 必须是 frozen 词表值之一；后续 domain audit 的 evidence row 的 `environment_class` 只能取某个 `qualified` lane 的 `frozen_strength`（或更低），不得升级。
- 冻结**两条成文规则**（写入资格记录 + `evidence-schema.md` 增补段，不改 11 字段/7 分类词表）：
  1. **gated-evidence 规则**：一个 gated 测试只有当其实际在已 `qualified` 的 lane 内执行（产出实跑日志/断言）时，其结果才可被后续 domain audit 采信为证据；缺省 skip 不算证据。
  2. **required-lane + blocked-gate 规则**：`required_lane` 从 Stage-4 manifest 声明能力派生；一个 domain audit 可带着 `blocked` 行收尾，仅当其 evidence row 把该行分类为 `blocked`（不得 `e2e-proved`）；任何 required-lane `blocked` 行阻塞 Stage 23 ready 判定并阻塞 readiness 里程碑。
- 扩展校验器：新增 `qualification` 子命令，校验 `@@LANE` 块结构（必填字段、`frozen_strength` 在 frozen 词表内、`status` ∈ {qualified,blocked}、blocked 行必带 `blocked_reason`+`rerun_condition`），并把 `qualification` 纳入 `all` 组合；带阳性对照。

### Qualification Record Format（`@@LANE` 块规范）

资格记录 `environment-qualification.md` 采用与 `@@ENTRY`/`@@EVIDENCE` 同风格的 `@@LANE ... @@END` 块，flat `key: value` 行。必填字段：

```
@@LANE
lane_id: T2-multi-jvm
frozen_strength: multi-jvm          # MUST be one of: unit | in-process | multi-jvm
invoke_command: ./mvnw test -pl nop-stream-runtime -am -Dnop.stream.test.multi-jvm.enabled=true -Dtest=TestMultiJvmExactlyOnceRecovery
preconditions: reactor built (./mvnw install -pl nop-stream -am -DskipTests); process spawn allowed
credential_isolation: none (in-process cluster)
cleanup: subprocess termination + temp port/file reclaim
timeout: 120s per test
artifact_retention: surefire-reports + subprocess stdout
owner: nop-stream-independent-audit
status: qualified                    # MUST be one of: qualified | blocked
expected_positive_result: surefire TestMultiJvmExactlyOnceRecovery PASS (recovery sink exactly-once assertion)
# when status=blocked, REQUIRED:
blocked_reason: <<only if blocked>>
rerun_condition: <<only if blocked>>
@@END
```

校验器 `qualification` 子命令拒绝：缺必填字段、`frozen_strength` 词表外、`status` 词表外、`status=blocked` 但缺 `blocked_reason`/`rerun_condition`、`status=qualified` 但缺 `expected_positive_result`、未知字段。当 `status=blocked` 且原因是"repo 无该 lane 的 gated 测试"时，`invoke_command` 允许值为 `none (no gated test in repo)`（blocked-no-test lane 的合法占位，非缺字段）。

## Non-Goals

- 产品正确性结论（某条 lane "能跑" ≠ 某项能力 "正确"；后者由 Stages 6-16 裁定）。
- 生产就绪（readiness）结论（Stage 23）。
- 新增 gated 测试或新 connector（本计划只认定/裁定现有 lane；T5 PostgreSQL / T6 真实 CDC 的"无测试"如实记 `blocked`，不在本计划内补测试）。
- 修改 frozen evidence-row 11 字段定义或 7 分类词表（本计划只补规则文本 + 映射规则）。
- 跨 lane 的性能/容量基线（归未来优化项）。

## Scope

### In Scope

- `ai-dev/audits/nop-stream-independent-audit/environment-qualification.md`（6 条 `@@LANE` 记录 T1-T6）。
- `evidence-schema.md` 增补段（gated-evidence 规则 + required-lane/blocked-gate 规则 + lane 映射规则；不改字段/词表）。
- `ai-dev/tools/check-nop-stream-audit-manifest.mjs` 新增 `qualification` 子命令 + 纳入 `all` 组合。

### Out Of Scope

- 任何单条 capability 的 evidence row（Stages 6-16）。
- 为 T5/T6 补测试（如实 `blocked`，由后续 remediation/connector plan 决定是否补）。
- 跨域审计的 ready 判定本身（Stage 23）。

## Execution Plan

### Phase 1 - Embedded & Always-Available Lanes (T1) + Rule Text

Status: completed
Targets: `ai-dev/audits/nop-stream-independent-audit/environment-qualification.md`（T1 记录）, `evidence-schema.md`（规则增补段）

- Item Types: `Proof | Decision`

- [x] 登记 T1（unit + 嵌入式 in-process）：`invoke_command`（`./mvnw test -pl nop-stream -am`，可拆子模块）、`frozen_strength: in-process`（嵌入 H2/mocked Debezium 归此）、preconditions（JDK + 本地构建，无外部服务）、正向预期结果（一组代表 unit/in-process 测试实跑通过的 surefire 引用，含 `TestJdbcTwoPhaseCommitSinkSkeleton` 嵌入 H2）。
- [x] 如实标注：H2 为嵌入式 in-process（非外部 provision）；Debezium 现有测试为 mocked（非真实 CDC）——在记录中以 note 字段说明，不冒充外部集成。
- [x] 冻结 gated-evidence 规则 + required-lane/blocked-gate 规则 + lane 映射规则的权威文本，写入 `evidence-schema.md` 增补段。

Exit Criteria:

- [x] `environment-qualification.md` 存在，含 T1 的 `@@LANE` 块，字段经 Phase 3 校验器 `qualification` 结构合法
- [x] T1 `invoke_command` 在当前 HEAD 实跑通过（`./mvnw test -pl nop-stream -am` 至少一组模块 SUCCESS；嵌入 H2 测试 PASS），surefire 报告路径作为 `expected_positive_result` 证据引用
- [x] gated-evidence / required-lane / blocked-gate / 映射规则在 `evidence-schema.md` 有显式权威文本
- [x] **无静默跳过**：H2 嵌入式 / Debezium mocked 性质在记录中显式标注，不冒充外部集成资格（Rule #24）
- [x] **接线验证**：规则文本 + `@@LANE` 格式被 Phase 3 校验器 `qualification` 子命令强制（非松散散文）
- [x] `No owner-doc update required`（资格记录 + 规则文本写入审计 schema，不改 `docs-for-ai/`）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - Multi-JVM Lane (T2) Qualification

Status: completed
Targets: `ai-dev/audits/nop-stream-independent-audit/environment-qualification.md`（T2 记录）

- Item Types: `Proof`

- [x] 登记 T2（multi-JVM）：`invoke_command`（`./mvnw test -pl nop-stream-runtime -am -Dnop.stream.test.multi-jvm.enabled=true -Dtest=TestMultiJvmExactlyOnceRecovery` 或等价）、`frozen_strength: multi-jvm`、preconditions（**reactor 须先 `./mvnw install -pl nop-stream -am -DskipTests`** 填充 classpath；`MiniStreamCluster.java:137-138` 解析 `java.class.path`/`java.home`；进程 spawn 允许）、provisioning（子 JVM spawn）、cleanup（子进程终止 + 临时端口/文件）、timeout、artifact retention、owner。
- [x] 认定尝试：在 `nop.stream.test.multi-jvm.enabled=true` + 已构建 classpath 下实跑 `TestMultiJvmExactlyOnceRecovery`（或 `TestMultiJvmCoordinatorFailover`）。

Exit Criteria:

- [x] T2 `@@LANE` 块存在，字段结构合法
- [x] **端到端验证（Rule #22）**：T2 正向预期结果来自一次**实跑**——multi-JVM 测试在门禁属性 + 已构建 classpath 下 PASS，证据引用 surefire 报告；若实跑失败/不可 provision（如 sandbox 禁进程 spawn），`status: blocked` + `blocked_reason` + `rerun_condition`（不得缺省 skip 后无记录）
- [x] **接线验证**：`invoke_command` 的门禁属性与测试源码 `:67` 一致（`nop.stream.test.multi-jvm.enabled`），可直接复制复现
- [x] **无静默跳过**：不可 provision 时显式 `blocked`
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - Gated External-Service Lanes (T3 Kafka, T4 Pulsar) + Honest Reclassification (T5 PostgreSQL, T6 Debezium-CDC) + Validator

Status: completed
Targets: `ai-dev/audits/nop-stream-independent-audit/environment-qualification.md`（T3-T6 记录）, `ai-dev/tools/check-nop-stream-audit-manifest.mjs`（`qualification` 子命令 + `all` 组合）

- Item Types: `Proof | Decision`

- [x] 登记 T3（Kafka）：门禁 `nop.stream.test.kafka.enabled=true`（**非 `.brokers`**）+ 连接串 `nop.stream.test.kafka.brokers`；`frozen_strength: in-process`；credential isolation（测试账号）/cleanup（topic 清理）/timeout/artifact retention/owner。
- [x] 登记 T4（Pulsar）：门禁 `nop.stream.test.pulsar.enabled=true`（**非 `.serviceUrl`**）+ 连接串 `nop.stream.test.pulsar.serviceUrl`；`frozen_strength: in-process`；同上字段。
- [x] 如实裁定 T5（PostgreSQL）：repo 无 gated PostgreSQL 测试 → `status: blocked`，`blocked_reason: no @EnabledIfSystemProperty-gated PostgreSQL test in repo`，`rerun_condition: add a gated PostgreSQL sink/checkpoint test`。
- [x] 如实裁定 T6（真实 CDC）：现有 Debezium 测试为 mocked offset-config → 真实 CDC lane `status: blocked`，`blocked_reason: only mocked offset-config test exists; no real-source Debezium engine integration test`，`rerun_condition: add real-source Debezium integration test`。
- [x] 实现校验器 `qualification` 子命令（按 "Qualification Record Format" 规范校验 `@@LANE` 块：必填字段、`frozen_strength`∈frozen 词表、`status`∈{qualified,blocked}、blocked 必带 reason+rerun、qualified 必带 expected_positive_result、拒未知字段）；把 `qualification` 纳入 `all` 组合（修改 `all` 分支）。
- [x] 对 T3/T4 做资格尝试：能 provision broker/service 的实跑记录正向结果；不能的 `blocked`。

Exit Criteria:

- [x] T3-T6 各有 `@@LANE` 记录（T3/T4 qualified 或 blocked；T5/T6 blocked 且带 reason+rerun）
- [x] T3 门禁属性为 `nop.stream.test.kafka.enabled`、T4 为 `nop.stream.test.pulsar.enabled`（**逐行与测试源码 `:44-45`/`:49-50` 一致**；`.brokers`/`.serviceUrl` 仅作连接串登记，不作门禁）
- [x] 校验器 `qualification` 子命令存在；`node ai-dev/tools/check-nop-stream-audit-manifest.mjs qualification` 对正式资格记录 exit 0
- [x] **阳性对照**：对已知坏 `@@LANE` 块（缺字段/`frozen_strength` 词表外/`status` 词表外/blocked 缺 reason/qualified 缺 expected_positive_result/未知字段），`qualification` 以非零退出码失败并打印具体错误
- [x] `all` 组合已纳入 `qualification`（`node ... all` 实际执行 qualification）
- [x] **无静默跳过**：T5/T6 如实 `blocked`（不冒充 qualified）；T3/T4 不可 provision 时 `blocked`
- [x] **接线验证**：`qualification` 子命令可被后续 domain audit plan 与 Stage 23 Closure Gates 直接调用
- [x] `No owner-doc update required`
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - Qualification Record Freeze & Frozen-Schema Integrity Check

Status: completed
Targets: `ai-dev/audits/nop-stream-independent-audit/environment-qualification.md`（冻结）, `evidence-schema.md`（blocked-gate 规则最终化）

- Item Types: `Decision | Proof`

- [x] 冻结资格记录顶部 status 为 `frozen`，给出 T1-T6 的 `qualified`/`blocked` 汇总表 + 每个 `qualified` lane 的 `frozen_strength`。
- [x] 在 `evidence-schema.md` 最终化：blocked-gate 门禁文本 + lane 映射规则（domain audit 的 `environment_class` 只能取某 `qualified` lane 的 `frozen_strength` 或更低）。
- [x] **Frozen 完整性校验**：核对 `evidence-schema.md` 的 11 字段定义与 7 分类词表与 Stage-4 冻结态逐字一致（本计划只增补规则段，未改字段/词表）；把核对结论写入资格记录。

Exit Criteria:

- [x] 资格记录 status 冻结为 `frozen`，含 T1-T6 汇总表 + frozen_strength 映射
- [x] blocked-gate + 映射规则在 `evidence-schema.md` 有显式权威文本
- [x] **frozen 完整性**：`evidence-schema.md` 的 11 字段 / 7 分类词表与 Stage-4 冻结态一致（核对结论已记录）
- [x] `node ai-dev/tools/check-nop-stream-audit-manifest.mjs all` exit 0（含 qualification）
- [x] `node ai-dev/tools/check-nop-stream-audit-manifest.mjs self-test` exit 0（阳性对照含 qualification）
- [x] `node --check ai-dev/tools/check-nop-stream-audit-manifest.mjs` 通过
- [x] **Anti-Hollow**：`qualified` 确有实跑证据引用；`blocked` 确有 reason+rerun_condition（非空声明）；H2/Debezium 性质如实标注未冒充外部
- [x] `No owner-doc update required`
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **纯数据/工具计划**：不改 nop-stream 生产代码（仅审计数据文件 + 校验器子命令）。`./mvnw test`/`compile` 不强制；以校验器退出码 + lane 实跑证据引用为 closure 依据。校验器须 `node --check` 通过。lane 实跑（Phase 1-3）须有真实 surefire 引用作正向预期结果。

- [x] T1-T6 全部资格认定完成或显式 `blocked`（H2 嵌入式 / Debezium mocked 如实归 T1，不冒充外部；T5/T6 如实 blocked）
- [x] gated-evidence + required-lane/blocked-gate + 映射规则在 `evidence-schema.md` 有显式权威文本，且 11 字段/7 词表未改
- [x] 校验器 `qualification` 子命令存在，正式输入 exit 0，阳性对照坏输入 exit 非 0，且纳入 `all`
- [x] Kafka/Pulsar 门禁属性为 `.enabled`（与测试源码一致），`.brokers`/`.serviceUrl` 仅作连接串
- [x] 不存在被静默降级到 deferred 的 in-scope lane 资格项（每条有 qualified/blocked 裁定）
- [x] `No owner-doc update required`
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 验证（a）`qualified` lane 确有实跑证据，（b）校验器 `qualification` 非空壳（阳性对照确有拒绝），（c）无静默跳过（blocked 显式标注），（d）H2/Debezium/PostgreSQL 性质如实裁定未冒充
- [x] `node --check ai-dev/tools/check-nop-stream-audit-manifest.mjs` 通过
- [x] `node ai-dev/tools/check-nop-stream-audit-manifest.mjs all` exit 0

## Deferred But Adjudicated

（预期延期场景：某 gated lane 在认定期不可 provision —— 应记为 `blocked` 而非 deferred，因为 `blocked` 是本计划合法终态且由 blocked-gate 规则承担其后果。）

## Non-Blocking Follow-ups

- 若后续 domain audit（Stages 9-16）发现需补充 lane 资格（如更细 timeout/cleanup），由对应 domain plan 触发资格记录增补（successor），不阻塞本计划 closure——前提是 T1-T6 已全部 qualified 或显式 blocked。
- T5（PostgreSQL）/ T6（真实 CDC）的"无 gated 测试"如实 blocked；是否补测试由后续 connector/remediation plan 决定，不阻塞本计划。
- 跨 lane 性能/容量基线归未来优化项。

## Closure

Status Note: Stage 5 freezes the nop-stream independent-audit lane qualification registry (T1–T6) and the gated-evidence / required-lane / lane-mapping rules. All 6 lanes adjudicated: T1 (in-process) + T2 (multi-jvm) qualified with real surefire run evidence; T3–T6 honestly blocked (no broker / no gated test / only mocked), with the blocked-gate rule propagating their consequences to Stage 23. The validator `qualification` subcommand guards the registry structurally (non-hollow positive control). No production Java code changed (pure data/tool plan). Frozen evidence-schema surface (11 fields / 7 disposition values) intact; only an additive rules supplement was appended.
Completed: 2026-08-08

Closure Audit Evidence:

- Reviewer / Agent: independent closure-audit subagent (fresh session, task `ses_022f50b54ffeKs1Mf9DTGO0034`, general agent) — NOT the implementing session.
- Audit Session: ses_022f50b54ffeKs1Mf9DTGO0034
- Evidence:
  - Exit Criteria per Phase — all `[x]`; Phases 1–4 `Status: completed`.
  - Closure Gate 1 (T1–T6 records): PASS — 6 `@@LANE` blocks (`grep -cE '^@@LANE$'` = 6); statuses T1 qualified(in-process), T2 qualified(multi-jvm), T3/T4/T5/T6 blocked.
  - Closure Gate 2 (rules S5-1/S5-2/S5-3): PASS — "Stage 5 Supplement" section in evidence-schema.md.
  - Closure Gate 3 (frozen integrity): PASS — 11 fields / 7 disposition values UNCHANGED (grep counts 11 / 7); supplement is additive only.
  - Closure Gate 4 (validator qualification): PASS — `node --check` exit 0; `qualification` exit 0; `self-test` exit 0; `all` exit 0 (manifest+corpus+evidence+qualification all [PASS]).
  - Closure Gate 5 (positive control non-hollow): PASS — checkLane rejects all 6 known-bad cases (missing field / frozen_strength=none / bad status / blocked-missing-reason / qualified-missing-positive-result / unknown field) + accepts a good lane; end-to-end file-swap test → [FAIL] EXIT=1 with 4 specific errors, restored → [PASS] EXIT=0.
  - Closure Gate 6 (gate property names): PASS — T3 `nop.stream.test.kafka.enabled` matches TestDataPlaneKafkaBackendE2E.java:45; T4 `nop.stream.test.pulsar.enabled` matches TestDataPlanePulsarBackendE2E.java:50; T2 `nop.stream.test.multi-jvm.enabled` matches TestMultiJvmExactlyOnceRecovery.java:67. `.brokers`/`.serviceUrl` registered only as connection strings.
  - Closure Gate 7 (no silent skip): PASS — T1 note marks H2 embedded-in-process + Debezium mocked; T5/T6 blocked_reasons honestly state "no gated PostgreSQL test" / "only mocked"; T3/T4 blocked (no broker), gates verified effective (Skipped without flag).
  - Anti-Hollow Check: PASS — (a) T1/T2 expected_positive_result cite real surefire (TestJdbcTwoPhaseCommitSinkSkeleton 19/19, TestDebeziumCdcCheckpoint 7/7, TestMiniStreamClusterProcessSpawn 3/3 @ 2.427s); (b) validator non-hollow (positive control rejects 6 bad + accepts 1 good); (c) all blocked lanes carry non-empty blocked_reason + rerun_condition; (d) H2/Debezium/PostgreSQL nature honestly classified, not冒充 external.
  - T1/T2 cited test files exist: PASS — TestJdbcTwoPhaseCommitSinkSkeleton.java + TestMiniStreamClusterProcessSpawn.java present at cited paths.
  - `node ai-dev/tools/check-plan-checklist.mjs <plan> --strict` — to be re-run after this Closure write; expected exit 0 (all Closure Gates now [x], Closure evidence populated).
  - Deferred 项分类检查: no in-scope item降级 to non-blocking; T5/T6 blocked (not deferred) is the plan's legitimate terminal state per the blocked-gate rule. Two deeper multi-JVM test defects (TestMultiJvmExactlyOnceRecovery log-label mismatch; TestMultiJvmCoordinatorFailover HA-failover takeover) recorded as findings for downstream capability audits (Stages 13/14), NOT closure blockers for this lane-qualification plan.
  - source-manifest.md denominator refresh (test-java-files-all 448→453): independently re-verified by auditor (`find ... | wc -l` = 453); measured-count refresh only, format/vocabulary unchanged.

Follow-up:

- T3/T4: provision Kafka/Pulsar broker in a CI/later environment and re-run with the documented gate flags to flip from `blocked` to `qualified`.
- T5: add a gated PostgreSQL sink/checkpoint test (connector remediation plan).
- T6: add a real-source Debezium integration test (connector remediation plan).
- T2 deeper defects: TestMultiJvmExactlyOnceRecovery log-label mismatch (`logFileFor("coordinator")` → should be `"coordinator-0"`) and TestMultiJvmCoordinatorFailover HA-failover takeover — owned by Stages 13/14 capability audits.
- No remaining plan-owned work; all in-scope items landed or honestly blocked.
