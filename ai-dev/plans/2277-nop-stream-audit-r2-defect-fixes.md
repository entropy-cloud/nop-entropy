# 2277 nop-stream 第二轮深审计缺陷修复（C1 关链顺序 + hashCode 对称性）

> Plan Status: completed
> Last Reviewed: 2026-09-27
> Source: 审计 `ai-dev/audits/2026-09/2026-09-27-deep-audit-nop-stream-quality-r2/`（05 回归审查 C1/C2 实证 + 3 个独立审计子代理）
> Related: 2278（可读性/结构整改）、2279（性能 R2）；358/359/360（均 completed，本计划修复其叠加产生的窄路径缺陷）
> Draft Review: R1 独立子 agent 对抗性审查（agent_6846e1a4）：**无 Blocker/Major，可直接执行**；4 Minor（closeInputGate 非公有/inventory 对象应为 InputGate.close、TestRemoteTransportLifecycle 在 runtime 模块须显式 -pl、C3 landed 形态钉死 catch-only 喂养+注释改写、gate 关闭断言经测试子类实现）已全部折入。

## Purpose

修复第二轮深审计确认的 live defect：plan 358 两项修复（EOS fail-fast typed 抛出 + InputGate.close 订阅关闭链）叠加后在任务关闭路径复活订阅泄漏（C1）；以及 plan 360 手写 hashCode 引入的 equals/hashCode 不对称（C2）。对 3 项低危注记（C3/C4/C5）逐项显式裁定，不允许默认遗留。

## Current Baseline

- HEAD 56e35aeaff，`./mvnw test -pl nop-stream/{core,runtime,cep,rocksdb,flow} -am` 全绿（2026-09-27 实测 exit 0）。
- `StreamTaskInvokable.invokeSource`（:713-719 finally）与 `invokeMiddle`（:758-763 finally）的关闭顺序为 `closeOutputWriters(); operatorChain.close(); closeInputGate();`。`RemoteResultPartition.close()`（:184-213）在 EOS 发送失败时以 `ERR_STREAM_STATE_ERROR` typed 抛出，`closeOutputWriters()`（:598-625）重抛首个 writer 异常 → 后两个 close 被跳过。`RunningTask.cancel()`（TaskManager.java:1142-1167）与 `TaskManager.stop()` 均不关 gate，无兜底。触发条件：任务正常走到 EOS 关闭、message backend 恰在 close 期间故障。
- `NodeId.hashCode()`（nop-stream-cep/.../sharedbuffer/NodeId.java:75-78）为 `31 * eventId.hashCode() + pageName.hashCode()`，字段为 null 时 NPE；`equals`（:63-72）为 `Objects.equals` null-safe。plan 360 R1 手写 hash 时引入的不对称。
- `InputGate.close()`（plan 358 新增公有方法）已登记进 `ai-dev/audits/nop-stream-invariants/gate-inventory.json`（TestInvariantTableCompleteness 守护）。
- C3：`JobCoordinator` 周期触发路径失败时不再喂养 `consecutiveTriggerFailures`（359 删除 CheckpointCoordinator 自带触发循环后，该计数器现仅 `GraphModelCheckpointExecutor.java:809` 供给）——可观测性回归，非正确性。
- C4：`SharedBuffer.isEmpty()`（:354-357）混判全局缓存空性与当前 key state；唯一调用方 `CepOperator.java:1184-1186` hasNonEmptySharedBuffer 为 test-only hook。
- C5：RocksDB (key,ns) 前缀缓存在用户原地变异可变 key 时返回过期编码——违反 key 不可变约定才可触发。

## Goals

- C1：任务 teardown 路径上 `operatorChain.close()` 与 `closeInputGate()` 在 `closeOutputWriters()` 抛出时仍被执行（异常语义保持：首个异常仍对外抛出，后续 close 异常 suppressed 或 WARN，不吞 EOS 失败信号）。
- C2：`NodeId.hashCode()` 对 null 字段与 equals 对称（null-safe，不回退为 varargs `Objects.hash` 热路径形态）。
- C3/C4/C5：每项落到 landed / adjudicated（watch-only）二者之一，理由写入本计划。

## Non-Goals

