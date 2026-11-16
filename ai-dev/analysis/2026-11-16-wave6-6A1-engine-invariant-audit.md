# 审计 6A-1：nop-ib 引擎全路径 invariant 审计

```yaml
Status: open
Date: 2026-11-16
Audit: wave6 / 6A-1 engine full-path invariant audit
Scope: nop-tools/nop-ib (model → plan → compile → index → dispatch → fork/worker → differential rerun → fallback)
Method: invariant-loop（ai-dev/skills/invariant-loop-audit-prompt.md）；设计声明逐条比对 live code（file:line）+ 单测运行取证
Test ground truth: ./mvnw test -pl nop-tools/nop-ib -q → EXIT=0，18 个测试类，57 tests / 0 failures / 0 errors / 0 skipped（surefire XML 聚合）
Evidence base: 设计 05-nop-ib-engine-design.md（§5.3/§6.3-6.5/§7/§八/§十 硬门）、06-junit-worker-reuse-and-generalization-design.md（§二/§六 正确性门）
Constraint: 只读审计，未修改任何源代码
```

---

## 一、Invariant Ledger

裁决含义：PASS = 代码实现且有证据；VIOLATION = 实现与设计不变式相悖；HOLLOW = 名义实现（字段/方法存在但未被消费或未被生效路径触达）；UNTESTED = 无单测覆盖（代码本身可能正确）。

### Stage 1 — Model（ModelRefresher / EffectivePomParser / ModelStore）

| ID | Invariant（设计出处） | 证据 | Verdict |
|---|---|---|---|
| INV-01 | 模型过期 → 一切快路径结论无效，plan 直接回退全量（05 §5.3、§十硬门 4） | `ScopePlanner.plan` 首行即判 `staleModelReason != null` → `setFallbackReason("model-stale: ...")` 并 return（scope/ScopePlanner.java:87-91）；`ModelStore.stalenessReason` 逐 pom 重哈希 + repoRoot 比对（model/ModelStore.java:65-77） | PASS（代码）/ UNTESTED（无任何 staleness 单测，grep 全 test 目录零命中） |
| INV-02 | pom 变更/删除 → 模型过期 | `stalenessReason`：`pom changed` / `pom missing`（ModelStore.java:68-75） | PASS |
| INV-03 | 模块增删 → 模型过期（05 §5.3"模块增删"） | 新增模块：`collectPomHashes` 只收录 fresh reactor 内模块的 pom（ModelRefresher.java:165-175），`stalenessReason` 只遍历已记录哈希（ModelStore.java:68）→ **新增 pom 不在记录中，永不触发过期**。删除模块：pom missing 可检出（INV-02） | VIOLATION（新增方向） |
| INV-04 | Maven/JDK 版本变化 → 过期（05 §5.2/§5.3） | 模型记录了 `mavenVersion`/`jdkVersion`（ModelRefresher.java:104-107），但 `stalenessReason`（ModelStore.java:65-77）从不比对 | HOLLOW |
| INV-05 | profile/属性变化 → 过期（05 §5.3）；dump 带内容哈希（§5.3"model.json 带内容哈希"） | `dumpSha256` 已写入模型（ModelRefresher.java:109）但 `stalenessReason` 不校验（重算 dump 成本高可理解，但 CLI `-P` 激活 profile 的场景无任何检出通道） | HOLLOW |
| INV-06 | 禁止手写 pom 解析，事实只从 Maven 导出（IB-D3） | ModelRefresher 全部经 mvnq/mvnw 调 `help:effective-pom` + `dependency:list`（ModelRefresher.java:61-90）；EffectivePomParser 只解析 effective-pom dump | PASS |

### Stage 2 — Plan（ScopePlanner / ModuleGraph / ChangeClassifier）

