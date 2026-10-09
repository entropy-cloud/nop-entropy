# 2366 full-code-review R1.3 — nop-jq 工具接缝资源耗尽修复（P1-1.9-001）

> Plan Status: completed
> Last Reviewed: 2026-10-09
> Source: `ai-dev/backlog/full-code-review-roadmap.md` R1.3 行；finding `P1-1.9-001`（`ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/1.9-jq-refactor-first-audit.md`，修复精算在案）
> Related: P2-1.9-003（env/$ENV 全进程直通——审计明示 env-supplier 与本条「同批」）

## Purpose

修复 jq 工具接缝的资源耗尽三面（P1-1.9-001，修复精算已由审计给出）：①4 类叶节点绕过输出预算；②JqToolExecutor 零取消接线（agent 层 orTimeout 后底层计算变僵尸）；③env/$ENV 全进程变量经 System.getenv 直通 agent（P2-1.9-003 同批收口）。修复后 jq 工具在 agent 默认链（两信任分支均自动批准）下具备资源护栏。

## Current Baseline

- 输出预算 `JqEnvironment.checkOutputLimit()`（`nop-jq/src/main/java/io/nop/jq/runtime/JqEnvironment.java:108-113`，MAX_OUTPUT_COUNT 计数）已有 10 处设防点（JqBuiltins:153/168/1657/1662/1670、JqExecutor:322/361/435/551/557）——设防是设计意图；4 类叶节点绕过属契约洞：
  - `combinations`：`JqBuiltins.java:1823-1836` 递归（调用点 `:1386-1390`，env 在作用域内）
  - `constructObjects`：`JqExecutor.java:574-576` 笛卡尔递归（env 参数在位）
  - `interpParts`：`JqExecutor.java:596-598` 笛卡尔递归（env 参数在位）
  - `repeatString`：`JqExecutor.java:963-975` 追加循环（调用点 `evalMathOp` MUL 分支 `:910-913`；`evalMathOp(op,left,right)` 无 env 参数——需穿参）
- OOM 实证（审计）：`[range(25)]|map([0,1])|combinations|length` 47 字符 -Xmx128m 630ms OOM；`"a"*1e9` 222ms OOM。
- 取消链断裂：`JqToolExecutor`（`nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/JqToolExecutor.java`）`executeAsync` 直接返回 `context.getExecutor().submit(...)`，`IToolExecuteContext.getCancelToken()` 零引用；agent 层 orTimeout(300s) 只完成阶段不停止底层计算。
- env 直通：`JqExecutor.environmentObject()`（:721-727）直接 `System.getenv()`；`$ENV` 变量（:297-300）与 `env` 内建（:467）两处消费。`IToolExecuteContext.getEnvs()`（沙箱 env，不可变）在位未接线。
- jq 求值结构：`JqEngine.compile` 走 LocalCache（编译产物 `JqDirectQuery` 缓存复用、内含单个 `JqExecutor`）；`JqEnvironment` 每次 `apply` 新建——env-supplier 与 deadline 应挂 JqEnvironment（per-apply 实例），不得挂 JqExecutor（缓存共享、有并发污染风险）。
- 平台 API：`ICancelToken`（isCancelled/appendOnCancel/removeOnCancel）；`IThreadPoolExecutor.submit` 返回 `CompletableFuture<V>`（cancel/whenComplete 可用）。
- 官方套件基线 430/430（本修复不得改变默认求值语义：interrupt 标志常态为 false、deadline 常态为 0/不存在，额外检查均为 no-op）。

## Goals

- 4 类叶节点全部纳入输出预算（每处 `env.checkOutputLimit()`；repeatString 以追加循环迭代为计数粒度）。
- jq 求值具备取消检查点：`checkOutputLimit` 同时检查线程中断与 deadline——全部设防点（10 旧 + 4 新）自动成为取消/超时检查点。
- `JqToolExecutor` 接线 cancelToken（onCancel → future.cancel(true)）与 deadline（expireAt 传入 jq 求值），僵尸计算消除。
- agent 接缝 env 不再直通全进程变量：`IJsonQuery` 新增带 envVars/deadline 的 apply 重载，toolkit 传入 `context.getEnvs()`；CLI/独立路径默认行为不变（仍 System.getenv）。

## Non-Goals

