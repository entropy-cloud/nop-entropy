# 2410 WI1 nop-bom 悬空条目清理与依赖版本收口

> Plan Status: completed
> Last Reviewed: 2026-10-11
> Source: ai-dev/backlog/nop-stream-structure-refactor-roadmap.md（WI1，Phase A）
> Related: ai-dev/plans/00-plan-authoring-and-execution-guide.md

## Purpose

把 nop-bom 与 tests/pom.xml 中指向已删除模块的两个悬空坐标清零，并把 rocksdbjni 版本收口到 nop-dependencies 单点管理；同时按裁定补齐 BOM 对 nop-stream 可消费坐标的缺口。完成后 nop-stream 13 模块在 BOM 中无悬空条目，rocksdbjni 版本来源唯一。

## Current Baseline

- `nop-bom/pom.xml:1246` 存在 `nop-stream-api` 条目、`:1318` 存在 `nop-stream-checkpoint` 条目。两模块已在 commit `e13895fd87`「feat(stream): 设计声明式 Stream DSL 元模型并清理空壳模块」中删除（已用 `git log --diff-filter=D` 复核，两 pom 同 commit 删除），BOM 未同步。
- `tests/pom.xml:866` 与 `:890` 存在同两个悬空坐标。除这两处外，全仓 build 面（xml/java）对这两个坐标无其他引用（已 grep 复核；`nop-stream-checkpoints` 命中均为 checkpoint 目录名字符串，与坐标无关）。
- nop-bom 当前收录 nop-stream 7 个模块：core/flow/cep/connector/connector-kafka/format-avro/runtime。缺 6 个：connector-batch、connector-jdbc、connector-debezium、rocksdb、sql、fraud-example。
- `nop-stream/nop-stream-rocksdb/pom.xml:23` 硬编码 `<version>9.11.2</version>` 于 `org.rocksdb:rocksdbjni`，绕过 nop-dependencies 单点管理。
- `nop-kernel/nop-dependencies/pom.xml` 以 `kafka.version`（:61）、`avro.version`（:65）属性 + dependencyManagement 条目（:314-322）集中管理第三方版本，无 rocksdb 条目。同仓库先例：`nop-stream-format-avro/pom.xml` 消费 `org.apache.avro:avro` 时不写版本号（注释 :20 自述 centrally managed）。
- nop-stream 的 parent 链为 nop-entropy（根 pom），nop-dependencies 的 dependencyManagement 对全部子模块生效（avro 免版本号解析即为实证）。
- BOM 条目统一使用 `${nop-entropy.version}`。

## Goals

- 删除 `nop-bom/pom.xml` 与 `tests/pom.xml` 中全部 4 处悬空坐标条目。
- rocksdbjni 版本迁移至 `nop-kernel/nop-dependencies/pom.xml`（属性 + dependencyManagement，命名与 `kafka.version`/`avro.version` 同风格），`nop-stream-rocksdb/pom.xml` 移除硬编码版本。
- BOM 补齐 5 个可消费坐标：connector-batch、connector-jdbc、connector-debezium、rocksdb、sql（见 Scope 的裁定）。

## Non-Goals

- 不做 nop-bom 对全仓模块的全量对齐（roadmap 明示「只补应补的」）。
- 不升级 rocksdbjni 版本（9.11.2 维持不变，本 WI 只迁移管理位置）。
- 不触碰 quickstart 模板与其他模块 pom。

## Scope

### In Scope

- `nop-bom/pom.xml`：删 2 悬空条目、增 5 个 BOM 条目。
- `tests/pom.xml`：删 2 悬空条目。
- `nop-kernel/nop-dependencies/pom.xml`：增 `rocksdb.version` 属性与 rocksdbjni dependencyManagement 条目。
- `nop-stream/nop-stream-rocksdb/pom.xml`：移除 rocksdbjni 硬编码版本（附与 avro 同风格的集中管理注释）。
- `nop-benchmark/nop-benchmark-stream/pom.xml`：更新 :20-21 与 :33-34 两处将因本 WI 失真的 BOM 覆盖注释（rocksdb、connector-jdbc 入 BOM 后显式版本行保留但注释改为历史裁定说明；:33-34 关于 connector 不在 BOM 的陈述现即为假，一并修正）。