| ID | Invariant | 证据 | Verdict |
|---|---|---|---|
| INV-07 | 模型/索引缺失 → 不剪裁、全量（I6，§6.5 末行） | plan 内 index==null → `addFullTestModule` + warn "full module tests (I6)"（ScopePlanner.java:203-207） | PASS / UNTESTED |
| INV-08 | §6.5 回退矩阵：L2 注解处理器、L3 AOP、XLang 模型/模板（L1 非 L0）→ Maven 回退，原因显式 | ScopePlanner.java:105-134（GEN_OR_MODEL / MAIN_JAVA × capabilityLevel 分支，`setFallbackReason` 带模块与等级） | PASS / UNTESTED |
| INV-09 | §6.5 放宽矩阵：config 资源、测试数据、test-source、主资源变更 → 该模块全量测试；模型/test-data 变更跨模块放宽 | `isWidenedModule`（ScopePlanner.java:233-251）+ plan 循环 `addFullTestModule`（199-207） | PASS / UNTESTED |
| INV-10 | 方法体变化也计入 changed（测试必须回归行为变化，IB-D4/§6.4） | ChangeClassifier 将一切非 `_gen` .java 归 MAIN_JAVA/TEST_JAVA（scope/ChangeClassifier.java:29-30）；selectTests 沿正向引用闭包命中 changedClasses（ScopePlanner.java:254-286） | PASS / UNTESTED |
| INV-11 | 测试选择种子必须过 surefire include parity（defect 14：Maven 不跑的类不得进选择集） | ScopePlanner.java:210-213 调 `SurefireClassFilter.filter`；junit/SurefireClassFilter.java:28-46 | PASS；部分测试（TestSurefireClassFilterExcludes 仅 1 个 @Test） |
| INV-12 | `partial` / `wideImpact` 必须同时出现在人类输出与 JSON（05 §十硬门 1） | `PlanResult.toMap` 含两字段（scope/PlanResult.java:59-60）；wideImpact 阈值判定 + warn（ScopePlanner.java:221-224）。**但 check/test 路径（cli/TestCommand.runTest）从不输出 plan 对象、从不透传 plan.getWarnings()** —— wideImpact/partial 只活在被丢弃的 PlanResult 里；CompileCommand feedback 亦不含 scope 字段 | VIOLATION（check/test 输出契约） |
| INV-13 | `--allow-partial` 才截断且退出码 6（05 §十硬门 3） | `plan.setPartial(allowPartial && requiresMavenFallback())`（ScopePlanner.java:225）；`Commands.EXIT_PARTIAL=6` 存在（cli/Commands.java:35,158） | PASS（名义）/ UNTESTED |
| INV-14 | `-pl` test 分支必须遵守模型过期门（05 §5.3"过期状态下任何快路径结论无效"） | TestCommand.runTest 的 `-pl` 分支（cli/TestCommand.java:154-233）直接用 `engine.model()` 派发 junit fork，**从不调用 engine.stalenessReason()**；`--rerun-failed` 分支（133-153）同样绕过。pom 过期后 classpath/surefire 事实陈旧仍执行测试 | VIOLATION |
| INV-15 | `--class` 显式执行与变更集/plan 解耦 | TestCommand.java:235-263，注释引用 roadmap item 22 | PASS（有意豁免，已文档化） |

### Stage 3 — Compile（IncrementalCompileExecutor / ClasspathMaterializer / MavenFallback / JavaCompilerFacade）

