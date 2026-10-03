# WI19 Closure Audit——27-wi19-delta-verification.md

- Audit 日期：2026-10-03（单轮独立 audit）
- Auditor：独立子 agent（fresh session，与实现者非同一 session/task_id；全部结论来自 live repo 实读/实跑与自建探针，未采信 plan 勾选、日志自述或 commit message）
- 审计对象：HEAD `50721c5bb4`（分支 add-stream-sql，审计起止工作树 clean；WI19 提交 5 文件 +311/-0——测试 + classpath 资源两件 + plan + 日志，零生产代码改动、零生成物触碰）
- **最终裁定：PASS——2 Minor 收口必改项（纯 plan/日志措辞与已交付设计的对齐，随 Phase 2 收口一并执行，不阻断翻转）；2 观察项不阻断。** roadmap WI19 完成判定三成分全部实证成立：编译产物可被 Delta 覆盖定制且有用例（三测试隔离实跑 3/3 绿，判别力反事实逐一核过）、§八 10 显式验证（merge 在 parse 阶段 XNode 层、无 runtime patch 通路、单一 env 全新构建）、单一确定结论（Goals 单句结论，全文无二选一判据）。

---

## 0. 裁定摘要

WI19 的交付物（TestDeltaOverCompiledProduct 三测试 + 两件 classpath 资源）成立且判别力充分：防陈旧钉经审计侧独立探针复现（fresh compile 943 字节 vs 提交资源 1259 字节，RAW 不等、normalize 后全等——差异仅为溯源注释头；红态探针变体查询 normalize 不等，证钉确会红）；合并结构断言对「merge 未生效」三种失败形态（wi19DropB 缺席 / e_out 未重定向 / 新边缺失）各自必红；delta 单次执行对「merge 未生效（b 行在场）/ 边未重定向（b 行在场）/ 编译 WHERE 缺失（a=-5 与 a=null 行在场）」必红，且实测输出 [a=1,a=3,c=1] 恰等于「推导 base 输出减 b 行」。§八 10 证据形态成立：x:extends 合并发生在 `DslModelParser → DslNodeLoader → XDslExtender/DeltaMerger` 的 XNode 层（对象转换之前），测试全程只解析模型资源并从合并模型全新构建一个 env，builder 对模型只读消费，平台无 runtime object patch 通路。发现的 2 Minor 均为 plan/日志内残留的「双执行」旧设计措辞与「base/delta 均为全新 env 构建」失实从句——执行期修正本身已如实记录（Phase 1 item 3 与日志同日条目），但 plan 的 In Scope、Phase 1 Exit Criteria 第 1 条与 Closure Gates 第 1 条三处仍按未修正前的「双执行行为断言」表述且已被勾选/留作收口判据，按 guide 规则 18/19（文本一致性）须在翻转前改齐；不改则收口时勾选的 Closure Gate 将断言一个不存在的设计。

## 1. TestDeltaOverCompiledProduct 三测试（审计项 1）——PASS

**隔离实跑**：`./mvnw test -pl nop-stream/nop-stream-sql -am -Dtest=TestDeltaOverCompiledProduct -Dsurefire.failIfNoSpecifiedTests=false` → `Tests run: 3, Failures: 0, Errors: 0, Skipped: 0`，exit 0（`_tmp/audit-wi19/test-delta-isolated2.log`）。

### 1a. 防陈旧钉 committedProductMatchesFreshCompile——PASS（独立探针复现）