- 不改 EOS fail-fast 语义（typed 抛出保持，358 已裁定）。
- 不做 InputGate/teardown 之外的资源生命周期重构（2278/359 Deferred 切面承接）。
- 不处理 SharedBuffer.isEmpty 之外的 test-only hook 语义收敛（watch-only 裁定即可）。

## Scope

### In Scope

- `nop-stream-core` StreamTaskInvokable（C1）+ 对应 focused 测试
- `nop-stream-cep` NodeId（C2）+ 对应 focused 测试
- `nop-stream-runtime` JobCoordinator 触发失败计数（C3，若裁定为 landed）

### Out Of Scope

- MessageService/后端实现、checkpoint 格式、行为语义变更
- 2278（结构/可读性）与 2279（性能）范围的一切事项

## Execution Plan

### Phase 1 - C1 关闭链顺序修复

Status: completed
Targets: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/task/StreamTaskInvokable.java`

- Item Types: `Fix`

- [x] invokeSource 与 invokeMiddle 两处 finally 重排：`operatorChain.close()` 与 `closeInputGate()`（宿主私有方法，内部调 `InputGate.close()`）改为在 `closeOutputWriters()` 抛出时仍必执行（try/finally 或等价结构）；同时覆盖既有掩蔽路径——`sourceError/inputError != null` 时 close 再抛出不得顶替首异常对外传播
- [x] 异常语义保持：closeOutputWriters 的首异常仍向上抛；operatorChain.close()/closeInputGate() 若再抛异常不得覆盖首异常（`Throwable.addSuppressed` 或 WARN 记录），且不得吞掉 sourceError/inputError 的对外抛出
- [x] 新增 focused 回归测试（Minimum Rules #25，放 core 模块）：经测试子类记录关闭事件——子类化 `InputChannel`（public 非 final）override `close()` 记录，子类化 `RecordWriter`（close :250 public 非 final）override `close()` 抛 `StreamException` 模拟 EOS 发送失败；装配 harness 可参照 `TestStreamTaskTerminalSemantics` 的 invokable 装配先例。断言 (a) 输入 channel 的 close 被调用（gate 关闭生效），(b) operatorChain 被 close，(c) EOS 失败首异常仍传播到调用方。测试须能判别修复前行为（修复前 channel.close 未被调用）——落地为 `TestTaskTeardownCloseChain` 3 条：MIDDLE EOS 失败不跳过 gate 关闭（assertSame 异常实例 + channel.closed 断言，修复前 channel.closed=false 可判别）、inputError 优先级 + close 异常 suppressed、SOURCE 路径 operatorChain 关闭保持

Exit Criteria:

- [x] focused 测试实跑通过且对修复前行为可判别（在注释或测试名中说明判别点）——TestTaskTeardownCloseChain 3/3 绿
- [x] `./mvnw test -pl nop-stream/nop-stream-core -am` 全绿不少于基线（core 模块全量绿，2026-09-27 实测）
- [x] `./mvnw test -pl nop-stream/nop-stream-runtime -am` 全绿不少于基线（覆盖 TestRemoteTransportLifecycle 等关闭链测试——该测试类在 runtime 模块，仅跑 core 不会执行它）
- [x] **无静默跳过**：新增的异常抑制路径均有 WARN 日志或 suppressed 记录，非空 catch（closeChainAndGate 用 addSuppressed 链，无吞异常）
- [x] No owner-doc update required（行为为缺陷修复，不改变文档化契约；invariant 门禁对象 `InputGate.close()` 公有签名不变，gate-inventory 无需变更）
- [x] `ai-dev/logs/2026/09-27.md` 条目已更新

### Phase 2 - C2 hashCode 对称 + C3/C4/C5 裁定

Status: completed
Targets: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/sharedbuffer/NodeId.java`、`nop-stream/nop-stream-runtime/.../coordinator/JobCoordinator.java`

- Item Types: `Fix`（C2）、`Decision`（C3/C4/C5）