| ID | Invariant | 证据 | Verdict |
|---|---|---|---|
| INV-16 | 禁止 intra-project jar 上快路径 classpath（IB-D7、I1、§十二） | materialize 将 reactor 内依赖替换为输出目录（compile/ClasspathMaterializer.java:57-85）；`assertNoIntraProjectJars` 硬断言在编译路径（compile/IncrementalCompileExecutor.java:145）与测试路径（TestCommand.java:477）双重执行 | PASS；有测试（TestClasspathMaterializer） |
| INV-17 | I4 删除残留：从**旧索引**反查源文件并清理 `.class` + 嵌套 `$` 产品 | IncrementalCompileExecutor.java:151（loadIndex 先于删除）、163-170（I4 注释）、230-254（deleteClassProducts 前缀扫除）；文件消失即入 deletedSources（132-135） | PASS / UNTESTED（无删除场景单测） |
| INV-18 | inline-constant ABI 传播：`static final` 常量变化 → 保守扩散（§6.2/§6.3 abiChanged 定义） | UnitIndexer.visitField 捕获非空 static final 值到 inlineConstants（index/UnitIndexer.java:166-172）；executor diff 时 `old.getInlineConstants().equals(...)` 与 abiDigest 并列入 abiChanged（IncrementalCompileExecutor.java:203-211），驱动 pass-2 下游重编（71-97） | PASS / UNTESTED（无端到端 ABI 扩散单测） |
| INV-19 | `-implicit:none`、proc/release 按模型 execution 装配（IB-D5） | javac/JavaCompilerFacade.java:60-77（IMPLICIT_NONE、release/source 回退链、procMode 透传） | PASS |
| INV-20 | classpath 不完整 → 不得静默继续 | 编译路径直接 throw（IncrementalCompileExecutor.java:146-149）；测试路径显式 `surefire(classpath-incomplete)` 回退（TestCommand.java:484-488） | PASS |
| INV-21 | I2：`clean`/产物缺失/`target/` 外部改写 → 状态失效（05 §八） | **无实现**：UnitIndex 只带 indexedAt（index/UnitIndex.java:72-78），引擎任何处不比对产物目录 mtime/存在性。`mvn clean` 后 index 存活：编译路径会因 sibling class 缺失而 javac 失败（响亮但违反"状态失效"契约）；更危险的是 `mvn compile` 外部重写产物后 index 图谱陈旧 → **测试选择可能窄选（假绿风险）**。测试路径仅部分覆盖（test-products 缺失 → Maven 委托，TestCommand.java:324-334） | HOLLOW（Major：含窄选假绿风险） |
| INV-22 | 编译失败事务化回滚 stash/restore（05 §7.1） | 未实现：javac 失败仅 `outcome.failModule` 返回（IncrementalCompileExecutor.java:184-186），本轮已删产品不恢复。失败响亮（exit 1）无假绿，但与设计承诺不符 | HOLLOW（Minor，建议 Deferred） |
| INV-23 | 产品写必须持 `.mvn/build.lock`（IB-D9） | CompileCommand.java:134 `BuildLock.acquire`；超时 → exit 75（136-139）；锁语义与 mvnq 同文件（lock/BuildLock.java:22-27） | PASS；有测试（TestBuildLock） |
| INV-24 | 回退编译走 mvnq、-pl -am compile test-compile 不入 package 相位 | compile/MavenFallback.java:34-89 | PASS；有测试（TestMavenFallbackSelection） |

### Stage 4 — Index（UnitIndexer / UnitIndex / UnitIndexStore）

| ID | Invariant | 证据 | Verdict |
|---|---|---|---|
| INV-25 | class 解析失败 → indexUnhealthy，不得降级部分索引（§6.2） | UnitIndexer.scan 任一 class 解析失败即 throw `indexUnhealthy`（index/UnitIndexer.java:63-65）——响亮、无部分索引。注：异常向上传播导致整个命令失败，而非"该模块回退 Maven"（设计措辞），方向安全但非优雅降级 | PASS（保留语义） |
| INV-26 | 派发面 = 可执行测试类（06 WR-D7）：自有测试注解/@Nested/索引内超类链；@Nested 去重；外部超类 concrete 类按 defect 18 放宽 | UnitIndex.testClasses（index/UnitIndex.java:147-222），cycle guard（170-171）、coveredByOuterContainer（195-204）、ownsDispatchableNested（215-222） | PASS；有测试（TestUnitIndexer 8、TestNestedOnlyContainerPromotion） |
| INV-27 | 索引格式不匹配不得静默复用 | UnitIndexStore.load 版本校验 throw（index/UnitIndexStore.java:52-54）。注：抛异常使命令失败而非触发重建——安全方向，无自愈 | PASS（语义偏硬） |

### Stage 5 — Dispatch / Fork / Worker（TestCommand / SurefireClassFilter / JUnitWorkerPool / JUnitForkExecutor / IbJUnitRunner）

