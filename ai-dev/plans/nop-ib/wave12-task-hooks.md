# Wave 12 — 声明式代码生成任务钩子（nop-ib.tasks.json）

> Plan Status: completed
> Last Reviewed: 2026-11-17
> Source: [design 08-codegen-task-hooks-design.md](../../design/nop-incremental-build/08-codegen-task-hooks-design.md)（IB-T1..T6）；roadmap Wave 12；owner 指令 2026-11-17（exec 插件不直接支持，改通用任务钩子）
> Related: wave9-test-cache.md / wave10-service-mode.md（完成态基线）

## Purpose

把"代码生成"从"整体回退 Maven"变为"声明式任务留在 ib 快路径"：项目自声明 `nop-ib.tasks.json`（根/模块分层可选），ib 负责增量判定、classpath 物化、fork 执行与失败显式回退。nop 平台的 xgen 链路作为首个使用方端到端验证。

## Current Baseline

- nop-ib 快路径覆盖 L0/L1 手写源码；xgen 模板 / xlang 模型变更触发整体 Maven 回退（`ScopePlanner.java:126-130`，reason "xlang model/template change in L1 module"）。
- 生成产物 `**/_gen/**` 已有排除通道（`CompileCommand.java:79` 等），L1 判定含启发式 `hasXgenTemplates`（`ScopePlanner.java:323-331` 文件扫描）。
- classpath 物化已具备：`ClasspathMaterializer.mainClasspath/testClasspath(IbModule)` → `Materialized(entries, missing)`（`ClasspathMaterializer.java:30/35/190`）。
- `EffectivePomParser.java:105-108` 仅记录 `execExecutionIds` 用于 pom 指纹，无执行语义。
- nop-ib 不依赖 nop-xlang（pom 依赖仅 nop-javac/nop-bytecode/junit）——xscript 执行体必须走项目 classpath fork JVM（IB-T3）。
- `IbModule.basedir` 为 repo 相对路径（`IbModule.java:20`）；模型模块枚举入口 `IbModel.module(id)` / `getModules()`。
- fork JVM 先例：`JUnitForkExecutor`（进程构建/cwd/看门狗模式可参照）。

## Goals

- `nop-ib.tasks.json` 分层发现/合并/校验（IB-T1）：root + 任意模块 basedir，同名模块覆盖根级，配置错误显式 exit 3。
- TaskRunner 增量执行（IB-T2/T3/T4）：classpathScope 语义对齐 exec 插件；java/xscript 双执行体；inputs/outputs 指纹缓存落 `<module>/target/nop-ib/tasks/`。
- planner/compile/check 接入（IB-T5/T6）：before-compile/after-compile 钩子；任务模块运行时 L1 叠加；失败/缺失 outputs → 显式 fallback Maven。
- 端到端证明：真实 xgen 模板变更 → 生成 → 编译 → 测试全程零 Maven 骨架。

## Non-Goals

- 不解析 exec-maven-plugin 的 pom 配置（迁移到任务声明是项目侧一次性手工动作）。
- 不内嵌 XLang 引擎（xscript 经项目 classpath 上的 runner 执行）。
- 不做任务间并行、不做跨项目任务结果缓存（roadmap Deferred 语义不变）。
- 不支持 exec:exec 外部进程形态（v2 预留，本期 `UnsupportedOperationException` 显式拒绝）。
- 不改变 07 服务化并发模型（任务在 job 路径内、lane 串行覆盖）。

## Scope

### In Scope

- `nop-tools/nop-ib` 新增 `io.nop.ib.tasks` 包（配置加载/合并/glob/TaskRunner/指纹存储）。
- `ScopePlanner` / `CompileCommand`（runCompile）/ `TestCommand` check 流程接入。
- 单元测试 + 真实模块 E2E。

### Out Of Scope

- nop 平台各 `-meta/-dao` 模块的批量任务声明迁移（另立后续项，本期以单模块 E2E 证明机制）。
- mvn passthrough（Wave 11 item 2 独立裁定）。

## Execution Plan

