# WI23 Closure Audit——30-wi23-quickstart.md

- Audit 日期：2026-10-03（单轮独立 audit）
- Auditor：独立子 agent（fresh session，与实现者非同一 session/task_id；全部结论来自 live repo 实读/实跑，未采信 plan 勾选、日志自述或 commit message）
- 审计对象：HEAD `5c63c6b883`（分支 add-stream-sql，审计起止工作树 clean；WI23 提交 5 文件 +225/-1——TestStreamSqlQuickstart.java（新）+ sql-quickstart.query.sql（新）+ plan 30（新）+ verify.sh（M +7/-1）+ 日志（M +7），零 `_`-前缀生成物触碰，探针在 gitignored `_tmp/`（`.gitignore:120`）未入库）
- **最终裁定：PASS——0 Blocker / 0 Major / 1 Minor（fraud-example 裁定的 plan Closure 回填与 roadmap 括注尚未落笔，属收口时点动作，随 Phase 2 消解）。** roadmap WI23 完成判定成分全部实证成立：新增 TestStreamSqlQuickstart 用例从 .sql 文件跑到 sink（隔离实跑绿、判别力充分），且已在 quickstart verify.sh step 4 纳入（前置 install 对 nop-stream-sql 的覆盖经 reactor 实证成立）；fraud-example 不纳入裁定已记录于当日日志，事实基础（fraud-example 为 CEP 演示、SQL 子集无 CEP）经实读证实。

---

## 0. 裁定摘要

WI23 的交付物小而完整，且判别力真实。TestStreamSqlQuickstart 以「.sql 文件 → `<sql>` 模型 → build → execute → sink」五步走完用户旅程，隔离实跑（verify.sh step 4 同形态命令）1/1 绿；终态断言 `[a=-1,b=6,c=1]` 与同模块 `TestContinuousGroupByQueryE2E` 钉死的 D1 运行值序列（`a=1,b=2,a=1,a=4,b=6,a=-1,c=1`）按组取末值精确收敛，且具备三向反事实判别力（分组缺失→编译期 fail-fast；聚合缺失→末行值 `[a=-5,b=4,c=1]` 断言红；first-value-wins 语义错→`[a=1,b=2,c=1]` 断言红）。verify.sh step 4 纳入的有效性审计中发现的关键疑问——`install -pl nop-stream -am` 是否涵盖 nop-stream-sql——以 Maven 4.0.0-rc-5 reactor 实跑定案：**涵盖**（reactor 全 11 个 nop-stream 子模块，nop-stream-sql `[452/453]`、fraud-example `[453/453]`），step 1 自动安装可为 step 4 备齐全部上游构件。sql 模块全量 79/79（WI22 基线 78 + 本用例 1，勾稽闭合，零退化），双门禁 0。

## 1. TestStreamSqlQuickstart 与 .sql 资源（审计项 1）——PASS

### 1a. 存在性与隔离实跑

- 用例位于 `nop-stream/nop-stream-sql/src/test/java/io/nop/stream/sql/compile/TestStreamSqlQuickstart.java`（105 行，WI23 提交新增），类名与 roadmap WI23 行明文要求逐字一致。
- 隔离实跑（与 verify.sh step 4 完全同形态：`./mvnw -f pom.xml test -pl nop-stream/nop-stream-sql -Dtest='TestStreamSqlQuickstart' -Dsurefire.failIfNoSpecifiedTests=false`）→ **Tests run: 1, Failures: 0, Errors: 0，BUILD SUCCESS，exit 0**（`_tmp/audit-wi23/mvn-quickstart-isolated.log`）。
- 端到端接线（Anti-Hollow 规则 22/23）经读码成立：`.sql` 资源经 `VirtualFileSystem` 实读（`:75-76`）→ `StreamSqlCompiler.compile(null, sql, schema, "testSink")` 产出 `<sql>` 模型 XML（`:84`）→ `DslModelParser` 解析为 `StreamModel`（`:85-86`）→ `StreamModelDslBuilder.of(model).build()` + `env.execute("sql-quickstart")`（`:89-90`）→ `SqlTestSink.consume()` 收集运行值（`:93-98`）。运行时管线由 `OrdersSourceFunction`（orders bean，`sql-compile.beans.xml:10`，FIXED_DATA 7 条固定记录）驱动至 sink；若管线任一环空壳，sink 为空、`finals={}` 即断言红——空壳无法通过本用例。

### 1b. .sql 文件驱动形态真实性

- `sql-quickstart.query.sql` 独立存在于 `src/test/resources/_vfs/nop/stream/sql/test/`（2 行纯查询文本 `SELECT item, sum(amount) AS total FROM orders GROUP BY item`），全仓仅 TestStreamSqlQuickstart 一处引用——非借用他测试资源，是真正的用户工位产物。
- 用例对该资源有内容级断言（`TestStreamSqlQuickstart.java:77-78`：资源必须含完整查询文本），资源被清空/换文即红——「.sql 文件驱动」不是摆设形态。

### 1c. 判别力（D1 收敛 + 反事实）

- **D1 收敛一致性**：同模块 `TestContinuousGroupByQueryE2E.java:82` 钉死 D1=(a) last-value-wins 的逐条运行值序列 `["a=1","b=2","a=1","a=4","b=6","a=-1","c=1"]`（ orders 同源数据、同查询形态）。按组取末值 = `{a=-1, b=6, c=1}`，与 quickstart 终态断言（`TestStreamSqlQuickstart.java:99-103`）**精确相等**——quickstart 断言即 D1 运行值收敛终态，两测试互相印证。数值勾稽：a 组 1+3+(-5)（null 行跳过）=-1，b 组 2+4=6，c 组 1。
- **反事实 1（分组缺失）**：无 GROUP BY 的全局聚合在 `StreamSqlCompiler.java:284` 编译期 fail-fast（`CompileErrors.invalidArg("global aggregation (aggregate without GROUP BY) ...")`），不存在错误值静默通过。
- **反事实 2（聚合缺失）**：若聚合缺失、原始行直达 sink，finals 按组取末行收敛为 `[a=-5, b=4, c=1]`（a 组末条为 amount=-5 的记录，`OrdersSourceFunction.java:40`）≠ 断言 `[a=-1, b=6, c=1]` → 红。
- **反事实 3（reduce 语义错）**：若 last-value-wins 退化成 first-value-wins，收敛为 `[a=1, b=2, c=1]` ≠ 断言 → 红。

## 2. verify.sh step 4 纳入与 install 覆盖（审计项 2）——PASS

### 2a. step 4 存在性与语法

- `nop-stream/quickstart/verify.sh:54-57` step 4 在案：`test -pl nop-stream/nop-stream-sql -Dtest='TestStreamSqlQuickstart' -Dsurefire.failIfNoSpecifiedTests=false`，与 plan Exit Criteria 所写「step 4：`-pl nop-stream/nop-stream-sql -Dtest=TestStreamSqlQuickstart`」一致；`bash -n` 语法过。
- step 4 不带 `-am`：nop-stream-sql 自身测试类从模块源码编译运行（非 local repo jar），被测代码恒为 live 源码；其上游依赖（pom 实读：flow、orm-eql、runtime、ioc）由 step 1 的 local repo 构件供给。

### 2b. install 覆盖实证（本 audit 关键疑问，reactor 定案）

- 疑问：step 1 自动安装用 `./mvnw install -pl nop-stream -am -DskipTests`（`verify.sh:31/:34`），`-pl` 选聚合 POM 是否连带其子模块？
- **实证**：`./mvnw -f pom.xml -pl nop-stream -am validate`（Maven **4.0.0-rc-5**，exit 0）Reactor Build Order 全量列出 `nop-stream/pom.xml` modules 清单（`:16-28`）的全部 11 个子模块——core、cep、flow、connector-debezium、connector-batch、rocksdb、connector、connector-jdbc、runtime、**nop-stream-sql `[452/453]`**、**fraud-example `[453/453]`**（`_tmp/audit-wi23/mvn-reactor-check.log`）。即 `-pl nop-stream -am` 在本仓 Maven 4 wrapper 下**涵盖 nop-stream-sql**，step 1 自动安装可为 step 4 备齐含 nop-stream-sql 在内的全部构件——集成有效。
- 新鲜度残留风险（非本 WI 引入）：step 1「存在即跳过」在构件已存在的常见路径下不会重装陈旧 jar——这是 verify.sh 既有 AR-28/AR-1 已裁行为（FORCE_REBUILD=1 逃生门，脚本 `:26-28` 注释明文）；且 step 4 测试类从源码编译，陈旧风险仅及上游依赖，不影响 quickstart 用例自身保真。

## 3. fraud-example 裁定（审计项 3）——PASS

- **裁定在案**：`ai-dev/logs/2026/10-03.md:7`——「fraud-example 裁定：不纳入——SQL 窄范围子集（无 CEP）与 fraud-example 的 CEP 演示定位不重叠，纳入需人工改写示例而非自然适配；quickstart 已由本用例覆盖。」
- **事实基础实读证实**：fraud-example 的模式层确实构建于 CEP API——`RapidTransactionPattern.java:18` `import io.nop.stream.cep.pattern.Pattern;`（AccountTakeoverPattern/GeographicAnomalyPattern 同构）；SQL 子集不支持清单明文排除「CEP 级复杂编排」（`ai-dev/design/nop-stream/sql-subset-and-semantics.md:52`，`:89` 再钉「不在本 roadmap」）。「SQL 化 fraud-example 需人工改写而非自然适配、与 `<sql>` 用例覆盖不重叠」的裁定理由与 live 代码一致，结论成立。
- **roadmap WI23 行括注**：本 audit 时点 WI23 行（roadmap `:273`）仍为原始完成判定文本、无裁定括注——与「括注随本 WI 收口更新」的安排一致（Phase 2 动作，见 §7 动作清单第 3 条）。plan Closure 段落同为收口时点回填（见 MIN-1）。

## 4. 实跑与门禁（审计项 4）——PASS

| 项 | 实跑 | 结果 |
|---|---|---|
| TestStreamSqlQuickstart 隔离 | verify.sh step 4 同形态命令 | **1/1 绿，BUILD SUCCESS，exit 0** ✓ |
| nop-stream-sql 全量 | `./mvnw test -pl nop-stream/nop-stream-sql` | **Tests run: 79, Failures: 0, Errors: 0**（`_tmp/audit-wi23/mvn-sql-full.log`）——WI22 基线 78 + 本用例 1，勾稽闭合，零退化，与 plan 声称 79 精确一致 ✓ |
| doc-links 门禁 | `node ai-dev/tools/check-doc-links.mjs --strict` | **exit 0**（0 errors；3 warnings 均在无关的 `ai-dev/plans/nop-bytecode/06-resource-leak-v1.md`，与 WI22 audit 所记同源，非本 WI 引入）✓ |
| plan-checklist | `node ai-dev/tools/check-plan-checklist.mjs .../30-wi23-quickstart.md --strict` | **exit 0**（active 计划 12 项未勾全为 Phase 2/Closure Gates 待收口项，warning 级合法）✓ |
| reactor 覆盖 | `./mvnw -pl nop-stream -am validate` | exit 0，453 项目，nop-stream-sql [452/453] 在列 ✓ |

## 5. git 纪律与 plan/日志一致性（审计项 5）——PASS

- **提交纪律**：WI23 提交 `5c63c6b883` 恰 5 文件（3 新增：用例 + .sql 资源 + plan 30；2 修改：verify.sh + 日志），零 `_`-前缀生成物触碰；探针在 gitignored `_tmp/`（`.gitignore:120`）；工作树 clean。
- **Phase 1 与 live 一致**：4 执行项 + 4 Exit Criteria 勾选经本 audit 逐项复核成立（用例在且隔离绿；step 4 在且 reactor 覆盖实证；裁定在日志；79 绿 + 日志在）。
- **Phase 2 待审计态正确**：Phase 2 `planned` 6 项未勾、Closure Gates 6 项未勾（仅第 4 项「79 绿」已勾且与实测一致）——与「独立 audit 进行中」的真实状态一致，无提前勾选。
- **日志一致性**：`ai-dev/logs/2026/10-03.md:3-8` WI23 节四条（用例形态、step 4、fraud 裁定、79 绿）逐条与 live 实测一致，无失实叙事。
- **roadmap 状态正确**：WI23 行 `todo`（待 audit 后翻转，正确）；deps WI18 已 `done`（依赖满足）；M5 里程碑行 `todo` 待 Phase 5 收口。

## 6. 发现清单

| # | 级别 | 发现 | 修复时点 |
|---|---|---|---|
| MIN-1 | Minor | fraud-example 裁定的两处收口落笔尚未发生：plan 30 Closure 段仍为模板占位（`<<完成时填写>>`/`<<独立子 agent>>`），roadmap WI23 行无裁定括注——Phase 1 第 3 项「裁定记录（plan Closure + roadmap 括注）」已勾 [x]，其实质记录目前仅在当日日志。裁定实质成立（§3），两处落笔本属 Phase 2 收口时点动作，但收口时必须实际落笔，否则该勾选项沦为陈旧 | Phase 2 收口时随翻转一并回填（见 §7） |

无 Blocker、无 Major；无 deferred 项、无 in-scope live defect 被降级。

## 7. 收口动作清单（PASS 后执行）

1. **MIN-1 回填**：plan 30 Closure 段填入 fraud-example 不纳入裁定（引本 audit §3 事实基础）+ Status Note + Completed 日期 + Closure Audit Evidence（Reviewer=本 audit，Evidence=本报告路径 `ai-dev/audits/nop-stream-sql/wi23-closure-audit.md` 与 §4 实跑计数）。
2. roadmap WI23 行 `todo` → `done`，行尾括注 fraud-example 裁定（如「fraud-example 不纳入——SQL 子集无 CEP 与 fraud 演示定位不重叠，见 plan 30 Closure」）；解析器断言 items=31/milestones=7/WI23=done。
3. Plan 30 Phase 2 六项与 Closure Gates 六项勾齐、Phase 2 Status → `completed`、Plan Status → `completed`。
4. `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-stream-sql/30-wi23-quickstart.md --strict` 与 `node ai-dev/tools/check-doc-links.mjs --strict` 双 exit 0 复跑确认。
5. 提交并按惯例 commit message 注明 audit PASS。

## 证据索引

- 探针与日志（gitignored）：`_tmp/audit-wi23/`——`mvn-quickstart-isolated.log`（1/1 绿 + BUILD SUCCESS）、`mvn-reactor-check.log`（`-pl nop-stream -am` reactor 453 项目全列表，nop-stream-sql [452/453] / fraud-example [453/453]）、`mvn-sql-full.log`（79/0F/0E）。
- live 代码锚点：`TestStreamSqlQuickstart.java:75-103`（.sql 实读 + 内容断言 + 终态断言）；`sql-quickstart.query.sql`（独立 2 行查询文本）；`TestContinuousGroupByQueryE2E.java:82`（D1 运行值序列钉）；`OrdersSourceFunction.java:32-43`（FIXED_DATA 7 条）；`StreamSqlCompiler.java:284`（全局聚合 fail-fast）；`verify.sh:54-57`（step 4）；`RapidTransactionPattern.java:18`（fraud CEP 事实基础）；`sql-subset-and-semantics.md:52/:89`（SQL 子集无 CEP）。
