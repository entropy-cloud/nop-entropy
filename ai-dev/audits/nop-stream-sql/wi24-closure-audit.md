# WI24 Closure Audit——31-wi24-compat-and-docs-closure.md（roadmap 收口终审）

- Audit 日期：2026-10-03（单轮独立 audit）
- Auditor：独立子 agent（fresh session，与实现者非同一 session/task_id；全部结论来自 live repo 实读/实跑，未采信 plan 勾选、日志自述或 commit message）
- 审计对象：分支 add-stream-sql，HEAD `ee32a3f4a9`；审计时工作树含 WI24 待收口交付物（3 M：roadmap 翻转 +6/-3、nop-stream-sql.md §5 兼容迁移节 +12/-2、nop-stream-user-guide.md 回链 +2；1 新增未跟踪：plan 31）——本 audit 按「翻转先行、audit 核实翻转内容与 live 一致、收口 commit 后置」的既定流程执行，未发现越界触碰（零 `_`-前缀生成物、零源码文件改动）；探针在 gitignored `_tmp/audit-wi24/`（`.gitignore:120`）
- **最终裁定：PASS——0 Blocker / 0 Major / 4 Minor（全部为收口时点落笔项，随 Phase 2 消解，详见 §6）。WI24 翻转内容与 live 一致，M5/M6 翻转合法；roadmap 级完成判定四条全部满足，roadmap 收口（roadmap done）成立。**

---

## 0. 裁定摘要

WI24 是本 roadmap 最后一个工作项，交付物为纯文档面：nop-stream-sql.md 新增 §5「兼容与迁移」五条 + §6 compile 签名可空措辞精确化（OBS-2）+ user-guide 回链（OBS-1）。本 audit 逐句对照实码核验 §5 全部六类主张（`<sql>` 唯一内容 fail-fast、SPI classpath 依赖点名模块、九受管类型闭集、D9 allowedLateness 语义消费、D1 last-value-wins、W2/T1 loud 回显），全部与 live 一致。roadmap 级完成判定四条逐条实跑核验：(1) 解析器实跑 items=31/milestones=7/done=31/roadmapAllDone=true；(2) 本报告；(3) hollow 扫描六模块 --severity high 全 0 + invariants 无参全量 exit 0；(4) D1-D15 五份设计文档 15 条裁定逐条有落点且头块均含负责人与日期。翻转核实：WI24 行六项交付主张与 live 一致、M5 解锁条件（Phase 5 七成员全 done）与 M6 解锁条件（WI24 done）均成立——**翻转合法，无需回滚**。

## 1. 兼容迁移节逐句对照 live（审计项 1）——PASS

`docs-for-ai/03-modules/nop-stream-sql.md` §5 五条逐句核验：