| ID | Invariant | 证据 | Verdict |
|---|---|---|---|
| INV-28 | 零假绿：未执行测试不得计通过；classpath 拼不出 TestEngine → 显式 surefire 回退不产生注定失败的 fork（06 WR-D8） | cmdPrefix 无 engine 即 throw（junit/JUnitWorkerPool.java:456-459）；TestCommand 捕获后 `surefire(worker-setup: ...)` 显式标注（TestCommand.java:499-508）；fork 内 -3 同样回退（538-543） | PASS |
| INV-29 | 差分重跑：worker 内失败 → 新鲜执行环境重跑一次，fresh 结果为准 + 污染告警（06 WR-D4/§六.1） | v2 模块级语义：exit==1 → 一个 fresh 顺序 fork 重跑整模块，fresh authoritative，两种结果均登记 warning（TestCommand.java:522-537） | PASS（代码）/ UNTESTED（rerun gate 无单测） |
| INV-30 | 重置钩子失败 ⇒ 该类不产出任何"通过"结论（06 §六.2） | **违背**：`runOne` 仅置 `resetFailed` 标志，类结果照常计入（junit/IbJUnitRunner.java:249-284）；JUnitWorkerPool 记 pollution + 退役 worker（junit/JUnitWorkerPool.java:134-147），但**类若通过，其通过仍计入 summary**；模块差分重跑门只看 `exit==1`（TestCommand.java:522）→ 全绿模块中被污染类不被 fresh 重跑验证 | VIOLATION（Major） |
| INV-31 | 差分重跑预算 = worker 实测 4 倍、120s 下限、300s 上限（WR-D4） | `retryTimeout()` 实现公式（TestCommand.java:603-615）但**生产路径零调用**——模块级重跑实际用 `nop.ib.moduleRerunTimeoutSeconds` 默认 1800s（TestCommand.java:523）。死代码 + `TestRetryTimeout` 在测死代码 | HOLLOW |
| INV-32 | cwd=模块 basedir、classpath 定序（test→main→deps）、禁跨模块合并 | Worker 构造 `pb.directory(model.resolve(module.getBasedir()))`（JUnitWorkerPool.java:283）；ClasspathMaterializer.java:44-47 定序 | PASS；部分测试（TestClasspathMaterializer、TestSurefireEnvironmentInjection） |
| INV-33 | stdout 泵防 64KB 死锁、`@@NOP-IB@@` 协议前缀、启动 hello 握手、EOF 快速失败、stderr 尾部 4KB、池级 watchdog | JUnitWorkerPool.java:293-337（pump+hello）、362-380（分片 poll 检测死 worker）、263-278（stderr tail）、502-518（池级 watchdog）；IbJUnitRunner.java:41,187 | PASS |
| INV-34 | excludedGroups/excludes/skipTests 与 surefire 语义 parity | IbJUnitRunner.java:51-54 + TagFilter（312-315）；SurefireClassFilter excludes（38-39）；skipTests 模型门（TestCommand.java:178,314） | PASS；有测试（TestExcludedTagFilter、TestSurefireClassFilterExcludes） |
| INV-35 | 找到 0 个测试的类必须可见（missingClasses），不得静默少执行 | JUnitWorkerPool 汇总 missingClasses 入 summary（JUnitWorkerPool.java:209-210,236），**但 TestCommand junit-fork 路径只读 slowest/failed（TestCommand.java:552-562），missingClasses 被丢弃，不出现在任何输出** | HOLLOW（选中类静默漏执行不可见） |
| INV-36 | 退出 2（类缺失）→ 一次 Maven test-compile 重试 | TestCommand.java:563-580。**缺陷**：重试后的 `fork=executor.execute(...)` 若 setup 失败返回 -3 + 空 summary → `f2=0` → `run.exitCode = EXIT_OK`（577）：**选中类一个都没跑却报 ok=true / exit 0** —— 硬门 4"不得给出成功结论"被击穿 | VIOLATION（Major） |
| INV-37 | surefire 回退走 `test` 生命周期相位（defect 20），非 surefire:test goal | surefireFallbackArgs（TestCommand.java:774-782） | PASS；有测试（TestSurefireFallbackArgs） |