### Out Of Scope

- `nop-stream-fraud-example` 不入 BOM。裁定理由：它是自带业务模型与启动配置的端到端示例应用，不是供其他构建消费的库坐标；BOM 收录口径取「外部部署可直接选择的实现坐标」，与已入库的 connector-kafka（连接器实现）、format-avro（格式实现）、flow（编排面）同构——故 connector-batch、connector-jdbc、connector-debezium、rocksdb、sql 五个补入，fraud-example 不补。`nop-report-demo` 在 BOM 属历史宽口径，不构成必须对齐的理由。

## Execution Plan

### Phase 1 - 悬空条目清理与版本收口

Status: completed
Targets: `nop-bom/pom.xml`、`tests/pom.xml`、`nop-kernel/nop-dependencies/pom.xml`、`nop-stream/nop-stream-rocksdb/pom.xml`、`nop-benchmark/nop-benchmark-stream/pom.xml`

- Item Types: `Fix`（悬空条目为已确认的 build 面漂移；版本收口为既定裁定的落地）

- [x] `nop-bom/pom.xml` 删除 `nop-stream-api`（:1246）与 `nop-stream-checkpoint`（:1318）两个 dependency 条目
- [x] `tests/pom.xml` 删除 `nop-stream-api`（:866）与 `nop-stream-checkpoint`（:890）两个 dependency 条目
- [x] `nop-kernel/nop-dependencies/pom.xml` 增 `rocksdb.version` 属性（值 9.11.2，附与 kafka/avro 同风格注释）与 `org.rocksdb:rocksdbjni` dependencyManagement 条目
- [x] `nop-stream/nop-stream-rocksdb/pom.xml` 的 rocksdbjni 依赖移除 `<version>` 行，附 centrally-managed 注释
- [x] `nop-bom/pom.xml` 在 nop-stream 区段补 5 个条目：connector-batch、connector-jdbc、connector-debezium、rocksdb、sql（`${nop-entropy.version}` 形态）
- [x] `nop-benchmark/nop-benchmark-stream/pom.xml` 修正 :20-21、:33-34 两处 BOM 覆盖注释（显式 `${project.version}` 行保留——显式版本优先于 depMgmt，构建行为不变，仅注释改写为与本 WI 后事实一致；执行时改为直接去除显式版本行、统一走 BOM 管理——benchmark 在根 pom 的 nop-bom import 链上，原注释本就失真，形态与同块 runtime/cep 对齐）

Exit Criteria:

- [x] `grep -n "<artifactId>nop-stream-api</artifactId>\|<artifactId>nop-stream-checkpoint</artifactId>" nop-bom/pom.xml tests/pom.xml` 零命中（实测 exit 1，零命中）
- [x] `grep -rn "9.11.2" nop-stream/nop-stream-rocksdb/pom.xml` 零命中（实测 exit 1），且 `grep -n "rocksdb.version" nop-kernel/nop-dependencies/pom.xml` 命中属性定义（:69）与 dependencyManagement 引用（:332）
- [x] `grep -c "<artifactId>nop-stream-" nop-bom/pom.xml` = 12（实测 12：core/flow/cep/connector/connector-kafka/format-avro/runtime 7 存量 + rocksdb/connector-batch/connector-jdbc/connector-debezium/sql 5 新增，逐一列出核对一致）
- [x] 全仓 build 面（`--include=*.xml --include=*.java`，排除 ai-dev/docs 历史文档）以坐标模式检索零残留（实测零命中；`nop-stream-checkpoints` 目录名字符串 9 处非坐标，不在钉范围）
- [x] **行为验证**：`./mvnw -o -q install -DskipTests -pl nop-stream/nop-stream-rocksdb` 通过（exit 0），`./mvnw -o dependency:tree -pl nop-stream/nop-stream-rocksdb` 显示 `org.rocksdb:rocksdbjni:jar:9.11.2:compile`（解析版本不变；结合模块 pom 零版本命中 ⇒ 来源为 nop-dependencies dependencyManagement）
- [x] **回归验证**：`./mvnw -o -q install -DskipTests -pl nop-bom` 通过（exit 0）；tests 模块已注释出 reactor，用 `-f` 形态抽查：`./mvnw -o dependency:resolve -f tests/pom.xml` 失败于 4 个与本次改动无关的缺席构件（nop-js、nop-dev-core、nop-dev-service、nop-dev-web——后三者仓库内已无对应模块目录，属 tests 模块被注释出 reactor 的先存原因；失败清单不含任何 nop-stream-* 坐标，且本 WI 恰把 tests 的不可解析集从 6 项减为 4 项，严格改善）。裁定：抽查目标「删除 2 条目不使 tests pom 变差」成立，先存缺席项不属本 WI scope
- [x] No new test required: 纯 pom 配置调整，无新增代码路径；行为面由上述 install/dependency:tree 命令验证
- [x] No owner-doc update required（pom 级修正，不改任何使用契约；module-groups.md 的 nop-stream 行在 WI12 收口统一核对）
- [x] `ai-dev/logs/2026/10-11.md` 已追加 WI1 条目

