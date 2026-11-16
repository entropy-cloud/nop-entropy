# 6C-2b: flink-agents-runtime fork JVM abort（exit 134）根因诊断

- Status: adjudicated（发现已修复或裁定，见 plan wave6-audit-and-fixes.md Closure 段）
- Date: 2026-11-16
- Scope: read-only diagnosis（无代码修改）
- Inputs: campaign 记录 `ai-dev/plans/nop-ib/external-project-support-campaign.md` "Deferred But Adjudicated"、wave6 计划 6C-2b、`~/sources/flink-agents/runtime/hs_err_pid*.log`（18 份）

## 1. 背景（来自 campaign / 日志）

- flink-agents-runtime fast-path：ib junit-fork 派发该模块时 fork JVM 以 exit 134（SIGABRT 包装的 native crash）中止；单类 run 正常（CompileUtilsTest 11/0）、per-class batch=1 仍 134、4g 堆无效。当时裁定为 watch-only，回退路径 `--runner maven`（1209/270e/25s）作 parity oracle。
- 此前记录中隐含假设："mvn surefire 跑同一批类正常，只有 ib fork 崩"。

## 2. 关键新证据：18 份 hs_err 文件（此前未被检查）

`/Users/abc/sources/flink-agents/runtime/hs_err_pid*.log` 共 18 份（2026-11-16 当天生成），分两组：

### 2.1 ib fork JVM 崩溃（java_command = `io.nop.ib.junit.IbJUnitRunner --worker`）

代表样本 hs_err_pid25975.log：

- `#  SIGSEGV (0xb) ... si_code: 2 (SEGV_ACCERR), si_addr: 0x00000000000000be`
- JVM: `OpenJDK 64-Bit Server VM Zulu26.30+11-CRaC-CA (26.0.1+8)`，macOS arm64, 16 cores, 128G
- VM args: `-Xmx4g --add-opens=java.base/java.util=ALL-UNNAMED --add-opens=java.base/java.nio=ALL-UNNAMED --add-exports=java.base/jdk.internal.vm=ALL-UNNAMED -Dnop.autotest.force-save-output=false -Dnop.autotest.disable-snapshot=false`
- 崩溃帧（全部为 **C2 JIT 编译产物** 内的 near-null 解引用）：

```
J 4573 c2 org.apache.flink.streaming.runtime.tasks.mailbox.MailboxExecutorImpl.yield()V (28 bytes)
J 4786% c2 org.apache.flink.agents.runtime.operator.ActionExecutionOperator.waitInFlightEventsFinished()V
j  org.apache.flink.agents.runtime.subagent.external.ExternalAgentSubagentSetupTest.deferredModeSurfacesARemoteFailure()
```

另一组崩在 `StreamTaskActionExecutor$SynchronizedStreamTaskActionExecutor.runThrowing` c2 帧（BaseAsyncSubagentSetupTest.cancelBeforeResolveThrowsCancellationByDefault，hs_err_pid93442 等）。

### 2.2 决定性反证：surefire booter JVM 同样崩溃

hs_err_pid84578 / pid8635 / pid93525 的 Command Line 是 **`.../target/surefire/surefirebooter-*.jar`**（standalone mvn surefire 的 fork），崩溃帧与 ib 完全同族：

```
hs_err_pid84578.log: J 86559 c2 org.apache.flink.streaming.runtime.tasks.mailbox.MailboxExecutorImpl.yield()V
hs_err_pid8635.log:  J 85302 c2 org.apache.flink.streaming.runtime.tasks.mailbox.MailboxExecutorImpl.yield()V
hs_err_pid93525.log: J 88951 c2 org.apache.flink.streaming.runtime.tasks.StreamTaskActionExecutor$...runThrowing
```