- [x] C2 Fix：`NodeId.hashCode()` 改为 null-safe 且无 varargs 分配的实现（与 equals 的 Objects.equals 对称）；补 focused 测试：含 null 字段的两个 NodeId equals 为 true 时 hashCode 必须相等，且不抛 NPE——落地 `TestNodeIdHashCodeContract` 3 条（all-null 不抛且相等/部分 null 相等性/minted 实例一致性），3/3 绿
- [x] C3 Decision：**landed**——`JobCoordinator.startPeriodicCheckpoints` 周期 lambda 的 catch 分支补 `checkpointCoordinator.incrementTriggerFailures()`（对齐 GraphModelCheckpointExecutor:809 先例）；pending==null（节流/standby/recoveryPending）分支不喂养（遵守 TestCheckpointMinPauseAndFailureCounter 钉死的"节流不计数"语义）；失实注释（"CheckpointCoordinator 自行覆盖"）已改写为准确语义。验证口径：计数 API 本身由 TestCheckpointMinPauseAndFailureCounter 覆盖（含节流不喂养负例），本改动为既有 API 的 catch-only 接线，cep+runtime 全绿（1079 runtime 含 checkpoint 计数测试）即为回归证据；无 JobCoordinator 周期触发集成 harness（无可复用驱动），不为此一行接线新建重型 harness 的裁定记录于本条
- [x] C4 Adjudication：**watch-only**——SharedBuffer.isEmpty(:354-357) 全局缓存空性与当前 key state 混判；唯一调用方 CepOperator.hasNonEmptySharedBuffer(:1183-1185) 为 test-only hook，不影响生产路径（理由见 Deferred But Adjudicated）
- [x] C5 Adjudication：**watch-only**——RocksDB (key,ns) 前缀缓存仅在用户原地变异可变 key 对象时返回过期编码，违反 key 不可变约定才可触发；改前逐次重编码在该违约场景下的行为差异无契约价值（理由见 Deferred But Adjudicated）

Exit Criteria:

- [x] C2 focused 测试实跑通过（equals true ⇒ hashCode 相等，null 字段不 NPE）——3/3 绿
- [x] `./mvnw test -pl nop-stream/nop-stream-cep,nop-stream/nop-stream-runtime -am` 全绿不少于基线（runtime 1079/0 Failures、cep 全绿，BUILD SUCCESS 2026-09-27）
- [x] C3/C4/C5 三项各有且仅有 landed / adjudicated watch-only 之一的显式状态与理由（Anti-Slacking Rule：无模糊词）
- [x] No owner-doc update required（无公共契约变化；C3 落地仅为既有计数器语义恢复）
- [x] `ai-dev/logs/2026/09-27.md` 条目已更新

## Closure Gates