- 测试逻辑：`StreamSqlCompiler.compile(null, "SELECT item, amount FROM orders WHERE amount > 0", LinkedHashMap{item:string, amount:int}, "testSink")` 与 `VirtualFileSystem.getResource("/nop/stream/sql/test/wi19-compiled-base.stream.xml").readText()` 经 `normalize`（去 `<!--...-->` 注释 + 折叠空白）后 `assertEquals`。schema 用 `LinkedHashMap` 定序（日志所称「首版 Map.of 顺序不定已修」与现文件相符——现文件无 Map.of 残留）。
- **审计侧独立探针**（`_tmp/audit-wi19/ProbeFreshCompile.java`，自建 javac/java 经 `dependency:build-classpath` 类路径直跑，不经由被审计测试本身）：fresh 输出 943 字节、提交资源 1259 字节，**RAW 不等 / normalize 后全等**——差异恰为提交资源的溯源注释头与缩进，与 normalize 语义精确一致（探针输出 `_tmp/audit-wi19/probe-out.txt`、归一对比脚本内联于会话）。
- **红态探针（判别力反事实）**：变体查询 `WHERE amount > 1` 的 compile 输出对提交资源 **normalize 后不等**（`_tmp/audit-wi19/probe-red.txt`）——编译器输出漂移（无论来自编译器演进还是资源陈旧）都会使 normalize 比较失败即红，强制重生成资源。normalize 只豁免注释与空白（语义中性），不豁免任何结构性差异。

### 1b. 合并模型结构断言 mergedModelCarriesTheDeltaTopology——PASS

断言五件套（`TestDeltaOverCompiledProduct.java` :99-124）：`wi19DropB` 在合并 transform 集（:104-106）；编译产物四 transform `sflt/ssrc/sproj/out` 存活（:108-112）；`e_out` 重定向为 `sproj → wi19DropB`（:114-116）；新边 `e_wi19: wi19DropB → out`（:117-119）；`delta.getSql()` 为 null（:122，编译产物的 `<sql>` 已被 WI17 编译器在 merge 前消费）。

- **判别力反事实**：若 x:extends 被忽略（delta 未合并、base 未加载或仅加载其一），`wi19DropB`/重定向 `e_out`/`e_wi19` 三者不可能同时在场——三条 assertTrue 各自独立必红；解析失败则测试直接 error 红。无假绿路径。
- merge 真实发生的机制锚：`DslModelParser`（`nop-kernel/nop-xlang/.../xdsl/DslModelParser.java`）`modelLoader = DslNodeLoader.INSTANCE`（:46）→ `DslNodeLoader.java` :95 `new XDslExtender(keys).xtend(...)` → `XDslExtender` 持 `DeltaMerger` 在 **XNode 层**执行 x:extends 展开，随后才 `DslBeanModelParser.transformToObject` 生成 `StreamModel` 对象——merge 先于对象构造，结构断言读到的是合并产物。

### 1c. delta 单次执行断言 deltaExecutionRunsTheAddedFilterOverTheCompiledTopology——PASS

- 数据面（`OrdersSourceFunction.FIXED_DATA`）：`a=1, b=2, a=null, a=3, b=4, a=-5, c=1`。base 产物（WHERE amount > 0，三值守卫 source 钉在产物 `<filter id="sflt">` 内）推导输出 = `{a=1, b=2, a=3, b=4, c=1}`；delta 合并模型执行断言恰为 `[a=1, a=3, c=1]`（:143，排序后全等）。
- **判别力反事实（逐一核过）**：
  - merge 未生效 / wi19DropB 未运行 → `b=2`、`b=4` 在场，行数与内容均不符 → 红；
  - e_out 未重定向（wi19DropB 成孤儿、sproj 直连 out）→ b 行在场 → 红；
  - 编译产物 WHERE（sflt）未运行 → `a=-5` 与 `a=null` 行在场 → 红；
  - sink 非冷实例 → `SqlTestSink` 为实例级 `synchronizedList`（非 static，`SqlTestSink.java` :23），bean 每容器单例、测试类建新容器 `wi19-delta-compiled` → 冷实例，无宿留污染。
- **单次执行设计的诚实性**：本地 runner「同 JVM 仅首次 execute 交付」为在案限制（plan 13 `13-wi6-union-multi-input.md` :10/:118 明文：「本地 runner 同 JVM 仅首次 execute 交付（既有 reduce 管线复现，与 union 无关）」，与 union 无关的平台限制）——日志与 plan item 3 对该限制及「改判别性等价设计」的引用**准确**（审计侧逐一核过 plan 13 原文）。该设计以单次执行同时覆盖两个命题：编译拓扑真实运行（WHERE 生效 = a=-5/a=null 缺席）与 delta 定制真实生效（b 行缺席），加上结构断言钉住四 transform 存活与边链 `ssrc→sflt→sproj→wi19DropB→out`，判别性等价成立。