### Phase 1 — TaskConfig：发现、合并、校验、glob

Status: completed
Targets: `nop-tools/nop-ib/src/main/java/io/nop/ib/tasks/TaskConfig.java`（record：name/trigger/inputs/outputs/exec）、`TaskConfigLoader.java`、`GlobMatcher.java`、`src/main/java/io/nop/ib/NopIbMain.java`、`src/test/java/io/nop/ib/tasks/TestTaskConfig.java`

- Item Types: `Feature`

- [x] TaskConfigLoader：从 repo root 到模块 basedir 路径链收集 `nop-ib.tasks.json`；根级先装、模块级后装、同名覆盖。合并语义裁定：**非 exec 顶层字段（name 除外）整体替换、exec 逐字段合并**（模块级只写要改的字段）
- [x] fail-loud 校验：未知顶层/嵌套字段拒绝、name/trigger/inputs/outputs/exec.type 必填、exec.java 需 mainClass、exec.xscript 需 mainClass（runner 入口，复用同一字段）+ script、classpathScope 可缺省（缺省 = `test`，IB-T2）且取值 ∈ {compile,runtime,test}；全部抛 `NopIbException`
- [x] `NopIbMain` 增 `catch (NopIbException) → exit 3` 全局分支（现状仅 IllegalArgumentException 映射 3，审查发现 #3）
- [x] GlobMatcher：模块 basedir 相对 glob，`**` 跨目录；outputs 展开 = 匹配的**文件**列表（目录本身不入列表，指纹粒度=文件集合）
- [x] 单元测试：分层合并两根钉（exec 部分覆写 / 顶层整体替换）、每个校验分支一根钉、classpathScope 缺省钉、glob 展开粒度钉（`**/gen-*.xgen` 跨目录匹配 + 新生成文件使 outputs 存在态变化）

Exit Criteria:

- [x] 配置错误全部显式失败：缺 name / 未知 trigger / 未知字段 / glob 语法错 / 未知 classpathScope → exit 3 且错误消息指明文件与字段（每分支一根钉）
- [x] 分层合并钉：根级任务 + 模块级同名覆写 exec.arguments → 合并结果仅覆写声明字段
- [x] `mvnw test -pl nop-tools/nop-ib` 全绿
- [x] No owner-doc update required（design 08 已在档，本 phase 无偏差）

### Phase 2 — TaskRunner：物化、执行、指纹增量

Status: completed
Targets: `io/nop/ib/tasks/TaskRunner.java`、`TaskFingerprint.java`、`src/test/java/io/nop/ib/tasks/TestTaskRunner.java`

- Item Types: `Feature`

- [x] TaskRunner.run(module, task, ctx) → TaskResult(ok, exit, stderrTail)：
  - 指纹文件 `<module>/target/nop-ib/tasks/<name>.fp.json`（inputs 内容哈希 + outputs 存在态列表）；命中即 skip（result kind=skipped）
  - classpathScope→ClasspathMaterializer 映射：compile/runtime→main，test→test；`missing` 非空 → fail-loud（列缺失 artifact）
  - exec java：fork `java -cp <cp> <mainClass> <arguments>`，cwd=模块 basedir，stderr 捕获、看门狗超时（复用 JUnitForkExecutor 的进程治理形态）
  - exec xscript：同 fork，命令 = `java -cp <cp> <exec.mainClass> <script> <arguments>`——xscript 复用 `mainClass` 字段承载 runner 入口（审查发现 #2 裁定：不新增字段，design 08 §二已同步）
  - exec.type=command/未知值 → 加载期 `NopIbException` 显式拒绝（配置期 exit 3；实现取此形态而非运行期 UOE——closure audit 裁定良性偏差）
  - 执行后 outputs 存在态为空 → fail（"task produced no outputs"）；成功写指纹
- [x] 指纹失效：inputs 任一文件内容变化 / outputs 任一路径消失 → 重跑（钉 3 颗）
- [x] 单元测试：skip 命中、inputs 变更重跑、outputs 删除重跑、执行失败 exit!=0 → TaskResult.ok=false + stderrTail、无 outputs → fail、classpath 不完整 → fail 列缺失、指纹文件随 target 目录（断言路径前缀）

