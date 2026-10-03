# 02 EQL WI0 — 多方言验证骨架与前置决策

> Plan Status: active
> Last Reviewed: 2026-10-03
> Source: `ai-dev/backlog/eql-cross-db-compat-roadmap.md`（WI0 节，v7.2）
> Related: `ai-dev/analysis/2026-10/2026-10-02-eql-cross-database-compatibility-syntax-research.md`

## Purpose

把 roadmap WI0 收口：建立后续所有文法/方言 WI 都要依赖的验证骨架（方言能力矩阵棘轮 + 既有 EQL 语料基线快照），堵住 `TemplateSQLFunction` 的静默 `null` 注入口，完成 G-1..G-4 四门书面裁决并回写 roadmap，固定 `nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4` 注释规则的上游溯源事实。

## Current Baseline

- 18 个 `*.dialect.xml`（`nop-persistence/nop-dao/src/main/resources/_vfs/nop/dao/dialect/`），其中 4 个不可实例化：`default`（缺 3 个 mandatory 子节点）、`db2`（缺 `driverClassName`/`jdbcUrlPattern`，但其 selector 已注册 ⇒ DB2 自动选择当前是坏的，WI3 复现）、`geo-support`/`window-expr-support`（mixin）；可加载 14 个。
- `DialectManager` 只有 `getDialect(name)`（按路径 `/nop/dao/dialect/{name}.dialect.xml` 经 `ResourceComponentManager` 加载），**无枚举 API**；VFS 枚举先例：`loadDialectSelectors()` 用 `VirtualFileSystem.getAllResources("/nop/dao/dialect/selector", ".selector.yaml")`。
- `TemplateSQLFunction.buildFunctionExpr`（`nop-persistence/nop-dao/.../function/TemplateSQLFunction.java`）对越界 `{n}` marker 静默产出 `" null "`；构造函数已解析 markers 但不校验越界。
- `nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4` 的 4 条 SQL-92 函数规则（positionFunction/substringFunction/extractFunction/trimFunction）自首次提交（`aa1b310122`，2022-06-27 "增加antlr解析配置示例"）起即为注释态，从未生效（`git log -S/-G` 仅命中该 commit）。
- `nop-stream-runtime` 有 3 项与本 roadmap 无关的既有失败测试（`TestE2EJdbcTwoPhaseCommitSink` / `TestCheckpointInheritedTwoPhaseCommitRestore` / `TestStreamConnectorRegistryDiscovery`，源自 commit `3c542f7046`），尚未在本工作区实测记录。
- `EqlASTKind` 常量 67 个，ordinal 已被 `add-stream-sql` 合并重编号（插入点 30、53-56），禁止持久化/跨版本比较——须记入基线快照文档。
- 仓库无任何"方言能力矩阵"或"EQL 语料回显基线"快照；`ai-dev/audits/` 目录存在。

## Goals

- 落地方言能力矩阵测试 + 棘轮快照（快照存 `ai-dev/audits/`），新增方言/函数不同步快照时门禁转红。
- `TemplateSQLFunction` 越界 marker 在**方言加载期**抛错；全方言参数化单测覆盖。
- G-1..G-4 四门书面裁决，结论回写 roadmap。
- `nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4` 上游溯源（git 证据）记入 owner doc；既有 EQL 语料 `toSqlString()` 基线快照落 `ai-dev/audits/`。
- 实测记录 `nop-stream-runtime` 3 项既有失败的当前基线。

## Non-Goals

- 不修任何 A/B 类缺陷（WI1/WI2）；不改任何方言 XML 配置值（WI3）；不修 `db2` selector 断链（WI3，本 WI 只记录复现步骤所需事实）。
- 不做 `INTERSECT` 版本门、不做 `greatest`/`least` NULL 策略（G-2 维持 open，ledger 项不动）。
- 不引入真实多数据库 CI（真库执行证据属 WI5/WI6/WI11 的 `TestDialect.testFunctions` 路径）。

## Scope

