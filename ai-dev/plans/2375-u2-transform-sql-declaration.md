# Plan 2375: U2 transform 级 SQL 声明面（`<sqlTransform>` 顶点 + SPI compileTransform + builder 拼接）

- Status: completed（draft → 对抗性审查 APPROVE WITH CONDITIONS（agent_7a5a3c01，4 Major+2 Minor+3 Nit 全部并入本稿）→ 实现 → 独立关闭审计首轮 REJECT（2 must-fix）→ 修复后复审 APPROVE（agent_8ee9e38e））
- Item Type: Feature
- Roadmap: `ai-dev/backlog/nop-stream-deferred-unlock-roadmap.md` U2
- Related: plan 2362（WI16 Java-only 裁定——本 plan 是其 Deferred 段「Successor Path: owner 重启裁定后按 StreamSqlCompiler 复用路径交付声明面」的落地）；`docs-for-ai/03-modules/nop-stream-user-guide.md` SQL 段；`ai-dev/design/nop-stream/sql-subset-and-semantics.md`
- Owner Direction: 2026-10-10 用户指令「容易实现的部分都写计划自动完成」+ 对 EQL 支持的质疑（= WI0(e) 延期项中 transform 形态的 owner 解除；**顶层 `<sql>` 唯一约束不放宽**——本 plan 新增的是独立 transform 顶点形态，不触碰 generator 语义）

## Purpose

把 WI16 锁定的 `ISqlStreamCompiler` SPI 复用路径延伸到 transform 级：图中任意位置可声明一个 SQL 变换顶点，编译产物（算子链片段）被 builder 拼接进图，替代 Java-only SPI 直调形态。EQL 解析器/求值器/编译器全部复用（`StreamSqlCompiler.compileStatement` 已返回链尾 id 并填充 transforms/edges 列表——片段化是它的自然变体），不新增解析设施。

## Background（代码事实锚点）

- 顶层 `<sql>`（`stream.xdef:117-124`）是 **model generator**：`expandSqlModel`（`StreamModelDslBuilder.java:516-584`）要求其为模型唯一内容，调 `ISqlStreamCompiler.compileSql(sql, schema, sinkBean, emit, fieldLateness)` 五参 SPI，产物为完整 `<stream>` 模型 XML，经 DslModelParser 回灌后**替换父模型全部内容**。
- `StreamSqlCompiler.compile`（sql 模块，:118-212）：EQL `EqlASTParser` 解析→单语句→`compileStatement`（:215）分派四路径（plain pipeline/windowed aggregate/continuous aggregate/join），填充 `transforms`/`edges` XNode 列表并返回**链尾 id**；随后 `appendSink`+注册表（schemas/aggregators/joins/windowingStrategies）组装 `<stream>` XML。
- `compilePlainPipeline`（:446-486）：发射 `sourceNode(srcId, FROM 表名)`→(TUMBLE 则 timestampsNode)→(WHERE 则 filter)→projection，边 (srcId→…) 保留——**source 顶点即天然 entry 标记**。
- UPDATE emit 仅窗口聚合路径合法（`ctx.failFastOnEmitUpdate`）；fieldLateness 仅 TUMBLE 时间列生效（WI25/Q1）；WITH/CTE、ORDER BY/LIMIT、DISTINCT、WINDOW、多语句 fail-fast/dialectNotSupport（既有面不动）。

## Scope

### In Scope

1. **xdef**：`<transforms>` 内新增 `<sqlTransform emit="!enum:...StreamEmitMode=CLOSE" xdef:name="StreamSqlTransformModel" xdef:ref="StreamTransformModel">`，子元素复用 `<schemas>`（StreamSqlFieldModel，含 lateness）+ `<source>`（SQL 文本）。两步再生成（先 `./mvnw install -pl nop-kernel/nop-xdefs -DskipTests`，再 `./mvnw generate-sources -pl nop-stream/nop-stream-flow`；核对恰好预期的新 _gen 文件数）。
2. **SPI 演进**：`ISqlStreamCompiler` 新增抽象方法
   `String compileTransform(String sql, Map<String,String> schema, StreamEmitMode emit, Map<String,Long> fieldLateness)`
   ——契约（审查 M1 修订）：返回**完整合法 `<stream>` 模型 XML**（与顶层同构、经 DslModelParser 可解析，复用全部既有产物校验），其中 FROM 表 source 顶点**保留**且 id 恒为保留常量 `sqlTransformEntry`（SPI 常量 + javadoc 锁定）；**无 sink**。builder 解析后剥离 entry source 顶点，exit=片段内唯一无出边的顶点（不唯一/不存在 typed reject）。UNION（审查 M2）与 join 在片段模式分派处 fail-fast（多 source）；builder 侧兜底校验「恰一个 id=sqlTransformEntry 的 source 顶点」。生产 provider（StreamSqlCompilerProvider）与 flow 测试 fake（FakeSqlStreamCompiler，审查 Mi5）同步实现。