## Closure Gates

- [x] 全部 4 处悬空坐标条目已删除且全仓无 build 面残留
- [x] rocksdbjni 版本唯一来源为 nop-dependencies，模块 pom 无版本号
- [x] BOM 补 5 坐标后 `./mvnw -o -q install -DskipTests -pl nop-bom` 通过
- [x] nop-stream-rocksdb 离线安装与 dependency:tree 验证通过
- [x] 无 in-scope live defect 被降级为 follow-up（tests 模块先存的 4 项缺席构件非本 WI 引入，裁定为 out-of-scope 并留档）
- [x] 独立子 agent closure audit 完成并记录证据
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2410-wi1-nop-bom-dangling-entries.md --strict` 退出码 0

## Closure

Status Note: 全部 9 条 Exit Criteria 与 Closure Gates 经独立审计 9/9 PASS（live repo 实测复核，非采信实现者记录）。4 处悬空坐标清零、rocksdbjni 单点收口、BOM 补 5 坐标后 12 条目与 13 模块清单对齐（fraud-example 裁定不入）。tests 模块 4 项先存缺席构件（nop-js/nop-dev-core/service/web）裁定为 out-of-scope watch-only 残留（reactor 外、本 WI 前即不可构建、无 successor WI 认领必要——非本 WI 引入且未被降级的 in-scope defect）。
Completed: 2026-10-11

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（general-purpose，closure-audit fresh session，2026-10-11）
- Audit Session: agent_126ae9e2-4a3c-4a6f-bd22-be889faada61
- Evidence:
  - Exit Criteria 1-9：全部 PASS（审计者重跑 grep/maven 实测：悬空坐标零命中、BOM 恰 12 条目与 13 模块清单对齐、rocksdb 版本单点化且解析版本 9.11.2 不变、`-pl nop-bom` 与 `-pl nop-stream/nop-stream-rocksdb` 离线安装双绿）
  - Closure Gates 1-5：PASS（含 Anti-Hollow 组件级检查：12 个 BOM artifactId 与模块目录 12/12 一一对应，无遗漏无多余）
  - `node ai-dev/tools/check-plan-checklist.mjs` 退出码 0（completed 态复跑确认）
  - Anti-Hollow 检查结果：本计划无代码路径；组件级替代面（BOM↔模块一一对应 + dependency:tree 版本来源链）已验证；`scan-hollow-implementations.mjs` 不适用（纯 pom 变更，无 Java 源）
  - Deferred 项分类检查：无 in-scope live defect 被降级；tests 4 项先存缺席构件为 watch-only residual，留档于 Exit Criteria 6 与本 Status Note

Follow-up:

- watch-only residual：tests/pom.xml 的 4 项先存缺席构件（nop-js、nop-dev-core/service/web）——reactor 外、非本 WI 引入；若未来复活 tests 模块须先清理（无 roadmap WI 认领，不阻塞任何在册基线）