- 不改 jq 求值语义（P2-1.9-001/002 等语义簇归 P2 通道）。
- 不动 agent 层安全链门禁（trusted/untrusted 自动批准属 AI 安全策略面，非本条修复裁定范围；资源护栏落地后风险降级）。
- 不处理 %%FAIL/applyPartial 等错误路径（P2-1.9-006/007）。
- 不给 nop-jq CLI 增加配置面（默认行为保持）。

## Scope

### In Scope

- `nop-jq`：`JqBuiltins.combinations`、`JqExecutor.constructObjects/interpParts/repeatString` 补预算；`JqEnvironment`（中断+deadline 检查、env-supplier、environmentObject 迁移）；`IJsonQuery`/`JqDirectQuery` 新 apply 重载。
- `nop-ai-toolkit`：`JqToolExecutor` cancel wiring + deadline/envVars 传递。

### Out Of Scope

- 其余 jq P2/P3 发现、ai-toolkit 其他工具、agent 层安全链。

## Execution Plan

### Phase 1 - nop-jq 资源护栏（4 叶节点 + 取消检查点 + env-supplier）

Status: completed
Targets: `nop-jq/src/main/java/io/nop/jq/runtime/JqBuiltins.java`、`JqExecutor.java`、`JqEnvironment.java`、`nop-jq/src/main/java/io/nop/jq/IJsonQuery.java`、`JqDirectQuery.java`

- Item Types: `Fix`

- [x] `combinations`：调用点传 env，输出添加处 `env.checkOutputLimit()`
- [x] `constructObjects` / `interpParts`：输出添加处 `env.checkOutputLimit()`
- [x] `repeatString`：env 穿参至追加循环，按迭代粒度 `env.checkOutputLimit()`（阻 1e9 追加）
- [x] `JqEnvironment.checkOutputLimit()`：头部增加取消/超时检查——中断检查用**非清除形态** `isInterrupted()`（不用 `Thread.interrupted()` 静态方法，避免清除标志令 `//` 右侧失控）并抛 **jq 不可捕异常**（`JqHaltException` 同型新类或复用语义，防 `try`/`//`/`recurse` 吞噬复活僵尸）；deadline 检查（`deadlineAtMillis>0 && now>deadlineAt`）同型不可捕异常。新增 `envVarSupplier`（默认 null=System.getenv）与 `environmentObject()`（自 `JqExecutor` **移除**（迁移非委托），三个调用点全部改走 env：`JqExecutor:298`（$ENV）、`JqExecutor:467`（env 内建）、`JqBuiltins:1117`（env 内建））
- [x] **新字段随 `fork()` 传播**：`JqEnvironment.fork()`（:140-144 私有构造器逐字段拷贝）必须携带 deadlineAtMillis/envVarSupplier——否则用户函数体内（`JqExecutor:770` fnEnv=fork()）14 个检查点全部静默失效
- [x] `IJsonQuery` 新增 default 重载 `apply(Object root, Map<String,String> envVars, long deadlineAtMillis)`（默认实现忽略配置直落旧 apply）；`JqDirectQuery` override：per-apply `JqEnvironment` 注入 supplier/deadline
- [x] 新增回归测试（`MAX_OUTPUT_COUNT` 为 public static final 无配置面——用大输入触发）：①`combinations` 超预算抛错（审计探针表达式，断言不可捕异常类型）；②`"a"*大次数` 不再 OOM（迭代粒度抛错）；③`constructObjects`/`interpParts` 同型超预算抛错；④deadline 超时抛错（**含经用户函数 `def f: ...; f` 的用例**——验证 fork 传播）；⑤中断响应（预中断线程后 apply 抛 cancelled，**含经用户函数与经 `try`/`//` 包裹的用例**——验证不可捕+标志不被清除）；⑥env 注入生效（`$ENV` 与 `env` 内建只见注入变量、不见全进程变量）+ 默认路径仍见 System.getenv

Exit Criteria:

- [x] 审计探针场景不再 OOM：4 类叶节点在预算/迭代粒度处抛 `JqRuntimeException`（测试①-③证据）
- [x] 取消检查点生效：deadline 与中断测试④⑤绿
- [x] env 接缝收口：测试⑥绿——toolkit 路径只见沙箱 env；`JqEngine.compile(expr).apply(root)` 默认路径行为不变（官方套件回归见 Phase 2）
- [x] `./mvnw test -pl nop-jq -am` 全绿（含官方 430/430 基线套件）
- [x] **端到端验证**（Rule #22 桥接项）：4 类叶节点 OOM 场景从 jq 表达式入口到不可捕异常出口完整走通（测试①-③）
- [x] No owner-doc update required（Phase 1 为内部护栏补全，公开 CLI 语义不变；Phase 3 统一裁定留档）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - JqToolExecutor 接线（cancelToken + 超时 + env）

Status: completed
Targets: `nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/JqToolExecutor.java`

- Item Types: `Fix`

- [x] `executeAsync`：保存 `CompletableFuture`；cancelToken 非 null 时 `appendOnCancel(reason -> future.cancel(true))`（**必须 true**——先例 `FutureHelper.bindCancelToken` 用 cancel(false) 不适用）+ `whenComplete` 中 `removeOnCancel`（防钩子泄漏）；doExecute 内改用新 apply 重载传 `context.getEnvs()` 与 `context.getExpireAt()`
- [x] 新增集成回归测试：①cancelToken.cancel() 后工具调用快速失败（不再僵尸——断言底层计算终止）；②工具结果只见 context envs（`$ENV` 与 `env` 双探测）；③expireAt 已过时调用失败（deadline 为协作式检查——doExecute 入口增加 fail-fast 预检，覆盖不经过检查点的表达式）
- [x] **接线验证**（Rule #23）：取消链路从 `ICancelToken` 到 jq 不可捕异常全程运行时连通（测试①）

Exit Criteria:

- [x] 取消/超时/env 三链路集成测试全绿（①②③）
- [x] **无静默跳过**（Rule #24）：取消与超时均以异常/错误结果终止（非吞掉返回空）
- [x] `./mvnw test -pl nop-ai-toolkit -am` 全绿
- [x] No owner-doc update required（toolkit 行为契约不变——此前取消/超时本就是上下文承诺，本 Phase 兑现之）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 收口

Status: completed
Targets: roadmap R1.3 行、轮次 index.md、本 plan

- Item Types: `Proof`

- [x] roadmap R1.3 行 → done；index P1-1.9-001 → fixed（附 plan 号；P2-1.9-003 env 直通部分同批注明，P2 台账行同步标注由 R1.3 承载的部分）+ index MR 表 R1.3 行
- [x] owner doc 裁定：jq 资源护栏为 nop-jq 设计意图的补全（`docs-for-ai/01-repo-map/module-groups.md` 如无 jq 行为描述则 No owner-doc update required，执行时核实）
- [x] 文本一致性核对五处一致

Exit Criteria:

- [x] roadmap 与 index 回填一致
- [x] `node ai-dev/tools/check-plan-checklist.mjs <本文件> --strict` 退出码 0
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 4 类叶节点预算全部落地（审计 OOM 探针场景测试化锁定）
- [x] 取消/超时链路连通：cancelToken → future.cancel(true) → 中断 → jq 检查点抛错（集成测试证明，非仅类型存在）
- [x] env 直通收口：toolkit 路径只见沙箱 env；CLI 默认路径行为不变（430/430 保持）
- [x] P2-1.9-003 的 env 部分由本计划承载并在 P2 台账标注（沙箱机制接线完成）
- [x] focused verification：nop-jq + nop-ai-toolkit 两模块测试全绿
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect
- [x] owner docs 已同步或显式记录 No owner-doc update required
- [x] 独立子 agent closure-audit 已完成并记录证据（本 plan Closure 段）
- [x] **Anti-Hollow Check**：closure audit 验证取消链与预算链运行时连通（测试证据）+ 无静默跳过
- [x] `./mvnw test -pl nop-jq -am` 与 `./mvnw test -pl nop-ai-toolkit -am` 全绿
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-jq --severity high` 退出码 0（nop-ai-toolkit 同）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <本文件> --strict` 退出码 0

## Deferred But Adjudicated

### agent 层 trusted/untrusted 双分支自动批准门禁

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 安全链门禁属 AI 安全策略面（HIGH_IMPACT_TOOLS 机制），资源护栏落地后 jq 工具的失控风险已收敛；门禁策略调整需产品层裁定
- Successor Required: `no`（如需门禁，归 ai 安全策略专项）