| §5 主张 | live 证据 | 一致性 |
|---|---|---|
| `<sql>` 纯增量顶层元素；含 `<sql>` 时模型必须以其为唯一内容，与 transforms/edges 混写 fail-fast | `StreamModelDslBuilder.expandSqlModel`（`:255-269`）：`hasTransforms()/hasEdges()/hasAggregators()/hasJoins()/hasSchemas()/!getWindowingStrategies().isEmpty()` 任一并存即 `ERR_STREAM_INVALID_ARG`（"`<sql>` must be the model's only content"）；`build()` 首步 `:172` 调用；不含 `<sql>` 时 `:257-259` 直接 return（零影响） | ✓（live 拒绝面为 doc 表述的超集——aggregators/joins/schemas/windowingStrategies 并存同样拒绝，doc 摘要为真） |
| classpath 需含 nop-stream-sql，缺失时构建期 `nop.err.stream.invalid-arg` fail-fast，文案点名模块 | `noSqlProvider`（`:315-324`）：容器未初始化或 `tryGetBeanByType(ISqlStreamCompiler.class)` 为 null 均 fail-fast，detail 明文 "add io.github.entropy-cloud:nop-stream-sql to the classpath"；`ERR_STREAM_INVALID_ARG = define("nop.err.stream.invalid-arg", ...)`（`NopStreamErrors.java:72-73`） | ✓ |
| 九受管类型名闭集；DATE/TIMESTAMP/DECIMAL 等集合外类型构建期 fail-fast（不静默近似） | `StreamSchemaRegistry.resolveManagedType`（`:85-108`）恰好九名 string/int/bigint/smallint/tinyint/float/double/boolean/bytes，default 返回 null 由 caller 锚位 fail-fast（`:122-130`）；compiler 侧 `MANAGED_TYPES`（`StreamSqlCompiler.java:89`）+ `validateSchema`（`:959-967`）非闭集内类型 `invalidArg` | ✓ |
| D9：`<window>` 与 `<strategy>` 的 allowedLateness 按语义消费（窗口关闭点推迟至 watermark 越过 lateness 界）；accumulationMode/triggerId/窗口级 parallelism 仍 fail-fast | `AdvancedTransforms.buildWindow`（`:199-206`）：合并语义——节点级显式 Long 覆盖 strategy 级、节点 null 则 strategy 值生效，`windowed.allowedLateness(lateness)`；`WindowOperator.java:1103`「WI10/D9: skip purge when allowedLateness > 0 on an event-time window」+ `:1346` isWindowDone 按 end+lateness 判定（关闭点推迟）；D10/D11：strategy 级 triggerId/accumulationMode 非默认值 fail-fast（`:220-227` `ERR_STREAM_WINDOW_ATTR_UNSUPPORTED`）、节点级 triggerId 同（`:243-247`）；D12：`<window>` parallelism fail-fast（`:176-184` `ERR_STREAM_NOT_IMPLEMENTED`，virtual element 无执行顶点） | ✓ |
| D1：SQL 聚合输出 last-value-wins 终值语义——非 append-only、非 retract；持续聚合逐条 emit 运行值 | `StreamReduceOperator.java:13` 文件级注释「NOT append-only and NOT a retract stream: downstream consumers see intermediate」；`StreamSqlCompiler` javadoc 聚合分派段「last-value-wins final-value semantics (D1)」 | ✓ |
| W2/T1：`TUMBLE` 查询仅流目标可执行；SQL 通道按原样回显 `TUMBLE(...)` 语法（真实 RDBMS 解析即失败——预期边界而非缺陷） | `AstToEqlGenerator.java:645` 与 `AstToSqlGenerator.java:230` 双通道 `visitSqlTumbleTableSource` 均完整回显 `TUMBLE(table.col, INTERVAL n UNIT)`，javadoc 明文「keeps it loud instead of silently dropping」「NOT executable SQL on an RDBMS, which is the documented boundary」 | ✓（loud 回显 + 语义拒绝双事实与 WI20 audit 钉定一致） |

D6 语境 sanity：`OverWindowOperator.java:27-44` javadoc 确认 event-time framed evaluation（D6=(a)）+ keyed MapState 持久化、无 retract markers——与 doc §3 执行语义标注的事件时间框架一致。

## 2. OBS-1 / OBS-2（审计项 2）——PASS

- **OBS-1 回链成立（双向）**：`nop-stream-sql.md:5` 头块「上位文档：`03-modules/nop-stream-user-guide.md`」→ `nop-stream-user-guide.md:269`「……详见 `03-modules/nop-stream-sql.md`」（本 WI 新增 +2 行 diff 确认）。plan In Scope 允许「nop-stream.md 或 user-guide」二选一，user-guide 已落，满足。路由与锚点在案：`docs-for-ai/INDEX.md:139` 路由行、`docs-for-ai/04-reference/source-anchors.md:244-246` STRM-SQL-001/002/003（WI17/WI18 期登记，持续受 doc-links 保护）。
- **OBS-2 措辞与 live javadoc 精确一致**：doc §6「schema 可为 null/empty 表示未声明 schema」↔ `StreamSqlCompiler.compile` javadoc `@param schema ... may be null/empty (no schema declared)`；行为面 `validateSchema` 对 null 直接 return。签名 `compile(loc, sql, schema, sinkBean)` 四参与 live 逐字一致；「sql 与 sinkBean 必填」↔ `:113-119` 两个 blank 检查 fail-fast。diff 同时移除了旧文「四参必填」的失实表述。

## 3. roadmap 级完成判定四条（审计项 3）——PASS