Exit Criteria:

- [x] 增量三态钉齐：命中 skip / inputs 变化重跑 / outputs 消失重跑
- [x] 失败语义钉：exit!=0、无产物、classpath 缺失三类全部 ok=false 且携带 stderr/reason，无静默路径
- [x] 临时文件位置钉：断言指纹与任务产物路径都在 `<module>/target/nop-ib/tasks/` 下
- [x] `mvnw test -pl nop-tools/nop-ib` 全绿

### Phase 3 — 接入：compile/check 流程 + L1 叠加 + 回退

Status: completed
Targets: `io/nop/ib/scope/ScopePlanner.java`、`io/nop/ib/cli/CompileCommand.java`、`io/nop/ib/cli/TestCommand.java`、`io/nop/ib/engine/IbEngine.java`、`src/test/java/io/nop/ib/cli/TestTaskHookIntegration.java`

- Item Types: `Feature`

- [x] IbEngine 增 `tasksFor(moduleId)`（loader 实例缓存于 engine，模型刷新不丢失）
- [x] ScopePlanner 回退抑制（审查 Blocker #1，显式语义）：模块拥有声明了 `before-compile` 任务的 `nop-ib.tasks.json` 时，该模块的 GEN_OR_MODEL 变更**不再触发 Maven 回退**（改由 Phase 3 的任务执行链路承接生成）；无任务声明的模块维持既有回退判定不动（零假绿兜底）。MAIN_JAVA × L1 判定改为声明优先于 `hasXgenTemplates` 启发式（IB-T5）
- [x] CompileCommand.runCompile：plan 无 Maven 回退时，编译前跑该轮受影响模块的 before-compile 任务、编译后跑 after-compile 任务；任一 TaskResult.ok=false → 转显式 Maven 回退，fallback reason = "task <name> failed: <tail>"（日志与 JSON 同步可见）
- [x] TestCommand check 流程复用 runCompile（既有调用链 TestCommand.java:326），零重复实现
- [x] JSON 契约：compile/check feedback 顶层增 `tasks: [{name, kind: skipped|executed|failed}]`（显式区分，不冒充编译结果——对齐 Wave 9 cached 契约纪律）
- [x] 集成测试：fixture 模块（root 任务 + 模块级覆写）走 runCompile → 任务被执行、产物生成、编译继续；任务失败 → fallback reason 在档；回退抑制双钉：有任务模块 GEN_OR_MODEL 变更不回退 + 无任务模块仍回退

Exit Criteria:

- [x] 接线验证（Minimum Rules #23）：集成测试断言 TaskRunner 在 runCompile 路径中被调用（任务计数/标志位），非仅类存在
- [x] 端到端验证（Minimum Rules #22）：fixture 从 `nop-ib check --paths <template>` 入口 → 任务执行 → 生成物落 `src/main/**/_gen` → 编译 → 测试选择，全链路 JSON 在档
- [x] 失败回退钉：任务失败后 compileModules 走 MavenFallback 且 reason 含任务名
- [x] `mvnw test -pl nop-tools/nop-ib` 全绿
- [x] owner-doc：design 08 若实现与契约有偏差原地修订；无偏差写 `No owner-doc update required`

### Phase 4 — 真实项目 E2E + 文档收口

Status: in progress
Targets: nop-entropy 真实 `-meta` 模块（E2E 场景）、`docs-for-ai/02-core-guides/incremental-build.md`、roadmap、daily log

- Item Types: `Proof | Follow-up`

- [x] E2E：选一个真实 `-meta` 模块临时声明 xgen 任务（不提交到模块，用 worktree 场景或临时文件），改 gen-*.xgen → `ib check` → 生成 → 编译 → 测试全程零 Maven 骨架；二跑指纹命中 skipped；`mvn clean` 后指纹随 target 清理、三跑重建；服务模式冒烟：同一场景经 HTTP `/v1/run` 提交一次，确认 job 路径（复用 runCompile）同样执行任务；对照数据（耗时 + JSON）落 `_tmp` 与 daily log
- [x] `docs-for-ai/02-core-guides/incremental-build.md` 增"代码生成任务声明"节（契约链接 design 08）
- [x] roadmap Wave 12 勾选；daily log 收口记录

