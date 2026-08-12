# 2251 - Bean 命名前缀合规修正（check-bean-naming 落地）

> Plan Status: active
> Last Reviewed: 2026-08-13
> Source: `docs-for-ai/02-core-guides/code-style.md`（§IoC Bean 命名强约定：平台内置 bean 短名以 `nop` 为前缀）；用户 2026-08-13 指示——写检查工具确认所有短名 bean 非测试目录均以 `nop` 前缀，nop-demo 豁免，其余修正
> Related: `ai-dev/plans/2026-08-13-0900-1-mfa-db-store-and-default-db.md`（MFA 4 bean 顺带改名，本 plan 与其协同）

## Purpose

修正平台仓库中所有不符合 IoC Bean 命名强约定的短名 bean id（非 `nop`/`biz_` 前缀、非测试目录），并将新检查工具 `ai-dev/tools/check-bean-naming.mjs` 接入 CI 门控，防止回归。

## Current Baseline

（已核对 live repo）

- 检查工具 `ai-dev/tools/check-bean-naming.mjs` 已创建（本 plan 前置产出）：扫描全部 `*.beans.xml`（排除 target/_dump/_gen/test 目录），校验三类：
  - **BEAN-ID**：短名（无 `.`）bean id 必须以 `nop`/`biz_` 前缀
  - **REF**：`ref`/`value-ref`/`depends-on` 引用短名同样校验（改名时引用点同步）
  - **COLLECT-PREFIX**：`ioc:collect-beans name-prefix` 前缀校验
- 豁免清单（工具已实现）：全限定类名（含 `.`）、`biz_`（codegen BizModel）、`ai-tools:`/`ai-agent-tools:`（AI 工具注册名，经 by-type 收集的平台约定）、`NopXxxBizModel_tenant/_main`（BizModel 变体）、测试目录与 `test`/`testMock` 前缀、`_dump`/`_gen`/`target`。
- **当前违规（实测）**：`node ai-dev/tools/check-bean-naming.mjs` → 213 文件 / 872 bean / **BEAN-ID 违规 60**（nop-demo 7 个经用户裁决豁免后净 53）+ **REF 违规 24** + COLLECT-PREFIX 0。
- 违规分布（净 53）：
  - nop-persistence/nop-db-migration：19（`migrationEngine`/`sqlExecutor`/`*ColumnExecutor`/`*DataExecutor`/`*TableExecutor`/`*IndexExecutor`/`*ViewExecutor` 等）
  - nop-auth：6（`mfaStoreProvider`/`mfaChallengeStore`/`smsCodeStore`/`totpAuthenticator`/`channelBindService`/`userChannelResolver`）
  - nop-stream：6（`streamMessageService`×2/`streamDistributedExecutor`/`streamDataPlaneWireCodec`/`streamTaskRpcServer_node0`/`streamTaskRpcProxy_node0`）
  - nop-credential：4（`credentialCipher`/`credentialProvider`/`defaultCredentialKeyProvider`/`defaultCredentialTypeRegistry`）
  - nop-cluster：4（`AbstractCluster/Http/Broadcast/RpcProxyFactoryBean`——**均为 `abstract="true"` 配置模板 bean**，非实例 bean）
  - nop-ai-gateway：4（`channelConnectorManager`/`channelSessionStore`/`channelMessageService`/`feishuConnector`）
  - nop-job：3（`workerAssignmentStrategy`/`workerLoadProvider`/`jobPartitionResolver`）
  - nop-integration-feishu：3（`feishuCredentials`/`feishuClient`/`feishuBindProvider`）
  - 单点 4：`graphqlSubscriptionManager`（nop-graphql）/`metaQualityCheckpointScheduler`（nop-metadata）/`sysCompactExtFieldHelper`（nop-sys）/`wfTaskScanner`（nop-wf）
- `nop-cluster` 4 个 Abstract 模板：`abstract="true"` + `ioc:default="true"`（`rpc-cluster-defaults.beans.xml`），是 spring 风格抽象模板 bean（供派生覆盖），**建议裁决为豁免**（abstract 模板非实例 bean，类名即模板名语义）。
- `nop-stream` 2 个 `_node0` 变体（`streamTaskRpcServer_node0`/`streamTaskRpcProxy_node0`）：节点变体注册，注释为"Stage 42 物化"（脚手架形态）。
- REF 违规 24 处：全部指向上述违规 bean（db-migration 12 + job 3 + feishu 1 + graphql 1 + spring-delta 1 + stream 6）——改名时同步。

## Goals

- 所有生产模块（非 nop-demo）短名 bean id 符合 `nop` 前缀强约定（`biz_` 除外）。
- 全部 `ref`/`value-ref`/`depends-on` 引用点同步改名，IoC 容器装配零破坏。
- `check-bean-naming.mjs` 接入 CI 门控（违规即失败）。
- nop-demo 模块豁免（用户裁决）；`nop-cluster` Abstract 模板 bean 裁决（倾向豁免）。

## Non-Goals

- 不改 `codegen` 生成物（`_service.beans.xml` 的 `biz_*` 是合法生成命名）。
- 不改 AI 工具注册名（`ai-tools:`/`ai-agent-tools:`）。
- 不改 BizModel 变体（`NopXxxBizModel_tenant` 等）。
- 不重构任何 bean 的 class/逻辑，纯 id 改名 + 引用同步。
- 不处理 nop-demo 模块（豁免）。

## Scope

### In Scope

- 11 个模块的 53 个 BEAN-ID 违规改名 + 24 处 REF 引用同步。
- `check-bean-naming.mjs` 接入 CI（`compliance` workflow 或等价 gate）。
- `nop-cluster` Abstract 模板 bean 的豁免裁决（如豁免则工具加白名单）。
- nop-auth 4 个 MFA bean 改名与 `2026-08-13-0900-1` 计划协同（该 plan 的 Phase 3 也引用这些 bean）。

### Out Of Scope

- nop-demo 模块（豁免）。
- 工具自身的规则调整（除 nop-cluster Abstract 豁免白名单外）。
- MFA DB 存储实现本身（归 `2026-08-13-0900-1`）。

## Execution Plan

### Phase 1 - 裁决与白名单

Status: planned

- [ ] 裁决 nop-cluster 4 个 `Abstract*RpcProxyFactoryBean`（abstract="true" 模板）：豁免入工具白名单，或在 `rpc-cluster-defaults.beans.xml` 改名（`nopRpcClusterProxyFactoryBean` 等）+ 引用同步
- [ ] 裁决 nop-stream 2 个 `_node0` 变体（脚手架形态）：豁免（`_nodeN` 变体模式）或改名
- [ ] 工具增加豁免机制（白名单段：abstract 模板 / `_nodeN` 变体），复跑确认净违规数

Exit Criteria:

- [ ] 裁决记录（豁免 vs 改名）写入本 plan；工具白名单生效
- [ ] 复跑 `node ai-dev/tools/check-bean-naming.mjs` 违规数符合裁决后预期
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 批量改名 + 引用同步（按模块）

Status: planned

- [ ] **nop-auth（6）**：`mfaStoreProvider`→`nopMfaStoreProvider`、`mfaChallengeStore`→`nopMfaChallengeStore`、`smsCodeStore`→`nopSmsCodeStore`、`totpAuthenticator`→`nopTotpAuthenticator`、`channelBindService`→`nopChannelBindService`、`userChannelResolver`→`nopUserChannelResolver`（含该 beans.xml 内全部 ref 引用点 + Java 侧 `@Inject`/`inject('...')` 引用点全仓 grep 同步）
- [ ] **nop-persistence/db-migration（19）**：`migrationEngine`→`nopMigrationEngine`、`migrationExecutor`→`nopMigrationExecutor`、`migrationFileScanner`→`nopMigrationFileScanner`、`migrationHistoryManager`→`nopMigrationHistoryManager`、`sqlExecutor`→`nopSqlExecutor`、`createTableExecutor`→`nopCreateTableExecutor`、`dropTableExecutor`→`nopDropTableExecutor`、`renameTableExecutor`→`nopRenameTableExecutor`、`addColumnExecutor`→`nopAddColumnExecutor`、`dropColumnExecutor`→`nopDropColumnExecutor`、`alterColumnExecutor`→`nopAlterColumnExecutor`、`createIndexExecutor`→`nopCreateIndexExecutor`、`dropIndexExecutor`→`nopDropIndexExecutor`、`createViewExecutor`→`nopCreateViewExecutor`、`dropViewExecutor`→`nopDropViewExecutor`、`insertDataExecutor`→`nopInsertDataExecutor`、`updateDataExecutor`→`nopUpdateDataExecutor`、`deleteDataExecutor`→`nopDeleteDataExecutor`、`customChangeExecutor`→`nopCustomChangeExecutor`（含 ref 同步 12 处 + Java 引用点）
- [ ] **nop-stream（6 或裁决后 4）**：`streamMessageService`→`nopStreamMessageService`（两文件）、`streamDistributedExecutor`→`nopStreamDistributedExecutor`、`streamDataPlaneWireCodec`→`nopStreamDataPlaneWireCodec`、`streamTaskRpcServer_node0`/`streamTaskRpcProxy_node0`（按 Phase 1 裁决）
- [ ] **nop-credential（4）**：`credentialCipher`→`nopCredentialCipher`、`credentialProvider`→`nopCredentialProvider`、`defaultCredentialKeyProvider`→`nopCredentialKeyProvider`、`defaultCredentialTypeRegistry`→`nopCredentialTypeRegistry`（含 ref + Java 引用点）
- [ ] **nop-cluster（4，按 Phase 1 裁决）**：豁免或改名
- [ ] **nop-ai-gateway（4）**：`channelConnectorManager`→`nopChannelConnectorManager`、`channelSessionStore`→`nopChannelSessionStore`、`channelMessageService`→`nopChannelMessageService`、`feishuConnector`→`nopFeishuConnector`（含 Java 引用点——nop-ai-gateway 是这些 bean 的主要消费者）
- [ ] **nop-job（3）**：`workerAssignmentStrategy`→`nopWorkerAssignmentStrategy`、`workerLoadProvider`→`nopWorkerLoadProvider`、`jobPartitionResolver`→`nopJobPartitionResolver`（含 ref 同步 3 处）
- [ ] **nop-integration-feishu（3）**：`feishuCredentials`→`nopFeishuCredentials`、`feishuClient`→`nopFeishuClient`、`feishuBindProvider`→`nopFeishuBindProvider`（含 ref 同步 1 处 + Java 引用点）
- [ ] **单点 4**：`graphqlSubscriptionManager`→`nopGraphqlSubscriptionManager`、`metaQualityCheckpointScheduler`→`nopMetaQualityCheckpointScheduler`、`sysCompactExtFieldHelper`→`nopSysCompactExtFieldHelper`、`wfTaskScanner`→`nopWfTaskScanner`（含 ref + Java 引用点）
- [ ] 每模块改名后立即复跑 `check-bean-naming.mjs` 验证该模块清零

Exit Criteria:

- [ ] 11 模块全部改名完成；复跑工具 BEAN-ID=0、REF=0（非豁免项）
- [ ] 每模块 `grep '"旧名"'` 零残留（beans.xml + Java + xbiz + 测试资源）
- [ ] **行为零回归**：受影响模块 `mvn test` 全绿（nop-auth/nop-ai-gateway/nop-credential/nop-job/nop-stream/nop-db-migration 等）；`mvn clean install -DskipTests` 全仓库 BUILD SUCCESS
- [ ] **无静默跳过**：改名遗漏即 grep 残留/工具复跑失败，显式处理而非跳过
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - CI 门控接入 + 文档同步

Status: planned

- [ ] `check-bean-naming.mjs` 接入 CI（`compliance.yml` 或独立 job）：全仓库运行，BEAN-ID 违规 exit 1
- [ ] `docs-for-ai/02-core-guides/code-style.md` §IoC Bean 命名补「check-bean-naming 检查工具」引用行（含运行命令）
- [ ] 若裁决豁免项（Abstract 模板/`_nodeN` 变体）入白名单，在工具注释与 code-style.md 记录豁免理由

Exit Criteria:

- [ ] CI workflow 含 bean 命名检查 job，本地 `node ai-dev/tools/check-bean-naming.mjs` exit 0
- [ ] code-style.md 含工具引用；豁免理由文档化
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [ ] 三个 Phase 的 Exit Criteria 全部勾选
- [ ] `node ai-dev/tools/check-bean-naming.mjs` exit 0（BEAN-ID=0、REF=0，豁免项白名单内）
- [ ] 受影响模块测试全绿 + 全仓库 `mvn clean install -DskipTests` BUILD SUCCESS
- [ ] MFA 计划（`2026-08-13-0900-1`）协同检查：4 个 MFA bean 新名一致，无冲突
- [ ] 独立子 agent closure audit 通过（evidence 写入本 plan Closure 段）
- [ ] `ai-dev/logs/` 收口记录

## Deferred But Adjudicated

- **nop-demo 模块**（7 个违规）：用户裁决豁免——示例代码非产品基线，不纳入检查范围（工具排除 nop-demo 或白名单）。
- **nop-stream `_node0` 变体**：脚手架形态（注释"Stage 42 物化"），倾向豁免入白名单（`_nodeN` 变体模式），裁决在 Phase 1 记录。

## Risks

- **Java 侧引用遗漏**：`@Inject`/`inject('xxx')` 按名引用 bean 的代码点——每模块改名后用 grep 全量核对（beans.xml + `src/main/java` + `src/test` + `_vfs` 全部）。
- **`ioc:default="true"` 覆盖语义**：部分违规 bean 带 `ioc:default`（如 `channelBindService`），应用层可能已按旧名覆盖——改名后应用侧 delta 若引用旧名会失效。全仓 grep 确认无外部引用（nop-app-erp 等消费方）后执行；如发现消费方引用，登记并同步或声明 breaking change。
- **工具误报/漏报**：正则以 `<bean id="...">` 匹配；`ioc:alias`/`ioc:bean-method` 等属性不在扫描范围（Phase 3 后如有发现补正则）。