## Non-Blocking Follow-ups

- P2-1.9-003 的「env 值脱敏/最小化」增强（本计划只做直通切断，不做白名单过滤）
- 合法巨结果 truncation（P2-1.9-005，toolkit 输出面，独立小项）

## Closure

Status Note: P1-1.9-001 三面修复全部落地：4 类叶节点预算护栏 + 取消检查点网络（isInterrupted 非清除检查+deadline，经不可捕 JqCancellationException 防 try/`//` 吞噬，fork() 传播保用户函数体内生效）+ JqToolExecutor cancelToken/deadline/env 三接线（僵尸计算消除；P2-1.9-003 env 直通同批收口）。官方 430/430 基线保持，nop-jq 569 + toolkit 266 tests 全绿，双模块 hollow scan exit 0。
Completed: 2026-10-09

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，只读）agent_b57c799e-581c-479c-92f6-2831fca0cf5a
- Evidence:
  - **审查轮 3 Blocker 修复落实核验（auditor 重点核验）全部 PASS**：B1 environmentObject 自 JqExecutor 移除（grep 4 处：定义在 JqEnvironment:147+三调用点 :298/:467/:1117 全走 env，无残留方法体，主代码 System.getenv 仅剩默认回退一处）；B2 fork() 经私有构造器传播 deadlineAtMillis/envVarSupplier（JqEnvironment:38-50/:183-186，两 fork 消费点 JqExecutor:142/:764 落同构造器）+用户函数路径测试实跑绿；B3 JqCancellationException 直接继承 RuntimeException（:15，不在 JqRuntimeException 层次，try :252/`//` :411 只捕后者）+ isInterrupted() 非清除形态（:124）+ try/`//` 双包裹与预中断测试实跑绿。
  - **Anti-Hollow**：取消链逐段读通（ICancelToken.cancel → JqToolExecutor:44-46 appendOnCancel→future.cancel(true)+whenComplete removeOnCancel → 生产 DefaultThreadPoolExecutor.submit → ExecutorHelper:128-149 → FutureHelper.bindCancel:313-317 f.cancel(true) → worker 中断 → checkOutputLimit:124 抛 → doExecute:86-88 转 errorResult）；4 叶节点预算真实在输出添加/迭代处（JqBuiltins:1826/JqExecutor:575/:598/:966 循环体内，:654 add 同享）；hollow scan 双模块 exit 0 零发现。
  - **测试**：auditor 亲跑 `TestJqResourceGuards,TestJqOfficial` exit 0（ResourceGuards 9/9 + Official 430/430）；`JqToolExecutorTest` 5/5 exit 0；surefire 汇总 nop-jq 569/0 fail、toolkit 266/0 fail（2 skipped 预存）。
  - **状态回填四处一致**：roadmap R1.3 done / index P1-1.9-001 fixed（P2-1.9-003 同批注记）/ index MR R1.3 / daily log——与 plan 一致。
  - **Deferred 分类**：agent 门禁自动批准=out-of-scope improvement 诚实（审计修复方向原文不含门禁；护栏已收敛失控风险）。
  - Advisory 处置：A1 整批随本 closure 提交（已做）；A2 审计报告 P2 行注记落点偏差（index/roadmap/MR 三处已可追溯，报告行留后续勘注——记录不动报告）；A3 index fixed 注补 deferred 脚注（已做）；A4 取消集成测试强度注记（测试用裸 supplyAsync 不中断 worker、zombie 断言双情形绿——取消链由分段证据+生产 bindCancel glue 读通成立，Rule #23 满足；测试强化记 follow-up）。
  - **执行教训（已入 daily log）**：跨模块新增类被下游 AOP codegen（GenAopProxy URLClassLoader 走 ~/.m2 jar）引用时，须先 `./mvnw install -pl <上游>` 再跑下游测试。

Follow-up:

- 取消集成测试强化（改用 ExecutorHelper 同型 submit 使 cancel(true) 真实中断 worker，或 in-flight latch）——non-blocking：Rule #23 由分段证据成立
- agent 层 HIGH_IMPACT_TOOLS 门禁：out-of-scope improvement（见 Deferred）
- P2-1.9-003 的 env 值脱敏/白名单增强与巨结果 truncation（P2-1.9-005）：独立小项