### Stage 6 — Differential rerun / Fallback（整体闭环）

| ID | Invariant | 证据 | Verdict |
|---|---|---|---|
| INV-38 | 回退编译后测试选择必须重新判定，不得沿用回退前结果（§6.4 末条） | check 流程 compile 后重跑 plan（TestCommand.java:268-275 "re-judge the selection after compilation"） | PASS / UNTESTED |
| INV-39 | 冷路径必须显式动作且经 mvnq/lock（IB-D9、§三） | MavenFallback 经 mvnq（mvnq 自取锁）或显式 mvnw/mvn（compile/MavenFallback.java:34-54）；TestCommand 注释明确不外加锁防自死锁（743-747） | PASS |
| INV-40 | 每次回退/部分/宽影响必须带原因且可复查（05 §十硬门 2） | fallback.reason 在 JSON（PlanResult.toMap:39-41、CompileCommand:113-114）；run.kind 标注 surefire(原因)（TestCommand.java:486,506,541） | PASS（compile/test run 级）；plan 级 wideImpact 见 INV-12 违背 |
| INV-41 | 空 changed + 模型过期不得崩溃 | TestCommand.java:284、CompileCommand.java:111：`model.moduleForPath(changed.get(0))` —— changed 为空时 `IndexOutOfBoundsException`（模型过期分支必经）。边界崩溃，非假绿 | VIOLATION（Minor） |
| INV-42 | 测试选择计数不重复统计 surefire per-class 行（defect 20 co-finding） | extractSummaries 剥离 " -- in " 行（TestCommand.java:902-916） | PASS / UNTESTED（间接） |
| INV-43 | 设计 06 WR-D4 的 per-class 差分重跑已被模块级 v2 语义取代 | 代码注释如实记载 v2 理由（TestCommand.java:511-518）；但 06 设计文档 §二/§六仍描述 per-class 语义（文档漂移） | HOLLOW（文档 SSOT 漂移，Minor） |

---

## 二、Findings Summary（severity 排序）

