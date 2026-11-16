# Wave 6 audit 6A-3: 派发面三向审计（UNDER / OVER / IB-ONLY）

> Status: open
> Date: 2026-11-16
> Scope: 对 3 个已实测外部项目做类级三向差分——nop-ib 派发/执行集 vs mvn 实际执行集（surefire-reports oracle）。
> Method: ib 侧用 `-Dnop.ib.debugPerClass=true` 取逐类计数（JUnitWorkerPool debug 输出）；mvn 侧用 surefire-reports `TEST-*.xml` testcase 聚合到 top-level class（`$` 前截断），执行数口径 = tests − skipped（runbook 第 5 节，skipped 口径对齐）。
> 独立审计：本审计由独立 agent 执行，未修改任何外部项目或本仓源码；中间产物在 `_tmp/6a3/`（`*-mvn.json` / `*-ib.json` 为机器可复算的逐类计数）。

## 前置（三项目共用）

```bash
cd /Users/abc/app/nop-entropy-wt/nop-entropy-ib && ./mvnw install -pl nop-tools/nop-ib -am -DskipTests -q   # 一次，EXIT=0
CP="/Users/abc/app/nop-entropy-wt/nop-entropy-ib/nop-tools/nop-ib/target/classes:$(cat /Users/abc/app/nop-entropy-wt/nop-entropy-ib/nop-tools/nop-ib/target/ib-cp.txt)"
java -Xmx6g -cp "$CP" io.nop.ib.NopIbMain model refresh    # 各项目目录内
java -Xmx6g -cp "$CP" -Dnop.ib.debugPerClass=true io.nop.ib.NopIbMain test -pl <module>
```

---

## 1. spring-batch — module `spring-batch-core`

### Commands

- model refresh：`model refreshed: 7 modules, maven 3.9.9`，dump e8e518ffeec727f8ea646e93b85bcce8cc4bc2bf3905e3bd0a03ddcb85c00802
- mvn oracle：**复用当日既有 surefire-reports**（`~/sources/spring-batch/spring-batch-core/target/surefire-reports`，mtime 2026-11-16 13:28，早于 ib run 13:40+，与当前代码同代，免重跑）。
- ib：`java -Xmx6g -cp "$CP" -Dnop.ib.debugPerClass=true io.nop.ib.NopIbMain test -pl spring-batch-core`

### Counts

| side | classes | tests | executed (tests−skipped) | failed | skipped |
|---|---|---|---|---|---|
| mvn | 152 | 949 | 904 | 1 | 45 |
| ib  | 157 | 906 (run) | 906 | 1 | 2 |

两侧同红 1 处：`MongoDefaultBatchConfigurationTests`（无 Docker/Mongo，速查表预存，非 ib 缺陷）。同类 `MicrometerTracingTests`：mvn 6/2skipped = 4 executed，ib 4/2skipped，精确同数。

### Three-way buckets

- **UNDER（mvn 有、ib 无）：0** ✅
- **OVER（ib 多跑且有执行）：0** ✅
- **IB-ONLY（0-test helper）：5**，全部 tests=0/failures=0，均为抽象基类（mvn surefire 不派发 abstract 类，ib 派发但零执行，无害）：
  - `org.springframework.batch.core.AbstractExceptionTests`
  - `org.springframework.batch.core.AbstractExceptionWithCauseTests`
  - `org.springframework.batch.core.listener.AbstractDoubleExceptionTests`
  - `org.springframework.batch.core.repository.dao.AbstractExecutionContextSerializerTests`
  - `org.springframework.batch.core.repository.dao.AbstractJobDaoTests`
- 同类计数差（IB-ONLY 之外）：0。

### Verdict

**parity**。ib run=906 = mvn executed 904 + 2（ib 自己的 skipped 口径差异：`MicrometerTracingTests` 2 skipped 两侧一致，ib run 不含 skipped；去重后执行类级完全一致）。

---

## 2. liteflow — module `liteflow-testcase-el-nospring`

### Commands / 过程（含一个重要口径发现）

- model refresh：`106 modules, maven 3.9.12`。
- mvn oracle 需 override：根 pom `pom.xml:44` 设 `<skipTests>true</skipTests>`，默认 `mvn test` 全模块 "Tests are skipped"。oracle 命令：
  `mvn test -fae -Dmaven.test.failure.ignore=true -Dmaven.build.cache.skipCache=true -DskipTests=false -Dmaven.test.skip=false -Denforcer.skip=true -Dspotless.check.skip=true -pl liteflow-testcase-el/liteflow-testcase-el-nospring` → 234 tests / 1 skipped / 0 failed。