## 2. §八 10 单一结论的诚实性（审计项 2）——PASS（附 OBS-1 措辞观察）

- **单一结论在案**：plan Goals 单句——「编译产物可被 Delta 覆盖定制……执行行为可观察地不同」；全文无二选一判据；roadmap WI19 完成判定「判据为前句两项，不留二选一判据」与 plan 内容相符。
- **Delta 全程模型层的证据充分**：
  1. merge 发生在 parse 阶段 XNode 层（§1b 机制锚：XDslExtender/DeltaMerger 作用于 XNode，`StreamModel` 对象在其后构造）——「xdef 校验的 XML 解析与合并」表述与 live 调用链一致；
  2. 测试全程的唯一运行时动作是 `StreamModelDslBuilder.of(delta).build()` + `env.execute("wi19-delta")`——从合并模型全新构建一个 env；无任何 API 触碰已构建 runtime object；builder 对模型只读消费（`StreamModelDslBuilder` 无 model patch 面，grep 无 setTransform/addTransform 式运行时变异通路）；
  3. 反证测试的豁免（Non-Goals：「机制上不存在该通路」）经核成立——Delta 机制在平台内只有资源/XDSL 合并一种形态，无 runtime patch 入口。
- **「base 行为由 WI17 E2E 钉住」论证的核验**：WI17 既有钉真实在案——`TestTumbleAggregateQueryE2E`/`TestContinuousGroupByQueryE2E`/`TestJoinQueryE2E`/`TestUnionQueryE2E` 各以 `compile → parse → build → execute → sink` 钉住编译产物的端到端执行（TUMBLE 聚合/持续聚合/join/union 四形态），其中 `TestUnionQueryE2E` 第一分支即同一 `orders` 源 + 同一 `WHERE amount > 0` 过滤形态，`TestStreamSqlCompiler` 的 M2 等价矩阵钉住三值守卫语义。**OBS-1（不阻断）**：严格说没有既有测试单独执行过「本条 plain filter+projection 产物」的未定制版本——但本设计的 delta 单次执行本身就是该编译拓扑全链的 live 执行证据（输出 = 推导 base 减 b 行，WHERE 生效与 sink 行内容逐行可判），完成判定「可被覆盖定制且行为可观察地不同」由 `(delta 实测输出) + (base 输出的可推导性)` 直接支撑，结论不变；javadoc :54 「already pinned by the WI17 E2Es」宜读作「同族产物执行与同 WHERE 语义已被钉」，下次触碰该文件可顺带精确，不要求本次修改。

## 3. 资源与编译器一致性（审计项 3）——PASS

审计侧自行实跑一次 fresh compile（独立探针，§1a）与提交资源对比：**normalize 全等**，RAW 差异仅为资源头部 6 行溯源注释与缩进。提交资源即当前 `StreamSqlCompiler` 对该查询（同 schema 定序、同 sinkBean）的确定性输出。

## 4. 实跑（审计项 4）——PASS（计数以实跑为准）

| 范围 | 实跑结果 | 判定 |
|---|---|---|
| TestDeltaOverCompiledProduct 隔离 | **3/3，0F/0E**，exit 0 | ✓ |
| nop-stream-sql 全量（`-pl nop-stream/nop-stream-sql -am`） | **74/74，0F/0E**，exit 0（E2E 9 = 六具名 E2E 6 + Delta 3；TestStreamSqlCompiler 31；eval 三类 34） | ✓ |
| 上游 reactor（core/flow/runtime/eql/orm 链） | 全绿 exit 0（runtime 1232 含 10 skip 既有） | ✓ |
| `node ai-dev/tools/check-doc-links.mjs --strict` | **0 errors，exit 0** | ✓ |
| `node ai-dev/tools/check-plan-checklist.mjs .../27-wi19-delta-verification.md --strict` | exit 0（1 条 non-completed 计划未勾项警告 = Phase 2 待审计态，符合时点） | ✓ |