| # | Severity | Finding | Suggested disposition |
|---|---|---|---|
| F1 | **Major (VIOLATION)** | INV-30：reset 钩子失败的类若在污染 worker 中通过，其"通过"直接计入全绿 summary，模块差分重跑门（exit==1）不会触发 fresh 重跑 —— 违背 06 §六.2"重置钩子失败 ⇒ 该类不产出任何通过结论"。 | **Fix**：resetFailed=true 时将该类结果改判失败（failures++ 且入 failed detail），或在 pool 返回值中携带 resetFailed 集合并让 TestCommand 对其做 fresh 单类/模块重跑。 |
| F2 | **Major (VIOLATION)** | INV-36：exit-2 → Maven test-compile 重试路径中，第二次 fork 若 setup 失败（-3，空 summary）→ `f2=0 → EXIT_OK`：选中类零执行却报成功（TestCommand.java:563-580）。 | **Fix**：重试 fork 后检查 `fork.exitCode()==-3` → 转 surefire 显式回退（与 538-543 同型）；summary 空时不得置 EXIT_OK。 |
| F3 | **Major (VIOLATION)** | INV-14：`nop-ib test -pl <modules>` 与 `--rerun-failed` 分支绕过模型过期门，pom 陈旧仍用陈旧 classpath/surefire 事实执行并给出结论（TestCommand.java:133-233）。 | **Fix**：两分支入口调用 `engine.stalenessReason()`，非空即回退 Maven（与 plan 分支同语义）或显式报错退出。 |
| F4 | **Major (VIOLATION)** | INV-12：check/test 输出不含 `wideImpact`/`partial`/plan warnings —— PlanResult.toMap 有字段但 check/test 路径从不输出 plan 对象（TestCommand.runTest 全程丢弃 plan.getWarnings()），违背 05 §十硬门 1。 | **Fix**：runTest 将 plan 的 wideImpact/partial/warnings 并入每条 report 或结尾汇总行（human+JSON）。 |
| F5 | **Major (VIOLATION)** | INV-03：新增模块/pom 不触发模型过期 —— collectPomHashes 只记录 fresh reactor，stalenessReason 无法感知新增文件；模型陈旧时新模块不进依赖图。 | **Fix**：refresh 时额外记录 pom 清单（或 reactor 模块数/坐标集哈希），stalenessReason 比对当前 `pom.xml` 文件集合 diff。 |
| F6 | **Major (HOLLOW)** | INV-21：I2 未实现 —— 单元索引不感知 `target/` 被 clean/外部改写；`mvn compile` 外部重写产物后索引图谱陈旧，测试选择存在窄选假绿风险（编译路径表现为响亮失败，尚可接受）。 | **Deferred**（或低成本 Fix）：scan 前比对 indexedAt vs 输出目录 root mtime；不匹配即丢弃索引按 index-missing 路径走。成本极低，建议本期修。 |
| F7 | Minor (VIOLATION) | INV-41：空 changed + 模型过期 → `changed.get(0)` 数组越界崩溃（TestCommand.java:284、CompileCommand.java:111）。 | Fix：空集守卫，直接报 model-stale 退出。 |
| F8 | Minor (HOLLOW) | INV-04/05：mavenVersion/jdkVersion/dumpSha256 已入模但 stalenessReason 不消费 —— 版本/profile 漂移无检出。 | Deferred：dump 重算成本高，至少比对 `mvn --version`（已在 refresh 路径有现成调用形态）可作廉价门。 |
| F9 | Minor (HOLLOW) | INV-31/43：`retryTimeout()` 死代码、`TestRetryTimeout` 测死代码；06 设计文档仍写 per-class 差分重跑，与模块级 v2 实现漂移。 | Fix（低成本）：删除 retryTimeout/remapSummary/executeForks 死代码与对应测试；06 文档补一段"v2 模块级语义"裁定（SSOT reconcile-on-touch）。 |
| F10 | Minor (HOLLOW) | INV-35：worker summary 的 missingClasses（发现 0 测试的选中类）被 TestCommand 丢弃，静默少执行不可见。 | Fix：junit-fork 路径将 missingClasses 转 warning 入 run.warnings。 |
| F11 | Minor (HOLLOW) | INV-22：编译失败无 stash/restore 事务回滚（§7.1 承诺）；失败响亮无假绿。 | Deferred：响亮失败已兜住正确性；回滚只改善工作树整洁度。 |
| F12 | Minor (HOLLOW) | INV-13：EXIT_PARTIAL=6 通路存在但无任何测试触达；partial 语义端到端无验证。 | Deferred：补 plan 命令级单测（无需真 Maven）。 |
| F13 | Minor (UNTESTED) | 全 scope 层（ScopePlanner/ModuleGraph/ChangeClassifier/PlanResult）与 ModelStore.stalenessReason、IncrementalCompileExecutor（删除/ABI 扩散）、差分重跑门 **零单测** —— 57 个现存测试集中在 parser/materializer/filter/runner 单元。 | Deferred→建议下一轮补参数化表驱动测试（按 invariant-loop 方法论 I1 沉淀为门禁），优先 INV-01/08/09/17/18/29。 |

### 裁定口径

- **Blocker：0**。F1/F2 虽属假绿族，但触发面窄（reset 钩子失败场景 / exit-2 重试 + setup 失败组合），且其余兜底（pollution 告警、kind 标注）保留可见性，不构成大面 silent fake-green。
- **PASS 面健康**：classpath 无 intra-project jar（双路硬断言）、I4 旧索引反查删除、inline-constant ABI 扩散、surefire lifecycle 回退、锁协议、模型过期主门（plan 路径）均实现到位且有 file:line 证据。
- **共性风险族**：三处 VIOLATION（F2/F3/F4 + F7）同属"分支入口漏接主门/主输出契约"家族 —— plan 分支实现了完整门禁，而 `-pl`/`--rerun-failed`/exit-2 重试分支各自绕开。修复时按类别清扫（同一门禁抽公共入口校验），不要只修报到实例。