Exit Criteria:

- [x] E2E 对照数据在档：模板变更链路耗时显著低于 Maven 回退路径（数字写入 daily log）
- [x] `mvn clean` 交互验证在档：指纹文件被 clean 删除、三跑结果正确
- [x] 文档与 roadmap 勾选完成；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见本 guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。
>
> **纯文档计划**：如果计划不涉及任何代码变更（仅修改 `docs/` 或 `ai-dev/` 下的文件），`./mvnw test`、`./mvnw lint` 等构建验证条目可以直接从 Closure Gates 中删除，不需要执行。

- [x] 所有 in-scope confirmed live defects 已修复（本 wave 无预存 live defect；顺手修复 ChangeClassifier ext 规则潜伏缺陷 + 回归钉 TestChangeClassifierExtRule）
- [x] IB-T1..T6 全部有 live code path + 钉证明（audit 逐条 PASS）
- [x] 端到端（真实项目 xgen 链路）对照数据在档（1.9s/0.24s vs mvn 7.8s；CLI + HTTP 双通道）
- [x] 不存在被静默降级的 in-scope 项
- [x] design 08 / docs-for-ai / roadmap / daily log 一致
- [x] 独立子 agent closure audit APPROVED（evidence 写入本 plan Closure 段）
- [x] Anti-Hollow：接线验证 + 端到端验证通过；scan-hollow-implementations --module nop-tools/nop-ib --severity high = 0 findings
- [x] `./mvnw test -pl nop-tools/nop-ib` 全绿（124/124，audit 独立实跑）## Deferred But Adjudicated

### exec.type=command（外部进程形态）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: v1 覆盖 java/xscript 即满足 nop 平台全部已知 codegen 场景；外部进程无消费方
- Successor Required: no（design 08 已预留 v2 契约位）

## Non-Blocking Follow-ups

- nop 平台全部 138 个 exec 激活模块的批量任务声明迁移（机制证明后的工程化推广）
- 任务执行输出摘要进入 RunLog 结构化 section（当前先走 stderr tail + tasks JSON 数组）

## Closure

Status Note: 四个 Phase 全部落地（20+2 钉、真实 xgen E2E 三态 + HTTP 冒烟、116/116 全绿）；audit 首轮 NOT APPROVED 的 3 项修复完成（ChangeClassifier 回归钉 / 服务模式冒烟证据 / roadmap 一致性），复审 APPROVED。
Completed: 2026-11-17

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，closure auditor）
- Evidence:
  - 首轮裁定 NOT APPROVED，3 项 MUST FIX（dot-fix 回归钉缺失 / Phase 4 HTTP 冒烟无证据勾选 / roadmap item 1 与 Exit Criteria 未勾选）——全部修复（daily log 11-17 修复轮记录）。
  - Exit Criteria 逐条 PASS（首轮审计表 + 修复后复核）：接线 PASS（IbEngine.java:98 构造点注入 this::tasksFor；CompileCommand runTriggerTasks→TaskRunner.run）；无静默跳过 PASS；ChangeClassifier Fix PASS+新增钉；E2E 三态证据链（_tmp/nop-ib/compile-20261117-223446/223459/223524.log）与声明吻合。
  - `node ai-dev/tools/check-plan-checklist.mjs --strict` 退出码 0；`scan-hollow-implementations --module nop-tools/nop-ib --severity high` 0 findings；`check-doc-links --strict` 0 errors。
  - Anti-Hollow：`./mvnw test -pl nop-tools/nop-ib` 124/124 全绿（audit 独立实跑确认）。
  - Deferred 分类复核：exec.type=command deferred 诚实（且实现更早在加载期显式拒绝，plan 已原地修订）。