- **(1) 31 项 done + roadmapAllDone=true**：探针实跑 `parseRoadmapMarkdown` + `roadmapAllDone`（复用 `tools/mission-driver/src/roadmap-check.mjs` 唯一实现）→ `items=31, milestones=7, done=31, notDone=[], roadmapAllDone=true`（探针 `_tmp/audit-wi24/parse-roadmap-probe.mjs`）。无静默丢弃（BULLET_RE 陷阱未触发，WI24 行尾括注单层非嵌套）。
- **(2) WI24 独立 closure audit 通过**：即本报告（fresh session，与实现者非同 task_id）。
- **(3) 无 hollow 项**：`scan-hollow-implementations.mjs --severity high` 对 core/runtime/sql/flow/cep/rocksdb 六模块实跑 → **Critical 0 / High 0 / Medium 0 / Low 0 / Total 0**；`check-nop-stream-invariants.mjs` 无参全量（inventory + sync + scan-iterations + scan-output-contract + scan-wiring + check-wildcard-imports×10 模块 + self-test）→ **exit 0**（0 violations）。
- **(4) D1-D15 落档核对**：五份文档头块均含负责人与日期——`sql-subset-and-semantics.md:4-6`（D3=2026-09-30，D1/D4/D5/D6/D15=2026-10-02）、`sql-compiler-contract.md:4-5`（2026-10-02）、`sql-landing-decision.md:4-5`（裁定 2026-09-30/落档 2026-10-02）、`window-failfast-decisions.md:4-6`（2026-10-02）、`sql-vision-conflict-resolution.md:4-5`（2026-10-02）。15 条裁定逐条有落点：D1/D3/D4/D5/D6/D15 → subset §1/§2/§3/§4/§5/§6；D7/D8/D13 → contract §1/§2/§3；D14 → landing §1；D9/D10/D11/D12 → failfast §1/§2/§3/§4；D2 → vision-conflict-resolution §1。**全部在案。**

**翻转合法性**：WI24 行六项交付主张与 §1/§2 核验一致（「独立 closure audit 待本轮」在本 audit 通过后由 MIN-4 收口更新）；M5 解锁条件 = Phase 5 全 done（WI16/17/18/19/20/22/23 均已 done，解析器证实）→ 合法；M6 解锁条件 = WI24 done → 合法。**无需回滚翻转。**

## 4. 文档门禁（审计项 4）——PASS

| 门禁 | 实跑 | 结果 |
|---|---|---|
| doc-links | `node ai-dev/tools/check-doc-links.mjs --strict` | **exit 0**（0 errors；3 warnings 均为 `ai-dev/plans/nop-bytecode/06-resource-leak-v1.md` 既存，与 WI13/WI22/WI23 audit 所记同源，非本 WI 引入）✓ |
| plan-checklist | `node ai-dev/tools/check-plan-checklist.mjs .../31-wi24-compat-and-docs-closure.md --strict` | **exit 0**（active 计划 19 项未勾 + Closure 占位符全为收口时点待办，warning 级合法，符合「审计先于收口」时点）✓ |
| hollow | 六模块 `--severity high` | Total 0 ✓ |
| invariants | 无参全量 | exit 0 ✓ |
| 解析器 | 探针实跑 | 31/7/done=31/allDone=true ✓ |

## 5. 三处一致与 git 纪律（审计项 5/6）——PASS（附收口义务）

- **roadmap WI24 行 vs live**：行内六项主张（§5 六类内容/OBS-1/OBS-2/doc-links 0/audit 待本轮/承载 plan）逐项与实况一致；deps 11 项与依赖图 WI24 入边逐一对应；Item Type Proof 正确。
- **plan 31 vs roadmap**：plan active、Phase 1/2 全未勾、Closure 占位——与「翻转先行、收口后置」的真实进度一致，无提前勾选、无陈旧勾选。
- **日志**：`ai-dev/logs/2026/10-03.md` 尚无 WI24 节——plan Phase 1「日志」与 Phase 2 exit「四条判定核验记录落档（日志）」均为收口时点动作（MIN-3）。
- **git 纪律**：工作树改动恰为 4 个 WI24 交付文件（3 M + plan 31 新增），diff 逐行核对零越界（无源码、无 `_`-前缀生成物、无探针入库——`_tmp/` gitignored）；收口 commit 为既定后置动作（§7）。

## 6. 发现清单