- [x] C1、C2 两项 live defect 已修复且各有可判别的 focused 回归测试
- [x] C3/C4/C5 全部显式裁定，无 in-scope live defect 被降级为 follow-up
- [x] 行为结果：EOS 发送失败时任务资源（gate 订阅、operator chain）零泄漏路径成立
- [x] focused verification 已完成（两测试实跑 + 受影响模块回归绿）
- [x] 不存在被静默降级的 in-scope live defect 或 contract drift
- [x] No owner-doc update required（显式裁定，两 Phase 均不改变文档化契约）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证测试真实走到新代码路径（断言可判别旧缺陷）、无空方法体/静默吞异常引入
- [x] `./mvnw compile -pl nop-stream/nop-stream-core,nop-stream/nop-stream-cep,nop-stream/nop-stream-runtime` 通过
- [x] `./mvnw test`（受影响模块）全绿
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2277-nop-stream-audit-r2-defect-fixes.md --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream --severity high` 退出码 0
- [x] checkstyle：显式裁定——root pom 仅做版本管理（maven-checkstyle-plugin 未绑定 nop-stream 构建链，:140-148 插件声明整体被注释），按仓库既有惯例以两个 mjs 工具门禁替代，不适用

## Deferred But Adjudicated

### C4：SharedBuffer.isEmpty 跨 key 语义（test-only hook）

- Classification: `watch-only residual`
- Why Not Blocking Closure: 唯一调用方 `CepOperator.hasNonEmptySharedBuffer`（:1183-1185）为 test-only hook；scoped 缓存保留跨 key 条目后该方法可能对已空 key 返回"非空"，但生产路径不消费该结果（05 回归审查 A6 注记 1 实证），无 live 行为影响
- Successor Required: `no`

### C5：RocksDB (key,ns) 前缀缓存对原地变异 key 的理论性过期

- Classification: `watch-only residual`
- Why Not Blocking Closure: 触发前提是用户在 currentKey 保持期间原地变异可变 key 对象——违反 `Objects.equals(x,x)` 恒真所依赖的 key 不可变约定；改前逐次重编码仅在该违约场景下产生差异，无契约价值（05 回归审查 A3 注记实证）
- Successor Required: `no`

## Non-Blocking Follow-ups

- TestCheckpointMinPauseAndFailureCounter.java:287-290 驱动行为注释与 catch-only 接线对齐；sustained NO_TASKS_TO_ACK 的可观测性当前只到 DEBUG 级（watch-only，见 Closure）
- C2 注记的运行护栏：若未来运行时出现畸形 NodeId（半反序列化）之外的 null 字段构造入口，应转为构造器拒绝 null（当前不在范围）

## Closure

Status Note: C1（EOS 发送失败跳过输入 gate 关闭、复活订阅泄漏）与 C2（NodeId.hashCode null 不对称）两项 live defect 已修复，各带可判别旧缺陷的 focused 回归测试（3+3 条实跑绿）；C3 落地为 catch-only 计数喂养（与"节流不喂养"既有契约严格兼容）+ 失实注释改写；C4/C5 经实码核实均 watch-only 裁定成立。core/cep/runtime 三模块回归全绿（runtime 1079/0 Failures），独立收口审计 APPROVE（无 Blocker/Major，2 个非阻塞 Minor 已入 Follow-ups）。
Completed: 2026-09-27

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，agent_d717e02f-1dec-4d21-ba73-444a62dfc3a8）
- Audit Session: agent_d717e02f-1dec-4d21-ba73-444a62dfc3a8
- Evidence:
  - Phase 1 Exit（6/6 PASS）：TestTaskTeardownCloseChain 审计独立实跑 3/3 绿（surefire mtime 复核）；channel.closed + close@mid 断言对修复前行为可判别；closeChainAndGate（StreamTaskInvokable.java:635-656）两 catch 均 addSuppressed/转正无空 catch；InputGate.close 公有签名未变、gate-inventory.json:112-116 登记不变；log 条目核对一致
  - Phase 2 Exit（5/5 PASS）：TestNodeIdHashCodeContract 审计独立实跑 3/3 绿（new NodeId(null,null) 对旧实现即 NPE，可判别）；C3 landed 形态实码核对（JobCoordinator.java:1047-1055 catch 内 warn+increment；:1044-1046 null 分支仅 debug）；C4/C5 watch-only 裁定经实码核实成立（SharedBuffer.isEmpty:354-357 全局缓存混判且唯一消费点为 test-only 注释区；RocksDBKeyedStateBackend.java:418-437 过期编码仅在 key 原地变异时可达）；cep+runtime 全绿
  - Closure Gates（12/12 PASS）：EOS 失败零泄漏路径代码追踪成立（invokeSource finally :751-779 / invokeMiddle :818-836 → closeChainAndGate 无条件执行）；Anti-Hollow 全过（测试走新路径实证：sawMaxWatermark 证明成功终态后才 teardown；scan-hollow high exit 0）；compile 三模块 exit 0；checklist --strict exit 0；checkstyle 裁定事实核实（root pom :140-148 插件被注释）
  - Deferred 诚实性：C3/C4/C5 三项裁定逐项核实，无 in-scope live defect 降级；git diff 范围与 Scope 精确对应（3 main + 2 test 文件，无夹带）
  - 非阻塞 Minor：①TestCheckpointMinPauseAndFailureCounter.java:287-290 既有注释称驱动对 NO_TASKS_TO_ACK 也喂养，landed 形态下该场景经 null 返回不被喂养——注释与 live 接线残余偏差（已入 Non-Blocking Follow-ups）；②plan C3 条目 pending==null 枚举不全（语义陈述本身准确）

Follow-up:

- TestCheckpointMinPauseAndFailureCounter.java:287-290 驱动行为注释与 catch-only 接线的措辞对齐 + sustained NO_TASKS_TO_ACK 可观测性仍只到 DEBUG 级（watch-only；不阻塞收口）
- （见 Non-Blocking Follow-ups）