即 **"surefire 跑正常、仅 ib fork 崩" 的前提被证伪**——surefire fork 在同机同 JDK 下以同一 C2 编译帧 SIGSEGV。mvn 侧表现为 surefire fork crash error（与 campaign 记录的 mvn oracle 1209/**270e** 的 error 量级吻合）。

### 2.3 JVM 参数对拍（surefire vs ib fork）——排除配置差异

`~/sources/flink-agents/pom.xml:335-339` surefire argLine 仅 3 个参数：

```
--add-opens=java.base/java.util=ALL-UNNAMED
--add-opens=java.base/java.nio=ALL-UNNAMED
--add-exports=java.base/jdk.internal.vm=ALL-UNNAMED
```

两组 hs_err 的 jvm_args 中这 3 项完全一致；ib 额外注入 `-Xmx4g`（surefire 无 -Xmx）。runtime/pom.xml 无 surefire 配置、无 jacoco agent、无 forkCount/reuseForks 特殊化。**ib 未遗漏、未改坏任何 surefire 注入的 JVM 选项**。

## 3. 假设排序

### H1（确立，置信度最高）：宿主 JDK C2 JIT 编译 bug —— 环境性，非 ib 缺陷

- 证据：§2.1/§2.2——同一 `MailboxExecutorImpl.yield()` / `runThrowing` c2 帧内 `SEGV_ACCERR`（near-null si_addr 0x86/0xb6/0xbe），ib fork 与 surefire fork 双双命中；JVM 为 Zulu26.30+11-**CRaC**-CA 26.0.1+8（CRaC 变体，AOT/CRaC ergonomic 旗标见 hs_err Global flags）。
- 与已知观察全部吻合：单类小 run 不崩（C2 后台编译未达标即结束）、整模块/连续多类跑崩（热点方法达 C1→C2 编译阈值后执行编译产物即崩）、4g 堆无效（非内存问题）、batch=1 仍崩（编译热点跨类共享）。
- 定位：upstream JDK（Zulu CRaC build）JIT 缺陷 × flink streaming mailbox 代码形态；非 nop-ib fork 机制缺陷。

### H2（证伪）：ib fork 遗漏/改坏 surefire 注入的 JVM 参数

- 证伪证据：§2.3——argLine 逐 token 一致；`JUnitForkExecutor.java:92-111`（argLine token 解析 + `${g:a:type}` 解析 + 不可解析占位符丢弃告警）行为正确，本模块 argLine 无占位符。n/a。

### H3（证伪）：内存耗尽 / worker 资源压力

- 证伪证据：hs_err 无 "insufficient memory"，为 SEGV_ACCERR near-null；host 128G；4g 堆实验无效。

## 4. 建议处置

**Adjudicated-environmental（宿主 JDK JIT bug），watch-only 维持，但改写结论：**

1. 修正 campaign Deferred 条目的表述：不是 "ib fork 独有 abort"，而是 "宿主 JDK 26 CRaC C2 对 flink mailbox 热点方法误编译，surefire 与 ib fork 同崩"（ib 的 crash 采集/诚实失败路径工作正常）。
2. nop-ib 侧唯一值得做的改进（对应 wave6 6C-2）：把已采集的 forkError/forkStdoutTail 透出为 run warnings，并识别 hs_err 写入提示（崩溃目录 `/Users/abc/sources/flink-agents/runtime/hs_err_pid*.log`），使 "宿主 JIT 崩溃" 在 run 输出中自明，无需人工翻 hs_err。非正确性修复。
3. 可选验证/绕过实验（留 successor）：对该模块尝试 `-XX:TieredStopAtLevel=1`（禁 C2）确认崩溃消失，即可彻底坐实 H1；或换非 CRaC Zulu/temurin JDK 26 复测。
4. Successor plan（campaign 已登记的 "fork JVM abort 隔离"）可降级为：JIT-crash 检测（exit 134 + hs_err 存在）→ warning 透出 + 建议 flag 提示。

## 5. 证据锚点

- `/Users/abc/sources/flink-agents/runtime/hs_err_pid25975.log`（ib worker，c2 yield，SEGV_ACCERR）
- `/Users/abc/sources/flink-agents/runtime/hs_err_pid93442.log`（ib worker，c2 runThrowing）
- `/Users/abc/sources/flink-agents/runtime/hs_err_pid84578.log`、`hs_err_pid8635.log`、`hs_err_pid93525.log`（**surefire booter 同帧崩溃**）
- `~/sources/flink-agents/pom.xml:335-339`（surefire argLine=3 个 add-opens）
- `nop-tools/nop-ib/src/main/java/io/nop/ib/junit/JUnitForkExecutor.java:92-111`（argLine 处理）
- `ai-dev/plans/nop-ib/external-project-support-campaign.md` "Deferred But Adjudicated" fork 134 条目
- `ai-dev/logs/2026/11-15.md`（flink 实测 I/II/III：缺陷 16-20、预存环境失败方法学）