计数勾稽：审计简报预估「74+3=77」有误，实际 **74 = WI18 基线 71 + WI19 新增 3**，与 plan「sql 模块全量零退化」（无计数声称）及 WI18 日志 71 基线相符，非退化。日志存 `_tmp/audit-wi19/`（gitignored）。

## 5. git 纪律与 plan/日志一致性（审计项 5）——PASS（附 MIN-1/MIN-2）

- **working tree clean**：审计起止 `git status --porcelain` 空（`_tmp/` gitignored :120）；WI19 提交 `50721c5bb4` 5 文件 +311/-0 与 plan In Scope 逐一对应（测试 + base/delta 两资源 + plan 自身 + 日志），零生成物、零探针入库。
- **roadmap 基线**：WI19 行（:270）`: todo`（翻转前正确状态，完成判定原文与本审计授权一致）；共享解析器实跑断言（`tools/mission-driver/src/roadmap-check.mjs` 的 `parseRoadmapMarkdown`）：**items=31 / milestones=7 / done=26 / WI19=todo**，剩余 WI19 WI20 WI22 WI23 WI24——与 WI18 收口日志「剩余 6 项去掉 WI18」勾稽相符。
- **plan 27 live 状态**：Phase 1 Status completed、三项 + Exit Criteria 三项全勾；Phase 2 Status planned、三项与 Exit Criteria 未勾；Closure Gates 6 项未勾；Plan Status: active；Closure 占位符原样——与「Phase 1 完成、待独立审计」时点一致。
- **日志一致性**：`ai-dev/logs/2026/10-03.md` :3-8 WI19 条目逐句对照 live 相符——三测试构成、LinkedHashMap 修正、plan 13 先例引用（原文核过）、执行期修正的如实记录、§八 10 结论、零退化（74 实测相符）。

### 发现（2 Minor 收口必改 + 2 观察不阻断）

- **MIN-1（plan 内「双执行」旧措辞残留，翻转前必改——guide 规则 18/19 文本一致性）**：执行期修正（双执行不可行 → 判别性等价设计）已在 Phase 1 item 3 与日志如实记录，但同一 plan 文件三处仍保留未修正前表述且承担勾选/收口判据角色：(a) In Scope「防陈旧钉 + **双执行行为断言**」；(b) Phase 1 Exit Criteria 第 1 条「防陈旧钉 + **双执行断言**绿」（已勾选，字面断言了一个不存在的设计）；(c) Closure Gates 第 1 条「防陈旧钉 + **双执行行为断言**」（收口时将按此勾选）。若不改，`completed` 态的 plan 将与自身 Phase 1 item 3 及 live 测试相矛盾。改法：三处统一改为已交付设计措辞（如「防陈旧钉 + 判别性等价断言（合并结构 + delta 单次执行）」）。
- **MIN-2（§八 10 从句失实，随 MIN-1 一并改）**：plan item 3 与日志条目中的「base/delta **均为全新 env 构建**」与已交付设计不符——base 从未构建 env（仅 fresh compile 参与钉比较），全测试唯一 env 构建来自 delta 合并模型。实质命题（无 runtime object 复用/patch、模型层全程）不受影响，但该从句按现文不可在 live 复现。改法：「delta 由全新 env 构建执行；base 不建 env（行为等价由判别性设计承载，见 WI17 E2E 与本测试执行断言）」。
- **OBS-1**：测试 javadoc :54「un-customized product's execution is already pinned by the WI17 E2Es」为同族钉（见 §2），下次触碰文件时宜顺带精确，不要求本次修改。
- **OBS-2**：`assertEquals(null, delta.getSql(), ...)`（:122）功能等价于 `assertNull`，风格项，不阻断。

## 6. 结论与翻转解锁条件

**最终裁定：PASS。** WI19 完成判定三成分全部实证：编译产物可被 Delta 覆盖定制且有用例（三测试隔离 3/3 绿、判别力反事实闭合、防陈旧钉独立探针复现含红态）；§八 10 显式验证成立（merge 在 parse 阶段 XNode 层、无 runtime patch 通路、单一全新 env）；单一确定结论在案且无二选一判据。2 Minor 均为 plan/日志措辞与已交付设计的对齐，随 Phase 2 收口执行，不阻断翻转。