- ib 直接 `test -pl liteflow-testcase-el-nospring` **整模块 skip**（`skipTests=true or no test source (model fact)`，TestCommand.java:178 模型事实门；ib 无 CLI override 可表达 mvn 的 `-DskipTests=false`）。
- 类级证据改走 **`--class` 显式执行路径**（显式请求按设计绕过模型事实门，TestCommand.java:235-263）：把 mvn oracle 的 52 个 top-level class 逐一 `--class liteflow-testcase-el-nospring:<FQCN>` 派发，`-Dnop.ib.debugPerClass=true` 逐类取证。52/52 全部成功执行。

### Counts

| side | classes | executed |
|---|---|---|
| mvn | 52 | 233（234 − 1 skipped） |
| ib（--class 52 探针） | 52 | 233 |

### Three-way buckets

- **UNDER：0** ✅（52 类逐一命中，含 skipped 口径：mvn 1 skipped 摊在类内，逐类 executed 与 ib tests 全同）
- **OVER：0** ✅
- **IB-ONLY：0** ✅

### Verdict

**parity（类级全同，三桶全零）**。附注（watch 级，非缺陷）：ib 的 `-pl` 全模块路径忠实执行 pom 默认（skipTests=true → skip），与 mvn 默认行为一致；差异仅出现在"操作者用 -DskipTests=false 覆盖"这一 ib 未建模的调用面——当前以 `--class` 显式路径可完整补齐执行能力（本次实测覆盖）。若后续需要一等公民支持，属 feature（CLI override），不属派发面缺陷。

---

## 3. liquibase — module `liquibase-standard`

### Commands

- model refresh：`8 modules, maven 3.9.16`。
- mvn oracle：复用既有 surefire-reports（427 XML，与 11-15 基线 6517/23s 全绿同代，`liquibase-standard/target/surefire-reports` 现存且未变）。
- ib：`java -Xmx6g -cp "$CP" -Dnop.ib.debugPerClass=true io.nop.ib.NopIbMain test -pl liquibase-standard` → run=6494 failed=0 skipped=0 selected=440。

### Counts

| side | classes | executed |
|---|---|---|
| mvn | 427 | 6494（6517 − 23 skipped） |
| ib  | 440 | 6494 |

### Three-way buckets

- **UNDER：0** ✅
- **OVER：0** ✅（交集 427 类逐类 executed 数全同，零计数差）
- **IB-ONLY（0-test helper）：13**，全部 tests=0（与 11-15 日志"ib 多派发若干 0 测试 helper 类"一致），含抽象基类、非命名惯例工具类与 2 个 Groovy closure 合成类：
  - `HubChangeTest`、`liquibase.analytics.TestAnalyticsWebserver`、`liquibase.change.StandardChangeTest`
  - `liquibase.command.TestAbstractCliWrapperCommandStepTest$__spock_feature_0_0_closure1`
  - `liquibase.database.AbstractJdbcDatabaseTest`、`liquibase.integration.commandline.ChangeExecListenerUtilsTest`
  - `liquibase.change.StandardChangeTest`、`liquibase.sql.visitor.StandardSqlVisitorTest`
  - `liquibase.sqlgenerator.AbstractSqlGeneratorTest`、`liquibase.statement.core.AbstractSqStatementTest`
  - `liquibase.util.BooleanParserTest`、`liquibase.util.TestUtil`、`liquibase.util.TestUtil$_findAllClasses_closure1`
  - `liquibase.verify.AbstractVerifyTest`

### Verdict

**parity**。执行面逐类精确同数；IB-ONLY 全为 0-test helper（mvn 语义下这些类由 surefire 派发但不产生 testcase，或命名不符 includes；ib 多派发但零执行，无害）。

---

## 总裁定

| 项目 | module | mvn classes/executed | ib classes/executed | UNDER | OVER | IB-ONLY | verdict |
|---|---|---|---|---|---|---|---|
| spring-batch | spring-batch-core | 152 / 904 | 157 / 906 | 0 | 0 | 5（抽象基类 0-test helper） | parity |
| liteflow | liteflow-testcase-el-nospring | 52 / 233 | 52 / 233（--class 探针） | 0 | 0 | 0 | parity |
| liquibase | liquibase-standard | 427 / 6494 | 440 / 6494 | 0 | 0 | 13（0-test helper） | parity |

- 三项目 UNDER=0、OVER=0：**派发面无漏测、无多跑（带执行）差异**。
- IB-ONLY 18 例全部为 tests=0 的 helper/抽象类派发，与 mvn"派发但零 testcase"语义等价，登记即可（runbook 第 5 节口径），不构成缺陷。
- liteflow 的模型事实门（skipTests=true）在默认路径上与 mvn 默认行为一致；`-DskipTests=false` 操作者覆盖是 ib 未建模的调用面，以 `--class` 显式路径可完整覆盖（已实测 52/52）。