### In Scope

- `nop-dao` 新增矩阵测试；`TemplateSQLFunction` 加载期校验 + 错误码；`nop-orm-eql` 新增语料快照生成/比对测试；`ai-dev/audits/eql-wi0/` 下两份快照；`ai-dev/tools/check-eql-dialect-matrix-snapshot.mjs` 门禁脚本；owner doc 溯源小节；roadmap 四门裁决回写；daily log。

### Out Of Scope

- 所有其他 WI 的交付物；`.github/workflows/` 大改（若 maven.yml 已跑全量测试则矩阵测试自动进 CI，仅在缺失时补 job）。

## Execution Plan

### Phase 1 - 方言能力矩阵棘轮测试与快照

Status: completed
Targets: `nop-persistence/nop-dao/src/test/java/io/nop/dao/dialect/TestDialectCapabilityMatrix.java`、`ai-dev/audits/eql-wi0/dialect-capability-matrix.json`、`ai-dev/tools/check-eql-dialect-matrix-snapshot.mjs`

- Item Types: `Feature`、`Proof`

- [x] 矩阵测试（无需真实 DB）：VFS 枚举 `/nop/dao/dialect/*.dialect.xml` 全部 18 个资源，逐个 try-load；可加载者对 model 函数表与运行时 accessor 做一致性断言（`DialectModel.getFunctions()` 的每个名字经 `DialectImpl.getFunction(name)` 可取到，且无多出的函数）。
- [x] 快照内容：每个方言的 loadable/extends/函数名排序清单；4 个不可实例化方言及其原因（异常类型/缺失 mandatory 节点名）；selector 清单（含 `db2` → DB2 的现存映射，供 WI3 复现）。
- [x] 快照落 `ai-dev/audits/eql-wi0/dialect-capability-matrix.json`（单一事实源，测试经 repo-root 定位读取并比对，drift 即 fail 并提示更新快照）；**不得**把 "14"/"18" 写死为断言。
- [x] 初始化先例：照 `TestDialectWriteIndex`（`CoreInitialization.initialize()` + `DialectManager.getDialect("h2")`、零 DataSource），**不是** `TestDialectSelector`（纯 POJO 无初始化）。
- [x] 不可实例化方言只记录 error code + 缺失的 mandatory 节点名，**不记异常 message 全文**（避免棘轮噪音）。
- [x] mjs 门禁脚本：校验快照存在、JSON 合法、仓库内每个 `*.dialect.xml` 都在快照中登记（新增方言必须同步快照，否则 exit 1）。
- [x] CI：确认 `.github/workflows/maven.yml` 的既有构建会执行该 JUnit 测试（若已覆盖，记录结论即可，不重复加 job）。
- [x] `es` 伪方言按红线不参与能力矩阵统计（快照中单独标注 `pseudo: true`）。

Exit Criteria:

- [x] `TestDialectCapabilityMatrix` 全绿且对快照 drift 会红（手工改动快照一个函数名可验证转红，验证后还原）。
- [x] 快照 JSON 中可加载数 + 不可加载数 = 18，与 roadmap 基线（14+4）一致；`db2` 的 selector 断链事实（selector 已注册 + 方言不可加载）在快照中可见。
- [x] mjs 门禁脚本对当前仓库 exit 0；临时删除快照中一个方言条目可使其 exit 1（验证后还原）。
- [x] `ai-dev/logs/2026/10-03.md` 已更新。

### Phase 2 - TemplateSQLFunction marker 加载期校验

Status: completed
Targets: `nop-dao/.../function/TemplateSQLFunction.java`、`nop-dao` 错误码登记、`TestSQLFunction.java` 或新测试类

- Item Types: `Fix`