| # | 级别 | 发现 | 修复时点 |
|---|---|---|---|
| MIN-1 | Minor | roadmap 头部「Last updated」头注记缺失——plan 31 Phase 2 第 2 项明文要求随翻转更新，当前仍为「2026-10-02（v8…）」。翻转实质内容合法（§3），但该子项须在收口时实际落笔（建议 v9 注记：WI24 done、M5/M6 达成、roadmap 收口），否则该 plan 勾选项沦为陈旧 | Phase 2 收口时随翻转补记 |
| MIN-2 | Minor | 「四份文档」计数失实：plan 31 `:70`（Phase 2 第 4 项）写「D1-D15 落档核对（…四份文档…）」，实际五份（`sql-vision-conflict-resolution.md` 为 D2 落点；roadmap `:281` 判定 (4) 本身无计数，本 audit 按 2+3+1+4+1=5 份核对全过）。同源陈旧计数亦见于 roadmap v8 头注（「D1-D15 落档于 …四份文档」）与 Rules `:421` 尾注「（四份均为未来交付物）」（前列五份）。plan 31 文本随收口修正；roadmap 两处随 MIN-1 的 v9 头注 / 顺手修正 | Phase 2 收口时修正 |
| MIN-3 | Minor | 日志 WI24 节未落：`ai-dev/logs/2026/10-03.md` 无 WI24 条目——Phase 1「日志」与 Phase 2「四条判定核验记录落档（日志）」待收口落笔（含本 audit 引用的四条判定实跑结果） | Phase 2 收口时落笔 |
| MIN-4 | Minor | roadmap WI24 行「独立 closure audit 待本轮」须随收口更新为 PASS 记录（沿其余行惯例，如「独立 closure audit PASS 2026-10-03」）；改后必须重跑解析器核对 items=31 不变（BULLET_RE 括注约束） | Phase 2 收口时更新 |

无 Blocker、无 Major；无 deferred 项；未发现任何「翻转内容与 live 不符」情形——不触发回滚条件。

## 7. 最终收口动作清单（PASS 后执行）

1. **MIN-1 + MIN-2 落笔**：roadmap 头部补「Last updated: 2026-10-03（v9：WI24 done，M5/M6 达成，roadmap 收口……）」注记，并修正其中与 Rules `:421` 的「四份文档」陈旧计数为五份；plan 31 `:70` 同步改「五份文档」。
2. **MIN-4 更新**：roadmap WI24 行「独立 closure audit 待本轮」→「独立 closure audit PASS 2026-10-03（4 Minor 收口落笔，见 ai-dev/audits/nop-stream-sql/wi24-closure-audit.md）」；保持尾括注单层非嵌套；**重跑解析器断言 items=31/milestones=7/done=31/roadmapAllDone=true**。
3. **MIN-3 落笔**：`ai-dev/logs/2026/10-03.md` 顶部追加 WI24 节（兼容迁移节 + OBS-1/2 + 四条判定核验记录 + 本 audit PASS）。
4. plan 31 Phase 1/2 全勾、Phase Status → `completed`、Plan Status → `completed`；Closure 段回填（Status Note / Completed / Closure Audit Evidence：Reviewer=本 audit，Evidence=`ai-dev/audits/nop-stream-sql/wi24-closure-audit.md` 与 §4 实跑结果）。
5. 双门禁复跑确认 exit 0：`node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-stream-sql/31-wi24-compat-and-docs-closure.md --strict` + `node ai-dev/tools/check-doc-links.mjs --strict`。
6. 提交（含 roadmap 翻转 + 两个 docs + plan 31 + 本报告 + 日志），commit message 注明 WI24 audit PASS、roadmap 31/31 收口。

**roadmap 级完成判定四条在本 audit 通过后全部满足——M6 翻转合法，roadmap 收口成立。**

## 证据索引

- 探针（gitignored）：`_tmp/audit-wi24/parse-roadmap-probe.mjs`（复用 `tools/mission-driver/src/roadmap-check.mjs` 的 `parseRoadmapMarkdown`/`roadmapAllDone`，输出 31/7/done=31/allDone=true）。
- live 代码锚点：`StreamModelDslBuilder.java:172/:255-269/:315-324`（`<sql>` 展开 + 唯一内容 fail-fast + SPI 点名）；`StreamSchemaRegistry.java:85-108/:122-130`（九受管类型闭集 + 首错 fail-fast）；`AdvancedTransforms.java:176-184/:199-206/:220-227/:243-247`（D9 消费 + D10/D11/D12 fail-fast）；`WindowOperator.java:1103/:1346`（清空点推迟）；`StreamReduceOperator.java:13`（非 append-only 非 retract）；`StreamSqlCompiler.java:89/:959-967` + compile javadoc（九类型 + schema 可空）；`AstToEqlGenerator.java:645` / `AstToSqlGenerator.java:230`（W2/T1 loud 回显）；`OverWindowOperator.java:27-44`（D6 事件时间 sanity）；`NopStreamErrors.java:72-73`（`nop.err.stream.invalid-arg`）。
- 文档锚点：`docs-for-ai/03-modules/nop-stream-sql.md` §5（兼容与迁移五条）/§6（OBS-2 措辞）；`nop-stream-user-guide.md:269`（OBS-1 回链）；`INDEX.md:139`；`source-anchors.md:244-246`；五份裁定文档头块（负责人 + 日期）与 15 条落点节号。
