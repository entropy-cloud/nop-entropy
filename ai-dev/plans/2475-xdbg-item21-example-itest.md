# 2475 xdbg item 21 示例接入 + 后端集成测试（真实 XDSL task 全链调试）

> Plan Status: active
> Last Reviewed: 2026-10-18
> Source: roadmap item 21（ai-dev/backlog/xlang-web-debugger-roadmap.md）；场景先例 = nop-demo/nop-quarkus-demo `_vfs/nop/task/test/DemoTask/v1.task.xml`；测试基座 = nop-xlang-debug-web AbstractDebugGatewayTest/TestDebugGatewayE2E
> Related: plans/2471（item 12 WS 网关）、plans/2473（item 14 REPL）、2474（item 20 交付链）

## Purpose

交付"示例脚本调试全链路"的后端集成测试：以真实 XDSL 资源（nop-task 的 xpl step）为场景，经 WS 调试网关全链（握手→session.open→setBreakpoints→业务线程执行→evt.suspended→求值→单步→resume→完成→session.close），证明在线调试能力对业务 XDSL 对象可用，并为 item 22 前后端 e2e 提供场景蓝本。

## Current Baseline

- **既有网关 E2E**（nop-xlang-debug-web `TestDebugGatewayE2E`，10 测试）：全链时序（open→setBreakpoints→挂起→evt.suspended→getStackInfo→renew→resume→evt.resumed→close→sessionClosed）已覆盖，但断点位置为合成值（`fromLine("a", 1)`）、"脚本线程"为桩（直接调 `debugger.checkBreakpoint`）——**无真实业务引擎执行**。
- **既有会话集成**（nop-xlang-debugger `TestSessionRoutingIntegration` 等）：ad-hoc 表达式脚本经 `XLang.newCompileTool().compileFullExpr` 执行，无 XDSL 资源。
- **场景先例**：`nop-demo/nop-quarkus-demo/src/main/resources/_vfs/nop/task/test/DemoTask/v1.task.xml`——task 含 xpl step（`return a + b`），证明 task 引擎场景形态；nop-task-core `EvalTaskStep.execute` → `source.invoke(stepRt)`（编译期 IEvalAction）。
- **模块拓扑**：nop-task-core 依赖 nop-xlang（不依赖 nop-dev-tools）——debug-web（test scope）→ nop-task-core 无环。
- **可行性已核（R1 审查 live code 全链追踪）**：task xpl step 的 source 为 `ExprEvalAction`（xpl std-domain `compileFullExpr(node.content().getLocation(), text)` 编译——位置 = task xml 真实虚拟路径+行号），`invoke → XLang.execute` 即调试 choke point（test classpath 无 java/truffle 后端注册，EvalBackendRouter 不参与）→ 全局执行器 → `DebugExpressionExecutor.checkBreakpoint(expr.getLocation(), rt)` 逐语句；语句级 executable 默认 `allowBreakPoint()==true`，仅常量折叠字面量不可断点。item 11 的 TestDebuggerBackendLinkE2E 未含 task 路径（inline fake executable）——本 plan 集成测试即为 task 路径的首个 live 证明。

## Goals

- XDBG-21-01 示例接入：debug-demo task 资源（多行 xpl step，适配断点演示与 item 22 e2e 复用）落于 debug-web test `_vfs`；示例即场景蓝本。
- XDBG-21-02 全链集成测试 `TestXdslTaskDebugFullChain`：真实 task 引擎线程 + 真实资源位置的全链（连接→断点→挂起→求值→单步→放行→任务完成），断言事件顺序与任务结果。

## Non-Goals

- playground/flux 侧改动（item 22）；生产示例页（item 20 裁定 demo 宿主 = playground）。
- nop-rule 场景（task 已满足"选定一个 XDSL 场景"；rule 留 follow-up）。
- 网关/会话/租约语义新用例（items 10/12/13 已覆盖）。

## Scope

### In Scope

- `nop-dev-tools/nop-xlang-debug-web`：test 资源 `_vfs` 示例 task、`TestXdslTaskDebugFullChain`、pom test-scope 依赖（nop-task-core/nop-task-api）

### Out Of Scope

- 主代码改动（若集成测试暴露 task 路径断点失效，按 item 11 已裁定的降级机制处置或另立 Fix plan——不静默跳过）

## Failure Paths

| 场景 | 触发 | 行为 | 可重试 | 用户可见表现 |
| --- | --- | --- | --- | --- |
| 断点不命中 | 执行器注册/会话标记/externalPath 三处接线任一失误（实测：externalPath 首版即命中此坑） | 测试红——按三处接线逐一对账，不得跳过 | 是 | 集成测试红 |
| 位置不可知（task 虚拟路径行号漂移） | xpl 内嵌 xml 行号随格式变化 | 测试先动态发现位置（record pass）再设断点 | 是 | — |

## Test Strategy

本档选择：`必须自动化`（item 本体即测试）——JUnit 5 集成测试，复用 AbstractDebugGatewayTest 基座（注入型 runtime/桩鉴权）。

## Execution Plan

### Phase 1 - 示例 task 资源 + 全链集成测试

Status: completed
Targets: `nop-dev-tools/nop-xlang-debug-web/src/test/`

- Item Types: `Feature`（示例资源）+ `Proof`（全链集成测试）