3. **sql 模块实现**：`StreamSqlCompiler.compileTransformFragment`——复用 compile() 的解析/校验/分派（exactly-one-FROM 实锚 :296-300），跳过 appendSink；source 顶点 id 强制 `sqlTransformEntry`；**join 路径与 UNION 路径 fail-fast**（分派点拦截，审查 M2）；组装 `<stream>` XML。
4. **builder 拼接**：`expandSqlTransforms()`（build() 中 expandSqlModel 之后、failFastOnUnsupportedRegistries/buildTransforms 之前）：
   - 顶点前置校验：**入度恰一（0 与 ≥2 都 typed reject，审查 M3）**；入边 partition≠FORWARD 或带 outputTag typed reject（HASH keyBy 效果/outputTag 通道随入边删除会静默丢失，v1 不支持）；≥1 条出边（fan-out 合法）；无 provider typed fail-fast（镜像 noSqlProvider，元素名 `<sqlTransform>`）。
   - 调 SPI → DslModelParser 解析（typed 包装）→ 兜底校验恰一个 `sqlTransformEntry` source 顶点 → 剥离之 → exit=唯一无出边顶点（typed reject）。
   - **片段确定性改名（审查 M4，覆盖 transforms+edges+registries）**：`<顶点id>_<原id>`，同步重写片段内部边端点、strategyRef/aggregatorRef/joinRef/schemaId 引用；「id 冲突 typed reject」降级为防撞安全网。
   - 拼接：删除 sqlTransform 顶点与其入边；片段 transforms 加入；片段内部边 from==entry 的改为上游顶点 id；模型出边 from==sqlTransform.id 重写为 exit。
   - 注册表合并（aggregators/joins/windowingStrategies/schemas），transform id 冲突 typed reject（安全网）。
   - 多个 `<sqlTransform>` 按声明序逐一展开（含两个 windowed 片段共存——审查 M4 测试）。
