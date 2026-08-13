# 不变式门禁（Invariant Guards）

> 受众：修改 nop-metadata（或后续扩展到其他模块）service/processor/bizmodel/ORM 模型层的开发者和 AI。

## 目的

Nop 平台用**可执行不变式门禁**取代"修实例不修类别"的反应式审计：每个反复复发的失败模式族沉淀为一条 CI 门禁，命中即红、非零即阻断。本文档列出 nop-metadata 已沉淀的门禁、运行方式、棘轮规则。

## 门禁清单（nop-metadata，4 条）

| 门禁 | 不变式 | 形式 | 扫描/穷举面 | 命中即红的语义 |
|------|--------|------|-------------|----------------|
| `check-silent-swallow.mjs` | INV-SILENT-SWALLOW | Node + ast-grep 静态扫描 | 130 catch 块 / 46 文件 | catch 块未 rethrow、未构造带 ErrorCode 的诊断信息（空 catch / 仅 getMessage / 静默降级返回默认值且无日志） |
| `check-orm-unique-key-constraint.mjs` | INV-UK | XML-aware 静态扫描 | 37 `<unique-key>` / 39 entity | `<unique-key>` 缺 `constraint=` 或 `columns=`（DDL 静默跳过唯一约束） |
| `check-sensitive-literal-leak.mjs` | INV-SENSITIVE | Node + ast-grep 静态扫描 | `.param(...)` / `LOG.*(...)` 字面量入参 | error/log message 含 raw JDBC URL（`jdbc:` + `@`）/ 内嵌 SQL 原文 / 已知凭据哨兵 |
| `TestLimitNegativeValueInvariant` | INV-LIMIT | JUnit 5 `@ParameterizedTest` + `@MethodSource` | 4 limit-taking public 入口方法 | 接受 `limit` 入参的 public 方法未显式拒绝负值（`limit = -1` 必须抛带 ErrorCode 的异常） |

> 每条不变式的完整定义（陈述 / 覆盖失败族 / 历史 audit-finding-ID 证据 / 检测方法）记录在不变式目录（invariant-catalog，位于仓库审计目录）。

## 运行方式

### 一键聚合（推荐）

```bash
# 本地（用 ./mvnw）
./ai-dev/tools/run-nop-metadata-invariants.sh

# CI（用 mvn，由 invariant-gate job 注入 MVN=mvn）
MVN=mvn ./ai-dev/tools/run-nop-metadata-invariants.sh
```

聚合入口语义：`set -e`，任一门禁非零退出 ⇒ 整体非零退出。先决条件：门禁工具的 `node_modules` 已安装（在工具目录下 `pnpm install`）。

### 单条门禁

```bash
# 1-3 为 Node 扫描器（exit 0 = 零命中，exit 1 = 有命中）
node ai-dev/tools/check-silent-swallow.mjs --module nop-metadata
node ai-dev/tools/check-orm-unique-key-constraint.mjs --module nop-metadata
node ai-dev/tools/check-sensitive-literal-leak.mjs --module nop-metadata

# 4 为 JUnit（默认从 surefire 排除，单独调用；0 failures = 零命中）
./mvnw test -pl nop-metadata/nop-metadata-service \
  -Dtest=TestLimitNegativeValueInvariant -Dsurefire.failIfNoSpecifiedTests=false
```

### CI

`.github/workflows/maven.yml` 的 `invariant-gate` job（`needs: build`）在 `build` 成功后运行聚合入口。3 条 Node 扫描器依赖 `pnpm` + Node 20（工具目录的 pnpm-lock.yaml）；limit 测试依赖 JDK 21。**非零即红**——不得用 `continue-on-error` 或 `|| true` 静默放行。

> INV-LIMIT 的 `TestLimitNegativeValueInvariant` 默认从 surefire `<excludes>` 排除（避免默认构建依赖这条强于一般契约的门禁），仅由聚合入口 / 显式 `-Dtest=` 调用。表完备性由 `TestLimitTargetSetCompleteness`（默认 surefire，常驻 PASS）守护——新增 limit-taking 入口方法不入方法表即该测试变红。

## 棘轮规则（Ratchet）

> **已沉淀的不变式只增不减。** 弱化 / 删除 / 豁免需人工确认 + 留痕 + committed 回归测试同步。

- 门禁集合单调递增：Cycle 1 沉淀 4 条；后续 Cycle（对抗探查发现新族）只增不减。
- 弱化一条不变式（如增加例外、缩小扫描面）必须在不变式目录留痕 + 人工确认 + 同步回归测试。
- 新增 / 重命名 `processor` / `bizmodel` / ORM entity 必须被对应门禁覆盖：
  - 新增 catch 块 → INV-SILENT-SWALLOW 自动覆盖（扫描全部 catch）。
  - 新增 `<unique-key>` → INV-UK 自动覆盖（扫描全部 orm.xml）。
  - 新增 limit-taking public 入口 → 必须同时更新 `TestLimitNegativeValueInvariant` 方法表 + `TestLimitTargetSetCompleteness` 完备性断言，否则后者变红。

## 表完备性说明

INV-SILENT-SWALLOW / INV-UK / INV-SENSITIVE 为**全量扫描**（扫描全部目标，不绑方法表），天然覆盖新增目标。INV-LIMIT 为**方法表驱动穷举**，完备性由独立的 `TestLimitTargetSetCompleteness` 守护（反查 `@Name("limit")` 全部入口，与方法表比对）。

## 复触发条件（Cycle 2 / I1 启动）

下列任一条件触发时，启动下一轮不变式审计 Cycle：

- CI `invariant-gate` 变红（门禁命中）。
- 新增或重命名 `processor` / `bizmodel` / ORM entity（结构变更）。
- 周期复探（定期重跑对抗探查，捕捉盲区漂移）。