- [x] pom：test-scope 增 `nop-task-core`（仅此一个——R1 M3 勘正：其仅依赖 nop-xlang/nop-ioc，api 不传递也无需；BOM 管版本，无环）
- [x] 示例资源 `_vfs/nop/task/DebugDemoTask/v1.task.xml`——**执行中修订（live 实证驱动）**：单 xpl step 多语句的 step 内语句级单步不经执行器路由（step 编译为单函数体 `__fn_1`，checkBreakpoint 每次执行仅入口一次；R1 的 SeqExecutable 逐语句追踪不适用于 task xpl 函数编译形态）；改为**双 xpl step** 任务（calc1: `const x = a * 2; return x;` / calc2: `const y = RESULT + b; return y;`——跨 step 载体为 RESULT，步骤局部 const 不跨 step）；input a/b + useParentScope + output sum（R1 M4 形态保留）
- [x] `TestXdslTaskDebugFullChain`（生命周期：`CoreInitialization.initialize()/destroy()` 于 @BeforeAll/@AfterAll——网关基座 setUp 不含平台初始化；**全局执行器必须在 @AfterAll 恢复 `DefaultExpressionExecutor.INSTANCE`**，防污染同 JVM 其余 46 测试——TestSessionRoutingIntegration 先例）：
  1. record pass（**执行裁定：改用 R1 M1 路线 a**——直连 `DebugExpressionExecutor(recorder)` 全局注册跑一次 task + finally 恢复，不动 manager）：收集各 step 入口 `SourceLocation`（虚拟路径+行号，同编译模型位置确定性一致）
  2. 网关全链（实测绿）：连接（Bearer tok1）→ session.open → 会话 debugger **`setUseExternalPath(false)`**（R1 M5——首版未设时断点静默不命中，与预警一致）→ `debugger.setBreakpoints`（step1 入口）→ 业务线程 `DebugSessionScope.withSession(...) + ITaskFlowManager.getTask/newTaskRuntime/task.execute` 执行 DebugDemoTask → `evt.suspended`（断言 sourcePath=`/nop/task/DebugDemoTask/v1.task.xml`、line==step1 入口）→ `getExprValue(a)`==2 → **stepInto**（step 边界单步）→ step2 入口次级挂起（reason=stepInto、不同执行单元）→ `getExprValue(RESULT)`==4 → `resume` → 任务完成（sum==7）→ `session.close` → sessionClosed；全局执行器于测试 finally 恢复（M2 防污染）
  3. 断言事件帧顺序与既有 E2E 同构（帧索引断言先例）
- [x] 既有测试零回归：debug-web 全量 46+ 绿（含 executor 恢复验证）

Exit Criteria:

- [ ] 全链测试绿且判别（移除 setBreakpoints 或位置错位即红）
- [ ] 端到端验证（Minimum Rule 22）：从 WS 帧（握手）到任务结果输出完整路径已验证
- [ ] 接线验证（Minimum Rule 23）：task step 编译链在运行时确实经全局执行器路由到会话 debugger（evt.suspended 事件即证据）
- [ ] No owner-doc update required（场景蓝本登记归 item 23 文档注册；本 plan 无 live baseline 变更——仅 test 树）
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [ ] 所有 in-scope 行为实现且有测试（本 plan 交付物即测试）
- [ ] Anti-Hollow：全链从 WS 帧入口到任务结果出口完整连通（evt.suspended 为 task→debugger 接线的运行时证据）
- [ ] No owner-doc update required（如实裁定：test 树变更，场景蓝本归 item 23 登记时一并入 docs-for-ai）
- [ ] 独立子 agent closure-audit 完成并记录证据
- [ ] `./mvnw test -pl nop-dev-tools/nop-xlang-debug-web -am` 绿（-am 连带 nop-task 全链测试，耗时可接受）
- [ ] check-plan-checklist --strict exit 0

## Deferred But Adjudicated

### task xpl step 内语句级单步

- Classification: `watch-only residual`
- Why Not Blocking Closure: live 实证——task xpl step 编译为单函数体（`__fn_1`），checkBreakpoint 每次执行仅函数入口一次，step 内语句级单步不经执行器路由；全链"单步"以 **step 边界粒度**成立并已测试承载。语句级粒度需 task 编译链产出语句级 executable 路由（涉 nop-task-core 编译形态，非 debug 层缺陷）；IDEA 桌面通道对独立 .xpl 文件的调试不受影响
- Successor Required: `no`（item 24 深审复核；item 22 e2e 的"单步"按 step 边界语义编写）

### 示例页面（roadmap XDBG-21-01 的"页面"半边）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: item 20 已裁定 demo 宿主 = playground（flux 侧 item 19 交付的 #/xlang-debug 演示页即示例页面）；entropy 侧"页面"无落点，示例以 task 资源 + 集成测试承载，item 23 文档注册时在 docs-for-ai 登记场景蓝本
- Successor Required: `no`

### nop-rule 场景接入

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: roadmap 明示"选定一个 XDSL 场景"（单数）——task 场景已满足全链证明；rule 引擎（dao/service 模块族）依赖面显著更重
- Successor Required: `no`

## Closure

Status Note: （待收口后填写）
Completed: （待收口后填写）

Closure Audit Evidence:

- Reviewer / Agent: （待填）
- Evidence: （待填）

## Draft Review Record

- Reviewer / Agent: 独立子代理 agent_ee447208（fresh session，R1）
- Verdict: revised（0B/5M/6m 全修订；可行性核心问题——task xpl 经调试 choke point——live code 全链追踪验证 TRUE）
- Findings addressed: M1 record-pass 路线钉死（re-init RecordingDebugger + 无标记 → default executor）；M2 补 CoreInitialization/执行器恢复生命周期项；M3 pom 仅 nop-task-core（api 不传递）；M4 示例 task 形态钉死（DemoTask 镜像 + useParentScope + const 语句 + 解析路径）；M5 setUseExternalPath(false) 前置；m1 E2E 计数勘正 10；m2 ITaskFlowManager 勘正；m3 failure path 去掉 item 11 误指；m4 Closure 补 Completed 行；m5 示例页面半边 Deferred 裁定；m6 gate 预算注记