**收口动作清单（实现侧按序执行）**：

1. **plan 27 措辞对齐（MIN-1/MIN-2）**：In Scope 行、Phase 1 Exit Criteria 第 1 条、Closure Gates 第 1 条三处「双执行」措辞改为已交付的判别性等价设计表述；item 3 与日志条目的「base/delta 均为全新 env 构建」从句改为如实的「delta 由全新 env 构建执行，base 不建 env」。纯文字改动，改后复跑 `check-doc-links --strict` 须 exit 0。
2. **Phase 2 勾选**：独立 audit 项勾选并落 `ai-dev/audits/nop-stream-sql/wi19-closure-audit.md`（本报告）；其余两项随翻转执行。
3. **roadmap 翻转**：WI19 行 `: todo` → `: done`（括注单层一对：承载 plan 27 + 本审计报告）；共享解析器断言复跑须 items=31 / milestones=7 / WI19=done / done=27（M5 仍 todo——WI20/WI22/WI23 未齐）。
4. **plan 27 收口**：Plan Status → `completed`；Phase 2 三项与 Closure Gates 六项逐项勾选（Closure Gates 第 1 条按改后措辞勾选）；Closure Status Note / Completed / Reviewer / Evidence 回填（Evidence 引用本报告与 `_tmp/audit-wi19/` 探针清单）；随后 `check-plan-checklist --strict` 与 `check-doc-links --strict` 双 exit 0 复跑确认。
5. **日志**：`ai-dev/logs/2026/10-03.md` 追加 WI19 closure audit PASS 与收口翻转条目（含 MIN-1/MIN-2 措辞修正记录）。
6. **提交（单 commit）**：须包含 (a) plan 27（措辞修正 + Phase 2 勾选 + Closure 回填）；(b) roadmap WI19 翻转；(c) **本审计报告 `ai-dev/audits/nop-stream-sql/wi19-closure-audit.md`（当前 untracked，漏提则审计证据缺失、文本一致性断裂）**；(d) 当日日志。提交后 `git status --porcelain` 须为空。
7. 后续：WI20（双目标一致性）与 WI22（指标钉入，deps 已齐可并行）→ WI23（等 WI18 ✓）→ WI24 收口；M5 随 Phase 5 全齐翻转。

---

### 附：审计探针与证据清单（`_tmp/audit-wi19/`，gitignored，保留供收口复核引用）

- `test-delta-isolated.log`（-q 静默版隔离跑，exit 0，备案）、`test-delta-isolated2.log`（**有效隔离证据**：`Running io.nop.stream.sql.compile.TestDeltaOverCompiledProduct` → `Tests run: 3, Failures: 0, Errors: 0, Skipped: 0`，exit 0）、`test-sql-full.log`（sql 模块全量 74/74 + 上游 reactor，exit 0）、`cp-build.log` + `cp.txt`（探针类路径）、`ProbeFreshCompile.java` + `probe-out.txt` + `fresh.xml`（绿态独立探针：RAW 不等/normalize 全等）、`ProbeRed.java` + `probe-red.txt`（红态探针：变体查询 normalize 不等）、`doc-links.log`（0 errors exit 0）、`plan-checklist.log`（exit 0，1 警告符合时点）。
- 关键实读锚：`TestDeltaOverCompiledProduct.java` :54/:86-145/:147-150；`wi19-compiled-base.stream.xml`（31 行含注释头，四 transform + 三边）/`wi19-compiled-delta.stream.xml`（x:extends 显式路径 + wi19DropB + e_out 重定向 + e_wi19）；`OrdersSourceFunction.java` :33-43（七记录定数）；`SqlTestSink.java` :23（实例级冷 sink）；`DslModelParser.java` :46（modelLoader=DslNodeLoader）；`DslNodeLoader.java` :95（XDslExtender.xtend 调用点）；`XDslExtender.java`（DeltaMerger，XNode 层）；`roadmap-check.mjs` parseRoadmapMarkdown（解析器断言实跑）；plan 13 :10/:118（本地 runner 一 execute 限制原文）；roadmap :58（Delta 验证不触门）/:270（WI19 行）。