5. **测试**（三模块，含审查 N9 补充）：
   - sql：`TestSqlTransformFragmentCompile`——片段契约（entry source id=保留常量、无 sink、TUMBLE 路径 timestampsNode 在片段、windowed aggregate 路径 strategy 生成、UPDATE 规则、join fail-fast、**UNION fail-fast（审查 M2）**、WITH/CTE 等既有失败面穿透）。
   - flow：`TestSqlTransformExpansion`——完整模型（bean 源→`<sqlTransform>` WHERE 过滤→sink）展开正确性（entry 剥离、边重写、registeredStream 消费、**改名后 id 断言**）；与既有 transforms 共存；fan-out 下游；链式双 sqlTransform；**两个 windowed 片段共存（审查 M4）**；无 provider/无入边/**入度 ≥2（审查 M3）**/**入边 HASH 或 outputTag reject（审查 M3）**/**`<sql>`+`<sqlTransform>` 并存 reject（审查 N9）** typed fail-fast；反退化钉（拼接边重写断线必红）。
   - runtime：`TestSqlTransformE2E`——内存源→`<sqlTransform>`（TUMBLE 窗口聚合+UPDATE emit）→sink 内容断言。
6. **文档**：user guide 新增「transform 级 SQL 声明面」段（声明形态、entry/exit 语义、与顶层 `<sql>` 的边界、失败面）；`sql-subset-and-semantics.md` 补 transform 形态节（确定项）；vision §六裁决记录补 owner 解除条目；daily log。**审查 N8 注记**：StreamModelLinter/StreamConfValidator 白名单式 WARNING 面对新顶点默认放行、无需改动；R4/R5 提示看不穿 `<sqlTransform>`（展开前模型）属已知盲区，记录即可。

### Out Of Scope

- 顶层 `<sql>` 唯一约束与 generator 语义（不放宽、不修改；既有双钉保持绿）。
- join（多入口）片段、WITH/CTE、ORDER BY/LIMIT、DISTINCT（维持既有 fail-fast/dialectNotSupport）。
- `<schemas>` 注册表引用式 schema（v1 用顶点内联 field 自携带，与顶层 `<sql>` 对称）。
- CI 门禁调整（归 U6 收口统一）。

## Phases

### Phase 0: 模型面（xdef + 再生成）
- [x] xdef 增 `<sqlTransform>` 顶点声明（属性 emit + 子 schemas/source）
- [x] 两步再生成；核对新增 _gen 文件（`_StreamSqlTransformModel.java` 等，记录确切数量）
- [x] flow 模块编译通过

### Phase 1: SPI 与 sql 实现
- [x] `ISqlStreamCompiler.compileTransform` 抽象方法 + javadoc 契约
- [x] `StreamSqlCompiler.compileTransformFragment`（含 join fail-fast、source 剥离、片段组装）
- [x] `TestSqlTransformFragmentCompile` 全绿（8/0）

### Phase 2: builder 拼接
- [x] `expandSqlTransforms()`（校验/改名/拼接/合并/typed fail-fast 全套）
- [x] `TestSqlTransformExpansion` 全绿（审计 must-fix 2 补 id 冲突钉后 10/0；变异探针：拼接重写断线→3 错红→恢复→绿）

### Phase 3: runtime E2E + 回归
- [x] `TestSqlTransformE2E` 全绿（审计 must-fix 1 补双 windowed 片段共存后 2/0，真实编译器 TUMBLE UPDATE 终值断言）
- [x] flow 全量 307/0 + sql 全量 165/0（fork+JDK25 关单前重跑实测）零回归（TestSqlModelExpansion 双钉绿）

### Phase 4: 文档与收口
- [x] user guide / sql-subset-and-semantics §8 / vision §六 2026-10-10 解除条目 / daily log
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2375-*.md --strict` 退出码 0
- [ ] roadmap U2 勾选 + 单次提交

## Exit Criteria

- [x] `<sqlTransform>` 声明→构建→执行全链可用（sql 模块 E2E sink 内容断言——runtime 归 sql 模块因 pom 依赖方向）
- [x] 顶层 `<sql>` 既有行为零回归（TestSqlModelExpansion 双钉绿，audit 亲跑 5/0）
- [x] 全部 typed fail-fast 有断言（无 provider/无入边/多入边/id 冲突/join 片段/UNION/UPDATE 误用/入边 HASH/outputTag/终点不唯一）
- [x] 测试全绿（sql 165/0 + flow 307/0，12 场景 15/0 无 skip）
- [x] 文档四处同步 + 两工具退出码 0（audit 复跑确认）

## Closure Gates

- [x] 无静默降级：join/UNION 片段等不支持项全部 typed fail-fast 且有测试断言
- [x] SPI 演进记录（compileTransform 新增抽象方法；生产 provider StreamSqlCompilerProvider + flow 测试 fake 同步实现——审查 Mi5 修订）
- [x] 变异探针：拼接边重写断线→3 错必红→恢复→复绿（记录于 Closure Evidence 与 daily log）
- [x] 独立子 agent closure audit 通过（首轮 REJECT 2 must-fix 修复后复审 APPROVE）后勾选 roadmap U2

## Deferred But Adjudicated

### join（多入口）片段 / UNION 片段

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 片段单入口契约（builder 拼接语义为单 entry 设计）；多入口拼接需 entry 集合语义设计。typed fail-fast 已钉（TestSqlTransformFragmentCompile.joinFragmentFailsFast/unionFragmentFailsFast）。
- Successor Required: `yes`
- Successor Path: 真实多流 SQL 融合诉求出现时设计多 entry 拼接协议（graph 层 `<joins>`/`<union>` 为既有替代）。

## Non-Blocking Follow-ups

（执行结束时填写）

## Closure

Status Note: transform 级 SQL 声明面落地：xdef `<sqlTransform>` 顶点（emit+schemas/source；StreamSqlFieldModel 提级共用 define）+ SPI `compileTransform` 片段契约（完整 stream 模型形态产物、entry source 保留 id、无 sink、UNION/JOIN fail-fast）+ `expandSqlTransforms` 拼接（入度恰一/FORWARD 无 outputTag 校验、顶点 id 前缀全量改名、原位拼接、注册表合并、终点唯一性）+ 三层测试（sql fragment 契约 8 + sql E2E 2 + flow expansion 10）。对抗性审查 APPROVE WITH CONDITIONS（4 Major+2 Minor+3 Nit 全并入）；独立 closure audit 首轮 REJECT（2 must-fix：双 windowed 片段共存缺失/id 冲突钉缺失）修复后复审 APPROVE。顶层 `<sql>` 唯一约束与双钉不动。
Completed: 2026-10-10

Closure Audit Evidence:

- Reviewer / Agent: agent_8ee9e38e（独立 fresh-session 关闭审计——首轮 REJECT 2 must-fix，修复后复审 APPROVE；对抗性计划审查 agent_7a5a3c01）
- Audit Session: agent_8ee9e38e-ec23-4b5f-adad-76065961f94c
- Evidence:
  - audit 亲跑（复审轮，fork+JDK25）：TestSqlTransformE2E 2/0（含 must-fix 1 双 windowed 片段共存——t1_ws0/t2_ws0 注册表改名合并断言）+ TestSqlTransformExpansion 10/0（含 must-fix 2 fragmentIdCollisionFailsFast）+ TestSqlTransformFragmentCompile 8/0 + TestSqlModelExpansion 双钉 5/0
  - 关单前全量重跑：sql 165/0 + flow 307/0；12 场景套件 15/0 无 skip（E3 defect 钉已启用）
  - 变异探针：expandSqlTransforms 拼接边重写（e.setFrom(exit)）断线→TestSqlTransformExpansion 3 错必红→恢复→复绿
  - 文档四处：user guide（裁定段改写+`<sqlTransform>` 形态节+支持矩阵行）、sql-subset-and-semantics §8、vision §六 2026-10-10 解除条目、daily log 2026-10-10
  - 存量发现（与 U2 无关，stash bisect 证实）：E1-E12 fixture 落地（WI21-WI26）未重跑 TestXdslCorpusLintGate——10 条 R2/R4/R5 WARNING 白名单补登记（E3/E8 全量上下文不产出）
- `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2375-u2-transform-sql-declaration.md --strict` 退出码 0（Closure Evidence 回填后复跑确认）
Completed: （未完成）