- [x] 构造期校验：任一全数字 marker 的索引 ≥ `argTypes.size()` 时抛 `NopException`（新错误码 `nop.err.dao.func-template-marker-out-of-range`，param 带 funcName/模板源/索引），使坏模板在**方言加载期**失败而非查询期静默产出 `" null "`。错误码定义遵循 `DaoErrors.java` 既有惯例（`ErrorCode.define` + `@Locale` 注解风格与全文件一致），并满足 `ai-dev/tools/check-error-param-consistency.mjs` 门禁。（注：该工具 registry 仅识别 `NopMetadataArgs/NopMetadataErrors`，对 nop-dao 结构性不适用、报 "registry empty"；其支持范围 nop-metadata 实测仍 exit 0 无新问题，新 define 的 placeholder↔param 已按 define-face 规则人工核对对称。）
- [x] 既有行为保持：合法 marker（索引 < argCount）替换逻辑不变；`{name}` 形式非全数字 marker 的既有跳过行为不变（记录该行为于测试注释）。
- [x] 参数化单测：遍历全部可加载方言的每个 `<template>` 函数，构造 `TemplateSQLFunction` 断言不抛错（守护现存模板全部合法，实测 14 个可加载方言共 22 个 template 函数）；另设越界模板用例断言**构造期**抛错。

Exit Criteria:

- [x] 新增负向用例：越界 marker 模板构造时抛错（错误码可断言）。
- [x] 全方言参数化守护测试全绿（18 个资源中的可加载者全部覆盖）。
- [x] `./mvnw test -pl :nop-dao` 相关测试全绿。
- [x] `ai-dev/logs/2026/10-03.md` 已更新。

### Phase 3 - 既有 EQL 语料基线快照 + 溯源记录 + stream 基线

Status: planned
Targets: `nop-orm-eql` 新增快照测试、`ai-dev/audits/eql-wi0/eql-corpus-baseline.md`、`docs-for-ai/02-core-guides/eql-and-database-compatibility.md`

- Item Types: `Proof`、`Decision`

- [ ] 语料快照测试（`nop-orm-eql`）：**解析机制**——`SqlLibModel` 在 nop-orm 模块、7 个语料文件分属 5 个其他模块，均不在 nop-orm-eql classpath；因此用 XNode 通用 XML 解析 + repo-root walk-up（自 cwd 向上找 `ai-dev/`，先例 `nop-stream-core` 的 `InvariantTableCompleteness.findInventoryFile()`）定位语料文件，抽取 `<eql>` 元素文本，经 `EqlASTParser.parseFromText` 解析、`toSqlString()` 回显。
- [ ] **模板归一化策略**（先归一化再解析，保证参数化语句不退化为 parse-error 记录）：① 递归把 `<eql>` 体内嵌套的动态子元素（如 `<sql:filter>`）替换为其内部文本（剥标签留内容）；② `${...}` XPL 占位符统一替换为哑字面量 `1`；③ 注释剥离。归一化规则写进快照文档头；对归一化后仍解析失败的语句记录确定性错误文本。
- [ ] 快照落 `ai-dev/audits/eql-wi0/eql-corpus-baseline.md`（每文件每语句：归一化源 → 回显文本或错误），测试比对 drift 即 fail；快照文档头记录两条红线事实（见下）。
- [ ] 快照文档头记录两条红线事实：`EqlASTKind.ordinal()` 不稳定（67 常量、已被合并重编号、禁止持久化比较）；`nop-stream-runtime` 3 项既有失败是基线豁免（附实测输出摘要）。
- [ ] 实测 `nop-stream-runtime` 3 个测试类当前状态并记录（若本地依赖不全则先 `-am install` 一次性补齐）。
- [ ] owner doc 新增"语法溯源"小节：`nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4` 4 条 SQL-92 函数规则自 `aa1b310122`（2022-06-27）首次提交即为注释态、从未生效（附 `git log -S` 证据结论），不得当作"EQL 曾正确实现过"的先例。（✅ 已由 Phase 4 一并完成，见该节勾稽）

Exit Criteria:

- [ ] 语料快照测试全绿且对回显文本 drift 会红（临时改动快照一行可验证转红，验证后还原）。
- [ ] 快照覆盖 7/7 个 sql-lib.xml，逐文件列出语句数与回显文本（或 parse-error 记录）。
- [ ] `nop-stream-runtime` 3 项失败的实测结果已记入快照文档（class 名 + 失败摘要）。
- [ ] owner doc 溯源小节含 git 证据结论。
- [ ] `ai-dev/logs/2026/10-03.md` 已更新。

### Phase 4 - G-1..G-4 四门书面裁决与回写

Status: completed
Targets: `ai-dev/design/eql-cross-db-compat/2026-10-03-wi0-gate-adjudications.md`、roadmap Gate 表

- Item Types: `Decision`

- [x] G-1：采纳 roadmap 已确认的最低版本门（Oracle 23ai / MySQL 8.0.31 / MariaDB 10.2 / MariaDB 13.0（UPDATE RETURNING）/ PG 15 / MSSQL 2022 / SQLite 3.39）为承诺基线；全部 `unverified` 边界维持 `unverified` 纪律（不计入覆盖率），逐条列出。
- [x] G-2：**结论 = 维持 open**——`greatest`/`least` 已在 ledger 延后且不阻塞任何 WI；回写 roadmap：G-2 的"阻塞"列改为"仅 ledger 项，无 WI 阻塞"，产品侧后续裁决后按 successor 路径（WI6 同批）重开。
- [x] G-3：给出证据化影响分析（别名只扩合法 `stdSqlType` 接受集、不改变既有合法取值的映射；114 个 orm.xml + `clean install` 全量回归作为落地门），**结论 = 接受** `TEXT→CLOB`/`NUMBER→NUMERIC`/`BOOL→BOOLEAN` 别名进入 WI9a；WI9b 时区类型不受 G-3 别名影响但共享该结论的回归门。（实测 TEXT/NUMBER/BOOL 全仓 orm.xml 出现 0/0/0 次）
- [x] G-4：**结论 = 批准 WI3 按表逐项纠错**（7 项改值均有调研证据链）；版本门形态与 G-1 一致；记录"执行授权来自用户对 roadmap 的执行指令 + 逐项证据，全程可 git 回滚"。
- [x] 四门结论回写 roadmap：Gate 表"阻塞"列与 WI0 交付 3 勾稽；不删除 Gate 行本身（保留溯源）。（commit `abbdeabf32`）

Exit Criteria:

- [x] 裁决文档四门各有结论 + 证据引用 + 回滚方式。
- [x] roadmap Gate 表与裁决文档一致（无"仍阻塞但已裁决"的矛盾）。
- [x] `ai-dev/logs/2026/10-03.md` 已更新。

## Closure Gates

- [ ] Phase 1-4 全部 Exit Criteria 勾选完毕。
- [ ] `./mvnw test -pl :nop-dao,:nop-orm-eql -am -T 1C` 全绿。
- [ ] `node ai-dev/tools/check-eql-dialect-matrix-snapshot.mjs` exit 0。
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict`：新增 broken link = 0（基线 3 项不动）。
- [ ] roadmap WI0 状态改 `done` 前完成独立 closure audit（不同 task_id 子 agent）。
- [ ] Anti-Hollow Check：矩阵测试真实加载方言模型（非读文件比对）；marker 校验在加载路径上真实触发（参数化测试证明）；快照测试真实解析 EQL 语料。

## Deferred But Adjudicated

### G-2 `greatest`/`least` NULL 策略

- Classification: `watch-only residual`
- Why Not Blocking Closure: 该项不属任何 WI，ledger 已登记 successor（WI6 同批）。
- Successor Required: `yes`
- Successor Path: G-2 裁决后进 WI6 同批（roadmap「明确延后项」第一行）。

## Non-Blocking Follow-ups

- `db2.selector.yaml` 断链修复属 WI3（本 WI 只在快照中记录事实与复现路径）。

## Closure

Status Note: （closure audit 通过后填写）
Completed:

Closure Audit Evidence:

- Reviewer / Agent:
- Evidence:
